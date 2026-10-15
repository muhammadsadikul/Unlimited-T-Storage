package com.example.security

import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val SALT_LENGTH_BYTE = 16
    private const val ITERATIONS = 15000
    private const val KEY_LENGTH = 256

    private val secureRandom = SecureRandom()

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTE)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun generateIv(): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTE)
        secureRandom.nextBytes(iv)
        return iv
    }

    fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_LENGTH)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    fun hashPassword(password: String, salt: ByteArray): String {
        val key = deriveKey(password.toCharArray(), salt)
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }

    fun encryptText(plainText: String, passphrase: String): EncryptedPayload {
        val salt = generateSalt()
        val iv = generateIv()
        val key = deriveKey(passphrase.toCharArray(), salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        )
    }

    fun decryptText(payload: EncryptedPayload, passphrase: String): String? {
        return try {
            val salt = Base64.decode(payload.saltBase64, Base64.NO_WRAP)
            val iv = Base64.decode(payload.ivBase64, Base64.NO_WRAP)
            val key = deriveKey(passphrase.toCharArray(), salt)

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
            val cipherBytes = Base64.decode(payload.ciphertextBase64, Base64.NO_WRAP)
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun encryptFile(inputFile: File, outputFile: File, password: String): EncryptionMeta {
        val salt = generateSalt()
        val iv = generateIv()
        val key = deriveKey(password.toCharArray(), salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        FileInputStream(inputFile).use { fis ->
            FileOutputStream(outputFile).use { fos ->
                CipherOutputStream(fos, cipher).use { cos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        cos.write(buffer, 0, bytesRead)
                    }
                }
            }
        }

        return EncryptionMeta(
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decryptFile(inputFile: File, outputFile: File, password: String, saltBase64: String, ivBase64: String): Boolean {
        return try {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val key = deriveKey(password.toCharArray(), salt)

            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            FileInputStream(inputFile).use { fis ->
                CipherInputStream(fis, cipher).use { cis ->
                    FileOutputStream(outputFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (cis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            outputFile.delete()
            false
        }
    }

    fun calculateSha256(file: File): String {
        return FileInputStream(file).use { calculateSha256(it) }
    }

    fun calculateSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        val bytes = digest.digest()
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

data class EncryptionMeta(
    val saltBase64: String,
    val ivBase64: String
)

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String,
    val saltBase64: String
)
