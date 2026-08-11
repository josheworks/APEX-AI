import os
import json
from dotenv import load_dotenv
from google import genai
from google.genai import types
from openai import OpenAI
import tools

# Load environment variables from .env file
load_dotenv()

# APEX Jolly, Friendly & Helpful System Personality Instruction
SYSTEM_INSTRUCTION = (
    "APEX stands for Advanced Personal Executive.\n"
    "APEX is a jolly, friendly, cheerful, warm, intelligent, and helpful personal AI executive assistant.\n\n"
    "Personality & Tone Guidelines:\n"
    "- Sound like a smart friend who happens to be extremely capable.\n"
    "- Friendly, warm, slightly playful, helpful, and natural.\n"
    "- Use conversational responses (e.g., 'Sure thing!', 'Got it!', 'On it!', 'Here you go!', 'Done!').\n"
    "- NEVER sound robotic, commanding, strict, or military.\n"
    "- Do NOT use emojis in every single response; use light humor occasionally.\n"
    "- For simple requests, keep responses brief. For educational questions, explain clearly.\n"
    "- Do NOT use web_search for normal questions you can answer yourself.\n"
    "- Use web_search when the user explicitly asks for web/search,"
    "OR when answering requires current/latest/real-time information..\n"
    "- Crucial Rule: APEX must NEVER claim that it performed an action when it did not."
)

# Custom exception class for Gemini quota exhaustion
class QuotaExhaustedException(Exception):
    pass
def _get_groq_client():
    api_key = os.getenv("GROQ_API_KEY")

    if not api_key:
        raise ValueError("GROQ_API_KEY is not configured.")

    return OpenAI(
        api_key=api_key,
        base_url="https://api.groq.com/openai/v1"
    )


def _get_openrouter_client():
    api_key = os.getenv("OPENROUTER_API_KEY")

    if not api_key:
        raise ValueError("OPENROUTER_API_KEY is not configured.")

    return OpenAI(
        api_key=api_key,
        base_url="https://openrouter.ai/api/v1"
    )

def _get_validated_api_key_and_model():
    """Helper to validate API key and retrieve model name."""
    api_key = os.getenv("GEMINI_API_KEY")
    model_name = os.getenv("GEMINI_MODEL", "gemini-3.6-flash")

    if not api_key or api_key.strip() == "" or api_key == "your_gemini_api_key_here":
        raise ValueError(
            "GEMINI_API_KEY is not configured or contains placeholder value. "
            "Please set a valid API key in your .env file."
        )

    return api_key, model_name


def _check_quota_error(exception: Exception) -> bool:
    """
    Returns True if the exception is a Gemini 429 RESOURCE_EXHAUSTED quota error.
    Checks both daily quota and per-minute rate limit exhaustion.
    """
    msg = str(exception)
    return "429" in msg or "RESOURCE_EXHAUSTED" in msg or "quota" in msg.lower()


def _format_tool_response(tool_name: str, tool_result: dict) -> str:
    """
    Converts a tool execution result into a friendly APEX natural-language response.
    This avoids a second Gemini API call for every tool invocation.
    """
    if not tool_result.get("success", False):
        error = tool_result.get("error", "Something went wrong.")
        return f"Hmm, I ran into a problem with that — {error} Want to try again?"

    result = tool_result.get("result", "")

    RESPONSES = {
        "get_current_time": lambda r: r,
        "get_current_date": lambda r: r,
        "open_website":     lambda r: f"Sure! {r}",
        "web_search":       lambda r: f"Got it! {r}",
        "calculate":        lambda r: f"Here you go: {r}",
        "get_system_info":  lambda r: f"Here's what I found:\n{r}",
        "open_local_app":   lambda r: f"Sure! {r}",
    }

    formatter = RESPONSES.get(tool_name)
    if formatter:
        return formatter(result)
    return result if result else "Done!"
