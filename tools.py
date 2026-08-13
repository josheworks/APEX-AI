import os
import ast
import operator
import platform
import subprocess
import webbrowser
import urllib.parse
import urllib.request
import re
import html
import xml.etree.ElementTree as ET
from email.utils import parsedate_to_datetime
from datetime import datetime
from google.genai import types


# ============================================================
# PREDEFINED ALLOWLISTS
# ============================================================

ALLOWED_WEBSITES = {
    "google": "https://www.google.com",
    "youtube": "https://www.youtube.com",
    "github": "https://github.com",
    "gmail": "https://mail.google.com",
    "drive": "https://drive.google.com",
    "calendar": "https://calendar.google.com"
}


ALLOWED_APPS = {
    "notepad": "notepad.exe",
    "calculator": "calc.exe"
}


# ============================================================
# SAFE AST MATH OPERATOR MAPPING
# ============================================================

SAFE_OPERATORS = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
    ast.Mod: operator.mod,
    ast.USub: operator.neg,
    ast.UAdd: operator.pos,
}


def _safe_eval_ast(node):
    """
    Safely evaluates mathematical AST nodes
    without using unrestricted eval().
    """

    if isinstance(node, ast.Expression):
        return _safe_eval_ast(node.body)

    elif isinstance(node, ast.Constant) and isinstance(
        node.value,
        (int, float)
    ):
        return node.value

    elif isinstance(node, ast.BinOp):

        left = _safe_eval_ast(node.left)
        right = _safe_eval_ast(node.right)

        op_type = type(node.op)

        if op_type not in SAFE_OPERATORS:
            raise ValueError(
                f"Unsupported operator: {op_type.__name__}"
            )

        if op_type == ast.Div and right == 0:
            raise ZeroDivisionError(
                "Division by zero is not allowed."
            )

        return SAFE_OPERATORS[op_type](
            left,
            right
        )

    elif isinstance(node, ast.UnaryOp):

        operand = _safe_eval_ast(node.operand)

        op_type = type(node.op)

        if op_type not in SAFE_OPERATORS:
            raise ValueError(
                f"Unsupported operator: {op_type.__name__}"
            )

        return SAFE_OPERATORS[op_type](
            operand
        )

    else:
        raise ValueError(
            "Invalid mathematical expression."
        )


# ============================================================
# BASIC SYSTEM TOOLS
# ============================================================

def get_current_time() -> dict:
    """
    Returns current computer local time.
    """

    now = datetime.now()

    time_str = now.strftime(
        "%I:%M %p"
    ).lstrip("0")

    return {
        "success": True,
        "result":
            f"The current time is {time_str}."
    }


def get_current_date() -> dict:
    """
    Returns current computer local date.
    """

    now = datetime.now()

    date_str = now.strftime(
        "%A, %B %d, %Y"
    )

    return {
        "success": True,
        "result":
            f"Today is {date_str}."
    }


# ============================================================
# WEBSITE TOOL
# ============================================================

def open_website(site: str) -> dict:
    """
    Opens a predefined website from the allowlist.
    """

    if not site:
        return {
            "success": False,
            "error":
                "Site name cannot be empty."
        }

    site_key = site.strip().lower()

    if site_key in ALLOWED_WEBSITES:

        url = ALLOWED_WEBSITES[
            site_key
        ]

        webbrowser.open(url)

        return {
            "success": True,
            "result":
                f"Opened {site_key.capitalize()} "
                f"({url}) in your browser."
        }

    allowed_list = ", ".join(
        ALLOWED_WEBSITES.keys()
    )

    return {
        "success": False,
        "error":
            f"Website '{site}' is not in the "
            f"allowed list. Allowed sites: "
            f"{allowed_list}."
    }


# ============================================================
# NEWS / CURRENT-INFO DETECTION
# ============================================================

