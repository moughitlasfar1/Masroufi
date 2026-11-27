package com.example

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountItem
import com.example.data.BackupV3Codec
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.data.local.MasroufiDatabase
import com.example.data.local.RoomMasroufiRepository
import com.example.data.local.SavedCreditLocalEntity
import com.example.domain.InternalTransferResult
import com.example.domain.LoanCategory
import com.example.domain.LoanDirection
import com.example.domain.LoanItem
import com.example.domain.LoanRepaymentResult
import com.example.domain.LoanStatus
import com.example.domain.NetWorthCalculator
import com.example.domain.PayCycleEngine
import com.example.security.PortableBackupCrypto
import com.example.security.PortableCryptoResult
import com.example.ui.BackupRestoreResult
import com.example.ui.BackupSerializer
import com.example.ui.DashboardState
import com.example.ui.MasroufiViewModel
import com.masroufi.core.Money
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Phase 5 — Accounts, Atomic Internal Transfers, Overpayment-Safe Loans, Unified Net Worth Engine,
 * and Portable Backup v3 Verification Suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase5AccountsLoansNetWorthTest {

    private lateinit var db: MasroufiDatabase
    private lateinit var repo: RoomMasroufiRepository
    private val uid = "phase5_test_user"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroufiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomMasroufiRepository(
            dao = db.masroufiDao(),
            userIdProvider = { uid }
        )
    }

    @After
    fun tearDown() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        db.close()
    }

    private suspend fun awaitMainCondition(timeoutMs: Long = 2500L, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            delay(25L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `atomic paired internal transfer preserves exact centimes net worth zero delta and excludes income and expense totals`() = runBlocking {
        repo.saveProfile(UserProfile(userId = uid, baseBudget = Money.fromDh(6000), payCycleDay = 1), uid)
        repo.upsertAccount(AccountItem(id = "acc_bank", userId = uid, name = "Banque", kind = "bank", initialBalance = Money.fromCentimes(500_000L)), uid)
        repo.upsertAccount(AccountItem(id = "acc_cash", userId = uid, name = "Cash", kind = "cash", initialBalance = Money.fromCentimes(100_030L)), uid)

        // Add one exact centime expense (0.10 + 0.20 = 0.30 DH = 30L)
        val today = PayCycleEngine.todayIso()
        repo.upsertTransaction(
            TransactionItem(id = "tx_c1", userId = uid, type = "expense", amount = Money.fromCentimes(10L), category = "cafe", envelopeKey = "cafe", accountId = "acc_cash", desc = "Test 10c", isoDate = today),
            uid
        )
        repo.upsertTransaction(
            TransactionItem(id = "tx_c2", userId = uid, type = "expense", amount = Money.fromCentimes(20L), category = "cafe", envelopeKey = "cafe", accountId = "acc_cash", desc = "Test 20c", isoDate = today),
            uid
        )

        val beforeAccounts = repo.observeAccounts(uid).first()
        val beforeTxs = repo.observeTransactions(uid).first()
        val beforeNw = NetWorthCalculator.compute(beforeAccounts, beforeTxs)
        assertEquals(Money.fromCentimes(600_000L), beforeNw.totalLiquidAssets)
        assertEquals(Money.fromCentimes(600_000L), beforeNw.netWorth)

        // Validation guards
        assertEquals(
            InternalTransferResult.InvalidAccounts,
            repo.executeInternalTransfer("acc_bank", "acc_bank", Money.fromDh(100), today, "Self", uid)
        )
        assertEquals(
            InternalTransferResult.InvalidAmount,
            repo.executeInternalTransfer("acc_bank", "acc_cash", Money.ZERO, today, "Zero", uid)
        )

        // Execute atomic paired internal transfer of 1250.75 DH (125_075L centimes)
        val transferResult = repo.executeInternalTransfer(
            fromAccountId = "acc_bank",
            toAccountId = "acc_cash",
            amount = Money.fromCentimes(125_075L),
            isoDate = today,
            desc = "Retrait GAB",
            userId = uid
        )
        assertTrue(transferResult is InternalTransferResult.Success)
        val ok = transferResult as InternalTransferResult.Success
        assertEquals("transfer_out", ok.outgoingTx.type)
        assertEquals("transfer_in", ok.incomingTx.type)
        assertEquals(ok.transferGroupId, ok.outgoingTx.transferGroupId)
        assertEquals(ok.transferGroupId, ok.incomingTx.transferGroupId)
        assertEquals("acc_bank", ok.outgoingTx.accountId)
        assertEquals("acc_cash", ok.incomingTx.accountId)

        // Verify exact balances & Net Worth delta == 0
        val afterTxs = repo.observeTransactions(uid).first()
        val afterNw = NetWorthCalculator.compute(beforeAccounts, afterTxs)
        val balMap = afterNw.accountBalances.associate { it.account.id to it.currentBalance.centimes }
        assertEquals(500_000L - 125_075L, balMap["acc_bank"])
        assertEquals(100_000L + 125_075L, balMap["acc_cash"])
        assertEquals(beforeNw.totalLiquidAssets, afterNw.totalLiquidAssets)
        assertEquals(beforeNw.netWorth, afterNw.netWorth)

        // Verify MasroufiViewModel excludes paired transfer rows from cycleExpenses and cycleIncomes
        val vm = MasroufiViewModel(repository = repo, currentUserId = uid)
        val uiSub = launch { vm.uiState.collect {} }
        try {
            awaitMainCondition {
                !vm.uiState.value.isLoading && vm.uiState.value.allTransactions.size == 4
            }
            assertEquals(Money.fromCentimes(30L), vm.uiState.value.cycleExpenses)
            assertEquals(Money.ZERO, vm.uiState.value.cycleIncomes)
            assertEquals(Money.fromCentimes(600_000L), vm.netWorthSnapshot.value.netWorth)
        } finally {
            uiSub.cancel()
        }

        // Atomic transfer group deletion removes both legs together
        val deletedRows = repo.deleteTransferGroup(ok.transferGroupId, uid)
        assertEquals(2, deletedRows)
        val postDeleteNw = NetWorthCalculator.compute(beforeAccounts, repo.observeTransactions(uid).first())
        assertEquals(500_000L, postDeleteNw.accountBalances.first { it.account.id == "acc_bank" }.currentBalance.centimes)
        assertEquals(100_000L, postDeleteNw.accountBalances.first { it.account.id == "acc_cash" }.currentBalance.centimes)
    }

    @Test
    fun `legacy single-row transfer remains backward compatible in account balances and net worth`() {
        val accounts = listOf(
            AccountItem(id = "acc_bank", name = "Banque", kind = "bank", initialBalance = Money.fromDh(3000)),
            AccountItem(id = "acc_cash", name = "Cash", kind = "cash", initialBalance = Money.fromDh(500))
        )
        val txs = listOf(
            TransactionItem(
                id = "tx_legacy_trf",
                type = "transfer",
                amount = Money.fromDh(1000),
                accountId = "acc_bank",
                toAccountId = "acc_cash",
                isoDate = "2026-06-01"
            )
        )
        val snapshot = NetWorthCalculator.compute(accounts, txs)
        val map = snapshot.accountBalances.associate { it.account.id to it.currentBalance }
        assertEquals(Money.fromDh(2000), map["acc_bank"])
        assertEquals(Money.fromDh(1500), map["acc_cash"])
        assertEquals(Money.fromDh(3500), snapshot.totalLiquidAssets)
        assertEquals(Money.fromDh(3500), snapshot.netWorth)
    }

    @Test
    fun `loan repayment blocks overpayment transitions to PAID_OFF and keeps net worth delta zero on linked account settlement`() = runBlocking {
        repo.upsertAccount(AccountItem(id = "acc_bank", userId = uid, name = "CIH", kind = "bank", initialBalance = Money.fromDh(10_000)), uid)
        repo.upsertAccount(AccountItem(id = "acc_cash", userId = uid, name = "Cash", kind = "cash", initialBalance = Money.fromDh(1_000)), uid)

        val liabilityId = repo.upsertLoan(
            LoanItem(
                id = "loan_car",
                userId = uid,
                title = "Crédit Auto",
                counterparty = "CIH",
                direction = LoanDirection.LIABILITY,
                category = LoanCategory.BANK_CREDIT,
                principal = Money.fromDh(3_000),
                remaining = Money.fromDh(3_000),
                monthlyPayment = Money.fromDh(1_000),
                status = LoanStatus.ACTIVE,
                linkedAccountId = "acc_bank"
            ),
            uid
        )
        val receivableId = repo.upsertLoan(
            LoanItem(
                id = "loan_friend",
                userId = uid,
                title = "Prêt Karim",
                counterparty = "Karim",
                direction = LoanDirection.RECEIVABLE,
                category = LoanCategory.PERSONAL_DEBT,
                principal = Money.fromDh(500),
                remaining = Money.fromDh(500),
                status = LoanStatus.ACTIVE
            ),
            uid
        )

        // Initial Net Worth = 11_000 liquid + 500 receivable - 3_000 liability = 8_500 DH
        val initialNw = NetWorthCalculator.compute(
            repo.observeAccounts(uid).first(),
            repo.observeTransactions(uid).first(),
            repo.getLoans(uid)
        )
        assertEquals(Money.fromDh(11_000), initialNw.totalLiquidAssets)
        assertEquals(Money.fromDh(500), initialNw.totalReceivables)
        assertEquals(Money.fromDh(3_000), initialNw.totalLiabilities)
        assertEquals(Money.fromDh(8_500), initialNw.netWorth)

        // 1. Invalid amount & missing loan guards
        assertEquals(LoanRepaymentResult.InvalidAmount, repo.recordLoanRepayment(liabilityId, Money.ZERO, "acc_bank", "2026-06-10", "", uid))
        assertTrue(repo.recordLoanRepayment("missing_loan", Money.fromDh(100), "acc_bank", "2026-06-10", "", uid) is LoanRepaymentResult.LoanNotFound)

        // 2. Overpayment guard: attempting 3_000.01 DH on 3_000.00 DH remaining MUST fail cleanly with zero mutations
        val overpay = repo.recordLoanRepayment(
            loanId = liabilityId,
            amount = Money.fromCentimes(300_001L),
            linkedAccountId = "acc_bank",
            isoDate = "2026-06-10",
            note = "Overpay attempt",
            userId = uid
        )
        assertTrue(overpay is LoanRepaymentResult.OverpaymentBlocked)
        val blocked = overpay as LoanRepaymentResult.OverpaymentBlocked
        assertEquals(Money.fromDh(3_000), blocked.remaining)
        assertEquals(Money.fromCentimes(300_001L), blocked.attempted)
        assertEquals(0, repo.getLoanRepayments(uid).size)
        assertEquals(0, repo.observeTransactions(uid).first().size)

        // 3. Partial LIABILITY repayment of 1_000 DH from acc_bank -> Net Worth stays strictly 8_500 DH!
        val partial = repo.recordLoanRepayment(
            loanId = liabilityId,
            amount = Money.fromDh(1_000),
            linkedAccountId = "acc_bank",
            isoDate = "2026-06-11",
            note = "Mensualité 1",
            userId = uid
        )
        assertTrue(partial is LoanRepaymentResult.Success)
        val partialOk = partial as LoanRepaymentResult.Success
        assertEquals(Money.fromDh(2_000), partialOk.updatedLoan.remaining)
        assertEquals(LoanStatus.ACTIVE, partialOk.updatedLoan.status)
        assertNotNull(partialOk.linkedTransaction)
        assertEquals("transfer_out", partialOk.linkedTransaction!!.type)
        assertEquals(liabilityId, partialOk.linkedTransaction!!.loanId)

        val afterPartialNw = NetWorthCalculator.compute(
            repo.observeAccounts(uid).first(),
            repo.observeTransactions(uid).first(),
            repo.getLoans(uid)
        )
        assertEquals(Money.fromDh(10_000), afterPartialNw.totalLiquidAssets) // 11_000 - 1_000
        assertEquals(Money.fromDh(2_000), afterPartialNw.totalLiabilities)   // 3_000 - 1_000
        assertEquals(Money.fromDh(8_500), afterPartialNw.netWorth)           // Net Worth Δ == 0!

        // 4. Final LIABILITY repayment of 2_000 DH -> auto-transitions to PAID_OFF
        val finalRepay = repo.recordLoanRepayment(
            loanId = liabilityId,
            amount = Money.fromDh(2_000),
            linkedAccountId = "acc_bank",
            isoDate = "2026-06-12",
            note = "Solde final",
            userId = uid
        )
        assertTrue(finalRepay is LoanRepaymentResult.Success)
        val finalOk = finalRepay as LoanRepaymentResult.Success
        assertEquals(Money.ZERO, finalOk.updatedLoan.remaining)
        assertEquals(LoanStatus.PAID_OFF, finalOk.updatedLoan.status)

        // 5. Further repayment on PAID_OFF loan returns AlreadyPaidOff with zero mutations
        val afterPaidOff = repo.recordLoanRepayment(
            loanId = liabilityId,
            amount = Money.fromDh(10),
            linkedAccountId = "acc_bank",
            isoDate = "2026-06-13",
            note = "Extra",
            userId = uid
        )
        assertEquals(LoanRepaymentResult.AlreadyPaidOff(liabilityId), afterPaidOff)

        // 6. RECEIVABLE full repayment of 500 DH into acc_cash -> cash +500, receivables -500, Net Worth Δ == 0!
        val recRepay = repo.recordLoanRepayment(
            loanId = receivableId,
            amount = Money.fromDh(500),
            linkedAccountId = "acc_cash",
            isoDate = "2026-06-14",
            note = "Remboursement Karim",
            userId = uid
        )
        assertTrue(recRepay is LoanRepaymentResult.Success)
        val recOk = recRepay as LoanRepaymentResult.Success
        assertEquals(LoanStatus.PAID_OFF, recOk.updatedLoan.status)
        assertEquals("transfer_in", recOk.linkedTransaction!!.type)

        val finalNw = NetWorthCalculator.compute(
            repo.observeAccounts(uid).first(),
            repo.observeTransactions(uid).first(),
            repo.getLoans(uid)
        )
        assertEquals(Money.fromDh(8_500), finalNw.totalLiquidAssets)
        assertEquals(Money.ZERO, finalNw.totalReceivables)
        assertEquals(Money.ZERO, finalNw.totalLiabilities)
        assertEquals(Money.fromDh(8_500), finalNw.netWorth)
        assertEquals(0, finalNw.activeLoansCount)
        assertEquals(2, finalNw.paidOffLoansCount)
    }

    @Test
    fun `promoteSavedCreditToLoan bridges saved credit into tracked liability idempotently`() = runBlocking {
        db.masroufiDao().upsertSavedCredit(
            SavedCreditLocalEntity(
                id = "credit_immo_1",
                userId = uid,
                type = "IMMOBILIER",
                loanAmountCentimes = 80_000_000L,
                downPaymentCentimes = 20_000_000L,
                financedPrincipalCentimes = 60_000_000L,
                durationMonths = 240,
                monthlyPaymentCentimes = 385_000L,
                totalCostCentimes = 92_400_000L,
                createdAtEpochMs = 1_000L
            )
        )

        val firstPromote = repo.promoteSavedCreditToLoan("credit_immo_1", linkedAccountId = "acc_bank", userId = uid)
        assertTrue(firstPromote.isSuccess)
        val loan = firstPromote.getOrThrow()
        assertEquals(LoanDirection.LIABILITY, loan.direction)
        assertEquals(LoanCategory.BANK_CREDIT, loan.category)
        assertEquals(Money.fromCentimes(60_000_000L), loan.principal)
        assertEquals(Money.fromCentimes(60_000_000L), loan.remaining)
        assertEquals(Money.fromCentimes(385_000L), loan.monthlyPayment)
        assertEquals("credit_immo_1", loan.sourceSavedCreditId)

        // Second promotion is idempotent (no duplicate liability row created)
        val secondPromote = repo.promoteSavedCreditToLoan("credit_immo_1", linkedAccountId = "acc_bank", userId = uid)
        assertTrue(secondPromote.isSuccess)
        assertEquals(loan.id, secondPromote.getOrThrow().id)
        assertEquals(1, repo.getLoans(uid).size)
    }

    @Test
    fun `reactive netWorthSnapshot StateFlow updates live on MasroufiViewModel across account transfer and loan repayment`() = runBlocking {
        repo.saveProfile(UserProfile(userId = uid, baseBudget = Money.fromDh(5000)), uid)
        repo.upsertAccount(AccountItem(id = "acc_cash", userId = uid, name = "Cash", kind = "cash", initialBalance = Money.fromDh(4000)), uid)
        repo.upsertAccount(AccountItem(id = "acc_bank", userId = uid, name = "Bank", kind = "bank", initialBalance = Money.fromDh(6000)), uid)

        val vm = MasroufiViewModel(repository = repo, currentUserId = uid)
        val uiSub = launch { vm.uiState.collect {} }
        try {
            awaitMainCondition { vm.netWorthSnapshot.value.netWorth == Money.fromDh(10_000) }
            assertEquals(Money.fromDh(10_000), vm.netWorthSnapshot.value.netWorth)

            // Add a liability loan of 2_500 DH -> reactive Net Worth drops to 7_500 DH
            repo.upsertLoan(
                LoanItem(
                    id = "loan_react",
                    userId = uid,
                    title = "Prêt réactif",
                    direction = LoanDirection.LIABILITY,
                    category = LoanCategory.PERSONAL_DEBT,
                    principal = Money.fromDh(2_500),
                    remaining = Money.fromDh(2_500),
                    status = LoanStatus.ACTIVE
                ),
                uid
            )
            awaitMainCondition {
                vm.netWorthSnapshot.value.netWorth == Money.fromDh(7_500) &&
                    vm.uiState.value.netWorthSnapshot.netWorth == Money.fromDh(7_500)
            }
            assertEquals(Money.fromDh(7_500), vm.netWorthSnapshot.value.netWorth)
            assertEquals(Money.fromDh(7_500), vm.uiState.value.netWorthSnapshot.netWorth)

            // Repay 500 DH without linked account (e.g. forgiven/external) -> Net Worth rises to 8_000 DH
            val repayRes = repo.recordLoanRepayment("loan_react", Money.fromDh(500), linkedAccountId = null, isoDate = PayCycleEngine.todayIso(), note = "Partiel", userId = uid)
            assertTrue(repayRes is LoanRepaymentResult.Success)
            awaitMainCondition { vm.netWorthSnapshot.value.netWorth == Money.fromDh(8_000) }
            assertEquals(Money.fromDh(8_000), vm.netWorthSnapshot.value.netWorth)
        } finally {
            uiSub.cancel()
        }
    }

    @Test
    fun `backup v3 round-trip preserves loans repayments paired transfers and remains compatible with legacy v2 and pre-phase5 v3 backups`() = runBlocking {
        val today = PayCycleEngine.todayIso()
        repo.saveProfile(UserProfile(userId = uid, baseBudget = Money.fromDh(8000), payCycleDay = 5), uid)
        repo.upsertAccount(AccountItem(id = "acc_bank", userId = uid, name = "CIH", kind = "bank", initialBalance = Money.fromDh(12_000)), uid)
        repo.upsertAccount(AccountItem(id = "acc_cash", userId = uid, name = "Cash", kind = "cash", initialBalance = Money.fromDh(2_000)), uid)

        val trf = repo.executeInternalTransfer("acc_bank", "acc_cash", Money.fromCentimes(150_025L), today, "Virement cash", uid) as InternalTransferResult.Success
        val loanId = repo.upsertLoan(
            LoanItem(
                id = "loan_bkp_1",
                userId = uid,
                title = "Crédit Equipement",
                counterparty = "Attijari",
                direction = LoanDirection.LIABILITY,
                category = LoanCategory.BANK_CREDIT,
                principal = Money.fromCentimes(400_050L),
                remaining = Money.fromCentimes(400_050L),
                monthlyPayment = Money.fromCentimes(50_000L),
                status = LoanStatus.ACTIVE,
                linkedAccountId = "acc_bank"
            ),
            uid
        )
        val rep = repo.recordLoanRepayment(loanId, Money.fromCentimes(50_050L), "acc_bank", today, "Échéance 1", uid) as LoanRepaymentResult.Success

        val vm = MasroufiViewModel(repository = repo, currentUserId = uid)
        val uiSub = launch { vm.uiState.collect {} }
        try {
            awaitMainCondition {
                !vm.uiState.value.isLoading && vm.uiState.value.allTransactions.size == 3
            }

            val passphrase = "Phase5BackupStrongPassword!2026"
            val enc = vm.exportBackupV3(passphrase.toCharArray(), wipePassphrase = true)
            assertTrue(enc is PortableCryptoResult.Success)
            val encryptedJson = (enc as PortableCryptoResult.Success).value

            // Wipe DB completely and restore from encrypted Backup v3
            db.masroufiDao().replaceAllUserData(uid, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
            awaitMainCondition { repo.getLoans(uid).isEmpty() && repo.getLoanRepayments(uid).isEmpty() }
            assertTrue(repo.getLoans(uid).isEmpty())
            assertTrue(repo.getLoanRepayments(uid).isEmpty())

            val restoreOutcome = vm.restoreBackupPortable(
                rawJson = encryptedJson,
                passphrase = passphrase.toCharArray(),
                replaceExisting = true,
                wipePassphrase = true
            )
            assertTrue("Expected Success restore, got $restoreOutcome", restoreOutcome is BackupRestoreResult.Success)
        } finally {
            uiSub.cancel()
        }

        val restoredLoans = repo.getLoans(uid)
        val restoredRepayments = repo.getLoanRepayments(uid)
        val restoredTxs = repo.observeTransactions(uid).first()

        assertEquals(1, restoredLoans.size)
        assertEquals(Money.fromCentimes(400_050L), restoredLoans.first().principal)
        assertEquals(Money.fromCentimes(350_000L), restoredLoans.first().remaining)
        assertEquals(LoanStatus.ACTIVE, restoredLoans.first().status)

        assertEquals(1, restoredRepayments.size)
        assertEquals(rep.repayment.id, restoredRepayments.first().id)
        assertEquals(Money.fromCentimes(50_050L), restoredRepayments.first().amount)
        assertEquals(rep.linkedTransaction!!.id, restoredRepayments.first().linkedTransactionId)

        val restoredGroupTxs = restoredTxs.filter { it.transferGroupId == trf.transferGroupId }
        assertEquals(2, restoredGroupTxs.size)
        assertTrue(restoredTxs.any { it.loanId == loanId && it.type == "transfer_out" })

        // Backward compatibility 1: Pre-Phase-5 v3 backup (no "loans" or "loanRepayments" keys) decodes cleanly
        val prePhase5V3Json = BackupV3Codec.encodeV3Backup(
            state = DashboardState(profile = UserProfile(userId = uid, baseBudget = Money.fromDh(5000))),
            loans = emptyList(),
            loanRepayments = emptyList()
        ).let { raw ->
            JSONObject(raw).apply {
                remove("loans")
                remove("loanRepayments")
                optJSONObject("metadata")?.optJSONObject("recordCounts")?.apply {
                    remove("loans")
                    remove("loanRepayments")
                }
            }.toString()
        }
        val decodedPrePhase5 = BackupSerializer.decodeBackup(prePhase5V3Json, uid, UserProfile(userId = uid))
        assertEquals(3, decodedPrePhase5.backupVersion)
        assertTrue(!decodedPrePhase5.hasLoansSection)
        assertTrue(decodedPrePhase5.loans.isEmpty())

        // Backward compatibility 2: Legacy v2 backup JSON decodes cleanly
        val legacyV2Json = BackupSerializer.encodeV2Backup(
            DashboardState(
                profile = UserProfile(userId = uid, baseBudget = Money.fromDh(6400)),
                debts = listOf(
                    FinancialItem(id = "d_leg", itemType = "debt", name = "Old Debt", amount = Money.fromDh(300), secondaryAmount = Money.fromDh(100), dateOrCycle = "borrowed")
                )
            )
        )
        val decodedV2 = BackupSerializer.decodeBackup(legacyV2Json, uid, UserProfile(userId = uid))
        assertEquals(2, decodedV2.backupVersion)
        assertEquals(1, decodedV2.financialItems.size)
        assertTrue(!decodedV2.hasLoansSection)
    }
}
