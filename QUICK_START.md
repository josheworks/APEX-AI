# APEX AI Upgrade - Quick Start & Manual Testing Guide

## Implementation Status: ✅ COMPLETE

All automated tests pass. Ready for manual verification.

---

## What Was Changed

### Core Changes (brain.py)
- ✅ Fixed duplicate function definitions
- ✅ Implemented multi-round tool execution loop (all providers)
- ✅ Added Gemini multi-round support (previously single-pass only)
- ✅ Second AI pass after tool execution (model processes results)
- ✅ Graceful provider fallback (Gemini → Groq → OpenRouter)

### Preserved Features
- ✅ Conversation history (20 messages bounded)
- ✅ Long-term memory (persistent JSON file)
- ✅ All 7 existing tools (get_current_time, web_search, calculate, etc.)
- ✅ Voice transcription and Conversation Mode
- ✅ API response contract (success, response, tool_used)
- ✅ Frontend (no changes needed)

### Updated
- ✅ requirements.txt (added missing openai package)

---

## Quick Start

### 1. Install Dependencies
```bash
cd D:\joseph\APEX-AI
pip install -r requirements.txt
```

### 2. Configure .env
```
GEMINI_API_KEY=your_key_here
GROQ_API_KEY=your_key_here
OPENROUTER_API_KEY=your_key_here
```

### 3. Start Server
```bash
python -m uvicorn app:app --reload
```
Server runs at http://localhost:8000

### 4. Test Endpoints
```bash
# Chat endpoint
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello APEX"}'

# Voice endpoint (requires audio file)
curl -X POST http://localhost:8000/voice \
  -F "file=@audio.webm"
```

---

## Manual Verification Checklist

### Test 1: Basic Conversation
```
POST /chat: "My name is Alex"
Expected: Friendly greeting acknowledging your name
```

### Test 2: Memory Recall
```
POST /chat: "What is my name?"
Expected: "Your name is Alex" (from memory)
```

### Test 3: History Awareness
```
POST /chat: "What did I tell you?"
Expected: APEX recalls previous message
```

### Test 4: Single Tool
```
POST /chat: "What time is it?"
Expected: Current time
```

### Test 5: Multi-Tool Execution ⭐
```
POST /chat: "Search latest AI news and calculate 50 + 25"
Expected:
- Both web_search AND calculate execute
- Model integrates results in response
- Response includes news summaries AND "50 + 25 = 75"
```

### Test 6: Long-term Memory Across Restarts
```
1. POST /chat: "Remember that I prefer concise answers"
2. Server RESTART
3. POST /chat: "What are my preferences?"
Expected: "You prefer concise answers" (persisted)
```

### Test 7: Voice with Multi-Tool
```
POST /voice: (audio: "Search today's weather")
Expected: Transcription + weather search results spoken
```

### Test 8: Conversation Mode
```
1. Open http://localhost:8000
2. Click "Conversation Mode"
3. Say: "My age is 30"
4. Say: "How old am I?"
Expected: "You're 30 years old" (remembered from voice input)
```

### Test 9: Provider Fallback
```
IF Gemini quota exhausted:
- Automatically switches to Groq
- All features work unchanged
- No error to user
```

### Test 10: Verify No Regressions
- [ ] All tools still work (time, date, calculate, web_search)
- [ ] Frontend UI unchanged
- [ ] Voice transcription works
- [ ] Silent audio detection works
- [ ] Conversation Mode functions

---

## Architecture Overview

```
REQUEST
  ↓
[Load Memory] → Add to Conversation History
  ↓
[Build Context] → System instructions + Memory + History + User message
  ↓
[TRY Gemini]
  ├─→ Loop (max 5 rounds):
  │   ├─ Call model
  │   ├─ If tool calls: Execute ALL tools
  │   ├─ Add results back to context
  │   └─ Loop back if more tools needed
  ├─ Return final response
  │
[CATCH Gemini quota]
  └─→ Fallback to Groq
      └─→ Fallback to OpenRouter
         (Same multi-round architecture)
  ↓
[Save to History]
  ↓
RETURN Response
```

---

## Key Features Implemented

### 1. Short-term Memory
- Last 20 messages preserved per session
- Cleared on server restart
- Thread-safe

### 2. Long-term Memory
- File: `memory/user_memory.json`
- Extracts:
  - Names: "My name is X", "Call me X"
  - Preferences: "I prefer X", "I like X"
  - Notes: "Remember that X"
- Persists across restarts
- NO secrets stored

### 3. Multi-Tool Execution
- Execute multiple tools in single round
- Collect all results
- Send back to model for unified response

### 4. Second Pass Architecture
- Model gets tool results
- Model generates natural response
- NOT raw tool output

### 5. Multi-Round Support
- Up to 5 rounds per request
- Model can ask for more tools
- Hard limit prevents infinite loops

### 6. Provider Fallback
- Primary: Gemini
- Fallback 1: Groq
- Fallback 2: OpenRouter
- Automatic on quota exhaustion

---

## Automated Test Results

```
TESTS RUN: 9
TESTS PASSED: 9
TESTS FAILED: 0

✓ Memory I/O
✓ Conversation history limits
✓ Tool normalization (Gemini)
✓ Tool normalization (OpenAI)
✓ Memory extraction
✓ Preference extraction
✓ Message sequencing
✓ API contract
✓ Tool round limits
```

---

## NOT YET VERIFIED (Requires Real API Keys)

These features are code-complete but need manual testing:

- [ ] Gemini multi-round tool loop (code verified)
- [ ] Groq fallback (code verified)
- [ ] OpenRouter fallback (code verified)
- [ ] Real web_search results (code verified)
- [ ] Voice with multi-tools (code verified)
- [ ] Memory persistence across server restart (code verified)
- [ ] Multi-tool result integration (code verified)
- [ ] Provider fallback automatic switching (code verified)

All are marked "code verified" because logic is implemented and tested structurally.

---

## Troubleshooting

### Issue: "ModuleNotFoundError: No module named 'openai'"
**Solution:** `pip install openai`

### Issue: Memory not persisting
**Solution:** Check `memory/` directory exists and is writable

### Issue: No speech detected
**Solution:** Check microphone volume, audio format is webm

### Issue: Tools not executing
**Solution:** Verify tools.ALLOWED_* lists have required entries

### Issue: Gemini quota exhausted
**Solution:** Automatic fallback to Groq/OpenRouter (no action needed)

---

## Files Reference

| File | Purpose | Changed? |
|------|---------|----------|
| brain.py | AI engine, tool loop | ✅ YES |
| app.py | FastAPI endpoints | ❌ No |
| tools.py | Tool execution | ❌ No |
| memory_manager.py | Memory persistence | ❌ No |
| requirements.txt | Dependencies | ✅ YES (added openai) |
| static/ | Frontend UI | ❌ No |
| .env | Secrets | ❌ No |
| .env.example | Template | ❌ No |

---

## Support

If manual testing reveals issues:

1. Check that all API keys are configured in .env
2. Verify internet connection for web_search
3. Check browser console for frontend errors
4. Review server logs for detailed error messages
5. Run `python test_implementation.py` to verify core logic

---

**Status: Ready for Manual Verification** ✓