def _is_news_query(query: str) -> bool:
    """
    Detect whether the user is asking for
    current, recent, or news information.
    """

    q = query.lower()

    news_keywords = [
        "latest",
        "current",
        "today",
        "news",
        "breaking",
        "recent",
        "this morning",
        "this afternoon",
        "this evening",
        "tonight",
        "this week",
        "what happened",
        "updates",
        "update",
        "headlines",
        "trending",
        "live",
        "right now",
        "currently"
    ]

    return any(
        keyword in q
        for keyword in news_keywords
    )


# ============================================================
# CLEAN SEARCH TEXT
# ============================================================

def _clean_text(value: str) -> str:
    """
    Cleans HTML/XML text.
    """

    if not value:
        return ""

    value = html.unescape(
        value
    )

    value = re.sub(
        r"<[^>]+>",
        "",
        value
    )

    return re.sub(
        r"\s+",
        " ",
        value
    ).strip()


# ============================================================
# NORMALIZE CURRENT-NEWS QUERY
# ============================================================

def _normalize_current_query(
    query: str
) -> str:
    """
    Prevents an AI-generated historical date/year
    from contaminating a current/latest query.

    Example:

        latest AI news today November 3 2025

    becomes:

        latest AI news today

    This is intentionally applied only to queries
    that already indicate current/latest/news intent.
    """

    clean_query = query.strip()

    if not _is_news_query(
        clean_query
    ):
        return clean_query


    # Remove common full date formats.
    clean_query = re.sub(
        r"\b(?:January|February|March|April|May|June|July|"
        r"August|September|October|November|December)"
        r"\s+\d{1,2}(?:st|nd|rd|th)?"
        r"(?:,\s*|\s+)\d{4}\b",
        "",
        clean_query,
        flags=re.IGNORECASE
    )


    # Remove numeric date formats.
    clean_query = re.sub(
        r"\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b",
        "",
        clean_query
    )


    # Remove standalone years when the query is explicitly
    # asking for current/latest/today information.
    clean_query = re.sub(
        r"\b(?:19|20)\d{2}\b",
        "",
        clean_query
    )


    # Clean duplicated whitespace.
    clean_query = re.sub(
        r"\s+",
        " ",
        clean_query
    ).strip()


    return clean_query


# ============================================================
# LIVE NEWS SEARCH
# ============================================================

def _live_news_search(
    query: str
) -> str:
    """
    Fetches recent news using Google News RSS.

    Current/latest/news queries are restricted to
    recent results using Google's 'when:1d' query filter.
    """

    if not query or not query.strip():
        raise ValueError(
            "Search query cannot be empty."
        )


    clean_query = _normalize_current_query(
        query
    )


    # Add a backend-controlled recency filter.
    #
    # IMPORTANT:
    # The model does not control the date.
    # The backend controls the time window.

    news_query = (
        f"{clean_query} when:1d"
    )


    rss_url = (
        "https://news.google.com/rss/search?"
        + urllib.parse.urlencode(
            {
                "q": news_query,
                "hl": "en-US",
                "gl": "US",
                "ceid": "US:en"
            }
        )
    )


    request = urllib.request.Request(
        rss_url,
        headers={
            "User-Agent":
                "Mozilla/5.0 "
                "(compatible; APEX-AI/1.0)"
        }
    )


    try:

        with urllib.request.urlopen(
            request,
            timeout=10
        ) as response:

            xml_data = response.read()

    except Exception as e:

        raise RuntimeError(
            "Live news search request failed: "
            f"{str(e)}"
        )


    try:

        root = ET.fromstring(
            xml_data
        )

    except ET.ParseError as e:

        raise RuntimeError(
            "Could not parse news results: "
            f"{str(e)}"
        )


    items = root.findall(
        ".//item"
    )


    if not items:

        return (
            "I couldn't find recent news "
            "for that topic right now."
        )


    results = []


    for item in items[:8]:

        title = _clean_text(
            item.findtext(
                "title",
                ""
            )
        )

        link = _clean_text(
            item.findtext(
                "link",
                ""
            )
        )

        description = _clean_text(
            item.findtext(
                "description",
                ""
            )
        )

        pub_date = _clean_text(
            item.findtext(
                "pubDate",
                ""
            )
        )


        if not title:
            continue


        formatted_date = pub_date


        if pub_date:

            try:

                parsed_date = (
                    parsedate_to_datetime(
                        pub_date
                    )
                )

                formatted_date = (
                    parsed_date.strftime(
                        "%Y-%m-%d %H:%M"
                    )
                )

            except Exception:
                pass


        result_block = (
            f"- {title}\n"
            f"  Published: {formatted_date}\n"
        )


        if description:
            result_block += (
                f"  Summary: {description}\n"
            )


        if link:
            result_block += (
                f"  Source: {link}"
            )


        results.append(
            result_block
        )


    if not results:

        return (
            "I couldn't find usable recent "
            "news results right now."
        )


    current_date = datetime.now().strftime(
        "%Y-%m-%d"
    )


    return (
        f"Recent news results "
        f"(search date: {current_date}, "
        f"recency window: last 24 hours):\n\n"
        +
        "\n\n".join(results)
    )


