package com.apex.ai.mobile.voice

/**
 * State machine stages for the APEX Mobile Voice Assistant pipeline.
 */
enum class VoiceState {
    IDLE,
    LISTENING,
    TRANSCRIBING,
    UNDERSTANDING,
    EXECUTING,
    GENERATING_RESPONSE,
    SPEAKING,
    ERROR
}

/**
 * Supported interaction modes for voice input.
 */
enum class VoiceInteractionMode {
    PUSH_TO_TALK,
    CONTINUOUS
}

/**
 * Detected speech input category.
 */
enum class DetectedInputLanguage {
    ENGLISH,
    TAMIL,
    TANGLISH,
    MIXED
}

/**
 * Transcription result carrying normalized text, confidence score, and language metadata.
 */
data class SpeechTranscriptionResult(
    val rawText: String,
    val normalizedText: String,
    val detectedLanguage: DetectedInputLanguage,
    val confidence: Float,
    val latencyMs: Long = 0L,
    val isFinal: Boolean = true
)

/**
 * Configuration for voice assistant operation and text-to-speech.
 */
data class VoiceAssistantConfig(
    val interactionMode: VoiceInteractionMode = VoiceInteractionMode.PUSH_TO_TALK,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val selectedVoiceName: String? = null,
    val preferOfflineOnly: Boolean = true,
    val vadSilenceTimeoutMs: Long = 1400L,
    val autoSpeakResponses: Boolean = true
)

/**
 * Information regarding an installed English TTS voice.
 */
data class VoiceInfo(
    val name: String,
    val locale: String,
    val isOfflineAvailable: Boolean,
    val quality: String
)
