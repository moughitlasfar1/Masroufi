package com.example

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountItem
import com.example.data.BackupV3Codec
import com.example.data.CustomizationPrefs
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.data.local.MasroufiDatabase
import com.example.data.local.RoomMasroufiRepository
import com.example.domain.CashFlowForecastEngine
import com.example.domain.ForecastHorizon
import com.example.domain.LoanCategory
import com.example.domain.LoanDirection
import com.example.domain.LoanItem
import com.example.domain.LoanStatus
import com.example.domain.NetWorthCalculator
import com.example.domain.PayCycleEngine
import com.example.domain.RecurrenceDirection
import com.example.domain.RecurrenceFrequency
import com.example.domain.RecurrencePostingMode
import com.example.domain.RecurrenceScheduleEngine
import com.example.domain.RecurringPostResult
import com.example.domain.RecurringRuleItem
import com.example.security.PortableCryptoResult
import com.example.ui.BackupRestoreResult
import com.example.ui.BackupSerializer
import com.example.ui.DashboardState
import com.example.ui.MasroufiViewModel
import com.example.ui.dashboard.localizedDirectionLabel
import com.example.ui.dashboard.localizedFrequencyLabel
import com.example.ui.dashboard.localizedPostingModeLabel
import com.example.ui.dashboardSectionLabel
import com.masroufi.core.Money
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Phase 6 — Recurrence Engine, Cash-Flow Forecasting (aligned with PayCycleEngine),
 * Customizable Dashboard Layout, Backup v3 Portability, and RTL/4-Language Verification Suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase6RecurrenceAndForecastTest {

    private lateinit var db: MasroufiDatabase
    private lateinit var repo: RoomMasroufiRepository
    private val uid = "phase6_test_user"

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

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // 1. Deterministic Recurrence Schedule Engine & Month-End Clamping
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `recurrence schedule engine clamps month-end dates accurately across non-leap and leap years`() {
        // Non-leap year monthly anchor 31: Jan 31 -> Feb 28 -> Mar 31 -> Apr 30 -> May 31
        val feb2026 = RecurrenceScheduleEngine.advanceDate("2026-01-31", RecurrenceFrequency.MONTHLY, 31)
        val mar2026 = RecurrenceScheduleEngine.advanceDate(feb2026, RecurrenceFrequency.MONTHLY, 31)
        val apr2026 = RecurrenceScheduleEngine.advanceDate(mar2026, RecurrenceFrequency.MONTHLY, 31)
        val may2026 = RecurrenceScheduleEngine.advanceDate(apr2026, RecurrenceFrequency.MONTHLY, 31)
        assertEquals("2026-02-28", feb2026)
        assertEquals("2026-03-31", mar2026)
        assertEquals("2026-04-30", apr2026)
        assertEquals("2026-05-31", may2026)

        // Leap year monthly anchor 31: Jan 31 -> Feb 29 -> Mar 31
        val feb2028 = RecurrenceScheduleEngine.advanceDate("2028-01-31", RecurrenceFrequency.MONTHLY, 31)
        val mar2028 = RecurrenceScheduleEngine.advanceDate(feb2028, RecurrenceFrequency.MONTHLY, 31)
        assertEquals("2028-02-29", feb2028)
        assertEquals("2028-03-31", mar2028)

        // Yearly leap anchor 29: 2028-02-29 -> 2029-02-28 -> ... -> 2032-02-29
        val y2029 = RecurrenceScheduleEngine.advanceDate("2028-02-29", RecurrenceFrequency.YEARLY, 29)
        val y2030 = RecurrenceScheduleEngine.advanceDate(y2029, RecurrenceFrequency.YEARLY, 29)
        val y2031 = RecurrenceScheduleEngine.advanceDate(y2030, RecurrenceFrequency.YEARLY, 29)
        val y2032 = RecurrenceScheduleEngine.advanceDate(y2031, RecurrenceFrequency.YEARLY, 29)
        assertEquals("2029-02-28", y2029)
        assertEquals("2032-02-29", y2032)

        // Weekly & Biweekly
        assertEquals("2026-06-08", RecurrenceScheduleEngine.advanceDate("2026-06-01", RecurrenceFrequency.WEEKLY, 1))
        assertEquals("2026-06-15", RecurrenceScheduleEngine.advanceDate("2026-06-01", RecurrenceFrequency.BIWEEKLY, 1))
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // 2. Idempotent Room @Transaction Posting, Skipping, and Auto-Post Worker Logic
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `posting recurring occurrence is strictly idempotent and skip advances without creating transaction`() = runBlocking {
        repo.upsertAccount(AccountItem(id = "acc_bank", userId = uid, name = "CIH", kind = "bank", initialBalance = Money.fromDh(5_000)), uid)

        val ruleId = repo.upsertRecurringRule(
            RecurringRuleItem(
                id = "rec_internet",
                userId = uid,
                title = "Orange Fibre",
                direction = RecurrenceDirection.EXPENSE,
                amount = Money.fromCentimes(24_999L),
                frequency = RecurrenceFrequency.MONTHLY,
                anchorDayOfMonth = 31,
                startIsoDate = "2026-01-31",
                nextDueIsoDate = "2026-01-31",
                postingMode = RecurrencePostingMode.CONFIRM,
                accountId = "acc_bank",
                envelopeKey = "charges",
                category = "charges"
            ),
            uid
        )

        // First post succeeds and advances nextDueIsoDate with month-end clamping (Jan 31 -> Feb 28)
        val first = repo.postRecurringOccurrence(ruleId, "2026-01-31", userId = uid)
        assertTrue(first is RecurringPostResult.Success)
        val ok = first as RecurringPostResult.Success
        assertEquals("rec_internet:2026-01-31", ok.occurrenceKey)
        assertEquals("2026-02-28", ok.updatedRule.nextDueIsoDate)
        assertNotNull(ok.postedTransaction)
        assertEquals("rec_internet:2026-01-31", ok.postedTransaction!!.recurringOccurrenceKey)
        assertEquals(Money.fromCentimes(24_999L), ok.postedTransaction!!.amount)

        // Second post of the exact same occurrence key is idempotently blocked
        val duplicate = repo.postRecurringOccurrence(ruleId, "2026-01-31", userId = uid)
        assertEquals(RecurringPostResult.AlreadyPosted("rec_internet:2026-01-31"), duplicate)
        assertEquals(1, repo.observeTransactions(uid).first().size)

        // Skip Feb 28 -> advances to Mar 31 with zero new transaction
        val skipped = repo.skipRecurringOccurrence(ruleId, "2026-02-28", uid)
        assertTrue(skipped)
        val afterSkip = repo.getRecurringRules(uid).first { it.id == ruleId }
        assertEquals("2026-03-31", afterSkip.nextDueIsoDate)
        assertTrue("rec_internet:2026-02-28" in afterSkip.skippedOccurrenceKeys)
        assertEquals(1, repo.observeTransactions(uid).first().size)
    }

    @Test
    fun `processDueAutoPostRules executes only AUTO_POST due rules once and never touches CONFIRM rules`() = runBlocking {
        repo.upsertAccount(AccountItem(id = "acc_bank", userId = uid, name = "CIH", kind = "bank", initialBalance = Money.fromDh(10_000)), uid)

        repo.upsertRecurringRule(
            RecurringRuleItem(
                id = "rec_auto_salary",
                userId = uid,
                title = "Salaire",
                direction = RecurrenceDirection.INCOME,
                amount = Money.fromDh(8_000),
                frequency = RecurrenceFrequency.MONTHLY,
                anchorDayOfMonth = 1,
                startIsoDate = "2026-05-01",
                nextDueIsoDate = "2026-05-01",
                postingMode = RecurrencePostingMode.AUTO_POST,
                accountId = "acc_bank"
            ),
            uid
        )
        repo.upsertRecurringRule(
            RecurringRuleItem(
                id = "rec_confirm_bill",
                userId = uid,
                title = "Eau & Élec",
                direction = RecurrenceDirection.EXPENSE,
                amount = Money.fromDh(350),
                frequency = RecurrenceFrequency.MONTHLY,
                anchorDayOfMonth = 1,
                startIsoDate = "2026-05-01",
                nextDueIsoDate = "2026-05-01",
                postingMode = RecurrencePostingMode.CONFIRM,
                accountId = "acc_bank"
            ),
            uid
        )

        // First worker/launch run on 2026-05-05 posts only the AUTO_POST rule
        val posted1 = repo.processDueAutoPostRules("2026-05-05", uid)
        assertEquals(1, posted1)
        val txsAfter1 = repo.observeTransactions(uid).first()
        assertEquals(1, txsAfter1.size)
        assertEquals("income", txsAfter1.first().type)
        assertEquals("rec_auto_salary:2026-05-01", txsAfter1.first().recurringOccurrenceKey)

        // Second run on same date is completely idempotent (0 new postings)
        val posted2 = repo.processDueAutoPostRules("2026-05-05", uid)
        assertEquals(0, posted2)
        assertEquals(1, repo.observeTransactions(uid).first().size)
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // 3. Loan-Linked Recurring Rule (Overpayment Protection + Auto-Pause + Net Worth Δ = 0)
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `loan-linked recurring rule enforces overpayment guard auto-pauses on PAID_OFF and keeps net worth delta zero`() = runBlocking {
        repo.upsertAccount(AccountItem(id = "acc_bank", userId = uid, name = "Banque", kind = "bank", initialBalance = Money.fromDh(10_000)), uid)
        val loanId = repo.upsertLoan(
            LoanItem(
                id = "loan_auto",
                userId = uid,
                title = "Crédit Auto",
                direction = LoanDirection.LIABILITY,
                category = LoanCategory.BANK_CREDIT,
                principal = Money.fromDh(2_500),
                remaining = Money.fromDh(2_500),
                monthlyPayment = Money.fromDh(1_500),
                status = LoanStatus.ACTIVE,
                linkedAccountId = "acc_bank"
            ),
            uid
        )
        val ruleId = repo.upsertRecurringRule(
            RecurringRuleItem(
                id = "rec_loan_auto",
                userId = uid,
                title = "Mensualité Auto",
                direction = RecurrenceDirection.LOAN_REPAYMENT,
                amount = Money.fromDh(1_500),
                frequency = RecurrenceFrequency.MONTHLY,
                anchorDayOfMonth = 5,
                startIsoDate = "2026-05-05",
                nextDueIsoDate = "2026-05-05",
                postingMode = RecurrencePostingMode.CONFIRM,
                accountId = "acc_bank",
                linkedLoanId = loanId
            ),
            uid
        )

        val initialNw = NetWorthCalculator.compute(
            repo.observeAccounts(uid).first(),
            repo.observeTransactions(uid).first(),
            repo.getLoans(uid)
        )
        assertEquals(Money.fromDh(7_500), initialNw.netWorth) // 10_000 - 2_500

        // Explicit overpayment attempt (2_600 > 2_500 remaining) is blocked cleanly
        val overpay = repo.postRecurringOccurrence(
            ruleId = ruleId,
            occurrenceIsoDate = "2026-05-05",
            amountOverride = Money.fromDh(2_600),
            userId = uid
        )
        assertTrue(overpay is RecurringPostResult.OverpaymentBlocked)

        // First scheduled installment (1_500 DH) reduces remaining to 1_000 DH; Net Worth Δ == 0
        val p1 = repo.postRecurringOccurrence(ruleId, "2026-05-05", userId = uid) as RecurringPostResult.Success
        assertEquals(Money.fromDh(1_000), p1.loanRepaymentResult!!.updatedLoan.remaining)
        assertFalse(p1.updatedRule.isPaused)
        assertEquals("rec_loan_auto:2026-05-05", p1.postedTransaction!!.recurringOccurrenceKey)
        assertEquals(loanId, p1.postedTransaction!!.loanId)

        val midNw = NetWorthCalculator.compute(
            repo.observeAccounts(uid).first(),
            repo.observeTransactions(uid).first(),
            repo.getLoans(uid)
        )
        assertEquals(Money.fromDh(7_500), midNw.netWorth)

        // Second scheduled installment (rule amount 1_500 clamped cleanly to remaining 1_000) -> PAID_OFF & rule auto-paused!
        val p2 = repo.postRecurringOccurrence(ruleId, "2026-06-05", userId = uid) as RecurringPostResult.Success
        assertEquals(Money.ZERO, p2.loanRepaymentResult!!.updatedLoan.remaining)
        assertEquals(LoanStatus.PAID_OFF, p2.loanRepaymentResult!!.updatedLoan.status)
        assertTrue("Rule must auto-pause once linked loan is PAID_OFF", p2.updatedRule.isPaused)

        val finalNw = NetWorthCalculator.compute(
            repo.observeAccounts(uid).first(),
            repo.observeTransactions(uid).first(),
            repo.getLoans(uid)
        )
        assertEquals(Money.fromDh(7_500), finalNw.netWorth)
        assertEquals(Money.ZERO, finalNw.totalLiabilities)

        // Any further posting against the paid-off loan returns LoanAlreadyPaidOff
        val p3 = repo.postRecurringOccurrence(ruleId, "2026-07-05", userId = uid)
        assertEquals(RecurringPostResult.LoanAlreadyPaidOff(loanId), p3)
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // 4. CashFlowForecastEngine (PayCycle Alignment, Deduplication & Deficit Date Detection)
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `cash flow forecast aligns with pay cycle deduplicates bills and loans and detects mid-cycle deficit date`() {
        val accounts = listOf(
            AccountItem(id = "acc_bank", name = "CIH", kind = "bank", initialBalance = Money.fromDh(2_000))
        )
        val nw = NetWorthCalculator.compute(accounts, emptyList())

        // 1. Migrated legacy bill (covered by rec_rent via sourceFinancialItemId) -> MUST NOT double count
        val legacyBill = FinancialItem(
            id = "bill_rent",
            itemType = "bill",
            name = "Loyer",
            amount = Money.fromDh(2_500),
            dayOrTurn = 10
        )
        // 2. Active loan covered by rec_loan via linkedLoanId -> MUST NOT double count
        val linkedLoan = LoanItem(
            id = "loan_linked",
            title = "Crédit Conso",
            direction = LoanDirection.LIABILITY,
            principal = Money.fromDh(10_000),
            remaining = Money.fromDh(5_000),
            monthlyPayment = Money.fromDh(500),
            dueIsoDate = "2026-06-12"
        )
        // 3. Unlinked active loan -> counted ONCE as LOAN_INSTALLMENT
        val unlinkedLoan = LoanItem(
            id = "loan_unlinked",
            title = "Crédit Auto",
            direction = LoanDirection.LIABILITY,
            principal = Money.fromDh(20_000),
            remaining = Money.fromDh(10_000),
            monthlyPayment = Money.fromDh(400),
            dueIsoDate = "2026-06-18"
        )

        val rules = listOf(
            RecurringRuleItem(
                id = "rec_rent",
                title = "Loyer",
                direction = RecurrenceDirection.EXPENSE,
                amount = Money.fromDh(2_500),
                frequency = RecurrenceFrequency.MONTHLY,
                anchorDayOfMonth = 10,
                startIsoDate = "2026-06-10",
                nextDueIsoDate = "2026-06-10",
                sourceFinancialItemId = "bill_rent"
            ),
            RecurringRuleItem(
                id = "rec_loan",
                title = "Mensualité Conso",
                direction = RecurrenceDirection.LOAN_REPAYMENT,
                amount = Money.fromDh(500),
                frequency = RecurrenceFrequency.MONTHLY,
                anchorDayOfMonth = 12,
                startIsoDate = "2026-06-12",
                nextDueIsoDate = "2026-06-12",
                linkedLoanId = "loan_linked"
            ),
            RecurringRuleItem(
                id = "rec_freelance",
                title = "Mission Freelance",
                direction = RecurrenceDirection.INCOME,
                amount = Money.fromDh(3_000),
                frequency = RecurrenceFrequency.MONTHLY,
                anchorDayOfMonth = 20,
                startIsoDate = "2026-06-20",
                nextDueIsoDate = "2026-06-20"
            )
        )

        // Active pay cycle: payCycleDay = 1, today = 2026-06-05 -> window = 2026-06-05..2026-06-30 (26 days)
        val snapshot = CashFlowForecastEngine.compute(
            netWorthSnapshot = nw,
            payCycleDay = 1,
            todayIso = "2026-06-05",
            horizon = ForecastHorizon.ACTIVE_PAY_CYCLE,
            recurringRules = rules,
            loans = listOf(linkedLoan, unlinkedLoan),
            legacyFinancialItems = listOf(legacyBill),
            transactions = emptyList()
        )

        assertEquals("2026-06-05", snapshot.windowStartIso)
        assertEquals("2026-06-30", snapshot.windowEndIso)
        assertEquals(26, snapshot.daysRemaining)
        assertEquals(Money.fromDh(2_000), snapshot.startingLiquidBalance)
        // Committed bills = 2_500 (rent ONCE) + 500 (linked loan ONCE) + 400 (unlinked loan ONCE) = 3_400 DH
        assertEquals(Money.fromDh(3_400), snapshot.committedUpcomingBills)
        assertEquals(Money.fromDh(3_000), snapshot.scheduledIncome)
        // End balance = 2_000 + 3_000 - 3_400 = 1_600 DH
        assertEquals(Money.fromDh(1_600), snapshot.projectedEndBalance)
        // Daily safe-to-spend = 160_000 / 26 = 6_153 centimes (exact integer division)
        assertEquals(Money.fromCentimes(160_000L / 26L), snapshot.dailySafeToSpend)

        // Running balance timeline:
        // Start: +2_000
        // 2026-06-10 (-2_500): -500  <-- earliest deficit date!
        // 2026-06-12 (-500):   -1_000
        // 2026-06-18 (-400):   -1_400 <-- deepest deficit trough!
        // 2026-06-20 (+3_000): +1_600
        assertTrue(snapshot.hasProjectedDeficit)
        assertEquals("2026-06-10", snapshot.projectedDeficitDateIso)
        assertEquals(Money.fromDh(1_400), snapshot.projectedDeficitAmount)

        // Rolling 30-day horizon from 2026-06-05 spans 30 inclusive days (2026-06-05..2026-07-04)
        val rolling = CashFlowForecastEngine.compute(
            netWorthSnapshot = nw,
            payCycleDay = 1,
            todayIso = "2026-06-05",
            horizon = ForecastHorizon.ROLLING_30_DAYS,
            recurringRules = rules,
            loans = listOf(linkedLoan, unlinkedLoan),
            legacyFinancialItems = listOf(legacyBill)
        )
        assertEquals(30, rolling.daysRemaining)
        assertEquals("2026-07-04", rolling.windowEndIso)
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // 5. Reactive ViewModel Forecast StateFlow + Customizable Dashboard Layout Persistence
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `viewModel exposes reactive cashFlowForecast and persists dashboard layout customization and reset`() = runBlocking {
        repo.saveProfile(UserProfile(userId = uid, baseBudget = Money.fromDh(6_000), payCycleDay = 1), uid)
        repo.upsertAccount(AccountItem(id = "acc_cash", userId = uid, name = "Cash", kind = "cash", initialBalance = Money.fromDh(4_000)), uid)

        val vm = MasroufiViewModel(repository = repo, currentUserId = uid)
        val uiSub = launch { vm.uiState.collect {} }
        try {
            awaitMainCondition {
                !vm.uiState.value.isLoading && vm.cashFlowForecast.value.startingLiquidBalance == Money.fromDh(4_000)
            }
            assertEquals(Money.fromDh(4_000), vm.cashFlowForecast.value.startingLiquidBalance)
            assertEquals(ForecastHorizon.ACTIVE_PAY_CYCLE, vm.forecastHorizon.value)

            // Switch forecast horizon to ROLLING_30_DAYS
            vm.setForecastHorizon(ForecastHorizon.ROLLING_30_DAYS)
            awaitMainCondition { vm.cashFlowForecast.value.horizon == ForecastHorizon.ROLLING_30_DAYS }
            assertEquals(30, vm.cashFlowForecast.value.daysRemaining)

            // Toggle visibility & reorder dashboard cards, then verify persistence
            val customized = vm.uiState.value.customPrefs
                .copy(showForecastCard = false, showRecentTransactionsCard = false)
                .withMovedSection("accounts", -1)
            vm.saveCustomizationPrefs(customized)
            awaitMainCondition {
                !vm.uiState.value.customPrefs.showForecastCard &&
                    !vm.uiState.value.customPrefs.showRecentTransactionsCard
            }
            assertFalse(vm.uiState.value.customPrefs.showForecastCard)
            assertFalse(vm.uiState.value.customPrefs.showRecentTransactionsCard)

            // Reset to Default restores default order & all default visibility flags
            vm.resetDashboardLayout()
            awaitMainCondition {
                vm.uiState.value.customPrefs.showForecastCard &&
                    vm.uiState.value.customPrefs.showRecentTransactionsCard &&
                    vm.uiState.value.customPrefs.resolvedDashboardOrder() == CustomizationPrefs.DEFAULT_EXTENDED_DASHBOARD_ORDER
            }
            assertTrue(vm.uiState.value.customPrefs.showForecastCard)
            assertEquals(
                CustomizationPrefs.DEFAULT_EXTENDED_DASHBOARD_ORDER,
                vm.uiState.value.customPrefs.resolvedDashboardOrder()
            )
        } finally {
            uiSub.cancel()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // 6. Backup v3 Portability Round-Trip + Backward Compatibility (Pre-Phase-6 v3 & Legacy v2)
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `backup v3 round-trip preserves recurring rules occurrence keys and dashboard layout while remaining backward compatible`() = runBlocking {
        val today = PayCycleEngine.todayIso()
        repo.saveProfile(UserProfile(userId = uid, baseBudget = Money.fromDh(7_000), payCycleDay = 1), uid)
        repo.upsertAccount(AccountItem(id = "acc_bank", userId = uid, name = "CIH", kind = "bank", initialBalance = Money.fromDh(9_000)), uid)

        val ruleId = repo.upsertRecurringRule(
            RecurringRuleItem(
                id = "rec_bkp_gym",
                userId = uid,
                title = "Salle Sport",
                direction = RecurrenceDirection.EXPENSE,
                amount = Money.fromCentimes(29_950L),
                frequency = RecurrenceFrequency.MONTHLY,
                anchorDayOfMonth = 15,
                startIsoDate = today,
                nextDueIsoDate = today,
                postingMode = RecurrencePostingMode.CONFIRM,
                accountId = "acc_bank",
                envelopeKey = "loisirs",
                category = "loisirs"
            ),
            uid
        )
        val post = repo.postRecurringOccurrence(ruleId, today, userId = uid) as RecurringPostResult.Success

        val vm = MasroufiViewModel(repository = repo, currentUserId = uid)
        val uiSub = launch { vm.uiState.collect {} }
        try {
            awaitMainCondition {
                !vm.uiState.value.isLoading && vm.uiState.value.recurringRules.size == 1
            }

            // Customize dashboard layout before export
            vm.saveCustomizationPrefs(
                vm.uiState.value.customPrefs
                    .copy(showUpcomingRecurringCard = false)
                    .withMovedSection("accounts", -1)
            )
            awaitMainCondition { !vm.uiState.value.customPrefs.showUpcomingRecurringCard }

            val passphrase = "Phase6StrongBackupPassphrase!2026"
            val enc = vm.exportBackupV3(passphrase.toCharArray(), wipePassphrase = true)
            assertTrue(enc is PortableCryptoResult.Success)
            val encryptedJson = (enc as PortableCryptoResult.Success).value

            // Wipe DB completely and restore
            db.masroufiDao().replaceAllUserData(
                uid, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                emptyList(), emptyList(), emptyList()
            )
            awaitMainCondition { repo.getRecurringRules(uid).isEmpty() }
            assertTrue(repo.getRecurringRules(uid).isEmpty())

            val restoreOutcome = vm.restoreBackupPortable(
                rawJson = encryptedJson,
                passphrase = passphrase.toCharArray(),
                replaceExisting = true,
                wipePassphrase = true
            )
            assertTrue("Expected Success restore, got $restoreOutcome", restoreOutcome is BackupRestoreResult.Success)
            awaitMainCondition {
                repo.getRecurringRules(uid).size == 1 && !vm.uiState.value.customPrefs.showUpcomingRecurringCard
            }
            assertFalse(vm.uiState.value.customPrefs.showUpcomingRecurringCard)
        } finally {
            uiSub.cancel()
        }

        val restoredRules = repo.getRecurringRules(uid)
        assertEquals(1, restoredRules.size)
        assertEquals("rec_bkp_gym", restoredRules.first().id)
        assertEquals(Money.fromCentimes(29_950L), restoredRules.first().amount)
        assertEquals(post.occurrenceKey, restoredRules.first().lastPostedOccurrenceKey)

        val restoredTxs = repo.observeTransactions(uid).first()
        assertEquals(1, restoredTxs.size)
        assertEquals(post.occurrenceKey, restoredTxs.first().recurringOccurrenceKey)

        // Backward compatibility 1: Pre-Phase-6 v3 backup (no "recurringRules" section) decodes cleanly
        val prePhase6V3Json = BackupV3Codec.encodeV3Backup(
            state = DashboardState(profile = UserProfile(userId = uid, baseBudget = Money.fromDh(5_000))),
            loans = emptyList(),
            loanRepayments = emptyList(),
            recurringRules = emptyList()
        ).let { raw ->
            JSONObject(raw).apply {
                remove("recurringRules")
                optJSONObject("metadata")?.optJSONObject("recordCounts")?.remove("recurringRules")
                optJSONObject("customPrefs")?.apply {
                    remove("showForecastCard")
                    remove("showUpcomingRecurringCard")
                    remove("showRecentTransactionsCard")
                    remove("extendedDashboardOrder")
                }
            }.toString()
        }
        val decodedPre6 = BackupSerializer.decodeBackup(prePhase6V3Json, uid, UserProfile(userId = uid))
        assertEquals(3, decodedPre6.backupVersion)
        assertFalse(decodedPre6.hasRecurringRulesSection)
        assertTrue(decodedPre6.recurringRules.isEmpty())

        // Backward compatibility 2: Legacy v2 backup JSON decodes cleanly
        val legacyV2Json = BackupSerializer.encodeV2Backup(
            DashboardState(profile = UserProfile(userId = uid, baseBudget = Money.fromDh(6_000)))
        )
        val decodedV2 = BackupSerializer.decodeBackup(legacyV2Json, uid, UserProfile(userId = uid))
        assertEquals(2, decodedV2.backupVersion)
        assertFalse(decodedV2.hasRecurringRulesSection)
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // 7. 4-Language Localization, Privacy Masking & RTL Sanity Checks
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `localization covers all 4 languages for dashboard sections recurrence labels and privacy masking`() {
        val languages = listOf("fr", "ar", "darija", "en")

        for (lang in languages) {
            for (sectionKey in CustomizationPrefs.DEFAULT_EXTENDED_DASHBOARD_ORDER) {
                val label = dashboardSectionLabel(sectionKey, lang)
                assertTrue("Blank section label for $sectionKey in $lang", label.isNotBlank())
            }
            for (freq in RecurrenceFrequency.entries) {
                assertTrue("Blank frequency label for $freq in $lang", localizedFrequencyLabel(freq, lang).isNotBlank())
            }
            for (dir in RecurrenceDirection.entries) {
                assertTrue("Blank direction label for $dir in $lang", localizedDirectionLabel(dir, lang).isNotBlank())
            }
            for (mode in RecurrencePostingMode.entries) {
                assertTrue("Blank posting mode label for $mode in $lang", localizedPostingModeLabel(mode, lang).isNotBlank())
            }
        }

        // Arabic labels contain Arabic script characters (confirming proper RTL localization, not French fallback)
        val arForecast = dashboardSectionLabel("forecast", "ar")
        val darijaForecast = dashboardSectionLabel("forecast", "darija")
        assertTrue("Arabic forecast label should contain Arabic script: $arForecast", arForecast.any { it in '\u0600'..'\u06FF' })
        assertTrue("Darija forecast label should contain Arabic script: $darijaForecast", darijaForecast.any { it in '\u0600'..'\u06FF' })

        // hideAmounts privacy mask hides exact centime values completely
        assertEquals("••••", PayCycleEngine.formatAmountOrMask(Money.fromCentimes(123_456L), hideAmounts = true))
        assertTrue(PayCycleEngine.formatAmountOrMask(Money.fromCentimes(123_456L), hideAmounts = false).contains("1"))
    }
}
