package com.example.domain

import com.example.data.AccountItem
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import com.example.data.DefaultCategoryResolver
import com.masroufi.core.Money
import com.masroufi.core.MoneyParser
import com.masroufi.core.sumMoney
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Calendar
import java.util.Collections
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class ProjectionAlertLevel {
    NONE, AMBER, RED
}

data class CycleSpendProjection(
    val projectedTotal: Money = Money.ZERO,
    val optimisticTotal: Money = Money.ZERO,
    val medianDailyVariable: Money = Money.ZERO,
    val lowerQuartileDailyVariable: Money = Money.ZERO,
    val daysElapsed: Int = 0,
    val transactionCount: Int = 0,
    val showEstimateAlert: Boolean = false,
    val alertLevel: ProjectionAlertLevel = ProjectionAlertLevel.NONE
)

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
    val currentBalance: Money,
    val totalIn: Money,
    val totalOut: Money
)

data class EnvelopeStatus(
    val config: EnvelopeConfig,
    val baseAllocated: Money,
    val rolloverAmount: Money,
    val totalAvailable: Money,
    val spent: Money,
    val previousSpent: Money = Money.ZERO,
    val remaining: Money,
    val usagePercent: Int,
    val isOver90Percent: Boolean
)

enum class PurchaseVerdict {
    FITS_TODAY,
    LOWERS_ALLOWANCE,
    OVER_POOL;

    companion object {
        val GREEN: PurchaseVerdict get() = FITS_TODAY
        val AMBER: PurchaseVerdict get() = LOWERS_ALLOWANCE
        val RED: PurchaseVerdict get() = OVER_POOL
    }
}

data class PurchaseImpact(
    val amount: Money,
    val safePool: Money,
    val daysRemaining: Int,
    val currentDailyAllowance: Money,
    val newSafeToday: Money,
    val delta: Money,
    val remainingTodayAllowance: Money,
    val overSafePoolAmount: Money,
    val verdict: PurchaseVerdict,
    val runOutIso: String? = null,
    val envelopeUsageAfter: Int? = null,
    val envelopeKey: String? = null,
    val envelopeNewSpent: Money? = null,
    val envelopeRemainingAfter: Money? = null,
    val envelopeHasCap: Boolean = false
) {
    val newDailyAllowance: Money get() = newSafeToday
    val dailyAllowanceDelta: Money get() = delta
    val envelopeNewUsagePercent: Int? get() = envelopeUsageAfter
}

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
    val amount: Money,
    val actionLabel: String,
    val item: FinancialItem
)

data class DetectedSubscription(
    val merchant: String,
    val amount: Money,
    val suggestedDueDay: Int,
    val occurrences: Int,
    val envelopeKey: String = "charges"
) {
    val name: String get() = merchant
    val monthlyAmount: Money get() = amount
    val typicalDayOfMonth: Int get() = suggestedDueDay
}

data class MoroccanBillTemplate(
    val id: String,
    val name: String,
    val providerSubtitle: String,
    val defaultAmount: Money,
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
    val defaultTargetAmount: Money,
    val defaultTargetIso: String,
    val emoji: String = "🌙"
) {
    val key: String get() = id
    val targetAmount: Money get() = defaultTargetAmount
    val targetIsoDate: String get() = defaultTargetIso
}

