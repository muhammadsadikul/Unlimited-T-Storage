package com.example.ui.screens

import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.data.database.entity.LocalMediaEntity
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TelegramBluePrimary
import java.io.File
import java.util.Date

/**
 * GalleryScreen: Displays photos and videos in an adaptive, responsive grid.
 * Configured with Coil image loading, VideoFrameDecoder for video thumbnails,
 * placeholders, error fallbacks, 1:1 aspect ratio, and video indicator badges.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    mediaList: List<LocalMediaEntity>,
    backedUpUris: Set<String>,
    onMediaClick: (LocalMediaEntity) -> Unit,
    onBackupSelected: (List<LocalMediaEntity>) -> Unit,
    onToggleFavorite: (LocalMediaEntity) -> Unit,
    onScanRequest: () -> Unit,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ImageLoader configured with VideoFrameDecoder for automatic video thumbnail extraction
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }

    var searchQuery by remember { mutableStateOf("") }
    val selectedItems = remember { mutableStateListOf<LocalMediaEntity>() }
    val isSelectionMode = selectedItems.isNotEmpty()

    val filteredList = remember(mediaList, searchQuery) {
        if (searchQuery.isBlank()) {
            mediaList
        } else {
            mediaList.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                        it.folderName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Group items by date string (e.g. "MMMM d, yyyy")
    val groupedItems = remember(filteredList) {
        filteredList.groupBy {
            DateFormat.format("MMMM d, yyyy", Date(it.dateAdded)).toString()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search / Filter & Selection Toolbar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedItems.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    onBackupSelected(selectedItems.toList())
                                    selectedItems.clear()
                                },
                                modifier = Modifier.testTag("backup_selected_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = TelegramBluePrimary)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = "Backup", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Backup")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { selectedItems.clear() },
                                colors = ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("gallery_search_field"),
                        placeholder = { Text("Search photos & videos...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Text("✕")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }

            // Scanning progress indicator
            if (isScanning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TelegramBluePrimary.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = TelegramBluePrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Scanning device for new photos and videos...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TelegramBluePrimary
                    )
                }
            }

            if (filteredList.isEmpty() && !isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No media matches '$searchQuery'" else "No photos found on device",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Grant storage access or tap scan to discover your photos & videos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onScanRequest,
                            modifier = Modifier.testTag("scan_storage_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBluePrimary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Device Media")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 105.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groupedItems.forEach { (dateHeader, itemsInDate) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = dateHeader,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp)
                            )
                        }

                        items(itemsInDate, key = { it.id }) { item ->
                            val isSelected = selectedItems.contains(item)
                            val isBackedUp = backedUpUris.contains(item.uriString)

                            GalleryThumbnailItem(
                                item = item,
                                imageLoader = imageLoader,
                                isSelected = isSelected,
                                isBackedUp = isBackedUp,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedItems.remove(item) else selectedItems.add(item)
                                    } else {
                                        onMediaClick(item)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        selectedItems.add(item)
                                    }
                                },
                                onToggleFavorite = { onToggleFavorite(item) }
                            )
                        }
                    }
                }
            }
        }

        // Quick scan FAB if not in selection mode
        if (!isSelectionMode && filteredList.isNotEmpty()) {
            FloatingActionButton(
                onClick = onScanRequest,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag("fab_scan"),
                containerColor = TelegramBluePrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Scan Media")
            }
        }
    }
}

/**
 * Gallery item with 1:1 aspect ratio, Coil asynchronous image/video thumbnail loading,
 * downsampling, caching, loading placeholders, error fallbacks, and a video badge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryThumbnailItem(
    item: LocalMediaEntity,
    imageLoader: ImageLoader,
    isSelected: Boolean,
    isBackedUp: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Build resilient media model (content URI or File fallback)
    val mediaModel: Any = remember(item.uriString, item.path) {
        if (item.uriString.isNotBlank()) {
            try {
                Uri.parse(item.uriString)
            } catch (_: Exception) {
                if (item.path.isNotBlank()) File(item.path) else item.uriString
            }
        } else if (item.path.isNotBlank()) {
            File(item.path)
        } else {
            item.uriString
        }
    }

    // Configure ImageRequest with downsampling, memory/disk cache, and video frame extraction
    val imageRequest = remember(mediaModel, item.isVideo) {
        ImageRequest.Builder(context)
            .data(mediaModel)
            .crossfade(true)
            .size(280, 280) // Downsampling to prevent OOM errors and UI lag
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .apply {
                if (item.isVideo) {
                    // Extract first frame of the video
                    videoFrameMillis(0L)
                }
            }
            .build()
    }

    // Enforce strict 1:1 aspect ratio so the grid never jumps or shifts
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("media_item_${item.id}"),
        shape = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Asynchronous Image / Video thumbnail rendering via Coil
            SubcomposeAsyncImage(
                model = imageRequest,
                imageLoader = imageLoader,
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.isVideo) {
                            // Play icon placeholder while video thumbnail extracts
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = "Loading video preview",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(30.dp)
                            )
                        } else {
                            // Subtle progress indicator for photos
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.BrokenImage,
                            contentDescription = "Failed to load thumbnail",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )

            // Video badge overlay on bottom-left corner
            if (item.isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Video",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Backup Status Badge on top-right corner
            if (isBackedUp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(StatusSuccess, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CloudDone,
                        contentDescription = "Backed up",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Favorite Icon on bottom-right corner
            if (item.isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Selection checkmark overlay
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TelegramBluePrimary.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(22.dp)
                            .background(TelegramBluePrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