# ============================================================
# LIVE GENERAL WEB SEARCH
# ============================================================

def _live_web_search(
    query: str
) -> str:
    """
    Performs a live web search.

    Current/latest/news queries are automatically
    routed to the dedicated recent-news search.
    """

    if not query or not query.strip():
        raise ValueError(
            "Search query cannot be empty."
        )


    clean_query = query.strip()


    # --------------------------------------------------------
    # CURRENT / NEWS REQUEST
    # --------------------------------------------------------

    if _is_news_query(
        clean_query
    ):

        return _live_news_search(
            clean_query
        )


    # --------------------------------------------------------
    # NORMAL WEB SEARCH
    # --------------------------------------------------------

    search_url = (
        "https://html.duckduckgo.com/html/?q="
        +
        urllib.parse.quote(
            clean_query
        )
    )


    request = urllib.request.Request(
        search_url,
        headers={
            "User-Agent":
                "Mozilla/5.0 "
                "(compatible; APEX-AI/1.0)"
        }
    )


    try:

        with urllib.request.urlopen(
            request,
            timeout=8
        ) as response:

            page = response.read().decode(
                "utf-8",
                errors="ignore"
            )

    except Exception as e:

        raise RuntimeError(
            "Live web search request failed: "
            f"{str(e)}"
        )


    titles = re.findall(
        r'class="result__a"[^>]*>(.*?)</a>',
        page,
        re.IGNORECASE | re.DOTALL
    )


    snippets = re.findall(
        r'class="result__snippet"[^>]*>(.*?)</div>',
        page,
        re.IGNORECASE | re.DOTALL
    )


    if not titles:

        return (
            "I could not fetch live web "
            "search results right now."
        )


    summaries = []


    for i, title in enumerate(
        titles[:5]
    ):

        clean_title = _clean_text(
            title
        )


        clean_snippet = ""


        if i < len(snippets):

            clean_snippet = _clean_text(
                snippets[i]
            )


        if clean_snippet:

            summaries.append(
                f"{clean_title} — "
                f"{clean_snippet}"
            )

        else:

            summaries.append(
                clean_title
            )


    return "\n".join(
        summaries
    )


# ============================================================
# PUBLIC WEB SEARCH TOOL
# ============================================================

def web_search(
    query: str
) -> dict:
    """
    Performs a live web search.

    Current/latest/news queries are routed to
    recent news automatically.
    """

    if not query or not query.strip():

        return {
            "success": False,
            "error":
                "Search query cannot be empty."
        }


    original_query = query.strip()


    try:

        result = _live_web_search(
            original_query
        )


        # Show the actual query used by the backend.
        if _is_news_query(
            original_query
        ):

            normalized_query = (
                _normalize_current_query(
                    original_query
                )
            )

            return {
                "success": True,
                "result":
                    f"Live recent-news search for "
                    f"'{normalized_query}':\n"
                    f"{result}"
            }


        return {
            "success": True,
            "result":
                f"Live search results for "
                f"'{original_query}':\n"
                f"{result}"
        }


    except Exception as e:

        return {
            "success": False,
            "error":
                f"Live web search failed: {str(e)}"
        }


