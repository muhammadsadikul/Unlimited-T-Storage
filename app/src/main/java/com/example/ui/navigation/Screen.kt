package com.example.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Photos : Screen("photos", "Photos")
    object Collections : Screen("collections", "Collections")
    object Files : Screen("files", "Files")
    object Backup : Screen("backup", "Backup")
    object Settings : Screen("settings", "Settings")

    // Sub-screens
    object MediaDetail : Screen("media_detail/{mediaId}", "Media Viewer") {
        fun createRoute(mediaId: Long) = "media_detail/$mediaId"
    }
    object Vault : Screen("vault", "Hidden Vault")
    object Restore : Screen("restore", "Cloud Restore")
    object Destinations : Screen("destinations", "Telegram Channels")
    object Onboarding : Screen("onboarding", "Welcome")
}
