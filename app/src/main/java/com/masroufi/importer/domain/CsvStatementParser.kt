package com.masroufi.importer.domain

import com.example.domain.PayCycleEngine
import com.masroufi.core.Money
import com.masroufi.core.MoneyParser
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale

/**
 * Pure Kotlin CSV statement parser tailored for Moroccan bank exports
 * (Banque Populaire / BCP, CIH Bank, Attijariwafa Bank, Bank of Africa / BMCE,
 * CFG Bank, Société Générale, and generic bank exports).
 *
 * Handles:
 * - UTF-8 (with or without BOM) and Windows-1252 encoding detection
 * - Automatic delimiter detection (';' or ',')
 * - RFC-4180 quoted fields, escaped quotes, embedded delimiters/newlines
 * - Moroccan number formats (decimal commas, spaces/NBSP thousands separators, accounting parens)
 * - dd/MM/yyyy, dd-MM-yyyy, dd.MM.yyyy, yyyy-MM-dd, dd/MM/yy dates
 * - Both separate Débit/Crédit columns and single signed amount column
 */
object CsvStatementParser {

    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")
    private val DATE_DMY_REGEX = Regex("^(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2,4})$")
    private val DATE_ISO_REGEX = Regex("^(\\d{4})[/\\-.](\\d{1,2})[/\\-.](\\d{1,2})$")

    private val DATE_HEADER_KEYWORDS = listOf("date op", "date operation", "date valeur", "date comptable", "date")
    private val LABEL_HEADER_KEYWORDS = listOf("libelle", "operation", "designation", "motif", "description", "intitule", "nature", "label")
    private val DEBIT_HEADER_KEYWORDS = listOf("debit", "sortie", "retrait", "depense")
    private val CREDIT_HEADER_KEYWORDS = listOf("credit", "entree", "versement", "recette")
    private val SIGNED_AMOUNT_HEADER_KEYWORDS = listOf("montant", "amount", "somme", "valeur")

