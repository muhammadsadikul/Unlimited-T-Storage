package com.example.backup

import android.content.Context
import android.net.Uri
import com.example.data.database.AppDatabase
import com.example.data.database.entity.BackupJobEntity
import com.example.data.database.entity.CloudRecordEntity
import com.example.data.database.entity.LocalMediaEntity
import com.example.security.CryptoManager
import com.example.telegram.TelegramApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class BackupEngine(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val jobDao = db.backupJobDao()
    private val cloudDao = db.cloudRecordDao()
    private val destDao = db.destinationDao()
    private val settingDao = db.settingDao()
    private val telegramClient = TelegramApiClient()
    private val manifestManager = ManifestManager(context)

    private val engineScope = CoroutineScope(Dispatchers.IO + Job())
    private var activeJob: Job? = null

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _currentJobFileName = MutableStateFlow<String?>(null)
    val currentJobFileName: StateFlow<String?> = _currentJobFileName.asStateFlow()

    private val _currentJobProgress = MutableStateFlow(0)
    val currentJobProgress: StateFlow<Int> = _currentJobProgress.asStateFlow()

    suspend fun enqueueMedia(media: LocalMediaEntity, channelId: String): Boolean = withContext(Dispatchers.IO) {
        // Resolve temp file if content uri
        val tempFile = resolveToFile(media.uriString, media.path, media.displayName) ?: return@withContext false
        val sha256 = media.sha256 ?: CryptoManager.calculateSha256(tempFile)

        // Deduplication check: Has this file already been backed up to this channel?
        val existingCloud = cloudDao.getBySha256(sha256)
        if (existingCloud != null && existingCloud.channelId == channelId) {
            // Already backed up!
            return@withContext true
        }

        val existingJob = jobDao.getJob(media.uriString, channelId)
        if (existingJob != null && existingJob.status == "BACKED_UP") {
            return@withContext true
        }

        val job = BackupJobEntity(
            id = existingJob?.id ?: 0,
            uriString = media.uriString,
            filePath = media.path,
            fileName = media.displayName,
            mimeType = media.mimeType,
            size = media.size,
            folderName = media.folderName,
            channelId = channelId,
            status = "QUEUED",
            progressPercent = 0,
            retryCount = 0
        )
        jobDao.insertOrUpdate(job)
        true
    }

    suspend fun enqueueFile(file: File, mimeType: String, channelId: String): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists() || file.isDirectory) return@withContext false
        val sha256 = CryptoManager.calculateSha256(file)

        val existingCloud = cloudDao.getBySha256(sha256)
        if (existingCloud != null && existingCloud.channelId == channelId) {
            return@withContext true
        }

        val uriString = Uri.fromFile(file).toString()
        val existingJob = jobDao.getJob(uriString, channelId)
        if (existingJob != null && existingJob.status == "BACKED_UP") {
            return@withContext true
        }

        val job = BackupJobEntity(
            id = existingJob?.id ?: 0,
            uriString = uriString,
            filePath = file.absolutePath,
            fileName = file.name,
            mimeType = mimeType,
            size = file.length(),
            folderName = file.parentFile?.name ?: "Files",
            channelId = channelId,
            status = "QUEUED",
            progressPercent = 0,
            retryCount = 0
        )
        jobDao.insertOrUpdate(job)
        true
    }

    suspend fun enqueueVaultItem(
        encryptedFile: File,
        originalName: String,
        mimeType: String,
        ivBase64: String,
        saltBase64: String,
        channelId: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!encryptedFile.exists()) return@withContext false
        val sha256 = CryptoManager.calculateSha256(encryptedFile)

        val job = BackupJobEntity(
            uriString = Uri.fromFile(encryptedFile).toString(),
            filePath = encryptedFile.absolutePath,
            fileName = "vault_${encryptedFile.name}.enc",
            mimeType = "application/octet-stream",
            size = encryptedFile.length(),
            folderName = "HiddenVault",
            channelId = channelId,
            status = "QUEUED",
            isVaultItem = true,
            vaultIvBase64 = ivBase64,
            vaultSaltBase64 = saltBase64
        )
        jobDao.insertOrUpdate(job)
        true
    }

    fun startOrResumeQueue() {
        if (activeJob?.isActive == true) return

        activeJob = engineScope.launch {
            _isUploading.value = true
            try {
                processQueue()
            } finally {
                _isUploading.value = false
                _currentJobFileName.value = null
                _currentJobProgress.value = 0
            }
        }
    }

    fun pauseQueue() {
        engineScope.launch {
            activeJob?.cancel()
            jobDao.pauseAll()
            _isUploading.value = false
            _currentJobFileName.value = null
        }
    }

    fun retryFailed() {
        engineScope.launch {
            jobDao.retryAllFailed()
            startOrResumeQueue()
        }
    }

    private suspend fun processQueue() = withContext(Dispatchers.IO) {
        val enabledDestinations = destDao.getEnabledDestinations()
        if (enabledDestinations.isEmpty()) return@withContext

        // Map destinations by id
        val destMap = enabledDestinations.associateBy { it.channelId }

        var hasMore = true
        var processedCount = 0

        while (hasMore) {
            val pendingJobs = jobDao.getPendingJobs()
            if (pendingJobs.isEmpty()) {
                hasMore = false
                break
            }

            for (job in pendingJobs) {
                val dest = destMap[job.channelId] ?: enabledDestinations.firstOrNull()
                if (dest == null) {
                    jobDao.markFailed(job.id, "FAILED", "Destination not configured or disabled")
                    continue
                }

                _currentJobFileName.value = job.fileName
                _currentJobProgress.value = 0
                jobDao.updateStatus(job.id, "UPLOADING")

                val targetFile = resolveToFile(job.uriString, job.filePath, job.fileName)
                if (targetFile == null || !targetFile.exists()) {
                    jobDao.markFailed(job.id, "FAILED", "Source file not found")
                    continue
                }

                // Upload with progress callback
                val uploadResult = telegramClient.uploadFile(
                    botToken = dest.botToken,
                    chatId = dest.channelId,
                    file = targetFile,
                    displayName = job.fileName,
                    mimeType = job.mimeType,
                    caption = "TG Cloud Backup: ${job.fileName} (${job.size / 1024} KB)",
                    onProgress = { percent, _, _ ->
                        _currentJobProgress.value = percent
                        engineScope.launch { jobDao.updateProgress(job.id, percent) }
                    }
                )

                if (uploadResult.retryAfterSeconds > 0) {
                    // Rate limit backoff
                    delay(uploadResult.retryAfterSeconds * 1000L)
                }

                if (uploadResult.success) {
                    val sha256 = CryptoManager.calculateSha256(targetFile)
                    // Persist Cloud Record
                    cloudDao.insert(
                        CloudRecordEntity(
                            fileName = job.fileName,
                            originalPath = job.filePath,
                            mimeType = job.mimeType,
                            size = job.size,
                            sha256 = sha256,
                            channelId = dest.channelId,
                            telegramMessageId = uploadResult.messageId,
                            telegramFileId = uploadResult.fileId,
                            uploadTimestamp = System.currentTimeMillis(),
                            isVaultEncrypted = job.isVaultItem,
                            ivBase64 = job.vaultIvBase64,
                            saltBase64 = job.vaultSaltBase64
                        )
                    )

                    jobDao.updateStatus(job.id, "BACKED_UP")
                    destDao.updateLastBackup(dest.channelId, System.currentTimeMillis())
                    processedCount++
                } else {
                    val errorMsg = uploadResult.error ?: "Upload failed"
                    jobDao.markFailed(job.id, "FAILED", errorMsg)
                    destDao.updateError(dest.channelId, errorMsg)
                }

                delay(500) // gentle delay between files to avoid hitting Telegram limits
            }

            if (processedCount > 0) {
                // Publish updated manifest to channel
                for (dest in enabledDestinations) {
                    manifestManager.publishManifest(dest.botToken, dest.channelId)
                }
            }
        }
    }

    private fun resolveToFile(uriString: String, path: String, fallbackName: String): File? {
        if (path.isNotBlank()) {
            val f = File(path)
            if (f.exists()) return f
        }

        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                uri.path?.let { File(it).takeIf { f -> f.exists() } }
            } else {
                // Read from content resolver into cache
                val tempFile = File(context.cacheDir, "upload_$fallbackName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.takeIf { it.exists() && it.length() > 0 }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
