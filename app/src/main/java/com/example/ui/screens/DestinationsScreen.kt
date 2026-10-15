package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.database.entity.BackupDestinationEntity
import com.example.telegram.TelegramApiClient
import com.example.telegram.TelegramConfig
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TelegramBluePrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SECURITY NOTE:
 * Credentials (Bot Token and Channel ID) are preconfigured directly in the build configuration (BuildConfig).
 * In-app credential input forms have been removed to prevent arbitrary token entry.
 * Note that hardcoded tokens can be extracted from APK files via reverse engineering.
 * For production applications, always use an authenticated backend server or API proxy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationsScreen(
    destinations: List<BackupDestinationEntity>,
    onAddOrUpdateDestination: (BackupDestinationEntity) -> Unit = {},
    onDeleteDestination: (BackupDestinationEntity) -> Unit = {},
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { TelegramApiClient() }
    val uploadManager = remember { com.example.telegram.TelegramUploadManager(context) }

    var isChecking by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<String>("Checking connection...") }
    var isConnected by remember { mutableStateOf(false) }
    var botName by remember { mutableStateOf<String?>(null) }
    var channelStatuses by remember { mutableStateOf<List<com.example.telegram.ChannelStatus>>(emptyList()) }

    fun checkConnection() {
        scope.launch {
            isChecking = true
            connectionStatus = "Connecting to Telegram channels..."
            withContext(Dispatchers.IO) {
                try {
                    val botRes = client.getMe(TelegramConfig.BOT_TOKEN)
                    if (botRes.isFailure) {
                        isConnected = false
                        connectionStatus = "Telegram connection failed. Please check your internet or contact the developer."
                        return@withContext
                    }
                    val bot = botRes.getOrThrow()
                    botName = bot.username ?: bot.firstName

                    val statuses = uploadManager.checkAllChannelsStatus()
                    channelStatuses = statuses

                    val activeCount = statuses.count { it.isConnected }
                    isConnected = activeCount > 0
                    connectionStatus = if (isConnected) {
                        "Connected ($activeCount/4 channels active)"
                    } else {
                        "Telegram connection failed on all channels."
                    }

                    // Sync all channels to local Room DB
                    statuses.forEach { st ->
                        onAddOrUpdateDestination(
                            BackupDestinationEntity(
                                channelId = st.channelId,
                                title = st.title,
                                botToken = TelegramConfig.BOT_TOKEN,
                                isEnabled = st.isConnected,
                                lastBackupAt = System.currentTimeMillis(),
                                lastError = st.errorMessage
                            )
                        )
                    }
                } catch (e: Exception) {
                    isConnected = false
                    connectionStatus = "Telegram connection failed. Please check your internet or contact the developer."
                } finally {
                    isChecking = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        checkConnection()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telegram Cloud Status") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("destinations_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { checkConnection() }, enabled = !isChecking) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Status")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Connection Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("telegram_connection_status_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isConnected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else if (isChecking) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isConnected) TelegramBluePrimary
                                        else if (isChecking) StatusWarning
                                        else StatusError
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else if (isConnected) {
                                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color.White)
                                } else {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(
                                    text = if (isConnected) "Connected" else "Connection Status",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = connectionStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isConnected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                            }
                        }

                        if (!isConnected && !isChecking) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { checkConnection() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().testTag("retry_connection_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry Connection")
                            }
                        }
                    }
                }
            }

            // Multi-Channel Detailed Status List
            item {
                Text("Multi-Channel Storage Pools (4 Channels)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("channels_list_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val displayList = if (channelStatuses.isNotEmpty()) {
                            channelStatuses
                        } else {
                            com.example.Constants.TELEGRAM_CHANNEL_IDS.mapIndexed { i, id ->
                                com.example.telegram.ChannelStatus(
                                    channelId = id,
                                    channelIndex = i + 1,
                                    title = "Channel ${i + 1}",
                                    isConnected = isConnected
                                )
                            }
                        }

                        displayList.forEach { ch ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(if (ch.isConnected) StatusSuccess else StatusError)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Channel ${ch.channelIndex}: ${if (ch.isConnected) "Connected" else "Disconnected"}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "ID: ${com.example.Constants.getMaskedChannelId(ch.channelId)} • ${ch.title}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = if (ch.isConnected) "Active" else "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ch.isConnected) StatusSuccess else StatusError
                                )
                            }
                        }
                    }
                }
            }

            // Preconfigured Credentials Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = TelegramBluePrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Preconfigured Telegram Channel", style = MaterialTheme.typography.titleMedium)
                        }

                        Text(
                            "Credentials are compiled directly into the build configuration. In-app token input has been disabled for consistency and automatic operation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Bot Account", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (botName != null) "@$botName" else "Preconfigured Bot",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TelegramBluePrimary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Target Channel ID", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = TelegramConfig.getMaskedChannelId(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Connection Source", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "BuildConfig (Compiled)",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusSuccess
                            )
                        }
                    }
                }
            }

            // Security Notice Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = TelegramBluePrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Security Notice", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Hardcoded credentials compiled into an APK can be extracted using reverse engineering tools (jadx, apktool). For production apps, always use an authenticated backend server to intermediate Telegram API calls.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
