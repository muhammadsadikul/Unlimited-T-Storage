package com.example

import com.example.data.model.CloudManifest
import com.example.data.model.ManifestEntry
import com.example.security.CryptoManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupAndCryptoUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSha256ChecksumVerification() {
        val testFile = tempFolder.newFile("sample.txt")
        testFile.writeText("Hello Telegram Cloud Gallery!")

        val hash1 = CryptoManager.calculateSha256(testFile)
        val hash2 = CryptoManager.calculateSha256(testFile)

        assertEquals("Checksums of identical files must match", hash1, hash2)
        assertEquals("SHA-256 hex string should be 64 characters long", 64, hash1.length)

        testFile.appendText(" modified")
        val hash3 = CryptoManager.calculateSha256(testFile)
        assertNotEquals("Modified file must have a different checksum", hash1, hash3)
    }

    @Test
    fun testAesGcmEncryptionAndDecryption() {
        val originalFile = tempFolder.newFile("vault_original.png")
        originalFile.writeText("SECRET_IMAGE_PAYLOAD_1234567890")

        val encryptedFile = tempFolder.newFile("vault_encrypted.enc")
        val decryptedFile = tempFolder.newFile("vault_decrypted.png")

        val password = "SuperSecretVaultPassword!#9"
        val meta = CryptoManager.encryptFile(originalFile, encryptedFile, password)

        assertTrue("Encrypted file should exist and have data", encryptedFile.exists() && encryptedFile.length() > 0)
        assertNotEquals("Encrypted bytes must not match original plain text", originalFile.readText(), encryptedFile.readText())

        // Decrypt with correct password
        val decryptSuccess = CryptoManager.decryptFile(
            inputFile = encryptedFile,
            outputFile = decryptedFile,
            password = password,
            saltBase64 = meta.saltBase64,
            ivBase64 = meta.ivBase64
        )

        assertTrue("Decryption with correct password should succeed", decryptSuccess)
        assertEquals("Decrypted content must exactly match original", originalFile.readText(), decryptedFile.readText())

        // Decrypt with wrong password
        val wrongDecryptedFile = tempFolder.newFile("wrong_decrypted.png")
        val wrongDecryptSuccess = CryptoManager.decryptFile(
            inputFile = encryptedFile,
            outputFile = wrongDecryptedFile,
            password = "WrongPassword",
            saltBase64 = meta.saltBase64,
            ivBase64 = meta.ivBase64
        )

        assertFalse("Decryption with wrong password must fail", wrongDecryptSuccess)
    }

    @Test
    fun testPasswordHashing() {
        val salt = CryptoManager.generateSalt()
        val hash1 = CryptoManager.hashPassword("1234", salt)
        val hash2 = CryptoManager.hashPassword("1234", salt)
        val hashWrong = CryptoManager.hashPassword("5678", salt)

        assertEquals("Identical password and salt must produce identical hash", hash1, hash2)
        assertNotEquals("Different passwords must produce different hashes", hash1, hashWrong)
    }

    @Test
    fun testManifestSerializationAndDeserialization() {
        val entry1 = ManifestEntry(
            id = "1",
            fileName = "vacation.jpg",
            relativePath = "DCIM/Camera/vacation.jpg",
            mimeType = "image/jpeg",
            size = 1048576L,
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            telegramMessageId = 42L,
            telegramFileId = "AQADBAADr6cxG123",
            isVaultEncrypted = false,
            uploadedAt = 1697274000000L
        )

        val entry2 = ManifestEntry(
            id = "2",
            fileName = "tax_2025.pdf.enc",
            relativePath = "vault/tax_2025.pdf",
            mimeType = "application/octet-stream",
            size = 524288L,
            sha256 = "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0",
            telegramMessageId = 43L,
            telegramFileId = "AQADBAADr6cxG456",
            isVaultEncrypted = true,
            ivBase64 = "YWJjZGVmZ2hpamts",
            saltBase64 = "bW5vcHFyc3R1dnd4",
            uploadedAt = 1697275000000L
        )

        val manifest = CloudManifest(
            version = 1,
            appVersion = "1.0",
            channelId = "-1001234567890",
            totalFiles = 2,
            totalBytes = 1048576L + 524288L,
            entries = listOf(entry1, entry2)
        )

        // Verify JSON representation
        assertEquals(2, manifest.entries.size)
        assertEquals("vacation.jpg", manifest.entries[0].fileName)
        assertTrue(manifest.entries[1].isVaultEncrypted)
        assertEquals("YWJjZGVmZ2hpamts", manifest.entries[1].ivBase64)
    }
}
