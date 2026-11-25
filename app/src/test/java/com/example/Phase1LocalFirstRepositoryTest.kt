package com.example

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountItem
import com.example.data.DataSourceConfigStore
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.LocalRoomMasroufiDataSource
import com.example.data.MasroufiRepository
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
import com.masroufi.credit.data.RoomCreditRepository
import com.masroufi.credit.data.SavedCreditEntity
import com.masroufi.credit.domain.CreditCalculator
import com.masroufi.credit.domain.CreditInput
import com.masroufi.credit.domain.CreditType
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 1 Verification Gate:
 * 1. 100% offline CRUD across all domain entities (profile, accounts, envelopes, financial items,
 *    transactions, saved credits) using RoomMasroufiRepository + LocalRoomMasroufiDataSource +
 *    RoomCreditRepository with NoOpRemoteMirror and zero Firestore calls.
 * 2. Spy RemoteMirror receives post-commit callbacks with exact committed domain payloads ONLY
 *    after Room already persisted the change.
 * 3. Throwing RemoteMirror NEVER rolls back or crashes committed Room writes (main repo & credit repo).
 * 4. Local Mode (local_device_user) persistence + atomic adoption into Google uid preserves all
 *    6 tables with zero data loss, zero duplication, and idempotent replays.
 * 5. Isolated legacy FirestoreToRoomImporter still migrates legacy cloud data cleanly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase1LocalFirstRepositoryTest {

    private lateinit var context: Context
    private val testDbName = "masroufi_phase1_local_first_test.db"

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
        FirestoreToRoomImporter.preImportBackupFile(context, "uid_legacy_import").delete()
    }

    @After
    fun tearDown() {
        MasroufiDatabaseProvider.resetForTesting()
        DatabaseKeyManager.deleteDatabaseArtifacts(context, testDbName)
        DataSourceConfigStore.resetForTesting(context)
        FirestoreToRoomImporter.preImportBackupFile(context, "uid_legacy_import").delete()
    }

    private fun openTestDb() = MasroufiDatabaseProvider.getOrOpen(
        context = context,
        dbName = testDbName,
        customFactory = FrameworkSQLiteOpenHelperFactory()
    )!!

    @Test
    fun `offline CRUD across all collections and saved credits succeeds with NoOpRemoteMirror and zero Firestore`() =
        runBlocking(Dispatchers.IO) {
            val db = openTestDb()
            val dao = db.masroufiDao()
            val uid = "uid_offline_crud"

            val repo = RoomMasroufiRepository(
                dao = dao,
                remoteMirror = NoOpRemoteMirror,
                userIdProvider = { uid }
            )
            val creditRepo = RoomCreditRepository(
                dao = dao,
                userIdProvider = { uid },
                remoteMirror = NoOpRemoteMirror
            )

            // 1. Seed & Profile update
            repo.ensureSeeded(uid)
            val seededProfile = repo.observeProfile(uid).first()
            assertNotNull(seededProfile)
            assertEquals(3, repo.observeAccounts(uid).first().size)
            assertEquals(5, repo.observeEnvelopes(uid).first().size)
            assertEquals(4, repo.observeFinancialItems(uid).first().size)

            repo.saveProfile(seededProfile!!.copy(baseBudget = 12500.0, payCycleDay = 5, language = "darija"), uid)
            val updatedProfile = repo.observeProfile(uid).first()!!
            assertEquals(12500.0, updatedProfile.baseBudget, 0.001)
            assertEquals(5, updatedProfile.payCycleDay)
            assertEquals("darija", updatedProfile.language)

            // 2. Account upsert & delete
            val accId = repo.upsertAccount(
                AccountItem(id = "acc_cih", userId = uid, name = "CIH Code 30", kind = "bank", initialBalance = 4200.0),
                uid
            )
            assertEquals("acc_cih", accId)
            assertTrue(repo.observeAccounts(uid).first().any { it.id == "acc_cih" && it.initialBalance == 4200.0 })
            repo.deleteAccount("acc_cih", uid)
            assertTrue(repo.observeAccounts(uid).first().none { it.id == "acc_cih" })

            // 3. Envelope upsert, atomic batch upsert & delete
            repo.upsertEnvelope(
                EnvelopeConfig(id = "env_custom_1", userId = uid, key = "sante", nameFr = "Santé", percentage = 12.0),
                uid
            )
            assertTrue(repo.observeEnvelopes(uid).first().any { it.id == "env_custom_1" })
            repo.upsertEnvelopesAtomically(
                listOf(
                    EnvelopeConfig(id = "env_custom_1", userId = uid, key = "sante", nameFr = "Santé+", percentage = 15.0),
                    EnvelopeConfig(id = "env_custom_2", userId = uid, key = "sport", nameFr = "Sport", percentage = 5.0)
                ),
                uid
            )
            assertEquals(15.0, repo.observeEnvelopes(uid).first().first { it.id == "env_custom_1" }.percentage, 0.001)
            repo.deleteEnvelope("env_custom_2", uid)
            assertTrue(repo.observeEnvelopes(uid).first().none { it.id == "env_custom_2" })

            // 4. Transaction upsert, getById, minIsoDate filter & delete
            val txId = repo.upsertTransaction(
                TransactionItem(
                    id = "tx_offline_1",
                    userId = uid,
                    type = "expense",
                    amount = 240.5,
                    category = "marche",
                    envelopeKey = "marche",
                    accountId = "acc_cash",
                    desc = "Marché Central",
                    isoDate = "2026-11-22"
                ),
                uid
            )
            assertEquals("tx_offline_1", txId)
            assertEquals(240.5, repo.getTransactionById(uid, txId)?.amount ?: 0.0, 0.001)
            assertEquals(1, repo.observeTransactions(uid, minIsoDate = "2026-11-01").first().size)
            assertEquals(0, repo.observeTransactions(uid, minIsoDate = "2026-12-01").first().size)
            repo.deleteTransaction(txId, uid)
            assertNull(repo.getTransactionById(uid, txId))

            // 5. Financial item + atomic item-and-transaction upsert + delete
            val bill = FinancialItem(id = "bill_rent", userId = uid, itemType = "bill", name = "Loyer", amount = 3500.0)
            val billTx = TransactionItem(
                id = "tx_bill_rent_2026_11",
                userId = uid,
                type = "expense",
                amount = 3500.0,
                category = "charges",
                envelopeKey = "charges",
                accountId = "acc_bank",
                desc = "Loyer",
                isoDate = "2026-11-05"
            )
            repo.upsertItemAndTransactionAtomically(bill, billTx, uid)
            assertTrue(repo.observeFinancialItems(uid).first().any { it.id == "bill_rent" })
            assertNotNull(repo.getTransactionById(uid, "tx_bill_rent_2026_11"))
            repo.deleteFinancialItem("bill_rent", uid)
            assertTrue(repo.observeFinancialItems(uid).first().none { it.id == "bill_rent" })

            // 6. Credit Simulator saved credits CRUD (100% Room-backed, works offline)
            val creditInput = CreditInput(
                creditType = CreditType.AUTO,
                loanAmount = 180_000.0,
                downPayment = 30_000.0,
                durationMonths = 60,
                annualInterestRateHt = 6.5,
                monthlyNetIncome = 14_000.0
            )
            val creditResult = CreditCalculator.calculate(creditInput, "fr")
            val saveCreditRes = creditRepo.saveCredit(creditInput, creditResult, linkToMonthlyExpenses = true)
            assertTrue("RoomCreditRepository save must succeed offline", saveCreditRes.isSuccess)
            val savedCreditId = saveCreditRes.getOrThrow()
            val observedCredits = creditRepo.observeSavedCredits().first()
            assertEquals(1, observedCredits.size)
            assertEquals(savedCreditId, observedCredits.first().id)
            assertEquals(180_000.0, observedCredits.first().loanAmount, 0.01)
            assertTrue(observedCredits.first().linkToMonthlyExpenses)

            assertTrue(creditRepo.deleteCredit(savedCreditId).isSuccess)
            assertEquals(0, creditRepo.observeSavedCredits().first().size)
        }

    @Test
    fun `spy RemoteMirror receives post-commit callbacks strictly after Room persists rows`() =
        runBlocking(Dispatchers.IO) {
            val db = openTestDb()
            val dao = db.masroufiDao()
            val uid = "uid_spy_mirror"
            val events = mutableListOf<String>()

            val spyMirror = object : RemoteMirror {
                override suspend fun onProfileUpserted(userId: String, profile: UserProfile) {
                    assertNotNull("Room must already contain profile before RemoteMirror runs", dao.getProfile(userId))
                    events.add("profile:${profile.baseBudget}")
                }

                override suspend fun onTransactionUpserted(userId: String, transaction: TransactionItem) {
                    assertNotNull(
                        "Room must already contain transaction before RemoteMirror runs",
                        dao.getTransactionById(userId, transaction.id)
                    )
                    events.add("tx_upsert:${transaction.id}")
                }

                override suspend fun onTransactionDeleted(userId: String, transactionId: String) {
                    assertNull(
                        "Room must already have deleted transaction before RemoteMirror runs",
                        dao.getTransactionById(userId, transactionId)
                    )
                    events.add("tx_delete:$transactionId")
                }

                override suspend fun onAccountUpserted(userId: String, account: AccountItem) {
                    events.add("acc_upsert:${account.id}")
                }

                override suspend fun onAccountDeleted(userId: String, accountId: String) {
                    events.add("acc_delete:$accountId")
                }

                override suspend fun onEnvelopeUpserted(userId: String, envelope: EnvelopeConfig) {
                    events.add("env_upsert:${envelope.id}")
                }

                override suspend fun onEnvelopesUpserted(userId: String, envelopes: List<EnvelopeConfig>) {
                    events.add("envs_batch:${envelopes.size}")
                }

                override suspend fun onEnvelopeDeleted(userId: String, envelopeId: String) {
                    events.add("env_delete:$envelopeId")
                }

                override suspend fun onFinancialItemUpserted(userId: String, item: FinancialItem) {
                    events.add("item_upsert:${item.id}")
                }

                override suspend fun onItemAndTransactionUpserted(
                    userId: String,
                    item: FinancialItem,
                    transaction: TransactionItem
                ) {
                    assertNotNull(dao.getTransactionById(userId, transaction.id))
                    events.add("atomic:${item.id}+${transaction.id}")
                }

                override suspend fun onFinancialItemDeleted(userId: String, itemId: String) {
                    events.add("item_delete:$itemId")
                }

                override suspend fun onSavedCreditUpserted(userId: String, credit: SavedCreditEntity) {
                    assertEquals(1, dao.countSavedCredits(userId))
                    events.add("credit_upsert:${credit.id}")
                }

                override suspend fun onSavedCreditDeleted(userId: String, creditId: String) {
                    assertEquals(0, dao.countSavedCredits(userId))
                    events.add("credit_delete:$creditId")
                }

                override suspend fun onAccountReset(
                    userId: String,
                    deletedTxIds: List<String>,
                    deletedItemIds: List<String>,
                    deletedAccountIds: List<String>
                ) {
                    events.add("reset:${deletedTxIds.size}")
                }

                override suspend fun onBackupRestored(
                    userId: String,
                    restoredTxCount: Int,
                    restoredItemCount: Int,
                    restoredAccountCount: Int,
                    restoredEnvelopeCount: Int
                ) {
                    events.add("backup:$restoredTxCount")
                }
            }

            val repo = RoomMasroufiRepository(dao = dao, remoteMirror = spyMirror, userIdProvider = { uid })
            val creditRepo = RoomCreditRepository(dao = dao, userIdProvider = { uid }, remoteMirror = spyMirror)

            repo.saveProfile(UserProfile(userId = uid, baseBudget = 9000.0), uid)
            repo.upsertAccount(AccountItem(id = "acc_1", userId = uid, name = "Cash"), uid)
            repo.deleteAccount("acc_1", uid)
            repo.upsertEnvelope(EnvelopeConfig(id = "env_1", userId = uid, key = "cafe", nameFr = "Café", percentage = 20.0), uid)
            repo.upsertEnvelopesAtomically(
                listOf(EnvelopeConfig(id = "env_1", userId = uid, key = "cafe", nameFr = "Café", percentage = 25.0)),
                uid
            )
            repo.deleteEnvelope("env_1", uid)
            repo.upsertTransaction(TransactionItem(id = "tx_1", userId = uid, amount = 40.0, isoDate = "2026-11-22"), uid)
            repo.deleteTransaction("tx_1", uid)
            repo.upsertFinancialItem(FinancialItem(id = "goal_1", userId = uid, itemType = "goal", name = "Vacances"), uid)
            repo.upsertItemAndTransactionAtomically(
                FinancialItem(id = "goal_1", userId = uid, itemType = "goal", name = "Vacances", secondaryAmount = 500.0),
                TransactionItem(id = "tx_goal_1", userId = uid, amount = 500.0, isoDate = "2026-11-22"),
                uid
            )
            repo.deleteFinancialItem("goal_1", uid)
            repo.resetToCleanAccount(listOf("tx_goal_1"), emptyList(), emptyList(), uid)
            repo.restoreFullBackupAtomically(
                profile = UserProfile(userId = uid, baseBudget = 7500.0),
                customPrefsItem = null,
                accounts = emptyList(),
                envelopes = emptyList(),
                financialItems = emptyList(),
                transactions = listOf(TransactionItem(id = "tx_restored_1", userId = uid, amount = 100.0, isoDate = "2026-11-20")),
                userId = uid
            )

            val input = CreditInput(loanAmount = 50_000.0, durationMonths = 24, monthlyNetIncome = 10_000.0)
            val savedCreditId = creditRepo.saveCredit(input, CreditCalculator.calculate(input), false).getOrThrow()
            creditRepo.deleteCredit(savedCreditId).getOrThrow()

            assertEquals(
                listOf(
                    "profile:9000.0",
                    "acc_upsert:acc_1",
                    "acc_delete:acc_1",
                    "env_upsert:env_1",
                    "envs_batch:1",
                    "env_delete:env_1",
                    "tx_upsert:tx_1",
                    "tx_delete:tx_1",
                    "item_upsert:goal_1",
                    "atomic:goal_1+tx_goal_1",
                    "item_delete:goal_1",
                    "reset:1",
                    "backup:1",
                    "credit_upsert:$savedCreditId",
                    "credit_delete:$savedCreditId"
                ),
                events
            )
        }

    @Test
    fun `throwing RemoteMirror never rolls back or crashes committed Room writes`() =
        runBlocking(Dispatchers.IO) {
            val db = openTestDb()
            val dao = db.masroufiDao()
            val uid = "uid_throwing_mirror"

            val alwaysThrowingMirror = object : RemoteMirror {
                override suspend fun onProfileUpserted(userId: String, profile: UserProfile) = throw IllegalStateException("Mirror crash: profile")
                override suspend fun onTransactionUpserted(userId: String, transaction: TransactionItem) = throw IOException("Mirror crash: tx upsert")
                override suspend fun onTransactionDeleted(userId: String, transactionId: String) = throw RuntimeException("Mirror crash: tx delete")
                override suspend fun onAccountUpserted(userId: String, account: AccountItem) = throw RuntimeException("Mirror crash: acc")
                override suspend fun onAccountDeleted(userId: String, accountId: String) = throw RuntimeException("Mirror crash: acc del")
                override suspend fun onEnvelopeUpserted(userId: String, envelope: EnvelopeConfig) = throw RuntimeException("Mirror crash: env")
                override suspend fun onEnvelopesUpserted(userId: String, envelopes: List<EnvelopeConfig>) = throw RuntimeException("Mirror crash: envs")
                override suspend fun onEnvelopeDeleted(userId: String, envelopeId: String) = throw RuntimeException("Mirror crash: env del")
                override suspend fun onFinancialItemUpserted(userId: String, item: FinancialItem) = throw RuntimeException("Mirror crash: item")
                override suspend fun onItemAndTransactionUpserted(userId: String, item: FinancialItem, transaction: TransactionItem) = throw RuntimeException("Mirror crash: atomic")
                override suspend fun onFinancialItemDeleted(userId: String, itemId: String) = throw RuntimeException("Mirror crash: item del")
                override suspend fun onSavedCreditUpserted(userId: String, credit: SavedCreditEntity) = throw IOException("Mirror crash: credit upsert")
                override suspend fun onSavedCreditDeleted(userId: String, creditId: String) = throw IOException("Mirror crash: credit delete")
                override suspend fun onUserSeeded(userId: String) = throw RuntimeException("Mirror crash: seed")
                override suspend fun onDemoDataLoaded(userId: String) = throw RuntimeException("Mirror crash: demo")
                override suspend fun onAccountReset(userId: String, deletedTxIds: List<String>, deletedItemIds: List<String>, deletedAccountIds: List<String>) = throw RuntimeException("Mirror crash: reset")
                override suspend fun onBackupRestored(userId: String, restoredTxCount: Int, restoredItemCount: Int, restoredAccountCount: Int, restoredEnvelopeCount: Int) = throw RuntimeException("Mirror crash: backup")
                override suspend fun onLocalDataAdopted(sourceUserId: String, targetUserId: String, adoptedRowCount: Int) = throw RuntimeException("Mirror crash: adopt")
            }

            val repo = RoomMasroufiRepository(dao = dao, remoteMirror = alwaysThrowingMirror, userIdProvider = { uid })
            val creditRepo = RoomCreditRepository(dao = dao, userIdProvider = { uid }, remoteMirror = alwaysThrowingMirror)

            // Every single Room commit must succeed and remain persisted despite RemoteMirror throwing
            repo.ensureSeeded(uid)
            assertNotNull(dao.getProfile(uid))

            repo.saveProfile(UserProfile(userId = uid, baseBudget = 8800.0), uid)
            assertEquals(8800.0, dao.getProfile(uid)?.baseBudget ?: 0.0, 0.001)

            val txId = repo.upsertTransaction(
                TransactionItem(id = "tx_safe_1", userId = uid, amount = 199.0, desc = "Safe commit", isoDate = "2026-11-22"),
                uid
            )
            assertEquals("tx_safe_1", txId)
            assertEquals(199.0, dao.getTransactionById(uid, "tx_safe_1")?.amount ?: 0.0, 0.001)

            repo.upsertItemAndTransactionAtomically(
                FinancialItem(id = "item_safe_1", userId = uid, itemType = "bill", name = "Internet", amount = 250.0),
                TransactionItem(id = "tx_safe_2", userId = uid, amount = 250.0, isoDate = "2026-11-22"),
                uid
            )
            assertEquals(2, dao.countTransactions(uid))

            repo.deleteTransaction("tx_safe_1", uid)
            assertEquals(1, dao.countTransactions(uid))

            val creditInput = CreditInput(loanAmount = 100_000.0, durationMonths = 48, monthlyNetIncome = 12_000.0)
            val savedId = creditRepo.saveCredit(creditInput, CreditCalculator.calculate(creditInput), false).getOrThrow()
            assertEquals(1, dao.countSavedCredits(uid))
            assertTrue(creditRepo.deleteCredit(savedId).isSuccess)
            assertEquals(0, dao.countSavedCredits(uid))
        }

    @Test
    fun `Local Mode data persists in Room and is atomically adopted on Google Sign-In without loss or duplication`() =
        runBlocking(Dispatchers.IO) {
            val db = openTestDb()
            val dao = db.masroufiDao()
            val localUid = MasroufiRepository.LOCAL_DEVICE_USER_ID
            val googleUid = "google_uid_42"

            var adoptedMirrorEvent: Triple<String, String, Int>? = null
            val spyMirror = object : RemoteMirror {
                override suspend fun onLocalDataAdopted(sourceUserId: String, targetUserId: String, adoptedRowCount: Int) {
                    adoptedMirrorEvent = Triple(sourceUserId, targetUserId, adoptedRowCount)
                }
            }

            // Phase A: User operates completely offline in Local Mode
            val localFacade = LocalRoomMasroufiDataSource(
                context = context,
                remoteMirror = spyMirror,
                userIdProvider = { localUid }
            )
            val localCreditRepo = RoomCreditRepository(
                context = context,
                userIdProvider = { localUid },
                remoteMirror = spyMirror
            )

            localFacade.ensureSeeded(localUid)
            localFacade.saveProfile(
                UserProfile(userId = localUid, baseBudget = 11500.0, payCycleDay = 10, language = "ar", isDarkTheme = true),
                localUid
            )
            localFacade.upsertTransaction(
                TransactionItem(id = "tx_local_1", userId = localUid, amount = 85.0, desc = "Local coffee", isoDate = "2026-11-20"),
                localUid
            )
            localFacade.upsertTransaction(
                TransactionItem(id = "tx_local_2", userId = localUid, amount = 420.0, desc = "Local groceries", isoDate = "2026-11-21"),
                localUid
            )
            val creditInput = CreditInput(loanAmount = 90_000.0, durationMonths = 36, monthlyNetIncome = 11_500.0)
            val localCreditId = localCreditRepo.saveCredit(creditInput, CreditCalculator.calculate(creditInput), true).getOrThrow()

            assertEquals(2, dao.countTransactions(localUid))
            assertEquals(1, dao.countSavedCredits(localUid))

            // Phase B: User signs in with Google -> ensureSeeded(googleUid) atomically adopts ALL local rows
            val googleFacade = LocalRoomMasroufiDataSource(
                context = context,
                remoteMirror = spyMirror,
                userIdProvider = { googleUid }
            )
            val googleCreditRepo = RoomCreditRepository(
                context = context,
                userIdProvider = { googleUid },
                remoteMirror = spyMirror
            )

            googleFacade.ensureSeeded(googleUid)

            // All local rows now belong to googleUid; zero orphaned local_device_user rows remain
            assertEquals(0, dao.countProfiles(localUid))
            assertEquals(0, dao.countAccounts(localUid))
            assertEquals(0, dao.countEnvelopes(localUid))
            assertEquals(0, dao.countTransactions(localUid))
            assertEquals(0, dao.countFinancialItems(localUid))
            assertEquals(0, dao.countSavedCredits(localUid))

            val adoptedProfile = googleFacade.observeProfile(googleUid).first()
            assertNotNull(adoptedProfile)
            assertEquals(11500.0, adoptedProfile!!.baseBudget, 0.001)
            assertEquals(10, adoptedProfile.payCycleDay)
            assertEquals("ar", adoptedProfile.language)
            assertTrue(adoptedProfile.isDarkTheme)

            assertEquals(3, googleFacade.observeAccounts(googleUid).first().size)
            assertEquals(5, googleFacade.observeEnvelopes(googleUid).first().size)
            assertEquals(4, googleFacade.observeFinancialItems(googleUid).first().size)
            assertEquals(setOf("tx_local_1", "tx_local_2"), googleFacade.observeTransactions(googleUid).first().map { it.id }.toSet())
            assertEquals(listOf(localCreditId), googleCreditRepo.observeSavedCredits().first().map { it.id })

            assertNotNull("RemoteMirror should be notified of adoption", adoptedMirrorEvent)
            assertEquals(localUid, adoptedMirrorEvent!!.first)
            assertEquals(googleUid, adoptedMirrorEvent!!.second)
            assertTrue(adoptedMirrorEvent!!.third >= 15)

            // Phase C: Idempotent replay -> re-calling adoptLocalDataIntoUser does nothing and duplicates nothing
            val secondAdoptionCount = googleFacade.adoptLocalDataIntoUser(localUid, googleUid)
            assertEquals(0, secondAdoptionCount)
            assertEquals(2, googleFacade.observeTransactions(googleUid).first().size)
            assertEquals(1, googleCreditRepo.observeSavedCredits().first().size)

            // Phase D: If user later switches back to Local Mode on same device, seeding Local Mode
            // must NEVER overwrite or steal googleUid's accounts/envelopes/presets
            localFacade.ensureSeeded(localUid)
            assertEquals(3, googleFacade.observeAccounts(googleUid).first().size)
            assertEquals(5, googleFacade.observeEnvelopes(googleUid).first().size)
            assertEquals(2, googleFacade.observeTransactions(googleUid).first().size)
            assertEquals(3, localFacade.observeAccounts(localUid).first().size)
        }

    @Test
    fun `isolated legacy FirestoreToRoomImporter still imports cloud snapshot without affecting daily local-first writes`() =
        runBlocking(Dispatchers.IO) {
            val db = openTestDb()
            val dao = db.masroufiDao()
            val uid = "uid_legacy_import"

            val legacyServer = object : FirestoreImportSource {
                override suspend fun fetchUserSnapshotFromServer(userId: String): FirestoreUserSnapshot {
                    return FirestoreUserSnapshot(
                        profile = UserProfile(id = uid, userId = uid, baseBudget = 9800.0, payCycleDay = 1, language = "fr"),
                        accounts = listOf(AccountItem(id = "acc_cash", userId = uid, name = "Cash", initialBalance = 500.0)),
                        transactions = listOf(
                            TransactionItem(id = "tx_legacy_1", userId = uid, amount = 150.0, desc = "Legacy Cloud Tx", isoDate = "2026-10-15")
                        ),
                        envelopes = listOf(EnvelopeConfig(id = "env_cafe", userId = uid, key = "cafe", nameFr = "Café", percentage = 20.0)),
                        financialItems = listOf(FinancialItem(id = "bill_legacy", userId = uid, itemType = "bill", name = "Fibre", amount = 250.0)),
                        credits = listOf(SavedCreditEntity(id = "cred_legacy", userId = uid, type = "CONSOMMATION", loanAmount = 40_000.0, durationMonths = 24, monthlyPayment = 1800.0))
                    )
                }
            }

            val importResult = FirestoreToRoomImporter.runImport(
                context = context,
                userId = uid,
                language = "en",
                dao = dao,
                serverSource = legacyServer
            )
            assertTrue(importResult is FirestoreImportResult.Success)

            // Normal local-first repositories immediately read imported data AND accept offline writes
            val repo = RoomMasroufiRepository(dao = dao, remoteMirror = NoOpRemoteMirror, userIdProvider = { uid })
            val creditRepo = RoomCreditRepository(dao = dao, userIdProvider = { uid }, remoteMirror = NoOpRemoteMirror)

            assertEquals(listOf("tx_legacy_1"), repo.observeTransactions(uid).first().map { it.id })
            assertEquals(listOf("cred_legacy"), creditRepo.observeSavedCredits().first().map { it.id })

            repo.upsertTransaction(
                TransactionItem(id = "tx_post_import_local", userId = uid, amount = 45.0, desc = "New Local Tx", isoDate = "2026-11-22"),
                uid
            )
            assertEquals(2, repo.observeTransactions(uid).first().size)
        }
}
