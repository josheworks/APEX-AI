package com.apex.ai.mobile.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Unified controller implementing the complete APEX Mobile Voice Pipeline:
 *
 * Microphone → AudioRecorder → VAD → STTManager → TranscriptionNormalizer →
 * FunctionGemma 270M → Safe Tool Registry → Action Execution → English Response → TTSManager → Speaker
 */
class VoiceAssistantController(
    private val context: Context,
    private val scope: CoroutineScope,
    val audioRecorder: AudioRecorder = AudioRecorder(context),
    val vad: VoiceActivityDetector = VoiceActivityDetector(),
    val sttManager: STTManager = STTManager(context),
    val ttsManager: TTSManager = TTSManager(context)
) {

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _currentTranscription = MutableStateFlow("")
    val currentTranscription: StateFlow<String> = _currentTranscription.asStateFlow()

    private val _lastSpokenResponse = MutableStateFlow("")
    val lastSpokenResponse: StateFlow<String> = _lastSpokenResponse.asStateFlow()

    private val _config = MutableStateFlow(VoiceAssistantConfig())
    val config: StateFlow<VoiceAssistantConfig> = _config.asStateFlow()

    private var vadJob: Job? = null

    // Callback invoked when a validated transcription is ready for FunctionGemma processing
    var onCommandRecognized: (suspend (normalizedText: String, detectedLang: DetectedInputLanguage) -> String)? = null

    init {
        // Configure VAD callbacks
        vad.onSpeechEnded = { durationMs ->
            Log.d(TAG, "VAD detected end of speech ($durationMs ms). Processing transcription.")
            scope.launch(Dispatchers.Main) {
                concludeVoiceTurn()
            }
        }

        // Configure TTS completion callback
        ttsManager.onSpeakingCompleted = {
            scope.launch(Dispatchers.Main) {
                if (_config.value.interactionMode == VoiceInteractionMode.CONTINUOUS) {
                    Log.d(TAG, "Continuous mode active. Restarting voice listening.")
                    startListening()
                } else {
                    _voiceState.value = VoiceState.IDLE
                }
            }
        }
    }

    suspend fun initialize(): Result<Unit> {
        sttManager.initialize()
        ttsManager.initialize(_config.value)
        _voiceState.value = VoiceState.IDLE
        return Result.success(Unit)
    }

    /**
     * Toggles voice listening session.
     */
    fun toggleListening() {
        when (_voiceState.value) {
            VoiceState.IDLE, VoiceState.ERROR -> startListening()
            VoiceState.LISTENING -> concludeVoiceTurn()
            VoiceState.SPEAKING -> stopSpeaking()
            else -> cancelAll()
        }
    }

    /**
     * Starts listening for speech input.
     * Interrupts any ongoing TTS playback immediately.
     */
    fun startListening() {
        // Immediate user interruption
        ttsManager.stopSpeaking()

        if (!audioRecorder.hasRecordAudioPermission) {
            _voiceState.value = VoiceState.ERROR
            _currentTranscription.value = "Microphone permission required."
            return
        }

        _voiceState.value = VoiceState.LISTENING
        _currentTranscription.value = ""
        vad.reset()

        // Start AudioRecorder and VAD monitoring
        audioRecorder.startRecording(scope)

        vadJob?.cancel()
        vadJob = scope.launch(Dispatchers.Default) {
            audioRecorder.audioChunks.collect { chunk ->
                if (_voiceState.value == VoiceState.LISTENING) {
                    vad.processChunk(chunk)
                }
            }
        }

        // Start SpeechRecognizer for real-time transcription
        sttManager.startNativeListening(
            onResult = { result ->
                scope.launch(Dispatchers.Main) {
                    handleTranscriptionResult(result)
                }
            },
            onError = { errMsg ->
                scope.launch(Dispatchers.Main) {
                    if (_voiceState.value == VoiceState.LISTENING) {
                        _voiceState.value = VoiceState.ERROR
                        _currentTranscription.value = errMsg
                    }
                }
            }
        )
    }

    /**
     * Concludes voice recording and advances to transcription.
     */
    fun concludeVoiceTurn() {
        if (_voiceState.value != VoiceState.LISTENING) return

        _voiceState.value = VoiceState.TRANSCRIBING
        vadJob?.cancel()
        vadJob = null
        sttManager.stopListening()

        val audioFloats = audioRecorder.stopRecording()

        // If Whisper model is ready, transcribe recorded PCM buffer
        if (sttManager.whisperEngine.isReady.value && audioFloats.isNotEmpty()) {
            scope.launch(Dispatchers.Default) {
                val whisperResult = sttManager.transcribeAudioSamples(audioFloats)
                whisperResult.onSuccess { result ->
                    scope.launch(Dispatchers.Main) {
                        handleTranscriptionResult(result)
                    }
                }
            }
        }
    }

    /**
     * Dispatches transcription result through the Understanding and Execution pipeline.
     */
    private suspend fun handleTranscriptionResult(result: SpeechTranscriptionResult) {
        _currentTranscription.value = result.rawText
        _voiceState.value = VoiceState.UNDERSTANDING

        val commandProcessor = onCommandRecognized
        if (commandProcessor != null) {
            _voiceState.value = VoiceState.EXECUTING
            val englishResponse = commandProcessor(result.normalizedText, result.detectedLanguage)

            _lastSpokenResponse.value = englishResponse
            _voiceState.value = VoiceState.SPEAKING

            if (_config.value.autoSpeakResponses && englishResponse.isNotBlank()) {
                ttsManager.speakResponse(englishResponse)
            } else {
                _voiceState.value = VoiceState.IDLE
            }
        } else {
            _voiceState.value = VoiceState.IDLE
        }
    }

    /**
     * Stops ongoing speech playback.
     */
    fun stopSpeaking() {
        ttsManager.stopSpeaking()
        _voiceState.value = VoiceState.IDLE
    }

    /**
     * Cancels all voice operations and resets to IDLE.
     */
    fun cancelAll() {
        vadJob?.cancel()
        vadJob = null
        audioRecorder.cancelRecording()
        sttManager.cancel()
        ttsManager.stopSpeaking()
        _voiceState.value = VoiceState.IDLE
    }

    fun updateConfig(newConfig: VoiceAssistantConfig) {
        _config.value = newConfig
        vad.updateConfig(newConfig.vadSilenceTimeoutMs)
        ttsManager.updateConfig(newConfig)
    }

    fun shutdown() {
        cancelAll()
        sttManager.release()
        ttsManager.shutdown()
    }

    companion object {
        private const val TAG = "VoiceController"
    }
}
