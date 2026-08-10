import os
import ast
import operator
import platform
import subprocess
import webbrowser
import urllib.parse
from datetime import datetime
from google.genai import types

# Predefined Allowlist for Websites
ALLOWED_WEBSITES = {
    "google": "https://www.google.com",
    "youtube": "https://www.youtube.com",
    "github": "https://github.com",
    "gmail": "https://mail.google.com",
    "drive": "https://drive.google.com",
    "calendar": "https://calendar.google.com"
}

# Predefined Allowlist for Local Applications (Windows)
ALLOWED_APPS = {
    "notepad": "notepad.exe",
    "calculator": "calc.exe"
}

# Safe AST Math Operator Mapping
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
    """Safely evaluates mathematical AST nodes without using unrestricted eval()."""
    if isinstance(node, ast.Expression):
        return _safe_eval_ast(node.body)
    elif isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
        return node.value
    elif isinstance(node, ast.BinOp):
        left = _safe_eval_ast(node.left)
        right = _safe_eval_ast(node.right)
        op_type = type(node.op)
        if op_type not in SAFE_OPERATORS:
            raise ValueError(f"Unsupported operator: {op_type.__name__}")
        if op_type == ast.Div and right == 0:
            raise ZeroDivisionError("Division by zero is not allowed.")
        return SAFE_OPERATORS[op_type](left, right)
    elif isinstance(node, ast.UnaryOp):
        operand = _safe_eval_ast(node.operand)
        op_type = type(node.op)
        if op_type not in SAFE_OPERATORS:
            raise ValueError(f"Unsupported operator: {op_type.__name__}")
        return SAFE_OPERATORS[op_type](operand)
    else:
        raise ValueError("Invalid mathematical expression.")


# --- ALLOWLISTED TOOL IMPLEMENTATIONS ---

def get_current_time() -> dict:
    """Returns current computer local time."""
    now = datetime.now()
    time_str = now.strftime("%I:%M %p").lstrip("0")
    return {"success": True, "result": f"The current time is {time_str}."}

def get_current_date() -> dict:
    """Returns current computer local date."""
    now = datetime.now()
    date_str = now.strftime("%A, %B %d, %Y")
    return {"success": True, "result": f"Today is {date_str}."}

def open_website(site: str) -> dict:
    """Opens a predefined website from allowlist."""
    if not site:
        return {"success": False, "error": "Site name cannot be empty."}

    site_key = site.strip().lower()
    if site_key in ALLOWED_WEBSITES:
        url = ALLOWED_WEBSITES[site_key]
        webbrowser.open(url)
        return {"success": True, "result": f"Opened {site_key.capitalize()} ({url}) in your browser."}
    else:
        allowed_list = ", ".join(ALLOWED_WEBSITES.keys())
        return {
            "success": False,
            "error": f"Website '{site}' is not in the allowed list. Allowed sites: {allowed_list}."
        }

def web_search(query: str) -> dict:
    """Opens a Google web search for query."""
    if not query or not query.strip():
        return {"success": False, "error": "Search query cannot be empty."}

    clean_query = query.strip()
    search_url = f"https://www.google.com/search?q={urllib.parse.quote(clean_query)}"
    webbrowser.open(search_url)
    return {"success": True, "result": f"Opened web search for '{clean_query}' in your browser."}

def calculate(expression: str) -> dict:
    """Safely evaluates basic mathematical expressions."""
    if not expression or not expression.strip():
        return {"success": False, "error": "Expression cannot be empty."}

    # Replace common words/symbols
    clean_expr = expression.strip().replace("×", "*").replace("÷", "/").replace("x", "*")
    
    try:
        parsed = ast.parse(clean_expr, mode='eval')
        val = _safe_eval_ast(parsed)
        # Format integer values cleanly
        if isinstance(val, float) and val.is_integer():
            val = int(val)
        return {"success": True, "result": f"{clean_expr} = {val}"}
    except ZeroDivisionError:
        return {"success": False, "error": "Division by zero is not allowed."}
    except Exception as e:
        return {"success": False, "error": f"Could not calculate expression: {str(e)}"}

def get_system_info() -> dict:
    """Returns safe basic system information."""
    info = (
        f"Operating System: {platform.system()} {platform.release()} ({platform.architecture()[0]})\n"
        f"Python Version: {platform.python_version()}\n"
        f"Hostname: {platform.node()}\n"
        f"Current Working Directory: {os.getcwd()}"
    )
    return {"success": True, "result": info}

def open_local_app(app_name: str) -> dict:
    """Opens a safe local application from predefined allowlist."""
    if not app_name:
        return {"success": False, "error": "Application name cannot be empty."}

    app_key = app_name.strip().lower()
    if app_key in ALLOWED_APPS:
        exe_name = ALLOWED_APPS[app_key]
        try:
            subprocess.Popen([exe_name], shell=False)
            return {"success": True, "result": f"Opened {app_key.capitalize()}."}
        except Exception as e:
            return {"success": False, "error": f"Failed to launch {app_key}: {str(e)}"}
    else:
        allowed_list = ", ".join(ALLOWED_APPS.keys())
        return {
            "success": False,
            "error": f"Application '{app_name}' is not in the allowed apps list. Allowed apps: {allowed_list}."
        }


# --- GEMINI TOOL DECLARATIONS ---

