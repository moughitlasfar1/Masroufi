package com.example.domain

import com.example.data.DefaultCategoryResolver
import com.example.data.EnvelopeConfig
import com.example.data.TransactionItem
import com.example.notifications.MasroufiDetectionLog
import com.masroufi.core.Money
import com.masroufi.core.MoneyParser
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class BankTemplate(
    val id: String,
    val bankName: String,
    val packageHints: List<String>,
    val senderKeywords: List<String>
)

data class ParsedBankNotification(
    val amount: Money,
    val currency: String,
    val direction: String, // "expense", "income", or "transfer"
    val merchant: String,
    val isoDate: String,
    val sourcePackage: String,
    val bankTemplateId: String,
    val confidence: Double,
    val needsReview: Boolean,
    val suggestedEnvelopeKey: String,
    val suggestedCategoryName: String,
    val dedupeHash: String,
    val maskedSamplePreview: String
)

sealed interface BankParseResult {
    data class Parsed(val notification: ParsedBankNotification) : BankParseResult
    data class Rejected(val reasonCode: String) : BankParseResult
}

internal data class AmountCandidate(
    val amount: Money,
    val currency: String,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * Phase 10C & Audit Fixes (D2, D3, D4, D7, D11):
 * Pure Kotlin on-device parser for bank and SMS transaction notifications.
 *
 * Privacy contract:
 * - Discards OTP / verification code messages before any parsing happens.
 * - Extracts only structured fields (amount, currency, direction, short merchant, date, dedupe hash).
 * - Never persists or logs raw notification text.
 */
object BankMessageParser {

    const val CONFIDENCE_REVIEW_THRESHOLD = 0.75

    private val ARABIC_INDIC_MAP = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9'
    )

    private val OTP_DISCARD_PHRASES = listOf(
        "code de vérification",
        "code de verification",
        "code de sécurité",
        "code de securite",
        "code secret",
        "code unique",
        "code d'authentification",
        "mot de passe à usage unique",
        "mot de passe a usage unique",
        "verification code",
        "security code",
        "one-time password",
        "one time password",
        "passcode",
        "validation code",
        "3ds code",
        "رمز التحقق",
        "رمز التأكيد",
        "الرمز السري",
        "كود التحقق",
        "كود التفعيل",
        "رمز الأمان",
        "كلمة السر المؤقتة"
    )

    private val OTP_WORD_REGEX = Regex("""(?<![\p{L}\d])otp(?![\p{L}\d])""", RegexOption.IGNORE_CASE)
    private val THREE_DS_REGEX = Regex("""(?<![\p{L}\d])(?:3d\s*secure|3ds)(?![\p{L}\d])""", RegexOption.IGNORE_CASE)
    private val STANDALONE_OTP_DIGITS_REGEX = Regex("""(?<![\p{L}\d])\d{4,8}(?![\p{L}\d])""")
    private val CODE_WORD_REGEX = Regex("""(?<![\p{L}\d])(?:code|pin|كود|رمز)(?![\p{L}\d])""", RegexOption.IGNORE_CASE)
    private val VALIDATION_VERB_REGEX = Regex(
        """(?<![\p{L}\d])(?:valider|confirmer|confirm|validate|authentifier|لتأكيد|للتحقق)(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE
    )
    private val OTP_DATE_MASK_REGEX = Regex("""\b(?:\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2})\b""")
    private val OTP_TIME_MASK_REGEX = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b""")
    private val OTP_CARD_SUFFIX_MASK_REGEX = Regex(
        """(?i)(?:\*{2,}\s*|x{2,}\s*|(?:carte|card)\s*(?:n°|num|no)?\s*[:#-]?\s*)\d{4}\b"""
    )
    private val OTP_AMOUNT_CURRENCY_MASK_REGEX = Regex(
        """(?i)(?<!\d)\d{1,3}(?:[ .,]\d{3})*(?:[.,]\d{1,2})?\s*(?:mad|dhs?|dirhams?|درهم|دراهم|د\.م\.?)(?![\p{L}\d])"""
    )

    private val BALANCE_OR_LIMIT_LABEL_REGEX = Regex(
        """(?<![\p{L}])(?:solde|balance|disponible|plafond|limite|frais|commission|الرصيد|رصيد|متاح|عمولة|مصاريف)(?![\p{L}])""",
        RegexOption.IGNORE_CASE
    )

    private val ATM_WITHDRAWAL_KEYWORDS = listOf(
        "retrait gab",
        "retrait guichet",
        "retrait dab",
        "retrait espèces",
        "retrait especes",
        "guichet automatique",
        "سحب من الشباك",
        "سحب نقدي",
        "الشباك الأوتوماتيكي",
        "atm withdrawal"
    )

    private val DEBIT_KEYWORDS = listOf(
        // French
        "paiement tpe", "paiement par carte", "paiement e-commerce", "paiement ecommerce",
        "paiement sans contact", "achat carte", "achat tpe", "achat en ligne",
        "virement émis", "virement emis", "virement effectué", "virement effectue",
        "opération carte", "operation carte", "retrait carte", "facture payée", "facture payee",
        "règlement", "reglement", "transaction", "autorisation", "opération", "operation",
        "paiement", "achat", "retrait", "prélèvement", "prelevement",
        "prélevé", "preleve", "prélevée", "prelevee",
        "débité", "debite", "débitée", "debitee", "débit", "debit",
        "payé", "paye", "facture", "tpe", "carte bancaire", "e-commerce",
        // English
        "payment", "purchase", "withdrawal", "debited", "spent", "charged", "pos purchase",
        // Arabic
        "تم خصم", "تم اقتطاع", "تم سحب", "عملية شراء", "عملية دفع", "عملية أداء", "عملية سحب",
        "أداء بالبطاقة", "خصم", "أداء", "شراء", "سحب", "دفعة", "دفع", "اقتطاع", "تحويل صادر"
    )

    private val CREDIT_KEYWORDS = listOf(
        // French
        "vous avez reçu un virement", "vous avez recu un virement",
        "virement reçu", "virement recu", "virement en votre faveur",
        "remboursement", "crédité", "credite", "créditée", "creditee",
        "crédit", "credit", "dépôt", "depot", "versement", "salaire", "encaissement",
        // English
        "transfer received", "deposit", "credited", "refund", "incoming transfer", "salary",
        // Arabic
        "تحويل وارد", "إيداع", "توصلتم", "تحويل لصالحكم", "تم تحويل لحسابكم", "تم إيداع", "استلام تحويل", "استرجاع"
    )

    private val REFUND_KEYWORDS = listOf("remboursement", "refund", "استرجاع")

    private val INVALID_MERCHANT_EXACT = setOf(
        "carte", "card", "bancaire", "compte", "account", "paiement", "achat",
        "retrait", "virement", "transaction", "operation", "opération", "gab", "dab", "tpe"
    )

    // D11: Full package prefixes & whole-word sender keywords (no bare 2-3 letter substring collisions)
    val BANK_TEMPLATES: List<BankTemplate> = listOf(
        BankTemplate(
            id = "cih",
            bankName = "CIH Bank",
            packageHints = listOf("com.b3g.cih.online", "com.b3g.cih", "com.cih.cihmobile", "ma.cih"),
            senderKeywords = listOf("cih bank", "cih mobile", "cih")
        ),
        BankTemplate(
            id = "attijari",
            bankName = "Attijariwafa Bank",
            packageHints = listOf(
                "com.attijariwafabank.attijarimobile",
                "com.attijariwafabank",
                "ma.attijariwafabank",
                "com.attijari",
                "ma.wafacash"
            ),
            senderKeywords = listOf("attijariwafa", "attijari mobile", "attijari", "lbankalik", "l'bankalik", "wafacash")
        ),
        BankTemplate(
            id = "bp",
            bankName = "Banque Populaire",
            packageHints = listOf("ma.gbp.pocketbank", "ma.gbp", "com.bcp.pocketbank", "ma.banquepopulaire", "com.gbp"),
            senderKeywords = listOf("pocket bank", "banque populaire", "chaabi net", "a.b.c.p", "chaabi", "bcp")
        ),
        BankTemplate(
            id = "boa",
            bankName = "Bank of Africa / BMCE",
            packageHints = listOf("com.bmce.bmcedirect", "ma.bankofafrica", "com.bmce.agencedirecte", "com.bmce"),
            senderKeywords = listOf("bank of africa", "bmce direct", "bmce")
        ),
        BankTemplate(
            id = "cfg",
            bankName = "CFG Bank",
            packageHints = listOf("com.cfgbank.app", "com.cfgbank.mobile", "ma.cfgbank"),
            senderKeywords = listOf("cfg bank")
        ),
        BankTemplate(
            id = "barid",
            bankName = "Al Barid Bank",
            packageHints = listOf("ma.abb.baridbankmobile", "ma.abb", "ma.baridbank.mobile", "ma.baridbank", "com.baridbank"),
            senderKeywords = listOf("barid bank", "al barid", "بريد بنك")
        ),
        BankTemplate(
            id = "cdm",
            bankName = "Crédit du Maroc",
            packageHints = listOf("ma.cdm.mycdm", "ma.cdm", "com.cdm"),
            senderKeywords = listOf("crédit du maroc", "credit du maroc", "mycdm", "cdm")
        ),
        BankTemplate(
            id = "sgma",
            bankName = "Société Générale Maroc / Saham Bank",
            packageHints = listOf("com.sgma.sgmaroc", "com.sgma", "ma.sgmaroc", "com.socgen", "ma.sahambank"),
            senderKeywords = listOf("société générale", "societe generale", "sg maroc", "sgma", "saham bank")
        ),
        BankTemplate(
            id = "generic",
            bankName = "Generic Bank / SMS",
            packageHints = emptyList(),
            senderKeywords = emptyList()
        )
    )

    private fun wholeWordRegex(keyword: String): Regex {
        val escaped = Regex.escape(keyword)
        return Regex("""(?<![\p{L}\d])$escaped(?![\p{L}\d])""", RegexOption.IGNORE_CASE)
    }

    private val debitKeywordRegexes: List<Pair<String, Regex>> = DEBIT_KEYWORDS.map { it to wholeWordRegex(it) }
    private val creditKeywordRegexes: List<Pair<String, Regex>> = CREDIT_KEYWORDS.map { it to wholeWordRegex(it) }
    private val atmKeywordRegexes: List<Pair<String, Regex>> = (ATM_WITHDRAWAL_KEYWORDS + "gab").map { it to wholeWordRegex(it) }
    private val refundKeywordRegexes: List<Regex> = REFUND_KEYWORDS.map { wholeWordRegex(it) }

    /**
     * Step 1: Normalize Arabic-Indic digits, non-breaking spaces, and whitespace.
     */
    fun normalizeText(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                '\u00A0', '\u202F', '\u2007', '\u2009' -> sb.append(' ')
                else -> sb.append(ARABIC_INDIC_MAP[ch] ?: ch)
            }
        }
        return sb.toString().replace(Regex("""[ \t]+"""), " ").trim()
    }

    /**
     * Step 2 (D7): Codes first — returns true if the message looks like an OTP, 3DS verification code,
     * or login/validation code.
     * - Matches `otp` on whole words only.
     * - `3d secure` / `3ds` discards ONLY when a standalone 4-8 digit code is also present.
     * - A standalone 4-8 digit code paired with validation verbs (`valider`, `confirmer`, `confirm`, `تأكيد`)
     *   or `code`/`pin`/`رمز` is discarded even when a currency amount is mentioned.
     */
    fun isOtpOrSecurityCodeMessage(raw: String): Boolean {
        val norm = normalizeText(raw).lowercase(Locale.ROOT)
        if (norm.isBlank()) return false

        if (OTP_WORD_REGEX.containsMatchIn(norm)) return true
        if (OTP_DISCARD_PHRASES.any { phrase -> norm.contains(phrase) }) return true

        // Mask dates (e.g. 26/11/2026), times, masked card suffixes (****4582 / carte 4582),
        // and currency amounts (e.g. 1500 MAD) so they are never mistaken for 4-8 digit OTP codes.
        val otpScanText = norm
            .let { OTP_DATE_MASK_REGEX.replace(it, " ") }
            .let { OTP_TIME_MASK_REGEX.replace(it, " ") }
            .let { OTP_CARD_SUFFIX_MASK_REGEX.replace(it, " ") }
            .let { OTP_AMOUNT_CURRENCY_MASK_REGEX.replace(it, " ") }

        val hasStandaloneOtpDigits = STANDALONE_OTP_DIGITS_REGEX.containsMatchIn(otpScanText)
        if (hasStandaloneOtpDigits && THREE_DS_REGEX.containsMatchIn(norm)) {
            return true
        }

        val hasCodeWord = CODE_WORD_REGEX.containsMatchIn(norm)
        val hasValidationVerb = VALIDATION_VERB_REGEX.containsMatchIn(norm)
        if (hasStandaloneOtpDigits && hasValidationVerb) {
            return true
        }
        val hasCurrency = Regex(
            """(?<![\p{L}])(?:mad|dhs?|dirhams?|درهم|دراهم|د\.م\.?)(?![\p{L}\d])""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(norm)
        if (hasCodeWord && hasStandaloneOtpDigits && !hasCurrency) {
            return true
        }
        return false
    }

    /**
     * Masks digits in a message so the user can safely copy and share a sample template without
     * exposing card numbers, account numbers, or exact amounts.
     */
    fun maskSampleText(raw: String): String {
        val norm = normalizeText(raw)
        return norm.replace(Regex("""\d"""), "X").take(180)
    }

    private const val MAX_BANK_NOTIFICATION_CENTIMES: Long = 1_000_000_000L // 10,000,000.00 DH
    private val SINGLE_DOT_THOUSANDS_REGEX = Regex("""^\d{1,3}\.\d{3}$""")

    /**
     * Step 4: Parses formatted number strings such as "1 234,50", "1.234,50", "1,234.50", "250,00", or "85"
     * strictly via [MoneyParser] (pure BigDecimal, zero Double).
     */
    fun parseNormalizedAmountToken(rawNumberToken: String): Money? {
        val compact = rawNumberToken.trim()
            .replace("\u00A0", " ")
            .replace("\u202F", " ")
            .replace(" ", "")
        if (compact.isBlank()) return null

        // Preserve Moroccan bank SMS thousands-dot convention for tokens like "1.250" (no comma, 3 digits after dot)
        val normalizedToken = if (SINGLE_DOT_THOUSANDS_REGEX.matches(compact) && !compact.startsWith("0")) {
            compact.replace(".", "")
        } else {
            compact
        }

        val centimes = MoneyParser.parseMoroccanAmountToCentimes(
            raw = normalizedToken,
            maxAbsCentimes = MAX_BANK_NOTIFICATION_CENTIMES
        ) ?: return null
        if (centimes <= 0L) return null
        return Money.fromCentimes(centimes)
    }

    /**
     * D2: Collects all amount+currency candidates (using whole-word currency boundaries),
     * drops candidates preceded within 25 characters by balance/limit/fee labels (`Solde`, `Disponible`,
     * `Plafond`, `Frais`, `الرصيد`, etc.), and picks the candidate closest to a transaction keyword.
     */
    internal fun extractBestAmountCandidate(
        normalizedText: String,
        keywordPositions: List<Int> = emptyList()
    ): AmountCandidate? {
        val numPattern = """(\d{1,3}(?:[ .,]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)"""
        val afterCurPattern = """(?<![\p{L}])(MAD|DHS?|Dirhams?|درهم|دراهم|د\.م\.?)(?![\p{L}\d])"""
        val beforeCurPattern = """(?<![\p{L}\d])(MAD|DHS?|Dirhams?|درهم|دراهم|د\.م\.?)(?![\p{L}])"""

        val candidates = mutableListOf<AmountCandidate>()

        val afterRegex = Regex("""(?<!\d)$numPattern\s*$afterCurPattern""", RegexOption.IGNORE_CASE)
        for (match in afterRegex.findAll(normalizedText)) {
            val amt = parseNormalizedAmountToken(match.groupValues[1]) ?: continue
            if (amt.isPositive) {
                candidates.add(
                    AmountCandidate(
                        amount = amt,
                        currency = normalizeCurrencyLabel(match.groupValues[2]),
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1
                    )
                )
            }
        }

        val beforeRegex = Regex("""$beforeCurPattern\s*:?\s*$numPattern(?!\d)""", RegexOption.IGNORE_CASE)
        for (match in beforeRegex.findAll(normalizedText)) {
            val amt = parseNormalizedAmountToken(match.groupValues[2]) ?: continue
            if (amt.isPositive) {
                val alreadyCaptured = candidates.any { abs(it.startIndex - match.range.first) <= 2 }
                if (!alreadyCaptured) {
                    candidates.add(
                        AmountCandidate(
                            amount = amt,
                            currency = normalizeCurrencyLabel(match.groupValues[1]),
                            startIndex = match.range.first,
                            endIndex = match.range.last + 1
                        )
                    )
                }
            }
        }

        if (candidates.isEmpty()) return null

        val nonBalanceCandidates = candidates.filter { cand ->
            val windowStart = (cand.startIndex - 65).coerceAtLeast(0)
            val rawPrefix = normalizedText.substring(windowStart, cand.startIndex)
            // Stop lookbehind at sentence/clause boundaries (newline, semicolon, period+space, or dash separator)
            val boundaryIdx = Regex("""[\n;]|\.\s+|\s+-\s+""").findAll(rawPrefix).lastOrNull()?.range?.last
            val clausePrefix = if (boundaryIdx != null) rawPrefix.substring(boundaryIdx + 1) else rawPrefix
            val lastBalanceMatch = BALANCE_OR_LIMIT_LABEL_REGEX.findAll(clausePrefix).lastOrNull()
            if (lastBalanceMatch == null) {
                true
            } else {
                // If a transaction keyword appears AFTER the balance label within the same clause, keep it
                val afterBalance = clausePrefix.substring(lastBalanceMatch.range.last + 1)
                debitKeywordRegexes.any { (_, rx) -> rx.containsMatchIn(afterBalance) } ||
                    creditKeywordRegexes.any { (_, rx) -> rx.containsMatchIn(afterBalance) } ||
                    atmKeywordRegexes.any { (_, rx) -> rx.containsMatchIn(afterBalance) }
            }
        }

        if (nonBalanceCandidates.isEmpty()) {
            // Every amount in the message is a balance/limit/fee notification, not a transaction
            return null
        }

        if (nonBalanceCandidates.size == 1 || keywordPositions.isEmpty()) {
            return nonBalanceCandidates.minByOrNull { it.startIndex }
        }

        return nonBalanceCandidates.minByOrNull { cand ->
            keywordPositions.minOf { kwPos -> abs(cand.startIndex - kwPos) }
        }
    }

    /**
     * Public helper: extracts (amount, normalizedCurrency), excluding balance/limit/fee amounts.
     */
    fun extractAmountAndCurrency(normalizedText: String): Pair<Money, String>? {
        val kwPositions = collectKeywordPositions(normalizedText)
        val best = extractBestAmountCandidate(normalizedText, kwPositions) ?: return null
        return best.amount to best.currency
    }

    private fun collectKeywordPositions(normalizedText: String): List<Int> {
        val positions = mutableListOf<Int>()
        for ((_, regex) in debitKeywordRegexes) {
            regex.findAll(normalizedText).forEach { positions.add(it.range.first) }
        }
        for ((_, regex) in creditKeywordRegexes) {
            regex.findAll(normalizedText).forEach { positions.add(it.range.first) }
        }
        for ((_, regex) in atmKeywordRegexes) {
            regex.findAll(normalizedText).forEach { positions.add(it.range.first) }
        }
        return positions
    }

    private fun normalizeCurrencyLabel(rawCur: String): String {
        val upper = rawCur.trim().uppercase(Locale.ROOT)
        return when {
            upper == "MAD" -> "MAD"
            else -> "DH"
        }
    }

    /**
     * Step 5 (D3): Extracts short merchant name using word-boundary prefixes (`chez`, `auprès de`,
     * `at`, `à`, `sur`, `لدى`, `عند`, `من طرف`) or labeled fields (`Commerçant:`, `Marchand:`, `Merchant:`).
     * Rejects candidates that are digits-only or generic words like `carte` / `card`.
     */
    fun extractMerchant(normalizedText: String): String {
        val stopLookahead =
            """(?=[.,;\n]|\s+(?:le\s+\d|du\s+\d|on\s+\d|في\s+\d|بتاريخ|carte\b|card\b|solde\b|balance\b|disponible\b|الرصيد|رصيد|\d{2}[/-]\d{2}|\d{2}:\d{2})|$)"""

        // 1. Labeled merchant fields ("Commerçant: MARJANE", "Merchant: CARREFOUR")
        val labeledRegex = Regex(
            """(?<![\p{L}])(?:commerçant|commercant|marchand|merchant)\s*[:\-]\s*([^\n.,;]+?)$stopLookahead""",
            RegexOption.IGNORE_CASE
        )
        labeledRegex.find(normalizedText)?.let { match ->
            val candidate = cleanMerchantCandidate(match.groupValues[1])
            if (isValidMerchantCandidate(candidate)) return candidate
        }

        // 2. Primary preposition prefixes with strict word boundaries (prevents "at" matching inside "Achat")
        val primaryRegex = Regex(
            """(?<![\p{L}])(?:chez|auprès\s+de|aupres\s+de|at|sur|لدى|عند|من\s+طرف)(?![\p{L}])\s+([^\n.,;]+?)$stopLookahead""",
            RegexOption.IGNORE_CASE
        )
        for (match in primaryRegex.findAll(normalizedText)) {
            val candidate = cleanMerchantCandidate(match.groupValues[1])
            if (isValidMerchantCandidate(candidate)) return candidate
        }

        // 3. Secondary French "à <MERCHANT>" when preceded by paiement/achat/retrait
        val secondaryRegex = Regex(
            """(?<![\p{L}])(?:paiement|achat|retrait|règlement|reglement)(?![\p{L}])\s+.*?(?<![\p{L}])à(?![\p{L}])\s+([^\n.,;]+?)$stopLookahead""",
            RegexOption.IGNORE_CASE
        )
        secondaryRegex.find(normalizedText)?.let { match ->
            val candidate = cleanMerchantCandidate(match.groupValues[1])
            if (isValidMerchantCandidate(candidate)) return candidate
        }

        return ""
    }

    private fun cleanMerchantCandidate(raw: String): String {
        return raw
            .replace(Regex("""(?<![\p{L}])(?:MAD|DHS?|Dirhams?|درهم|دراهم|د\.م\.?)(?![\p{L}\d])""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\*+\d{2,4}"""), "")
            .replace(Regex("""\b\d+(?:[.,]\d{1,2})?\b"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', ':', '*', '"', '\'', '.', ',')
            .take(48)
    }

    private fun isValidMerchantCandidate(candidate: String): Boolean {
        if (candidate.length < 2) return false
        val lower = candidate.lowercase(Locale.ROOT)
        if (lower in INVALID_MERCHANT_EXACT) return false
        // Reject if candidate has no letters (digits/symbols only)
        if (!candidate.any { it.isLetter() }) return false
        // Reject if starts with "carte " or "card " (e.g. "carte bancaire")
        if (lower.startsWith("carte ") || lower.startsWith("card ") || lower.startsWith("votre ")) return false
        return true
    }

    internal fun isoDateFromMillis(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = timeZone
        return sdf.format(Date(millis))
    }

    /**
     * Full parsing pipeline returning either [BankParseResult.Parsed] or [BankParseResult.Rejected]
     * with a structured reason code for [MasroufiDetectionLog].
     */
    fun parseDetailed(
        rawTitle: String,
        rawBody: String,
        sourcePackage: String = "generic",
        notificationKey: String = "",
        isoDate: String? = null,
        postTimeMillis: Long? = null,
        envelopes: List<EnvelopeConfig> = emptyList(),
        language: String = "fr"
    ): BankParseResult {
        val combinedRaw = "$rawTitle $rawBody".trim()
        if (combinedRaw.isBlank()) {
            return BankParseResult.Rejected(MasroufiDetectionLog.REASON_EMPTY)
        }

        // 1. Codes first: drop OTP / verification codes immediately
        if (isOtpOrSecurityCodeMessage(combinedRaw)) {
            return BankParseResult.Rejected(MasroufiDetectionLog.REASON_OTP)
        }

        val normalized = normalizeText(combinedRaw)
        val lower = normalized.lowercase(Locale.ROOT)

        // 2. Collect keyword positions for direction & closest-amount disambiguation (D2 & D4)
        val debitMatches = debitKeywordRegexes.flatMap { (_, rx) -> rx.findAll(lower).map { it.range.first }.toList() }
        val creditMatches = creditKeywordRegexes.flatMap { (_, rx) -> rx.findAll(lower).map { it.range.first }.toList() }
        val atmMatches = atmKeywordRegexes.flatMap { (_, rx) -> rx.findAll(lower).map { it.range.first }.toList() }
        val allKwPositions = debitMatches + creditMatches + atmMatches

        // 3. Amount + Currency extraction (ignoring Solde / Balance / Plafond / Frais)
        val bestAmount = extractBestAmountCandidate(normalized, allKwPositions)
            ?: return BankParseResult.Rejected(MasroufiDetectionLog.REASON_NO_AMOUNT)
        val amount = bestAmount.amount
        val currency = bestAmount.currency

        val hasCredit = creditMatches.isNotEmpty()
        val isAtmWithdrawal = atmMatches.isNotEmpty() && !refundKeywordRegexes.any { it.containsMatchIn(lower) }
        val hasDebit = debitMatches.isNotEmpty() || isAtmWithdrawal

        if (!hasCredit && !hasDebit) {
            return BankParseResult.Rejected(MasroufiDetectionLog.REASON_NO_KEYWORD)
        }

        // D4: When both credit and debit keywords appear, choose the one nearest to the amount
        val direction = when {
            isAtmWithdrawal -> "transfer"
            refundKeywordRegexes.any { it.containsMatchIn(lower) } -> "income"
            hasCredit && !hasDebit -> "income"
            hasDebit && !hasCredit -> "expense"
            else -> {
                val nearestCreditDist = creditMatches.minOf { abs(it - bestAmount.startIndex) }
                val nearestDebitDist = debitMatches.minOf { abs(it - bestAmount.startIndex) }
                if (nearestCreditDist <= nearestDebitDist) "income" else "expense"
            }
        }

        // 4. D11: Match BankTemplate using exact/prefix package or whole-word sender keyword
        val pkgLower = sourcePackage.lowercase(Locale.ROOT)
        val matchedTemplate = BANK_TEMPLATES.firstOrNull { tpl ->
            tpl.id != "generic" && (
                tpl.packageHints.any { hint -> pkgLower == hint || pkgLower.startsWith("$hint.") } ||
                    tpl.senderKeywords.any { kw -> wholeWordRegex(kw).containsMatchIn(lower) }
                )
        } ?: BANK_TEMPLATES.last()

        // 5. D3: Extract Merchant (leave empty and set needsReview=true if missing; never substitute bank name)
        val extractedMerchant = if (isAtmWithdrawal) "" else extractMerchant(normalized)
        val finalMerchant = if (isAtmWithdrawal) "Retrait GAB" else extractedMerchant

        // 6. Confidence score calculation
        var confidence = 0.55
        if (hasDebit || hasCredit) confidence += 0.15
        if (extractedMerchant.isNotBlank() || isAtmWithdrawal) confidence += 0.18
        if (matchedTemplate.id != "generic") confidence += 0.10
        val finalConfidence = confidence.coerceIn(0.0, 0.99)
        val needsReview = !isAtmWithdrawal && (finalConfidence < CONFIDENCE_REVIEW_THRESHOLD || extractedMerchant.isBlank())

        // 7. Category resolution
        val catLookupText = finalMerchant.ifBlank {
            when (direction) {
                "income" -> if (language == "en") "Bank Transfer" else "Virement bancaire"
                else -> if (language == "en") "Card Payment" else "Paiement carte"
            }
        }
        val resolvedEnvKey = if (isAtmWithdrawal) {
            "autre"
        } else {
            val learnedEnv = if (finalMerchant.isNotBlank()) {
                PayCycleEngine.getLearnedCategoryForDescription(finalMerchant).orEmpty()
            } else ""
            val voiceGuess = if (learnedEnv.isBlank() && finalMerchant.isNotBlank()) {
                VoiceDarijaParser.parse("$finalMerchant ${amount.toCanonicalDecimalString()}", envelopes, language)?.envelopeKey.orEmpty()
            } else ""
            when {
                learnedEnv.isNotBlank() -> learnedEnv
                voiceGuess.isNotBlank() -> voiceGuess
                else -> PayCycleEngine.resolveEnvelopeKey(catLookupText, catLookupText, envelopes)
            }.ifBlank { "autre" }
        }

        val resolvedCategoryName = if (isAtmWithdrawal) {
            Translations.forLang(language).transferAction
        } else {
            DefaultCategoryResolver.resolveTransactionCategoryDisplay(
                rawCategory = resolvedEnvKey,
                rawEnvelopeKey = resolvedEnvKey,
                envelopes = envelopes,
                lang = language
            )
        }

        // Derive isoDate from postTimeMillis in device timezone when provided
        val effectiveIsoDate = when {
            postTimeMillis != null && postTimeMillis > 0L -> isoDateFromMillis(postTimeMillis)
            !isoDate.isNullOrBlank() -> isoDate
            else -> PayCycleEngine.todayIso()
        }

        // 8. D1: Deterministic dedupe hash WITHOUT notificationKey (which never changes in SMS threads!)
        // Uses effectiveIsoDate + full normalized content digest so re-posted identical notifications
        // are always caught even across minute boundaries while distinct thread messages never collide.
        val contentDigest = sha256Short(lower)
        val dedupeSeed = "${pkgLower}|$direction|${amount.toCanonicalDecimalString()}|${finalMerchant.lowercase(Locale.ROOT)}|$effectiveIsoDate|$contentDigest"
        val dedupeHash = sha256Short(dedupeSeed)

        return BankParseResult.Parsed(
            ParsedBankNotification(
                amount = amount,
                currency = currency,
                direction = direction,
                merchant = finalMerchant,
                isoDate = effectiveIsoDate,
                sourcePackage = sourcePackage,
                bankTemplateId = matchedTemplate.id,
                confidence = finalConfidence,
                needsReview = needsReview,
                suggestedEnvelopeKey = resolvedEnvKey,
                suggestedCategoryName = resolvedCategoryName,
                dedupeHash = dedupeHash,
                maskedSamplePreview = maskSampleText(combinedRaw)
            )
        )
    }

    /**
     * Backwards-compatible parse API returning [ParsedBankNotification] or null.
     */
    fun parse(
        rawTitle: String,
        rawBody: String,
        sourcePackage: String = "generic",
        notificationKey: String = "",
        isoDate: String = PayCycleEngine.todayIso(),
        envelopes: List<EnvelopeConfig> = emptyList(),
        language: String = "fr",
        postTimeMillis: Long? = null
    ): ParsedBankNotification? {
        return when (
            val res = parseDetailed(
                rawTitle = rawTitle,
                rawBody = rawBody,
                sourcePackage = sourcePackage,
                notificationKey = notificationKey,
                isoDate = isoDate,
                postTimeMillis = postTimeMillis,
                envelopes = envelopes,
                language = language
            )
        ) {
            is BankParseResult.Parsed -> res.notification
            is BankParseResult.Rejected -> null
        }
    }

    /**
     * Step 9: Checks if a manual or voice transaction of the same amount & direction was logged recently
     * (either on the same ISO date or within ±10 minutes of `createdAt`).
     */
    fun findRecentManualMatch(
        amount: Money,
        direction: String,
        isoDate: String,
        allTransactions: List<TransactionItem>,
        nowMs: Long = System.currentTimeMillis()
    ): TransactionItem? {
        val tenMinutesMs = 10 * 60 * 1000L
        return allTransactions.firstOrNull { tx ->
            tx.type == direction &&
                tx.amount == amount &&
                (
                    (tx.createdAtEpochMs > 0L && abs(nowMs - tx.createdAtEpochMs) <= tenMinutesMs) ||
                        (tx.createdAtEpochMs <= 0L && tx.isoDate == isoDate)
                    )
        }
    }

    private fun sha256Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
