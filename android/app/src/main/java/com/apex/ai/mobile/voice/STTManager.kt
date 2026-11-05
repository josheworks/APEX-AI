package com.apex.ai.mobile.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Speech-to-Text Manager orchestrating on-device offline recognition.
 *
 * Employs:
 * 1. WhisperEngine (Whisper Multilingual Base / Tiny) when model weights are loaded.
 * 2. Android On-Device SpeechRecognizer (configured with EXTRA_PREFER_OFFLINE and Tamil/English locales)
 *    as a seamless offline fallback.
 */
class STTManager(
    private val context: Context,
    val whisperEngine: WhisperEngine = WhisperEngine(context)
) {

    private val _isRecognizing = MutableStateFlow(false)
    val isRecognizing: StateFlow<Boolean> = _isRecognizing.asStateFlow()

    private val _lastTranscription = MutableStateFlow<SpeechTranscriptionResult?>(null)
    val lastTranscription: StateFlow<SpeechTranscriptionResult?> = _lastTranscription.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Initializes STT engines and checks model availability.
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        val whisperResult = whisperEngine.initialize()
        if (whisperResult.isSuccess) {
            Log.i(TAG, "STTManager initialized with primary WhisperEngine.")
        } else {
            Log.i(TAG, "Whisper model not yet found; Android offline SpeechRecognizer active as fallback.")
        }
        Result.success(Unit)
    }

    /**
     * Transcribes pre-recorded 16kHz PCM float samples (from AudioRecorder).
     */
    suspend fun transcribeAudioSamples(samples: FloatArray): Result<SpeechTranscriptionResult> {
        if (whisperEngine.isReady.value) {
            val result = whisperEngine.transcribe(samples)
            if (result.isSuccess) {
                _lastTranscription.value = result.getOrNull()
                return result
            }
        }
        return Result.failure(IllegalStateException("Whisper engine not ready for raw sample decoding."))
    }

    /**
     * Starts on-device recognition using Android Native SpeechRecognizer with offline preference.
     * Supports mixed English / Tamil recognition.
     */
    fun startNativeListening(
        onResult: (SpeechTranscriptionResult) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Android SpeechRecognizer is not available on this device.")
            return
        }

        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("en-US", "ta-IN"))
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isRecognizing.value = true
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _isRecognizing.value = false
                }

                override fun onError(error: Int) {
                    _isRecognizing.value = false
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected before timeout."
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                        else -> "Speech recognition error (Code $error)."
                    }
                    Log.w(TAG, "SpeechRecognizer error: $errorMsg")
                    onError(errorMsg)
                }

                override fun onResults(results: Bundle?) {
                    _isRecognizing.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    val bestText = matches?.firstOrNull() ?: ""

                    if (bestText.isNotBlank()) {
                        val confidence = confidences?.firstOrNull() ?: 0.90f
                        val (normalized, lang) = TranscriptionNormalizer.normalizeInput(bestText)
                        val result = SpeechTranscriptionResult(
                            rawText = bestText,
                            normalizedText = normalized,
                            detectedLanguage = lang,
                            confidence = confidence,
                            isFinal = true
                        )
                        _lastTranscription.value = result
                        onResult(result)
                    } else {
                        onError("No intelligible speech detected.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = partials?.firstOrNull() ?: return
                    if (partialText.isNotBlank()) {
                        val (normalized, lang) = TranscriptionNormalizer.normalizeInput(partialText)
                        val partialResult = SpeechTranscriptionResult(
                            rawText = partialText,
                            normalizedText = normalized,
                            detectedLanguage = lang,
                            confidence = 0.5f,
                            isFinal = false
                        )
                        _lastTranscription.value = partialResult
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
        } catch (e: Exception) {
            _isRecognizing.value = false
            Log.e(TAG, "Exception starting speech recognition: ${e.message}", e)
            onError("Failed to start speech recognition: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Exception stopping SpeechRecognizer: ${e.message}")
        }
        _isRecognizing.value = false
    }

    fun cancel() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Exception cancelling SpeechRecognizer: ${e.message}")
        } finally {
            speechRecognizer = null
            _isRecognizing.value = false
        }
    }

    fun release() {
        cancel()
        whisperEngine.release()
    }

    companion object {
        private const val TAG = "ApexSTTManager"
    }
}
