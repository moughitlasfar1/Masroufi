package com.example.domain

import com.example.data.AccountItem
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

data class PayCycleWindow(
    val cycleKey: String, // "YYYY-MM" anchor
    val startIso: String, // "YYYY-MM-DD"
    val endIso: String,   // "YYYY-MM-DD"
    val totalDays: Int,
    val daysElapsed: Int,
    val daysRemaining: Int,
    val isCurrentCycle: Boolean
)

data class AccountWithBalance(
    val account: AccountItem,
    val currentBalance: Double,
    val totalIn: Double,
    val totalOut: Double
)

data class EnvelopeStatus(
    val config: EnvelopeConfig,
    val baseAllocated: Double,
    val rolloverAmount: Double,
    val totalAvailable: Double,
    val spent: Double,
    val remaining: Double,
    val usagePercent: Int,
    val isOver90Percent: Boolean
)

object PayCycleEngine {

    fun todayIso(): String {
        val cal = Calendar.getInstance()
        return formatIso(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun currentCycleKey(payCycleDay: Int, nowIso: String = todayIso()): String {
        val parts = nowIso.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size < 3) return "2026-10"
        val year = parts[0]
        val month = parts[1]
        val day = parts[2]
        val clampedStart = payCycleDay.coerceIn(1, 28)
        return if (clampedStart == 1 || day >= clampedStart) {
            "%04d-%02d".format(Locale.US, year, month)
        } else {
            // Belongs to the cycle that started in the previous calendar month
            shiftCycleKey("%04d-%02d".format(Locale.US, year, month), -1)
        }
    }

    fun shiftCycleKey(cycleKey: String, deltaMonths: Int): String {
        val parts = cycleKey.split("-").mapNotNull { it.toIntOrNull() }
        val year = parts.getOrElse(0) { 2026 }
        val month = parts.getOrElse(1) { 10 }
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, deltaMonths)
        }
        return "%04d-%02d".format(
            Locale.US,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1
        )
    }

    fun computeWindow(
        cycleKey: String,
        payCycleDay: Int,
        nowIso: String = todayIso()
    ): PayCycleWindow {
        val parts = cycleKey.split("-").mapNotNull { it.toIntOrNull() }
        val year = parts.getOrElse(0) { 2026 }
        val month = parts.getOrElse(1) { 10 }
        val startDay = payCycleDay.coerceIn(1, 28)

        val startIso: String
        val endIso: String
        if (startDay == 1) {
            val maxDay = daysInMonth(year, month)
            startIso = formatIso(year, month, 1)
            endIso = formatIso(year, month, maxDay)
        } else {
            startIso = formatIso(year, month, startDay)
            val nextKey = shiftCycleKey(cycleKey, 1)
            val nextParts = nextKey.split("-").mapNotNull { it.toIntOrNull() }
            val ny = nextParts.getOrElse(0) { year }
            val nm = nextParts.getOrElse(1) { month }
            endIso = formatIso(ny, nm, startDay - 1)
        }

        val totalDays = max(1, daysBetweenInclusive(startIso, endIso))
        val isCurrent = nowIso >= startIso && nowIso <= endIso
        val daysElapsed = when {
            isCurrent -> daysBetweenInclusive(startIso, nowIso).coerceIn(1, totalDays)
            nowIso > endIso -> totalDays
            else -> 1
        }
        val daysRemaining = if (isCurrent) {
            daysBetweenInclusive(nowIso, endIso).coerceAtLeast(1)
        } else {
            0
        }

        return PayCycleWindow(
            cycleKey = cycleKey,
            startIso = startIso,
            endIso = endIso,
            totalDays = totalDays,
            daysElapsed = daysElapsed,
            daysRemaining = daysRemaining,
            isCurrentCycle = isCurrent
        )
    }

    fun isInWindow(isoDate: String, window: PayCycleWindow): Boolean {
        return isoDate >= window.startIso && isoDate <= window.endIso
    }

    fun resolveEnvelopeKey(
        category: String,
        desc: String,
        envelopes: List<EnvelopeConfig>
    ): String {
        val text = "$category $desc".lowercase(Locale.ROOT)
        // Check custom envelope match first
        for (env in envelopes) {
            if (env.key.isNotBlank() && (
                    category.equals(env.nameFr, ignoreCase = true) ||
                    category.equals(env.nameEn, ignoreCase = true) ||
                    category.equals(env.nameAr, ignoreCase = true) ||
                    category.equals(env.nameDarija, ignoreCase = true) ||
                    category.equals(env.key, ignoreCase = true)
                )
            ) {
                return env.key
            }
        }
        return when {
            text.contains("transp") || text.contains("oncf") || text.contains("taxi") ||
                text.contains("carbur") || text.contains("خدمة") || text.contains("نقل") -> "transport"
            text.contains("charg") || text.contains("fixe") || text.contains("maison") ||
                text.contains("factur") || text.contains("قارة") || text.contains("دار") ||
                text.contains("khobz") || text.contains("fibre") || text.contains("lydec") -> "charges"
            text.contains("caf") || text.contains("garo") || text.contains("sorti") ||
                text.contains("قهوة") || text.contains("شخصي") -> "cafe"
            text.contains("épargn") || text.contains("epargn") || text.contains("invest") ||
                text.contains("ادخار") || text.contains("توفير") || text.contains("daret") -> "epargne"
            else -> "autre"
        }
    }

    fun computeAccountBalances(
        accounts: List<AccountItem>,
        transactions: List<TransactionItem>
    ): List<AccountWithBalance> {
        return accounts.map { acc ->
            var totalIn = 0.0
            var totalOut = 0.0
            for (tx in transactions) {
                when (tx.type) {
                    "income" -> if (tx.accountId == acc.id) totalIn += tx.amount
                    "expense" -> if (tx.accountId == acc.id) totalOut += tx.amount
                    "transfer" -> {
                        if (tx.accountId == acc.id) totalOut += tx.amount
                        if (tx.toAccountId == acc.id) totalIn += tx.amount
                    }
                }
            }
            AccountWithBalance(
                account = acc,
                currentBalance = acc.initialBalance + totalIn - totalOut,
                totalIn = totalIn,
                totalOut = totalOut
            )
        }
    }

    fun computeEnvelopes(
        envelopes: List<EnvelopeConfig>,
        baseBudget: Double,
        rolloverEnabled: Boolean,
        currentWindow: PayCycleWindow,
        previousWindow: PayCycleWindow,
        allTransactions: List<TransactionItem>
    ): List<EnvelopeStatus> {
        val curExpenses = allTransactions.filter { it.type == "expense" && isInWindow(it.isoDate, currentWindow) }
        val prevExpenses = allTransactions.filter { it.type == "expense" && isInWindow(it.isoDate, previousWindow) }
        val totalCurExp = curExpenses.sumOf { it.amount }

        return envelopes.map { env ->
            val key = env.key
            val spentCurrent = curExpenses
                .filter { (it.envelopeKey.ifBlank { resolveEnvelopeKey(it.category, it.desc, envelopes) }) == key }
                .sumOf { it.amount }

            val baseAllocated = if (env.hasCap) (baseBudget * (env.percentage / 100.0)) else 0.0

            val rolloverAmount = if (rolloverEnabled && env.hasCap && prevExpenses.isNotEmpty()) {
                val spentPrev = prevExpenses
                    .filter { (it.envelopeKey.ifBlank { resolveEnvelopeKey(it.category, it.desc, envelopes) }) == key }
                    .sumOf { it.amount }
                max(0.0, baseAllocated - spentPrev)
            } else {
                0.0
            }

            val totalAvailable = baseAllocated + rolloverAmount
            val remaining = if (env.hasCap) totalAvailable - spentCurrent else 0.0
            val usagePct = if (env.hasCap && totalAvailable > 0.0) {
                ((spentCurrent / totalAvailable) * 100.0).roundToLong().toInt()
            } else if (!env.hasCap && totalCurExp > 0.0) {
                ((spentCurrent / totalCurExp) * 100.0).roundToLong().toInt()
            } else {
                0
            }

            EnvelopeStatus(
                config = env,
                baseAllocated = baseAllocated,
                rolloverAmount = rolloverAmount,
                totalAvailable = totalAvailable,
                spent = spentCurrent,
                remaining = remaining,
                usagePercent = usagePct,
                isOver90Percent = env.hasCap && usagePct >= 90
            )
        }
    }

    fun isBillPaidInCycle(bill: FinancialItem, cycleKey: String): Boolean {
        return bill.dateOrCycle.split(",").map { it.trim() }.contains(cycleKey)
    }

    private fun daysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun formatIso(year: Int, month: Int, day: Int): String {
        return "%04d-%02d-%02d".format(Locale.US, year, month, day)
    }

    fun daysBetweenInclusive(startIso: String, endIso: String): Int {
        val s = parseEpochDays(startIso)
        val e = parseEpochDays(endIso)
        return max(1, (e - s + 1).toInt())
    }

    fun parseEpochDays(iso: String): Long {
        val parts = iso.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size < 3) return 0L
        val cal = Calendar.getInstance().apply {
            set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis / 86_400_000L
    }

    fun formatDh(value: Double): String {
        val nf = NumberFormat.getIntegerInstance(Locale.FRANCE)
        return nf.format(value.roundToLong())
    }
}
