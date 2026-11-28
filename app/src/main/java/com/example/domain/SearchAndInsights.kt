package com.example.domain

import com.example.data.EnvelopeConfig
import com.example.data.TransactionItem
import com.masroufi.core.Money
import java.util.Locale
import kotlin.math.abs

/**
 * Multi-criteria offline search filter for Room FTS4 + exact `Long` centimes SQL queries.
 * Zero `Float` or `Double` values anywhere in the search contract.
 */
data class TransactionSearchCriteria(
    val query: String = "",
    val startIsoDate: String? = null,
    val endIsoDate: String? = null,
    val minAmountCentimes: Long? = null,
    val maxAmountCentimes: Long? = null,
    val accountId: String? = null,
    val envelopeKey: String? = null,
    val type: String? = null, // null / "all" | "expense" | "income" | "transfer"
    val loanId: String? = null,
    val loanLinkedOnly: Boolean = false
) {
    val normalizedType: String?
        get() = type?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() && it != "all" }

    val normalizedAccountId: String?
        get() = accountId?.trim()?.takeIf { it.isNotBlank() && it != "all" }

    val normalizedEnvelopeKey: String?
        get() = envelopeKey?.trim()?.takeIf { it.isNotBlank() && it != "all" }

    val normalizedLoanId: String?
        get() = loanId?.trim()?.takeIf { it.isNotBlank() && it != "all" }

    val normalizedStartIso: String?
        get() = startIsoDate?.trim()?.takeIf { PayCycleEngine.isValidIsoDate(it) }

    val normalizedEndIso: String?
        get() = endIsoDate?.trim()?.takeIf { PayCycleEngine.isValidIsoDate(it) }

    val hasAdvancedFilters: Boolean
        get() = normalizedStartIso != null ||
            normalizedEndIso != null ||
            minAmountCentimes != null ||
            maxAmountCentimes != null ||
            normalizedType != null ||
            normalizedLoanId != null ||
            loanLinkedOnly

    val hasAnyCriteria: Boolean
        get() = query.isNotBlank() ||
            normalizedAccountId != null ||
            normalizedEnvelopeKey != null ||
            hasAdvancedFilters

    val activeFilterCount: Int
        get() {
            var count = 0
            if (normalizedStartIso != null || normalizedEndIso != null) count++
            if (minAmountCentimes != null || maxAmountCentimes != null) count++
            if (normalizedAccountId != null) count++
            if (normalizedEnvelopeKey != null) count++
            if (normalizedType != null) count++
            if (loanLinkedOnly || normalizedLoanId != null) count++
            return count
        }
}

/**
 * Sanitizes arbitrary user search input (French accents, Arabic, Darija, English, numbers)
 * into a safe SQLite FTS4 `MATCH` expression that can never trigger SQLite FTS syntax errors
 * or operator injection.
 */
object FtsQuerySanitizer {

    private val RESERVED_FTS_KEYWORDS = setOf("AND", "OR", "NOT", "NEAR")
    private val NON_TOKEN_REGEX = Regex("[^\\p{L}\\p{Nd}]+")

    /**
     * Converts raw text into a whitespace-separated FTS4 prefix query (`tok1* tok2*`).
     * Returns `null` when no searchable Unicode letters or digits remain.
     */
    fun buildFtsMatchExpression(rawQuery: String): String? {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return null
        val tokens = NON_TOKEN_REGEX.split(trimmed)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.uppercase(Locale.ROOT) in RESERVED_FTS_KEYWORDS }
            .take(8)
            .map { "${it.take(32)}*" }
            .toList()
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ")
    }
}

enum class InsightAnomalyKind {
    CATEGORY_SPIKE,
    LARGE_TRANSACTION
}

/**
 * Cycle-over-cycle spending comparison for a single envelope/category using exact `Long` centimes.
 */
data class CategoryTrendItem(
    val envelopeKey: String,
    val currentCycleSpent: Money,
    val previousCycleSpent: Money,
    val delta: Money,
    val percentChange: Int?
) {
    val isIncrease: Boolean get() = delta.isPositive
    val isDecrease: Boolean get() = delta.isNegative
}

/**
 * Detected unusual spending spike or unusually large single expense (transfers strictly excluded).
 */
