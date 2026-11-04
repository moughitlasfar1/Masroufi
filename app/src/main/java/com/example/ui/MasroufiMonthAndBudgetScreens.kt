package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialItem
import com.example.domain.AppStrings
import com.example.domain.EnvelopeStatus
import com.example.domain.PayCycleEngine
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.AccentViolet
import kotlin.math.abs
import kotlin.math.max

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.ui.theme.ExpressiveAnimatedProgressBar
import com.example.ui.theme.ExpressiveGlassCard
import com.example.ui.theme.ExpressiveGlassSurface
import com.example.ui.theme.ExpressiveSectionHeader

@Composable
fun MonthScreen(
    state: DashboardState,
    strings: AppStrings,
    onUpdateWhatIf: (String, Int) -> Unit,
    onContributeSeasonal: (FinancialItem) -> Unit,
    onOpenNewSeasonal: (FinancialItem?) -> Unit,
    onDeleteItem: (String) -> Unit,
    onShiftCycle: (Int) -> Unit = {},
    onQuickCreateSeasonalTemplate: (String, Double, String) -> Unit = { _, _, _ -> }
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val hide = state.hideAmounts
    val haptics = LocalHapticFeedback.current
    val lang = state.profile.language
    fun fmt(value: Double): String = PayCycleEngine.formatAmountOrMask(value, hide)
    val pct = if (state.totalAvailableBudget > 0) {
        ((state.cycleExpenses / state.totalAvailableBudget) * 100.0).safeRoundToInt()
    } else 0
    val isOver = state.remainingBudget < 0

    val selectedEnv = state.envelopes.find { it.config.key == state.whatIfState.selectedEnvelopeKey }
        ?: state.envelopes.firstOrNull()
    val baseSpendForSim = max(selectedEnv?.spent ?: 0.0, selectedEnv?.baseAllocated ?: 500.0)
    val monthlySaved = baseSpendForSim * (state.whatIfState.cutPercentage / 100.0)
    val yearlySaved = monthlySaved * 12.0
    val goalImpactText = PayCycleEngine.computeWhatIfGoalImpact(
        monthlySaved = monthlySaved,
        goals = state.goals,
        seasonals = state.seasonals,
        lang = lang,
        currency = cur
    )

    var headerDragAccumulator by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("month_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Monthly Tracking Summary Card (Swipe Left/Right to change month)
        item {
            ExpressiveGlassCard(
                cornerRadius = 28.dp,
                accentRimColor = if (isOver) AccentCoral else AccentEmerald,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(state.activeWindow.cycleKey) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                headerDragAccumulator += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    headerDragAccumulator <= -72f -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onShiftCycle(1)
                                    }
                                    headerDragAccumulator >= 72f -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onShiftCycle(-1)
                                    }
                                }
                                headerDragAccumulator = 0f
                            },
                            onDragCancel = {
                                headerDragAccumulator = 0f
                            }
                        )
                    }
                    .testTag("month_header_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.monthlyTracking,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Swipe,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = state.activeWindow.cycleKey,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = strings.monthlySub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${strings.incomes}: ${fmt(state.totalAvailableBudget)} $cur",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${strings.expenses}: ${fmt(state.cycleExpenses)} $cur",
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentCoral
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                ExpressiveAnimatedProgressBar(
                    progress = (pct / 100f).safeFraction(),
                    color = if (isOver) AccentCoral else AccentEmerald,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    height = 10.dp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = strings.netBalance,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${if (isOver && !hide) "-" else ""}${fmt(abs(state.remainingBudget))} $cur",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isOver) AccentCoral else AccentEmerald
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${strings.extraIncome}: ${fmt(state.cycleIncomes)} $cur",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${strings.endOfMonthProj}: ${fmt(state.projectedCycleSpend)} $cur",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Advanced Analytics Card (Month-over-Month Comparison & Trends)
        item {
            ExpressiveGlassCard(
                cornerRadius = 28.dp,
                accentRimColor = AccentViolet,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("advanced_analytics_card")
            ) {
                val projectedDelta = state.totalAvailableBudget - state.projectedCycleSpend
                ExpressiveSectionHeader(
                    title = when (state.profile.language) {
                        "darija" -> "تحليلات متقدمة ومقارنة الشهر"
                        "ar" -> "تحليلات متقدمة ومقارنة الأشهر"
                        "en" -> "Advanced Analytics & Month Comparison"
                        else -> "Analytique Avancée & Comparaison Mensuelle"
                    },
                    subtitle = if (projectedDelta >= 0) {
                        "Projection fin de cycle : Excédent estimé de +${fmt(projectedDelta)} $cur"
                    } else {
                        "Projection fin de cycle : Déficit estimé de -${fmt(abs(projectedDelta))} $cur"
                    },
                    icon = Icons.Default.Analytics,
                    accentColor = AccentViolet
                )

                Spacer(modifier = Modifier.height(12.dp))

                state.envelopes.forEach { env ->
                    val curSpent = env.spent
                    val prevSpent = env.previousSpent
                    val diff = curSpent - prevSpent
                    val badgeText = when {
                        prevSpent <= 0.0 && curSpent <= 0.0 -> "Stable (0 $cur)"
                        prevSpent <= 0.0 -> "Nouveau (${fmt(curSpent)} $cur)"
                        diff <= 0.0 -> "-${fmt(abs(diff))} $cur vs mois préc."
                        else -> "+${fmt(diff)} $cur vs mois préc."
                    }
                    val badgeColor = if (diff <= 0.0) AccentEmerald else AccentCoral
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        accentBorderColor = badgeColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = env.config.localizedName(state.profile.language),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Actuel: ${fmt(curSpent)} $cur · Préc.: ${fmt(prevSpent)} $cur",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. "What-If" Savings Simulator Card with Goal Months-Sooner Impact
        item {
            ExpressiveGlassCard(
                cornerRadius = 28.dp,
                accentRimColor = AccentSkyBlue,
                modifier = Modifier.fillMaxWidth()
            ) {
                ExpressiveSectionHeader(
                    title = strings.whatIfTitle,
                    subtitle = strings.whatIfSub,
                    icon = Icons.Default.Tune,
                    accentColor = AccentSkyBlue
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.envelopes.forEach { env ->
                        FilterChip(
                            selected = env.config.key == (selectedEnv?.config?.key ?: ""),
                            onClick = {
                                onUpdateWhatIf(env.config.key, state.whatIfState.cutPercentage)
                            },
                            label = {
                                Text(
                                    text = env.config.localizedName(state.profile.language).take(22),
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = state.whatIfState.cutPercentage.toFloat().coerceIn(5f, 70f),
                    onValueChange = { newVal ->
                        onUpdateWhatIf(
                            selectedEnv?.config?.key ?: "cafe",
                            newVal.safeRoundToInt().coerceIn(5, 70)
                        )
                    },
                    valueRange = 5f..70f,
                    modifier = Modifier.testTag("what_if_slider")
                )

                ExpressiveGlassSurface(
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    accentBorderColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = strings.whatIfResult
                                .replace("{pct}", state.whatIfState.cutPercentage.toString())
                                .replace("{m1}", PayCycleEngine.formatDh(monthlySaved))
                                .replace("{m12}", PayCycleEngine.formatDh(yearlySaved)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (goalImpactText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = goalImpactText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("what_if_goal_impact_text")
                            )
                        }
                    }
                }
            }
        }

        // 4. Seasonal & Yearly Planners (Ramadan, Aïd al-Adha sheep fund, Rentrée Scolaire) with Countdown & Suggested Monthly Saving
        item {
            val seasonalTemplates = remember { PayCycleEngine.moroccanSeasonalTemplates() }
            ExpressiveGlassCard(
                cornerRadius = 28.dp,
                accentRimColor = AccentAmber,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("seasonal_envelopes_card")
            ) {
                ExpressiveSectionHeader(
                    title = strings.seasonalTitle,
                    subtitle = strings.seasonalSub,
                    icon = Icons.Default.Event,
                    accentColor = AccentAmber,
                    trailingContent = {
                        FilledTonalButton(
                            onClick = { onOpenNewSeasonal(null) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_add_seasonal")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 1-Tap Moroccan Seasonal Envelope Templates
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    seasonalTemplates.forEach { tpl ->
                        ExpressiveGlassSurface(
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            accentBorderColor = AccentAmber,
                            onClick = {
                                onQuickCreateSeasonalTemplate(
                                    "${tpl.emoji} ${tpl.name}",
                                    tpl.targetAmount,
                                    tpl.targetIsoDate
                                )
                            },
                            modifier = Modifier.testTag("seasonal_tpl_${tpl.key}")
                        ) {
                            Text(
                                text = "+ ${tpl.emoji} ${tpl.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                state.seasonals.forEach { item ->
                    val prog = if (item.amount > 0) (item.secondaryAmount / item.amount).toFloat().safeFraction() else 0f
                    val countdown = PayCycleEngine.computeSeasonalCountdown(
                        targetIso = item.dateOrCycle,
                        targetAmount = item.amount,
                        savedAmount = item.secondaryAmount
                    )
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        accentBorderColor = AccentAmber,
                        onClick = { onOpenNewSeasonal(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .testTag("seasonal_item_${item.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(text = item.name, style = MaterialTheme.typography.labelLarge)
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${fmt(item.secondaryAmount)} / ${fmt(item.amount)} $cur · Échéance: ${item.dateOrCycle}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = AccentAmber.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = "⏳ J-${countdown.daysRemaining} (${countdown.monthsRemaining}m)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AccentAmber,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (countdown.suggestedMonthlySaving > 0.0) {
                                            Text(
                                                text = "💡 ${fmt(countdown.suggestedMonthlySaving)} $cur/mois",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AccentEmerald,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(
                                        onClick = { onContributeSeasonal(item) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("+ 200 $cur", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { onDeleteItem(item.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ExpressiveAnimatedProgressBar(
                                progress = prog,
                                color = AccentAmber,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                height = 8.dp
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
fun BudgetAndDaretScreen(
    state: DashboardState,
    strings: AppStrings,
    onOpenCustomizeEnvelopes: () -> Unit,
    onPayDaretRound: (FinancialItem) -> Unit,
    onOpenNewDaret: (FinancialItem?) -> Unit,
    onSettleDebt: (FinancialItem) -> Unit,
    onOpenNewDebt: (FinancialItem?) -> Unit,
    onContributeGoal: (FinancialItem) -> Unit,
    onOpenNewGoal: (FinancialItem?) -> Unit,
    onOpenNewBill: (FinancialItem?) -> Unit,
    onPayBill: (FinancialItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onPartialRepayDebt: (FinancialItem, Double) -> Unit = { _, _ -> },
    onSendWhatsAppReminder: (String) -> Unit = {},
    onOpenSplitBillModal: () -> Unit = {},
    onQuickCreateBillTemplate: (String, Double, Int) -> Unit = { _, _, _ -> }
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val hide = state.hideAmounts
    val haptics = LocalHapticFeedback.current
    fun fmt(value: Double): String = PayCycleEngine.formatAmountOrMask(value, hide)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("budget_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Category Donut Chart + Customizable Envelopes
        item {
            ExpressiveGlassCard(
                cornerRadius = 28.dp,
                accentRimColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.envelopesTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = strings.envelopesSub,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpenCustomizeEnvelopes,
                        modifier = Modifier.testTag("btn_customize_envelopes")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = strings.editEnvelopesBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Category Donut Chart
                CategoryDonutChart(
                    envelopes = state.envelopes,
                    totalSpent = state.cycleExpenses,
                    lang = state.profile.language,
                    currencySymbol = cur,
                    hideAmounts = hide
                )

                Spacer(modifier = Modifier.height(16.dp))

                state.envelopes.forEach { envStatus ->
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(22.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        accentBorderColor = if (envStatus.isOver90Percent) AccentCoral else AccentEmerald,
                        onClick = { onOpenCustomizeEnvelopes() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = envStatus.config.localizedName(state.profile.language),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (envStatus.config.hasCap) {
                                        "${fmt(envStatus.totalAvailable)} $cur (${envStatus.config.percentage.safeRoundToInt()}%)"
                                    } else {
                                        strings.noCap
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (envStatus.rolloverAmount > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = strings.rolloverBadge.replace("{amt}", fmt(envStatus.rolloverAmount)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentAmber
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${strings.expenses}: ${fmt(envStatus.spent)} $cur",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${envStatus.usagePercent}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (envStatus.isOver90Percent) AccentCoral else MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            ExpressiveAnimatedProgressBar(
                                progress = (envStatus.usagePercent / 100f).safeFraction(),
                                color = if (envStatus.isOver90Percent) AccentCoral else AccentEmerald,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                height = 10.dp
                            )
                        }
                    }
                }
            }
        }

        // 2. Moroccan Daret/Jam3iya Tracker Card (Members, Monthly Contribution, Whose Turn, Payout Date, WhatsApp Reminder)
        item {
            ExpressiveGlassCard(
                cornerRadius = 28.dp,
                accentRimColor = AccentViolet,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("daret_tracker_card")
            ) {
                ExpressiveSectionHeader(
                    title = strings.daretTitle,
                    subtitle = strings.daretSub,
                    icon = Icons.Default.Groups,
                    accentColor = AccentViolet,
                    trailingContent = {
                        FilledTonalButton(
                            onClick = { onOpenNewDaret(null) },
                            modifier = Modifier.testTag("btn_add_daret")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                state.darets.forEach { daret ->
                    val totalMembers = daret.secondaryAmount.safeRoundToInt().coerceAtLeast(1)
                    val totalPot = daret.amount * totalMembers
                    val progress = (daret.extraInt.toFloat() / totalMembers.toFloat()).safeFraction()
                    val whoseTurn = daret.subtitle.ifBlank { "Membre #${(daret.extraInt + 1).coerceAtMost(totalMembers)}" }
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        accentBorderColor = AccentViolet,
                        onClick = { onOpenNewDaret(daret) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .testTag("daret_item_${daret.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(text = daret.name, style = MaterialTheme.typography.labelLarge)
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = strings.daretTurnLabel
                                            .replace("{turn}", daret.dayOrTurn.toString())
                                            .replace("{total}", totalMembers.toString())
                                            .replace("{pot}", fmt(totalPot)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${fmt(daret.amount)} $cur/mois · Payé: ${daret.extraInt}/$totalMembers · Réception: ${daret.dateOrCycle}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 11.5.sp,
                                        color = AccentViolet
                                    )
                                    Text(
                                        text = "👤 Tour en cours : $whoseTurn",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(
                                        onClick = { onPayDaretRound(daret) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = strings.daretPayRound, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(
                                        onClick = {
                                            val msg = "Salam l-khout! Rappel Daret '${daret.name}' : cotisation ${daret.amount.toInt()} $cur (Tour actuel: $whoseTurn, réception prévue: ${daret.dateOrCycle}). Lah yssakher 🙏"
                                            onSendWhatsAppReminder(msg)
                                        },
                                        modifier = Modifier.testTag("btn_whatsapp_daret_${daret.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Rappel WhatsApp Daret",
                                            tint = AccentEmerald
                                        )
                                    }
                                    IconButton(onClick = { onDeleteItem(daret.id) }) {
                                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ExpressiveAnimatedProgressBar(
                                progress = progress,
                                color = AccentViolet,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                height = 8.dp
                            )
                        }
                    }
                }
            }
        }

        // 3. Sellef Tracker (Crédits & Dettes) with Partial Repayments, WhatsApp Reminder & Bill Splitting
        item {
            ExpressiveGlassCard(
                cornerRadius = 28.dp,
                accentRimColor = AccentSkyBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sellef_tracker_card")
            ) {
                ExpressiveSectionHeader(
                    title = "${strings.debtsTitle} (Sellef)",
                    subtitle = "Suivez qui vous doit & vos remboursements partiels",
                    icon = Icons.Default.Handshake,
                    accentColor = AccentSkyBlue,
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = onOpenSplitBillModal,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_open_split_bill")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                    contentDescription = "Partager une note",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Split", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(
                                onClick = { onOpenNewDebt(null) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_add_sellef")
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                state.debts.forEach { debt ->
                    val isLent = debt.dateOrCycle != "borrowed"
                    val remDebt = max(0.0, debt.amount - debt.secondaryAmount)
                    val prog = if (debt.amount > 0) (debt.secondaryAmount / debt.amount).toFloat().safeFraction() else 0f
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        accentBorderColor = if (isLent) AccentEmerald else AccentCoral,
                        onClick = { onOpenNewDebt(debt) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .testTag("sellef_item_${debt.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${debt.name} (${if (isLent) strings.debtsLent else strings.debtsBorrowed})",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (debt.subtitle.isNotBlank()) {
                                        Text(
                                            text = debt.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "Reste: ${fmt(remDebt)} $cur / ${fmt(debt.amount)} $cur",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (remDebt > 0) {
                                        IconButton(
                                            onClick = {
                                                val waMsg = if (isLent) {
                                                    "Salam ${debt.name} ! Petit rappel sympa pour le sellef (${debt.subtitle.ifBlank { "avance" }}) : il reste ${remDebt.toInt()} $cur sur ${debt.amount.toInt()} $cur. Lah yhfdek 🙏"
                                                } else {
                                                    "Salam ${debt.name} ! Je te confirme qu'il me reste ${remDebt.toInt()} $cur à te régler sur ${debt.amount.toInt()} $cur. Merci 🙏"
                                                }
                                                onSendWhatsAppReminder(waMsg)
                                            },
                                            modifier = Modifier.testTag("btn_whatsapp_sellef_${debt.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Rappel WhatsApp Sellef",
                                                tint = AccentEmerald
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onDeleteItem(debt.id) }) {
                                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete")
                                    }
                                }
                            }

                            // Partial Repayment Chips (+50, +100, +200, Tout)
                            if (remDebt > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(50.0, 100.0, 200.0).filter { it < remDebt }.forEach { stepAmt ->
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .clickable {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onPartialRepayDebt(debt, stepAmt)
                                                }
                                                .testTag("btn_sellef_partial_${debt.id}_${stepAmt.toInt()}")
                                        ) {
                                            Text(
                                                text = "+${stepAmt.toInt()} $cur",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onSettleDebt(debt)
                                            }
                                            .testTag("btn_sellef_settle_${debt.id}")
                                    ) {
                                        Text(
                                            text = "${strings.recordRepayment} (${fmt(remDebt)} $cur)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            ExpressiveAnimatedProgressBar(
                                progress = prog,
                                color = if (isLent) AccentEmerald else AccentCoral,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                height = 8.dp
                            )
                        }
                    }
                }
            }
        }

        // 4. Savings Goals & Recurring Moroccan Bills Management (Lydec/Redal/Amendis, ONEE, IAM, Inwi, Orange, Loyer)
        item {
            val billTemplates = remember { PayCycleEngine.moroccanBillTemplates() }
            ExpressiveGlassCard(
                cornerRadius = 28.dp,
                accentRimColor = AccentEmerald,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("goals_and_bills_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.Savings, contentDescription = null, tint = AccentEmerald)
                        Text(text = strings.goalsTitle, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onOpenNewGoal(null) }) {
                            Text("+ Objectif", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { onOpenNewBill(null) },
                            modifier = Modifier.testTag("btn_open_new_bill")
                        ) {
                            Text("+ Facture", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 1-Tap Moroccan Bill Templates Bar (Lydec, Redal, Amendis, ONEE, IAM, Inwi, Orange, Rent)
                Text(
                    text = "⚡ Modèles Maroc (jour d'échéance pré-rempli) :",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    billTemplates.forEach { tpl ->
                        ExpressiveGlassSurface(
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            accentBorderColor = AccentEmerald,
                            onClick = {
                                onQuickCreateBillTemplate(
                                    "${tpl.emoji} ${tpl.name}",
                                    tpl.defaultAmount,
                                    tpl.defaultDueDay
                                )
                            },
                            modifier = Modifier.testTag("bill_tpl_${tpl.key}")
                        ) {
                            Text(
                                text = "+ ${tpl.emoji} ${tpl.name} (J${tpl.defaultDueDay})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Recurring Bills List
                state.bills.forEach { bill ->
                    val isPaid = PayCycleEngine.isBillPaidInCycle(bill, state.activeWindow.cycleKey)
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(18.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        accentBorderColor = if (isPaid) AccentEmerald else AccentAmber,
                        onClick = { onOpenNewBill(bill) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bill.name,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "${fmt(bill.amount)} $cur · Échéance le ${bill.dayOrTurn} du mois",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 11.5.sp,
                                    color = if (isPaid) AccentEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isPaid) {
                                    FilledTonalButton(
                                        onClick = { onPayBill(bill) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Payer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text(
                                        text = "Payée ✓",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentEmerald,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                                IconButton(onClick = { onDeleteItem(bill.id) }) {
                                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }

                state.goals.forEach { goal ->
                    val prog = if (goal.amount > 0) (goal.secondaryAmount / goal.amount).toFloat().safeFraction() else 0f
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        accentBorderColor = AccentEmerald,
                        onClick = { onOpenNewGoal(goal) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(text = goal.name, style = MaterialTheme.typography.labelLarge)
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${fmt(goal.secondaryAmount)} / ${fmt(goal.amount)} $cur (${(prog * 100f).safeRoundToInt()}%)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { onContributeGoal(goal) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("+ 200 $cur", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { onDeleteItem(goal.id) }) {
                                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ExpressiveAnimatedProgressBar(
                                progress = prog,
                                color = AccentEmerald,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                height = 8.dp
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
fun CategoryDonutChart(
    envelopes: List<EnvelopeStatus>,
    totalSpent: Double,
    lang: String,
    currencySymbol: String = "DH",
    hideAmounts: Boolean = false
) {
    val palette = listOf(AccentEmerald, AccentSkyBlue, AccentAmber, AccentViolet, AccentCoral)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier.size(132.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(116.dp)) {
                val stroke = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Butt)
                if (totalSpent <= 0.0) {
                    drawArc(
                        color = Color.Gray.copy(alpha = 0.25f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke
                    )
                } else {
                    var startAngle = -90f
                    envelopes.forEachIndexed { index, env ->
                        if (env.spent > 0) {
                            val sweep = ((env.spent / totalSpent) * 360.0).toFloat()
                            if (sweep.isFinite() && sweep > 0f) {
                                drawArc(
                                    color = palette[index % palette.size],
                                    startAngle = startAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    style = stroke
                                )
                                startAngle += sweep
                            }
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = PayCycleEngine.formatAmountOrMask(totalSpent, hideAmounts),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = currencySymbol,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            envelopes.forEachIndexed { index, env ->
                val sharePct = if (totalSpent > 0) ((env.spent / totalSpent) * 100.0).safeRoundToInt() else 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(palette[index % palette.size])
                    )
                    Text(
                        text = env.config.localizedName(lang).take(20),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        text = "$sharePct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
