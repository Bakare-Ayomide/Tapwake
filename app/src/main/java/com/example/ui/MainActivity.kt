package com.example.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.DoubleTapAccessibilityService
import com.example.service.DoubleTapForegroundService
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                val context = LocalContext.current

                // Sync preferences with Foreground Service static variables
                LaunchedEffect(uiState.requiredTaps, uiState.maxTapIntervalMs, uiState.vibrationEnabled) {
                    DoubleTapForegroundService.requiredTaps = uiState.requiredTaps
                    DoubleTapForegroundService.maxTapIntervalMs = uiState.maxTapIntervalMs
                    DoubleTapForegroundService.vibrationEnabled = uiState.vibrationEnabled
                }

                // Start Foreground Service if either wake or sleep is enabled
                LaunchedEffect(uiState.isWakeEnabled || uiState.isSleepEnabled) {
                    if (uiState.isWakeEnabled || uiState.isSleepEnabled) {
                        val intent = Intent(context, DoubleTapForegroundService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        uiState = uiState,
                        onToggleWake = { viewModel.toggleWake(it) },
                        onToggleSleep = { viewModel.toggleSleep(it) },
                        onSelectTaps = { viewModel.setRequiredTaps(it) },
                        onTapIntervalChange = { viewModel.setMaxTapIntervalMs(it) },
                        onToggleVibration = { viewModel.toggleVibration(it) },
                        onTestLock = {
                            val locked = DoubleTapAccessibilityService.lockDevice()
                            if (!locked) {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        },
                        onOpenAccessibility = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onOpenBatteryOptimization = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: DoubleTapUiState,
    onToggleWake: (Boolean) -> Unit,
    onToggleSleep: (Boolean) -> Unit,
    onSelectTaps: (Int) -> Unit,
    onTapIntervalChange: (Long) -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onTestLock: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBatteryOptimization: () -> Unit
) {
    val isAccessibilityActive = DoubleTapAccessibilityService.isRunning()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DoubleTap Preferences", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(
                        onClick = onTestLock,
                        modifier = Modifier.testTag("test_lock_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock Screen Test")
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
            // Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isAccessibilityActive)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = if (isAccessibilityActive) Icons.Outlined.CheckCircle else Icons.Default.Info,
                        contentDescription = "Status",
                        tint = if (isAccessibilityActive)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAccessibilityActive) "Accessibility Service Ready" else "Accessibility Permission Needed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isAccessibilityActive)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (isAccessibilityActive)
                                "Screen locking is fully active."
                            else
                                "Grant Accessibility permission to enable locking.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isAccessibilityActive)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Core Switches
            Text(
                text = "Core Features",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wake Assist", fontWeight = FontWeight.Bold)
                            Text("Proximity / hardware wake monitoring", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = uiState.isWakeEnabled,
                            onCheckedChange = onToggleWake,
                            modifier = Modifier.testTag("switch_wake")
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.hardwareWakeStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Divider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Double Tap to Sleep", fontWeight = FontWeight.Bold)
                            Text("Lock display instantly on tap pattern", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = uiState.isSleepEnabled,
                            onCheckedChange = onToggleSleep,
                            modifier = Modifier.testTag("switch_sleep")
                        )
                    }
                }
            }

            // Advanced Preferences: Number of Taps & Speed
            Text(
                text = "Gesture Preferences",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Number of taps selector
                    Text("Number of Taps Required: ${uiState.requiredTaps}", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2, 3, 4).forEach { count ->
                            FilterChip(
                                selected = uiState.requiredTaps == count,
                                onClick = { onSelectTaps(count) },
                                label = { Text("$count Taps") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Tap Speed / Time Window slider
                    Text("Tap Speed Window: ${uiState.maxTapIntervalMs} ms", fontWeight = FontWeight.Bold)
                    Text("Maximum interval allowed between taps", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = uiState.maxTapIntervalMs.toFloat(),
                        onValueChange = { onTapIntervalChange(it.toLong()) },
                        valueRange = 250f..800f,
                        steps = 10,
                        modifier = Modifier.testTag("slider_tap_speed")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Fast (250ms)", style = MaterialTheme.typography.bodySmall)
                        Text("Relaxed (800ms)", style = MaterialTheme.typography.bodySmall)
                    }

                    Divider()

                    // Vibration feedback toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vibration Feedback", fontWeight = FontWeight.Bold)
                            Text("Haptic pulse on successful lock", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = uiState.vibrationEnabled,
                            onCheckedChange = onToggleVibration,
                            modifier = Modifier.testTag("switch_vibration")
                        )
                    }
                }
            }

            // Permissions & Setup
            Text(
                text = "Setup & Permissions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onOpenAccessibility,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable Accessibility Service")
                    }

                    OutlinedButton(
                        onClick = onOpenBatteryOptimization,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable Battery Optimization")
                    }
                }
            }
        }
    }
}
