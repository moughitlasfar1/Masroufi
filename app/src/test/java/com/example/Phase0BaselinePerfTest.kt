package com.example

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountItem
import com.example.data.EnvelopeConfig
import com.example.data.local.MasroufiDatabaseProvider
import com.example.data.local.MasroufiEntityMappers.toEntity
import com.example.data.local.RoomMasroufiRepository
import com.example.data.local.TransactionEntity
import com.example.domain.PayCycleEngine
import com.example.security.DatabaseKeyManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.system.measureNanoTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 0 (Step 0.1) — Baseline Performance Harness (5,000 transactions across 12 months).
 *
 * Measures and logs three core timings on v1.2.1 without brittle ms assertions:
 * 1. Bulk insert of 5,000 realistic Moroccan transactions into Room.
 * 2. Full history query (ordered DESC) + Room-entity-to-domain mapping via RoomMasroufiRepository.
 * 3. Typical PayCycleEngine dashboard computation (cycle window, account balances, envelopes with
 *    rollover, overspend projection, and recurring subscription detection) over all 5,000 rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase0BaselinePerfTest {

    private lateinit var context: Context
    private val testDbName = "masroufi_phase0_perf_5k.db"
    private val testUserId = "uid_phase0_perf"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MasroufiDatabaseProvider.resetForTesting()
        DatabaseKeyManager.deleteDatabaseArtifacts(context, testDbName)
        context.getSharedPreferences("masroufi_secure_kv_v1", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        MasroufiDatabaseProvider.resetForTesting()
        DatabaseKeyManager.deleteDatabaseArtifacts(context, testDbName)
    }

    @Test
    fun `baseline benchmark 5000 transactions across 12 months`() = runBlocking(Dispatchers.IO) {
        val db = checkNotNull(
            MasroufiDatabaseProvider.getOrOpen(
                context = context,
                dbName = testDbName,
                customFactory = FrameworkSQLiteOpenHelperFactory()
            )
        ) { "Expected Room test database to open cleanly" }

        val dao = db.masroufiDao()
        val repo = RoomMasroufiRepository(
            dao = dao,
            userIdProvider = { testUserId }
        )

        val nowMs = 1_795_564_800_000L // deterministic baseline timestamp (2026-11-25)
        val accounts = listOf(
            AccountItem(id = "acc_cash", userId = testUserId, name = "Espèces (Cash)", kind = "cash", initialBalance = 2500.0),
            AccountItem(id = "acc_bank", userId = testUserId, name = "CIH Bank", kind = "bank", initialBalance = 18000.0),
            AccountItem(id = "acc_wallet", userId = testUserId, name = "Portefeuille Mobile", kind = "wallet", initialBalance = 900.0)
        )
        val envelopes = listOf(
            EnvelopeConfig(id = "env_transport", userId = testUserId, key = "transport", nameFr = "Transport", percentage = 20.0, hasCap = true),
            EnvelopeConfig(id = "env_charges", userId = testUserId, key = "charges", nameFr = "Charges & Marché", percentage = 35.0, hasCap = true),
            EnvelopeConfig(id = "env_cafe", userId = testUserId, key = "cafe", nameFr = "Café & Sorties", percentage = 20.0, hasCap = true),
            EnvelopeConfig(id = "env_epargne", userId = testUserId, key = "epargne", nameFr = "Épargne", percentage = 15.0, hasCap = true),
            EnvelopeConfig(id = "env_autre", userId = testUserId, key = "autre", nameFr = "Autre", percentage = 10.0, hasCap = false)
        )

        dao.upsertAccounts(accounts.mapIndexed { idx, acc -> acc.toEntity(testUserId, sortOrder = idx, nowMs = nowMs) })
        dao.upsertEnvelopes(envelopes.map { it.toEntity(testUserId, nowMs = nowMs) })

        val seedEntities = buildDeterministicTransactions(
            userId = testUserId,
            count = 5_000,
            endDateIso = "2026-11-25",
            baseEpochMs = nowMs
        )
        assertEquals(5_000, seedEntities.size)

        // 1. Measure bulk insert time (Room @Insert upsertTransactions)
        val bulkInsertNs = measureNanoTime {
            dao.upsertTransactions(seedEntities)
        }
        val persistedCount = dao.countTransactions(testUserId)
        assertEquals("All 5,000 transactions must be persisted", 5_000, persistedCount)

        // 2. Measure full history query (ORDER BY isoDate DESC, createdAtEpochMs DESC) + entity-to-domain mapping
        var domainTransactions = emptyList<com.example.data.TransactionItem>()
        val queryAndMapNs = measureNanoTime {
            domainTransactions = repo.observeTransactions(testUserId).first()
        }
        assertEquals("Full history query must return 5,000 mapped domain transactions", 5_000, domainTransactions.size)

        // 3. Measure typical PayCycleEngine dashboard computation over all 5,000 transactions
        var balancesCount = 0
        var envelopesCount = 0
        var currentCycleSpend = 0.0
        var recurringCount = 0
        val engineNs = measureNanoTime {
            val currentWindow = PayCycleEngine.computeWindow(
                cycleKey = "2026-11",
                payCycleDay = 1,
                nowIso = "2026-11-25"
            )
            val previousWindow = PayCycleEngine.computeWindow(
                cycleKey = "2026-10",
                payCycleDay = 1,
                nowIso = "2026-11-25"
            )
            val balances = PayCycleEngine.computeAccountBalances(accounts, domainTransactions)
            val computedEnvelopes = PayCycleEngine.computeEnvelopes(
                envelopes = envelopes,
                baseBudget = 12_000.0,
                rolloverEnabled = true,
                currentWindow = currentWindow,
                previousWindow = previousWindow,
                allTransactions = domainTransactions,
                maxDateIso = "2026-11-25"
            )
            val cycleTxs = domainTransactions.filter {
                it.type == "expense" &&
                    it.isoDate >= currentWindow.startIso &&
                    it.isoDate <= "2026-11-25"
            }
            val spentSoFar = cycleTxs.sumOf { it.amount }
            PayCycleEngine.computeOverspendProjection(
                window = currentWindow,
                totalAvailableBudget = 12_000.0,
                spentSoFar = spentSoFar,
                scheduledBills = 1_250.0,
                pastOrTodayCycleTransactions = cycleTxs,
                bills = emptyList()
            )
            val detectedSubs = PayCycleEngine.detectRecurringSubscriptions(domainTransactions, emptyList())
            balancesCount = balances.size
            envelopesCount = computedEnvelopes.size
            currentCycleSpend = spentSoFar
            recurringCount = detectedSubs.size
        }

        val bulkInsertMs = bulkInsertNs / 1_000_000.0
        val queryAndMapMs = queryAndMapNs / 1_000_000.0
        val engineMs = engineNs / 1_000_000.0
        val totalMs = bulkInsertMs + queryAndMapMs + engineMs

        println("=== PHASE 0 BASELINE PERF (5,000 transactions / 12 months) ===")
        println(String.format("Bulk insert (5,000 rows):             %.2f ms", bulkInsertMs))
        println(String.format("Full query + domain mapping (5,000):  %.2f ms", queryAndMapMs))
        println(String.format("PayCycleEngine compute (5,000 rows):  %.2f ms", engineMs))
        println(String.format("Total harness time:                   %.2f ms", totalMs))
        println("Sanity -> count=$persistedCount, balances=$balancesCount, envelopes=$envelopesCount, cycleSpend=${String.format("%.2f", currentCycleSpend)} DH, recurringDetected=$recurringCount")
        println("==============================================================")

        assertEquals(3, balancesCount)
        assertEquals(5, envelopesCount)
        assertTrue("Current cycle spend must be positive", currentCycleSpend > 0.0)
    }

    private fun buildDeterministicTransactions(
        userId: String,
        count: Int,
        endDateIso: String,
        baseEpochMs: Long
    ): List<TransactionEntity> {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val endDate = LocalDate.parse(endDateIso, formatter)
        val spanDays = 365L

        val merchantCatalog = listOf(
            Triple("Marjane Hay Riad", "charges", "Charges & Marché"),
            Triple("Café Paul", "cafe", "Café & Sorties"),
            Triple("Petit Taxi", "transport", "Transport"),
            Triple("BIM Market", "charges", "Charges & Marché"),
            Triple("ONCF Ticket", "transport", "Transport"),
            Triple("Pharmacie Atlas", "autre", "Autre"),
            Triple("Carrefour Gourmet", "charges", "Charges & Marché"),
            Triple("Glovo Livraison", "cafe", "Café & Sorties"),
            Triple("Afriquia Station", "transport", "Transport"),
            Triple("Epargne Mensuelle", "epargne", "Épargne")
        )
        val accountIds = listOf("acc_cash", "acc_bank", "acc_wallet")

        return List(count) { index ->
            val daysAgo = (index.toLong() * spanDays) / count.toLong()
            val txDate = endDate.minusDays(daysAgo)
            val isoDate = txDate.format(formatter)
            val ts = baseEpochMs - (daysAgo * 86_400_000L) - ((index % 1000) * 1_000L)

            when {
                // Realistic monthly recurring subscription (12th of each month)
                index < 12 -> TransactionEntity(
                    id = "tx_perf_sub_$index",
                    userId = userId,
                    type = "expense",
                    amount = 95.0,
                    category = "Charges & Marché",
                    envelopeKey = "charges",
                    accountId = "acc_bank",
                    toAccountId = "",
                    desc = "Netflix",
                    isoDate = endDate.minusMonths(index.toLong()).withDayOfMonth(10).format(formatter),
                    hourOfDay = 10,
                    createdAtEpochMs = ts,
                    updatedAtEpochMs = ts
                )
                // ~7% incomes (salary / freelance / bonus)
                index % 15 == 0 -> TransactionEntity(
                    id = "tx_perf_$index",
                    userId = userId,
                    type = "income",
                    amount = 2_500.0 + ((index % 7) * 450.0),
                    category = "Revenu",
                    envelopeKey = "autre",
                    accountId = "acc_bank",
                    toAccountId = "",
                    desc = "Virement Salaire / Prime",
                    isoDate = isoDate,
                    hourOfDay = 9,
                    createdAtEpochMs = ts,
                    updatedAtEpochMs = ts
                )
                // ~5% bank-to-cash ATM transfers
                index % 20 == 1 -> TransactionEntity(
                    id = "tx_perf_$index",
                    userId = userId,
                    type = "transfer",
                    amount = 300.0 + ((index % 4) * 100.0),
                    category = "Transfert",
                    envelopeKey = "autre",
                    accountId = "acc_bank",
                    toAccountId = "acc_cash",
                    desc = "Retrait GAB",
                    isoDate = isoDate,
                    hourOfDay = 14,
                    createdAtEpochMs = ts,
                    updatedAtEpochMs = ts
                )
                // ~88% everyday expenses
                else -> {
                    val (merchant, envKey, categoryName) = merchantCatalog[index % merchantCatalog.size]
                    val amount = 12.0 + ((index * 37) % 420) + ((index % 4) * 0.25)
                    TransactionEntity(
                        id = "tx_perf_$index",
                        userId = userId,
                        type = "expense",
                        amount = amount,
                        category = categoryName,
                        envelopeKey = envKey,
                        accountId = accountIds[index % accountIds.size],
                        toAccountId = "",
                        desc = merchant,
                        isoDate = isoDate,
                        hourOfDay = 8 + (index % 14),
                        createdAtEpochMs = ts,
                        updatedAtEpochMs = ts
                    )
                }
            }
        }
    }
}
