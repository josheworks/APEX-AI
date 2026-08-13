import base64
import json
import os
import threading
from typing import Any

from dotenv import load_dotenv
from google import genai
from google.genai import types
from openai import OpenAI

import memory_manager
import tools


# ============================================================
# ENVIRONMENT
# ============================================================

load_dotenv()


# ============================================================
# LIMITS
# ============================================================

MAX_CONVERSATION_MESSAGES = 20
MAX_TOOL_ROUNDS = 5


# ============================================================
# CONVERSATION HISTORY
# ============================================================

_conversation_history: list[dict[str, str]] = []
_conversation_lock = threading.Lock()


# ============================================================
# APEX PERSONALITY
# ============================================================

SYSTEM_INSTRUCTION = (
    "APEX stands for Advanced Personal Executive.\n"
    "APEX is a jolly, friendly, cheerful, warm, intelligent, "
    "and helpful personal AI executive assistant.\n\n"

    "Personality & Tone Guidelines:\n"
    "- Sound like a smart friend who happens to be extremely capable.\n"
    "- Friendly, warm, slightly playful, helpful, and natural.\n"
    "- Use conversational responses "
    "(e.g., 'Sure thing!', 'Got it!', 'On it!', 'Here you go!', 'Done!').\n"
    "- NEVER sound robotic, commanding, strict, or military.\n"
    "- Do NOT use emojis in every single response; use light humor occasionally.\n"
    "- For simple requests, keep responses brief. "
    "For educational questions, explain clearly.\n"

    "- Do NOT use web_search for normal questions "
    "that can be answered reliably from your existing knowledge.\n"

    "- ALWAYS use web_search for requests involving current, latest, "
    "today's, recent, breaking, live, trending, news, prices, weather, "
    "events, or other time-sensitive information.\n"

    "- NEVER invent, assume, or append a date or year to the user's "
    "search query unless the user explicitly provided that date or year.\n"

    "- For queries containing 'today', 'latest', 'current', or 'recent', "
    "preserve the user's wording and do not convert it into an old specific date.\n"

    "- If the user says 'today', search for today's information based "
    "on the actual current date, not a remembered date.\n"

    "- When the user asks for current/latest/news information, "
    "never rely on memory alone. Use the web_search tool first.\n"

    "- For current/news searches, prefer recent results "
    "and pay attention to publication dates.\n"

    "- Crucial Rule: APEX must NEVER claim that it performed "
    "an action when it did not."
)


# ============================================================
# EXCEPTIONS
# ============================================================

class QuotaExhaustedException(Exception):
    pass


# ============================================================
# CLIENTS
# ============================================================

def _get_groq_client() -> OpenAI:
    api_key = os.getenv("GROQ_API_KEY")

    if not api_key:
        raise ValueError(
            "GROQ_API_KEY is not configured."
        )

    return OpenAI(
        api_key=api_key,
        base_url="https://api.groq.com/openai/v1"
    )


def _get_openrouter_client() -> OpenAI:
    api_key = os.getenv("OPENROUTER_API_KEY")

    if not api_key:
        raise ValueError(
            "OPENROUTER_API_KEY is not configured."
        )

    return OpenAI(
        api_key=api_key,
        base_url="https://openrouter.ai/api/v1"
    )


def _get_validated_gemini_api_key_and_model():
    api_key = os.getenv("GEMINI_API_KEY")

    model_name = os.getenv(
        "GEMINI_MODEL",
        "gemini-3.6-flash"
    )

    if (
        not api_key
        or api_key.strip() == ""
        or api_key == "your_gemini_api_key_here"
    ):
        raise ValueError(
            "GEMINI_API_KEY is not configured or contains "
            "placeholder value. Please set a valid API key."
        )

    return api_key, model_name


# ============================================================
# ERROR HELPERS
# ============================================================

def _check_quota_error(exception: Exception) -> bool:
    """
    Detect quota/rate-limit errors.

    This is retained for diagnostics, but provider fallback
    no longer depends only on quota errors.
    """

    msg = str(exception)

    return (
        "429" in msg
        or "RESOURCE_EXHAUSTED" in msg
        or "quota" in msg.lower()
        or "rate limit" in msg.lower()
        or "rate_limit" in msg.lower()
    )


# ============================================================
# CONVERSATION HISTORY
# ============================================================

