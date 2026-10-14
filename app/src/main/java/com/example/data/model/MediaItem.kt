package com.example.data.model

data class MediaItem(
    val id: Long,
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val folderName: String,
    val isVideo: Boolean = false,
    val duration: Long = 0L,
    val isFavorite: Boolean = false,
    val backupStatus: BackupStatus = BackupStatus.NOT_BACKED_UP,
    val sha256: String? = null
)
