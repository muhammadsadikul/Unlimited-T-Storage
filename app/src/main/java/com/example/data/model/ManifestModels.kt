package com.example.data.model

data class CloudManifest(
    val version: Int = 1,
    val appVersion: String = "1.0",
    val generatedAt: Long = System.currentTimeMillis(),
    val channelId: String = "",
    val totalFiles: Int = 0,
    val totalBytes: Long = 0L,
    val entries: List<ManifestEntry> = emptyList()
)

data class ManifestEntry(
    val id: String,
    val fileName: String,
    val relativePath: String,
    val mimeType: String,
    val size: Long,
    val sha256: String,
    val channelId: String = "",
    val telegramMessageId: Long,
    val telegramFileId: String,
    val isVaultEncrypted: Boolean = false,
    val ivBase64: String? = null,
    val saltBase64: String? = null,
    val uploadedAt: Long = System.currentTimeMillis()
)