def _append_conversation(
    role: str,
    content: str
) -> None:

    if not content:
        return

    with _conversation_lock:

        _conversation_history.append(
            {
                "role": role,
                "content": content
            }
        )

        if (
            len(_conversation_history)
            > MAX_CONVERSATION_MESSAGES
        ):

            excess = (
                len(_conversation_history)
                - MAX_CONVERSATION_MESSAGES
            )

            _conversation_history[:] = (
                _conversation_history[excess:]
            )


def _get_conversation_context():
    with _conversation_lock:
        return list(_conversation_history)


# ============================================================
# MEMORY / SYSTEM MESSAGES
# ============================================================

def _build_system_messages():
    memory = (
        memory_manager.load_user_memory()
    )

    memory_summary = (
        memory_manager.build_memory_summary(
            memory
        )
    )

    messages = [
        {
            "role": "system",
            "content": SYSTEM_INSTRUCTION
        }
    ]

    if memory_summary:

        messages.append(
            {
                "role": "system",
                "content":
                    f"Relevant stored memory: "
                    f"{memory_summary}"
            }
        )

    return messages


def _build_message_sequence(
    user_message: str
):
    messages = _build_system_messages()

    messages.extend(
        _get_conversation_context()
    )

    messages.append(
        {
            "role": "user",
            "content": user_message
        }
    )

    return messages


# ============================================================
# TOOL RESPONSE FORMATTER
# ============================================================

def _format_tool_response(
    tool_name: str,
    tool_result: dict
) -> str:

    if not tool_result.get(
        "success",
        False
    ):

        error = tool_result.get(
            "error",
            "Something went wrong."
        )

        return (
            f"Hmm, I ran into a problem with that — "
            f"{error} Want to try again?"
        )

    result = tool_result.get(
        "result",
        ""
    )

    responses = {

        "get_current_time":
            lambda r: r,

        "get_current_date":
            lambda r: r,

        "open_website":
            lambda r: f"Sure! {r}",

        "web_search":
            lambda r: f"Got it! {r}",

        "calculate":
            lambda r: f"Here you go: {r}",

        "get_system_info":
            lambda r: f"Here's what I found:\n{r}",

        "open_local_app":
            lambda r: f"Sure! {r}",
    }

    formatter = responses.get(
        tool_name
    )

    if formatter:
        return formatter(result)

    return (
        result
        if result
        else "Done!"
    )


# ============================================================
# TOOL CALL NORMALIZATION
# ============================================================

def _normalize_tool_call(
    tool_call: Any
):
    if not tool_call:
        return None


    # Gemini function call

    if (
        hasattr(tool_call, "name")
        and hasattr(tool_call, "args")
    ):

        return {
            "name": tool_call.name,
            "arguments": (
                tool_call.args
                or {}
            )
        }


    # Dictionary format

    if isinstance(
        tool_call,
        dict
    ):

        function = (
            tool_call.get("function")
            or tool_call.get("name")
        )

        args = (
            tool_call.get("arguments")
            or tool_call.get("args")
            or {}
        )

        if isinstance(
            function,
            dict
        ):

            tool_name = function.get(
                "name"
            )

        else:

            tool_name = function

        if not tool_name:
            return None

        if isinstance(args, str):

            try:
                args = json.loads(args)

            except Exception:
                args = {}

        return {
            "name": tool_name,
            "arguments": args or {}
        }


    # OpenAI/Groq/OpenRouter object

    function = getattr(
        tool_call,
        "function",
        None
    )

    if function is not None:

        name = getattr(
            function,
            "name",
            None
        )

        args = getattr(
            function,
            "arguments",
            None
        )

        try:

            args = json.loads(
                args or "{}"
            )

        except Exception:

            args = {}

        if name:

            return {
                "name": name,
                "arguments": args
            }

    return None


# ============================================================
# OPENAI-COMPATIBLE TOOL LOOP
# ============================================================

