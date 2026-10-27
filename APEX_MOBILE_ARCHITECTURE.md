# APEX AI MOBILE — ARCHITECTURE & SPECIFICATION PLAN

## 1. Executive Summary & Audit of Existing PC Codebase

### Existing PC Application Analysis
- **Framework & Stack**: FastAPI (`app.py`), Vanilla ES6 JavaScript (`static/app.js`), Modern Terminal CSS3 (`static/style.css`), HTML5 (`static/index.html`).
- **AI Brain**: Gemini 3.6 Flash / OpenAI-compatible provider (`brain.py`), with multi-turn tool calling (up to 5 rounds) and bounded conversation history (20 messages max).
- **Tool System**: Allowlisted execution in `tools.py` (`calculate`, `web_search`, `open_website`, `open_app`, `get_current_time`).
- **Memory Subsystem**: Local JSON storage (`memory/user_memory.json`, `memory_manager.py`) tracking user profile, preferences, and notes.
- **Visual Identity & Theme**:
  - Deep space terminal cyberpunk aesthetic.
  - Background palette: Deep black `#05080c`, main background `#080b10`, panel `#080d13`, borders `rgba(148, 163, 184, 0.18)`.
  - Neon accents: Cyan `#00f2fe`, Blue `#4facfe`, Success Green `#10b981`, Purple `#a855f7`, Coral Pink `#fb7185`.
  - Typography: Monospace (`Space Mono`, `Courier New`).
  - Terminal frame with ASCII corners and dividers (`┌─`, `─┐`, `└─`, `─┘`, `> `).
  - Status gauges: `CORE`, `VOICE`, `MEMORY`, `TOOLS`, `AI`.
  - Interactive components: Live activity log, conversation stream, tool execution badges, collapsible panels, command bar with voice/mic button and prompt prefix `> `.

### Android Architecture Requirements
- **Native Android Stack**: Kotlin 1.9+, Android SDK 34 (Android 14, backwards compatible to API 26+), Jetpack Compose for declarative UI, Coroutines/Flow, StateFlow, AndroidX ViewModel, Jetpack CameraX, Storage Access Framework (SAF), AccessibilityService, MediaProjection API.
- **Visual Parity**: Pixel-faithful translation of the PC terminal aesthetic into Jetpack Compose styling (custom Cyberpunk Terminal theme, ASCII box decorations, neon glowing bars, monospace font families, collapsible panels, tool execution cards).

---

## 2. Platform Boundary & Request Flow

The architecture strictly enforces an independent safety pipeline where the AI model is treated as an untrusted intent proposer:

```
[User Input (Text / Voice)]
         │
         ▼
[APEX Mobile UI (Compose Screen)]
         │
         ▼
[ApexViewModel & StateFlow]
         │
         ▼
[LocalModelAdapter (Mobile Action 270M / LiteRT)]
         │
         ▼
[Structured Intent / ToolCallProposal]
         │
         ▼
[Schema Validation & Allowlist Check]
         │
         ▼
[PolicyEngine (Risk Assessment: LOW / MEDIUM / HIGH / RESTRICTED)]
         │
    ┌────┴──────────────────────────┐
    ▼                               ▼
[LOW / Pre-approved]        [HIGH / Sensitive]
    │                               │
    │                       [Confirmation Dialog]
    │                        (Recipient, message, consequences)
    │                               │
    │                        ┌──────┴──────┐
    │                        ▼             ▼
    │                   [Confirmed]    [Cancelled]
    │                        │             │
    └────────────────┬───────┘             ▼
                     ▼             [Aborted & Audited]
           [Native Tool Handler]
           (Android SDK / Intent / SAF / CameraX)
                     │
                     ▼
           [Verified ToolExecutionResult]
                     │
                     ▼
           [AuditLogger (Sanitized)]
                     │
                     ▼
           [UI State Update / TTS]
```

---

## 3. Mobile Action 270M Local Model Integration

### Model Identification & Discovery
- **Exact Model Identity**: Google FunctionGemma 270M (`litert-community/functiongemma-270m-ft-mobile-actions` / `google/functiongemma-270m-it`).
- **Base Architecture**: Gemma 3 transformer with 270 million parameters.
- **Runtime Target**: Google LiteRT (TensorFlow Lite / MediaPipe GenAI LLM Inference Engine for Android).
- **Model Packaging**: `.litertlm` container (e.g. INT8 quantized `functiongemma-270m-it-q8_ekv1024.litertlm`) or `.task` bundle.
- **Memory Footprint**: ~288 MB to 320 MB RAM in INT8 format, easily fitting within budget of mobile devices (e.g. Vivo Y58 5G with 6-8 GB RAM).
- **Execution Speed**: 80-120+ tokens/second on mobile CPU (Snapdragon 4 Gen 2 with XNNPACK) or mobile GPU (Adreno / Mali).
- **Tokenizer & Control Tokens**:
  - Gemma 3 SentencePiece tokenizer with 256,000 vocabulary.
  - Native function calling control tokens:
    - `<start_function_declaration>declaration:name{params}<end_function_declaration>`
    - `<start_function_call>call:tool_name{arg:<escape>val<escape>}<end_function_call>`
    - `<start_function_response>response:...<end_function_response>`
    - String safety enclosure token `<escape>`
- **Local Model Adapter Interface**:
  - `LocalModelAdapter`: initialize, generateResponse, parseToolCalls, unload, cancel, getHealth.
  - `LiteRTLocalModelAdapter`: Production implementation interfacing with LiteRT LLM C++/Java runtime.
  - `MockLocalModelAdapter`: Test/development mock ensuring offline compilation and unit test isolation without requiring 300MB weight downloads during build.

---

## 4. Typed Native Tool Registry & Policy Engine

### Risk Classification
- **LOW**: Device diagnostics, battery status, opening known applications, current time. Auto-executes after validation.
- **MEDIUM**: Camera capture, file picker, setting volume/brightness. Validates permissions and user context.
- **HIGH**: Sending messages, APK install requests, app uninstallation, file deletion/overwrite, audio/screen recording. Always requires modal confirmation showing target, payload, and consequences.
- **RESTRICTED**: Silent app installation, covert audio/video capture, credential theft, bypassing Play Protect. Outright rejected with safety explanation.

### Implemented Tool Inventory
1. **App Control**: `open_app`, `find_app`, `list_available_apps`, `open_app_settings`, `open_app_store`.
2. **Communication (WhatsApp)**: `open_whatsapp_chat`, `prepare_whatsapp_message`, `send_whatsapp_message`, `share_file_to_whatsapp`, `read_notifications`.
3. **Camera & Media**: `take_photo`, `record_video`, `record_audio`, `toggle_flashlight`, `get_camera_status`.
4. **App Installation & Management**: `find_apk`, `inspect_apk`, `request_app_install`, `open_install_permissions`, `request_app_uninstall`, `check_installed_app`.
5. **Files & Sharing**: `find_file`, `read_selected_file`, `create_note`, `copy_file`, `share_file`, `get_storage_summary`.
6. **Device Settings & Utilities**: `get_battery_status`, `get_device_info`, `get_location`, `open_wifi_settings`, `open_bluetooth_settings`, `set_brightness`, `set_volume`, `open_accessibility_settings`.
7. **Screen Interaction**: `get_active_app`, `read_visible_ui`, `tap_ui_element`, `type_text`, `scroll_screen`, `press_back`, `take_screenshot`.
8. **Workflow Orchestration**: `create_workflow`, `execute_workflow`, `cancel_workflow`, `get_action_status`.
