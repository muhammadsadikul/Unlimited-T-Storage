package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class LocalMediaEntity(
    @PrimaryKey val id: Long,
    val uriString: String,
    val path: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val folderName: String,
    val isVideo: Boolean = false,
    val isFavorite: Boolean = false,
    val sha256: String? = null
)

@Entity(
    tableName = "backup_jobs",
    indices = [
        Index(value = ["uriString", "channelId"]),
        Index(value = ["status"])
    ]
)
data class BackupJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uriString: String,
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val folderName: String,
    val channelId: String,
    val status: String,
    val progressPercent: Int = 0,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isVaultItem: Boolean = false,
    val vaultIvBase64: String? = null,
    val vaultSaltBase64: String? = null
)

@Entity(
    tableName = "cloud_records",
    indices = [
        Index(value = ["sha256", "channelId"], unique = true),
        Index(value = ["fileName"])
    ]
)
data class CloudRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val originalPath: String,
    val mimeType: String,
    val size: Long,
    val sha256: String,
    val channelId: String,
    val telegramMessageId: Long,
    val telegramFileId: String,
    val uploadTimestamp: Long = System.currentTimeMillis(),
    val isVaultEncrypted: Boolean = false,
    val ivBase64: String? = null,
    val saltBase64: String? = null
)

@Entity(tableName = "backup_destinations")
data class BackupDestinationEntity(
    @PrimaryKey val channelId: String,
    val title: String,
    val botToken: String,
    val isEnabled: Boolean = true,
    val lastBackupAt: Long? = null,
    val lastError: String? = null
)

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalName: String,
    val encryptedFileName: String,
    val mimeType: String,
    val size: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val ivBase64: String,
    val saltBase64: String,
    val sha256: String,
    val isBackedUp: Boolean = false
)

@Entity(tableName = "folder_rules")
data class FolderRuleEntity(
    @PrimaryKey val folderPath: String,
    val folderName: String,
    val isEnabled: Boolean = true,
    val targetChannelId: String? = null
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
