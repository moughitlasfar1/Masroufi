package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccountItem
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import com.example.domain.AppStrings
import com.example.domain.DetectedSubscription
import com.example.domain.PayCycleEngine
import com.example.domain.SmartReminder
import com.example.ui.dashboard.DashboardHeroCard
import com.example.ui.dashboard.DashboardRecentTransactions
import com.example.ui.theme.ExpressiveGlassCard
import com.example.ui.theme.ExpressiveGlassSurface
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveSectionHeader
import com.example.ui.theme.VyxelOnPeriwinkle
import com.example.ui.theme.VyxelPeriwinkle
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

internal fun Double.safeRoundToInt(): Int {
    if (this.isNaN() || this.isInfinite()) return 0
    return this.roundToInt()
}

internal fun Float.safeRoundToInt(): Int {
    if (this.isNaN() || this.isInfinite()) return 0
    return this.roundToInt()
}

internal fun Float.safeFraction(minVal: Float = 0f, maxVal: Float = 1f): Float {
    if (this.isNaN() || this.isInfinite()) return minVal
    return this.coerceIn(minVal, maxVal)
}

internal fun dashboardSectionLabel(sectionKey: String, lang: String): String = when (sectionKey) {
    "accounts" -> when (lang) {
        "ar" -> "الحسابات"
        "darija" -> "الحسابات"
        "en" -> "Accounts"
        else -> "Comptes"
    }
    "presets" -> when (lang) {
        "ar" -> "إضافة 1-Tap"
        "darija" -> "1-Tap"
        "en" -> "1-Tap Presets"
        else -> "Achats 1-Tap"
    }
    "insights" -> when (lang) {
        "ar" -> "المساعد الذكي"
        "darija" -> "المستشار الذكي"
        "en" -> "Smart Insights"
        else -> "Assistant IA"
    }
    "bills" -> when (lang) {
        "ar" -> "الفواتير"
        "darija" -> "الفواتير"
        "en" -> "Bills"
        else -> "Factures"
    }
    "week_summary" -> when (lang) {
        "ar" -> "الأسبوع"
        "darija" -> "السيمانة"
        "en" -> "Week Summary"
        else -> "Résumé Semaine"
    }
    "monthly_review" -> when (lang) {
        "ar" -> "الشهر"
        "darija" -> "الشهر"
        "en" -> "Monthly Review"
        else -> "Bilan Mois"
    }
    else -> sectionKey
}

/**
 * Phase 3: Lean Home Screen
 * - First-time setup card shows only for new users (`state.isCleanFreshAccount`).
 * - `DashboardHeroCard` shows "Safe to spend today" with one supporting line and tap-to-expand details.
 * - `DashboardRecentTransactions` shows up to 3 recent transactions or a real empty state.
 */
