# Masroufi — Security Architecture

## 1. Local Database Encryption at Rest (SQLCipher + AndroidKeyStore)

- **Cipher Engine**: `net.zetetic:sqlcipher-android:4.20.0@aar` paired with `androidx.sqlite:sqlite:2.7.0` via Room's `SupportOpenHelperFactory`.
- **Native Initialization**: `MasroufiApplication.onCreate()` calls `System.loadLibrary("sqlcipher")` once per process prior to any database access.
- **Database File**: `masroufi_encrypted.db` (SQLCipher AES-256 encrypted SQLite database; unreadable by standard SQLite tools).

## 2. Key Lifecycle (`DatabaseKeyManager` + `SecureStore`)

- **Single Wrapping Key**: `DatabaseKeyManager` reuses Masroufi's existing `SecureStore` (`AndroidKeyStore` key alias `masroufi_secure_store_aes256_v1`, AES-256-GCM, 96-bit random IV per write, 128-bit auth tag, preference key bound as GCM AAD). No duplicate Keystore wrapping key is created.
- **Passphrase Generation**: On first launch (when no encrypted DB file exists on disk), `DatabaseKeyManager` generates a 32-byte (256-bit) cryptographically random passphrase using `java.security.SecureRandom`, encodes it, and persists only the AES-GCM ciphertext + IV inside `SecureStore` under key `masroufi_sqlcipher_db_key_v1`.
- **Memory Hygiene**:
  - Intermediate passphrase `ByteArray` buffers are explicitly zeroed (`fill(0)`) in `finally` blocks.
  - `SupportOpenHelperFactory(passphrase, null, clearPassphrase = true)` clears the passphrase array internally immediately after keying SQLCipher.
  - No key material is ever hardcoded, logged, or stored in plaintext SharedPreferences.
- **On-Disk Encryption Header Verification**: After opening `masroufi_encrypted.db` with SQLCipher, `DatabaseKeyManager.verifyDatabaseFileEncrypted()` reads the first 16 bytes directly from disk and asserts they do **not** match the plaintext SQLite magic header (`"SQLite format 3"`). If a plaintext header is ever detected on a production DB, `MasroufiDatabaseProvider` closes the connection and fails closed to `RecoveryRequired("plaintext_header_detected")`.
- **Corruption & Keystore Invalidation Recovery**:
  - If `masroufi_encrypted.db` already exists on disk while the `SecureStore` key is missing, malformed, or undecryptable (for example, after an OS Keystore reset or unsupported device restore), `DatabaseKeyManager.resolvePassphrase()` returns `DatabaseKeyResolution.KeyCorruptedOrMissing` without modifying or deleting the DB file.
  - `MasroufiDatabaseProvider` publishes `DatabaseHealthState.RecoveryRequired`, causing `MainActivity` to render `DatabaseRecoveryDialog` (4 languages) with two explicit choices:
    1. **Restore Encrypted Backup** (opens Masroufi's AES-256-GCM backup restore flow).
    2. **Reset Local Database** (explicitly deletes DB + `-wal`/`-shm`/`-journal` and regenerates a fresh 256-bit key). Once Firestore → Room import has completed (`isRoomPrimaryAfterImport = true`), the dialog prominently warns that Room is now the only local copy and requires a second-step explicit checkbox confirmation before wiping.

## 3. Backup & Data Extraction Hardening

- `AndroidManifest.xml` enforces `android:allowBackup="false"`, `android:usesCleartextTraffic="false"`, `android:dataExtractionRules="@xml/data_extraction_rules"`, and `android:fullBackupContent="@xml/backup_rules"`.
- Both `res/xml/data_extraction_rules.xml` (Android 12+) and `res/xml/backup_rules.xml` (Android 11 and below) exclude all domains (`database`, `sharedpref`, `file`, `external`, `root`) from Google cloud backup and device-to-device transfers.

## 4. App-Level Privacy Controls

- **Screen Capture Protection (`FLAG_SECURE`)**: Optional user setting persisted in `AppSecurityManager` and applied live to `MainActivity`'s window.
- **App Lock**: Optional 6–8 digit PIN hashed with PBKDF2-HMAC-SHA256 (`600,000` iterations, progressive lockout up to 1 hour) plus `BiometricPrompt` hardware unlock.
- **Amount Privacy Mask**: One-tap header toggle hides monetary figures (`••••`) across screens and home widgets.
- **Encrypted Backups**: User-initiated JSON exports require a 10+ character password and use AES-256-GCM with AAD binding.

## 5. Logging & R8 Minification Hygiene

- Source code audit confirmed zero financial amounts, transaction notes, account names, or key material are emitted to Logcat.
- `app/proguard-rules.pro` strips all `android.util.Log` (`v`, `d`, `i`, `w`, `e`) calls in release builds via `-assumenosideeffects`.
- Minimal SQLCipher R8 keep rules preserve `net.zetetic.database.**` and JNI `native <methods>` without broad `SQLiteOpenHelper` keep rules.