def get_gemini_tools() -> list:
    """Returns Gemini Function Declarations for allowlisted tools."""
    fn_time = types.FunctionDeclaration(
        name="get_current_time",
        description="Returns the current computer local time.",
        parameters=types.Schema(type=types.Type.OBJECT, properties={})
    )

    fn_date = types.FunctionDeclaration(
        name="get_current_date",
        description="Returns the current computer local date.",
        parameters=types.Schema(type=types.Type.OBJECT, properties={})
    )

    fn_website = types.FunctionDeclaration(
        name="open_website",
        description="Opens a predefined allowed website (google, youtube, github, gmail, drive, calendar) in default browser.",
        parameters=types.Schema(
            type=types.Type.OBJECT,
            properties={
                "site": types.Schema(
                    type=types.Type.STRING,
                    description="Name of site to open (e.g. 'youtube', 'github', 'google', 'gmail', 'drive', 'calendar')"
                )
            },
            required=["site"]
        )
    )

    fn_search = types.FunctionDeclaration(
        name="web_search",
        description="Opens a Google web search for a user search query in browser.",
        parameters=types.Schema(
            type=types.Type.OBJECT,
            properties={
                "query": types.Schema(
                    type=types.Type.STRING,
                    description="Search query terms"
                )
            },
            required=["query"]
        )
    )

    fn_calc = types.FunctionDeclaration(
        name="calculate",
        description="Evaluates a basic mathematical expression (addition, subtraction, multiplication, division, modulo).",
        parameters=types.Schema(
            type=types.Type.OBJECT,
            properties={
                "expression": types.Schema(
                    type=types.Type.STRING,
                    description="Mathematical expression string, e.g. '25 * 17' or '500 / 8'"
                )
            },
            required=["expression"]
        )
    )

    fn_sysinfo = types.FunctionDeclaration(
        name="get_system_info",
        description="Returns safe operating system name, Python version, hostname, and current directory.",
        parameters=types.Schema(type=types.Type.OBJECT, properties={})
    )

    fn_app = types.FunctionDeclaration(
        name="open_local_app",
        description="Opens a safe local desktop application from allowlist (notepad, calculator).",
        parameters=types.Schema(
            type=types.Type.OBJECT,
            properties={
                "app_name": types.Schema(
                    type=types.Type.STRING,
                    description="Application name (notepad or calculator)"
                )
            },
            required=["app_name"]
        )
    )

    return [
        types.Tool(function_declarations=[
            fn_time, fn_date, fn_website, fn_search, fn_calc, fn_sysinfo, fn_app
        ])
    ]


def get_openai_tools() -> list:
    """
    Returns OpenAI-compatible function declarations.

    Compatible with:
    - Groq
    - OpenRouter
    - Other OpenAI-compatible APIs
    """

    return [
        {
            "type": "function",
            "function": {
                "name": "get_current_time",
                "description": "Returns the current computer local time.",
                "parameters": {
                    "type": "object",
                    "properties": {},
                    "required": []
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "get_current_date",
                "description": "Returns the current computer local date.",
                "parameters": {
                    "type": "object",
                    "properties": {},
                    "required": []
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "open_website",
                "description": (
                    "Opens a predefined allowed website "
                    "(google, youtube, github, gmail, drive, calendar) "
                    "in the default browser."
                ),
                "parameters": {
                    "type": "object",
                    "properties": {
                        "site": {
                            "type": "string",
                            "description": (
                                "Website name: google, youtube, github, "
                                "gmail, drive, or calendar."
                            )
                        }
                    },
                    "required": ["site"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "web_search",
                "description": (
                    "Opens a Google web search for the user's query."
                ),
                "parameters": {
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "The search query."
                        }
                    },
                    "required": ["query"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "calculate",
                "description": (
                    "Safely evaluates a basic mathematical expression "
                    "using addition, subtraction, multiplication, "
                    "division, and modulo."
                ),
                "parameters": {
                    "type": "object",
                    "properties": {
                        "expression": {
                            "type": "string",
                            "description": (
                                "Mathematical expression such as "
                                "'25 * 17' or '500 / 8'."
                            )
                        }
                    },
                    "required": ["expression"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "get_system_info",
                "description": (
                    "Returns safe basic system information including "
                    "operating system, Python version, hostname, "
                    "and current directory."
                ),
                "parameters": {
                    "type": "object",
                    "properties": {},
                    "required": []
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "open_local_app",
                "description": (
                    "Opens a safe local desktop application from the "
                    "allowlist: notepad or calculator."
                ),
                "parameters": {
                    "type": "object",
                    "properties": {
                        "app_name": {
                            "type": "string",
                            "description": (
                                "Application name: notepad or calculator."
                            )
                        }
                    },
                    "required": ["app_name"]
                }
            }
        }
    ]


# --- DISPATCHER ---

TOOL_ROUTER = {
    "get_current_time": lambda args: get_current_time(),
    "get_current_date": lambda args: get_current_date(),
    "open_website": lambda args: open_website(args.get("site", "")),
    "web_search": lambda args: web_search(args.get("query", "")),
    "calculate": lambda args: calculate(args.get("expression", "")),
    "get_system_info": lambda args: get_system_info(),
    "open_local_app": lambda args: open_local_app(args.get("app_name", "")),
}

def execute_tool(tool_name: str, arguments: dict) -> dict:
    """
    Executes an allowlisted tool by name with arguments.
    Returns structured dict result: {'success': bool, 'result': ..., 'error': ...}
    """
    if tool_name not in TOOL_ROUTER:
        return {"success": False, "error": f"Tool '{tool_name}' is not in the allowlist."}

    try:
        return TOOL_ROUTER[tool_name](arguments or {})
    except Exception as e:
        return {"success": False, "error": f"Error executing tool '{tool_name}': {str(e)}"}
