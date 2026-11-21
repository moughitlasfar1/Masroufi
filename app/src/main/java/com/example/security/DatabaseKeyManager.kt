package com.example.security

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.SecureRandom

sealed interface DatabaseKeyResolution {
    /**
     * Valid 32-byte (256-bit) database passphrase ready for SQLCipher.
     * Caller (or `SupportOpenHelperFactory(..., clearPassphrase = true)`) must zero
     * [passphrase] after opening the database.
     */
    data class Ready(val passphrase: ByteArray) : DatabaseKeyResolution {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Ready) return false
            return passphrase.contentEquals(other.passphrase)
        }

        override fun hashCode(): Int = passphrase.contentHashCode()
    }

    /**
     * An encrypted database file already exists on disk, but the wrapped key in [SecureStore]
     * is missing, corrupted, or undecryptable (e.g., AndroidKeyStore invalidated after backup/reset).
     * Never silently destroy user data — show a recovery dialog (Restore Backup or Reset DB).
     */
    data class KeyCorruptedOrMissing(val reason: String) : DatabaseKeyResolution

    /**
     * Failed to persist a newly generated key in [SecureStore] (fail-closed on device).
     */
    data class KeyStorageUnavailable(val reason: String) : DatabaseKeyResolution
}

/**
 * Manages the 256-bit SQLCipher passphrase for Masroufi's encrypted Room database.
 *
 * Design guarantees:
 * - Reuses the existing [SecureStore] (AndroidKeyStore AES-256-GCM with per-key AAD binding)
 *   rather than creating a duplicate Keystore wrapping key.
 * - Generates a cryptographically random 32-byte passphrase via [SecureRandom] on first launch.
 * - Never logs, hardcodes, or persists plain-text key material in SharedPreferences.
 * - Zeroes intermediate byte arrays in `finally` blocks after encoding/decoding.
 * - Detects corrupted/missing keys when an existing database file is present so the app
 *   can present a safe recovery flow instead of crashing.
 */
object DatabaseKeyManager {

    const val ENCRYPTED_DB_NAME = "masroufi_encrypted.db"
    internal const val SECURE_STORE_DB_KEY = "masroufi_sqlcipher_db_key_v1"
    private const val PASSPHRASE_BYTE_LENGTH = 32

    private val lock = Any()
    private val secureRandom = SecureRandom()

    fun databaseFile(context: Context, dbName: String = ENCRYPTED_DB_NAME): File {
        return context.applicationContext.getDatabasePath(dbName)
    }

    fun hasExistingDatabaseFile(context: Context, dbName: String = ENCRYPTED_DB_NAME): Boolean {
        val file = databaseFile(context, dbName)
        return file.exists() && file.length() > 0L
    }

    /**
     * Resolves the 32-byte SQLCipher passphrase for [dbName].
     *
     * - If a valid wrapped key exists in [SecureStore], decrypts and returns a fresh 32-byte copy.
     * - If the DB file already exists on disk but the key is missing or undecryptable, returns
     *   [DatabaseKeyResolution.KeyCorruptedOrMissing] without touching the DB file.
     * - If no DB file exists yet (fresh install), generates a new 32-byte random key, wraps it
     *   in [SecureStore], and returns it.
     */
    fun resolvePassphrase(
        context: Context,
        dbName: String = ENCRYPTED_DB_NAME
    ): DatabaseKeyResolution = synchronized(lock) {
        val appCtx = context.applicationContext
        val dbExists = hasExistingDatabaseFile(appCtx, dbName)
        val hasStoredEntry = SecureStore.contains(appCtx, SECURE_STORE_DB_KEY)

        val storedB64 = SecureStore.getString(appCtx, SECURE_STORE_DB_KEY)
        if (storedB64 != null) {
            val decoded = runCatching { Base64.decode(storedB64, Base64.NO_WRAP) }.getOrNull()
            if (decoded != null && decoded.size == PASSPHRASE_BYTE_LENGTH) {
                return DatabaseKeyResolution.Ready(decoded)
            }
            decoded?.fill(0)
            if (dbExists) {
                return DatabaseKeyResolution.KeyCorruptedOrMissing("stored_key_malformed")
            }
            // No DB file exists yet; safe to replace malformed key entry
            SecureStore.remove(appCtx, SECURE_STORE_DB_KEY)
        } else if (dbExists) {
            val reason = if (hasStoredEntry) "keystore_decrypt_failed" else "key_entry_missing"
            return DatabaseKeyResolution.KeyCorruptedOrMissing(reason)
        }

        val freshKey = ByteArray(PASSPHRASE_BYTE_LENGTH)
        var encoded: String? = null
        return try {
            secureRandom.nextBytes(freshKey)
            encoded = Base64.encodeToString(freshKey, Base64.NO_WRAP)
            val saved = SecureStore.putString(appCtx, SECURE_STORE_DB_KEY, encoded)
            if (!saved) {
                freshKey.fill(0)
                DatabaseKeyResolution.KeyStorageUnavailable("secure_store_write_failed")
            } else {
                DatabaseKeyResolution.Ready(freshKey.copyOf())
            }
        } finally {
            freshKey.fill(0)
            encoded = null
        }
    }

