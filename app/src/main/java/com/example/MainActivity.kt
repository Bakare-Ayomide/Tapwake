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
    onTestLock: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBatteryOptimization: () -> Unit
) {
    val isAccessibilityActive = DoubleTapAccessibilityService.isRunning()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DoubleTap", fontWeight = FontWeight.Bold) },
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
                                "Double-tap to sleep is fully active."
                            else
                                "Grant Accessibility permission to enable screen locking.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isAccessibilityActive)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Core Switches Section (Two Independent ON/OFF Switches as requested)
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
                    // Switch 1: Double Tap to Wake
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Double Tap to Wake", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Text("Wake screen via hardware / proximity support", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = uiState.isWakeEnabled,
                            onCheckedChange = onToggleWake,
                            modifier = Modifier.testTag("switch_wake")
                        )
                    }

                    // Hardware Support Status for Wake
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.hardwareWakeStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Divider()

                    // Switch 2: Double Tap to Sleep
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Double Tap to Sleep", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Text("Lock display instantly using Accessibility", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = uiState.isSleepEnabled,
                            onCheckedChange = onToggleSleep,
                            modifier = Modifier.testTag("switch_sleep")
                        )
                    }
                }
            }

            // Setup & Permissions
            Text(
                text = "Configuration & Permissions",
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_accessibility")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Accessibility Settings")
                    }

                    OutlinedButton(
                        onClick = onOpenBatteryOptimization,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_battery")
                    ) {
                        Text("Disable Battery Optimization")
                    }
                }
            }

            // GitHub Actions APK Build info
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🚀 GitHub Actions APK Build",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Workflow configured at .github/workflows/android.yml to automatically build, verify, and upload APK artifacts upon push or manual trigger.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
