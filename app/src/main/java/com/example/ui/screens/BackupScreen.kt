package com.example.ui.screens

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.database.entity.BackupDestinationEntity
import com.example.data.database.entity.BackupJobEntity
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusUploading
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TelegramBluePrimary
import java.util.Date

@Composable
fun BackupScreen(
    totalMediaCount: Int,
    cloudRecordsCount: Int,
    cloudTotalBytes: Long,
    pendingJobsCount: Int,
    failedJobsCount: Int,
    destinations: List<BackupDestinationEntity>,
    recentJobs: List<BackupJobEntity>,
    isUploading: Boolean,
    currentUploadingName: String?,
    currentUploadingProgress: Int,
    onStartBackup: () -> Unit,
    onPauseBackup: () -> Unit,
    onRetryFailed: () -> Unit,
    onManageDestinations: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val enabledDestinations = destinations.filter { it.isEnabled }
    val lastBackup = destinations.mapNotNull { it.lastBackupAt }.maxOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Destination status Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageDestinations)
                    .testTag("manage_destinations_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (enabledDestinations.isNotEmpty())
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (enabledDestinations.isNotEmpty()) TelegramBluePrimary else StatusError
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (enabledDestinations.isNotEmpty()) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (enabledDestinations.isNotEmpty())
                                    "${enabledDestinations.size} Telegram Channel(s) Connected"
                                else
                                    "No Telegram Destination Configured",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (lastBackup != null)
                                    "Last synced: ${DateFormat.format("yyyy-MM-dd HH:mm", Date(lastBackup))}"
                                else
                                    "Tap to set up your private channel & bot",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(Icons.Default.Settings, contentDescription = "Manage")
                }
            }
        }

        // Active Upload Progress Box
        item {
            AnimatedVisibility(visible = isUploading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Uploading file...", style = MaterialTheme.typography.labelLarge, color = TelegramBluePrimary)
                            Text("$currentUploadingProgress%", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentUploadingName ?: "Connecting to Telegram...",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { currentUploadingProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = TelegramBluePrimary
                        )
                    }
                }
            }
        }

        // Backup Controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isUploading) {
                    Button(
                        onClick = onPauseBackup,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusWarning),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pause_backup_button")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pause Backup")
                    }
                } else {
                    Button(
                        onClick = onStartBackup,
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBluePrimary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_backup_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Backup")
                    }
                }

                if (failedJobsCount > 0) {
                    OutlinedButton(
                        onClick = onRetryFailed,
                        modifier = Modifier.testTag("retry_failed_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry ($failedJobsCount)")
                    }
                }
            }
        }

        // Live Statistics Grid
        item {
            Text("Real-Time Storage Stats", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        title = "Accessible Files",
                        value = totalMediaCount.toString(),
                        subtitle = "Indexed on device",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Cloud Backed Up",
                        value = cloudRecordsCount.toString(),
                        subtitle = Formatter.formatFileSize(context, cloudTotalBytes),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        title = "Pending Queue",
                        value = pendingJobsCount.toString(),
                        subtitle = if (isUploading) "Uploading now" else "Waiting in queue",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Failed Uploads",
                        value = failedJobsCount.toString(),
                        subtitle = if (failedJobsCount > 0) "Tap retry to resume" else "All clear",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Upload Queue / History Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Queue & Activity", style = MaterialTheme.typography.titleMedium)
                Text("${recentJobs.size} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (recentJobs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No upload history yet. Select photos or files to start.")
                    }
                }
            }
        } else {
            items(recentJobs, key = { it.id }) { job ->
                JobRowItem(job = job)
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = TelegramBluePrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun JobRowItem(job: BackupJobEntity) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = when (job.status) {
                "BACKED_UP" -> StatusSuccess
                "UPLOADING" -> StatusUploading
                "FAILED" -> StatusError
                "PAUSED" -> StatusWarning
                else -> Color.Gray
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(job.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    text = "${job.status} • ${Formatter.formatFileSize(context, job.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (job.lastError != null) {
                    Text(
                        text = "Error: ${job.lastError}",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusError,
                        maxLines = 1
                    )
                }
            }

            if (job.status == "BACKED_UP") {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(20.dp))
            } else if (job.status == "FAILED") {
                Icon(Icons.Default.Error, contentDescription = null, tint = StatusError, modifier = Modifier.size(20.dp))
            }
        }
    }
}
