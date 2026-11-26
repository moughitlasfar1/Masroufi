package com.masroufi.credit.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.core.Money
import com.masroufi.credit.data.SavedCreditEntity
import com.masroufi.credit.domain.AmortizationRow
import com.masroufi.credit.domain.CreditCalculator
import com.masroufi.credit.domain.CreditInput
import com.masroufi.credit.domain.CreditResult
import com.masroufi.credit.domain.CreditStrings
import com.masroufi.credit.domain.CreditType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreditSimulatorScreen(
    viewModel: CreditSimulatorViewModel,
    language: String,
    initialMonthlyIncome: Money,
    isLocalMode: Boolean,
    onBack: () -> Unit,
    onRequestGoogleSignIn: () -> Unit,
    onCreateLinkedRecurringBill: (CreditInput, CreditResult) -> Unit
) {
    BackHandler { onBack() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = remember(language) { CreditStrings.forLang(language) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLocalSignInDialog by remember { mutableStateOf(false) }

    LaunchedEffect(language) {
        viewModel.setLanguage(language)
    }
    LaunchedEffect(initialMonthlyIncome) {
        if (initialMonthlyIncome.isPositive) {
            viewModel.applyMasroufiIncomePrefill(initialMonthlyIncome)
        }
    }
    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbarMessage()
        }
    }

    val loanSliderMax = when (uiState.input.creditType) {
        CreditType.IMMOBILIER -> 3_000_000f
        CreditType.AUTO -> 800_000f
        CreditType.CONSOMMATION -> 600_000f
    }
    val downPaymentSliderMax = uiState.input.loanAmount.toBigDecimalDh().toFloat().coerceAtLeast(1_000f)

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("credit_simulator_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.backContentDesc
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = strings.screenTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = strings.subtitleBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            if (isLocalMode) {
                                showLocalSignInDialog = true
                            } else {
                                showSaveDialog = true
                            }
                        },
                        enabled = uiState.validationErrors.isEmpty() && !uiState.isSaving,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .widthIn(max = 620.dp)
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("credit_save_primary_cta")
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = strings.savePrimaryCta,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxSize()
            ) {
                // Sticky Hero Result Summary Card stays visible while tuning sliders & scrolling
                CreditHeroSummaryCard(
                    input = uiState.input,
                    result = uiState.result,
                    strings = strings,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Single flat LazyColumn — zero nested scrollables
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("credit_simulator_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Preset FilterChip Selector
                    item(key = "preset_selector") {
                        CreditPresetChipRow(
                            selectedType = uiState.input.creditType,
                            strings = strings,
                            onSelectType = viewModel::onCreditTypeSelected
                        )
                    }

                    // 2. Core Input Controls Card
                    item(key = "core_inputs") {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = strings.coreInputsTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                CreditSyncedMoneySliderField(
                                    label = strings.loanAmountLabel,
                                    value = uiState.input.loanAmount,
                                    valueRange = 5_000f..loanSliderMax,
                                    onValueChange = viewModel::onLoanAmountChanged,
                                    suffix = "MAD",
                                    errorText = uiState.validationErrors[CreditValidationKeys.LOAN_AMOUNT],
                                    testTagPrefix = "credit_loan_amount"
                                )

                                CreditSyncedMoneySliderField(
                                    label = strings.downPaymentLabel,
                                    value = uiState.input.downPayment,
                                    valueRange = 0f..downPaymentSliderMax,
                                    onValueChange = viewModel::onDownPaymentChanged,
                                    suffix = "MAD",
                                    errorText = uiState.validationErrors[CreditValidationKeys.DOWN_PAYMENT],
                                    testTagPrefix = "credit_down_payment"
                                )

                                val durationYears = uiState.input.durationMonths / 12
                                val durationRemMonths = uiState.input.durationMonths % 12
                                val durationSubtitle = if (durationYears > 0) {
                                    strings.yearsMonthsFmt
                                        .replace("{years}", durationYears.toString())
                                        .replace("{months}", durationRemMonths.toString())
                                        .replace("{total}", uiState.input.durationMonths.toString())
                                } else {
                                    strings.monthsOnlyFmt.replace("{total}", uiState.input.durationMonths.toString())
                                }

                                CreditSyncedSliderField(
                                    label = strings.durationLabel,
                                    value = uiState.input.durationMonths.toDouble(),
                                    valueRange = 6f..uiState.maxDurationMonths.toFloat().coerceAtLeast(12f),
                                    onValueChange = { viewModel.onDurationMonthsChanged(it.toInt()) },
                                    isInteger = true,
                                    supportingSubtitle = durationSubtitle,
                                    errorText = uiState.validationErrors[CreditValidationKeys.DURATION],
                                    testTagPrefix = "credit_duration"
                                )

                                CreditSyncedSliderField(
                                    label = strings.annualRateHtLabel,
                                    value = uiState.input.annualInterestRateHt,
                                    valueRange = 0f..18f,
                                    onValueChange = viewModel::onInterestRateChanged,
                                    isInteger = false,
                                    suffix = "%",
                                    errorText = uiState.validationErrors[CreditValidationKeys.INTEREST_RATE],
                                    testTagPrefix = "credit_rate_ht"
                                )

                                // Moroccan 10% TVA Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            text = strings.applyTvaLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = strings.applyTvaDesc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = uiState.input.applyInterestTva,
                                        onCheckedChange = viewModel::onApplyTvaChanged,
                                        modifier = Modifier.testTag("credit_apply_tva_switch")
                                    )
                                }

                                CreditSyncedMoneySliderField(
                                    label = strings.monthlyNetIncomeLabel,
                                    value = uiState.input.monthlyNetIncome,
                                    valueRange = 3_000f..100_000f,
                                    onValueChange = viewModel::onMonthlyNetIncomeChanged,
                                    suffix = "MAD",
                                    errorText = uiState.validationErrors[CreditValidationKeys.MONTHLY_INCOME],
                                    testTagPrefix = "credit_income"
                                )
                            }
                        }
                    }

                    // 3. Collapsible Banking Profile & Advanced Options
                    item(key = "advanced_profile") {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp)
                                        .clickable { viewModel.onToggleAdvancedProfileExpanded() }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .testTag("credit_advanced_profile_toggle"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = strings.advancedProfileTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Icon(
                                        imageVector = if (uiState.isAdvancedProfileExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }

                                if (uiState.isAdvancedProfileExpanded) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        CreditSyncedMoneySliderField(
                                            label = strings.existingMonthlyDebtLabel,
                                            value = uiState.input.existingMonthlyDebt,
                                            valueRange = 0f..30_000f,
                                            onValueChange = viewModel::onExistingMonthlyDebtChanged,
                                            suffix = "MAD",
                                            testTagPrefix = "credit_existing_debt"
                                        )

                                        // Grace period (Différé) 0..3 months chips
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = strings.deferredPeriodLabel,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                for (m in 0..uiState.input.creditType.maxDeferredMonths) {
                                                    val label = if (m == 0) {
                                                        strings.deferredNoneLabel
                                                    } else {
                                                        strings.deferredMonthsOptionFmt.replace("{m}", m.toString())
                                                    }
                                                    FilterChip(
                                                        selected = uiState.input.deferredMonths == m,
                                                        onClick = { viewModel.onDeferredMonthsChanged(m) },
                                                        label = { Text(text = label) },
                                                        modifier = Modifier
                                                            .heightIn(min = 48.dp)
                                                            .testTag("credit_deferred_chip_$m")
                                                    )
                                                }
                                            }
                                            uiState.validationErrors[CreditValidationKeys.DEFERRED]?.let { err ->
                                                Text(
                                                    text = err,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }

                                        CreditSyncedSliderField(
                                            label = strings.insuranceRateLabel,
                                            value = uiState.input.insuranceRate,
                                            valueRange = 0f..2f,
                                            onValueChange = viewModel::onInsuranceRateChanged,
                                            isInteger = false,
                                            suffix = "%",
                                            supportingSubtitle = strings.insuranceRateHint,
                                            errorText = uiState.validationErrors[CreditValidationKeys.INSURANCE_RATE],
                                            testTagPrefix = "credit_insurance_rate"
                                        )

                                        CreditSyncedSliderField(
                                            label = strings.borrowerAgeLabel,
                                            value = uiState.input.borrowerAge.toDouble(),
                                            valueRange = 18f..69f,
                                            onValueChange = { viewModel.onBorrowerAgeChanged(it.toInt()) },
                                            isInteger = true,
                                            supportingSubtitle = strings.ageAtEndFmt.replace(
                                                "{age}",
                                                CreditCalculator.formatDecimal1(uiState.result.ageAtLastPayment)
                                            ),
                                            errorText = uiState.validationErrors[CreditValidationKeys.BORROWER_AGE],
                                            testTagPrefix = "credit_borrower_age"
                                        )

                                        // Existing Mortgage Switch
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                                Text(
                                                    text = strings.hasExistingMortgageLabel,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = strings.hasExistingMortgageDesc,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = uiState.input.hasExistingMortgage,
                                                onCheckedChange = viewModel::onHasExistingMortgageChanged,
                                                modifier = Modifier.testTag("credit_existing_mortgage_switch")
                                            )
                                        }

                                        // 120-month Consommation eligibility switch
                                        if (uiState.input.creditType == CreditType.CONSOMMATION) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                                    Text(
                                                        text = strings.eligible120MonthsLabel,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = strings.eligible120MonthsDesc,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Switch(
                                                    checked = uiState.input.isEligibleFor120Months,
                                                    onCheckedChange = viewModel::onEligibleFor120MonthsChanged,
                                                    modifier = Modifier.testTag("credit_eligible_120_switch")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Collapsible Amortization Schedule (flat items in LazyColumn!)
                    item(key = "schedule_header_card") {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable { viewModel.onToggleScheduleExpanded() }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("credit_schedule_toggle"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = strings.scheduleTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = strings.scheduleCountFmt.replace(
                                                "{count}",
                                                uiState.result.amortizationSchedule.size.toString()
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (uiState.isScheduleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                        }
                    }

                    if (uiState.isScheduleExpanded) {
                        item(key = "schedule_table_columns") {
                            AmortizationHeaderRow(strings = strings)
                        }
                        items(
                            items = uiState.result.amortizationSchedule,
                            key = { row -> "amort_row_${row.month}" }
                        ) { row ->
                            AmortizationScheduleItemRow(row = row, strings = strings)
                        }
                    }

                    // 5. Saved Credits Section
                    item(key = "saved_credits_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = strings.savedCreditsTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (uiState.savedCredits.isNotEmpty()) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = uiState.savedCredits.size.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.isLoadingCredits) {
                        item(key = "saved_credits_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                    } else if (!uiState.savedCreditsError.isNullOrBlank()) {
                        item(key = "saved_credits_error") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = uiState.savedCreditsError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.startObservingSavedCredits() }) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (uiState.savedCredits.isEmpty()) {
                        item(key = "saved_credits_empty") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = strings.savedCreditsEmpty,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(
                            items = uiState.savedCredits,
                            key = { entity -> "saved_credit_${entity.id}" }
                        ) { entity ->
                            SavedCreditCard(
                                entity = entity,
                                strings = strings,
                                onLoad = { viewModel.loadSavedCredit(entity) },
                                onDelete = { viewModel.deleteSavedCredit(entity.id) }
                            )
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        CreditSaveConfirmDialog(
            input = uiState.input,
            result = uiState.result,
            strings = strings,
            isSaving = uiState.isSaving,
            onDismiss = { showSaveDialog = false },
            onConfirm = { linkToMonthlyExpenses ->
                viewModel.saveCurrentCredit(linkToMonthlyExpenses = linkToMonthlyExpenses) { savedInput, savedResult, linked ->
                    showSaveDialog = false
                    if (linked) {
                        onCreateLinkedRecurringBill(savedInput, savedResult)
                    }
                }
            }
        )
    }

    if (showLocalSignInDialog) {
        CreditLocalModeSignInDialog(
            strings = strings,
            onDismiss = { showLocalSignInDialog = false },
            onSignInClick = onRequestGoogleSignIn
        )
    }
}

@Composable
private fun AmortizationHeaderRow(strings: CreditStrings) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.colMonth,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.75f)
            )
            Text(
                text = strings.colPrincipal,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1.1f)
            )
            Text(
                text = strings.colInterestTtc,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1.05f)
            )
            Text(
                text = strings.colBalance,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1.15f)
            )
        }
    }
}

