package com.example.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.database.entity.CloudRecordEntity
import com.example.security.CryptoManager
import com.example.telegram.TelegramApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class BackupWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val db = AppDatabase.getDatabase(appContext)
    private val jobDao = db.backupJobDao()
    private val cloudDao = db.cloudRecordDao()
    private val destDao = db.destinationDao()
    private val telegramClient = TelegramApiClient()
    private val manifestManager = ManifestManager(appContext)

    companion object {
        const val WORK_NAME_PERIODIC = "unlimited_t_storage_periodic_backup"
        const val WORK_NAME_IMMEDIATE = "unlimited_t_storage_immediate_backup"
        const val CHANNEL_ID = "backup_service_channel"
        const val NOTIFICATION_ID = 1001

        fun enqueuePeriodicBackup(context: Context, wifiOnly: Boolean = true, requiresCharging: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresCharging(requiresCharging)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(2, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun enqueueImmediateBackup(context: Context, wifiOnly: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val enabledDestinations = destDao.getEnabledDestinations()
        if (enabledDestinations.isEmpty()) {
            return@withContext Result.success()
        }

        val destMap = enabledDestinations.associateBy { it.channelId }
        val pendingJobs = jobDao.getPendingJobs()
        if (pendingJobs.isEmpty()) {
            return@withContext Result.success()
        }

        createNotificationChannel()

        var processedCount = 0

        for (job in pendingJobs) {
            if (isStopped) return@withContext Result.retry()

            val dest = destMap[job.channelId] ?: enabledDestinations.firstOrNull()
            val targetBotToken = dest?.getDecryptedToken() ?: com.example.telegram.TelegramConfig.BOT_TOKEN
            val targetChatId = dest?.channelId ?: com.example.telegram.TelegramConfig.CHANNEL_ID

            jobDao.updateStatus(job.id, "UPLOADING")

            val targetFile = resolveToFile(job.uriString, job.filePath, job.fileName)
            if (targetFile == null || !targetFile.exists()) {
                jobDao.markFailed(job.id, "FAILED", "Source file not found")
                continue
            }

            val uploadResult = telegramClient.uploadFile(
                botToken = targetBotToken,
                chatId = targetChatId,
                file = targetFile,
                displayName = job.fileName,
                mimeType = job.mimeType,
                caption = "Unlimited T Storage: ${job.fileName}",
                onProgress = { percent, _, _ ->
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        jobDao.updateProgress(job.id, percent)
                    }
                }
            )

            if (uploadResult.retryAfterSeconds > 0) {
                delay(uploadResult.retryAfterSeconds * 1000L)
            }

            if (uploadResult.success) {
                val sha256 = CryptoManager.calculateSha256(targetFile)
                cloudDao.insert(
                    CloudRecordEntity(
                        fileName = job.fileName,
                        originalPath = job.filePath,
                        mimeType = job.mimeType,
                        size = job.size,
                        sha256 = sha256,
                        channelId = targetChatId,
                        telegramMessageId = uploadResult.messageId,
                        telegramFileId = uploadResult.fileId,
                        uploadTimestamp = System.currentTimeMillis(),
                        isVaultEncrypted = job.isVaultItem,
                        ivBase64 = job.vaultIvBase64,
                        saltBase64 = job.vaultSaltBase64
                    )
                )

                jobDao.updateStatus(job.id, "BACKED_UP")
                dest?.channelId?.let { destDao.updateLastBackup(it, System.currentTimeMillis()) }
                processedCount++
            } else {
                jobDao.markFailed(job.id, "FAILED", uploadResult.error ?: "Upload failed")
            }

            delay(500)
        }

        if (processedCount > 0) {
            for (dest in enabledDestinations) {
                manifestManager.publishManifest(dest.getDecryptedToken(), dest.channelId)
            }
        }

        Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Unlimited T Storage Backup",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background cloud backup progress"
            }
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
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
                val tempFile = File(appContext.cacheDir, "work_upload_$fallbackName")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.takeIf { it.exists() && it.length() > 0 }
            }
        } catch (e: Exception) {
            null
        }
    }
}
