package com.apex.ai.mobile.voice

import com.apex.ai.mobile.registry.ToolExecutionResult

/**
 * Normalizes multilingual input (English, Tamil, Tanglish, Mixed) into standardized English
 * intent suitable for FunctionGemma 270M tool invocation.
 *
 * Also formats tool execution outcomes into concise, natural English spoken responses,
 * strictly upholding the policy: "APEX MUST ALWAYS RESPOND IN ENGLISH."
 */
object TranscriptionNormalizer {

    /**
     * Normalizes a transcription turn and returns the detected language along with canonical intent.
     */
    fun normalizeInput(rawTranscription: String): Pair<String, DetectedInputLanguage> {
        val trimmed = rawTranscription.trim()
        val language = LanguageDetector.detectLanguage(trimmed)

        if (language == DetectedInputLanguage.ENGLISH) {
            return Pair(trimmed, language)
        }

        var normalized = trimmed

        // 1. Native Tamil Script Normalization Patterns
        normalized = normalizeTamilScript(normalized)

        // 2. Tanglish / Romanized Tamil Normalization Patterns
        normalized = normalizeTanglish(normalized)

        return Pair(normalized.trim(), language)
    }

    private fun normalizeTamilScript(input: String): String {
        var text = input

        // Apps
        text = text.replace(Regex("வாட்ஸ்அப்(ப)?\\s*(திற|ஓபன்\\s*செய்|ஆன்\\s*செய்)", RegexOption.IGNORE_CASE), "open WhatsApp")
        text = text.replace(Regex("யூடியூப்(ப)?\\s*(திற|ஓபன்\\s*செய்)", RegexOption.IGNORE_CASE), "open YouTube")
        text = text.replace(Regex("கேமரா(வை)?\\s*(திற|ஓபன்\\s*செய்)", RegexOption.IGNORE_CASE), "open Camera")
        text = text.replace(Regex("அமைப்புகள்(ை)?\\s*(திற|காட்டு)", RegexOption.IGNORE_CASE), "open Settings")

        // Actions
        text = text.replace(Regex("பேட்டரி\\s*(எவ்வளவு|நிலை|எவ்ளோ).*?", RegexOption.IGNORE_CASE), "check battery status")
        text = text.replace(Regex("(பிளாஷ்லைட்|டார்ச்)\\s*(ஆன்\\s*செய்|போடு)", RegexOption.IGNORE_CASE), "turn on flashlight")
        text = text.replace(Regex("(பிளாஷ்லைட்|டார்ச்)\\s*(ஆஃப்\\s*செய்|அணை)", RegexOption.IGNORE_CASE), "turn off flashlight")
        text = text.replace(Regex("(புகைப்படம்|போட்டோ)\\s*(எடு|பிடி)", RegexOption.IGNORE_CASE), "take a photo")
        text = text.replace(Regex("வைஃபை\\s*(ஆன்\\s*செய்|போடு)", RegexOption.IGNORE_CASE), "enable Wi-Fi")
        text = text.replace(Regex("வைஃபை\\s*(ஆஃப்\\s*செய்|அணை)", RegexOption.IGNORE_CASE), "disable Wi-Fi")
        text = text.replace(Regex("புளூடூத்\\s*(ஆன்\\s*செய்|போடு)", RegexOption.IGNORE_CASE), "enable Bluetooth")
        text = text.replace(Regex("புளூடூத்\\s*(ஆஃப்\\s*செய்|அணை)", RegexOption.IGNORE_CASE), "disable Bluetooth")
        text = text.replace(Regex("ஒலி(யை)?\\s*(அதிகரி|ஏற்று)", RegexOption.IGNORE_CASE), "increase volume")
        text = text.replace(Regex("ஒலி(யை)?\\s*(குறை|கம்மி\\s*செய்)", RegexOption.IGNORE_CASE), "decrease volume")

        return text
    }

