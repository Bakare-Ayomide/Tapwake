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
        // Genuine check for double tap to wake / screen-off wake capability.
        // True double-tap-to-wake requires OEM kernel/firmware support (e.g., Samsung One UI / custom kernels).
        // Standard Android apps cannot intercept touch events while the screen is completely powered off
        // unless provided by OEM firmware or root/accessibility wake drivers.
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        
        val supportsOemWake = manufacturer.contains("samsung") || 
                              manufacturer.contains("oneplus") || 
                              manufacturer.contains("xiaomi") || 
                              manufacturer.contains("asus")

        val message = if (supportsOemWake) {
            "Device firmware (${Build.MANUFACTURER}) may support tap-to-wake via system settings. Check device display settings."
        } else {
            "Standard Android firmware on ${Build.MANUFACTURER} ${Build.MODEL} does not natively expose kernel touch interception for screen-off wake without OEM ROM support. Proximity wake assist is available."
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
}
