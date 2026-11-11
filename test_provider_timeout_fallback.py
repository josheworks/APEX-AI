"""
Unit tests for APEX AI 2-Second Provider Timeout and Automatic Fallback.

Tests 10 key scenarios:
1. OpenRouter success -> Groq/Gemini must not execute.
2. OpenRouter timeout -> Groq executes next and succeeds.
3. Groq timeout -> Gemini executes.
4. All providers fail -> Clear RuntimeError returned.
5. Timeout configuration -> OpenRouter 2s, Groq 2s, Gemini 2000ms.
6. No unnecessary retries -> OpenAI-compatible clients use max_retries=0 for chat.
7. Voice protection -> Transcription clients do not inherit 2s chat timeout.
8. Tool execution -> Existing tool calls and tool results work.
9. Conversation history -> Not duplicated when fallback occurs.
10. Model configuration -> Groq LLM model and Groq Whisper model remain separate.
"""

import json
from unittest.mock import MagicMock, patch
import pytest

import brain
import tools


def test_1_openrouter_success(monkeypatch):
    """
    Test 1 — OpenRouter success:
    OpenRouter responds successfully. Groq and Gemini must not execute.
    """
    mock_openrouter = MagicMock(return_value={
        "text": "OpenRouter response",
        "tool_used": None,
        "tools_used": [],
        "tool_events": []
    })
    mock_groq = MagicMock()
    mock_gemini = MagicMock()

    monkeypatch.setattr(brain, "_ask_openrouter", mock_openrouter)
    monkeypatch.setattr(brain, "_ask_groq", mock_groq)
    monkeypatch.setattr(brain, "_ask_gemini", mock_gemini)

    result = brain._fallback_chat_response("Hello")
    assert result["text"] == "OpenRouter response"
    mock_openrouter.assert_called_once_with("Hello")
    mock_groq.assert_not_called()
    mock_gemini.assert_not_called()


def test_2_openrouter_timeout_fallback_to_groq(monkeypatch, capsys):
    """
    Test 2 — OpenRouter timeout:
    Simulate OpenRouter timeout. Groq must execute next. Verify Groq success is returned.
    """
    mock_openrouter = MagicMock(side_effect=TimeoutError("Request timed out"))
    mock_groq = MagicMock(return_value={
        "text": "Groq response",
        "tool_used": None,
        "tools_used": [],
        "tool_events": []
    })
    mock_gemini = MagicMock()

    monkeypatch.setattr(brain, "_ask_openrouter", mock_openrouter)
    monkeypatch.setattr(brain, "_ask_groq", mock_groq)
    monkeypatch.setattr(brain, "_ask_gemini", mock_gemini)

    result = brain._fallback_chat_response("Hello")
    assert result["text"] == "Groq response"
    mock_openrouter.assert_called_once_with("Hello")
    mock_groq.assert_called_once_with("Hello")
    mock_gemini.assert_not_called()

    captured = capsys.readouterr().out
    assert "[APEX] Trying chat provider: OpenRouter" in captured
    assert f"[APEX] OpenRouter timed out after {brain.CHAT_PROVIDER_TIMEOUT_SECONDS}s" in captured
    assert "[APEX] Trying chat provider: Groq" in captured
    assert "[APEX] Chat provider succeeded: Groq" in captured


def test_3_groq_timeout_fallback_to_gemini(monkeypatch, capsys):
    """
    Test 3 — Groq timeout:
    Simulate OpenRouter failure. Simulate Groq timeout. Verify Gemini executes.
    """
    mock_openrouter = MagicMock(side_effect=Exception("OpenRouter 500 internal error"))
    mock_groq = MagicMock(side_effect=TimeoutError("Groq request timed out"))
    mock_gemini = MagicMock(return_value={
        "text": "Gemini response",
        "tool_used": None,
        "tools_used": [],
        "tool_events": []
    })

    monkeypatch.setattr(brain, "_ask_openrouter", mock_openrouter)
    monkeypatch.setattr(brain, "_ask_groq", mock_groq)
    monkeypatch.setattr(brain, "_ask_gemini", mock_gemini)

    result = brain._fallback_chat_response("Hello")
    assert result["text"] == "Gemini response"
    mock_openrouter.assert_called_once_with("Hello")
    mock_groq.assert_called_once_with("Hello")
    mock_gemini.assert_called_once_with("Hello")

    captured = capsys.readouterr().out
    assert "[APEX] OpenRouter failed: OpenRouter 500 internal error" in captured
    assert f"[APEX] Groq timed out after {brain.CHAT_PROVIDER_TIMEOUT_SECONDS}s" in captured
    assert "[APEX] Chat provider succeeded: Gemini" in captured


