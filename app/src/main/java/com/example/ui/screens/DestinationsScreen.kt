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
    val scope = rememberCoroutineScope()
    val client = remember { TelegramApiClient() }

    var isChecking by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<String>("Checking connection...") }
    var isConnected by remember { mutableStateOf(false) }
    var botName by remember { mutableStateOf<String?>(null) }
    var channelTitle by remember { mutableStateOf<String?>(null) }

    fun checkConnection() {
        scope.launch {
            isChecking = true
            connectionStatus = "Connecting to Telegram..."
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

                    val chatRes = client.getChat(TelegramConfig.BOT_TOKEN, TelegramConfig.CHANNEL_ID)
                    channelTitle = if (chatRes.isSuccess) chatRes.getOrThrow().title else "Private Storage Channel"

                    isConnected = true
                    connectionStatus = "Connected"

                    // Sync the active preconfigured destination to local Room DB
                    onAddOrUpdateDestination(
                        BackupDestinationEntity(
                            channelId = TelegramConfig.CHANNEL_ID,
                            title = channelTitle ?: "Telegram Cloud Storage",
                            botToken = TelegramConfig.BOT_TOKEN,
                            isEnabled = true,
                            lastBackupAt = System.currentTimeMillis()
                        )
                    )
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
