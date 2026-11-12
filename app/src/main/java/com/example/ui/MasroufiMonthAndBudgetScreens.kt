package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccountItem
import com.example.data.FinancialItem
import com.example.domain.AppStrings
import com.example.domain.DetectedSubscription
import com.example.domain.EnvelopeStatus
import com.example.domain.PayCycleEngine
import com.example.domain.SmartReminder
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.ExpressiveAnimatedProgressBar
import com.example.ui.theme.ExpressiveGlassCard
import com.example.ui.theme.ExpressiveGlassSurface
import com.example.ui.theme.ExpressiveSectionHeader
import com.example.ui.theme.VyxelPillDark
import com.example.ui.theme.distinctAccountColor
import com.example.ui.theme.distinctEnvelopeColor
import com.example.ui.theme.semanticExpenseColor
import com.example.ui.theme.semanticIncomeColor
import kotlin.math.abs
import kotlin.math.max

@Composable
fun MonthScreen(
    state: DashboardState,
    strings: AppStrings,
    moreFeaturesEnabled: Boolean = true,
    onUpdateWhatIf: (String, Int) -> Unit,
    onContributeSeasonal: (FinancialItem) -> Unit,
    onOpenNewSeasonal: (FinancialItem?) -> Unit,
    onDeleteItem: (String) -> Unit,
    onShiftCycle: (Int) -> Unit = {},
    onQuickCreateSeasonalTemplate: (String, Double, String) -> Unit = { _, _, _ -> },
    onConvertSubscriptionToBill: (DetectedSubscription) -> Unit = {},
    onDismissDetectedSubscription: (DetectedSubscription) -> Unit = {},
    onShareText: (String) -> Unit = {}
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
                accentRimColor = MaterialTheme.colorScheme.primary,
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
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
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
                val isDark = state.profile.isDarkTheme
                val incColor = semanticIncomeColor(isDark)
                val expColor = semanticExpenseColor(isDark)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = incColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, incColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "+ ${strings.incomes}: ${fmt(state.totalAvailableBudget)} $cur",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = incColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = expColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, expColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "- ${strings.expenses}: ${fmt(state.cycleExpenses)} $cur",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = expColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                ExpressiveAnimatedProgressBar(
                    progress = (pct / 100f).safeFraction(),
                    color = if (isOver) expColor else MaterialTheme.colorScheme.primary,
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
                    text = "${if (isOver && !hide) "-" else "+"}${fmt(abs(state.remainingBudget))} $cur",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isOver) expColor else incColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${strings.extraIncome}: +${fmt(state.cycleIncomes)} $cur",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = incColor
                    )
                    Text(
                        text = "${strings.endOfMonthProj}: ${fmt(state.projectedCycleSpend)} $cur",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 1B. Monthly Review Card (moved from Home in Phase 2)
        item(key = "insights_monthly_review") {
            val vsLastMonthDiff = if (state.previousCycleExpenses > 0.0) {
                (((state.cycleExpenses - state.previousCycleExpenses) / state.previousCycleExpenses) * 100.0).safeRoundToInt()
            } else 0
            val vsLastMonthText = if (state.previousCycleExpenses > 0.0) {
                val sign = if (vsLastMonthDiff > 0) "+" else ""
                "$sign$vsLastMonthDiff% ${strings.vsLastMonthSuffix}"
            } else {
                strings.firstTrackedCycleLabel
            }
            val topCatName = state.topEnvelopeStatus?.config?.localizedName(lang) ?: "—"
            val topCatPct = state.topEnvelopeStatus?.usagePercent ?: 0
            val biggestDayLabel = if (state.biggestSpendingDayIso.isNotBlank()) {
                "${state.biggestSpendingDayIso} (${fmt(state.biggestSpendingDayAmount)} $cur)"
            } else "—"

            ExpressiveGlassCard(
                cornerRadius = 28.dp,
                accentRimColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_recap_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                text = "${strings.monthlyReviewTitle} • $vsLastMonthText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (state.remainingBudget >= 0) {
                                    "${strings.remainingLabel} ${fmt(state.remainingBudget)} $cur (${state.activeWindow.daysRemaining}j)"
                                } else {
                                    "${strings.overrunLabel} ${fmt(-state.remainingBudget)} $cur"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val sharePayload = buildString {
                                appendLine("📊 ${strings.monthlyReviewTitle} (${state.activeWindow.cycleKey})")
                                appendLine("• ${strings.budgetLabel} : $pct% (•••• $cur)")
                                appendLine("• ${strings.topCategoryPrefix} $topCatName ($topCatPct%)")
                                appendLine("• ${strings.biggestDayPrefix} ${state.biggestSpendingDayIso.ifBlank { "—" }} (•••• $cur)")
                                appendLine("• $vsLastMonthText")
                            }
                            onShareText(sharePayload)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_share_monthly_recap")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = strings.shareReviewDesc,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.shareBtn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                ExpressiveGlassSurface(
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${strings.topCategoryPrefix} $topCatName",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${strings.biggestDayPrefix} $biggestDayLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = vsLastMonthText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Advanced Analytics Card (Month-over-Month Comparison & Trends)
        item {
            ExpressiveGlassCard(
                cornerRadius = 28.dp,
                accentRimColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("advanced_analytics_card")
            ) {
                val isDark = state.profile.isDarkTheme
                val incColor = semanticIncomeColor(isDark)
                val expColor = semanticExpenseColor(isDark)
                val projectedDelta = state.totalAvailableBudget - state.projectedCycleSpend
                ExpressiveSectionHeader(
                    title = when (state.profile.language) {
                        "darija" -> "مقارنة الشهر"
                        "ar" -> "مقارنة الأشهر"
                        "en" -> "Month Comparison"
                        else -> "Comparaison Mensuelle"
                    },
                    subtitle = if (projectedDelta >= 0) {
                        "Projection : +${fmt(projectedDelta)} $cur"
                    } else {
                        "Projection : -${fmt(abs(projectedDelta))} $cur"
                    },
                    icon = Icons.Default.Analytics,
                    accentColor = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                state.envelopes.forEach { env ->
                    val curSpent = env.spent
                    val prevSpent = env.previousSpent
                    val diff = curSpent - prevSpent
                    val badgeText = when {
                        prevSpent <= 0.0 && curSpent <= 0.0 -> "0 $cur"
                        prevSpent <= 0.0 -> "${strings.newLabel} (-${fmt(curSpent)} $cur)"
                        diff <= 0.0 -> "-${fmt(abs(diff))} $cur ${strings.vsLastMonthSuffix}"
                        else -> "+${fmt(diff)} $cur ${strings.vsLastMonthSuffix}"
                    }
                    val badgeColor = if (diff <= 0.0) incColor else expColor
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${strings.currentShortLabel}: ${fmt(curSpent)} $cur · ${strings.previousShortLabel}: ${fmt(prevSpent)} $cur",
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

        // Phase 5: AI / Smart Insights & Recurring Subscription Detector (only when moreFeaturesEnabled is on)
        if (moreFeaturesEnabled && (state.smartInsights.isNotEmpty() || state.detectedSubscriptions.isNotEmpty())) {
            item(key = "insights_smart_assistant") {
                ExpressiveGlassCard(
                    cornerRadius = 28.dp,
                    accentRimColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_assistant_card")
                ) {
                    ExpressiveSectionHeader(
                        title = when (lang) {
                            "darija" -> "المستشار الذكي"
                            "ar" -> "المساعد الذكي"
                            "en" -> "Smart Insights"
                            else -> "Assistant Intelligent"
                        },
                        icon = Icons.Default.AutoAwesome,
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    state.smartInsights.forEach { insight ->
                        ExpressiveGlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = insight.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = insight.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (state.detectedSubscriptions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ExpressiveGlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            accentBorderColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("detected_subscriptions_section")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = when (lang) {
                                        "ar" -> "اشتراكات متكررة مكتشفة"
                                        "darija" -> "اشتراكات كتعاود"
                                        "en" -> "Recurring Charges Detected"
                                        else -> "Abonnements détectés"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                state.detectedSubscriptions.forEach { sub ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sub.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "-${fmt(sub.amount)} $cur • ${sub.occurrences}x (~le ${sub.typicalDayOfMonth})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onDismissDetectedSubscription(sub)
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.testTag("btn_dismiss_sub_${sub.name.take(10)}")
                                            ) {
                                                Text(
                                                    text = strings.notABillBtn,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            FilledTonalButton(
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onConvertSubscriptionToBill(sub)
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.testTag("btn_convert_sub_${sub.name.take(10)}")
                                            ) {
                                                Text(strings.addBillBtn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. "What-If" Savings Simulator Card (Phase 5: only when moreFeaturesEnabled is on)
        if (moreFeaturesEnabled) {
            item(key = "what_if_card") {
                ExpressiveGlassCard(
                    cornerRadius = 28.dp,
                    accentRimColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ExpressiveSectionHeader(
                        title = strings.whatIfTitle,
                        subtitle = strings.whatIfSub,
                        icon = Icons.Default.Tune,
                        accentColor = MaterialTheme.colorScheme.primary
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

            // 4. Seasonal & Yearly Planners (Phase 5: only when moreFeaturesEnabled is on)
            item(key = "seasonal_card") {
                val seasonalTemplates = remember { PayCycleEngine.moroccanSeasonalTemplates() }
                ExpressiveGlassCard(
                    cornerRadius = 28.dp,
                    accentRimColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("seasonal_envelopes_card")
                ) {
                    ExpressiveSectionHeader(
                        title = strings.seasonalTitle,
                        subtitle = strings.seasonalSub,
                        icon = Icons.Default.Event,
                        accentColor = MaterialTheme.colorScheme.primary,
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
                                accentBorderColor = MaterialTheme.colorScheme.primary,
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
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                modifier = Modifier.size(13.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "${fmt(item.secondaryAmount)} / ${fmt(item.amount)} $cur · ${item.dateOrCycle}",
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
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = "⏳ J-${countdown.daysRemaining} (${countdown.monthsRemaining}m)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (countdown.suggestedMonthlySaving > 0.0) {
                                                Text(
                                                    text = "+${fmt(countdown.suggestedMonthlySaving)} $cur/m",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = semanticIncomeColor(state.profile.isDarkTheme),
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
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    height = 8.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

/**
 * Phase 4 & Phase 5: Plan Tab (`BudgetAndDaretScreen`)
 * - Combines Envelopes, Accounts & Presets (moved off Home in Phase 2), Smart Reminders, Goals & Bills.
 * - Daret and Sellef only show when `moreFeaturesEnabled` is true (Phase 5).
 */
@Composable
fun BudgetAndDaretScreen(
    state: DashboardState,
    strings: AppStrings,
    moreFeaturesEnabled: Boolean = true,
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
    onQuickCreateBillTemplate: (String, Double, Int) -> Unit = { _, _, _ -> },
    onOpenAccountModal: (AccountItem?) -> Unit = {},
    onOpenTransferModal: () -> Unit = {},
    onQuickPresetTap: (FinancialItem) -> Unit = {},
    onAddPresetModal: (FinancialItem?) -> Unit = {},
    onDeletePreset: (String) -> Unit = {},
    onTriggerReminderAction: (SmartReminder) -> Unit = {}
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val hide = state.hideAmounts
    val haptics = LocalHapticFeedback.current
    val lang = state.profile.language
    var editPresetsMode by remember { mutableStateOf(false) }
    fun fmt(value: Double): String = PayCycleEngine.formatAmountOrMask(value, hide)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("budget_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 0A. Accounts & Balances Card (moved from Home in Phase 2)
        item(key = "plan_accounts") {
            ExpressiveGlassCard(
                cornerRadius = 28.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("plan_accounts_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.accountsTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${strings.totalNetWorth}: ${fmt(state.totalNetWorth)} $cur",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = onOpenTransferModal,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_transfer_accounts")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = strings.transferAction,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = strings.transferAction, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { onOpenAccountModal(null) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_add_account")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Account",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.accounts.forEach { accWithBal ->
                        val icon = when (accWithBal.account.kind) {
                            "bank" -> Icons.Default.AccountBalance
                            "wallet" -> Icons.Default.Smartphone
                            else -> Icons.Default.Payments
                        }
                        val accent = distinctAccountColor(
                            accWithBal.account.kind,
                            state.profile.isDarkTheme
                        )
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (state.profile.isDarkTheme) Color(0xFF14171E) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(
                                1.dp,
                                if (state.profile.isDarkTheme) Color(0xFF232834) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .width(140.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { onOpenAccountModal(accWithBal.account) }
                                .testTag("account_card_${accWithBal.account.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(accent.copy(alpha = 0.14f))
                                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Text(
                                    text = accWithBal.account.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = if (state.profile.isDarkTheme) VyxelPillDark else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${fmt(accWithBal.currentBalance)} $cur",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 0B. 1-Tap Quick Presets Card (moved from Home in Phase 2)
        item(key = "plan_presets") {
            ExpressiveGlassCard(
                cornerRadius = 28.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("plan_presets_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = strings.quickPresetsTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = if (editPresetsMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { editPresetsMode = !editPresetsMode }
                        ) {
                            Text(
                                text = if (editPresetsMode) strings.doneBtn else strings.editBtn,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (editPresetsMode) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onAddPresetModal(null) }
                        ) {
                            Text(
                                text = strings.addPresetBtn,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.presets.forEach { preset ->
                        ExpressiveGlassSurface(
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = {
                                if (editPresetsMode) {
                                    onAddPresetModal(preset)
                                } else {
                                    onQuickPresetTap(preset)
                                }
                            },
                            modifier = Modifier.testTag("preset_${preset.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (editPresetsMode) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit preset",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "-${fmt(preset.amount)} $cur",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = semanticExpenseColor(state.profile.isDarkTheme),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                if (editPresetsMode) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete preset",
                                        tint = AccentCoral,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onDeletePreset(preset.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. Category Donut Chart + Customizable Envelopes
        item(key = "plan_envelopes") {
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
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
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

                CategoryDonutChart(
                    envelopes = state.envelopes,
                    totalSpent = state.cycleExpenses,
                    lang = state.profile.language,
                    currencySymbol = cur,
                    hideAmounts = hide,
                    isDarkTheme = state.profile.isDarkTheme
                )

                Spacer(modifier = Modifier.height(16.dp))

                val isDark = state.profile.isDarkTheme
                val expColor = semanticExpenseColor(isDark)
                state.envelopes.forEach { envStatus ->
                    val envColor = distinctEnvelopeColor(
                        envStatus.config.key.ifBlank { envStatus.config.nameFr },
                        isDark
                    )
                    val activeAccent = if (envStatus.isOver90Percent) expColor else envColor
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(22.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(envColor)
                                    )
                                    Text(
                                        text = envStatus.config.localizedName(state.profile.language),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = envColor.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (envStatus.config.hasCap) {
                                            "${fmt(envStatus.totalAvailable)} $cur (${envStatus.config.percentage.safeRoundToInt()}%)"
                                        } else {
                                            strings.noCap
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = envColor,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (envStatus.rolloverAmount > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = strings.rolloverBadge.replace("{amt}", fmt(envStatus.rolloverAmount)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = semanticIncomeColor(isDark)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "-${fmt(envStatus.spent)} $cur",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (envStatus.spent > 0) expColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${envStatus.usagePercent}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = activeAccent
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            ExpressiveAnimatedProgressBar(
                                progress = (envStatus.usagePercent / 100f).safeFraction(),
                                color = activeAccent,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                height = 10.dp
                            )
                        }
                    }
                }
            }
        }

        // Smart Reminders Card (moved from Home in Phase 2)
        if (state.smartReminders.isNotEmpty()) {
            item(key = "plan_smart_reminders") {
                ExpressiveGlassCard(
                    cornerRadius = 28.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_reminders_card")
                ) {
                    ExpressiveSectionHeader(
                        title = when (lang) {
                            "darija" -> "تذكيرات الفواتير"
                            "ar" -> "تذكيرات الفواتير"
                            "en" -> "Bill Reminders"
                            else -> "Rappels & Échéances"
                        },
                        icon = Icons.Default.NotificationsActive,
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    state.smartReminders.take(5).forEach { rem ->
                        ExpressiveGlassSurface(
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rem.title,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = if (hide) "•••• $cur" else rem.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = { onTriggerReminderAction(rem) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(text = rem.actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Savings Goals & Recurring Bills Management
        item(key = "plan_goals_and_bills") {
            val billTemplates = remember { PayCycleEngine.moroccanBillTemplates() }
            ExpressiveGlassCard(
                cornerRadius = 28.dp,
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
                        Icon(imageVector = Icons.Default.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(text = strings.goalsTitle, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onOpenNewGoal(null) }) {
                            Text(strings.addGoalBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { onOpenNewBill(null) },
                            modifier = Modifier.testTag("btn_open_new_bill")
                        ) {
                            Text(strings.addBillBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = strings.moroccoTemplatesTitle,
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

                val isDark = state.profile.isDarkTheme
                val incColor = semanticIncomeColor(isDark)
                val expColor = semanticExpenseColor(isDark)

                // Recurring Bills List
                state.bills.forEach { bill ->
                    val isPaid = PayCycleEngine.isBillPaidInCycle(bill, state.activeWindow.cycleKey)
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(18.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                    text = "-${fmt(bill.amount)} $cur · J${bill.dayOrTurn}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 11.5.sp,
                                    color = if (isPaid) incColor else expColor
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isPaid) {
                                    FilledTonalButton(
                                        onClick = { onPayBill(bill) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(strings.payBillBtn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text(
                                        text = strings.paidBadgeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = incColor,
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
                                        text = "+${fmt(goal.secondaryAmount)} / ${fmt(goal.amount)} $cur (${(prog * 100f).safeRoundToInt()}%)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = incColor
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
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                height = 8.dp
                            )
                        }
                    }
                }
            }
        }

        // Phase 5: Daret & Sellef only appear when `moreFeaturesEnabled` is true
        if (moreFeaturesEnabled) {
            // 3. Moroccan Daret/Jam3iya Tracker Card
            item(key = "plan_daret") {
                ExpressiveGlassCard(
                    cornerRadius = 28.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("daret_tracker_card")
                ) {
                    ExpressiveSectionHeader(
                        title = strings.daretTitle,
                        subtitle = strings.daretSub,
                        icon = Icons.Default.Groups,
                        accentColor = MaterialTheme.colorScheme.primary,
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
                                            text = "-${fmt(daret.amount)} $cur/m · ${daret.extraInt}/$totalMembers · ${daret.dateOrCycle}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "👤 $whoseTurn",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                                tint = MaterialTheme.colorScheme.primary
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
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    height = 8.dp
                                )
                            }
                        }
                    }
                }
            }

            // 4. Sellef Tracker (Crédits & Dettes)
            item(key = "plan_sellef") {
                val isDark = state.profile.isDarkTheme
                val incColor = semanticIncomeColor(isDark)
                val expColor = semanticExpenseColor(isDark)
                ExpressiveGlassCard(
                    cornerRadius = 28.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sellef_tracker_card")
                ) {
                    ExpressiveSectionHeader(
                        title = strings.debtsTitle,
                        subtitle = strings.debtsLent + " / " + strings.debtsBorrowed,
                        icon = Icons.Default.Handshake,
                        accentColor = MaterialTheme.colorScheme.primary,
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(
                                    onClick = onOpenSplitBillModal,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("btn_open_split_bill")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                        contentDescription = strings.splitBillDesc,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.splitBillBtnLabel, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                        val amtSign = if (isLent) "+" else "-"
                        val amtColor = if (isLent) incColor else expColor
                        ExpressiveGlassSurface(
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "${strings.remainingLabel}: $amtSign${fmt(remDebt)} $cur / ${fmt(debt.amount)} $cur",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = amtColor
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
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        IconButton(onClick = { onDeleteItem(debt.id) }) {
                                            Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete")
                                        }
                                    }
                                }

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
                                    color = amtColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    height = 8.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
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
    hideAmounts: Boolean = false,
    isDarkTheme: Boolean = false
) {
    val envColors = envelopes.map { env ->
        distinctEnvelopeColor(env.config.key.ifBlank { env.config.nameFr }, isDark = isDarkTheme)
    }
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
                                    color = envColors[index].copy(alpha = (1f - (index * 0.12f)).coerceIn(0.35f, 1f)),
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
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
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
                val dotColor = envColors[index].copy(alpha = (1f - (index * 0.12f)).coerceIn(0.45f, 1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = env.config.localizedName(lang).take(20),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        text = "$sharePct%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
