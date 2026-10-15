package com.example.telegram

import android.os.Environment
import com.example.data.database.entity.CloudRecordEntity
import com.example.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * RestoreManager handles downloading and restoring backed-up files from Telegram channels
 * using the configured hardcoded Telegram Bot Token and TelegramApiClient.
 *
 * All API operations use TelegramConfig.BOT_TOKEN (sourced from Constants.TELEGRAM_BOT_TOKEN).
 */
class RestoreManager(
    private val telegramClient: TelegramApiClient = TelegramApiClient()
) {
    /**
     * Sourced from Constants.TELEGRAM_BOT_TOKEN / BuildConfig.
     */
    val botToken: String = TelegramConfig.BOT_TOKEN

    /**
     * Downloads and restores a single cloud record file from Telegram cloud storage.
     * Verifies SHA-256 integrity upon download completion.
     */
    suspend fun downloadRecord(
        record: CloudRecordEntity,
        destinationDir: File,
        onProgress: (percent: Int, bytesRead: Long, totalBytes: Long) -> Unit = { _, _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        val destFile = File(destinationDir, record.fileName)
        val result = telegramClient.downloadFile(
            botToken = botToken,
            fileId = record.telegramFileId,
            destinationFile = destFile,
            onProgress = onProgress
        )
        if (result.isSuccess) {
            val downloadedSha = CryptoManager.calculateSha256(destFile)
            if (record.sha256.isNotBlank() && !downloadedSha.equals(record.sha256, ignoreCase = true)) {
                return@withContext Result.failure(
                    IllegalStateException("SHA-256 integrity verification failed for ${record.fileName}")
                )
            }
        }
        result
    }

    /**
     * Restores a list of cloud records to the designated storage folder.
     */
    suspend fun restoreRecords(
        records: List<CloudRecordEntity>,
        destinationDir: File = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "TelegramRestore"
        ).also { it.mkdirs() },
        onRecordStarted: (current: Int, total: Int, record: CloudRecordEntity) -> Unit = { _, _, _ -> },
        onProgress: (percent: Int, bytesRead: Long, totalBytes: Long) -> Unit = { _, _, _ -> }
    ): List<File> = withContext(Dispatchers.IO) {
        val restoredFiles = mutableListOf<File>()
        records.forEachIndexed { index, record ->
            onRecordStarted(index + 1, records.size, record)
            val res = downloadRecord(record, destinationDir, onProgress)
            if (res.isSuccess) {
                restoredFiles.add(res.getOrThrow())
            }
        }
        restoredFiles
    }
}
