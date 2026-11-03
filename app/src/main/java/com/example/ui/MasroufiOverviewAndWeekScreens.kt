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
import com.example.domain.PayCycleEngine
import com.example.domain.SmartReminder
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.heroGradientColors
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
    onLoadDemoData: () -> Unit = {},
    onTriggerReminderAction: (SmartReminder) -> Unit = {},
    onToggleHideAmounts: () -> Unit = {},
    onOpenQuickPresetPicker: () -> Unit = {},
    onMoveDashboardSection: (String, Int) -> Unit = { _, _ -> },
    onSetDashboardSectionVisibility: (String, Boolean) -> Unit = { _, _ -> }
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
        // 0. Clean First-Time Onboarding / Optional Demo Mode Card
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
                                text = "Bienvenue sur Masroufi 2.0 — Compte Vierge Prêt (0 $cur)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Vos comptes démarrent à 0 $cur sans fausses transactions. Touchez une carte de compte ci-dessous pour définir votre solde réel, ou chargez le Mode Démo pour explorer toutes les fonctions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val firstAcc = state.accounts.firstOrNull()?.account
                                    onOpenAccountModal(firstAcc)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Saisir mon solde", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(
                                onClick = onLoadDemoData,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_load_demo_data")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mode Démo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 1. Hero Expressive Card (Cycle Spend + Safe to Spend Today + Privacy Eye Button)
        item(key = "hero_card") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomEnd = 36.dp, bottomStart = 14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = heroGradientColors(state.customPrefs.accentPalette, isOverBudget)
                        )
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
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // One-tap Privacy Eye Toggle on Hero Card
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.28f),
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
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (hide) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (hide) "Afficher les montants" else "Masquer les montants",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
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
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.28f)
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

                    Spacer(modifier = Modifier.height(8.dp))

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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${strings.budgetLabel}: ${fmt(state.totalAvailableBudget)} $cur",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold
                        )
                        if (state.totalCycleRollover > 0) {
                            Text(
                                text = strings.rolloverBadge.replace("{amt}", fmt(state.totalCycleRollover)),
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentAmber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { (pct / 100f).safeFraction() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(CircleShape),
                        color = if (isOverBudget) AccentCoral else Color.White,
                        trackColor = Color.Black.copy(alpha = 0.35f)
                    )

                    if (state.customPrefs.showSafeToSpend) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.26f),
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
                                        color = Color.White.copy(alpha = 0.9f)
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
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "${fmt(state.safeToSpendToday)} ${strings.dhPerDay.replace("DH", cur)}",
                                        style = MaterialTheme.typography.titleMedium,
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
                                        text = "Studio",
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
                                                    contentDescription = "Monter",
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
                                                    contentDescription = "Descendre",
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
                            Card(
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
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
                                            val accent = when (accWithBal.account.kind) {
                                                "bank" -> AccentSkyBlue
                                                "wallet" -> AccentAmber
                                                else -> AccentEmerald
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier
                                                    .width(172.dp)
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .clickable { onOpenAccountModal(accWithBal.account) }
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
                }

                "presets" -> {
                    if (state.customPrefs.showQuickPresetsCard) {
                        item(key = "section_presets") {
                            Card(
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
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
                                                    text = if (editPresetsMode) "Terminé" else "Modifier",
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
                                                    text = "+ Preset",
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
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        if (editPresetsMode) {
                                                            onAddPresetModal(preset)
                                                        } else {
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            onQuickPresetTap(preset)
                                                        }
                                                    }
                                                    .testTag("preset_${preset.id}")
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
                }

                "insights" -> {
                    if (state.customPrefs.showSmartInsightsCard && state.smartInsights.isNotEmpty()) {
                        item(key = "section_insights") {
                            Card(
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("smart_assistant_card")
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = AccentAmber,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = when (lang) {
                                                "darija" -> "المستشار الذكي (Smart Assistant)"
                                                "ar" -> "المساعد المالي الذكي"
                                                "en" -> "Smart Financial Assistant"
                                                else -> "Assistant Financier Intelligent"
                                            },
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    state.smartInsights.forEach { insight ->
                                        val badgeColor = when (insight.severity) {
                                            "warn" -> AccentCoral
                                            "good" -> AccentEmerald
                                            else -> AccentSkyBlue
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                }
                            }
                        }
                    }
                }

                "bills" -> {
                    if (state.customPrefs.showBillsCard && state.smartReminders.isNotEmpty()) {
                        item(key = "section_bills") {
                            Card(
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("smart_reminders_card")
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            tint = AccentViolet,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = when (lang) {
                                                "darija" -> "تذكيرات ذكية (الفواتير، دارت، الكريدي)"
                                                "ar" -> "تذكيرات ذكية (الفواتير، دارت، الديون)"
                                                "en" -> "Smart Reminders (Bills, Daret, Debts)"
                                                else -> "Rappels Intelligents (Factures, Daret, Crédits)"
                                            },
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    state.smartReminders.take(5).forEach { rem ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
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
                                Card(
                                    shape = RoundedCornerShape(26.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = strings.thisWeek,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "${fmt(state.weekTotalSpend)} $cur",
                                            style = MaterialTheme.typography.headlineMedium,
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
                                }

                                Card(
                                    shape = RoundedCornerShape(26.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = strings.dailyAvg,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "${fmt(state.dailyAverageSpend)} $cur",
                                            style = MaterialTheme.typography.headlineMedium,
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
                }

                "monthly_review" -> {
                    if (state.customPrefs.showMonthlyReviewCard) {
                        item(key = "section_monthly_review") {
                            Card(
                                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 28.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lightbulb,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = strings.monthlyReviewTitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = if (state.remainingBudget >= 0) {
                                                    "Reste ${fmt(state.remainingBudget)} $cur (${state.activeWindow.daysRemaining}j restants)"
                                                } else {
                                                    "Dépassement de ${fmt(-state.remainingBudget)} $cur"
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainer,
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
                                                    text = "${state.bestControlledEnvelope?.usagePercent ?: 0}% utilisé",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainer,
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
                                                    text = "${state.worstControlledEnvelope?.usagePercent ?: 0}% utilisé",
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
        }

        // 8. Hero Add Expense Button + Customization Studio Shortcut
        item(key = "bottom_cta") {
            Button(
                onClick = { onOpenAddModal("expense") },
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("hero_add_expense_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.btnQuickAdd,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            FilledTonalButton(
                onClick = onOpenCustomizationModal,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_customization_studio_button")
            ) {
                Icon(imageVector = Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Personnaliser le thème, la devise & l'écran d'accueil",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(modifier = Modifier.height(76.dp))
        }
    }
}

@Composable
private fun ModernBankingQuickActionsCard(
    lang: String,
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
    val expenseSub = when (lang) {
        "ar" -> "بضغطة واحدة"
        "darija" -> "بضغطة وحدة"
        "en" -> "One tap to add"
        else -> "1-tap ajout"
    }
    val incomeTitle = when (lang) {
        "ar" -> "مدخول"
        "darija" -> "مدخول"
        "en" -> "Income"
        else -> "Revenu"
    }
    val incomeSub = when (lang) {
        "ar" -> "بضغطة واحدة"
        "darija" -> "زيد مدخول"
        "en" -> "One tap to add"
        else -> "1-tap entrée"
    }
    val transferTitle = when (lang) {
        "ar" -> "تحويل"
        "darija" -> "تحويل"
        "en" -> "Transfer"
        else -> "Transfert"
    }
    val transferSub = when (lang) {
        "ar" -> "بين الحسابات"
        "darija" -> "بين الحسابات"
        "en" -> "Between accounts"
        else -> "Entre comptes"
    }
    val presetTitle = when (lang) {
        "ar" -> "شراء سريع"
        "darija" -> "تقضية دغيا"
        "en" -> "Quick preset"
        else -> "Achat rapide"
    }
    val presetSub = when (lang) {
        "ar" -> "مصاريف متكررة"
        "darija" -> "مصاريف يومية"
        "en" -> "Common expenses"
        else -> "Dépenses fréquentes"
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_quick_actions_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BankingQuickActionTile(
                    title = expenseTitle,
                    subtitle = expenseSub,
                    icon = Icons.Default.ArrowUpward,
                    accentColor = AccentCoral,
                    onClick = onAddExpense,
                    testTag = "quick_action_expense",
                    modifier = Modifier.weight(1f)
                )
                BankingQuickActionTile(
                    title = incomeTitle,
                    subtitle = incomeSub,
                    icon = Icons.Default.ArrowDownward,
                    accentColor = AccentEmerald,
                    onClick = onAddIncome,
                    testTag = "quick_action_income",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BankingQuickActionTile(
                    title = transferTitle,
                    subtitle = transferSub,
                    icon = Icons.Default.SwapHoriz,
                    accentColor = AccentSkyBlue,
                    onClick = onTransfer,
                    testTag = "quick_action_transfer",
                    modifier = Modifier.weight(1f)
                )
                BankingQuickActionTile(
                    title = presetTitle,
                    subtitle = presetSub,
                    icon = Icons.Default.Bolt,
                    accentColor = AccentAmber,
                    onClick = onQuickPreset,
                    testTag = "quick_action_preset",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BankingQuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    val rawMax = state.weekDaysSpend.maxOrNull() ?: 100.0
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
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = strings.weeklyAnalysis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.weeklySub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = "${strings.currentWeekBadge} · ${state.weekRangeLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = strings.weekTotal,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${PayCycleEngine.formatAmountOrMask(state.weekTotalSpend, hide)} $cur",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
        }

        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = strings.chartTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(14.dp)
                    ) {
                        val lineColor = MaterialTheme.colorScheme.outlineVariant
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
                                val ratio = (value / ceilVal).toFloat().safeFraction(0.04f, 1f)
                                val isPeak = idx == state.mostExpensiveDayIndex && value > 0
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    if (value > 0) {
                                        Text(
                                            text = if (hide) "••" else value.safeRoundToInt().toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(26.dp)
                                            .fillMaxHeight(ratio * 0.72f)
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                            .background(
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
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = strings.daysShort.getOrElse(idx) { "" },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        color = if (isPeak) AccentCoral else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(76.dp))
        }
    }
}
