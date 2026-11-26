package com.masroufi.importer.domain

import com.masroufi.core.Money

/**
 * Pure Kotlin PDF text-to-StatementRow parser for Moroccan bank statements (Phase 3).
 *
 * Works identically on:
 * - Text extracted via PDFBox-Android from native digital bank PDFs
 * - Text produced by ML Kit OCR on rendered scanned PDF pages
 *
 * Supports multi-line descriptions and bank-specific header/footer filtering for:
 * Banque Populaire (BCP), CIH Bank, Attijariwafa Bank, Bank of Africa (BMCE),
 * CFG Bank / Société Générale, and generic bank layouts.
 */
object PdfStatementParser {

    // Matches leading operation date: DD/MM/YYYY, DD-MM-YYYY, DD.MM.YYYY, DD/MM/YY, or YYYY-MM-DD
    private val LEADING_DATE_REGEX = Regex(
        "^\\s*(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}|\\d{4}[/\\-.]\\d{1,2}[/\\-.]\\d{1,2})\\b(.*)$"
    )

    // Matches optional secondary value date right after operation date or mid-line (DD/MM/YYYY or DD/MM/YY or DD/MM)
    private val VALUE_DATE_PREFIX_REGEX = Regex(
        "^\\s*(\\d{1,2}[/\\-.]\\d{1,2}(?:[/\\-.]\\d{2,4})?)\\s+"
    )

    // Matches formatted amounts (with optional sign, thousands spaces/dots, and 2 decimal places)
    private val LINE_AMOUNT_REGEX = Regex(
        "(?<![\\d/\\-])([+-]?\\s*(?:\\d{1,3}(?:[\\s\\u00A0\\u202F.]\\d{3})+|\\d{1,7})[,.]\\d{2}-?)(?![\\d])"
    )

    private val IGNORED_LINES = listOf(
        "solde anterieur",
        "solde initial",
        "solde de depart",
        "solde au ",
        "nouveau solde",
        "solde final",
        "solde crediteur",
        "solde debiteur",
        "total mouvements",
        "total des mouvements",
        "total debit",
        "total credit",
        "a reporter",
        "report ",
        "date operation",
        "date d'operation",
        "date valeur",
        "nature de l'operation",
        "libelle",
        "page ",
        "releve de compte",
        "releve d'identite",
        "extrait de compte",
        "rib :",
        "iban :",
        "siege social",
        "rc casablanca"
    )

    private val CREDIT_KEYWORDS = listOf(
        "vir recu",
        "virement recu",
        "virement en votre faveur",
        "virement rec",
        "versement",
        "remise cheque",
        "remise chq",
        "salaire",
        "paie ",
        "remboursement",
        "avoir ",
        "credit ",
        "interets crediteurs",
        "depot especes",
        "encaissement",
        "allocation",
        "indemnite",
        "virement de "
    )

    private val DEBIT_KEYWORDS = listOf(
        "paiement",
        "achat",
        "retrait",
        "gab",
        "dab",
        "atm",
        "prelevement",
        "prlv",
        "frais",
        "commission",
        "agios",
        "timbre",
        "tva ",
        "virement emis",
        "vir emis",
        "cheque ",
        "chq ",
        "facture",
        "abonnement",
        "cotisation",
        "taxe",
        "debit"
    )

