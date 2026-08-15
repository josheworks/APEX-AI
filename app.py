import os
from fastapi import FastAPI, Request, File, UploadFile, status
from fastapi.responses import JSONResponse, FileResponse
from fastapi.staticfiles import StaticFiles
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, field_validator
import brain
from brain import QuotaExhaustedException

app = FastAPI(
    title="APEX v0.1",
    description="Minimal FastAPI backend with AI Brain, Voice Input & Tools for APEX personal AI assistant",
    version="0.1.0"
)

# Development-friendly CORS: allow Live Server default origin(s)
# This is intentionally permissive for local dev only.
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://127.0.0.1:5500",
        "http://localhost:5500"
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"]
)

# Mount static files directory
if os.path.exists("static"):
    app.mount("/static", StaticFiles(directory="static"), name="static")

class ChatRequest(BaseModel):
    message: str

    @field_validator("message")
    @classmethod
    def validate_message(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("Message cannot be empty.")
        return v.strip()

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    """
    Custom exception handler to format validation errors gracefully into JSON response.
    """
    errors = exc.errors()
    msg = errors[0].get("msg", "Invalid request body.") if errors else "Invalid request body."
    if msg.startswith("Value error, "):
        msg = msg.replace("Value error, ", "")
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={
            "success": False,
            "error": msg
        }
    )

@app.get("/")
def read_root(request: Request):
    accept = request.headers.get("accept", "")
    if "text/html" in accept and os.path.exists("static/index.html"):
        return FileResponse("static/index.html")
    return "APEX is online."

@app.get("/health")
def health_check():
    return {
        "status": "ok",
        "assistant": "APEX"
    }

@app.post("/chat")
def chat_endpoint(request: ChatRequest):
    """
    Text Chat endpoint receiving user message, triggering tool execution if needed,
    and returning APEX response.
    """
    try:
        ai_response, tool_used = brain.get_ai_response(request.message)
        res = {
            "success": True,
            "response": ai_response
        }
        if tool_used:
            res["tool_used"] = tool_used
        return res
    except QuotaExhaustedException:
        return JSONResponse(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            content={
                "success": False,
                "error_type": "QUOTA_EXHAUSTED",
                "error": "APEX's AI quota has been exhausted. Built-in tools are still available!"
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

@app.post("/voice")
async def voice_endpoint(file: UploadFile = File(...)):
    """
    Voice endpoint accepting browser-recorded audio file, transcribing via Gemini Multimodal Audio,
    executing tools if needed, and generating APEX response.
    """
    if not file:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": "No audio file provided."}
        )

    try:
        audio_bytes = await file.read()
        if not audio_bytes or len(audio_bytes) == 0:
            return JSONResponse(
                status_code=status.HTTP_400_BAD_REQUEST,
                content={"success": False, "error": "Uploaded audio file is empty."}
            )

        mime_type = file.content_type or "audio/webm"

        # 1. Transcribe audio using Gemini Multimodal Audio
        transcription = brain.transcribe_audio(audio_bytes, mime_type=mime_type)

        if not transcription or transcription.strip() == "" or transcription.strip() == "[No speech detected]":
            return JSONResponse(
                status_code=status.HTTP_400_BAD_REQUEST,
                content={"success": False, "error": "No speech detected in recorded audio."}
            )

        # 2. Pass transcribed text through existing APEX brain (with tools support)
        ai_response, tool_used = brain.get_ai_response(transcription)

        res = {
            "success": True,
            "transcription": transcription,
            "response": ai_response
        }
        if tool_used:
            res["tool_used"] = tool_used
        return res

    except QuotaExhaustedException:
        return JSONResponse(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            content={
                "success": False,
                "error_type": "QUOTA_EXHAUSTED",
                "error": "APEX's AI quota has been exhausted. Built-in tools are still available!"
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
