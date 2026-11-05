package com.apex.ai.mobile.voice

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Inspects and selects installed, offline-capable English voices from Android's TextToSpeech engine.
 */
object VoiceSelector {

    private const val TAG = "VoiceSelector"

    /**
     * Discovers all installed English voices and returns detailed metadata.
     */
    fun getInstalledEnglishVoices(tts: TextToSpeech): List<VoiceInfo> {
        val voiceList = mutableListOf<VoiceInfo>()
        try {
            val voices = tts.voices ?: return emptyList()
            for (voice in voices) {
                if (voice.locale.language.equals("en", ignoreCase = true)) {
                    val isOffline = !voice.isNetworkConnectionRequired &&
                            !voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

                    val qualityDesc = when (voice.quality) {
                        Voice.QUALITY_VERY_HIGH -> "Very High (Neural)"
                        Voice.QUALITY_HIGH -> "High"
                        Voice.QUALITY_NORMAL -> "Normal"
                        else -> "Standard"
                    }

                    voiceList.add(
                        VoiceInfo(
                            name = voice.name,
                            locale = "${voice.locale.displayLanguage} (${voice.locale.country})",
                            isOfflineAvailable = isOffline,
                            quality = qualityDesc
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error enumerating TTS voices: ${e.message}")
        }
        return voiceList.sortedByDescending { it.isOfflineAvailable }
    }

    /**
     * Selects the highest quality, offline-capable English voice available on the device.
     */
    fun selectBestEnglishVoice(tts: TextToSpeech, preferredVoiceName: String? = null): Voice? {
        try {
            val voices = tts.voices ?: return null

            // 1. If user explicitly selected a voice in Settings, honor it if available
            if (!preferredVoiceName.isNullOrBlank()) {
                val matched = voices.firstOrNull { it.name.equals(preferredVoiceName, ignoreCase = true) }
                if (matched != null) {
                    Log.d(TAG, "Using preferred English voice: ${matched.name}")
                    return matched
                }
            }

            // 2. Filter for English voices that do not require network connectivity
            val englishVoices = voices.filter { voice ->
                voice.locale.language.equals("en", ignoreCase = true) &&
                        !voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }

            val offlineVoices = englishVoices.filter { !it.isNetworkConnectionRequired }
            val candidates = if (offlineVoices.isNotEmpty()) offlineVoices else englishVoices

            // Rank by quality and US/UK preference
            val bestVoice = candidates.maxByOrNull { voice ->
                var score = voice.quality * 10
                if (!voice.isNetworkConnectionRequired) score += 20
                if (voice.locale.country.equals("US", ignoreCase = true)) score += 5
                if (voice.locale.country.equals("GB", ignoreCase = true)) score += 4
                score
            }

            Log.d(TAG, "Selected best English voice: ${bestVoice?.name} (Offline: ${bestVoice?.isNetworkConnectionRequired == false})")
            return bestVoice
        } catch (e: Exception) {
            Log.w(TAG, "Error selecting best voice: ${e.message}")
            return null
        }
    }

    /**
     * Verifies whether English TTS is supported by the engine.
     */
    fun isEnglishSupported(tts: TextToSpeech): Boolean {
        val result = tts.isLanguageAvailable(Locale.US)
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }
}