    fun parseText(
        fullText: String,
        forcedProfile: BankProfile? = null,
        usedOcrFallback: Boolean = false,
        pagesProcessed: Int = 1
    ): PdfParseResult {
        val detectedProfile = forcedProfile ?: BankProfile.detectFromText(fullText.take(4000))
        val rawLines = fullText.lineSequence()
            .map { ImporterConstants.sanitizeLabel(it, maxLength = 220) }
            .filter { it.isNotBlank() }
            .toList()

        if (rawLines.isEmpty()) {
            return PdfParseResult(
                detectedBankProfile = detectedProfile,
                rows = emptyList(),
                usedOcrFallback = usedOcrFallback,
                pagesProcessed = pagesProcessed
            )
        }

        // Track an opening balance if present to infer credit vs debit when a running balance column exists
        var runningBalance: Money? = extractOpeningBalance(rawLines)

        data class RawPdfEntry(
            val isoDate: String,
            val parts: MutableList<String>,
            val lineNumber: Int
        )

        val entries = ArrayList<RawPdfEntry>()
        var current: RawPdfEntry? = null
        var continuationCount = 0

        for ((index, line) in rawLines.withIndex()) {
            val norm = CsvStatementParser.normalizeAscii(line)
            if (isIgnoredHeaderOrFooter(norm)) {
                // Footer/header ends any open multi-line entry
                current = null
                continuationCount = 0
                continue
            }

            val dateMatch = LEADING_DATE_REGEX.matchEntire(line)
            if (dateMatch != null) {
                val isoDate = CsvStatementParser.parseStatementDate(dateMatch.groupValues[1])
                if (isoDate != null) {
                    val rest = dateMatch.groupValues[2].trim()
                    val entry = RawPdfEntry(isoDate = isoDate, parts = mutableListOf(rest), lineNumber = index + 1)
                    entries.add(entry)
                    current = entry
                    continuationCount = 0
                    continue
                }
            }

            // Append continuation description lines (up to 3 lines) to the current entry
            if (current != null && continuationCount < 3 && !isLikelyPageNoise(norm)) {
                current.parts.add(line)
                continuationCount++
            }
        }

        val statementRows = ArrayList<StatementRow>(entries.size)
        for (entry in entries) {
            if (statementRows.size >= ImporterConstants.MAX_IMPORT_ROWS) break
            val combined = entry.parts.joinToString(" ").trim()
            if (combined.isEmpty()) continue
            val normCombined = CsvStatementParser.normalizeAscii(combined)
            if (isIgnoredHeaderOrFooter(normCombined)) continue

            val strippedOfValueDate = combined.replaceFirst(VALUE_DATE_PREFIX_REGEX, "").trim()
            val amountMatches = LINE_AMOUNT_REGEX.findAll(strippedOfValueDate).toList()
            if (amountMatches.isEmpty()) continue

            val parsedAmounts = amountMatches.mapNotNull { m ->
                CsvStatementParser.parseMoroccanAmount(m.groupValues[1])?.let { m to it }
            }
            if (parsedAmounts.isEmpty()) continue

            // Extract clean label by removing amount tokens and inline dates while preserving multi-line continuation text
            var labelText = LINE_AMOUNT_REGEX.replace(strippedOfValueDate, " ").trim()
            labelText = labelText.replace(Regex("\\b\\d{1,2}[/\\-.]\\d{1,2}(?:[/\\-.]\\d{2,4})?\\b"), " ")
            labelText = ImporterConstants.sanitizeLabel(labelText)
            if (labelText.length < 2) continue

            val (txAmount, isCredit, updatedBalance) = classifyPdfRowAmounts(
                parsedAmounts = parsedAmounts.map { it.second },
                rawTokens = parsedAmounts.map { it.first.groupValues[1] },
                labelNorm = CsvStatementParser.normalizeAscii(labelText),
                previousBalance = runningBalance
            )
            if (updatedBalance != null) {
                runningBalance = updatedBalance
            }
            if (!txAmount.isPositive) continue

            statementRows.add(
                StatementRow(
                    isoDate = entry.isoDate,
                    rawLabel = labelText,
                    debitMad = if (!isCredit) txAmount else null,
                    creditMad = if (isCredit) txAmount else null,
                    signedAmountMad = if (isCredit) txAmount else -txAmount,
                    sourceLineNumber = entry.lineNumber
                )
            )
        }

        return PdfParseResult(
            detectedBankProfile = detectedProfile,
            rows = statementRows,
            usedOcrFallback = usedOcrFallback,
            pagesProcessed = pagesProcessed
        )
    }

