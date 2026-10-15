package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.database.entity.LocalMediaEntity
import com.example.ui.theme.StatusError
import java.util.Date

@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun MediaDetailScreen(
    media: LocalMediaEntity?,
    isBackedUp: Boolean = false,
    onBack: () -> Unit,
    onToggleFavorite: (LocalMediaEntity) -> Unit = {},
    onDeleteMedia: (LocalMediaEntity) -> Unit = {}
) {
    val context = LocalContext.current

    if (media == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Media not found", color = Color.White)
        }
        return
    }

    var showControls by remember { mutableStateOf(true) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Double-tap to zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("media_viewer_container")
    ) {
        // Main content: Video player or Zoomable Image
        if (media.isVideo) {
            VideoPlayer(
                uri = Uri.parse(media.uriString),
                onTap = { showControls = !showControls },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ZoomableImage(
                media = media,
                scale = scale,
                offset = offset,
                onScaleChange = { newScale -> scale = newScale },
                onOffsetChange = { newOffset -> offset = newOffset },
                onTap = { showControls = !showControls },
                onDoubleTap = { tapOffset, size ->
                    if (scale > 1.05f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                        offset = Offset(
                            x = (size.width / 2f - tapOffset.x) * 1.5f,
                            y = (size.height / 2f - tapOffset.y) * 1.5f
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top Transparent Overlay Bar (Google Photos style)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("detail_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = media.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1
                            )
                            val formattedDate = DateFormat.format("MMM dd, yyyy", Date(media.dateAdded))
                            val formattedSize = Formatter.formatFileSize(context, media.size)
                            Text(
                                text = "$formattedDate • $formattedSize",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Favorite toggle
                        IconButton(
                            onClick = { onToggleFavorite(media) },
                            modifier = Modifier.testTag("detail_favorite_button")
                        ) {
                            Icon(
                                if (media.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (media.isFavorite) Color(0xFFEF4444) else Color.White
                            )
                        }

                        // Share
                        IconButton(
                            onClick = { shareMedia(context, media) },
                            modifier = Modifier.testTag("detail_share_button")
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White
                            )
                        }

                        // Delete
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.testTag("detail_delete_button")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.White
                            )
                        }

                        // Info Details
                        IconButton(
                            onClick = { showDetailsSheet = true },
                            modifier = Modifier.testTag("detail_info_button")
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Details",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Delete from device?") },
                text = {
                    Text("Are you sure you want to permanently delete \"${media.displayName}\"? This file will be removed from your device storage.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            deleteMediaFromDevice(context, media)
                            onDeleteMedia(media)
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                        modifier = Modifier.testTag("confirm_delete_button")
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // File Details Modal Bottom Sheet
        if (showDetailsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDetailsSheet = false },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text("File Details", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))

                    DetailItemRow(label = "Filename", value = media.displayName)
                    DetailItemRow(label = "Folder", value = media.folderName)
                    DetailItemRow(label = "Size", value = Formatter.formatFileSize(context, media.size))
                    DetailItemRow(label = "MIME Type", value = media.mimeType)
                    DetailItemRow(
                        label = "Date Added",
                        value = DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(media.dateAdded)).toString()
                    )
                    DetailItemRow(label = "Path", value = media.path.ifBlank { media.uriString })
                    DetailItemRow(label = "Cloud Backup", value = if (isBackedUp) "Backed up to Telegram" else "Stored on Device")
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * AndroidX Media3 ExoPlayer Video Component with lifecycle-aware pause/release
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    uri: Uri,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember(context, uri) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> { /* User can resume playback manually or keep state */ }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Zoomable & Pannable Image Component with Coil memory caching and downsampling
 */
@Composable
private fun ZoomableImage(
    media: LocalMediaEntity,
    scale: Float,
    offset: Offset,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit,
    onTap: () -> Unit,
    onDoubleTap: (Offset, androidx.compose.ui.unit.IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        onDoubleTap(tapOffset, size)
                    },
                    onTap = {
                        onTap()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    onScaleChange(newScale)
                    if (newScale > 1f) {
                        val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                        val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                        onOffsetChange(
                            Offset(
                                x = (offset.x + pan.x * newScale).coerceIn(-maxOffsetX, maxOffsetX),
                                y = (offset.y + pan.y * newScale).coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        )
                    } else {
                        onOffsetChange(Offset.Zero)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(media.uriString)
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = media.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(36.dp)
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.BrokenImage,
                            contentDescription = "Failed to load",
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Failed to load image",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        )
    }
}

/**
 * Helper to share image or video using Android ACTION_SEND intent
 */
private fun shareMedia(context: Context, media: LocalMediaEntity) {
    try {
        val uri = Uri.parse(media.uriString)
        val mimeType = if (media.mimeType.isNotBlank()) {
            media.mimeType
        } else if (media.isVideo) {
            "video/*"
        } else {
            "image/*"
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, media.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${media.displayName}"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
private fun DetailItemRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Deletes the media file from device storage.
 * Uses MediaStore content resolver for Android 10+ (API 29+), with File API fallback.
 */
fun deleteMediaFromDevice(context: Context, media: LocalMediaEntity): Boolean {
    var deleted = false
    try {
        val uri = Uri.parse(media.uriString)
        val rows = context.contentResolver.delete(uri, null, null)
        deleted = rows > 0
    } catch (e: Exception) {
        // Fallback for file path
    }

    if (!deleted && media.path.isNotBlank()) {
        try {
            val file = java.io.File(media.path)
            if (file.exists()) {
                deleted = file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return deleted
}

/**
 * Composable alias for MediaViewerScreen requested by developer.
 */
@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun MediaViewerScreen(
    media: LocalMediaEntity?,
    isBackedUp: Boolean = false,
    onBack: () -> Unit,
    onToggleFavorite: (LocalMediaEntity) -> Unit = {},
    onDeleteMedia: (LocalMediaEntity) -> Unit = {}
) {
    MediaDetailScreen(
        media = media,
        isBackedUp = isBackedUp,
        onBack = onBack,
        onToggleFavorite = onToggleFavorite,
        onDeleteMedia = onDeleteMedia
    )
}

