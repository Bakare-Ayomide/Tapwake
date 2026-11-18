package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import com.example.data.PreferencesRepository
import kotlin.math.hypot

class DoubleTapAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var globalTouchWatcherView: View? = null

    private var tapCount = 0
    private var lastTapUptimeMs = 0L
    private var lastTapX = -1f
    private var lastTapY = -1f

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        PreferencesRepository.init(this)
        setupGlobalTouchWatcher()
        Log.d(TAG, "DoubleTapAccessibilityService connected and global touch watcher active")
    }

    private fun setupGlobalTouchWatcher() {
        if (globalTouchWatcherView != null) return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            // 1x1 pixel transparent TYPE_ACCESSIBILITY_OVERLAY with FLAG_WATCH_OUTSIDE_TOUCH.
            // Receives ACTION_OUTSIDE for every finger-down anywhere on the screen across
            // Home screen, App drawer, WhatsApp, Browser, Settings, and all apps,
            // while passing 100% of touches through without blocking any app interaction.
            val params = WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            val watcher = FrameLayout(this).apply {
                setOnTouchListener { _, event ->
                    val action = event.actionMasked
                    if (action == MotionEvent.ACTION_OUTSIDE || action == MotionEvent.ACTION_DOWN) {
                        onGlobalTouchDetected(event.rawX, event.rawY)
                    }
                    false
                }
            }

            wm.addView(watcher, params)
            globalTouchWatcherView = watcher
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach global touch watcher overlay", e)
        }
    }

    private fun removeGlobalTouchWatcher() {
        try {
            globalTouchWatcherView?.let { view ->
                windowManager?.removeView(view)
            }
        } catch (_: Exception) {
        } finally {
            globalTouchWatcherView = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // Backup detection when views consume clicks on certain OEM launchers/system windows
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            onGlobalTouchDetected(-1f, -1f)
        }
    }

    private fun onGlobalTouchDetected(rawX: Float, rawY: Float) {
        val prefs = PreferencesRepository.state.value
        if (!prefs.doubleTapToSleepEnabled) {
            tapCount = 0
            return
        }

        // Do not lock when user is actively typing on the soft keyboard if preference is enabled
        if (prefs.ignoreWhenKeyboardOpen && isSoftKeyboardActive()) {
            tapCount = 0
            return
        }

        // Ignore taps right on top of the Floating Lock Button if it is enabled so they don't conflict
        if (prefs.floatingLockButtonEnabled && rawX > 0f && rawY > 0f) {
            val density = resources.displayMetrics.density
            val btnSizePx = prefs.floatingButtonSizeDp * density
            val screenWidth = resources.displayMetrics.widthPixels
            // floatingButtonX is offset from END (right edge)
            val btnCenterX = screenWidth - prefs.floatingButtonX - (btnSizePx / 2f)
            val btnCenterY = prefs.floatingButtonY + (btnSizePx / 2f)
            if (hypot(rawX - btnCenterX, rawY - btnCenterY) <= btnSizePx * 0.85f) {
                return
            }
        }

        val now = SystemClock.uptimeMillis()
        val delta = now - lastTapUptimeMs

        // Debounce duplicate events from the same physical tap (< 65ms apart)
        if (delta in 0..65) {
            return
        }

        val maxInterval = prefs.maxTapIntervalMs
        val maxDistancePx = 110f * resources.displayMetrics.density

        val isCloseEnoughInSpace = if (rawX > 0f && rawY > 0f && lastTapX > 0f && lastTapY > 0f) {
            hypot(rawX - lastTapX, rawY - lastTapY) <= maxDistancePx
        } else {
            true
        }

        if (delta in 66..maxInterval && isCloseEnoughInSpace) {
            tapCount++
        } else {
            tapCount = 1
        }

        lastTapUptimeMs = now
        if (rawX > 0f && rawY > 0f) {
            lastTapX = rawX
            lastTapY = rawY
        }

        if (tapCount >= prefs.requiredTaps) {
            tapCount = 0
            lastTapUptimeMs = 0L
            ScreenLockHelper.lockScreen(this)
        }
    }

    private fun isSoftKeyboardActive(): Boolean {
        return try {
            val windowList = windows ?: return false
            windowList.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        } catch (_: Exception) {
            false
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        removeGlobalTouchWatcher()
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    fun lockScreenNow(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform GLOBAL_ACTION_LOCK_SCREEN", e)
            false
        }
    }

    companion object {
        private const val TAG = "DoubleTapSleep"
        var instance: DoubleTapAccessibilityService? = null
            private set

        fun lockDevice(): Boolean {
            return instance?.lockScreenNow() ?: false
        }

        fun isRunning(): Boolean = instance != null
    }
}
