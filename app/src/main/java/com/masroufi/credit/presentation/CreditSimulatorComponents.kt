package com.masroufi.credit.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.masroufi.core.Money
import com.masroufi.core.MoneyParser
import com.masroufi.credit.domain.CreditCalculator
import com.masroufi.credit.domain.CreditInput
import com.masroufi.credit.domain.CreditResult
import com.masroufi.credit.domain.CreditStrings
import com.masroufi.credit.domain.CreditType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val DtiSafeColor = Color(0xFF10B981)
private val DtiCautionColor = Color(0xFFF59E0B)
private val DtiDangerColor = Color(0xFFEF4444)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreditPresetChipRow(
    selectedType: CreditType,
    strings: CreditStrings,
    onSelectType: (CreditType) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CreditType.entries.forEach { type ->
            val selected = type == selectedType
            val icon = when (type) {
                CreditType.CONSOMMATION -> Icons.Default.Payments
                CreditType.AUTO -> Icons.Default.DirectionsCar
                CreditType.IMMOBILIER -> Icons.Default.Home
            }
            FilterChip(
                selected = selected,
                onClick = { onSelectType(type) },
                label = {
                    Text(
                        text = strings.presetLabel(type),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("credit_preset_${type.name.lowercase(Locale.US)}")
            )
        }
    }
}

@Composable
fun CreditHeroSummaryCard(
    input: CreditInput,
    result: CreditResult,
    strings: CreditStrings,
    modifier: Modifier = Modifier
) {
    val dtiColorTarget = when {
        result.debtToIncomeRatio > result.maxAllowedDti -> DtiDangerColor
        result.debtToIncomeRatio > (result.maxAllowedDti - 10.0) -> DtiCautionColor
        else -> DtiSafeColor
    }
    val dtiColor by animateColorAsState(
        targetValue = dtiColorTarget,
        animationSpec = tween(durationMillis = 240),
        label = "dti_color"
    )
    val dtiStatusLabel = when {
        result.debtToIncomeRatio > result.maxAllowedDti -> strings.dtiStatusOver
        result.debtToIncomeRatio > (result.maxAllowedDti - 10.0) -> strings.dtiStatusCaution
        else -> strings.dtiStatusSafe
    }
    val rawProgress = if (result.maxAllowedDti > 0.0) {
        (result.debtToIncomeRatio / result.maxAllowedDti).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 260),
        label = "dti_progress"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = if (result.isOverIndebted) DtiDangerColor.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("credit_hero_summary_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.monthlyPaymentLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = strings.ttcInsBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CreditCalculator.formatMad(result.monthlyPayment),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("credit_monthly_payment_value")
                    )
                    if (input.deferredMonths > 0 && result.deferredMonthlyPayment.isPositive) {
                        Text(
                            text = strings.deferredPaymentSubtitleFmt
                                .replace("{months}", input.deferredMonths.toString())
                                .replace("{amount}", CreditCalculator.formatMad(result.deferredMonthlyPayment)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // Secondary totals strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryMetricTile(
                    label = strings.financedPrincipalLabel,
                    value = CreditCalculator.formatMadCompact(result.financedPrincipal),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricTile(
                    label = strings.totalCostLabel,
                    value = CreditCalculator.formatMadCompact(result.totalCost),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricTile(
                    label = strings.totalRepaymentLabel,
                    value = CreditCalculator.formatMadCompact(result.totalRepayment),
                    modifier = Modifier.weight(1f)
                )
            }

            // Color-coded DTI Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${strings.dtiLabel} : ${CreditCalculator.formatPercent(result.debtToIncomeRatio)}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = dtiColor,
                        modifier = Modifier.testTag("credit_dti_ratio_text")
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.dtiCapFmt.replace("{cap}", CreditCalculator.formatPercent(result.maxAllowedDti)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = CircleShape,
                            color = dtiColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = dtiStatusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = dtiColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(dtiColor)
                    )
                }
            }

            // Compact Banking Warnings Banner
            if (result.warnings.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DtiDangerColor.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, DtiDangerColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("credit_warnings_banner")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        result.warnings.forEach { warning ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = DtiDangerColor,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(15.dp)
                                )
                                Text(
                                    text = warning,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CreditSyncedSliderField(
    label: String,
    value: Double,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    isInteger: Boolean = false,
    suffix: String = "",
    supportingSubtitle: String? = null,
    errorText: String? = null,
    testTagPrefix: String
) {
    var textState by remember {
        mutableStateOf(formatEditableNumber(value, isInteger))
    }

    LaunchedEffect(value) {
        val parsed = parseLocalizedNumber(textState)
        val delta = if (parsed != null) abs(parsed - value) else Double.MAX_VALUE
        val threshold = if (isInteger) 0.5 else 0.005
        if (delta >= threshold) {
            textState = formatEditableNumber(value, isInteger)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = textState,
            onValueChange = { raw ->
                textState = raw
                val parsed = parseLocalizedNumber(raw)
                if (parsed != null) {
                    onValueChange(if (isInteger) parsed.roundToLong().toDouble() else parsed)
                } else if (raw.isBlank()) {
                    onValueChange(0.0)
                }
            },
            label = { Text(text = label) },
            suffix = if (suffix.isNotBlank()) {
                { Text(text = suffix, style = MaterialTheme.typography.labelMedium) }
            } else null,
            supportingText = when {
                !errorText.isNullOrBlank() -> {
                    { Text(text = errorText, color = MaterialTheme.colorScheme.error) }
                }
                !supportingSubtitle.isNullOrBlank() -> {
                    { Text(text = supportingSubtitle) }
                }
                else -> null
            },
            isError = !errorText.isNullOrBlank(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isInteger) KeyboardType.Number else KeyboardType.Decimal
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${testTagPrefix}_input")
        )

        val clampedSlider = value.toFloat().coerceIn(valueRange.start, valueRange.endInclusive)
        Slider(
            value = clampedSlider,
            onValueChange = { sliderVal ->
                val next = if (isInteger) {
                    sliderVal.roundToLong().toDouble()
                } else {
                    (sliderVal * 100.0).roundToLong() / 100.0
                }
                textState = formatEditableNumber(next, isInteger)
                onValueChange(next)
            },
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${testTagPrefix}_slider")
        )
    }
}

@Composable
fun CreditSyncedMoneySliderField(
    label: String,
    value: Money,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Money) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "MAD",
    supportingSubtitle: String? = null,
    errorText: String? = null,
    testTagPrefix: String
) {
    fun formatMoneyEditable(money: Money): String {
        return if (money.centimes % 100L == 0L) {
            (money.centimes / 100L).toString()
        } else {
            money.toCanonicalDecimalString()
        }
    }

    var textState by remember {
        mutableStateOf(formatMoneyEditable(value))
    }

    LaunchedEffect(value) {
        val parsedCentimes = MoneyParser.parseMoroccanAmountToCentimes(textState)
        val deltaCentimes = if (parsedCentimes != null) abs(parsedCentimes - value.centimes) else Long.MAX_VALUE
        if (deltaCentimes >= 1L) {
            textState = formatMoneyEditable(value)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = textState,
            onValueChange = { raw ->
                textState = raw
                val parsedCentimes = MoneyParser.parseMoroccanAmountToCentimes(raw)
                if (parsedCentimes != null) {
                    onValueChange(Money.fromCentimes(parsedCentimes).coerceAtLeast(Money.ZERO))
                } else if (raw.isBlank()) {
                    onValueChange(Money.ZERO)
                }
            },
            label = { Text(text = label) },
            suffix = if (suffix.isNotBlank()) {
                { Text(text = suffix, style = MaterialTheme.typography.labelMedium) }
            } else null,
            supportingText = when {
                !errorText.isNullOrBlank() -> {
                    { Text(text = errorText, color = MaterialTheme.colorScheme.error) }
                }
                !supportingSubtitle.isNullOrBlank() -> {
                    { Text(text = supportingSubtitle) }
                }
                else -> null
            },
            isError = !errorText.isNullOrBlank(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${testTagPrefix}_input")
        )

        val clampedSlider = value.toBigDecimalDh().toFloat().coerceIn(valueRange.start, valueRange.endInclusive)
        Slider(
            value = clampedSlider,
            onValueChange = { sliderVal ->
                val next = Money.fromDh(sliderVal.roundToLong().coerceAtLeast(0L))
                textState = formatMoneyEditable(next)
                onValueChange(next)
            },
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${testTagPrefix}_slider")
        )
    }
}

@Composable
fun CreditSaveConfirmDialog(
    input: CreditInput,
    result: CreditResult,
    strings: CreditStrings,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (linkToMonthlyExpenses: Boolean) -> Unit
) {
    var linkToMonthlyExpenses by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = strings.saveDialogTitle,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.presetLabel(input.creditType),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = strings.saveDialogMonthlyFmt.replace(
                        "{amount}",
                        CreditCalculator.formatMad(result.monthlyPayment)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = strings.saveDialogTotalCostFmt.replace(
                        "{amount}",
                        CreditCalculator.formatMad(result.totalCost)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = strings.saveDialogDtiFmt
                        .replace("{dti}", CreditCalculator.formatPercent(result.debtToIncomeRatio))
                        .replace("{cap}", CreditCalculator.formatPercent(result.maxAllowedDti)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = strings.saveDialogLinkSwitchTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = strings.saveDialogLinkSwitchDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = linkToMonthlyExpenses,
                        onCheckedChange = { linkToMonthlyExpenses = it },
                        modifier = Modifier.testTag("credit_save_link_expenses_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(linkToMonthlyExpenses) },
                enabled = !isSaving,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("credit_save_confirm_button")
            ) {
                Text(text = strings.saveDialogConfirm, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("credit_save_cancel_button")
            ) {
                Text(text = strings.saveDialogCancel)
            }
        }
    )
}

@Composable
fun CreditLocalModeSignInDialog(
    strings: CreditStrings,
    onDismiss: () -> Unit,
    onSignInClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = strings.localModeSignInTitle, fontWeight = FontWeight.Bold) },
        text = { Text(text = strings.localModeSignInBody) },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onSignInClick()
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("credit_local_signin_button")
            ) {
                Text(text = strings.localModeSignInCta)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = strings.saveDialogCancel)
            }
        }
    )
}

private fun formatEditableNumber(value: Double, isInteger: Boolean): String {
    if (!value.isFinite()) return "0"
    return if (isInteger) {
        value.roundToLong().toString()
    } else if (abs(value - value.roundToLong()) < 0.005) {
        value.roundToLong().toString()
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}

private fun parseLocalizedNumber(raw: String): Double? {
    val cleaned = raw.trim().replace(" ", "").replace('\u00A0'.toString(), "").replace(',', '.')
    if (cleaned.isBlank()) return null
    return cleaned.toDoubleOrNull()
}
