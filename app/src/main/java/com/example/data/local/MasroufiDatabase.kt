package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        ImportMetadataEntity::class,
        LoanEntity::class,
        LoanRepaymentEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class MasroufiDatabase : RoomDatabase() {
    abstract fun masroufiDao(): MasroufiDao

    companion object {
        /**
         * Lossless Room v1 -> v2 migration converting every monetary REAL (DH) column to
         * exact INTEGER centimes (`CAST(ROUND(col * 100.0) AS INTEGER)`) and separating
         * Daret member counts into nullable `financial_items.daretMembers`.
         *
         * Tables without monetary columns (`envelopes`, `import_metadata`) are untouched.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. user_profiles (baseBudget REAL -> baseBudgetCentimes INTEGER)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_profiles_new` (
                        `userId` TEXT NOT NULL,
                        `baseBudgetCentimes` INTEGER NOT NULL,
                        `payCycleDay` INTEGER NOT NULL,
                        `rolloverEnabled` INTEGER NOT NULL,
                        `language` TEXT NOT NULL,
                        `isDarkTheme` INTEGER NOT NULL,
                        `seeded` INTEGER NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `user_profiles_new` (
                        `userId`, `baseBudgetCentimes`, `payCycleDay`, `rolloverEnabled`,
                        `language`, `isDarkTheme`, `seeded`, `createdAtEpochMs`, `updatedAtEpochMs`
                    )
                    SELECT
                        `userId`,
                        CAST(ROUND(`baseBudget` * 100.0) AS INTEGER),
                        `payCycleDay`, `rolloverEnabled`,
                        `language`, `isDarkTheme`, `seeded`, `createdAtEpochMs`, `updatedAtEpochMs`
                    FROM `user_profiles`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `user_profiles`")
                db.execSQL("ALTER TABLE `user_profiles_new` RENAME TO `user_profiles`")

                // 2. accounts (initialBalance REAL -> initialBalanceCentimes INTEGER)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `accounts_new` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `initialBalanceCentimes` INTEGER NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `accounts_new` (
                        `id`, `userId`, `name`, `kind`, `currency`, `initialBalanceCentimes`,
                        `colorHex`, `isArchived`, `sortOrder`, `createdAtEpochMs`, `updatedAtEpochMs`
                    )
                    SELECT
                        `id`, `userId`, `name`, `kind`, `currency`,
                        CAST(ROUND(`initialBalance` * 100.0) AS INTEGER),
                        `colorHex`, `isArchived`, `sortOrder`, `createdAtEpochMs`, `updatedAtEpochMs`
                    FROM `accounts`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `accounts`")
                db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_userId` ON `accounts` (`userId`)")

                // 3. transactions (amount REAL -> amountCentimes INTEGER)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transactions_new` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `amountCentimes` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `envelopeKey` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `toAccountId` TEXT NOT NULL,
                        `desc` TEXT NOT NULL,
                        `isoDate` TEXT NOT NULL,
                        `hourOfDay` INTEGER NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `transactions_new` (
                        `id`, `userId`, `type`, `amountCentimes`, `category`, `envelopeKey`,
                        `accountId`, `toAccountId`, `desc`, `isoDate`, `hourOfDay`,
                        `createdAtEpochMs`, `updatedAtEpochMs`
                    )
                    SELECT
                        `id`, `userId`, `type`,
                        CAST(ROUND(`amount` * 100.0) AS INTEGER),
                        `category`, `envelopeKey`, `accountId`, `toAccountId`, `desc`, `isoDate`,
                        `hourOfDay`, `createdAtEpochMs`, `updatedAtEpochMs`
                    FROM `transactions`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `transactions`")
                db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_userId` ON `transactions` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_userId_isoDate` ON `transactions` (`userId`, `isoDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_envelopeKey` ON `transactions` (`envelopeKey`)")

                // 4. financial_items (amount/secondaryAmount REAL -> *Centimes INTEGER + nullable daretMembers INTEGER)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `financial_items_new` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `itemType` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `subtitle` TEXT NOT NULL,
                        `amountCentimes` INTEGER NOT NULL,
                        `secondaryAmountCentimes` INTEGER NOT NULL,
                        `daretMembers` INTEGER,
                        `dayOrTurn` INTEGER NOT NULL,
                        `extraInt` INTEGER NOT NULL,
                        `dateOrCycle` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `financial_items_new` (
                        `id`, `userId`, `itemType`, `name`, `subtitle`,
                        `amountCentimes`, `secondaryAmountCentimes`, `daretMembers`,
                        `dayOrTurn`, `extraInt`, `dateOrCycle`, `createdAtEpochMs`, `updatedAtEpochMs`
                    )
                    SELECT
                        `id`, `userId`, `itemType`, `name`, `subtitle`,
                        CAST(ROUND(`amount` * 100.0) AS INTEGER),
                        CASE WHEN `itemType` = 'daret' THEN 0 ELSE CAST(ROUND(`secondaryAmount` * 100.0) AS INTEGER) END,
                        CASE WHEN `itemType` = 'daret' THEN CAST(ROUND(`secondaryAmount`) AS INTEGER) ELSE NULL END,
                        `dayOrTurn`, `extraInt`, `dateOrCycle`, `createdAtEpochMs`, `updatedAtEpochMs`
                    FROM `financial_items`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `financial_items`")
                db.execSQL("ALTER TABLE `financial_items_new` RENAME TO `financial_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_items_userId` ON `financial_items` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_items_userId_itemType` ON `financial_items` (`userId`, `itemType`)")

                // 5. saved_credits (5 monetary REAL columns -> *Centimes INTEGER; rates & DTI stay REAL)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_credits_new` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `loanAmountCentimes` INTEGER NOT NULL,
                        `downPaymentCentimes` INTEGER NOT NULL,
                        `financedPrincipalCentimes` INTEGER NOT NULL,
                        `durationMonths` INTEGER NOT NULL,
                        `deferredMonths` INTEGER NOT NULL,
                        `annualInterestRateHt` REAL NOT NULL,
                        `applyInterestTva` INTEGER NOT NULL,
                        `insuranceRate` REAL NOT NULL,
                        `monthlyPaymentCentimes` INTEGER NOT NULL,
                        `totalCostCentimes` INTEGER NOT NULL,
                        `debtToIncomeRatio` REAL NOT NULL,
                        `maxAllowedDti` REAL NOT NULL,
                        `linkToMonthlyExpenses` INTEGER NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `saved_credits_new` (
                        `id`, `userId`, `type`,
                        `loanAmountCentimes`, `downPaymentCentimes`, `financedPrincipalCentimes`,
                        `durationMonths`, `deferredMonths`, `annualInterestRateHt`, `applyInterestTva`,
                        `insuranceRate`, `monthlyPaymentCentimes`, `totalCostCentimes`,
                        `debtToIncomeRatio`, `maxAllowedDti`, `linkToMonthlyExpenses`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `userId`, `type`,
                        CAST(ROUND(`loanAmount` * 100.0) AS INTEGER),
                        CAST(ROUND(`downPayment` * 100.0) AS INTEGER),
                        CAST(ROUND(`financedPrincipal` * 100.0) AS INTEGER),
                        `durationMonths`, `deferredMonths`, `annualInterestRateHt`, `applyInterestTva`,
                        `insuranceRate`,
                        CAST(ROUND(`monthlyPayment` * 100.0) AS INTEGER),
                        CAST(ROUND(`totalCost` * 100.0) AS INTEGER),
                        `debtToIncomeRatio`, `maxAllowedDti`, `linkToMonthlyExpenses`, `createdAtEpochMs`
                    FROM `saved_credits`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `saved_credits`")
                db.execSQL("ALTER TABLE `saved_credits_new` RENAME TO `saved_credits`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_credits_userId` ON `saved_credits` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_credits_userId_createdAtEpochMs` ON `saved_credits` (`userId`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_2_3: Migration = MasroufiDatabaseMigrations.MIGRATION_2_3
    }
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

    private val _dbGeneration = MutableStateFlow(0L)
    val dbGeneration: StateFlow<Long> = _dbGeneration.asStateFlow()

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
            ).addMigrations(MasroufiDatabase.MIGRATION_1_2, MasroufiDatabase.MIGRATION_2_3)
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
            _dbGeneration.value = _dbGeneration.value + 1L
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
        val hadInstance = instance != null
        runCatching { instance?.close() }
        instance = null
        if (hadInstance) {
            _dbGeneration.value = _dbGeneration.value + 1L
        }
    }

    internal fun resetForTesting() = synchronized(lock) {
        closeInternal()
        _healthState.value = DatabaseHealthState.Uninitialized
        _headerCheckState.value = null
        _dbGeneration.value = _dbGeneration.value + 1L
    }
}
