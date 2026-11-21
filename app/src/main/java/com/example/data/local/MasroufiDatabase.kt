package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.example.MasroufiApplication
import com.example.security.DatabaseKeyManager
import com.example.security.DatabaseKeyResolution
import com.example.security.DbEncryptionHeaderCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        UserProfileEntity::class,
        AccountEntity::class,
        TransactionEntity::class,
        EnvelopeEntity::class,
        FinancialItemEntity::class,
        SavedCreditLocalEntity::class,
        ImportMetadataEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class MasroufiDatabase : RoomDatabase() {
    abstract fun masroufiDao(): MasroufiDao
}

sealed interface DatabaseHealthState {
    data object Uninitialized : DatabaseHealthState
    data object Healthy : DatabaseHealthState
    data class RecoveryRequired(val reason: String) : DatabaseHealthState
}

/**
 * Thread-safe singleton provider for Masroufi's SQLCipher-encrypted Room database.
 *
 * Security & durability guarantees:
 * - Never calls `fallbackToDestructiveMigration()`.
 * - Calls [MasroufiApplication.ensureSqlCipherLoaded] prior to database construction.
 * - Uses [DatabaseKeyManager] (backed by [com.example.security.SecureStore]) and passes
 *   `clearPassphrase = true` to [SupportOpenHelperFactory] so SQLCipher clears key bytes
 *   from memory immediately after keying the connection.
 * - Never throws an unhandled crash if the Keystore key is missing/corrupted; instead
 *   publishes [DatabaseHealthState.RecoveryRequired] so the UI can show the recovery dialog.
 */
object MasroufiDatabaseProvider {

    @Volatile
    private var instance: MasroufiDatabase? = null

    private val lock = Any()

    private val _healthState = MutableStateFlow<DatabaseHealthState>(DatabaseHealthState.Uninitialized)
    val healthState: StateFlow<DatabaseHealthState> = _healthState.asStateFlow()

    private val _headerCheckState = MutableStateFlow<DbEncryptionHeaderCheck?>(null)
    val headerCheckState: StateFlow<DbEncryptionHeaderCheck?> = _headerCheckState.asStateFlow()

    fun getOpenInstanceOrNull(): MasroufiDatabase? = instance

    /**
     * Opens (or returns the cached) encrypted [MasroufiDatabase] instance.
     * Returns `null` and sets [healthState] to [DatabaseHealthState.RecoveryRequired] if
     * the database encryption key is corrupted/missing or opening fails.
     */
    fun getOrOpen(
        context: Context,
        dbName: String = DatabaseKeyManager.ENCRYPTED_DB_NAME,
        customFactory: SupportSQLiteOpenHelper.Factory? = null
    ): MasroufiDatabase? {
        instance?.let { return it }
        return synchronized(lock) {
            instance?.let { return it }
            val appCtx = context.applicationContext
            val keyResolution = DatabaseKeyManager.resolvePassphrase(appCtx, dbName)
            when (keyResolution) {
                is DatabaseKeyResolution.KeyCorruptedOrMissing -> {
                    _healthState.value = DatabaseHealthState.RecoveryRequired(keyResolution.reason)
                    null
                }
                is DatabaseKeyResolution.KeyStorageUnavailable -> {
                    _healthState.value = DatabaseHealthState.RecoveryRequired(keyResolution.reason)
                    null
                }
                is DatabaseKeyResolution.Ready -> {
                    openWithPassphrase(appCtx, dbName, keyResolution.passphrase, customFactory)
                }
            }
        }
    }

    /**
     * Explicitly wipes the unreadable encrypted DB file, regenerates a fresh 256-bit key,
     * and opens a clean encrypted database. Called only from the user-confirmed recovery dialog.
     */
    fun resetAndReopen(
        context: Context,
        dbName: String = DatabaseKeyManager.ENCRYPTED_DB_NAME,
        customFactory: SupportSQLiteOpenHelper.Factory? = null
    ): MasroufiDatabase? = synchronized(lock) {
        closeInternal()
        val appCtx = context.applicationContext
        when (val resolution = DatabaseKeyManager.resetDatabaseAndRegenerateKey(appCtx, dbName)) {
            is DatabaseKeyResolution.Ready -> {
                openWithPassphrase(appCtx, dbName, resolution.passphrase, customFactory)
            }
            is DatabaseKeyResolution.KeyCorruptedOrMissing -> {
                _healthState.value = DatabaseHealthState.RecoveryRequired(resolution.reason)
                null
            }
            is DatabaseKeyResolution.KeyStorageUnavailable -> {
                _healthState.value = DatabaseHealthState.RecoveryRequired(resolution.reason)
                null
            }
        }
    }

    private fun openWithPassphrase(
        appCtx: Context,
        dbName: String,
        passphrase: ByteArray,
        customFactory: SupportSQLiteOpenHelper.Factory?
    ): MasroufiDatabase? {
        return try {
            val builder = Room.databaseBuilder(
                appCtx,
                MasroufiDatabase::class.java,
                dbName
            )
            if (customFactory != null) {
                passphrase.fill(0)
                builder.openHelperFactory(customFactory)
            } else {
                MasroufiApplication.ensureSqlCipherLoaded()
                // clearPassphrase = true ensures SQLCipher zeroes passphrase bytes after keying
                val factory = SupportOpenHelperFactory(passphrase, null, true)
                builder.openHelperFactory(factory)
            }
            val db = builder.build()
            // Force connection validation now so wrong/corrupted key surfaces cleanly as RecoveryRequired
            db.openHelper.writableDatabase
            val headerCheck = DatabaseKeyManager.verifyDatabaseFileEncrypted(
                DatabaseKeyManager.databaseFile(appCtx, dbName)
            )
            _headerCheckState.value = headerCheck
            if (customFactory == null && headerCheck == DbEncryptionHeaderCheck.PlaintextSqliteDetected) {
                runCatching { db.close() }
                _healthState.value = DatabaseHealthState.RecoveryRequired("plaintext_header_detected")
                return null
            }
            instance = db
            _healthState.value = DatabaseHealthState.Healthy
            db
        } catch (e: Throwable) {
            passphrase.fill(0)
            _healthState.value = DatabaseHealthState.RecoveryRequired("db_open_failed")
            null
        }
    }

    fun close() = synchronized(lock) {
        closeInternal()
    }

    private fun closeInternal() {
        runCatching { instance?.close() }
        instance = null
    }

    internal fun resetForTesting() = synchronized(lock) {
        closeInternal()
        _healthState.value = DatabaseHealthState.Uninitialized
        _headerCheckState.value = null
    }
}