data class SpendingAnomalyItem(
    val id: String,
    val envelopeKey: String,
    val kind: InsightAnomalyKind,
    val currentAmount: Money,
    val baselineAmount: Money,
    val percentAboveBaseline: Int,
    val transactionId: String? = null,
    val transactionDesc: String = "",
    val isoDate: String = ""
)

/**
 * Suspected duplicate expense transaction (same exact centimes amount, normalized label, <= 2 days apart).
 */
data class DuplicateSuspectItem(
    val id: String,
    val amount: Money,
    val description: String,
    val envelopeKey: String,
    val firstTransactionId: String,
    val secondTransactionId: String,
    val firstIsoDate: String,
    val secondIsoDate: String,
    val daysApart: Int
)

/**
 * Single reactive Financial Insights snapshot exposed by `MasroufiViewModel`.
 * Every monetary field uses exact `Long` centimes (`Money`). Zero `Float` or `Double` math.
 */
data class FinancialInsightsSnapshot(
    val currentCycleKey: String = "",
    val previousCycleKey: String = "",
    val currentCycleExpenseTotal: Money = Money.ZERO,
    val previousCycleExpenseTotal: Money = Money.ZERO,
    val totalExpenseDelta: Money = Money.ZERO,
    val totalExpensePercentChange: Int? = null,
    val categoryTrends: List<CategoryTrendItem> = emptyList(),
    val anomalies: List<SpendingAnomalyItem> = emptyList(),
    val duplicateSuspects: List<DuplicateSuspectItem> = emptyList(),
    val hasEnoughHistory: Boolean = false
) {
    val hasAnyAlerts: Boolean
        get() = anomalies.isNotEmpty() || duplicateSuspects.isNotEmpty()

    val topIncreasingCategory: CategoryTrendItem?
        get() = categoryTrends.firstOrNull { it.delta.isPositive }

    val topSavingCategory: CategoryTrendItem?
        get() = categoryTrends.filter { it.delta.isNegative }.minByOrNull { it.delta.centimes }
}

/**
 * Pure Kotlin exact `Long`-centimes Financial Insights engine aligned with [PayCycleEngine].
 *
 * Guarantees:
 * - Zero `Float`/`Double` in all monetary calculations and percentage ratios (pure integer centime math).
 * - Strictly excludes internal transfers (`transfer`, `transfer_out`, `transfer_in`, and any row with
 *   `transferGroupId != null`) so account transfers and loan repayments never skew spending insights.
 * - Compares active pay cycle against previous pay cycle (`monthOffset = -1`).
 * - Detects category spikes (>= +25% and >= 200 DH), unusual single expenses (>= 2x category average and >= 300 DH),
 *   and suspected duplicate expenses (identical exact centimes + normalized label within <= 2 days,
 *   ignoring distinct recurring occurrence postings).
 */
object FinancialInsightsEngine {

    private const val CATEGORY_SPIKE_MIN_CENTIMES = 20_000L // 200.00 DH
    private const val CATEGORY_SPIKE_MIN_RATIO_PCT = 125L   // >= 125% (+25%)
    private const val LARGE_TX_MIN_CENTIMES = 30_000L       // 300.00 DH
    private const val LARGE_TX_MIN_HISTORY_COUNT = 3
    private const val DUPLICATE_MAX_DAYS_APART = 2

