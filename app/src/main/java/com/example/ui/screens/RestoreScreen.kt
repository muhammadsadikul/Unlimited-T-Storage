package com.example.ui.screens

import android.os.Environment
import android.text.format.DateFormat
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.database.entity.CloudRecordEntity
import com.example.security.CryptoManager
import com.example.telegram.TelegramApiClient
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusUploading
import com.example.ui.theme.TelegramBluePrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreScreen(
    cloudRecords: List<CloudRecordEntity>,
    onImportManifestJson: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val telegramClient = remember { TelegramApiClient() }

    var botToken by remember { mutableStateOf("") }
    var manifestJsonInput by remember { mutableStateOf("") }
    var showGuideDialog by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
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
            // Recovery Concept Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = TelegramBluePrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New-Phone Cloud Recovery", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Lost your old phone? Paste your backup manifest JSON or load from Telegram to reconstruct your entire photo & file cloud library without needing the old device's local database.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                            value = botToken,
                            onValueChange = { botToken = it },
                            label = { Text("Telegram Bot Token (for downloading files)") },
                            placeholder = { Text("123456:ABC-DEF...") },
                            modifier = Modifier.fillMaxWidth().testTag("restore_bot_token"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = manifestJsonInput,
                            onValueChange = { manifestJsonInput = it },
                            label = { Text("Paste Manifest JSON Content") },
                            placeholder = { Text("{\"version\":1, \"entries\":[...]} or export from TG channel") },
                            modifier = Modifier.fillMaxWidth().height(100.dp).testTag("restore_manifest_json_input"),
                            maxLines = 4
                        )

                        Button(
                            onClick = {
                                if (manifestJsonInput.isNotBlank()) {
                                    onImportManifestJson(manifestJsonInput.trim())
                                }
                            },
                            enabled = manifestJsonInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().testTag("import_manifest_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBluePrimary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Manifest & Reconstruct Index")
                        }
                    }
                }
            }

            // Progress Banner
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
                    Text("Cloud Backed-Up Files (${cloudRecords.size})", style = MaterialTheme.typography.titleMedium)

                    if (cloudRecords.isNotEmpty()) {
                        Button(
                            onClick = {
                                if (botToken.isBlank()) {
                                    restoreStatusText = "Please provide your Bot Token above to download files."
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
                                            botToken = botToken.trim(),
                                            fileId = record.telegramFileId,
                                            destinationFile = destFile
                                        )

                                        if (downloadResult.isSuccess) {
                                            // Verify SHA-256 checksum!
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
                            Text("No cloud records yet. Back up files or paste a manifest to recover.")
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
                title = { Text("Disaster Recovery Guide") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. When you configure backup on your primary device, the app continuously updates a versioned manifest 'manifest_v1.json' pinned to your Telegram channel.")
                        Text("2. On your new device: Install TG Cloud Gallery.")
                        Text("3. Add your Bot Token and Channel ID.")
                        Text("4. Download the pinned manifest_v1.json from your Telegram channel, copy its text, and paste it into the field above.")
                        Text("5. Tap 'Import Manifest'. Your entire cloud catalog is restored instantly.")
                        Text("6. Tap 'Restore All' to download originals with automatic SHA-256 integrity verification.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGuideDialog = false }) { Text("Close") }
                }
            )
        }
    }
}
