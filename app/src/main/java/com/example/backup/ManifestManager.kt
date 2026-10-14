package com.example.backup

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.database.entity.AppSettingEntity
import com.example.data.database.entity.CloudRecordEntity
import com.example.data.model.CloudManifest
import com.example.data.model.ManifestEntry
import com.example.telegram.TelegramApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ManifestManager(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val cloudDao = db.cloudRecordDao()
    private val settingDao = db.settingDao()
    private val telegramClient = TelegramApiClient()

    companion object {
        const val MANIFEST_CAPTION = "#TELEGRAM_GALLERY_MANIFEST_V1"
        const val KEY_LATEST_MANIFEST_MSG_ID = "latest_manifest_msg_id"
        const val KEY_LATEST_MANIFEST_FILE_ID = "latest_manifest_file_id"
    }

    suspend fun generateManifest(channelId: String): CloudManifest = withContext(Dispatchers.IO) {
        val records = cloudDao.getRecordsForChannel(channelId)
        val entries = records.map {
            ManifestEntry(
                id = it.id.toString(),
                fileName = it.fileName,
                relativePath = it.originalPath,
                mimeType = it.mimeType,
                size = it.size,
                sha256 = it.sha256,
                telegramMessageId = it.telegramMessageId,
                telegramFileId = it.telegramFileId,
                isVaultEncrypted = it.isVaultEncrypted,
                ivBase64 = it.ivBase64,
                saltBase64 = it.saltBase64,
                uploadedAt = it.uploadTimestamp
            )
        }

        CloudManifest(
            version = 1,
            appVersion = "1.0",
            generatedAt = System.currentTimeMillis(),
            channelId = channelId,
            totalFiles = entries.size,
            totalBytes = entries.sumOf { it.size },
            entries = entries
        )
    }

    fun manifestToJson(manifest: CloudManifest): String {
        val json = JSONObject()
        json.put("version", manifest.version)
        json.put("appVersion", manifest.appVersion)
        json.put("generatedAt", manifest.generatedAt)
        json.put("channelId", manifest.channelId)
        json.put("totalFiles", manifest.totalFiles)
        json.put("totalBytes", manifest.totalBytes)

        val array = JSONArray()
        for (e in manifest.entries) {
            val item = JSONObject()
            item.put("id", e.id)
            item.put("fileName", e.fileName)
            item.put("relativePath", e.relativePath)
            item.put("mimeType", e.mimeType)
            item.put("size", e.size)
            item.put("sha256", e.sha256)
            item.put("telegramMessageId", e.telegramMessageId)
            item.put("telegramFileId", e.telegramFileId)
            item.put("isVaultEncrypted", e.isVaultEncrypted)
            item.put("ivBase64", e.ivBase64 ?: "")
            item.put("saltBase64", e.saltBase64 ?: "")
            item.put("uploadedAt", e.uploadedAt)
            array.put(item)
        }
        json.put("entries", array)
        return json.toString(2)
    }

    fun jsonToManifest(jsonStr: String): CloudManifest {
        val json = JSONObject(jsonStr)
        val entries = mutableListOf<ManifestEntry>()
        val array = json.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            entries.add(
                ManifestEntry(
                    id = item.optString("id"),
                    fileName = item.getString("fileName"),
                    relativePath = item.optString("relativePath", ""),
                    mimeType = item.optString("mimeType", "application/octet-stream"),
                    size = item.optLong("size", 0L),
                    sha256 = item.optString("sha256", ""),
                    telegramMessageId = item.optLong("telegramMessageId", 0L),
                    telegramFileId = item.optString("telegramFileId", ""),
                    isVaultEncrypted = item.optBoolean("isVaultEncrypted", false),
                    ivBase64 = item.optString("ivBase64").takeIf { it.isNotBlank() },
                    saltBase64 = item.optString("saltBase64").takeIf { it.isNotBlank() },
                    uploadedAt = item.optLong("uploadedAt", System.currentTimeMillis())
                )
            )
        }

        return CloudManifest(
            version = json.optInt("version", 1),
            appVersion = json.optString("appVersion", "1.0"),
            generatedAt = json.optLong("generatedAt", System.currentTimeMillis()),
            channelId = json.optString("channelId", ""),
            totalFiles = json.optInt("totalFiles", entries.size),
            totalBytes = json.optLong("totalBytes", entries.sumOf { it.size }),
            entries = entries
        )
    }

    suspend fun publishManifest(botToken: String, channelId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val manifest = generateManifest(channelId)
            val jsonStr = manifestToJson(manifest)
            val tempFile = File(context.cacheDir, "manifest_v1.json")
            tempFile.writeText(jsonStr)

            val uploadResult = telegramClient.uploadFile(
                botToken = botToken,
                chatId = channelId,
                file = tempFile,
                displayName = "tg_cloud_gallery_manifest.json",
                mimeType = "application/json",
                caption = "$MANIFEST_CAPTION - Updated ${manifest.totalFiles} files"
            )

            tempFile.delete()

            if (uploadResult.success) {
                settingDao.set(AppSettingEntity(KEY_LATEST_MANIFEST_MSG_ID, uploadResult.messageId.toString()))
                settingDao.set(AppSettingEntity(KEY_LATEST_MANIFEST_FILE_ID, uploadResult.fileId))
                Result.success("Manifest published (Message ID: ${uploadResult.messageId})")
            } else {
                Result.failure(Exception(uploadResult.error ?: "Failed to upload manifest"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importManifestToDatabase(manifest: CloudManifest) = withContext(Dispatchers.IO) {
        val records = manifest.entries.map {
            CloudRecordEntity(
                fileName = it.fileName,
                originalPath = it.relativePath,
                mimeType = it.mimeType,
                size = it.size,
                sha256 = it.sha256,
                channelId = manifest.channelId,
                telegramMessageId = it.telegramMessageId,
                telegramFileId = it.telegramFileId,
                uploadTimestamp = it.uploadedAt,
                isVaultEncrypted = it.isVaultEncrypted,
                ivBase64 = it.ivBase64,
                saltBase64 = it.saltBase64
            )
        }
        cloudDao.insertAll(records)
    }
}
