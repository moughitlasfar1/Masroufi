package com.masroufi.importer.presentation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.EnvelopeConfig
import com.example.data.TransactionItem
import com.example.domain.PayCycleEngine
import com.example.ui.DashboardState
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.ExpressiveGlassSurface
import com.example.ui.theme.semanticExpenseColor
import com.example.ui.theme.semanticIncomeColor
import com.example.ui.theme.semanticTransferColor
import com.masroufi.importer.data.ImporterMappingStore
import com.masroufi.importer.data.PdfExtractionOutcome
import com.masroufi.importer.data.PdfStatementExtractor
import com.masroufi.importer.data.StatementFileReader
import com.masroufi.importer.data.StatementFileTooLargeException
import com.masroufi.importer.domain.AmountColumnMode
import com.masroufi.importer.domain.BankProfile
import com.masroufi.importer.domain.CsvColumnMapping
import com.masroufi.importer.domain.CsvInspectionResult
import com.masroufi.importer.domain.CsvStatementParser
import com.masroufi.importer.domain.ImportCandidateRow
import com.masroufi.importer.domain.ImporterStrings
import com.masroufi.importer.domain.StatementPipeline
import com.masroufi.importer.domain.StatementRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ImportStep {
    PICK_FILE,
    LOADING,
    CSV_MAPPING,
    REVIEW
}

