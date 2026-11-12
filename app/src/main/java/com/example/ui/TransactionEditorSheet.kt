package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DefaultCategoryResolver
import com.example.data.EnvelopeConfig
import com.example.data.TransactionItem
import com.example.domain.AppStrings
import com.example.domain.PayCycleEngine
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.ExpressiveGlassSurface
import com.example.ui.theme.ExpressivePillChip
import com.example.ui.theme.semanticExpenseColor
import com.example.ui.theme.semanticIncomeColor
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorSheet(
    existingTx: TransactionItem?,
    initialType: String,
    state: DashboardState,
    strings: AppStrings,
    autoLaunchVoice: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, String, String, String, String, String) -> Unit,
    onDelete: ((TransactionItem) -> Unit)? = null
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val lang = state.profile.language
    val haptics = LocalHapticFeedback.current

    val initialSmartDefault = remember(existingTx) {
        if (existingTx == null) {
            PayCycleEngine.inferSmartDefaults(
                description = "",
                allTransactions = state.allTransactions,
                envelopes = state.envelopes.map { it.config },
                accounts = state.accounts.map { it.account },
                lang = lang
            )
        } else null
    }

    var txType by remember { mutableStateOf(existingTx?.type ?: initialType) }
    var amountText by remember {
        mutableStateOf(
            existingTx?.amount?.let {
                if (it > 0) {
                    if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                } else ""
            } ?: ""
        )
    }
    val todayIso = remember { PayCycleEngine.todayIso() }
    val yesterdayIso = remember(todayIso) {
        PayCycleEngine.epochDaysToIso(PayCycleEngine.parseEpochDays(todayIso) - 1L)
    }
    var isoDate by remember { mutableStateOf(existingTx?.isoDate ?: todayIso) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    var selectedEnvelopeKey by remember {
        mutableStateOf(
            existingTx?.envelopeKey?.ifBlank { null }
                ?: initialSmartDefault?.envelopeKey
                ?: state.envelopes.firstOrNull()?.config?.key
                ?: "transport"
        )
    }
    var customCategory by remember {
        mutableStateOf(
            existingTx?.category
                ?: initialSmartDefault?.categoryName
                ?: state.envelopes.firstOrNull()?.config?.localizedName(state.profile.language)
                ?: "Transport"
        )
    }
    var selectedAccountId by remember {
        mutableStateOf(
            existingTx?.accountId
                ?: initialSmartDefault?.accountId
                ?: state.accounts.firstOrNull()?.account?.id
                ?: "acc_cash"
        )
    }
    var toAccountId by remember {
        mutableStateOf(
            existingTx?.toAccountId?.ifBlank { null }
                ?: state.accounts.getOrNull(1)?.account?.id
                ?: "acc_bank"
        )
    }
    var desc by remember { mutableStateOf(existingTx?.desc ?: "") }
    var twoTapInstantSave by remember { mutableStateOf(existingTx == null) }
    var showAdvancedFields by remember { mutableStateOf(existingTx != null) }
    var showVoiceSection by remember { mutableStateOf(autoLaunchVoice) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var smartDefaultHint by remember { mutableStateOf<String?>(null) }
    var duplicateWarningTx by remember { mutableStateOf<TransactionItem?>(null) }

    val baseCurrencyCode = PayCycleEngine.normalizeCurrencyCode(cur)
    var entryCurrency by remember { mutableStateOf(baseCurrencyCode) }
    var eurRateText by remember { mutableStateOf(PayCycleEngine.DEFAULT_EUR_TO_MAD.toString()) }
    var usdRateText by remember { mutableStateOf(PayCycleEngine.DEFAULT_USD_TO_MAD.toString()) }

    val recentCategories = remember(state.allTransactions, state.envelopes, lang) {
        computeTopThreeRecentCategories(state)
    }

    fun computeConvertedAmount(): Double {
        val rawAmt = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
        val eurRate = eurRateText.replace(",", ".").toDoubleOrNull() ?: PayCycleEngine.DEFAULT_EUR_TO_MAD
        val usdRate = usdRateText.replace(",", ".").toDoubleOrNull() ?: PayCycleEngine.DEFAULT_USD_TO_MAD
        return PayCycleEngine.convertCurrencyAmount(
            amount = rawAmt,
            fromCurrency = entryCurrency,
            toCurrency = baseCurrencyCode,
            eurToMadRate = eurRate,
            usdToMadRate = usdRate
        )
    }

    fun tryCommitTransaction(
        overrideCategory: String = customCategory,
        overrideEnvKey: String = selectedEnvelopeKey,
        ignoreDuplicateCheck: Boolean = false
    ): Boolean {
        val rawAmt = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
        val convertedAmt = computeConvertedAmount()
        val normalizedDate = isoDate.trim().ifEmpty { todayIso }
        if (!PayCycleEngine.isValidIsoDate(normalizedDate)) {
            dateError = "Date invalide ('$normalizedDate')."
            showAdvancedFields = true
            return false
        }
        if (txType == "transfer" && (toAccountId.isBlank() || toAccountId == selectedAccountId)) {
            dateError = "Veuillez sélectionner un compte destinataire différent du compte source."
            return false
        }
        if (convertedAmt <= 0.0) {
            return false
        }

        if (existingTx == null && !ignoreDuplicateCheck) {
            val dup = PayCycleEngine.findRecentDuplicateTransaction(
                type = txType,
                amount = convertedAmt,
                envelopeKey = if (txType == "transfer") "autre" else overrideEnvKey,
                category = if (txType == "transfer") "Transfert" else overrideCategory,
                allTransactions = state.allTransactions,
                nowIso = normalizedDate
            )
            if (dup != null) {
                duplicateWarningTx = dup
                return false
            }
        }

        dateError = null
        duplicateWarningTx = null
        val baseDesc = desc.ifBlank { if (txType == "transfer") "Transfert entre comptes" else overrideCategory }
        val finalDesc = if (entryCurrency != baseCurrencyCode && rawAmt > 0.0) {
            "$baseDesc (${PayCycleEngine.formatDh(rawAmt)} $entryCurrency)"
        } else {
            baseDesc
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onSave(
            existingTx?.id ?: "",
            txType,
            convertedAmt,
            if (txType == "transfer") "Transfert" else overrideCategory,
            if (txType == "transfer") "autre" else overrideEnvKey,
            selectedAccountId,
            if (txType == "transfer") toAccountId else "",
            finalDesc,
            normalizedDate
        )
        onDismiss()
        return true
    }

    if (showDatePickerDialog) {
        val initialMillis = remember(isoDate) {
            PayCycleEngine.parseEpochDays(isoDate) * 86_400_000L
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = selectedMillis
                            }
                            isoDate = String.format(
                                java.util.Locale.US,
                                "%04d-%02d-%02d",
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH) + 1,
                                cal.get(Calendar.DAY_OF_MONTH)
                            )
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text(strings.saveBtn, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text(strings.cancelBtn)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingTx != null) strings.editOperationTitle else strings.addOperationTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ExpressivePillChip(
                    label = strings.moreOptionsLabel,
                    selected = showAdvancedFields,
                    onClick = { showAdvancedFields = !showAdvancedFields },
                    icon = Icons.Default.Tune,
                    testTag = "tx_advanced_options_toggle"
                )
            }

            val isDark = state.profile.isDarkTheme
            val expColor = semanticExpenseColor(isDark)
            val incColor = semanticIncomeColor(isDark)
            ExpressiveGlassSurface(
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple("expense", strings.expenseTab, expColor),
                        Triple("income", strings.incomeTab, incColor),
                        Triple("transfer", strings.transferTab, MaterialTheme.colorScheme.primary)
                    ).forEach { (typeKey, label, accent) ->
                        val isSelected = txType == typeKey
                        val tabFg = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) accent.copy(alpha = 0.22f) else Color.Transparent,
                            contentColor = tabFg,
                            border = if (isSelected) BorderStroke(1.dp, accent.copy(alpha = 0.65f)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    txType = typeKey
                                }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = tabFg,
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Step 1: Large Numeric Keypad with Voice Mic Button next to amount
            LargeNumericKeypadSection(
                amountText = amountText,
                currencySymbol = if (entryCurrency == baseCurrencyCode) cur else entryCurrency,
                language = lang,
                twoTapInstantSave = twoTapInstantSave,
                onToggleTwoTapInstantSave = { twoTapInstantSave = it },
                recentCategories = recentCategories,
                selectedEnvelopeKey = selectedEnvelopeKey,
                onAmountChange = {
                    amountText = it
                    duplicateWarningTx = null
                },
                onSelectCategoryShortcut = { shortcut, commitImmediately ->
                    selectedEnvelopeKey = shortcut.envelopeKey
                    customCategory = shortcut.categoryName
                    if (commitImmediately && txType != "transfer") {
                        tryCommitTransaction(
                            overrideCategory = shortcut.categoryName,
                            overrideEnvKey = shortcut.envelopeKey
                        )
                    }
                },
                onVoiceMicClick = {
                    showVoiceSection = !showVoiceSection
                }
            )

            AnimatedVisibility(visible = showVoiceSection) {
                VoiceDarijaEntrySection(
                    state = state,
                    autoLaunchVoice = autoLaunchVoice,
                    onVoiceParsed = { parsed ->
                        txType = parsed.txType
                        amountText = if (parsed.amount % 1.0 == 0.0) {
                            parsed.amount.toInt().toString()
                        } else {
                            parsed.amount.toString()
                        }
                        desc = parsed.description
                        selectedEnvelopeKey = parsed.envelopeKey
                        customCategory = parsed.categoryName
                    }
                )
            }

            // Step 2: Category Chips (or Transfer target account chips)
            if (txType == "transfer") {
                ExpressiveGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${strings.sourceAccountFieldLabel} → ${strings.accountFieldLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.accounts.forEach { acc ->
                                FilterChip(
                                    selected = toAccountId == acc.account.id,
                                    onClick = { toAccountId = acc.account.id },
                                    label = { Text(acc.account.name) }
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .testTag("tx_category_chips_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.envelopes.forEach { env ->
                        val catLabel = env.config.localizedName(state.profile.language)
                        FilterChip(
                            selected = selectedEnvelopeKey == env.config.key,
                            onClick = {
                                selectedEnvelopeKey = env.config.key
                                customCategory = catLabel
                                duplicateWarningTx = null
                                if (twoTapInstantSave && (amountText.replace(",", ".").toDoubleOrNull() ?: 0.0) > 0.0) {
                                    tryCommitTransaction(
                                        overrideCategory = catLabel,
                                        overrideEnvKey = env.config.key
                                    )
                                }
                            },
                            label = { Text(catLabel.take(22)) },
                            modifier = Modifier.testTag("tx_cat_chip_${env.config.key}")
                        )
                    }
                }
            }

            if (duplicateWarningTx != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AccentAmber.copy(alpha = 0.18f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AccentAmber, RoundedCornerShape(16.dp))
                        .testTag("duplicate_tx_warning_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "⚠️ Doublon détecté : ${PayCycleEngine.formatDh(duplicateWarningTx!!.amount)} $cur dans '${duplicateWarningTx!!.category}'",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentAmber,
                            fontWeight = FontWeight.Bold
                        )
                        FilledTonalButton(
                            onClick = {
                                tryCommitTransaction(ignoreDuplicateCheck = true)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_confirm_duplicate_tx")
                        ) {
                            Text(strings.addAnywayBtn, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (!dateError.isNullOrBlank()) {
                Text(
                    text = dateError!!,
                    color = AccentCoral,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Step 3: "Plus d'options" panel ABOVE the pinned Save button (no duplicate amount/category fields!)
            AnimatedVisibility(visible = showAdvancedFields || !dateError.isNullOrBlank()) {
                ExpressiveGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_more_options_panel")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = desc,
                            onValueChange = { newDesc ->
                                desc = newDesc
                                if (existingTx == null && newDesc.trim().length >= 2) {
                                    val guessed = PayCycleEngine.inferSmartDefaults(
                                        description = newDesc,
                                        allTransactions = state.allTransactions,
                                        envelopes = state.envelopes.map { it.config },
                                        accounts = state.accounts.map { it.account },
                                        lang = lang
                                    )
                                    selectedEnvelopeKey = guessed.envelopeKey
                                    customCategory = guessed.categoryName
                                    selectedAccountId = guessed.accountId
                                    smartDefaultHint = "✨ ${guessed.categoryName}"
                                } else {
                                    smartDefaultHint = null
                                }
                            },
                            label = { Text("Note / Description") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tx_desc_input")
                        )

                        if (!smartDefaultHint.isNullOrBlank()) {
                            Text(
                                text = smartDefaultHint!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = incColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("smart_default_hint_text")
                            )
                        }

                        // Date Selector: Aujourd'hui, Hier, and DatePickerDialog button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = isoDate == todayIso,
                                onClick = { isoDate = todayIso },
                                label = { Text(strings.todayChipLabel) },
                                modifier = Modifier.testTag("tx_date_today_chip")
                            )
                            FilterChip(
                                selected = isoDate == yesterdayIso,
                                onClick = { isoDate = yesterdayIso },
                                label = { Text(strings.yesterdayChipLabel) },
                                modifier = Modifier.testTag("tx_date_yesterday_chip")
                            )
                            FilterChip(
                                selected = isoDate != todayIso && isoDate != yesterdayIso,
                                onClick = { showDatePickerDialog = true },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = strings.pickDateBtnLabel,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        if (isoDate != todayIso && isoDate != yesterdayIso) {
                                            PayCycleEngine.formatFriendlyDate(isoDate, lang, todayIso)
                                        } else {
                                            strings.pickDateBtnLabel
                                        }
                                    )
                                },
                                modifier = Modifier.testTag("tx_date_picker_btn")
                            )
                        }

                        Text(
                            text = if (txType == "transfer") strings.sourceAccountFieldLabel else strings.accountFieldLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.accounts.forEach { acc ->
                                FilterChip(
                                    selected = selectedAccountId == acc.account.id,
                                    onClick = { selectedAccountId = acc.account.id },
                                    label = { Text(acc.account.name) }
                                )
                            }
                        }

                        // Multi-Currency Selector (MAD / EUR / USD)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("multi_currency_bar"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "MAD / EUR / USD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("MAD", "EUR", "USD").forEach { code ->
                                        FilterChip(
                                            selected = entryCurrency == code,
                                            onClick = { entryCurrency = code },
                                            label = { Text(code, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                            modifier = Modifier.testTag("currency_chip_$code")
                                        )
                                    }
                                }
                            }
                            if (entryCurrency != baseCurrencyCode) {
                                val isEur = entryCurrency == "EUR"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = if (isEur) eurRateText else usdRateText,
                                        onValueChange = { if (isEur) eurRateText = it else usdRateText = it },
                                        label = { Text("1 $entryCurrency → MAD") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("fx_rate_input")
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "= ${PayCycleEngine.formatDh(computeConvertedAmount())} $cur",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                                .testTag("fx_converted_preview_text")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Step 4: Pinned Primary Save Button at the bottom of the sheet
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (existingTx != null && onDelete != null) {
                    FilledTonalButton(
                        onClick = {
                            onDelete(existingTx)
                            onDismiss()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(strings.deleteBtn, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        tryCommitTransaction()
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(2f)
                        .height(54.dp)
                        .testTag("tx_save_button")
                ) {
                    Text(strings.saveBtn, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeEnvelopesSheet(
    state: DashboardState,
    onDismiss: () -> Unit,
    onSaveEnvelope: (EnvelopeConfig) -> Unit,
    onDeleteEnvelope: (String) -> Unit,
    onAutoBalance100: () -> Unit
) {
    val lang = state.profile.language
    var newName by remember { mutableStateOf("") }
    var newPct by remember { mutableStateOf("10") }
    val totalPct = state.envelopes.filter { it.config.hasCap }.sumOf { it.config.percentage }.safeRoundToInt()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Personnaliser les Enveloppes",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Total alloué : $totalPct% (Recommandé : 100%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (totalPct == 100) AccentEmerald else AccentCoral
                    )
                }
                FilledTonalButton(
                    onClick = onAutoBalance100,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("100%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            state.envelopes.forEach { envStatus ->
                var nameInput by remember(envStatus.config.id, lang) {
                    mutableStateOf(envStatus.config.localizedName(lang))
                }
                var pctInput by remember(envStatus.config.id, envStatus.config.percentage) {
                    mutableStateOf(envStatus.config.percentage.safeRoundToInt().toString())
                }
                var hasCapToggle by remember(envStatus.config.id, envStatus.config.hasCap) {
                    mutableStateOf(envStatus.config.hasCap)
                }
                val helperSubtitle = remember(envStatus.config.key, lang) {
                    DefaultCategoryResolver.localizedDefaultHelper(envStatus.config.key, lang)
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Nom") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (hasCapToggle) {
                                OutlinedTextField(
                                    value = pctInput,
                                    onValueChange = { pctInput = it },
                                    label = { Text("%") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.width(78.dp)
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    val cleanName = nameInput.trim().ifBlank { envStatus.config.nameFr }
                                    val p = if (hasCapToggle) {
                                        (pctInput.replace(",", ".").toDoubleOrNull() ?: envStatus.config.percentage).coerceIn(0.0, 100.0)
                                    } else 0.0
                                    onSaveEnvelope(
                                        envStatus.config.copy(
                                            nameFr = cleanName,
                                            nameAr = cleanName,
                                            nameDarija = cleanName,
                                            nameEn = cleanName,
                                            percentage = p,
                                            hasCap = hasCapToggle
                                        )
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (helperSubtitle.isNotBlank()) {
                            Text(
                                text = helperSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("envelope_helper_${envStatus.config.key}")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Switch(
                                    checked = hasCapToggle,
                                    onCheckedChange = { checked ->
                                        hasCapToggle = checked
                                        val p = if (checked) {
                                            (pctInput.replace(",", ".").toDoubleOrNull() ?: 10.0).coerceIn(1.0, 100.0)
                                        } else 0.0
                                        onSaveEnvelope(
                                            envStatus.config.copy(
                                                hasCap = checked,
                                                percentage = p
                                            )
                                        )
                                    }
                                )
                                Text(
                                    text = if (hasCapToggle) "Plafond actif" else "Sans plafond",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (state.envelopes.size > 1) {
                                IconButton(
                                    onClick = { onDeleteEnvelope(envStatus.config.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Supprimer enveloppe",
                                        tint = AccentCoral,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Ajouter une catégorie personnalisée :",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nom (ex: Sport, Enfants)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = newPct,
                    onValueChange = { newPct = it },
                    label = { Text("%") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(80.dp)
                )
                Button(
                    onClick = {
                        val n = newName.trim()
                        val p = newPct.replace(",", ".").toDoubleOrNull() ?: 10.0
                        if (n.isNotEmpty()) {
                            val key = n.lowercase().replace(Regex("[^a-z0-9]"), "_").take(20)
                            onSaveEnvelope(
                                EnvelopeConfig(
                                    id = "env_${System.currentTimeMillis()}",
                                    key = key.ifBlank { "custom" },
                                    nameFr = n,
                                    nameAr = n,
                                    nameDarija = n,
                                    nameEn = n,
                                    percentage = p.coerceIn(0.0, 100.0),
                                    hasCap = true,
                                    colorHex = "#10B981"
                                )
                            )
                            newName = ""
                        }
                    }
                ) {
                    Text("+")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
