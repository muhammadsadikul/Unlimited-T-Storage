package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.database.entity.AppSettingEntity
import com.example.data.database.entity.BackupDestinationEntity
import com.example.data.database.entity.BackupJobEntity
import com.example.data.database.entity.CloudRecordEntity
import com.example.data.database.entity.FolderRuleEntity
import com.example.data.database.entity.LocalMediaEntity
import com.example.data.database.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY dateAdded DESC")
    fun getAllMediaFlow(): Flow<List<LocalMediaEntity>>

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavoritesFlow(): Flow<List<LocalMediaEntity>>

    @Query("SELECT * FROM media_items WHERE folderName = :folderName ORDER BY dateAdded DESC")
    fun getByFolderFlow(folderName: String): Flow<List<LocalMediaEntity>>

    @Query("SELECT * FROM media_items WHERE isVideo = 1 ORDER BY dateAdded DESC")
    fun getVideosFlow(): Flow<List<LocalMediaEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LocalMediaEntity?

    @Query("SELECT * FROM media_items WHERE uriString = :uriString LIMIT 1")
    suspend fun getByUri(uriString: String): LocalMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LocalMediaEntity>)

    @Query("UPDATE media_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM media_items")
    fun getMediaCountFlow(): Flow<Int>

    @Delete
    suspend fun delete(media: LocalMediaEntity)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BackupJobDao {
    @Query("SELECT * FROM backup_jobs ORDER BY createdAt DESC")
    fun getAllJobsFlow(): Flow<List<BackupJobEntity>>

    @Query("SELECT * FROM backup_jobs WHERE status IN ('QUEUED', 'UPLOADING') ORDER BY createdAt ASC")
    suspend fun getPendingJobs(): List<BackupJobEntity>

    @Query("SELECT * FROM backup_jobs WHERE status = 'FAILED' ORDER BY updatedAt DESC")
    suspend fun getFailedJobs(): List<BackupJobEntity>

    @Query("SELECT * FROM backup_jobs WHERE uriString = :uriString LIMIT 1")
    suspend fun getJobByUri(uriString: String): BackupJobEntity?

    @Query("SELECT * FROM backup_jobs WHERE uriString = :uriString AND channelId = :channelId LIMIT 1")
    suspend fun getJob(uriString: String, channelId: String): BackupJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(job: BackupJobEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(jobs: List<BackupJobEntity>)

    @Update
    suspend fun update(job: BackupJobEntity)

    @Query("UPDATE backup_jobs SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE backup_jobs SET progressPercent = :progress, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE backup_jobs SET status = :status, lastError = :error, retryCount = retryCount + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markFailed(id: Long, status: String, error: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE backup_jobs SET status = 'QUEUED', retryCount = 0, lastError = null WHERE status = 'FAILED'")
    suspend fun retryAllFailed()

    @Query("UPDATE backup_jobs SET status = 'PAUSED' WHERE status = 'QUEUED' OR status = 'UPLOADING'")
    suspend fun pauseAll()

    @Query("UPDATE backup_jobs SET status = 'QUEUED' WHERE status = 'PAUSED'")
    suspend fun resumeAll()

    @Delete
    suspend fun delete(job: BackupJobEntity)

    @Query("DELETE FROM backup_jobs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM backup_jobs WHERE status = 'BACKED_UP'")
    fun getCompletedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM backup_jobs WHERE status IN ('QUEUED', 'UPLOADING')")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM backup_jobs WHERE status = 'FAILED'")
    fun getFailedCountFlow(): Flow<Int>
}

@Dao
interface CloudRecordDao {
    @Query("SELECT * FROM cloud_records ORDER BY uploadTimestamp DESC")
    fun getAllRecordsFlow(): Flow<List<CloudRecordEntity>>

    @Query("SELECT * FROM cloud_records ORDER BY uploadTimestamp DESC")
    suspend fun getAllRecords(): List<CloudRecordEntity>

    @Query("SELECT * FROM cloud_records WHERE channelId = :channelId ORDER BY uploadTimestamp DESC")
    suspend fun getRecordsForChannel(channelId: String): List<CloudRecordEntity>

    @Query("SELECT * FROM cloud_records WHERE sha256 = :sha256 LIMIT 1")
    suspend fun getBySha256(sha256: String): CloudRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CloudRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<CloudRecordEntity>)

    @Query("DELETE FROM cloud_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM cloud_records")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT COALESCE(SUM(size), 0) FROM cloud_records")
    fun getTotalBytesFlow(): Flow<Long>
}

@Dao
interface DestinationDao {
    @Query("SELECT * FROM backup_destinations")
    fun getAllDestinationsFlow(): Flow<List<BackupDestinationEntity>>

    @Query("SELECT * FROM backup_destinations WHERE isEnabled = 1")
    suspend fun getEnabledDestinations(): List<BackupDestinationEntity>

    @Query("SELECT * FROM backup_destinations WHERE channelId = :channelId LIMIT 1")
    suspend fun getById(channelId: String): BackupDestinationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(destination: BackupDestinationEntity)

    @Delete
    suspend fun delete(destination: BackupDestinationEntity)

    @Query("UPDATE backup_destinations SET lastBackupAt = :timestamp, lastError = null WHERE channelId = :channelId")
    suspend fun updateLastBackup(channelId: String, timestamp: Long)

    @Query("UPDATE backup_destinations SET lastError = :error WHERE channelId = :channelId")
    suspend fun updateError(channelId: String, error: String)
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY createdAt DESC")
    fun getAllVaultItemsFlow(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): VaultItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VaultItemEntity): Long

    @Delete
    suspend fun delete(item: VaultItemEntity)

    @Query("UPDATE vault_items SET isBackedUp = :isBackedUp WHERE id = :id")
    suspend fun updateBackupStatus(id: Long, isBackedUp: Boolean)
}

@Dao
interface FolderRuleDao {
    @Query("SELECT * FROM folder_rules")
    fun getAllRulesFlow(): Flow<List<FolderRuleEntity>>

    @Query("SELECT * FROM folder_rules WHERE isEnabled = 1")
    suspend fun getEnabledRules(): List<FolderRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(rule: FolderRuleEntity)

    @Query("UPDATE folder_rules SET isEnabled = :isEnabled WHERE folderPath = :folderPath")
    suspend fun toggleRule(folderPath: String, isEnabled: Boolean)
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    fun getFlow(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: AppSettingEntity)
}