def test_4_all_providers_fail(monkeypatch):
    """
    Test 4 — All providers fail:
    Simulate failures from all three providers. Verify a clear RuntimeError is returned.
    """
    mock_openrouter = MagicMock(side_effect=TimeoutError("OpenRouter timeout"))
    mock_groq = MagicMock(side_effect=TimeoutError("Groq timeout"))
    mock_gemini = MagicMock(side_effect=Exception("Gemini quota exhausted"))

    monkeypatch.setattr(brain, "_ask_openrouter", mock_openrouter)
    monkeypatch.setattr(brain, "_ask_groq", mock_groq)
    monkeypatch.setattr(brain, "_ask_gemini", mock_gemini)

    with pytest.raises(RuntimeError) as exc_info:
        brain._fallback_chat_response("Hello")

    err_msg = str(exc_info.value)
    assert "All chat providers failed." in err_msg
    assert "OpenRouter: OpenRouter timeout" in err_msg
    assert "Groq: Groq timeout" in err_msg
    assert "Gemini: Gemini quota exhausted" in err_msg


def test_5_timeout_configuration(monkeypatch):
    """
    Test 5 — Timeout configuration:
    Verify OpenRouter receives a 2-second timeout.
    Verify Groq receives a 2-second timeout.
    Verify Gemini receives a 2000-millisecond timeout.
    """
    assert brain.CHAT_PROVIDER_TIMEOUT_SECONDS == 2.0

    # 1. OpenRouter chat client receives 2.0s timeout
    with patch("brain.OpenAI") as mock_openai, \
         patch("brain._run_openai_compatible_tool_loop", return_value={"text": "ok"}):
        monkeypatch.setenv("OPENROUTER_API_KEY", "test-openrouter-key")
        brain._ask_openrouter("test")
        mock_openai.assert_called_once_with(
            api_key="test-openrouter-key",
            base_url="https://openrouter.ai/api/v1",
            timeout=2.0,
            max_retries=0
        )

    # 2. Groq chat client receives 2.0s timeout
    with patch("brain.OpenAI") as mock_openai, \
         patch("brain._run_openai_compatible_tool_loop", return_value={"text": "ok"}):
        monkeypatch.setenv("GROQ_API_KEY", "test-groq-key")
        brain._ask_groq("test")
        mock_openai.assert_called_once_with(
            api_key="test-groq-key",
            base_url="https://api.groq.com/openai/v1",
            timeout=2.0,
            max_retries=0
        )

    # 3. Gemini chat client receives 2000ms timeout
    with patch("brain.genai.Client") as mock_genai_client, \
         patch("brain._run_gemini_tool_loop", return_value={"text": "ok"}):
        monkeypatch.setenv("GEMINI_API_KEY", "test-gemini-key")
        brain._ask_gemini("test")
        mock_genai_client.assert_called_once()
        _, kwargs = mock_genai_client.call_args
        assert kwargs["api_key"] == "test-gemini-key"
        assert "http_options" in kwargs
        assert kwargs["http_options"].timeout == 2000


def test_6_no_unnecessary_retries(monkeypatch):
    """
    Test 6 — No unnecessary retries:
    Verify OpenAI-compatible clients use max_retries=0 for chat requests.
    """
    monkeypatch.setenv("OPENROUTER_API_KEY", "test-key")
    monkeypatch.setenv("GROQ_API_KEY", "test-key")

    with patch("brain.OpenAI") as mock_openai:
        brain._get_openrouter_client(timeout=2.0)
        _, kwargs = mock_openai.call_args
        assert kwargs.get("max_retries") == 0
        assert kwargs.get("timeout") == 2.0

    with patch("brain.OpenAI") as mock_openai:
        brain._get_groq_client(timeout=2.0)
        _, kwargs = mock_openai.call_args
        assert kwargs.get("max_retries") == 0
        assert kwargs.get("timeout") == 2.0


