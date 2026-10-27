package com.apex.ai.mobile.tools.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.core.content.FileProvider
import com.apex.ai.mobile.registry.ToolExecutionResult
import com.apex.ai.mobile.registry.ToolHandler
import java.io.File

object FilesSharingTools {

    val findFileHandler = ToolHandler { context, args ->
        val mimeType = args["mime_type"]?.toString() ?: "*/*"
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            ToolExecutionResult.Success(
                "find_file",
                mapOf("mime_type" to mimeType, "status" to "picker_opened"),
                "Opened system file selector for '$mimeType'"
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failed("find_file", "PICKER_ERROR", e.message ?: "Failed to open document picker", e)
        }
    }

    val readSelectedFileHandler = ToolHandler { context, args ->
        val uriString = args["uri"]?.toString() ?: ""
        if (uriString.isBlank()) {
            return@ToolHandler ToolExecutionResult.Failed("read_selected_file", "MISSING_URI", "No file URI provided")
        }

        try {
            val uri = Uri.parse(uriString)
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: return@ToolHandler ToolExecutionResult.Failed("read_selected_file", "STREAM_ERROR", "Cannot open stream for URI")

            val bytes = inputStream.use { it.readBytes() }
            if (bytes.size > 1024 * 512) {
                // Truncate preview to prevent memory blowup on 270M model
                val preview = String(bytes.take(2048).toByteArray(), Charsets.UTF_8)
                ToolExecutionResult.Success(
                    "read_selected_file",
                    mapOf("size_bytes" to bytes.size, "preview" to preview, "truncated" to true),
                    "Read file (${bytes.size} bytes). Showing first 2KB preview."
                )
            } else {
                val text = String(bytes, Charsets.UTF_8)
                ToolExecutionResult.Success(
                    "read_selected_file",
                    mapOf("size_bytes" to bytes.size, "content" to text, "truncated" to false),
                    "Read file (${bytes.size} bytes) successfully."
                )
            }
        } catch (e: Exception) {
            ToolExecutionResult.Failed("read_selected_file", "READ_ERROR", "Failed to read file: ${e.message}", e)
        }
    }

    val createNoteHandler = ToolHandler { context, args ->
        val title = args["title"]?.toString()?.trim() ?: "Note_${System.currentTimeMillis()}"
        val content = args["content"]?.toString() ?: ""

        try {
            val notesDir = File(context.filesDir, "notes")
            if (!notesDir.exists()) notesDir.mkdirs()

            val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val noteFile = File(notesDir, "$sanitizedTitle.txt")
            noteFile.writeText(content, Charsets.UTF_8)

            ToolExecutionResult.Success(
                "create_note",
                mapOf("title" to title, "path" to noteFile.absolutePath, "size" to content.length),
                "Saved note '$title' successfully"
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failed("create_note", "IO_ERROR", e.message ?: "Failed to write note", e)
        }
    }

    val copyFileHandler = ToolHandler { context, args ->
        val sourcePath = args["source"]?.toString() ?: ""
        val targetPath = args["target"]?.toString() ?: ""

        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) {
            return@ToolHandler ToolExecutionResult.Failed("copy_file", "NOT_FOUND", "Source file does not exist")
        }

        try {
            val targetFile = File(targetPath)
            targetFile.parentFile?.mkdirs()
            sourceFile.copyTo(targetFile, overwrite = true)
            ToolExecutionResult.Success("copy_file", mapOf("source" to sourcePath, "target" to targetPath), "Copied file to $targetPath")
        } catch (e: Exception) {
            ToolExecutionResult.Failed("copy_file", "COPY_ERROR", e.message ?: "Failed to copy file", e)
        }
    }

    val shareFileHandler = ToolHandler { context, args ->
        val filePath = args["file_path"]?.toString() ?: ""
        val file = File(filePath)
        if (!file.exists()) {
            return@ToolHandler ToolExecutionResult.Failed("share_file", "NOT_FOUND", "File not found at $filePath")
        }

        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(shareIntent, "Share ${file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(chooser)
            ToolExecutionResult.Success("share_file", mapOf("file" to file.name), "Opened share sheet for ${file.name}")
        } catch (e: Exception) {
            ToolExecutionResult.Failed("share_file", "SHARE_ERROR", e.message ?: "Failed to share file", e)
        }
    }

    val getStorageSummaryHandler = ToolHandler { context, _ ->
        val internalStat = StatFs(context.filesDir.absolutePath)
        val availableBytes = internalStat.availableBlocksLong * internalStat.blockSizeLong
        val totalBytes = internalStat.blockCountLong * internalStat.blockSizeLong
        val availableMb = availableBytes / (1024 * 1024)
        val totalMb = totalBytes / (1024 * 1024)

        ToolExecutionResult.Success(
            "get_storage_summary",
            mapOf("available_mb" to availableMb, "total_mb" to totalMb, "used_mb" to (totalMb - availableMb)),
            "Internal Storage: ${availableMb}MB free of ${totalMb}MB total"
        )
    }
}