    fun compute(
        transactions: List<TransactionItem>,
        envelopes: List<EnvelopeConfig>,
        payCycleDay: Int,
        todayIso: String = PayCycleEngine.todayIso(),
        activeWindowOverride: PayCycleWindow? = null,
        previousWindowOverride: PayCycleWindow? = null
    ): FinancialInsightsSnapshot {
        val safeToday = if (PayCycleEngine.isValidIsoDate(todayIso)) todayIso else PayCycleEngine.todayIso()
        val currentWindow = activeWindowOverride ?: PayCycleEngine.computeCycleWindow(
            payCycleDay = payCycleDay,
            monthOffset = 0,
            nowIso = safeToday
        )
        val previousWindow = previousWindowOverride ?: PayCycleEngine.computeWindow(
            cycleKey = PayCycleEngine.shiftCycleKey(currentWindow.cycleKey, -1),
            payCycleDay = payCycleDay,
            nowIso = safeToday
        )

        // Strictly exclude all transfer types (legacy single-row + Phase 5 paired legs + transferGroupId)
        val pureExpenses = transactions.filter { tx ->
            tx.type == "expense" &&
                !tx.isTransferType() &&
                tx.transferGroupId.isNullOrBlank() &&
                tx.amount.isPositive &&
                PayCycleEngine.isValidIsoDate(tx.isoDate) &&
                tx.isoDate <= safeToday
        }

        fun resolveEnv(tx: TransactionItem): String {
            return tx.envelopeKey.ifBlank {
                PayCycleEngine.resolveEnvelopeKey(tx.category, tx.desc, envelopes)
            }
        }

        val currentExpenses = pureExpenses.filter { PayCycleEngine.isInWindow(it.isoDate, currentWindow) }
        val previousExpenses = pureExpenses.filter { PayCycleEngine.isInWindow(it.isoDate, previousWindow) }

        var currentTotalCentimes = 0L
        val currentByCategory = LinkedHashMap<String, Long>()
        for (tx in currentExpenses) {
            currentTotalCentimes += tx.amount.centimes
            val key = resolveEnv(tx)
            currentByCategory[key] = (currentByCategory[key] ?: 0L) + tx.amount.centimes
        }

        var previousTotalCentimes = 0L
        val previousByCategory = LinkedHashMap<String, Long>()
        for (tx in previousExpenses) {
            previousTotalCentimes += tx.amount.centimes
            val key = resolveEnv(tx)
            previousByCategory[key] = (previousByCategory[key] ?: 0L) + tx.amount.centimes
        }

        val totalDeltaCentimes = currentTotalCentimes - previousTotalCentimes
        val totalPercentChange = if (previousTotalCentimes > 0L) {
            ((totalDeltaCentimes * 100L) / previousTotalCentimes).toInt()
        } else {
            null
        }

        // 1. Category Trends across current vs previous pay cycle
        val allCategoryKeys = (currentByCategory.keys + previousByCategory.keys).toSet()
        val categoryTrends = allCategoryKeys.map { key ->
            val cur = currentByCategory[key] ?: 0L
            val prev = previousByCategory[key] ?: 0L
            val delta = cur - prev
            val pct = if (prev > 0L) ((delta * 100L) / prev).toInt() else null
            CategoryTrendItem(
                envelopeKey = key,
                currentCycleSpent = Money.fromCentimes(cur),
                previousCycleSpent = Money.fromCentimes(prev),
                delta = Money.fromCentimes(delta),
                percentChange = pct
            )
        }.sortedWith(
            compareByDescending<CategoryTrendItem> { abs(it.delta.centimes) }
                .thenByDescending { it.currentCycleSpent.centimes }
                .thenBy { it.envelopeKey }
        )

        // 2. Anomalies: category spikes (>= +25% & >= 200 DH) + large single-transaction outliers
        val anomalies = mutableListOf<SpendingAnomalyItem>()
        for (trend in categoryTrends) {
            val cur = trend.currentCycleSpent.centimes
            val prev = trend.previousCycleSpent.centimes
            if (prev > 0L && cur >= CATEGORY_SPIKE_MIN_CENTIMES && cur * 100L >= prev * CATEGORY_SPIKE_MIN_RATIO_PCT) {
                val pctAbove = (((cur - prev) * 100L) / prev).toInt()
                anomalies.add(
                    SpendingAnomalyItem(
                        id = "spike_${trend.envelopeKey}_${currentWindow.cycleKey}",
                        envelopeKey = trend.envelopeKey,
                        kind = InsightAnomalyKind.CATEGORY_SPIKE,
                        currentAmount = trend.currentCycleSpent,
                        baselineAmount = trend.previousCycleSpent,
                        percentAboveBaseline = pctAbove
                    )
                )
            }
        }

        // Large single-transaction outlier vs category historical average (pure integer centimes)
        val historicalByCategory = pureExpenses.groupBy { resolveEnv(it) }
        for (tx in currentExpenses.sortedByDescending { it.amount.centimes }) {
            if (tx.amount.centimes < LARGE_TX_MIN_CENTIMES) continue
            if (!tx.recurringOccurrenceKey.isNullOrBlank()) continue
            val key = resolveEnv(tx)
            val others = historicalByCategory[key].orEmpty().filter { it.id != tx.id }
            if (others.size < LARGE_TX_MIN_HISTORY_COUNT) continue
            var sumOther = 0L
            for (o in others) sumOther += o.amount.centimes
            val avgOther = sumOther / others.size.toLong()
            if (avgOther > 0L && tx.amount.centimes >= avgOther * 2L) {
                val pctAbove = (((tx.amount.centimes - avgOther) * 100L) / avgOther).toInt()
                anomalies.add(
                    SpendingAnomalyItem(
                        id = "outlier_${tx.id}",
                        envelopeKey = key,
                        kind = InsightAnomalyKind.LARGE_TRANSACTION,
                        currentAmount = tx.amount,
                        baselineAmount = Money.fromCentimes(avgOther),
                        percentAboveBaseline = pctAbove,
                        transactionId = tx.id,
                        transactionDesc = tx.desc.ifBlank { tx.category },
                        isoDate = tx.isoDate
                    )
                )
            }
        }

        // 3. Suspected duplicate expenses within active pay cycle (excluding distinct recurring occurrence keys)
        val duplicates = mutableListOf<DuplicateSuspectItem>()
        val groupedForDup = currentExpenses.groupBy { tx ->
            tx.amount.centimes to normalizeMerchantLabel(tx.desc.ifBlank { tx.category })
        }
        val seenPairs = HashSet<String>()
        for ((pairKey, list) in groupedForDup) {
            val (amountCentimes, normLabel) = pairKey
            if (list.size < 2 || normLabel.isBlank()) continue
            val sorted = list.sortedWith(compareBy<TransactionItem> { it.isoDate }.thenBy { it.id })
            for (i in 0 until sorted.lastIndex) {
                val first = sorted[i]
                val second = sorted[i + 1]
                // Distinct recurring rule occurrences are intentional scheduled posts, not duplicates
                if (!first.recurringOccurrenceKey.isNullOrBlank() &&
                    !second.recurringOccurrenceKey.isNullOrBlank() &&
                    first.recurringOccurrenceKey != second.recurringOccurrenceKey
                ) {
                    continue
                }
                val daysApart = abs(
                    PayCycleEngine.parseEpochDays(second.isoDate) - PayCycleEngine.parseEpochDays(first.isoDate)
                ).toInt()
                if (daysApart <= DUPLICATE_MAX_DAYS_APART) {
                    val pairId = "dup_${first.id}_${second.id}"
                    if (seenPairs.add(pairId)) {
                        duplicates.add(
                            DuplicateSuspectItem(
                                id = pairId,
                                amount = Money.fromCentimes(amountCentimes),
                                description = second.desc.ifBlank { second.category },
                                envelopeKey = resolveEnv(second),
                                firstTransactionId = first.id,
                                secondTransactionId = second.id,
                                firstIsoDate = first.isoDate,
                                secondIsoDate = second.isoDate,
                                daysApart = daysApart
                            )
                        )
                    }
                }
            }
        }

        val hasEnoughHistory = previousExpenses.isNotEmpty() || currentExpenses.size >= 3

        return FinancialInsightsSnapshot(
            currentCycleKey = currentWindow.cycleKey,
            previousCycleKey = previousWindow.cycleKey,
            currentCycleExpenseTotal = Money.fromCentimes(currentTotalCentimes),
            previousCycleExpenseTotal = Money.fromCentimes(previousTotalCentimes),
            totalExpenseDelta = Money.fromCentimes(totalDeltaCentimes),
            totalExpensePercentChange = totalPercentChange,
            categoryTrends = categoryTrends,
            anomalies = anomalies.sortedByDescending { it.percentAboveBaseline }.take(6),
            duplicateSuspects = duplicates.sortedByDescending { it.secondIsoDate }.take(6),
            hasEnoughHistory = hasEnoughHistory
        )
    }

    private fun normalizeMerchantLabel(raw: String): String {
        return raw.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }
}
