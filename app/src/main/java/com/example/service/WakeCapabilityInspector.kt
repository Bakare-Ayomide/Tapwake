package com.example.service

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

data class WakeCapabilityReport(
    val deviceModel: String,
    val manufacturer: String,
    val androidRelease: String,
    val sdkInt: Int,
    val isGalaxyS9Family: Boolean,
    val systemDoubleTapSettingFound: Boolean,
    val systemDoubleTapSettingEnabled: Boolean,
    val canWriteSecureSettings: Boolean,
    val hasHardwareWakeGestureSensor: Boolean,
    val hasHighFreqTapAccelerometer: Boolean,
    val hasAccelerometer: Boolean,
    val doubleTapWakeStatusTitle: String,
    val doubleTapWakeStatusDetail: String
)

object WakeCapabilityInspector {

    private val DT2W_KEYS = listOf(
        "double_tap_to_wake",
        "double_tap_enable",
        "gesture_double_tap"
    )

    fun inspect(context: Context): WakeCapabilityReport {
        val manufacturer = Build.MANUFACTURER ?: "Unknown"
        val model = Build.MODEL ?: "Unknown"
        val device = Build.DEVICE ?: ""
        val release = Build.VERSION.RELEASE ?: ""
        val sdkInt = Build.VERSION.SDK_INT

        val isGalaxyS9 = model.contains("SM-G960", ignoreCase = true) ||
                model.contains("SM-G965", ignoreCase = true) ||
                device.contains("starlte", ignoreCase = true) ||
                device.contains("star2lte", ignoreCase = true) ||
                device.contains("starqlte", ignoreCase = true) ||
                device.contains("star2qlte", ignoreCase = true)

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val wakeGestureSensor = sensorManager?.getDefaultSensor(23) // Sensor.TYPE_WAKE_GESTURE = 23
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val canWriteSecure = ContextCompat.checkSelfPermission(
            context,
            "android.permission.WRITE_SECURE_SETTINGS"
        ) == PackageManager.PERMISSION_GRANTED

        var settingFound = false
        var settingEnabled = false

        val cr = context.contentResolver
        for (key in DT2W_KEYS) {
            try {
                val v = Settings.Secure.getInt(cr, key, -1)
                if (v != -1) {
                    settingFound = true
                    if (v == 1) settingEnabled = true
                    break
                }
            } catch (_: Exception) {
            }
            try {
                val v = Settings.System.getInt(cr, key, -1)
                if (v != -1) {
                    settingFound = true
                    if (v == 1) settingEnabled = true
                    break
                }
            } catch (_: Exception) {
            }
        }

        val hasWakeSensor = wakeGestureSensor != null
        val hasAccel = accelerometer != null

        val statusTitle: String
        val statusDetail: String

        when {
            settingFound && settingEnabled -> {
                statusTitle = "Supported and active (Native Firmware + Tap Impulse)"
                statusDetail = "Native system double_tap_to_wake is ON in firmware, backed by high-frequency screen-off tap impulse detection."
            }
            isGalaxyS9 && !settingFound -> {
                statusTitle = "Firmware limitation on Galaxy S9 (Active via Tap Impulse Sensor)"
                statusDetail = "Samsung Galaxy S9 ($model, Android $release) hardware uses a pressure-sensitive Home button & AOD rather than exposing a third-party touchscreen digitizer wake API when display is off. High-frequency dual-tap impulse wake is active when enabled."
            }
            settingFound && !settingEnabled -> {
                statusTitle = if (canWriteSecure) {
                    "Supported and ready"
                } else {
                    "System setting off (Active via Tap Impulse Sensor)"
                }
                statusDetail = "Firmware exposes double_tap_to_wake (currently OFF in System Display/Gestures Settings). High-frequency dual-tap impulse wake is active, or enable 'Double tap to turn on screen' in System Settings."
            }
            hasWakeSensor -> {
                statusTitle = "Supported and active (Hardware Wake Sensor + Tap Impulse)"
                statusDetail = "Hardware Wake Gesture sensor (${wakeGestureSensor?.name}) and dual-tap impulse wake are available on $manufacturer $model."
            }
            hasAccel -> {
                statusTitle = "Active via High-Frequency Tap Impulse (No OEM Digitizer API)"
                statusDetail = "$manufacturer $model (Android $release) does not expose kernel touchscreen digitizer wake to ordinary apps while screen is off. Dual-tap high-pass impulse detection is used instead."
            }
            else -> {
                statusTitle = "Not supported by device hardware"
                statusDetail = "No wake gesture sensor or accelerometer detected on $manufacturer $model."
            }
        }

        return WakeCapabilityReport(
            deviceModel = model,
            manufacturer = manufacturer,
            androidRelease = release,
            sdkInt = sdkInt,
            isGalaxyS9Family = isGalaxyS9,
            systemDoubleTapSettingFound = settingFound,
            systemDoubleTapSettingEnabled = settingEnabled,
            canWriteSecureSettings = canWriteSecure,
            hasHardwareWakeGestureSensor = hasWakeSensor,
            hasHighFreqTapAccelerometer = hasAccel,
            hasAccelerometer = hasAccel,
            doubleTapWakeStatusTitle = statusTitle,
            doubleTapWakeStatusDetail = statusDetail
        )
    }

    fun trySetSystemDoubleTapToWake(context: Context, enabled: Boolean): Boolean {
        val cr = context.contentResolver
        val value = if (enabled) 1 else 0
        for (key in DT2W_KEYS) {
            try {
                if (Settings.Secure.putInt(cr, key, value)) {
                    return true
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
        return false
    }
}
