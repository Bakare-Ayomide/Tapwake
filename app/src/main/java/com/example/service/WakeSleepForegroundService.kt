package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import com.example.ui.MainActivity

class WakeSleepForegroundService : Service(), SensorEventListener {

    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isOverlayShown = false

    private val CHANNEL_ID = "tapwake_foreground_channel"
    private val NOTIF_ID = 1001

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("TapWake is active & ready"))

        // Register proximity sensor listener
        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_TOGGLE_OVERLAY) {
            val enable = intent.getBooleanExtra(EXTRA_ENABLE_OVERLAY, false)
            if (enable) showFloatingOverlay() else removeFloatingOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TapWake Active Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps double tap wake & sleep service running in background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val lockIntent = Intent(this, WakeSleepForegroundService::class.java).apply {
            action = ACTION_TRIGGER_LOCK
        }
        val lockPendingIntent = PendingIntent.getService(
            this, 1, lockIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TapWake Service Running")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_lock_power_off, "Lock Screen", lockPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
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
                    "TapWake:WakeLock"
                )
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(3000)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showFloatingOverlay() {
        if (isOverlayShown) return
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    TapSleepAccessibilityService.lockDevice()
                    return true
                }
            })

            val view = FrameLayout(this).apply {
                setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    false
                }
            }

            windowManager?.addView(view, params)
            floatingView = view
            isOverlayShown = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    companion object {
        const val ACTION_TOGGLE_OVERLAY = "com.example.ACTION_TOGGLE_OVERLAY"
        const val EXTRA_ENABLE_OVERLAY = "EXTRA_ENABLE_OVERLAY"
        const val ACTION_TRIGGER_LOCK = "com.example.ACTION_TRIGGER_LOCK"
    }
}
