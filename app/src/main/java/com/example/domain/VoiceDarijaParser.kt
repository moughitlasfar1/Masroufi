package com.example.domain

import com.example.data.EnvelopeConfig
import com.example.ui.DashboardState
import java.util.Locale

data class VoiceParsedTransaction(
    val rawTranscript: String,
    val amount: Double,
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
        val totalAmount: Double get() = PayCycleEngine.roundMoney(items.sumOf { it.amount })
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
                "tacos", "طاكوس", "طاكوص", "pizza", "بيتزا", "sandwich", "سندويش", "فطور", "غداء", "غدا",
                "gheda", "ghda", "lgheda", "déjeuner", "dejeuner", "lunch",
                "عشاء", "عشا", "echa", "3cha", "acha", "dîner", "diner", "dinner",
                "فتور", "ftour", "breakfast", "resto", "restaurant", "مسمن", "حرشة", "عصير", "jus",
                "snack", "garo", "chawarma", "شاورما", "burger", "glace", "pâtisserie", "patisserie"
            ),
            "cafe",
            "Café & Sorties"
        ),
        // Transport -> "transport"
        Triple(
            listOf(
                "taxi", "طاكسي", "تاكسي", "petit taxi", "grand taxi", "oncf", "تران", "قطار", "train",
                "tram", "طرام", "bus", "طوبيس", "وطوبيس", "مازوط", "ليسنس", "essence", "gasoil",
                "carburant", "autoroute", "peage", "péage", "نقل", "transport", "indrive", "careem",
                "uber", "parking", "باركينغ", "buraq", "al boraq"
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
                "gaz", "بوطا", "boutagaz", "bota"
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

        val roundedAmount = PayCycleEngine.roundMoney(extractedAmount)
        if (roundedAmount <= 0.0) return null

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
            amount = roundedAmount,
            description = cleanedDesc,
            envelopeKey = finalEnvKey,
            categoryName = finalCategoryName,
            txType = txType
        )
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
        // ",", ";", "+", "&", " w ", " et ", " and ", " puis ", " ثم ", " من بعد ", or Arabic " و" when separating clauses with numbers
        val delimiterRegex = Regex(
            """(?:\s*[,;+&،]\s*|\s+(?:w|et|and|puis|then|ثم|من بعد)\s+|\s+و(?=\s*\p{L}+\s+\d|\p{L}+\s+\d))""",
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
        // e.g. "qahwa 15 taxi 20 khobz 5"
        val itemPattern = Regex("""([^\d,;+&،]+?\s+\d+(?:[.,]\d{1,2})?(?:\s*(?:dh|dhs|mad|درهم|دراهم))?)""", RegexOption.IGNORE_CASE)
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