@Composable
fun OverviewScreen(
    state: DashboardState,
    strings: AppStrings,
    onOpenAddModal: (String) -> Unit,
    onQuickPresetTap: (FinancialItem) -> Unit = {},
    onOpenAccountModal: (AccountItem?) -> Unit = {},
    onPayBill: (FinancialItem) -> Unit = {},
    onAddPresetModal: (FinancialItem?) -> Unit = {},
    onDeletePreset: (String) -> Unit = {},
    onOpenCustomizationModal: () -> Unit = {},
    onOpenQuickOnboarding: () -> Unit = {},
    onLoadDemoData: () -> Unit = {},
    onTriggerReminderAction: (SmartReminder) -> Unit = {},
    onToggleHideAmounts: () -> Unit = {},
    onOpenQuickPresetPicker: () -> Unit = {},
    onMoveDashboardSection: (String, Int) -> Unit = { _, _ -> },
    onSetDashboardSectionVisibility: (String, Boolean) -> Unit = { _, _ -> },
    onConvertSubscriptionToBill: (DetectedSubscription) -> Unit = {},
    onDismissDetectedSubscription: (DetectedSubscription) -> Unit = {},
    onShareText: (String) -> Unit = {},
    onEditTransaction: (TransactionItem) -> Unit = {},
    onSeeAllTransactions: () -> Unit = {}
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val lang = state.profile.language

    val recentThree = remember(state.cycleTransactions) {
        val todayIso = PayCycleEngine.todayIso()
        state.cycleTransactions.filter { it.isoDate <= todayIso }.take(3)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("overview_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (state.isCleanFreshAccount) {
            item(key = "clean_onboarding") {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clean_onboarding_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = when (lang) {
                                    "ar" -> "مرحباً بك في مصروفي — حساب جاهز (0 $cur)"
                                    "darija" -> "مرحبا بيك فـ مصروفي — حساب واجد (0 $cur)"
                                    "en" -> "Welcome to Masroufi — Ready (0 $cur)"
                                    else -> "Bienvenue sur Masroufi — Compte Prêt (0 $cur)"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (lang) {
                                "ar" -> "اضبط راتبك ويوم الراتب و3 أظرفة في 30 ثانية."
                                "darija" -> "قاد الخلصة ونهار الخلاص و3 أظرفة فـ 30 ثانية."
                                "en" -> "Set up your salary, payday and 3 envelopes in 30 seconds."
                                else -> "Configurez votre salaire, jour de paie et 3 enveloppes en 30s."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenQuickOnboarding,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_open_30s_onboarding")
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (lang) {
                                        "ar" -> "إعداد 30 ثانية"
                                        "darija" -> "إعداد 30 ثانية"
                                        "en" -> "30s Setup"
                                        else -> "Config 30s"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    val firstAcc = state.accounts.firstOrNull()?.account
                                    onOpenAccountModal(firstAcc)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(strings.enterBalanceBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item(key = "hero_card") {
            DashboardHeroCard(
                state = state,
                strings = strings,
                onToggleHideAmounts = onToggleHideAmounts
            )
        }

        item(key = "home_recent_transactions") {
            DashboardRecentTransactions(
                state = state,
                strings = strings,
                recentTransactions = recentThree,
                onEditTransaction = onEditTransaction,
                onSeeAllTransactions = onSeeAllTransactions,
                onAddFirstTransaction = { onOpenAddModal("expense") }
            )
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

/**
 * Phase 4: Insights Tab combining Week and Month views with an inline segmented toggle.
 */
@Composable
fun InsightsScreen(
    state: DashboardState,
    strings: AppStrings,
    selectedSubTab: String,
    onSelectSubTab: (String) -> Unit,
    moreFeaturesEnabled: Boolean,
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
    val isDark = state.profile.isDarkTheme
    val activeBg = if (isDark) VyxelPeriwinkle else MaterialTheme.colorScheme.primary
    val activeFg = if (isDark) VyxelOnPeriwinkle else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("insights_screen")
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("insights_subtab_toggle_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "week" to strings.weekToggleLabel,
                    "month" to strings.monthToggleLabel
                ).forEach { (key, label) ->
                    val selected = selectedSubTab == key
                    val itemFg = if (selected) activeFg else MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                        shape = CircleShape,
                        color = if (selected) activeBg else Color.Transparent,
                        contentColor = itemFg,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .clickable { onSelectSubTab(key) }
                            .testTag("insights_${key}_toggle")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = itemFg
                            )
                        }
                    }
                }
            }
        }

        if (selectedSubTab == "month") {
            MonthScreen(
                state = state,
                strings = strings,
                moreFeaturesEnabled = moreFeaturesEnabled,
                onUpdateWhatIf = onUpdateWhatIf,
                onContributeSeasonal = onContributeSeasonal,
                onOpenNewSeasonal = onOpenNewSeasonal,
                onDeleteItem = onDeleteItem,
                onShiftCycle = onShiftCycle,
                onQuickCreateSeasonalTemplate = onQuickCreateSeasonalTemplate,
                onConvertSubscriptionToBill = onConvertSubscriptionToBill,
                onDismissDetectedSubscription = onDismissDetectedSubscription,
                onShareText = onShareText
            )
        } else {
            WeekScreen(
                state = state,
                strings = strings
            )
        }
    }
}

@Composable
fun WeekScreen(
    state: DashboardState,
    strings: AppStrings
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val hide = state.hideAmounts
    fun fmt(value: Double): String = PayCycleEngine.formatAmountOrMask(value, hide)
    val rawSpentMax = state.weekDaysSpend.maxOrNull() ?: 0.0
    val rawFutureMax = state.weekDaysFutureSpend.maxOrNull() ?: 0.0
    val rawMax = max(100.0, max(rawSpentMax, rawFutureMax))
    val maxVal = if (rawMax.isNaN() || rawMax.isInfinite()) 100.0 else max(100.0, rawMax)
    val step = listOf(50, 100, 200, 250, 500, 1000, 2000, 5000).firstOrNull { it * 4 >= maxVal }
        ?: (ceil(maxVal / 4000.0).toInt() * 1000).coerceAtLeast(100)
    val ceilVal = (step * 4).toDouble().coerceAtLeast(100.0)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("week_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ExpressiveGlassCard(
                cornerRadius = 28.dp,
                accentRimColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                ExpressiveSectionHeader(
                    title = strings.weeklyAnalysis,
                    subtitle = strings.weeklySub,
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    accentColor = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f))
                ) {
                    Text(
                        text = "${strings.currentWeekBadge} · ${state.weekRangeLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExpressiveGlassCard(
                    cornerRadius = 26.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = strings.thisWeek,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${fmt(state.weekTotalSpend)} $cur",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${strings.prevWeek}: ${fmt(state.prevWeekTotalSpend)} $cur",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ExpressiveGlassCard(
                    cornerRadius = 26.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = strings.dailyAvg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${fmt(state.dailyAverageSpend)} $cur",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val dayName = if (state.mostExpensiveDayIndex in 0..6) {
                        strings.daysFull[state.mostExpensiveDayIndex]
                    } else "—"
                    Text(
                        text = "${strings.mostSpentDay}: $dayName",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            ExpressiveGlassCard(
                cornerRadius = 28.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = strings.chartTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                ExpressiveGlassSurface(
                    shape = RoundedCornerShape(22.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val chartHeight = size.height - 28.dp.toPx()
                            for (i in 1..4) {
                                val y = chartHeight - (i / 4f) * (chartHeight - 16.dp.toPx())
                                drawLine(
                                    color = lineColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            state.weekDaysSpend.forEachIndexed { idx, value ->
                                val futureVal = state.weekDaysFutureSpend.getOrElse(idx) { 0.0 }
                                val isFutureOutlined = value <= 0.0 && futureVal > 0.0
                                val displayVal = if (isFutureOutlined) futureVal else value
                                val rawRatio = (displayVal / ceilVal).toFloat().safeFraction(0.05f, 1f)
                                val animatedRatio by animateFloatAsState(
                                    targetValue = rawRatio,
                                    animationSpec = ExpressiveMotion.fluidSpring(),
                                    label = "week_bar_$idx"
                                )
                                val isPeak = idx == state.mostExpensiveDayIndex && value > 0
                                val barShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    if (displayVal > 0) {
                                        Text(
                                            text = if (hide) "••" else displayVal.safeRoundToInt().toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(28.dp)
                                            .fillMaxHeight(animatedRatio * 0.72f)
                                            .clip(barShape)
                                            .then(
                                                if (isFutureOutlined) {
                                                    Modifier
                                                        .border(1.75.dp, MaterialTheme.colorScheme.primary, barShape)
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                                        .testTag("week_future_outlined_bar_$idx")
                                                } else {
                                                    Modifier.background(
                                                        Brush.verticalGradient(
                                                            colors = when {
                                                                value <= 0 -> listOf(
                                                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                                                )
                                                                else -> listOf(
                                                                    MaterialTheme.colorScheme.primary,
                                                                    MaterialTheme.colorScheme.primaryContainer
                                                                )
                                                            }
                                                        )
                                                    )
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = strings.daysShort.getOrElse(idx) { "" },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        fontWeight = if (isPeak) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isPeak) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}
