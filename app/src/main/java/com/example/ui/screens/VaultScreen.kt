package com.example.ui.screens

import android.net.Uri
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.database.entity.VaultItemEntity
import com.example.security.AuthManager
import com.example.security.CryptoManager
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TelegramBluePrimary
import com.example.ui.theme.VaultPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    vaultItems: List<VaultItemEntity>,
    onImportVaultItem: (name: String, mimeType: String, originalFile: File, password: String) -> Unit,
    onDeleteVaultItem: (VaultItemEntity) -> Unit,
    onBackupVaultItem: (VaultItemEntity, password: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val scope = rememberCoroutineScope()

    var isVaultSetup by remember { mutableStateOf(false) }
    var isUnlocked by remember { mutableStateOf(false) }
    var enteredPassword by remember { mutableStateOf("") }
    var setupPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var selectedItemForPreview by remember { mutableStateOf<VaultItemEntity?>(null) }
    var decryptedPreviewFile by remember { mutableStateOf<File?>(null) }
    var isDecrypting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVaultSetup = authManager.isVaultSetup()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val displayName = "import_${System.currentTimeMillis()}"
                    val tempFile = File(context.cacheDir, "temp_import_$displayName")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    withContext(Dispatchers.Main) {
                        onImportVaultItem(displayName, mimeType, tempFile, enteredPassword)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = VaultPurple)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hidden Vault")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("vault_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isUnlocked) {
                        IconButton(onClick = {
                            isUnlocked = false
                            enteredPassword = ""
                            decryptedPreviewFile = null
                        }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Vault", tint = VaultPurple)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!isUnlocked) {
            // Lock / Setup View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(VaultPurple.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = VaultPurple, modifier = Modifier.size(32.dp))
                        }

                        if (!isVaultSetup) {
                            Text("Create Master Vault PIN", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Files inside the Hidden Vault are encrypted with AES-256-GCM before storage or Telegram backup. Your PIN is never uploaded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = setupPassword,
                                onValueChange = { setupPassword = it },
                                label = { Text("Vault PIN / Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth().testTag("vault_setup_pin"),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Confirm PIN / Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth().testTag("vault_confirm_pin"),
                                singleLine = true
                            )

                            if (passwordError != null) {
                                Text(passwordError!!, color = StatusError, style = MaterialTheme.typography.bodySmall)
                            }

                            Button(
                                onClick = {
                                    if (setupPassword.length < 4) {
                                        passwordError = "PIN must be at least 4 characters"
                                        return@Button
                                    }
                                    if (setupPassword != confirmPassword) {
                                        passwordError = "Passwords do not match"
                                        return@Button
                                    }
                                    scope.launch {
                                        authManager.setupVaultPin(setupPassword)
                                        isVaultSetup = true
                                        enteredPassword = setupPassword
                                        isUnlocked = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("vault_create_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = VaultPurple)
                            ) {
                                Text("Create & Unlock Vault")
                            }
                        } else {
                            Text("Unlock Hidden Vault", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Enter your private vault PIN to view and decrypt your hidden files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = enteredPassword,
                                onValueChange = {
                                    enteredPassword = it
                                    passwordError = null
                                },
                                label = { Text("Vault PIN / Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth().testTag("vault_unlock_pin"),
                                singleLine = true
                            )

                            if (passwordError != null) {
                                Text(passwordError!!, color = StatusError, style = MaterialTheme.typography.bodySmall)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        if (authManager.verifyVaultPin(enteredPassword)) {
                                            isUnlocked = true
                                        } else {
                                            passwordError = "Incorrect PIN. Please try again."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("vault_unlock_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = VaultPurple)
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unlock Vault")
                            }

                            val fragmentActivity = context as? androidx.fragment.app.FragmentActivity
                            if (fragmentActivity != null && com.example.security.BiometricAuthHelper.isBiometricAvailable(context)) {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = {
                                        com.example.security.BiometricAuthHelper.promptBiometric(
                                            activity = fragmentActivity,
                                            onSuccess = { isUnlocked = true },
                                            onError = { err -> passwordError = err }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("vault_biometric_unlock_button")
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = VaultPurple)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Unlock with Biometrics")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Unlocked Vault View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                // Security status banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = VaultPurple.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = VaultPurple)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Client-Side AES-GCM Active", style = MaterialTheme.typography.labelLarge, color = VaultPurple)
                                Text("Zero-knowledge encryption. Cloud backups are encrypted.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${vaultItems.size} Encrypted File(s)", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = VaultPurple),
                        modifier = Modifier.testTag("import_to_vault_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add File")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (vaultItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = VaultPurple.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Your vault is empty", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Add sensitive photos, videos or documents here for full encryption", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(vaultItems, key = { it.id }) { item ->
                            VaultItemCard(
                                item = item,
                                onClick = {
                                    selectedItemForPreview = item
                                    isDecrypting = true
                                    scope.launch(Dispatchers.IO) {
                                        val encryptedFile = File(context.filesDir, "vault/${item.encryptedFileName}")
                                        val decryptedTemp = File(context.cacheDir, "decrypted_${item.originalName}")
                                        val success = CryptoManager.decryptFile(
                                            inputFile = encryptedFile,
                                            outputFile = decryptedTemp,
                                            password = enteredPassword,
                                            saltBase64 = item.saltBase64,
                                            ivBase64 = item.ivBase64
                                        )
                                        withContext(Dispatchers.Main) {
                                            isDecrypting = false
                                            if (success) {
                                                decryptedPreviewFile = decryptedTemp
                                            }
                                        }
                                    }
                                },
                                onBackup = { onBackupVaultItem(item, enteredPassword) },
                                onDelete = { onDeleteVaultItem(item) }
                            )
                        }
                    }
                }
            }
        }

        // Preview Dialog for Decrypted Vault Item
        if (selectedItemForPreview != null) {
            val item = selectedItemForPreview!!
            AlertDialog(
                onDismissRequest = {
                    selectedItemForPreview = null
                    decryptedPreviewFile?.delete()
                    decryptedPreviewFile = null
                },
                title = { Text(item.originalName, maxLines = 1) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isDecrypting) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Decrypting file with AES-256-GCM...")
                        } else if (decryptedPreviewFile != null && decryptedPreviewFile!!.exists()) {
                            if (item.mimeType.startsWith("image")) {
                                AsyncImage(
                                    model = decryptedPreviewFile,
                                    contentDescription = item.originalName,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(64.dp), tint = VaultPurple)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Size: ${Formatter.formatFileSize(context, item.size)}")
                            Text("SHA-256: ${item.sha256.take(16)}...", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Decryption failed. Please check password.")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onBackupVaultItem(item, enteredPassword)
                            selectedItemForPreview = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VaultPurple)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Backup to Telegram")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        selectedItemForPreview = null
                        decryptedPreviewFile?.delete()
                        decryptedPreviewFile = null
                    }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun VaultItemCard(
    item: VaultItemEntity,
    onClick: () -> Unit,
    onBackup: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("vault_item_${item.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(VaultPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = VaultPurple, modifier = Modifier.size(36.dp))
                if (item.isBackedUp) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .background(StatusSuccess, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(item.originalName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(Formatter.formatFileSize(context, item.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackup, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "Backup", tint = TelegramBluePrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusError, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