# ============================================================
# CALCULATOR
# ============================================================

def calculate(
    expression: str
) -> dict:
    """
    Safely evaluates basic mathematical expressions.
    """

    if not expression or not expression.strip():

        return {
            "success": False,
            "error":
                "Expression cannot be empty."
        }


    clean_expr = (
        expression
        .strip()
        .replace("×", "*")
        .replace("÷", "/")
        .replace("x", "*")
    )


    try:

        parsed = ast.parse(
            clean_expr,
            mode="eval"
        )


        val = _safe_eval_ast(
            parsed
        )


        if (
            isinstance(val, float)
            and val.is_integer()
        ):

            val = int(val)


        return {
            "success": True,
            "result":
                f"{clean_expr} = {val}"
        }


    except ZeroDivisionError:

        return {
            "success": False,
            "error":
                "Division by zero is not allowed."
        }


    except Exception as e:

        return {
            "success": False,
            "error":
                f"Could not calculate expression: "
                f"{str(e)}"
        }


# ============================================================
# SYSTEM INFORMATION
# ============================================================

def get_system_info() -> dict:
    """
    Returns safe basic system information.
    """

    info = (
        f"Operating System: "
        f"{platform.system()} "
        f"{platform.release()} "
        f"({platform.architecture()[0]})\n"

        f"Python Version: "
        f"{platform.python_version()}\n"

        f"Hostname: "
        f"{platform.node()}\n"

        f"Current Working Directory: "
        f"{os.getcwd()}"
    )


    return {
        "success": True,
        "result": info
    }


# ============================================================
# LOCAL APPLICATION TOOL
# ============================================================

def open_local_app(
    app_name: str
) -> dict:
    """
    Opens a safe local application
    from the predefined allowlist.
    """

    if not app_name:

        return {
            "success": False,
            "error":
                "Application name cannot be empty."
        }


    app_key = app_name.strip().lower()


    if app_key in ALLOWED_APPS:

        exe_name = ALLOWED_APPS[
            app_key
        ]


        try:

            subprocess.Popen(
                [exe_name],
                shell=False
            )


            return {
                "success": True,
                "result":
                    f"Opened "
                    f"{app_key.capitalize()}."
            }


        except Exception as e:

            return {
                "success": False,
                "error":
                    f"Failed to launch "
                    f"{app_key}: {str(e)}"
            }


    allowed_list = ", ".join(
        ALLOWED_APPS.keys()
    )


    return {
        "success": False,
        "error":
            f"Application '{app_name}' "
            f"is not in the allowed apps list. "
            f"Allowed apps: {allowed_list}."
    }


# ============================================================
# GEMINI TOOL DECLARATIONS
# ============================================================

