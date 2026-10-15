package com.example.security

import android.content.Context
import android.util.Base64
import com.example.data.database.AppDatabase
import com.example.data.database.entity.AppSettingEntity

sealed class AuthResult {
    object Success : AuthResult()
    object InvalidPassword : AuthResult()
    data class LockedOut(val remainingSeconds: Long) : AuthResult()
}

class AuthManager(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val settingDao = db.settingDao()

    companion object {
        private const val KEY_MASTER_PASSWORD_HASH = "master_pwd_hash"
        private const val KEY_MASTER_PASSWORD_SALT = "master_pwd_salt"
        private const val KEY_MASTER_SETUP = "master_is_setup"

        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_APP_PIN_HASH = "app_pin_hash"
        private const val KEY_APP_PIN_SALT = "app_pin_salt"

        private const val KEY_VAULT_PIN_HASH = "vault_pin_hash"
        private const val KEY_VAULT_PIN_SALT = "vault_pin_salt"
        private const val KEY_VAULT_SETUP = "vault_is_setup"

        private const val KEY_FAILED_ATTEMPTS = "auth_failed_attempts"
        private const val KEY_LOCKOUT_TIMESTAMP = "auth_lockout_until"

        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds lockout

        // In-memory active session secret (cleared when app terminates)
        @Volatile
        var activeMasterPasswordSession: String? = null
            private set

        fun clearSession() {
            activeMasterPasswordSession = null
        }
    }

    suspend fun isMasterPasswordSetup(): Boolean {
        return settingDao.get(KEY_MASTER_SETUP) == "true"
    }

    suspend fun isAppLockEnabled(): Boolean {
        val appLockSetting = settingDao.get(KEY_APP_LOCK_ENABLED)
        return if (appLockSetting != null) {
            appLockSetting == "true"
        } else {
            isMasterPasswordSetup()
        }
    }

    suspend fun setAppLock(enabled: Boolean, pin: String? = null) {
        settingDao.set(AppSettingEntity(KEY_APP_LOCK_ENABLED, if (enabled) "true" else "false"))
        if (pin != null && pin.isNotBlank()) {
            val salt = CryptoManager.generateSalt()
            val hash = CryptoManager.hashPassword(pin, salt)
            settingDao.set(AppSettingEntity(KEY_APP_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP)))
            settingDao.set(AppSettingEntity(KEY_APP_PIN_HASH, hash))
            // If master password is not yet configured, use this as master password
            if (!isMasterPasswordSetup()) {
                setupMasterPassword(pin)
            }
        }
    }

    suspend fun setupMasterPassword(password: String) {
        val salt = CryptoManager.generateSalt()
        val hash = CryptoManager.hashPassword(password, salt)
        settingDao.set(AppSettingEntity(KEY_MASTER_PASSWORD_SALT, Base64.encodeToString(salt, Base64.NO_WRAP)))
        settingDao.set(AppSettingEntity(KEY_MASTER_PASSWORD_HASH, hash))
        settingDao.set(AppSettingEntity(KEY_MASTER_SETUP, "true"))
        settingDao.set(AppSettingEntity(KEY_APP_LOCK_ENABLED, "true"))
        settingDao.set(AppSettingEntity(KEY_FAILED_ATTEMPTS, "0"))
        settingDao.set(AppSettingEntity(KEY_LOCKOUT_TIMESTAMP, "0"))
        activeMasterPasswordSession = password
    }

    suspend fun verifyMasterPassword(password: String): AuthResult {
        val now = System.currentTimeMillis()
        val lockoutUntil = settingDao.get(KEY_LOCKOUT_TIMESTAMP)?.toLongOrNull() ?: 0L

        if (now < lockoutUntil) {
            val remaining = (lockoutUntil - now) / 1000L
            return AuthResult.LockedOut(if (remaining > 0) remaining else 1)
        }

        val saltBase64 = settingDao.get(KEY_MASTER_PASSWORD_SALT) ?: return AuthResult.InvalidPassword
        val storedHash = settingDao.get(KEY_MASTER_PASSWORD_HASH) ?: return AuthResult.InvalidPassword

        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val computedHash = CryptoManager.hashPassword(password, salt)

        return if (computedHash == storedHash) {
            settingDao.set(AppSettingEntity(KEY_FAILED_ATTEMPTS, "0"))
            settingDao.set(AppSettingEntity(KEY_LOCKOUT_TIMESTAMP, "0"))
            activeMasterPasswordSession = password
            AuthResult.Success
        } else {
            val currentFailed = (settingDao.get(KEY_FAILED_ATTEMPTS)?.toIntOrNull() ?: 0) + 1
            settingDao.set(AppSettingEntity(KEY_FAILED_ATTEMPTS, currentFailed.toString()))

            if (currentFailed >= MAX_FAILED_ATTEMPTS) {
                val newLockout = now + LOCKOUT_DURATION_MS
                settingDao.set(AppSettingEntity(KEY_LOCKOUT_TIMESTAMP, newLockout.toString()))
                AuthResult.LockedOut(LOCKOUT_DURATION_MS / 1000L)
            } else {
                AuthResult.InvalidPassword
            }
        }
    }

    suspend fun verifyAppPin(pin: String): Boolean {
        val masterAuth = verifyMasterPassword(pin)
        if (masterAuth is AuthResult.Success) {
            return true
        }

        // Also check if custom App PIN is stored
        val pinSaltBase64 = settingDao.get(KEY_APP_PIN_SALT)
        val pinStoredHash = settingDao.get(KEY_APP_PIN_HASH)
        if (pinSaltBase64 != null && pinStoredHash != null) {
            val salt = Base64.decode(pinSaltBase64, Base64.NO_WRAP)
            val computedHash = CryptoManager.hashPassword(pin, salt)
            if (computedHash == pinStoredHash) {
                activeMasterPasswordSession = pin
                return true
            }
        }

        return false
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
        // Vault can be unlocked with Vault PIN or with the Master Password
        if (activeMasterPasswordSession != null && activeMasterPasswordSession == pin) {
            return true
        }

        val saltBase64 = settingDao.get(KEY_VAULT_PIN_SALT) ?: return false
        val storedHash = settingDao.get(KEY_VAULT_PIN_HASH) ?: return false
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val computedHash = CryptoManager.hashPassword(pin, salt)
        return computedHash == storedHash
    }
}