    fun decodeBytes(bytes: ByteArray): Pair<String, String> {
        require(bytes.size <= ImporterConstants.MAX_IMPORT_FILE_BYTES) {
            "File exceeds maximum allowed size (${ImporterConstants.MAX_IMPORT_FILE_BYTES} bytes)."
        }
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            val text = String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
            return text to "UTF-8-BOM"
        }
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()
            decoded to "UTF-8"
        } catch (_: CharacterCodingException) {
            String(bytes, WINDOWS_1252) to "Windows-1252"
        }
    }

    fun detectDelimiter(text: String): Char {
        val sampleLines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(25)
            .toList()
        if (sampleLines.isEmpty()) return ';'

        var semiCount = 0
        var commaCount = 0
        var semiLines = 0
        var commaLines = 0

        for (line in sampleLines) {
            var inQuotes = false
            var lineSemi = 0
            var lineComma = 0
            for (ch in line) {
                when (ch) {
                    '"' -> inQuotes = !inQuotes
                    ';' -> if (!inQuotes) lineSemi++
                    ',' -> if (!inQuotes) lineComma++
                }
            }
            semiCount += lineSemi
            commaCount += lineComma
            if (lineSemi >= 2) semiLines++
            if (lineComma >= 2) commaLines++
        }

        return when {
            semiLines > commaLines -> ';'
            commaLines > semiLines -> ','
            semiCount >= commaCount && semiCount > 0 -> ';'
            commaCount > 0 -> ','
            else -> ';'
        }
    }

    fun parseRawRows(text: String, delimiter: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val currentRow = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = text.length
        val maxRows = ImporterConstants.MAX_IMPORT_ROWS + 30

        while (i < len && rows.size < maxRows) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < len && text[i + 1] == '"') {
                        field.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> {
                        if (field.isEmpty()) {
                            inQuotes = true
                        } else {
                            field.append(ch)
                        }
                    }
                    delimiter -> {
                        currentRow.add(field.toString().trim())
                        field.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 < len && text[i + 1] == '\n') i++
                        currentRow.add(field.toString().trim())
                        field.setLength(0)
                        if (currentRow.any { it.isNotEmpty() }) {
                            rows.add(currentRow.toList())
                        }
                        currentRow.clear()
                    }
                    '\n' -> {
                        currentRow.add(field.toString().trim())
                        field.setLength(0)
                        if (currentRow.any { it.isNotEmpty() }) {
                            rows.add(currentRow.toList())
                        }
                        currentRow.clear()
                    }
                    else -> field.append(ch)
                }
            }
            i++
        }

        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString().trim())
            if (currentRow.any { it.isNotEmpty() } && rows.size < maxRows) {
                rows.add(currentRow.toList())
            }
        }
        return rows
    }

    fun parseMoroccanAmount(raw: String?): Money? {
        if (raw.isNullOrBlank()) return null
        return MoneyParser.parseMoroccanAmountToCentimes(raw)?.let { Money.fromCentimes(it) }
    }

    fun parseStatementDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val token = raw.trim().substringBefore(' ').substringBefore('T').trim()
        if (token.isEmpty()) return null

        DATE_ISO_REGEX.matchEntire(token)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return null
            val mo = m.groupValues[2].toIntOrNull() ?: return null
            val d = m.groupValues[3].toIntOrNull() ?: return null
            val iso = String.format(Locale.US, "%04d-%02d-%02d", y, mo, d)
            return iso.takeIf { PayCycleEngine.isValidIsoDate(it) }
        }

        DATE_DMY_REGEX.matchEntire(token)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return null
            val mo = m.groupValues[2].toIntOrNull() ?: return null
            val rawY = m.groupValues[3].toIntOrNull() ?: return null
            val y = if (rawY < 100) 2000 + rawY else rawY
            val iso = String.format(Locale.US, "%04d-%02d-%02d", y, mo, d)
            return iso.takeIf { PayCycleEngine.isValidIsoDate(it) }
        }
        return null
    }

    fun inspectCsv(
        bytes: ByteArray,
        savedMappings: Map<BankProfile, CsvColumnMapping> = emptyMap(),
        delimiterOverride: Char? = null
    ): CsvInspectionResult {
        val (text, encoding) = decodeBytes(bytes)
        val delimiter = delimiterOverride ?: detectDelimiter(text)
        val rawRows = parseRawRows(text, delimiter)

        val preambleSample = text.lineSequence().take(15).joinToString("\n")
        val detectedBank = BankProfile.detectFromText(preambleSample)

        val headerRowIdx = findHeaderRowIndex(rawRows)
        val headerRow = rawRows.getOrNull(headerRowIdx).orEmpty()
        val hasHeader = headerRowIdx >= 0 && isLikelyHeaderRow(headerRow)

        val maxCols = rawRows.drop(if (headerRowIdx >= 0) headerRowIdx else 0)
            .take(20)
            .maxOfOrNull { it.size }
            ?: headerRow.size

        val normalizedHeaders = if (hasHeader) {
            (0 until maxCols).map { idx ->
                headerRow.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: "Col ${idx + 1}"
            }
        } else {
            (0 until maxCols).map { idx -> "Col ${idx + 1}" }
        }

        val dataStartIdx = if (hasHeader) headerRowIdx + 1 else 0
        val previewRows = rawRows.drop(dataStartIdx).take(5)

        val saved = savedMappings[detectedBank]?.takeIf {
            it.delimiter == delimiter &&
                it.dateCol in 0 until maxCols &&
                it.labelCol in 0 until maxCols &&
                (if (it.amountMode == AmountColumnMode.SPLIT_DEBIT_CREDIT) {
                    it.debitCol in 0 until maxCols && it.creditCol in 0 until maxCols
                } else {
                    it.signedAmountCol in 0 until maxCols
                })
        }

        if (saved != null) {
            return CsvInspectionResult(
                encodingName = encoding,
                delimiter = delimiter,
                detectedBankProfile = detectedBank,
                headerRowIndex = if (hasHeader) headerRowIdx else 0,
                headers = normalizedHeaders,
                previewRows = previewRows,
                allRawRows = rawRows,
                suggestedMapping = saved,
                highConfidenceAutoMap = true,
                usedSavedMapping = true
            )
        }

        val (suggested, highConfidence) = autoDetectMapping(
            bankProfile = detectedBank,
            delimiter = delimiter,
            hasHeader = hasHeader,
            headers = normalizedHeaders,
            sampleDataRows = previewRows
        )

        return CsvInspectionResult(
            encodingName = encoding,
            delimiter = delimiter,
            detectedBankProfile = detectedBank,
            headerRowIndex = if (hasHeader) headerRowIdx else 0,
            headers = normalizedHeaders,
            previewRows = previewRows,
            allRawRows = rawRows,
            suggestedMapping = suggested,
            highConfidenceAutoMap = highConfidence,
            usedSavedMapping = false
        )
    }

    fun extractRows(
        rawRows: List<List<String>>,
        mapping: CsvColumnMapping,
        headerRowIndex: Int = 0
    ): List<StatementRow> {
        val startIdx = if (mapping.hasHeaderRow) headerRowIndex + 1 else 0
        if (startIdx >= rawRows.size) return emptyList()

        val result = ArrayList<StatementRow>()
        for (idx in startIdx until rawRows.size) {
            if (result.size >= ImporterConstants.MAX_IMPORT_ROWS) break
            val row = rawRows[idx]
            val dateCell = row.getOrNull(mapping.dateCol)
            val isoDate = parseStatementDate(dateCell) ?: continue
            val rawLabel = row.getOrNull(mapping.labelCol).orEmpty().trim()
            if (rawLabel.isEmpty()) continue
            val normLabel = normalizeAscii(rawLabel)
            if (isSummaryLine(normLabel)) continue

            when (mapping.amountMode) {
                AmountColumnMode.SPLIT_DEBIT_CREDIT -> {
                    val debit = parseMoroccanAmount(row.getOrNull(mapping.debitCol))?.abs()?.takeIf { it.isPositive }
                    val credit = parseMoroccanAmount(row.getOrNull(mapping.creditCol))?.abs()?.takeIf { it.isPositive }
                    if (debit == null && credit == null) continue
                    result.add(
                        StatementRow(
                            isoDate = isoDate,
                            rawLabel = rawLabel,
                            debitMad = debit,
                            creditMad = credit,
                            signedAmountMad = null,
                            sourceLineNumber = idx + 1
                        )
                    )
                }
                AmountColumnMode.SINGLE_SIGNED -> {
                    val signed = parseMoroccanAmount(row.getOrNull(mapping.signedAmountCol))
                    if (signed == null || signed.isZero) continue
                    result.add(
                        StatementRow(
                            isoDate = isoDate,
                            rawLabel = rawLabel,
                            debitMad = if (signed.isNegative) signed.abs() else null,
                            creditMad = if (signed.isPositive) signed else null,
                            signedAmountMad = signed,
                            sourceLineNumber = idx + 1
                        )
                    )
                }
            }
        }
        return result
    }

    private fun findHeaderRowIndex(rawRows: List<List<String>>): Int {
        val limit = rawRows.size.coerceAtMost(15)
        for (i in 0 until limit) {
            if (isLikelyHeaderRow(rawRows[i])) return i
        }
        return 0
    }

    private fun isLikelyHeaderRow(row: List<String>): Boolean {
        if (row.size < 3) return false
        var hits = 0
        for (cell in row) {
            val norm = normalizeAscii(cell)
            if (DATE_HEADER_KEYWORDS.any { norm.contains(it) } ||
                LABEL_HEADER_KEYWORDS.any { norm.contains(it) } ||
                DEBIT_HEADER_KEYWORDS.any { norm.contains(it) } ||
                CREDIT_HEADER_KEYWORDS.any { norm.contains(it) } ||
                SIGNED_AMOUNT_HEADER_KEYWORDS.any { norm.contains(it) }
            ) {
                hits++
            }
        }
        return hits >= 2
    }

    private fun autoDetectMapping(
        bankProfile: BankProfile,
        delimiter: Char,
        hasHeader: Boolean,
        headers: List<String>,
        sampleDataRows: List<List<String>>
    ): Pair<CsvColumnMapping, Boolean> {
        val normHeaders = headers.map { normalizeAscii(it) }
        val colCount = headers.size.coerceAtLeast(3)

        var dateCol = normHeaders.indexOfFirst { h -> h.contains("date op") || h == "date" || h.startsWith("date ") }
        if (dateCol < 0) dateCol = normHeaders.indexOfFirst { h -> h.contains("date") }

        var labelCol = normHeaders.indexOfFirst { h ->
            !h.contains("date") && LABEL_HEADER_KEYWORDS.any { kw -> h.contains(kw) }
        }
        var debitCol = normHeaders.indexOfFirst { h ->
            !h.contains("date") && DEBIT_HEADER_KEYWORDS.any { kw -> h.contains(kw) }
        }
        var creditCol = normHeaders.indexOfFirst { h ->
            !h.contains("date") && CREDIT_HEADER_KEYWORDS.any { kw -> h.contains(kw) }
        }
        var signedCol = normHeaders.indexOfFirst { h ->
            !h.contains("date") && SIGNED_AMOUNT_HEADER_KEYWORDS.any { kw -> h.contains(kw) }
        }

        // Heuristic fallback from sample rows if headerless or unrecognized headers
        if (dateCol < 0 && sampleDataRows.isNotEmpty()) {
            dateCol = (0 until colCount).firstOrNull { col ->
                sampleDataRows.any { row -> parseStatementDate(row.getOrNull(col)) != null }
            } ?: 0
        }
        if (labelCol < 0) {
            labelCol = (0 until colCount).firstOrNull { col ->
                col != dateCol && col != debitCol && col != creditCol && col != signedCol &&
                    sampleDataRows.any { row ->
                        val cell = row.getOrNull(col).orEmpty()
                        cell.length >= 4 && parseStatementDate(cell) == null && parseMoroccanAmount(cell) == null
                    }
            } ?: 1.coerceAtMost(colCount - 1)
        }

        val amountMode: AmountColumnMode
        val highConfidence: Boolean
        if (debitCol >= 0 && creditCol >= 0 && debitCol != creditCol) {
            amountMode = AmountColumnMode.SPLIT_DEBIT_CREDIT
            highConfidence = hasHeader && dateCol >= 0 && labelCol >= 0
        } else if (signedCol >= 0) {
            amountMode = AmountColumnMode.SINGLE_SIGNED
            highConfidence = hasHeader && dateCol >= 0 && labelCol >= 0
        } else {
            // Try finding numeric columns in sample rows
            val numericCols = (0 until colCount).filter { col ->
                col != dateCol && col != labelCol &&
                    sampleDataRows.any { row -> parseMoroccanAmount(row.getOrNull(col)) != null }
            }
            if (numericCols.size >= 2) {
                amountMode = AmountColumnMode.SPLIT_DEBIT_CREDIT
                debitCol = numericCols[0]
                creditCol = numericCols[1]
            } else {
                amountMode = AmountColumnMode.SINGLE_SIGNED
                signedCol = numericCols.firstOrNull() ?: (colCount - 1).coerceAtLeast(0)
            }
            highConfidence = false
        }

        val safeDate = dateCol.coerceIn(0, colCount - 1)
        val safeLabel = labelCol.coerceIn(0, colCount - 1)
        val safeDebit = if (debitCol >= 0) debitCol.coerceIn(0, colCount - 1) else (colCount - 2).coerceAtLeast(0)
        val safeCredit = if (creditCol >= 0) creditCol.coerceIn(0, colCount - 1) else (colCount - 1).coerceAtLeast(0)
        val safeSigned = if (signedCol >= 0) signedCol.coerceIn(0, colCount - 1) else (colCount - 1).coerceAtLeast(0)

        return CsvColumnMapping(
            bankProfile = bankProfile,
            delimiter = delimiter,
            hasHeaderRow = hasHeader,
            dateCol = safeDate,
            labelCol = safeLabel,
            amountMode = amountMode,
            debitCol = safeDebit,
            creditCol = safeCredit,
            signedAmountCol = safeSigned
        ) to highConfidence
    }

    private fun isSummaryLine(normLabel: String): Boolean {
        return normLabel.startsWith("solde anterieur") ||
            normLabel.startsWith("solde initial") ||
            normLabel.startsWith("nouveau solde") ||
            normLabel.startsWith("solde final") ||
            normLabel.startsWith("total mouvements") ||
            normLabel.startsWith("total debit") ||
            normLabel.startsWith("total credit")
    }

    internal fun normalizeAscii(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
            .trim()
    }
}
