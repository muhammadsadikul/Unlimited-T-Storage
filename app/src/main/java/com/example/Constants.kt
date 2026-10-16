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
    const val TELEGRAM_BOT_TOKEN: String = "8916322348:AAGJ1lLkigwOEmHOzVI88F3GrX_z_eMnnnA"
    const val BOT_TOKEN: String = TELEGRAM_BOT_TOKEN

    /**
     * Preconfigured Telegram Channel IDs for multi-channel distributed storage.
     * All channel IDs are strictly formatted with the Telegram supergroup/channel prefix "-100".
     */
    val TELEGRAM_CHANNEL_IDS: List<String> = listOf(
        "-1003922853955",
        "-1004327071150",
        "-1004307831754",
        "-1004447017054"
    )
    val CHANNEL_IDS: List<String> = TELEGRAM_CHANNEL_IDS

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
     * Ensures any given channel ID has the required "-100" Telegram prefix.
     */
    fun formatChannelId(rawId: String): String {
        val trimmed = rawId.trim()
        if (trimmed.startsWith("-100")) return trimmed
        if (trimmed.startsWith("-")) return "-100${trimmed.removePrefix("-")}"
        if (trimmed.all { it.isDigit() }) return "-100$trimmed"
        return trimmed
    }

    /**
     * Returns a masked representation of a Channel ID for safe display in the UI.
     */
    fun getMaskedChannelId(channelId: String): String {
        val formatted = formatChannelId(channelId)
        if (formatted.length <= 8) return "••••"
        return formatted.take(5) + "••••" + formatted.takeLast(3)
    }
}
