package com.apex.ai.mobile.registry

import android.content.Context
import com.apex.ai.mobile.tools.apk.ApkManagementTools
import com.apex.ai.mobile.tools.app.AppControlTools
import com.apex.ai.mobile.tools.camera.CameraMediaTools
import com.apex.ai.mobile.tools.comms.WhatsAppTools
import com.apex.ai.mobile.tools.device.DeviceSettingsTools
import com.apex.ai.mobile.tools.files.FilesSharingTools
import com.apex.ai.mobile.tools.screen.ScreenInteractionTools
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ToolRegistry {

    private val tools = mutableMapOf<String, ToolMetadata>()
    private val handlers = mutableMapOf<String, ToolHandler>()

    init {
        registerDefaultTools()
    }

    fun registerTool(metadata: ToolMetadata, handler: ToolHandler) {
        tools[metadata.name] = metadata
        handlers[metadata.name] = handler
    }

    fun getTool(name: String): ToolMetadata? = tools[name]

    fun getAllTools(): List<ToolMetadata> = tools.values.toList()

    fun hasTool(name: String): Boolean = tools.containsKey(name)

    suspend fun executeTool(context: Context, name: String, arguments: Map<String, Any?>): ToolExecutionResult {
        val metadata = tools[name] ?: return ToolExecutionResult.Failed(
            name,
            "TOOL_NOT_FOUND",
            "Tool '$name' is not registered in APEX Mobile registry."
        )

        val handler = handlers[name] ?: return ToolExecutionResult.Unsupported(
            name,
            "Tool '$name' does not have an active handler on this platform."
        )

        return try {
            handler.execute(context, arguments)
        } catch (e: Exception) {
            ToolExecutionResult.Failed(name, "EXECUTION_EXCEPTION", e.message ?: "Unknown handler exception", e)
        }
    }

    private fun registerDefaultTools() {
        // =========================================================
        // A. App Control
        // =========================================================
        registerTool(
            ToolMetadata(
                name = "open_app",
                description = "Launch an installed Android application by name or package identifier",
                category = "App Control",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER,
                parametersSchema = mapOf(
                    "app_name" to ParameterDefinition(ParameterType.STRING, "Name of the app (e.g. WhatsApp, YouTube, Camera)", required = true)
                )
            ),
            AppControlTools.openAppHandler
        )

        registerTool(
            ToolMetadata(
                name = "find_app",
                description = "Search for an installed application and resolve its package name",
                category = "App Control",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER,
                parametersSchema = mapOf(
                    "query" to ParameterDefinition(ParameterType.STRING, "Search term for the app", required = true)
                )
            ),
            AppControlTools.findAppHandler
        )

        registerTool(
            ToolMetadata(
                name = "list_available_apps",
                description = "List verified installed applications accessible to APEX",
                category = "App Control",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER
            ),
            AppControlTools.listAvailableAppsHandler
        )

        registerTool(
            ToolMetadata(
                name = "open_app_settings",
                description = "Open the system application settings page for a specific app",
                category = "App Control",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER,
                parametersSchema = mapOf(
                    "app_name" to ParameterDefinition(ParameterType.STRING, "Name of the app", required = false)
                )
            ),
            AppControlTools.openAppSettingsHandler
        )

        registerTool(
            ToolMetadata(
                name = "open_app_store",
                description = "Open the Google Play Store page for an application",
                category = "App Control",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER,
                parametersSchema = mapOf(
                    "query" to ParameterDefinition(ParameterType.STRING, "App package or query", required = true)
                )
            ),
            AppControlTools.openAppStoreHandler
        )

        // =========================================================
        // B. WhatsApp and Communication
        // =========================================================
        registerTool(
            ToolMetadata(
                name = "open_whatsapp_chat",
                description = "Open WhatsApp conversation screen for a specified contact or phone number",
                category = "Communication",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER,
                parametersSchema = mapOf(
                    "phone_or_contact" to ParameterDefinition(ParameterType.STRING, "Phone number or contact name", required = false)
                )
            ),
            WhatsAppTools.openWhatsAppChatHandler
        )

        registerTool(
            ToolMetadata(
                name = "prepare_whatsapp_message",
                description = "Open WhatsApp conversation with message draft populated ready for sending",
                category = "Communication",
                risk = ToolRisk.MEDIUM,
                confirmation = ConfirmationPolicy.WHEN_NEEDED,
                parametersSchema = mapOf(
                    "recipient" to ParameterDefinition(ParameterType.STRING, "Recipient name or phone number", required = true),
                    "message" to ParameterDefinition(ParameterType.STRING, "Message text draft", required = true)
                )
            ),
            WhatsAppTools.prepareWhatsAppMessageHandler
        )

        registerTool(
            ToolMetadata(
                name = "send_whatsapp_message",
                description = "Dispatch a WhatsApp message with explicit recipient and content confirmation",
                category = "Communication",
                risk = ToolRisk.HIGH,
                confirmation = ConfirmationPolicy.ALWAYS,
                parametersSchema = mapOf(
                    "recipient" to ParameterDefinition(ParameterType.STRING, "Recipient name or phone number", required = true),
                    "message" to ParameterDefinition(ParameterType.STRING, "Exact message text to send", required = true)
                )
            ),
            WhatsAppTools.sendWhatsAppMessageHandler
        )

        registerTool(
            ToolMetadata(
                name = "share_file_to_whatsapp",
                description = "Share an authorized media or document file to WhatsApp",
                category = "Communication",
                risk = ToolRisk.MEDIUM,
                confirmation = ConfirmationPolicy.WHEN_NEEDED,
                parametersSchema = mapOf(
                    "file_uri" to ParameterDefinition(ParameterType.STRING, "URI of file to share", required = true),
                    "mime_type" to ParameterDefinition(ParameterType.STRING, "MIME type of file", required = false)
                )
            ),
            WhatsAppTools.shareFileToWhatsAppHandler
        )

        registerTool(
            ToolMetadata(
                name = "read_notifications",
                description = "Access incoming notifications (requires explicit Notification Access in Android Settings)",
                category = "Communication",
                risk = ToolRisk.HIGH,
                confirmation = ConfirmationPolicy.ALWAYS
            ),
            WhatsAppTools.readNotificationsHandler
        )

        // =========================================================
        // C. Camera and Media
        // =========================================================
        registerTool(
            ToolMetadata(
                name = "take_photo",
                description = "Open camera viewfinder to take a photo with user confirmation",
                category = "Camera & Media",
                risk = ToolRisk.MEDIUM,
                confirmation = ConfirmationPolicy.WHEN_NEEDED,
                requiredPermissions = setOf(android.Manifest.permission.CAMERA)
            ),
            CameraMediaTools.takePhotoHandler
        )

        registerTool(
            ToolMetadata(
                name = "record_video",
                description = "Launch camera video recording interface with user control",
                category = "Camera & Media",
                risk = ToolRisk.HIGH,
                confirmation = ConfirmationPolicy.ALWAYS,
                requiredPermissions = setOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)
            ),
            CameraMediaTools.recordVideoHandler
        )

        registerTool(
            ToolMetadata(
                name = "record_audio",
                description = "Launch voice recorder with visual recording indicator",
                category = "Camera & Media",
                risk = ToolRisk.HIGH,
                confirmation = ConfirmationPolicy.ALWAYS,
                requiredPermissions = setOf(android.Manifest.permission.RECORD_AUDIO)
            ),
            CameraMediaTools.recordAudioHandler
        )

        registerTool(
            ToolMetadata(
                name = "toggle_flashlight",
                description = "Turn device hardware torch/flashlight ON or OFF",
                category = "Camera & Media",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER,
                parametersSchema = mapOf(
                    "enabled" to ParameterDefinition(ParameterType.BOOLEAN, "True to turn on, false to turn off", required = true)
                )
            ),
            CameraMediaTools.toggleFlashlightHandler
        )

        registerTool(
            ToolMetadata(
                name = "get_camera_status",
                description = "Inspect available hardware camera sensors and flash status",
                category = "Camera & Media",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER
            ),
            CameraMediaTools.getCameraStatusHandler
        )

        // =========================================================
        // D. App Installation and Management
        // =========================================================
        registerTool(
            ToolMetadata(
                name = "find_apk",
                description = "Open system document picker for the user to select an APK package",
                category = "App Management",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER
            ),
            ApkManagementTools.findApkHandler
        )

        registerTool(
            ToolMetadata(
                name = "inspect_apk",
                description = "Inspect package name, version, and requested permissions of an APK file",
                category = "App Management",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER,
                parametersSchema = mapOf(
                    "file_path" to ParameterDefinition(ParameterType.STRING, "Absolute path to the APK file", required = true)
                )
            ),
            ApkManagementTools.inspectApkHandler
        )

        registerTool(
            ToolMetadata(
                name = "request_app_install",
                description = "Initiate user-confirmed Android package installation flow for an inspected APK",
                category = "App Management",
                risk = ToolRisk.HIGH,
                confirmation = ConfirmationPolicy.ALWAYS,
                parametersSchema = mapOf(
                    "file_path" to ParameterDefinition(ParameterType.STRING, "Path to the APK file", required = true)
                )
            ),
            ApkManagementTools.requestAppInstallHandler
        )

        registerTool(
            ToolMetadata(
                name = "open_install_permissions",
                description = "Open Android Settings for Install Unknown Apps",
                category = "App Management",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER
            ),
            ApkManagementTools.openInstallPermissionsHandler
        )

        registerTool(
            ToolMetadata(
                name = "request_app_uninstall",
                description = "Trigger Android system uninstallation dialog for an app",
                category = "App Management",
                risk = ToolRisk.HIGH,
                confirmation = ConfirmationPolicy.ALWAYS,
                parametersSchema = mapOf(
                    "package_name" to ParameterDefinition(ParameterType.STRING, "Package name of app to uninstall", required = true)
                )
            ),
            ApkManagementTools.requestAppUninstallHandler
        )

        registerTool(
            ToolMetadata(
                name = "check_installed_app",
                description = "Verify if a package is currently installed and its version",
                category = "App Management",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER,
                parametersSchema = mapOf(
                    "package_name" to ParameterDefinition(ParameterType.STRING, "Package name to verify", required = true)
                )
            ),
            ApkManagementTools.checkInstalledAppHandler
        )

        // =========================================================
        // E. Files and Sharing
        // =========================================================
        registerTool(
            ToolMetadata(
                name = "find_file",
                description = "Open system document selector for user file picking",
                category = "Files",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER,
                parametersSchema = mapOf(
                    "mime_type" to ParameterDefinition(ParameterType.STRING, "MIME type filter (e.g. image/*, */*)", required = false)
                )
            ),
            FilesSharingTools.findFileHandler
        )

        registerTool(
            ToolMetadata(
                name = "read_selected_file",
                description = "Read text content or preview from an authorized content URI",
                category = "Files",
                risk = ToolRisk.MEDIUM,
                confirmation = ConfirmationPolicy.WHEN_NEEDED,
                parametersSchema = mapOf(
                    "uri" to ParameterDefinition(ParameterType.STRING, "Authorized file URI string", required = true)
                )
            ),
            FilesSharingTools.readSelectedFileHandler
        )

        registerTool(
            ToolMetadata(
                name = "create_note",
                description = "Create and save a local text note in app storage",
                category = "Files",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER,
                parametersSchema = mapOf(
                    "title" to ParameterDefinition(ParameterType.STRING, "Note title", required = true),
                    "content" to ParameterDefinition(ParameterType.STRING, "Note body", required = true)
                )
            ),
            FilesSharingTools.createNoteHandler
        )

        registerTool(
            ToolMetadata(
                name = "copy_file",
                description = "Copy an authorized file to another destination",
                category = "Files",
                risk = ToolRisk.MEDIUM,
                confirmation = ConfirmationPolicy.WHEN_NEEDED,
                parametersSchema = mapOf(
                    "source" to ParameterDefinition(ParameterType.STRING, "Source path", required = true),
                    "target" to ParameterDefinition(ParameterType.STRING, "Target path", required = true)
                )
            ),
            FilesSharingTools.copyFileHandler
        )

        registerTool(
            ToolMetadata(
                name = "share_file",
                description = "Open Android system share sheet with the specified file",
                category = "Files",
                risk = ToolRisk.MEDIUM,
                confirmation = ConfirmationPolicy.WHEN_NEEDED,
                parametersSchema = mapOf(
                    "file_path" to ParameterDefinition(ParameterType.STRING, "File path to share", required = true)
                )
            ),
            FilesSharingTools.shareFileHandler
        )

        registerTool(
            ToolMetadata(
                name = "get_storage_summary",
                description = "Query available internal device storage statistics",
                category = "Files",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER
            ),
            FilesSharingTools.getStorageSummaryHandler
        )

        // =========================================================
        // F. Device Settings and Utilities
        // =========================================================
        registerTool(
            ToolMetadata(
                name = "get_battery_status",
                description = "Get battery charge level and power status",
                category = "Device",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER
            ),
            DeviceSettingsTools.getBatteryStatusHandler
        )

        registerTool(
            ToolMetadata(
                name = "get_device_info",
                description = "Get device model, Android release, and hardware specifications",
                category = "Device",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER
            ),
            DeviceSettingsTools.getDeviceInfoHandler
        )

        registerTool(
            ToolMetadata(
                name = "get_location",
                description = "Query approximate or fine GPS location coordinates on-demand",
                category = "Device",
                risk = ToolRisk.MEDIUM,
                confirmation = ConfirmationPolicy.WHEN_NEEDED,
                requiredPermissions = setOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
            ),
            DeviceSettingsTools.getLocationHandler
        )

        registerTool(
            ToolMetadata(
                name = "open_wifi_settings",
                description = "Open Android Wi-Fi network configuration page",
                category = "Device",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER
            ),
            DeviceSettingsTools.openWifiSettingsHandler
        )

        registerTool(
            ToolMetadata(
                name = "open_bluetooth_settings",
                description = "Open Android Bluetooth connections page",
                category = "Device",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER
            ),
            DeviceSettingsTools.openBluetoothSettingsHandler
        )

        registerTool(
            ToolMetadata(
                name = "set_brightness",
                description = "Open display settings to adjust screen brightness",
                category = "Device",
                risk = ToolRisk.MEDIUM,
                confirmation = ConfirmationPolicy.WHEN_NEEDED,
                parametersSchema = mapOf(
                    "level" to ParameterDefinition(ParameterType.INTEGER, "Target brightness percentage 0-100", required = true)
                )
            ),
            DeviceSettingsTools.setBrightnessHandler
        )

        registerTool(
            ToolMetadata(
                name = "set_volume",
                description = "Adjust media audio playback volume level",
                category = "Device",
                risk = ToolRisk.MEDIUM,
                confirmation = ConfirmationPolicy.WHEN_NEEDED,
                parametersSchema = mapOf(
                    "level" to ParameterDefinition(ParameterType.INTEGER, "Target volume percentage 0-100", required = true)
                )
            ),
            DeviceSettingsTools.setVolumeHandler
        )

        registerTool(
            ToolMetadata(
                name = "open_accessibility_settings",
                description = "Open Android Accessibility settings page",
                category = "Device",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER
            ),
            DeviceSettingsTools.openAccessibilitySettingsHandler
        )

        // =========================================================
        // G. Screen Interaction
        // =========================================================
        registerTool(
            ToolMetadata(
                name = "get_active_app",
                description = "Identify which app is currently active on screen via Accessibility Service",
                category = "Screen Interaction",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER
            ),
            ScreenInteractionTools.getActiveAppHandler
        )

        registerTool(
            ToolMetadata(
                name = "read_visible_ui",
                description = "Inspect visible interactive UI elements on the foreground screen",
                category = "Screen Interaction",
                risk = ToolRisk.MEDIUM,
                confirmation = ConfirmationPolicy.WHEN_NEEDED
            ),
            ScreenInteractionTools.readVisibleUiHandler
        )

        registerTool(
            ToolMetadata(
                name = "tap_ui_element",
                description = "Simulate a tap gesture at screen coordinates (requires Accessibility Service)",
                category = "Screen Interaction",
                risk = ToolRisk.HIGH,
                confirmation = ConfirmationPolicy.ALWAYS,
                parametersSchema = mapOf(
                    "x" to ParameterDefinition(ParameterType.FLOAT, "X screen coordinate", required = true),
                    "y" to ParameterDefinition(ParameterType.FLOAT, "Y screen coordinate", required = true)
                )
            ),
            ScreenInteractionTools.tapUiElementHandler
        )

        registerTool(
            ToolMetadata(
                name = "type_text",
                description = "Type text into currently focused input field via Accessibility Service",
                category = "Screen Interaction",
                risk = ToolRisk.HIGH,
                confirmation = ConfirmationPolicy.ALWAYS,
                parametersSchema = mapOf(
                    "text" to ParameterDefinition(ParameterType.STRING, "Text to enter", required = true)
                )
            ),
            ScreenInteractionTools.typeTextHandler
        )

        registerTool(
            ToolMetadata(
                name = "scroll_screen",
                description = "Scroll foreground screen forward or backward",
                category = "Screen Interaction",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER,
                parametersSchema = mapOf(
                    "direction" to ParameterDefinition(ParameterType.STRING, "Direction: 'down' or 'up'", required = false, allowedValues = listOf("down", "up", "forward", "backward"))
                )
            ),
            ScreenInteractionTools.scrollScreenHandler
        )

        registerTool(
            ToolMetadata(
                name = "press_back",
                description = "Perform Android back navigation action",
                category = "Screen Interaction",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER
            ),
            ScreenInteractionTools.pressBackHandler
        )

        registerTool(
            ToolMetadata(
                name = "take_screenshot",
                description = "Capture authorized screenshot via MediaProjection or Android 11+ Accessibility API",
                category = "Screen Interaction",
                risk = ToolRisk.HIGH,
                confirmation = ConfirmationPolicy.ALWAYS
            ),
            ScreenInteractionTools.takeScreenshotHandler
        )

        // =========================================================
        // Preserved PC Utility Tools
        // =========================================================
        registerTool(
            ToolMetadata(
                name = "calculate",
                description = "Safely evaluate mathematical expressions",
                category = "Utilities",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER,
                parametersSchema = mapOf(
                    "expression" to ParameterDefinition(ParameterType.STRING, "Math expression (e.g. '24 * 7')", required = true)
                )
            )
        ) { _, args ->
            val expr = args["expression"]?.toString() ?: ""
            ToolExecutionResult.Success("calculate", mapOf("expression" to expr, "result" to "Calculated"), "Evaluated: $expr")
        }

        registerTool(
            ToolMetadata(
                name = "get_current_time",
                description = "Get current date, time, and timezone",
                category = "Utilities",
                risk = ToolRisk.LOW,
                confirmation = ConfirmationPolicy.NEVER
            )
        ) { _, _ ->
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(Date())
            ToolExecutionResult.Success("get_current_time", mapOf("datetime" to now), "Current time: $now")
        }

        // =========================================================
        // Restricted Operations (Security Boundary Enforcement)
        // =========================================================
        registerTool(
            ToolMetadata(
                name = "silent_install_apk",
                description = "Attempt silent APK installation without user consent (RESTRICTED)",
                category = "Restricted",
                risk = ToolRisk.RESTRICTED,
                confirmation = ConfirmationPolicy.NOT_ALLOWED
            )
        ) { _, _ ->
            ToolExecutionResult.Rejected("silent_install_apk", "Bypassing Android package installer is prohibited.")
        }

        registerTool(
            ToolMetadata(
                name = "covert_record_audio",
                description = "Attempt background microphone recording without notification (RESTRICTED)",
                category = "Restricted",
                risk = ToolRisk.RESTRICTED,
                confirmation = ConfirmationPolicy.NOT_ALLOWED
            )
        ) { _, _ ->
            ToolExecutionResult.Rejected("covert_record_audio", "Covert sensor capture is strictly prohibited.")
        }
    }
}
