package com.example.ui.screens

import android.content.Intent
import android.os.Environment
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.model.FileItem
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TelegramBluePrimary
import java.io.File
import java.util.Date

@Composable
fun FilesScreen(
    onBackupFile: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentDir by remember {
        val defaultDir = Environment.getExternalStorageDirectory()
        mutableStateOf(if (defaultDir != null && defaultDir.exists()) defaultDir else context.filesDir)
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var fileItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }

    // Intercept back button when inside a subdirectory
    val isRoot = currentDir.parentFile == null || currentDir == Environment.getExternalStorageDirectory()
    BackHandler(enabled = !isRoot) {
        currentDir.parentFile?.let { currentDir = it }
    }

    // Refresh file list whenever currentDir changes
    LaunchedEffect(currentDir) {
        val files = currentDir.listFiles()?.toList().orEmpty()
        fileItems = files.map { file ->
            val isDir = file.isDirectory
            val itemCount = if (isDir) file.listFiles()?.size ?: 0 else 0
            val mimeType = if (isDir) "directory" else getMimeType(file.extension)
            FileItem(
                file = file,
                name = file.name,
                path = file.absolutePath,
                isDirectory = isDir,
                size = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                mimeType = mimeType,
                itemCount = itemCount
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    val filteredList = remember(fileItems, searchQuery, selectedFilter) {
        fileItems.filter { item ->
            val matchesSearch = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Folders" -> item.isDirectory
                "Documents" -> item.name.endsWith(".pdf", true) || item.name.endsWith(".doc", true) || item.name.endsWith(".docx", true) || item.name.endsWith(".txt", true)
                "Downloads" -> item.path.contains("download", ignoreCase = true)
                "Audio" -> item.mimeType.startsWith("audio") || item.name.endsWith(".mp3", true) || item.name.endsWith(".m4a", true)
                "Archives" -> item.name.endsWith(".zip", true) || item.name.endsWith(".rar", true) || item.name.endsWith(".7z", true) || item.name.endsWith(".tar", true)
                "Images" -> item.mimeType.startsWith("image")
                "Videos" -> item.mimeType.startsWith("video")
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Search & Filter header
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search files & folders...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("files_search_input"),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filters = listOf("All", "Folders", "Documents", "Audio", "Archives", "Images", "Videos")
                    items(filters) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) }
                        )
                    }
                }
            }
        }

        // Breadcrumb Navigation
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRoot) {
                    IconButton(
                        onClick = { currentDir.parentFile?.let { currentDir = it } },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up directory")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = currentDir.name.ifBlank { "Internal Storage" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${filteredList.size} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // File List
        if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("This folder is empty or inaccessible")
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredList, key = { it.path }) { item ->
                    FileRowItem(
                        item = item,
                        onClick = {
                            if (item.isDirectory) {
                                currentDir = item.file
                            } else {
                                // Attempt to open or share
                                shareOrOpenFile(context, item.file, item.mimeType)
                            }
                        },
                        onBackup = { onBackupFile(item.file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRowItem(
    item: FileItem,
    onClick: () -> Unit,
    onBackup: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("file_item_${item.name}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, tint) = getFileIcon(item)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (item.isDirectory) {
                        "${item.itemCount} items"
                    } else {
                        "${Formatter.formatFileSize(context, item.size)} • ${DateFormat.format("yyyy-MM-dd", Date(item.lastModified))}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!item.isDirectory) {
                IconButton(onClick = onBackup, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = "Backup to Telegram",
                        tint = TelegramBluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (!item.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Backup to Telegram") },
                            leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onBackup()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                shareOrOpenFile(context, item.file, item.mimeType)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun getFileIcon(item: FileItem): Pair<ImageVector, Color> {
    if (item.isDirectory) return Icons.Default.Folder to Color(0xFFF59E0B)
    val ext = item.file.extension.lowercase()
    return when {
        item.mimeType.startsWith("image") || ext in listOf("jpg", "jpeg", "png", "webp", "gif") ->
            Icons.Default.Image to Color(0xFF10B981)
        item.mimeType.startsWith("video") || ext in listOf("mp4", "mkv", "avi", "mov") ->
            Icons.Default.Movie to Color(0xFF3B82F6)
        item.mimeType.startsWith("audio") || ext in listOf("mp3", "m4a", "wav", "flac") ->
            Icons.Default.AudioFile to Color(0xFF8B5CF6)
        ext in listOf("pdf", "doc", "docx", "txt", "xlsx") ->
            Icons.Default.Description to Color(0xFFEF4444)
        ext in listOf("zip", "rar", "7z", "tar", "gz") ->
            Icons.Default.Archive to Color(0xFF6B7280)
        else ->
            Icons.Default.InsertDriveFile to Color(0xFF64748B)
    }
}

private fun getMimeType(extension: String): String {
    return when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}

private fun shareOrOpenFile(context: android.content.Context, file: File, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share File"))
    } catch (e: Exception) {
        val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_TEXT, file.name)
        }
        context.startActivity(Intent.createChooser(fallbackIntent, "Share"))
    }
}
