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
import android.widget.Button
import androidx.core.app.NotificationCompat
import com.example.ui.MainActivity

class DoubleTapForegroundService : Service(), SensorEventListener {

    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isOverlayShown = false

    private var tapCount = 0
    private var lastTapTime = 0L

    private var wakeTapCount = 0
    private var lastWakeTapTime = 0L

    private val CHANNEL_ID = "doubletap_foreground_channel"
    private val NOTIF_ID = 2002

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("DoubleTap running (Wake & Sleep active)"))

        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_TOGGLE_OVERLAY) {
            val enable = intent.getBooleanExtra(EXTRA_ENABLE_OVERLAY, true)
            if (enable) showFloatingOverlay() else removeFloatingOverlay()
        } else if (action == ACTION_LOCK) {
            DoubleTapAccessibilityService.lockDevice()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DoubleTap Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains wake & sleep gesture detection"
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
            .addAction(android.R.drawable.ic_lock_lock, "Lock Screen", lockPi)
            .setOngoing(true)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && isWakeEnabled) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gForce = Math.sqrt((x * x + y * y + z * z).toDouble()) / 9.80665
            if (gForce > 2.0) {
                val now = System.currentTimeMillis()
                if (now - lastWakeTapTime <= maxTapIntervalMs) {
                    wakeTapCount++
                } else {
                    wakeTapCount = 1
                }
                lastWakeTapTime = now

                if (wakeTapCount >= requiredTaps) {
                    wakeTapCount = 0
                    wakeUpScreen()
                }
            }
        } else if (event.sensor.type == Sensor.TYPE_PROXIMITY && isWakeEnabled) {
            val distance = event.values[0]
            if (distance < (proximitySensor?.maximumRange ?: 5f)) {
                wakeUpScreen()
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
    private fun showFloatingOverlay() {
        if (isOverlayShown) return
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 30
                y = 200
            }

            val button = Button(this).apply {
                text = "⚡"
                textSize = 18f
                setBackgroundColor(Color.parseColor("#806750A4"))
                setTextColor(Color.WHITE)
                setPadding(20, 10, 20, 10)
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN && isSleepEnabled) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime <= maxTapIntervalMs) {
                            tapCount++
                        } else {
                            tapCount = 1
                        }
                        lastTapTime = currentTime

                        if (tapCount >= requiredTaps) {
                            tapCount = 0
                            triggerSleepAction()
                        }
                    }
                    false
                }
            }

            windowManager?.addView(button, params)
            floatingView = button
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

    private fun removeFloatingOverlay() {
        if (!isOverlayShown) return
        try {
            if (floatingView != null) {
                windowManager?.removeView(floatingView)
                floatingView = null
            }
            isOverlayShown = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingOverlay()
        sensorManager?.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
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
