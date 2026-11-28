package com.masroufi.importer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.platform.PlatformReceiptOcrBridge
import com.example.security.SecureStore
import com.masroufi.importer.domain.AmountColumnMode
import com.masroufi.importer.domain.BankProfile
import com.masroufi.importer.domain.CsvColumnMapping
import com.masroufi.importer.domain.ImporterConstants
import com.masroufi.importer.domain.ParsedReceipt
import com.masroufi.importer.domain.PdfParseResult
import com.masroufi.importer.domain.PdfStatementParser
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class StatementFileTooLargeException(val maxBytes: Int) :
    IllegalArgumentException("Statement file exceeds $maxBytes bytes limit")

/**
 * Persists per-bank CSV column mappings encrypted at rest inside [SecureStore].
 */
object ImporterMappingStore {
    private const val STORE_KEY = "importer_csv_mappings_v1"

    fun loadAll(context: Context): Map<BankProfile, CsvColumnMapping> {
        val raw = SecureStore.getString(context, STORE_KEY) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val result = LinkedHashMap<BankProfile, CsvColumnMapping>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val bankId = keys.next()
                val obj = root.optJSONObject(bankId) ?: continue
                val profile = BankProfile.fromId(bankId)
                val delimStr = obj.optString("delimiter", ";")
                val modeStr = obj.optString("amountMode", AmountColumnMode.SPLIT_DEBIT_CREDIT.name)
                val mode = runCatching { AmountColumnMode.valueOf(modeStr) }
                    .getOrDefault(AmountColumnMode.SPLIT_DEBIT_CREDIT)
                result[profile] = CsvColumnMapping(
                    bankProfile = profile,
                    delimiter = delimStr.firstOrNull() ?: ';',
                    hasHeaderRow = obj.optBoolean("hasHeaderRow", true),
                    dateCol = obj.optInt("dateCol", 0).coerceAtLeast(0),
                    labelCol = obj.optInt("labelCol", 1).coerceAtLeast(0),
                    amountMode = mode,
                    debitCol = obj.optInt("debitCol", 2).coerceAtLeast(0),
                    creditCol = obj.optInt("creditCol", 3).coerceAtLeast(0),
                    signedAmountCol = obj.optInt("signedAmountCol", 2).coerceAtLeast(0)
                )
            }
            result
        }.getOrDefault(emptyMap())
    }

    fun saveMapping(context: Context, mapping: CsvColumnMapping): Boolean {
        val current = loadAll(context).toMutableMap()
        current[mapping.bankProfile] = mapping
        val root = JSONObject()
        current.forEach { (profile, m) ->
            root.put(
                profile.id,
                JSONObject().apply {
                    put("delimiter", m.delimiter.toString())
                    put("hasHeaderRow", m.hasHeaderRow)
                    put("dateCol", m.dateCol)
                    put("labelCol", m.labelCol)
                    put("amountMode", m.amountMode.name)
                    put("debitCol", m.debitCol)
                    put("creditCol", m.creditCol)
                    put("signedAmountCol", m.signedAmountCol)
                }
            )
        }
        return SecureStore.putString(context, STORE_KEY, root.toString())
    }

    fun clearAll(context: Context) {
        SecureStore.remove(context, STORE_KEY)
    }
}

/**
 * Strictly confined temporary file manager under `cacheDir/receipts/` exposed via FileProvider.
 * Ensures every temp receipt image or unlocked scanned PDF temp file is confined by canonical path
 * and deleted immediately after OCR.
 */
object ReceiptFileHelper {
    private const val RECEIPTS_SUBDIR = "receipts"

