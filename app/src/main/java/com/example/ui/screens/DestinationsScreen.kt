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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TelegramBluePrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationsScreen(
    destinations: List<BackupDestinationEntity>,
    onAddOrUpdateDestination: (BackupDestinationEntity) -> Unit,
    onDeleteDestination: (BackupDestinationEntity) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telegram Destinations") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("destinations_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showGuideDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Setup Guide")
                    }
                }
            )
        },
        floatingActionButton = {
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = TelegramBluePrimary),
                modifier = Modifier.testTag("add_channel_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Channel")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = TelegramBluePrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("How Telegram Cloud Storage Works", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Each private Telegram channel acts as an unlimited remote storage drive. Your files are securely uploaded through your private bot with original byte-level fidelity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (destinations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No Telegram channels configured yet", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Add your private channel ID and Bot Token to begin backups",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(destinations, key = { it.channelId }) { destination ->
                    DestinationCard(
                        destination = destination,
                        onToggle = { isEnabled ->
                            onAddOrUpdateDestination(destination.copy(isEnabled = isEnabled))
                        },
                        onDelete = { onDeleteDestination(destination) }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddDestinationDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { newDest ->
                    onAddOrUpdateDestination(newDest)
                    showAddDialog = false
                }
            )
        }

        if (showGuideDialog) {
            AlertDialog(
                onDismissRequest = { showGuideDialog = false },
                title = { Text("Telegram Bot & Channel Setup Guide") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. Open Telegram and search for @BotFather.")
                        Text("2. Send /newbot and follow instructions to create a bot. Copy the API Token.")
                        Text("3. In Telegram, create a new Private Channel (e.g. 'My Cloud Backup').")
                        Text("4. Add your new bot as an Administrator of the channel with 'Post Messages' permission.")
                        Text("5. Get your Channel ID: Forward a post from the channel to @userinfobot or @raw_data_bot to find the chat id (typically starts with -100).")
                        Text("6. Paste both into this app and tap 'Test Connection'.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGuideDialog = false }) { Text("Got it") }
                }
            )
        }
    }
}

@Composable
private fun DestinationCard(
    destination: BackupDestinationEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(destination.title, style = MaterialTheme.typography.titleMedium)
                    Text("Chat ID: ${destination.channelId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = destination.isEnabled, onCheckedChange = onToggle)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (destination.lastError == null) StatusSuccess else StatusError)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (destination.lastError == null) "Ready for backup" else "Error: ${destination.lastError}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (destination.lastError == null) StatusSuccess else StatusError
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AddDestinationDialog(
    onDismiss: () -> Unit,
    onConfirm: (BackupDestinationEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var channelId by remember { mutableStateOf("") }
    var botToken by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val client = remember { TelegramApiClient() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Telegram Channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. My Photo Backup") },
                    modifier = Modifier.fillMaxWidth().testTag("dest_title_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = channelId,
                    onValueChange = { channelId = it },
                    label = { Text("Channel Chat ID") },
                    placeholder = { Text("-100xxxxxxxxxx") },
                    modifier = Modifier.fillMaxWidth().testTag("dest_channel_id_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    label = { Text("Bot Token") },
                    placeholder = { Text("123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11") },
                    modifier = Modifier.fillMaxWidth().testTag("dest_bot_token_input"),
                    singleLine = true
                )

                Button(
                    onClick = {
                        isTesting = true
                        testResult = null
                        scope.launch {
                            val botRes = client.getMe(botToken.trim())
                            if (botRes.isSuccess) {
                                val chatRes = client.getChat(botToken.trim(), channelId.trim())
                                if (chatRes.isSuccess) {
                                    val chat = chatRes.getOrNull()
                                    testResult = "Success: Connected to '${chat?.title}'"
                                    isSuccess = true
                                    if (title.isBlank()) {
                                        title = chat?.title ?: "Telegram Channel"
                                    }
                                } else {
                                    testResult = "Error: ${chatRes.exceptionOrNull()?.message}"
                                    isSuccess = false
                                }
                            } else {
                                testResult = "Invalid Token: ${botRes.exceptionOrNull()?.message}"
                                isSuccess = false
                            }
                            isTesting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("test_connection_button"),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying credentials...")
                    } else {
                        Text("Test Real Connection")
                    }
                }

                if (testResult != null) {
                    Text(
                        text = testResult!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSuccess) StatusSuccess else StatusError
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (channelId.isNotBlank() && botToken.isNotBlank()) {
                        onConfirm(
                            BackupDestinationEntity(
                                channelId = channelId.trim(),
                                title = title.ifBlank { "Channel $channelId" },
                                botToken = botToken.trim(),
                                isEnabled = true
                            )
                        )
                    }
                },
                enabled = channelId.isNotBlank() && botToken.isNotBlank(),
                modifier = Modifier.testTag("save_destination_button")
            ) {
                Text("Save Destination")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