data class SeasonalCountdownInfo(
    val daysRemaining: Int,
    val monthsRemaining: Int,
    val remainingAmount: Money,
    val suggestedMonthlySaving: Money
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
        DefaultCategoryResolver.canonicalDefaultId(category)?.let { canonical ->
            if (envelopes.isEmpty() || envelopes.any { it.key.equals(canonical, ignoreCase = true) }) {
                return canonical
            }
        }
        val strippedCat = DefaultCategoryResolver.stripNumericPrefix(category)
        val text = "$strippedCat $desc".lowercase(Locale.ROOT)
        for (env in envelopes) {
            if (env.key.isNotBlank() && (
                    strippedCat.equals(DefaultCategoryResolver.stripNumericPrefix(env.nameFr), ignoreCase = true) ||
                    strippedCat.equals(DefaultCategoryResolver.stripNumericPrefix(env.nameEn), ignoreCase = true) ||
                    strippedCat.equals(DefaultCategoryResolver.stripNumericPrefix(env.nameAr), ignoreCase = true) ||
                    strippedCat.equals(DefaultCategoryResolver.stripNumericPrefix(env.nameDarija), ignoreCase = true) ||
                    strippedCat.equals(env.key, ignoreCase = true)
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
    ): List<AccountWithBalance> = NetWorthCalculator.computeAccountBalances(accounts, transactions)

    fun computeEnvelopes(
        envelopes: List<EnvelopeConfig>,
        baseBudget: Money,
        rolloverEnabled: Boolean,
        currentWindow: PayCycleWindow,
        previousWindow: PayCycleWindow,
        allTransactions: List<TransactionItem>,
        maxDateIso: String = ""
    ): List<EnvelopeStatus> {
        val curExpenses = allTransactions.filter {
            it.type == "expense" &&
                isInWindow(it.isoDate, currentWindow) &&
                (maxDateIso.isBlank() || it.isoDate <= maxDateIso)
        }
        val prevExpenses = allTransactions.filter { it.type == "expense" && isInWindow(it.isoDate, previousWindow) }
        val hundredBd = BigDecimal.valueOf(100L)

        return envelopes.map { env ->
            val key = env.key
            val spentCurrent = curExpenses
                .filter { (it.envelopeKey.ifBlank { resolveEnvelopeKey(it.category, it.desc, envelopes) }) == key }
                .map { it.amount }
                .sumMoney()

            val spentPrev = prevExpenses
                .filter { (it.envelopeKey.ifBlank { resolveEnvelopeKey(it.category, it.desc, envelopes) }) == key }
                .map { it.amount }
                .sumMoney()

            val baseAllocated = if (env.hasCap) {
                val ratio = BigDecimal.valueOf(env.percentage).divide(hundredBd, 10, RoundingMode.HALF_UP)
                baseBudget.multiplyRate(ratio)
            } else {
                Money.ZERO
            }

            val rolloverAmount = if (rolloverEnabled && env.hasCap && prevExpenses.isNotEmpty()) {
                (baseAllocated - spentPrev).coerceAtLeast(Money.ZERO)
            } else {
                Money.ZERO
            }

            val totalAvailable = baseAllocated + rolloverAmount
            val remaining = if (env.hasCap) totalAvailable - spentCurrent else Money.ZERO
            val usagePct = if (env.hasCap && totalAvailable.isPositive) {
                ((spentCurrent.centimes.toDouble() / totalAvailable.centimes.toDouble()) * 100.0).roundToInt()
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
        totalAvailableBudget: Money,
        cycleExpenses: Money,
        previousCycleExpenses: Money,
        safeToSpendToday: Money,
        dailyAverageSpend: Money,
        envelopes: List<EnvelopeStatus>,
        projectedCycleSpend: Money = dailyAverageSpend * window.totalDays.toLong()
    ): List<SmartInsight> {
        val insights = mutableListOf<SmartInsight>()
        val timeProgressPct = ((window.daysElapsed.toDouble() / max(1, window.totalDays)) * 100.0).roundToInt()
        val budgetProgressPct = if (totalAvailableBudget.isPositive) {
            ((cycleExpenses.centimes.toDouble() / totalAvailableBudget.centimes.toDouble()) * 100.0).roundToInt()
        } else 0

        // 1. Pace vs Cycle Time Insight (uses unified projectedCycleSpend from state.spendProjection)
        if (!cycleExpenses.isPositive) {
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

        if (budgetProgressPct > timeProgressPct + 12 || projectedCycleSpend > totalAvailableBudget) {
            val projectedDiff = (projectedCycleSpend - totalAvailableBudget).coerceAtLeast(Money.ZERO)
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
            val potentialSave = (totalAvailableBudget - projectedCycleSpend).coerceAtLeast(Money.ZERO)
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
        val topEnv = envelopes.filter { it.spent.isPositive }.maxByOrNull { it.spent }
        if (topEnv != null) {
            val share = if (cycleExpenses.isPositive) {
                ((topEnv.spent.centimes.toDouble() / cycleExpenses.centimes.toDouble()) * 100.0).roundToInt()
            } else 0
            val save20 = topEnv.spent.multiplyRate(BigDecimal("0.20"))
            val save20Year = save20 * 12L
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
                        "darija" -> "صرفتي فيها ${formatDh(topEnv.spent)} $currency. غير تنقص 20% غادي تربح ${formatDh(save20)} $currency فـ الشهر (${formatDh(save20Year)} $currency فـ العام)."
                        "ar" -> "أنفقت ${formatDh(topEnv.spent)} $currency. خفض 20% يوفر لك ${formatDh(save20)} $currency شهرياً."
                        "en" -> "Spent ${formatDh(topEnv.spent)} $currency. Cutting 20% saves ${formatDh(save20)} $currency/mo (${formatDh(save20Year)} $currency/yr)."
                        else -> "Vous y avez dépensé ${formatDh(topEnv.spent)} $currency. Réduire de 20% libère +${formatDh(save20)} $currency/mois (+${formatDh(save20Year)} $currency/an)."
                    },
                    severity = if (topEnv.isOver90Percent) "warn" else "info"
                )
            )
        }

        // 3. Month-over-Month Trend Insight
        if (previousCycleExpenses.isPositive) {
            val delta = cycleExpenses - previousCycleExpenses
            val deltaPct = ((delta.centimes.toDouble() / previousCycleExpenses.centimes.toDouble()) * 100.0).roundToInt()
            insights.add(
                SmartInsight(
                    id = "mom_trend",
                    title = if (delta <= Money.ZERO) {
                        "Tendance mensuelle : ${deltaPct}% vs cycle précédent"
                    } else {
                        "Tendance mensuelle : +${deltaPct}% vs cycle précédent"
                    },
                    body = if (delta <= Money.ZERO) {
                        "Vous avez dépensé ${formatDh(delta.abs())} $currency de moins que le cycle dernier (${formatDh(previousCycleExpenses)} $currency)."
                    } else {
                        "Vos dépenses dépassent le cycle précédent de ${formatDh(delta)} $currency (${formatDh(previousCycleExpenses)} $currency)."
                    },
                    severity = if (delta <= Money.ZERO) "good" else "warn"
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

        val minWeeklyBaseline = Money.fromDh(20)
        val spikeThresholdFactor = BigDecimal("1.20")

        for (env in envelopes) {
            val recentWeekSum = last7Txs.filter {
                (it.envelopeKey.ifBlank { resolveEnvelopeKey(it.category, it.desc, envelopes) }) == env.key
            }.map { it.amount }.sumMoney()
            val prior4WeeksSum = prior28Txs.filter {
                (it.envelopeKey.ifBlank { resolveEnvelopeKey(it.category, it.desc, envelopes) }) == env.key
            }.map { it.amount }.sumMoney()
            val priorWeeklyAvg = prior4WeeksSum.divideBy(4L)

            if (priorWeeklyAvg >= minWeeklyBaseline && recentWeekSum > priorWeeklyAvg.multiplyRate(spikeThresholdFactor)) {
                val upPct = (((recentWeekSum - priorWeeklyAvg).centimes.toDouble() / priorWeeklyAvg.centimes.toDouble()) * 100.0).roundToInt()
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

    private val DEBT_REPAYMENT_KEYWORDS = listOf(
        "remboursement",
        "sellef",
        "selef",
        "crédit",
        "credit",
        "dette",
        "سلف",
        "كريدي",
        "تسديد",
        "رد الكريدي"
    )

    private fun isDebtRepaymentTransaction(tx: TransactionItem): Boolean {
        if (tx.id.startsWith("tx_debt_", ignoreCase = true)) return true
        val combined = "${tx.category} ${tx.desc}".lowercase(Locale.ROOT)
        return DEBT_REPAYMENT_KEYWORDS.any { kw -> combined.contains(kw) }
    }

    /**
     * Phase 0 Overspend Projection:
     * projected = spentSoFar + scheduledBills + median(daily variable spend this cycle,
     *             ignoring scheduled bills and single-day outliers above 3x the median) * daysLeft.
     * Shows the alert only if daysElapsed >= 7 and transactionCount >= 10, labeled "Estimate".
     * Uses amber when the projection exceeds the budget, red only when even an optimistic
     * estimate (lower-quartile daily spend) exceeds it.
     */
    fun computeOverspendProjection(
        window: PayCycleWindow,
        totalAvailableBudget: Money,
        spentSoFar: Money,
        scheduledBills: Money,
        pastOrTodayCycleTransactions: List<TransactionItem>,
        bills: List<FinancialItem> = emptyList()
    ): CycleSpendProjection {
        val expenseTxs = pastOrTodayCycleTransactions.filter { it.type == "expense" && it.amount.isPositive }
        val txCount = expenseTxs.size
        val billNamesLower = bills.map { it.name.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }

        val variableExpenses = expenseTxs.filter { tx ->
            val isBillTx = tx.id.startsWith("tx_bill_", ignoreCase = true) ||
                tx.envelopeKey.equals("charges", ignoreCase = true) ||
                DefaultCategoryResolver.canonicalDefaultId(tx.category) == "charges" ||
                billNamesLower.any { bn -> tx.desc.lowercase(Locale.ROOT).contains(bn) }
            !isBillTx
        }

        val dailyByDate = variableExpenses
            .groupBy { it.isoDate }
            .mapValues { (_, list) -> list.map { it.amount }.sumMoney() }

        val elapsedCount = max(1, window.daysElapsed)
        val rawDailySamples: List<Money> = dailyByDate.values.sorted()

        val initialMedian = computePercentile(rawDailySamples, 0.50)
        val filteredSamples = if (initialMedian.isPositive) {
            val outlierCap = initialMedian * 3L
            rawDailySamples.filter { it <= outlierCap }.ifEmpty { rawDailySamples }
        } else {
            rawDailySamples
        }

        val cleanMedian = computePercentile(filteredSamples, 0.50)
        val lowerQuartile = computePercentile(filteredSamples, 0.25)

        val daysLeft = if (window.isCurrentCycle) {
            (window.totalDays - window.daysElapsed).coerceAtLeast(0)
        } else {
            0
        }

        // Unified pace projection: if there are no separate outlier spikes, pace = spentSoFar / elapsedCount.
        // If there are single-day >3x outliers filtered out, use cleanMedian; otherwise use pace so 5 276 DH in 16/31d -> ~10 222 DH.
        val hasFilteredOutliers = filteredSamples.size < rawDailySamples.size
        val paceDailyRate = spentSoFar.divideBy(elapsedCount.toLong())
        val effectiveDailyRate = if (hasFilteredOutliers && cleanMedian.isPositive) {
            cleanMedian
        } else if (paceDailyRate.isPositive) {
            paceDailyRate
        } else {
            cleanMedian
        }

        val projectedTotal = if (window.isCurrentCycle) {
            spentSoFar + scheduledBills + (effectiveDailyRate * daysLeft.toLong())
        } else {
            spentSoFar
        }
        val optimisticTotal = if (window.isCurrentCycle) {
            spentSoFar + scheduledBills + (lowerQuartile * daysLeft.toLong())
        } else {
            spentSoFar
        }

        val eligibleForAlert = window.isCurrentCycle && window.daysElapsed >= 7 && txCount >= 10
        val alertLevel = when {
            !eligibleForAlert -> ProjectionAlertLevel.NONE
            optimisticTotal > totalAvailableBudget -> ProjectionAlertLevel.RED
            projectedTotal > totalAvailableBudget -> ProjectionAlertLevel.AMBER
            else -> ProjectionAlertLevel.NONE
        }

        return CycleSpendProjection(
            projectedTotal = projectedTotal,
            optimisticTotal = optimisticTotal,
            medianDailyVariable = cleanMedian,
            lowerQuartileDailyVariable = lowerQuartile,
            daysElapsed = window.daysElapsed,
            transactionCount = txCount,
            showEstimateAlert = eligibleForAlert && alertLevel != ProjectionAlertLevel.NONE,
            alertLevel = alertLevel
        )
    }

    private fun computePercentile(sorted: List<Money>, p: Double): Money {
        if (sorted.isEmpty()) return Money.ZERO
        if (sorted.size == 1) return sorted[0]
        val pos = p.coerceIn(0.0, 1.0) * (sorted.size - 1)
        val lowerIdx = pos.toInt()
        val upperIdx = (lowerIdx + 1).coerceAtMost(sorted.lastIndex)
        val weight = BigDecimal.valueOf(pos - lowerIdx)
        val oneMinusWeight = BigDecimal.ONE.subtract(weight)
        val lowerCents = BigDecimal.valueOf(sorted[lowerIdx].centimes)
        val upperCents = BigDecimal.valueOf(sorted[upperIdx].centimes)
        val interpolated = lowerCents.multiply(oneMinusWeight)
            .add(upperCents.multiply(weight))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        return Money.fromCentimes(interpolated)
    }

    /**
     * Subscription detector (Phase 0):
     * - Ignores debt repayments ("Remboursement" / sellef) and transfers.
     * - Ignores locally dismissed merchants ("Not a bill").
     * - Requires 3+ occurrences, roughly monthly or weekly spacing, and amounts within 10%.
     */
    fun detectRecurringSubscriptions(
        allTransactions: List<TransactionItem>,
        existingBills: List<FinancialItem>,
        dismissedMerchants: Set<String> = emptySet()
    ): List<DetectedSubscription> {
        val existingNamesLower = existingBills.map { it.name.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
        val dismissedLower = dismissedMerchants.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.toSet()

        val expenses = allTransactions.filter { tx ->
            tx.type == "expense" &&
                tx.desc.isNotBlank() &&
                !isDebtRepaymentTransaction(tx)
        }
        val grouped = expenses.groupBy { tx ->
            tx.desc.trim().lowercase(Locale.ROOT)
                .replace(Regex("""\s+\d{4}-\d{2}$"""), "")
                .trim()
        }

        val detected = mutableListOf<DetectedSubscription>()
        for ((normKey, txList) in grouped) {
            if (normKey.length < 3) continue
            if (normKey in dismissedLower) continue
            if (existingNamesLower.any { it.contains(normKey) || normKey.contains(it) }) continue
            if (txList.size < 3) continue

            val sorted = txList.sortedBy { it.isoDate }
            val amounts = sorted.map { it.amount }
            val minAmt = amounts.minOrNull() ?: Money.ZERO
            val maxAmt = amounts.maxOrNull() ?: Money.ZERO
            if (!minAmt.isPositive || (maxAmt - minAmt).centimes.toDouble() / minAmt.centimes.toDouble() > 0.100001) continue

            val intervals = sorted.zipWithNext { a, b ->
                (parseEpochDays(b.isoDate) - parseEpochDays(a.isoDate)).toInt()
            }
            if (intervals.isEmpty()) continue
            val avgInterval = intervals.average()
            val isMonthly = intervals.all { it in 18..45 } && avgInterval in 20.0..40.0
            val isWeekly = intervals.all { it in 4..11 } && avgInterval in 5.0..9.0
            if (!isMonthly && !isWeekly) continue

            val latest = sorted.last()
            val dayPart = latest.isoDate.split("-").getOrNull(2)?.toIntOrNull()?.coerceIn(1, 28) ?: 5
            detected.add(
                DetectedSubscription(
                    merchant = latest.desc.trim().replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    },
                    amount = latest.amount,
                    suggestedDueDay = dayPart,
                    occurrences = sorted.size,
                    envelopeKey = latest.envelopeKey.ifBlank { "charges" }
                )
            )
        }
        return detected.sortedByDescending { it.occurrences }.take(5)
    }

    /**
     * No-spend streak: counts consecutive days (up to [nowIso] within [window]) with 0 expense transactions.
     * When [verifiedActiveDates] is provided, a 0-expense day only counts if the user opened the app,
     * tapped "Nothing today", or logged a non-expense operation on that date.
     */
    fun computeNoSpendStreakDays(
        cycleTransactions: List<TransactionItem>,
        window: PayCycleWindow,
        nowIso: String = todayIso(),
        verifiedActiveDates: Set<String>? = null
    ): Int {
        val anchorIso = if (nowIso in window.startIso..window.endIso) nowIso else window.endIso
        val startEpoch = parseEpochDays(window.startIso)
        val anchorEpoch = parseEpochDays(anchorIso)
        if (anchorEpoch < startEpoch) return 0

        val expenseEpochDays = cycleTransactions
            .filter { it.type == "expense" && it.amount.isPositive }
            .map { parseEpochDays(it.isoDate) }
            .toSet()

        val anyTxEpochDays = cycleTransactions
            .filter { it.isoDate.isNotBlank() }
            .map { parseEpochDays(it.isoDate) }
            .toSet()

        val verifiedEpochDays = verifiedActiveDates
            ?.filter { it.isNotBlank() }
            ?.map { parseEpochDays(it) }
            ?.toSet()

        var streak = 0
        var d = anchorEpoch
        while (d >= startEpoch) {
            if (d in expenseEpochDays) {
                break
            }
            if (verifiedEpochDays != null && d !in verifiedEpochDays && d !in anyTxEpochDays) {
                break
            }
            streak++
            d--
        }
        return streak
    }

    fun computeNoSpendStreakDays(
        allTransactions: List<TransactionItem>,
        nowIso: String = todayIso(),
        verifiedActiveDates: Set<String>? = null
    ): Int {
        val window = computeCycleWindow(1, 0, nowIso)
        return computeNoSpendStreakDays(allTransactions, window, nowIso, verifiedActiveDates)
    }

    /**
     * Biggest spending day in the active cycle for the Monthly Recap Card.
     */
    fun computeBiggestSpendingDayInCycle(
        cycleTransactions: List<TransactionItem>
    ): Pair<String, Money> {
        val byDay = cycleTransactions
            .filter { it.type == "expense" && it.isoDate.isNotBlank() }
            .groupBy { it.isoDate }
            .mapValues { (_, list) -> list.map { it.amount }.sumMoney() }
        return byDay.maxByOrNull { it.value }?.toPair() ?: ("" to Money.ZERO)
    }

    /**
     * Month-end forecast message:
     * "At this pace you'll finish the month with 1,240 MAD."
     */
    fun formatMonthEndForecast(
        totalAvailableBudget: Money,
        projectedCycleSpend: Money,
        currency: String,
        lang: String,
        hideAmounts: Boolean = false
    ): String {
        val forecastEnd = totalAvailableBudget - projectedCycleSpend
        return formatMonthEndForecast(forecastEnd, currency, lang, hideAmounts)
    }

    fun formatMonthEndForecast(
        forecastEnd: Money,
        currency: String,
        lang: String,
        hideAmounts: Boolean = false
    ): String {
        val absFormatted = if (hideAmounts) "••••" else formatDh(forecastEnd.abs())
        return if (!forecastEnd.isNegative) {
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

    private const val MAX_LEARNED_CATEGORIES = 80
    private const val DARET_POT_COLLECTED_MARKER = "pot_collected"

    private val learnedCategoryMap: MutableMap<String, String> =
        Collections.synchronizedMap(LinkedHashMap<String, String>())

    fun learnCategoryForDescription(description: String, envelopeKey: String) {
        val cleanDesc = description.trim().lowercase(Locale.ROOT)
            .replace("|", " ")
            .replace("=", " ")
            .trim()
            .take(40)
        val cleanKey = envelopeKey.trim().lowercase(Locale.ROOT)
            .replace("|", "")
            .replace("=", "")
            .take(30)
        if (cleanDesc.length >= 2 && cleanKey.isNotBlank()) {
            synchronized(learnedCategoryMap) {
                learnedCategoryMap.remove(cleanDesc)
                learnedCategoryMap[cleanDesc] = cleanKey
                while (learnedCategoryMap.size > MAX_LEARNED_CATEGORIES) {
                    val oldestKey = learnedCategoryMap.keys.firstOrNull() ?: break
                    learnedCategoryMap.remove(oldestKey)
                }
            }
        }
    }

    fun getLearnedCategoryForDescription(description: String): String? {
        val cleanDesc = description.trim().lowercase(Locale.ROOT)
        if (cleanDesc.length < 2) return null
        synchronized(learnedCategoryMap) {
            learnedCategoryMap[cleanDesc]?.let { return it }
            return learnedCategoryMap.entries.firstOrNull { (k, _) ->
                cleanDesc.contains(k) || k.contains(cleanDesc)
            }?.value
        }
    }

    fun getAllLearnedCategories(): Map<String, String> = synchronized(learnedCategoryMap) {
        learnedCategoryMap.toMap()
    }

    fun exportLearnedCategoriesJson(): String {
        val obj = JSONObject()
        synchronized(learnedCategoryMap) {
            for ((k, v) in learnedCategoryMap) {
                obj.put(k, v)
            }
        }
        return obj.toString()
    }

    fun importLearnedCategoriesJson(rawJson: String, merge: Boolean = true) {
        if (rawJson.isBlank()) return
        runCatching {
            val obj = JSONObject(rawJson)
            synchronized(learnedCategoryMap) {
                if (!merge) learnedCategoryMap.clear()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val desc = keys.next().trim().lowercase(Locale.ROOT)
                    val env = obj.optString(desc, "").trim().lowercase(Locale.ROOT)
                    if (desc.length >= 2 && env.isNotBlank()) {
                        learnedCategoryMap.remove(desc)
                        learnedCategoryMap[desc] = env
                    }
                }
            }
        }
    }

    /**
     * Packs learned mappings (most recent first) into two <=240-char strings compatible with
     * firestore.rules (`subtitle` <= 250, `dateOrCycle` <= 250).
     */
    fun exportLearnedCategoriesEncoded(maxChunkLen: Int = 240): Pair<String, String> {
        val entries = synchronized(learnedCategoryMap) {
            learnedCategoryMap.entries.toList().reversed()
        }
        val firstChunk = StringBuilder()
        val secondChunk = StringBuilder()
        for ((k, v) in entries) {
            val safeKey = k.replace("|", " ").replace("=", " ").trim()
            val safeVal = v.replace("|", "").replace("=", "").trim()
            if (safeKey.length < 2 || safeVal.isBlank()) continue
            val token = "$safeKey=$safeVal"
            val sep1 = if (firstChunk.isNotEmpty()) "|" else ""
            if (firstChunk.length + sep1.length + token.length <= maxChunkLen) {
                firstChunk.append(sep1).append(token)
            } else {
                val sep2 = if (secondChunk.isNotEmpty()) "|" else ""
                if (secondChunk.length + sep2.length + token.length <= maxChunkLen) {
                    secondChunk.append(sep2).append(token)
                }
            }
        }
        return firstChunk.toString() to secondChunk.toString()
    }

    fun importLearnedCategoriesEncoded(primary: String, secondary: String = "", merge: Boolean = true) {
        val combined = listOf(primary.trim(), secondary.trim()).filter { it.isNotBlank() }
        if (combined.isEmpty()) return
        synchronized(learnedCategoryMap) {
            if (!merge) learnedCategoryMap.clear()
            for (chunk in combined) {
                if (chunk.startsWith("{")) {
                    importLearnedCategoriesJson(chunk, merge = true)
                    continue
                }
                val pairs = chunk.split("|").reversed()
                for (pair in pairs) {
                    val eqIdx = pair.indexOf('=')
                    if (eqIdx <= 0) continue
                    val desc = pair.substring(0, eqIdx).trim().lowercase(Locale.ROOT)
                    val env = pair.substring(eqIdx + 1).trim().lowercase(Locale.ROOT)
                    if (desc.length >= 2 && env.isNotBlank()) {
                        learnedCategoryMap.remove(desc)
                        learnedCategoryMap[desc] = env
                    }
                }
            }
        }
    }

    fun buildLearnedCategoriesFinancialItem(
        userId: String,
        existingCreatedAtEpochMs: Long = 0L
    ): FinancialItem {
        val (part1, part2) = exportLearnedCategoriesEncoded()
        return FinancialItem(
            id = "learned_categories",
            userId = userId,
            itemType = "custom_prefs",
            name = "learned_categories",
            subtitle = part1,
            amount = Money.ZERO,
            secondaryAmount = Money.ZERO,
            dayOrTurn = 0,
            extraInt = 0,
            dateOrCycle = part2,
            createdAtEpochMs = existingCreatedAtEpochMs
        )
    }

    fun importLearnedCategoriesFromFinancialItem(item: FinancialItem?) {
        if (item == null) return
        importLearnedCategoriesEncoded(item.subtitle, item.dateOrCycle, merge = true)
    }

    fun isDaretPotCollected(daret: FinancialItem): Boolean {
        return daret.dateOrCycle.contains(DARET_POT_COLLECTED_MARKER, ignoreCase = true)
    }

    fun daretPayoutDateOnly(daret: FinancialItem): String {
        return daret.dateOrCycle
            .split("|")
            .firstOrNull { it.isNotBlank() && !it.equals(DARET_POT_COLLECTED_MARKER, ignoreCase = true) }
            ?.trim()
            .orEmpty()
    }

    fun markDaretPotCollected(daret: FinancialItem): FinancialItem {
        val cleanDate = daretPayoutDateOnly(daret)
        val totalMembers = daret.resolvedDaretMembers()
        val markedDateOrCycle = if (cleanDate.isNotBlank()) {
            "$cleanDate|$DARET_POT_COLLECTED_MARKER"
        } else {
            DARET_POT_COLLECTED_MARKER
        }
        return daret.copy(
            extraInt = maxOf(daret.extraInt, daret.dayOrTurn).coerceAtMost(totalMembers),
            dateOrCycle = markedDateOrCycle.take(250)
        )
    }

    fun clearLearnedCategories() {
        learnedCategoryMap.clear()
    }

    /**
     * Smart defaults: guesses the category/envelope and account from learned mappings,
     * historical transactions, or Darija/Arabic/FR/EN keywords, or defaults to the last-used one.
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
            // 0. Check explicitly learned description -> category mappings
            val learnedKey = getLearnedCategoryForDescription(cleanDesc)
            if (learnedKey != null) {
                val envConfig = envelopes.find { it.key == learnedKey }
                val accountForEnv = sortedRecent.firstOrNull { it.envelopeKey == learnedKey }?.accountId
                    ?: lastUsedAccount
                return SmartDefaultGuess(
                    envelopeKey = envConfig?.key ?: learnedKey,
                    categoryName = envConfig?.localizedName(lang)
                        ?: DefaultCategoryResolver.localizedDefaultName(learnedKey, lang)
                        ?: learnedKey,
                    accountId = accountForEnv
                )
            }

            // 1. Check historical transactions with matching description
            val histMatch = sortedRecent.firstOrNull {
                val d = it.desc.trim().lowercase(Locale.ROOT)
                !it.isTransferType() && d.isNotBlank() && (d.contains(cleanDesc) || cleanDesc.contains(d))
            }
            if (histMatch != null) {
                val envConfig = envelopes.find { it.key == histMatch.envelopeKey }
                return SmartDefaultGuess(
                    envelopeKey = envConfig?.key ?: histMatch.envelopeKey.ifBlank { "cafe" },
                    categoryName = envConfig?.localizedName(lang) ?: histMatch.category.ifBlank { "Autre" },
                    accountId = histMatch.accountId.ifBlank { lastUsedAccount }
                )
            }

            // 2. Check Darija/Arabic/FR/EN keyword parser (including gheda, echa, dwa, khobz, etc.)
            val kwMatch = VoiceDarijaParser.inferKeywordCategory(description, envelopes, lang)
            if (kwMatch != null) {
                val (envKey, catName) = kwMatch
                val accountForEnv = sortedRecent.firstOrNull { it.envelopeKey == envKey }?.accountId
                    ?: lastUsedAccount
                return SmartDefaultGuess(
                    envelopeKey = envKey,
                    categoryName = catName,
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
        amount: Money,
        envelopeKey: String,
        category: String,
        allTransactions: List<TransactionItem>,
        nowIso: String = todayIso(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): TransactionItem? {
        if (!amount.isPositive) return null
        return allTransactions.firstOrNull { tx ->
            val sameType = tx.type == type
            val sameAmount = tx.amount == amount
            val sameCategory = (tx.envelopeKey.isNotBlank() && tx.envelopeKey == envelopeKey) ||
                tx.category.equals(category, ignoreCase = true)
            val createdMs = tx.createdAtEpochMs
            val isWithinMinutes = if (createdMs > 0L) {
                abs(nowEpochMs - createdMs) <= 15 * 60 * 1000L
            } else {
                tx.isoDate == nowIso || allTransactions.size <= 10
            }
            sameType && sameAmount && sameCategory && isWithinMinutes
        }
    }

    fun findRecentDuplicateTransaction(
        amount: Money,
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
        targetAmount: Money,
        savedAmount: Money,
        nowIso: String = todayIso()
    ): SeasonalCountdownInfo {
        val validTarget = if (isValidIsoDate(targetIso)) targetIso else "2027-03-01"
        val nowEpoch = parseEpochDays(nowIso)
        val targetEpoch = parseEpochDays(validTarget)
        val daysLeft = max(0, (targetEpoch - nowEpoch).toInt())
        val monthsLeft = max(1, kotlin.math.ceil(daysLeft / 30.0).toInt())
        val remAmount = (targetAmount - savedAmount).coerceAtLeast(Money.ZERO)
        val monthlySave = if (remAmount.isPositive) remAmount.divideBy(monthsLeft.toLong()) else Money.ZERO
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
        monthlySaved: Money,
        goals: List<FinancialItem>,
        seasonals: List<FinancialItem>,
        lang: String,
        currency: String
    ): String {
        val targetItem = (goals + seasonals).firstOrNull { (it.amount - it.secondaryAmount).isPositive }
        val remGoal = if (targetItem != null) {
            (targetItem.amount - targetItem.secondaryAmount).coerceAtLeast(Money.ZERO)
        } else {
            Money.fromDh(5000)
        }
        val goalName = targetItem?.name ?: when (lang) {
            "darija" -> "صندوق الطوارئ"
            "ar" -> "هدف الادخار"
            "en" -> "Emergency Fund"
            else -> "Objectif d'épargne"
        }
        val baselineMonthlySave = maxOf(Money.fromDh(200), remGoal.divideBy(10L))
        val baseMonths = kotlin.math.ceil(remGoal.centimes.toDouble() / baselineMonthlySave.centimes.toDouble()).toInt().coerceAtLeast(2)
        val improvedMonthly = baselineMonthlySave + monthlySaved.coerceAtLeast(Money.fromDh(1))
        val newMonths = kotlin.math.ceil(remGoal.centimes.toDouble() / improvedMonthly.centimes.toDouble()).toInt().coerceAtLeast(1)
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
        envelopeMonthlySpend: Money,
        cutPercent: Double,
        currentMonthlySavingCapacity: Money,
        goals: List<FinancialItem>,
        currencySymbol: String,
        language: String,
        hideAmounts: Boolean = false
    ): String {
        val rate = BigDecimal.valueOf(cutPercent).divide(BigDecimal.valueOf(100L), 10, RoundingMode.HALF_UP)
        val monthlySaved = envelopeMonthlySpend.multiplyRate(rate)
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
        amount: Money,
        fromCurrency: String,
        toCurrency: String,
        eurToMadRate: Double = DEFAULT_EUR_TO_MAD,
        usdToMadRate: Double = DEFAULT_USD_TO_MAD
    ): Money {
        if (!amount.isPositive) return Money.ZERO
        val normFrom = normalizeCurrencyCode(fromCurrency)
        val normTo = normalizeCurrencyCode(toCurrency)
        if (normFrom == normTo) return amount

        val safeEur = BigDecimal.valueOf(if (eurToMadRate > 0.1) eurToMadRate else DEFAULT_EUR_TO_MAD)
        val safeUsd = BigDecimal.valueOf(if (usdToMadRate > 0.1) usdToMadRate else DEFAULT_USD_TO_MAD)
        val mc = MathContext.DECIMAL128

        val amountBd = amount.toBigDecimalDh()
        val inMad = when (normFrom) {
            "EUR" -> amountBd.multiply(safeEur, mc)
            "USD" -> amountBd.multiply(safeUsd, mc)
            else -> amountBd
        }
        val converted = when (normTo) {
            "EUR" -> inMad.divide(safeEur, mc)
            "USD" -> inMad.divide(safeUsd, mc)
            else -> inMad
        }
        return Money.fromMajorUnits(converted, RoundingMode.HALF_UP)
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
        MoroccanBillTemplate("tpl_lydec", "Lydec / SRM (Eau & Élec)", "Eau & Électricité Casablanca", Money.fromDh(380), 15, "💧"),
        MoroccanBillTemplate("tpl_redal", "Redal (Eau & Élec)", "Eau & Électricité Rabat-Salé", Money.fromDh(340), 15, "💧"),
        MoroccanBillTemplate("tpl_amendis", "Amendis (Eau & Élec)", "Eau & Électricité Tanger/Tétouan", Money.fromDh(350), 15, "💧"),
        MoroccanBillTemplate("tpl_onee", "ONEE (Électricité & Eau)", "Office National Électricité & Eau", Money.fromDh(320), 18, "⚡"),
        MoroccanBillTemplate("tpl_iam", "Maroc Telecom (IAM Fibre/ADSL)", "Internet & Fixe IAM", Money.fromDh(400), 5, "🌐"),
        MoroccanBillTemplate("tpl_inwi", "Inwi (Forfait / Fibre)", "Mobile & Internet Inwi", Money.fromDh(249), 10, "📱"),
        MoroccanBillTemplate("tpl_orange", "Orange Maroc (Forfait / Fibre)", "Mobile & Fibre Orange", Money.fromDh(249), 10, "🍊"),
        MoroccanBillTemplate("tpl_rent", "Loyer / الكراء", "Loyer mensuel appartement", Money.fromDh(2500), 1, "🏠")
    )

    fun moroccanSeasonalTemplates(): List<MoroccanSeasonalTemplate> = listOf(
        MoroccanSeasonalTemplate("sea_ramadan", "Ramadan (قفة ومصاريف رمضان)", "Provisions & Table du Ftour", Money.fromDh(3000), "2027-02-08", "🌙"),
        MoroccanSeasonalTemplate("sea_aid_adha", "Aïd al-Adha (صندوق الحولي)", "Mouton & Fête de l'Aïd", Money.fromDh(4500), "2027-05-16", "🐏"),
        MoroccanSeasonalTemplate("sea_rentree", "Rentrée Scolaire (الدخول المدرسي)", "Fournitures, manuels & inscription", Money.fromDh(2500), "2027-09-01", "🎒"),
        MoroccanSeasonalTemplate("sea_vacances", "Vacances d'été (عطلة الصيف)", "Voyage & Détente en famille", Money.fromDh(5000), "2027-07-15", "🏖️")
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
            val totalMembers = daret.resolvedDaretMembers()
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
            val rem = (debt.amount - debt.secondaryAmount).coerceAtLeast(Money.ZERO)
            if (rem.isPositive) {
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

    fun daysInMonth(year: Int, month: Int): Int {
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

    fun epochDaysToIso(epochDays: Long): String {
        val cal = Calendar.getInstance(UTC_TZ).apply {
            clear()
            timeInMillis = epochDays * 86_400_000L
        }
        return formatIso(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
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

    private val MONTHS_SHORT_FR = arrayOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")
    private val MONTHS_SHORT_EN = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MONTHS_SHORT_AR = arrayOf("يناير", "فبراير", "مارس", "أبريل", "ماي", "يونيو", "يوليوز", "غشت", "شتنبر", "أكتوبر", "نونبر", "دجنبر")

    fun formatFriendlyDate(isoDate: String, lang: String = "fr", todayIso: String = todayIso()): String {
        val clean = isoDate.trim()
        if (clean == todayIso) {
            return when (lang) {
                "ar", "darija" -> "اليوم"
                "en" -> "Today"
                else -> "Aujourd'hui"
            }
        }
        val diff = parseEpochDays(todayIso) - parseEpochDays(clean)
        if (diff == 1L) {
            return when (lang) {
                "ar" -> "أمس"
                "darija" -> "البارح"
                "en" -> "Yesterday"
                else -> "Hier"
            }
        }
        return formatShortDayMonth(clean, lang)
    }

    fun formatShortDayMonth(isoDate: String, lang: String = "fr"): String {
        val parts = isoDate.trim().split("-")
        if (parts.size < 3) return isoDate
        val monthIdx = (parts[1].toIntOrNull() ?: 1).coerceIn(1, 12) - 1
        val dayNum = parts[2].toIntOrNull() ?: 1
        val monthName = when (lang) {
            "ar", "darija" -> MONTHS_SHORT_AR[monthIdx]
            "en" -> MONTHS_SHORT_EN[monthIdx]
            else -> MONTHS_SHORT_FR[monthIdx]
        }
        return "$dayNum $monthName"
    }

    fun formatCycleRangeFriendly(startIso: String, endIso: String, lang: String = "fr"): String {
        val s = formatShortDayMonth(startIso, lang).removeSuffix(".")
        val e = formatShortDayMonth(endIso, lang).removeSuffix(".")
        return "$s → $e"
    }

    fun formatDh(value: Money): String = value.formatDhRounded()

    fun formatAmountOrMask(value: Money, hideAmounts: Boolean): String =
        if (hideAmounts) "••••" else value.formatDhRounded()

    fun formatCurrencyOrMask(value: Money, currencySymbol: String, hideAmounts: Boolean): String =
        value.formatCurrencyOrMask(currencySymbol, hideAmounts, includeDecimals = false)

    /**
     * Parses a user-typed non-negative amount strictly via [MoneyParser] (zero Double).
     * Supports dots ("85.50"), commas ("85,50"), thousands separators, and currency suffixes.
     */
    fun parseFlexibleAmount(rawAmountText: String): Money {
        val centimes = MoneyParser.parseMoroccanAmountToCentimes(rawAmountText) ?: return Money.ZERO
        return if (centimes >= 0L) Money.fromCentimes(centimes) else Money.ZERO
    }

    /**
     * Computes 7 day-of-week weights (indexed by Calendar.SUNDAY..Calendar.SATURDAY = 1..7)
     * from the last 8 weeks (56 days) of expenses (excluding transfers and income).
     * Uses flat weights (all 1.0) if there are fewer than 14 days of expense data.
     */
    fun computeDayOfWeekWeights(
        expenseHistory: List<TransactionItem>,
        referenceIso: String = todayIso()
    ): DoubleArray {
        val flat = DoubleArray(8) { 1.0 }
        if (!isValidIsoDate(referenceIso)) return flat
        val refEpoch = parseEpochDays(referenceIso)

        val recentExpenses = expenseHistory.filter { tx ->
            if (tx.type != "expense" || !tx.amount.isPositive || !isValidIsoDate(tx.isoDate)) return@filter false
            val ageDays = (refEpoch - parseEpochDays(tx.isoDate)).toInt()
            ageDays in 0..55
        }

        val distinctActiveDays = recentExpenses.map { it.isoDate }.distinct().size
        if (distinctActiveDays < 14) {
            return flat
        }

        val minEpoch = recentExpenses.minOf { parseEpochDays(it.isoDate) }.coerceAtLeast(refEpoch - 55L)
        val spanDays = (refEpoch - minEpoch + 1L).toInt().coerceIn(14, 56)

        val spendByIso = recentExpenses
            .groupBy { it.isoDate }
            .mapValues { (_, list) -> list.sumOf { it.amount.centimes.toDouble() } }

        val sumByDow = DoubleArray(8)
        val countByDow = IntArray(8)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        for (offset in 0 until spanDays) {
            val dayEpoch = refEpoch - offset
            val iso = epochDaysToIso(dayEpoch)
            val parts = iso.split("-").mapNotNull { it.toIntOrNull() }
            if (parts.size == 3) {
                cal.clear()
                cal.set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
                val dow = cal.get(Calendar.DAY_OF_WEEK).coerceIn(1, 7)
                countByDow[dow]++
                sumByDow[dow] += spendByIso[iso] ?: 0.0
            }
        }

        val meanByDow = DoubleArray(8)
        var totalMeanSum = 0.0
        var validDows = 0
        for (dow in 1..7) {
            val count = countByDow[dow]
            if (count > 0) {
                val m = sumByDow[dow] / count.toDouble()
                meanByDow[dow] = m
                totalMeanSum += m
                validDows++
            }
        }

        if (validDows == 0 || totalMeanSum <= 0.0) return flat
        val overallDailyMean = totalMeanSum / validDows.toDouble()
        if (overallDailyMean <= 0.0) return flat

        val weights = DoubleArray(8) { 1.0 }
        for (dow in 1..7) {
            val rawRatio = meanByDow[dow] / overallDailyMean
            weights[dow] = rawRatio.coerceIn(0.35, 2.5)
        }
        return weights
    }

    private fun dayOfWeekForEpochDay(epochDay: Long): Int {
        val iso = epochDaysToIso(epochDay)
        val parts = iso.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size < 3) return Calendar.MONDAY
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
        }
        return cal.get(Calendar.DAY_OF_WEEK).coerceIn(1, 7)
    }

    /**
     * Live "Can I afford this?" purchase impact calculator (Prompt 2):
     * - Weights days of the week from the last 8 weeks of expenses (flat weights if < 14 days of data).
     * - Re-runs [computeOverspendProjection] with spentSoFar + amount to compute [runOutIso].
     * - Returns FITS_TODAY, LOWERS_ALLOWANCE, or OVER_POOL, plus [envelopeUsageAfter].
     */
    fun computePurchaseImpact(
        amount: Money,
        safePool: Money,
        daysRemaining: Int,
        expenseHistory: List<TransactionItem> = emptyList(),
        window: PayCycleWindow? = null,
        envelope: EnvelopeStatus? = null,
        todayIso: String = todayIso()
    ): PurchaseImpact {
        val safeAmount = amount.coerceAtLeast(Money.ZERO)
        val clampedPool = safePool.coerceAtLeast(Money.ZERO)
        val effectiveDays = max(1, daysRemaining)

        val refIso = when {
            isValidIsoDate(todayIso) && (window == null || isInWindow(todayIso, window)) -> todayIso
            window != null && isValidIsoDate(window.startIso) -> {
                val startEpoch = parseEpochDays(window.startIso)
                val endEpoch = parseEpochDays(window.endIso)
                val approxTodayEpoch = (endEpoch - max(0, daysRemaining - 1)).coerceIn(startEpoch, endEpoch)
                epochDaysToIso(approxTodayEpoch)
            }
            else -> todayIso()
        }
        val refEpoch = parseEpochDays(refIso)
        val dowWeights = computeDayOfWeekWeights(expenseHistory, refIso)

        val todayShare = if (daysRemaining <= 1) {
            1.0
        } else {
            val todayDow = dayOfWeekForEpochDay(refEpoch)
            val todayWeight = dowWeights[todayDow]
            var totalRemainingWeight = 0.0
            for (d in 0 until effectiveDays) {
                val dow = dayOfWeekForEpochDay(refEpoch + d)
                totalRemainingWeight += dowWeights[dow]
            }
            if (totalRemainingWeight > 0.0) {
                todayWeight / totalRemainingWeight
            } else {
                1.0 / effectiveDays.toDouble()
            }
        }

        val todayShareBd = BigDecimal.valueOf(todayShare)
        val currentDaily = clampedPool.multiplyRate(todayShareBd)
        val remainingPoolAfter = (clampedPool - safeAmount).coerceAtLeast(Money.ZERO)
        val newDaily = remainingPoolAfter.multiplyRate(todayShareBd)
        val delta = newDaily - currentDaily
        val remainingToday = (currentDaily - safeAmount).coerceAtLeast(Money.ZERO)
        val overPool = if (safeAmount > clampedPool) safeAmount - clampedPool else Money.ZERO

        val verdict = when {
            !clampedPool.isPositive && safeAmount.isPositive -> PurchaseVerdict.OVER_POOL
            safeAmount <= currentDaily -> PurchaseVerdict.FITS_TODAY
            safeAmount <= clampedPool -> PurchaseVerdict.LOWERS_ALLOWANCE
            else -> PurchaseVerdict.OVER_POOL
        }

        // Re-run computeOverspendProjection with spentSoFar + amount to get runOutIso
        val runOutIso: String? = if (window != null) {
            val cycleExpensesSoFar = expenseHistory.filter {
                it.type == "expense" && it.amount.isPositive && isInWindow(it.isoDate, window) && it.isoDate <= refIso
            }
            val spentSoFar = cycleExpensesSoFar.map { it.amount }.sumMoney()
            val totalAvailableBudget = spentSoFar + clampedPool
            val proj = computeOverspendProjection(
                window = window,
                totalAvailableBudget = totalAvailableBudget,
                spentSoFar = spentSoFar + safeAmount,
                scheduledBills = Money.ZERO,
                pastOrTodayCycleTransactions = cycleExpensesSoFar
            )
            val dailyPace = when {
                proj.medianDailyVariable.isPositive -> proj.medianDailyVariable
                window.daysElapsed > 0 && spentSoFar.isPositive -> spentSoFar.divideBy(window.daysElapsed.toLong())
                else -> Money.ZERO
            }
            when {
                safeAmount > clampedPool && safeAmount.isPositive -> refIso
                dailyPace.isPositive && proj.projectedTotal > totalAvailableBudget -> {
                    val daysLeftUntilExhausted = kotlin.math.floor(
                        remainingPoolAfter.centimes.toDouble() / dailyPace.centimes.toDouble()
                    ).toLong().coerceAtLeast(0L)
                    val runOutEpoch = (refEpoch + daysLeftUntilExhausted).coerceAtMost(parseEpochDays(window.endIso))
                    val candidateIso = epochDaysToIso(runOutEpoch)
                    if (candidateIso <= window.endIso) candidateIso else null
                }
                else -> null
            }
        } else {
            null
        }

        val envNewSpent = envelope?.let { it.spent + safeAmount }
        val envHasCap = envelope != null && envelope.config.hasCap && envelope.totalAvailable.isPositive
        val envNewPct = if (envelope != null && envHasCap) {
            ((envNewSpent!!.centimes.toDouble() / envelope.totalAvailable.centimes.toDouble()) * 100.0).roundToInt().coerceAtLeast(0)
        } else if (envelope != null) {
            0
        } else {
            null
        }
        val envRemAfter = envelope?.let { it.totalAvailable - (envNewSpent ?: Money.ZERO) }

        return PurchaseImpact(
            amount = safeAmount,
            safePool = clampedPool,
            daysRemaining = max(0, daysRemaining),
            currentDailyAllowance = currentDaily,
            newSafeToday = newDaily,
            delta = delta,
            remainingTodayAllowance = remainingToday,
            overSafePoolAmount = overPool,
            verdict = verdict,
            runOutIso = runOutIso,
            envelopeUsageAfter = envNewPct,
            envelopeKey = envelope?.config?.key,
            envelopeNewSpent = envNewSpent,
            envelopeRemainingAfter = envRemAfter,
            envelopeHasCap = envHasCap
        )
    }

    fun computePurchaseImpact(
        rawAmountText: String,
        safePool: Money,
        daysRemaining: Int,
        expenseHistory: List<TransactionItem> = emptyList(),
        window: PayCycleWindow? = null,
        envelope: EnvelopeStatus? = null,
        todayIso: String = todayIso()
    ): PurchaseImpact {
        return computePurchaseImpact(
            amount = parseFlexibleAmount(rawAmountText),
            safePool = safePool,
            daysRemaining = daysRemaining,
            expenseHistory = expenseHistory,
            window = window,
            envelope = envelope,
            todayIso = todayIso
        )
    }

    private fun formatRunOutDayLabel(runOutIso: String, strings: AppStrings, hideAmounts: Boolean): String {
        if (hideAmounts) return "••"
        val day = runOutIso.split("-").getOrNull(2)?.toIntOrNull() ?: return runOutIso
        val isEn = strings === Translations.EN
        if (!isEn) return day.toString()
        val mod100 = day % 100
        val suffix = if (mod100 in 11..13) {
            "th"
        } else {
            when (day % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        }
        return "$day$suffix"
    }

    fun formatPurchaseImpactLine(
        impact: PurchaseImpact,
        currencySymbol: String,
        hideAmounts: Boolean,
        strings: AppStrings,
        envelopeName: String? = null
    ): String {
        val cur = currencySymbol.ifBlank { "DH" }
        val oldSafeStr = formatAmountOrMask(impact.currentDailyAllowance, hideAmounts)
        val newSafeStr = formatAmountOrMask(impact.newSafeToday, hideAmounts)
        val overStr = formatAmountOrMask(impact.overSafePoolAmount, hideAmounts)

        val baseText = when (impact.verdict) {
            PurchaseVerdict.FITS_TODAY -> strings.impactGreenFitsTodayFmt
                .replace("{oldSafe}", oldSafeStr)
                .replace("{newSafe}", newSafeStr)
                .replace("{cur}", cur)
            PurchaseVerdict.LOWERS_ALLOWANCE -> strings.impactAmberLowersDailyFmt
                .replace("{oldSafe}", oldSafeStr)
                .replace("{newSafe}", newSafeStr)
                .replace("{cur}", cur)
            PurchaseVerdict.OVER_POOL -> strings.impactRedOverSafePoolFmt
                .replace("{over}", overStr)
                .replace("{oldSafe}", oldSafeStr)
                .replace("{newSafe}", newSafeStr)
                .replace("{cur}", cur)
        }

        val runOutSuffix = if (!impact.runOutIso.isNullOrBlank()) {
            val dayLabel = formatRunOutDayLabel(impact.runOutIso, strings, hideAmounts)
            strings.impactRunOutSuffixFmt.replace("{day}", dayLabel)
        } else {
            ""
        }

        val envSuffix = if (impact.envelopeHasCap && impact.envelopeUsageAfter != null && !envelopeName.isNullOrBlank()) {
            val pctStr = if (hideAmounts) "••" else impact.envelopeUsageAfter.toString()
            strings.impactEnvelopeUsageSuffixFmt
                .replace("{env}", envelopeName)
                .replace("{pct}", pctStr)
        } else {
            ""
        }

        return baseText + runOutSuffix + envSuffix
    }
}
