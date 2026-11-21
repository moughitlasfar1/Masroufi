package com.example

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountItem
import com.example.data.ActiveDataSourceMode
import com.example.data.DataSourceConfigStore
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.MasroufiDataSource
import com.example.data.SwitchingMasroufiRepository
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.data.local.FirestoreImportResult
import com.example.data.local.FirestoreImportSource
import com.example.data.local.FirestoreToRoomImporter
import com.example.data.local.FirestoreUserSnapshot
import com.example.data.local.MasroufiDatabaseProvider
import com.example.data.local.RoomMasroufiRepository
import com.example.security.DatabaseKeyManager
import com.example.security.DbEncryptionHeaderCheck
import com.masroufi.credit.data.SavedCreditEntity
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun `Source SERVER import failure leaves Room untouched, does not flip mode, and returns localized errors`() =
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

            // Room untouched, no backup file, data source still FIRESTORE_LEGACY
            assertEquals(0, dao.countProfiles(uid))
            assertEquals(0, dao.countTransactions(uid))
            assertNull(dao.getImportMetadata(uid))
            assertEquals(9999.0, dao.getProfile("sentinel_user")?.baseBudget ?: 0.0, 0.001)
            assertFalse(FirestoreToRoomImporter.preImportBackupFile(context, uid).exists())
            assertFalse(DataSourceConfigStore.isRoomImportCompleted(context, uid))
            assertEquals(
                ActiveDataSourceMode.FIRESTORE_LEGACY,
                DataSourceConfigStore.resolveEffectiveMode(context, uid, isRoomHealthy = true)
            )
        }

    @Test
    fun `import is resumable after count mismatch, creates encrypted masroufi backup, flips mode only when counts match, and is idempotent`() =
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

            // Attempt 1: Count verification fails -> must NOT flip data source or write metadata
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
            assertEquals(ActiveDataSourceMode.FIRESTORE_LEGACY, DataSourceConfigStore.activeMode.value)

            // Attempt 2: Resume import cleanly -> counts match -> encrypted .masroufi backup written & flipped to Room
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
    fun `RoomMasroufiRepository writes locally first and performs best-effort Firestore write-through`() =
        runBlocking(Dispatchers.IO) {
            val db = MasroufiDatabaseProvider.getOrOpen(
                context = context,
                dbName = testDbName,
                customFactory = FrameworkSQLiteOpenHelperFactory()
            )!!
            val uid = "uid_writethrough"
            val cloudSpy = InMemoryFakeDataSource(uid)

            val roomRepo = RoomMasroufiRepository(
                dao = db.masroufiDao(),
                cloudWriteThrough = cloudSpy,
                userIdProvider = { uid },
                awaitWriteThroughForTesting = true
            )

            // Online write-through succeeds in both Room and Cloud spy
            val txId = roomRepo.upsertTransaction(
                TransactionItem(id = "tx_wt_1", type = "expense", amount = 75.0, desc = "Cafe", isoDate = "2026-11-21"),
                uid
            )
            assertEquals("tx_wt_1", txId)
            assertEquals(1, roomRepo.observeTransactions(uid).first().size)
            assertEquals(1, cloudSpy.transactions.value.size)

            // Simulate offline Firestore error -> local Room write MUST still succeed without throwing
            cloudSpy.failAllWrites = true
            val offlineTxId = roomRepo.upsertTransaction(
                TransactionItem(id = "tx_wt_offline", type = "expense", amount = 120.0, desc = "Taxi", isoDate = "2026-11-21"),
                uid
            )
            assertEquals("tx_wt_offline", offlineTxId)
            assertEquals(2, roomRepo.observeTransactions(uid).first().size)
            assertEquals("Cloud spy should not receive offline write", 1, cloudSpy.transactions.value.size)
        }

    @Test
    fun `SwitchingMasroufiRepository flips to Room after import and falls back immediately on kill switch`() =
        runBlocking(Dispatchers.IO) {
            val db = MasroufiDatabaseProvider.getOrOpen(
                context = context,
                dbName = testDbName,
                customFactory = FrameworkSQLiteOpenHelperFactory()
            )!!
            val uid = "uid_switch"
            val firestoreFake = InMemoryFakeDataSource(uid)
            firestoreFake.upsertTransaction(
                TransactionItem(id = "tx_cloud_only", type = "expense", amount = 50.0, desc = "CloudRow", isoDate = "2026-11-21"),
                uid
            )
            val roomRepo = RoomMasroufiRepository(
                dao = db.masroufiDao(),
                cloudWriteThrough = null,
                userIdProvider = { uid }
            )
            roomRepo.upsertTransaction(
                TransactionItem(id = "tx_room_only", type = "expense", amount = 90.0, desc = "RoomRow", isoDate = "2026-11-21"),
                uid
            )

            DataSourceConfigStore.refreshActiveMode(context, uid, isRoomHealthy = true)
            val router = SwitchingMasroufiRepository(
                firestoreFallback = firestoreFake,
                roomRepositoryProvider = { roomRepo }
            )

            // Before import completes -> legacy Firestore active
            assertEquals(
                listOf("tx_cloud_only"),
                router.observeTransactions(uid).first().map { it.id }
            )

            // Mark import completed -> flips reactively to Room primary
            DataSourceConfigStore.markRoomImportCompleted(context, uid)
            assertEquals(
                listOf("tx_room_only"),
                router.observeTransactions(uid).first().map { it.id }
            )

            // Engage Firestore fallback kill switch -> immediately back to legacy Firestore
            DataSourceConfigStore.setFirestoreKillSwitchEnabled(context, enabled = true, currentUserId = uid)
            assertEquals(
                listOf("tx_cloud_only"),
                router.observeTransactions(uid).first().map { it.id }
            )

            // Disengage kill switch -> back to Room primary
            DataSourceConfigStore.setFirestoreKillSwitchEnabled(context, enabled = false, currentUserId = uid)
            assertEquals(
                listOf("tx_room_only"),
                router.observeTransactions(uid).first().map { it.id }
            )
        }

    private class InMemoryFakeDataSource(private val defaultUid: String) : MasroufiDataSource {
        var failAllWrites = false
        val profile = MutableStateFlow<UserProfile?>(null)
        val transactions = MutableStateFlow<List<TransactionItem>>(emptyList())
        val accounts = MutableStateFlow<List<AccountItem>>(emptyList())
        val envelopes = MutableStateFlow<List<EnvelopeConfig>>(emptyList())
        val items = MutableStateFlow<List<FinancialItem>>(emptyList())

        override fun requireUserId(explicitUserId: String?): String = explicitUserId ?: defaultUid
        override fun observeProfile(userId: String): Flow<UserProfile?> = profile
        override fun observeTransactions(userId: String, minIsoDate: String?): Flow<List<TransactionItem>> = transactions
        override fun observeAccounts(userId: String): Flow<List<AccountItem>> = accounts
        override fun observeEnvelopes(userId: String): Flow<List<EnvelopeConfig>> = envelopes
        override fun observeFinancialItems(userId: String): Flow<List<FinancialItem>> = items

        private fun checkWriteAllowed() {
            if (failAllWrites) throw IOException("Simulated cloud offline failure")
        }

        override suspend fun saveProfile(profile: UserProfile, userId: String) {
            checkWriteAllowed()
            this.profile.value = profile
        }

        override suspend fun upsertTransaction(tx: TransactionItem, userId: String): String {
            checkWriteAllowed()
            val id = tx.id.ifBlank { "tx_fake" }
            transactions.value = transactions.value.filterNot { it.id == id } + tx.copy(id = id)
            return id
        }

        override suspend fun getTransactionById(targetUserId: String, txId: String): TransactionItem? =
            transactions.value.find { it.id == txId }

        override suspend fun deleteTransaction(txId: String, userId: String) {
            checkWriteAllowed()
            transactions.value = transactions.value.filterNot { it.id == txId }
        }

        override suspend fun upsertAccount(account: AccountItem, userId: String): String {
            checkWriteAllowed()
            accounts.value = accounts.value.filterNot { it.id == account.id } + account
            return account.id
        }

        override suspend fun deleteAccount(accId: String, userId: String) {
            checkWriteAllowed()
            accounts.value = accounts.value.filterNot { it.id == accId }
        }

        override suspend fun upsertEnvelope(envelope: EnvelopeConfig, userId: String): String {
            checkWriteAllowed()
            envelopes.value = envelopes.value.filterNot { it.id == envelope.id } + envelope
            return envelope.id
        }

        override suspend fun upsertEnvelopesAtomically(envelopes: List<EnvelopeConfig>, userId: String) {
            checkWriteAllowed()
            this.envelopes.value = envelopes
        }

        override suspend fun deleteEnvelope(envId: String, userId: String) {
            checkWriteAllowed()
            envelopes.value = envelopes.value.filterNot { it.id == envId }
        }

        override suspend fun upsertFinancialItem(item: FinancialItem, userId: String): String {
            checkWriteAllowed()
            items.value = items.value.filterNot { it.id == item.id } + item
            return item.id
        }

        override suspend fun upsertItemAndTransactionAtomically(item: FinancialItem, tx: TransactionItem, userId: String) {
            checkWriteAllowed()
            upsertFinancialItem(item, userId)
            upsertTransaction(tx, userId)
        }

        override suspend fun deleteFinancialItem(itemId: String, userId: String) {
            checkWriteAllowed()
            items.value = items.value.filterNot { it.id == itemId }
        }

        override suspend fun ensureSeeded(userId: String) {
            checkWriteAllowed()
        }

        override suspend fun loadDemoData(
            existingAccounts: List<AccountItem>,
            existingEnvelopes: List<EnvelopeConfig>,
            existingItems: List<FinancialItem>,
            existingTransactions: List<TransactionItem>,
            userId: String
        ) {
            checkWriteAllowed()
        }

        override suspend fun resetToCleanAccount(
            existingTxIds: List<String>,
            existingItemIdsToClear: List<String>,
            existingAccounts: List<AccountItem>,
            userId: String
        ) {
            checkWriteAllowed()
        }

        override suspend fun restoreFullBackupAtomically(
            profile: UserProfile?,
            customPrefsItem: FinancialItem?,
            accounts: List<AccountItem>,
            envelopes: List<EnvelopeConfig>,
            financialItems: List<FinancialItem>,
            transactions: List<TransactionItem>,
            staleTxIdsToDelete: List<String>,
            staleItemIdsToDelete: List<String>,
            staleAccountIdsToDelete: List<String>,
            staleEnvelopeIdsToDelete: List<String>,
            onChunkProgress: (completedChunks: Int, totalChunks: Int) -> Unit,
            userId: String
        ) {
            checkWriteAllowed()
            onChunkProgress(1, 1)
        }
    }
}
