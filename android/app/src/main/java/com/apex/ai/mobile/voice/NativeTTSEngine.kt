package com.apex.ai.mobile.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Android Native TextToSpeech engine configured for natural, offline-first English speech.
 */
class NativeTTSEngine(
    private val context: Context
) : TextToSpeech.OnInitListener {

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    val availableVoices: StateFlow<List<VoiceInfo>> = _availableVoices.asStateFlow()

    private var tts: TextToSpeech? = null
    private var config = VoiceAssistantConfig()

    // Completion callback
    var onSpeechComplete: (() -> Unit)? = null

    fun initialize(assistantConfig: VoiceAssistantConfig = VoiceAssistantConfig()) {
        this.config = assistantConfig
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return

            // Ensure English is supported
            val langResult = engine.setLanguage(Locale.US)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Locale.US not supported or missing data, falling back to default English")
                engine.language = Locale.ENGLISH
            }

            // Select best offline voice
            val bestVoice = VoiceSelector.selectBestEnglishVoice(engine, config.selectedVoiceName)
            if (bestVoice != null) {
                try {
                    engine.voice = bestVoice
                } catch (e: Exception) {
                    Log.w(TAG, "Failed setting specific voice: ${e.message}")
                }
            }

            engine.setSpeechRate(config.speechRate)
            engine.setPitch(config.pitch)

            _availableVoices.value = VoiceSelector.getInstalledEnglishVoices(engine)

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    onSpeechComplete?.invoke()
                }

                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    Log.w(TAG, "TTS Utterance error on: $utteranceId")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    Log.w(TAG, "TTS Utterance error on: $utteranceId, code: $errorCode")
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    _isSpeaking.value = false
                }
            })

            _isInitialized.value = true
            Log.i(TAG, "NativeTTSEngine successfully initialized.")
        } else {
            _isInitialized.value = false
            Log.e(TAG, "Failed to initialize Android TextToSpeech (status: $status)")
        }
    }

    /**
     * Speaks the provided text in English.
     * Guaranteed: All non-English inputs are previously formatted to English.
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH): Result<Unit> {
        val engine = tts
        if (engine == null || !_isInitialized.value) {
            return Result.failure(IllegalStateException("TTS engine is not ready."))
        }

        if (text.isBlank()) return Result.success(Unit)

        try {
            // Natural text cleaning: expand numbers, clean technical tokens
            val cleanText = sanitizeForSpeech(text)
            val utteranceId = UUID.randomUUID().toString()

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            val result = engine.speak(cleanText, queueMode, params, utteranceId)
            return if (result == TextToSpeech.SUCCESS) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("TTS speak failed with code: $result"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during TTS speak: ${e.message}", e)
            return Result.failure(e)
        }
    }

    /**
     * Immediately stops current audio playback (for user interruptions).
     */
    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Exception stopping TTS: ${e.message}")
        }
        _isSpeaking.value = false
    }

    fun updateConfig(newConfig: VoiceAssistantConfig) {
        this.config = newConfig
        val engine = tts ?: return
        if (_isInitialized.value) {
            engine.setSpeechRate(newConfig.speechRate)
            engine.setPitch(newConfig.pitch)
            val voice = VoiceSelector.selectBestEnglishVoice(engine, newConfig.selectedVoiceName)
            if (voice != null) {
                engine.voice = voice
            }
        }
    }

    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Exception shutting down TTS: ${e.message}")
        } finally {
            tts = null
            _isInitialized.value = false
            _isSpeaking.value = false
        }
    }

    private fun sanitizeForSpeech(text: String): String {
        return text
            .replace(Regex("<.*?>"), "") // Strip any residual tags
            .replace(Regex("[{}]"), "") // Strip JSON braces
            .replace("Wi-Fi", "Why Fye")
            .replace("APEX", "Apex")
            .trim()
    }

    companion object {
        private const val TAG = "NativeTTSEngine"
    }
}
