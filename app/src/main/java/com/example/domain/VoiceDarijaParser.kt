package com.example.domain

import com.example.data.EnvelopeConfig
import java.util.Locale

data class VoiceParsedTransaction(
    val rawTranscript: String,
    val amount: Double,
    val description: String,
    val envelopeKey: String,
    val categoryName: String,
    val txType: String = "expense"
)

object VoiceDarijaParser {

    private val ARABIC_INDIC_DIGITS = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9'
    )

    private val SPOKEN_NUMBERS = listOf(
        "ألفين" to 2000.0,
        "الفين" to 2000.0,
        "ألف" to 1000.0,
        "الف" to 1000.0,
        "خمسمائة" to 500.0,
        "خمسمية" to 500.0,
        "ميتين" to 200.0,
        "مائتين" to 200.0,
        "مية" to 100.0,
        "مائة" to 100.0,
        "خمسين" to 50.0,
        "ربعين" to 40.0,
        "أربعين" to 40.0,
        "تلاتين" to 30.0,
        "ثلاثين" to 30.0,
        "خمسة وعشرين" to 25.0,
        "عشرين" to 20.0,
        "خمسطاش" to 15.0,
        "خمسة عشر" to 15.0,
        "طناش" to 12.0,
        "عشرة" to 10.0,
        "عشره" to 10.0,
        "تمنية" to 8.0,
        "ثمانية" to 8.0,
        "سبعة" to 7.0,
        "ستة" to 6.0,
        "خمسة" to 5.0,
        "خمسه" to 5.0
    )

    private val CURRENCY_NOISE_WORDS = listOf(
        "درهم", "دراهم", "د.م", "dh", "dhs", "mad", "dirhams", "dirham", "بـ", "ب"
    )

    private val INCOME_KEYWORDS = listOf(
        "salaire", "خلصة", "الخلصة", "راتب", "مدخول", "virement", "income", "salary", "prime", "بريم"
    )

    private val DARIJA_CATEGORY_MAP = listOf(
        // Café, Resto & Sorties -> "cafe"
        Triple(
            listOf(
                "coffee", "café", "cafe", "qahwa", "kahwa", "قهوة", "قهوه", "أتاي", "اتاي", "atay",
                "tacos", "طاكوس", "طاكوص", "pizza", "بيتزا", "sandwich", "سندويش", "فطور", "غداء",
                "عشاء", "فتور", "resto", "restaurant", "مسمن", "حرشة", "عصير", "jus", "snack", "garo"
            ),
            "cafe",
            "Café & Sorties"
        ),
        // Transport & Carburant -> "transport"
        Triple(
            listOf(
                "taxi", "طاكسي", "تاكسي", "oncf", "تران", "قطار", "train", "tram", "طرام", "bus",
                "طوبيس", "وطوبيس", "مازوط", "ليسنس", "essence", "gasoil", "carburant", "autoroute",
                "نقل", "transport", "indrive", "careem", "uber", "parking", "باركينغ"
            ),
            "transport",
            "Transport & Carburant"
        ),
        // Charges fixes & Maison -> "charges"
        Triple(
            listOf(
                "khobz", "5obz", "خبز", "حليب", "hlib", "7lib", "تقضية", "تقدية", "tqadya", "t9adya", "سوق", "مرجان", "بيم",
                "bim", "marjane", "carrefour", "أسيما", "خضرة", "لحم", "l7am", "دجاج", "حوت", "7out", "hout", "ماء", "ضو",
                "ضوء", "الكراء", "كراء", "ويفي", "wifi", "fibre", "inwi", "iam", "orange", "روشارج",
                "recharge", "lydec", "redal", "amendis", "amendys", "onee", "facture", "فاتورة", "دار", "maison", "courses"
            ),
            "charges",
            "Charges fixes & Maison"
        ),
        // Épargne & Investissement -> "epargne"
        Triple(
            listOf(
                "épargne", "epargne", "توفير", "ادخار", "دارت", "daret", "jam3iya", "قرعة", "ذهب", "استثمار", "savings"
            ),
            "epargne",
            "Épargne & Investissement"
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
        Regex("""\b3cha\b""", RegexOption.IGNORE_CASE) to "Acha",
        Regex("""\bt9adya\b""", RegexOption.IGNORE_CASE) to "Tqadya",
        Regex("""\b9raya\b""", RegexOption.IGNORE_CASE) to "Qraya",
        Regex("""\bjam3iya\b""", RegexOption.IGNORE_CASE) to "Jam3iya",
        Regex("""\b7anout\b""", RegexOption.IGNORE_CASE) to "Hanout"
    )

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

        // 1. Extract standalone numeric amount (digits not attached to letters first, then spoken Darija/Arabic words)
        val standaloneNumberRegex = Regex("""(?<!\p{L})(\d+(?:[.,]\d{1,2})?)(?!\p{L})""")
        val fallbackNumberRegex = Regex("""(\d+(?:[.,]\d{1,2})?)""")
        val match = standaloneNumberRegex.find(normalizedDigits) ?: fallbackNumberRegex.find(normalizedDigits)
        var extractedAmount = match?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        var cleanedText = if (match != null) {
            (normalizedDigits.substring(0, match.range.first) + " " + normalizedDigits.substring(match.range.last + 1)).trim()
        } else {
            normalizedDigits
        }

        if (extractedAmount <= 0.0) {
            for ((word, value) in SPOKEN_NUMBERS) {
                if (cleanedText.contains(word, ignoreCase = true)) {
                    extractedAmount = value
                    cleanedText = cleanedText.replaceFirst(word, "", ignoreCase = true).trim()
                    break
                }
            }
        }

        if (extractedAmount <= 0.0) return null

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

        // 3. Infer envelopeKey & localized category name
        var matchedKey = ""
        var fallbackLabel = ""
        for ((keywords, envKey, defaultLabel) in DARIJA_CATEGORY_MAP) {
            if (keywords.any { kw -> fullLower.contains(kw) }) {
                matchedKey = envKey
                fallbackLabel = defaultLabel
                break
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

    fun samplePhrasesForLanguage(lang: String): List<String> = when (lang) {
        "darija" -> listOf("9ahwa 15", "قهوة 15", "طاكسي 20", "تقضية 80", "طاكوس 35")
        "ar" -> listOf("قهوة 15", "طاكسي 20", "خبز وحليب 18", "غداء 45", "وقود 200")
        "en" -> listOf("9ahwa 15", "Coffee 15", "Taxi 20", "Groceries 85", "Train 60")
        else -> listOf("9ahwa 15", "Café 15", "Taxi 20", "Khobz & Lait 18", "ONCF 80")
    }
}
