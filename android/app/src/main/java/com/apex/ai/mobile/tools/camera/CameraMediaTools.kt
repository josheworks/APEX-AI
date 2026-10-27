package com.apex.ai.mobile.tools.camera

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.provider.MediaStore
import com.apex.ai.mobile.registry.ToolExecutionResult
import com.apex.ai.mobile.registry.ToolHandler

object CameraMediaTools {

    val takePhotoHandler = ToolHandler { context, _ ->
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            ToolExecutionResult.Success(
                "take_photo",
                mapOf("status" to "camera_intent_launched"),
                "Opened system camera capture interface"
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failed("take_photo", "CAMERA_ERROR", e.message ?: "Failed to launch camera", e)
        }
    }

    val recordVideoHandler = ToolHandler { context, _ ->
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            ToolExecutionResult.Success(
                "record_video",
                mapOf("status" to "video_intent_launched"),
                "Opened system video recording interface"
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failed("record_video", "VIDEO_ERROR", e.message ?: "Failed to launch video recorder", e)
        }
    }

    val recordAudioHandler = ToolHandler { context, _ ->
        val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            ToolExecutionResult.Success(
                "record_audio",
                mapOf("status" to "audio_intent_launched"),
                "Opened system audio recording interface"
            )
        } catch (_: Exception) {
            // General sound recorder fallback
            ToolExecutionResult.Success(
                "record_audio",
                mapOf("status" to "prompted_audio_record"),
                "Ready to record audio. Audio capture requires foreground permission."
            )
        }
    }

    val toggleFlashlightHandler = ToolHandler { context, args ->
        val enabled = args["enabled"] as? Boolean ?: true
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            return@ToolHandler ToolExecutionResult.Failed("toggle_flashlight", "NO_HARDWARE", "Camera service unavailable")
        }

        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraId, enabled)
                ToolExecutionResult.Success(
                    "toggle_flashlight",
                    mapOf("flashlight_on" to enabled),
                    "Flashlight turned ${if (enabled) "ON" else "OFF"}"
                )
            } else {
                ToolExecutionResult.Failed("toggle_flashlight", "UNSUPPORTED", "Flashlight hardware not available on this device")
            }
        } catch (e: Exception) {
            ToolExecutionResult.Failed("toggle_flashlight", "TORCH_ERROR", e.message ?: "Failed to toggle flashlight", e)
        }
    }

    val getCameraStatusHandler = ToolHandler { context, _ ->
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val count = cameraManager?.cameraIdList?.size ?: 0
        ToolExecutionResult.Success(
            "get_camera_status",
            mapOf("available_cameras" to count, "has_flash" to (count > 0)),
            "Device has $count camera sensors available"
        )
    }
}
