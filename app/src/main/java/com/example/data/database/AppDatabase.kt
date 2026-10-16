package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.database.dao.BackupJobDao
import com.example.data.database.dao.CloudRecordDao
import com.example.data.database.dao.DestinationDao
import com.example.data.database.dao.FolderRuleDao
import com.example.data.database.dao.MediaDao
import com.example.data.database.dao.SettingDao
import com.example.data.database.dao.VaultDao
import com.example.data.database.entity.AppSettingEntity
import com.example.data.database.entity.BackupDestinationEntity
import com.example.data.database.entity.BackupJobEntity
import com.example.data.database.entity.CloudRecordEntity
import com.example.data.database.entity.FolderRuleEntity
import com.example.data.database.entity.LocalMediaEntity
import com.example.data.database.entity.VaultItemEntity

@Database(
    entities = [
        LocalMediaEntity::class,
        BackupJobEntity::class,
        CloudRecordEntity::class,
        BackupDestinationEntity::class,
        VaultItemEntity::class,
        FolderRuleEntity::class,
        AppSettingEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun backupJobDao(): BackupJobDao
    abstract fun cloudRecordDao(): CloudRecordDao
    abstract fun destinationDao(): DestinationDao
    abstract fun vaultDao(): VaultDao
    abstract fun folderRuleDao(): FolderRuleDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "telegram_gallery.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
