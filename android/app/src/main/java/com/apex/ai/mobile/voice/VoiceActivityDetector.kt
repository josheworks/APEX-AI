package com.apex.ai.mobile.voice

import kotlin.math.sqrt

/**
 * Energy and RMS-based Voice Activity Detector (VAD) optimized for on-device speech boundary detection.
 * Operates on 16kHz PCM chunks to identify when speech commences and automatically conclude
 * recording after a configurable duration of silence.
 */
class VoiceActivityDetector(
    private var speechThresholdRms: Float = 0.025f,
    private var silenceTimeoutMs: Long = 1400L,
    private var minSpeechDurationMs: Long = 300L
) {

    private var speechStartTimeMs: Long = 0L
    private var lastSpeechTimeMs: Long = 0L
    private var isSpeechActive = false
    private var consecutiveSpeechFrames = 0
    private var consecutiveSilenceFrames = 0

    // Callback listeners
    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechEnded: ((speechDurationMs: Long) -> Unit)? = null

    /**
     * Resets internal VAD state for a fresh listening turn.
     */
    fun reset() {
        speechStartTimeMs = 0L
        lastSpeechTimeMs = 0L
        isSpeechActive = false
        consecutiveSpeechFrames = 0
        consecutiveSilenceFrames = 0
    }

    /**
     * Updates VAD configuration parameters.
     */
    fun updateConfig(silenceTimeout: Long, threshold: Float = speechThresholdRms) {
        this.silenceTimeoutMs = silenceTimeout
        this.speechThresholdRms = threshold
    }

    /**
     * Calculates Root Mean Square (RMS) energy normalized between 0.0 and 1.0.
     */
    fun calculateRms(chunk: ShortArray): Float {
        if (chunk.isEmpty()) return 0f
        var sumSquares = 0.0
        for (sample in chunk) {
            sumSquares += sample * sample
        }
        return (sqrt(sumSquares / chunk.size) / 32768.0).toFloat()
    }

    /**
     * Determines whether an individual chunk exceeds the active speech threshold.
     */
    fun isSpeechFrame(chunk: ShortArray): Boolean {
        return calculateRms(chunk) >= speechThresholdRms
    }

    /**
     * Processes an incoming 16-bit PCM chunk from AudioRecorder.
     *
     * @param chunk 16-bit PCM samples
     * @return Pair(isCurrentlySpeech: Boolean, hasSpeechEnded: Boolean)
     */
    fun processChunk(chunk: ShortArray): Pair<Boolean, Boolean> {
        if (chunk.isEmpty()) return Pair(isSpeechActive, false)

        val rms = calculateRms(chunk)
        val now = System.currentTimeMillis()

        val isFrameSpeech = rms >= speechThresholdRms

        if (isFrameSpeech) {
            consecutiveSpeechFrames++
            consecutiveSilenceFrames = 0
            lastSpeechTimeMs = now

            if (!isSpeechActive && consecutiveSpeechFrames >= REQUIRED_SPEECH_FRAMES) {
                isSpeechActive = true
                speechStartTimeMs = now
                onSpeechStarted?.invoke()
            }
        } else {
            consecutiveSilenceFrames++
            consecutiveSpeechFrames = 0

            if (isSpeechActive) {
                val silenceDuration = now - lastSpeechTimeMs
                val totalSpeechDuration = now - speechStartTimeMs

                if (silenceDuration >= silenceTimeoutMs && totalSpeechDuration >= minSpeechDurationMs) {
                    isSpeechActive = false
                    val duration = lastSpeechTimeMs - speechStartTimeMs
                    onSpeechEnded?.invoke(duration)
                    reset()
                    return Pair(false, true)
                }
            }
        }

        return Pair(isSpeechActive, false)
    }

    companion object {
        private const val REQUIRED_SPEECH_FRAMES = 2 // ~120ms of sustained energy to trigger speech
    }
}
