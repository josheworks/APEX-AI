package com.apex.ai.mobile.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.StateFlow

/**
 * Text-to-Speech manager providing high-level playback orchestration, sentence segmentation,
 * and immediate speech interruption handling.
 */
class TTSManager(
    private val context: Context,
    val nativeEngine: NativeTTSEngine = NativeTTSEngine(context)
) {

    val isSpeaking: StateFlow<Boolean> = nativeEngine.isSpeaking
    val isInitialized: StateFlow<Boolean> = nativeEngine.isInitialized
    val availableVoices: StateFlow<List<VoiceInfo>> = nativeEngine.availableVoices

    var onSpeakingCompleted: (() -> Unit)? = null

    init {
        nativeEngine.onSpeechComplete = {
            onSpeakingCompleted?.invoke()
        }
    }

    fun initialize(config: VoiceAssistantConfig = VoiceAssistantConfig()) {
        nativeEngine.initialize(config)
    }

    /**
     * Speaks the assistant response text in English.
     */
    fun speakResponse(englishText: String): Result<Unit> {
        if (englishText.isBlank()) return Result.success(Unit)

        Log.d(TAG, "Speaking response: $englishText")
        return nativeEngine.speak(englishText)
    }

    /**
     * Immediately halts speech synthesis (interruption).
     */
    fun stopSpeaking() {
        nativeEngine.stop()
    }

    fun updateConfig(config: VoiceAssistantConfig) {
        nativeEngine.updateConfig(config)
    }

    fun shutdown() {
        nativeEngine.shutdown()
    }

    companion object {
        private const val TAG = "ApexTTSManager"
    }
}
