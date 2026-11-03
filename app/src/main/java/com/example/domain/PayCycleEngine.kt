package com.example.domain

import com.example.data.AccountItem
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
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
    val previousSpent: Double = 0.0,
    val remaining: Double,
    val usagePercent: Int,
    val isOver90Percent: Boolean
)

data class SmartInsight(
    val id: String,
    val title: String,
    val body: String,
    val severity: String // "good", "warn", "info"
)

data class SmartReminder(
    val id: String,
    val kind: String, // "bill", "daret", "debt"
    val title: String,
    val subtitle: String,
    val amount: Double,
    val actionLabel: String,
    val item: FinancialItem
)

object PayCycleEngine {

    private val UTC_TZ: TimeZone = TimeZone.getTimeZone("UTC")
    private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun todayIso(timeZone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(timeZone)
        return formatIso(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Validates that [iso] is a strict YYYY-MM-DD string representing a real calendar date
     * (including leap-year validation for February).
     */
    fun isValidIsoDate(iso: String): Boolean {
        val trimmed = iso.trim()
        if (!ISO_DATE_REGEX.matches(trimmed)) return false
        val parts = trimmed.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return false
        val year = parts[0]
        val month = parts[1]
        val day = parts[2]
        if (year !in 1970..2100) return false
        if (month !in 1..12) return false
        val maxDay = daysInMonth(year, month)
        return day in 1..maxDay
    }

    /**
     * Returns today's real date if [iso] is blank, or validates that [iso] is a real YYYY-MM-DD date.
     * Throws IllegalArgumentException if [iso] is non-blank and invalid (never substitutes a hardcoded date).
     */
    fun requireOrNormalizeIsoDate(iso: String, timeZone: TimeZone = TimeZone.getDefault()): String {
        val trimmed = iso.trim()
        if (trimmed.isEmpty()) return todayIso(timeZone)
        require(isValidIsoDate(trimmed)) {
            "Date invalide '$trimmed'. Format attendu : YYYY-MM-DD valide."
        }
        return trimmed
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
        val cal = Calendar.getInstance(UTC_TZ).apply {
            clear()
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

    fun computeCycleWindow(
        payCycleDay: Int,
        monthOffset: Int = 0,
        nowIso: String = todayIso()
    ): PayCycleWindow {
        val baseKey = currentCycleKey(payCycleDay, nowIso)
        val targetKey = if (monthOffset == 0) baseKey else shiftCycleKey(baseKey, monthOffset)
        return computeWindow(targetKey, payCycleDay, nowIso)
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

            val spentPrev = prevExpenses
                .filter { (it.envelopeKey.ifBlank { resolveEnvelopeKey(it.category, it.desc, envelopes) }) == key }
                .sumOf { it.amount }

            val baseAllocated = if (env.hasCap) (baseBudget * (env.percentage / 100.0)) else 0.0

            val rolloverAmount = if (rolloverEnabled && env.hasCap && prevExpenses.isNotEmpty()) {
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
                previousSpent = spentPrev,
                remaining = remaining,
                usagePercent = usagePct,
                isOver90Percent = env.hasCap && usagePct >= 90
            )
        }
    }

    fun computeSmartInsights(
        lang: String,
        currency: String,
        window: PayCycleWindow,
        totalAvailableBudget: Double,
        cycleExpenses: Double,
        previousCycleExpenses: Double,
        safeToSpendToday: Double,
        dailyAverageSpend: Double,
        envelopes: List<EnvelopeStatus>
    ): List<SmartInsight> {
        val insights = mutableListOf<SmartInsight>()
        val timeProgressPct = ((window.daysElapsed.toDouble() / max(1, window.totalDays)) * 100.0).roundToInt()
        val budgetProgressPct = if (totalAvailableBudget > 0) {
            ((cycleExpenses / totalAvailableBudget) * 100.0).roundToInt()
        } else 0

        // 1. Pace vs Cycle Time Insight
        if (cycleExpenses <= 0.0) {
            insights.add(
                SmartInsight(
                    id = "clean_start",
                    title = when (lang) {
                        "darija" -> "بداية نقية لهاد الشهر"
                        "ar" -> "بداية جديدة لهذا الشهر"
                        "en" -> "Fresh cycle — no expenses logged yet"
                        else -> "Cycle vierge — aucune dépense enregistrée"
                    },
                    body = when (lang) {
                        "darija" -> "يمكن ليك تزيد أول مصروف بـ زر + أو تستعمل أزرار الشراء السريع (1-Tap)."
                        "ar" -> "سجل أول عملية عبر زر الإضافة أو أزرار الشراء السريع."
                        "en" -> "Tap + or use 1-Tap Quick Presets to record your first expense."
                        else -> "Ajoutez votre première opération via + ou utilisez les achats rapides 1-Tap."
                    },
                    severity = "info"
                )
            )
            return insights
        }

        if (budgetProgressPct > timeProgressPct + 12) {
            val projectedDiff = max(0.0, (dailyAverageSpend * window.totalDays) - totalAvailableBudget)
            insights.add(
                SmartInsight(
                    id = "pace_fast",
                    title = when (lang) {
                        "darija" -> "تنبيه: المصروف غادي بسرعة ($budgetProgressPct% فـ ${window.daysElapsed} يوم)"
                        "ar" -> "تنبيه: وتيرة الإنفاق مرتفعة ($budgetProgressPct%)"
                        "en" -> "Spending pace alert ($budgetProgressPct% used in ${window.daysElapsed}d)"
                        else -> "Rythme rapide : $budgetProgressPct% consommé en ${window.daysElapsed}j"
                    },
                    body = when (lang) {
                        "darija" -> "إلى كملتي بهاد الريتم غادي تفوت الميزانية بـ ${formatDh(projectedDiff)} $currency. حاول متفوتش ${formatDh(safeToSpendToday)} $currency/نهار."
                        "ar" -> "بهذه الوتيرة قد تتجاوز الميزانية بـ ${formatDh(projectedDiff)} $currency. التزم بـ ${formatDh(safeToSpendToday)} $currency/يوم."
                        "en" -> "At this pace you may exceed budget by ${formatDh(projectedDiff)} $currency. Aim for ${formatDh(safeToSpendToday)} $currency/day."
                        else -> "À ce rythme, projection à +${formatDh(projectedDiff)} $currency au-dessus du budget. Visez max ${formatDh(safeToSpendToday)} $currency/jour."
                    },
                    severity = "warn"
                )
            )
        } else {
            val potentialSave = max(0.0, totalAvailableBudget - (dailyAverageSpend * window.totalDays))
            insights.add(
                SmartInsight(
                    id = "pace_good",
                    title = when (lang) {
                        "darija" -> "إيقاع مزيان! راك متحكم فـ الميزانية ($budgetProgressPct%)"
                        "ar" -> "وتيرة ممتازة! أنت ضمن حدود الميزانية ($budgetProgressPct%)"
                        "en" -> "Great pace! You're well within budget ($budgetProgressPct%)"
                        else -> "Excellent rythme : $budgetProgressPct% utilisé ($timeProgressPct% du mois écoulé)"
                    },
                    body = when (lang) {
                        "darija" -> "إلى بقيتي هاكا تقدر توفر تقريباً ${formatDh(potentialSave)} $currency فـ اللخر ديال الشهر."
                        "ar" -> "إذا استمررت هكذا يمكنك ادخار حوالي ${formatDh(potentialSave)} $currency نهاية الشهر."
                        "en" -> "Keep this pace to save ~${formatDh(potentialSave)} $currency by the end of the cycle."
                        else -> "En gardant ce cap, vous pourrez épargner environ ${formatDh(potentialSave)} $currency en fin de cycle."
                    },
                    severity = "good"
                )
            )
        }

        // 2. Top Spending Category & Savings Suggestion
        val topEnv = envelopes.filter { it.spent > 0 }.maxByOrNull { it.spent }
        if (topEnv != null) {
            val share = if (cycleExpenses > 0) ((topEnv.spent / cycleExpenses) * 100.0).roundToInt() else 0
            val save20 = topEnv.spent * 0.20
            val envName = topEnv.config.localizedName(lang)
            insights.add(
                SmartInsight(
                    id = "top_driver",
                    title = when (lang) {
                        "darija" -> "فين كيمشي الفلوس: $envName ($share%)"
                        "ar" -> "أكبر باب للصرف: $envName ($share%)"
                        "en" -> "Top spending driver: $envName ($share%)"
                        else -> "Où va votre argent : $envName ($share%)"
                    },
                    body = when (lang) {
                        "darija" -> "صرفتي فيها ${formatDh(topEnv.spent)} $currency. غير تنقص 20% غادي تربح ${formatDh(save20)} $currency فـ الشهر (${formatDh(save20 * 12)} $currency فـ العام)."
                        "ar" -> "أنفقت ${formatDh(topEnv.spent)} $currency. خفض 20% يوفر لك ${formatDh(save20)} $currency شهرياً."
                        "en" -> "Spent ${formatDh(topEnv.spent)} $currency. Cutting 20% saves ${formatDh(save20)} $currency/mo (${formatDh(save20 * 12)} $currency/yr)."
                        else -> "Vous y avez dépensé ${formatDh(topEnv.spent)} $currency. Réduire de 20% libère +${formatDh(save20)} $currency/mois (+${formatDh(save20 * 12)} $currency/an)."
                    },
                    severity = if (topEnv.isOver90Percent) "warn" else "info"
                )
            )
        }

        // 3. Month-over-Month Trend Insight
        if (previousCycleExpenses > 0.0) {
            val delta = cycleExpenses - previousCycleExpenses
            val deltaPct = ((delta / previousCycleExpenses) * 100.0).roundToInt()
            insights.add(
                SmartInsight(
                    id = "mom_trend",
                    title = if (delta <= 0) {
                        "Tendance mensuelle : ${deltaPct}% vs cycle précédent"
                    } else {
                        "Tendance mensuelle : +${deltaPct}% vs cycle précédent"
                    },
                    body = if (delta <= 0) {
                        "Vous avez dépensé ${formatDh(abs(delta))} $currency de moins que le cycle dernier (${formatDh(previousCycleExpenses)} $currency)."
                    } else {
                        "Vos dépenses dépassent le cycle précédent de ${formatDh(delta)} $currency (${formatDh(previousCycleExpenses)} $currency)."
                    },
                    severity = if (delta <= 0) "good" else "warn"
                )
            )
        }

        return insights
    }

    fun computeSmartReminders(
        cycleKey: String,
        currency: String,
        bills: List<FinancialItem>,
        darets: List<FinancialItem>,
        debts: List<FinancialItem>
    ): List<SmartReminder> {
        val list = mutableListOf<SmartReminder>()
        // 1. Unpaid bills in active cycle
        bills.filter { !isBillPaidInCycle(it, cycleKey) }
            .sortedBy { it.dayOrTurn }
            .forEach { bill ->
                list.add(
                    SmartReminder(
                        id = "rem_bill_${bill.id}",
                        kind = "bill",
                        title = "Facture à régler : ${bill.name}",
                        subtitle = "Échéance jour ${bill.dayOrTurn} · ${formatDh(bill.amount)} $currency",
                        amount = bill.amount,
                        actionLabel = "Payer",
                        item = bill
                    )
                )
            }

        // 2. Active Darets with remaining rounds
        darets.forEach { daret ->
            val totalMembers = daret.secondaryAmount.roundToInt().coerceAtLeast(1)
            if (daret.extraInt < totalMembers) {
                list.add(
                    SmartReminder(
                        id = "rem_daret_${daret.id}",
                        kind = "daret",
                        title = "Cotisation Daret : ${daret.name}",
                        subtitle = "Tour payé ${daret.extraInt}/$totalMembers · ${formatDh(daret.amount)} $currency/mois",
                        amount = daret.amount,
                        actionLabel = "Cotiser",
                        item = daret
                    )
                )
            }
        }

        // 3. Unsettled Debts / Loans
        debts.forEach { debt ->
            val rem = max(0.0, debt.amount - debt.secondaryAmount)
            if (rem > 0.0) {
                val isLent = debt.dateOrCycle != "borrowed"
                list.add(
                    SmartReminder(
                        id = "rem_debt_${debt.id}",
                        kind = "debt",
                        title = if (isLent) "Crédit à encaisser : ${debt.name}" else "Dette à rembourser : ${debt.name}",
                        subtitle = "Reste ${formatDh(rem)} $currency sur ${formatDh(debt.amount)} $currency",
                        amount = rem,
                        actionLabel = "+ 100 $currency",
                        item = debt
                    )
                )
            }
        }
        return list
    }

    fun isBillPaidInCycle(bill: FinancialItem, cycleKey: String): Boolean {
        return bill.dateOrCycle.split(",").map { it.trim() }.contains(cycleKey)
    }

    private fun daysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance(UTC_TZ).apply {
            clear()
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

    /**
     * Timezone- and DST-independent epoch day calculation using pure UTC calendar midnight.
     */
    fun parseEpochDays(iso: String): Long {
        val parts = iso.trim().split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size < 3) return 0L
        val cal = Calendar.getInstance(UTC_TZ).apply {
            clear()
            set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis / 86_400_000L
    }

    /**
     * Computes Monday epoch day and formatted Monday->Sunday range label using UTC so DST
     * transitions never shift day-of-week buckets.
     */
    fun computeMondayEpochDayAndLabel(anchorIso: String): Pair<Long, String> {
        val parts = anchorIso.trim().split("-").mapNotNull { it.toIntOrNull() }
        val cal = Calendar.getInstance(UTC_TZ).apply {
            clear()
            if (parts.size >= 3) {
                set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
            } else {
                set(2026, 9, 30, 0, 0, 0)
            }
            set(Calendar.MILLISECOND, 0)
        }
        val dow = cal.get(Calendar.DAY_OF_WEEK) // Sun=1 .. Sat=7
        val offsetFromMonday = (dow + 5) % 7
        cal.add(Calendar.DAY_OF_MONTH, -offsetFromMonday)
        val mondayEpochDay = cal.timeInMillis / 86_400_000L
        val monStr = "%02d/%02d".format(Locale.US, cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
        cal.add(Calendar.DAY_OF_MONTH, 6)
        val sunStr = "%02d/%02d".format(Locale.US, cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
        return mondayEpochDay to "$monStr → $sunStr"
    }

    fun formatDh(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val nf = NumberFormat.getIntegerInstance(Locale.FRANCE)
        return nf.format(value.roundToLong())
    }
}
