package com.apex.ai.mobile.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

sealed class ModelImportStatus {
    object Idle : ModelImportStatus()
    data class Importing(
        val progress: Float,
        val bytesCopied: Long,
        val totalBytes: Long,
        val filename: String = ""
    ) : ModelImportStatus()
    data class Success(val file: File, val sizeBytes: Long) : ModelImportStatus()
    data class Error(val message: String, val resolutionGuide: String = "") : ModelImportStatus()
}

class ModelImporter(
    private val context: Context
) {
    private val _importStatus = MutableStateFlow<ModelImportStatus>(ModelImportStatus.Idle)
    val importStatus: StateFlow<ModelImportStatus> = _importStatus.asStateFlow()

    /**
     * Imports a model from an Android SAF Content Uri into the app's private files/models directory.
     */
    suspend fun importModelFromUri(
        sourceUri: Uri,
        targetFileName: String = "mobile_actions_q8_ekv1024.litertlm"
    ): Result<File> = withContext(Dispatchers.IO) {
        var declaredSize: Long = -1L
        var sourceDisplayName: String = targetFileName

        try {
            context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) {
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        declaredSize = cursor.getLong(sizeIndex)
                    }
                    if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                        sourceDisplayName = cursor.getString(nameIndex) ?: sourceDisplayName
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback if content provider rejects metadata query
        }

        val targetDir = File(context.filesDir, "models")
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(IOException("Unable to open input stream from selected URI: $sourceUri"))

            inputStream.use { stream ->
                importModelFromStream(
                    inputStream = stream,
                    totalBytes = declaredSize,
                    sourceDisplayName = sourceDisplayName,
                    targetFileName = targetFileName,
                    targetDir = targetDir
                )
            }
        } catch (e: CancellationException) {
            _importStatus.value = ModelImportStatus.Error("Model import was cancelled by user.")
            throw e
        } catch (e: Exception) {
            val msg = "Import failed: ${e.message}"
            _importStatus.value = ModelImportStatus.Error(
                message = msg,
                resolutionGuide = "Ensure the file in Downloads is accessible and you have sufficient free storage."
            )
            Result.failure(e)
        }
    }

    /**
     * Core streaming copy and validation logic, usable by both SAF URI and unit tests.
     */
    suspend fun importModelFromStream(
        inputStream: InputStream,
        totalBytes: Long,
        sourceDisplayName: String = "model",
        targetFileName: String = "mobile_actions_q8_ekv1024.litertlm",
        targetDir: File = File(context.filesDir, "models"),
        minimumSizeBytes: Long = 1024L * 1024L // 1MB minimum for LLM weights
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!targetDir.exists()) {
            val created = targetDir.mkdirs()
            if (!created && !targetDir.exists()) {
                val err = "Failed to create target directory: ${targetDir.absolutePath}"
                _importStatus.value = ModelImportStatus.Error(err)
                return@withContext Result.failure(IOException(err))
            }
        }

        // Validate storage availability before streaming
        val usableSpace = targetDir.usableSpace
        val requiredSafetyMargin = 50L * 1024L * 1024L // 50MB reserve
        if (totalBytes > 0 && usableSpace > 0 && usableSpace < (totalBytes + requiredSafetyMargin)) {
            val neededMb = totalBytes / (1024 * 1024)
            val availableMb = usableSpace / (1024 * 1024)
            val err = "Insufficient storage space: Model requires ${neededMb}MB, but only ${availableMb}MB is available."
            _importStatus.value = ModelImportStatus.Error(
                message = err,
                resolutionGuide = "Free up storage space on your device (at least ${neededMb + 50}MB) and retry."
            )
            return@withContext Result.failure(IOException(err))
        }

        val tempFile = File(targetDir, "import_${System.currentTimeMillis()}.tmp")
        val destFile = File(targetDir, targetFileName)

        _importStatus.value = ModelImportStatus.Importing(
            progress = 0f,
            bytesCopied = 0L,
            totalBytes = totalBytes,
            filename = sourceDisplayName
        )

        try {
            FileOutputStream(tempFile).use { outputStream ->
                val buffer = ByteArray(64 * 1024) // 64KB buffer
                var bytesCopied: Long = 0
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    ensureActive() // Check for coroutine cancellation
                    outputStream.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead

                    val progress = if (totalBytes > 0) {
                        (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0.5f // Indeterminate progress if size unknown
                    }

                    _importStatus.value = ModelImportStatus.Importing(
                        progress = progress,
                        bytesCopied = bytesCopied,
                        totalBytes = totalBytes,
                        filename = sourceDisplayName
                    )
                }
                outputStream.flush()
            }

            // Post-copy verification
            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                val err = "Imported model file is empty (0 bytes)."
                _importStatus.value = ModelImportStatus.Error(
                    message = err,
                    resolutionGuide = "The source file in Downloads is empty. Please re-download the .litertlm file."
                )
                return@withContext Result.failure(IllegalStateException(err))
            }

            if (tempFile.length() < minimumSizeBytes) {
                val sizeBytes = tempFile.length()
                tempFile.delete()
                val err = "Selected file size ($sizeBytes bytes) is too small to be a valid on-device LLM model."
                _importStatus.value = ModelImportStatus.Error(
                    message = err,
                    resolutionGuide = "Expected FunctionGemma INT8 weights (~288MB). Please verify you selected mobile_actions_q8_ekv1024.litertlm."
                )
                return@withContext Result.failure(IllegalStateException(err))
            }

            // Replace destination atomically
            if (destFile.exists()) {
                destFile.delete()
            }

            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }

            val finalSize = destFile.length()
            _importStatus.value = ModelImportStatus.Success(destFile, finalSize)
            Result.success(destFile)
        } catch (e: CancellationException) {
            tempFile.delete()
            _importStatus.value = ModelImportStatus.Error("Model import was cancelled.")
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            val msg = "Copy error: ${e.message}"
            _importStatus.value = ModelImportStatus.Error(msg, "Check file access permissions and storage.")
            Result.failure(e)
        }
    }

    fun resetStatus() {
        _importStatus.value = ModelImportStatus.Idle
    }
}
