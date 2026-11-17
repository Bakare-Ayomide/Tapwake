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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                LaunchedEffect(uiState.requiredTaps, uiState.maxTapIntervalMs, uiState.vibrationEnabled, uiState.isWakeEnabled, uiState.isSleepEnabled) {
                    DoubleTapForegroundService.requiredTaps = uiState.requiredTaps
                    DoubleTapForegroundService.maxTapIntervalMs = uiState.maxTapIntervalMs
                    DoubleTapForegroundService.vibrationEnabled = uiState.vibrationEnabled
                    DoubleTapForegroundService.isWakeEnabled = uiState.isWakeEnabled
                    DoubleTapForegroundService.isSleepEnabled = uiState.isSleepEnabled
                }

                // Start Foreground Service & Floating Overlay
                LaunchedEffect(uiState.isWakeEnabled || uiState.isSleepEnabled) {
                    val intent = Intent(context, DoubleTapForegroundService::class.java).apply {
                        action = DoubleTapForegroundService.ACTION_TOGGLE_OVERLAY
                        putExtra(DoubleTapForegroundService.EXTRA_ENABLE_OVERLAY, uiState.isSleepEnabled)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
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
    var internalTapCount by remember { mutableStateOf(0) }
    var lastInternalTapTime by remember { mutableStateOf(0L) }
    var testStatusMessage by remember { mutableStateOf("Double-tap this test box to lock screen") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DoubleTap Controls", fontWeight = FontWeight.Bold) },
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
            // Accessibility Permission Status Card
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
                            text = if (isAccessibilityActive) "Accessibility Service Enabled ✓" else "Action Required: Enable Accessibility",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isAccessibilityActive)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (isAccessibilityActive)
                                "Double tap to sleep is ready to lock your phone."
                            else
                                "Tap 'Enable Accessibility Service' below and turn on DoubleTap in your system settings to allow screen locking.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isAccessibilityActive)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Interactive Test Box (Guaranteed working test area)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val now = System.currentTimeMillis()
                            if (now - lastInternalTapTime <= uiState.maxTapIntervalMs) {
                                internalTapCount++
                            } else {
                                internalTapCount = 1
                            }
                            lastInternalTapTime = now

                            if (internalTapCount >= uiState.requiredTaps) {
                                internalTapCount = 0
                                testStatusMessage = "Success! Locking screen..."
                                val locked = DoubleTapAccessibilityService.lockDevice()
                                if (!locked) {
                                    testStatusMessage = "Please enable Accessibility Service first!"
                                }
                            } else {
                                testStatusMessage = "Tap detected! (${internalTapCount}/${uiState.requiredTaps})"
                            }
                        }
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(36.dp))
                    Text("Interactive Double-Tap Test Box", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(testStatusMessage, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
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
                            Text("Double Tap to Wake", fontWeight = FontWeight.Bold)
                            Text("Screen-off sensor wake assist", style = MaterialTheme.typography.bodySmall)
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
                            text = "Note on Wake: True screen-off touch wake requires OEM kernel/firmware support (enable 'Tap to wake' in your phone's system display settings). Proximity & motion assist are active.",
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

            // Gesture Preferences
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

                    Text("Tap Speed Window: ${uiState.maxTapIntervalMs} ms", fontWeight = FontWeight.Bold)
                    Slider(
                        value = uiState.maxTapIntervalMs.toFloat(),
                        onValueChange = { onTapIntervalChange(it.toLong()) },
                        valueRange = 250f..800f,
                        steps = 10
                    )

                    Divider()

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
                            onCheckedChange = onToggleVibration
                        )
                    }
                }
            }

            // Setup & Permissions
            Text(
                text = "Required Setup",
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
                        Text("1. Enable Accessibility Service")
                    }

                    OutlinedButton(
                        onClick = onOpenBatteryOptimization,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("2. Disable Battery Optimization")
                    }
                }
            }
        }
    }
}
