package com.example

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountItem
import com.example.data.ActiveDataSourceMode
import com.example.data.DataSourceConfigStore
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.LocalRoomMasroufiDataSource
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.data.local.FirestoreImportResult
import com.example.data.local.FirestoreImportSource
import com.example.data.local.FirestoreToRoomImporter
import com.example.data.local.FirestoreUserSnapshot
import com.example.data.local.MasroufiDatabaseProvider
import com.example.data.local.NoOpRemoteMirror
import com.example.data.local.RemoteMirror
import com.example.data.local.RoomMasroufiRepository
import com.example.security.DatabaseKeyManager
import com.example.security.DbEncryptionHeaderCheck
import com.masroufi.credit.data.SavedCreditEntity
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase1bDataLayerAndImportTest {

    private lateinit var context: Context
    private val testDbName = "masroufi_phase1b_test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MasroufiDatabaseProvider.resetForTesting()
        DatabaseKeyManager.deleteDatabaseArtifacts(context, testDbName)
        DataSourceConfigStore.resetForTesting(context)
        context.getSharedPreferences("masroufi_secure_kv_v1", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        FirestoreToRoomImporter.preImportBackupFile(context, "uid_server_test").delete()
    }

    @After
    fun tearDown() {
        MasroufiDatabaseProvider.resetForTesting()
        DatabaseKeyManager.deleteDatabaseArtifacts(context, testDbName)
        DataSourceConfigStore.resetForTesting(context)
        FirestoreToRoomImporter.preImportBackupFile(context, "uid_server_test").delete()
    }

    @Test
    fun `on-disk DB header check detects plaintext SQLite format 3 and accepts encrypted header`() =
        runBlocking(Dispatchers.IO) {
            val missingFile = File(context.filesDir, "nonexistent_phase1b.db")
            missingFile.delete()
            assertEquals(
                DbEncryptionHeaderCheck.FileMissingOrTooShort,
                DatabaseKeyManager.verifyDatabaseFileEncrypted(missingFile)
            )

            // 1. Open a real unencrypted SQLite DB via Android framework SQLite -> must be caught!
            val unencryptedDb = MasroufiDatabaseProvider.getOrOpen(
                context = context,
                dbName = testDbName,
                customFactory = FrameworkSQLiteOpenHelperFactory()
            )
            assertNotNull(unencryptedDb)
            unencryptedDb!!.masroufiDao().upsertProfile(
                com.example.data.local.UserProfileEntity(userId = "hdr_user", baseBudget = 5000.0)
            )
            MasroufiDatabaseProvider.close()

            val dbFile = DatabaseKeyManager.databaseFile(context, testDbName)
            assertEquals(
                "Unencrypted SQLite file must be flagged as PlaintextSqliteDetected",
                DbEncryptionHeaderCheck.PlaintextSqliteDetected,
                DatabaseKeyManager.verifyDatabaseFileEncrypted(dbFile)
            )

            // 2. Simulated SQLCipher encrypted page-1 (random high-entropy salt + ciphertext, no "SQLite format 3")
            val encryptedFile = File(context.filesDir, "simulated_sqlcipher.db")
            try {
                val randomPage = ByteArray(4096)
                SecureRandom().nextBytes(randomPage)
                // Ensure the random bytes cannot accidentally begin with 'S'
                randomPage[0] = 0xA7.toByte()
                encryptedFile.writeBytes(randomPage)

                assertEquals(
                    "SQLCipher-formatted binary page header must pass as VerifiedEncrypted",
                    DbEncryptionHeaderCheck.VerifiedEncrypted,
                    DatabaseKeyManager.verifyDatabaseFileEncrypted(encryptedFile)
                )
            } finally {
                encryptedFile.delete()
            }
        }

    @Test
    fun `Source SERVER import failure leaves Room untouched, does not mark import completed, and returns localized errors`() =
        runBlocking(Dispatchers.IO) {
            val db = MasroufiDatabaseProvider.getOrOpen(
                context = context,
                dbName = testDbName,
                customFactory = FrameworkSQLiteOpenHelperFactory()
            )!!
            val dao = db.masroufiDao()
            val uid = "uid_server_test"

            // Pre-existing sentinel row for another user to verify Room stays untouched
            dao.upsertProfile(com.example.data.local.UserProfileEntity(userId = "sentinel_user", baseBudget = 9999.0))

            val failingServer = object : FirestoreImportSource {
                override suspend fun fetchUserSnapshotFromServer(userId: String): FirestoreUserSnapshot {
                    throw IOException("UNAVAILABLE: offline")
                }
            }

            listOf("fr", "ar", "darija", "en").forEach { lang ->
                val result = FirestoreToRoomImporter.runImport(
                    context = context,
                    userId = uid,
                    language = lang,
                    dao = dao,
                    serverSource = failingServer
                )
                assertTrue("Expected Failed result for $lang", result is FirestoreImportResult.Failed)
                val failed = result as FirestoreImportResult.Failed
                assertEquals("server_read_failed", failed.reasonCode)
                assertTrue("Localized error for $lang must not be blank", failed.localizedMessage.isNotBlank())
            }

            // Room untouched, no backup file, import not marked completed; app stays strictly local-first ROOM_PRIMARY
            assertEquals(0, dao.countProfiles(uid))
            assertEquals(0, dao.countTransactions(uid))
            assertNull(dao.getImportMetadata(uid))
            assertEquals(9999.0, dao.getProfile("sentinel_user")?.baseBudget ?: 0.0, 0.001)
            assertFalse(FirestoreToRoomImporter.preImportBackupFile(context, uid).exists())
            assertFalse(DataSourceConfigStore.isRoomImportCompleted(context, uid))
            assertEquals(
                ActiveDataSourceMode.ROOM_PRIMARY,
                DataSourceConfigStore.resolveEffectiveMode(context, uid, isRoomHealthy = true)
            )
        }

    @Test
    fun `import is resumable after count mismatch, creates encrypted masroufi backup, records completion only when counts match, and is idempotent`() =
        runBlocking(Dispatchers.IO) {
            val db = MasroufiDatabaseProvider.getOrOpen(
                context = context,
                dbName = testDbName,
                customFactory = FrameworkSQLiteOpenHelperFactory()
            )!!
            val dao = db.masroufiDao()
            val uid = "uid_server_test"

            val sampleSnapshot = FirestoreUserSnapshot(
                profile = UserProfile(id = uid, userId = uid, baseBudget = 11200.0, payCycleDay = 5, language = "fr"),
                accounts = listOf(
                    AccountItem(id = "acc_cash", userId = uid, name = "Cash", kind = "cash", initialBalance = 1200.0),
                    AccountItem(id = "acc_bank", userId = uid, name = "CIH", kind = "bank", initialBalance = 7500.0)
                ),
                transactions = listOf(
                    TransactionItem(
                        id = "tx_1",
                        userId = uid,
                        type = "expense",
                        amount = 345.0,
                        category = "marche",
                        envelopeKey = "marche",
                        accountId = "acc_bank",
                        desc = "SecretMarjaneReceipt99",
                        isoDate = "2026-11-20"
                    )
                ),
                envelopes = listOf(
                    EnvelopeConfig(id = "env_marche", userId = uid, key = "marche", nameFr = "Marché", percentage = 35.0)
                ),
                financialItems = listOf(
                    FinancialItem(id = "bill_1", userId = uid, itemType = "bill", name = "Loyer", amount = 3200.0)
                ),
                credits = listOf(
                    SavedCreditEntity(
                        id = "cred_1",
                        userId = uid,
                        type = "AUTO",
                        loanAmount = 150000.0,
                        financedPrincipal = 120000.0,
                        durationMonths = 60,
                        monthlyPayment = 2300.0
                    )
                )
            )

            var serverFetchCount = 0
            val fakeServer = object : FirestoreImportSource {
                override suspend fun fetchUserSnapshotFromServer(userId: String): FirestoreUserSnapshot {
                    serverFetchCount++
                    return sampleSnapshot
                }
            }

            // Attempt 1: Count verification fails -> must NOT write import metadata or mark completed
            val mismatchResult = FirestoreToRoomImporter.runImport(
                context = context,
                userId = uid,
                language = "en",
                dao = dao,
                serverSource = fakeServer,
                verifyCountsOverride = { _, _, _ -> false }
            )
            assertTrue(mismatchResult is FirestoreImportResult.Failed)
            assertEquals("count_mismatch", (mismatchResult as FirestoreImportResult.Failed).reasonCode)
            assertNull(dao.getImportMetadata(uid))
            assertFalse(DataSourceConfigStore.isRoomImportCompleted(context, uid))

            // Attempt 2: Resume import cleanly -> counts match -> encrypted .masroufi backup written & marked completed
            val successResult = FirestoreToRoomImporter.runImport(
                context = context,
                userId = uid,
                language = "en",
                dao = dao,
                serverSource = fakeServer
            )
            assertTrue("Resumed import must succeed", successResult is FirestoreImportResult.Success)
            val success = successResult as FirestoreImportResult.Success
            assertEquals(1, success.metadata.profileCount)
            assertEquals(2, success.metadata.accountCount)
            assertEquals(1, success.metadata.transactionCount)
            assertEquals(1, success.metadata.envelopeCount)
            assertEquals(1, success.metadata.financialItemCount)
            assertEquals(1, success.metadata.creditCount)
            assertTrue(success.metadata.backupChecksumSha256.length == 64)

            // Encrypted .masroufi backup exists and does NOT contain plaintext financial details
            assertTrue(success.encryptedBackupFile.exists())
            val rawBackupText = success.encryptedBackupFile.readText()
            assertFalse(
                "Pre-import .masroufi backup must be encrypted (no raw description)",
                rawBackupText.contains("SecretMarjaneReceipt99")
            )
            assertTrue(DataSourceConfigStore.isRoomImportCompleted(context, uid))
            assertEquals(ActiveDataSourceMode.ROOM_PRIMARY, DataSourceConfigStore.activeMode.value)

            // Attempt 3: Idempotency -> must return AlreadyCompleted without fetching server again
            val fetchesBeforeReplay = serverFetchCount
            val idempotentResult = FirestoreToRoomImporter.runImport(
                context = context,
                userId = uid,
                language = "en",
                dao = dao,
                serverSource = fakeServer
            )
            assertTrue(idempotentResult is FirestoreImportResult.AlreadyCompleted)
            assertEquals("Idempotent replay must not re-fetch server", fetchesBeforeReplay, serverFetchCount)
        }

    @Test
    fun `RoomMasroufiRepository writes locally first and isolates RemoteMirror failures`() =
        runBlocking(Dispatchers.IO) {
            val db = MasroufiDatabaseProvider.getOrOpen(
                context = context,
                dbName = testDbName,
                customFactory = FrameworkSQLiteOpenHelperFactory()
            )!!
            val uid = "uid_remote_mirror"
            val mirroredIds = mutableListOf<String>()
            var failMirror = false
            val spyMirror = object : RemoteMirror {
                override suspend fun onTransactionUpserted(userId: String, transaction: TransactionItem) {
                    if (failMirror) throw IOException("Simulated remote mirror failure")
                    mirroredIds.add(transaction.id)
                }
            }

            val roomRepo = RoomMasroufiRepository(
                dao = db.masroufiDao(),
                remoteMirror = spyMirror,
                userIdProvider = { uid }
            )

            // Post-commit mirror receives committed transaction
            val txId = roomRepo.upsertTransaction(
                TransactionItem(id = "tx_rm_1", type = "expense", amount = 75.0, desc = "Cafe", isoDate = "2026-11-21"),
                uid
            )
            assertEquals("tx_rm_1", txId)
            assertEquals(1, roomRepo.observeTransactions(uid).first().size)
            assertEquals(listOf("tx_rm_1"), mirroredIds)

            // Failing RemoteMirror MUST NOT roll back or crash local Room write
            failMirror = true
            val offlineTxId = roomRepo.upsertTransaction(
                TransactionItem(id = "tx_rm_offline", type = "expense", amount = 120.0, desc = "Taxi", isoDate = "2026-11-21"),
                uid
            )
            assertEquals("tx_rm_offline", offlineTxId)
            assertEquals(2, roomRepo.observeTransactions(uid).first().size)
            assertEquals(listOf("tx_rm_1"), mirroredIds)
        }

    @Test
    fun `LocalRoomMasroufiDataSource always serves Room reactively across database open and reopen`() =
        runBlocking(Dispatchers.IO) {
            val uid = "uid_local_facade"
            val facade = LocalRoomMasroufiDataSource(
                context = context,
                remoteMirror = NoOpRemoteMirror,
                userIdProvider = { uid }
            )

            // Before DB opens -> returns empty safely without throwing or touching Firestore
            assertEquals(emptyList<TransactionItem>(), facade.observeTransactions(uid).first())

            // Open Room DB -> facade immediately binds reactively
            val db = MasroufiDatabaseProvider.getOrOpen(
                context = context,
                dbName = testDbName,
                customFactory = FrameworkSQLiteOpenHelperFactory()
            )!!
            facade.upsertTransaction(
                TransactionItem(id = "tx_room_only", type = "expense", amount = 90.0, desc = "RoomRow", isoDate = "2026-11-21"),
                uid
            )
            assertEquals(
                listOf("tx_room_only"),
                facade.observeTransactions(uid).first().map { it.id }
            )

            // Even if legacy kill switch preference is toggled, app stays strictly ROOM_PRIMARY
            DataSourceConfigStore.setFirestoreKillSwitchEnabled(context, enabled = true, currentUserId = uid)
            assertEquals(ActiveDataSourceMode.ROOM_PRIMARY, DataSourceConfigStore.activeMode.value)
            assertEquals(
                listOf("tx_room_only"),
                facade.observeTransactions(uid).first().map { it.id }
            )

            assertEquals(1, db.masroufiDao().countTransactions(uid))
        }
}