    private fun extractOpeningBalance(lines: List<String>): Money? {
        for (line in lines.take(40)) {
            val norm = CsvStatementParser.normalizeAscii(line)
            if (norm.contains("solde anterieur") || norm.contains("solde initial") || norm.contains("solde de depart")) {
                val lastMatch = LINE_AMOUNT_REGEX.findAll(line).lastOrNull() ?: continue
                return CsvStatementParser.parseMoroccanAmount(lastMatch.groupValues[1])
            }
        }
        return null
    }

    /**
     * Classifies amount(s) on a PDF statement row into (positiveAmount, isCredit, newRunningBalance?).
     */
    private fun classifyPdfRowAmounts(
        parsedAmounts: List<Money>,
        rawTokens: List<String>,
        labelNorm: String,
        previousBalance: Money?
    ): Triple<Money, Boolean, Money?> {
        // Case 1: Single amount on the row (e.g., Debit or Credit column only, or signed Montant)
        if (parsedAmounts.size == 1) {
            val val1 = parsedAmounts[0]
            val token1 = rawTokens[0].trim()
            val explicitNeg = token1.startsWith("-") || token1.endsWith("-") || val1.isNegative
            val explicitPos = token1.startsWith("+")
            val isCredit = when {
                explicitNeg -> false
                explicitPos -> true
                else -> inferIsCreditFromLabel(labelNorm)
            }
            val absVal = val1.abs()
            return Triple(absVal, isCredit, previousBalance?.let { if (isCredit) it + absVal else it - absVal })
        }

        // Case 2: Two or more amounts on the row -> usually [movement, runningBalance] or [debit, credit]
        val first = parsedAmounts[0]
        val last = parsedAmounts.last()
        val firstToken = rawTokens[0].trim()

        // If we know previous running balance, check if last amount matches prev +/- abs(first) within 5 centimes
        if (previousBalance != null) {
            val amt = first.abs()
            if (((previousBalance - amt) - last).abs().centimes < 5L) {
                return Triple(amt, false, last)
            }
            if (((previousBalance + amt) - last).abs().centimes < 5L) {
                return Triple(amt, true, last)
            }
        }

        // If one of the two amounts is zero (0,00), it's explicit [Debit, Credit] columns
        if (parsedAmounts.size == 2) {
            if (first.isZero && last.abs().isPositive) {
                return Triple(last.abs(), true, previousBalance)
            }
            if (last.isZero && first.abs().isPositive) {
                return Triple(first.abs(), false, previousBalance)
            }
        }

        // Otherwise treat the first non-zero amount as the transaction amount and infer sign/direction
        val movement = first
        val explicitNeg = firstToken.startsWith("-") || firstToken.endsWith("-") || movement.isNegative
        val explicitPos = firstToken.startsWith("+")
        val isCredit = when {
            explicitNeg -> false
            explicitPos -> true
            else -> inferIsCreditFromLabel(labelNorm)
        }
        val newBalance = if (parsedAmounts.size >= 2) last else previousBalance
        return Triple(movement.abs(), isCredit, newBalance)
    }

    private fun inferIsCreditFromLabel(labelNorm: String): Boolean {
        val hasCredit = CREDIT_KEYWORDS.any { labelNorm.contains(it) }
        val hasDebit = DEBIT_KEYWORDS.any { labelNorm.contains(it) }
        return hasCredit && !hasDebit
    }

    private fun isIgnoredHeaderOrFooter(norm: String): Boolean {
        if (norm.isEmpty()) return true
        return IGNORED_LINES.any { norm.contains(it) }
    }

    private fun isLikelyPageNoise(norm: String): Boolean {
        return norm.length <= 2 ||
            Regex("^\\d+\\s*/\\s*\\d+$").matches(norm) ||
            norm.startsWith("ref ") && norm.length < 18
    }
}
