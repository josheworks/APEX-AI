import os
import wave 
import io
import grpc

from fastapi import FastAPI, Request, File, UploadFile, status
from fastapi.responses import JSONResponse, FileResponse, Response
from fastapi.staticfiles import StaticFiles
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, field_validator

import brain
import tools
from brain import QuotaExhaustedException

# NVIDIA Riva / Chatterbox TTS
import riva.client
# from riva.client.proto import riva_tts_pb2


# =========================================================
# APEX CONFIGURATION
# =========================================================

NVIDIA_API_KEY = os.getenv("NVIDIA_API_KEY")
if not NVIDIA_API_KEY:
    print("WARNING: NVIDIA_API_KEY is not set.")

NVIDIA_RIVA_URL = "grpc.nvcf.nvidia.com:443"

# NVIDIA function/model ID for Chatterbox Multilingual TTS
NVIDIA_FUNCTION_ID = "ddacc747-1269-4fab-bfd9-8f593dead106"

# Official APEX voice
APEX_TTS_VOICE = "Chatterbox-Multilingual.en-US.Male"

# Prevent excessively large TTS requests.
# Long responses can be handled by the frontend/backend in chunks later.
MAX_TTS_CHARACTERS = 500


# =========================================================
# FASTAPI APP
# =========================================================

app = FastAPI(
    title="APEX v0.1",
    description="Minimal FastAPI backend with AI Brain, Voice Input, Tools and NVIDIA Chatterbox TTS for APEX personal AI assistant",
    version="0.1.0"
)


# =========================================================
# CORS
# =========================================================

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://127.0.0.1:5500",
        "http://localhost:5500",
        "http://localhost",
        "capacitor://localhost",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"]
)


# =========================================================
# STATIC FILES
# =========================================================

if os.path.exists("static"):
    app.mount("/static", StaticFiles(directory="static"), name="static")


# =========================================================
# REQUEST MODELS
# =========================================================

class ChatRequest(BaseModel):
    message: str

    @field_validator("message")
    @classmethod
    def validate_message(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("Message cannot be empty.")
        return v.strip()


class TTSRequest(BaseModel):
    text: str

    @field_validator("text")
    @classmethod
    def validate_text(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("Text cannot be empty.")
        return v.strip()


# =========================================================
# VALIDATION ERROR HANDLER
# =========================================================

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    request: Request,
    exc: RequestValidationError
):
    """
    Custom exception handler to format validation errors
    gracefully into JSON response.
    """

    errors = exc.errors()

    msg = (
        errors[0].get("msg", "Invalid request body.")
        if errors
        else "Invalid request body."
    )

    if msg.startswith("Value error, "):
        msg = msg.replace("Value error, ", "")

    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={
            "success": False,
            "error": msg
        }
    )


# =========================================================
# ROOT
# =========================================================

@app.get("/")
def read_root(request: Request):

    accept = request.headers.get("accept", "")

    if (
        "text/html" in accept
        and os.path.exists("static/index.html")
    ):
        return FileResponse("static/index.html")

    return "APEX is online."


# =========================================================
# HEALTH
# =========================================================

@app.get("/health")
def health_check():

    return {
        "status": "ok",
        "assistant": "APEX",
        "tts": "nvidia-chatterbox"
    }


# =========================================================
# TOOLS
# =========================================================

@app.get("/tools")
def tools_endpoint():
    """
    Returns available built-in tool metadata
    and labels suitable for frontend display.
    """

    try:

        tool_defs = tools.get_openai_tools()

        labels = {}

        for entry in tool_defs:

            fn = entry.get("function") or {}

            name = fn.get("name")
            desc = fn.get("description", "")

            label = (
                desc.split(".")[0].strip()
                if desc
                else name
            )

            if name:
                labels[name] = label

        return {
            "success": True,
            "tools": tool_defs,
            "labels": labels
        }

    except Exception as e:

        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={
                "success": False,
                "error": str(e)
            }
        )


# =========================================================
# CHAT
# =========================================================

@app.post("/chat")
def chat_endpoint(request: ChatRequest):
    """
    Text Chat endpoint receiving user message,
    triggering tool execution if needed,
    and returning APEX response.
    """

    try:

        (
            ai_response,
            tool_used,
            tools_used,
            tool_events
        ) = brain.get_ai_response_with_metadata(request.message)

        res = {
            "success": True,
            "response": ai_response
        }

        if tool_used:
            res["tool_used"] = tool_used

        if tools_used:
            res["tools_used"] = tools_used

        if tool_events:
            res["tool_events"] = tool_events

        return res

    except QuotaExhaustedException:

        return JSONResponse(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            content={
                "success": False,
                "error_type": "QUOTA_EXHAUSTED",
                "error": (
                    "APEX's AI quota has been exhausted. "
                    "Built-in tools are still available!"
                )
            }
        )

    except Exception as e:

        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={
                "success": False,
                "error": str(e)
            }
        )


