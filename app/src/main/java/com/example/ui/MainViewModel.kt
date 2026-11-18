package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.data.AppPreferences
import com.example.data.PreferencesRepository
import com.example.service.DoubleTapForegroundService
import com.example.service.ScreenLockHelper
import com.example.service.WakeCapabilityInspector
import com.example.service.WakeCapabilityReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PermissionAndDeviceState(
    val isAccessibilityGranted: Boolean = false,
    val isOverlayGranted: Boolean = false,
    val isDeviceAdminGranted: Boolean = false,
    val wakeReport: WakeCapabilityReport? = null
)

class MainViewModel : ViewModel() {

    val preferences: StateFlow<AppPreferences> = PreferencesRepository.state

    private val _deviceState = MutableStateFlow(PermissionAndDeviceState())
    val deviceState: StateFlow<PermissionAndDeviceState> = _deviceState.asStateFlow()

    fun refreshStatus(context: Context) {
        PreferencesRepository.init(context)
        val a11y = ScreenLockHelper.isAccessibilityServiceEnabled(context)
        val overlay = ScreenLockHelper.isOverlayPermissionGranted(context)
        val admin = ScreenLockHelper.isDeviceAdminActive(context)
        val report = WakeCapabilityInspector.inspect(context)

        _deviceState.update {
            it.copy(
                isAccessibilityGranted = a11y,
                isOverlayGranted = overlay,
                isDeviceAdminGranted = admin,
                wakeReport = report
            )
        }
        DoubleTapForegroundService.sync(context)
    }

    fun setDoubleTapToSleep(context: Context, enabled: Boolean) {
        PreferencesRepository.setDoubleTapToSleep(context, enabled)
        refreshStatus(context)
    }

    fun setFloatingLockButton(context: Context, enabled: Boolean) {
        PreferencesRepository.setFloatingLockButton(context, enabled)
        refreshStatus(context)
    }

    fun setDoubleTapToWake(context: Context, enabled: Boolean) {
        PreferencesRepository.setDoubleTapToWake(context, enabled)
        if (enabled) {
            WakeCapabilityInspector.trySetSystemDoubleTapToWake(context, true)
        }
        refreshStatus(context)
    }

    fun setShakeToWake(context: Context, enabled: Boolean) {
        PreferencesRepository.setShakeToWake(context, enabled)
        refreshStatus(context)
    }

    fun setRequiredTaps(context: Context, taps: Int) {
        PreferencesRepository.setRequiredTaps(context, taps)
    }

    fun setMaxTapIntervalMs(context: Context, intervalMs: Long) {
        PreferencesRepository.setMaxTapIntervalMs(context, intervalMs)
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        PreferencesRepository.setVibrationEnabled(context, enabled)
    }

    fun setIgnoreWhenKeyboardOpen(context: Context, ignore: Boolean) {
        PreferencesRepository.setIgnoreWhenKeyboardOpen(context, ignore)
    }

    fun setShakeSensitivityThreshold(context: Context, threshold: Float) {
        PreferencesRepository.setShakeSensitivityThreshold(context, threshold)
        DoubleTapForegroundService.sync(context)
    }

    fun setFloatingButtonSizeDp(context: Context, sizeDp: Int) {
        PreferencesRepository.setFloatingButtonSizeDp(context, sizeDp)
        DoubleTapForegroundService.sync(context)
    }

    fun resetFloatingButtonPosition(context: Context) {
        PreferencesRepository.saveFloatingButtonPosition(context, 36, 320)
        DoubleTapForegroundService.sync(context)
    }
}
