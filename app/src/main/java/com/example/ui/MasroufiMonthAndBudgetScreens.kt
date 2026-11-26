package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.example.ui.theme.ExpressiveAccordionCard
import com.example.ui.theme.ExpressiveAnimatedProgressBar
import com.example.ui.theme.ExpressiveGlassCard
import com.example.ui.theme.ExpressiveGlassSurface
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveSectionHeader
import com.example.ui.theme.VyxelPillDark
import com.example.ui.theme.distinctAccountColor
import com.example.ui.theme.distinctEnvelopeColor
import com.example.ui.theme.semanticExpenseColor
import com.example.ui.theme.semanticIncomeColor
import com.masroufi.core.Money
import java.math.BigDecimal

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
    onQuickCreateSeasonalTemplate: (String, Money, String) -> Unit = { _, _, _ -> },
    onConvertSubscriptionToBill: (DetectedSubscription) -> Unit = {},
    onDismissDetectedSubscription: (DetectedSubscription) -> Unit = {},
    onShareText: (String) -> Unit = {}
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val hide = state.hideAmounts
    val haptics = LocalHapticFeedback.current
    val lang = state.profile.language
    fun fmt(value: Money): String = PayCycleEngine.formatAmountOrMask(value, hide)
    val pct = if (state.totalAvailableBudget.isPositive) {
        ((state.cycleExpenses.centimes.toDouble() / state.totalAvailableBudget.centimes.toDouble()) * 100.0).safeRoundToInt()
    } else 0
    val isOver = state.remainingBudget.isNegative

    val selectedEnv = state.envelopes.find { it.config.key == state.whatIfState.selectedEnvelopeKey }
        ?: state.envelopes.firstOrNull()
    val baseSpendForSim = maxOf(selectedEnv?.spent ?: Money.ZERO, selectedEnv?.baseAllocated ?: Money.fromDh(500))
    val monthlySaved = baseSpendForSim.multiplyRate(BigDecimal.valueOf(state.whatIfState.cutPercentage.toLong(), 2))
    val yearlySaved = monthlySaved * 12L
    val goalImpactText = PayCycleEngine.computeWhatIfGoalImpact(
        monthlySaved = monthlySaved,
        goals = state.goals,
        seasonals = state.seasonals,
        lang = lang,
        currency = cur
    )

    var headerDragAccumulator by remember { mutableFloatStateOf(0f) }
    var expandedMonthSection by rememberSaveable { mutableStateOf<String?>(null) }
    fun toggleMonthSection(key: String) {
        expandedMonthSection = if (expandedMonthSection == key) null else key
    }

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
                                text = PayCycleEngine.formatCycleRangeFriendly(
                                    state.activeWindow.startIso,
                                    state.activeWindow.endIso,
                                    lang
                                ),
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
                    text = "${if (isOver && !hide) "-" else "+"}${fmt(state.remainingBudget.abs())} $cur",
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
                        text = "${strings.endOfMonthProj}: ${fmt(state.spendProjection.projectedTotal)} $cur",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Advanced Analytics Accordion (Month-over-Month Comparison)
        if (state.previousCycleExpenses.isPositive) {
            item(key = "month_comparison_card") {
                val isDark = state.profile.isDarkTheme
                val incColor = semanticIncomeColor(isDark)
                val expColor = semanticExpenseColor(isDark)
                val projectedDelta = state.totalAvailableBudget - state.spendProjection.projectedTotal
                val momDiffPct = (((state.cycleExpenses - state.previousCycleExpenses).centimes.toDouble() / state.previousCycleExpenses.centimes.toDouble()) * 100.0).safeRoundToInt()
                val momBadge = "${if (momDiffPct > 0) "+" else ""}$momDiffPct%"
                ExpressiveAccordionCard(
                    title = when (state.profile.language) {
                        "darija" -> "مقارنة الشهر"
                        "ar" -> "مقارنة الأشهر"
                        "en" -> "Month Comparison"
                        else -> "Comparaison Mensuelle"
                    },
                    subtitle = if (!projectedDelta.isNegative) {
                        "Projection : +${fmt(projectedDelta)} $cur"
                    } else {
                        "Projection : -${fmt(projectedDelta.abs())} $cur"
                    },
                    summaryBadge = momBadge,
                    summaryBadgeColor = if (momDiffPct <= 0) incColor else expColor,
                    icon = Icons.Default.Analytics,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isExpanded = expandedMonthSection == "mom_comparison",
                    onToggle = { toggleMonthSection("mom_comparison") },
                    headerTestTag = "accordion_month_comparison",
                    modifier = Modifier.testTag("advanced_analytics_card")
                ) {
                    state.envelopes.forEach { env ->
                        val curSpent = env.spent
                        val prevSpent = env.previousSpent
                        val diff = curSpent - prevSpent
                        val badgeText = when {
                            !prevSpent.isPositive && !curSpent.isPositive -> "0 $cur"
                            !prevSpent.isPositive -> "${strings.newLabel} (-${fmt(curSpent)} $cur)"
                            !diff.isPositive -> "-${fmt(diff.abs())} $cur ${strings.vsLastMonthSuffix}"
                            else -> "+${fmt(diff)} $cur ${strings.vsLastMonthSuffix}"
                        }
                        val badgeColor = if (!diff.isPositive) incColor else expColor
                        ExpressiveGlassSurface(
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
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
                                        fontSize = 12.sp,
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
        }

        // 3. Smart Insights & Recurring Subscription Detector Accordion
        if (moreFeaturesEnabled && (state.smartInsights.isNotEmpty() || state.detectedSubscriptions.isNotEmpty())) {
            item(key = "insights_smart_assistant") {
                val totalSignals = state.smartInsights.size + state.detectedSubscriptions.size
                ExpressiveAccordionCard(
                    title = when (lang) {
                        "darija" -> "المستشار الذكي"
                        "ar" -> "المساعد الذكي"
                        "en" -> "Smart Insights"
                        else -> "Assistant Intelligent"
                    },
                    subtitle = state.smartInsights.firstOrNull()?.title ?: when (lang) {
                        "ar" -> "اشتراكات متكررة مكتشفة"
                        "darija" -> "اشتراكات كتعاود"
                        "en" -> "Recurring charges detected"
                        else -> "Abonnements détectés"
                    },
                    summaryBadge = "$totalSignals",
                    summaryBadgeColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.AutoAwesome,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isExpanded = expandedMonthSection == "smart_assistant",
                    onToggle = { toggleMonthSection("smart_assistant") },
                    headerTestTag = "accordion_month_insights",
                    modifier = Modifier.testTag("smart_assistant_card")
                ) {
                    state.smartInsights.forEach { insight ->
                        ExpressiveGlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
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
                                                    fontSize = 12.sp,
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
                                                Text(strings.addBillBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

        // 4. Monthly Review Accordion (Bilan du mois)
        item(key = "insights_monthly_review") {
            val vsLastMonthDiff = if (state.previousCycleExpenses.isPositive) {
                (((state.cycleExpenses - state.previousCycleExpenses).centimes.toDouble() / state.previousCycleExpenses.centimes.toDouble()) * 100.0).safeRoundToInt()
            } else 0
            val vsLastMonthText = if (state.previousCycleExpenses.isPositive) {
                val sign = if (vsLastMonthDiff > 0) "+" else ""
                "$sign$vsLastMonthDiff% ${strings.vsLastMonthSuffix}"
            } else {
                strings.firstTrackedCycleLabel
            }
            val topCatName = state.topEnvelopeStatus?.config?.localizedName(lang) ?: "—"
            val topCatPct = state.topEnvelopeStatus?.usagePercent ?: 0
            val biggestDayLabel = if (state.biggestSpendingDayIso.isNotBlank()) {
                "${PayCycleEngine.formatFriendlyDate(state.biggestSpendingDayIso, lang)} (${fmt(state.biggestSpendingDayAmount)} $cur)"
            } else "—"

            ExpressiveAccordionCard(
                title = strings.monthlyReviewTitle,
                subtitle = vsLastMonthText,
                summaryBadge = "$pct%",
                summaryBadgeColor = if (isOver) semanticExpenseColor(state.profile.isDarkTheme) else MaterialTheme.colorScheme.primary,
                icon = Icons.Default.Lightbulb,
                accentColor = MaterialTheme.colorScheme.primary,
                isExpanded = expandedMonthSection == "monthly_review",
                onToggle = { toggleMonthSection("monthly_review") },
                headerTestTag = "accordion_month_review",
                modifier = Modifier.testTag("monthly_recap_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (!state.remainingBudget.isNegative) {
                            "${strings.remainingLabel} ${fmt(state.remainingBudget)} $cur (${state.activeWindow.daysRemaining}j)"
                        } else {
                            "${strings.overrunLabel} ${fmt(-state.remainingBudget)} $cur"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
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
                        Text(strings.shareBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

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

        // 5. "What-If" Savings Simulator Accordion
        if (moreFeaturesEnabled) {
            item(key = "what_if_card") {
                ExpressiveAccordionCard(
                    title = strings.whatIfTitle,
                    subtitle = strings.whatIfSub,
                    summaryBadge = "+${fmt(yearlySaved)} $cur",
                    summaryBadgeColor = semanticIncomeColor(state.profile.isDarkTheme),
                    icon = Icons.Default.Tune,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isExpanded = expandedMonthSection == "what_if",
                    onToggle = { toggleMonthSection("what_if") },
                    headerTestTag = "accordion_month_what_if"
                ) {
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

            // 6. Seasonal & Yearly Planners Accordion
            item(key = "seasonal_card") {
                val seasonalTemplates = remember { PayCycleEngine.moroccanSeasonalTemplates() }
                ExpressiveAccordionCard(
                    title = strings.seasonalTitle,
                    subtitle = strings.seasonalSub,
                    summaryBadge = "${state.seasonals.size}",
                    summaryBadgeColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.Event,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isExpanded = expandedMonthSection == "seasonal",
                    onToggle = { toggleMonthSection("seasonal") },
                    headerTestTag = "accordion_month_seasonal",
                    modifier = Modifier.testTag("seasonal_envelopes_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = { onOpenNewSeasonal(null) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_add_seasonal")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.seasonalTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

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

                    state.seasonals.forEach { item ->
                        val prog = if (item.amount.isPositive) (item.secondaryAmount.centimes.toDouble() / item.amount.centimes.toDouble()).toFloat().safeFraction() else 0f
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
                                .padding(vertical = 3.dp)
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
                                            if (countdown.suggestedMonthlySaving.isPositive) {
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
                                            Text("+ 200 $cur", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    onCollectDaretPot: (FinancialItem, String) -> Unit = { _, _ -> },
    onOpenNewDaret: (FinancialItem?) -> Unit,
    onSettleDebt: (FinancialItem) -> Unit,
    onOpenNewDebt: (FinancialItem?) -> Unit,
    onContributeGoal: (FinancialItem) -> Unit,
    onOpenNewGoal: (FinancialItem?) -> Unit,
    onOpenNewBill: (FinancialItem?) -> Unit,
    onPayBill: (FinancialItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onPartialRepayDebt: (FinancialItem, Money) -> Unit = { _, _ -> },
    onSendWhatsAppReminder: (String) -> Unit = {},
    onOpenSplitBillModal: () -> Unit = {},
    onQuickCreateBillTemplate: (String, Money, Int) -> Unit = { _, _, _ -> },
    onOpenAccountModal: (AccountItem?) -> Unit = {},
    onOpenTransferModal: () -> Unit = {},
    onQuickPresetTap: (FinancialItem) -> Unit = {},
    onAddPresetModal: (FinancialItem?) -> Unit = {},
    onDeletePreset: (String) -> Unit = {},
    onTriggerReminderAction: (SmartReminder) -> Unit = {},
    savedCreditsCount: Int = 0,
    onOpenCreditSimulator: () -> Unit = {},
    activeAnnualBillsCount: Int = 0,
    onOpenAnnualBills: () -> Unit = {},
    onOpenZakatTracker: () -> Unit = {},
    onShareCard: (ShareCardPayload) -> Unit = {}
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val hide = state.hideAmounts
    val haptics = LocalHapticFeedback.current
    val lang = state.profile.language
    var editPresetsMode by remember { mutableStateOf(false) }
    var expandedPlanSection by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedEnvelopeKey by rememberSaveable { mutableStateOf<String?>(null) }
    fun togglePlanSection(key: String) {
        expandedPlanSection = if (expandedPlanSection == key) null else key
    }
    fun fmt(value: Money): String = PayCycleEngine.formatAmountOrMask(value, hide)
    val cycleUsagePct = if (state.totalAvailableBudget.isPositive) {
        ((state.cycleExpenses.centimes.toDouble() / state.totalAvailableBudget.centimes.toDouble()) * 100.0).safeRoundToInt()
    } else 0
    val isDark = state.profile.isDarkTheme
    val incColor = semanticIncomeColor(isDark)
    val expColor = semanticExpenseColor(isDark)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("budget_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 0. Always-Visible Compact Cycle & Liquidity Strip (Tesler's Law: scannable KPIs without clutter)
        item(key = "plan_summary_strip") {
            ExpressiveGlassCard(
                cornerRadius = 24.dp,
                accentRimColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("plan_summary_strip")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { togglePlanSection("accounts") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = strings.totalAccountsLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${fmt(state.totalNetWorth)} $cur",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { togglePlanSection("envelopes") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = strings.cycleBalanceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${fmt(state.remainingBudget)} $cur",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (!state.remainingBudget.isNegative) MaterialTheme.colorScheme.primary else expColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (state.cycleSavings.isPositive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${strings.cycleSavingsLabel}: +${fmt(state.cycleSavings)} $cur",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = incColor
                    )
                }
            }
        }

        // 1. Categories & Envelopes Accordion (with nested Tap-to-Expand per Category!)
        item(key = "plan_envelopes") {
            ExpressiveAccordionCard(
                title = strings.envelopesTitle,
                subtitle = strings.envelopesSub,
                summaryBadge = "${state.envelopes.size} • $cycleUsagePct%",
                summaryBadgeColor = if (cycleUsagePct >= 90) expColor else MaterialTheme.colorScheme.primary,
                icon = Icons.Default.PieChart,
                accentColor = MaterialTheme.colorScheme.primary,
                isExpanded = expandedPlanSection == "envelopes",
                onToggle = { togglePlanSection("envelopes") },
                headerTestTag = "accordion_plan_envelopes"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onOpenCustomizeEnvelopes,
                        modifier = Modifier.testTag("btn_customize_envelopes")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = strings.editEnvelopesBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                CategoryDonutChart(
                    envelopes = state.envelopes,
                    totalSpent = state.cycleExpenses,
                    lang = state.profile.language,
                    currencySymbol = cur,
                    hideAmounts = hide,
                    isDarkTheme = isDark
                )

                val daysLeft = state.activeWindow.daysRemaining.coerceAtLeast(1)
                state.envelopes.forEach { envStatus ->
                    val envLabel = envStatus.config.localizedName(state.profile.language)
                    val catVisual = com.example.ui.theme.categoryVisualStyle(
                        "${envStatus.config.key} $envLabel",
                        isDark
                    )
                    val envColor = catVisual.foreground
                    val activeAccent = if (envStatus.isOver90Percent) expColor else envColor
                    val isEnvOpen = expandedEnvelopeKey == envStatus.config.key
                    val envChevron by animateFloatAsState(
                        targetValue = if (isEnvOpen) 180f else 0f,
                        animationSpec = ExpressiveMotion.snappySpring(),
                        label = "env_chevron_${envStatus.config.key}"
                    )
                    val envRemaining = (envStatus.totalAvailable - envStatus.spent).coerceAtLeast(Money.ZERO)
                    val envDailyPace = envRemaining.divideBy(daysLeft.toLong())

                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        accentBorderColor = if (isEnvOpen) activeAccent else Color.Transparent,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            expandedEnvelopeKey = if (isEnvOpen) null else envStatus.config.key
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .testTag("envelope_accordion_${envStatus.config.key}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
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
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(catVisual.container),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = catVisual.icon,
                                            contentDescription = null,
                                            tint = envColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = envLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "-${fmt(envStatus.spent)} $cur",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (envStatus.spent.isPositive) expColor else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = activeAccent.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = if (envStatus.config.hasCap) "${envStatus.usagePercent}%" else strings.noCap,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = activeAccent,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = envLabel,
                                        tint = if (isEnvOpen) activeAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .rotate(envChevron)
                                    )
                                }
                            }

                            if (envStatus.config.hasCap) {
                                Spacer(modifier = Modifier.height(8.dp))
                                ExpressiveAnimatedProgressBar(
                                    progress = (envStatus.usagePercent / 100f).safeFraction(),
                                    color = activeAccent,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    height = 6.dp
                                )
                            }

                            AnimatedVisibility(
                                visible = isEnvOpen,
                                enter = expandVertically(animationSpec = ExpressiveMotion.snappySpring()) + fadeIn(),
                                exit = shrinkVertically(animationSpec = ExpressiveMotion.snappySpring()) + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                text = if (envStatus.config.hasCap) {
                                                    "${strings.budgetLabel}: ${fmt(envStatus.totalAvailable)} $cur (${envStatus.config.percentage.safeRoundToInt()}%)"
                                                } else {
                                                    strings.noCap
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (envStatus.config.hasCap) {
                                                Text(
                                                    text = "${strings.remainingLabel}: ${fmt(envRemaining)} $cur • ${fmt(envDailyPace)} $cur/j",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (envStatus.isOver90Percent) expColor else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (envStatus.rolloverAmount.isPositive) {
                                                Text(
                                                    text = strings.rolloverBadge.replace("{amt}", fmt(envStatus.rolloverAmount)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = incColor,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        FilledTonalButton(
                                            onClick = onOpenCustomizeEnvelopes,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("btn_edit_env_${envStatus.config.key}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = strings.editBtn,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(strings.editBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Accounts & Balances Accordion
        item(key = "plan_accounts") {
            ExpressiveAccordionCard(
                title = strings.accountsTitle,
                subtitle = "${state.accounts.size} • ${strings.transferAction}",
                summaryBadge = "${fmt(state.totalNetWorth)} $cur",
                summaryBadgeColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.AccountBalanceWallet,
                accentColor = MaterialTheme.colorScheme.primary,
                isExpanded = expandedPlanSection == "accounts",
                onToggle = { togglePlanSection("accounts") },
                headerTestTag = "accordion_plan_accounts",
                modifier = Modifier.testTag("plan_accounts_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenTransferModal,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_transfer_accounts")
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_add_account")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = strings.accountsTitle,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

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
                            isDark
                        )
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (isDark) Color(0xFF14171E) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isDark) Color(0xFF232834) else MaterialTheme.colorScheme.outlineVariant
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
                                    color = if (isDark) VyxelPillDark else MaterialTheme.colorScheme.surfaceContainerHighest,
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

        // 3. Moroccan Financial Tools Accordion (Credit Simulator, Annual Bills / Vignette, Zakat & Hawl)
        item(key = "plan_moroccan_tools") {
            val creditStrings = remember(lang) {
                com.masroufi.credit.domain.CreditStrings.forLang(lang)
            }
            val fiscalStrings = remember(lang) {
                com.masroufi.fiscal.domain.FiscalStrings.forLang(lang)
            }
            val activeToolsCount = savedCreditsCount + activeAnnualBillsCount
            ExpressiveAccordionCard(
                title = when (lang) {
                    "darija" -> "أدوات مالية د المغرب"
                    "ar" -> "أدوات مالية مغربية"
                    "en" -> "Morocco Finance Tools"
                    else -> "Outils Maroc"
                },
                subtitle = "BCP • Vignette • Zakat",
                summaryBadge = if (activeToolsCount > 0) "$activeToolsCount" else "3",
                summaryBadgeColor = AccentEmerald,
                icon = Icons.Default.Calculate,
                accentColor = MaterialTheme.colorScheme.primary,
                isExpanded = expandedPlanSection == "moroccan_tools",
                onToggle = { togglePlanSection("moroccan_tools") },
                headerTestTag = "accordion_plan_tools"
            ) {
                ExpressiveGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onOpenCreditSimulator() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tools_credit_simulator_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = creditStrings.toolCardTitle,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = creditStrings.toolCardTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (savedCreditsCount > 0) "$savedCreditsCount" else "BCP",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = creditStrings.toolCardSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = onOpenCreditSimulator,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("tools_credit_simulator_open_button")
                        ) {
                            Text(
                                text = creditStrings.toolCardCta,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                ExpressiveGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onOpenAnnualBills() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tools_annual_bills_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = fiscalStrings.annualBillsCardTitle,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = fiscalStrings.annualBillsCardTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (activeAnnualBillsCount > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = AccentEmerald.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "$activeAnnualBillsCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentEmerald,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = fiscalStrings.annualBillsCardSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = onOpenAnnualBills,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("tools_annual_bills_open_button")
                        ) {
                            Text(
                                text = fiscalStrings.annualBillsCardCta,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                ExpressiveGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onOpenZakatTracker() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tools_zakat_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AccentEmerald.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = fiscalStrings.zakatCardTitle,
                                tint = AccentEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = fiscalStrings.zakatCardTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = fiscalStrings.zakatCardSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = onOpenZakatTracker,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("tools_zakat_open_button")
                        ) {
                            Text(
                                text = fiscalStrings.zakatCardCta,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. Smart Reminders Accordion (shown when due/upcoming reminders exist)
        if (state.smartReminders.isNotEmpty()) {
            item(key = "plan_smart_reminders") {
                ExpressiveAccordionCard(
                    title = when (lang) {
                        "darija" -> "تذكيرات الفواتير"
                        "ar" -> "تذكيرات الفواتير"
                        "en" -> "Bill Reminders"
                        else -> "Rappels & Échéances"
                    },
                    subtitle = state.smartReminders.firstOrNull()?.title,
                    summaryBadge = "${state.smartReminders.size}",
                    summaryBadgeColor = AccentAmber,
                    icon = Icons.Default.NotificationsActive,
                    accentColor = AccentAmber,
                    isExpanded = expandedPlanSection == "reminders",
                    onToggle = { togglePlanSection("reminders") },
                    headerTestTag = "accordion_plan_reminders",
                    modifier = Modifier.testTag("smart_reminders_card")
                ) {
                    state.smartReminders.take(5).forEach { rem ->
                        ExpressiveGlassSurface(
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
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

        // 5. Savings Goals & Recurring Bills Accordion
        item(key = "plan_goals_and_bills") {
            val billTemplates = remember { PayCycleEngine.moroccanBillTemplates() }
            ExpressiveAccordionCard(
                title = "${strings.goalsTitle} & ${strings.billsTitle}",
                subtitle = "${state.goals.size} ${strings.goalsTitle} · ${state.bills.size} ${strings.billsTitle}",
                summaryBadge = "${state.goals.size + state.bills.size}",
                summaryBadgeColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.Savings,
                accentColor = MaterialTheme.colorScheme.primary,
                isExpanded = expandedPlanSection == "goals_bills",
                onToggle = { togglePlanSection("goals_bills") },
                headerTestTag = "accordion_plan_goals_bills",
                modifier = Modifier.testTag("goals_and_bills_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onOpenNewGoal(null) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(strings.addGoalBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = { onOpenNewBill(null) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_open_new_bill")
                    ) {
                        Text(strings.addBillBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = strings.moroccoTemplatesTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

                // Recurring Bills List
                state.bills.forEach { bill ->
                    val isPaid = PayCycleEngine.isBillPaidInCycle(bill, state.activeWindow.cycleKey)
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(18.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { onOpenNewBill(bill) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
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
                                    fontSize = 12.sp,
                                    color = if (isPaid) incColor else expColor
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isPaid) {
                                    FilledTonalButton(
                                        onClick = { onPayBill(bill) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(strings.payBillBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    val prog = if (goal.amount.isPositive) (goal.secondaryAmount.centimes.toDouble() / goal.amount.centimes.toDouble()).toFloat().safeFraction() else 0f
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { onOpenNewGoal(goal) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
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
                                    Text("+ 200 $cur", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

        // 6. 1-Tap Quick Presets Accordion
        item(key = "plan_presets") {
            ExpressiveAccordionCard(
                title = strings.quickPresetsTitle,
                subtitle = when (lang) {
                    "darija" -> "مصاريف سريعة بضغطة وحدة"
                    "ar" -> "مصاريف سريعة بضغطة واحدة"
                    "en" -> "1-tap frequent expenses"
                    else -> "Dépenses fréquentes en 1 tap"
                },
                summaryBadge = "${state.presets.size}",
                summaryBadgeColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.Bolt,
                accentColor = MaterialTheme.colorScheme.primary,
                isExpanded = expandedPlanSection == "presets",
                onToggle = { togglePlanSection("presets") },
                headerTestTag = "accordion_plan_presets",
                modifier = Modifier.testTag("plan_presets_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

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
                                        color = expColor,
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

        // 7 & 8. Moroccan Daret & Sellef Accordions (shown when `moreFeaturesEnabled` is true)
        if (moreFeaturesEnabled) {
            item(key = "plan_daret") {
                ExpressiveAccordionCard(
                    title = strings.daretTitle,
                    subtitle = strings.daretSub,
                    summaryBadge = "${state.darets.size}",
                    summaryBadgeColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.Groups,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isExpanded = expandedPlanSection == "daret",
                    onToggle = { togglePlanSection("daret") },
                    headerTestTag = "accordion_plan_daret",
                    modifier = Modifier.testTag("daret_tracker_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = { onOpenNewDaret(null) },
                            modifier = Modifier.testTag("btn_add_daret")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.daretTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    state.darets.forEach { daret ->
                        val totalMembers = daret.resolvedDaretMembers()
                        val totalPot = daret.amount * totalMembers.toLong()
                        val progress = (daret.extraInt.toFloat() / totalMembers.toFloat()).safeFraction()
                        val whoseTurn = daret.subtitle.ifBlank { "Membre #${(daret.extraInt + 1).coerceAtMost(totalMembers)}" }
                        val cleanPayoutDate = PayCycleEngine.daretPayoutDateOnly(daret)
                        val isPotCollected = PayCycleEngine.isDaretPotCollected(daret)
                        var selectedCollectAcc by remember(daret.id) { mutableStateOf("acc_bank") }
                        ExpressiveGlassSurface(
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = { onOpenNewDaret(daret) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
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
                                            text = "-${fmt(daret.amount)} $cur/m · ${daret.extraInt}/$totalMembers · $cleanPayoutDate",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 12.sp,
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
                                            Text(text = strings.daretPayRound, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        IconButton(
                                            onClick = {
                                                onShareCard(
                                                    ShareCardPayload.DaretCard(
                                                        groupName = daret.name,
                                                        turnNumber = daret.dayOrTurn,
                                                        totalMembers = totalMembers,
                                                        amountMad = daret.amount,
                                                        recipientName = whoseTurn,
                                                        payoutDate = cleanPayoutDate,
                                                        currencySymbol = cur
                                                    )
                                                )
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
                                Spacer(modifier = Modifier.height(10.dp))
                                if (isPotCollected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = AccentEmerald.copy(alpha = 0.18f),
                                        modifier = Modifier.testTag("daret_pot_collected_badge_${daret.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = AccentEmerald,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = strings.daretPotCollectedBadge,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = AccentEmerald,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilterChip(
                                                selected = selectedCollectAcc == "acc_bank",
                                                onClick = { selectedCollectAcc = "acc_bank" },
                                                label = { Text(strings.daretCollectAccountBank, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                                modifier = Modifier.testTag("daret_collect_acc_bank_${daret.id}")
                                            )
                                            FilterChip(
                                                selected = selectedCollectAcc == "acc_cash",
                                                onClick = { selectedCollectAcc = "acc_cash" },
                                                label = { Text(strings.daretCollectAccountCash, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                                modifier = Modifier.testTag("daret_collect_acc_cash_${daret.id}")
                                            )
                                        }
                                        val isMyTurnReached = daret.extraInt >= daret.dayOrTurn
                                        if (isMyTurnReached) {
                                            Button(
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onCollectDaretPot(daret, selectedCollectAcc)
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier.testTag("btn_collect_daret_pot_${daret.id}")
                                            ) {
                                                Text(
                                                    text = strings.daretCollectPotBtn,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        } else {
                                            FilledTonalButton(
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onCollectDaretPot(daret, selectedCollectAcc)
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier.testTag("btn_collect_daret_pot_${daret.id}")
                                            ) {
                                                Text(
                                                    text = strings.daretCollectPotBtn,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "plan_sellef") {
                ExpressiveAccordionCard(
                    title = strings.debtsTitle,
                    subtitle = "${strings.debtsLent} / ${strings.debtsBorrowed}",
                    summaryBadge = "${state.debts.size}",
                    summaryBadgeColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.Handshake,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isExpanded = expandedPlanSection == "sellef",
                    onToggle = { togglePlanSection("sellef") },
                    headerTestTag = "accordion_plan_sellef",
                    modifier = Modifier.testTag("sellef_tracker_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onOpenSplitBillModal,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_open_split_bill")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                contentDescription = strings.splitBillDesc,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.splitBillBtnLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { onOpenNewDebt(null) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_add_sellef")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }

                    state.debts.forEach { debt ->
                        val isLent = debt.dateOrCycle != "borrowed"
                        val remDebt = (debt.amount - debt.secondaryAmount).coerceAtLeast(Money.ZERO)
                        val prog = if (debt.amount.isPositive) (debt.secondaryAmount.centimes.toDouble() / debt.amount.centimes.toDouble()).toFloat().safeFraction() else 0f
                        val amtColor = if (isLent) incColor else expColor
                        ExpressiveGlassSurface(
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = { onOpenNewDebt(debt) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
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
                                        val debtStatusText = if (isLent) {
                                            strings.debtRemainingToReceiveFmt
                                                .replace("{rem}", "${fmt(remDebt)} $cur")
                                                .replace("{total}", "${fmt(debt.amount)} $cur")
                                        } else {
                                            strings.debtRemainingToPayFmt
                                                .replace("{rem}", "${fmt(remDebt)} $cur")
                                                .replace("{total}", "${fmt(debt.amount)} $cur")
                                        }
                                        Text(
                                            text = debtStatusText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = amtColor
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (remDebt.isPositive) {
                                            IconButton(
                                                onClick = {
                                                    onShareCard(
                                                        ShareCardPayload.SellefCard(
                                                            personName = debt.name,
                                                            totalBalanceMad = debt.amount,
                                                            remainingMad = remDebt,
                                                            isLent = isLent,
                                                            currencySymbol = cur
                                                        )
                                                    )
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

                                if (remDebt.isPositive) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf(50L, 100L, 200L).map { Money.fromDh(it) }.filter { it < remDebt }.forEach { stepAmt ->
                                            val stepDh = stepAmt.centimes / 100L
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onPartialRepayDebt(debt, stepAmt)
                                                    }
                                                    .testTag("btn_sellef_partial_${debt.id}_$stepDh")
                                            ) {
                                                Text(
                                                    text = "+$stepDh $cur",
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
    totalSpent: Money,
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
                if (!totalSpent.isPositive) {
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
                        if (env.spent.isPositive) {
                            val sweep = ((env.spent.centimes.toDouble() / totalSpent.centimes.toDouble()) * 360.0).toFloat()
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
                val sharePct = if (totalSpent.isPositive) ((env.spent.centimes.toDouble() / totalSpent.centimes.toDouble()) * 100.0).safeRoundToInt() else 0
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
