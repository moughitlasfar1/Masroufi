package com.example.domain

import com.example.data.EnvelopeConfig
import com.example.ui.DashboardState
import com.masroufi.core.Money
import com.masroufi.core.MoneyParser
import com.masroufi.core.sumMoney
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class VoiceParsedTransaction(
    val rawTranscript: String,
    val amount: Money,
    val description: String,
    val envelopeKey: String,
    val categoryName: String,
    val txType: String = "expense"
)

enum class VoiceQueryType {
    SAFE_TODAY,
    CYCLE_BALANCE,
    SPENT_THIS_CYCLE,
    TOTAL_ACCOUNTS,
    CATEGORY_SPEND,
    NO_SPEND_STREAK
}

sealed interface VoiceBatchParseResult {
    data class ExpensesBatch(
        val items: List<VoiceParsedTransaction>
    ) : VoiceBatchParseResult {
        val totalAmount: Money get() = items.map { it.amount }.sumMoney()
    }

    data class BudgetQuery(
        val queryType: VoiceQueryType,
        val title: String,
        val displayValue: String,
        val subtitle: String,
        val spokenAnswer: String
    ) : VoiceBatchParseResult

    data class Unrecognized(
        val rawText: String,
        val hint: String
    ) : VoiceBatchParseResult
}

object VoiceDarijaParser {

