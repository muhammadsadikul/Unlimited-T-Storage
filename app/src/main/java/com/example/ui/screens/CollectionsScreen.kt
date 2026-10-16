package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.database.entity.FolderRuleEntity
import com.example.data.database.entity.LocalMediaEntity
import com.example.ui.theme.TelegramBluePrimary
import com.example.ui.theme.VaultPurple

@Composable
fun CollectionsScreen(
    mediaList: List<LocalMediaEntity>,
    folderRules: List<FolderRuleEntity>,
    onFolderRuleToggle: (String, Boolean) -> Unit,
    onOpenVault: () -> Unit,
    onSelectFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val favoritesCount = mediaList.count { it.isFavorite }
    val videosCount = mediaList.count { it.isVideo }
    val screenshotsCount = mediaList.count { it.displayName.contains("screenshot", ignoreCase = true) || it.folderName.contains("screenshot", ignoreCase = true) }

    // Group by folder
    val folders = mediaList.groupBy { it.folderName }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("Special Collections", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpecialCard(
                    title = "Favorites",
                    count = favoritesCount,
                    icon = Icons.Default.Favorite,
                    iconTint = Color(0xFFEF4444),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectFolder("Favorites") }
                )
                SpecialCard(
                    title = "Videos",
                    count = videosCount,
                    icon = Icons.Default.PlayCircle,
                    iconTint = TelegramBluePrimary,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectFolder("Videos") }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpecialCard(
                    title = "Screenshots",
                    count = screenshotsCount,
                    icon = Icons.Default.CropLandscape,
                    iconTint = Color(0xFF00897B),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectFolder("Screenshots") }
                )
                SpecialCard(
                    title = "Hidden Vault",
                    count = null,
                    icon = Icons.Default.Lock,
                    iconTint = VaultPurple,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenVault
                )
            }
        }

        item {
            Text("Device Albums & Folders", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (folders.isNotEmpty()) {
            items(folders.keys.toList().size) { index ->
                val folderName = folders.keys.toList()[index]
                val itemsInFolder = folders[folderName] ?: emptyList()
                val thumbnailUri = itemsInFolder.firstOrNull()?.uriString
                val rule = folderRules.firstOrNull { it.folderPath == folderName }
                val isBackupEnabled = rule?.isEnabled ?: true

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectFolder(folderName) }
                        .testTag("folder_card_$folderName"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (thumbnailUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(thumbnailUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = folderName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = folderName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${itemsInFolder.size} items",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isBackupEnabled) "Sync ON" else "Sync OFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isBackupEnabled) TelegramBluePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(
                                    checked = isBackupEnabled,
                                    onCheckedChange = { onFolderRuleToggle(folderName, it) },
                                    modifier = Modifier.testTag("switch_$folderName")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecialCard(
    title: String,
    count: Int?,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("special_card_$title"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconTint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (count != null) {
                    Text(
                        "$count items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Encrypted",
                        style = MaterialTheme.typography.bodySmall,
                        color = iconTint
                    )
                }
            }
        }
    }
}
