package com.example.ui

import android.os.Build
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DoubleTapUiState(
    val isWakeEnabled: Boolean = false,
    val isSleepEnabled: Boolean = true,
    val requiredTaps: Int = 2, // 2, 3, or 4 taps
    val maxTapIntervalMs: Long = 450L, // 300ms to 800ms
    val vibrationEnabled: Boolean = true,
    val sensitivity: Float = 0.5f,
    val isHardwareWakeSupported: Boolean = false,
    val hardwareWakeStatusMessage: String = ""
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DoubleTapUiState())
    val uiState: StateFlow<DoubleTapUiState> = _uiState.asStateFlow()

    init {
        checkHardwareWakeSupport()
    }

    private fun checkHardwareWakeSupport() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val supportsOemWake = manufacturer.contains("samsung") ||
                manufacturer.contains("oneplus") ||
                manufacturer.contains("xiaomi") ||
                manufacturer.contains("asus")

        val message = if (supportsOemWake) {
            "Device firmware (${Build.MANUFACTURER}) supports tap-to-wake via system display settings."
        } else {
            "Standard Android firmware on ${Build.MANUFACTURER} ${Build.MODEL} does not natively expose screen-off touch drivers. Proximity wake assist is available."
        }

        _uiState.update {
            it.copy(
                isHardwareWakeSupported = supportsOemWake,
                hardwareWakeStatusMessage = message
            )
        }
    }

    fun toggleWake(enabled: Boolean) {
        _uiState.update { it.copy(isWakeEnabled = enabled) }
    }

    fun toggleSleep(enabled: Boolean) {
        _uiState.update { it.copy(isSleepEnabled = enabled) }
    }

    fun setRequiredTaps(taps: Int) {
        _uiState.update { it.copy(requiredTaps = taps) }
    }

    fun setMaxTapIntervalMs(intervalMs: Long) {
        _uiState.update { it.copy(maxTapIntervalMs = intervalMs) }
    }

    fun toggleVibration(enabled: Boolean) {
        _uiState.update { it.copy(vibrationEnabled = enabled) }
    }

    fun setSensitivity(sensitivity: Float) {
        _uiState.update { it.copy(sensitivity = sensitivity) }
    }
}
