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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.theme.CardTier
import com.example.ui.theme.ExpressiveAccordionCard
import com.example.ui.theme.ExpressiveAnimatedProgressBar
import com.example.ui.theme.ExpressiveGlassCard
import com.example.ui.theme.ExpressiveGlassSurface
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveSectionHeader
import com.example.ui.theme.MasroufiTokens
import com.example.ui.theme.categoryVisualStyle
import com.example.ui.theme.semanticExpenseColor
import com.example.ui.theme.semanticIncomeColor
import com.example.ui.theme.semanticWarningColor
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
 * Computes the single contextual line under the Hero card (Prompt 5):
 * - No data: friendly first action
 * - Overspending: highlights the category that needs attention
 * - Start of cycle: available budget
 * - End of cycle: remaining and forecast
 * - Mid cycle: spending pace
 */
internal fun computeHeroContextualStatusLine(
    state: DashboardState,
    cur: String
): String {
    val lang = state.profile.language
    val hide = state.hideAmounts
    fun fmt(v: Double): String = PayCycleEngine.formatAmountOrMask(v, hide)

    if (state.cycleTransactions.isEmpty()) {
        return when (lang) {
            "ar" -> "ابدأ بتسجيل أول مصروف اليوم بالزر + أو بالصوت 🎤"
            "darija" -> "بدا بتقييد أول مصروف اليوم بالزر + ولا بالصوت 🎤"
            "en" -> "Log your first expense today with + or hold the mic 🎤"
            else -> "Ajoutez votre première opération avec + ou maintenez le micro 🎤"
        }
    }

    val overEnvelope = state.envelopes.firstOrNull { it.usagePercent > 100 }
        ?: state.envelopes.maxByOrNull { it.usagePercent }?.takeIf { it.isOver90Percent || it.usagePercent >= 90 }
    if (state.remainingBudget < 0 || overEnvelope != null) {
        val catName = overEnvelope?.config?.localizedName(lang) ?: when (lang) {
            "ar" -> "الميزانية"
            "darija" -> "الميزانية"
            "en" -> "Budget"
            else -> "Budget"
        }
        return when (lang) {
            "ar" -> "تنبيه: فئة «$catName» تحتاج انتباهاً في هذه الدورة"
            "darija" -> "رد البال: فئة «$catName» قربات ولا فاتت السقف"
            "en" -> "Heads up: \"$catName\" needs attention this cycle"
            else -> "Attention : la catégorie « $catName » demande votre vigilance"
        }
    }

    val daysElapsed = (state.activeWindow.totalDays - state.daysRemaining).coerceAtLeast(0)
    if (daysElapsed <= 3) {
        return when (lang) {
            "ar" -> "بداية الدورة · الميزانية المتاحة ${fmt(state.totalAvailableBudget)} $cur"
            "darija" -> "بداية الدورة · الميزانية المتوفرة ${fmt(state.totalAvailableBudget)} $cur"
            "en" -> "Start of cycle · ${fmt(state.totalAvailableBudget)} $cur available budget"
            else -> "Début de cycle · ${fmt(state.totalAvailableBudget)} $cur de budget disponible"
        }
    }

    if (state.daysRemaining <= 5) {
        return when (lang) {
            "ar" -> "نهاية الدورة (${state.daysRemaining} أيام) · المتبقي ${fmt(state.remainingBudget)} $cur"
            "darija" -> "قربات تسالي الدورة (${state.daysRemaining} أيام) · الباقي ${fmt(state.remainingBudget)} $cur"
            "en" -> "End of cycle (${state.daysRemaining}d left) · ${fmt(state.remainingBudget)} $cur remaining"
            else -> "Fin de cycle (${state.daysRemaining}j restants) · ${fmt(state.remainingBudget)} $cur restants"
        }
    }

    return when (lang) {
        "ar" -> "وتيرة الصرف: ${fmt(state.dailyAverageSpend)} $cur/اليوم مقابل سقف ${fmt(state.safeToSpendToday)} $cur/اليوم"
        "darija" -> "وتيرة الصرف: ${fmt(state.dailyAverageSpend)} $cur/نهار مقابل ${fmt(state.safeToSpendToday)} $cur/نهار"
        "en" -> "Spending pace: ${fmt(state.dailyAverageSpend)} $cur/day vs ${fmt(state.safeToSpendToday)} $cur/day allowance"
        else -> "Rythme actuel : ${fmt(state.dailyAverageSpend)} $cur/j pour ${fmt(state.safeToSpendToday)} $cur/j autorisés"
    }
}

