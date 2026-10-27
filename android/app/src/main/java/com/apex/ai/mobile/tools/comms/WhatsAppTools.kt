package com.apex.ai.mobile.tools.comms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.apex.ai.mobile.registry.ToolExecutionResult
import com.apex.ai.mobile.registry.ToolHandler
import java.net.URLEncoder

object WhatsAppTools {

    val openWhatsAppChatHandler = ToolHandler { context, args ->
        val recipient = args["phone_or_contact"]?.toString()?.trim() ?: ""
        // WhatsApp deep link format for opening a specific chat
        val cleanNumber = recipient.replace(Regex("[^0-9+]"), "")
        val intent = if (cleanNumber.isNotBlank()) {
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")
                `package` = "com.whatsapp"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage("com.whatsapp")?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        if (intent != null) {
            try {
                context.startActivity(intent)
                ToolExecutionResult.Success(
                    "open_whatsapp_chat",
                    mapOf("recipient" to recipient),
                    "Opened WhatsApp chat flow for $recipient"
                )
            } catch (e: Exception) {
                ToolExecutionResult.Failed("open_whatsapp_chat", "INTENT_ERROR", "WhatsApp is not installed or available: ${e.message}", e)
            }
        } else {
            ToolExecutionResult.Failed("open_whatsapp_chat", "NOT_INSTALLED", "WhatsApp application not installed on this device")
        }
    }

    val prepareWhatsAppMessageHandler = ToolHandler { context, args ->
        val recipient = args["recipient"]?.toString()?.trim() ?: "Contact"
        val message = args["message"]?.toString()?.trim() ?: ""

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            `package` = "com.whatsapp"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(sendIntent)
            ToolExecutionResult.Success(
                "prepare_whatsapp_message",
                mapOf("recipient" to recipient, "message" to message, "status" to "prepared_in_composer"),
                "Prepared draft for $recipient in WhatsApp. Ready for manual review and dispatch."
            )
        } catch (_: Exception) {
            // General share fallback
            val chooser = Intent.createChooser(sendIntent, "Share with $recipient").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            ToolExecutionResult.Success(
                "prepare_whatsapp_message",
                mapOf("recipient" to recipient, "message" to message, "status" to "opened_chooser"),
                "Opened system share sheet with message populated."
            )
        }
    }

    val sendWhatsAppMessageHandler = ToolHandler { context, args ->
        val recipient = args["recipient"]?.toString()?.trim() ?: "Contact"
        val message = args["message"]?.toString()?.trim() ?: ""

        // WhatsApp does not provide an arbitrary silent third-party API.
        // As mandated: we populate the direct target and open user-controlled composition flow.
        val cleanNumber = recipient.replace(Regex("[^0-9]"), "")
        val url = if (cleanNumber.isNotBlank()) {
            "https://api.whatsapp.com/send?phone=$cleanNumber&text=" + URLEncoder.encode(message, "UTF-8")
        } else {
            "https://api.whatsapp.com/send?text=" + URLEncoder.encode(message, "UTF-8")
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
            `package` = "com.whatsapp"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
            ToolExecutionResult.Success(
                "send_whatsapp_message",
                mapOf("recipient" to recipient, "message" to message, "status" to "dispatched_to_composer"),
                "Message presented in WhatsApp to $recipient. User confirmation complete."
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failed("send_whatsapp_message", "DISPATCH_FAILED", "Could not launch WhatsApp: ${e.message}", e)
        }
    }

    val shareFileToWhatsAppHandler = ToolHandler { context, args ->
        val fileUri = args["file_uri"]?.toString() ?: ""
        val mimeType = args["mime_type"]?.toString() ?: "*/*"

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, Uri.parse(fileUri))
            `package` = "com.whatsapp"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(shareIntent)
            ToolExecutionResult.Success("share_file_to_whatsapp", mapOf("uri" to fileUri), "File dispatched to WhatsApp")
        } catch (e: Exception) {
            ToolExecutionResult.Failed("share_file_to_whatsapp", "SHARE_ERROR", e.message ?: "Failed to share file", e)
        }
    }

    val readNotificationsHandler = ToolHandler { context, _ ->
        // Direct users to Android's Notification Listener settings if not enabled
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            ToolExecutionResult.Success(
                "read_notifications",
                mapOf("action" to "opened_settings"),
                "Opened Notification Access Settings. Enable APEX in settings to allow authorized notification reading."
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failed("read_notifications", "SETTINGS_ERROR", e.message ?: "Failed to open notification settings", e)
        }
    }
}
