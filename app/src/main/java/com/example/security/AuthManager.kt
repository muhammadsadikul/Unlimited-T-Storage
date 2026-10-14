package com.example.security

import android.content.Context
import android.util.Base64
import com.example.data.database.AppDatabase
import com.example.data.database.entity.AppSettingEntity

class AuthManager(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val settingDao = db.settingDao()

    companion object {
        private const val KEY_APP_PIN_HASH = "app_pin_hash"
        private const val KEY_APP_PIN_SALT = "app_pin_salt"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"

        private const val KEY_VAULT_PIN_HASH = "vault_pin_hash"
        private const val KEY_VAULT_PIN_SALT = "vault_pin_salt"
        private const val KEY_VAULT_SETUP = "vault_is_setup"
    }

    suspend fun isAppLockEnabled(): Boolean {
        return settingDao.get(KEY_APP_LOCK_ENABLED) == "true"
    }

    suspend fun setAppLock(enabled: Boolean, pin: String? = null) {
        settingDao.set(AppSettingEntity(KEY_APP_LOCK_ENABLED, enabled.toString()))
        if (enabled && !pin.isNullOrBlank()) {
            val salt = CryptoManager.generateSalt()
            val hash = CryptoManager.hashPassword(pin, salt)
            settingDao.set(AppSettingEntity(KEY_APP_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP)))
            settingDao.set(AppSettingEntity(KEY_APP_PIN_HASH, hash))
        }
    }

    suspend fun verifyAppPin(pin: String): Boolean {
        val saltBase64 = settingDao.get(KEY_APP_PIN_SALT) ?: return false
        val storedHash = settingDao.get(KEY_APP_PIN_HASH) ?: return false
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val computedHash = CryptoManager.hashPassword(pin, salt)
        return computedHash == storedHash
    }

    suspend fun isVaultSetup(): Boolean {
        return settingDao.get(KEY_VAULT_SETUP) == "true"
    }

    suspend fun setupVaultPin(pin: String) {
        val salt = CryptoManager.generateSalt()
        val hash = CryptoManager.hashPassword(pin, salt)
        settingDao.set(AppSettingEntity(KEY_VAULT_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP)))
        settingDao.set(AppSettingEntity(KEY_VAULT_PIN_HASH, hash))
        settingDao.set(AppSettingEntity(KEY_VAULT_SETUP, "true"))
    }

    suspend fun verifyVaultPin(pin: String): Boolean {
        val saltBase64 = settingDao.get(KEY_VAULT_PIN_SALT) ?: return false
        val storedHash = settingDao.get(KEY_VAULT_PIN_HASH) ?: return false
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val computedHash = CryptoManager.hashPassword(pin, salt)
        return computedHash == storedHash
    }
}
