package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.security.AuthManager
import com.example.ui.theme.TelegramBluePrimary
import com.example.ui.theme.VaultPurple
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateToDestinations: () -> Unit,
    onNavigateToRestore: () -> Unit,
    onNavigateToVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val scope = rememberCoroutineScope()

    var isAppLockEnabled by remember { mutableStateOf(false) }
    var showAppLockDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var wifiOnly by remember { mutableStateOf(true) }
    var chargingOnly by remember { mutableStateOf(false) }
    var showRecoveryGuide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isAppLockEnabled = authManager.isAppLockEnabled()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Cloud & Storage", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsNavigationRow(
                        title = "Telegram Multi-Channel Storage",
                        subtitle = "View 4 channels status & round-robin distribution",
                        icon = Icons.Default.CloudDone,
                        onClick = onNavigateToDestinations,
                        tag = "settings_destinations"
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNavigationRow(
                        title = "Cloud Restore & Disaster Recovery",
                        subtitle = "Rebuild index on a new phone using manifest",
                        icon = Icons.Default.CloudDownload,
                        onClick = onNavigateToRestore,
                        tag = "settings_restore"
                    )
                }
            }
        }

        item {
            Text("Backup Constraints & Rules", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsSwitchRow(
                        title = "Backup on Wi-Fi Only",
                        subtitle = "Prevent mobile data consumption for large uploads",
                        icon = Icons.Default.Wifi,
                        checked = wifiOnly,
                        onCheckedChange = { wifiOnly = it },
                        tag = "switch_wifi_only"
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchRow(
                        title = "Backup while Charging Only",
                        subtitle = "Conserve device battery during large file batches",
                        icon = Icons.Default.BatteryChargingFull,
                        checked = chargingOnly,
                        onCheckedChange = { chargingOnly = it },
                        tag = "switch_charging_only"
                    )
                }
            }
        }

        item {
            Text("Privacy & Security", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsSwitchRow(
                        title = "App Lock (PIN Protection)",
                        subtitle = "Require PIN upon opening Unlimited T Storage",
                        icon = Icons.Default.Lock,
                        checked = isAppLockEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showAppLockDialog = true
                            } else {
                                scope.launch {
                                    authManager.setAppLock(false)
                                    isAppLockEnabled = false
                                }
                            }
                        },
                        tag = "switch_app_lock"
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNavigationRow(
                        title = "Hidden Vault (AES-256-GCM)",
                        subtitle = "Separate password, biometric unlock and client-side encryption",
                        icon = Icons.Default.Security,
                        onClick = onNavigateToVault,
                        tag = "settings_vault"
                    )
                }
            }
        }

        item {
            Text("Support & About", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsNavigationRow(
                        title = "Disaster Recovery Guide",
                        subtitle = "Learn how to preserve and restore your backups",
                        icon = Icons.Default.Info,
                        onClick = { showRecoveryGuide = true },
                        tag = "settings_recovery_guide"
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Unlimited T Storage v1.0", style = MaterialTheme.typography.bodyMedium)
                            Text("Native Android • Material 3 • AES-256-GCM • WorkManager", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showAppLockDialog) {
        AlertDialog(
            onDismissRequest = { showAppLockDialog = false },
            title = { Text("Set App Lock PIN") },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it },
                    label = { Text("Enter 4-6 digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length >= 4) {
                            scope.launch {
                                authManager.setAppLock(true, pinInput)
                                isAppLockEnabled = true
                                showAppLockDialog = false
                                pinInput = ""
                            }
                        }
                    },
                    enabled = pinInput.length >= 4
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAppLockDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRecoveryGuide) {
        AlertDialog(
            onDismissRequest = { showRecoveryGuide = false },
            title = { Text("How To Recover Your Data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("To ensure zero data loss on a new phone:")
                    Text("1. Note your Telegram Bot Token and Channel ID.")
                    Text("2. If using the Hidden Vault, memorize or safely store your Vault Password (recovery is impossible without it!).")
                    Text("3. The app automatically pushes a versioned 'manifest_v1.json' document to your private channel.")
                    Text("4. On any new phone, open Settings -> Cloud Restore, import the manifest, and tap Restore All.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showRecoveryGuide = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TelegramBluePrimary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TelegramBluePrimary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(tag))
    }
}
