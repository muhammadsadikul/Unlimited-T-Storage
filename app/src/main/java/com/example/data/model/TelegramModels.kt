package com.example.data.model

data class TelegramChannel(
    val channelId: String,
    val title: String,
    val botToken: String,
    val isEnabled: Boolean = true,
    val lastBackupAt: Long? = null,
    val lastError: String? = null,
    val messageCount: Int = 0
)

data class TelegramChatInfo(
    val id: Long,
    val title: String,
    val type: String,
    val username: String? = null
)

data class TelegramBotInfo(
    val id: Long,
    val isBot: Boolean,
    val firstName: String,
    val username: String? = null
)

data class TelegramUploadResult(
    val success: Boolean,
    val messageId: Long = 0L,
    val fileId: String = "",
    val error: String? = null,
    val retryAfterSeconds: Int = 0
)
