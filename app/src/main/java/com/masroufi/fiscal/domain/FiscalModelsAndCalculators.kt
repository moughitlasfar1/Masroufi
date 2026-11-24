package com.masroufi.fiscal.domain

import android.os.Build
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.round

enum class AnnualBillPreset(val key: String) {
    VIGNETTE("vignette"),
    CAR_INSURANCE("car_insurance"),
    CUSTOM("custom"),
    ZAKAT("zakat");

    companion object {
        fun fromKey(raw: String?): AnnualBillPreset =
            entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) } ?: CUSTOM
    }
}

data class AnnualBillItem(
    val id: String,
    val preset: AnnualBillPreset,
    val title: String,
    val annualAmountMad: Double,
    val dueMonth: Int, // 1..12
    val dueYear: Int,
    val cvBand: FiscalRates.HorsepowerBand? = null,
    val fuelType: FiscalRates.FuelType? = null,
    val isManualVignetteAmount: Boolean = false,
    val linkedGoalId: String = "",
    val linkedBillId: String = "",
    val isPaid: Boolean = false,
    val paidIsoDate: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class AnnualBillReserveBreakdown(
    val annualAmountMad: Double,
    val savedAmountMad: Double,
    val remainingToSaveMad: Double,
    val monthsRemaining: Int,
    val monthlyReserveMad: Double,
    val progressFraction: Float,
    val dueYear: Int,
    val dueMonth: Int,
    val dueDateIso: String,
    val isDueThisMonth: Boolean,
    val isDueDatePassed: Boolean,
    val rolledToNextYear: Boolean
)

/**
 * Pure Kotlin calculator for turning annual Moroccan expenses (Vignette, Car Insurance,
 * school fees, Eid, Zakat) into monthly reserves.
 */
object AnnualBillCalculator {

    fun roundMoney(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return round(value * 100.0) / 100.0
    }

    /**
     * Resolves the upcoming target year for a newly created bill with [dueMonth] (`1..12`).
     * - If `dueMonth >= currentMonth`: targets [currentYear] (including due this month).
     * - If `dueMonth < currentMonth`: target month already passed this year -> rolls to `currentYear + 1`.
     */
    fun resolveTargetYearForNewBill(
        dueMonth: Int,
        currentYear: Int,
        currentMonth: Int
    ): Pair<Int, Boolean> {
        val safeDueMonth = dueMonth.coerceIn(1, 12)
        val safeCurrentMonth = currentMonth.coerceIn(1, 12)
        return if (safeDueMonth < safeCurrentMonth) {
            (currentYear + 1) to true
        } else {
            currentYear to false
        }
    }

    /**
     * Computes monthly reserve breakdown for an annual bill.
     *
     * Edge cases:
     * - **Due month is this month** (`dueYear == currentYear && dueMonth == currentMonth`):
     *   `monthsRemaining = 1`, `isDueThisMonth = true`, `monthlyReserveMad = remainingToSaveMad`.
     * - **Due date passed** (`dueYear < currentYear` or `dueYear == currentYear && dueMonth < currentMonth`):
     *   - When [rollForwardIfPassed] is `true` (used when planning a recurring new bill), targets `currentYear + 1`
     *     and sets `rolledToNextYear = true`.
     *   - When [rollForwardIfPassed] is `false` (used for an existing unpaid bill whose due month passed),
     *     sets `isDueDatePassed = true`, `monthsRemaining = 1`, and `monthlyReserveMad = remainingToSaveMad`.
     */
    fun computeBreakdown(
        annualAmountMad: Double,
        savedAmountMad: Double,
        dueMonth: Int,
        dueYear: Int,
        currentYear: Int,
        currentMonth: Int,
        rollForwardIfPassed: Boolean = false
    ): AnnualBillReserveBreakdown {
        val safeAnnual = roundMoney(annualAmountMad.coerceAtLeast(0.0))
        val safeSaved = roundMoney(savedAmountMad.coerceAtLeast(0.0))
        val remaining = roundMoney(max(0.0, safeAnnual - safeSaved))
        val safeDueMonth = dueMonth.coerceIn(1, 12)
        val safeCurrentMonth = currentMonth.coerceIn(1, 12)

        val rawMonthsDelta = (dueYear - currentYear) * 12 + (safeDueMonth - safeCurrentMonth)
        val effectiveDueYear: Int
        val monthsRemaining: Int
        val isDueThisMonth: Boolean
        val isDueDatePassed: Boolean
        val rolledToNextYear: Boolean

        when {
            rawMonthsDelta < 0 && rollForwardIfPassed -> {
                val (nextYear, _) = resolveTargetYearForNewBill(safeDueMonth, currentYear, safeCurrentMonth)
                effectiveDueYear = nextYear
                monthsRemaining = ((nextYear - currentYear) * 12 + (safeDueMonth - safeCurrentMonth)).coerceAtLeast(1)
                isDueThisMonth = false
                isDueDatePassed = false
                rolledToNextYear = true
            }
            rawMonthsDelta < 0 -> {
                effectiveDueYear = dueYear
                monthsRemaining = 1
                isDueThisMonth = false
                isDueDatePassed = true
                rolledToNextYear = false
            }
            rawMonthsDelta == 0 -> {
                effectiveDueYear = dueYear
                monthsRemaining = 1
                isDueThisMonth = true
                isDueDatePassed = false
                rolledToNextYear = false
            }
            else -> {
                effectiveDueYear = dueYear
                monthsRemaining = rawMonthsDelta
                isDueThisMonth = false
                isDueDatePassed = false
                rolledToNextYear = false
            }
        }

        val monthlyReserve = if (remaining <= 0.0) {
            0.0
        } else {
            roundMoney(remaining / monthsRemaining.toDouble())
        }
        val progress = if (safeAnnual <= 0.0) {
            0f
        } else {
            (safeSaved / safeAnnual).toFloat().coerceIn(0f, 1f)
        }
        val dueDateIso = String.format(Locale.US, "%04d-%02d-01", effectiveDueYear, safeDueMonth)

        return AnnualBillReserveBreakdown(
            annualAmountMad = safeAnnual,
            savedAmountMad = safeSaved,
            remainingToSaveMad = remaining,
            monthsRemaining = monthsRemaining,
            monthlyReserveMad = monthlyReserve,
            progressFraction = progress,
            dueYear = effectiveDueYear,
            dueMonth = safeDueMonth,
            dueDateIso = dueDateIso,
            isDueThisMonth = isDueThisMonth,
            isDueDatePassed = isDueDatePassed,
            rolledToNextYear = rolledToNextYear
        )
    }

    fun currentYearAndMonth(): Pair<Int, Int> {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
    }
}

// -----------------------------------------------------------------------------
// Zakat & Hawl Models and Pure Calculator (Step 5)
// -----------------------------------------------------------------------------

enum class NisabStandard(val key: String) {
    GOLD_85G("gold_85g"),
    SILVER_595G("silver_595g");

    companion object {
        fun fromKey(raw: String?): NisabStandard =
            entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) } ?: GOLD_85G
    }
}