/**
 * Full Bank Statement Importer flow (CSV & PDF) modeled on Masroufi's Expressive Glass sheets.
 * Supports:
 * - Zero-permission SAF file selection (CSV & PDF)
 * - Password prompt for protected PDFs (kept in memory only)
 * - Automatic scanned-PDF OCR fallback via PdfRenderer + bundled ML Kit
 * - Per-bank CSV column mapping with encrypted SecureStore persistence
 * - Review sheet with target account selector, duplicate flagging (unticked by default),
 *   ATM transfer detection, category override per row, and chunked write progress
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementImportFlowSheet(
    state: DashboardState,
    onImportTransactions: (
        transactions: List<TransactionItem>,
        onProgress: (completed: Int, total: Int) -> Unit,
        onComplete: (importedCount: Int) -> Unit
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = state.profile.language
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val hide = state.hideAmounts
    val isDark = state.profile.isDarkTheme
    val ui = remember(lang) { ImporterStrings.forLang(lang) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val envelopes = remember(state.envelopes) { state.envelopes.map { it.config } }
    val accounts = remember(state.accounts) { state.accounts.map { it.account } }
    val defaultBankAccId = remember(accounts) {
        accounts.firstOrNull { it.kind == "bank" }?.id
            ?: accounts.firstOrNull()?.id
            ?: "acc_bank"
    }

    var step by remember { mutableStateOf(ImportStep.PICK_FILE) }
    var loadingMessage by remember { mutableStateOf(ui.parsingProgress) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Loaded file state
    var rawBytes by remember { mutableStateOf<ByteArray?>(null) }
    var loadedFileName by remember { mutableStateOf<String?>(null) }
    var isPdfSource by remember { mutableStateOf(false) }
    var usedOcrFallback by remember { mutableStateOf(false) }

    // PDF password prompt state (strictly in memory, never logged or saved)
    var showPdfPasswordDialog by remember { mutableStateOf(false) }
    var pdfPasswordInput by remember { mutableStateOf("") }
    var pdfPasswordWrong by remember { mutableStateOf(false) }

    // CSV inspection & mapping state
    var csvInspection by remember { mutableStateOf<CsvInspectionResult?>(null) }
    var currentMapping by remember { mutableStateOf(CsvColumnMapping()) }
    var rememberMapping by remember { mutableStateOf(true) }

    // Parsed rows & review candidates
    var detectedProfile by remember { mutableStateOf(BankProfile.GENERIC) }
    var extractedRows by remember { mutableStateOf<List<StatementRow>>(emptyList()) }
    var selectedBankAccountId by remember { mutableStateOf(defaultBankAccId) }
    var candidates by remember { mutableStateOf<List<ImportCandidateRow>>(emptyList()) }
    var editingCategoryCandidateId by remember { mutableStateOf<String?>(null) }

    // Chunked write progress
    var isImporting by remember { mutableStateOf(false) }
    var importCompleted by remember { mutableIntStateOf(0) }
    var importTotal by remember { mutableIntStateOf(0) }

    fun rebuildCandidates(rows: List<StatementRow>, bankAccId: String) {
        extractedRows = rows
        candidates = StatementPipeline.buildCandidates(
            rows = rows,
            existingTransactions = state.allTransactions,
            envelopes = envelopes,
            accounts = accounts,
            targetBankAccountId = bankAccId,
            lang = lang
        )
    }

    fun applyMappingAndReview(inspection: CsvInspectionResult, mapping: CsvColumnMapping, saveToStore: Boolean) {
        if (saveToStore) {
            ImporterMappingStore.saveMapping(context, mapping)
        }
        detectedProfile = mapping.bankProfile
        val rows = CsvStatementParser.extractRows(
            rawRows = inspection.allRawRows,
            mapping = mapping,
            headerRowIndex = inspection.headerRowIndex
        )
        rebuildCandidates(rows, selectedBankAccountId)
        step = ImportStep.REVIEW
    }

    fun processPdfBytes(bytes: ByteArray, password: String?) {
        step = ImportStep.LOADING
        loadingMessage = ui.parsingProgress
        errorMessage = null
        scope.launch {
            val outcome = PdfStatementExtractor.extractStatement(
                context = context,
                pdfBytes = bytes,
                password = password,
                onOcrFallbackStarted = { loadingMessage = ui.ocrPdfProgress }
            )
            // Immediately clear password from memory state after extraction attempt
            pdfPasswordInput = ""
            when (outcome) {
                is PdfExtractionOutcome.PasswordRequired -> {
                    pdfPasswordWrong = outcome.wasWrongPassword
                    showPdfPasswordDialog = true
                    step = ImportStep.PICK_FILE
                }
                is PdfExtractionOutcome.Error -> {
                    errorMessage = outcome.message
                    step = ImportStep.PICK_FILE
                }
                is PdfExtractionOutcome.Success -> {
                    detectedProfile = outcome.result.detectedBankProfile
                    usedOcrFallback = outcome.result.usedOcrFallback
                    rebuildCandidates(outcome.result.rows, selectedBankAccountId)
                    step = ImportStep.REVIEW
                }
            }
        }
    }

    fun processCsvBytes(bytes: ByteArray, delimiterOverride: Char? = null, forceShowMapping: Boolean = false) {
        step = ImportStep.LOADING
        loadingMessage = ui.parsingProgress
        errorMessage = null
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val savedMappings = ImporterMappingStore.loadAll(context)
                    CsvStatementParser.inspectCsv(
                        bytes = bytes,
                        savedMappings = savedMappings,
                        delimiterOverride = delimiterOverride
                    )
                }
            }
            result.onSuccess { inspection ->
                csvInspection = inspection
                currentMapping = inspection.suggestedMapping
                detectedProfile = inspection.detectedBankProfile
                if (!forceShowMapping && inspection.highConfidenceAutoMap) {
                    applyMappingAndReview(inspection, inspection.suggestedMapping, saveToStore = false)
                } else {
                    step = ImportStep.CSV_MAPPING
                }
            }.onFailure { err ->
                errorMessage = if (err is StatementFileTooLargeException) ui.fileTooLargeError else ui.fileReadError
                step = ImportStep.PICK_FILE
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        step = ImportStep.LOADING
        loadingMessage = ui.parsingProgress
        errorMessage = null
        usedOcrFallback = false
        scope.launch {
            runCatching {
                StatementFileReader.readUriBounded(context, uri)
            }.onSuccess { loaded ->
                rawBytes = loaded.bytes
                loadedFileName = loaded.displayName
                isPdfSource = loaded.isPdf
                if (loaded.isPdf) {
                    processPdfBytes(loaded.bytes, password = null)
                } else {
                    processCsvBytes(loaded.bytes)
                }
            }.onFailure { err ->
                errorMessage = if (err is StatementFileTooLargeException) ui.fileTooLargeError else ui.fileReadError
                step = ImportStep.PICK_FILE
            }
        }
    }

    // Internal back navigation between steps
    BackHandler(enabled = step == ImportStep.CSV_MAPPING || step == ImportStep.REVIEW) {
        if (step == ImportStep.REVIEW && !isPdfSource && csvInspection != null) {
            step = ImportStep.CSV_MAPPING
        } else {
            step = ImportStep.PICK_FILE
        }
    }

    // PDF password prompt dialog
    if (showPdfPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                pdfPasswordInput = ""
                showPdfPasswordDialog = false
            },
            modifier = Modifier.testTag("importer_pdf_password_dialog"),
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            title = { Text(ui.pdfPasswordTitle, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ui.pdfPasswordHint, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = pdfPasswordInput,
                        onValueChange = {
                            pdfPasswordInput = it
                            pdfPasswordWrong = false
                        },
                        label = { Text(ui.pdfPasswordPlaceholder) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = pdfPasswordWrong,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("importer_pdf_password_input")
                    )
                    if (pdfPasswordWrong) {
                        Text(
                            text = ui.pdfPasswordWrong,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pwd = pdfPasswordInput
                        showPdfPasswordDialog = false
                        rawBytes?.let { processPdfBytes(it, pwd) }
                    },
                    enabled = pdfPasswordInput.isNotEmpty(),
                    modifier = Modifier.testTag("importer_pdf_unlock_btn")
                ) {
                    Text(ui.unlockBtn, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pdfPasswordInput = ""
                        showPdfPasswordDialog = false
                    }
                ) {
                    Text(ui.cancelBtn)
                }
            }
        )
    }

    // Category picker dialog for individual rows in Review step
    val editingRow = candidates.firstOrNull { it.deterministicId == editingCategoryCandidateId }
    if (editingRow != null) {
        AlertDialog(
            onDismissRequest = { editingCategoryCandidateId = null },
            modifier = Modifier.testTag("importer_category_picker_dialog"),
            title = { Text(ui.changeCategoryTitle, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    envelopes.forEach { env: EnvelopeConfig ->
                        FilterChip(
                            selected = editingRow.envelopeKey == env.key,
                            onClick = {
                                val targetId = editingRow.deterministicId
                                candidates = candidates.map { c ->
                                    if (c.deterministicId == targetId) {
                                        c.copy(envelopeKey = env.key, categoryName = env.localizedName(lang))
                                    } else c
                                }
                                editingCategoryCandidateId = null
                            },
                            label = { Text(env.localizedName(lang), fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingCategoryCandidateId = null }) {
                    Text(ui.cancelBtn)
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isImporting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("statement_import_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = if (step == ImportStep.CSV_MAPPING) ui.mappingTitle else ui.sheetTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        loadedFileName?.let { fname ->
                            Text(
                                text = fname,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (step == ImportStep.REVIEW && !isPdfSource && csvInspection != null) {
                    TextButton(
                        onClick = { step = ImportStep.CSV_MAPPING },
                        modifier = Modifier.testTag("importer_edit_columns_btn")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(ui.editMappingBtn, fontWeight = FontWeight.Bold)
                    }
                }
            }

            when (step) {
                ImportStep.PICK_FILE -> {
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(22.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = ui.sheetSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = ui.pickFileHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            errorMessage?.let { err ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    openDocumentLauncher.launch(
                                        arrayOf(
                                            "text/csv",
                                            "text/comma-separated-values",
                                            "application/csv",
                                            "text/plain",
                                            "application/pdf",
                                            "*/*"
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("importer_pick_file_btn")
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(ui.pickFileBtn, fontWeight = FontWeight.ExtraBold)
                            }
                            Text(
                                text = ui.offlinePrivacyNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentEmerald,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                ImportStep.LOADING -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = loadingMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                ImportStep.CSV_MAPPING -> {
                    val inspection = csvInspection
                    if (inspection != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 520.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = ui.mappingSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Bank profile selector
                            Text(ui.bankProfileLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BankProfile.entries.forEach { profile ->
                                    FilterChip(
                                        selected = currentMapping.bankProfile == profile,
                                        onClick = {
                                            currentMapping = currentMapping.copy(bankProfile = profile)
                                        },
                                        label = { Text(profile.displayName, fontSize = 12.sp) }
                                    )
                                }
                            }

                            // Delimiter & Header toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ui.delimiterLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                listOf(';' to " ; ", ',' to " , ").forEach { (delim, label) ->
                                    FilterChip(
                                        selected = currentMapping.delimiter == delim,
                                        onClick = {
                                            rawBytes?.let { bytes ->
                                                processCsvBytes(bytes, delimiterOverride = delim, forceShowMapping = true)
                                            }
                                        },
                                        label = { Text(label, fontWeight = FontWeight.ExtraBold) }
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text(ui.hasHeaderLabel, style = MaterialTheme.typography.labelSmall)
                                Switch(
                                    checked = currentMapping.hasHeaderRow,
                                    onCheckedChange = { currentMapping = currentMapping.copy(hasHeaderRow = it) }
                                )
                            }

                            // Amount mode toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = currentMapping.amountMode == AmountColumnMode.SPLIT_DEBIT_CREDIT,
                                    onClick = { currentMapping = currentMapping.copy(amountMode = AmountColumnMode.SPLIT_DEBIT_CREDIT) },
                                    label = { Text(ui.amountModeSplit, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = currentMapping.amountMode == AmountColumnMode.SINGLE_SIGNED,
                                    onClick = { currentMapping = currentMapping.copy(amountMode = AmountColumnMode.SINGLE_SIGNED) },
                                    label = { Text(ui.amountModeSingle, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Column selectors
                            ColumnIndexChipRow(
                                label = ui.dateColLabel,
                                headers = inspection.headers,
                                selectedIndex = currentMapping.dateCol,
                                onSelect = { currentMapping = currentMapping.copy(dateCol = it) }
                            )
                            ColumnIndexChipRow(
                                label = ui.labelColLabel,
                                headers = inspection.headers,
                                selectedIndex = currentMapping.labelCol,
                                onSelect = { currentMapping = currentMapping.copy(labelCol = it) }
                            )
                            if (currentMapping.amountMode == AmountColumnMode.SPLIT_DEBIT_CREDIT) {
                                ColumnIndexChipRow(
                                    label = ui.debitColLabel,
                                    headers = inspection.headers,
                                    selectedIndex = currentMapping.debitCol,
                                    onSelect = { currentMapping = currentMapping.copy(debitCol = it) }
                                )
                                ColumnIndexChipRow(
                                    label = ui.creditColLabel,
                                    headers = inspection.headers,
                                    selectedIndex = currentMapping.creditCol,
                                    onSelect = { currentMapping = currentMapping.copy(creditCol = it) }
                                )
                            } else {
                                ColumnIndexChipRow(
                                    label = ui.signedAmountColLabel,
                                    headers = inspection.headers,
                                    selectedIndex = currentMapping.signedAmountCol,
                                    onSelect = { currentMapping = currentMapping.copy(signedAmountCol = it) }
                                )
                            }

                            // Save per-bank mapping switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ui.saveMappingLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(checked = rememberMapping, onCheckedChange = { rememberMapping = it })
                            }

                            // Preview of first 3 rows
                            if (inspection.previewRows.isNotEmpty()) {
                                Text(ui.previewRowsLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                ExpressiveGlassSurface(
                                    shape = RoundedCornerShape(14.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        inspection.previewRows.take(3).forEach { row ->
                                            val d = row.getOrNull(currentMapping.dateCol).orEmpty()
                                            val l = row.getOrNull(currentMapping.labelCol).orEmpty()
                                            val a = if (currentMapping.amountMode == AmountColumnMode.SPLIT_DEBIT_CREDIT) {
                                                val deb = row.getOrNull(currentMapping.debitCol).orEmpty()
                                                val cr = row.getOrNull(currentMapping.creditCol).orEmpty()
                                                "D:$deb / C:$cr"
                                            } else {
                                                row.getOrNull(currentMapping.signedAmountCol).orEmpty()
                                            }
                                            Text(
                                                text = "$d • $l • $a",
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { applyMappingAndReview(inspection, currentMapping, rememberMapping) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("importer_continue_review_btn")
                        ) {
                            Text(ui.continueToReviewBtn, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                ImportStep.REVIEW -> {
                    val selectedCount = candidates.count { it.isSelected }
                    val duplicateCount = candidates.count { it.isProbableDuplicate }

                    // Detected bank badge & target bank account selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = detectedProfile.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        if (usedOcrFallback) {
                            Surface(
                                shape = CircleShape,
                                color = AccentAmber.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = ui.ocrFallbackBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentAmber,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                        if (duplicateCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = AccentAmber.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = ui.duplicateSummaryFormat(duplicateCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentAmber,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    // Target bank account chips
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = ui.targetAccountLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            accounts.forEach { acc ->
                                FilterChip(
                                    selected = selectedBankAccountId == acc.id,
                                    onClick = {
                                        if (selectedBankAccountId != acc.id) {
                                            selectedBankAccountId = acc.id
                                            rebuildCandidates(extractedRows, acc.id)
                                        }
                                    },
                                    label = { Text(acc.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                                )
                            }
                        }
                    }

                    if (candidates.isEmpty()) {
                        ExpressiveGlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(ui.noRowsFoundTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    ui.noRowsFoundBody,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { step = ImportStep.PICK_FILE },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(ui.pickAnotherFileBtn)
                                }
                            }
                        }
                    } else {
                        // Select new / Clear quick toolbar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    candidates = candidates.map { it.copy(isSelected = !it.isProbableDuplicate) }
                                }
                            ) {
                                Text(ui.selectAllNewBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = {
                                    candidates = candidates.map { it.copy(isSelected = false) }
                                }
                            ) {
                                Text(ui.deselectAllBtn, fontSize = 12.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "$selectedCount / ${candidates.size}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }

                        // Scrollable candidate review list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .testTag("importer_review_list"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(candidates, key = { it.deterministicId }) { row ->
                                val amountColor = when (row.txType) {
                                    "income" -> semanticIncomeColor(isDark)
                                    "transfer" -> semanticTransferColor(isDark)
                                    else -> semanticExpenseColor(isDark)
                                }
                                val prefix = when (row.txType) {
                                    "income" -> "+"
                                    "transfer" -> "⇄ "
                                    else -> "-"
                                }
                                ExpressiveGlassSurface(
                                    shape = RoundedCornerShape(16.dp),
                                    containerColor = if (row.isSelected) {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("importer_row_${row.deterministicId}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                candidates = candidates.map {
                                                    if (it.deterministicId == row.deterministicId) it.copy(isSelected = !it.isSelected)
                                                    else it
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = row.isSelected,
                                            onCheckedChange = { checked ->
                                                candidates = candidates.map {
                                                    if (it.deterministicId == row.deterministicId) it.copy(isSelected = checked)
                                                    else it
                                                }
                                            }
                                        )
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = row.cleanedLabel,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = row.isoDate,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (row.isAtmTransfer) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = semanticTransferColor(isDark).copy(alpha = 0.16f)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.SwapHoriz,
                                                                contentDescription = null,
                                                                tint = semanticTransferColor(isDark),
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                text = ui.atmTransferBadge,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = semanticTransferColor(isDark)
                                                            )
                                                        }
                                                    }
                                                } else if (row.txType == "expense") {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .clickable { editingCategoryCandidateId = row.deterministicId }
                                                    ) {
                                                        Text(
                                                            text = row.categoryName,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (row.isProbableDuplicate) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = AccentAmber.copy(alpha = 0.2f)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.WarningAmber,
                                                                contentDescription = null,
                                                                tint = AccentAmber,
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                            Text(
                                                                text = ui.duplicateBadge,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = AccentAmber
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Text(
                                            text = if (hide) "••••" else "$prefix${PayCycleEngine.formatDh(row.amountMad)} $cur",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = amountColor
                                        )
                                    }
                                }
                            }
                        }

                        if (isImporting && importTotal > 0) {
                            LinearProgressIndicator(
                                progress = { importCompleted.toFloat() / importTotal.coerceAtLeast(1).toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                val txs = StatementPipeline.toTransactionItems(
                                    candidates = candidates,
                                    userId = state.profile.userId
                                )
                                if (txs.isEmpty()) return@Button
                                isImporting = true
                                importCompleted = 0
                                importTotal = txs.size
                                onImportTransactions(
                                    txs,
                                    { done, total ->
                                        importCompleted = done
                                        importTotal = total
                                    },
                                    {
                                        isImporting = false
                                        onDismiss()
                                    }
                                )
                            },
                            enabled = selectedCount > 0 && !isImporting,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("importer_confirm_import_btn")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = ui.importActionFormat(selectedCount),
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnIndexChipRow(
    label: String,
    headers: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            headers.forEachIndexed { idx, header ->
                FilterChip(
                    selected = selectedIndex == idx,
                    onClick = { onSelect(idx) },
                    label = { Text("${idx + 1}: $header", fontSize = 11.sp, maxLines = 1) }
                )
            }
        }
    }
}
