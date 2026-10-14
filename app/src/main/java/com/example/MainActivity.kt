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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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

    // App Lock PIN authentication state
    var isAppLocked by remember { mutableStateOf(false) }
    var appPinInput by remember { mutableStateOf("") }
    var appPinError by remember { mutableStateOf<String?>(null) }

    // Map backed-up files by original path or uri
    val backedUpUris = remember(cloudRecords) {
        cloudRecords.map { it.originalPath }.toSet()
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
        if (authManager.isAppLockEnabled()) {
            isAppLocked = true
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
                        // Cloud sync status button in top bar
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
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (currentScreen is Screen.Photos || currentScreen is Screen.Collections ||
                currentScreen is Screen.Files || currentScreen is Screen.Backup || currentScreen is Screen.Settings) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    val navItems = listOf(
                        Screen.Photos to Icons.Default.Photo,
                        Screen.Collections to Icons.Default.Collections,
                        Screen.Files to Icons.Default.Folder,
                        Screen.Backup to Icons.Default.CloudUpload,
                        Screen.Settings to Icons.Default.Settings
                    )

                    navItems.forEach { (screen, icon) ->
                        val isSelected = currentScreen == screen
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentScreen = screen },
                            icon = {
                                if (screen is Screen.Backup && (pendingJobsCount > 0 || failedJobsCount > 0)) {
                                    BadgedBox(badge = {
                                        Badge(containerColor = if (failedJobsCount > 0) StatusError else TelegramBluePrimary) {
                                            Text(if (failedJobsCount > 0) "! " else pendingJobsCount.toString())
                                        }
                                    }) {
                                        Icon(icon, contentDescription = screen.title)
                                    }
                                } else {
                                    Icon(icon, contentDescription = screen.title)
                                }
                            },
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
                                val activeDest = destinations.firstOrNull { it.isEnabled }
                                if (activeDest != null) {
                                    selectedItems.forEach { media ->
                                        backupEngine.enqueueMedia(media, activeDest.channelId)
                                    }
                                    backupEngine.startOrResumeQueue()
                                    currentScreen = Screen.Backup
                                } else {
                                    currentScreen = Screen.Destinations
                                }
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
                        onBackupNow = { item ->
                            scope.launch {
                                val activeDest = destinations.firstOrNull { it.isEnabled }
                                if (activeDest != null) {
                                    backupEngine.enqueueMedia(item, activeDest.channelId)
                                    backupEngine.startOrResumeQueue()
                                    currentScreen = Screen.Backup
                                } else {
                                    currentScreen = Screen.Destinations
                                }
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
                                val activeDest = destinations.firstOrNull { it.isEnabled }
                                if (activeDest != null) {
                                    backupEngine.enqueueFile(file, "application/octet-stream", activeDest.channelId)
                                    backupEngine.startOrResumeQueue()
                                    currentScreen = Screen.Backup
                                } else {
                                    currentScreen = Screen.Destinations
                                }
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
                                val activeDest = destinations.firstOrNull { it.isEnabled }
                                if (activeDest != null) {
                                    val encFile = File(context.filesDir, "vault/${item.encryptedFileName}")
                                    backupEngine.enqueueVaultItem(
                                        encryptedFile = encFile,
                                        originalName = item.originalName,
                                        mimeType = item.mimeType,
                                        ivBase64 = item.ivBase64,
                                        saltBase64 = item.saltBase64,
                                        channelId = activeDest.channelId
                                    )
                                    backupEngine.startOrResumeQueue()
                                    currentScreen = Screen.Backup
                                } else {
                                    currentScreen = Screen.Destinations
                                }
                            }
                        },
                        onBack = { currentScreen = Screen.Collections }
                    )
                }

                is Screen.Restore -> {
                    RestoreScreen(
                        cloudRecords = cloudRecords,
                        onImportManifestJson = { jsonStr ->
                            scope.launch {
                                try {
                                    val manifest = manifestManager.jsonToManifest(jsonStr)
                                    manifestManager.importManifestToDatabase(manifest)
                                } catch (e: Exception) {
                                    e.printStackTrace()
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
                        onNavigateToVault = { currentScreen = Screen.Vault }
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

    // App Lock PIN Modal
    if (isAppLocked) {
        AlertDialog(
            onDismissRequest = { /* Cannot dismiss without PIN */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = TelegramBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("App Locked")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your PIN to access Unlimited T Storage.")
                    OutlinedTextField(
                        value = appPinInput,
                        onValueChange = {
                            appPinInput = it
                            appPinError = null
                        },
                        label = { Text("PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
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
                            if (authManager.verifyAppPin(appPinInput)) {
                                isAppLocked = false
                                appPinInput = ""
                            } else {
                                appPinError = "Incorrect PIN"
                            }
                        }
                    },
                    modifier = Modifier.testTag("app_pin_unlock_button")
                ) {
                    Text("Unlock")
                }
            }
        )
    }
}
