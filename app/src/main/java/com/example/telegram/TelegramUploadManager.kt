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
 * 1. Round-Robin distribution across all 4 channels formatted with "-100".
 * 2. Automatic channel failover: If upload to one channel fails, automatically tries next channels in rotation.
 * 3. Real live API status verification: No fake or hardcoded "Connected" status.
 * 4. Sanitized logging: Never leaks sensitive bot tokens in Logcat.
 */
class TelegramUploadManager(
    private val context: Context? = null,
    private val telegramClient: TelegramApiClient = TelegramApiClient()
) {
    companion object {
        private const val TAG = "TelegramUploadManager"
        private val roundRobinCounter = AtomicInteger(0)
    }

    val channelIds: List<String> = Constants.TELEGRAM_CHANNEL_IDS.map { Constants.formatChannelId(it) }
    val botToken: String = TelegramConfig.BOT_TOKEN

    /**
     * Checks if the bot token can authenticate with Telegram's getMe endpoint.
     */
    suspend fun checkBotConnection(): Result<String> = withContext(Dispatchers.IO) {
        val res = telegramClient.getMe(botToken)
        if (res.isSuccess) {
            val bot = res.getOrThrow()
            Result.success(bot.username ?: bot.firstName)
        } else {
            val err = res.exceptionOrNull()?.message ?: "Failed to authenticate bot token with Telegram"
            Result.failure(Exception(err))
        }
    }

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

        val startingChannel = preferredChannelId?.let { Constants.formatChannelId(it) }?.takeIf { channelIds.contains(it) }
            ?: getNextRoundRobinChannel()
        val startingIndex = channelIds.indexOf(startingChannel).coerceAtLeast(0)

        val candidateChannels = List(channelIds.size) { offset ->
            channelIds[(startingIndex + offset) % channelIds.size]
        }

        val attemptedChannels = mutableListOf<String>()
        var lastError: String? = null

        for ((attemptIndex, candidateChannelId) in candidateChannels.withIndex()) {
            val formattedChannelId = Constants.formatChannelId(candidateChannelId)
            attemptedChannels.add(formattedChannelId)

            val result = telegramClient.uploadFile(
                botToken = botToken,
                chatId = formattedChannelId,
                file = file,
                displayName = displayName,
                mimeType = mimeType,
                caption = caption,
                onProgress = onProgress
            )

            if (result.success) {
                return@withContext MultiChannelUploadResult(
                    success = true,
                    targetChannelId = formattedChannelId,
                    messageId = result.messageId,
                    fileId = result.fileId,
                    attemptedChannelIds = attemptedChannels
                )
            } else {
                lastError = result.error ?: "Upload failed on channel"
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
     * Checks the real live status of all 4 channels by contacting Telegram's getChat API.
     */
    suspend fun checkAllChannelsStatus(): List<ChannelStatus> = withContext(Dispatchers.IO) {
        val db = context?.let { AppDatabase.getDatabase(it) }
        val cloudDao = db?.cloudRecordDao()

        channelIds.mapIndexed { index, rawChannelId ->
            val formattedId = Constants.formatChannelId(rawChannelId)
            val chatRes = telegramClient.getChat(botToken, formattedId)
            val fileCount = cloudDao?.getCountForChannel(formattedId) ?: 0
            val totalBytes = cloudDao?.getBytesForChannel(formattedId) ?: 0L

            if (chatRes.isSuccess) {
                val chat = chatRes.getOrThrow()
                ChannelStatus(
                    channelId = formattedId,
                    channelIndex = index + 1,
                    title = chat.title.ifBlank { "Channel ${index + 1}" },
                    isConnected = true,
                    fileCount = fileCount,
                    totalBytes = totalBytes
                )
            } else {
                val errorMsg = chatRes.exceptionOrNull()?.message ?: "Unable to connect"
                ChannelStatus(
                    channelId = formattedId,
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
