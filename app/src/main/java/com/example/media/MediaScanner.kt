package com.example.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.database.AppDatabase
import com.example.data.database.entity.FolderRuleEntity
import com.example.data.database.entity.LocalMediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaScanner(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val mediaDao = db.mediaDao()
    private val folderRuleDao = db.folderRuleDao()

    suspend fun scanAllMedia(): Int = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<LocalMediaEntity>()
        val folderSet = mutableSetOf<String>()

        // 1. Scan Images
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATA
        )

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Image_$id"
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol) * 1000L
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    val parentDir = if (path.isNotBlank()) File(path).parentFile?.name ?: "Pictures" else "Pictures"
                    folderSet.add(parentDir)

                    mediaList.add(
                        LocalMediaEntity(
                            id = id,
                            uriString = contentUri.toString(),
                            path = path,
                            displayName = name,
                            mimeType = mime,
                            size = size,
                            dateAdded = if (date > 0) date else System.currentTimeMillis(),
                            folderName = parentDir,
                            isVideo = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Scan Videos
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATA
        )

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video_$id"
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol) * 1000L
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    val parentDir = if (path.isNotBlank()) File(path).parentFile?.name ?: "Movies" else "Movies"
                    folderSet.add(parentDir)

                    mediaList.add(
                        LocalMediaEntity(
                            id = id + 1000000000L, // offset video IDs to prevent collision with image IDs
                            uriString = contentUri.toString(),
                            path = path,
                            displayName = name,
                            mimeType = mime,
                            size = size,
                            dateAdded = if (date > 0) date else System.currentTimeMillis(),
                            folderName = parentDir,
                            isVideo = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Insert into Room
        if (mediaList.isNotEmpty()) {
            mediaDao.insertAll(mediaList)
        }

        // Save discovered folder rules
        for (folder in folderSet) {
            folderRuleDao.insertOrUpdate(
                FolderRuleEntity(
                    folderPath = folder,
                    folderName = folder,
                    isEnabled = true
                )
            )
        }

        mediaList.size
    }

    suspend fun getAccessibleFolders(): List<String> = withContext(Dispatchers.IO) {
        val standardFolders = listOf("Camera", "Pictures", "Screenshots", "Download", "Documents", "Movies", "Music")
        val rules = folderRuleDao.getAllRulesFlow()
        standardFolders
    }

    suspend fun deleteMediaItem(item: LocalMediaEntity): Boolean = withContext(Dispatchers.IO) {
        var deleted = false
        try {
            val uri = Uri.parse(item.uriString)
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) {
                deleted = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (item.path.isNotBlank()) {
                val file = File(item.path)
                if (file.exists() && file.delete()) {
                    deleted = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mediaDao.delete(item)
        deleted
    }
}