    fun receiptsDir(context: Context): File {
        val dir = File(context.applicationContext.cacheDir, RECEIPTS_SUBDIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createConfinedTempFile(
        context: Context,
        prefix: String = "rcpt_",
        extension: String = ".jpg"
    ): File {
        val dir = receiptsDir(context)
        val safePrefix = prefix.replace(Regex("[^a-zA-Z0-9_\\-]"), "")
        val safeExt = if (extension.startsWith(".")) extension else ".$extension"
        require(safeExt.matches(Regex("^\\.[a-zA-Z0-9]{1,6}$"))) { "Invalid temp file extension" }

        val candidate = File(dir, "${safePrefix}${UUID.randomUUID().toString().replace("-", "").take(16)}$safeExt")
        val dirCanonical = dir.canonicalPath
        val fileCanonical = candidate.canonicalPath
        require(fileCanonical.startsWith(dirCanonical + File.separator)) {
            "Temp file escaped confined receipts cache directory"
        }
        return candidate
    }

    fun createReceiptPhotoUri(context: Context): Pair<File, Uri> {
        val file = createConfinedTempFile(context, prefix = "rcpt_", extension = ".jpg")
        val authority = "${context.applicationContext.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context.applicationContext, authority, file)
        return file to uri
    }

    fun deleteQuietly(file: File?) {
        if (file == null) return
        runCatching {
            if (file.exists()) {
                file.delete()
            }
        }
    }

    fun clearReceiptCache(context: Context) {
        runCatching {
            val dir = File(context.applicationContext.cacheDir, RECEIPTS_SUBDIR)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { child ->
                    runCatching { child.delete() }
                }
            }
        }
    }
}

/**
 * Bounded SAF content reader (prevents OOM / DoS on oversized files).
 */
object StatementFileReader {

    data class LoadedFile(
        val bytes: ByteArray,
        val displayName: String?,
        val mimeType: String?,
        val isPdf: Boolean
    )

    suspend fun readUriBounded(
        context: Context,
        uri: Uri,
        maxBytes: Int = ImporterConstants.MAX_IMPORT_FILE_BYTES
    ): LoadedFile = withContext(Dispatchers.IO) {
        val resolver = context.applicationContext.contentResolver
        val mimeType = runCatching { resolver.getType(uri) }.getOrNull()
        val displayName = queryDisplayName(context, uri)

        val stream = resolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open selected document stream")

        val out = ByteArrayOutputStream()
        stream.use { input ->
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                total += read
                if (total > maxBytes) {
                    throw StatementFileTooLargeException(maxBytes)
                }
                out.write(buf, 0, read)
            }
        }
        val bytes = out.toByteArray()
        LoadedFile(
            bytes = bytes,
            displayName = displayName,
            mimeType = mimeType,
            isPdf = isLikelyPdf(bytes, displayName, mimeType)
        )
    }

    fun isLikelyPdf(bytes: ByteArray, displayName: String?, mimeType: String?): Boolean {
        val hasMagic = bytes.size >= 5 &&
            bytes[0] == '%'.code.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'D'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[4] == '-'.code.toByte()
        if (hasMagic) return true
        if (mimeType.equals("application/pdf", ignoreCase = true)) return true
        return displayName?.lowercase()?.endsWith(".pdf") == true
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.applicationContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()
    }
}

/**
 * Flavor-aware receipt and bitmap OCR facade delegating to [PlatformReceiptOcrBridge]
 * (bundled ML Kit in Play flavor; graceful no-op in FOSS flavor).
 */
object ReceiptOcrEngine {

    val isSupported: Boolean
        get() = PlatformReceiptOcrBridge.isReceiptOcrSupported

    suspend fun scanReceiptFromUri(
        context: Context,
        uri: Uri,
        tempFileToDelete: File? = null
    ): ParsedReceipt = PlatformReceiptOcrBridge.scanReceiptFromUri(
        context = context,
        uri = uri,
        tempFileToDelete = tempFileToDelete
    )

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String =
        PlatformReceiptOcrBridge.recognizeTextFromBitmap(bitmap)
}

sealed interface PdfExtractionOutcome {
    data class Success(val result: PdfParseResult) : PdfExtractionOutcome
    data class PasswordRequired(val wasWrongPassword: Boolean) : PdfExtractionOutcome
    data class Error(val message: String) : PdfExtractionOutcome
}

/**
 * Phase 3: On-device PDF statement extractor.
 * 1. Tries PDFBox-Android text layer extraction first (fast, exact).
 * 2. Prompts for password if encrypted (never logs or stores the password).
 * 3. Falls back to Android [PdfRenderer] + ML Kit OCR for scanned image-only PDFs.
 */
