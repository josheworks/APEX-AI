# APEX AI MOBILE — ENGINEERING SPECIFICATION & SYSTEM GUIDE

> **APEX AI (Advanced Personal Executive) — Android Edition**  
> Native Android architecture powered by local on-device **Mobile Action 270M (Google FunctionGemma 270M)**, Jetpack Compose, an independent policy engine, and a strongly typed tool registry.

---

## 1. Mobile Architecture Overview

APEX Mobile translates the desktop cyberpunk terminal interface into a native Android application built with Kotlin and Jetpack Compose.

### Core Architecture Pipeline

```text
[User Voice / Text Input]
            │
            ▼
   [APEX Mobile UI] ── (Jetpack Compose Cyberpunk Terminal)
            │
            ▼
    [ApexViewModel]
            │
            ▼
  [LocalModelAdapter] ── (FunctionGemma 270M via LiteRT / MediaPipe GenAI)
            │
            ▼
  [Structured Proposal] ── (<start_function_call>call:tool_name{args}<end_function_call>)
            │
            ▼
   [Schema Validator] ── (Type checking, range validation, input sanitization)
            │
            ▼
    [Policy Engine] ── (Risk levels: LOW, MEDIUM, HIGH, RESTRICTED)
            │
      ┌─────┴─────────────────────────┐
      ▼                               ▼
[LOW / Pre-approved]          [HIGH / Sensitive]
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
             (Intent / SAF / CameraX / Accessibility)
                       │
                       ▼
             [Verified Result]
                       │
                       ▼
            [Audit Logger & Feed] ── (Sanitized, zero secret leakage)
                       │
                       ▼
             [Spoken / Chat Output]
```

### Key Safety Guarantees
- **Independent Safety Pipeline:** The AI model is strictly an untrusted intent proposer. The model cannot execute code directly or alter its own risk classification.
- **Zero Cloud Leakage:** All inference runs offline on the device processor using LiteRT. Private conversations and device data never leave the phone.
- **Immutable Confirmation Binding:** High-risk actions generate a cryptographically bound hash of `toolName + arguments`. If any parameter is mutated (e.g., recipient changed), the confirmation token is immediately invalidated.

---

## 2. Model Discovery & Artifact Specifications

### Model Identity
- **Identifier:** `google/functiongemma-270m-it` or `litert-community/functiongemma-270m-ft-mobile-actions`
- **Architecture:** Gemma 3 Transformer (270 million parameters)
- **Primary Runtime:** Google LiteRT (formerly TensorFlow Lite) / MediaPipe GenAI LLM Inference API for Android
- **Quantization & Format:** INT8 quantized LiteRT Language Model (`.litertlm` / `.task`)
  - Target filename: `functiongemma-270m-it-q8_ekv1024.litertlm`
- **Memory Footprint:** ~288 MB to 320 MB RAM in INT8 format (runs comfortably on phones like Vivo Y58 5G with Snapdragon 4 Gen 2 / 6-8GB RAM)
- **Token Limits:** Max context 1024–2048 tokens; small output budget of 128–256 tokens for rapid function calling
- **Inference Speed:** 80–120+ tokens/second on mobile CPU (XNNPACK delegate) or mobile GPU (OpenCL/Adreno)

### Prompt Structure & Special Tokens
FunctionGemma uses native control tokens for tool declaration and invocation:
- `<start_function_declaration>declaration:name{params}<end_function_declaration>`
- `<start_function_call>call:tool_name{param:<escape>val<escape>}<end_function_call>`
- `<start_function_response>response:...<end_function_response>`
- String enclosure token: `<escape>`

---

## 3. How to Build & Run the Android Application

### Prerequisites
- Android Studio Iguana (2023.2.1+) or newer
- JDK 17 (Java 17)
- Android SDK Platform 34 (Android 14)
- Physical Android phone (e.g. Vivo Y58 5G) or Android Emulator running API 26+

### Project Location
The Android project is situated in `android/`:
```bash
cd android
```

### Build Commands
Using the Gradle wrapper:

```bash
# Clean and compile debug build
./gradlew assembleDebug

# Run automated unit test suite
./gradlew testDebugUnitTest

# Install directly to a connected device
./gradlew installDebug
```

On Windows Command Prompt or PowerShell:
```cmd
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
gradlew.bat installDebug
```

The resulting debug APK is generated at:
`android/app/build/outputs/apk/debug/app-debug.apk`

---

## 4. Local Model Configuration & Loading

1. Download the quantized FunctionGemma 270M `.litertlm` model artifact from Hugging Face or Google AI Edge:
   ```bash
   # Example: litert-community/functiongemma-270m-ft-mobile-actions
   ```
