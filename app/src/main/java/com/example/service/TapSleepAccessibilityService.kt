package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class TapSleepAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "TapSleepAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for capturing events, purely used for global lock action & gestures
    }

    override fun onInterrupt() {
        Log.d(TAG, "TapSleepAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "TapSleepAccessibilityService destroyed")
    }

    fun lockScreenNow(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lock screen", e)
            false
        }
    }

    companion object {
        private const val TAG = "TapSleepService"
        var instance: TapSleepAccessibilityService? = null
            private set

        fun lockDevice(): Boolean {
            return instance?.lockScreenNow() ?: false
        }

        fun isRunning(): Boolean = instance != null
    }
}
