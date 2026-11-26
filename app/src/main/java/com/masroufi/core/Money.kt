package com.masroufi.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Exact, integer-backed monetary value in Moroccan centimes (`1 DH = 100 centimes`,
 * `1 Moroccan Ryal = 5 centimes` exact).
 *
 * Design invariants:
 * - Never stores or parses money through IEEE-754 binary `Double`/`Float`.
 * - Every arithmetic operator uses exact `Long` math (`Math.addExact`, `Math.subtractExact`,
 *   `Math.multiplyExact`, `Math.negateExact`) and throws [ArithmeticException] on overflow.
 * - Fractional rate/division operations always require an explicit [RoundingMode]
 *   (defaulting to [RoundingMode.HALF_UP]).
 */
@JvmInline
value class Money(val centimes: Long) : Comparable<Money> {

    val isZero: Boolean get() = centimes == 0L
    val isPositive: Boolean get() = centimes > 0L
    val isNegative: Boolean get() = centimes < 0L

    override fun compareTo(other: Money): Int = centimes.compareTo(other.centimes)

    /** Exact addition; throws [ArithmeticException] on `Long` overflow. */
    operator fun plus(other: Money): Money = Money(Math.addExact(centimes, other.centimes))

    /** Exact subtraction; throws [ArithmeticException] on `Long` overflow. */
    operator fun minus(other: Money): Money = Money(Math.subtractExact(centimes, other.centimes))

    /** Exact negation; throws [ArithmeticException] if `centimes == Long.MIN_VALUE`. */
    operator fun unaryMinus(): Money = Money(Math.negateExact(centimes))

    /** Exact absolute value; throws [ArithmeticException] if `centimes == Long.MIN_VALUE`. */
    fun abs(): Money = if (centimes < 0L) Money(Math.negateExact(centimes)) else this

    /** Exact integer scaling; throws [ArithmeticException] on `Long` overflow. */
    operator fun times(multiplier: Long): Money = Money(Math.multiplyExact(centimes, multiplier))

    /**
     * Multiplies this monetary amount by a decimal [rate] (e.g., `0.025` for 2.5% Zakat or
     * `0.10` for 10% TVA) using exact [BigDecimal] arithmetic and explicit rounding.
     *
     * Throws [ArithmeticException] if the rounded centime result overflows `Long`.
     */
    fun multiplyRate(
        rate: BigDecimal,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): Money {
        val scaled = BigDecimal.valueOf(centimes)
            .multiply(rate)
            .setScale(0, roundingMode)
            .longValueExact()
        return Money(scaled)
    }

    /**
     * Divides this monetary amount by a non-zero integer [divisor] (e.g., days or months remaining)
     * with explicit [RoundingMode].
     *
     * Throws [ArithmeticException] if [divisor] is `0L` or the result overflows `Long`.
     */
    fun divideBy(
        divisor: Long,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): Money {
        if (divisor == 0L) throw ArithmeticException("Division by zero in Money.divideBy")
        val scaled = BigDecimal.valueOf(centimes)
            .divide(BigDecimal.valueOf(divisor), 0, roundingMode)
            .longValueExact()
        return Money(scaled)
    }

    fun coerceAtLeast(minimumValue: Money): Money =
        if (centimes < minimumValue.centimes) minimumValue else this

    fun coerceAtMost(maximumValue: Money): Money =
        if (centimes > maximumValue.centimes) maximumValue else this

    fun coerceIn(minimumValue: Money, maximumValue: Money): Money {
        require(minimumValue.centimes <= maximumValue.centimes) {
            "Cannot coerce value to an empty range: maximum $maximumValue is less than minimum $minimumValue."
        }
        return when {
            centimes < minimumValue.centimes -> minimumValue
            centimes > maximumValue.centimes -> maximumValue
            else -> this
        }
    }

    /** Exact major-unit (DH) representation with scale 2 (e.g., `1999L -> BigDecimal("19.99")`). */
    fun toBigDecimalDh(): BigDecimal = BigDecimal.valueOf(centimes, 2)

    /**
     * Canonical ASCII decimal representation with exactly 2 fractional digits and a `.` separator
     * (e.g. `"123.45"`, `"100.00"`, `"-45.99"`, `"0.00"`).
     *
     * Byte-for-byte compatible with legacy `String.format(Locale.US, "%.2f", dh)` used by
     * `ImporterConstants.buildDeterministicId` and `StatementPipeline.duplicateSignature`.
     */
    fun toCanonicalDecimalString(): String = toBigDecimalDh().toPlainString()

    /**
     * Exact two-decimal Moroccan/French display format (e.g. `"1 250,50"` or `"1 250,50 DH"`,
     * `"-45,99 DH"`). Deterministic across all JVM/Android locales.
     */
    fun formatDhExact(
        currencySymbol: String = "",
        useGrouping: Boolean = true
    ): String {
        val sign = if (centimes < 0L) "-" else ""
        val absBig = BigDecimal.valueOf(centimes).abs()
        val whole = absBig.divideToIntegralValue(HUNDRED_BD).toPlainString()
        val frac = absBig.remainder(HUNDRED_BD).toInt()
        val groupedWhole = if (useGrouping) groupThousandsWithSpace(whole) else whole
        val fracStr = if (frac < 10) "0$frac" else frac.toString()
        val base = "$sign$groupedWhole,$fracStr"
        val cur = currencySymbol.trim()
        return if (cur.isEmpty()) base else "$base $cur"
    }

    /**
     * Rounded whole-Dirham Moroccan/French display format matching `PayCycleEngine.formatDh`
     * (e.g. `125050L -> "1 251"`, `125049L -> "1 250"`, `-4550L -> "-46"`).
     * Rounds via [RoundingMode.HALF_UP] and normalizes `-0` to `"0"`.
     */
    fun formatDhRounded(
        currencySymbol: String = "",
        useGrouping: Boolean = true
    ): String {
        val roundedMajor = toBigDecimalDh().setScale(0, RoundingMode.HALF_UP)
        val normalized = if (roundedMajor.signum() == 0) BigDecimal.ZERO else roundedMajor
        val sign = if (normalized.signum() < 0) "-" else ""
        val absWhole = normalized.abs().toPlainString()
        val grouped = if (useGrouping) groupThousandsWithSpace(absWhole) else absWhole
        val base = "$sign$grouped"
        val cur = currencySymbol.trim()
        return if (cur.isEmpty()) base else "$base $cur"
    }

    /**
     * Privacy-aware currency formatter supporting both rounded integer and exact two-decimal display.
     */
    fun formatCurrencyOrMask(
        currencySymbol: String,
        hideAmounts: Boolean,
        includeDecimals: Boolean = false
    ): String {
        val cur = currencySymbol.trim()
        if (hideAmounts) {
            return if (cur.isEmpty()) "••••" else "•••• $cur"
        }
        return if (includeDecimals) {
            formatDhExact(cur)
        } else {
            formatDhRounded(cur)
        }
    }

    companion object {
        val ZERO: Money = Money(0L)
        val ONE_CENTIME: Money = Money(1L)

        /** Default storable bounds (±100,000,000.00 DH = ±10_000_000_000L centimes). */
        val MAX_STORABLE: Money = Money(MoneyParser.DEFAULT_MAX_ABS_CENTIMES)
        val MIN_STORABLE: Money = Money(-MoneyParser.DEFAULT_MAX_ABS_CENTIMES)

        /** Exact centimes per 1 Dirham (MAD). */
        const val CENTIMES_PER_DH: Long = 100L

        /** Exact centimes per 1 Moroccan Ryal (`20 Ryal = 1 DH = 100 centimes`). */
        const val CENTIMES_PER_RYAL: Long = 5L

        private val HUNDRED_BD: BigDecimal = BigDecimal.valueOf(CENTIMES_PER_DH)

        fun fromCentimes(centimes: Long): Money = Money(centimes)

        /** Exact whole-Dirham factory (`dh * 100L`); throws [ArithmeticException] on overflow. */
        fun fromDh(dh: Long): Money = Money(Math.multiplyExact(dh, CENTIMES_PER_DH))

        /**
         * Converts exact major units (DH) to centimes via `movePointRight(2)` and explicit rounding.
         * Throws [ArithmeticException] if the resulting centimes overflow `Long`.
         */
        fun fromMajorUnits(
            amountDh: BigDecimal,
            roundingMode: RoundingMode = RoundingMode.HALF_UP
        ): Money {
            val centimes = amountDh
                .movePointRight(2)
                .setScale(0, roundingMode)
                .longValueExact()
            return Money(centimes)
        }

        /**
         * Parses a user/bank/CSV amount string into [Money] strictly via [BigDecimal],
         * returning `null` for blank, malformed, or out-of-bounds strings.
         */
        fun fromDhString(
            raw: String,
            maxAbsCentimes: Long = MoneyParser.DEFAULT_MAX_ABS_CENTIMES
        ): Money? {
            val centimes = MoneyParser.parseMoroccanAmountToCentimes(raw, maxAbsCentimes)
                ?: return null
            return Money(centimes)
        }

        /**
         * Exact Moroccan Ryal conversion: `1 Ryal = 5 centimes` (`200 Ryal = 1000 centimes = 10.00 DH`).
         * Throws [ArithmeticException] on `Long` overflow.
         */
        fun fromRyal(ryal: Long): Money = Money(Math.multiplyExact(ryal, CENTIMES_PER_RYAL))

        /**
         * Safe bridge for migrating legacy persisted `Double` DH values (Room v1, Firestore, v2 JSON)
         * via `BigDecimal.valueOf(Double)` — NEVER multiplies `Double * 100.0`.
         *
         * Throws [IllegalArgumentException] for `NaN`/`Infinity` and [ArithmeticException] on overflow.
         */
        fun fromLegacyDoubleDh(
            dh: Double,
            roundingMode: RoundingMode = RoundingMode.HALF_UP
        ): Money {
            require(dh.isFinite()) { "Legacy DH double must be finite, got $dh" }
            return fromMajorUnits(BigDecimal.valueOf(dh), roundingMode)
        }

        private fun groupThousandsWithSpace(digits: String): String {
            if (digits.length <= 3) return digits
            val sb = StringBuilder(digits.length + digits.length / 3)
            val firstGroupLen = digits.length % 3
            var idx = 0
            if (firstGroupLen > 0) {
                sb.append(digits, 0, firstGroupLen)
                idx = firstGroupLen
            }
            while (idx < digits.length) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(digits, idx, idx + 3)
                idx += 3
            }
            return sb.toString()
        }
    }
}