def get_gemini_tools() -> list:
    """
    Returns Gemini Function Declarations
    for allowlisted tools.
    """

    fn_time = types.FunctionDeclaration(
        name="get_current_time",

        description=(
            "Returns the current computer local time."
        ),

        parameters=types.Schema(
            type=types.Type.OBJECT,
            properties={}
        )
    )


    fn_date = types.FunctionDeclaration(
        name="get_current_date",

        description=(
            "Returns the current computer local date."
        ),

        parameters=types.Schema(
            type=types.Type.OBJECT,
            properties={}
        )
    )


    fn_website = types.FunctionDeclaration(
        name="open_website",

        description=(
            "Opens a predefined allowed website "
            "(google, youtube, github, gmail, "
            "drive, calendar) in the default browser."
        ),

        parameters=types.Schema(
            type=types.Type.OBJECT,

            properties={
                "site": types.Schema(
                    type=types.Type.STRING,

                    description=(
                        "Name of site to open. "
                        "Examples: youtube, github, "
                        "google, gmail, drive, calendar."
                    )
                )
            },

            required=["site"]
        )
    )


    fn_search = types.FunctionDeclaration(
        name="web_search",

        description=(
            "Searches the live web for current "
            "information. MUST be used for latest, "
            "current, today, recent, breaking news, "
            "headlines, trending topics, prices, "
            "weather, events, and other time-sensitive "
            "requests. Preserve the user's search topic. "
            "NEVER invent or append a date or year that "
            "the user did not provide."
        ),

        parameters=types.Schema(
            type=types.Type.OBJECT,

            properties={
                "query": types.Schema(
                    type=types.Type.STRING,

                    description=(
                        "The user's search topic. "
                        "Preserve the user's original "
                        "wording. Do not add dates, years, "
                        "locations, events, or other "
                        "constraints unless explicitly "
                        "provided by the user."
                    )
                )
            },

            required=["query"]
        )
    )


    fn_calc = types.FunctionDeclaration(
        name="calculate",

        description=(
            "Evaluates a basic mathematical expression "
            "(addition, subtraction, multiplication, "
            "division, modulo)."
        ),

        parameters=types.Schema(
            type=types.Type.OBJECT,

            properties={
                "expression": types.Schema(
                    type=types.Type.STRING,

                    description=(
                        "Mathematical expression string, "
                        "e.g. '25 * 17' or '500 / 8'."
                    )
                )
            },

            required=["expression"]
        )
    )


    fn_sysinfo = types.FunctionDeclaration(
        name="get_system_info",

        description=(
            "Returns safe operating system name, "
            "Python version, hostname, and "
            "current directory."
        ),

        parameters=types.Schema(
            type=types.Type.OBJECT,
            properties={}
        )
    )


    fn_app = types.FunctionDeclaration(
        name="open_local_app",

        description=(
            "Opens a safe local desktop application "
            "from the allowlist: notepad or calculator."
        ),

        parameters=types.Schema(
            type=types.Type.OBJECT,

            properties={
                "app_name": types.Schema(
                    type=types.Type.STRING,

                    description=(
                        "Application name: "
                        "notepad or calculator."
                    )
                )
            },

            required=["app_name"]
        )
    )


    return [
        types.Tool(
            function_declarations=[
                fn_time,
                fn_date,
                fn_website,
                fn_search,
                fn_calc,
                fn_sysinfo,
                fn_app
            ]
        )
    ]


# ============================================================
# OPENAI-COMPATIBLE TOOL DECLARATIONS
# ============================================================