@Composable
private fun AmortizationScheduleItemRow(
    row: AmortizationRow,
    strings: CreditStrings
) {
    val bg = if (row.isDeferred) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    } else if (row.month % 2 == 0) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        Color.Transparent
    }
    val interestTtc = row.interestHt + row.tvaOnInterest

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(0.75f)) {
                Text(
                    text = "#${row.month}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (row.isDeferred) {
                    Text(
                        text = strings.deferredBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = CreditCalculator.formatMadCompact(row.principal),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.1f)
            )
            Text(
                text = CreditCalculator.formatMadCompact(interestTtc),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.05f)
            )
            Text(
                text = CreditCalculator.formatMadCompact(row.remainingBalance),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.15f)
            )
        }
    }
}

@Composable
private fun SavedCreditCard(
    entity: SavedCreditEntity,
    strings: CreditStrings,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    val typeEnum = runCatching { CreditType.valueOf(entity.type) }.getOrDefault(CreditType.CONSOMMATION)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLoad() }
            .testTag("saved_credit_card_${entity.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = strings.presetLabel(typeEnum),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (entity.linkToMonthlyExpenses) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = strings.savedCreditsLinkedBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${CreditCalculator.formatMad(entity.monthlyPayment)} / ${strings.monthsOnlyFmt.replace("{total}", entity.durationMonths.toString())}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${CreditCalculator.formatMadCompact(entity.financedPrincipal)} · ${CreditCalculator.formatPercent(entity.annualInterestRateHt)}% HT · DTI ${CreditCalculator.formatPercent(entity.debtToIncomeRatio)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = strings.savedCreditsReloadHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("saved_credit_delete_${entity.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = strings.savedCreditsDeleteDesc,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
