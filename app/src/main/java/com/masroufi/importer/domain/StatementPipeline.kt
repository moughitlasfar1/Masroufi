package com.masroufi.importer.domain

import com.example.data.AccountItem
import com.example.data.DefaultCategoryResolver
import com.example.data.EnvelopeConfig
import com.example.data.TransactionItem
import com.example.domain.PayCycleEngine
import java.util.Locale
import kotlin.math.abs

/**
 * Shared statement import pipeline:
 * StatementRow -> label cleaning -> ATM withdrawal transfer rule -> category guess
 * -> deterministic ID generation -> duplicate detection -> TransactionItem conversion.
 *
 * Never calls `recordQuickCaptureTransactions`; preserves exact statement `isoDate`
 * and uses deterministic IDs (`tx_imp_<hash>`) so re-importing updates cleanly without Room schema changes.
 */
object StatementPipeline {

    private val ATM_KEYWORDS = listOf(
        "retrait gab",
        "retrait dab",
        "retrait atm",
        "retrait especes",
        "retrait espèce",
        "retrait guichet",
        "retrait carte gab",
        "gab ",
        "dab ",
        "atm ",
        "سحب"
    )

    private val NOISE_PREFIXES = listOf(
        Regex("(?i)^(paiement|achat|reglement|règlement)\\s+(par\\s+)?(carte|tpe|cb|internet|ecom|e-com|sans\\s+contact)\\s*[:\\-]?\\s*"),
        Regex("(?i)^(op\\.?\\s*carte|carte\\s+bancaire|tpe|pos)\\s*[:\\-]?\\s*"),
        Regex("(?i)^(virement|vir\\.?)\\s+(recu|reçu|emis|émis|permanent|instantane|instantané|sepa)?\\s*(de|vers|a|à|en\\s+faveur\\s+de)?\\s*[:\\-]?\\s*"),
        Regex("(?i)^(prelevement|prélèvement|prlv\\.?)\\s*(automatique|sepa)?\\s*[:\\-]?\\s*"),
        Regex("(?i)^(facture|paiement\\s+facture)\\s*[:\\-]?\\s*")
    )

    private val CARD_MASK_REGEX = Regex("\\b(?:\\d{4}[*xX]{4,}\\d{4}|\\*{2,}\\d{4}|[xX]{4,}\\d{4})\\b")
    private val TRAILING_DATE_REGEX = Regex("\\s+\\d{2}/\\d{2}(?:/\\d{2,4})?\\s*$")

    private val MOROCCAN_MERCHANT_HINTS: List<Pair<List<String>, String>> = listOf(
        listOf("marjane", "carrefour", "bim ", "bim-", "atacadao", "acima", "aswak", "label vie", "kazyon", "supermarche", "boulangerie", "epicerie") to "marche",
        listOf("glovo", "jumia food", "mcdonald", "kfc", "burger king", "starbucks", "paul ", "cafe ", "café", "resto", "restaurant", "pizzeria", "tacos", "patisserie", "cinema", "megarama", "netflix", "spotify") to "cafe",
        listOf("oncf", "ctm", "supratours", "tramway", "casatram", "indrive", "careem", "heetch", "uber", "taxi", "autoroute", "adm ", "jawaz", "afriquia", "shell", "totalenergies", "total ", "winxo", "petrom", "ola energy", "station ") to "transport",
        listOf("inwi", "orange", "maroc telecom", "iam ", "lydec", "redal", "amendys", "radeema", "radeef", "onee", "loyer", "syndic", "assurance", "wafa assurance", "axa ", "saham", "rma ", "cnss", "impot", "dgi", "vignette", "frais de tenue", "commission", "agios", "timbre") to "charges",
        listOf("epargne", "épargne", "placement", "opcvm", "bourse", "cdg") to "epargne"
    )

    fun isAtmWithdrawal(rawLabel: String): Boolean {
        val norm = CsvStatementParser.normalizeAscii(ImporterConstants.sanitizeLabel(rawLabel))
        if (norm.isEmpty()) return false
        if (ATM_KEYWORDS.any { norm.contains(it) }) return true
        return Regex("\\b(gab|dab|atm)\\b").containsMatchIn(norm)
    }

