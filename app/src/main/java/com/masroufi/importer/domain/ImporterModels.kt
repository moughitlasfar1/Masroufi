package com.masroufi.importer.domain

import java.security.MessageDigest
import java.util.Locale

enum class BankProfile(
    val id: String,
    val displayName: String,
    val defaultDelimiter: Char = ';',
    val keywords: List<String> = emptyList()
) {
    BCP(
        id = "bcp",
        displayName = "Banque Populaire (BCP)",
        defaultDelimiter = ';',
        keywords = listOf("banque populaire", "bcp", "chaabi", "gbp", "pocket bank")
    ),
    CIH(
        id = "cih",
        displayName = "CIH Bank",
        defaultDelimiter = ';',
        keywords = listOf("cih bank", "cih online", "cih mobile", "credit immobilier et hotelier")
    ),
    ATTIJARIWAFA(
        id = "attijariwafa",
        displayName = "Attijariwafa Bank",
        defaultDelimiter = ';',
        keywords = listOf("attijariwafa", "attijari", "l'bankalik", "bankalik", "awb")
    ),
    BMCE(
        id = "bmce",
        displayName = "Bank of Africa (BMCE)",
        defaultDelimiter = ';',
        keywords = listOf("bank of africa", "bmce", "boa", "daba pay", "bmce direct")
    ),
    CFG_SG(
        id = "cfg_sg",
        displayName = "CFG Bank / Société Générale",
        defaultDelimiter = ';',
        keywords = listOf("cfg bank", "societe generale", "société générale", "sgmb", "sg maroc", "so Gé", "saham bank")
    ),
    GENERIC(
        id = "generic",
        displayName = "Autre banque (Générique)",
        defaultDelimiter = ';',
        keywords = emptyList()
    );

    companion object {
        fun fromId(id: String?): BankProfile =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GENERIC

        fun detectFromText(sampleText: String): BankProfile {
            val lower = sampleText.lowercase(Locale.ROOT)
            return entries.firstOrNull { profile ->
                profile != GENERIC && profile.keywords.any { kw -> lower.contains(kw) }
            } ?: GENERIC
        }
    }
}

enum class AmountColumnMode {
    SPLIT_DEBIT_CREDIT,
    SINGLE_SIGNED
}

data class CsvColumnMapping(
    val bankProfile: BankProfile = BankProfile.GENERIC,
    val delimiter: Char = ';',
    val hasHeaderRow: Boolean = true,
    val dateCol: Int = 0,
    val labelCol: Int = 1,
    val amountMode: AmountColumnMode = AmountColumnMode.SPLIT_DEBIT_CREDIT,
    val debitCol: Int = 2,
    val creditCol: Int = 3,
    val signedAmountCol: Int = 2
)

data class CsvInspectionResult(
    val encodingName: String,
    val delimiter: Char,
    val detectedBankProfile: BankProfile,
    val headerRowIndex: Int,
    val headers: List<String>,
    val previewRows: List<List<String>>,
    val allRawRows: List<List<String>>,
    val suggestedMapping: CsvColumnMapping,
    val highConfidenceAutoMap: Boolean,
    val usedSavedMapping: Boolean = false
)

data class StatementRow(
    val isoDate: String,
    val rawLabel: String,
    val debitMad: Double? = null,
    val creditMad: Double? = null,
    val signedAmountMad: Double? = null,
    val sourceLineNumber: Int = 0
)

data class ImportCandidateRow(
    val deterministicId: String,
    val isoDate: String,
    val rawLabel: String,
    val cleanedLabel: String,
    val txType: String, // "expense", "income", "transfer"
    val amountMad: Double,
    val accountId: String,
    val toAccountId: String = "",
    val envelopeKey: String,
    val categoryName: String,
    val isAtmTransfer: Boolean = false,
    val isProbableDuplicate: Boolean = false,
    val isSelected: Boolean = true
)

data class ParsedReceipt(
    val totalAmountMad: Double?,
    val isoDate: String?,
    val merchantName: String?,
    val rawLineCount: Int,
    val arabicWeakHint: Boolean = false
)

data class PdfParseResult(
    val detectedBankProfile: BankProfile,
    val rows: List<StatementRow>,
    val usedOcrFallback: Boolean = false,
    val pagesProcessed: Int = 0
)

object ImporterConstants {
    const val MAX_IMPORT_FILE_BYTES: Int = 15 * 1024 * 1024 // 15 MB
    const val MAX_IMPORT_ROWS: Int = 5_000
    const val MAX_PDF_PAGES_TEXT: Int = 60
    const val MAX_PDF_PAGES_OCR: Int = 20
    const val MAX_LABEL_LENGTH: Int = 96
    const val IMPORT_CHUNK_SIZE: Int = 25

    private val BIDI_AND_INVISIBLE_CHARS = setOf(
        '\u200E', '\u200F', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
        '\u2066', '\u2067', '\u2068', '\u2069', '\uFEFF'
    )

    private val MULTI_SPACE_REGEX = Regex("\\s+")

    /**
     * Strips control characters, Unicode BiDi override characters, normalizes whitespace,
     * and enforces a bounded display length to prevent UI/CSV spoofing or memory bloat.
     */
    fun sanitizeLabel(raw: String, maxLength: Int = MAX_LABEL_LENGTH): String {
        val sb = StringBuilder(raw.length.coerceAtMost(maxLength * 2))
        for (ch in raw) {
            if (Character.isISOControl(ch)) {
                if (ch == '\n' || ch == '\r' || ch == '\t') sb.append(' ')
                continue
            }
            if (ch in BIDI_AND_INVISIBLE_CHARS) continue
            sb.append(ch)
        }
        return sb.toString()
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
            .take(maxLength)
    }

    /**
     * Generates a deterministic, Room/Firestore-safe ID (`tx_imp_<16-hex>`) from
     * date + amount + normalized description + accountId (+ occurrence index for same-day twins).
     * Re-importing the exact same statement updates existing rows cleanly without creating duplicates.
     */
    fun buildDeterministicId(
        isoDate: String,
        amountMad: Double,
        normalizedLabel: String,
        accountId: String,
        occurrenceIndex: Int = 0
    ): String {
        val canonicalAmount = String.format(Locale.US, "%.2f", amountMad)
        val canonicalLabel = sanitizeLabel(normalizedLabel)
            .lowercase(Locale.ROOT)
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
        val payload = buildString {
            append(isoDate.trim())
            append('|')
            append(canonicalAmount)
            append('|')
            append(canonicalLabel)
            append('|')
            append(accountId.trim())
            if (occurrenceIndex > 0) {
                append('|')
                append(occurrenceIndex)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(16)
        for (i in 0 until 8) {
            hex.append(String.format(Locale.US, "%02x", digest[i]))
        }
        return "tx_imp_$hex"
    }
}
