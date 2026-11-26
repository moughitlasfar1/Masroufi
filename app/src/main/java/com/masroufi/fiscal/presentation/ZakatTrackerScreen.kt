package com.masroufi.fiscal.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.PayCycleEngine
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.ExpressiveAnimatedProgressBar
import com.example.ui.theme.ExpressiveGlassCard
import com.masroufi.core.Money
import com.masroufi.fiscal.domain.FiscalStrings
import com.masroufi.fiscal.domain.HawlStatus
import com.masroufi.fiscal.domain.NisabStandard
import com.masroufi.fiscal.domain.ZakatCalculator
import com.masroufi.fiscal.domain.ZakatConfig

/**
 * Step 5: Compact, distraction-free Zakat & Hawl tracker screen in `com.masroufi.fiscal.presentation`.
 * - Eligible wealth = Accounts + Savings - Short-term Sellef debts owed.
 * - Zakat = 2.5% when at or above editable offline Gold (85g) / Silver (595g) Nisab.
 * - Hawl tracked via `HijrahDate`; reuses Step 1's monthly reserve flow on CTA tap.
 */
@Composable
fun ZakatTrackerScreen(
    accountsTotalMad: Money,
    savingsTotalMad: Money,
    shortTermDebtsOwedMad: Money,
    alreadyReservedMad: Money,
    initialConfig: ZakatConfig,
    language: String,
    currencySymbol: String,
    hideAmounts: Boolean,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onSaveConfig: (ZakatConfig) -> Unit,
    onCreateMonthlyReserve: (zakatDueMad: Money, hawlDueIso: String) -> Unit
) {
    BackHandler(onBack = onBack)

    val strings = remember(language) { FiscalStrings.forLang(language) }
    val cur = currencySymbol.ifBlank { "DH" }
    val todayIso = remember { PayCycleEngine.todayIso() }

    fun formatEditableMoney(m: Money): String =
        if (m.centimes % 100L == 0L) (m.centimes / 100L).toString() else m.toCanonicalDecimalString()

    var nisabStandard by remember(initialConfig) { mutableStateOf(initialConfig.nisabStandard) }
    var goldPriceText by remember(initialConfig) { mutableStateOf(formatEditableMoney(initialConfig.goldPriceMadPerGram)) }
    var silverPriceText by remember(initialConfig) { mutableStateOf(formatEditableMoney(initialConfig.silverPriceMadPerGram)) }
    var pricesAsOfIso by remember(initialConfig) { mutableStateOf(initialConfig.pricesAsOfIso) }
    var hawlStartText by remember(initialConfig) {
        mutableStateOf(initialConfig.hawlStartIso.ifBlank { todayIso })
    }

    val liveConfig = remember(nisabStandard, goldPriceText, silverPriceText, pricesAsOfIso, hawlStartText, initialConfig) {
        val goldParsed = PayCycleEngine.parseFlexibleAmount(goldPriceText)
            .takeIf { it.isPositive } ?: initialConfig.goldPriceMadPerGram
        val silverParsed = PayCycleEngine.parseFlexibleAmount(silverPriceText)
            .takeIf { it.isPositive } ?: initialConfig.silverPriceMadPerGram
        initialConfig.copy(
            nisabStandard = nisabStandard,
            goldPriceMadPerGram = goldParsed,
            silverPriceMadPerGram = silverParsed,
            pricesAsOfIso = pricesAsOfIso,
            hawlStartIso = hawlStartText.trim()
        )
    }

    val result = remember(accountsTotalMad, savingsTotalMad, shortTermDebtsOwedMad, liveConfig, todayIso, alreadyReservedMad) {
        ZakatCalculator.calculate(
            accountsTotalMad = accountsTotalMad,
            savingsTotalMad = savingsTotalMad,
            shortTermDebtsOwedMad = shortTermDebtsOwedMad,
            config = liveConfig,
            todayIso = todayIso,
            alreadyReservedMad = alreadyReservedMad
        )
    }

    fun fmt(v: Money): String = "${PayCycleEngine.formatAmountOrMask(v, hideAmounts)} $cur"

    val (statusLabel, statusColor) = when (result.status) {
        HawlStatus.BELOW_NISAB -> strings.zakatStatusBelowNisab to MaterialTheme.colorScheme.onSurfaceVariant
        HawlStatus.HAWL_RUNNING -> strings.zakatStatusHawlRunning to AccentAmber
        HawlStatus.DUE_NOW -> strings.zakatStatusDueNow to AccentEmerald
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
                    modifier = Modifier.testTag("zakat_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.cancelBtn,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = strings.zakatTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.16f),
                    modifier = Modifier.testTag("zakat_status_pill")
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("zakat_tracker_screen"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Eligible Wealth & 2.5% Zakat Summary Card
                ExpressiveGlassCard(
                    cornerRadius = 28.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("zakat_wealth_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.zakatEligibleWealthTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ZakatBreakdownRow(strings.zakatAccountsLine, fmt(result.accountsTotalMad))
                        ZakatBreakdownRow(strings.zakatSavingsLine, fmt(result.savingsTotalMad))
                        ZakatBreakdownRow(
                            label = strings.zakatDebtsMinusLine,
                            value = "-${fmt(result.shortTermDebtsOwedMad)}",
                            valueColor = AccentCoral
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ZakatBreakdownRow(
                            label = strings.zakatNetEligibleLine,
                            value = fmt(result.eligibleWealthMad),
                            bold = true
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDarkTheme) 0.55f else 0.72f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.zakatDue25Label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = fmt(result.zakatDueMad),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("zakat_due_amount_text")
                                )
                            }
                        }
                    }
                }

                // 2. Hawl Progress & Hijri Countdown Card
                ExpressiveGlassCard(
                    cornerRadius = 26.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("zakat_hawl_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = strings.zakatHawlTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = hawlStartText,
                            onValueChange = { hawlStartText = it.take(10) },
                            label = { Text(strings.zakatHawlStartLabel) },
                            placeholder = { Text(strings.zakatHawlStartHint) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("zakat_hawl_start_input")
                        )
                        ExpressiveAnimatedProgressBar(
                            progress = if (result.status == HawlStatus.BELOW_NISAB) 0f else result.hawlProgressFraction,
                            color = statusColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            height = 8.dp
                        )
                        Text(
                            text = strings.zakatHawlHijriAnniversaryFmt
                                .replace("{hijri}", result.hawlHijriDueLabel)
                                .replace("{gregorian}", result.hawlDueDateIso),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (result.status == HawlStatus.HAWL_RUNNING) {
                            Text(
                                text = strings.zakatHawlDaysLeftFmt.replace("{days}", result.daysRemaining.toString()),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentAmber,
                                modifier = Modifier.testTag("zakat_days_remaining_text")
                            )
                        }

                        // Primary CTA: Set aside monthly (reuses Step 1 reserve engine)
                        Button(
                            onClick = {
                                onSaveConfig(liveConfig)
                                onCreateMonthlyReserve(result.zakatDueMad, result.hawlDueDateIso)
                            },
                            enabled = result.zakatDueMad.isPositive,
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("zakat_set_aside_monthly_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.zakatSetAsideMonthlyBtn, fontWeight = FontWeight.ExtraBold)
                        }
                        if (result.zakatDueMad.isPositive) {
                            Text(
                                text = strings.zakatSetAsideHintFmt
                                    .replace("{monthly}", fmt(result.monthlyReserveSuggestionMad))
                                    .replace("{date}", result.hawlDueDateIso),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 3. Offline Nisab & Editable Metal Prices Card
                ExpressiveGlassCard(
                    cornerRadius = 26.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("zakat_nisab_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = strings.zakatNisabTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = nisabStandard == NisabStandard.GOLD_85G,
                                onClick = { nisabStandard = NisabStandard.GOLD_85G },
                                label = { Text(strings.zakatStandardGold, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("zakat_standard_gold")
                            )
                            FilterChip(
                                selected = nisabStandard == NisabStandard.SILVER_595G,
                                onClick = { nisabStandard = NisabStandard.SILVER_595G },
                                label = { Text(strings.zakatStandardSilver, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("zakat_standard_silver")
                            )
                        }
                        Text(
                            text = "Nisab : ${fmt(result.nisabThresholdMad)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = goldPriceText,
                                onValueChange = { goldPriceText = it.take(10) },
                                label = { Text(strings.zakatGoldPriceLabel) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("zakat_gold_price_input")
                            )
                            OutlinedTextField(
                                value = silverPriceText,
                                onValueChange = { silverPriceText = it.take(10) },
                                label = { Text(strings.zakatSilverPriceLabel) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("zakat_silver_price_input")
                            )
                        }
                        Text(
                            text = strings.zakatPricesAsOfFmt.replace("{date}", pricesAsOfIso),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                pricesAsOfIso = todayIso
                                onSaveConfig(liveConfig.copy(pricesAsOfIso = todayIso))
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("zakat_save_rates_btn")
                        ) {
                            Text(strings.zakatSaveRatesBtn, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 4. Scholar Disclaimer Note
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = strings.zakatDisclaimer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ZakatBreakdownRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold,
            color = valueColor
        )
    }
}