    fun cleanBankLabel(raw: String): String {
        val sanitized = ImporterConstants.sanitizeLabel(raw)
        if (sanitized.isEmpty()) return "Opération bancaire"

        var cleaned = sanitized
        for (regex in NOISE_PREFIXES) {
            cleaned = cleaned.replace(regex, "")
        }
        cleaned = cleaned.replace(CARD_MASK_REGEX, " ")
        cleaned = cleaned.replace(TRAILING_DATE_REGEX, "")
        cleaned = ImporterConstants.sanitizeLabel(cleaned)

        return if (cleaned.length >= 2) cleaned else sanitized
    }

    fun buildCandidates(
        rows: List<StatementRow>,
        existingTransactions: List<TransactionItem>,
        envelopes: List<EnvelopeConfig>,
        accounts: List<AccountItem>,
        targetBankAccountId: String,
        lang: String = "fr"
    ): List<ImportCandidateRow> {
        val bankAccId = targetBankAccountId.ifBlank {
            accounts.firstOrNull { it.kind == "bank" }?.id
                ?: accounts.firstOrNull()?.id
                ?: "acc_bank"
        }
        val cashAccId = accounts.firstOrNull { it.kind == "cash" && it.id != bankAccId }?.id
            ?: "acc_cash"

        // Pre-index existing transactions for fast duplicate matching
        val existingIds = existingTransactions.mapTo(HashSet()) { it.id }
        val existingSignatureCounts = HashMap<String, Int>()
        existingTransactions.forEach { tx ->
            val sig = duplicateSignature(tx.isoDate, tx.amount, tx.type)
            existingSignatureCounts[sig] = (existingSignatureCounts[sig] ?: 0) + 1
        }

        val fileIdOccurrences = HashMap<String, Int>()
        val matchedSignatureCounts = HashMap<String, Int>()
        val candidates = ArrayList<ImportCandidateRow>(rows.size)

        for (row in rows) {
            if (!PayCycleEngine.isValidIsoDate(row.isoDate)) continue
            val (rawType, amount) = resolveTypeAndAmount(row) ?: continue
            val roundedAmount = PayCycleEngine.roundMoney(amount)
            if (roundedAmount <= 0.0) continue

            val atmTransfer = rawType == "expense" && isAtmWithdrawal(row.rawLabel)
            val txType = if (atmTransfer) "transfer" else rawType
            val cleanedLabel = if (atmTransfer) "Retrait GAB" else cleanBankLabel(row.rawLabel)

            val (envKey, catName) = when (txType) {
                "transfer" -> "autre" to "Transfert"
                "income" -> {
                    val label = when (lang) {
                        "en" -> "Income"
                        "ar" -> "مدخول"
                        "darija" -> "مدخول"
                        else -> "Revenu"
                    }
                    "autre" to label
                }
                else -> guessExpenseCategory(
                    cleanedLabel = cleanedLabel,
                    rawLabel = row.rawLabel,
                    existingTransactions = existingTransactions,
                    envelopes = envelopes,
                    accounts = accounts,
                    lang = lang
                )
            }

            val baseKey = "${row.isoDate}|${String.format(Locale.US, "%.2f", roundedAmount)}|${cleanedLabel.lowercase(Locale.ROOT)}|$bankAccId"
            val occ = fileIdOccurrences[baseKey] ?: 0
            fileIdOccurrences[baseKey] = occ + 1

            val deterministicId = ImporterConstants.buildDeterministicId(
                isoDate = row.isoDate,
                amountMad = roundedAmount,
                normalizedLabel = cleanedLabel,
                accountId = bankAccId,
                occurrenceIndex = occ
            )

            val dupSig = duplicateSignature(row.isoDate, roundedAmount, txType)
            val existingCount = existingSignatureCounts[dupSig] ?: 0
            val alreadyMatched = matchedSignatureCounts[dupSig] ?: 0
            val isProbableDuplicate = if (deterministicId in existingIds) {
                true
            } else if (existingCount > alreadyMatched) {
                matchedSignatureCounts[dupSig] = alreadyMatched + 1
                true
            } else {
                false
            }

            candidates.add(
                ImportCandidateRow(
                    deterministicId = deterministicId,
                    isoDate = row.isoDate,
                    rawLabel = ImporterConstants.sanitizeLabel(row.rawLabel),
                    cleanedLabel = cleanedLabel,
                    txType = txType,
                    amountMad = roundedAmount,
                    accountId = bankAccId,
                    toAccountId = if (atmTransfer) cashAccId else "",
                    envelopeKey = envKey,
                    categoryName = catName,
                    isAtmTransfer = atmTransfer,
                    isProbableDuplicate = isProbableDuplicate,
                    isSelected = !isProbableDuplicate
                )
            )
        }
        return candidates
    }

