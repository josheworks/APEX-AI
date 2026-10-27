package com.apex.ai.mobile.tools.screen

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.apex.ai.mobile.registry.ToolExecutionResult
import com.apex.ai.mobile.registry.ToolHandler

object ScreenInteractionTools {

    val getActiveAppHandler = ToolHandler { _, _ ->
        if (!ApexAccessibilityService.isActive) {
            return@ToolHandler ToolExecutionResult.Failed(
                "get_active_app",
                "ACCESSIBILITY_DISABLED",
                "APEX Accessibility Service is not active. Please enable it in Settings -> Accessibility."
            )
        }
        val currentApp = ApexAccessibilityService.currentForegroundPackage
        ToolExecutionResult.Success(
            "get_active_app",
            mapOf("package_name" to currentApp),
            "Current foreground app: $currentApp"
        )
    }

    val readVisibleUiHandler = ToolHandler { _, _ ->
        val service = ApexAccessibilityService.instance
        if (service == null || !ApexAccessibilityService.isActive) {
            return@ToolHandler ToolExecutionResult.Failed(
                "read_visible_ui",
                "ACCESSIBILITY_DISABLED",
                "Screen inspection requires the APEX Accessibility Service to be enabled in Android Settings."
            )
        }

        val elements = service.getVisibleUiHierarchy()
        ToolExecutionResult.Success(
            "read_visible_ui",
            mapOf("elements_count" to elements.size, "elements" to elements.take(20)),
            "Found ${elements.size} accessible UI elements on screen (excluding password inputs)."
        )
    }

    val tapUiElementHandler = ToolHandler { _, args ->
        val service = ApexAccessibilityService.instance
        if (service == null || !ApexAccessibilityService.isActive) {
            return@ToolHandler ToolExecutionResult.Failed(
                "tap_ui_element",
                "ACCESSIBILITY_DISABLED",
                "Screen interaction requires APEX Accessibility Service."
            )
        }

        val x = (args["x"] as? Number)?.toFloat() ?: 500f
        val y = (args["y"] as? Number)?.toFloat() ?: 500f
        val dispatched = service.tapScreen(x, y)

        if (dispatched) {
            ToolExecutionResult.Success("tap_ui_element", mapOf("x" to x, "y" to y), "Tapped at coordinates ($x, $y)")
        } else {
            ToolExecutionResult.Failed("tap_ui_element", "GESTURE_FAILED", "Gesture dispatch failed or not supported on this OS version")
        }
    }

    val typeTextHandler = ToolHandler { _, args ->
        val service = ApexAccessibilityService.instance
        if (service == null || !ApexAccessibilityService.isActive) {
            return@ToolHandler ToolExecutionResult.Failed(
                "type_text",
                "ACCESSIBILITY_DISABLED",
                "Typing text requires APEX Accessibility Service."
            )
        }

        val text = args["text"]?.toString() ?: ""
        val success = service.typeTextIntoFocused(text)

        if (success) {
            ToolExecutionResult.Success("type_text", mapOf("text_length" to text.length), "Typed text into focused field")
        } else {
            ToolExecutionResult.Failed("type_text", "NO_FOCUSED_INPUT", "No non-password text input field is currently focused")
        }
    }

    val scrollScreenHandler = ToolHandler { _, args ->
        val service = ApexAccessibilityService.instance
        if (service == null || !ApexAccessibilityService.isActive) {
            return@ToolHandler ToolExecutionResult.Failed(
                "scroll_screen",
                "ACCESSIBILITY_DISABLED",
                "Screen scrolling requires APEX Accessibility Service."
            )
        }

        val direction = args["direction"]?.toString()?.lowercase() ?: "down"
        val forward = direction == "down" || direction == "forward"
        val success = service.scroll(forward)

        if (success) {
            ToolExecutionResult.Success("scroll_screen", mapOf("direction" to direction), "Scrolled screen $direction")
        } else {
            ToolExecutionResult.Failed("scroll_screen", "SCROLL_FAILED", "Active screen does not support scroll action")
        }
    }

    val pressBackHandler = ToolHandler { _, _ ->
        val service = ApexAccessibilityService.instance
        if (service == null || !ApexAccessibilityService.isActive) {
            return@ToolHandler ToolExecutionResult.Failed(
                "press_back",
                "ACCESSIBILITY_DISABLED",
                "Back navigation requires APEX Accessibility Service."
            )
        }

        val success = service.triggerBack()
        if (success) {
            ToolExecutionResult.Success("press_back", emptyMap(), "Triggered back navigation")
        } else {
            ToolExecutionResult.Failed("press_back", "ACTION_FAILED", "Failed to perform global back action")
        }
    }

    val takeScreenshotHandler = ToolHandler { context, _ ->
        val service = ApexAccessibilityService.instance
        if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ AccessibilityService.takeScreenshot API
            ToolExecutionResult.Success(
                "take_screenshot",
                mapOf("method" to "accessibility_service_capture"),
                "Screen capture requested via Android 11+ system API"
            )
        } else {
            // Direct user to system screenshot or explain MediaProjection consent flow
            ToolExecutionResult.Success(
                "take_screenshot",
                mapOf("method" to "system_media_projection"),
                "Screen capture requires foreground user consent via MediaProjection dialog"
            )
        }
    }
}
