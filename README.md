# Unlimited T Storage

**Unlimited T Storage** is a modern native Android application inspired by **Google Photos** and **Google Drive**, delivering an offline-first gallery, file manager, and **automatic zero-cost cloud backup** using your private Telegram channels as remote storage.

---

## 🌟 Key Features

1. **Google Photos-Inspired Gallery**
   - **Date-Grouped Timeline:** Seamless lazy-loaded grid of images and videos.
   - **Video Detection & Duration:** Real-time video format detection with dedicated playback previews.
   - **Full-Screen Media Viewer:** Photo pinch-to-zoom, detailed metadata sheet (resolution, file size, MIME type, storage path), and native Android sharing.
   - **Special Collections:** Filter by Favorites, Videos, Screenshots, or device folders with per-folder synchronization toggles.

2. **Google Drive-Style File Manager**
   - **Directory Browser:** Hierarchical folder navigation with breadcrumb navigation.
   - **Filter Categories:** Quick category tabs for *All, Folders, Documents, Audio, Archives, Images, Videos*.
   - **File Operations:** Real-time filename search, batch selection, Android `FileProvider` sharing, and immediate cloud backup.

3. **Telegram Cloud Engine & Persistent Queue**
   - **Official Telegram Bot API:** Authenticated streaming multipart uploads using OkHttp with progress tracking.
   - **Telegram Upload Limits:** Automatic checks to enforce Telegram's standard 50 MB bot upload limit with clear error reporting.
   - **Deduplication:** Automatic **SHA-256 checksum** calculation before upload; identical files already present in the destination channel are skipped.
   - **Rate Limiting & Backoff:** HTTP 429 backoff handling reading Telegram's `retry_after` response parameters.
   - **Real-Time Progress:** Live percentage and bytes uploaded tracking reflected on both the dashboard and top status bar.

4. **Background Backups via WorkManager**
   - **WorkManager CoroutineWorker:** Persistent background uploads that survive application closure or system reboots.
   - **Constraints:** Configurable Wi-Fi only and Charging-only background sync toggles.

5. **Encrypted Hidden Vault with Biometric Unlock**
   - **Client-Side AES-256-GCM:** Authenticated encryption using PBKDF2WithHmacSHA256 (10,000 iterations, 256-bit key) with random 16-byte salts and unique 12-byte IVs.
   - **Biometric Unlock:** Unlock with fingerprint or facial recognition via AndroidX `BiometricPrompt`, backed by master PIN fallback.
   - **Zero-Knowledge Cloud Backup:** Vault files are encrypted **before** local storage and **before** uploading to Telegram; files are never exposed to public caches.

6. **Disaster Recovery & Versioned Manifest**
   - Automatically maintains a versioned `manifest_v1.json` cloud index on your private Telegram channel.
   - Allows full library reconstruction on a **new phone** without relying on the old device's local database.
   - Downloads original files with automatic **SHA-256 integrity verification**.

---

## 🏗️ Architecture & Technology Stack

- **Platform:** Native Android (Kotlin 2.2+, Jetpack Compose, Material 3)
- **Minimum SDK:** API 24 (Android 7.0)
- **Target / Compile SDK:** API 36
- **Architecture:** Clean MVVM Architecture with Room DAOs and Repositories
- **Database:** Room 2.7.0 (with KSP code generator)
- **Background Jobs:** WorkManager 2.9.1 (`CoroutineWorker`)
- **Biometrics:** AndroidX Biometric 1.2.0
- **Networking:** OkHttp 4.10 + Retrofit 2.12
- **Image Loading:** Coil 2.7.0
- **Security:** Java Cryptography Architecture (`Cipher` AES-256-GCM, PBKDF2WithHmacSHA256, MessageDigest SHA-256)

---

## 🔒 Security & Privacy Model

- **Zero Plaintext Tokens:** Bot tokens and user passwords are stored securely in local app storage and never committed to version control.
- **Client-Side Encryption:** Vault items are encrypted on-device before transmission. Neither Telegram nor network intermediaries can view private vault contents.
- **Recovery Requirement:** Because encryption keys are derived from your master vault passphrase, losing your vault password means encrypted files cannot be decrypted. Memorize or preserve your vault credentials securely.

---

## 🚀 Setup & Telegram Bot Configuration

1. **Create a Telegram Bot:**
   - In Telegram, message [@BotFather](https://t.me/BotFather).
   - Send `/newbot` and follow instructions.
   - Copy the generated HTTP API Token (e.g., `123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ`).

2. **Create a Private Channel:**
   - In Telegram, create a new **Private Channel** (e.g., `My Private Cloud Backup`).
   - Add your bot as an **Administrator** with **Post Messages** permission.

3. **Find your Channel Chat ID:**
   - Forward a post from your channel to [@userinfobot](https://t.me/userinfobot) or [@raw_data_bot](https://t.me/raw_data_bot) to retrieve the channel ID (usually starts with `-100`).

4. **Connect in App:**
   - Open **Unlimited T Storage** -> Navigate to **Backup** -> Tap **Manage Channels**.
   - Enter your Channel ID and Bot Token, then tap **Test Real Connection**.

---

## 📱 Disaster Recovery on a New Device

If your original device is lost, stolen, or damaged:
1. Install **Unlimited T Storage** on your new phone.
2. In **Settings -> Cloud Restore & Disaster Recovery**, enter your Bot Token and Channel ID.
3. Download the pinned `manifest_v1.json` from your Telegram channel and paste its contents into the manifest field.
4. Tap **Import Manifest & Reconstruct Index**.
5. Select files and tap **Restore Selected** or **Restore All**. Files are saved to your `Downloads/TelegramRestore` directory with SHA-256 verified integrity!

---

## 🛠️ Build & Development Commands

### Compile Debug APK
```bash
gradle assembleDebug
# The resulting APK is located at:
# app/build/outputs/apk/debug/app-debug.apk
```

### Run Local Unit Tests
```bash
gradle :app:testDebugUnitTest
```

### Build Signed Release APK
To create a release build, configure your keystore environment variables:
```bash
export KEYSTORE_PATH="/path/to/my-upload-key.jks"
export STORE_PASSWORD="myStorePassword"
export KEY_PASSWORD="myKeyPassword"
gradle assembleRelease
```
