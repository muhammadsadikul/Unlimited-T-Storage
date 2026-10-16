package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.backup.BackupEngine
import com.example.backup.ManifestManager
import com.example.data.database.AppDatabase
import com.example.data.database.entity.FolderRuleEntity
import com.example.data.database.entity.LocalMediaEntity
import com.example.data.database.entity.VaultItemEntity
import com.example.media.MediaScanner
import com.example.security.AuthManager
import com.example.security.CryptoManager
import com.example.ui.navigation.Screen
import com.example.ui.screens.BackupScreen
import com.example.ui.screens.CollectionsScreen
import com.example.ui.screens.DestinationsScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.MediaDetailScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PhotosScreen
import com.example.ui.screens.RestoreScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusUploading
import com.example.ui.theme.TelegramBluePrimary
import com.example.ui.theme.VaultPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val db = remember { AppDatabase.getDatabase(context) }
    val mediaDao = remember { db.mediaDao() }
    val cloudDao = remember { db.cloudRecordDao() }
    val jobDao = remember { db.backupJobDao() }
    val destDao = remember { db.destinationDao() }
    val vaultDao = remember { db.vaultDao() }
    val folderRuleDao = remember { db.folderRuleDao() }

    val mediaScanner = remember { MediaScanner(context) }
    val backupEngine = remember { BackupEngine(context) }
    val manifestManager = remember { ManifestManager(context) }
    val authManager = remember { AuthManager(context) }

    // Collect Room database flows
    val mediaList by mediaDao.getAllMediaFlow().collectAsState(initial = emptyList())
    val cloudRecords by cloudDao.getAllRecordsFlow().collectAsState(initial = emptyList())
    val totalMediaCount by mediaDao.getMediaCountFlow().collectAsState(initial = 0)
    val cloudTotalCount by cloudDao.getTotalCountFlow().collectAsState(initial = 0)
    val cloudTotalBytes by cloudDao.getTotalBytesFlow().collectAsState(initial = 0L)
    val recentJobs by jobDao.getAllJobsFlow().collectAsState(initial = emptyList())
    val pendingJobsCount by jobDao.getPendingCountFlow().collectAsState(initial = 0)
    val failedJobsCount by jobDao.getFailedCountFlow().collectAsState(initial = 0)
    val destinations by destDao.getAllDestinationsFlow().collectAsState(initial = emptyList())
    val vaultItems by vaultDao.getAllVaultItemsFlow().collectAsState(initial = emptyList())
    val folderRules by folderRuleDao.getAllRulesFlow().collectAsState(initial = emptyList())

    val isUploading by backupEngine.isUploading.collectAsState()
    val currentUploadingName by backupEngine.currentJobFileName.collectAsState()
    val currentUploadingProgress by backupEngine.currentJobProgress.collectAsState()

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Photos) }
    var selectedMediaForDetail by remember { mutableStateOf<LocalMediaEntity?>(null) }
    var isScanning by remember { mutableStateOf(false) }

    // App Lock & Master Password authentication state
    var isMasterPasswordSetup by remember { mutableStateOf(false) }
    var isMasterPasswordUnlocked by remember { mutableStateOf(false) }
    var showMasterPasswordSetupDialog by remember { mutableStateOf(false) }
    var masterPasswordInput by remember { mutableStateOf("") }
    var masterPasswordConfirmInput by remember { mutableStateOf("") }
    var masterPasswordError by remember { mutableStateOf<String?>(null) }

    var isAppLocked by remember { mutableStateOf(false) }
    var appPinInput by remember { mutableStateOf("") }
    var appPinError by remember { mutableStateOf<String?>(null) }

    // Automatic Telegram Connection State
    var isTelegramConnected by remember { mutableStateOf(false) }
    var telegramConnectionStatusText by remember { mutableStateOf("Connecting to Telegram...") }

    // If master password is required and not yet unlocked, show only local files
    val visibleCloudRecords = remember(cloudRecords, isMasterPasswordUnlocked, isMasterPasswordSetup) {
        if (!isMasterPasswordSetup || isMasterPasswordUnlocked) {
            cloudRecords
        } else {
            emptyList()
        }
    }

    // Map backed-up files by original path or uri
    val backedUpUris = remember(visibleCloudRecords) {
        visibleCloudRecords.map { it.originalPath }.toSet()
    }

    // Permission handling
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            scope.launch {
                isScanning = true
                mediaScanner.scanAllMedia()
                isScanning = false
            }
        }
    }

    fun requestPermissionsAndScan() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val hasPermission = permissionsToRequest.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (hasPermission) {
            scope.launch {
                isScanning = true
                mediaScanner.scanAllMedia()
                isScanning = false
            }
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    LaunchedEffect(Unit) {
        val masterSetup = authManager.isMasterPasswordSetup()
        val isSkipped = authManager.isMasterPasswordSkipped()
        val appLockEnabled = authManager.isAppLockEnabled()
        isMasterPasswordSetup = masterSetup
        if (!masterSetup && !isSkipped) {
            showMasterPasswordSetupDialog = true
        } else if (appLockEnabled && masterSetup) {
            isAppLocked = true
        } else {
            isMasterPasswordUnlocked = true
        }

        // Automatic Telegram connection initialization across all 4 channels
        scope.launch(Dispatchers.IO) {
            try {
                val client = com.example.telegram.TelegramApiClient()
                val botRes = client.getMe(com.example.telegram.TelegramConfig.BOT_TOKEN)
                if (botRes.isFailure) {
                    isTelegramConnected = false
                    telegramConnectionStatusText = "Connection failed. Please check your internet."
                } else {
                    var connectedCount = 0
                    for ((index, chId) in com.example.Constants.TELEGRAM_CHANNEL_IDS.withIndex()) {
                        val formattedChId = com.example.Constants.formatChannelId(chId)
                        val chatRes = client.getChat(com.example.telegram.TelegramConfig.BOT_TOKEN, formattedChId)
                        val isConn = chatRes.isSuccess
                        val title = if (isConn) {
                            connectedCount++
                            chatRes.getOrThrow().title.ifBlank { "Channel ${index + 1}" }
                        } else {
                            "Channel ${index + 1}"
                        }

                        destDao.insert(
                            com.example.data.database.entity.BackupDestinationEntity(
                                channelId = formattedChId,
                                title = title,
                                botToken = com.example.telegram.TelegramConfig.BOT_TOKEN,
                                isEnabled = isConn,
                                lastBackupAt = System.currentTimeMillis()
                            )
                        )
                    }
                    if (connectedCount > 0) {
                        isTelegramConnected = true
                        telegramConnectionStatusText = "Connected ($connectedCount/4 channels active)"
                    } else {
                        isTelegramConnected = false
                        telegramConnectionStatusText = "Connection failed. Please check your internet."
                    }
                }
            } catch (e: Exception) {
                isTelegramConnected = false
                telegramConnectionStatusText = "Connection failed. Please check your internet."
            }
        }

        com.example.backup.BackupWorker.enqueuePeriodicBackup(context)
        requestPermissionsAndScan()
    }

    // Handle back button for sub-screens
    BackHandler(enabled = currentScreen !is Screen.Photos) {
        currentScreen = Screen.Photos
    }

    Scaffold(
        topBar = {
            if (currentScreen !is Screen.MediaDetail && currentScreen !is Screen.Onboarding) {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentScreen.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    actions = {
                        if (currentScreen is Screen.Photos) {
                            IconButton(onClick = { currentScreen = Screen.Backup }) {
                                if (isUploading) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = "Uploading", tint = StatusUploading)
                                } else if (failedJobsCount > 0) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = "Failed uploads", tint = StatusError)
                                } else if (cloudRecords.isNotEmpty()) {
                                    Icon(Icons.Default.CloudDone, contentDescription = "Synced", tint = StatusSuccess)
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = "Backup", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (currentScreen is Screen.Photos || currentScreen is Screen.Collections ||
                currentScreen is Screen.Files || currentScreen is Screen.Settings) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    val navItems = listOf(
                        Screen.Photos to Icons.Default.Photo,
                        Screen.Collections to Icons.Default.Collections,
                        Screen.Files to Icons.Default.Folder,
                        Screen.Settings to Icons.Default.Settings
                    )

                    navItems.forEach { (screen, icon) ->
                        val isSelected = currentScreen == screen
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentScreen = screen },
                            icon = { Icon(icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TelegramBluePrimary,
                                indicatorColor = TelegramBluePrimary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_${screen.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val screen = currentScreen) {
                is Screen.Photos -> {
                    PhotosScreen(
                        mediaList = mediaList,
                        backedUpUris = backedUpUris,
                        onMediaClick = { item ->
                            selectedMediaForDetail = item
                            currentScreen = Screen.MediaDetail
                        },
                        onBackupSelected = { selectedItems ->
                            scope.launch {
                                selectedItems.forEach { media ->
                                    backupEngine.enqueueMedia(media)
                                }
                                backupEngine.startOrResumeQueue()
                                currentScreen = Screen.Backup
                            }
                        },
                        onToggleFavorite = { item ->
                            scope.launch {
                                mediaDao.updateFavorite(item.id, !item.isFavorite)
                            }
                        },
                        onScanRequest = { requestPermissionsAndScan() },
                        isScanning = isScanning
                    )
                }

                is Screen.MediaDetail -> {
                    val media = selectedMediaForDetail
                    MediaDetailScreen(
                        media = media,
                        isBackedUp = media != null && backedUpUris.contains(media.path),
                        onBack = { currentScreen = Screen.Photos },
                        onToggleFavorite = { item ->
                            scope.launch { mediaDao.updateFavorite(item.id, !item.isFavorite) }
                        },
                        onDeleteMedia = { item ->
                            scope.launch {
                                com.example.ui.screens.deleteMediaFromDevice(context, item)
                                mediaDao.delete(item)
                                selectedMediaForDetail = null
                                currentScreen = Screen.Photos
                            }
                        }
                    )
                }

                is Screen.Collections -> {
                    CollectionsScreen(
                        mediaList = mediaList,
                        folderRules = folderRules,
                        onFolderRuleToggle = { folder, isEnabled ->
                            scope.launch {
                                folderRuleDao.insertOrUpdate(
                                    FolderRuleEntity(folderPath = folder, folderName = folder, isEnabled = isEnabled)
                                )
                            }
                        },
                        onOpenVault = { currentScreen = Screen.Vault },
                        onSelectFolder = { _ ->
                            currentScreen = Screen.Photos
                        }
                    )
                }

                is Screen.Files -> {
                    FilesScreen(
                        onBackupFile = { file ->
                            scope.launch {
                                backupEngine.enqueueFile(file, "application/octet-stream")
                                backupEngine.startOrResumeQueue()
                                currentScreen = Screen.Backup
                            }
                        }
                    )
                }

                is Screen.Backup -> {
                    BackupScreen(
                        totalMediaCount = totalMediaCount,
                        cloudRecordsCount = cloudTotalCount,
                        cloudTotalBytes = cloudTotalBytes,
                        pendingJobsCount = pendingJobsCount,
                        failedJobsCount = failedJobsCount,
                        destinations = destinations,
                        recentJobs = recentJobs,
                        isUploading = isUploading,
                        currentUploadingName = currentUploadingName,
                        currentUploadingProgress = currentUploadingProgress,
                        isTelegramConnected = isTelegramConnected,
                        connectionStatusText = telegramConnectionStatusText,
                        onStartBackup = {
                            backupEngine.startOrResumeQueue()
                            com.example.backup.BackupWorker.enqueueImmediateBackup(context)
                        },
                        onPauseBackup = { backupEngine.pauseQueue() },
                        onRetryFailed = { backupEngine.retryFailed() },
                        onManageDestinations = { currentScreen = Screen.Destinations }
                    )
                }

                is Screen.Destinations -> {
                    DestinationsScreen(
                        destinations = destinations,
                        onAddOrUpdateDestination = { dest ->
                            scope.launch { destDao.insert(dest) }
                        },
                        onDeleteDestination = { dest ->
                            scope.launch { destDao.delete(dest) }
                        },
                        onBack = { currentScreen = Screen.Backup }
                    )
                }

                is Screen.Vault -> {
                    VaultScreen(
                        vaultItems = vaultItems,
                        onImportVaultItem = { name, mimeType, originalFile, password ->
                            scope.launch(Dispatchers.IO) {
                                val vaultDir = File(context.filesDir, "vault").also { it.mkdirs() }
                                val encFileName = "enc_${UUID.randomUUID()}.dat"
                                val encryptedFile = File(vaultDir, encFileName)
                                val meta = CryptoManager.encryptFile(originalFile, encryptedFile, password)
                                val sha256 = CryptoManager.calculateSha256(encryptedFile)

                                vaultDao.insert(
                                    VaultItemEntity(
                                        originalName = name,
                                        encryptedFileName = encFileName,
                                        mimeType = mimeType,
                                        size = encryptedFile.length(),
                                        ivBase64 = meta.ivBase64,
                                        saltBase64 = meta.saltBase64,
                                        sha256 = sha256
                                    )
                                )
                                originalFile.delete()
                            }
                        },
                        onDeleteVaultItem = { item ->
                            scope.launch(Dispatchers.IO) {
                                val encFile = File(context.filesDir, "vault/${item.encryptedFileName}")
                                encFile.delete()
                                vaultDao.delete(item)
                            }
                        },
                        onBackupVaultItem = { item, _ ->
                            scope.launch {
                                val encFile = File(context.filesDir, "vault/${item.encryptedFileName}")
                                backupEngine.enqueueVaultItem(
                                    encryptedFile = encFile,
                                    originalName = item.originalName,
                                    mimeType = item.mimeType,
                                    ivBase64 = item.ivBase64,
                                    saltBase64 = item.saltBase64
                                )
                                backupEngine.startOrResumeQueue()
                                currentScreen = Screen.Backup
                            }
                        },
                        onBack = { currentScreen = Screen.Collections }
                    )
                }

                is Screen.Restore -> {
                    RestoreScreen(
                        cloudRecords = visibleCloudRecords,
                        onImportEncryptedManifest = { rawContent, masterPwd, onResult ->
                            scope.launch {
                                try {
                                    val result = manifestManager.decryptAndParseManifest(rawContent, masterPwd)
                                    result.fold(
                                        onSuccess = { manifest ->
                                            manifestManager.importManifestToDatabase(manifest)
                                            isMasterPasswordUnlocked = true
                                            onResult(true, "Restored ${manifest.totalFiles} cloud records successfully!")
                                        },
                                        onFailure = { err ->
                                            onResult(false, err.message ?: "Decryption failed. Please check Master Password.")
                                        }
                                    )
                                } catch (e: Exception) {
                                    onResult(false, e.message ?: "Failed to process manifest")
                                }
                            }
                        },
                        onBack = { currentScreen = Screen.Settings }
                    )
                }

                is Screen.Settings -> {
                    SettingsScreen(
                        onNavigateToDestinations = { currentScreen = Screen.Destinations },
                        onNavigateToRestore = { currentScreen = Screen.Restore },
                        onNavigateToVault = { currentScreen = Screen.Vault },
                        onNavigateToBackup = { currentScreen = Screen.Backup }
                    )
                }

                is Screen.Onboarding -> {
                    OnboardingScreen(
                        onGetStarted = { currentScreen = Screen.Photos }
                    )
                }
            }
        }
    }

    // First-Launch Master Password Setup Modal (Futuristic Modern Design)
    if (showMasterPasswordSetupDialog) {
        AlertDialog(
            onDismissRequest = { /* Require deliberate action */ },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(TelegramBluePrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = TelegramBluePrimary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        "Secure Master Vault",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Set a secure Master Password to encrypt your cloud backup manifests. Your backups are fully secured with client-side encryption.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = masterPasswordInput,
                        onValueChange = {
                            masterPasswordInput = it
                            masterPasswordError = null
                        },
                        label = { Text("Master Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("setup_master_password_input")
                    )

                    OutlinedTextField(
                        value = masterPasswordConfirmInput,
                        onValueChange = {
                            masterPasswordConfirmInput = it
                            masterPasswordError = null
                        },
                        label = { Text("Confirm Master Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("setup_master_password_confirm_input")
                    )

                    if (masterPasswordError != null) {
                        Text(masterPasswordError!!, color = StatusError, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (masterPasswordInput.length < 4) {
                            masterPasswordError = "Password must be at least 4 characters"
                            return@Button
                        }
                        if (masterPasswordInput != masterPasswordConfirmInput) {
                            masterPasswordError = "Passwords do not match"
                            return@Button
                        }
                        scope.launch {
                            authManager.setupMasterPassword(masterPasswordInput)
                            authManager.setMasterPasswordSkipped(false)
                            isMasterPasswordSetup = true
                            isMasterPasswordUnlocked = true
                            showMasterPasswordSetupDialog = false
                            masterPasswordInput = ""
                            masterPasswordConfirmInput = ""
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBluePrimary),
                    modifier = Modifier.testTag("setup_master_password_button")
                ) {
                    Text("Protect & Encrypt", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            authManager.setMasterPasswordSkipped(true)
                            showMasterPasswordSetupDialog = false
                            isMasterPasswordUnlocked = true
                        }
                    }
                ) {
                    Text("Skip (Local Mode)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // App Lock & Cloud Unlock Modal (Futuristic Modern Design)
    if (isAppLocked) {
        AlertDialog(
            onDismissRequest = { /* Must enter credentials or select local mode */ },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(TelegramBluePrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = TelegramBluePrimary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        "Unlock Vault",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Enter your Master Password or PIN to decrypt cloud catalogs and access Telegram backups.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = appPinInput,
                        onValueChange = {
                            appPinInput = it
                            appPinError = null
                        },
                        label = { Text("Master Password / PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("app_pin_input")
                    )
                    if (appPinError != null) {
                        Text(appPinError!!, color = StatusError, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            when (val result = authManager.verifyMasterPassword(appPinInput)) {
                                is com.example.security.AuthResult.Success -> {
                                    isAppLocked = false
                                    isMasterPasswordUnlocked = true
                                    appPinInput = ""
                                    appPinError = null
                                }
                                is com.example.security.AuthResult.LockedOut -> {
                                    appPinError = "Locked out for ${result.remainingSeconds}s"
                                }
                                is com.example.security.AuthResult.InvalidPassword -> {
                                    if (authManager.verifyAppPin(appPinInput)) {
                                        isAppLocked = false
                                        isMasterPasswordUnlocked = true
                                        appPinInput = ""
                                        appPinError = null
                                    } else {
                                        appPinError = "Incorrect password or PIN"
                                    }
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBluePrimary),
                    modifier = Modifier.testTag("app_pin_unlock_button")
                ) {
                    Text("Unlock", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            authManager.setMasterPasswordSkipped(true)
                            isAppLocked = false
                            isMasterPasswordUnlocked = true
                            appPinInput = ""
                            appPinError = null
                        }
                    }
                ) {
                    Text("Skip (Local Mode)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
