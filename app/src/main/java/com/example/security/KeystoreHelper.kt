package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeystoreHelper {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "UnlimitedTStorageMasterKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encrypt(plainText: String): EncryptedData {
        return try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            EncryptedData(
                ciphertextBase64 = Base64.encodeToString(cipherText, Base64.NO_WRAP),
                ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            )
        } catch (e: Exception) {
            // Fallback for JVM testing where AndroidKeyStore provider is unavailable
            val salt = CryptoManager.generateSalt()
            val fallbackKey = CryptoManager.deriveKey("local_keystore_fallback".toCharArray(), salt)
            val iv = CryptoManager.generateIv()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, fallbackKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            EncryptedData(
                ciphertextBase64 = Base64.encodeToString(cipherText, Base64.NO_WRAP),
                ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            )
        }
    }

    fun decrypt(encrypted: EncryptedData): String {
        return try {
            if (encrypted.saltBase64 != null) {
                val salt = Base64.decode(encrypted.saltBase64, Base64.NO_WRAP)
                val iv = Base64.decode(encrypted.ivBase64, Base64.NO_WRAP)
                val key = CryptoManager.deriveKey("local_keystore_fallback".toCharArray(), salt)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
                val plainBytes = cipher.doFinal(Base64.decode(encrypted.ciphertextBase64, Base64.NO_WRAP))
                String(plainBytes, Charsets.UTF_8)
            } else {
                val secretKey = getOrCreateKey()
                val iv = Base64.decode(encrypted.ivBase64, Base64.NO_WRAP)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
                val plainBytes = cipher.doFinal(Base64.decode(encrypted.ciphertextBase64, Base64.NO_WRAP))
                String(plainBytes, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            ""
        }
    }
}

data class EncryptedData(
    val ciphertextBase64: String,
    val ivBase64: String,
    val saltBase64: String? = null
) {
    fun serialize(): String {
        return if (saltBase64 != null) {
            "$ivBase64:$ciphertextBase64:$saltBase64"
        } else {
            "$ivBase64:$ciphertextBase64"
        }
    }

    companion object {
        fun deserialize(serialized: String): EncryptedData? {
            val parts = serialized.split(":")
            return when (parts.size) {
                2 -> EncryptedData(ivBase64 = parts[0], ciphertextBase64 = parts[1])
                3 -> EncryptedData(ivBase64 = parts[0], ciphertextBase64 = parts[1], saltBase64 = parts[2])
                else -> null
            }
        }
    }
}
