package com.apex.ai.mobile.tools.apk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.apex.ai.mobile.registry.ToolExecutionResult
import com.apex.ai.mobile.registry.ToolHandler
import java.io.File

object ApkManagementTools {

    val findApkHandler = ToolHandler { context, _ ->
        // Directs to SAF file picker for APK selection
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.android.package-archive"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            ToolExecutionResult.Success(
                "find_apk",
                mapOf("status" to "saf_picker_opened"),
                "Opened system document picker. Please select the APK file to inspect or install."
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failed("find_apk", "PICKER_FAILED", e.message ?: "Failed to open document picker", e)
        }
    }

    val inspectApkHandler = ToolHandler { context, args ->
        val filePath = args["file_path"]?.toString() ?: ""
        val file = File(filePath)
        if (!file.exists()) {
            return@ToolHandler ToolExecutionResult.Failed("inspect_apk", "FILE_NOT_FOUND", "APK file does not exist at $filePath")
        }

        val pm = context.packageManager
        val archiveInfo = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS)
        if (archiveInfo != null) {
            val appInfo = archiveInfo.applicationInfo
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath
            val appName = pm.getApplicationLabel(appInfo).toString()
            val packageName = archiveInfo.packageName
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode.toLong()
            }
            val permissions = archiveInfo.requestedPermissions?.toList() ?: emptyList()

            ToolExecutionResult.Success(
                "inspect_apk",
                mapOf(
                    "app_name" to appName,
                    "package_name" to packageName,
                    "version_name" to (archiveInfo.versionName ?: "unknown"),
                    "version_code" to versionCode,
                    "permissions_count" to permissions.size,
                    "requested_permissions" to permissions.take(10)
                ),
                "APK metadata inspected: $appName ($packageName v${archiveInfo.versionName})"
            )
        } else {
            ToolExecutionResult.Failed("inspect_apk", "PARSE_FAILED", "Failed to parse APK package metadata. File may be corrupted.")
        }
    }

    val requestAppInstallHandler = ToolHandler { context, args ->
        val filePath = args["file_path"]?.toString() ?: ""
        val file = File(filePath)

        if (!file.exists()) {
            return@ToolHandler ToolExecutionResult.Failed("request_app_install", "FILE_NOT_FOUND", "APK not found at $filePath")
        }

        // Check if unknown app sources is allowed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return@ToolHandler ToolExecutionResult.PermissionRequired(
                    "request_app_install",
                    setOf(android.Manifest.permission.REQUEST_INSTALL_PACKAGES),
                    "Android requires explicit user permission to install packages from unknown sources. Please toggle permission in Settings."
                )
            }
        }

        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(installIntent)
            ToolExecutionResult.Success(
                "request_app_install",
                mapOf("uri" to contentUri.toString(), "path" to filePath),
                "Dispatched system package installer for ${file.name}. Follow on-screen confirmation."
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failed("request_app_install", "INSTALLER_FAILED", "Could not start installer: ${e.message}", e)
        }
    }

    val openInstallPermissionsHandler = ToolHandler { context, _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult.Success("open_install_permissions", mapOf("target" to "unknown_app_sources"), "Opened Install Unknown Apps settings")
        } else {
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult.Success("open_install_permissions", mapOf("target" to "security_settings"), "Opened Security Settings")
        }
    }

    val requestAppUninstallHandler = ToolHandler { context, args ->
        val packageName = args["package_name"]?.toString() ?: ""
        if (packageName.isBlank()) {
            return@ToolHandler ToolExecutionResult.Failed("request_app_uninstall", "INVALID_PARAM", "Missing package_name")
        }

        val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(uninstallIntent)
            ToolExecutionResult.Success(
                "request_app_uninstall",
                mapOf("package_name" to packageName),
                "Requested Android uninstallation prompt for $packageName"
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failed("request_app_uninstall", "UNINSTALL_FAILED", e.message ?: "Failed to prompt uninstallation", e)
        }
    }

    val checkInstalledAppHandler = ToolHandler { context, args ->
        val packageName = args["package_name"]?.toString() ?: ""
        val pm = context.packageManager
        try {
            val info = pm.getPackageInfo(packageName, 0)
            ToolExecutionResult.Success(
                "check_installed_app",
                mapOf("installed" to true, "package_name" to packageName, "version" to info.versionName),
                "Application $packageName is installed (v${info.versionName})"
            )
        } catch (_: PackageManager.NameNotFoundException) {
            ToolExecutionResult.Success(
                "check_installed_app",
                mapOf("installed" to false, "package_name" to packageName),
                "Application $packageName is NOT installed"
            )
        }
    }
}
