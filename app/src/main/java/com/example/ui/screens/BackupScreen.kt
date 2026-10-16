package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.entity.BackupDestinationEntity
import com.example.data.database.entity.BackupJobEntity
import com.example.ui.theme.SecondaryTextGray
import com.example.ui.theme.SoftBlueAccent
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusUploading
import com.example.ui.theme.StatusWarning

/**
 * Premium Google Photos-inspired Backup Dashboard Screen.
 * - Airy padding and clean white background.
 * - 16dp and 20dp rounded corners with no harsh borders or dividers.
 * - Large, prominent, rounded "Start Backup" CTA button.
 * - Real live connection status with green dots for active channels.
 * - Technical channel IDs, security warnings, and technical boxes removed from user view.
 */
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
    isTelegramConnected: Boolean,
    connectionStatusText: String,
    onStartBackup: () -> Unit,
    onPauseBackup: () -> Unit,
    onRetryFailed: () -> Unit,
    onManageDestinations: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Live Connection Status Card (Replaces the technical box)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onManageDestinations)
                    .testTag("manage_destinations_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isTelegramConnected) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isTelegramConnected) StatusSuccess.copy(alpha = 0.15f)
                                    else StatusError.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isTelegramConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (isTelegramConnected) StatusSuccess else StatusError,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isTelegramConnected) StatusSuccess else StatusError)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isTelegramConnected) "Connected" else "Connection Failed",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isTelegramConnected) "Cloud storage pools active" else connectionStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryTextGray
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View Details",
                        tint = SecondaryTextGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Channels Status Card (Clean list with green dot & Connected, no IDs exposed)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("multi_channel_status_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Storage Channels",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val channelCount = com.example.Constants.TELEGRAM_CHANNEL_IDS.size
                    for (i in 0 until channelCount) {
                        val chId = com.example.Constants.TELEGRAM_CHANNEL_IDS[i]
                        val dest = destinations.firstOrNull { it.channelId == chId }
                        val isChannelActive = isTelegramConnected && (dest == null || dest.isEnabled)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isChannelActive) StatusSuccess else StatusError)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Channel ${i + 1}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = if (isChannelActive) "Connected" else "Disconnected",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = if (isChannelActive) StatusSuccess else StatusError
                            )
                        }
                    }
                }
            }
        }

        // Active Upload Progress Indicator
        item {
            AnimatedVisibility(visible = isUploading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Uploading to Cloud...",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = SoftBlueAccent
                            )
                            Text(
                                text = "$currentUploadingProgress%",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = SoftBlueAccent
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currentUploadingName ?: "Preparing files...",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryTextGray,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { currentUploadingProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = SoftBlueAccent,
                            trackColor = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }

        // Large, Prominent, Rounded Primary Action Button
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isUploading) {
                    Button(
                        onClick = onPauseBackup,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusWarning),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("pause_backup_button")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Pause Backup", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onStartBackup,
                        colors = ButtonDefaults.buttonColors(containerColor = SoftBlueAccent),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_backup_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Start Backup", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (failedJobsCount > 0) {
                    OutlinedButton(
                        onClick = onRetryFailed,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("retry_failed_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = SoftBlueAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry Failed Uploads ($failedJobsCount)", color = SoftBlueAccent, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Storage Statistics Section
        item {
            Text(
                text = "Storage Overview",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 2.dp, top = 4.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        title = "Device Media",
                        value = totalMediaCount.toString(),
                        subtitle = "Ready to backup",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Cloud Stored",
                        value = cloudRecordsCount.toString(),
                        subtitle = Formatter.formatFileSize(context, cloudTotalBytes),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        title = "In Queue",
                        value = pendingJobsCount.toString(),
                        subtitle = if (isUploading) "Uploading" else "Queued",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Issues",
                        value = failedJobsCount.toString(),
                        subtitle = if (failedJobsCount > 0) "Needs retry" else "All clear",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Recent Activity Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (recentJobs.isNotEmpty()) {
                    Text(
                        text = "${recentJobs.size} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryTextGray
                    )
                }
            }
        }

        if (recentJobs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No recent upload activity. Tap Start Backup to begin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryTextGray
                        )
                    }
                }
            }
        } else {
            items(recentJobs, key = { it.id }) { job ->
                JobRowItem(job = job)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = SecondaryTextGray
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = SoftBlueAccent
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryTextGray
            )
        }
    }
}

@Composable
private fun JobRowItem(job: BackupJobEntity) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.fileName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1
                )
                Text(
                    text = "${job.status} • ${Formatter.formatFileSize(context, job.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryTextGray
                )
            }

            if (job.status == "BACKED_UP") {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = StatusSuccess,
                    modifier = Modifier.size(18.dp)
                )
            } else if (job.status == "FAILED") {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = StatusError,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
