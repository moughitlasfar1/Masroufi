package com.example

import android.content.Context
import android.util.Base64
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AccountEntity
import com.example.data.local.DatabaseHealthState
import com.example.data.local.EnvelopeEntity
import com.example.data.local.FinancialItemEntity
import com.example.data.local.ImportMetadataEntity
import com.example.data.local.MasroufiDatabaseProvider
import com.example.data.local.SavedCreditLocalEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.UserProfileEntity
import com.example.security.DatabaseKeyManager
import com.example.security.DatabaseKeyResolution
import com.example.security.SecureStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 1a automated verification suite:
 * - DatabaseKeyManager key generation, SecureStore wrapping, reload, corruption detection, and reset.
 * - MasroufiDatabaseProvider lifecycle, recovery state transitions, and close/reopen persistence.
 * - Room v1 CRUD across all 7 tables + atomic replaceAllUserData.
 * - Exported Room schema JSON (v1) presence and table completeness.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EncryptedDatabasePhase1aTest {

    private lateinit var context: Context
    private val testDbName = "masroufi_phase1a_test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MasroufiDatabaseProvider.resetForTesting()
        DatabaseKeyManager.deleteDatabaseArtifacts(context, testDbName)
        DatabaseKeyManager.deleteDatabaseArtifacts(context, DatabaseKeyManager.ENCRYPTED_DB_NAME)
        context.getSharedPreferences("masroufi_secure_kv_v1", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        MasroufiDatabaseProvider.resetForTesting()
        DatabaseKeyManager.deleteDatabaseArtifacts(context, testDbName)
        DatabaseKeyManager.deleteDatabaseArtifacts(context, DatabaseKeyManager.ENCRYPTED_DB_NAME)
    }

    @Test
    fun `DatabaseKeyManager generates 32-byte key via SecureStore and reloads identically without raw plaintext`() {
        val first = DatabaseKeyManager.resolvePassphrase(context, testDbName)
        assertTrue("Expected Ready key on fresh install", first is DatabaseKeyResolution.Ready)
        val firstBytes = (first as DatabaseKeyResolution.Ready).passphrase
        assertEquals("Passphrase must be 256 bits (32 bytes)", 32, firstBytes.size)
        assertTrue("Generated key must not be all zeroes", firstBytes.any { it != 0.toByte() })

        // Verify SecureStore does not store the raw Base64 passphrase directly
        val rawPrefsValue = context.getSharedPreferences("masroufi_secure_kv_v1", Context.MODE_PRIVATE)
            .getString(DatabaseKeyManager.SECURE_STORE_DB_KEY, null)
        assertNotNull("Wrapped key entry must exist in SecureStore prefs", rawPrefsValue)
        val rawKeyBase64 = Base64.encodeToString(firstBytes, Base64.NO_WRAP)
        assertFalse(
            "Raw Base64 passphrase must never appear verbatim in SharedPreferences",
            rawPrefsValue!!.contains(rawKeyBase64)
        )

        // Subsequent resolution returns the exact same 32-byte key
        val second = DatabaseKeyManager.resolvePassphrase(context, testDbName)
        assertTrue(second is DatabaseKeyResolution.Ready)
        val secondBytes = (second as DatabaseKeyResolution.Ready).passphrase
        assertArrayEquals(firstBytes, secondBytes)
    }

    @Test
    fun `DatabaseKeyManager detects missing or corrupted key when DB file exists without deleting DB`() {
        val dbFile = DatabaseKeyManager.databaseFile(context, testDbName)
        dbFile.parentFile?.mkdirs()
        dbFile.writeBytes("ENCRYPTED_PAYLOAD_SENTINEL".toByteArray())
        assertTrue(dbFile.exists())

        // Case 1: DB file exists on disk, but SecureStore has no key entry
        val missingRes = DatabaseKeyManager.resolvePassphrase(context, testDbName)
        assertTrue(
            "Missing key with existing DB must return KeyCorruptedOrMissing",
            missingRes is DatabaseKeyResolution.KeyCorruptedOrMissing
        )
        assertEquals("key_entry_missing", (missingRes as DatabaseKeyResolution.KeyCorruptedOrMissing).reason)
        assertTrue("Existing DB file must NOT be deleted on missing key", dbFile.exists())
        assertEquals("ENCRYPTED_PAYLOAD_SENTINEL", dbFile.readText())

        // Case 2: DB file exists on disk, and SecureStore entry is corrupted / undecryptable
        context.getSharedPreferences("masroufi_secure_kv_v1", Context.MODE_PRIVATE)
            .edit()
            .putString(DatabaseKeyManager.SECURE_STORE_DB_KEY, "ss1:corrupted_iv:corrupted_ct")
            .commit()

        val corruptRes = DatabaseKeyManager.resolvePassphrase(context, testDbName)
        assertTrue(
            "Undecryptable key with existing DB must return KeyCorruptedOrMissing",
            corruptRes is DatabaseKeyResolution.KeyCorruptedOrMissing
        )
        assertEquals("keystore_decrypt_failed", (corruptRes as DatabaseKeyResolution.KeyCorruptedOrMissing).reason)
        assertTrue("Existing DB file must NOT be deleted on corrupted key", dbFile.exists())
    }

    @Test
    fun `DatabaseKeyManager resetDatabaseAndRegenerateKey wipes all DB files and rotates key`() {
        val initial = DatabaseKeyManager.resolvePassphrase(context, testDbName) as DatabaseKeyResolution.Ready
        val initialCopy = initial.passphrase.copyOf()

        val dbFile = DatabaseKeyManager.databaseFile(context, testDbName)
        dbFile.parentFile?.mkdirs()
        val walFile = File("${dbFile.path}-wal")
        val shmFile = File("${dbFile.path}-shm")
        val journalFile = File("${dbFile.path}-journal")
        listOf(dbFile, walFile, shmFile, journalFile).forEach { it.writeBytes(byteArrayOf(1, 2, 3, 4)) }

        val rotated = DatabaseKeyManager.resetDatabaseAndRegenerateKey(context, testDbName)
        assertTrue(rotated is DatabaseKeyResolution.Ready)
        val rotatedBytes = (rotated as DatabaseKeyResolution.Ready).passphrase

        assertFalse(dbFile.exists())
        assertFalse(walFile.exists())
        assertFalse(shmFile.exists())
        assertFalse(journalFile.exists())
        assertEquals(32, rotatedBytes.size)
        assertFalse("Reset must generate a fresh distinct key", initialCopy.contentEquals(rotatedBytes))
    }

    @Test
    fun `Room v1 persists all 7 tables across database close and reopen`() = runBlocking(Dispatchers.IO) {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val db1 = MasroufiDatabaseProvider.getOrOpen(context, testDbName, customFactory = factory)
        assertNotNull("Database should open cleanly", db1)
        assertEquals(DatabaseHealthState.Healthy, MasroufiDatabaseProvider.healthState.value)

        val dao1 = db1!!.masroufiDao()
        val uid = "user_phase1a"

        dao1.upsertProfile(
            UserProfileEntity(
                userId = uid,
                baseBudget = 12500.0,
                payCycleDay = 28,
                rolloverEnabled = true,
                language = "darija",
                isDarkTheme = true,
                seeded = true,
                createdAtEpochMs = 1000L,
                updatedAtEpochMs = 2000L
            )
        )
        dao1.upsertAccount(
            AccountEntity(
                id = "acc_cih",
                userId = uid,
                name = "CIH Bank",
                kind = "bank",
                currency = "MAD",
                initialBalance = 8500.0,
                colorHex = "#2563EB",
                sortOrder = 1,
                createdAtEpochMs = 1000L,
                updatedAtEpochMs = 2000L
            )
        )
        dao1.upsertTransaction(
            TransactionEntity(
                id = "tx_1",
                userId = uid,
                type = "expense",
                amount = 145.50,
                category = "alimentation",
                envelopeKey = "alimentation",
                accountId = "acc_cih",
                desc = "Marjane",
                isoDate = "2026-11-21",
                hourOfDay = 14,
                createdAtEpochMs = 1500L,
                updatedAtEpochMs = 1500L
            )
        )
        dao1.upsertEnvelope(
            EnvelopeEntity(
                id = "env_food",
                userId = uid,
                key = "alimentation",
                nameFr = "Alimentation",
                nameAr = "التغذية",
                nameDarija = "الماكلة",
                nameEn = "Food",
                percentage = 30.0,
                hasCap = true,
                colorHex = "#10B981",
                createdAtEpochMs = 1000L,
                updatedAtEpochMs = 1000L
            )
        )
        dao1.upsertFinancialItem(
            FinancialItemEntity(
                id = "bill_wifi",
                userId = uid,
                itemType = "bill",
                name = "Fibre Orange",
                subtitle = "Monthly",
                amount = 249.0,
                dayOrTurn = 5,
                dateOrCycle = "2026-11",
                createdAtEpochMs = 1000L,
                updatedAtEpochMs = 1000L
            )
        )
        dao1.upsertSavedCredit(
            SavedCreditLocalEntity(
                id = "cred_auto",
                userId = uid,
                type = "AUTO",
                loanAmount = 180000.0,
                downPayment = 30000.0,
                financedPrincipal = 150000.0,
                durationMonths = 60,
                deferredMonths = 0,
                annualInterestRateHt = 5.5,
                applyInterestTva = true,
                insuranceRate = 0.4,
                monthlyPayment = 2940.25,
                totalCost = 176415.0,
                debtToIncomeRatio = 23.5,
                maxAllowedDti = 45.0,
                linkToMonthlyExpenses = true,
                createdAtEpochMs = 1800L
            )
        )
        dao1.upsertImportMetadata(
            ImportMetadataEntity(
                userId = uid,
                completedAtEpochMs = 1900L,
                backupChecksumSha256 = "abc123sha256",
                profileCount = 1,
                accountCount = 1,
                transactionCount = 1,
                envelopeCount = 1,
                financialItemCount = 1,
                creditCount = 1
            )
        )

        // Close and reopen from disk to prove durable persistence
        MasroufiDatabaseProvider.close()
        val db2 = MasroufiDatabaseProvider.getOrOpen(context, testDbName, customFactory = factory)
        assertNotNull("Database should reopen cleanly", db2)
        val dao2 = db2!!.masroufiDao()

        assertEquals(12500.0, dao2.getProfile(uid)?.baseBudget ?: 0.0, 0.001)
        assertEquals("darija", dao2.observeProfile(uid).first()?.language)
        assertEquals(1, dao2.countAccounts(uid))
        assertEquals("CIH Bank", dao2.observeAccounts(uid).first().single().name)
        assertEquals(1, dao2.countTransactions(uid))
        assertEquals(145.50, dao2.observeTransactions(uid).first().single().amount, 0.001)
        assertEquals(1, dao2.countEnvelopes(uid))
        assertEquals(1, dao2.countFinancialItems(uid))
        assertEquals(1, dao2.countSavedCredits(uid))
        assertEquals("abc123sha256", dao2.getImportMetadata(uid)?.backupChecksumSha256)
    }

    @Test
    fun `replaceAllUserData atomically replaces target user without affecting other users`() =
        runBlocking(Dispatchers.IO) {
            val factory = FrameworkSQLiteOpenHelperFactory()
            val db = MasroufiDatabaseProvider.getOrOpen(context, testDbName, customFactory = factory)!!
            val dao = db.masroufiDao()

            dao.upsertProfile(UserProfileEntity(userId = "user_a", baseBudget = 5000.0))
            dao.upsertTransaction(TransactionEntity(id = "tx_a1", userId = "user_a", amount = 100.0))
            dao.upsertProfile(UserProfileEntity(userId = "user_b", baseBudget = 9000.0))
            dao.upsertTransaction(TransactionEntity(id = "tx_b1", userId = "user_b", amount = 250.0))

            dao.replaceAllUserData(
                userId = "user_a",
                profile = UserProfileEntity(userId = "user_a", baseBudget = 7500.0),
                accounts = listOf(AccountEntity(id = "acc_new", userId = "user_a", name = "Cash")),
                transactions = listOf(
                    TransactionEntity(id = "tx_a2", userId = "user_a", amount = 42.0),
                    TransactionEntity(id = "tx_a3", userId = "user_a", amount = 58.0)
                ),
                envelopes = emptyList(),
                financialItems = emptyList(),
                savedCredits = emptyList()
            )

            // user_a fully replaced
            assertEquals(7500.0, dao.getProfile("user_a")?.baseBudget ?: 0.0, 0.001)
            assertEquals(2, dao.countTransactions("user_a"))
            assertEquals(setOf("tx_a2", "tx_a3"), dao.getTransactions("user_a").map { it.id }.toSet())

            // user_b untouched
            assertEquals(9000.0, dao.getProfile("user_b")?.baseBudget ?: 0.0, 0.001)
            assertEquals(1, dao.countTransactions("user_b"))
            assertEquals("tx_b1", dao.getTransactions("user_b").single().id)
        }

    @Test
    fun `MasroufiDatabaseProvider transitions to RecoveryRequired on missing key and recovers via resetAndReopen`() =
        runBlocking(Dispatchers.IO) {
            val factory = FrameworkSQLiteOpenHelperFactory()
            // Open once so the DB file exists on disk
            val firstDb = MasroufiDatabaseProvider.getOrOpen(context, testDbName, customFactory = factory)
            assertNotNull(firstDb)
            firstDb!!.masroufiDao().upsertProfile(UserProfileEntity(userId = "u1", baseBudget = 6400.0))
            MasroufiDatabaseProvider.close()

            // Simulate AndroidKeyStore loss by removing the wrapped key while DB file stays on disk
            SecureStore.remove(context, DatabaseKeyManager.SECURE_STORE_DB_KEY)

            val failedOpen = MasroufiDatabaseProvider.getOrOpen(context, testDbName, customFactory = factory)
            assertNull("Opening DB with missing key must return null instead of crashing", failedOpen)
            val health = MasroufiDatabaseProvider.healthState.value
            assertTrue("Health state must be RecoveryRequired", health is DatabaseHealthState.RecoveryRequired)

            // User clicks "Reset Local Database"
            val recoveredDb = MasroufiDatabaseProvider.resetAndReopen(context, testDbName, customFactory = factory)
            assertNotNull("resetAndReopen must return a fresh usable DB", recoveredDb)
            assertEquals(DatabaseHealthState.Healthy, MasroufiDatabaseProvider.healthState.value)
            assertNull("Old unreadable rows should be wiped after explicit reset", recoveredDb!!.masroufiDao().getProfile("u1"))
        }

    @Test
    fun `Exported Room schema JSON v1 exists and contains all 7 entities`() {
        val schemaStream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("com.example.data.local.MasroufiDatabase/1.json")
        ) { "Expected exported Room schema JSON on test classpath" }
        val schemaText = schemaStream.bufferedReader().use { it.readText() }
        val root = JSONObject(schemaText)
        val database = root.getJSONObject("database")
        assertEquals(1, database.getInt("version"))

        val entities = database.getJSONArray("entities")
        val tableNames = mutableSetOf<String>()
        for (i in 0 until entities.length()) {
            tableNames += entities.getJSONObject(i).getString("tableName")
        }

        val expectedTables = setOf(
            "user_profiles",
            "accounts",
            "transactions",
            "envelopes",
            "financial_items",
            "saved_credits",
            "import_metadata"
        )
        assertEquals(expectedTables, tableNames)
    }
}