def _run_openai_compatible_tool_loop(
    client: Any,
    model: str,
    user_message: str
):
    """
    Multi-round tool execution loop.

    Used by:
        1. OpenRouter
        2. Groq

    Supports multiple tool calls and
    sends tool results back to the model.
    """

    messages = _build_message_sequence(
        user_message
    )

    final_tool_name = None
    last_tool_result = None


    for tool_round in range(
        1,
        MAX_TOOL_ROUNDS + 1
    ):

        response = (
            client.chat.completions.create(
                model=model,
                messages=messages,
                tools=tools.get_openai_tools(),
                tool_choice="auto"
            )
        )


        message = (
            response.choices[0].message
        )


        tool_calls = getattr(
            message,
            "tool_calls",
            None
        )


        # Final text response.

        if not tool_calls:

            content = getattr(
                message,
                "content",
                None
            )

            if content:

                return (
                    content.strip(),
                    final_tool_name
                )

            break


        # Preserve assistant tool-call message.

        assistant_message = {
            "role": "assistant",
            "content":
                getattr(
                    message,
                    "content",
                    ""
                ) or ""
        }


        if tool_calls:

            assistant_message[
                "tool_calls"
            ] = tool_calls


        messages.append(
            assistant_message
        )


        any_executed = False


        # Execute EVERY tool call.

        for tool_call in tool_calls:

            normalized = (
                _normalize_tool_call(
                    tool_call
                )
            )

            if not normalized:
                continue


            tool_name = normalized[
                "name"
            ]


            if final_tool_name is None:

                final_tool_name = (
                    tool_name
                )


            last_tool_result = (
                tools.execute_tool(
                    tool_name,
                    normalized[
                        "arguments"
                    ]
                )
            )


            # OpenAI-compatible tool result.

            tool_call_id = getattr(
                tool_call,
                "id",
                None
            )


            tool_message = {
                "role": "tool",
                "content": json.dumps(
                    last_tool_result
                )
            }


            if tool_call_id:

                tool_message[
                    "tool_call_id"
                ] = tool_call_id


            messages.append(
                tool_message
            )


            any_executed = True


        if not any_executed:
            break


    # Safety fallback if provider stopped
    # after tool execution.

    if (
        last_tool_result is not None
        and final_tool_name is not None
    ):

        return (
            _format_tool_response(
                final_tool_name,
                last_tool_result
            ),
            final_tool_name
        )


    raise RuntimeError(
        "Unable to produce a response "
        "after tool execution."
    )


# ============================================================
# OPENROUTER CHAT
# ============================================================

def _ask_openrouter(
    user_message: str
):
    """
    PRIMARY CHAT PROVIDER.
    """

    client = _get_openrouter_client()

    model = os.getenv(
        "OPENROUTER_MODEL",
        "openrouter/auto"
    )

    return _run_openai_compatible_tool_loop(
        client,
        model,
        user_message
    )


# ============================================================
# GROQ CHAT
# ============================================================

def _ask_groq(
    user_message: str
):
    """
    SECOND CHAT PROVIDER.
    """

    client = _get_groq_client()

    model = os.getenv(
        "GROQ_MODEL",
        "llama-3.3-70b-versatile"
    )

    return _run_openai_compatible_tool_loop(
        client,
        model,
        user_message
    )


# ============================================================
# GEMINI CHAT
# ============================================================

def _ask_gemini(
    user_message: str
):
    """
    THIRD CHAT PROVIDER.
    """

    api_key, model_name = (
        _get_validated_gemini_api_key_and_model()
    )

    client = genai.Client(
        api_key=api_key
    )

    return _run_gemini_tool_loop(
        client,
        model_name,
        user_message
    )


# ============================================================
# GEMINI TOOL LOOP
# ============================================================