def _ask_groq(user_message: str) -> tuple[str, str | None]:

    client = _get_groq_client()

    model = os.getenv(
        "GROQ_MODEL",
        "openai/gpt-oss-20b"
    )

    messages = [
        {
            "role": "system",
            "content": SYSTEM_INSTRUCTION
        },
        {
            "role": "user",
            "content": user_message
        }
    ]

    response = client.chat.completions.create(
        model=model,
        messages=messages,
        tools=tools.get_openai_tools(),
        tool_choice="auto"
    )

    message = response.choices[0].message

    if not message.tool_calls:

        if not message.content:
            raise RuntimeError("Groq returned an empty response.")

        return message.content.strip(), None

    messages.append(message)

    first_tool_name = None
    last_tool_result = None

    for tool_call in message.tool_calls:

        tool_name = tool_call.function.name

        if first_tool_name is None:
            first_tool_name = tool_name

        try:
            arguments = json.loads(
                tool_call.function.arguments or "{}"
            )
        except json.JSONDecodeError:
            arguments = {}

        last_tool_result = tools.execute_tool(
            tool_name,
            arguments
        )

        messages.append(
            {
                "role": "tool",
                "tool_call_id": tool_call.id,
                "name": tool_name,
                "content": json.dumps(last_tool_result)
            }
        )

    final_response = client.chat.completions.create(
        model=model,
        messages=messages
    )

    final_message = final_response.choices[0].message

    if not final_message.content:
        return (
            _format_tool_response(
                first_tool_name,
                last_tool_result
            ),
            first_tool_name
        )

    return final_message.content.strip(), first_tool_name

def _ask_openrouter(user_message: str) -> tuple[str, str | None]:

    client = _get_openrouter_client()

    model = os.getenv(
        "OPENROUTER_MODEL",
        "openrouter/free"
    )

    messages = [
        {
            "role": "system",
            "content": SYSTEM_INSTRUCTION
        },
        {
            "role": "user",
            "content": user_message
        }
    ]

    response = client.chat.completions.create(
        model=model,
        messages=messages,
        tools=tools.get_openai_tools(),
        tool_choice="auto"
    )

    message = response.choices[0].message

    if not message.tool_calls:

        if not message.content:
            raise RuntimeError("OpenRouter returned an empty response.")

        return message.content.strip(), None

    messages.append(message)

    first_tool_name = None
    last_tool_result = None

    for tool_call in message.tool_calls:

        tool_name = tool_call.function.name

        if first_tool_name is None:
            first_tool_name = tool_name

        try:
            arguments = json.loads(
                tool_call.function.arguments or "{}"
            )
        except json.JSONDecodeError:
            arguments = {}

        last_tool_result = tools.execute_tool(
            tool_name,
            arguments
        )

        messages.append(
            {
                "role": "tool",
                "tool_call_id": tool_call.id,
                "name": tool_name,
                "content": json.dumps(last_tool_result)
            }
        )

    final_response = client.chat.completions.create(
        model=model,
        messages=messages
    )

    final_message = final_response.choices[0].message

    if not final_message.content:
        return (
            _format_tool_response(
                first_tool_name,
                last_tool_result
            ),
            first_tool_name
        )

    return final_message.content.strip(), first_tool_name
def _fallback_response(
    user_message: str
) -> tuple[str, str | None]:

    providers = [
        ("Groq", _ask_groq),
        ("OpenRouter", _ask_openrouter),
    ]

    errors = []

    for provider_name, provider_function in providers:
        try:
            response = provider_function(user_message)

            print(
                f"[APEX] Fallback provider: {provider_name}"
            )

            return response

        except Exception as e:

            print(
                f"[APEX] {provider_name} failed: {e}"
            )
            errors.append(
                f"{provider_name}: {e}"
            )

    raise RuntimeError(
        "All fallback AI providers failed.\n"
        + "\n".join(errors)
    )