2. Place the model file into the Android device's app storage:
   `/data/data/com.apex.ai.mobile/files/models/functiongemma-270m-it-q8_ekv1024.litertlm`
   or inside `android/app/src/main/assets/models/`.
3. In `ApexApplication.kt`, select `LiteRTLocalModelAdapter(context)` for real on-device hardware inference, or `MockLocalModelAdapter()` for offline builds and hermetic tests.

---

## 5. Tool Registry Inventory & Capability Status

| Tool Name | Category | Risk Level | Confirmation Policy | Implementation Status | Notes / Limitations |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `open_app` | App Control | `LOW` | `NEVER` | **Implemented** | Uses explicit package queries and launch intents |
| `find_app` | App Control | `LOW` | `NEVER` | **Implemented** | Resolves package identifiers |
| `list_available_apps` | App Control | `LOW` | `NEVER` | **Implemented** | Queries allowlisted apps |
| `open_app_settings` | App Control | `LOW` | `NEVER` | **Implemented** | Opens system settings for app |
| `open_app_store` | App Control | `LOW` | `NEVER` | **Implemented** | Opens Play Store market URI |
| `open_whatsapp_chat` | Communication | `LOW` | `NEVER` | **Implemented** | Uses WhatsApp deep link |
| `prepare_whatsapp_message` | Communication | `MEDIUM` | `WHEN_NEEDED` | **Implemented** | Populates message in WhatsApp composer |
| `send_whatsapp_message` | Communication | `HIGH` | `ALWAYS` | **Implemented** | Dispatches to composer with explicit confirmation |
| `share_file_to_whatsapp` | Communication | `MEDIUM` | `WHEN_NEEDED` | **Implemented** | Uses FileProvider with URI read grant |
| `read_notifications` | Communication | `HIGH` | `ALWAYS` | **Permission-Gated** | Directs to Notification Access Settings |
| `take_photo` | Camera & Media | `MEDIUM` | `WHEN_NEEDED` | **Implemented** | Uses Camera intent with camera permission |
| `record_video` | Camera & Media | `HIGH` | `ALWAYS` | **Implemented** | Camera & microphone permission required |
| `record_audio` | Camera & Media | `HIGH` | `ALWAYS` | **Implemented** | Microphone permission required |
| `toggle_flashlight` | Camera & Media | `LOW` | `NEVER` | **Implemented** | Uses `CameraManager.setTorchMode` |
| `get_camera_status` | Camera & Media | `LOW` | `NEVER` | **Implemented** | Queries hardware sensor count |
| `find_apk` | App Management | `LOW` | `NEVER` | **Implemented** | Storage Access Framework file picker |
| `inspect_apk` | App Management | `LOW` | `NEVER` | **Implemented** | Reads package name, version, permissions |
| `request_app_install` | App Management | `HIGH` | `ALWAYS` | **Implemented** | Uses system package installer & FileProvider |
| `open_install_permissions` | App Management | `LOW` | `NEVER` | **Implemented** | Opens Unknown App Sources settings |
| `request_app_uninstall` | App Management | `HIGH` | `ALWAYS` | **Implemented** | Prompts Android system uninstall dialog |
| `check_installed_app` | App Management | `LOW` | `NEVER` | **Implemented** | Verifies installation status |
| `find_file` | Files | `LOW` | `NEVER` | **Implemented** | SAF document picker |
| `read_selected_file` | Files | `MEDIUM` | `WHEN_NEEDED` | **Implemented** | Reads file via ContentResolver (2KB preview if large) |
| `create_note` | Files | `LOW` | `NEVER` | **Implemented** | Writes local note in app storage |
| `copy_file` | Files | `MEDIUM` | `WHEN_NEEDED` | **Implemented** | Safe file copy |
| `share_file` | Files | `MEDIUM` | `WHEN_NEEDED` | **Implemented** | Dispatches via `Intent.createChooser` |
| `get_storage_summary` | Files | `LOW` | `NEVER` | **Implemented** | `StatFs` internal storage query |
| `get_battery_status` | Device | `LOW` | `NEVER` | **Implemented** | BatteryManager level & charging status |
| `get_device_info` | Device | `LOW` | `NEVER` | **Implemented** | Model, Android version, ABIs |
| `get_location` | Device | `MEDIUM` | `WHEN_NEEDED` | **Permission-Gated** | On-demand GPS coordinates |
| `open_wifi_settings` | Device | `LOW` | `NEVER` | **Implemented** | Opens Android Wi-Fi settings page |
| `open_bluetooth_settings`| Device | `LOW` | `NEVER` | **Implemented** | Opens Bluetooth configuration page |
| `set_brightness` | Device | `MEDIUM` | `WHEN_NEEDED` | **Implemented** | Opens display settings |
| `set_volume` | Device | `MEDIUM` | `WHEN_NEEDED` | **Implemented** | Adjusts music stream volume via AudioManager |
| `open_accessibility_settings` | Device | `LOW` | `NEVER` | **Implemented** | Opens Accessibility Settings |
| `get_active_app` | Screen | `LOW` | `NEVER` | **Service-Gated** | Requires ApexAccessibilityService |
| `read_visible_ui` | Screen | `MEDIUM` | `WHEN_NEEDED` | **Service-Gated** | Traverses UI nodes (skips passwords) |
| `tap_ui_element` | Screen | `HIGH` | `ALWAYS` | **Service-Gated** | Dispatches gesture tap |
| `type_text` | Screen | `HIGH` | `ALWAYS` | **Service-Gated** | Sets text into focused non-password field |
| `scroll_screen` | Screen | `LOW` | `NEVER` | **Service-Gated** | Dispatches forward/backward scroll |
| `press_back` | Screen | `LOW` | `NEVER` | **Service-Gated** | Global back navigation action |
| `take_screenshot` | Screen | `HIGH` | `ALWAYS` | **Service-Gated** | Android 11+ Accessibility / MediaProjection |
| `silent_install_apk` | Restricted | `RESTRICTED`| `NOT_ALLOWED` | **REJECTED** | Blocked by Policy Engine |
| `covert_record_audio`| Restricted | `RESTRICTED`| `NOT_ALLOWED` | **REJECTED** | Blocked by Policy Engine |