    private fun normalizeTanglish(input: String): String {
        var text = input

        // Battery queries
        // "En battery evlo irukku", "battery evlo", "battery percentage sollu"
        if (text.contains(Regex("(battery|charge).*?(evlo|evvalavu|percentage|status|irukku)", RegexOption.IGNORE_CASE))) {
            return "check battery status"
        }

        // Flashlight / Torch
        if (text.contains(Regex("(torch|flashlight).*?(on\\s*pannu|podu|vai)", RegexOption.IGNORE_CASE))) {
            return "turn on flashlight"
        }
        if (text.contains(Regex("(torch|flashlight).*?(off\\s*pannu|anachu|moodu)", RegexOption.IGNORE_CASE))) {
            return "turn off flashlight"
        }

        // Camera / Photo
        if (text.contains(Regex("(photo|oru\\s*photo).*?(edu|edunga|eduthu\\s*vai)", RegexOption.IGNORE_CASE)) ||
            text.contains(Regex("camera.*?(open\\s*pannu|thira)", RegexOption.IGNORE_CASE))) {
            return "take a photo"
        }

        // Settings
        if (text.contains(Regex("settings.*?(therandhu|thira|open\\s*pannu|vai)", RegexOption.IGNORE_CASE))) {
            return "open device settings"
        }

        // Wi-Fi
        if (text.contains(Regex("(wifi|wi-fi).*?(on\\s*pannu|podu|enable)", RegexOption.IGNORE_CASE))) {
            return "enable Wi-Fi"
        }
        if (text.contains(Regex("(wifi|wi-fi).*?(off\\s*pannu|anachu|disable)", RegexOption.IGNORE_CASE))) {
            return "disable Wi-Fi"
        }

        // Bluetooth
        if (text.contains(Regex("bluetooth.*?(on\\s*pannu|podu|enable)", RegexOption.IGNORE_CASE))) {
            return "enable Bluetooth"
        }
        if (text.contains(Regex("bluetooth.*?(off\\s*pannu|anachu|disable)", RegexOption.IGNORE_CASE))) {
            return "disable Bluetooth"
        }

        // Volume
        if (text.contains(Regex("volume.*?(athigam|yethu|kootu|increase)", RegexOption.IGNORE_CASE))) {
            return "increase volume"
        }
        if (text.contains(Regex("volume.*?(kura|kammi|decrease|korai)", RegexOption.IGNORE_CASE))) {
            return "decrease volume"
        }

        // App Launch: "<AppName> open pannu", "<AppName> thira"
        val openAppRegex = Regex("([a-zA-Z0-9 ]+?)\\s*(open\\s*pannu|open\\s*pannunga|thira|thiravum|therandhu\\s*vai)", RegexOption.IGNORE_CASE)
        val openMatch = openAppRegex.find(text)
        if (openMatch != null) {
            val app = openMatch.groupValues[1].replace(Regex("^(apex|hey\\s*apex|please)\\s*", RegexOption.IGNORE_CASE), "").trim()
            if (app.isNotBlank()) {
                return "open $app"
            }
        }

        // Fallback: strip leading APEX summons and common Tanglish suffixes
        text = text.replace(Regex("^(apex|hey\\s*apex|dei\\s*apex|machan|machi)\\s*,?\\s*", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("\\s*(pannu|pannunga|podu|sollu|sollunga|da|di|machi)\\s*$", RegexOption.IGNORE_CASE), "")

        return text
    }

    /**
     * Synthesizes a natural, concise English response for spoken feedback.
     * Guaranteed never to include raw JSON, package names, or non-English speech.
     */
    fun formatSpokenResponse(toolResult: ToolExecutionResult): String {
        return when (toolResult) {
            is ToolExecutionResult.Success -> {
                val outputMap = toolResult.output as? Map<*, *>
                when (toolResult.toolName) {
                    "get_battery_status" -> {
                        val level = outputMap?.get("percentage") ?: outputMap?.get("level") ?: outputMap?.get("battery_level")
                        val charging = outputMap?.get("is_charging") == true
                        val chargingStr = if (charging) ", and currently charging" else ""
                        if (level != null) {
                            "Your battery is at $level percent$chargingStr."
                        } else {
                            "Your battery status has been checked."
                        }
                    }
                    "open_app", "launch_app" -> {
                        val app = outputMap?.get("app_name") ?: outputMap?.get("app")
                        if (app != null) "Opening $app." else toolResult.message
                    }
                    "toggle_flashlight", "set_flashlight" -> {
                        val state = outputMap?.get("state") ?: outputMap?.get("enabled")
                        if (state == true || state == "on") "Flashlight is now turned on." else "Flashlight is turned off."
                    }
                    "take_photo", "capture_image" -> {
                        "Photo captured successfully."
                    }
                    "open_device_settings" -> {
                        "Opening settings."
                    }
                    "set_wifi_state" -> {
                        val state = outputMap?.get("enabled")
                        if (state == true) "Wi-Fi is enabled." else "Wi-Fi is disabled."
                    }
                    "set_bluetooth_state" -> {
                        val state = outputMap?.get("enabled")
                        if (state == true) "Bluetooth is enabled." else "Bluetooth is disabled."
                    }
                    "set_volume_level" -> {
                        val level = outputMap?.get("volume_level") ?: outputMap?.get("level")
                        if (level != null) "Volume set to $level percent." else "Volume adjusted."
                    }
                    else -> {
                        toolResult.message.ifBlank { "Action completed successfully." }
                    }
                }
            }
            is ToolExecutionResult.Failed -> {
                val reason = toolResult.errorMessage.replace(Regex("com\\.[a-zA-Z0-9.]+"), "app")
                "I couldn't complete that action. $reason"
            }
            is ToolExecutionResult.Rejected -> {
                "Action was blocked by device security policy."
            }
            is ToolExecutionResult.ConfirmationRequired -> {
                "This action requires your confirmation."
            }
            is ToolExecutionResult.PermissionRequired -> {
                "Permission is required to execute this action."
            }
            is ToolExecutionResult.Unsupported -> {
                "That action is not supported on this device."
            }
        }
    }
}
