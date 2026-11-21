package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppPreferences(
    val doubleTapToSleepEnabled: Boolean = true,
    val floatingLockButtonEnabled: Boolean = false,
    val doubleTapToWakeEnabled: Boolean = false,
    val shakeToWakeEnabled: Boolean = false,
    val requiredTaps: Int = 2,
    val maxTapIntervalMs: Long = 400L,
    val vibrationEnabled: Boolean = true,
    val ignoreWhenKeyboardOpen: Boolean = true,
    val shakeSensitivityThreshold: Float = 18.0f,
    val floatingButtonX: Int = 36,
    val floatingButtonY: Int = 320,
    val floatingButtonSizeDp: Int = 52
)

object PreferencesRepository {
    private const val PREFS_NAME = "doubletap_prefs"

    private const val KEY_DT_SLEEP = "dt_sleep_enabled"
    private const val KEY_FLOATING_BTN = "floating_btn_enabled"
    private const val KEY_DT_WAKE = "dt_wake_enabled"
    private const val KEY_SHAKE_WAKE = "shake_wake_enabled"
    private const val KEY_REQUIRED_TAPS = "required_taps"
    private const val KEY_MAX_TAP_INTERVAL = "max_tap_interval_ms"
    private const val KEY_VIBRATION = "vibration_enabled"
    private const val KEY_IGNORE_KEYBOARD = "ignore_keyboard"
    private const val KEY_SHAKE_THRESHOLD = "shake_threshold"
    private const val KEY_FLOAT_X = "floating_x"
    private const val KEY_FLOAT_Y = "floating_y"
    private const val KEY_FLOAT_SIZE_DP = "floating_size_dp"

    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(AppPreferences())
    val state: StateFlow<AppPreferences> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _state.value = readFromPrefs()
        }
    }

    private fun ensurePrefs(context: Context): SharedPreferences {
        init(context)
        return prefs!!
    }

    private fun readFromPrefs(): AppPreferences {
        val p = prefs ?: return AppPreferences()
        return AppPreferences(
            doubleTapToSleepEnabled = p.getBoolean(KEY_DT_SLEEP, true),
            floatingLockButtonEnabled = p.getBoolean(KEY_FLOATING_BTN, false),
            doubleTapToWakeEnabled = p.getBoolean(KEY_DT_WAKE, false),
            shakeToWakeEnabled = p.getBoolean(KEY_SHAKE_WAKE, false),
            requiredTaps = p.getInt(KEY_REQUIRED_TAPS, 2).coerceIn(2, 4),
            maxTapIntervalMs = p.getLong(KEY_MAX_TAP_INTERVAL, 400L).coerceIn(200L, 800L),
            vibrationEnabled = p.getBoolean(KEY_VIBRATION, true),
            ignoreWhenKeyboardOpen = p.getBoolean(KEY_IGNORE_KEYBOARD, true),
            shakeSensitivityThreshold = p.getFloat(KEY_SHAKE_THRESHOLD, 18.0f).coerceIn(11.0f, 28.0f),
            floatingButtonX = p.getInt(KEY_FLOAT_X, 36),
            floatingButtonY = p.getInt(KEY_FLOAT_Y, 320),
            floatingButtonSizeDp = p.getInt(KEY_FLOAT_SIZE_DP, 52).coerceIn(40, 72)
        )
    }

    fun setDoubleTapToSleep(context: Context, enabled: Boolean) {
        ensurePrefs(context).edit().putBoolean(KEY_DT_SLEEP, enabled).apply()
        _state.value = readFromPrefs()
    }

    fun setFloatingLockButton(context: Context, enabled: Boolean) {
        ensurePrefs(context).edit().putBoolean(KEY_FLOATING_BTN, enabled).apply()
        _state.value = readFromPrefs()
    }

    fun setDoubleTapToWake(context: Context, enabled: Boolean) {
        ensurePrefs(context).edit().putBoolean(KEY_DT_WAKE, enabled).apply()
        _state.value = readFromPrefs()
    }

    fun setShakeToWake(context: Context, enabled: Boolean) {
        ensurePrefs(context).edit().putBoolean(KEY_SHAKE_WAKE, enabled).apply()
        _state.value = readFromPrefs()
    }

    fun setRequiredTaps(context: Context, taps: Int) {
        ensurePrefs(context).edit().putInt(KEY_REQUIRED_TAPS, taps.coerceIn(2, 4)).apply()
        _state.value = readFromPrefs()
    }

    fun setMaxTapIntervalMs(context: Context, intervalMs: Long) {
        ensurePrefs(context).edit().putLong(KEY_MAX_TAP_INTERVAL, intervalMs.coerceIn(200L, 800L)).apply()
        _state.value = readFromPrefs()
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        ensurePrefs(context).edit().putBoolean(KEY_VIBRATION, enabled).apply()
        _state.value = readFromPrefs()
    }

    fun setIgnoreWhenKeyboardOpen(context: Context, ignore: Boolean) {
        ensurePrefs(context).edit().putBoolean(KEY_IGNORE_KEYBOARD, ignore).apply()
        _state.value = readFromPrefs()
    }

    fun setShakeSensitivityThreshold(context: Context, threshold: Float) {
        ensurePrefs(context).edit().putFloat(KEY_SHAKE_THRESHOLD, threshold.coerceIn(11.0f, 28.0f)).apply()
        _state.value = readFromPrefs()
    }

    fun saveFloatingButtonPosition(context: Context, x: Int, y: Int) {
        ensurePrefs(context).edit()
            .putInt(KEY_FLOAT_X, x)
            .putInt(KEY_FLOAT_Y, y)
            .apply()
        _state.value = readFromPrefs()
    }

    fun setFloatingButtonSizeDp(context: Context, sizeDp: Int) {
        ensurePrefs(context).edit().putInt(KEY_FLOAT_SIZE_DP, sizeDp.coerceIn(40, 72)).apply()
        _state.value = readFromPrefs()
    }
}
