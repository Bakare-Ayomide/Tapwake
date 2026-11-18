package com.example.service

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.TextUtils
import com.example.data.PreferencesRepository

class DoubleTapDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, DoubleTapDeviceAdminReceiver::class.java)
        }
    }
}

object ScreenLockHelper {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (DoubleTapAccessibilityService.isRunning()) return true
        val expectedComponentName = ComponentName(context, DoubleTapAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            dpm?.isAdminActive(DoubleTapDeviceAdminReceiver.getComponentName(context)) == true
        } catch (e: Exception) {
            false
        }
    }

    fun canLockScreen(context: Context): Boolean {
        return DoubleTapAccessibilityService.isRunning() || isDeviceAdminActive(context)
    }

    fun lockScreen(context: Context): Boolean {
        PreferencesRepository.init(context)
        val vibrate = PreferencesRepository.state.value.vibrationEnabled

        // 1. Prefer AccessibilityService GLOBAL_ACTION_LOCK_SCREEN (preserves biometric/fingerprint unlock)
        if (DoubleTapAccessibilityService.isRunning()) {
            if (vibrate) triggerHaptic(context)
            val locked = DoubleTapAccessibilityService.lockDevice()
            if (locked) return true
        }

        // 2. Fallback to DevicePolicyManager.lockNow() if Device Admin is active
        if (isDeviceAdminActive(context)) {
            return try {
                if (vibrate) triggerHaptic(context)
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                dpm.lockNow()
                true
            } catch (e: Exception) {
                false
            }
        }

        return false
    }

    fun triggerHaptic(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(45)
            }
        } catch (_: Exception) {
        }
    }
}
