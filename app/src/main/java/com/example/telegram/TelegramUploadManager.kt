package com.example.telegram

import android.content.Context
import android.util.Log
import com.example.Constants
import com.example.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Result data class for multi-channel distributed uploads.
 */
data class MultiChannelUploadResult(
    val success: Boolean,
    val targetChannelId: String? = null,
    val messageId: Long = 0L,
    val fileId: String = "",
    val error: String? = null,
    val attemptedChannelIds: List<String> = emptyList()
)

/**
 * Status representation for each of the preconfigured channels.
 */
data class ChannelStatus(
    val channelId: String,
    val channelIndex: Int,
    val title: String,
    val isConnected: Boolean,
    val errorMessage: String? = null,
    val fileCount: Int = 0,
    val totalBytes: Long = 0L
)

/**
 * Manager handling distributed uploads across multiple Telegram channels.
 *
 * Implements:
 * 1. Round-Robin & Least Recently Used distribution to spread uploads evenly across all 4 channels,
 *    achieving virtually unlimited storage capacity.
 * 2. Automatic channel failover: If upload to one channel fails (e.g. rate limit, Telegram restriction,
 *    temporary server error), it automatically rotates and attempts the remaining channels in rotation.
 * 3. Sanitized error handling: Never logs sensitive bot tokens or channel IDs in Logcat.
 */
class TelegramUploadManager(
    private val context: Context? = null,
    private val telegramClient: TelegramApiClient = TelegramApiClient()
) {
    companion object {
        private const val TAG = "TelegramUploadManager"
        private val roundRobinCounter = AtomicInteger(0)
    }

    val channelIds: List<String> = Constants.TELEGRAM_CHANNEL_IDS
    val botToken: String = TelegramConfig.BOT_TOKEN

    /**
     * Gets the next channel ID according to the Round-Robin strategy.
     */
    fun getNextRoundRobinChannel(): String {
        val index = Math.floorMod(roundRobinCounter.getAndIncrement(), channelIds.size)
        return channelIds[index]
    }

    /**
     * Uploads a file with automatic multi-channel fallback:
     * - Starts with the round-robin selected channel (or preferredChannelId).
     * - If that upload fails, automatically tries the next channels in order until one succeeds or all fail.
     */
    suspend fun uploadFileWithFallback(
        file: File,
        displayName: String,
        mimeType: String,
        caption: String? = null,
        preferredChannelId: String? = null,
        onProgress: (percent: Int, bytesWritten: Long, totalBytes: Long) -> Unit = { _, _, _ -> }
    ): MultiChannelUploadResult = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext MultiChannelUploadResult(
                success = false,
                error = "File does not exist: $displayName"
            )
        }

        // Generate prioritized candidate channel list:
        val startingChannel = preferredChannelId?.takeIf { channelIds.contains(it) }
            ?: getNextRoundRobinChannel()
        val startingIndex = channelIds.indexOf(startingChannel).coerceAtLeast(0)

        val candidateChannels = List(channelIds.size) { offset ->
            channelIds[(startingIndex + offset) % channelIds.size]
        }

        val attemptedChannels = mutableListOf<String>()
        var lastError: String? = null

        for ((attemptIndex, candidateChannelId) in candidateChannels.withIndex()) {
            attemptedChannels.add(candidateChannelId)

            val result = telegramClient.uploadFile(
                botToken = botToken,
                chatId = candidateChannelId,
                file = file,
                displayName = displayName,
                mimeType = mimeType,
                caption = caption,
                onProgress = onProgress
            )

            if (result.success) {
                return@withContext MultiChannelUploadResult(
                    success = true,
                    targetChannelId = candidateChannelId,
                    messageId = result.messageId,
                    fileId = result.fileId,
                    attemptedChannelIds = attemptedChannels
                )
            } else {
                lastError = result.error ?: "Upload failed on channel"
                // Log sanitarily without leaking channel id or bot token
                Log.w(
                    TAG,
                    "Channel attempt ${attemptIndex + 1}/${candidateChannels.size} failed. Trying next channel in pool..."
                )
            }
        }

        // All channels failed
        MultiChannelUploadResult(
            success = false,
            error = "Upload failed across all ${candidateChannels.size} channels. Last error: $lastError",
            attemptedChannelIds = attemptedChannels
        )
    }

    /**
     * Checks the live status of all 4 channels.
     */
    suspend fun checkAllChannelsStatus(): List<ChannelStatus> = withContext(Dispatchers.IO) {
        val db = context?.let { AppDatabase.getDatabase(it) }
        val cloudDao = db?.cloudRecordDao()

        channelIds.mapIndexed { index, channelId ->
            val chatRes = telegramClient.getChat(botToken, channelId)
            val fileCount = cloudDao?.getCountForChannel(channelId) ?: 0
            val totalBytes = cloudDao?.getBytesForChannel(channelId) ?: 0L

            if (chatRes.isSuccess) {
                val chat = chatRes.getOrThrow()
                ChannelStatus(
                    channelId = channelId,
                    channelIndex = index + 1,
                    title = chat.title.ifBlank { "Channel ${index + 1}" },
                    isConnected = true,
                    fileCount = fileCount,
                    totalBytes = totalBytes
                )
            } else {
                val errorMsg = chatRes.exceptionOrNull()?.message ?: "Unable to connect"
                ChannelStatus(
                    channelId = channelId,
                    channelIndex = index + 1,
                    title = "Channel ${index + 1}",
                    isConnected = false,
                    errorMessage = errorMsg,
                    fileCount = fileCount,
                    totalBytes = totalBytes
                )
            }
        }
    }
}
