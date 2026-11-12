package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.ExpressiveAnimatedProgressBar
import com.example.ui.theme.VyxelPeriwinkle
import com.example.ui.theme.heroGradientColors

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
    val pct = if (state.totalAvailableBudget > 0) {
        ((state.cycleExpenses / state.totalAvailableBudget) * 100.0).safeRoundToInt()
    } else 0
    val isOverBudget = state.remainingBudget < 0
    var heroExpanded by remember { mutableStateOf(false) }

    fun fmt(value: Double): String = PayCycleEngine.formatAmountOrMask(value, hide)

    val heroColors = heroGradientColors(state.customPrefs.accentPalette, isOverBudget)
    val heroShape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(heroShape)
            .background(Brush.linearGradient(colors = heroColors))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = heroShape
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                heroExpanded = !heroExpanded
            }
            .padding(22.dp)
            .testTag("hero_card_container")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tapHeroHint = when (lang) {
                        "ar" -> "اضغط للتفاصيل"
                        "darija" -> "ورك للتفاصيل"
                        "en" -> "Tap for details"
                        else -> "Toucher pour détails"
                    }
                    Text(
                        text = tapHeroHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = VyxelPeriwinkle,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (heroExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = tapHeroHint,
                        tint = VyxelPeriwinkle,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("hero_safe_to_spend_today_badge")
            ) {
                Text(
                    text = fmt(state.safeToSpendToday),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = strings.dhPerDay.replace("DH", cur),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            if (isOverBudget) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠️ ${strings.overrunLabel} ${fmt(-state.remainingBudget)} $cur",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else if (state.unpaidBillsTotal > 0.0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.safeToSpendSub.replace("{bills}", fmt(state.unpaidBillsTotal)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }

            AnimatedVisibility(visible = heroExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
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
                            border = BorderStroke(1.dp, VyxelPeriwinkle.copy(alpha = 0.35f)),
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
                                color = VyxelPeriwinkle,
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
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    ExpressiveAnimatedProgressBar(
                        progress = (pct / 100f).safeFraction(),
                        color = if (isOverBudget) AccentCoral else VyxelPeriwinkle,
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
