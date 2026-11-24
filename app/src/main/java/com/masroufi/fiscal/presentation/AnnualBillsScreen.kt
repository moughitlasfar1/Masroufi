package com.masroufi.fiscal.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialItem
import com.example.domain.PayCycleEngine
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.ExpressiveAnimatedProgressBar
import com.example.ui.theme.ExpressiveGlassCard
import com.masroufi.fiscal.domain.AnnualBillCalculator
import com.masroufi.fiscal.domain.AnnualBillItem
import com.masroufi.fiscal.domain.AnnualBillPreset
import com.masroufi.fiscal.domain.FiscalRates
import com.masroufi.fiscal.domain.FiscalStrings
import com.masroufi.fiscal.domain.FiscalUiStrings

/**
 * Step 1: Annual Bills Reserve screen (Vignette TSAVA, Car Insurance, Custom Annual Bills).
 * Isolated inside `com.masroufi.fiscal.presentation` while reusing Masroufi's existing
 * Savings Goal + recurring monthly contribution models, theme, glass cards, and `••••` masking.
 */
@Composable
fun AnnualBillsScreen(
    annualBills: List<AnnualBillItem>,
    existingGoals: List<FinancialItem>,
    language: String,
    currencySymbol: String,
    hideAmounts: Boolean,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onSaveReserve: (AnnualBillItem) -> Unit,
    onMarkBillPaid: (bill: AnnualBillItem, logExpense: Boolean) -> Unit,
    onDeleteBill: (AnnualBillItem) -> Unit
) {
    BackHandler(onBack = onBack)

    val strings = remember(language) { FiscalStrings.forLang(language) }
    val cur = currencySymbol.ifBlank { "DH" }
    val (currentYear, currentMonth) = remember { AnnualBillCalculator.currentYearAndMonth() }

    var editingBillId by remember { mutableStateOf<String?>(null) }
    var selectedPreset by remember { mutableStateOf(AnnualBillPreset.VIGNETTE) }
    var selectedCvBand by remember { mutableStateOf(FiscalRates.HorsepowerBand.UNDER_8_CV) }
    var selectedFuel by remember { mutableStateOf(FiscalRates.FuelType.ESSENCE) }
    var customName by remember { mutableStateOf("") }
    var manualAmountText by remember { mutableStateOf("") }
    var selectedDueMonth by remember { mutableIntStateOf(1) } // January default for Vignette
    var billPendingPayDialog by remember { mutableStateOf<AnnualBillItem?>(null) }

    val officialVignetteAmount = remember(selectedPreset, selectedCvBand, selectedFuel) {
        if (selectedPreset == AnnualBillPreset.VIGNETTE) {
            FiscalRates.vignetteAnnualAmountMad(selectedCvBand, selectedFuel)
        } else {
            null
        }
    }
    val requiresManualAmount = selectedPreset != AnnualBillPreset.VIGNETTE || officialVignetteAmount == null

    val resolvedAmountMad = remember(selectedPreset, officialVignetteAmount, manualAmountText) {
        if (selectedPreset == AnnualBillPreset.VIGNETTE && officialVignetteAmount != null) {
            officialVignetteAmount
        } else {
            PayCycleEngine.parseFlexibleAmount(manualAmountText)
        }
    }

    val (resolvedDueYear, rolledNextYear) = remember(selectedDueMonth, currentYear, currentMonth) {
        AnnualBillCalculator.resolveTargetYearForNewBill(
            dueMonth = selectedDueMonth,
            currentYear = currentYear,
            currentMonth = currentMonth
        )
    }

    val existingEditingBill = remember(editingBillId, annualBills) {
        editingBillId?.let { id -> annualBills.find { it.id == id } }
    }
    val existingEditingSaved = remember(existingEditingBill, existingGoals) {
        val goalId = existingEditingBill?.linkedGoalId.orEmpty()
        if (goalId.isBlank()) 0.0 else existingGoals.find { it.id == goalId }?.secondaryAmount ?: 0.0
    }

    val liveBreakdown = remember(
        resolvedAmountMad,
        existingEditingSaved,
        selectedDueMonth,
        resolvedDueYear,
        currentYear,
        currentMonth
    ) {
        AnnualBillCalculator.computeBreakdown(
            annualAmountMad = resolvedAmountMad,
            savedAmountMad = existingEditingSaved,
            dueMonth = selectedDueMonth,
            dueYear = resolvedDueYear,
            currentYear = currentYear,
            currentMonth = currentMonth,
            rollForwardIfPassed = false
        )
    }

    val activeBills = remember(annualBills) { annualBills.filterNot { it.isPaid } }
    val paidBills = remember(annualBills) { annualBills.filter { it.isPaid } }

    fun fmt(value: Double): String = "${PayCycleEngine.formatAmountOrMask(value, hideAmounts)} $cur"

    fun monthLabel(month: Int): String =
        strings.monthNames.getOrElse((month - 1).coerceIn(0, 11)) { month.toString() }

    fun defaultTitleForPreset(): String = when (selectedPreset) {
        AnnualBillPreset.VIGNETTE -> {
            val cvText = when (selectedCvBand) {
                FiscalRates.HorsepowerBand.UNDER_8_CV -> strings.cvUnder8
                FiscalRates.HorsepowerBand.CV_8_TO_10 -> strings.cv8To10
                FiscalRates.HorsepowerBand.CV_11_TO_14 -> strings.cv11To14
                FiscalRates.HorsepowerBand.CV_15_PLUS -> strings.cv15Plus
            }
            val fuelText = if (selectedFuel == FiscalRates.FuelType.DIESEL) strings.fuelDiesel else strings.fuelEssence
            "${strings.presetVignette} ($cvText · $fuelText)"
        }
        AnnualBillPreset.CAR_INSURANCE -> customName.trim().ifBlank { strings.presetInsurance }
        AnnualBillPreset.CUSTOM -> customName.trim().ifBlank { strings.presetCustom }
        AnnualBillPreset.ZAKAT -> customName.trim().ifBlank { strings.presetZakat }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("annual_bills_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.cancelBtn,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.annualBillsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = strings.vignetteRatesAsOfFmt.replace("{date}", FiscalRates.VIGNETTE_RATES_AS_OF_ISO),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .testTag("annual_bills_screen"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Preset Builder & Live Reserve Calculator Card
                item(key = "annual_bill_builder") {
                    ExpressiveGlassCard(
                        cornerRadius = 28.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("annual_bill_builder_card")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 3 Preset Chips: Vignette, Car Insurance, Custom
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple(AnnualBillPreset.VIGNETTE, strings.presetVignette, Icons.Default.DirectionsCar),
                                    Triple(AnnualBillPreset.CAR_INSURANCE, strings.presetInsurance, Icons.Default.Security),
                                    Triple(AnnualBillPreset.CUSTOM, strings.presetCustom, Icons.Default.ReceiptLong)
                                ).forEach { (preset, label, icon) ->
                                    FilterChip(
                                        selected = selectedPreset == preset,
                                        onClick = {
                                            selectedPreset = preset
                                            editingBillId = null
                                            if (preset == AnnualBillPreset.VIGNETTE) {
                                                selectedDueMonth = 1
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text(label, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.testTag("annual_preset_${preset.key}")
                                    )
                                }
                            }

                            // Vignette inputs: CV band & Fuel type
                            if (selectedPreset == AnnualBillPreset.VIGNETTE) {
                                Text(
                                    text = strings.vignetteCvLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        FiscalRates.HorsepowerBand.UNDER_8_CV to strings.cvUnder8,
                                        FiscalRates.HorsepowerBand.CV_8_TO_10 to strings.cv8To10,
                                        FiscalRates.HorsepowerBand.CV_11_TO_14 to strings.cv11To14,
                                        FiscalRates.HorsepowerBand.CV_15_PLUS to strings.cv15Plus
                                    ).forEach { (band, label) ->
                                        FilterChip(
                                            selected = selectedCvBand == band,
                                            onClick = { selectedCvBand = band },
                                            label = { Text(label, fontWeight = FontWeight.SemiBold) },
                                            modifier = Modifier.testTag("vignette_cv_${band.key}")
                                        )
                                    }
                                }

                                Text(
                                    text = strings.vignetteFuelLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        FiscalRates.FuelType.ESSENCE to strings.fuelEssence,
                                        FiscalRates.FuelType.DIESEL to strings.fuelDiesel
                                    ).forEach { (fuel, label) ->
                                        FilterChip(
                                            selected = selectedFuel == fuel,
                                            onClick = { selectedFuel = fuel },
                                            label = { Text(label, fontWeight = FontWeight.SemiBold) },
                                            modifier = Modifier.testTag("vignette_fuel_${fuel.key}")
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = officialVignetteAmount == null) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = AccentAmber.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.45f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = strings.vignetteManualRequiredNotice,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = customName,
                                    onValueChange = { customName = it.take(50) },
                                    label = { Text(strings.billNameLabel) },
                                    placeholder = { Text(strings.billNameHint) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("annual_bill_name_input")
                                )
                            }

                            if (requiresManualAmount) {
                                OutlinedTextField(
                                    value = manualAmountText,
                                    onValueChange = { manualAmountText = it.take(12) },
                                    label = { Text(strings.annualAmountLabel) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("annual_bill_amount_input")
                                )
                            }

                            // Due month selector (1..12)
                            Text(
                                text = strings.dueMonthLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                (1..12).forEach { m ->
                                    FilterChip(
                                        selected = selectedDueMonth == m,
                                        onClick = { selectedDueMonth = m },
                                        label = { Text(monthLabel(m), fontSize = 12.sp) },
                                        modifier = Modifier.testTag("annual_due_month_$m")
                                    )
                                }
                            }

                            // Live Monthly Reserve Result Highlight
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDarkTheme) 0.55f else 0.72f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("annual_bill_live_summary")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = strings.monthlyReserveLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = fmt(liveBreakdown.annualAmountMad),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    Text(
                                        text = "${fmt(liveBreakdown.monthlyReserveMad)} / ${strings.monthNames.getOrNull(0)?.let { "m" } ?: "m"}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("annual_bill_monthly_amount_text")
                                    )
                                    val subLine = if (liveBreakdown.isDueThisMonth) {
                                        strings.dueThisMonthBadge
                                    } else {
                                        strings.monthsLeftFmt
                                            .replace("{months}", liveBreakdown.monthsRemaining.toString())
                                            .replace("{month}", monthLabel(liveBreakdown.dueMonth))
                                            .replace("{year}", liveBreakdown.dueYear.toString())
                                    }
                                    Text(
                                        text = subLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (rolledNextYear) {
                                        Text(
                                            text = strings.rolledNextYearNoteFmt
                                                .replace("{month}", monthLabel(liveBreakdown.dueMonth))
                                                .replace("{year}", liveBreakdown.dueYear.toString()),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }

                            // Primary CTA: Create or Update Monthly Reserve
                            Button(
                                onClick = {
                                    val id = editingBillId ?: "ab_${System.currentTimeMillis()}"
                                    val existing = existingEditingBill
                                    val item = AnnualBillItem(
                                        id = id,
                                        preset = selectedPreset,
                                        title = defaultTitleForPreset(),
                                        annualAmountMad = resolvedAmountMad,
                                        dueMonth = selectedDueMonth,
                                        dueYear = resolvedDueYear,
                                        cvBand = if (selectedPreset == AnnualBillPreset.VIGNETTE) selectedCvBand else null,
                                        fuelType = if (selectedPreset == AnnualBillPreset.VIGNETTE) selectedFuel else null,
                                        isManualVignetteAmount = selectedPreset == AnnualBillPreset.VIGNETTE && officialVignetteAmount == null,
                                        linkedGoalId = existing?.linkedGoalId.orEmpty().ifBlank { "goal_fiscal_$id" },
                                        linkedBillId = existing?.linkedBillId.orEmpty().ifBlank { "bill_fiscal_$id" },
                                        isPaid = false,
                                        paidIsoDate = "",
                                        createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis()
                                    )
                                    onSaveReserve(item)
                                    editingBillId = null
                                    if (selectedPreset != AnnualBillPreset.VIGNETTE) {
                                        customName = ""
                                        manualAmountText = ""
                                    }
                                },
                                enabled = resolvedAmountMad > 0.0,
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(vertical = 14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("annual_bill_create_reserve_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Savings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (editingBillId != null) strings.updateReserveBtn else strings.createReserveBtn,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                // 2. Active Annual Bill Reserves
                if (activeBills.isEmpty()) {
                    item(key = "annual_bills_empty") {
                        ExpressiveGlassCard(
                            cornerRadius = 24.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventRepeat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = strings.annualBillsEmptyTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = strings.annualBillsEmptyBody,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    item(key = "active_reserves_header") {
                        Text(
                            text = strings.activeReservesHeader,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    items(activeBills, key = { it.id }) { bill ->
                        val linkedGoal = existingGoals.find { it.id == bill.linkedGoalId }
                        val savedAmt = linkedGoal?.secondaryAmount ?: 0.0
                        val breakdown = AnnualBillCalculator.computeBreakdown(
                            annualAmountMad = bill.annualAmountMad,
                            savedAmountMad = savedAmt,
                            dueMonth = bill.dueMonth,
                            dueYear = bill.dueYear,
                            currentYear = currentYear,
                            currentMonth = currentMonth,
                            rollForwardIfPassed = false
                        )
                        AnnualBillReserveCard(
                            bill = bill,
                            breakdown = breakdown,
                            strings = strings,
                            currencySymbol = cur,
                            hideAmounts = hideAmounts,
                            onEdit = {
                                editingBillId = bill.id
                                selectedPreset = bill.preset
                                bill.cvBand?.let { selectedCvBand = it }
                                bill.fuelType?.let { selectedFuel = it }
                                customName = bill.title
                                manualAmountText = bill.annualAmountMad.toString()
                                selectedDueMonth = bill.dueMonth
                            },
                            onMarkPaid = { billPendingPayDialog = bill },
                            onDelete = { onDeleteBill(bill) }
                        )
                    }
                }

                // 3. Paid & Closed History
                if (paidBills.isNotEmpty()) {
                    item(key = "paid_reserves_header") {
                        Text(
                            text = strings.paidReservesHeader,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                    items(paidBills, key = { "paid_${it.id}" }) { bill ->
                        ExpressiveGlassCard(
                            cornerRadius = 22.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AccentEmerald,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column {
                                        Text(
                                            text = bill.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${fmt(bill.annualAmountMad)} · ${bill.paidIsoDate}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { onDeleteBill(bill) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = strings.deleteBtn
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    // Pay & Close confirmation dialog
    val dialogBill = billPendingPayDialog
    if (dialogBill != null) {
        AlertDialog(
            onDismissRequest = { billPendingPayDialog = null },
            title = {
                Text(
                    text = strings.markPaidDialogTitle,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = strings.markPaidDialogBodyFmt
                        .replace("{amount}", fmt(dialogBill.annualAmountMad))
                        .replace("{title}", dialogBill.title)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onMarkBillPaid(dialogBill, true)
                        billPendingPayDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentEmerald,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("annual_bill_confirm_log_paid_btn")
                ) {
                    Text(strings.markPaidLogAndCloseBtn, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            onMarkBillPaid(dialogBill, false)
                            billPendingPayDialog = null
                        },
                        modifier = Modifier.testTag("annual_bill_close_only_btn")
                    ) {
                        Text(strings.markPaidCloseOnlyBtn)
                    }
                    TextButton(onClick = { billPendingPayDialog = null }) {
                        Text(strings.cancelBtn)
                    }
                }
            }
        )
    }
}

@Composable
private fun AnnualBillReserveCard(
    bill: AnnualBillItem,
    breakdown: com.masroufi.fiscal.domain.AnnualBillReserveBreakdown,
    strings: FiscalUiStrings,
    currencySymbol: String,
    hideAmounts: Boolean,
    onEdit: () -> Unit,
    onMarkPaid: () -> Unit,
    onDelete: () -> Unit
) {
    fun fmt(v: Double): String = "${PayCycleEngine.formatAmountOrMask(v, hideAmounts)} $currencySymbol"
    val monthText = strings.monthNames.getOrElse((bill.dueMonth - 1).coerceIn(0, 11)) { bill.dueMonth.toString() }
    val statusColor = when {
        breakdown.isDueDatePassed -> AccentCoral
        breakdown.isDueThisMonth -> AccentAmber
        else -> AccentEmerald
    }

    ExpressiveGlassCard(
        cornerRadius = 24.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("annual_bill_card_${bill.id}")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bill.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val scheduleText = when {
                        breakdown.isDueDatePassed -> strings.dueDatePassedBadge
                        breakdown.isDueThisMonth -> strings.dueThisMonthBadge
                        else -> strings.monthsLeftFmt
                            .replace("{months}", breakdown.monthsRemaining.toString())
                            .replace("{month}", monthText)
                            .replace("{year}", breakdown.dueYear.toString())
                    }
                    Text(
                        text = scheduleText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${fmt(breakdown.monthlyReserveMad)}/m",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            ExpressiveAnimatedProgressBar(
                progress = breakdown.progressFraction,
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                height = 8.dp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.savedProgressFmt
                        .replace("{saved}", fmt(breakdown.savedAmountMad))
                        .replace("{total}", fmt(breakdown.annualAmountMad)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onMarkPaid,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("annual_bill_mark_paid_${bill.id}")
                    ) {
                        Text(strings.markAsPaidBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = strings.deleteBtn,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
