package com.example.ui.screens

import android.os.Environment
import android.text.format.Formatter
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.database.entity.CloudRecordEntity
import com.example.security.CryptoManager
import com.example.telegram.TelegramApiClient
import com.example.telegram.TelegramConfig
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TelegramBluePrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreScreen(
    cloudRecords: List<CloudRecordEntity>,
    onImportEncryptedManifest: (rawContent: String, masterPassword: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val telegramClient = remember { TelegramApiClient() }

    var masterPassword by remember { mutableStateOf("") }
    var manifestContentInput by remember { mutableStateOf("") }
    var showGuideDialog by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var isDecryptingManifest by remember { mutableStateOf(false) }
    var decryptStatusMessage by remember { mutableStateOf<String?>(null) }
    var isDecryptSuccess by remember { mutableStateOf(false) }
    var restoreStatusText by remember { mutableStateOf<String?>(null) }
    var restoreProgress by remember { mutableStateOf(0) }

    val selectedRecords = remember { mutableStateListOf<CloudRecordEntity>() }
    val restoredIds = remember { mutableStateListOf<Long>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Restore & Recovery") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("restore_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showGuideDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Recovery Guide")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Critical Security Warning Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Critical Master Password Protection", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Your cloud backup index is encrypted with AES-256-GCM. If you do not enter your exact Master Password, the cloud backup cannot be decrypted and will remain locked.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Manifest Import Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Rebuild Cloud File Index", style = MaterialTheme.typography.titleSmall)

                        OutlinedTextField(
                            value = masterPassword,
                            onValueChange = {
                                masterPassword = it
                                decryptStatusMessage = null
                            },
                            label = { Text("Master Password (Required to Decrypt)") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().testTag("restore_master_password"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = manifestContentInput,
                            onValueChange = {
                                manifestContentInput = it
                                decryptStatusMessage = null
                            },
                            label = { Text("Encrypted Manifest Content or JSON") },
                            placeholder = { Text("Paste content of manifest_v1.enc downloaded from Telegram channel") },
                            modifier = Modifier.fillMaxWidth().height(90.dp).testTag("restore_manifest_json_input"),
                            maxLines = 4
                        )

                        if (decryptStatusMessage != null) {
                            Text(
                                text = decryptStatusMessage!!,
                                color = if (isDecryptSuccess) StatusSuccess else StatusError,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                if (masterPassword.isNotBlank() && manifestContentInput.isNotBlank()) {
                                    isDecryptingManifest = true
                                    onImportEncryptedManifest(manifestContentInput.trim(), masterPassword.trim()) { success, err ->
                                        isDecryptingManifest = false
                                        isDecryptSuccess = success
                                        decryptStatusMessage = if (success) {
                                            "Decryption successful: Cloud catalog reconstructed!"
                                        } else {
                                            "Decryption failed: ${err ?: "Incorrect Master Password"}"
                                        }
                                    }
                                }
                            },
                            enabled = masterPassword.isNotBlank() && manifestContentInput.isNotBlank() && !isDecryptingManifest,
                            modifier = Modifier.fillMaxWidth().testTag("import_manifest_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBluePrimary)
                        ) {
                            if (isDecryptingManifest) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Decrypting with AES-256-GCM...")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Decrypt & Reconstruct Index")
                            }
                        }
                    }
                }
            }

            // Restore Progress Banner
            if (isRestoring) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(restoreStatusText ?: "Downloading files...", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { restoreProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )
                        }
                    }
                }
            }

            // Recovered Cloud Records List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Decrypted Cloud Backups (${cloudRecords.size})", style = MaterialTheme.typography.titleMedium)

                    if (cloudRecords.isNotEmpty()) {
                        Button(
                            onClick = {
                                if (TelegramConfig.BOT_TOKEN.isBlank()) {
                                    restoreStatusText = "Telegram connection failed. Please check your internet or contact the developer."
                                    return@Button
                                }
                                val toDownload = if (selectedRecords.isNotEmpty()) selectedRecords.toList() else cloudRecords
                                isRestoring = true
                                scope.launch(Dispatchers.IO) {
                                    val restoreDir = File(
                                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                        "TelegramRestore"
                                    ).also { it.mkdirs() }

                                    var completed = 0
                                    for (record in toDownload) {
                                        withContext(Dispatchers.Main) {
                                            restoreStatusText = "Downloading ${record.fileName}..."
                                            restoreProgress = ((completed * 100) / toDownload.size)
                                        }

                                        val destFile = File(restoreDir, record.fileName)
                                        val downloadResult = telegramClient.downloadFile(
                                            botToken = TelegramConfig.BOT_TOKEN,
                                            fileId = record.telegramFileId,
                                            destinationFile = destFile
                                        )

                                        if (downloadResult.isSuccess) {
                                            val downloadedSha = CryptoManager.calculateSha256(destFile)
                                            if (downloadedSha.equals(record.sha256, ignoreCase = true)) {
                                                withContext(Dispatchers.Main) {
                                                    restoredIds.add(record.id)
                                                }
                                            }
                                        }
                                        completed++
                                    }

                                    withContext(Dispatchers.Main) {
                                        isRestoring = false
                                        restoreStatusText = "Restoration complete! Saved to Downloads/TelegramRestore."
                                    }
                                }
                            },
                            enabled = !isRestoring,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                            modifier = Modifier.testTag("download_all_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedRecords.isNotEmpty()) "Restore Selected (${selectedRecords.size})" else "Restore All")
                        }
                    }
                }
            }

            if (cloudRecords.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No cloud backups unlocked. Enter your Master Password and manifest above.")
                        }
                    }
                }
            } else {
                items(cloudRecords, key = { it.id }) { record ->
                    val isSelected = selectedRecords.contains(record)
                    val isRestored = restoredIds.contains(record.id)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    if (it) selectedRecords.add(record) else selectedRecords.remove(record)
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(record.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    text = "${Formatter.formatFileSize(context, record.size)} • Msg ID: ${record.telegramMessageId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "SHA: ${record.sha256.take(16)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (isRestored) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Restored", tint = StatusSuccess, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Verified", color = StatusSuccess, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showGuideDialog) {
            AlertDialog(
                onDismissRequest = { showGuideDialog = false },
                title = { Text("Master Password Disaster Recovery") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. When backups run, the app encrypts your entire file catalog with AES-256-GCM using your Master Password.")
                        Text("2. The encrypted file 'manifest_v1.enc' is sent to your private Telegram channel.")
                        Text("3. On a new phone: Download 'manifest_v1.enc' from your Telegram channel.")
                        Text("4. Enter your Master Password and paste the manifest file content.")
                        Text("5. If the Master Password matches, your files are decrypted and can be restored with SHA-256 verification.")
                        Text("6. If the Master Password is forgotten, encrypted Telegram backups cannot be recovered by anyone.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGuideDialog = false }) { Text("Understood") }
                }
            )
        }
    }
}
