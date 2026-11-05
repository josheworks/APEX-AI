package com.apex.ai.mobile.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * High-performance, offline PCM audio recorder configured for 16 kHz 16-bit Mono.
 * Designed for on-device Voice Activity Detection and Whisper STT inference.
 */
class AudioRecorder(
    private val context: Context
) {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _audioChunks = MutableSharedFlow<ShortArray>(extraBufferCapacity = 64)
    val audioChunks: SharedFlow<ShortArray> = _audioChunks.asSharedFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val recordedPcmStream = ByteArrayOutputStream()

    val hasRecordAudioPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Starts audio capture session on Dispatchers.IO.
     * Throws SecurityException if microphone permission is not granted.
     */
    fun startRecording(scope: CoroutineScope): Result<Unit> {
        if (!hasRecordAudioPermission) {
            val err = SecurityException("Microphone permission (RECORD_AUDIO) not granted.")
            Log.e(TAG, "Cannot start AudioRecorder: ${err.message}")
            return Result.failure(err)
        }

        if (_isRecording.value) {
            return Result.success(Unit)
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                return Result.failure(IllegalStateException("Invalid AudioRecord buffer size calculated: $minBufferSize"))
            }

            val bufferSize = (minBufferSize * 2).coerceAtLeast(BUFFER_SIZE_FRAMES * 2)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return Result.failure(IllegalStateException("Failed to initialize AudioRecord native instance."))
            }

            audioRecord = record
            record.startRecording()
            _isRecording.value = true

            synchronized(recordedPcmStream) {
                recordedPcmStream.reset()
            }

            recordingJob = scope.launch(Dispatchers.IO) {
                val shortBuffer = ShortArray(BUFFER_SIZE_FRAMES)
                val byteBuffer = ByteBuffer.allocate(BUFFER_SIZE_FRAMES * 2).order(ByteOrder.LITTLE_ENDIAN)

                try {
                    while (isActive && _isRecording.value) {
                        val shortsRead = record.read(shortBuffer, 0, shortBuffer.size)
                        if (shortsRead > 0) {
                            val chunk = shortBuffer.copyOf(shortsRead)
                            _audioChunks.tryEmit(chunk)

                            // Calculate RMS energy for UI audio level indicator (0.0 to 1.0)
                            var sumSquares = 0.0
                            for (sample in chunk) {
                                sumSquares += sample * sample
                            }
                            val rms = sqrt(sumSquares / shortsRead)
                            val normalizedLevel = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                            _audioLevel.value = normalizedLevel

                            // Append to in-memory PCM stream
                            byteBuffer.clear()
                            for (sample in chunk) {
                                byteBuffer.putShort(sample)
                            }
                            synchronized(recordedPcmStream) {
                                recordedPcmStream.write(byteBuffer.array(), 0, shortsRead * 2)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Audio recording loop error: ${e.message}", e)
                } finally {
                    cleanUpNativeRecord()
                }
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing AudioRecorder: ${e.message}", e)
            cleanUpNativeRecord()
            return Result.failure(e)
        }
    }

    /**
     * Stops audio capture session and returns the recorded PCM audio as FloatArray
     * normalized to [-1.0f, 1.0f] (format required by Whisper speech models).
     */
    fun stopRecording(): FloatArray {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        cleanUpNativeRecord()
        _audioLevel.value = 0f

        return synchronized(recordedPcmStream) {
            val bytes = recordedPcmStream.toByteArray()
            recordedPcmStream.reset()
            pcm16BytesToNormalizedFloatArray(bytes)
        }
    }

    /**
     * Discards any recorded audio buffer and stops recording.
     */
    fun cancelRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        cleanUpNativeRecord()
        _audioLevel.value = 0f
        synchronized(recordedPcmStream) {
            recordedPcmStream.reset()
        }
    }

    private fun cleanUpNativeRecord() {
        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception cleaning up AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }
    }

    companion object {
        private const val TAG = "ApexAudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE_FRAMES = 1024 // ~64ms per chunk at 16kHz

        fun pcm16BytesToNormalizedFloatArray(pcmBytes: ByteArray): FloatArray {
            val floatCount = pcmBytes.size / 2
            val floats = FloatArray(floatCount)
            val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until floatCount) {
                floats[i] = buffer.short / 32768.0f
            }
            return floats
        }

        fun shortsToNormalizedFloatArray(shorts: ShortArray): FloatArray {
            val floats = FloatArray(shorts.size)
            for (i in shorts.indices) {
                floats[i] = shorts[i] / 32768.0f
            }
            return floats
        }
    }
}