/**
 * Dashboard Home Screen with stable reading order (Prompt 5):
 * 1. First-time setup card (only for clean fresh accounts)
 * 2. Safe to Spend Hero card
 * 3. Single contextual status line under the hero
 * 4. Three compact indicators (Income, Expenses, Net/Savings)
 * 5. Mini weekly chart & Top 3 categories (when advanced sections are visible)
 * 6. Recent transactions
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
    onSeeAllTransactions: () -> Unit = {},
    pendingBankDetectionsCount: Int = 0,
    onOpenBankDetections: () -> Unit = {}
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val lang = state.profile.language
    val isDark = state.profile.isDarkTheme
    val hide = state.hideAmounts
    fun fmt(value: Double): String = PayCycleEngine.formatAmountOrMask(value, hide)
    var expandedHomeSection by rememberSaveable { mutableStateOf<String?>(null) }

    val recentThree = remember(state.cycleTransactions) {
        val todayIso = PayCycleEngine.todayIso()
        state.cycleTransactions.filter { it.isoDate <= todayIso }.take(3)
    }

    val contextualLine = remember(
        state.cycleTransactions.size,
        state.remainingBudget,
        state.daysRemaining,
        state.activeWindow.totalDays,
        state.dailyAverageSpend,
        state.safeToSpendToday,
        state.hideAmounts,
        lang,
        cur
    ) {
        computeHeroContextualStatusLine(state, cur)
    }

    val topThreeCategories = remember(state.envelopes) {
        state.envelopes
            .filter { it.spent > 0.0 }
            .sortedByDescending { it.spent }
            .take(3)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("overview_screen"),
        contentPadding = PaddingValues(horizontal = MasroufiTokens.Spacing.lg, vertical = MasroufiTokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(MasroufiTokens.Spacing.md)
    ) {
        if (state.isCleanFreshAccount) {
            item(key = "clean_onboarding") {
                Card(
                    shape = MasroufiTokens.Shapes.card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clean_onboarding_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MasroufiTokens.Spacing.sm)
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
                        Spacer(modifier = Modifier.height(MasroufiTokens.Spacing.md))
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

        // 1. Safe to Spend Hero Card
        item(key = "hero_card") {
            DashboardHeroCard(
                state = state,
                strings = strings,
                onToggleHideAmounts = onToggleHideAmounts
            )
        }

        // 2. Single Contextual Line under the Hero
        item(key = "hero_contextual_status") {
            val isAlert = state.remainingBudget < 0
            val stripColor = if (isAlert) semanticWarningColor(isDark) else MaterialTheme.colorScheme.primary
            Surface(
                shape = MasroufiTokens.Shapes.innerTile,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_contextual_status_line")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = stripColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = contextualLine,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (pendingBankDetectionsCount > 0) {
            item(key = "pending_bank_detections_chip_item") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onOpenBankDetections() }
                        .testTag("pending_bank_detections_chip")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.bankDetectPendingChipFmt.replace(
                                "{count}",
                                pendingBankDetectionsCount.toString()
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = strings.bankDetectConfirmBtn,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 3. Three Compact Indicators (Income, Expenses, Remaining/Savings)
        item(key = "home_compact_indicators") {
            val incomeAmount = state.totalAvailableBudget
            val expenseAmount = state.cycleExpenses
            val netSavings = state.remainingBudget
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_compact_indicators_row"),
                horizontalArrangement = Arrangement.spacedBy(MasroufiTokens.Spacing.sm)
            ) {
                CompactKpiTile(
                    label = strings.incomeTab,
                    amountText = "+${fmt(incomeAmount)} $cur",
                    amountColor = semanticIncomeColor(isDark),
                    modifier = Modifier.weight(1f)
                )
                CompactKpiTile(
                    label = strings.expenseTab,
                    amountText = "-${fmt(expenseAmount)} $cur",
                    amountColor = semanticExpenseColor(isDark),
                    modifier = Modifier.weight(1f)
                )
                CompactKpiTile(
                    label = strings.remainingLabel,
                    amountText = "${fmt(netSavings)} $cur",
                    amountColor = if (netSavings >= 0.0) semanticIncomeColor(isDark) else semanticExpenseColor(isDark),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4. Mini Weekly Chart + Top 3 Categories (Tesler's Law single-open accordion, collapsed by default)
        if (state.customPrefs.showWeekSummaryRow && (state.weekTotalSpend > 0.0 || topThreeCategories.isNotEmpty())) {
            item(key = "home_mini_weekly_and_top_categories") {
                val topCatSubtitle = topThreeCategories.firstOrNull()?.let { top ->
                    "${top.config.localizedName(lang)} · ${top.usagePercent}%"
                } ?: state.weekRangeLabel
                ExpressiveAccordionCard(
                    title = strings.thisWeek,
                    subtitle = topCatSubtitle,
                    summaryBadge = "-${fmt(state.weekTotalSpend)} $cur",
                    summaryBadgeColor = semanticExpenseColor(isDark),
                    icon = Icons.Default.BarChart,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isExpanded = expandedHomeSection == "week_preview",
                    onToggle = {
                        expandedHomeSection = if (expandedHomeSection == "week_preview") null else "week_preview"
                    },
                    headerTestTag = "accordion_home_week_preview",
                    modifier = Modifier.testTag("home_mini_week_and_top_categories_card")
                ) {
                    val maxDay = (state.weekDaysSpend.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        state.weekDaysSpend.forEachIndexed { idx, dayVal ->
                            val ratio = (dayVal / maxDay).toFloat().safeFraction(0.10f, 1f)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.65f)
                                        .fillMaxHeight(ratio * 0.68f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (dayVal > 0.0) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = strings.daysShort.getOrElse(idx) { "" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Top 3 categories with category icons & soft tints
                    if (topThreeCategories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(MasroufiTokens.Spacing.sm))
                        Column(verticalArrangement = Arrangement.spacedBy(MasroufiTokens.Spacing.sm)) {
                            topThreeCategories.forEach { envStatus ->
                                val catName = envStatus.config.localizedName(lang)
                                val visual = categoryVisualStyle("${envStatus.config.key} $catName", isDark)
                                val progress = if (envStatus.totalAvailable > 0.0) {
                                    (envStatus.spent / envStatus.totalAvailable).toFloat().safeFraction(0f, 1f)
                                } else {
                                    (envStatus.spent / state.cycleExpenses.coerceAtLeast(1.0)).toFloat().safeFraction(0f, 1f)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(visual.container),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = visual.icon,
                                            contentDescription = null,
                                            tint = visual.foreground,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = catName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "-${fmt(envStatus.spent)} $cur",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = semanticExpenseColor(isDark)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        ExpressiveAnimatedProgressBar(
                                            progress = progress,
                                            color = visual.foreground,
                                            height = 6.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Recent Transactions (Tesler's Law single-open accordion, collapsed by default)
        item(key = "home_recent_transactions") {
            val recentTitle = when (lang) {
                "en" -> "Recent transactions"
                "ar" -> "أحدث العمليات"
                "darija" -> "آخر العمليات"
                else -> "Opérations récentes"
            }
            val recentSubtitle = recentThree.firstOrNull()?.let { latest ->
                "${latest.desc.ifBlank { latest.category }} · ${fmt(latest.amount)} $cur"
            } ?: when (lang) {
                "en" -> "Tap to view or add an entry"
                "ar" -> "اضغط للعرض أو الإضافة"
                "darija" -> "برك باش تشوف ولا تزيد عملية"
                else -> "Appuyez pour consulter ou ajouter"
            }
            ExpressiveAccordionCard(
                title = recentTitle,
                subtitle = recentSubtitle,
                summaryBadge = "${recentThree.size} op.",
                summaryBadgeColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.History,
                accentColor = MaterialTheme.colorScheme.primary,
                isExpanded = expandedHomeSection == "recent_txs",
                onToggle = {
                    expandedHomeSection = if (expandedHomeSection == "recent_txs") null else "recent_txs"
                },
                headerTestTag = "accordion_home_recent_txs"
            ) {
                DashboardRecentTransactions(
                    state = state,
                    strings = strings,
                    recentTransactions = recentThree,
                    onEditTransaction = onEditTransaction,
                    onSeeAllTransactions = onSeeAllTransactions,
                    onAddFirstTransaction = { onOpenAddModal("expense") }
                )
            }
            Spacer(modifier = Modifier.height(92.dp))
        }
    }
}

@Composable
private fun CompactKpiTile(
    label: String,
    amountText: String,
    amountColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MasroufiTokens.Shapes.innerTile,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
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
                text = amountText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = amountColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Insights Tab combining Week and Month views with an inline segmented toggle.
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
    val activeBg = MaterialTheme.colorScheme.primary
    val activeFg = MaterialTheme.colorScheme.onPrimary

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
    val haptics = LocalHapticFeedback.current
    fun fmt(value: Double): String = PayCycleEngine.formatAmountOrMask(value, hide)
    val rawSpentMax = state.weekDaysSpend.maxOrNull() ?: 0.0
    val rawFutureMax = state.weekDaysFutureSpend.maxOrNull() ?: 0.0
    val rawMax = max(100.0, max(rawSpentMax, rawFutureMax))
    val maxVal = if (rawMax.isNaN() || rawMax.isInfinite()) 100.0 else max(100.0, rawMax)
    val step = listOf(50, 100, 200, 250, 500, 1000, 2000, 5000).firstOrNull { it * 4 >= maxVal }
        ?: (ceil(maxVal / 4000.0).toInt() * 1000).coerceAtLeast(100)
    val ceilVal = (step * 4).toDouble().coerceAtLeast(100.0)
    var selectedBarIndex by remember { mutableIntStateOf(-1) }
    var expandedWeekSection by rememberSaveable { mutableStateOf<String?>(null) }
    val lang = state.profile.language
    val isDark = state.profile.isDarkTheme
    val topCategories = remember(state.envelopes) {
        state.envelopes.filter { it.spent > 0.0 }.sortedByDescending { it.spent }.take(5)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("week_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        style = MaterialTheme.typography.bodySmall,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "week_chart_accordion") {
            val peakDayLabel = if (state.mostExpensiveDayIndex in 0..6) {
                "${strings.mostSpentDay}: ${strings.daysFull[state.mostExpensiveDayIndex]}"
            } else {
                state.weekRangeLabel
            }
            ExpressiveAccordionCard(
                title = strings.chartTitle,
                subtitle = peakDayLabel,
                summaryBadge = "${fmt(state.weekTotalSpend)} $cur",
                summaryBadgeColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.BarChart,
                accentColor = MaterialTheme.colorScheme.primary,
                isExpanded = expandedWeekSection == "daily_chart",
                onToggle = {
                    expandedWeekSection = if (expandedWeekSection == "daily_chart") null else "daily_chart"
                },
                headerTestTag = "accordion_week_daily_chart"
            ) {
                if (selectedBarIndex in 0..6) {
                    val selectedDayLabel = strings.daysFull.getOrElse(selectedBarIndex) { "" }
                    val selectedDayAmount = state.weekDaysSpend.getOrElse(selectedBarIndex) { 0.0 }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.testTag("week_chart_selected_tooltip")
                    ) {
                        Text(
                            text = "$selectedDayLabel · ${fmt(selectedDayAmount)} $cur",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

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
                                val isSelected = idx == selectedBarIndex
                                val barShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedBarIndex = if (selectedBarIndex == idx) -1 else idx
                                        }
                                        .testTag("week_bar_column_$idx"),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    if (displayVal > 0) {
                                        Text(
                                            text = if (hide) "••" else displayVal.safeRoundToInt().toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(if (isSelected) 32.dp else 28.dp)
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
                                        fontSize = 12.sp,
                                        fontWeight = if (isPeak || isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isPeak || isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (topCategories.isNotEmpty()) {
            item(key = "week_top_categories_accordion") {
                val topCatTitle = when (lang) {
                    "en" -> "Top categories"
                    "ar" -> "أبرز الفئات"
                    "darija" -> "أكثر الفئات صرفاً"
                    else -> "Top catégories"
                }
                val lead = topCategories.first()
                ExpressiveAccordionCard(
                    title = topCatTitle,
                    subtitle = "${lead.config.localizedName(lang)} · -${fmt(lead.spent)} $cur",
                    summaryBadge = "Top ${topCategories.size}",
                    summaryBadgeColor = semanticExpenseColor(isDark),
                    icon = Icons.Default.Category,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    isExpanded = expandedWeekSection == "top_categories",
                    onToggle = {
                        expandedWeekSection = if (expandedWeekSection == "top_categories") null else "top_categories"
                    },
                    headerTestTag = "accordion_week_top_categories"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        topCategories.forEach { envStatus ->
                            val catName = envStatus.config.localizedName(lang)
                            val visual = categoryVisualStyle("${envStatus.config.key} $catName", isDark)
                            val progress = (envStatus.spent / state.cycleExpenses.coerceAtLeast(1.0)).toFloat().safeFraction(0f, 1f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(visual.container),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = visual.icon,
                                        contentDescription = null,
                                        tint = visual.foreground,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = catName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "-${fmt(envStatus.spent)} $cur",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = semanticExpenseColor(isDark)
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    ExpressiveAnimatedProgressBar(
                                        progress = progress,
                                        color = visual.foreground,
                                        height = 6.dp
                                    )
                                }
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
