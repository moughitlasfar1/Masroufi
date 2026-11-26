package com.masroufi.importer.domain

import com.example.domain.PayCycleEngine
import com.masroufi.core.Money

/**
 * Pure Kotlin receipt text extractor (Phase 2).
 * Extracts:
 * 1. Total amount (largest plausible amount near TOTAL / NET A PAYER / MONTANT / TTC)
 * 2. Receipt date (dd/MM/yyyy, dd-MM-yyyy, dd.MM.yyyy, yyyy-MM-dd, dd/MM/yy)
 * 3. Merchant name (cleanest top header line)
 *
 * Also flags when OCR output has little/no Latin text so the UI can remind the user
 * that Arabic-only receipts may need manual adjustment.
 */
object ReceiptParser {

    private val HIGH_PRIORITY_TOTAL_KEYWORDS = listOf(
        "net a payer",
        "total ttc",
        "total a payer",
        "montant total",
        "montant ttc",
        "total general",
        "a payer",
        "total dh",
        "total mad"
    )

    private val MEDIUM_PRIORITY_TOTAL_KEYWORDS = listOf(
        "total",
        "montant",
        "ttc",
        "somme",
        "mnt"
    )

    private val EXCLUDED_AMOUNT_LINE_KEYWORDS = listOf(
        "rendu",
        "monnaie",
        "change",
        "recu",
        "especes",
        "tva",
        "sous-total",
        "sous total",
        "total ht",
        "hors taxe",
        "remise",
        "economie",
        "points",
        "fidelite",
        "carte n",
        "ice",
        "if ",
        "rc ",
        "tel"
    )

    private val MERCHANT_SKIP_KEYWORDS = listOf(
        "ticket",
        "recu",
        "facture",
        "bienvenue",
        "merci",
        "caisse",
        "caissier",
        "serveur",
        "date",
        "heure",
        "client",
        "ice",
        "patente",
        "cnss",
        "tel",
        "fax",
        "www.",
        "http",
        "av ",
        "avenue",
        "bd ",
        "boulevard",
        "rue ",
        "quartier",
        "casablanca",
        "rabat",
        "marrakech",
        "tanger",
        "fes",
        "agadir",
        "maroc"
    )

    // Matches Moroccan/French amounts like 145,50 / 1 250,00 / 85.00 / 120 DH
    private val AMOUNT_TOKEN_REGEX = Regex(
        "(?<![\\d/\\-])(\\d{1,3}(?:[\\s\\u00A0.]\\d{3})+|\\d{1,6})(?:[,.](\\d{2}))(?![\\d])"
    )
    private val INTEGER_CURRENCY_REGEX = Regex(
        "(?i)(?<![\\d/\\-])(\\d{1,6})\\s*(?:dh|dhs|mad)\\b"
    )
    private val DATE_INLINE_REGEX = Regex(
        "\\b(\\d{4}[/\\-.]\\d{1,2}[/\\-.]\\d{1,2}|\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4})\\b"
    )

    fun parse(rawText: String, todayIso: String = PayCycleEngine.todayIso()): ParsedReceipt {
        val lines = rawText.lineSequence()
            .map { ImporterConstants.sanitizeLabel(it, maxLength = 120) }
            .filter { it.isNotBlank() }
            .toList()

        val latinLetters = rawText.count { it in 'A'..'Z' || it in 'a'..'z' }
        val arabicLetters = rawText.count { it in '\u0600'..'\u06FF' }

        if (lines.isEmpty()) {
            return ParsedReceipt(
                totalAmountMad = null,
                isoDate = null,
                merchantName = null,
                rawLineCount = 0,
                arabicWeakHint = true
            )
        }

        val total = extractTotalAmount(lines)
        val date = extractDate(lines, todayIso)
        val merchant = extractMerchant(lines)
        val weakLatinHint = (latinLetters < 6 && total == null) || (arabicLetters > latinLetters && total == null)

        return ParsedReceipt(
            totalAmountMad = total,
            isoDate = date,
            merchantName = merchant,
            rawLineCount = lines.size,
            arabicWeakHint = weakLatinHint
        )
    }

    private val MIN_RECEIPT_AMOUNT: Money = Money.fromCentimes(50L)
    private val MAX_RECEIPT_AMOUNT: Money = Money.fromDh(500_000L)