/** Exact sum of a sequence of [Money] values; throws [ArithmeticException] on overflow. */
fun Iterable<Money>.sumMoney(): Money {
    var acc = 0L
    for (item in this) {
        acc = Math.addExact(acc, item.centimes)
    }
    return Money(acc)
}

/** Exact sum of [Money] values produced by [selector]; throws [ArithmeticException] on overflow. */
inline fun <T> Iterable<T>.sumMoney(selector: (T) -> Money): Money {
    var acc = 0L
    for (item in this) {
        acc = Math.addExact(acc, selector(item).centimes)
    }
    return Money(acc)
}

/**
 * Pure-decimal parser for Moroccan, French, and English monetary strings.
 *
 * Strictly executes `String -> normalized ASCII decimal -> BigDecimal -> movePointRight(2)
 * -> setScale(0, HALF_UP) -> longValueExact()` and NEVER passes through `Double`.
 */
object MoneyParser {

    /** Default ceiling: ±100,000,000.00 DH (`±10_000_000_000L` centimes). */
    const val DEFAULT_MAX_ABS_CENTIMES: Long = 10_000_000_000L

    private val CURRENCY_TOKEN_REGEX = Regex(
        """(?i)(?<![\p{L}\d])(?:mad|dhs?|dirhams?|درهم|دراهم)(?![\p{L}\d])|د\.م\.?"""
    )
    private val VALID_CANONICAL_DECIMAL_REGEX = Regex("""^\d+(?:\.\d+)?$""")