object PdfStatementExtractor {

    suspend fun extractStatement(
        context: Context,
        pdfBytes: ByteArray,
        password: String? = null,
        onOcrFallbackStarted: () -> Unit = {}
    ): PdfExtractionOutcome = withContext(Dispatchers.IO) {
        if (pdfBytes.size > ImporterConstants.MAX_IMPORT_FILE_BYTES) {
            return@withContext PdfExtractionOutcome.Error("File exceeds 15 MB limit")
        }

        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }

        var document: PDDocument? = null
        var tempPdfForRenderer: File? = null
        try {
            document = if (password.isNullOrEmpty()) {
                PDDocument.load(ByteArrayInputStream(pdfBytes))
            } else {
                PDDocument.load(ByteArrayInputStream(pdfBytes), password)
            }

            val totalPages = document.numberOfPages
            val pagesToRead = totalPages.coerceAtMost(ImporterConstants.MAX_PDF_PAGES_TEXT)

            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                startPage = 1
                endPage = pagesToRead
            }
            val extractedText = stripper.getText(document).orEmpty()
            val alphaNumCount = extractedText.count { it.isLetterOrDigit() }

            if (alphaNumCount >= 40) {
                val parsed = PdfStatementParser.parseText(
                    fullText = extractedText,
                    usedOcrFallback = false,
                    pagesProcessed = pagesToRead
                )
                return@withContext PdfExtractionOutcome.Success(parsed)
            }

            // Text layer empty or minimal -> scanned PDF fallback (Play OCR only; FOSS fails gracefully)
            if (!PlatformReceiptOcrBridge.isReceiptOcrSupported) {
                return@withContext PdfExtractionOutcome.Error(
                    "Scanned image-only PDFs require OCR (unavailable in FOSS build). Please import a digital PDF or CSV statement."
                )
            }
            withContext(Dispatchers.Main) { onOcrFallbackStarted() }

            tempPdfForRenderer = ReceiptFileHelper.createConfinedTempFile(
                context = context,
                prefix = "pdf_ocr_",
                extension = ".pdf"
            )
            if (document.isEncrypted) {
                document.isAllSecurityToBeRemoved = true
                document.save(tempPdfForRenderer)
            } else {
                FileOutputStream(tempPdfForRenderer).use { fos ->
                    fos.write(pdfBytes)
                }
            }

            // Close PDFBox document before opening PdfRenderer to reduce peak memory usage
            runCatching { document.close() }
            document = null

            val ocrText = renderPdfPagesWithOcr(tempPdfForRenderer)
            val parsedOcr = PdfStatementParser.parseText(
                fullText = ocrText.first,
                usedOcrFallback = true,
                pagesProcessed = ocrText.second
            )
            PdfExtractionOutcome.Success(parsedOcr)
        } catch (_: InvalidPasswordException) {
            PdfExtractionOutcome.PasswordRequired(wasWrongPassword = !password.isNullOrEmpty())
        } catch (t: Throwable) {
            PdfExtractionOutcome.Error(t.localizedMessage ?: "PDF extraction error")
        } finally {
            runCatching { document?.close() }
            ReceiptFileHelper.deleteQuietly(tempPdfForRenderer)
        }
    }

    private suspend fun renderPdfPagesWithOcr(pdfFile: File): Pair<String, Int> {
        val sb = StringBuilder()
        var renderedCount = 0
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pageLimit = renderer.pageCount.coerceAtMost(ImporterConstants.MAX_PDF_PAGES_OCR)
                for (i in 0 until pageLimit) {
                    renderer.openPage(i).use { page ->
                        val targetWidth = (page.width * 2).coerceIn(600, 1600)
                        val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1).toFloat()
                        val targetHeight = (page.height * scale).toInt().coerceIn(600, 2400)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        try {
                            Canvas(bitmap).drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            val pageText = ReceiptOcrEngine.recognizeTextFromBitmap(bitmap)
                            if (pageText.isNotBlank()) {
                                sb.append(pageText).append('\n')
                            }
                            renderedCount++
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
        return sb.toString() to renderedCount
    }
}
