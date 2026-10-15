package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.database.entity.LocalMediaEntity

/**
 * PhotosScreen: Primary gallery screen entry point that displays local media items.
 * Delegates directly to GalleryScreen which implements Coil asynchronous image loading,
 * VideoFrameDecoder for video thumbnails, memory caching, downsampling, and video badges.
 */
@Composable
fun PhotosScreen(
    mediaList: List<LocalMediaEntity>,
    backedUpUris: Set<String>,
    onMediaClick: (LocalMediaEntity) -> Unit,
    onBackupSelected: (List<LocalMediaEntity>) -> Unit,
    onToggleFavorite: (LocalMediaEntity) -> Unit,
    onScanRequest: () -> Unit,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    GalleryScreen(
        mediaList = mediaList,
        backedUpUris = backedUpUris,
        onMediaClick = onMediaClick,
        onBackupSelected = onBackupSelected,
        onToggleFavorite = onToggleFavorite,
        onScanRequest = onScanRequest,
        isScanning = isScanning,
        modifier = modifier
    )
}
