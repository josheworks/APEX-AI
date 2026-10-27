package com.apex.ai.mobile.tools.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import com.apex.ai.mobile.registry.ToolExecutionResult
import com.apex.ai.mobile.registry.ToolHandler

object DeviceSettingsTools {

    val getBatteryStatusHandler = ToolHandler { context, _ ->
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, ifilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 100

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        ToolExecutionResult.Success(
            "get_battery_status",
            mapOf("percentage" to batteryPct, "is_charging" to isCharging),
            "Battery is at $batteryPct% (${if (isCharging) "Charging" else "Discharging"})"
        )
    }

    val getDeviceInfoHandler = ToolHandler { _, _ ->
        val info = mapOf(
            "device" to Build.DEVICE,
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "android_version" to Build.VERSION.RELEASE,
            "sdk_int" to Build.VERSION.SDK_INT,
            "hardware" to Build.HARDWARE,
            "supported_abis" to Build.SUPPORTED_ABIS.toList()
        )
        ToolExecutionResult.Success(
            "get_device_info",
            info,
            "${Build.MANUFACTURER} ${Build.MODEL} running Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
    }

    val getLocationHandler = ToolHandler { context, _ ->
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            return@ToolHandler ToolExecutionResult.Failed("get_location", "NO_SERVICE", "Location service unavailable")
        }

        try {
            val isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!isGpsEnabled && !isNetEnabled) {
                return@ToolHandler ToolExecutionResult.Failed(
                    "get_location",
                    "LOCATION_DISABLED",
                    "Device location services are turned off"
                )
            }

            // Return status and prompt location
            ToolExecutionResult.Success(
                "get_location",
                mapOf("gps_available" to isGpsEnabled, "network_available" to isNetEnabled),
                "Location provider active (GPS: $isGpsEnabled, Network: $isNetEnabled)"
            )
        } catch (e: SecurityException) {
            ToolExecutionResult.PermissionRequired(
                "get_location",
                setOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION),
                "Location permission required to query device coordinates"
            )
        }
    }

    val openWifiSettingsHandler = ToolHandler { context, _ ->
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            ToolExecutionResult.Success(
                "open_wifi_settings",
                mapOf("action" to "opened_wifi_settings"),
                "Opened system Wi-Fi settings"
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failed("open_wifi_settings", "ERROR", e.message ?: "Failed to open Wi-Fi settings", e)
        }
    }

    val openBluetoothSettingsHandler = ToolHandler { context, _ ->
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            ToolExecutionResult.Success(
                "open_bluetooth_settings",
                mapOf("action" to "opened_bluetooth_settings"),
                "Opened system Bluetooth settings"
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failed("open_bluetooth_settings", "ERROR", e.message ?: "Failed to open Bluetooth settings", e)
        }
    }

    val setVolumeHandler = ToolHandler { context, args ->
        val level = args["level"] as? Int ?: 50
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            return@ToolHandler ToolExecutionResult.Failed("set_volume", "NO_AUDIO", "Audio service unavailable")
        }

        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((level.coerceIn(0, 100) / 100f) * max).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)

        ToolExecutionResult.Success(
            "set_volume",
            mapOf("level_pct" to level, "raw_volume" to target, "max" to max),
            "Adjusted media volume to $level%"
        )
    }

    val setBrightnessHandler = ToolHandler { context, args ->
        val level = args["level"] as? Int ?: 50
        // Changing system brightness directly requires WRITE_SETTINGS.
        // We open display settings or adjust window brightness.
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            ToolExecutionResult.Success(
                "set_brightness",
                mapOf("target_level" to level, "action" to "opened_display_settings"),
                "Opened Display Settings to adjust brightness (Target: $level%)"
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failed("set_brightness", "SETTINGS_ERROR", e.message ?: "Failed to open display settings", e)
        }
    }

    val openAccessibilitySettingsHandler = ToolHandler { context, _ ->
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            ToolExecutionResult.Success(
                "open_accessibility_settings",
                mapOf("action" to "opened_accessibility_settings"),
                "Opened Android Accessibility settings. Enable APEX Accessibility Service for screen navigation assistance."
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failed("open_accessibility_settings", "ERROR", e.message ?: "Failed to open accessibility settings", e)
        }
    }
}
