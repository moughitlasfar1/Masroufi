package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountItem
import com.example.data.EnvelopeConfig
import com.example.data.LocalRoomMasroufiDataSource
import com.example.data.TransactionItem
import com.example.data.local.MasroufiDatabase
import com.example.data.local.NoOpRemoteMirror
import com.example.data.local.RoomMasroufiRepository
import com.example.domain.FinancialInsightsEngine
import com.example.domain.FtsQuerySanitizer
import com.example.domain.InsightAnomalyKind
import com.example.domain.PayCycleEngine
import com.example.domain.TransactionSearchCriteria
import com.example.ui.MasroufiViewModel
import com.example.ui.dashboard.formatPercentBadge
import com.example.ui.dashboard.formatSignedDeltaOrMask
import com.example.ui.dashboard.insightsStringsFor
import com.masroufi.core.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

/**
 * Phase 7A — FTS4 Multi-Criteria Search & Pure Centimes Financial Insights Verification Suite.
 *
 * Covers:
 * 1. `FtsQuerySanitizer` operator-injection neutralization + Unicode Latin/French/Arabic/Darija tokens.
 * 2. Real Room FTS4 + multi-criteria exact `Long`-centimes SQL queries (French accents, Arabic,
 *    Darija, English, date range, min/max centimes, account, category, type including paired
 *    `transfer_out`/`transfer_in`, and loan link).
 * 3. Pure `FinancialInsightsEngine` (`Long` centimes, aligned with `PayCycleEngine`):
 *    - Category trends across pay cycles
 *    - Category spike detection (>= +25% & >= 200 DH)
 *    - Large single-transaction outlier detection (>= 2x category avg & >= 300 DH)
 *    - Suspected duplicate expense detection (exact centimes + normalized label within <= 2 days)
 *    - Strict exclusion of internal transfers (`transfer`, `transfer_out`, `transfer_in`, `transferGroupId`)
 *    - Distinct recurring occurrences NOT flagged as duplicates or large outliers.
 * 4. Reactive `MasroufiViewModel.financialInsights` & `MasroufiViewModel.searchResults` flows.
 * 5. All 4 languages (`fr`, `ar`, `darija`, `en`) and `hideAmounts` (`••••`) privacy masking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase7ASearchAndInsightsTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var db: MasroufiDatabase
    private lateinit var repo: RoomMasroufiRepository
    private val userId = "u_phase7a"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroufiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomMasroufiRepository(
            dao = db.masroufiDao(),
            database = db,
            remoteMirror = NoOpRemoteMirror
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `FtsQuerySanitizer neutralizes operators and preserves French Arabic Darija and English tokens`() {
        assertNull(FtsQuerySanitizer.buildFtsMatchExpression("   "))
        assertNull(FtsQuerySanitizer.buildFtsMatchExpression("\"*** () --- AND OR NOT\""))
        assertEquals("café* crème*", FtsQuerySanitizer.buildFtsMatchExpression("café \"crème\" OR NOT *"))
        assertEquals("مرجان* تقدية*", FtsQuerySanitizer.buildFtsMatchExpression("مرجان - تقدية!"))
        assertEquals("9ahwa* 25*", FtsQuerySanitizer.buildFtsMatchExpression("9ahwa:25"))
    }

    @Test
    fun `Room FTS4 search supports 4 languages and exact Long centimes multi-criteria filters`() = runBlocking {
        repo.saveAccount(userId, AccountItem(id = "acc_cash", userId = userId, name = "Cash", kind = "cash", initialBalance = Money.fromDh(5000)))
        repo.saveAccount(userId, AccountItem(id = "acc_cih", userId = userId, name = "CIH", kind = "bank", initialBalance = Money.fromDh(10000)))

        val txs = listOf(
            TransactionItem(
                id = "t_fr", type = "expense", amount = Money.fromCentimes(3550L),
                category = "Café", envelopeKey = "cafe", accountId = "acc_cash",
                desc = "Café Crème Étoile", isoDate = "2026-08-05"
            ),
            TransactionItem(
                id = "t_ar", type = "expense", amount = Money.fromCentimes(42000L),
                category = "بقالة", envelopeKey = "alimentation", accountId = "acc_cih",
                desc = "تقدية مرجان الشهرية", isoDate = "2026-08-08"
            ),
            TransactionItem(
                id = "t_darija", type = "expense", amount = Money.fromCentimes(8000L),
                category = "قهوة", envelopeKey = "cafe", accountId = "acc_cash",
                desc = "9ahwa m3a shab f derb", isoDate = "2026-08-10"
            ),
            TransactionItem(
                id = "t_loan", type = "expense", amount = Money.fromCentimes(150000L),
                category = "Prêt", envelopeKey = "autre", accountId = "acc_cih",
                loanId = "loan_car_1", desc = "Mensualité Crédit Auto", isoDate = "2026-08-12"
            ),
            TransactionItem(
                id = "t_tr_out", type = "transfer_out", amount = Money.fromCentimes(50000L),
                category = "Virement", envelopeKey = "autre", accountId = "acc_cih",
                toAccountId = "acc_cash", transferGroupId = "grp_1",
                desc = "Retrait GAB", isoDate = "2026-08-14"
            ),
            TransactionItem(
                id = "t_tr_in", type = "transfer_in", amount = Money.fromCentimes(50000L),
                category = "Virement", envelopeKey = "autre", accountId = "acc_cash",
                toAccountId = "acc_cih", transferGroupId = "grp_1",
                desc = "Retrait GAB", isoDate = "2026-08-14"
            )
        )
        txs.forEach { repo.saveTransaction(userId, it) }

        // 1. French accent-insensitive FTS4 query
        val frRes = repo.searchTransactions(userId, TransactionSearchCriteria(query = "cafe etoile"))
        assertEquals(listOf("t_fr"), frRes.map { it.id })

        // 2. Arabic FTS4 query
        val arRes = repo.searchTransactions(userId, TransactionSearchCriteria(query = "مرجان"))
        assertEquals(listOf("t_ar"), arRes.map { it.id })

        // 3. Darija FTS4 query with hostile FTS punctuation
        val darRes = repo.searchTransactions(userId, TransactionSearchCriteria(query = "\"9ahwa\" OR (shab)*"))
        assertEquals(listOf("t_darija"), darRes.map { it.id })

        // 4. Exact Long centimes min/max range filter (35.50 DH to 80.00 DH inclusive)
        val rangeRes = repo.searchTransactions(
            userId,
            TransactionSearchCriteria(minAmountCentimes = 3550L, maxAmountCentimes = 8000L)
        )
        assertEquals(setOf("t_fr", "t_darija"), rangeRes.map { it.id }.toSet())

        // 5. Transfer filter matches paired transfer_out + transfer_in rows
        val transferRes = repo.searchTransactions(userId, TransactionSearchCriteria(type = "transfer"))
        assertEquals(setOf("t_tr_out", "t_tr_in"), transferRes.map { it.id }.toSet())

        // 6. Loan-linked filter & specific loanId filter
        val loanRes = repo.searchTransactions(
            userId,
            TransactionSearchCriteria(loanLinkedOnly = true, loanId = "loan_car_1")
        )
        assertEquals(listOf("t_loan"), loanRes.map { it.id })

        // 7. Combined date range + account filter
        val dateAccRes = repo.searchTransactions(
            userId,
            TransactionSearchCriteria(startIsoDate = "2026-08-06", endIsoDate = "2026-08-13", accountId = "acc_cih")
        )
        assertEquals(setOf("t_ar", "t_loan"), dateAccRes.map { it.id }.toSet())
    }

    @Test
    fun `FinancialInsightsEngine computes exact trends spikes outliers duplicates and strictly excludes transfers`() {
        val envelopes = listOf(
            EnvelopeConfig(id = "e1", key = "alimentation", nameFr = "Alimentation", nameAr = "بقالة", nameDarija = "تقدية", nameEn = "Groceries", percentage = 40.0),
            EnvelopeConfig(id = "e2", key = "cafe", nameFr = "Café", nameAr = "مقهى", nameDarija = "قهوة", nameEn = "Cafe", percentage = 20.0),
            EnvelopeConfig(id = "e3", key = "transport", nameFr = "Transport", nameAr = "نقل", nameDarija = "طونسبور", nameEn = "Transport", percentage = 20.0)
        )
        val todayIso = "2026-09-20"

        val txs = listOf(
            // Previous pay cycle (2026-08): Alimentation = 1000 DH, Cafe = 300 DH (4 historical tx for avg)
            TransactionItem(id = "p1", type = "expense", amount = Money.fromDh(1000), category = "Alimentation", envelopeKey = "alimentation", desc = "Marjane", isoDate = "2026-08-10"),
            TransactionItem(id = "p2", type = "expense", amount = Money.fromDh(100), category = "Café", envelopeKey = "cafe", desc = "Café A", isoDate = "2026-08-05"),
            TransactionItem(id = "p3", type = "expense", amount = Money.fromDh(100), category = "Café", envelopeKey = "cafe", desc = "Café B", isoDate = "2026-08-12"),
            TransactionItem(id = "p4", type = "expense", amount = Money.fromDh(100), category = "Café", envelopeKey = "cafe", desc = "Café C", isoDate = "2026-08-18"),

            // Current pay cycle (2026-09):
            // Alimentation jumps from 1000 DH -> 1500 DH (+50% spike >= +25% & >= 200 DH)
            TransactionItem(id = "c1", type = "expense", amount = Money.fromDh(1500), category = "Alimentation", envelopeKey = "alimentation", desc = "Carrefour", isoDate = "2026-09-05"),
            // Cafe has a large single outlier: 450 DH (>= 2x avg 100 DH & >= 300 DH)
            TransactionItem(id = "c2", type = "expense", amount = Money.fromDh(450), category = "Café", envelopeKey = "cafe", desc = "Brunch Famille", isoDate = "2026-09-08"),
            // Suspected duplicate: same exact centimes (75.50 DH), same normalized label, 1 day apart
            TransactionItem(id = "c_dup1", type = "expense", amount = Money.fromCentimes(7550L), category = "Transport", envelopeKey = "transport", desc = "InDrive  Casa", isoDate = "2026-09-12"),
            TransactionItem(id = "c_dup2", type = "expense", amount = Money.fromCentimes(7550L), category = "Transport", envelopeKey = "transport", desc = "indrive casa", isoDate = "2026-09-13"),
            // Distinct recurring postings with same amount/label must NOT be flagged as duplicate
            TransactionItem(id = "c_rec1", type = "expense", amount = Money.fromDh(200), category = "Transport", envelopeKey = "transport", desc = "Abonnement", isoDate = "2026-09-01", recurringOccurrenceKey = "rule_sub:2026-09-01"),
            TransactionItem(id = "c_rec2", type = "expense", amount = Money.fromDh(200), category = "Transport", envelopeKey = "transport", desc = "Abonnement", isoDate = "2026-09-02", recurringOccurrenceKey = "rule_sub:2026-09-02"),

            // Internal transfers (legacy + paired legs) MUST be strictly excluded from spending totals & anomalies
            TransactionItem(id = "tr_legacy", type = "transfer", amount = Money.fromDh(9000), category = "Virement", envelopeKey = "autre", desc = "Transfert", isoDate = "2026-09-10"),
            TransactionItem(id = "tr_out", type = "transfer_out", amount = Money.fromDh(8000), category = "Virement", envelopeKey = "autre", transferGroupId = "g_99", desc = "Virement CIH", isoDate = "2026-09-11"),
            TransactionItem(id = "tr_in", type = "transfer_in", amount = Money.fromDh(8000), category = "Virement", envelopeKey = "autre", transferGroupId = "g_99", desc = "Virement CIH", isoDate = "2026-09-11"),
            // Defensive: even an expense carrying a transferGroupId is excluded
            TransactionItem(id = "tr_tagged", type = "expense", amount = Money.fromDh(7000), category = "Virement", envelopeKey = "autre", transferGroupId = "g_100", desc = "Tagged", isoDate = "2026-09-11")
        )

        val snap = FinancialInsightsEngine.compute(
            transactions = txs,
            envelopes = envelopes,
            payCycleDay = 1,
            todayIso = todayIso
        )

        // Previous total = 1000 + 300 = 1300 DH (130_000L)
        // Current total = 1500 + 450 + 75.50 + 75.50 + 200 + 200 = 2501 DH (250_100L) — ZERO transfer pollution
        assertEquals(Money.fromCentimes(130_000L), snap.previousCycleExpenseTotal)
        assertEquals(Money.fromCentimes(250_100L), snap.currentCycleExpenseTotal)
        assertEquals(Money.fromCentimes(120_100L), snap.totalExpenseDelta)
        assertEquals(92, snap.totalExpensePercentChange) // (120_100 * 100) / 130_000 = 92%
        assertTrue(snap.hasEnoughHistory)

        // Category trend for alimentation: +500 DH (+50%)
        val alimTrend = snap.categoryTrends.firstOrNull { it.envelopeKey == "alimentation" }
        assertNotNull(alimTrend)
        assertEquals(Money.fromDh(500), alimTrend!!.delta)
        assertEquals(50, alimTrend.percentChange)

        // Category spike detected for alimentation
        val spike = snap.anomalies.firstOrNull {
            it.kind == InsightAnomalyKind.CATEGORY_SPIKE && it.envelopeKey == "alimentation"
        }
        assertNotNull("Expected CATEGORY_SPIKE for alimentation", spike)
        assertEquals(50, spike!!.percentAboveBaseline)

        // Large single transaction outlier detected for c2 (450 DH vs 100 DH avg = +350%)
        val outlier = snap.anomalies.firstOrNull {
            it.kind == InsightAnomalyKind.LARGE_TRANSACTION && it.transactionId == "c2"
        }
        assertNotNull("Expected LARGE_TRANSACTION outlier for c2", outlier)
        assertEquals(Money.fromDh(450), outlier!!.currentAmount)

        // Duplicate suspect detected for c_dup1 + c_dup2 ONLY (recurring posts ignored)
        assertEquals(1, snap.duplicateSuspects.size)
        val dup = snap.duplicateSuspects.first()
        assertEquals(Money.fromCentimes(7550L), dup.amount)
        assertEquals("c_dup1", dup.firstTransactionId)
        assertEquals("c_dup2", dup.secondTransactionId)
        assertEquals(1, dup.daysApart)
    }

    @Test
    fun `MasroufiViewModel exposes reactive financialInsights and FTS4 searchResults StateFlows`() = runBlocking {
        val dataSource = LocalRoomMasroufiDataSource(repo)
        val today = PayCycleEngine.todayIso()
        val prevCycleDate = PayCycleEngine.epochDaysToIso(PayCycleEngine.parseEpochDays(today) - 32L)

        repo.saveTransaction(
            userId,
            TransactionItem(
                id = "vm_prev", type = "expense", amount = Money.fromDh(400),
                category = "Alimentation", envelopeKey = "alimentation", accountId = "acc_cash",
                desc = "Marjane", isoDate = prevCycleDate
            )
        )
        repo.saveTransaction(
            userId,
            TransactionItem(
                id = "vm_cur", type = "expense", amount = Money.fromDh(900),
                category = "Alimentation", envelopeKey = "alimentation", accountId = "acc_cash",
                desc = "Carrefour Hyper", isoDate = today
            )
        )

        val vm = MasroufiViewModel(repository = dataSource, currentUserId = userId)
        delay(120)

        assertEquals(Money.fromDh(900), vm.financialInsights.value.currentCycleExpenseTotal)
        assertTrue(vm.uiState.value.financialInsights.categoryTrends.any { it.envelopeKey == "alimentation" })

        vm.updateFilter { it.copy(searchQuery = "carrefour") }
        delay(120)

        assertTrue(vm.uiState.value.isFtsSearchActive)
        assertEquals(listOf("vm_cur"), vm.searchResults.value.map { it.id })
        assertEquals(listOf("vm_cur"), vm.uiState.value.filteredHistory.map { it.id })

        vm.clearTransactionFilters()
        delay(80)
        assertFalse(vm.uiState.value.isFtsSearchActive)
    }

    @Test
    fun `localized insights strings cover all 4 languages and hideAmounts masks monetary and percent values`() {
        listOf("fr", "ar", "darija", "en").forEach { lang ->
            val s = insightsStringsFor(lang)
            assertTrue("cardTitle blank for $lang", s.cardTitle.isNotBlank())
            assertTrue("detailTitle blank for $lang", s.detailTitle.isNotBlank())
            assertTrue("advancedFiltersTitle blank for $lang", s.advancedFiltersTitle.isNotBlank())
            assertTrue("alertsBadgeFormat blank for $lang", s.alertsBadgeFormat(2).contains("2"))
        }

        // Privacy masking check
        val maskedDelta = formatSignedDeltaOrMask(Money.fromDh(450), "DH", hideAmounts = true)
        assertEquals("•••• DH", maskedDelta)
        assertFalse(maskedDelta.contains("450"))

        val visibleDelta = formatSignedDeltaOrMask(Money.fromDh(450), "DH", hideAmounts = false)
        assertTrue(visibleDelta.startsWith("+"))
        assertTrue(visibleDelta.contains("450"))

        assertEquals("••%", formatPercentBadge(42, hideAmounts = true))
        assertEquals("+42%", formatPercentBadge(42, hideAmounts = false))
        assertEquals("-15%", formatPercentBadge(-15, hideAmounts = false))
    }
}
