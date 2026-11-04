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

data class DetectedSubscription(
    val merchant: String,
    val amount: Double,
    val suggestedDueDay: Int,
    val occurrences: Int,
    val envelopeKey: String = "charges"
) {
    val name: String get() = merchant
    val monthlyAmount: Double get() = amount
    val typicalDayOfMonth: Int get() = suggestedDueDay
}

data class MoroccanBillTemplate(
    val id: String,
    val name: String,
    val providerSubtitle: String,
    val defaultAmount: Double,
    val dueDay: Int,
    val emoji: String = "🧾"
) {
    val key: String get() = id
    val defaultDueDay: Int get() = dueDay
}

data class MoroccanSeasonalTemplate(
    val id: String,
    val name: String,
    val subtitle: String,
    val defaultTargetAmount: Double,
    val defaultTargetIso: String,
    val emoji: String = "🌙"
) {
    val key: String get() = id
    val targetAmount: Double get() = defaultTargetAmount
    val targetIsoDate: String get() = defaultTargetIso
}

data class SeasonalCountdownInfo(
    val daysRemaining: Int,
    val monthsRemaining: Int,
    val remainingAmount: Double,
    val suggestedMonthlySaving: Double
)

data class SmartDefaultGuess(
    val envelopeKey: String,
    val categoryName: String,
    val accountId: String
)

object PayCycleEngine {

    const val DEFAULT_EUR_TO_MAD = 10.85
    const val DEFAULT_USD_TO_MAD = 9.95

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

    /**
     * Anomaly nudge: compares category / coffee / transport / groceries spending in the last 7 days
     * (or active cycle pace) against the trailing 4-week weekly average.
     * Example: "Coffee is up 40% vs the last 4 weeks."
     */
    fun computeAnomalyNudges(
        allTransactions: List<TransactionItem>,
        envelopes: List<EnvelopeConfig>,
        nowIso: String = todayIso(),
        lang: String = "fr",
        currency: String = "DH"
    ): List<SmartInsight> {
        val nudges = mutableListOf<SmartInsight>()
        val nowEpoch = parseEpochDays(nowIso)
        val expenses = allTransactions.filter { it.type == "expense" }
        if (expenses.isEmpty()) return nudges

        // Last 7 days: [nowEpoch - 6 .. nowEpoch]
        // Prior 28 days (4 weeks): [nowEpoch - 34 .. nowEpoch - 7]
        val last7Txs = expenses.filter {
            val d = parseEpochDays(it.isoDate)
            d in (nowEpoch - 6)..nowEpoch
        }
        val prior28Txs = expenses.filter {
            val d = parseEpochDays(it.isoDate)
            d in (nowEpoch - 34)..(nowEpoch - 7)
        }

        for (env in envelopes) {
            val recentWeekSum = last7Txs.filter {
                (it.envelopeKey.ifBlank { resolveEnvelopeKey(it.category, it.desc, envelopes) }) == env.key
            }.sumOf { it.amount }
            val prior4WeeksSum = prior28Txs.filter {
                (it.envelopeKey.ifBlank { resolveEnvelopeKey(it.category, it.desc, envelopes) }) == env.key
            }.sumOf { it.amount }
            val priorWeeklyAvg = prior4WeeksSum / 4.0

            if (priorWeeklyAvg >= 20.0 && recentWeekSum > priorWeeklyAvg * 1.20) {
                val upPct = (((recentWeekSum - priorWeeklyAvg) / priorWeeklyAvg) * 100.0).roundToInt()
                val label = env.localizedName(lang)
                nudges.add(
                    SmartInsight(
                        id = "anomaly_${env.key}",
                        title = when (lang) {
                            "darija" -> "📈 تنبيه: $label طلع بـ +$upPct% مقارنة بـ 4 سيمانات اللخرين"
                            "ar" -> "📈 تنبيه: $label ارتفع بنسبة +$upPct% مقارنة بآخر 4 أسابيع"
                            "en" -> "📈 $label is up $upPct% vs the last 4 weeks"
                            else -> "📈 $label est en hausse de +$upPct% vs les 4 dernières semaines"
                        },
                        body = when (lang) {
                            "darija" -> "صرفتي ${formatDh(recentWeekSum)} $currency هاد السيمانة مقابل معدل ${formatDh(priorWeeklyAvg)} $currency/سيمانة."
                            "ar" -> "أنفقت ${formatDh(recentWeekSum)} $currency هذا الأسبوع مقابل متوسط ${formatDh(priorWeeklyAvg)} $currency/أسبوع."
                            "en" -> "Spent ${formatDh(recentWeekSum)} $currency in the last 7d vs ${formatDh(priorWeeklyAvg)} $currency weekly avg."
                            else -> "${formatDh(recentWeekSum)} $currency sur 7 jours contre ${formatDh(priorWeeklyAvg)} $currency/semaine en moyenne."
                        },
                        severity = "warn"
                    )
                )
            }
        }
        return nudges
    }

