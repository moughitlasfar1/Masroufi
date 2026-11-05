package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.example.domain.PayCycleEngine
import com.example.domain.SmartReminder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.ExpressiveAnimatedProgressBar
import com.example.ui.theme.ExpressiveGlassCard
import com.example.ui.theme.ExpressiveGlassSurface
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveSectionHeader
import com.example.ui.theme.distinctAccountColor
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
        "ar" -> "حساباتي وأرصدتي"
        "darija" -> "حساباتي (كاش، بنكة، محفظة)"
        "en" -> "My Accounts & Balances"
        else -> "Mes Comptes & Soldes"
    }
    "presets" -> when (lang) {
        "ar" -> "إضافة سريعة (1-Tap)"
        "darija" -> "تقضية دغيا (1-Tap)"
        "en" -> "Quick Presets (1-Tap)"
        else -> "Achats Rapides (1-Tap)"
    }
    "insights" -> when (lang) {
        "ar" -> "المساعد المالي الذكي"
        "darija" -> "المستشار الذكي"
        "en" -> "Smart Financial Assistant"
        else -> "Assistant Financier Intelligent"
    }
    "bills" -> when (lang) {
        "ar" -> "تذكيرات الفواتير ودارت"
        "darija" -> "تذكيرات الفواتير ودارت"
        "en" -> "Smart Reminders & Bills"
        else -> "Rappels & Factures"
    }
    "week_summary" -> when (lang) {
        "ar" -> "ملخص الأسبوع والمعدل اليومي"
        "darija" -> "ملخص السيمانة والمعدل اليومي"
        "en" -> "Weekly Summary & Daily Avg"
        else -> "Résumé Semaine & Moyenne/Jour"
    }
    "monthly_review" -> when (lang) {
        "ar" -> "حصيلة الشهر"
        "darija" -> "حصيلة الشهر (البيلان)"
        "en" -> "Monthly Review (Bilan)"
        else -> "Bilan du Mois"
    }
    else -> sectionKey
}

