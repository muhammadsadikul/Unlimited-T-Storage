package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.entity.BackupDestinationEntity
import com.example.telegram.ChannelStatus
import com.example.telegram.TelegramApiClient
import com.example.telegram.TelegramUploadManager
import com.example.ui.theme.SecondaryTextGray
import com.example.ui.theme.SoftBlueAccent
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Premium Minimalist Telegram Status Screen.
 * Follows Google Photos aesthetic:
 * - Airy padding and white/subtle surfaces with soft shadows, no harsh 1dp borders.
 * - Clean user-friendly status banner (e.g. "Connection failed. Please check your internet.").
 * - Channel list showing clean green dot & "Connected" while hiding technical channel IDs.
 * - Hidden collapsible debug diagnostics revealing exact Telegram JSON responses (e.g. 401 Unauthorized, 400 Bad Request).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramStatusScreen(
    destinations: List<BackupDestinationEntity> = emptyList(),
    onAddOrUpdateDestination: (BackupDestinationEntity) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uploadManager = remember { TelegramUploadManager(context) }

    var isChecking by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var friendlyStatusMessage by remember { mutableStateOf("Checking connection...") }
    var channelStatuses by remember { mutableStateOf<List<ChannelStatus>>(emptyList()) }
    var showDebugLogs by remember { mutableStateOf(false) }
    var rawLogs by remember { mutableStateOf<List<String>>(emptyList()) }

    fun refreshStatus() {
        scope.launch {
            isChecking = true
            friendlyStatusMessage = "Connecting to cloud storage..."
            withContext(Dispatchers.IO) {
                val botRes = uploadManager.checkBotConnection()
                val statuses = uploadManager.checkAllChannelsStatus()
                channelStatuses = statuses
                rawLogs = TelegramApiClient.debugLogs

                val anyConnected = statuses.any { it.isConnected } && botRes.isSuccess
                isConnected = anyConnected

                if (anyConnected) {
                    val activeCount = statuses.count { it.isConnected }
                    friendlyStatusMessage = "Connected ($activeCount/4 channels active)"
                } else {
                    friendlyStatusMessage = "Connection failed. Please check your internet."
                }

                statuses.forEach { st ->
                    onAddOrUpdateDestination(
                        BackupDestinationEntity(
                            channelId = st.channelId,
                            title = st.title,
                            botToken = com.example.telegram.TelegramConfig.BOT_TOKEN,
                            isEnabled = st.isConnected,
                            lastBackupAt = System.currentTimeMillis(),
                            lastError = st.errorMessage
                        )
                    )
                }
                isChecking = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshStatus()
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Cloud Status",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("status_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshStatus() }, enabled = !isChecking) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = SoftBlueAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overall Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isConnected) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) StatusSuccess.copy(alpha = 0.15f) else StatusError.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = if (isConnected) StatusSuccess else StatusError
                                )
                            } else {
                                Icon(
                                    imageVector = if (isConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = if (isConnected) StatusSuccess else StatusError,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isConnected) "Connected" else "Connection Status",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = friendlyStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryTextGray
                            )
                        }
                    }
                }
            }

            // Retry Button if disconnected
            if (!isConnected && !isChecking) {
                item {
                    Button(
                        onClick = { refreshStatus() },
                        colors = ButtonDefaults.buttonColors(containerColor = SoftBlueAccent),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("retry_status_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry Connection", fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Channels Section
            item {
                Text(
                    text = "Storage Channels",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val displayList = if (channelStatuses.isNotEmpty()) {
                            channelStatuses
                        } else {
                            com.example.Constants.TELEGRAM_CHANNEL_IDS.mapIndexed { index, _ ->
                                ChannelStatus(
                                    channelId = "",
                                    channelIndex = index + 1,
                                    title = "Channel ${index + 1}",
                                    isConnected = isConnected
                                )
                            }
                        }

                        displayList.forEach { ch ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (ch.isConnected) StatusSuccess else StatusError)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Channel ${ch.channelIndex}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (ch.isConnected) "Connected" else "Disconnected",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                        color = if (ch.isConnected) StatusSuccess else StatusError
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Hidden Collapsible Debug Diagnostics
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showDebugLogs = !showDebugLogs }
                        .testTag("debug_diagnostics_toggle"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = SecondaryTextGray,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Debug Diagnostics",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SecondaryTextGray
                                )
                            }
                            Icon(
                                imageVector = if (showDebugLogs) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = SecondaryTextGray
                            )
                        }

                        AnimatedVisibility(visible = showDebugLogs) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = "Telegram API Responses:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                val logs = rawLogs.ifEmpty { listOf("No recent Telegram error responses recorded.") }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1E293B))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = logs.takeLast(10).joinToString("\n\n"),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