enum class HawlStatus {
    BELOW_NISAB,
    HAWL_RUNNING,
    DUE_NOW
}

data class ZakatConfig(
    val nisabStandard: NisabStandard = NisabStandard.GOLD_85G,
    val goldPriceMadPerGram: Double = FiscalRates.DEFAULT_GOLD_PRICE_MAD_PER_GRAM,
    val silverPriceMadPerGram: Double = FiscalRates.DEFAULT_SILVER_PRICE_MAD_PER_GRAM,
    val pricesAsOfIso: String = FiscalRates.DEFAULT_METAL_PRICES_AS_OF_ISO,
    val hawlStartIso: String = "", // YYYY-MM-DD when wealth first passed Nisab
    val linkedAnnualBillId: String = ""
)

data class ZakatCalculationResult(
    val accountsTotalMad: Double,
    val savingsTotalMad: Double,
    val shortTermDebtsOwedMad: Double,
    val eligibleWealthMad: Double,
    val nisabThresholdMad: Double,
    val zakatDueMad: Double,
    val status: HawlStatus,
    val hawlStartIso: String,
    val hawlDueDateIso: String,
    val hawlHijriStartLabel: String,
    val hawlHijriDueLabel: String,
    val daysRemaining: Int,
    val hawlProgressFraction: Float,
    val monthlyReserveSuggestionMad: Double
)

/**
 * Pure Kotlin Zakat & Hawl calculator using `java.time.chrono.HijrahDate` on API 26+ / JVM
 * (with a safe 354-day lunar fallback on API 24–25 so minSdk 24 stays 100% crash-free).
 */
object ZakatCalculator {