@Composable
fun OverviewScreen(
    state: DashboardState,
    strings: AppStrings,
    onOpenAddModal: (String) -> Unit,
    onQuickPresetTap: (FinancialItem) -> Unit,
    onOpenAccountModal: (AccountItem?) -> Unit,
    onPayBill: (FinancialItem) -> Unit,
    onAddPresetModal: (FinancialItem?) -> Unit,
    onDeletePreset: (String) -> Unit,
    onOpenCustomizationModal: () -> Unit,
    onOpenQuickOnboarding: () -> Unit = {},
    onLoadDemoData: () -> Unit = {},
    onTriggerReminderAction: (SmartReminder) -> Unit = {},
    onToggleHideAmounts: () -> Unit = {},
    onOpenQuickPresetPicker: () -> Unit = {},
    onMoveDashboardSection: (String, Int) -> Unit = { _, _ -> },
    onSetDashboardSectionVisibility: (String, Boolean) -> Unit = { _, _ -> },
    onConvertSubscriptionToBill: (DetectedSubscription) -> Unit = {},
    onDismissDetectedSubscription: (DetectedSubscription) -> Unit = {},
    onShareText: (String) -> Unit = {}
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val hide = state.hideAmounts
    val haptics = LocalHapticFeedback.current
    val lang = state.profile.language
    val pct = if (state.totalAvailableBudget > 0) {
        ((state.cycleExpenses / state.totalAvailableBudget) * 100.0).safeRoundToInt()
    } else 0
    val isOverBudget = state.remainingBudget < 0
    var editPresetsMode by remember { mutableStateOf(false) }
    var editHomeMode by remember { mutableStateOf(false) }

    fun fmt(value: Double): String = PayCycleEngine.formatAmountOrMask(value, hide)

    val orderedSections = state.customPrefs.normalizedOrder()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("overview_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 0. Clean First-Time Onboarding / 30s Quick Setup / Optional Demo Mode Card
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
                                    "ar" -> "مرحباً بك في مصروفي 2.0 — حساب جديد جاهز (0 $cur)"
                                    "darija" -> "مرحبا بيك فـ مصروفي 2.0 — حساب خاوي واجد (0 $cur)"
                                    "en" -> "Welcome to Masroufi 2.0 — Clean Account Ready (0 $cur)"
                                    else -> "Bienvenue sur Masroufi 2.0 — Compte Vierge Prêt (0 $cur)"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (lang) {
                                "ar" -> "اضبط راتبك ويوم الراتب و3 أظرفة في 30 ثانية، أو أدخل أرصدتك الحالية."
                                "darija" -> "قاد الخلصة ونهار الخلاص و3 أظرفة فـ 30 ثانية، أو دخل شحال عندك فالحسابات."
                                "en" -> "Set up your salary, payday and 3 envelopes in 30 seconds, or enter your account balances."
                                else -> "Configurez votre salaire, jour de paie et 3 enveloppes en 30 secondes, ou saisissez vos soldes."
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
                                    "ar" -> "إعداد سريع 30 ثانية (الراتب + 3 أظرفة)"
                                    "darija" -> "إعداد دغيا فـ 30 ثانية (المانضة + 3 أظرفة)"
                                    "en" -> "30-Second Quick Setup (Salary + 3 Envelopes)"
                                    else -> "Configuration Express 30s (Salaire + 3 Enveloppes)"
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

        // 1. Hero Expressive Glass Card (Cycle Spend + Safe to Spend Today + No-Spend Streak + Month-End Forecast + Privacy Eye Button)
        item(key = "hero_card") {
            val heroColors = heroGradientColors(state.customPrefs.accentPalette, isOverBudget)
            val heroShape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp, bottomEnd = 38.dp, bottomStart = 16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(heroShape)
                    .background(Brush.linearGradient(colors = heroColors))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.28f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        ),
                        shape = heroShape
                    )
                    .padding(22.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${strings.thisMonth} (${state.activeWindow.startIso.takeLast(5)} → ${state.activeWindow.endIso.takeLast(5)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // No-Spend Streak Counter Badge on Hero Card
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.28f),
                                border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.4f)),
                                modifier = Modifier.testTag("hero_no_spend_streak_badge")
                            ) {
                                Text(
                                    text = when (lang) {
                                        "ar" -> "🔥 ${state.noSpendStreakDays} يوم بدون صرف"
                                        "darija" -> "🔥 ${state.noSpendStreakDays} يوم بلا مصروف"
                                        "en" -> "🔥 ${state.noSpendStreakDays}d streak"
                                        else -> "🔥 ${state.noSpendStreakDays}j sans dépense"
                                    },
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp
                                )
                            }

                            // One-tap Privacy Eye Toggle on Hero Card
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
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (hide) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (hide) "Afficher les montants" else "Masquer les montants",
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
                                        color = Color.White,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.28f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                            ) {
                                Text(
                                    text = "$pct%",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isOverBudget) AccentCoral else Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = fmt(state.cycleExpenses),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                            modifier = Modifier.testTag("hero_cycle_expenses_text")
                        )
                        Text(
                            text = cur,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF10B981).copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, Color(0xFF6EE7B7).copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = "${strings.budgetLabel}: ${fmt(state.totalAvailableBudget)} $cur",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            if (state.cycleIncomes > 0.0) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF10B981).copy(alpha = 0.32f),
                                    border = BorderStroke(1.dp, Color(0xFF6EE7B7).copy(alpha = 0.65f))
                                ) {
                                    Text(
                                        text = "+${fmt(state.cycleIncomes)} $cur",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFA7F3D0),
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        if (state.totalCycleRollover > 0) {
                            Text(
                                text = strings.rolloverBadge.replace("{amt}", fmt(state.totalCycleRollover)),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFDE68A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ExpressiveAnimatedProgressBar(
                        progress = (pct / 100f).safeFraction(),
                        color = if (isOverBudget) AccentCoral else Color.White,
                        trackColor = Color.Black.copy(alpha = 0.35f),
                        height = 11.dp
                    )

                    if (state.spendProjection.showEstimateAlert && state.monthEndForecastMessage.isNotBlank()) {
                        val isRedAlert = state.spendProjection.alertLevel == com.example.domain.ProjectionAlertLevel.RED
                        val alertColor = if (isRedAlert) AccentCoral else AccentAmber
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = alertColor.copy(alpha = 0.24f),
                            border = BorderStroke(1.dp, alertColor.copy(alpha = 0.72f)),
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

                    if (state.customPrefs.showSafeToSpend) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color.Black.copy(alpha = 0.28f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.safeToSpendTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.92f)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = strings.safeToSpendSub.replace(
                                            "{bills}",
                                            fmt(state.unpaidBillsTotal)
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 11.5.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White.copy(alpha = 0.22f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                                    modifier = Modifier.testTag("hero_safe_to_spend_today_badge")
                                ) {
                                    Text(
                                        text = "${fmt(state.safeToSpendToday)} ${strings.dhPerDay.replace("DH", cur)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1B. Modern Banking Quick-Action Area (One-Tap Expense, Income, Transfer, Quick Preset)
        item(key = "quick_action_bar") {
            ModernBankingQuickActionsCard(
                lang = lang,
                isDarkTheme = state.profile.isDarkTheme,
                onAddExpense = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenAddModal("expense")
                },
                onAddIncome = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenAddModal("income")
                },
                onTransfer = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenAddModal("transfer")
                },
                onQuickPreset = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenQuickPresetPicker()
                }
            )
        }

        // 1C. Inline "Edit Home" Header & Customization Controls
        item(key = "edit_home_bar") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (lang) {
                            "ar" -> "لوحة التحكم المخصصة"
                            "darija" -> "الواجهة الرئيسية ديالك"
                            "en" -> "Your Customizable Dashboard"
                            else -> "Votre Tableau de Bord"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = if (editHomeMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    editHomeMode = !editHomeMode
                                }
                                .testTag("inline_edit_home_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DashboardCustomize,
                                    contentDescription = null,
                                    tint = if (editHomeMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (editHomeMode) {
                                        when (lang) {
                                            "ar" -> "تم التعديل"
                                            "darija" -> "صافي ساليت"
                                            "en" -> "Done Editing"
                                            else -> "Terminer"
                                        }
                                    } else {
                                        when (lang) {
                                            "ar" -> "تعديل الرئيسية"
                                            "darija" -> "قاد الرئيسية"
                                            "en" -> "Edit Home"
                                            else -> "Modifier l'accueil"
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (editHomeMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (editHomeMode) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .testTag("inline_edit_home_panel")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (lang) {
                                        "ar" -> "رتّب البطاقات أو أخفِ ما لا تحتاجه"
                                        "darija" -> "رتب الكارطات الفوق/لتحت أو خبي اللي ما بغيتيش"
                                        "en" -> "Reorder or hide Home dashboard cards"
                                        else -> "Réorganisez ou masquez les cartes de l'accueil"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onOpenCustomizationModal() }
                                ) {
                                    Text(
                                        text = strings.studioShortBtn,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            orderedSections.forEachIndexed { idx, sectionKey ->
                                val isVisible = state.customPrefs.isSectionVisible(sectionKey)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("edit_home_row_$sectionKey")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${idx + 1}. ${dashboardSectionLabel(sectionKey, lang)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onMoveDashboardSection(sectionKey, -1)
                                                },
                                                enabled = idx > 0,
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .testTag("move_up_$sectionKey")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = strings.moveUpDesc,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onMoveDashboardSection(sectionKey, 1)
                                                },
                                                enabled = idx < orderedSections.lastIndex,
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .testTag("move_down_$sectionKey")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = strings.moveDownDesc,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Switch(
                                                checked = isVisible,
                                                onCheckedChange = { checked ->
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onSetDashboardSectionVisibility(sectionKey, checked)
                                                },
                                                modifier = Modifier.testTag("toggle_vis_$sectionKey")
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

        // Dynamic Ordered Dashboard Cards
        orderedSections.forEach { sectionKey ->
            when (sectionKey) {
                "accounts" -> {
                    if (state.customPrefs.showAccountsCard) {
                        item(key = "section_accounts") {
                            ExpressiveGlassCard(
                                cornerRadius = 28.dp,
                                modifier = Modifier.fillMaxWidth()
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
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(
                                            onClick = { onOpenAddModal("transfer") },
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
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                        ExpressiveGlassSurface(
                                            shape = RoundedCornerShape(20.dp),
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            accentBorderColor = accent,
                                            onClick = { onOpenAccountModal(accWithBal.account) },
                                            modifier = Modifier
                                                .width(172.dp)
                                                .testTag("account_card_${accWithBal.account.id}")
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(
                                                            imageVector = icon,
                                                            contentDescription = null,
                                                            tint = accent,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            text = accWithBal.account.name,
                                                            style = MaterialTheme.typography.labelLarge,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit Account",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "${fmt(accWithBal.currentBalance)} $cur",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.ExtraBold,
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

                "presets" -> {
                    if (state.customPrefs.showQuickPresetsCard) {
                        item(key = "section_presets") {
                            ExpressiveGlassCard(
                                cornerRadius = 28.dp,
                                accentRimColor = AccentAmber,
                                modifier = Modifier.fillMaxWidth()
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
                                            tint = AccentAmber,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = strings.quickPresetsTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            accentBorderColor = AccentAmber,
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
                                                        text = "${fmt(preset.amount)} $cur",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
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
                    }
                }

                "insights" -> {
                    if (state.customPrefs.showSmartInsightsCard && (state.smartInsights.isNotEmpty() || state.detectedSubscriptions.isNotEmpty())) {
                        item(key = "section_insights") {
                            ExpressiveGlassCard(
                                cornerRadius = 28.dp,
                                accentRimColor = AccentAmber,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("smart_assistant_card")
                            ) {
                                ExpressiveSectionHeader(
                                    title = when (lang) {
                                        "darija" -> "المستشار الذكي (Smart Assistant)"
                                        "ar" -> "المساعد المالي الذكي"
                                        "en" -> "Smart Financial Assistant"
                                        else -> "Assistant Financier Intelligent"
                                    },
                                    icon = Icons.Default.AutoAwesome,
                                    accentColor = AccentAmber
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                state.smartInsights.forEach { insight ->
                                    val badgeColor = when (insight.severity) {
                                        "warn" -> AccentCoral
                                        "good" -> AccentEmerald
                                        else -> AccentSkyBlue
                                    }
                                    ExpressiveGlassSurface(
                                        shape = RoundedCornerShape(18.dp),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        accentBorderColor = badgeColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text(
                                                text = insight.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = badgeColor,
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

                                // Subscription Detector Section
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
                                                    "ar" -> "🔍 اشتراكات متكررة مكتشفة تلقائياً"
                                                    "darija" -> "🔍 اشتراكات كتعاود كل شهر (Subscription Detector)"
                                                    "en" -> "🔍 Recurring Charges Detected Automatically"
                                                    else -> "🔍 Abonnements récurrents détectés"
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
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "${fmt(sub.amount)} $cur • ${sub.occurrences}x (~le ${sub.typicalDayOfMonth})",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        androidx.compose.material3.TextButton(
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
                }

                "bills" -> {
                    if (state.customPrefs.showBillsCard && state.smartReminders.isNotEmpty()) {
                        item(key = "section_bills") {
                            ExpressiveGlassCard(
                                cornerRadius = 28.dp,
                                accentRimColor = AccentViolet,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("smart_reminders_card")
                            ) {
                                ExpressiveSectionHeader(
                                    title = when (lang) {
                                        "darija" -> "تذكيرات ذكية (الفواتير، دارت، الكريدي)"
                                        "ar" -> "تذكيرات ذكية (الفواتير، دارت، الديون)"
                                        "en" -> "Smart Reminders (Bills, Daret, Debts)"
                                        else -> "Rappels Intelligents (Factures, Daret, Crédits)"
                                    },
                                    icon = Icons.Default.NotificationsActive,
                                    accentColor = AccentViolet
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
                }

                "week_summary" -> {
                    if (state.customPrefs.showWeekSummaryRow) {
                        item(key = "section_week_summary") {
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
                                    accentRimColor = AccentSkyBlue,
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
                                    Text(
                                        text = strings.basedOnMonth,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                "monthly_review" -> {
                    if (state.customPrefs.showMonthlyReviewCard) {
                        item(key = "section_monthly_review") {
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
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
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

                                // Top category + Biggest spending day summary banner
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
                                                fontWeight = FontWeight.Bold
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
                                            color = if (vsLastMonthDiff <= 0) AccentEmerald else AccentCoral,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ExpressiveGlassSurface(
                                        shape = RoundedCornerShape(16.dp),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        accentBorderColor = AccentEmerald,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                                    contentDescription = null,
                                                    tint = AccentEmerald,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = strings.bestCategory,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = AccentEmerald
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = state.bestControlledEnvelope?.config?.localizedName(lang) ?: "—",
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${state.bestControlledEnvelope?.usagePercent ?: 0}${strings.usedPercentSuffix}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    ExpressiveGlassSurface(
                                        shape = RoundedCornerShape(16.dp),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        accentBorderColor = AccentCoral,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                                    contentDescription = null,
                                                    tint = AccentCoral,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = strings.worstCategory,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = AccentCoral
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = state.worstControlledEnvelope?.config?.localizedName(lang) ?: "—",
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${state.worstControlledEnvelope?.usagePercent ?: 0}${strings.usedPercentSuffix}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

        // 8. Compact Expressive Footer Bar (Add Expense + Customization Studio side-by-side)
        item(key = "bottom_cta") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onOpenAddModal("expense") },
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("hero_add_expense_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.btnQuickAdd,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledTonalButton(
                    onClick = onOpenCustomizationModal,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("open_customization_studio_button")
                ) {
                    Icon(imageVector = Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (lang) {
                            "ar" -> "تخصيص المظهر"
                            "darija" -> "تخصيص الواجهة"
                            "en" -> "Customize UI"
                            else -> "Studio Thème"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
private fun ModernBankingQuickActionsCard(
    lang: String,
    isDarkTheme: Boolean = false,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onTransfer: () -> Unit,
    onQuickPreset: () -> Unit
) {
    val expenseTitle = when (lang) {
        "ar" -> "مصروف"
        "darija" -> "مصروف"
        "en" -> "Expense"
        else -> "Dépense"
    }
    val incomeTitle = when (lang) {
        "ar" -> "مدخول"
        "darija" -> "مدخول"
        "en" -> "Income"
        else -> "Revenu"
    }
    val transferTitle = when (lang) {
        "ar" -> "تحويل"
        "darija" -> "تحويل"
        "en" -> "Transfer"
        else -> "Transfert"
    }
    val presetTitle = when (lang) {
        "ar" -> "شراء سريع"
        "darija" -> "تقضية دغيا"
        "en" -> "1-Tap"
        else -> "1-Tap"
    }

    ExpressiveGlassCard(
        cornerRadius = 28.dp,
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_quick_actions_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BankingQuickActionTile(
                title = expenseTitle,
                icon = Icons.Default.ArrowUpward,
                accentColor = semanticExpenseColor(isDarkTheme),
                onClick = onAddExpense,
                testTag = "quick_action_expense",
                modifier = Modifier.weight(1f)
            )
            BankingQuickActionTile(
                title = incomeTitle,
                icon = Icons.Default.ArrowDownward,
                accentColor = semanticIncomeColor(isDarkTheme),
                onClick = onAddIncome,
                testTag = "quick_action_income",
                modifier = Modifier.weight(1f)
            )
            BankingQuickActionTile(
                title = transferTitle,
                icon = Icons.Default.SwapHoriz,
                accentColor = semanticTransferColor(isDarkTheme),
                onClick = onTransfer,
                testTag = "quick_action_transfer",
                modifier = Modifier.weight(1f)
            )
            BankingQuickActionTile(
                title = presetTitle,
                icon = Icons.Default.Bolt,
                accentColor = AccentAmber,
                onClick = onQuickPreset,
                testTag = "quick_action_preset",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BankingQuickActionTile(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    ExpressiveGlassSurface(
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        accentBorderColor = accentColor,
        onClick = onClick,
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                        text = strings.weekTotal,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${PayCycleEngine.formatAmountOrMask(state.weekTotalSpend, hide)} $cur",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                ExpressiveGlassCard(
                    cornerRadius = 26.dp,
                    accentRimColor = AccentSkyBlue,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = strings.mostSpentDay,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val dayName = if (state.mostExpensiveDayIndex in 0..6) {
                        strings.daysFull[state.mostExpensiveDayIndex]
                    } else "—"
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentSkyBlue
                    )
                    Text(
                        text = "${PayCycleEngine.formatAmountOrMask(state.mostExpensiveDayAmount, hide)} $cur",
                        style = MaterialTheme.typography.bodyMedium,
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
                                            color = when {
                                                isFutureOutlined -> MaterialTheme.colorScheme.primary
                                                isPeak -> AccentCoral
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
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
                                                                isPeak -> listOf(AccentCoral, Color(0xFFDC2626))
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
                                        color = if (isPeak) AccentCoral else MaterialTheme.colorScheme.onSurfaceVariant
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
