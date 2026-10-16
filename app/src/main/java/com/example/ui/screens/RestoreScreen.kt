package com.example.ui.screens

import android.os.Environment
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backup.ManifestManager
import com.example.data.database.entity.CloudRecordEntity
import com.example.telegram.RestoreManager
import com.example.ui.theme.SecondaryTextGray
import com.example.ui.theme.SoftBlueAccent
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Premium Google Photos-inspired Cloud Restore Screen.
 * - Manual JSON paste field has been completely removed.
 * - When user enters Master Password and taps "Decrypt & Restore Catalog",
 *   the app automatically connects to Telegram channels, finds the manifest,
 *   downloads it, decrypts it, and reconstructs the media catalog.
 * - Clean, airy layout with 16dp/20dp rounded corners, no harsh borders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreScreen(
    cloudRecords: List<CloudRecordEntity>,
    onImportEncryptedManifest: (rawContent: String, masterPassword: String, onResult: (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manifestManager = remember { ManifestManager(context) }
    val restoreManager = remember { RestoreManager() }

    var masterPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isDecryptingManifest by remember { mutableStateOf(false) }
    var decryptStatusMessage by remember { mutableStateOf<String?>(null) }
    var isDecryptSuccess by remember { mutableStateOf(false) }

    var isRestoring by remember { mutableStateOf(false) }
    var restoreStatusText by remember { mutableStateOf<String?>(null) }
    var restoreProgress by remember { mutableStateOf(0) }

    val restoredIds = remember { mutableStateListOf<Long>() }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Cloud Restore",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("restore_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header / Explanatory Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SoftBlueAccent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = SoftBlueAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Disaster Recovery",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Enter your Master Password to automatically locate and reconstruct your cloud catalog from Telegram.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryTextGray
                            )
                        }
                    }
                }
            }

            // Automatic Manifest Decryption Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Catalog Authentication",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = masterPassword,
                            onValueChange = { masterPassword = it },
                            label = { Text("Master Password") },
                            placeholder = { Text("Enter password used to encrypt catalog") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = SoftBlueAccent)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                        tint = SecondaryTextGray
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("master_password_field"),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SoftBlueAccent,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            singleLine = true
                        )

                        if (decryptStatusMessage != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDecryptSuccess) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                                )
                            ) {
                                Text(
                                    text = decryptStatusMessage!!,
                                    color = if (isDecryptSuccess) StatusSuccess else StatusError,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (masterPassword.isNotBlank()) {
                                    isDecryptingManifest = true
                                    decryptStatusMessage = null
                                    scope.launch(Dispatchers.IO) {
                                        val res = manifestManager.fetchManifestFromTelegramAndDecrypt(masterPassword.trim())
                                        withContext(Dispatchers.Main) {
                                            isDecryptingManifest = false
                                            if (res.isSuccess) {
                                                val manifest = res.getOrThrow()
                                                isDecryptSuccess = true
                                                decryptStatusMessage = "Successfully connected to Telegram! Reconstructed ${manifest.totalFiles} cloud items."
                                            } else {
                                                isDecryptSuccess = false
                                                val ex = res.exceptionOrNull()
                                                decryptStatusMessage = if (ex is java.io.FileNotFoundException) {
                                                    "Manifest not found on Telegram. Please ensure a backup has been performed and published first."
                                                } else {
                                                    ex?.message ?: "Decryption failed. Please check your Master Password."
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = masterPassword.isNotBlank() && !isDecryptingManifest,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auto_restore_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SoftBlueAccent)
                        ) {
                            if (isDecryptingManifest) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Connecting & Decrypting...", fontWeight = FontWeight.SemiBold)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Decrypt & Reconstruct Index", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Restore Progress Card
            if (isRestoring) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = restoreStatusText ?: "Downloading files...",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = SoftBlueAccent
                                )
                                Text(
                                    text = "$restoreProgress%",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = SoftBlueAccent
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { restoreProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = SoftBlueAccent,
                                trackColor = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }

            // Cloud Records Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cloud Backups (${cloudRecords.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (cloudRecords.isNotEmpty()) {
                        Button(
                            onClick = {
                                isRestoring = true
                                scope.launch(Dispatchers.IO) {
                                    val restoreDir = File(
                                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                        "TelegramRestore"
                                    ).also { it.mkdirs() }

                                    var completed = 0
                                    val total = cloudRecords.size
                                    for (record in cloudRecords) {
                                        withContext(Dispatchers.Main) {
                                            restoreStatusText = "Downloading ${record.fileName}..."
                                            restoreProgress = ((completed * 100) / total.coerceAtLeast(1))
                                        }

                                        val downloadResult = restoreManager.downloadRecord(
                                            record = record,
                                            destinationDir = restoreDir
                                        )

                                        if (downloadResult.isSuccess) {
                                            withContext(Dispatchers.Main) {
                                                restoredIds.add(record.id)
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
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                            modifier = Modifier.testTag("download_all_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore All", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (cloudRecords.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No cloud records found yet. Enter your Master Password above to fetch your catalog.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryTextGray
                            )
                        }
                    }
                }
            } else {
                items(cloudRecords, key = { it.id }) { record ->
                    val isRestored = restoredIds.contains(record.id)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRestored) StatusSuccess.copy(alpha = 0.15f)
                                        else SoftBlueAccent.copy(alpha = 0.1f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isRestored) Icons.Default.CheckCircle else Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = if (isRestored) StatusSuccess else SoftBlueAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.fileName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1
                                )
                                Text(
                                    text = Formatter.formatFileSize(context, record.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryTextGray
                                )
                            }

                            if (isRestored) {
                                Text(
                                    text = "Restored",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = StatusSuccess
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}