    /**
     * Parses [raw] into exact signed centimes (`Long`), or returns `null` if [raw] is blank,
     * malformed, contains unexpected characters, or exceeds `[maxAbsCentimes]`.
     *
     * Supported formats include:
     * - Moroccan/French: `"1 250,50"`, `"1\u00A0250,50 DH"`, `"1.250,50 MAD"`, `"-45,99"`, `"120,5"`
     * - English/US: `"1,250.50"`, `"19.99"`, `"-45.99"`, `"1,250"`
     * - Accounting negatives: `"(1 250,50)"`, `"45,99-"`
     * - Arabic-Indic digits: `"١٢٥٠,٥٠ درهم"`
     */
    fun parseMoroccanAmountToCentimes(
        raw: String,
        maxAbsCentimes: Long = DEFAULT_MAX_ABS_CENTIMES
    ): Long? {
        require(maxAbsCentimes >= 0L) { "maxAbsCentimes must be non-negative" }
        if (raw.isBlank()) return null

        // 1. Normalize Arabic-Indic digits & Unicode spaces/apostrophes
        val normalizedChars = StringBuilder(raw.length)
        for (ch in raw.trim()) {
            when (ch) {
                in '\u0660'..'\u0669' -> normalizedChars.append(('0'.code + (ch.code - '\u0660'.code)).toChar())
                in '\u06F0'..'\u06F9' -> normalizedChars.append(('0'.code + (ch.code - '\u06F0'.code)).toChar())
                '\u00A0', '\u202F', '\u2009', '\u2007' -> normalizedChars.append(' ')
                '\'' -> Unit // drop Swiss/bank apostrophe thousands separator
                else -> normalizedChars.append(ch)
            }
        }

        var cleaned = CURRENCY_TOKEN_REGEX.replace(normalizedChars.toString(), " ").trim()
        if (cleaned.isEmpty() || cleaned == "-" || cleaned == "--" || cleaned == "+") return null

        // 2. Extract sign (accounting parens, leading +/-, or trailing -) — reject conflicting signs
        var negative = false
        var signCount = 0

        if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            negative = true
            signCount++
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }
        if (cleaned.startsWith("-")) {
            negative = true
            signCount++
            cleaned = cleaned.substring(1).trim()
        } else if (cleaned.startsWith("+")) {
            signCount++
            cleaned = cleaned.substring(1).trim()
        }
        if (cleaned.endsWith("-")) {
            negative = true
            signCount++
            cleaned = cleaned.dropLast(1).trim()
        }
        if (signCount > 1 || cleaned.isEmpty()) return null

