package com.example.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.AppStrings
import com.example.domain.PayCycleEngine
import com.example.domain.ProjectionAlertLevel
import com.example.ui.DashboardState
import com.example.ui.safeFraction
import com.example.ui.safeRoundToInt
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.ExpressiveAnimatedProgressBar
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.MasroufiTokens
import com.example.ui.theme.heroGradientColors
import com.example.ui.theme.paletteForKey
import com.masroufi.core.Money

@Composable
fun DashboardHeroCard(
    state: DashboardState,
    strings: AppStrings,
    onToggleHideAmounts: () -> Unit
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val hide = state.hideAmounts
    val haptics = LocalHapticFeedback.current
    val lang = state.profile.language
    val pct = if (state.totalAvailableBudget.isPositive) {
        ((state.cycleExpenses.centimes.toDouble() / state.totalAvailableBudget.centimes.toDouble()) * 100.0).safeRoundToInt()
    } else 0
    val isOverBudget = state.remainingBudget.isNegative
    var heroExpanded by remember { mutableStateOf(false) }

    fun fmt(value: Money): String = PayCycleEngine.formatAmountOrMask(value, hide)

    val heroColors = heroGradientColors(state.customPrefs.accentPalette, isOverBudget)
    val heroAccent = paletteForKey(state.customPrefs.accentPalette).primaryDark
    val heroShape = MasroufiTokens.Shapes.heroCard

    // Budget remaining ratio for the arc gauge (1.0 = full budget remaining, 0.0 = drained)
    val remainingFraction = if (state.totalAvailableBudget.isPositive) {
        ((state.totalAvailableBudget - state.cycleExpenses).centimes.toDouble() / state.totalAvailableBudget.centimes.toDouble()).toFloat().safeFraction(0f, 1f)
    } else if (state.safeToSpendToday.isPositive) 0.75f else 0f

    val animatedGaugeFraction by animateFloatAsState(
        targetValue = if (isOverBudget) 1f else remainingFraction.coerceAtLeast(0.06f),
        animationSpec = ExpressiveMotion.fluidSpring(),
        label = "hero_arc_gauge_fraction"
    )

    // Arc gauge mood shifts calm -> amber -> coral as the budget drains
    val gaugeMoodColor = when {
        isOverBudget || state.spendProjection.alertLevel == ProjectionAlertLevel.RED -> AccentCoral
        pct >= 75 || state.spendProjection.alertLevel == ProjectionAlertLevel.AMBER -> AccentAmber
        else -> AccentEmerald
    }

    val tapHeroHint = when (lang) {
        "ar" -> "اضغط للتفاصيل"
        "darija" -> "ورك للتفاصيل"
        "en" -> "Tap for details"
        else -> "Toucher pour détails"
    }

    val plainForecastSentence = when {
        state.monthEndForecastMessage.isNotBlank() -> state.monthEndForecastMessage
        state.daysRemaining > 0 -> when (lang) {
            "ar" -> "باقي ${state.daysRemaining} يوم في الدورة · الميزانية الصافية ${fmt(state.safePool)} $cur"
            "darija" -> "باقي ${state.daysRemaining} يوم فالدورة · الصافي ${fmt(state.safePool)} $cur"
            "en" -> "${state.daysRemaining}d left in cycle · ${fmt(state.safePool)} $cur safe pool"
            else -> "${state.daysRemaining}j restants · ${fmt(state.safePool)} $cur disponibles sur le cycle"
        }
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(heroShape)
            .background(Brush.linearGradient(colors = heroColors))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.16f),
                shape = heroShape
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                heroExpanded = !heroExpanded
            }
            .padding(MasroufiTokens.Spacing.xl)
            .testTag("hero_card_container")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top bar: Title + Chevron only (no "Tap for details" text clutter, contentDescription preserved)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MasroufiTokens.Spacing.sm),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = MasroufiTokens.Spacing.sm)
                ) {
                    if (isOverBudget) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = strings.overrunLabel,
                            tint = AccentCoral,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = strings.safeToSpendTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.96f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .testTag("hero_chevron_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (heroExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = tapHeroHint,
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(MasroufiTokens.Spacing.md))

            // Main Hero Row: Rolling-digit daily allowance + Mood Arc Gauge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(MasroufiTokens.Spacing.sm),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hero_safe_to_spend_today_badge")
                ) {
                    val formattedSafeToday = fmt(state.safeToSpendToday)
                    AnimatedContent(
                        targetState = formattedSafeToday,
                        transitionSpec = {
                            (slideInVertically { height -> height / 2 } + fadeIn()).togetherWith(
                                slideOutVertically { height -> -height / 2 } + fadeOut()
                            )
                        },
                        label = "hero_rolling_digit_amount"
                    ) { targetAmount ->
                        Text(
                            text = targetAmount,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = strings.dhPerDay.replace("DH", cur),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.90f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Signature Arc Gauge shifting calm -> amber -> coral
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .testTag("hero_arc_gauge"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(60.dp)) {
                        val strokePx = 6.5.dp.toPx()
                        val arcDiameter = size.minDimension - strokePx
                        val topLeft = Offset(strokePx / 2f, strokePx / 2f)
                        val arcSize = Size(arcDiameter, arcDiameter)
                        // Track arc (240 degrees)
                        drawArc(
                            color = Color.White.copy(alpha = 0.20f),
                            startAngle = 150f,
                            sweepAngle = 240f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                        // Mood value arc
                        drawArc(
                            color = gaugeMoodColor,
                            startAngle = 150f,
                            sweepAngle = 240f * animatedGaugeFraction,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = if (isOverBudget) "!" else "${(100 - pct).coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            if (isOverBudget) {
                Spacer(modifier = Modifier.height(MasroufiTokens.Spacing.xs))
                Text(
                    text = "⚠️ ${strings.overrunLabel} ${fmt(-state.remainingBudget)} $cur",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else if (state.unpaidBillsTotal.isPositive) {
                Spacer(modifier = Modifier.height(MasroufiTokens.Spacing.xs))
                Text(
                    text = strings.safeToSpendSub.replace("{bills}", fmt(state.unpaidBillsTotal)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }

            if (plainForecastSentence.isNotBlank()) {
                Spacer(modifier = Modifier.height(MasroufiTokens.Spacing.xs))
                Text(
                    text = plainForecastSentence,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.90f),
                    fontWeight = FontWeight.Medium
                )
            }

            // Clarify daily allowance vs total cash available in accounts when accounts have balance
            if (state.accounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(MasroufiTokens.Spacing.sm))
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.24f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier.testTag("hero_accounts_cash_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.90f),
                            modifier = Modifier.size(14.dp)
                        )
                        val cashVsBudgetLabel = when (lang) {
                            "ar" -> "السيولة في الحسابات: ${fmt(state.netWorthSnapshot.totalLiquidAssets)} $cur"
                            "darija" -> "الفلوس فالحسابات: ${fmt(state.netWorthSnapshot.totalLiquidAssets)} $cur"
                            "en" -> "Cash in accounts: ${fmt(state.netWorthSnapshot.totalLiquidAssets)} $cur"
                            else -> "Solde des comptes : ${fmt(state.netWorthSnapshot.totalLiquidAssets)} $cur"
                        }
                        Text(
                            text = cashVsBudgetLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.92f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                val nwSnap = state.netWorthSnapshot
                if (nwSnap.totalLiabilities.isPositive || nwSnap.totalReceivables.isPositive) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        modifier = Modifier.testTag("hero_net_worth_pill")
                    ) {
                        val netWorthLabel = when (lang) {
                            "ar" -> "صافي الثروة: ${fmt(nwSnap.netWorth)} $cur"
                            "darija" -> "صافي الرصيد: ${fmt(nwSnap.netWorth)} $cur"
                            "en" -> "Net worth: ${fmt(nwSnap.netWorth)} $cur"
                            else -> "Patrimoine net : ${fmt(nwSnap.netWorth)} $cur"
                        }
                        Text(
                            text = netWorthLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.88f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Expandable "Why this number" breakdown panel using precomputed values
            AnimatedVisibility(visible = heroExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .testTag("hero_why_this_number_panel"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.28f),
                            border = BorderStroke(1.dp, heroAccent.copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("hero_no_spend_streak_badge")
                        ) {
                            Text(
                                text = when (lang) {
                                    "ar" -> "🔥 ${state.noSpendStreakDays} يوم بدون صرف"
                                    "darija" -> "🔥 ${state.noSpendStreakDays} يوم بلا مصروف"
                                    "en" -> "🔥 ${state.noSpendStreakDays}d streak"
                                    else -> "🔥 ${state.noSpendStreakDays}j sans dépense"
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.28f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleHideAmounts()
                                }
                                .testTag("hero_privacy_eye_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (hide) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = strings.privacyLabel,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = strings.privacyLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // "Why this number" breakdown card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.24f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = when (lang) {
                                    "ar" -> "لماذا هذا المبلغ اليومي؟"
                                    "darija" -> "كيفاش تحسب هاد المبلغ اليومي؟"
                                    "en" -> "Why this daily number?"
                                    else -> "Pourquoi ce montant par jour ?"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                            HeroBreakdownRow(
                                label = when (lang) {
                                    "ar" -> "الميزانية المتبقية"
                                    "darija" -> "الميزانية اللي بقات"
                                    "en" -> "Remaining budget"
                                    else -> "Budget restant"
                                },
                                value = "${fmt(state.remainingBudget)} $cur"
                            )
                            HeroBreakdownRow(
                                label = when (lang) {
                                    "ar" -> "الفواتير الملتزم بها"
                                    "darija" -> "الفواتير اللي مازال"
                                    "en" -> "Committed bills"
                                    else -> "Factures engagées"
                                },
                                value = "- ${fmt(state.unpaidBillsTotal)} $cur"
                            )
                            HeroBreakdownRow(
                                label = when (lang) {
                                    "ar" -> "الرصيد الصافي ÷ الأيام المتبقية (${state.daysRemaining})"
                                    "darija" -> "الصافي ÷ الأيام اللي بقات (${state.daysRemaining})"
                                    "en" -> "Safe pool ÷ Days left (${state.daysRemaining})"
                                    else -> "Marge nette ÷ Jours restants (${state.daysRemaining})"
                                },
                                value = "${fmt(state.safePool)} $cur ÷ ${state.daysRemaining.coerceAtLeast(1)}j"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${strings.thisMonth}: ${fmt(state.cycleExpenses)} $cur",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("hero_cycle_expenses_text")
                        )
                        Text(
                            text = "${strings.budgetLabel}: ${fmt(state.totalAvailableBudget)} $cur ($pct%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.90f)
                        )
                    }

                    ExpressiveAnimatedProgressBar(
                        progress = (pct / 100f).safeFraction(),
                        color = if (isOverBudget) AccentCoral else heroAccent,
                        trackColor = Color.Black.copy(alpha = 0.28f),
                        height = 8.dp
                    )

                    if (state.spendProjection.showEstimateAlert && state.monthEndForecastMessage.isNotBlank()) {
                        val isRedAlert = state.spendProjection.alertLevel == ProjectionAlertLevel.RED
                        val alertColor = if (isRedAlert) AccentCoral else AccentAmber
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = alertColor.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, alertColor.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hero_month_end_forecast_text")
                        ) {
                            Text(
                                text = "${strings.estimateLabel} · ${state.monthEndForecastMessage}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBreakdownRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.88f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
