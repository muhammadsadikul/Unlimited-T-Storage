package com.example

/**
 * Global Constants for Unlimited T Storage application.
 * Contains preconfigured credentials for multiple Telegram channels and bot tokens.
 *
 * =========================================================================================
 * SECURITY WARNING:
 * =========================================================================================
 * Hardcoded tokens and credentials compiled into client APKs can easily be decompiled and
 * extracted using standard Android reverse engineering tools (e.g., jadx, apktool, strings).
 *
 * For production applications, never hardcode private bot tokens or API keys directly in client
 * code. Instead, use an authenticated backend server or cloud proxy (e.g., Firebase Cloud Functions,
 * Google Cloud Run, or AWS Lambda) that securely stores secrets on the server and authenticates
 * mobile clients before proxying Telegram Bot API calls.
 * =========================================================================================
 */
object Constants {
    /**
     * Hardcoded Telegram Bot Token.
     */
    const val TELEGRAM_BOT_TOKEN: String = "8916322348:AAF-_jWCtvjPjoOmJYCgXckO1AqnGGpDXD0"

    /**
     * Preconfigured Telegram Channel IDs for multi-channel distributed storage.
     * Files are distributed across these channels using a Round-Robin strategy to avoid
     * single-channel limits and achieve unlimited cloud storage capacity.
     */
    val TELEGRAM_CHANNEL_IDS: List<String> = listOf(
        "-3922853955",
        "-4327071150",
        "-4307831754",
        "-4447017054"
    )

    /**
     * Primary default channel ID (first channel in rotation).
     */
    val DEFAULT_CHANNEL_ID: String = TELEGRAM_CHANNEL_IDS.first()

    /**
     * Helper to retrieve a channel by index safely.
     */
    fun getChannelId(index: Int): String {
        return TELEGRAM_CHANNEL_IDS[Math.floorMod(index, TELEGRAM_CHANNEL_IDS.size)]
    }

    /**
     * Returns a masked representation of a Channel ID for safe display in the UI.
     */
    fun getMaskedChannelId(channelId: String): String {
        if (channelId.length <= 6) return "••••"
        return channelId.take(4) + "••••" + channelId.takeLast(2)
    }
}