    /**
     * Explicit recovery action invoked only after the user confirms resetting the local
     * encrypted database in the Keystore recovery dialog.
     * Deletes the encrypted DB file (plus `-wal`, `-shm`, `-journal`), removes the old
     * key entry from [SecureStore], and generates a fresh 256-bit passphrase.
     */
    fun resetDatabaseAndRegenerateKey(
        context: Context,
        dbName: String = ENCRYPTED_DB_NAME
    ): DatabaseKeyResolution = synchronized(lock) {
        val appCtx = context.applicationContext
        deleteDatabaseArtifacts(appCtx, dbName)
        SecureStore.remove(appCtx, SECURE_STORE_DB_KEY)
        resolvePassphrase(appCtx, dbName)
    }

    fun deleteDatabaseArtifacts(context: Context, dbName: String = ENCRYPTED_DB_NAME) {
        val dbFile = databaseFile(context, dbName)
        val siblings = listOf(
            dbFile,
            File("${dbFile.path}-wal"),
            File("${dbFile.path}-shm"),
            File("${dbFile.path}-journal")
        )
        siblings.forEach { file ->
            if (file.exists()) {
                runCatching { file.delete() }
            }
        }
    }

    private val SQLITE_PLAINTEXT_MAGIC = "SQLite format 3".toByteArray(Charsets.US_ASCII)
    private const val SQLITE_HEADER_PROBE_BYTES = 16

    /**
     * Reads the first 16 bytes of [file] directly from disk and verifies that it does NOT
     * begin with the unencrypted SQLite header ("SQLite format 3").
     *
     * A real SQLCipher database encrypts page 1 (starting with a random 16-byte salt), so its
     * on-disk header will never match the plaintext SQLite magic string.
     */
    fun verifyDatabaseFileEncrypted(file: File): DbEncryptionHeaderCheck {
        if (!file.exists() || file.length() < SQLITE_HEADER_PROBE_BYTES) {
            return DbEncryptionHeaderCheck.FileMissingOrTooShort
        }
        return runCatching {
            val header = ByteArray(SQLITE_HEADER_PROBE_BYTES)
            val bytesRead = file.inputStream().use { it.read(header) }
            if (bytesRead < SQLITE_PLAINTEXT_MAGIC.size) {
                return@runCatching DbEncryptionHeaderCheck.FileMissingOrTooShort
            }
            val matchesPlaintext = SQLITE_PLAINTEXT_MAGIC.indices.all { idx ->
                header[idx] == SQLITE_PLAINTEXT_MAGIC[idx]
            }
            if (matchesPlaintext) {
                DbEncryptionHeaderCheck.PlaintextSqliteDetected
            } else {
                DbEncryptionHeaderCheck.VerifiedEncrypted
            }
        }.getOrElse {
            DbEncryptionHeaderCheck.ReadError
        }
    }
}

sealed interface DbEncryptionHeaderCheck {
    data object VerifiedEncrypted : DbEncryptionHeaderCheck
    data object PlaintextSqliteDetected : DbEncryptionHeaderCheck
    data object FileMissingOrTooShort : DbEncryptionHeaderCheck
    data object ReadError : DbEncryptionHeaderCheck
}