def get_openai_tools() -> list:
    """
    Returns OpenAI-compatible function declarations.

    Compatible with:
    - Groq
    - OpenRouter
    - Other OpenAI-compatible APIs
    """

    return [

        # ----------------------------------------------------
        # CURRENT TIME
        # ----------------------------------------------------

        {
            "type": "function",

            "function": {
                "name":
                    "get_current_time",

                "description":
                    "Returns the current "
                    "computer local time.",

                "parameters": {
                    "type": "object",

                    "properties": {},

                    "required": []
                }
            }
        },


        # ----------------------------------------------------
        # CURRENT DATE
        # ----------------------------------------------------

        {
            "type": "function",

            "function": {
                "name":
                    "get_current_date",

                "description":
                    "Returns the current "
                    "computer local date.",

                "parameters": {
                    "type": "object",

                    "properties": {},

                    "required": []
                }
            }
        },


        # ----------------------------------------------------
        # OPEN WEBSITE
        # ----------------------------------------------------

        {
            "type": "function",

            "function": {
                "name":
                    "open_website",

                "description": (
                    "Opens a predefined allowed "
                    "website (google, youtube, "
                    "github, gmail, drive, calendar) "
                    "in the default browser."
                ),

                "parameters": {
                    "type": "object",

                    "properties": {

                        "site": {
                            "type": "string",

                            "description": (
                                "Website name: google, "
                                "youtube, github, gmail, "
                                "drive, or calendar."
                            )
                        }
                    },

                    "required": [
                        "site"
                    ]
                }
            }
        },


        # ----------------------------------------------------
        # WEB SEARCH
        # ----------------------------------------------------

        {
            "type": "function",

            "function": {
                "name":
                    "web_search",

                "description": (
                    "Searches the live web for current "
                    "information. MUST be used for "
                    "latest, current, today, recent, "
                    "breaking news, headlines, trending "
                    "topics, prices, weather, events, "
                    "and other time-sensitive requests. "
                    "IMPORTANT: Preserve the user's "
                    "search topic exactly. NEVER invent "
                    "or append a date or year that the "
                    "user did not provide. If the user "
                    "says today, do not replace it with "
                    "a historical date."
                ),

                "parameters": {
                    "type": "object",

                    "properties": {

                        "query": {
                            "type": "string",

                            "description": (
                                "The user's search topic. "
                                "Preserve the user's "
                                "original wording. Do not "
                                "add dates, years, locations, "
                                "events, or other constraints "
                                "unless explicitly provided "
                                "by the user."
                            )
                        }
                    },

                    "required": [
                        "query"
                    ]
                }
            }
        },


        # ----------------------------------------------------
        # CALCULATOR
        # ----------------------------------------------------

        {
            "type": "function",

            "function": {
                "name":
                    "calculate",

                "description": (
                    "Safely evaluates a basic "
                    "mathematical expression using "
                    "addition, subtraction, "
                    "multiplication, division, "
                    "and modulo."
                ),

                "parameters": {
                    "type": "object",

                    "properties": {

                        "expression": {
                            "type": "string",

                            "description": (
                                "Mathematical expression "
                                "such as '25 * 17' "
                                "or '500 / 8'."
                            )
                        }
                    },

                    "required": [
                        "expression"
                    ]
                }
            }
        },


        # ----------------------------------------------------
        # SYSTEM INFO
        # ----------------------------------------------------

        {
            "type": "function",

            "function": {
                "name":
                    "get_system_info",

                "description": (
                    "Returns safe basic system "
                    "information including operating "
                    "system, Python version, hostname, "
                    "and current directory."
                ),

                "parameters": {
                    "type": "object",

                    "properties": {},

                    "required": []
                }
            }
        },


        # ----------------------------------------------------
        # LOCAL APP
        # ----------------------------------------------------

        {
            "type": "function",

            "function": {
                "name":
                    "open_local_app",

                "description": (
                    "Opens a safe local desktop "
                    "application from the allowlist: "
                    "notepad or calculator."
                ),

                "parameters": {
                    "type": "object",

                    "properties": {

                        "app_name": {
                            "type": "string",

                            "description": (
                                "Application name: "
                                "notepad or calculator."
                            )
                        }
                    },

                    "required": [
                        "app_name"
                    ]
                }
            }
        }
    ]


# ============================================================
# TOOL DISPATCHER
# ============================================================

TOOL_ROUTER = {

    "get_current_time":
        lambda args:
            get_current_time(),

    "get_current_date":
        lambda args:
            get_current_date(),

    "open_website":
        lambda args:
            open_website(
                args.get(
                    "site",
                    ""
                )
            ),

    "web_search":
        lambda args:
            web_search(
                args.get(
                    "query",
                    ""
                )
            ),

    "calculate":
        lambda args:
            calculate(
                args.get(
                    "expression",
                    ""
                )
            ),

    "get_system_info":
        lambda args:
            get_system_info(),

    "open_local_app":
        lambda args:
            open_local_app(
                args.get(
                    "app_name",
                    ""
                )
            ),
}


# ============================================================
# TOOL EXECUTION
# ============================================================

def execute_tool(
    tool_name: str,
    arguments: dict
) -> dict:
    """
    Executes an allowlisted tool by name.

    Returns:
        {
            'success': bool,
            'result': ...,
            'error': ...
        }
    """

    if tool_name not in TOOL_ROUTER:

        return {
            "success": False,
            "error":
                f"Tool '{tool_name}' "
                f"is not in the allowlist."
        }


    try:

        return TOOL_ROUTER[
            tool_name
        ](
            arguments or {}
        )


    except Exception as e:

        return {
            "success": False,
            "error":
                f"Error executing tool "
                f"'{tool_name}': {str(e)}"
        }