    private fun extractTotalAmount(lines: List<String>): Money? {
        data class ScoredAmount(val amount: Money, val score: Int, val lineIndex: Int)

        val candidates = ArrayList<ScoredAmount>()
        val totalLines = lines.size

        for (i in lines.indices) {
            val line = lines[i]
            val norm = CsvStatementParser.normalizeAscii(line)
            val isExcluded = EXCLUDED_AMOUNT_LINE_KEYWORDS.any { norm.contains(it) }
            val hasHighKw = HIGH_PRIORITY_TOTAL_KEYWORDS.any { norm.contains(it) }
            val hasMedKw = MEDIUM_PRIORITY_TOTAL_KEYWORDS.any { norm.contains(it) }

            val amountsOnLine = extractAmountsFromLine(line)
            if (amountsOnLine.isNotEmpty()) {
                val baseScore = when {
                    hasHighKw && !isExcluded -> 100
                    hasMedKw && !isExcluded -> 70
                    isExcluded -> -50
                    i >= totalLines / 2 -> 20
                    else -> 10
                }
                for (amt in amountsOnLine) {
                    if (amt in MIN_RECEIPT_AMOUNT..MAX_RECEIPT_AMOUNT) {
                        candidates.add(ScoredAmount(amt, baseScore, i))
                    }
                }
            } else if ((hasHighKw || hasMedKw) && !isExcluded && i + 1 < totalLines) {
                // Sometimes OCR puts "TOTAL TTC" on one line and "149,50 DH" on the next line
                val nextAmounts = extractAmountsFromLine(lines[i + 1])
                val score = if (hasHighKw) 95 else 65
                for (amt in nextAmounts) {
                    if (amt in MIN_RECEIPT_AMOUNT..MAX_RECEIPT_AMOUNT) {
                        candidates.add(ScoredAmount(amt, score, i + 1))
                    }
                }
            }
        }

        val positiveCandidates = candidates.filter { it.score > 0 }
        if (positiveCandidates.isEmpty()) return null

        val maxScore = positiveCandidates.maxOf { it.score }
        // Among the highest-priority matches (e.g. near TOTAL / NET A PAYER), pick the largest amount
        return positiveCandidates
            .filter { it.score >= maxScore - 10 }
            .maxByOrNull { it.amount }
            ?.amount
    }

    private fun extractAmountsFromLine(line: String): List<Money> {
        val found = ArrayList<Money>()
        // Remove dates and timestamps so "14.05.2026" or "12:30" are not misread as amounts
        val withoutDates = DATE_INLINE_REGEX.replace(line, " ")
            .replace(Regex("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b"), " ")

        AMOUNT_TOKEN_REGEX.findAll(withoutDates).forEach { match ->
            val intPart = match.groupValues[1].replace(Regex("[\\s\\u00A0.]"), "")
            val decPart = match.groupValues[2]
            val parsed = Money.fromDhString("$intPart.$decPart")
            if (parsed != null && parsed.isPositive) {
                found.add(parsed)
            }
        }
        INTEGER_CURRENCY_REGEX.findAll(withoutDates).forEach { match ->
            val parsed = Money.fromDhString(match.groupValues[1])
            if (parsed != null && parsed.isPositive) {
                found.add(parsed)
            }
        }
        return found
    }

    private fun extractDate(lines: List<String>, todayIso: String): String? {
        val todayEpoch = PayCycleEngine.parseEpochDays(todayIso)
        for (line in lines) {
            DATE_INLINE_REGEX.findAll(line).forEach { match ->
                val iso = CsvStatementParser.parseStatementDate(match.groupValues[1])
                if (iso != null) {
                    val epoch = PayCycleEngine.parseEpochDays(iso)
                    // Reject clearly bogus dates (more than 10 years old or more than 7 days in future)
                    if (epoch in (todayEpoch - 3650L)..(todayEpoch + 7L)) {
                        return iso
                    }
                }
            }
        }
        return null
    }

    private fun extractMerchant(lines: List<String>): String? {
        val topLines = lines.take(6)
        for (line in topLines) {
            val cleaned = line.trim().trim('*', '-', '=', '_', '#')
            if (cleaned.length < 2) continue
            val norm = CsvStatementParser.normalizeAscii(cleaned)
            if (MERCHANT_SKIP_KEYWORDS.any { norm.startsWith(it) || norm == it.trim() }) continue
            val letterCount = cleaned.count { it.isLetter() }
            val digitCount = cleaned.count { it.isDigit() }
            if (letterCount < 2 || digitCount > letterCount) continue
            if (DATE_INLINE_REGEX.containsMatchIn(cleaned)) continue
            return cleaned.take(60)
        }
        return null
    }
}