        // 3. Remove internal spaces used as thousands grouping
        cleaned = cleaned.replace(" ", "")
        if (cleaned.isEmpty()) return null

        // Only digits, dots, and commas may remain (rejects letters, "NaN", "1e5", extra signs, etc.)
        if (!cleaned.all { it in '0'..'9' || it == '.' || it == ',' }) return null

        // 4. Disambiguate thousands vs decimal separators without touching Double
        val canonical = disambiguateDecimalString(cleaned) ?: return null
        if (!VALID_CANONICAL_DECIMAL_REGEX.matches(canonical)) return null

        // 5. Exact String -> BigDecimal -> centimes (HALF_UP) -> Long
        val centimesBig = try {
            BigDecimal(canonical)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
        } catch (_: NumberFormatException) {
            return null
        }

        if (centimesBig.compareTo(BigDecimal.valueOf(maxAbsCentimes)) > 0) return null

        val absCentimes = try {
            centimesBig.longValueExact()
        } catch (_: ArithmeticException) {
            return null
        }

        if (absCentimes == 0L) return 0L
        return if (negative) {
            try {
                Math.negateExact(absCentimes)
            } catch (_: ArithmeticException) {
                null
            }
        } else {
            absCentimes
        }
    }

    private fun disambiguateDecimalString(cleaned: String): String? {
        val lastComma = cleaned.lastIndexOf(',')
        val lastDot = cleaned.lastIndexOf('.')

        return when {
            lastComma >= 0 && lastDot >= 0 -> {
                if (lastComma > lastDot) {
                    // Moroccan/European: 1.250,50 -> integer part uses '.' thousands, ',' is decimal
                    val intPart = cleaned.substring(0, lastComma)
                    val fracPart = cleaned.substring(lastComma + 1)
                    if (intPart.contains(',') || fracPart.isEmpty() || !isValidThousandsGrouped(intPart, '.')) {
                        return null
                    }
                    "${intPart.replace(".", "")}.$fracPart"
                } else {
                    // US/English: 1,250.50 -> integer part uses ',' thousands, '.' is decimal
                    val intPart = cleaned.substring(0, lastDot)
                    val fracPart = cleaned.substring(lastDot + 1)
                    if (intPart.contains('.') || fracPart.isEmpty() || !isValidThousandsGrouped(intPart, ',')) {
                        return null
                    }
                    "${intPart.replace(",", "")}.$fracPart"
                }
            }
            lastComma >= 0 -> {
                val commaCount = cleaned.count { it == ',' }
                if (commaCount > 1) {
                    // Multiple commas -> must be valid thousands grouping: 1,234,567
                    if (!isValidThousandsGrouped(cleaned, ',')) return null
                    cleaned.replace(",", "")
                } else {
                    val intPart = cleaned.substring(0, lastComma)
                    val fracPart = cleaned.substring(lastComma + 1)
                    if (intPart.isEmpty() || fracPart.isEmpty()) return null
                    if (fracPart.length == 3 && intPart.length in 1..3 && !intPart.startsWith("0")) {
                        // Ambiguous "1,250" -> thousands separator (matches CsvStatementParser)
                        "$intPart$fracPart"
                    } else {
                        // Decimal comma: "120,50", "120,5", "0,125"
                        "$intPart.$fracPart"
                    }
                }
            }
            lastDot >= 0 -> {
                val dotCount = cleaned.count { it == '.' }
                if (dotCount > 1) {
                    // Multiple dots -> must be valid thousands grouping: 1.234.567
                    if (!isValidThousandsGrouped(cleaned, '.')) return null
                    cleaned.replace(".", "")
                } else {
                    val intPart = cleaned.substring(0, lastDot)
                    val fracPart = cleaned.substring(lastDot + 1)
                    if (intPart.isEmpty() || fracPart.isEmpty()) return null
                    cleaned
                }
            }
            else -> cleaned
        }
    }

    private fun isValidThousandsGrouped(part: String, sep: Char): Boolean {
        val groups = part.split(sep)
        if (groups.size < 2) return false
        if (groups[0].isEmpty() || groups[0].length > 3 || !groups[0].all { it.isDigit() }) return false
        for (i in 1 until groups.size) {
            if (groups[i].length != 3 || !groups[i].all { it.isDigit() }) return false
        }
        return true
    }
}