    fun toTransactionItems(
        candidates: List<ImportCandidateRow>,
        userId: String
    ): List<TransactionItem> {
        return candidates
            .filter { it.isSelected && it.amountMad > 0.0 }
            .map { c ->
                val normalizedCategory = if (c.txType == "transfer") {
                    "Transfert"
                } else {
                    DefaultCategoryResolver.normalizeStoredCategoryValue(c.categoryName, c.envelopeKey)
                }
                TransactionItem(
                    id = c.deterministicId,
                    userId = userId,
                    type = c.txType,
                    amount = PayCycleEngine.roundMoney(c.amountMad),
                    category = normalizedCategory,
                    envelopeKey = if (c.txType == "transfer") "autre" else c.envelopeKey,
                    accountId = c.accountId,
                    toAccountId = if (c.txType == "transfer") c.toAccountId else "",
                    desc = ImporterConstants.sanitizeLabel(c.cleanedLabel),
                    isoDate = c.isoDate,
                    hourOfDay = 12
                )
            }
    }

    private fun resolveTypeAndAmount(row: StatementRow): Pair<String, Double>? {
        val debit = row.debitMad?.takeIf { it > 0.0 }
        val credit = row.creditMad?.takeIf { it > 0.0 }
        return when {
            debit != null && credit == null -> "expense" to debit
            credit != null && debit == null -> "income" to credit
            debit != null && credit != null -> {
                if (debit >= credit) "expense" to debit else "income" to credit
            }
            row.signedAmountMad != null && abs(row.signedAmountMad) > 0.0 -> {
                if (row.signedAmountMad < 0.0) "expense" to abs(row.signedAmountMad)
                else "income" to row.signedAmountMad
            }
            else -> null
        }
    }

    private fun guessExpenseCategory(
        cleanedLabel: String,
        rawLabel: String,
        existingTransactions: List<TransactionItem>,
        envelopes: List<EnvelopeConfig>,
        accounts: List<AccountItem>,
        lang: String
    ): Pair<String, String> {
        // 1. User's learned description -> category mappings take top priority
        val learnedKey = PayCycleEngine.getLearnedCategoryForDescription(cleanedLabel)
            ?: PayCycleEngine.getLearnedCategoryForDescription(rawLabel)
        if (!learnedKey.isNullOrBlank()) {
            return resolveEnvAndName(learnedKey, envelopes, lang)
        }

        // 2. Moroccan merchant & utility heuristics
        val combinedNorm = CsvStatementParser.normalizeAscii("$cleanedLabel $rawLabel")
        for ((keywords, targetKey) in MOROCCAN_MERCHANT_HINTS) {
            if (keywords.any { combinedNorm.contains(it) }) {
                return resolveEnvAndName(targetKey, envelopes, lang)
            }
        }

        // 3. Existing PayCycleEngine smart defaults (historical descriptions + Darija/FR/AR/EN keywords)
        val smart = PayCycleEngine.inferSmartDefaults(
            description = cleanedLabel,
            allTransactions = existingTransactions,
            envelopes = envelopes,
            accounts = accounts,
            lang = lang
        )
        val matched = envelopes.find { it.key == smart.envelopeKey }
        if (matched != null) {
            return matched.key to matched.localizedName(lang)
        }
        return resolveEnvAndName(smart.envelopeKey.ifBlank { "autre" }, envelopes, lang)
    }

    private fun resolveEnvAndName(
        preferredKey: String,
        envelopes: List<EnvelopeConfig>,
        lang: String
    ): Pair<String, String> {
        val exact = envelopes.firstOrNull { it.key.equals(preferredKey, ignoreCase = true) }
        if (exact != null) return exact.key to exact.localizedName(lang)
        val fallback = envelopes.firstOrNull { it.key == "autre" } ?: envelopes.firstOrNull()
        val key = fallback?.key ?: preferredKey
        val name = fallback?.localizedName(lang)
            ?: DefaultCategoryResolver.localizedDefaultName(key, lang)
            ?: key
        return key to name
    }

    private fun duplicateSignature(isoDate: String, amount: Double, type: String): String {
        val rounded = String.format(Locale.US, "%.2f", PayCycleEngine.roundMoney(amount))
        return "${isoDate.trim()}|$rounded|${type.trim().lowercase(Locale.ROOT)}"
    }
}