def get_ai_response(user_message: str) -> tuple[str, str | None]:
    """
    Communicates with the configured LLM API (Google Gemini) to generate
    an AI response and execute allowlisted tools if requested.

    :param user_message: The text input from the user.
    :return: A tuple of (response_text, tool_used_name_or_None).
    :raises QuotaExhaustedException: If Gemini 429 quota limit is hit.
    :raises RuntimeError: For other LLM API errors.
    """
    api_key, model_name = _get_validated_api_key_and_model()

    try:
        client = genai.Client(api_key=api_key)
        gemini_tools = tools.get_gemini_tools()

        response = client.models.generate_content(
            model=model_name,
            contents=user_message,
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_INSTRUCTION,
                tools=gemini_tools
            )
        )

        # Check if Gemini requested a function call
        if response.function_calls and len(response.function_calls) > 0:
            call = response.function_calls[0]
            tool_name = call.name
            tool_args = call.args or {}

            # Execute allowlisted tool safely
            tool_result = tools.execute_tool(tool_name, tool_args)

            # Format response locally — no second API call needed
            final_text = _format_tool_response(tool_name, tool_result)
            return final_text, tool_name

        if not response.text:
            raise RuntimeError("The model returned an empty response.")

        return response.text.strip(), None

    except Exception as e:
        if _check_quota_error(e):
            print("[APEX] Gemini quota exhausted.")
            print("[APEX] Switching to fallback providers...")

            try:
                return _fallback_response(user_message)
            except Exception as fallback_error:
                raise RuntimeError(
                    "Gemini quota exhausted and all "
                    f"fallback providers failed: {fallback_error}"
                )

        raise RuntimeError(f"LLM API Error: {str(e)}")


def _transcribe_with_groq(
    audio_bytes: bytes,
    mime_type: str
) -> str:

    client = _get_groq_client()

    model = os.getenv(
        "GROQ_STT_MODEL",
        "whisper-large-v3-turbo"
    )

    filename = "apex_audio.webm"

    transcription = client.audio.transcriptions.create(
        file=(
            filename,
            audio_bytes,
            mime_type
        ),
        model=model,
        response_format="json",
        temperature=0.0
    )

    if not transcription.text:
        raise RuntimeError(
            "Groq returned an empty transcription."
        )

    return transcription.text.strip()


def transcribe_audio(
    audio_bytes: bytes,
    mime_type: str = "audio/webm"
) -> str:
    """
    Transcribes audio using Gemini first.
    If Gemini quota is exhausted, falls back to Groq Whisper.
    """

    if not audio_bytes or len(audio_bytes) == 0:
        raise ValueError("Audio data is empty.")

    clean_mime_type = (
        mime_type.split(";")[0].strip()
        if mime_type
        else "audio/webm"
    )

    # -------------------------------------------------
    # 1. Try Gemini first
    # -------------------------------------------------

    try:

        api_key, model_name = (
            _get_validated_api_key_and_model()
        )

        client = genai.Client(
            api_key=api_key
        )

        audio_part = types.Part.from_bytes(
            data=audio_bytes,
            mime_type=clean_mime_type
        )

        prompt = (
            "Transcribe the spoken audio in this recording "
            "accurately into plain text. "
            "Output ONLY the raw transcription text "
            "without commentary, intro, or quotation marks. "
            "If no speech is detected, respond with "
            "'[No speech detected]'."
        )

        response = client.models.generate_content(
            model=model_name,
            contents=[
                audio_part,
                prompt
            ]
        )

        if not response.text:
            raise RuntimeError(
                "Gemini audio transcription returned "
                "an empty result."
            )

        print("[APEX] Voice transcription provider: Gemini")

        return response.text.strip()

    # -------------------------------------------------
    # 2. Gemini quota → Groq Whisper fallback
    # -------------------------------------------------

    except Exception as e:

        if _check_quota_error(e):

            print(
                "[APEX] Gemini audio quota exhausted."
            )

            print(
                "[APEX] Switching voice transcription "
                "to Groq Whisper..."
            )

            try:

                transcription = _transcribe_with_groq(
                    audio_bytes,
                    clean_mime_type
                )

                print(
                    "[APEX] Voice transcription provider: Groq"
                )

                return transcription

            except Exception as groq_error:

                raise RuntimeError(
                    "Gemini audio quota exhausted and "
                    f"Groq transcription failed: {groq_error}"
                )

        raise RuntimeError(
            f"Audio Transcription Error: {str(e)}"
        )
