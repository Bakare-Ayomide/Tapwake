package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.data.PreferencesRepository
import com.example.ui.MainActivity
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

class DoubleTapForegroundService : Service(), SensorEventListener {

    private lateinit var powerManager: PowerManager
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var partialWakeLock: PowerManager.WakeLock? = null

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var wakeGestureSensor: Sensor? = null
    private var isSensorRegistered = false
    private var isWakeTriggerRegistered = false

    private var windowManager: WindowManager? = null
    private var floatingButtonView: View? = null
    private var floatingLayoutParams: WindowManager.LayoutParams? = null
    private var isOverlayShown = false

    private val mainHandler = Handler(Looper.getMainLooper())

    // High-pass filter state for separating sharp Double-Tap impulses from Shake
    private val gravity = FloatArray(3) { 0f }
    private var gravityInitialized = false

    // Double-Tap to Wake impulse state
    private var lastTapImpulseMs = 0L
    private var tapImpulseCount = 0
    private var inTapPeak = false

    // Shake to Wake state
    private var lastShakePeakMs = 0L
    private var shakePeakCount = 0

    private val wakeTriggerListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            isWakeTriggerRegistered = false
            val prefs = PreferencesRepository.state.value
            if (prefs.doubleTapToWakeEnabled && !powerManager.isInteractive) {
                wakeUpScreen("Hardware Wake Gesture")
            }
            // Re-arm hardware wake gesture if still needed
            registerWakeTriggerIfNeeded()
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Re-register sensors shortly after screen turns off so Android/Samsung
                    // does not suspend the accelerometer batch queue during sleep
                    updateWakeSensorsForScreenState(isScreenInteractive = false)
                    mainHandler.postDelayed({
                        if (!powerManager.isInteractive) {
                            unregisterWakeSensors()
                            updateWakeSensorsForScreenState(isScreenInteractive = false)
                        }
                    }, 250L)
                }
                Intent.ACTION_SCREEN_ON -> {
                    // Save battery while screen is already ON
                    updateWakeSensorsForScreenState(isScreenInteractive = true)
                }
            }
        }
    }

    // Polls overlay permission when Floating Lock Button is enabled and user is granting "Appear on top"
    private val overlayPermissionPollRunnable = object : Runnable {
        override fun run() {
            val prefs = PreferencesRepository.state.value
            if (prefs.floatingLockButtonEnabled && !isOverlayShown) {
                if (ScreenLockHelper.isOverlayPermissionGranted(this@DoubleTapForegroundService)) {
                    showFloatingCircularIcon()
                } else {
                    mainHandler.postDelayed(this, 500L)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        PreferencesRepository.init(this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        wakeGestureSensor = sensorManager?.getDefaultSensor(23) // TYPE_WAKE_GESTURE

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenStateReceiver, filter)
        }

        syncStateWithPreferences()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LOCK -> {
                ScreenLockHelper.lockScreen(this)
            }
            ACTION_SYNC, null -> {
                syncStateWithPreferences()
            }
        }
        return START_STICKY
    }

    private fun syncStateWithPreferences() {
        val prefs = PreferencesRepository.state.value

        // 1. Sync Floating Lock Button
        if (prefs.floatingLockButtonEnabled) {
            if (ScreenLockHelper.isOverlayPermissionGranted(this)) {
                showFloatingCircularIcon()
                floatingButtonView?.invalidate()
            } else {
                removeFloatingCircularIcon()
                mainHandler.removeCallbacks(overlayPermissionPollRunnable)
                mainHandler.postDelayed(overlayPermissionPollRunnable, 500L)
            }
        } else {
            mainHandler.removeCallbacks(overlayPermissionPollRunnable)
            removeFloatingCircularIcon()
        }

        // 2. Sync Wake Sensors & Partial WakeLock
        val needWakeMonitoring = prefs.doubleTapToWakeEnabled || prefs.shakeToWakeEnabled
        if (needWakeMonitoring) {
            acquirePartialWakeLock()
            updateWakeSensorsForScreenState(isScreenInteractive = powerManager.isInteractive)
        } else {
            unregisterWakeSensors()
            releasePartialWakeLock()
        }

        // 3. Update notification or stop service if nothing needs background execution
        if (!prefs.floatingLockButtonEnabled && !needWakeMonitoring) {
            stopSelf()
        } else {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIF_ID, buildNotification())
        }
    }

    private fun updateWakeSensorsForScreenState(isScreenInteractive: Boolean) {
        val prefs = PreferencesRepository.state.value
        val needWakeMonitoring = prefs.doubleTapToWakeEnabled || prefs.shakeToWakeEnabled
        if (!needWakeMonitoring) {
            unregisterWakeSensors()
            return
        }

        // Keep sensors active when screen is OFF (or always armed so transitions are instant)
        if (!isScreenInteractive || needWakeMonitoring) {
            if (!isSensorRegistered && accelerometer != null) {
                gravityInitialized = false
                isSensorRegistered = sensorManager?.registerListener(
                    this,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_GAME,
                    0
                ) == true
            }
            registerWakeTriggerIfNeeded()
        }
    }

    private fun registerWakeTriggerIfNeeded() {
        val prefs = PreferencesRepository.state.value
        if (prefs.doubleTapToWakeEnabled && wakeGestureSensor != null && !isWakeTriggerRegistered) {
            try {
                isWakeTriggerRegistered = sensorManager?.requestTriggerSensor(
                    wakeTriggerListener,
                    wakeGestureSensor
                ) == true
            } catch (_: Exception) {
            }
        }
    }

    private fun unregisterWakeSensors() {
        if (isSensorRegistered) {
            try {
                sensorManager?.unregisterListener(this)
            } catch (_: Exception) {
            }
            isSensorRegistered = false
        }
        if (isWakeTriggerRegistered && wakeGestureSensor != null) {
            try {
                sensorManager?.cancelTriggerSensor(wakeTriggerListener, wakeGestureSensor)
            } catch (_: Exception) {
            }
            isWakeTriggerRegistered = false
        }
    }

    private fun acquirePartialWakeLock() {
        try {
            if (partialWakeLock == null) {
                partialWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "DoubleTap:SensorWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (partialWakeLock?.isHeld == false) {
                partialWakeLock?.acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun releasePartialWakeLock() {
        try {
            partialWakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        // Only wake when display is actually OFF
        if (powerManager.isInteractive) return

        val prefs = PreferencesRepository.state.value
        if (!prefs.doubleTapToWakeEnabled && !prefs.shakeToWakeEnabled) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        if (!gravityInitialized) {
            gravity[0] = x
            gravity[1] = y
            gravity[2] = z
            gravityInitialized = true
            return
        }

        // Low-pass filter for gravity, high-pass filter for linear impulse
        val alpha = 0.82f
        gravity[0] = alpha * gravity[0] + (1f - alpha) * x
        gravity[1] = alpha * gravity[1] + (1f - alpha) * y
        gravity[2] = alpha * gravity[2] + (1f - alpha) * z

        val lx = x - gravity[0]
        val ly = y - gravity[1]
        val lz = z - gravity[2]

        val linearMag = sqrt(lx * lx + ly * ly + lz * lz)
        val zImpulse = abs(lz)
        val now = SystemClock.uptimeMillis()

        // 1. DOUBLE-TAP TO WAKE:
        // Detects two crisp surface tap impulses (especially along Z axis / screen surface)
        // without requiring a heavy shake.
        if (prefs.doubleTapToWakeEnabled) {
            val tapMetric = maxOf(zImpulse * 1.15f, linearMag)
            if (tapMetric in 1.15f..11.5f) {
                if (!inTapPeak) {
                    inTapPeak = true
                    val dt = now - lastTapImpulseMs
                    if (dt in 75L..prefs.maxTapIntervalMs.coerceAtLeast(450L)) {
                        tapImpulseCount++
                        if (tapImpulseCount >= 2) {
                            tapImpulseCount = 0
                            lastTapImpulseMs = 0L
                            wakeUpScreen("Double Tap to Wake")
                            return
                        }
                    } else if (dt > prefs.maxTapIntervalMs.coerceAtLeast(450L)) {
                        tapImpulseCount = 1
                    }
                    lastTapImpulseMs = now
                }
            } else if (tapMetric < 0.65f) {
                inTapPeak = false
            }
        }

        // 2. SHAKE TO WAKE:
        // Detects energetic shake motion exceeding user-configured shake threshold.
        if (prefs.shakeToWakeEnabled) {
            val threshold = (prefs.shakeSensitivityThreshold * 0.65f).coerceIn(7.5f, 18.0f)
            if (linearMag >= threshold) {
                val dt = now - lastShakePeakMs
                if (dt in 90L..650L) {
                    shakePeakCount++
                    if (shakePeakCount >= 2) {
                        shakePeakCount = 0
                        lastShakePeakMs = 0L
                        wakeUpScreen("Shake to Wake")
                    }
                } else if (dt > 650L) {
                    shakePeakCount = 1
                }
                lastShakePeakMs = now
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Suppress("DEPRECATION")
    fun wakeUpScreen(reason: String) {
        try {
            val prefs = PreferencesRepository.state.value
            if (prefs.vibrationEnabled) {
                ScreenLockHelper.triggerHaptic(this)
            }
            val wl = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "DoubleTap:WakeUpScreen"
            )
            wl.acquire(3500L)
            screenWakeLock = wl
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("DEPRECATION")
    private fun showFloatingCircularIcon() {
        val prefs = PreferencesRepository.state.value
        if (!prefs.floatingLockButtonEnabled) return
        if (!ScreenLockHelper.isOverlayPermissionGranted(this)) return

        val density = resources.displayMetrics.density
        val sizePx = (prefs.floatingButtonSizeDp * density).toInt().coerceAtLeast(110)

        if (isOverlayShown && floatingButtonView != null && floatingLayoutParams != null) {
            floatingLayoutParams?.width = sizePx
            floatingLayoutParams?.height = sizePx
            try {
                windowManager?.updateViewLayout(floatingButtonView, floatingLayoutParams)
            } catch (_: Exception) {
            }
            return
        }

        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = prefs.floatingButtonX
                y = prefs.floatingButtonY
            }

            val buttonView = FloatingLockCircleView(this).apply {
                var startParamX = 0
                var startParamY = 0
                var downRawX = 0f
                var downRawY = 0f
                var dragged = false
                val touchSlop = 8f * density

                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startParamX = params.x
                            startParamY = params.y
                            downRawX = event.rawX
                            downRawY = event.rawY
                            dragged = false
                            isPressedState = true
                            invalidate()
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - downRawX
                            val dy = event.rawY - downRawY
                            if (hypot(dx, dy) > touchSlop) {
                                dragged = true
                            }
                            if (dragged) {
                                // Gravity.END means x increases towards the left
                                params.x = (startParamX - dx.toInt()).coerceAtLeast(0)
                                params.y = (startParamY + dy.toInt()).coerceAtLeast(0)
                                try {
                                    wm.updateViewLayout(view, params)
                                } catch (_: Exception) {
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            isPressedState = false
                            invalidate()
                            if (dragged) {
                                PreferencesRepository.saveFloatingButtonPosition(
                                    this@DoubleTapForegroundService,
                                    params.x,
                                    params.y
                                )
                            } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                                // Single tap on the Floating Lock Button immediately locks the phone
                                val locked = ScreenLockHelper.lockScreen(this@DoubleTapForegroundService)
                                if (!locked) {
                                    // Bring user to MainActivity to enable Accessibility or Device Admin
                                    val openIntent = Intent(this@DoubleTapForegroundService, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(openIntent)
                                }
                            }
                            true
                        }
                        else -> false
                    }
                }
            }

            wm.addView(buttonView, params)
            floatingButtonView = buttonView
            floatingLayoutParams = params
            isOverlayShown = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeFloatingCircularIcon() {
        if (!isOverlayShown) return
        try {
            floatingButtonView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        } finally {
            floatingButtonView = null
            floatingLayoutParams = null
            isOverlayShown = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DoubleTap Active Features",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Floating Lock Button and Wake sensors active"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val prefs = PreferencesRepository.state.value
        val activeFeatures = mutableListOf<String>()
        if (prefs.doubleTapToSleepEnabled) activeFeatures.add("Global Tap-Sleep")
        if (prefs.floatingLockButtonEnabled) activeFeatures.add("Floating Button")
        if (prefs.doubleTapToWakeEnabled) activeFeatures.add("Tap-Wake")
        if (prefs.shakeToWakeEnabled) activeFeatures.add("Shake-Wake")

        val summary = if (activeFeatures.isEmpty()) {
            "Ready"
        } else {
            activeFeatures.joinToString(" • ")
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val lockIntent = Intent(this, DoubleTapForegroundService::class.java).apply {
            action = ACTION_LOCK
        }
        val lockPi = PendingIntent.getService(
            this,
            1,
            lockIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DoubleTap Active")
            .setContentText(summary)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_lock_power_off, "Lock Now", lockPi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {
        }
        removeFloatingCircularIcon()
        unregisterWakeSensors()
        releasePartialWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "doubletap_active_channel"
        private const val NOTIF_ID = 2002

        const val ACTION_SYNC = "com.example.ACTION_SYNC"
        const val ACTION_LOCK = "com.example.ACTION_LOCK"

        fun sync(context: Context) {
            PreferencesRepository.init(context)
            val prefs = PreferencesRepository.state.value
            val needsService = prefs.floatingLockButtonEnabled ||
                    prefs.doubleTapToWakeEnabled ||
                    prefs.shakeToWakeEnabled

            val intent = Intent(context, DoubleTapForegroundService::class.java).apply {
                action = ACTION_SYNC
            }
            if (needsService) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (_: Exception) {
                }
            } else {
                try {
                    context.stopService(intent)
                } catch (_: Exception) {
                }
            }
        }
    }
}

/**
 * Custom lightweight circular floating lock icon view with crisp vector-style canvas drawing.
 */
private class FloatingLockCircleView(context: Context) : View(context) {

    var isPressedState: Boolean = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * resources.displayMetrics.density
        color = Color.parseColor("#6638BDF8")
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val rectF = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - ringPaint.strokeWidth

        bgPaint.color = if (isPressedState) {
            Color.parseColor("#EE0284C7")
        } else {
            Color.parseColor("#D90F172A")
        }

        // Outer circle & glowing cyan border ring
        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)

        // Draw crisp padlock icon inside the circle
        val bodyW = radius * 0.72f
        val bodyH = radius * 0.56f
        val bodyLeft = cx - bodyW / 2f
        val bodyTop = cy - bodyH * 0.15f
        val bodyRight = cx + bodyW / 2f
        val bodyBottom = bodyTop + bodyH
        val corner = radius * 0.14f

        rectF.set(bodyLeft, bodyTop, bodyRight, bodyBottom)
        canvas.drawRoundRect(rectF, corner, corner, iconPaint)

        // Shackle arc above padlock body
        val shackleW = bodyW * 0.58f
        val shackleH = bodyH * 0.68f
        rectF.set(
            cx - shackleW / 2f,
            bodyTop - shackleH * 0.55f,
            cx + shackleW / 2f,
            bodyTop + shackleH * 0.45f
        )
        canvas.drawArc(rectF, 180f, 180f, false, iconPaint)

        // Keyhole dot in padlock center
        canvas.drawCircle(cx, (bodyTop + bodyBottom) / 2f, radius * 0.08f, iconFillPaint)
    }
}