---

## 6. Code Examples for Extending APEX

### Example 1: Registering a Low-Risk Tool (`get_battery_status`)
```kotlin
toolRegistry.registerTool(
    ToolMetadata(
        name = "get_battery_status",
        description = "Get battery charge level and power status",
        category = "Device",
        risk = ToolRisk.LOW,
        confirmation = ConfirmationPolicy.NEVER
    )
) { context, _ ->
    val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val batteryStatus = context.registerReceiver(null, ifilter)
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
    ToolExecutionResult.Success("get_battery_status", mapOf("battery" to level), "Battery is at $level%")
}
```

### Example 2: Registering a High-Risk Tool (`send_whatsapp_message`)
```kotlin
toolRegistry.registerTool(
    ToolMetadata(
        name = "send_whatsapp_message",
        description = "Dispatch a WhatsApp message with explicit recipient confirmation",
        category = "Communication",
        risk = ToolRisk.HIGH,
        confirmation = ConfirmationPolicy.ALWAYS,
        parametersSchema = mapOf(
            "recipient" to ParameterDefinition(ParameterType.STRING, "Recipient name or phone number", required = true),
            "message" to ParameterDefinition(ParameterType.STRING, "Exact message text to send", required = true)
        )
    )
) { context, args ->
    val recipient = args["recipient"] as String
    val message = args["message"] as String
    val url = "https://api.whatsapp.com/send?text=" + URLEncoder.encode(message, "UTF-8")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        `package` = "com.whatsapp"
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
    ToolExecutionResult.Success("send_whatsapp_message", args, "Dispatched message to WhatsApp composer for $recipient")
}
```

### Example 3: Defining a Multi-Step Photo Sharing Workflow
```kotlin
val photoWorkflow = WorkflowDefinition(
    workflowId = "workflow_capture_and_share",
    title = "Capture & Share Photo",
    description = "Captures photo and prepares WhatsApp share",
    steps = listOf(
        WorkflowStep(
            stepId = "step_1_camera",
            toolName = "take_photo",
            arguments = emptyMap(),
            description = "Capture photo via camera viewfinder"
        ),
        WorkflowStep(
            stepId = "step_2_share",
            toolName = "prepare_whatsapp_message",
            arguments = mapOf("recipient" to "Abi", "message" to "Photo from APEX"),
            description = "Prepare WhatsApp conversation with draft",
            requiresConfirmation = true
        )
    )
)

// Execution via WorkflowEngine
val state = workflowEngine.executeWorkflow(context, photoWorkflow)
```

---

## 7. Running Tests & Verification

To run the automated verification suite:
```bash
node verify_apex_mobile.js
```

All 9 test categories verify:
1. Android project manifest and permissions.
2. FunctionGemma 270M prompt formatting and control tokens.
3. FunctionGemma 270M tool call parser.
4. Tool registry completeness (all 40+ tools registered).
5. Policy engine risk enforcement (LOW, HIGH, RESTRICTED).
6. Security boundary: invalidating confirmation upon argument mutation.
7. Workflow engine sequential execution and preflight validation.
8. Audit logger sanitization (passwords, tokens, API keys).
9. Compose Theme colors matching PC CSS variables.