def test_7_voice_protection(monkeypatch):
    """
    Test 7 — Voice protection:
    Verify Whisper transcription clients do not inherit the 2-second chat timeout.
    """
    monkeypatch.setenv("OPENROUTER_API_KEY", "test-openrouter-key")
    monkeypatch.setenv("GROQ_API_KEY", "test-groq-key")
    monkeypatch.setenv("GEMINI_API_KEY", "test-gemini-key")

    # 1. OpenRouter transcription client: no timeout or max_retries override
    with patch("brain.OpenAI") as mock_openai:
        mock_instance = MagicMock()
        mock_openai.return_value = mock_instance
        mock_transcription = MagicMock()
        mock_transcription.text = "Transcribed text"
        mock_instance.audio.transcriptions.create.return_value = mock_transcription

        res = brain._transcribe_with_openrouter(b"fake-audio-bytes", "audio/webm")
        assert res == "Transcribed text"
        mock_openai.assert_called_once_with(
            api_key="test-openrouter-key",
            base_url="https://openrouter.ai/api/v1"
        )
        _, kwargs = mock_openai.call_args
        assert "timeout" not in kwargs
        assert "max_retries" not in kwargs

    # 2. Groq Whisper transcription client: no timeout or max_retries override
    with patch("brain.OpenAI") as mock_openai:
        mock_instance = MagicMock()
        mock_openai.return_value = mock_instance
        mock_transcription = MagicMock()
        mock_transcription.text = "Groq transcribed text"
        mock_instance.audio.transcriptions.create.return_value = mock_transcription

        res = brain._transcribe_with_groq(b"fake-audio-bytes", "audio/webm")
        assert res == "Groq transcribed text"
        mock_openai.assert_called_once_with(
            api_key="test-groq-key",
            base_url="https://api.groq.com/openai/v1"
        )
        _, kwargs = mock_openai.call_args
        assert "timeout" not in kwargs
        assert "max_retries" not in kwargs

    # 3. Gemini audio transcription client: no http_options timeout override
    with patch("brain.genai.Client") as mock_genai:
        mock_instance = MagicMock()
        mock_genai.return_value = mock_instance
        mock_resp = MagicMock()
        mock_resp.text = "Gemini audio transcribed text"
        mock_instance.models.generate_content.return_value = mock_resp

        res = brain._transcribe_with_gemini(b"fake-audio-bytes", "audio/webm")
        assert res == "Gemini audio transcribed text"
        mock_genai.assert_called_once_with(
            api_key="test-gemini-key"
        )
        _, kwargs = mock_genai.call_args
        assert "http_options" not in kwargs


def test_8_tool_execution():
    """
    Test 8 — Tool execution:
    Verify existing tool calls and tool results still work in tool loop.
    """
    mock_client = MagicMock()

    class FunctionObj:
        def __init__(self):
            self.name = "calculate"
            self.arguments = json.dumps({"expression": "7 * 6"})

    class OpenAIToolCall:
        def __init__(self):
            self.id = "call_calc_1"
            self.function = FunctionObj()

    # Step 1: Model returns a tool call for calculation
    call_msg = MagicMock()
    call_msg.tool_calls = [OpenAIToolCall()]
    call_msg.content = None

    choice_1 = MagicMock()
    choice_1.message = call_msg
    resp_1 = MagicMock()
    resp_1.choices = [choice_1]

    # Step 2: Model returns final response after receiving tool output
    resp_msg = MagicMock()
    resp_msg.tool_calls = None
    resp_msg.content = "7 times 6 is 42."
    choice_2 = MagicMock()
    choice_2.message = resp_msg
    resp_2 = MagicMock()
    resp_2.choices = [choice_2]

    mock_client.chat.completions.create.side_effect = [resp_1, resp_2]

    result = brain._run_openai_compatible_tool_loop(
        mock_client,
        "openai/gpt-oss-120b",
        "What is 7 * 6?"
    )

    assert result["text"] == "7 times 6 is 42."
    assert result["tool_used"] == "calculate"
    assert "calculate" in result["tools_used"]
    assert len(result["tool_events"]) == 1
    assert result["tool_events"][0]["tool"] == "calculate"
    assert result["tool_events"][0]["success"] is True