def _run_gemini_tool_loop(
    client: Any,
    model: str,
    user_message: str
):
    """
    Multi-round Gemini tool execution.
    """

    memory = (
        memory_manager.load_user_memory()
    )

    memory_summary = (
        memory_manager.build_memory_summary(
            memory
        )
    )

    final_tool_name = None
    last_tool_result = None

    conversation_context = (
        _get_conversation_context()
    )


    for tool_round in range(
        1,
        MAX_TOOL_ROUNDS + 1
    ):

        if tool_round == 1:

            content = user_message

        else:

            context_text = (
                "Previous conversation:\n"
            )

            for msg in (
                conversation_context[-4:]
            ):

                context_text += (
                    f"{msg['role'].upper()}: "
                    f"{msg['content']}\n"
                )


            if memory_summary:

                context_text += (
                    f"\nRelevant stored memory: "
                    f"{memory_summary}\n"
                )


            context_text += (
                "\nNow please process the "
                "following and provide a natural "
                "language response:\n"
                f"{user_message}"
            )


            content = context_text


        response = (
            client.models.generate_content(
                model=model,
                contents=content,
                config=types.GenerateContentConfig(
                    system_instruction=
                        SYSTEM_INSTRUCTION,
                    tools=
                        tools.get_gemini_tools()
                )
            )
        )


        function_calls = getattr(
            response,
            "function_calls",
            None
        )


        if not function_calls:

            if response.text:

                return (
                    response.text.strip(),
                    final_tool_name
                )

            break


        any_executed = False


        for call in function_calls:

            normalized = (
                _normalize_tool_call(
                    call
                )
            )

            if not normalized:
                continue


            tool_name = normalized[
                "name"
            ]


            if final_tool_name is None:

                final_tool_name = (
                    tool_name
                )


            last_tool_result = (
                tools.execute_tool(
                    tool_name,
                    normalized[
                        "arguments"
                    ]
                )
            )


            _append_conversation(
                "assistant",
                f"[Executing {tool_name} tool...]"
            )


            _append_conversation(
                "tool",
                json.dumps(
                    last_tool_result
                )
            )


            any_executed = True


        if not any_executed:
            break


        conversation_context = (
            _get_conversation_context()
        )


    if (
        last_tool_result is not None
        and final_tool_name is not None
    ):

        return (
            _format_tool_response(
                final_tool_name,
                last_tool_result
            ),
            final_tool_name
        )


    raise RuntimeError(
        "Unable to produce a response "
        "after tool execution."
    )


# ============================================================
# CHAT PROVIDER FALLBACK
# ============================================================

def _get_chat_providers():

    return [
        (
            "OpenRouter",
            _ask_openrouter
        ),
        (
            "Groq",
            _ask_groq
        ),
        (
            "Gemini",
            _ask_gemini
        ),
    ]


def _fallback_chat_response(
    user_message: str
):
    """
    Provider priority:

        OpenRouter
            ↓
        Groq
            ↓
        Gemini

    Any provider error triggers the next provider.
    """

    errors = []


    for (
        provider_name,
        provider_function
    ) in _get_chat_providers():

        try:

            print(
                f"[APEX] Trying chat provider: "
                f"{provider_name}"
            )


            response = (
                provider_function(
                    user_message
                )
            )


            print(
                f"[APEX] Chat provider succeeded: "
                f"{provider_name}"
            )


            return response


        except Exception as error:

            print(
                f"[APEX] {provider_name} failed: "
                f"{error}"
            )


            errors.append(
                f"{provider_name}: {error}"
            )


    raise RuntimeError(
        "All chat providers failed.\n"
        + "\n".join(errors)
    )


# ============================================================
# MAIN AI RESPONSE
# ============================================================

def get_ai_response(
    user_message: str
):
    """
    Main APEX AI entry point.

    Priority:

        1. OpenRouter
        2. Groq
        3. Gemini
    """

    if (
        not user_message
        or not user_message.strip()
    ):

        raise ValueError(
            "User message cannot be empty."
        )


    # Update memory.

    memory_manager.update_user_memory(
        user_message
    )


    # Add user message once.

    _append_conversation(
        "user",
        user_message
    )


    try:

        final_text, tool_used = (
            _fallback_chat_response(
                user_message
            )
        )


        _append_conversation(
            "assistant",
            final_text
        )


        return (
            final_text,
            tool_used
        )


    except Exception as error:

        raise RuntimeError(
            f"All LLM providers failed: "
            f"{error}"
        )


# ============================================================
# GROQ WHISPER TRANSCRIPTION
# ============================================================

def _transcribe_with_groq(
    audio_bytes: bytes,
    mime_type: str
) -> str:

    client = _get_groq_client()


    model = os.getenv(
        "GROQ_STT_MODEL",
        "whisper-large-v3-turbo"
    )


    filename = (
        "apex_audio.webm"
    )


    transcription = (
        client.audio.transcriptions.create(
            file=(
                filename,
                audio_bytes,
                mime_type
            ),
            model=model,
            response_format="json",
            temperature=0.0
        )
    )


    text = getattr(
        transcription,
        "text",
        None
    )


    if not text:

        raise RuntimeError(
            "Groq returned an empty transcription."
        )


    return text.strip()


# ============================================================
# OPENROUTER TRANSCRIPTION
# ============================================================