    /**
     * Subscription detector: automatically detects recurring charges from transaction history
     * that are not yet tracked in [existingBills].
     */
    fun detectRecurringSubscriptions(
        allTransactions: List<TransactionItem>,
        existingBills: List<FinancialItem>
    ): List<DetectedSubscription> {
        val existingNamesLower = existingBills.map { it.name.trim().lowercase(Locale.ROOT) }
        val subscriptionKeywords = listOf(
            "netflix", "spotify", "icloud", "youtube", "chatgpt", "shahid", "bein",
            "maroc telecom", "iam", "inwi", "orange", "lydec", "redal", "amendis", "onee",
            "fibre", "abonnement", "forfait", "gym", "salle de sport", "city club", "loyer", "كراء"
        )

        val expenses = allTransactions.filter { it.type == "expense" && it.desc.isNotBlank() }
        val grouped = expenses.groupBy { tx ->
            tx.desc.trim().lowercase(Locale.ROOT)
                .replace(Regex("""\s+\d{4}-\d{2}$"""), "")
                .trim()
        }

        val detected = mutableListOf<DetectedSubscription>()
        for ((normKey, txList) in grouped) {
            if (normKey.length < 3) continue
            if (existingNamesLower.any { it.contains(normKey) || normKey.contains(it) }) continue

            val isKnownSubKeyword = subscriptionKeywords.any { kw -> normKey.contains(kw) }
            val distinctMonths = txList.map { it.isoDate.take(7) }.distinct().size
            if (txList.size >= 2 && (distinctMonths >= 2 || isKnownSubKeyword || txList.map { it.amount }.distinct().size == 1)) {
                val latest = txList.maxByOrNull { it.isoDate } ?: txList.first()
                val dayPart = latest.isoDate.split("-").getOrNull(2)?.toIntOrNull()?.coerceIn(1, 28) ?: 5
                detected.add(
                    DetectedSubscription(
                        merchant = latest.desc.trim().replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        },
                        amount = latest.amount,
                        suggestedDueDay = dayPart,
                        occurrences = txList.size,
                        envelopeKey = latest.envelopeKey.ifBlank { "charges" }
                    )
                )
            } else if (isKnownSubKeyword && txList.isNotEmpty()) {
                val latest = txList.first()
                val dayPart = latest.isoDate.split("-").getOrNull(2)?.toIntOrNull()?.coerceIn(1, 28) ?: 5
                detected.add(
                    DetectedSubscription(
                        merchant = latest.desc.trim().replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        },
                        amount = latest.amount,
                        suggestedDueDay = dayPart,
                        occurrences = txList.size,
                        envelopeKey = "charges"
                    )
                )
            }
        }
        return detected.sortedByDescending { it.occurrences }.take(5)
    }

    /**
     * No-spend streak: counts consecutive days (up to [nowIso] within [window]) with 0 expense transactions.
     */
    fun computeNoSpendStreakDays(
        cycleTransactions: List<TransactionItem>,
        window: PayCycleWindow,
        nowIso: String = todayIso()
    ): Int {
        val anchorIso = if (nowIso in window.startIso..window.endIso) nowIso else window.endIso
        val startEpoch = parseEpochDays(window.startIso)
        val anchorEpoch = parseEpochDays(anchorIso)
        if (anchorEpoch < startEpoch) return 0

        val expenseEpochDays = cycleTransactions
            .filter { it.type == "expense" && it.amount > 0.0 }
            .map { parseEpochDays(it.isoDate) }
            .toSet()

        var streak = 0
        var d = anchorEpoch
        while (d >= startEpoch) {
            if (d in expenseEpochDays) {
                break
            }
            streak++
            d--
        }
        return streak
    }

    fun computeNoSpendStreakDays(
        allTransactions: List<TransactionItem>,
        nowIso: String = todayIso()
    ): Int {
        val window = computeCycleWindow(1, 0, nowIso)
        return computeNoSpendStreakDays(allTransactions, window, nowIso)
    }

    /**
     * Biggest spending day in the active cycle for the Monthly Recap Card.
     */
    fun computeBiggestSpendingDayInCycle(
        cycleTransactions: List<TransactionItem>
    ): Pair<String, Double> {
        val byDay = cycleTransactions
            .filter { it.type == "expense" && it.isoDate.isNotBlank() }
            .groupBy { it.isoDate }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        return byDay.maxByOrNull { it.value }?.toPair() ?: ("" to 0.0)
    }

    /**
     * Month-end forecast message:
     * "At this pace you'll finish the month with 1,240 MAD."
     */
    fun formatMonthEndForecast(
        totalAvailableBudget: Double,
        projectedCycleSpend: Double,
        currency: String,
        lang: String,
        hideAmounts: Boolean = false
    ): String {
        val forecastEnd = totalAvailableBudget - projectedCycleSpend
        return formatMonthEndForecast(forecastEnd, currency, lang, hideAmounts)
    }

    fun formatMonthEndForecast(
        forecastEnd: Double,
        currency: String,
        lang: String,
        hideAmounts: Boolean = false
    ): String {
        val absFormatted = if (hideAmounts) "••••" else formatDh(abs(forecastEnd))
        return if (forecastEnd >= 0) {
            when (lang) {
                "darija" -> "بهاد الوتيرة غادي تكمل الشهر بـ +$absFormatted $currency"
                "ar" -> "بهذه الوتيرة ستنهي الشهر بـ +$absFormatted $currency"
                "en" -> "At this pace you'll finish the month with $absFormatted $currency."
                else -> "À ce rythme, vous finirez le mois avec +$absFormatted $currency."
            }
        } else {
            when (lang) {
                "darija" -> "رد بالك: بهاد الوتيرة غادي تكمل الشهر بـ ناقص -$absFormatted $currency"
                "ar" -> "تنبيه: بهذه الوتيرة ستنهي الشهر بعجز -$absFormatted $currency"
                "en" -> "At this pace you'll finish the month -$absFormatted $currency over budget."
                else -> "Attention : à ce rythme, fin de mois à -$absFormatted $currency."
            }
        }
    }

    /**
     * Smart defaults: guesses the category/envelope and account from the description or the last-used one.
     */
    fun inferSmartDefaults(
        description: String,
        allTransactions: List<TransactionItem>,
        envelopes: List<EnvelopeConfig>,
        accounts: List<AccountItem>,
        lang: String = "fr"
    ): SmartDefaultGuess {
        val sortedRecent = allTransactions.sortedWith(
            compareByDescending<TransactionItem> { it.isoDate }.thenByDescending { it.id }
        )
        val lastUsedAccount = sortedRecent.firstOrNull { it.accountId.isNotBlank() }?.accountId
            ?: accounts.firstOrNull()?.id
            ?: "acc_cash"

        val cleanDesc = description.trim().lowercase(Locale.ROOT)
        if (cleanDesc.length >= 2) {
            // 1. Check historical transactions with matching description
            val histMatch = sortedRecent.firstOrNull {
                val d = it.desc.trim().lowercase(Locale.ROOT)
                it.type != "transfer" && d.isNotBlank() && (d.contains(cleanDesc) || cleanDesc.contains(d))
            }
            if (histMatch != null) {
                val envConfig = envelopes.find { it.key == histMatch.envelopeKey }
                return SmartDefaultGuess(
                    envelopeKey = envConfig?.key ?: histMatch.envelopeKey.ifBlank { "cafe" },
                    categoryName = envConfig?.localizedName(lang) ?: histMatch.category.ifBlank { "Général" },
                    accountId = histMatch.accountId.ifBlank { lastUsedAccount }
                )
            }

            // 2. Check Darija/Arabic/FR/EN keyword parser
            val parsed = VoiceDarijaParser.parse("$description 10", envelopes, lang)
            if (parsed != null && parsed.envelopeKey != "autre") {
                val accountForEnv = sortedRecent.firstOrNull { it.envelopeKey == parsed.envelopeKey }?.accountId
                    ?: lastUsedAccount
                return SmartDefaultGuess(
                    envelopeKey = parsed.envelopeKey,
                    categoryName = parsed.categoryName,
                    accountId = accountForEnv
                )
            }
        }

        // 3. Fallback to most recently used category and account
        val lastExpense = sortedRecent.firstOrNull { it.type == "expense" }
        val fallbackEnv = envelopes.find { it.key == lastExpense?.envelopeKey } ?: envelopes.firstOrNull()
        return SmartDefaultGuess(
            envelopeKey = fallbackEnv?.key ?: "cafe",
            categoryName = fallbackEnv?.localizedName(lang) ?: lastExpense?.category ?: "Café & Sorties",
            accountId = lastUsedAccount
        )
    }

    fun inferSmartDefaults(
        description: String,
        allTransactions: List<TransactionItem>,
        envelopeStatuses: List<EnvelopeStatus>,
        defaultAccountId: String,
        lang: String = "fr"
    ): SmartDefaultGuess? {
        return inferSmartDefaults(
            description = description,
            allTransactions = allTransactions,
            envelopes = envelopeStatuses.map { it.config },
            accounts = listOf(AccountItem(id = defaultAccountId, name = "Default")),
            lang = lang
        )
    }

    /**
     * Duplicate warning: flags the same amount and category added twice within a few minutes (or same day).
     */
    fun findRecentDuplicateTransaction(
        type: String,
        amount: Double,
        envelopeKey: String,
        category: String,
        allTransactions: List<TransactionItem>,
        nowIso: String = todayIso(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): TransactionItem? {
        if (amount <= 0.0) return null
        return allTransactions.firstOrNull { tx ->
            val sameType = tx.type == type
            val sameAmount = abs(tx.amount - amount) < 0.01
            val sameCategory = (tx.envelopeKey.isNotBlank() && tx.envelopeKey == envelopeKey) ||
                tx.category.equals(category, ignoreCase = true)
            val createdMs = tx.createdAt?.toDate()?.time ?: 0L
            val isWithinMinutes = if (createdMs > 0L) {
                abs(nowEpochMs - createdMs) <= 15 * 60 * 1000L
            } else {
                tx.isoDate == nowIso || allTransactions.size <= 10
            }
            sameType && sameAmount && sameCategory && isWithinMinutes
        }
    }

    fun findRecentDuplicateTransaction(
        amount: Double,
        envelopeKey: String,
        category: String,
        allTransactions: List<TransactionItem>,
        nowMs: Long = System.currentTimeMillis()
    ): TransactionItem? {
        return findRecentDuplicateTransaction(
            type = "expense",
            amount = amount,
            envelopeKey = envelopeKey,
            category = category,
            allTransactions = allTransactions,
            nowEpochMs = nowMs
        )
    }

    /**
     * Seasonal countdown and suggested monthly saving for Ramadan, Aïd al-Adha, Back-to-School, etc.
     */
    fun computeSeasonalCountdown(
        targetIso: String,
        targetAmount: Double,
        savedAmount: Double,
        nowIso: String = todayIso()
    ): SeasonalCountdownInfo {
        val validTarget = if (isValidIsoDate(targetIso)) targetIso else "2027-03-01"
        val nowEpoch = parseEpochDays(nowIso)
        val targetEpoch = parseEpochDays(validTarget)
        val daysLeft = max(0, (targetEpoch - nowEpoch).toInt())
        val monthsLeft = max(1, kotlin.math.ceil(daysLeft / 30.0).toInt())
        val remAmount = max(0.0, targetAmount - savedAmount)
        val monthlySave = if (remAmount > 0.0) remAmount / monthsLeft else 0.0
        return SeasonalCountdownInfo(
            daysRemaining = daysLeft,
            monthsRemaining = monthsLeft,
            remainingAmount = remAmount,
            suggestedMonthlySaving = monthlySave
        )
    }

    fun computeSeasonalCountdown(
        seasonalItem: FinancialItem,
        nowIso: String = todayIso()
    ): SeasonalCountdownInfo {
        return computeSeasonalCountdown(
            targetIso = seasonalItem.dateOrCycle,
            targetAmount = seasonalItem.amount,
            savedAmount = seasonalItem.secondaryAmount,
            nowIso = nowIso
        )
    }

    /**
     * What-if simulator goal impact: "Cut takeout by 20% and you reach your goal 2 months sooner."
     */
    fun computeWhatIfGoalImpact(
        monthlySaved: Double,
        goals: List<FinancialItem>,
        seasonals: List<FinancialItem>,
        lang: String,
        currency: String
    ): String {
        val targetItem = (goals + seasonals).firstOrNull { (it.amount - it.secondaryAmount) > 0.0 }
        val remGoal = if (targetItem != null) max(0.0, targetItem.amount - targetItem.secondaryAmount) else 5000.0
        val goalName = targetItem?.name ?: when (lang) {
            "darija" -> "صندوق الطوارئ"
            "ar" -> "هدف الادخار"
            "en" -> "Emergency Fund"
            else -> "Objectif d'épargne"
        }
        val baselineMonthlySave = max(200.0, remGoal / 10.0)
        val baseMonths = kotlin.math.ceil(remGoal / baselineMonthlySave).toInt().coerceAtLeast(2)
        val newMonths = kotlin.math.ceil(remGoal / (baselineMonthlySave + max(1.0, monthlySaved))).toInt().coerceAtLeast(1)
        val monthsSooner = max(1, baseMonths - newMonths)

        return when (lang) {
            "darija" -> "🎯 غادي توصل لهدف « $goalName » قبل بـ $monthsSooner أشهر (+${formatDh(monthlySaved)} $currency/شهر)!"
            "ar" -> "🎯 ستصل إلى هدف « $goalName » أبكر بـ $monthsSooner أشهر (+${formatDh(monthlySaved)} $currency/شهر)!"
            "en" -> "🎯 You reach your goal \"$goalName\" $monthsSooner months sooner (+${formatDh(monthlySaved)} $currency/mo)!"
            else -> "🎯 Vous atteignez votre objectif « $goalName » $monthsSooner mois plus tôt (+${formatDh(monthlySaved)} $currency/mois) !"
        }
    }

    fun computeWhatIfGoalImpact(
        envelopeName: String,
        envelopeMonthlySpend: Double,
        cutPercent: Double,
        currentMonthlySavingCapacity: Double,
        goals: List<FinancialItem>,
        currencySymbol: String,
        language: String,
        hideAmounts: Boolean = false
    ): String {
        val monthlySaved = envelopeMonthlySpend * (cutPercent / 100.0)
        return computeWhatIfGoalImpact(
            monthlySaved = monthlySaved,
            goals = goals,
            seasonals = emptyList(),
            lang = language,
            currency = currencySymbol
        )
    }

    /**
     * Multi-currency conversion for MRE users & trips (MAD, EUR, USD).
     */
    fun convertCurrencyAmount(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        eurToMadRate: Double = DEFAULT_EUR_TO_MAD,
        usdToMadRate: Double = DEFAULT_USD_TO_MAD
    ): Double {
        if (amount <= 0.0) return 0.0
        val normFrom = normalizeCurrencyCode(fromCurrency)
        val normTo = normalizeCurrencyCode(toCurrency)
        if (normFrom == normTo) return amount

        val safeEur = if (eurToMadRate > 0.1) eurToMadRate else DEFAULT_EUR_TO_MAD
        val safeUsd = if (usdToMadRate > 0.1) usdToMadRate else DEFAULT_USD_TO_MAD

        // Convert to MAD first
        val inMad = when (normFrom) {
            "EUR" -> amount * safeEur
            "USD" -> amount * safeUsd
            else -> amount
        }
        return when (normTo) {
            "EUR" -> inMad / safeEur
            "USD" -> inMad / safeUsd
            else -> inMad
        }
    }

    fun normalizeCurrencyCode(symbolOrCode: String): String {
        val u = symbolOrCode.trim().uppercase(Locale.ROOT)
        return when {
            u.contains("EUR") || u.contains("€") -> "EUR"
            u.contains("USD") || u.contains("$") -> "USD"
            else -> "MAD"
        }
    }

    fun moroccanBillTemplates(): List<MoroccanBillTemplate> = listOf(
        MoroccanBillTemplate("tpl_lydec", "Lydec / SRM (Eau & Élec)", "Eau & Électricité Casablanca", 380.0, 15, "💧"),
        MoroccanBillTemplate("tpl_redal", "Redal (Eau & Élec)", "Eau & Électricité Rabat-Salé", 340.0, 15, "💧"),
        MoroccanBillTemplate("tpl_amendis", "Amendis (Eau & Élec)", "Eau & Électricité Tanger/Tétouan", 350.0, 15, "💧"),
        MoroccanBillTemplate("tpl_onee", "ONEE (Électricité & Eau)", "Office National Électricité & Eau", 320.0, 18, "⚡"),
        MoroccanBillTemplate("tpl_iam", "Maroc Telecom (IAM Fibre/ADSL)", "Internet & Fixe IAM", 400.0, 5, "🌐"),
        MoroccanBillTemplate("tpl_inwi", "Inwi (Forfait / Fibre)", "Mobile & Internet Inwi", 249.0, 10, "📱"),
        MoroccanBillTemplate("tpl_orange", "Orange Maroc (Forfait / Fibre)", "Mobile & Fibre Orange", 249.0, 10, "🍊"),
        MoroccanBillTemplate("tpl_rent", "Loyer / الكراء", "Loyer mensuel appartement", 2500.0, 1, "🏠")
    )

    fun moroccanSeasonalTemplates(): List<MoroccanSeasonalTemplate> = listOf(
        MoroccanSeasonalTemplate("sea_ramadan", "Ramadan (قفة ومصاريف رمضان)", "Provisions & Table du Ftour", 3000.0, "2027-02-08", "🌙"),
        MoroccanSeasonalTemplate("sea_aid_adha", "Aïd al-Adha (صندوق الحولي)", "Mouton & Fête de l'Aïd", 4500.0, "2027-05-16", "🐏"),
        MoroccanSeasonalTemplate("sea_rentree", "Rentrée Scolaire (الدخول المدرسي)", "Fournitures, manuels & inscription", 2500.0, "2027-09-01", "🎒"),
        MoroccanSeasonalTemplate("sea_vacances", "Vacances d'été (عطلة الصيف)", "Voyage & Détente en famille", 5000.0, "2027-07-15", "🏖️")
    )

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
                val whoseTurn = daret.subtitle.ifBlank { "Tour #${daret.dayOrTurn}" }
                list.add(
                    SmartReminder(
                        id = "rem_daret_${daret.id}",
                        kind = "daret",
                        title = "Cotisation Daret : ${daret.name}",
                        subtitle = "$whoseTurn · Payé ${daret.extraInt}/$totalMembers · ${formatDh(daret.amount)} $currency/mois",
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
                        title = if (isLent) "Sellef à encaisser : ${debt.name}" else "Sellef à rembourser : ${debt.name}",
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

    fun formatAmountOrMask(value: Double, hideAmounts: Boolean): String {
        return if (hideAmounts) "••••" else formatDh(value)
    }

    fun formatCurrencyOrMask(value: Double, currencySymbol: String, hideAmounts: Boolean): String {
        return if (hideAmounts) "•••• $currencySymbol" else "${formatDh(value)} $currencySymbol"
    }
}