    private val ARABIC_INDIC_DIGITS = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9'
    )

    private val SPOKEN_NUMBERS: List<Pair<String, Long>> = listOf(
        "ألفين" to 2000L,
        "الفين" to 2000L,
        "ألف" to 1000L,
        "الف" to 1000L,
        "تسعمية" to 900L,
        "تسعمائة" to 900L,
        "تمنمية" to 800L,
        "ثمانمائة" to 800L,
        "سبعمية" to 700L,
        "سبعمائة" to 700L,
        "ستمية" to 600L,
        "ستمائة" to 600L,
        "خمسمائة" to 500L,
        "خمسمية" to 500L,
        "ربعمية" to 400L,
        "أربعمائة" to 400L,
        "تلاتمية" to 300L,
        "ثلاثمائة" to 300L,
        "ميتين" to 200L,
        "مائتين" to 200L,
        "مية" to 100L,
        "مائة" to 100L,
        "تسعين" to 90L,
        "تمانين" to 80L,
        "ثمانين" to 80L,
        "سبعين" to 70L,
        "ستين" to 60L,
        "خمسين" to 50L,
        "ربعين" to 40L,
        "أربعين" to 40L,
        "تلاتين" to 30L,
        "ثلاثين" to 30L,
        "خمسة وعشرين" to 25L,
        "عشرين" to 20L,
        "خمسطاش" to 15L,
        "خمسة عشر" to 15L,
        "طناش" to 12L,
        "عشرة" to 10L,
        "عشره" to 10L,
        "تسعود" to 9L,
        "تسعة" to 9L,
        "تمنية" to 8L,
        "ثمانية" to 8L,
        "سبعة" to 7L,
        "ستة" to 6L,
        "خمسة" to 5L,
        "خمسه" to 5L,
        "ربعة" to 4L,
        "أربعة" to 4L,
        "تلاتة" to 3L,
        "ثلاثة" to 3L,
        "جوج" to 2L,
        "زوج" to 2L
    )

    private val RYAL_THOUSAND_MULTIPLIERS: List<Pair<String, BigDecimal>> = listOf(
        "مية" to BigDecimal.valueOf(100L), "مائة" to BigDecimal.valueOf(100L),
        "تسعين" to BigDecimal.valueOf(90L), "تمانين" to BigDecimal.valueOf(80L), "ثمانين" to BigDecimal.valueOf(80L),
        "سبعين" to BigDecimal.valueOf(70L), "ستين" to BigDecimal.valueOf(60L), "خمسين" to BigDecimal.valueOf(50L),
        "ربعين" to BigDecimal.valueOf(40L), "أربعين" to BigDecimal.valueOf(40L),
        "تلاتين" to BigDecimal.valueOf(30L), "ثلاثين" to BigDecimal.valueOf(30L),
        "عشرين" to BigDecimal.valueOf(20L), "3echrin" to BigDecimal.valueOf(20L),
        "خمسطاش" to BigDecimal.valueOf(15L), "خمسة عشر" to BigDecimal.valueOf(15L),
        "طناش" to BigDecimal.valueOf(12L),
        "عشرة" to BigDecimal.valueOf(10L), "عشره" to BigDecimal.valueOf(10L), "3achra" to BigDecimal.valueOf(10L), "achra" to BigDecimal.valueOf(10L),
        "تسعود" to BigDecimal.valueOf(9L), "تسعة" to BigDecimal.valueOf(9L),
        "تمنية" to BigDecimal.valueOf(8L), "ثمانية" to BigDecimal.valueOf(8L), "temnya" to BigDecimal.valueOf(8L),
        "سبعة" to BigDecimal.valueOf(7L), "seb3a" to BigDecimal.valueOf(7L),
        "ستة" to BigDecimal.valueOf(6L), "setta" to BigDecimal.valueOf(6L),
        "خمسة" to BigDecimal.valueOf(5L), "خمسه" to BigDecimal.valueOf(5L), "khamsa" to BigDecimal.valueOf(5L),
        "ربعة" to BigDecimal.valueOf(4L), "أربعة" to BigDecimal.valueOf(4L), "rb3a" to BigDecimal.valueOf(4L), "reb3a" to BigDecimal.valueOf(4L),
        "تلاتة" to BigDecimal.valueOf(3L), "ثلاثة" to BigDecimal.valueOf(3L), "tlata" to BigDecimal.valueOf(3L),
        "جوج" to BigDecimal.valueOf(2L), "زوج" to BigDecimal.valueOf(2L), "jouj" to BigDecimal.valueOf(2L), "jooj" to BigDecimal.valueOf(2L)
    )

    private val FIFTY_BD: BigDecimal = BigDecimal.valueOf(50L)
    private val RYAL_TO_DH_BD: BigDecimal = BigDecimal("0.05")

    private val CURRENCY_NOISE_WORDS = listOf(
        "درهم", "دراهم", "د.م", "dh", "dhs", "mad", "dirhams", "dirham",
        "ريال", "ريالات", "ryal", "rial", "ryalat", "دلاف", "dlaf",
        "فرنك", "frank", "franc", "francs",
        "بـ", "ب"
    )

    private val INCOME_KEYWORDS = listOf(
        "salaire", "خلصة", "الخلصة", "راتب", "مدخول", "virement", "income", "salary", "prime", "بريم"
    )

    private val DARIJA_CATEGORY_MAP = listOf(
        // Café, Resto & Sorties -> "cafe"
        Triple(
            listOf(
                "coffee", "café", "cafe", "qahwa", "kahwa", "قهوة", "قهوه", "أتاي", "اتاي", "atay",
                "tacos", "طاكوس", "طاكوص", "pizza", "بيتزا", "sandwich", "سندويش", "فطور", "غداء", "غدا",
                "gheda", "ghda", "lgheda", "déjeuner", "dejeuner", "lunch",
                "عشاء", "عشا", "echa", "3cha", "acha", "dîner", "diner", "dinner",
                "فتور", "ftour", "breakfast", "resto", "restaurant", "مسمن", "حرشة", "عصير", "jus",
                "snack", "garo", "chawarma", "شاورما", "burger", "glace", "pâtisserie", "patisserie",
                "glovo", "جلوفو", "mcdo", "ماكدو"
            ),
            "cafe",
            "Café & Sorties"
        ),
        // Transport -> "transport"
        Triple(
            listOf(
                "طاكسي كبير", "طاكسي صغير", "petit taxi", "grand taxi", "taxi", "طاكسي", "تاكسي",
                "oncf", "تران", "قطار", "train", "tram", "طرام", "bus", "طوبيس", "وطوبيس",
                "مازوط", "ليسنس", "ليصانص", "كازوال", "essence", "gasoil", "carburant",
                "autoroute", "peage", "péage", "jawaz", "جواز", "navette",
                "buraq", "al boraq", "alboraq", "لبراق", "البراق",
                "afriquia", "افريقيا", "winxo", "وينكسو", "shell", "شيل",
                "lavage", "لاڤاج", "لافاج",
                "نقل", "transport", "indrive", "careem", "uber", "parking", "باركينغ"
            ),
            "transport",
            "Transport"
        ),
        // Charges & Maison -> "charges"
        Triple(
            listOf(
                "khobz", "5obz", "خبز", "حليب", "hlib", "7lib", "lben", "لبن", "تقضية", "تقدية",
                "tqadya", "t9adya", "سوق", "مرجان", "بيم", "bim", "marjane", "carrefour", "أسيما",
                "خضرة", "لحم", "l7am", "دجاج", "حوت", "7out", "hout", "hanout", "7anout", "حانوت",
                "epicerie", "épicerie", "ماء", "ضو", "ضوء", "الكراء", "كراء", "loyer", "syndic",
                "ويفي", "wifi", "fibre", "inwi", "iam", "orange", "روشارج", "recharge", "lydec",
                "redal", "amendis", "amendys", "onee", "facture", "فاتورة", "دار", "maison", "courses",
                "gaz", "بوطا", "بوتاغاز", "boutagaz", "bota",
                "la visite", "لافيزيت", "ضريبة", "vignette", "تأمين", "assurance"
            ),
            "charges",
            "Charges & Maison"
        ),
        // Épargne -> "epargne"
        Triple(
            listOf(
                "épargne", "epargne", "توفير", "ادخار", "دارت", "daret", "jam3iya", "قرعة", "ذهب", "استثمار", "savings"
            ),
            "epargne",
            "Épargne"
        ),
        // Santé, Pharmacie & Imprévus -> "autre"
        Triple(
            listOf(
                "dwa", "ldwa", "دوا", "دواء", "pharmacie", "pharmacy", "صيدلية", "tbib", "طبيب",
                "médecin", "medecin", "dentiste", "clinique", "coiffeur", "7ellak", "حلاق", "cadeau", "هدية"
            ),
            "autre",
            "Autre"
        )
    )

    private val ARABIZI_WORD_REPLACEMENTS = listOf(
        Regex("""\b9ahwa\b""", RegexOption.IGNORE_CASE) to "Qahwa",
        Regex("""\b9ehwa\b""", RegexOption.IGNORE_CASE) to "Qahwa",
        Regex("""\b7lib\b""", RegexOption.IGNORE_CASE) to "Hlib",
        Regex("""\b5obz\b""", RegexOption.IGNORE_CASE) to "Khobz",
        Regex("""\b3asir\b""", RegexOption.IGNORE_CASE) to "Asir",
        Regex("""\b3assir\b""", RegexOption.IGNORE_CASE) to "Asir",
        Regex("""\b7out\b""", RegexOption.IGNORE_CASE) to "Hout",
        Regex("""\bl7am\b""", RegexOption.IGNORE_CASE) to "Lham",
        Regex("""\b3cha\b""", RegexOption.IGNORE_CASE) to "Echa",
        Regex("""\bt9adya\b""", RegexOption.IGNORE_CASE) to "Tqadya",
        Regex("""\b9raya\b""", RegexOption.IGNORE_CASE) to "Qraya",
        Regex("""\bjam3iya\b""", RegexOption.IGNORE_CASE) to "Jam3iya",
        Regex("""\b7anout\b""", RegexOption.IGNORE_CASE) to "Hanout",
        Regex("""\b7ellak\b""", RegexOption.IGNORE_CASE) to "Hellak"
    )

    fun inferKeywordCategory(
        rawText: String,
        envelopes: List<EnvelopeConfig> = emptyList(),
        language: String = "fr"
    ): Pair<String, String>? {
        var normalized = rawText.trim()
        if (normalized.isBlank()) return null
        for ((pattern, replacement) in ARABIZI_WORD_REPLACEMENTS) {
            normalized = pattern.replace(normalized, replacement)
        }
        PayCycleEngine.getLearnedCategoryForDescription(normalized)?.let { learnedKey ->
            val matchedEnv = envelopes.find { it.key == learnedKey }
            if (matchedEnv != null) {
                return matchedEnv.key to matchedEnv.localizedName(language)
            }
        }
        val lower = normalized.lowercase(Locale.ROOT)
        for ((keywords, envKey, defaultLabel) in DARIJA_CATEGORY_MAP) {
            if (keywords.any { kw -> lower.contains(kw) }) {
                val matchedEnv = envelopes.find { it.key == envKey }
                val finalKey = matchedEnv?.key ?: envKey
                val finalName = matchedEnv?.localizedName(language) ?: defaultLabel
                return finalKey to finalName
            }
        }
        return null
    }

    fun parse(
        rawInput: String,
        envelopes: List<EnvelopeConfig> = emptyList(),
        language: String = "fr"
    ): VoiceParsedTransaction? {
        var normalizedDigits = buildString(rawInput.length) {
            for (ch in rawInput.trim()) {
                append(ARABIC_INDIC_DIGITS[ch] ?: ch)
            }
        }
        if (normalizedDigits.isBlank()) return null

        // Normalize common Moroccan Arabizi words (e.g., "9ahwa 15", "7lib 10", "5obz 5") so leading
        // Arabizi digits (9, 7, 5, 3) are recognized as letters rather than the transaction amount.
        for ((pattern, replacement) in ARABIZI_WORD_REPLACEMENTS) {
            normalizedDigits = pattern.replace(normalizedDigits, replacement)
        }

        // 1. Check Moroccan Ryal / Dlaf / Francs idioms and numeric Ryal ("1000 ryal", "ألف ريال", "جوج دلاف") first (1 Ryal = 5 centimes exact)
        var extractedAmount = Money.ZERO
        var cleanedText = normalizedDigits
        val ryalMatch = extractRyalOrDlafAmount(normalizedDigits)
        if (ryalMatch != null) {
            extractedAmount = ryalMatch.first
            cleanedText = ryalMatch.second
        } else {
            // 2. Extract standalone numeric amount (digits not attached to letters first, then spoken Darija/Arabic words)
            val standaloneNumberRegex = Regex("""(?<!\p{L})(\d+(?:[.,]\d{1,2})?)(?!\p{L})""")
            val fallbackNumberRegex = Regex("""(\d+(?:[.,]\d{1,2})?)""")
            val match = standaloneNumberRegex.find(normalizedDigits) ?: fallbackNumberRegex.find(normalizedDigits)
            extractedAmount = match?.groupValues?.get(1)
                ?.let { MoneyParser.parseMoroccanAmountToCentimes(it) }
                ?.takeIf { it > 0L }
                ?.let { Money.fromCentimes(it) }
                ?: Money.ZERO
            cleanedText = if (match != null) {
                (normalizedDigits.substring(0, match.range.first) + " " + normalizedDigits.substring(match.range.last + 1)).trim()
            } else {
                normalizedDigits
            }

            if (!extractedAmount.isPositive) {
                for ((word, valueDh) in SPOKEN_NUMBERS) {
                    if (cleanedText.contains(word, ignoreCase = true)) {
                        extractedAmount = Money.fromDh(valueDh)
                        cleanedText = cleanedText.replaceFirst(word, "", ignoreCase = true).trim()
                        break
                    }
                }
            }
        }

        if (!extractedAmount.isPositive) return null

        // 2. Clean currency words and punctuation
        val descTokens = cleanedText
            .replace(Regex("""[،,;:\-_/]+"""), " ")
            .split(Regex("""\s+"""))
            .filter { token ->
                val lower = token.lowercase(Locale.ROOT)
                lower.isNotBlank() && lower !in CURRENCY_NOISE_WORDS
            }

        val cleanedDesc = if (descTokens.isEmpty()) {
            when (language) {
                "ar" -> "مصروف سريع"
                "darija" -> "مصروف دغيا"
                "en" -> "Quick Expense"
                else -> "Achat rapide"
            }
        } else {
            descTokens.joinToString(" ").replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }

        val fullLower = "$normalizedDigits $cleanedDesc".lowercase(Locale.ROOT)
        val isIncome = INCOME_KEYWORDS.any { fullLower.contains(it) }
        val txType = if (isIncome) "income" else "expense"

        // 3. Infer envelopeKey & localized category name (learned user categories first, then keyword map)
        var matchedKey = PayCycleEngine.getLearnedCategoryForDescription(cleanedDesc).orEmpty()
        var fallbackLabel = ""
        if (matchedKey.isBlank()) {
            for ((keywords, envKey, defaultLabel) in DARIJA_CATEGORY_MAP) {
                if (keywords.any { kw -> fullLower.contains(kw) }) {
                    matchedKey = envKey
                    fallbackLabel = defaultLabel
                    break
                }
            }
        }

        if (matchedKey.isBlank()) {
            matchedKey = PayCycleEngine.resolveEnvelopeKey(cleanedDesc, cleanedDesc, envelopes)
        }

        val matchedEnvelope = envelopes.find { it.key == matchedKey } ?: envelopes.firstOrNull()
        val finalEnvKey = matchedEnvelope?.key ?: matchedKey.ifBlank { "autre" }
        val finalCategoryName = matchedEnvelope?.localizedName(language)
            ?: fallbackLabel.ifBlank { cleanedDesc }

        return VoiceParsedTransaction(
            rawTranscript = rawInput.trim(),
            amount = extractedAmount,
            description = cleanedDesc,
            envelopeKey = finalEnvKey,
            categoryName = finalCategoryName,
            txType = txType
        )
    }

    private fun parsePositiveDecimal(raw: String): BigDecimal? {
        val clean = raw.trim().replace(",", ".")
        if (clean.isEmpty()) return null
        val bd = clean.toBigDecimalOrNull() ?: return null
        return if (bd.signum() > 0) bd else null
    }

    private fun parseMultiplierToken(raw: String): BigDecimal? {
        val clean = raw.trim().lowercase(Locale.ROOT).replace(",", ".")
        parsePositiveDecimal(clean)?.let { return it }
        return RYAL_THOUSAND_MULTIPLIERS.firstOrNull { (token, _) ->
            clean == token.lowercase(Locale.ROOT)
        }?.second
    }

    /**
     * Converts Moroccan Ryal (`1 Ryal = 5 centimes` exact), Dlaf (`جوج دلاف` = 2000 Ryal = 100.00 DH,
     * `عشرة دلاف` = 10000 Ryal = 500.00 DH), and Francs (`1 Franc = 1 centime` exact) expressions to [Money].
     */
    private fun extractRyalOrDlafAmount(input: String): Pair<Money, String>? {
        val multChoices = RYAL_THOUSAND_MULTIPLIERS.joinToString("|") { Regex.escape(it.first) }

        // A. Dlaf idioms: "جوج دلاف", "عشرة دلاف", "2 dlaf", "10 dlaf" -> multiplier * 1000 ryal = multiplier * 50 DH
        val dlafRegex = Regex(
            """(?<!\p{L})($multChoices|\d+(?:[.,]\d+)?)\s*(?:دلاف|dlaf|d'alaf)(?!\p{L})""",
            RegexOption.IGNORE_CASE
        )
        dlafRegex.find(input)?.let { m ->
            val mult = parseMultiplierToken(m.groupValues[1])
            if (mult != null && mult.signum() > 0) {
                val money = runCatching {
                    Money.fromMajorUnits(mult.multiply(FIFTY_BD), RoundingMode.HALF_UP)
                }.getOrNull()
                if (money != null && money.isPositive) {
                    val rem = (input.substring(0, m.range.first) + " " + input.substring(m.range.last + 1)).trim()
                    return money to rem
                }
            }
        }

        // B. Compound "<multiplier> ألف/آلاف ريال": e.g. "عشرين ألف ريال" = 20000 ryal = 1000.00 DH
        val compoundAlfRyalRegex = Regex(
            """(?<!\p{L})($multChoices|\d+(?:[.,]\d+)?)\s*(?:ألف|الف|آلاف|الاف|alf|alaf)\s*(?:ريال|ريالات|ryalat|ryal|rial)(?!\p{L})""",
            RegexOption.IGNORE_CASE
        )
        compoundAlfRyalRegex.find(input)?.let { m ->
            val mult = parseMultiplierToken(m.groupValues[1])
            if (mult != null && mult.signum() > 0) {
                val money = runCatching {
                    Money.fromMajorUnits(mult.multiply(FIFTY_BD), RoundingMode.HALF_UP)
                }.getOrNull()
                if (money != null && money.isPositive) {
                    val rem = (input.substring(0, m.range.first) + " " + input.substring(m.range.last + 1)).trim()
                    return money to rem
                }
            }
        }

        // C. Numeric "<amount> ryal/ريال": 1 Ryal = 5 centimes exact (e.g., "1000 ryal" = 50.00 DH, "1 ryal" = 0.05 DH)
        val numericRyalRegex = Regex(
            """(?<!\p{L})(\d+(?:[.,]\d{1,2})?)\s*(?:ريال|ريالات|ryalat|ryal|rial)(?!\p{L})""",
            RegexOption.IGNORE_CASE
        )
        numericRyalRegex.find(input)?.let { m ->
            val ryalBd = parsePositiveDecimal(m.groupValues[1])
            if (ryalBd != null) {
                val money = runCatching {
                    Money.fromMajorUnits(ryalBd.multiply(RYAL_TO_DH_BD), RoundingMode.HALF_UP)
                }.getOrNull()
                if (money != null && money.isPositive) {
                    val rem = (input.substring(0, m.range.first) + " " + input.substring(m.range.last + 1)).trim()
                    return money to rem
                }
            }
        }

        // D. Spoken single number + Ryal: e.g. "ألف ريال" = 50.00 DH, "مية ريال" = 5.00 DH, "خمسمية ريال" = 25.00 DH
        for ((word, valueRyal) in SPOKEN_NUMBERS) {
            val spokenRyalRegex = Regex(
                """(?<!\p{L})${Regex.escape(word)}\s*(?:ريال|ريالات|ryalat|ryal|rial)(?!\p{L})""",
                RegexOption.IGNORE_CASE
            )
            spokenRyalRegex.find(input)?.let { m ->
                val money = Money.fromRyal(valueRyal)
                val rem = (input.substring(0, m.range.first) + " " + input.substring(m.range.last + 1)).trim()
                return money to rem
            }
        }

        // E. Numeric Francs (1 DH = 100 francs -> 1 franc = 1 centime exact): e.g. "500 francs" -> 5.00 DH
        val numericFrancRegex = Regex(
            """(?<!\p{L})(\d+(?:[.,]\d{1,2})?)\s*(?:فرنك|frank|francs?)(?!\p{L})""",
            RegexOption.IGNORE_CASE
        )
        numericFrancRegex.find(input)?.let { m ->
            val frBd = parsePositiveDecimal(m.groupValues[1])
            if (frBd != null) {
                val money = runCatching {
                    Money.fromMajorUnits(frBd.movePointLeft(2), RoundingMode.HALF_UP)
                }.getOrNull()
                if (money != null && money.isPositive) {
                    val rem = (input.substring(0, m.range.first) + " " + input.substring(m.range.last + 1)).trim()
                    return money to rem
                }
            }
        }

        return null
    }

    /**
     * Splits a multi-expense sentence in Darija, Arabic, French, or English into individual
     * [VoiceParsedTransaction] items (e.g., "9ahwa 15, taxi 25 w khobz 5" -> 3 transactions).
     */
    fun parseMultiExpenses(
        rawInput: String,
        envelopes: List<EnvelopeConfig> = emptyList(),
        language: String = "fr"
    ): List<VoiceParsedTransaction> {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return emptyList()

        // Normalize Arabic-Indic digits first so we can detect multi-number clauses reliably
        var normalized = buildString(trimmed.length) {
            for (ch in trimmed) {
                append(ARABIC_INDIC_DIGITS[ch] ?: ch)
            }
        }
        for ((pattern, replacement) in ARABIZI_WORD_REPLACEMENTS) {
            normalized = pattern.replace(normalized, replacement)
        }

        // Split on natural conjunctions across Darija/AR/FR/EN:
        // ",", ";", "+", "&", " w ", " et ", " and ", " puis ", " ثم ", " من بعد ", or Arabic " و" when separating clauses with numbers/ryal
        val spokenStart = "ألف|الف|مية|مائة|ميتين|خمسمية|جوج|عشرة|عشرين|تلاتين|ربعين|أربعين|خمسين"
        val delimiterRegex = Regex(
            """(?:\s*[,;+&،]\s*|\s+(?:w|et|and|puis|then|ثم|من بعد)\s+|\s+و(?=\s*\p{L}+\s+(?:\d|$spokenStart)|\p{L}+\s+(?:\d|$spokenStart)))""",
            RegexOption.IGNORE_CASE
        )

        val candidateClauses = normalized
            .split(delimiterRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (candidateClauses.size > 1) {
            val parsedList = candidateClauses.mapNotNull { clause ->
                parse(clause, envelopes, language)
            }
            if (parsedList.isNotEmpty()) {
                return parsedList
            }
        }

        // Also support adjacent "<word(s)> <number> <word(s)> <number>" without explicit conjunctions
        // e.g. "qahwa 15 taxi 20 khobz 5" or "qahwa 200 ryal taxi 500 ryal"
        val itemPattern = Regex(
            """([^\d,;+&،]+?\s+\d+(?:[.,]\d{1,2})?(?:\s*(?:dh|dhs|mad|درهم|دراهم|ryal|rial|ryalat|ريال|ريالات|dlaf|دلاف|frank|francs?|فرنك))?)""",
            RegexOption.IGNORE_CASE
        )
        val sequentialMatches = itemPattern.findAll(normalized).map { it.value.trim() }.toList()
        if (sequentialMatches.size >= 2) {
            val parsedSeq = sequentialMatches.mapNotNull { seg ->
                parse(seg, envelopes, language)
            }
            if (parsedSeq.size >= 2) {
                return parsedSeq
            }
        }

        return listOfNotNull(parse(trimmed, envelopes, language))
    }

    /**
     * Evaluates a spoken utterance as either:
     * 1. A hands-free Budget Q&A question answered strictly from [DashboardState] (with privacy masking support),
     * 2. A single or multi-expense batch ([VoiceBatchParseResult.ExpensesBatch]), or
     * 3. An unrecognized phrase with a helpful localized example.
     */
    fun evaluateVoiceUtterance(
        rawInput: String,
        state: DashboardState
    ): VoiceBatchParseResult {
        val lang = state.profile.language
        val envelopes = state.envelopes.map { it.config }
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return VoiceBatchParseResult.Unrecognized(trimmed, defaultHintForLang(lang))
        }

        // 1. Check if the user is asking a hands-free Budget Question
        detectBudgetQuery(trimmed, state)?.let { queryResult ->
            return queryResult
        }

        // 2. Otherwise parse single or multiple expenses from the utterance
        val expenses = parseMultiExpenses(trimmed, envelopes, lang)
        if (expenses.isNotEmpty()) {
            return VoiceBatchParseResult.ExpensesBatch(expenses)
        }

        return VoiceBatchParseResult.Unrecognized(trimmed, defaultHintForLang(lang))
    }

    private fun detectBudgetQuery(
        rawInput: String,
        state: DashboardState
    ): VoiceBatchParseResult.BudgetQuery? {
        val lang = state.profile.language
        val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
        val hide = state.hideAmounts
        val lower = rawInput.lowercase(Locale.ROOT)

        val questionMarkers = listOf(
            "ch7al", "chhal", "شحال", "كم", "combien", "how much", "what is", "whats",
            "b9a", "bqa", "بقى", "تبقى", "متبقي", "reste", "left", "remaining",
            "sraft", "sreft", "صرفت", "أنفقت", "dépensé", "depense", "spent",
            "solde", "رصيد", "balance", "streak", "سلسلة", "safe"
        )
        val hasQuestionMarker = questionMarkers.any { lower.contains(it) } || rawInput.endsWith("?") || rawInput.endsWith("؟")
        if (!hasQuestionMarker) return null

        // If it contains a standalone number and no explicit question word ("ch7al"/"combien"/"how much"/"كم"),
        // treat it as an expense rather than a question.
        val hasExplicitInterrogative = listOf("ch7al", "chhal", "شحال", "كم", "combien", "how much", "?", "؟")
            .any { lower.contains(it) }
        val hasDigits = rawInput.any { it.isDigit() && it != '7' && it != '9' && it != '3' && it != '5' }
        if (hasDigits && !hasExplicitInterrogative) return null

        // Check if asking about a specific category/envelope first (e.g. "ch7al sraft f transport?")
        for (envStatus in state.envelopes) {
            val envNameLower = envStatus.config.localizedName(lang).lowercase(Locale.ROOT)
            val keyLower = envStatus.config.key.lowercase(Locale.ROOT)
            val matchesCat = lower.contains(keyLower) ||
                lower.contains(envNameLower) ||
                (keyLower == "transport" && (lower.contains("طاكسي") || lower.contains("نقل") || lower.contains("تران"))) ||
                (keyLower == "cafe" && (lower.contains("قهوة") || lower.contains("qahwa") || lower.contains("خرج") || lower.contains("café"))) ||
                (keyLower == "charges" && (lower.contains("دار") || lower.contains("تقدية") || lower.contains("سوق")))
            if (matchesCat) {
                val catTitle = envStatus.config.localizedName(lang)
                val spentDisp = PayCycleEngine.formatCurrencyOrMask(envStatus.spent, cur, hide)
                val remDisp = PayCycleEngine.formatCurrencyOrMask(envStatus.remaining, cur, hide)
                val spoken = when (lang) {
                    "darija" -> "فـ $catTitle صرفتي ${PayCycleEngine.formatDh(envStatus.spent)} $cur، وباقي ليك ${PayCycleEngine.formatDh(envStatus.remaining)} $cur."
                    "ar" -> "في فئة $catTitle أنفقت ${PayCycleEngine.formatDh(envStatus.spent)} $cur، والمتبقي ${PayCycleEngine.formatDh(envStatus.remaining)} $cur."
                    "en" -> "In $catTitle, you spent ${PayCycleEngine.formatDh(envStatus.spent)} $cur, with ${PayCycleEngine.formatDh(envStatus.remaining)} $cur remaining."
                    else -> "Dans $catTitle, vous avez dépensé ${PayCycleEngine.formatDh(envStatus.spent)} $cur, il reste ${PayCycleEngine.formatDh(envStatus.remaining)} $cur."
                }
                val sub = when (lang) {
                    "darija" -> "الباقي فهاد الظرف: $remDisp"
                    "ar" -> "المتبقي في الظرف: $remDisp"
                    "en" -> "Remaining in envelope: $remDisp"
                    else -> "Reste dans l'enveloppe : $remDisp"
                }
                return VoiceBatchParseResult.BudgetQuery(
                    queryType = VoiceQueryType.CATEGORY_SPEND,
                    title = catTitle,
                    displayValue = spentDisp,
                    subtitle = sub,
                    spokenAnswer = spoken
                )
            }
        }

        // Check if asking about total spent this cycle ("ch7al sraft", "combien j'ai dépensé", "how much did I spend")
        val asksSpent = listOf("sraft", "sreft", "صرفت", "أنفقت", "dépensé", "depense", "spent")
            .any { lower.contains(it) }
        if (asksSpent) {
            val spentDisp = PayCycleEngine.formatCurrencyOrMask(state.cycleExpenses, cur, hide)
            val budgetDisp = PayCycleEngine.formatCurrencyOrMask(state.totalAvailableBudget, cur, hide)
            val title = when (lang) {
                "darija" -> "مجموع المصاريف هاد الشهر"
                "ar" -> "إجمالي مصاريف الدورة"
                "en" -> "Spent this cycle"
                else -> "Dépensé ce cycle"
            }
            val sub = when (lang) {
                "darija" -> "من أصل ميزانية $budgetDisp"
                "ar" -> "من أصل ميزانية $budgetDisp"
                "en" -> "Out of $budgetDisp budget"
                else -> "Sur un budget de $budgetDisp"
            }
            val spoken = when (lang) {
                "darija" -> "صرفتي هاد الشهر ${PayCycleEngine.formatDh(state.cycleExpenses)} $cur من أصل ${PayCycleEngine.formatDh(state.totalAvailableBudget)} $cur."
                "ar" -> "أنفقت في هذه الدورة ${PayCycleEngine.formatDh(state.cycleExpenses)} $cur من أصل ${PayCycleEngine.formatDh(state.totalAvailableBudget)} $cur."
                "en" -> "You have spent ${PayCycleEngine.formatDh(state.cycleExpenses)} $cur this cycle out of ${PayCycleEngine.formatDh(state.totalAvailableBudget)} $cur."
                else -> "Vous avez dépensé ${PayCycleEngine.formatDh(state.cycleExpenses)} $cur ce cycle sur ${PayCycleEngine.formatDh(state.totalAvailableBudget)} $cur."
            }
            return VoiceBatchParseResult.BudgetQuery(
                queryType = VoiceQueryType.SPENT_THIS_CYCLE,
                title = title,
                displayValue = spentDisp,
                subtitle = sub,
                spokenAnswer = spoken
            )
        }

        // Check if asking about total accounts / net worth ("حساباتي", "comptes", "accounts")
        val asksAccounts = listOf("comptes", "accounts", "حسابات", "فلوس", "net worth", "banque")
            .any { lower.contains(it) }
        if (asksAccounts) {
            val totalDisp = PayCycleEngine.formatCurrencyOrMask(state.totalNetWorth, cur, hide)
            val cycleDisp = PayCycleEngine.formatCurrencyOrMask(state.remainingBudget, cur, hide)
            val title = Translations.forLang(lang).totalAccountsLabel
            val sub = "${Translations.forLang(lang).cycleBalanceLabel} : $cycleDisp"
            val spoken = when (lang) {
                "darija" -> "مجموع الحسابات ديالك هو ${PayCycleEngine.formatDh(state.totalNetWorth)} $cur، والباقي فميزانية الشهر هو ${PayCycleEngine.formatDh(state.remainingBudget)} $cur."
                "ar" -> "مجموع حساباتك هو ${PayCycleEngine.formatDh(state.totalNetWorth)} $cur، ورصيد الدورة المتبقي هو ${PayCycleEngine.formatDh(state.remainingBudget)} $cur."
                "en" -> "Your total across accounts is ${PayCycleEngine.formatDh(state.totalNetWorth)} $cur, and your cycle balance is ${PayCycleEngine.formatDh(state.remainingBudget)} $cur."
                else -> "Le total de vos comptes est de ${PayCycleEngine.formatDh(state.totalNetWorth)} $cur, et votre solde du cycle est de ${PayCycleEngine.formatDh(state.remainingBudget)} $cur."
            }
            return VoiceBatchParseResult.BudgetQuery(
                queryType = VoiceQueryType.TOTAL_ACCOUNTS,
                title = title,
                displayValue = totalDisp,
                subtitle = sub,
                spokenAnswer = spoken
            )
        }

        // Check if asking about monthly / cycle balance ("هاد الشهر", "ce mois", "this month", "cycle")
        val asksCycleBalance = listOf("chhar", "شهر", "الشهر", "mois", "month", "cycle")
            .any { lower.contains(it) }
        if (asksCycleBalance) {
            val remDisp = PayCycleEngine.formatCurrencyOrMask(state.remainingBudget, cur, hide)
            val safeDisp = PayCycleEngine.formatCurrencyOrMask(state.safeToSpendToday, cur, hide)
            val title = Translations.forLang(lang).cycleBalanceLabel
            val sub = "${Translations.forLang(lang).safeToSpendTitle} : $safeDisp"
            val spoken = when (lang) {
                "darija" -> "باقي ليك فهاد الشهر ${PayCycleEngine.formatDh(state.remainingBudget)} $cur، أي ${PayCycleEngine.formatDh(state.safeToSpendToday)} $cur فاليوم."
                "ar" -> "المتبقي في رصيد الدورة ${PayCycleEngine.formatDh(state.remainingBudget)} $cur، أي ${PayCycleEngine.formatDh(state.safeToSpendToday)} $cur لليوم."
                "en" -> "You have ${PayCycleEngine.formatDh(state.remainingBudget)} $cur left in this cycle, or ${PayCycleEngine.formatDh(state.safeToSpendToday)} $cur safe to spend today."
                else -> "Il vous reste ${PayCycleEngine.formatDh(state.remainingBudget)} $cur sur ce cycle, soit ${PayCycleEngine.formatDh(state.safeToSpendToday)} $cur disponibles aujourd'hui."
            }
            return VoiceBatchParseResult.BudgetQuery(
                queryType = VoiceQueryType.CYCLE_BALANCE,
                title = title,
                displayValue = remDisp,
                subtitle = sub,
                spokenAnswer = spoken
            )
        }

        // Default budget question: Safe to spend today ("ch7al b9a liya lyoum?", "combien il me reste ?", "how much left today?")
        val safeDisp = PayCycleEngine.formatCurrencyOrMask(state.safeToSpendToday, cur, hide)
        val cycleDisp = PayCycleEngine.formatCurrencyOrMask(state.remainingBudget, cur, hide)
        val title = Translations.forLang(lang).safeToSpendTitle
        val sub = "${Translations.forLang(lang).cycleBalanceLabel} : $cycleDisp (${state.activeWindow.daysRemaining}j)"
        val spoken = when (lang) {
            "darija" -> "تقدر تصرف اليوم مرتاح ${PayCycleEngine.formatDh(state.safeToSpendToday)} $cur. الباقي فالشهر هو ${PayCycleEngine.formatDh(state.remainingBudget)} $cur."
            "ar" -> "المتاح للصرف اليوم هو ${PayCycleEngine.formatDh(state.safeToSpendToday)} $cur. المتبقي في الدورة ${PayCycleEngine.formatDh(state.remainingBudget)} $cur."
            "en" -> "You can safely spend ${PayCycleEngine.formatDh(state.safeToSpendToday)} $cur today. Your remaining cycle balance is ${PayCycleEngine.formatDh(state.remainingBudget)} $cur."
            else -> "Vous avez ${PayCycleEngine.formatDh(state.safeToSpendToday)} $cur disponibles aujourd'hui. Solde du cycle : ${PayCycleEngine.formatDh(state.remainingBudget)} $cur."
        }
        return VoiceBatchParseResult.BudgetQuery(
            queryType = VoiceQueryType.SAFE_TODAY,
            title = title,
            displayValue = safeDisp,
            subtitle = sub,
            spokenAnswer = spoken
        )
    }

    private fun defaultHintForLang(lang: String): String = when (lang) {
        "darija" -> "قول مثلاً: « قهوة 15 وطاكسي 25 » ولا « شحال بقى ليا اليوم؟ »"
        "ar" -> "قل مثلاً: « قهوة 15 وطاكسي 25 » أو « كم تبقى لي اليوم؟ »"
        "en" -> "Say e.g. 'Coffee 15 and taxi 25' or 'How much left today?'"
        else -> "Dites ex: « Café 15 et taxi 25 » ou « Combien il me reste ? »"
    }

    fun samplePhrasesForLanguage(lang: String): List<String> = when (lang) {
        "darija" -> listOf("9ahwa 15 w taxi 25 w khobz 5", "قهوة 15", "طاكسي 20", "شحال بقى ليا اليوم؟", "شحال صرفت فـ transport؟")
        "ar" -> listOf("قهوة 15 وطاكسي 25 وخبز 5", "غداء 45", "كم تبقى لي اليوم؟", "كم أنفقت هذا الشهر؟")
        "en" -> listOf("Coffee 15 and taxi 25 and bread 5", "Lunch 45", "How much left today?", "How much did I spend?")
        else -> listOf("Café 15 et taxi 25 et pain 5", "Déjeuner 45", "Combien il me reste aujourd'hui ?", "Combien j'ai dépensé ?")
    }
}
