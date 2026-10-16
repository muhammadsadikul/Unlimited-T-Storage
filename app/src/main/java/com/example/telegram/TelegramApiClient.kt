package com.example.telegram

import com.example.Constants
import com.example.data.model.TelegramBotInfo
import com.example.data.model.TelegramChatInfo
import com.example.data.model.TelegramUploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class TelegramApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val MAX_BOT_UPLOAD_SIZE = 50L * 1024L * 1024L // 50MB
        private const val BASE_URL = "https://api.telegram.org/bot"

        // Hidden debug logs storing exact Telegram JSON error responses
        private val _debugLogs = mutableListOf<String>()
        val debugLogs: List<String>
            get() = synchronized(_debugLogs) { _debugLogs.toList() }

        fun addDebugLog(entry: String) {
            synchronized(_debugLogs) {
                if (_debugLogs.size >= 50) _debugLogs.removeAt(0)
                _debugLogs.add(entry)
            }
        }

        fun clearDebugLogs() {
            synchronized(_debugLogs) { _debugLogs.clear() }
        }
    }

    suspend fun getMe(botToken: String): Result<TelegramBotInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL$botToken/getMe"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                if (json.optBoolean("ok")) {
                    val result = json.getJSONObject("result")
                    Result.success(
                        TelegramBotInfo(
                            id = result.getLong("id"),
                            isBot = result.getBoolean("is_bot"),
                            firstName = result.getString("first_name"),
                            username = result.optString("username", null)
                        )
                    )
                } else {
                    val errorLog = "getMe Error (HTTP ${response.code}): $body"
                    addDebugLog(errorLog)
                    Result.failure(IOException(json.optString("description", "Failed to connect bot")))
                }
            }
        } catch (e: Exception) {
            addDebugLog("getMe Exception: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getChat(botToken: String, chatId: String): Result<TelegramChatInfo> = withContext(Dispatchers.IO) {
        val formattedChatId = Constants.formatChannelId(chatId)
        try {
            val url = "$BASE_URL$botToken/getChat?chat_id=$formattedChatId"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                if (json.optBoolean("ok")) {
                    val result = json.getJSONObject("result")
                    Result.success(
                        TelegramChatInfo(
                            id = result.getLong("id"),
                            title = result.optString("title", result.optString("first_name", "Channel")),
                            type = result.optString("type", "channel"),
                            username = result.optString("username", null)
                        )
                    )
                } else {
                    val errorLog = "getChat ($formattedChatId) Error (HTTP ${response.code}): $body"
                    addDebugLog(errorLog)
                    Result.failure(IOException(json.optString("description", "Channel not found or bot lacks permission")))
                }
            }
        } catch (e: Exception) {
            addDebugLog("getChat ($formattedChatId) Exception: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getChatPinnedFileId(botToken: String, chatId: String): String? = withContext(Dispatchers.IO) {
        val formattedChatId = Constants.formatChannelId(chatId)
        try {
            val url = "$BASE_URL$botToken/getChat?chat_id=$formattedChatId"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                if (json.optBoolean("ok")) {
                    val result = json.getJSONObject("result")
                    val pinned = result.optJSONObject("pinned_message")
                    val document = pinned?.optJSONObject("document")
                    return@withContext document?.optString("file_id")
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun pinChatMessage(botToken: String, chatId: String, messageId: Long): Boolean = withContext(Dispatchers.IO) {
        val formattedChatId = Constants.formatChannelId(chatId)
        try {
            val url = "$BASE_URL$botToken/pinChatMessage?chat_id=$formattedChatId&message_id=$messageId&disable_notification=true"
            val request = Request.Builder().url(url).post(RequestBody.create(null, ByteArray(0))).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                json.optBoolean("ok", false)
            }
        } catch (e: Exception) {
            addDebugLog("pinChatMessage Exception: ${e.message}")
            false
        }
    }

    suspend fun uploadFile(
        botToken: String,
        chatId: String,
        file: File,
        displayName: String,
        mimeType: String,
        caption: String? = null,
        onProgress: (percent: Int, bytesWritten: Long, totalBytes: Long) -> Unit = { _, _, _ -> }
    ): TelegramUploadResult = withContext(Dispatchers.IO) {
        val formattedChatId = Constants.formatChannelId(chatId)
        if (!file.exists()) {
            return@withContext TelegramUploadResult(success = false, error = "File not found: ${file.absolutePath}")
        }
        if (file.length() > MAX_BOT_UPLOAD_SIZE) {
            return@withContext TelegramUploadResult(
                success = false,
                error = "File size (${file.length() / (1024 * 1024)}MB) exceeds Telegram standard bot limit of 50MB."
            )
        }

        try {
            val countingRequestBody = CountingRequestBody(
                delegate = RequestBody.create(mimeType.toMediaTypeOrNull(), file),
                totalBytes = file.length(),
                onProgress = onProgress
            )

            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", formattedChatId)
                .addFormDataPart("document", displayName, countingRequestBody)

            if (!caption.isNullOrBlank()) {
                multipartBuilder.addFormDataPart("caption", caption)
            }

            val request = Request.Builder()
                .url("$BASE_URL$botToken/sendDocument")
                .post(multipartBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = JSONObject(bodyStr)

                if (response.code == 429) {
                    val retryAfter = json.optJSONObject("parameters")?.optInt("retry_after", 5) ?: 5
                    addDebugLog("uploadFile 429 RateLimit: $bodyStr")
                    return@withContext TelegramUploadResult(
                        success = false,
                        error = "Rate limited by Telegram. Retry after $retryAfter seconds",
                        retryAfterSeconds = retryAfter
                    )
                }

                if (json.optBoolean("ok")) {
                    val result = json.getJSONObject("result")
                    val messageId = result.optLong("message_id", 0L)
                    val document = result.optJSONObject("document")
                    val fileId = document?.optString("file_id") ?: ""

                    TelegramUploadResult(
                        success = true,
                        messageId = messageId,
                        fileId = fileId
                    )
                } else {
                    val desc = json.optString("description", "Upload failed")
                    addDebugLog("uploadFile Error (HTTP ${response.code}): $bodyStr")
                    TelegramUploadResult(
                        success = false,
                        error = desc
                    )
                }
            }
        } catch (e: Exception) {
            addDebugLog("uploadFile Exception: ${e.message}")
            TelegramUploadResult(
                success = false,
                error = e.localizedMessage ?: "Network error during upload"
            )
        }
    }

    suspend fun downloadFile(
        botToken: String,
        fileId: String,
        destinationFile: File,
        onProgress: (percent: Int, bytesRead: Long, totalBytes: Long) -> Unit = { _, _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val getFileUrl = "$BASE_URL$botToken/getFile?file_id=$fileId"
            val getFileRequest = Request.Builder().url(getFileUrl).build()
            val filePath = client.newCall(getFileRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                if (json.optBoolean("ok")) {
                    json.getJSONObject("result").getString("file_path")
                } else {
                    addDebugLog("getFile Error (HTTP ${response.code}): $body")
                    throw IOException(json.optString("description", "Failed to get file path"))
                }
            }

            val downloadUrl = "https://api.telegram.org/file/bot$botToken/$filePath"
            val downloadRequest = Request.Builder().url(downloadUrl).build()
            client.newCall(downloadRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    addDebugLog("downloadFile Error HTTP ${response.code}")
                    throw IOException("HTTP error ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty response body")
                val totalLength = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val percent = if (totalLength > 0) ((totalRead * 100) / totalLength).toInt() else 0
                            onProgress(percent, totalRead, totalLength)
                        }
                    }
                }
            }
            Result.success(destinationFile)
        } catch (e: Exception) {
            addDebugLog("downloadFile Exception: ${e.message}")
            Result.failure(e)
        }
    }
}

private class CountingRequestBody(
    private val delegate: RequestBody,
    private val totalBytes: Long,
    private val onProgress: (percent: Int, bytesWritten: Long, totalBytes: Long) -> Unit
) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = totalBytes

    override fun writeTo(sink: BufferedSink) {
        val countingSink = object : okio.ForwardingSink(sink) {
            var bytesWritten = 0L

            override fun write(source: okio.Buffer, byteCount: Long) {
                super.write(source, byteCount)
                bytesWritten += byteCount
                val percent = if (totalBytes > 0) ((bytesWritten * 100) / totalBytes).toInt() else 0
                onProgress(percent, bytesWritten, totalBytes)
            }
        }
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}