def test_9_conversation_history_not_duplicated_on_fallback(monkeypatch):
    """
    Test 9 — Conversation history:
    Verify conversation history is not duplicated when fallback occurs.
    """
    with brain._conversation_lock:
        brain._conversation_history.clear()

    mock_openrouter = MagicMock(side_effect=TimeoutError("OpenRouter timeout"))
    mock_groq = MagicMock(return_value={
        "text": "Groq successful response",
        "tool_used": None,
        "tools_used": [],
        "tool_events": []
    })
    mock_gemini = MagicMock()

    monkeypatch.setattr(brain, "_ask_openrouter", mock_openrouter)
    monkeypatch.setattr(brain, "_ask_groq", mock_groq)
    monkeypatch.setattr(brain, "_ask_gemini", mock_gemini)

    final_text, tool_used, tools_used, tool_events = brain.get_ai_response_with_metadata(
        "Tell me a joke"
    )

    assert final_text == "Groq successful response"

    with brain._conversation_lock:
        history = list(brain._conversation_history)

    # Exactly 1 user message and 1 assistant message
    assert len(history) == 2
    assert history[0] == {"role": "user", "content": "Tell me a joke"}
    assert history[1] == {"role": "assistant", "content": "Groq successful response"}


def test_10_model_configuration_separation(monkeypatch):
    """
    Test 10 — Model configuration:
    Verify the Groq LLM model and Groq Whisper model remain separate.
    """
    monkeypatch.setenv("GROQ_API_KEY", "test-groq-key")
    monkeypatch.delenv("GROQ_LLM_MODEL", raising=False)
    monkeypatch.delenv("GROQ_STT_MODEL", raising=False)

    captured_chat_model = None
    def mock_run_tool_loop(client, model, user_msg):
        nonlocal captured_chat_model
        captured_chat_model = model
        return {
            "text": "chat response",
            "tool_used": None,
            "tools_used": [],
            "tool_events": []
        }

    monkeypatch.setattr(brain, "_run_openai_compatible_tool_loop", mock_run_tool_loop)

    # 1. Chat model uses GROQ_LLM_MODEL default (openai/gpt-oss-120b)
    with patch("brain._get_groq_client"):
        brain._ask_groq("test chat")
    assert captured_chat_model == "openai/gpt-oss-120b"
    assert "whisper" not in captured_chat_model.lower()

    # 2. Transcription model uses GROQ_STT_MODEL default (whisper-large-v3-turbo)
    with patch("brain._get_groq_client") as mock_get_client:
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client
        mock_transcription = MagicMock()
        mock_transcription.text = "transcribed speech"
        mock_client.audio.transcriptions.create.return_value = mock_transcription

        brain._transcribe_with_groq(b"audio", "audio/webm")
        _, kwargs = mock_client.audio.transcriptions.create.call_args
        assert kwargs["model"] == "whisper-large-v3-turbo"


def test_positive_timeout_validation(monkeypatch):
    """
    Verify that non-positive CHAT_PROVIDER_TIMEOUT_SECONDS values are rejected with ValueError.
    """
    import importlib
    monkeypatch.setenv("CHAT_PROVIDER_TIMEOUT_SECONDS", "0")
    with pytest.raises(ValueError, match="CHAT_PROVIDER_TIMEOUT_SECONDS must be positive"):
        importlib.reload(brain)

    monkeypatch.setenv("CHAT_PROVIDER_TIMEOUT_SECONDS", "-1.5")
    with pytest.raises(ValueError, match="CHAT_PROVIDER_TIMEOUT_SECONDS must be positive"):
        importlib.reload(brain)

    # Restore valid value
    monkeypatch.setenv("CHAT_PROVIDER_TIMEOUT_SECONDS", "2")
    importlib.reload(brain)
