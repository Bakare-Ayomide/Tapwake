package com.example.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppPreferences
import com.example.data.PreferencesRepository
import com.example.service.DoubleTapDeviceAdminReceiver
import com.example.service.ScreenLockHelper
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferencesRepository.init(this)
        enableEdgeToEdge()
        viewModel.refreshStatus(this)

        setContent {
            MyApplicationTheme {
                val prefs by viewModel.preferences.collectAsState()
                val deviceState by viewModel.deviceState.collectAsState()
                val context = LocalContext.current

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DoubleTapSettingsScreen(
                        prefs = prefs,
                        deviceState = deviceState,
                        onToggleDoubleTapSleep = { enabled ->
                            viewModel.setDoubleTapToSleep(context, enabled)
                            if (enabled && !ScreenLockHelper.isAccessibilityServiceEnabled(context)) {
                                openAccessibilitySettings(context)
                            }
                        },
                        onToggleFloatingButton = { enabled ->
                            viewModel.setFloatingLockButton(context, enabled)
                            if (enabled) {
                                // Ask for "Appear on top" if not yet granted; floating icon automatically appears once granted!
                                if (!ScreenLockHelper.isOverlayPermissionGranted(context)) {
                                    openOverlayPermissionSettings(context)
                                } else if (!ScreenLockHelper.canLockScreen(context)) {
                                    openAccessibilitySettings(context)
                                }
                            }
                        },
                        onToggleDoubleTapWake = { enabled ->
                            viewModel.setDoubleTapToWake(context, enabled)
                        },
                        onToggleShakeWake = { enabled ->
                            viewModel.setShakeToWake(context, enabled)
                        },
                        onSelectTaps = { viewModel.setRequiredTaps(context, it) },
                        onTapIntervalChange = { viewModel.setMaxTapIntervalMs(context, it) },
                        onToggleVibration = { viewModel.setVibrationEnabled(context, it) },
                        onToggleIgnoreKeyboard = { viewModel.setIgnoreWhenKeyboardOpen(context, it) },
                        onShakeSensitivityChange = { viewModel.setShakeSensitivityThreshold(context, it) },
                        onFloatingSizeChange = { viewModel.setFloatingButtonSizeDp(context, it) },
                        onResetFloatingPosition = { viewModel.resetFloatingButtonPosition(context) },
                        onOpenAccessibility = { openAccessibilitySettings(context) },
                        onOpenOverlaySettings = { openOverlayPermissionSettings(context) },
                        onOpenDeviceAdmin = { openDeviceAdminPrompt(context) },
                        onOpenSystemDisplaySettings = { openSystemDisplaySettings(context) },
                        onLockNow = {
                            if (!ScreenLockHelper.lockScreen(context)) {
                                openAccessibilitySettings(context)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Automatically refreshes permissions and immediately shows Floating Lock Button
        // as soon as user returns from "Appear on top" settings
        viewModel.refreshStatus(this)
    }

    private fun openAccessibilitySettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
        }
    }

    private fun openOverlayPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    private fun openDeviceAdminPrompt(context: Context) {
        if (!ScreenLockHelper.isDeviceAdminActive(context)) {
            try {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(
                        DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        DoubleTapDeviceAdminReceiver.getComponentName(context)
                    )
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Allows DoubleTap and the Floating Lock Button to lock your screen immediately."
                    )
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    private fun openSystemDisplaySettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        } catch (_: Exception) {
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoubleTapSettingsScreen(
    prefs: AppPreferences,
    deviceState: PermissionAndDeviceState,
    onToggleDoubleTapSleep: (Boolean) -> Unit,
    onToggleFloatingButton: (Boolean) -> Unit,
    onToggleDoubleTapWake: (Boolean) -> Unit,
    onToggleShakeWake: (Boolean) -> Unit,
    onSelectTaps: (Int) -> Unit,
    onTapIntervalChange: (Long) -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onToggleIgnoreKeyboard: (Boolean) -> Unit,
    onShakeSensitivityChange: (Float) -> Unit,
    onFloatingSizeChange: (Int) -> Unit,
    onResetFloatingPosition: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenDeviceAdmin: () -> Unit,
    onOpenSystemDisplaySettings: () -> Unit,
    onLockNow: () -> Unit
) {
    val report = deviceState.wakeReport
    val canLock = deviceState.isAccessibilityGranted || deviceState.isDeviceAdminGranted

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("DoubleTap", fontWeight = FontWeight.Bold)
                        Text(
                            "System-Wide Wake & Sleep Utility",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    FilledTonalIconButton(
                        onClick = onLockNow,
                        modifier = Modifier.testTag("test_lock_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock Screen Now")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: 4 Independent Core Controls
            Text(
                text = "Core Controls",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Control 1: Double Tap to Sleep
            val dtSleepStatus = when {
                !prefs.doubleTapToSleepEnabled -> "Supported (Switched OFF)"
                deviceState.isAccessibilityGranted -> "Supported and active (System-wide across all apps)"
                else -> "Permission required (Enable Accessibility Service)"
            }
            val dtSleepActive = prefs.doubleTapToSleepEnabled && deviceState.isAccessibilityGranted

            FeatureControlCard(
                title = "1. Double Tap to Sleep",
                subtitle = "Double-tap anywhere on Home screen, App drawer, WhatsApp, Browser, Settings, or any app to lock.",
                statusText = dtSleepStatus,
                isStatusPositive = dtSleepActive,
                isStatusWarning = prefs.doubleTapToSleepEnabled && !deviceState.isAccessibilityGranted,
                checked = prefs.doubleTapToSleepEnabled,
                onCheckedChange = onToggleDoubleTapSleep,
                testTag = "switch_sleep"
            )

            // Control 2: Floating Lock Button
            val floatingStatus = when {
                !prefs.floatingLockButtonEnabled -> "Supported (Switched OFF)"
                !deviceState.isOverlayGranted -> "Permission required (Appear on top / Display over other apps)"
                !canLock -> "Permission required (Enable Accessibility or Device Admin to lock)"
                else -> "Supported and active (Draggable above all apps • Tap to lock)"
            }
            val floatingActive = prefs.floatingLockButtonEnabled && deviceState.isOverlayGranted && canLock

            FeatureControlCard(
                title = "2. Floating Lock Button",
                subtitle = "Prominent small circular lock icon over all apps. Drag anywhere; single tap immediately locks phone.",
                statusText = floatingStatus,
                isStatusPositive = floatingActive,
                isStatusWarning = prefs.floatingLockButtonEnabled && (!deviceState.isOverlayGranted || !canLock),
                checked = prefs.floatingLockButtonEnabled,
                onCheckedChange = onToggleFloatingButton,
                testTag = "switch_floating_button"
            )

            // Control 3: Double Tap to Wake
            val dtWakeStatus = when {
                report == null -> "Inspecting hardware..."
                !prefs.doubleTapToWakeEnabled -> "${report.doubleTapWakeStatusTitle} (Switched OFF)"
                else -> report.doubleTapWakeStatusTitle
            }
            val dtWakeActive = prefs.doubleTapToWakeEnabled && (report?.hasAccelerometer == true || report?.hasHardwareWakeGestureSensor == true)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "3. Double Tap to Wake",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Wakes screen on rapid double-tap impulse while display is off.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.doubleTapToWakeEnabled,
                            onCheckedChange = onToggleDoubleTapWake,
                            modifier = Modifier.testTag("switch_wake")
                        )
                    }

                    StatusBadge(
                        text = dtWakeStatus,
                        isPositive = dtWakeActive,
                        isWarning = prefs.doubleTapToWakeEnabled && report?.systemDoubleTapSettingEnabled == false
                    )

                    if (report != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Device Compatibility (${report.manufacturer} ${report.deviceModel} • Android ${report.androidRelease}):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = report.doubleTapWakeStatusDetail,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(
                                    onClick = onOpenSystemDisplaySettings,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Open System Display / Gesture Settings")
                                }
                            }
                        }
                    }
                }
            }

            // Control 4: Shake to Wake
            val hasAccel = report?.hasAccelerometer ?: true
            val shakeStatus = when {
                !hasAccel -> "Not supported by device hardware"
                !prefs.shakeToWakeEnabled -> "Supported (Switched OFF)"
                else -> "Supported and active (Screen-off shake wake armed)"
            }
            FeatureControlCard(
                title = "4. Shake to Wake",
                subtitle = "Shake the phone while the display is off to turn on the screen immediately.",
                statusText = shakeStatus,
                isStatusPositive = prefs.shakeToWakeEnabled && hasAccel,
                isStatusWarning = !hasAccel,
                checked = prefs.shakeToWakeEnabled,
                onCheckedChange = onToggleShakeWake,
                testTag = "switch_shake_wake"
            )

            // Section 2: Permissions Status (Never nags for already granted permissions)
            Text(
                text = "Permissions & System Access",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PermissionRow(
                        title = "Accessibility Service",
                        subtitle = "Required for system-wide Double Tap to Sleep & biometric-friendly screen locking",
                        isGranted = deviceState.isAccessibilityGranted,
                        actionLabel = "Enable",
                        onActionClick = onOpenAccessibility,
                        testTag = "btn_accessibility"
                    )

                    HorizontalDivider()

                    PermissionRow(
                        title = "Appear on Top (Overlay)",
                        subtitle = "Required for Floating Lock Button to appear over other apps",
                        isGranted = deviceState.isOverlayGranted,
                        actionLabel = "Grant",
                        onActionClick = onOpenOverlaySettings,
                        testTag = "btn_overlay_permission"
                    )

                    HorizontalDivider()

                    PermissionRow(
                        title = "Device Administrator",
                        subtitle = "Alternative hardware lock mechanism if Accessibility is not used",
                        isGranted = deviceState.isDeviceAdminGranted,
                        actionLabel = "Enable",
                        onActionClick = onOpenDeviceAdmin,
                        testTag = "btn_device_admin"
                    )
                }
            }

            // Section 3: Gesture & Floating Button Preferences
            Text(
                text = "Preferences & Fine-Tuning",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Number of Taps to Sleep: ${prefs.requiredTaps} Taps",
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2, 3, 4).forEach { count ->
                            FilterChip(
                                selected = prefs.requiredTaps == count,
                                onClick = { onSelectTaps(count) },
                                label = { Text("$count Taps") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "Double-Tap Speed Window: ${prefs.maxTapIntervalMs} ms",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Maximum time allowed between consecutive taps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = prefs.maxTapIntervalMs.toFloat(),
                        onValueChange = { onTapIntervalChange(it.toLong()) },
                        valueRange = 200f..750f,
                        steps = 10,
                        modifier = Modifier.testTag("slider_tap_speed")
                    )

                    HorizontalDivider()

                    // Shake to Wake Sensitivity Slider
                    val sensitivityPercent = (((28f - prefs.shakeSensitivityThreshold) / 17f) * 100f).toInt().coerceIn(5, 100)
                    Text(
                        text = "Shake to Wake Sensitivity: $sensitivityPercent%",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Higher sensitivity wakes the screen with a gentler shake",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = 28f - prefs.shakeSensitivityThreshold,
                        onValueChange = { v -> onShakeSensitivityChange((28f - v).coerceIn(11f, 27f)) },
                        valueRange = 1f..17f,
                        modifier = Modifier.testTag("slider_shake_sensitivity")
                    )

                    HorizontalDivider()

                    // Floating Button Size & Position Reset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Floating Button Size: ${prefs.floatingButtonSizeDp} dp",
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onResetFloatingPosition) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Position")
                        }
                    }
                    Slider(
                        value = prefs.floatingButtonSizeDp.toFloat(),
                        onValueChange = { onFloatingSizeChange(it.toInt()) },
                        valueRange = 40f..72f,
                        steps = 7
                    )

                    HorizontalDivider()

                    // Ignore when keyboard is open
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Pause Sleep While Typing", fontWeight = FontWeight.Bold)
                            Text(
                                "Prevents accidental lock when keyboard is open in WhatsApp or Browser",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.ignoreWhenKeyboardOpen,
                            onCheckedChange = onToggleIgnoreKeyboard
                        )
                    }

                    HorizontalDivider()

                    // Haptic vibration feedback
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Haptic Vibration Feedback", fontWeight = FontWeight.Bold)
                            Text(
                                "Subtle vibration pulse when locking or waking screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.vibrationEnabled,
                            onCheckedChange = onToggleVibration
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureControlCard(
    title: String,
    subtitle: String,
    statusText: String,
    isStatusPositive: Boolean,
    isStatusWarning: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.testTag(testTag)
                )
            }

            StatusBadge(
                text = statusText,
                isPositive = isStatusPositive,
                isWarning = isStatusWarning
            )
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    isPositive: Boolean,
    isWarning: Boolean
) {
    val bgColor = when {
        isPositive -> MaterialTheme.colorScheme.secondaryContainer
        isWarning -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isPositive -> MaterialTheme.colorScheme.onSecondaryContainer
        isWarning -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = when {
                    isPositive -> Icons.Default.CheckCircle
                    isWarning -> Icons.Outlined.Warning
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String,
    onActionClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isGranted) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Granted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            FilledTonalButton(
                onClick = onActionClick,
                modifier = Modifier.testTag(testTag)
            ) {
                Text(actionLabel)
            }
        }
    }
}