def _transcribe_with_openrouter(
    audio_bytes: bytes,
    mime_type: str
) -> str:
    """
    PRIMARY VOICE TRANSCRIPTION PROVIDER.

    Uses OpenRouter's dedicated transcription endpoint
    through the OpenAI-compatible client.

    OpenRouter supports multipart audio uploads and
    transcription models such as Whisper.
    """

    client = _get_openrouter_client()


    model = os.getenv(
        "OPENROUTER_STT_MODEL",
        "openai/whisper-large-v3"
    )


    # OpenAI-compatible transcription endpoint.

    filename = (
        _audio_filename_from_mime(
            mime_type
        )
    )


    transcription = (
        client.audio.transcriptions.create(
            file=(
                filename,
                audio_bytes,
                mime_type
            ),
            model=model,
            response_format="json",
            temperature=0.0
        )
    )


    text = getattr(
        transcription,
        "text",
        None
    )


    if not text:

        raise RuntimeError(
            "OpenRouter returned an empty transcription."
        )


    return text.strip()


# ============================================================
# GEMINI AUDIO TRANSCRIPTION
# ============================================================

def _transcribe_with_gemini(
    audio_bytes: bytes,
    mime_type: str
) -> str:
    """
    THIRD VOICE TRANSCRIPTION PROVIDER.
    """

    api_key, model_name = (
        _get_validated_gemini_api_key_and_model()
    )


    client = genai.Client(
        api_key=api_key
    )


    audio_part = (
        types.Part.from_bytes(
            data=audio_bytes,
            mime_type=mime_type
        )
    )


    prompt = (
        "Transcribe the spoken audio in this recording "
        "accurately into plain text. "
        "Output ONLY the raw transcription text "
        "without commentary, intro, or quotation marks. "
        "If no speech is detected, respond with "
        "'[No speech detected]'."
    )


    response = (
        client.models.generate_content(
            model=model_name,
            contents=[
                audio_part,
                prompt
            ]
        )
    )


    if not response.text:

        raise RuntimeError(
            "Gemini audio transcription returned "
            "an empty result."
        )


    return response.text.strip()


# ============================================================
# AUDIO FILENAME
# ============================================================

def _audio_filename_from_mime(
    mime_type: str
) -> str:

    clean_mime = (
        mime_type
        .split(";")[0]
        .strip()
        .lower()
    )


    extensions = {

        "audio/webm":
            "webm",

        "audio/webm;codecs=opus":
            "webm",

        "audio/mp4":
            "mp4",

        "audio/mpeg":
            "mp3",

        "audio/mp3":
            "mp3",

        "audio/wav":
            "wav",

        "audio/x-wav":
            "wav",

        "audio/ogg":
            "ogg",

        "audio/flac":
            "flac",

        "audio/aac":
            "aac",
    }


    extension = extensions.get(
        clean_mime,
        "webm"
    )


    return (
        f"apex_audio.{extension}"
    )


# ============================================================
# VOICE PROVIDER FALLBACK
# ============================================================

def _get_voice_providers():

    return [
        (
            "OpenRouter",
            _transcribe_with_openrouter
        ),
        (
            "Groq Whisper",
            _transcribe_with_groq
        ),
        (
            "Gemini Audio",
            _transcribe_with_gemini
        ),
    ]


def transcribe_audio(
    audio_bytes: bytes,
    mime_type: str = "audio/webm"
) -> str:
    """
    Main APEX speech-to-text entry point.

    Priority:

        1. OpenRouter
        2. Groq Whisper
        3. Gemini Audio

    IMPORTANT:
    Any provider error causes fallback to the
    next provider.
    """

    if (
        not audio_bytes
        or len(audio_bytes) == 0
    ):

        raise ValueError(
            "Audio data is empty."
        )


    clean_mime_type = (
        mime_type
        .split(";")[0]
        .strip()
        if mime_type
        else "audio/webm"
    )


    errors = []


    for (
        provider_name,
        provider_function
    ) in _get_voice_providers():

        try:

            print(
                f"[APEX] Trying voice transcription: "
                f"{provider_name}"
            )


            transcription = (
                provider_function(
                    audio_bytes,
                    clean_mime_type
                )
            )


            if not transcription:

                raise RuntimeError(
                    "Provider returned empty transcription."
                )


            print(
                f"[APEX] Voice transcription provider: "
                f"{provider_name}"
            )


            return transcription.strip()


        except Exception as error:

            print(
                f"[APEX] {provider_name} "
                f"transcription failed: {error}"
            )


            errors.append(
                f"{provider_name}: {error}"
            )


    raise RuntimeError(
        "All voice transcription providers failed.\n"
        + "\n".join(errors)
    )