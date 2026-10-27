package com.apex.ai.mobile.tools.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.apex.ai.mobile.registry.ToolExecutionResult
import com.apex.ai.mobile.registry.ToolHandler

object AppControlTools {

    // Common known app packages
    private val KNOWN_APPS = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "camera" to "com.google.android.GoogleCamera",
        "settings" to "com.android.settings",
        "maps" to "com.google.android.apps.maps",
        "chrome" to "com.android.chrome",
        "calculator" to "com.google.android.calculator"
    )

    val openAppHandler = ToolHandler { context, args ->
        val appName = args["app_name"]?.toString()?.trim()
            ?: return@ToolHandler ToolExecutionResult.Failed(
                "open_app",
                "MISSING_PARAM",
                "Missing 'app_name' parameter"
            )

        val pm = context.packageManager
        val packageName = resolvePackageName(context, appName)

        if (packageName == null) {
            return@ToolHandler ToolExecutionResult.Failed(
                "open_app",
                "APP_NOT_FOUND",
                "Could not find application matching '$appName'"
            )
        }

        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
                ToolExecutionResult.Success(
                    "open_app",
                    mapOf("app_name" to appName, "package_name" to packageName),
                    "Launched $appName successfully"
                )
            } catch (e: Exception) {
                ToolExecutionResult.Failed("open_app", "LAUNCH_ERROR", "Failed to start $appName: ${e.message}", e)
            }
        } else {
            ToolExecutionResult.Failed("open_app", "NO_LAUNCH_INTENT", "App $appName does not expose a launch activity")
        }
    }

    val findAppHandler = ToolHandler { context, args ->
        val query = args["query"]?.toString()?.lowercase() ?: ""
        val resolved = resolvePackageName(context, query)
        if (resolved != null) {
            ToolExecutionResult.Success("find_app", mapOf("query" to query, "package_name" to resolved))
        } else {
            ToolExecutionResult.Failed("find_app", "NOT_FOUND", "No installed application found for '$query'")
        }
    }

    val listAvailableAppsHandler = ToolHandler { context, _ ->
        val pm = context.packageManager
        val installed = KNOWN_APPS.filter { (_, pkg) ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }.keys.toList()

        ToolExecutionResult.Success(
            "list_available_apps",
            mapOf("available_apps" to installed, "count" to installed.size),
            "Found ${installed.size} verified available applications"
        )
    }

    val openAppSettingsHandler = ToolHandler { context, args ->
        val appName = args["app_name"]?.toString() ?: ""
        val pkg = resolvePackageName(context, appName) ?: context.packageName
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkg, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            ToolExecutionResult.Success("open_app_settings", mapOf("package" to pkg), "Opened settings for $pkg")
        } catch (e: Exception) {
            ToolExecutionResult.Failed("open_app_settings", "ERROR", e.message ?: "Failed to open app settings", e)
        }
    }

    val openAppStoreHandler = ToolHandler { context, args ->
        val query = args["query"]?.toString() ?: "com.whatsapp"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$query")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            ToolExecutionResult.Success("open_app_store", mapOf("target" to query), "Opened app store for $query")
        } catch (_: Exception) {
            // Fallback to browser Play Store
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$query")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            ToolExecutionResult.Success("open_app_store", mapOf("target" to query), "Opened web app store for $query")
        }
    }

    private fun resolvePackageName(context: Context, name: String): String? {
        val lower = name.lowercase().trim()
        if (KNOWN_APPS.containsKey(lower)) {
            return KNOWN_APPS[lower]
        }
        // Check if passed string is already a package name
        try {
            context.packageManager.getPackageInfo(name, 0)
            return name
        } catch (_: Exception) {
        }
        return null
    }
}
