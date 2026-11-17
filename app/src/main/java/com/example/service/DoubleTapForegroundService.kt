package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.ui.MainActivity

class DoubleTapForegroundService : Service(), SensorEventListener {

    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var partialWakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null

    private var windowManager: WindowManager? = null
    private var floatingButtonView: View? = null
    private var isOverlayShown = false

    // Double tap sleep on floating button
    private var buttonTapCount = 0
    private var lastButtonTapTime = 0L

    // Precise double tap to wake (knock detection)
    private var lastWakeSpikeTime = 0L
    private var wakeSpikeCount = 0

    private val CHANNEL_ID = "doubletap_foreground_channel"
    private val NOTIF_ID = 2002

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        try {
            partialWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DoubleTap:PartialWakeLock"
            ).apply {
                acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("DoubleTap floating service active"))

        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_TOGGLE_OVERLAY) {
            val enable = intent.getBooleanExtra(EXTRA_ENABLE_OVERLAY, true)
            if (enable) showFloatingCircularIcon() else removeFloatingCircularIcon()
        } else if (action == ACTION_LOCK) {
            DoubleTapAccessibilityService.lockDevice()
        } else {
            if (isSleepEnabled) {
                showFloatingCircularIcon()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DoubleTap Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating lock icon & wake service"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val lockIntent = Intent(this, DoubleTapForegroundService::class.java).apply {
            action = ACTION_LOCK
        }
        val lockPi = PendingIntent.getService(
            this, 1, lockIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DoubleTap Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_power_off)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_lock_lock, "Lock", lockPi)
            .setOngoing(true)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isWakeEnabled) return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Strict Double-Tap / Knock detection (sharp impact spike rejecting slow shakes)
            // Normal gravity is ~9.8. A sharp tap on device body creates an abrupt high acceleration spike (> 2.8g)
            val totalAccel = Math.sqrt((x * x + y * y + z * z).toDouble())
            val gForce = Math.abs(totalAccel - 9.80665)

            if (gForce > 2.8) { // Sharp knock threshold
                val now = System.currentTimeMillis()
                // A valid double tap knock interval: between 100ms and 500ms apart
                val diff = now - lastWakeSpikeTime
                if (diff in 80..550) {
                    wakeSpikeCount++
                    if (wakeSpikeCount >= (requiredTaps - 1)) {
                        wakeSpikeCount = 0
                        wakeUpScreen()
                    }
                } else if (diff > 550) {
                    wakeSpikeCount = 1
                }
                lastWakeSpikeTime = now
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun wakeUpScreen() {
        try {
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                    "DoubleTap:WakeLock"
                )
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(3000)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("DEPRECATION")
    private fun showFloatingCircularIcon() {
        if (isOverlayShown || !isSleepEnabled) return
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // Prominent floating circular lock icon layer displayed on top of other apps
            val params = WindowManager.LayoutParams(
                140, 140, // Prominent circular button size in px
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 40
                y = 300
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isMoved = false

            val circularButton = TextView(this).apply {
                text = "🔒"
                textSize = 28f
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#CC6750A4")) // semi-transparent purple circular background
                setTextColor(Color.WHITE)
                elevation = 16f
                // Make it circular
                clipToOutline = true
                
                setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isMoved = false
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                                isMoved = true
                                params.x = initialX - dx
                                params.y = initialY + dy
                                windowManager?.updateViewLayout(view, params)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!isMoved) {
                                // Handle tap / double tap
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastButtonTapTime <= maxTapIntervalMs) {
                                    buttonTapCount++
                                } else {
                                    buttonTapCount = 1
                                }
                                lastButtonTapTime = currentTime

                                if (buttonTapCount >= requiredTaps) {
                                    buttonTapCount = 0
                                    triggerSleepAction()
                                }
                            }
                            true
                        }
                        else -> false
                    }
                }
            }

            windowManager?.addView(circularButton, params)
            floatingButtonView = circularButton
            isOverlayShown = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun triggerSleepAction() {
        if (vibrationEnabled) {
            try {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(70)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        DoubleTapAccessibilityService.lockDevice()
    }

    private fun removeFloatingCircularIcon() {
        if (!isOverlayShown) return
        try {
            if (floatingButtonView != null) {
                windowManager?.removeView(floatingButtonView)
                floatingButtonView = null
            }
            isOverlayShown = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingCircularIcon()
        sensorManager?.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        partialWakeLock?.let { if (it.isHeld) it.release() }
    }

    companion object {
        const val ACTION_TOGGLE_OVERLAY = "com.example.ACTION_TOGGLE_OVERLAY"
        const val EXTRA_ENABLE_OVERLAY = "EXTRA_ENABLE_OVERLAY"
        const val ACTION_LOCK = "com.example.ACTION_LOCK"

        var requiredTaps: Int = 2
        var maxTapIntervalMs: Long = 450L
        var vibrationEnabled: Boolean = true
        var isWakeEnabled: Boolean = true
        var isSleepEnabled: Boolean = true
    }
}
