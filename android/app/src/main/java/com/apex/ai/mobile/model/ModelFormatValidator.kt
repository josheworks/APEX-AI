package com.apex.ai.mobile.model

import java.io.File
import java.io.InputStream

/**
 * Validates and differentiates FlatBuffer binary identifiers for edge AI models.
 *
 * LiteRT-LM language models (.litertlm) carry the 4-byte FlatBuffer identifier 'RTLM' at offset 4.
 * Standard TensorFlow Lite models (.tflite) carry the 4-byte FlatBuffer identifier 'TFL3' at offset 4.
 *
 * Attempting to load an 'RTLM' model through a standard TensorFlow Lite Interpreter or MediaPipe
 * Task runtime causes the native FlatBuffer verifier to fail with:
 * "Model provided has model identifier 'RTLM', should be 'TFL3'".
 */
object ModelFormatValidator {

    const val IDENTIFIER_LITERTLM = "RTLM"
    const val IDENTIFIER_TFLITE = "TFL3"

    /**
     * Reads the 4-byte FlatBuffer file identifier located at byte offset 4..7.
     */
    fun readModelIdentifier(file: File): String? {
        if (!file.exists() || file.length() < 8) return null
        return try {
            file.inputStream().use { stream ->
                readModelIdentifier(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads the 4-byte FlatBuffer file identifier from an input stream.
     */
    fun readModelIdentifier(stream: InputStream): String? {
        return try {
            val header = ByteArray(8)
            val read = stream.read(header)
            if (read >= 8) {
                String(header, 4, 4, Charsets.US_ASCII)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isLiteRtLm(file: File): Boolean = readModelIdentifier(file) == IDENTIFIER_LITERTLM

    fun isStandardTfLite(file: File): Boolean = readModelIdentifier(file) == IDENTIFIER_TFLITE

    /**
     * Guards standard TensorFlow Lite loaders.
     * Ensures that .litertlm models ('RTLM') are NOT passed to standard TFLite interpreters.
     */
    fun validateForStandardTfLiteInterpreter(file: File) {
        val id = readModelIdentifier(file)
        if (id == IDENTIFIER_LITERTLM) {
            throw IllegalArgumentException(
                "Model provided has model identifier 'RTLM', should be 'TFL3'. " +
                "LiteRT-LM models (.litertlm) are not compatible with standard TensorFlow Lite interpreters. " +
                "They must be loaded using the LiteRT-LM runtime (com.google.ai.edge.litertlm:litertlm-android)."
            )
        }
        if (id != IDENTIFIER_TFLITE) {
            throw IllegalArgumentException("Model provided has model identifier '$id', should be 'TFL3'")
        }
    }

    /**
     * Guards LiteRT-LM engine loaders.
     * Ensures that standard TFLite models ('TFL3') are NOT passed to LiteRT-LM engines.
     */
    fun validateForLiteRtLmEngine(file: File) {
        val id = readModelIdentifier(file)
        if (id == IDENTIFIER_TFLITE) {
            throw IllegalArgumentException(
                "Model provided has model identifier 'TFL3', should be 'RTLM'. " +
                "Standard TensorFlow Lite models cannot be executed by the LiteRT-LM engine. " +
                "Please provide a compiled LiteRT-LM (.litertlm) model."
            )
        }
        if (id != IDENTIFIER_LITERTLM) {
            throw IllegalArgumentException("Model provided has model identifier '$id', should be 'RTLM'")
        }
    }
}