    private val ISO_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun isValidIsoDate(raw: String?): Boolean {
        if (raw.isNullOrBlank() || !ISO_REGEX.matches(raw.trim())) return false
        val parts = raw.trim().split("-")
        val y = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        val d = parts[2].toIntOrNull() ?: return false
        return y in 1970..2150 && m in 1..12 && d in 1..31
    }

    fun computeNisabThresholdMad(config: ZakatConfig): Double {
        val goldPrice = config.goldPriceMadPerGram.takeIf { it.isFinite() && it > 0.0 }
            ?: FiscalRates.DEFAULT_GOLD_PRICE_MAD_PER_GRAM
        val silverPrice = config.silverPriceMadPerGram.takeIf { it.isFinite() && it > 0.0 }
            ?: FiscalRates.DEFAULT_SILVER_PRICE_MAD_PER_GRAM
        return AnnualBillCalculator.roundMoney(
            when (config.nisabStandard) {
                NisabStandard.GOLD_85G -> FiscalRates.NISAB_GOLD_GRAMS * goldPrice
                NisabStandard.SILVER_595G -> FiscalRates.NISAB_SILVER_GRAMS * silverPrice
            }
        )
    }

    /**
     * Computes eligible wealth, Nisab comparison, 2.5% Zakat estimate, and Hijri Hawl countdown.
     *
     * Formula:
     * `eligibleWealth = max(0, accountsTotal + savingsTotal - shortTermDebtsOwed)`
     * `zakatDue = if (eligibleWealth >= nisabThreshold) eligibleWealth * 0.025 else 0`
     */
    fun calculate(
        accountsTotalMad: Double,
        savingsTotalMad: Double,
        shortTermDebtsOwedMad: Double,
        config: ZakatConfig,
        todayIso: String,
        alreadyReservedMad: Double = 0.0
    ): ZakatCalculationResult {
        val safeAccounts = AnnualBillCalculator.roundMoney(accountsTotalMad.coerceAtLeast(0.0))
        val safeSavings = AnnualBillCalculator.roundMoney(savingsTotalMad.coerceAtLeast(0.0))
        val safeDebtsOwed = AnnualBillCalculator.roundMoney(shortTermDebtsOwedMad.coerceAtLeast(0.0))
        val eligibleWealth = AnnualBillCalculator.roundMoney(
            max(0.0, safeAccounts + safeSavings - safeDebtsOwed)
        )
        val nisabThreshold = computeNisabThresholdMad(config)
        val meetsNisab = eligibleWealth >= nisabThreshold && nisabThreshold > 0.0
        val zakatDue = if (meetsNisab) {
            AnnualBillCalculator.roundMoney(eligibleWealth * FiscalRates.ZAKAT_RATE)
        } else {
            0.0
        }

        val normalizedToday = if (isValidIsoDate(todayIso)) todayIso.trim() else fallbackTodayIso()
        val effectiveStartIso = if (isValidIsoDate(config.hawlStartIso)) {
            config.hawlStartIso.trim()
        } else {
            normalizedToday
        }

        val hawlDates = computeHijriHawlDates(
            startIso = effectiveStartIso,
            todayIso = normalizedToday
        )

        val status = when {
            !meetsNisab -> HawlStatus.BELOW_NISAB
            hawlDates.daysRemaining <= 0 -> HawlStatus.DUE_NOW
            else -> HawlStatus.HAWL_RUNNING
        }

        val dueParts = hawlDates.dueIso.split("-")
        val todayParts = normalizedToday.split("-")
        val dueYear = dueParts.getOrNull(0)?.toIntOrNull() ?: 2027
        val dueMonth = dueParts.getOrNull(1)?.toIntOrNull() ?: 1
        val curYear = todayParts.getOrNull(0)?.toIntOrNull() ?: 2026
        val curMonth = todayParts.getOrNull(1)?.toIntOrNull() ?: 1

        val monthlyReserve = if (zakatDue > 0.0) {
            AnnualBillCalculator.computeBreakdown(
                annualAmountMad = zakatDue,
                savedAmountMad = alreadyReservedMad,
                dueMonth = dueMonth,
                dueYear = dueYear,
                currentYear = curYear,
                currentMonth = curMonth,
                rollForwardIfPassed = false
            ).monthlyReserveMad
        } else {
            0.0
        }

        return ZakatCalculationResult(
            accountsTotalMad = safeAccounts,
            savingsTotalMad = safeSavings,
            shortTermDebtsOwedMad = safeDebtsOwed,
            eligibleWealthMad = eligibleWealth,
            nisabThresholdMad = nisabThreshold,
            zakatDueMad = zakatDue,
            status = status,
            hawlStartIso = effectiveStartIso,
            hawlDueDateIso = hawlDates.dueIso,
            hawlHijriStartLabel = hawlDates.hijriStartLabel,
            hawlHijriDueLabel = hawlDates.hijriDueLabel,
            daysRemaining = hawlDates.daysRemaining.coerceAtLeast(0),
            hawlProgressFraction = hawlDates.progressFraction,
            monthlyReserveSuggestionMad = monthlyReserve
        )
    }

