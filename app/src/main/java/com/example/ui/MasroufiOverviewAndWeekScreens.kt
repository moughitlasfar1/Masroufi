package com.example.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.ExpressiveAnimatedProgressBar
import com.example.ui.theme.ExpressiveGlassCard
import com.example.ui.theme.ExpressiveGlassSurface
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveSectionHeader
import com.example.ui.theme.VyxelOnPeriwinkle
import com.example.ui.theme.VyxelPeriwinkle
import com.example.ui.theme.glass3d
import com.example.ui.theme.heroGradientColors
import com.example.ui.theme.semanticExpenseColor
import com.example.ui.theme.semanticIncomeColor
import com.example.ui.theme.semanticTransferColor
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
 * Phase 1 & Phase 2: Simplified Home Screen
 * - First-time setup card shows only for new users (`state.isCleanFreshAccount`).
 * - Hero card shows ONLY "Safe to spend today" by default. Tapping it reveals streak, forecast, cycle spend, and privacy eye.
 * - Below Hero: the last 3 transactions (`state.cycleTransactions.take(3)`).
 * - Accounts, presets, insights, bills, week summary, and monthly review are moved off Home (to Insights and Plan).
 * - Duplicate quick-actions card and footer bar are removed (single FAB remains).
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
    val hide = state.hideAmounts
    val haptics = LocalHapticFeedback.current
    val lang = state.profile.language
    val pct = if (state.totalAvailableBudget > 0) {
        ((state.cycleExpenses / state.totalAvailableBudget) * 100.0).safeRoundToInt()
    } else 0
    val isOverBudget = state.remainingBudget < 0
    var heroExpanded by remember { mutableStateOf(false) }

    fun fmt(value: Double): String = PayCycleEngine.formatAmountOrMask(value, hide)

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
        // 0. First-Time Setup Card — shows ONLY for new users
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
                        Button(
                            onClick = onOpenQuickOnboarding,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_open_30s_onboarding")
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (lang) {
                                    "ar" -> "إعداد سريع 30 ثانية"
                                    "darija" -> "إعداد دغيا فـ 30 ثانية"
                                    "en" -> "30-Second Quick Setup"
                                    else -> "Configuration Express 30s"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val firstAcc = state.accounts.firstOrNull()?.account
                                    onOpenAccountModal(firstAcc)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(strings.enterBalanceBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(
                                onClick = onLoadDemoData,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_load_demo_data")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.demoModeBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 1. Simple Hero Card: Shows ONLY "Safe to spend today" by default.
        // Tapping it reveals streak, forecast, cycle spend details, and privacy eye.
        item(key = "hero_card") {
            val heroColors = heroGradientColors(state.customPrefs.accentPalette, isOverBudget)
            val heroShape = RoundedCornerShape(28.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass3d(
                        cornerRadius = 28.dp,
                        isDark = true,
                        drawOuterShadows = true
                    )
                    .clip(heroShape)
                    .background(Brush.linearGradient(colors = heroColors))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF232834),
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
                        Text(
                            text = strings.safeToSpendTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.90f)
                        )
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
                            color = VyxelPeriwinkle
                        )
                        Text(
                            text = strings.dhPerDay.replace("DH", cur),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.safeToSpendSub.replace("{bills}", fmt(state.unpaidBillsTotal)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f)
                    )

                    // Expanded details revealed on tap: streak, privacy eye, cycle spend, forecast
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
                                // No-Spend Streak Badge
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

                                // Privacy Eye Toggle
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
                                            contentDescription = if (hide) "Afficher" else "Masquer",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (hide) {
                                                when (lang) {
                                                    "ar" -> "مخفي"
                                                    "darija" -> "مخبي"
                                                    "en" -> "Hidden"
                                                    else -> "Masqué"
                                                }
                                            } else {
                                                when (lang) {
                                                    "ar" -> "إخفاء"
                                                    "darija" -> "خبي"
                                                    "en" -> "Privacy"
                                                    else -> "Privé"
                                                }
                                            },
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
                                    color = Color.White.copy(alpha = 0.82f)
                                )
                            }

                            ExpressiveAnimatedProgressBar(
                                progress = (pct / 100f).safeFraction(),
                                color = if (isOverBudget) AccentCoral else VyxelPeriwinkle,
                                trackColor = Color(0xFF232732),
                                height = 8.dp
                            )

                            if (state.spendProjection.showEstimateAlert && state.monthEndForecastMessage.isNotBlank()) {
                                val isRedAlert = state.spendProjection.alertLevel == com.example.domain.ProjectionAlertLevel.RED
                                val alertColor = if (isRedAlert) AccentCoral else AccentAmber
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = alertColor.copy(alpha = 0.20f),
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

        // 2. Last 3 Transactions Card
        item(key = "home_recent_transactions") {
            ExpressiveGlassCard(
                cornerRadius = 26.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_recent_transactions_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.recentTransactionsTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = onSeeAllTransactions,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("home_see_all_transactions_btn")
                    ) {
                        Text(
                            text = strings.seeAllBtn,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (recentThree.isEmpty()) {
                    Text(
                        text = when (lang) {
                            "ar" -> "لا توجد عمليات بعد."
                            "darija" -> "مازال ما قيدتي والو."
                            "en" -> "No transactions yet."
                            else -> "Aucune opération enregistrée."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                } else {
                    val isDark = state.profile.isDarkTheme
                    val envConfigs = state.envelopes.map { it.config }
                    val accMap = state.accounts.associate { it.account.id to it.account.name }
                    recentThree.forEach { tx ->
                        val isIncome = tx.type == "income"
                        val isTransfer = tx.type == "transfer"
                        val amtColor = when {
                            isIncome -> semanticIncomeColor(isDark)
                            isTransfer -> semanticTransferColor(isDark)
                            else -> semanticExpenseColor(isDark)
                        }
                        val sign = when {
                            isIncome -> "+"
                            isTransfer -> "⇄ "
                            else -> "-"
                        }
                        val catDisplay = if (isTransfer) {
                            strings.transferTab
                        } else {
                            tx.localizedCategory(lang, envConfigs)
                        }
                        val accDisplay = accMap[tx.accountId] ?: tx.accountId
                        ExpressiveGlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = { onEditTransaction(tx) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("home_recent_tx_${tx.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                isIncome -> Icons.Default.ArrowDownward
                                                isTransfer -> Icons.Default.SwapHoriz
                                                else -> Icons.Default.ArrowUpward
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tx.desc.ifBlank { catDisplay },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$catDisplay · $accDisplay",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$sign${fmt(tx.amount)} $cur",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = amtColor
                                )
                            }
                        }
                    }
                }
            }
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
        // Week / Month Segmented Toggle
        Surface(
            shape = CircleShape,
            color = if (isDark) Color(0xFF13161D) else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                1.dp,
                if (isDark) Color(0xFF242833) else MaterialTheme.colorScheme.outlineVariant
            ),
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
                    val itemFg = if (selected) activeFg else if (isDark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
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

        // Week summary + Daily average row (moved from Home in Phase 2)
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
