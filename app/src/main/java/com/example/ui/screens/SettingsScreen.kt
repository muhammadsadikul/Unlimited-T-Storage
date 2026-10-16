package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.security.AuthManager
import com.example.ui.theme.SecondaryTextGray
import com.example.ui.theme.SoftBlueAccent
import com.example.ui.theme.VaultPurple
import kotlinx.coroutines.launch

/**
 * Premium Google Photos-inspired Settings Screen.
 * - Grouped item cards without dividers or 1dp borders.
 * - Standard Android toggles with Soft Blue accent.
 * - 20dp rounded corners and airy layout with white background.
 */
@Composable
fun SettingsScreen(
    onNavigateToDestinations: () -> Unit,
    onNavigateToRestore: () -> Unit,
    onNavigateToVault: () -> Unit,
    onNavigateToBackup: () -> Unit,
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
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Section: Cloud & Channels
        item {
            Text(
                text = "Cloud & Storage",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SettingsNavigationItem(
                        title = "Backup Dashboard & Status",
                        subtitle = "Manage active queue and Telegram connection status",
                        icon = Icons.Default.CloudUpload,
                        iconTint = SoftBlueAccent,
                        onClick = onNavigateToBackup,
                        testTag = "settings_backup_item"
                    )
                    SettingsNavigationItem(
                        title = "Telegram Storage Pools",
                        subtitle = "View connection status for all 4 channels",
                        icon = Icons.Default.CloudDone,
                        iconTint = SoftBlueAccent,
                        onClick = onNavigateToDestinations,
                        testTag = "settings_destinations_item"
                    )
                    SettingsNavigationItem(
                        title = "Cloud Restore & Disaster Recovery",
                        subtitle = "Reconstruct catalog and download backed up media",
                        icon = Icons.Default.CloudDownload,
                        iconTint = SoftBlueAccent,
                        onClick = onNavigateToRestore,
                        testTag = "settings_restore_item"
                    )
                    SettingsNavigationItem(
                        title = "Encrypted Vault",
                        subtitle = "Client-side AES-256 encrypted storage",
                        icon = Icons.Default.Lock,
                        iconTint = VaultPurple,
                        onClick = onNavigateToVault,
                        testTag = "settings_vault_item"
                    )
                }
            }
        }

        // Section: Network & Backup Rules
        item {
            Text(
                text = "Backup Preferences",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsToggleItem(
                        title = "Back up over Wi-Fi only",
                        subtitle = "Prevent mobile data consumption for large uploads",
                        icon = Icons.Default.Wifi,
                        iconTint = SoftBlueAccent,
                        checked = wifiOnly,
                        onCheckedChange = { wifiOnly = it },
                        testTag = "switch_wifi_only"
                    )
                    SettingsToggleItem(
                        title = "Back up while charging only",
                        subtitle = "Conserve device battery during large file uploads",
                        icon = Icons.Default.BatteryChargingFull,
                        iconTint = SoftBlueAccent,
                        checked = chargingOnly,
                        onCheckedChange = { chargingOnly = it },
                        testTag = "switch_charging_only"
                    )
                }
            }
        }

        // Section: Privacy & App Security
        item {
            Text(
                text = "Privacy & Security",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsToggleItem(
                        title = "App Lock (PIN & Biometric)",
                        subtitle = "Require authentication to open vault and view media",
                        icon = Icons.Default.Lock,
                        iconTint = SoftBlueAccent,
                        checked = isAppLockEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showAppLockDialog = true
                            } else {
                                scope.launch {
                                    authManager.setAppLockEnabled(false)
                                    authManager.clearAppLockPin()
                                    isAppLockEnabled = false
                                }
                            }
                        },
                        testTag = "switch_app_lock"
                    )
                }
            }
        }

        // Section: About & Help
        item {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SettingsNavigationItem(
                        title = "Disaster Recovery Guide",
                        subtitle = "Learn how to restore all data on a new phone",
                        icon = Icons.Default.Info,
                        iconTint = SoftBlueAccent,
                        onClick = { showRecoveryGuide = true },
                        testTag = "settings_guide_item"
                    )
                    SettingsNavigationItem(
                        title = "Share Application",
                        subtitle = "Share Unlimited T Storage with friends",
                        icon = Icons.Default.Share,
                        iconTint = SoftBlueAccent,
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Unlimited T Storage: Unlimited Cloud Storage for Android backed by Telegram.")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share App"))
                        },
                        testTag = "settings_share_item"
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // App Lock PIN Setup Dialog
    if (showAppLockDialog) {
        AlertDialog(
            onDismissRequest = {
                showAppLockDialog = false
                pinInput = ""
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            title = { Text("Set 4-Digit Security PIN", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            text = {
                Column {
                    Text(
                        "Enter a 4-digit PIN to lock access to your media and encrypted files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryTextGray
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) pinInput = it },
                        label = { Text("PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().testTag("pin_setup_input"),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SoftBlueAccent,
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length == 4) {
                            scope.launch {
                                authManager.saveAppLockPin(pinInput)
                                authManager.setAppLockEnabled(true)
                                isAppLockEnabled = true
                                showAppLockDialog = false
                                pinInput = ""
                            }
                        }
                    },
                    enabled = pinInput.length == 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SoftBlueAccent),
                    modifier = Modifier.testTag("confirm_pin_button")
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAppLockDialog = false
                        pinInput = ""
                    }
                ) {
                    Text("Cancel", color = SecondaryTextGray)
                }
            }
        )
    }

    // Disaster Recovery Guide Dialog
    if (showRecoveryGuide) {
        AlertDialog(
            onDismissRequest = { showRecoveryGuide = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            title = { Text("Disaster Recovery Guide", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Your media is backed up directly to your 4 private Telegram storage channels.", style = MaterialTheme.typography.bodySmall, color = SecondaryTextGray)
                    Text("2. The encrypted catalog is automatically updated and pinned in your channel.", style = MaterialTheme.typography.bodySmall, color = SecondaryTextGray)
                    Text("3. On any new or reformatted device, open Cloud Restore, enter your Master Password, and tap Decrypt.", style = MaterialTheme.typography.bodySmall, color = SecondaryTextGray)
                    Text("4. The app will automatically connect to Telegram, retrieve your catalog, and restore all files.", style = MaterialTheme.typography.bodySmall, color = SecondaryTextGray)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showRecoveryGuide = false },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SoftBlueAccent)
                ) {
                    Text("Got It")
                }
            }
        )
    }
}

@Composable
private fun SettingsNavigationItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryTextGray
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = SecondaryTextGray.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryTextGray
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SoftBlueAccent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCBD5E1),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