    internal data class HijriHawlDates(
        val dueIso: String,
        val hijriStartLabel: String,
        val hijriDueLabel: String,
        val daysRemaining: Int,
        val progressFraction: Float
    )

    internal fun computeHijriHawlDates(
        startIso: String,
        todayIso: String
    ): HijriHawlDates {
        // Use java.time.chrono.HijrahDate on API 26+ and all JVM unit test environments
        val canUseJavaTime = runCatching { Build.VERSION.SDK_INT == 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O }
            .getOrDefault(true)
        if (canUseJavaTime) {
            val result = runCatching {
                val startLocal = LocalDate.parse(startIso, DateTimeFormatter.ISO_LOCAL_DATE)
                val todayLocal = LocalDate.parse(todayIso, DateTimeFormatter.ISO_LOCAL_DATE)
                val hijriStart = HijrahDate.from(startLocal)
                val hijriDue = hijriStart.plus(1, ChronoUnit.YEARS)
                val dueLocal = LocalDate.from(hijriDue)

                val totalDays = ChronoUnit.DAYS.between(startLocal, dueLocal).coerceAtLeast(1L)
                val elapsedDays = ChronoUnit.DAYS.between(startLocal, todayLocal)
                val remainingDays = ChronoUnit.DAYS.between(todayLocal, dueLocal).toInt()
                val progress = (elapsedDays.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)

                HijriHawlDates(
                    dueIso = dueLocal.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    hijriStartLabel = formatHijriDate(hijriStart),
                    hijriDueLabel = formatHijriDate(hijriDue),
                    daysRemaining = remainingDays,
                    progressFraction = progress
                )
            }.getOrNull()
            if (result != null) return result
        }

        // Safe API 24–25 fallback using 354-day lunar Hijri year
        val startCal = parseCalendar(startIso)
        val todayCal = parseCalendar(todayIso)
        val dueCal = (startCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 354) }
        val remainingDays = ((dueCal.timeInMillis - todayCal.timeInMillis) / 86_400_000L).toInt()
        val elapsedDays = ((todayCal.timeInMillis - startCal.timeInMillis) / 86_400_000L).toFloat()
        val dueIso = String.format(
            Locale.US,
            "%04d-%02d-%02d",
            dueCal.get(Calendar.YEAR),
            dueCal.get(Calendar.MONTH) + 1,
            dueCal.get(Calendar.DAY_OF_MONTH)
        )
        return HijriHawlDates(
            dueIso = dueIso,
            hijriStartLabel = startIso,
            hijriDueLabel = dueIso,
            daysRemaining = remainingDays,
            progressFraction = (elapsedDays / 354f).coerceIn(0f, 1f)
        )
    }

    private fun formatHijriDate(hijrah: HijrahDate): String {
        val d = hijrah.get(ChronoField.DAY_OF_MONTH)
        val m = hijrah.get(ChronoField.MONTH_OF_YEAR)
        val y = hijrah.get(ChronoField.YEAR)
        val monthName = HIJRI_MONTHS.getOrElse((m - 1).coerceIn(0, 11)) { m.toString() }
        return "$d $monthName $y H"
    }

    private fun parseCalendar(iso: String): Calendar {
        val parts = iso.split("-")
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, parts.getOrNull(0)?.toIntOrNull() ?: 2026)
            set(Calendar.MONTH, ((parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, (parts.getOrNull(2)?.toIntOrNull() ?: 1).coerceIn(1, 31))
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun fallbackTodayIso(): String {
        val cal = Calendar.getInstance()
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private val HIJRI_MONTHS = listOf(
        "Muharram", "Safar", "Rabi' I", "Rabi' II",
        "Jumada I", "Jumada II", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
    )
}
