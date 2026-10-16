package com.example.ui.screens

import androidx.compose.runtime.Composable
import com.example.data.database.entity.BackupDestinationEntity

/**
 * DestinationsScreen: Delegates to the redesigned, premium TelegramStatusScreen.
 */
@Composable
fun DestinationsScreen(
    destinations: List<BackupDestinationEntity>,
    onAddOrUpdateDestination: (BackupDestinationEntity) -> Unit = {},
    onDeleteDestination: (BackupDestinationEntity) -> Unit = {},
    onBack: () -> Unit
) {
    TelegramStatusScreen(
        destinations = destinations,
        onAddOrUpdateDestination = onAddOrUpdateDestination,
        onBack = onBack
    )
}
