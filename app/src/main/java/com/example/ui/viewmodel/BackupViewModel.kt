package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.BackupEngine
import com.example.backup.BackupWorker
import com.example.backup.ManifestManager
import com.example.data.database.AppDatabase
import com.example.data.database.entity.BackupDestinationEntity
import com.example.data.database.entity.BackupJobEntity
import com.example.data.database.entity.CloudRecordEntity
import com.example.telegram.ChannelStatus
import com.example.telegram.TelegramApiClient
import com.example.telegram.TelegramConfig
import com.example.telegram.TelegramUploadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI State for the modern, premium Backup Dashboard Screen.
 */
data class BackupDashboardUiState(
    val isTelegramConnected: Boolean = false,
    val connectionStatusText: String = "Connecting...",
    val activeChannelsCount: Int = 0,
    val totalChannelsCount: Int = 4,
    val botUsername: String? = null,
    val isCheckingConnection: Boolean = false,
    val channelStatuses: List<ChannelStatus> = emptyList(),
    val rawDebugErrorLog: String? = null
)

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val mediaDao = db.mediaDao()
    private val cloudDao = db.cloudRecordDao()
    private val jobDao = db.backupJobDao()
    private val destDao = db.destinationDao()

    val uploadManager = TelegramUploadManager(application)
    val manifestManager = ManifestManager(application)
    val backupEngine = BackupEngine(application)

    private val _uiState = MutableStateFlow(BackupDashboardUiState())
    val uiState: StateFlow<BackupDashboardUiState> = _uiState.asStateFlow()

    // Observable flows
    val totalMediaCount = mediaDao.getMediaCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cloudTotalCount = cloudDao.getTotalCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cloudTotalBytes = cloudDao.getTotalBytesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val pendingJobsCount = jobDao.getPendingCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedJobsCount = jobDao.getFailedCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentJobs: StateFlow<List<BackupJobEntity>> = jobDao.getAllJobsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val destinations: StateFlow<List<BackupDestinationEntity>> = destDao.getAllDestinationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cloudRecords: StateFlow<List<CloudRecordEntity>> = cloudDao.getAllRecordsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isUploading = backupEngine.isUploading
    val currentUploadingName = backupEngine.currentUploadingName
    val currentUploadingProgress = backupEngine.currentUploadingProgress

    init {
        refreshConnectionStatus()
    }

    /**
     * Checks real live connectivity against Telegram's API without hardcoded statuses.
     */
    fun refreshConnectionStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCheckingConnection = true,
                connectionStatusText = "Checking Telegram connection..."
            )

            withContext(Dispatchers.IO) {
                val botRes = uploadManager.checkBotConnection()
                if (botRes.isFailure) {
                    val rawErr = TelegramApiClient.debugLogs.lastOrNull()
                        ?: botRes.exceptionOrNull()?.message
                        ?: "Telegram connection failed. Please check your internet."
                    _uiState.value = _uiState.value.copy(
                        isTelegramConnected = false,
                        connectionStatusText = "Connection failed. Please check your internet.",
                        activeChannelsCount = 0,
                        botUsername = null,
                        isCheckingConnection = false,
                        rawDebugErrorLog = rawErr
                    )
                    return@withContext
                }

                val botUsername = botRes.getOrNull()
                val statuses = uploadManager.checkAllChannelsStatus()
                val activeCount = statuses.count { it.isConnected }
                val isFullyOrPartiallyConnected = activeCount > 0

                // Sync status to Room DB
                statuses.forEach { st ->
                    destDao.insert(
                        BackupDestinationEntity(
                            channelId = st.channelId,
                            title = st.title,
                            botToken = TelegramConfig.BOT_TOKEN,
                            isEnabled = st.isConnected,
                            lastBackupAt = System.currentTimeMillis(),
                            lastError = st.errorMessage
                        )
                    )
                }

                val latestDebug = if (!isFullyOrPartiallyConnected) {
                    TelegramApiClient.debugLogs.lastOrNull()
                } else null

                _uiState.value = _uiState.value.copy(
                    isTelegramConnected = isFullyOrPartiallyConnected,
                    connectionStatusText = if (isFullyOrPartiallyConnected) "Connected" else "Connection failed. Please check your internet.",
                    activeChannelsCount = activeCount,
                    totalChannelsCount = statuses.size,
                    botUsername = botUsername,
                    channelStatuses = statuses,
                    isCheckingConnection = false,
                    rawDebugErrorLog = latestDebug
                )
            }
        }
    }

    fun startBackup() {
        viewModelScope.launch {
            backupEngine.startOrResumeQueue()
            BackupWorker.enqueueImmediateBackup(getApplication())
        }
    }

    fun pauseBackup() {
        backupEngine.pauseQueue()
    }

    fun retryFailed() {
        backupEngine.retryFailed()
    }

    /**
     * Automatically queries Telegram for the latest manifest, downloads and decrypts it.
     */
    fun restoreManifestAutomatically(masterPassword: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val res = manifestManager.fetchManifestFromTelegramAndDecrypt(masterPassword)
                withContext(Dispatchers.Main) {
                    if (res.isSuccess) {
                        val manifest = res.getOrThrow()
                        onResult(true, "Catalog reconstructed! Restored ${manifest.totalFiles} cloud items.")
                    } else {
                        val err = res.exceptionOrNull()?.message ?: "Decryption or manifest retrieval failed."
                        onResult(false, err)
                    }
                }
            }
        }
    }
}
