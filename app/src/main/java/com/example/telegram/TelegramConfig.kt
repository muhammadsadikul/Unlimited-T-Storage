package com.example.telegram

import com.example.BuildConfig

/**
 * =========================================================================================
 * SECURITY NOTICE & ARCHITECTURAL WARNING:
 * =========================================================================================
 * Hardcoding Telegram Bot Tokens and Channel IDs directly in the client application binary (APK)
 * exposes credentials to reverse engineering. APK files are public client artifacts that can be
 * decompiled or inspected using standard reverse engineering utilities such as `jadx`, `apktool`,
 * or binary `strings` search.
 *
 * Anyone who extracts this Bot Token obtains administrative capabilities over the Telegram Bot,
 * potentially enabling them to read conversations, access private channels, download uploaded media,
 * or delete cloud backup storage.
 *
 * RECOMMENDATION FOR PRODUCTION APPLICATIONS:
 * In production systems, client applications should never communicate directly with the Telegram
 * Bot API using a private bot token. Instead, implement a secure backend server or cloud function
 * (e.g., Firebase Cloud Functions, Google Cloud Run, or AWS Lambda) that acts as an authenticated
 * proxy. The mobile client authenticates with your backend (via Firebase Auth, OAuth2, or JWT),
 * and the backend securely handles Telegram Bot API requests while keeping the token strictly
 * secret on the server side.
 * =========================================================================================
 */
object TelegramConfig {
    /**
     * The Telegram Bot Token from Constants / BuildConfig.
     */
    val BOT_TOKEN: String = if (BuildConfig.TELEGRAM_BOT_TOKEN.isNotBlank() && !BuildConfig.TELEGRAM_BOT_TOKEN.startsWith("[")) {
        BuildConfig.TELEGRAM_BOT_TOKEN
    } else {
        com.example.Constants.TELEGRAM_BOT_TOKEN
    }

    /**
     * The list of 4 preconfigured Telegram Channel IDs for multi-channel distributed storage.
     */
    val CHANNEL_IDS: List<String> = com.example.Constants.TELEGRAM_CHANNEL_IDS

    /**
     * Primary default channel ID (first channel in rotation).
     */
    val CHANNEL_ID: String = CHANNEL_IDS.firstOrNull() ?: "-3922853955"

    /**
     * Checks if the credentials have been configured with non-placeholder values.
     */
    fun isConfigured(): Boolean {
        return BOT_TOKEN.isNotBlank() &&
                BOT_TOKEN != "[YOUR_BOT_TOKEN]" &&
                !BOT_TOKEN.startsWith("[") &&
                CHANNEL_IDS.isNotEmpty()
    }

    /**
     * Returns a masked representation of the Channel ID for safe display in the UI.
     */
    fun getMaskedChannelId(channelId: String = CHANNEL_ID): String {
        return com.example.Constants.getMaskedChannelId(channelId)
    }
}