# =========================================================
# VOICE INPUT
# =========================================================

@app.post("/voice")
async def voice_endpoint(file: UploadFile = File(...)):
    """
    Voice endpoint accepting browser-recorded audio file,
    transcribing via Gemini Multimodal Audio,
    executing tools if needed,
    and generating APEX response.
    """

    if not file:

        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={
                "success": False,
                "error": "No audio file provided."
            }
        )

    try:

        audio_bytes = await file.read()

        if not audio_bytes or len(audio_bytes) == 0:

            return JSONResponse(
                status_code=status.HTTP_400_BAD_REQUEST,
                content={
                    "success": False,
                    "error": "Uploaded audio file is empty."
                }
            )

        mime_type = file.content_type or "audio/webm"


        # -------------------------------------------------
        # 1. TRANSCRIBE USER AUDIO
        # -------------------------------------------------

        transcription = brain.transcribe_audio(
            audio_bytes,
            mime_type=mime_type
        )


        # -------------------------------------------------
        # 2. CHECK FOR SPEECH
        # -------------------------------------------------

        if (
            not transcription
            or transcription.strip() == ""
            or transcription.strip() == "[No speech detected]"
        ):

            return JSONResponse(
                status_code=status.HTTP_400_BAD_REQUEST,
                content={
                    "success": False,
                    "error": "No speech detected in recorded audio."
                }
            )


        # -------------------------------------------------
        # 3. SEND TO APEX BRAIN
        # -------------------------------------------------

        (
            ai_response,
            tool_used,
            tools_used,
            tool_events
        ) = brain.get_ai_response_with_metadata(
            transcription
        )


        # -------------------------------------------------
        # 4. RETURN RESPONSE
        # -------------------------------------------------

        res = {
            "success": True,
            "transcription": transcription,
            "response": ai_response
        }

        if tool_used:
            res["tool_used"] = tool_used

        if tools_used:
            res["tools_used"] = tools_used

        if tool_events:
            res["tool_events"] = tool_events

        return res


    except QuotaExhaustedException:

        return JSONResponse(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            content={
                "success": False,
                "error_type": "QUOTA_EXHAUSTED",
                "error": (
                    "APEX's AI quota has been exhausted. "
                    "Built-in tools are still available!"
                )
            }
        )

    except Exception as e:

        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={
                "success": False,
                "error": str(e)
            }
        )


# =========================================================
# NVIDIA CHATTERBOX TTS
# =========================================================

@app.post("/tts")
async def text_to_speech(request: TTSRequest):

    try:

        text = request.text.strip()

        if not text:
            return JSONResponse(
                status_code=status.HTTP_400_BAD_REQUEST,
                content={
                    "success": False,
                    "error": "Text cannot be empty."
                }
            )

        if len(text) > MAX_TTS_CHARACTERS:
            text = text[:MAX_TTS_CHARACTERS]

        # Create NVIDIA Riva authentication.
        auth = riva.client.Auth(
            uri=NVIDIA_RIVA_URL,
            use_ssl=True,
            metadata_args=[
                ("function-id", NVIDIA_FUNCTION_ID),
                ("authorization", f"Bearer {NVIDIA_API_KEY}")
            ]
        )

        # Create TTS service.
        tts_service = riva.client.SpeechSynthesisService(auth)

        # Generate speech.
        response = tts_service.synthesize(
            text=text,
            voice_name=APEX_TTS_VOICE,
            language_code="en-US",
            sample_rate_hz=24000,
            encoding=riva.client.AudioEncoding.LINEAR_PCM
        )

        # Convert raw PCM audio into WAV.
        wav_buffer = io.BytesIO()

        with wave.open(wav_buffer, "wb") as wav_file:
            wav_file.setnchannels(1)
            wav_file.setsampwidth(2)
            wav_file.setframerate(24000)
            wav_file.writeframes(response.audio)

        wav_bytes = wav_buffer.getvalue()

        return Response(
            content=wav_bytes,
            media_type="audio/wav",
            headers={
                "Content-Disposition":
                    "inline; filename=apex_voice.wav"
            }
        )

    except grpc.RpcError as e:

        print(
            f"NVIDIA TTS gRPC error: {e.details()}"
        )

        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content={
                "success": False,
                "error": (
                    f"NVIDIA TTS service error: "
                    f"{e.details()}"
                )
            }
        )

    except Exception as e:

        print(f"APEX TTS error: {e}")

        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={
                "success": False,
                "error": f"APEX TTS failed: {str(e)}"
            }
        )