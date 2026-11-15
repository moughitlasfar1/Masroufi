package com.example.domain

import com.example.data.DefaultCategoryResolver
import com.example.data.EnvelopeConfig
import com.example.data.TransactionItem
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

data class BankTemplate(
    val id: String,
    val bankName: String,
    val packageHints: List<String>,
    val senderKeywords: List<String>
)

data class ParsedBankNotification(
    val amount: Double,
    val currency: String,
    val direction: String, // "expense" or "income"
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

/**
 * Phase 10C: Pure Kotlin on-device parser for bank and SMS transaction notifications.
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

    private val OTP_DISCARD_KEYWORDS = listOf(
        "otp",
        "code de vérification",
        "code de verification",
        "code de sécurité",
        "code de securite",
        "code secret",
        "code unique",
        "code d'authentification",
        "mot de passe à usage unique",
        "verification code",
        "security code",
        "one-time password",
        "one time password",
        "passcode",
        "validation code",
        "3d secure",
        "3ds code",
        "رمز التحقق",
        "رمز التأكيد",
        "الرمز السري",
        "كود التحقق",
        "كود التفعيل",
        "رمز الأمان",
        "كلمة السر المؤقتة"
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
        "paiement tpe", "paiement e-commerce", "paiement ecommerce", "achat carte", "achat tpe",
        "paiement", "achat", "retrait", "prélèvement", "prelevement",
        "débit", "debit", "payé", "paye", "facture", "tpe", "carte bancaire", "e-commerce",
        // English
        "payment", "purchase", "withdrawal", "debited", "spent", "charged", "pos purchase",
        // Arabic
        "خصم", "أداء", "شراء", "سحب", "دفعة", "اقتطاع", "عملية أداء", "عملية سحب"
    )

    private val CREDIT_KEYWORDS = listOf(
        // French
        "virement reçu", "virement recu", "virement en votre faveur",
        "crédit", "credit", "dépôt", "depot", "versement", "remboursement", "crédité", "credite",
        // English
        "transfer received", "deposit", "credited", "refund", "incoming transfer", "salary",
        // Arabic
        "تحويل وارد", "إيداع", "توصلتم", "تحويل لصالحكم", "تم إيداع", "استلام تحويل"
    )

    val BANK_TEMPLATES: List<BankTemplate> = listOf(
        BankTemplate(
            id = "cih",
            bankName = "CIH Bank",
            packageHints = listOf("cih", "com.cih.cihmobile"),
            senderKeywords = listOf("cih", "cih bank", "cih mobile")
        ),
        BankTemplate(
            id = "attijari",
            bankName = "Attijariwafa Bank",
            packageHints = listOf("attijari", "awb", "wafacash", "lbankalik"),
            senderKeywords = listOf("attijari", "attijariwafa", "lbankalik", "l'bankalik", "attijari mobile")
        ),
        BankTemplate(
            id = "bp",
            bankName = "Banque Populaire",
            packageHints = listOf("gbp", "pocketbank", "banquepopulaire", "bcp", "chaabi"),
            senderKeywords = listOf("pocket bank", "banque populaire", "chaabi net", "chaabi", "bcp", "bp")
        ),
        BankTemplate(
            id = "boa",
            bankName = "Bank of Africa / BMCE",
            packageHints = listOf("bmce", "bankofafrica", "agencedirecte"),
            senderKeywords = listOf("bmce", "bmce direct", "bank of africa", "boa")
        ),
        BankTemplate(
            id = "cfg",
            bankName = "CFG Bank",
            packageHints = listOf("cfg", "cfgbank"),
            senderKeywords = listOf("cfg bank", "cfg")
        ),
        BankTemplate(
            id = "barid",
            bankName = "Al Barid Bank",
            packageHints = listOf("barid", "baridbank"),
            senderKeywords = listOf("barid bank", "al barid", "بريد بنك")
        ),
        BankTemplate(
            id = "generic",
            bankName = "Generic Bank / SMS",
            packageHints = emptyList(),
            senderKeywords = emptyList()
        )
    )

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
     * Step 2: Codes first — returns true if the message looks like an OTP, 3DS, or login verification code.
     */
    fun isOtpOrSecurityCodeMessage(raw: String): Boolean {
        val norm = normalizeText(raw).lowercase(Locale.ROOT)
        if (norm.isBlank()) return false
        if (OTP_DISCARD_KEYWORDS.any { kw -> norm.contains(kw) }) {
            return true
        }
        // Also discard short messages containing a 4-8 digit standalone code with "code" or "رمز"
        val hasCodeWord = Regex("""\b(code|pin|كود|رمز)\b""", RegexOption.IGNORE_CASE).containsMatchIn(norm)
        val hasStandaloneOtpDigits = Regex("""\b\d{4,8}\b""").containsMatchIn(norm)
        val hasCurrency = Regex("""\b(mad|dhs?|dirhams?)\b|درهم|دراهم|د\.م""", RegexOption.IGNORE_CASE).containsMatchIn(norm)
        return hasCodeWord && hasStandaloneOtpDigits && !hasCurrency
    }

    /**
     * Masks digits in a message so the user can safely copy and share a sample template without
     * exposing card numbers, account numbers, or exact amounts.
     */
    fun maskSampleText(raw: String): String {
        val norm = normalizeText(raw)
        return norm.replace(Regex("""\d"""), "X").take(180)
    }

    /**
     * Step 4: Parses formatted number strings such as "1 234,50", "1.234,50", "1,234.50", "250,00", or "85".
     */
    fun parseNormalizedAmountToken(rawNumberToken: String): Double? {
        var cleaned = rawNumberToken.trim()
            .replace("\u00A0", " ")
            .replace("\u202F", " ")
            .replace(" ", "")
        if (cleaned.isBlank()) return null

        val lastComma = cleaned.lastIndexOf(',')
        val lastDot = cleaned.lastIndexOf('.')

        cleaned = when {
            lastComma > lastDot && lastDot != -1 -> {
                // Format like "1.234,50" -> "." is thousands, "," is decimal
                cleaned.replace(".", "").replace(",", ".")
            }
            lastDot > lastComma && lastComma != -1 -> {
                // Format like "1,234.50" -> "," is thousands, "." is decimal
                cleaned.replace(",", "")
            }
            lastComma != -1 -> {
                // Only commas present: if 1 or 2 digits after last comma (e.g. "1234,50"), treat as decimal;
                // if 3 digits after comma (e.g. "1,200"), treat as thousands separator
                val digitsAfter = cleaned.length - lastComma - 1
                if (digitsAfter in 1..2) {
                    cleaned.replace(",", ".")
                } else {
                    cleaned.replace(",", "")
                }
            }
            lastDot != -1 -> {
                val digitsAfter = cleaned.length - lastDot - 1
                val dotCount = cleaned.count { it == '.' }
                if (dotCount > 1 || digitsAfter == 3) {
                    cleaned.replace(".", "")
                } else {
                    cleaned
                }
            }
            else -> cleaned
        }

        val value = cleaned.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value <= 0.0 || value > 10_000_000.0) return null
        return PayCycleEngine.roundMoney(value)
    }

    /**
     * Extracts (amount, normalizedCurrency) when a number appears next to MAD, DH, Dhs, Dirhams, or درهم.
     */
    fun extractAmountAndCurrency(normalizedText: String): Pair<Double, String>? {
        val numPattern = """(\d{1,3}(?:[ .,]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)"""
        val curPattern = """(MAD|DHS?|Dirhams?|درهم|دراهم|د\.م\.?)"""

        // Pattern A: Number followed by currency (e.g., "1 234,50 MAD", "150 درهم")
        val afterRegex = Regex("""$numPattern\s*$curPattern""", RegexOption.IGNORE_CASE)
        for (match in afterRegex.findAll(normalizedText)) {
            val amt = parseNormalizedAmountToken(match.groupValues[1])
            if (amt != null && amt > 0.0) {
                return amt to normalizeCurrencyLabel(match.groupValues[2])
            }
        }

        // Pattern B: Currency followed by number (e.g., "MAD 1,234.50", "DH 85.00")
        val beforeRegex = Regex("""$curPattern\s*:?\s*$numPattern""", RegexOption.IGNORE_CASE)
        for (match in beforeRegex.findAll(normalizedText)) {
            val amt = parseNormalizedAmountToken(match.groupValues[2])
            if (amt != null && amt > 0.0) {
                return amt to normalizeCurrencyLabel(match.groupValues[1])
            }
        }

        return null
    }

    private fun normalizeCurrencyLabel(rawCur: String): String {
        val upper = rawCur.trim().uppercase(Locale.ROOT)
        return when {
            upper == "MAD" -> "MAD"
            else -> "DH"
        }
    }

    /**
     * Step 5: Extracts the short merchant name after "chez", "auprès de", "à", "at", "لدى", "عند",
     * trimming at date/time markers or card suffixes.
     */
    fun extractMerchant(normalizedText: String): String {
        val merchantRegex = Regex(
            """(?:chez|auprès\s+de|aupres\s+de|at|لدى|عند|من\s+طرف)\s+([^\n.,;]+?)(?=\s+(?:le\s+\d|du\s+\d|on\s+\d|في\s+\d|بتاريخ|carte\b|card\b|solde\b|balance\b|\d{2}[/-]\d{2}|\d{2}:\d{2})|$)""",
            RegexOption.IGNORE_CASE
        )
        val primaryMatch = merchantRegex.find(normalizedText)
        if (primaryMatch != null) {
            val candidate = cleanMerchantCandidate(primaryMatch.groupValues[1])
            if (candidate.isNotBlank()) return candidate
        }

        // Secondary French "à <MERCHANT>" when preceded by paiement/achat/retrait
        val secondaryRegex = Regex(
            """(?:paiement|achat|retrait)\s+.*?\bà\s+([^\n.,;]+?)(?=\s+(?:le\s+\d|du\s+\d|carte\b|solde\b|\d{2}[/-]\d{2}|\d{2}:\d{2})|$)""",
            RegexOption.IGNORE_CASE
        )
        val secondaryMatch = secondaryRegex.find(normalizedText)
        if (secondaryMatch != null) {
            val candidate = cleanMerchantCandidate(secondaryMatch.groupValues[1])
            if (candidate.isNotBlank()) return candidate
        }

        return ""
    }

    private fun cleanMerchantCandidate(raw: String): String {
        return raw
            .replace(Regex("""\b(?:MAD|DHS?|Dirhams?|درهم|دراهم)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\*+\d{2,4}"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', ':', '*', '"', '\'')
            .take(48)
    }

    /**
     * Full parsing pipeline for a notification title + body from [sourcePackage].
     * Returns `null` if the message is an OTP code or does not contain a recognizable bank transaction.
     */
    fun parse(
        rawTitle: String,
        rawBody: String,
        sourcePackage: String = "generic",
        notificationKey: String = "",
        isoDate: String = PayCycleEngine.todayIso(),
        envelopes: List<EnvelopeConfig> = emptyList(),
        language: String = "fr"
    ): ParsedBankNotification? {
        val combinedRaw = "$rawTitle $rawBody".trim()
        if (combinedRaw.isBlank()) return null

        // 1. Codes first: drop OTP / verification codes immediately
        if (isOtpOrSecurityCodeMessage(combinedRaw)) {
            return null
        }

        val normalized = normalizeText(combinedRaw)
        val lower = normalized.lowercase(Locale.ROOT)

        // 2. Amount + Currency extraction
        val (amount, currency) = extractAmountAndCurrency(normalized) ?: return null

        // 3. Direction detection (ATM withdrawal transfer vs debit/expense vs credit/income)
        val hasCredit = CREDIT_KEYWORDS.any { lower.contains(it) }
        val isAtmWithdrawal = !hasCredit && (
            ATM_WITHDRAWAL_KEYWORDS.any { lower.contains(it) } ||
                Regex("""\bgab\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
            )
        val hasDebit = DEBIT_KEYWORDS.any { lower.contains(it) } || isAtmWithdrawal
        if (!hasCredit && !hasDebit) {
            // Require at least one financial transaction keyword so random chat messages with "50 DH" aren't treated as bank alerts
            return null
        }
        val direction = when {
            isAtmWithdrawal -> "transfer"
            hasCredit && !hasDebit -> "income"
            else -> "expense"
        }

        // 4. Match BankTemplate
        val pkgLower = sourcePackage.lowercase(Locale.ROOT)
        val matchedTemplate = BANK_TEMPLATES.firstOrNull { tpl ->
            tpl.id != "generic" && (
                tpl.packageHints.any { pkgLower.contains(it) } ||
                    tpl.senderKeywords.any { lower.contains(it) }
                )
        } ?: BANK_TEMPLATES.last()

        // 5. Extract Merchant
        val extractedMerchant = if (isAtmWithdrawal) "" else extractMerchant(normalized)
        val fallbackMerchant = when {
            isAtmWithdrawal -> "Retrait GAB"
            direction == "income" -> when (language) {
                "ar" -> "تحويل بنكي"
                "darija" -> "تحويل بنكي"
                "en" -> "Bank Transfer"
                else -> "Virement bancaire"
            }
            matchedTemplate.id != "generic" -> matchedTemplate.bankName
            else -> when (language) {
                "ar" -> "عملية بنكية"
                "darija" -> "خلاص بالبنكة"
                "en" -> "Card Payment"
                else -> "Paiement carte"
            }
        }
        val finalMerchant = if (isAtmWithdrawal) "Retrait GAB" else extractedMerchant.ifBlank { fallbackMerchant }

        // 6. Confidence score calculation
        var confidence = 0.55
        if (hasDebit || hasCredit) confidence += 0.15
        if (extractedMerchant.isNotBlank() || isAtmWithdrawal) confidence += 0.18
        if (matchedTemplate.id != "generic") confidence += 0.10
        val finalConfidence = confidence.coerceIn(0.0, 0.99)
        val needsReview = !isAtmWithdrawal && (finalConfidence < CONFIDENCE_REVIEW_THRESHOLD || extractedMerchant.isBlank())

        // 7. Category resolution (ATM transfers go to "autre", otherwise learned categories + Darija/FR parser)
        val resolvedEnvKey = if (isAtmWithdrawal) {
            "autre"
        } else {
            val learnedEnv = PayCycleEngine.getLearnedCategoryForDescription(finalMerchant).orEmpty()
            val voiceGuess = if (learnedEnv.isBlank()) {
                VoiceDarijaParser.parse("$finalMerchant ${amount.toInt()}", envelopes, language)?.envelopeKey.orEmpty()
            } else ""
            when {
                learnedEnv.isNotBlank() -> learnedEnv
                voiceGuess.isNotBlank() -> voiceGuess
                else -> PayCycleEngine.resolveEnvelopeKey(finalMerchant, finalMerchant, envelopes)
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

        // 8. Deterministic dedupe hash (ignores re-posted or edited notifications for the same event)
        val dedupeSeed = "${sourcePackage.lowercase(Locale.ROOT)}|$direction|$amount|${finalMerchant.lowercase(Locale.ROOT)}|$isoDate|$notificationKey"
        val dedupeHash = sha256Short(dedupeSeed)

        return ParsedBankNotification(
            amount = amount,
            currency = currency,
            direction = direction,
            merchant = finalMerchant,
            isoDate = isoDate,
            sourcePackage = sourcePackage,
            bankTemplateId = matchedTemplate.id,
            confidence = finalConfidence,
            needsReview = needsReview,
            suggestedEnvelopeKey = resolvedEnvKey,
            suggestedCategoryName = resolvedCategoryName,
            dedupeHash = dedupeHash,
            maskedSamplePreview = maskSampleText(combinedRaw)
        )
    }

    /**
     * Step 9: Checks if a manual or voice transaction of the same amount & direction was logged recently
     * (either on the same ISO date or within ±10 minutes of `createdAt`).
     */
    fun findRecentManualMatch(
        amount: Double,
        direction: String,
        isoDate: String,
        allTransactions: List<TransactionItem>,
        nowMs: Long = System.currentTimeMillis()
    ): TransactionItem? {
        val tenMinutesMs = 10 * 60 * 1000L
        return allTransactions.firstOrNull { tx ->
            tx.type == direction &&
                abs(tx.amount - amount) < 0.01 &&
                (
                    (tx.createdAt != null && abs(nowMs - tx.createdAt.toDate().time) <= tenMinutesMs) ||
                        (tx.createdAt == null && tx.isoDate == isoDate)
                    )
        }
    }

    private fun sha256Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
