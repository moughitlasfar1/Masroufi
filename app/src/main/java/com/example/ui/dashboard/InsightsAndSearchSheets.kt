package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.EnvelopeConfig
import com.example.domain.CategoryTrendItem
import com.example.domain.DuplicateSuspectItem
import com.example.domain.FinancialInsightsSnapshot
import com.example.domain.InsightAnomalyKind
import com.example.domain.PayCycleEngine
import com.example.domain.SmartInsight
import com.example.domain.SpendingAnomalyItem
import com.example.ui.DashboardState
import com.example.ui.FilterState
import com.example.ui.theme.ExpressiveGlassCard
import com.example.ui.theme.semanticExpenseColor
import com.example.ui.theme.semanticIncomeColor
import com.example.ui.theme.semanticWarningColor
import com.masroufi.core.Money

// ---------------------------------------------------------------------------
// Localized 4-language (fr / ar / darija / en) labels & privacy-safe helpers
// ---------------------------------------------------------------------------

internal data class InsightsUiStrings(
    val cardTitle: String,
    val detailTitle: String,
    val viewDetailsCta: String,
    val cycleComparisonTitle: String,
    val currentCycleLabel: String,
    val previousCycleLabel: String,
    val trendsSectionTitle: String,
    val anomaliesSectionTitle: String,
    val duplicatesSectionTitle: String,
    val categorySpikeBadge: String,
    val largeTxBadge: String,
    val inspectAction: String,
    val noAlertsMessage: String,
    val notEnoughHistoryMessage: String,
    val alertsBadgeFormat: (Int) -> String,
    val duplicatesBadgeFormat: (Int) -> String,
    val daysApartFormat: (Int) -> String,
    val advancedFiltersTitle: String,
    val typeFilterLabel: String,
    val typeAll: String,
    val typeExpense: String,
    val typeIncome: String,
    val typeTransfer: String,
    val dateRangeLabel: String,
    val startDateLabel: String,
    val endDateLabel: String,
    val presetCycleLabel: String,
    val preset7dLabel: String,
    val preset30dLabel: String,
    val amountRangeLabel: String,
    val minAmountLabel: String,
    val maxAmountLabel: String,
    val loanFilterLabel: String,
    val loanLinkedOnlyLabel: String,
    val allLoansLabel: String,
    val accountsLabel: String,
    val categoriesLabel: String,
    val resetAllLabel: String,
    val applyFiltersLabel: String,
    val filtersBtnLabel: String,
    val clearFiltersLabel: String
)

internal fun insightsStringsFor(lang: String): InsightsUiStrings = when (lang) {
    "ar" -> InsightsUiStrings(
        cardTitle = "تحليلات مالية ذكية",
        detailTitle = "التحليلات والتنبيهات المالية",
        viewDetailsCta = "عرض التحليلات",
        cycleComparisonTitle = "مقارنة الدورة الحالية بالسابقة",
        currentCycleLabel = "الدورة الحالية",
        previousCycleLabel = "الدورة السابقة",
        trendsSectionTitle = "اتجاهات الفئات",
        anomaliesSectionTitle = "مصاريف غير معتادة",
        duplicatesSectionTitle = "عمليات مكررة محتملة",
        categorySpikeBadge = "ارتفاع فئة",
        largeTxBadge = "مصروف مرتفع",
        inspectAction = "فحص",
        noAlertsMessage = "لم يتم رصد أي ارتفاع غير معتاد أو تكرار في هذه الدورة.",
        notEnoughHistoryMessage = "أضف المزيد من العمليات لتفعيل مقارنة الدورات واكتشاف التنبيهات.",
        alertsBadgeFormat = { n -> "$n تنبيهات" },
        duplicatesBadgeFormat = { n -> "$n تكرار محتمل" },
        daysApartFormat = { d -> if (d == 0) "نفس اليوم" else "فارق $d يوم" },
        advancedFiltersTitle = "فلاتر البحث المتقدمة",
        typeFilterLabel = "نوع العملية",
        typeAll = "الكل",
        typeExpense = "مصروف",
        typeIncome = "دخل",
        typeTransfer = "تحويل",
        dateRangeLabel = "الفترة الزمنية (YYYY-MM-DD)",
        startDateLabel = "من تاريخ",
        endDateLabel = "إلى تاريخ",
        presetCycleLabel = "الدورة",
        preset7dLabel = "7 أيام",
        preset30dLabel = "30 يوم",
        amountRangeLabel = "نطاق المبلغ",
        minAmountLabel = "الحد الأدنى",
        maxAmountLabel = "الحد الأقصى",
        loanFilterLabel = "القروض والسلف",
        loanLinkedOnlyLabel = "المرتبطة بقرض فقط",
        allLoansLabel = "كل القروض",
        accountsLabel = "الحساب",
        categoriesLabel = "الفئة",
        resetAllLabel = "إعادة ضبط",
        applyFiltersLabel = "تطبيق",
        filtersBtnLabel = "فلاتر",
        clearFiltersLabel = "مسح الفلاتر"
    )
    "darija" -> InsightsUiStrings(
        cardTitle = "تحليلات وتنبيهات الفلوس",
        detailTitle = "تحليلات المصروف والتنبيهات",
        viewDetailsCta = "شوف التفاصيل",
        cycleComparisonTitle = "مقارنة هاد الدورة مع اللي فاتت",
        currentCycleLabel = "هاد الدورة",
        previousCycleLabel = "الدورة اللي فاتت",
        trendsSectionTitle = "تطور الفئات",
        anomaliesSectionTitle = "مصاريف ماشي عادية",
        duplicatesSectionTitle = "عمليات يقدر يكونو معاودين",
        categorySpikeBadge = "طلوع فالفئة",
        largeTxBadge = "مصروف كبير",
        inspectAction = "شوفها",
        noAlertsMessage = "كلشي مزيان، ما بان حتى ارتفاع غريب ولا عملية معاودة فهاد الدورة.",
        notEnoughHistoryMessage = "زيد قيّد عمليات باش تبان مقارنة الدورات والتنبيهات.",
        alertsBadgeFormat = { n -> "$n تنبيهات" },
        duplicatesBadgeFormat = { n -> "$n معاودة محتملة" },
        daysApartFormat = { d -> if (d == 0) "نفس النهار" else "فرق $d أيام" },
        advancedFiltersTitle = "فلاتر البحث المتقدمة",
        typeFilterLabel = "نوع العملية",
        typeAll = "الكل",
        typeExpense = "مصروف",
        typeIncome = "مدخول",
        typeTransfer = "تحويل",
        dateRangeLabel = "التاريخ (YYYY-MM-DD)",
        startDateLabel = "من نهار",
        endDateLabel = "حتى لنهار",
        presetCycleLabel = "هاد الدورة",
        preset7dLabel = "7 أيام",
        preset30dLabel = "30 يوم",
        amountRangeLabel = "المبلغ",
        minAmountLabel = "من مبلغ",
        maxAmountLabel = "حتى لمبلغ",
        loanFilterLabel = "الكريدي والسلف",
        loanLinkedOnlyLabel = "غير اللي مربوطين بكريدي",
        allLoansLabel = "كل الكريديات",
        accountsLabel = "الحساب",
        categoriesLabel = "الفئة",
        resetAllLabel = "حيد كلشي",
        applyFiltersLabel = "طبّق",
        filtersBtnLabel = "فلاتر",
        clearFiltersLabel = "مسح الفلاتر"
    )
    "en" -> InsightsUiStrings(
        cardTitle = "Financial Insights",
        detailTitle = "Financial Insights & Alerts",
        viewDetailsCta = "View Insights",
        cycleComparisonTitle = "Pay-Cycle Comparison",
        currentCycleLabel = "Current cycle",
        previousCycleLabel = "Previous cycle",
        trendsSectionTitle = "Category Trends",
        anomaliesSectionTitle = "Unusual Spending",
        duplicatesSectionTitle = "Possible Duplicates",
        categorySpikeBadge = "Category spike",
        largeTxBadge = "Large expense",
        inspectAction = "Inspect",
        noAlertsMessage = "No spending spikes or suspected duplicates detected in this pay cycle.",
        notEnoughHistoryMessage = "Add more expenses to unlock cycle comparisons and anomaly alerts.",
        alertsBadgeFormat = { n -> "$n alert${if (n > 1) "s" else ""}" },
        duplicatesBadgeFormat = { n -> "$n duplicate${if (n > 1) "s" else ""}" },
        daysApartFormat = { d -> if (d == 0) "Same day" else "$d day${if (d > 1) "s" else ""} apart" },
        advancedFiltersTitle = "Advanced Search Filters",
        typeFilterLabel = "Transaction Type",
        typeAll = "All",
        typeExpense = "Expense",
        typeIncome = "Income",
        typeTransfer = "Transfer",
        dateRangeLabel = "Date Range (YYYY-MM-DD)",
        startDateLabel = "Start date",
        endDateLabel = "End date",
        presetCycleLabel = "Cycle",
        preset7dLabel = "7d",
        preset30dLabel = "30d",
        amountRangeLabel = "Amount Range",
        minAmountLabel = "Min",
        maxAmountLabel = "Max",
        loanFilterLabel = "Loans & Debts",
        loanLinkedOnlyLabel = "Loan-linked only",
        allLoansLabel = "All loans",
        accountsLabel = "Account",
        categoriesLabel = "Category",
        resetAllLabel = "Reset",
        applyFiltersLabel = "Apply",
        filtersBtnLabel = "Filters",
        clearFiltersLabel = "Clear filters"
    )
    else -> InsightsUiStrings(
        cardTitle = "Analyses Financières",
        detailTitle = "Analyses & Alertes Financières",
        viewDetailsCta = "Voir les analyses",
        cycleComparisonTitle = "Comparaison des cycles",
        currentCycleLabel = "Cycle actif",
        previousCycleLabel = "Cycle précédent",
        trendsSectionTitle = "Tendances par catégorie",
        anomaliesSectionTitle = "Dépenses inhabituelles",
        duplicatesSectionTitle = "Doublons potentiels",
        categorySpikeBadge = "Hausse catégorie",
        largeTxBadge = "Dépense élevée",
        inspectAction = "Voir",
        noAlertsMessage = "Aucune anomalie ni doublon détecté sur ce cycle de paie.",
        notEnoughHistoryMessage = "Ajoutez quelques opérations pour activer les comparaisons et alertes.",
        alertsBadgeFormat = { n -> "$n alerte${if (n > 1) "s" else ""}" },
        duplicatesBadgeFormat = { n -> "$n doublon${if (n > 1) "s" else ""}" },
        daysApartFormat = { d -> if (d == 0) "Même jour" else "Écart de ${d}j" },
        advancedFiltersTitle = "Filtres avancés",
        typeFilterLabel = "Type d'opération",
        typeAll = "Tous",
        typeExpense = "Dépense",
        typeIncome = "Revenu",
        typeTransfer = "Virement",
        dateRangeLabel = "Période (AAAA-MM-JJ)",
        startDateLabel = "Date début",
        endDateLabel = "Date fin",
        presetCycleLabel = "Cycle",
        preset7dLabel = "7j",
        preset30dLabel = "30j",
        amountRangeLabel = "Montant",
        minAmountLabel = "Min",
        maxAmountLabel = "Max",
        loanFilterLabel = "Prêts & Dettes",
        loanLinkedOnlyLabel = "Liées à un prêt uniquement",
        allLoansLabel = "Tous les prêts",
        accountsLabel = "Compte",
        categoriesLabel = "Catégorie",
        resetAllLabel = "Réinitialiser",
        applyFiltersLabel = "Appliquer",
        filtersBtnLabel = "Filtres",
        clearFiltersLabel = "Effacer filtres"
    )
}

internal fun formatSignedDeltaOrMask(
    delta: Money,
    currency: String,
    hideAmounts: Boolean
): String {
    if (hideAmounts) return "•••• $currency"
    val sign = if (delta.isPositive) "+" else if (delta.isNegative) "-" else ""
    return "$sign${PayCycleEngine.formatDh(delta.abs())} $currency"
}

internal fun formatPercentBadge(percentChange: Int?, hideAmounts: Boolean): String? {
    if (percentChange == null) return null
    if (hideAmounts) return "••%"
    return if (percentChange > 0) "+$percentChange%" else "$percentChange%"
}

internal fun resolveEnvelopeDisplayName(
    envelopeKey: String,
    envelopes: List<EnvelopeConfig>,
    lang: String
): String {
    return envelopes.firstOrNull { it.key.equals(envelopeKey, ignoreCase = true) }
        ?.localizedName(lang)
        ?: envelopeKey.replaceFirstChar { it.uppercaseChar() }
}

// ---------------------------------------------------------------------------
// 1. Dashboard Card — Financial Insights Summary
// ---------------------------------------------------------------------------

@Composable
fun FinancialInsightsDashboardCard(
    snapshot: FinancialInsightsSnapshot,
    smartInsights: List<SmartInsight>,
    envelopes: List<EnvelopeConfig>,
    currency: String,
    lang: String,
    isDark: Boolean,
    hideAmounts: Boolean,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui = remember(lang) { insightsStringsFor(lang) }
    val incColor = semanticIncomeColor(isDark)
    val expColor = semanticExpenseColor(isDark)
    val warnColor = semanticWarningColor(isDark)
    val deltaColor = when {
        snapshot.totalExpenseDelta.isPositive -> expColor
        snapshot.totalExpenseDelta.isNegative -> incColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ExpressiveGlassCard(
        cornerRadius = 26.dp,
        accentRimColor = if (snapshot.hasAnyAlerts) warnColor else MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_insights_card")
    ) {
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
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = if (snapshot.hasAnyAlerts) warnColor else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = ui.cardTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val pctText = formatPercentBadge(snapshot.totalExpensePercentChange, hideAmounts)
            if (pctText != null) {
                Surface(
                    shape = CircleShape,
                    color = deltaColor.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (snapshot.totalExpenseDelta.isPositive) {
                                Icons.AutoMirrored.Filled.TrendingUp
                            } else {
                                Icons.AutoMirrored.Filled.TrendingDown
                            },
                            contentDescription = null,
                            tint = deltaColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = pctText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = deltaColor,
                            modifier = Modifier.testTag("insights_card_delta_pct")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (snapshot.hasEnoughHistory) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ui.currentCycleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${PayCycleEngine.formatAmountOrMask(snapshot.currentCycleExpenseTotal, hideAmounts)} $currency",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("insights_card_current_total")
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = ui.previousCycleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatSignedDeltaOrMask(snapshot.totalExpenseDelta, currency, hideAmounts),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = deltaColor,
                        modifier = Modifier.testTag("insights_card_delta_amount")
                    )
                }
            }

            val topTrend = snapshot.topIncreasingCategory ?: snapshot.topSavingCategory
            if (topTrend != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val catLabel = resolveEnvelopeDisplayName(topTrend.envelopeKey, envelopes, lang)
                val trendColor = if (topTrend.isIncrease) expColor else incColor
                val trendPct = formatPercentBadge(topTrend.percentChange, hideAmounts)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = catLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = buildString {
                                append(formatSignedDeltaOrMask(topTrend.delta, currency, hideAmounts))
                                if (trendPct != null) append(" ($trendPct)")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = trendColor
                        )
                    }
                }
            }
        } else if (smartInsights.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                smartInsights.take(2).forEach { insight ->
                    Text(
                        text = "${insight.title} — ${insight.body}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Text(
                text = ui.notEnoughHistoryMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (snapshot.hasAnyAlerts) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (snapshot.anomalies.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = warnColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, warnColor.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = warnColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = ui.alertsBadgeFormat(snapshot.anomalies.size),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = warnColor,
                                modifier = Modifier.testTag("insights_card_anomalies_badge")
                            )
                        }
                    }
                }
                if (snapshot.duplicateSuspects.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = expColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, expColor.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = expColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = ui.duplicatesBadgeFormat(snapshot.duplicateSuspects.size),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = expColor,
                                modifier = Modifier.testTag("insights_card_duplicates_badge")
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onOpenDetail,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("btn_open_financial_insights")
        ) {
            Text(ui.viewDetailsCta, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Detail Bottom Sheet — Cycle Comparison, Trends, Anomalies, Duplicates
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialInsightsDetailSheet(
    state: DashboardState,
    onInspectFilter: ((FilterState) -> FilterState) -> Unit,
    onDismiss: () -> Unit
) {
    val snapshot = state.financialInsights
    val lang = state.profile.language
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val hide = state.hideAmounts
    val isDark = state.profile.isDarkTheme
    val envelopes = remember(state.envelopes) { state.envelopes.map { it.config } }
    val ui = remember(lang) { insightsStringsFor(lang) }
    val incColor = semanticIncomeColor(isDark)
    val expColor = semanticExpenseColor(isDark)
    val warnColor = semanticWarningColor(isDark)

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("financial_insights_detail_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = ui.detailTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )

            // Cycle comparison summary
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = ui.cycleComparisonTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = ui.currentCycleLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${PayCycleEngine.formatAmountOrMask(snapshot.currentCycleExpenseTotal, hide)} $cur",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = ui.previousCycleLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${PayCycleEngine.formatAmountOrMask(snapshot.previousCycleExpenseTotal, hide)} $cur",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val deltaColor = when {
                        snapshot.totalExpenseDelta.isPositive -> expColor
                        snapshot.totalExpenseDelta.isNegative -> incColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val pctText = formatPercentBadge(snapshot.totalExpensePercentChange, hide)
                    Text(
                        text = buildString {
                            append(formatSignedDeltaOrMask(snapshot.totalExpenseDelta, cur, hide))
                            if (pctText != null) append(" ($pctText)")
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = deltaColor
                    )
                }
            }

            // Anomalies
            if (snapshot.anomalies.isNotEmpty()) {
                Text(
                    text = ui.anomaliesSectionTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snapshot.anomalies.forEach { anomaly ->
                        AnomalyRowCard(
                            anomaly = anomaly,
                            envelopes = envelopes,
                            currency = cur,
                            lang = lang,
                            hideAmounts = hide,
                            warnColor = warnColor,
                            ui = ui,
                            onInspect = {
                                onInspectFilter {
                                    FilterState(
                                        searchQuery = anomaly.transactionDesc,
                                        selectedCategoryKey = anomaly.envelopeKey,
                                        selectedType = "expense"
                                    )
                                }
                                onDismiss()
                            }
                        )
                    }
                }
            }

            // Duplicate Suspects
            if (snapshot.duplicateSuspects.isNotEmpty()) {
                Text(
                    text = ui.duplicatesSectionTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snapshot.duplicateSuspects.forEach { dup ->
                        DuplicateSuspectRowCard(
                            duplicate = dup,
                            currency = cur,
                            hideAmounts = hide,
                            expColor = expColor,
                            ui = ui,
                            onInspect = {
                                onInspectFilter {
                                    FilterState(
                                        searchQuery = dup.description,
                                        selectedType = "expense",
                                        minAmount = dup.amount,
                                        maxAmount = dup.amount
                                    )
                                }
                                onDismiss()
                            }
                        )
                    }
                }
            }

            if (!snapshot.hasAnyAlerts) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (snapshot.hasEnoughHistory) ui.noAlertsMessage else ui.notEnoughHistoryMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // Category Trends
            if (snapshot.categoryTrends.isNotEmpty()) {
                Text(
                    text = ui.trendsSectionTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snapshot.categoryTrends.take(8).forEach { trend ->
                        CategoryTrendRowCard(
                            trend = trend,
                            envelopes = envelopes,
                            currency = cur,
                            lang = lang,
                            hideAmounts = hide,
                            incColor = incColor,
                            expColor = expColor,
                            onClick = {
                                onInspectFilter {
                                    FilterState(
                                        selectedCategoryKey = trend.envelopeKey,
                                        selectedType = "expense"
                                    )
                                }
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnomalyRowCard(
    anomaly: SpendingAnomalyItem,
    envelopes: List<EnvelopeConfig>,
    currency: String,
    lang: String,
    hideAmounts: Boolean,
    warnColor: androidx.compose.ui.graphics.Color,
    ui: InsightsUiStrings,
    onInspect: () -> Unit
) {
    val catName = resolveEnvelopeDisplayName(anomaly.envelopeKey, envelopes, lang)
    val badge = when (anomaly.kind) {
        InsightAnomalyKind.CATEGORY_SPIKE -> ui.categorySpikeBadge
        InsightAnomalyKind.LARGE_TRANSACTION -> ui.largeTxBadge
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, warnColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insight_anomaly_${anomaly.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "$badge · $catName",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = warnColor
                )
                Text(
                    text = anomaly.transactionDesc.ifBlank { catName },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${PayCycleEngine.formatAmountOrMask(anomaly.currentAmount, hideAmounts)} $currency" +
                        " (${formatPercentBadge(anomaly.percentAboveBaseline, hideAmounts) ?: ""})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onInspect,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(ui.inspectAction, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DuplicateSuspectRowCard(
    duplicate: DuplicateSuspectItem,
    currency: String,
    hideAmounts: Boolean,
    expColor: androidx.compose.ui.graphics.Color,
    ui: InsightsUiStrings,
    onInspect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, expColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insight_duplicate_${duplicate.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "${duplicate.description} · ${PayCycleEngine.formatAmountOrMask(duplicate.amount, hideAmounts)} $currency",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${duplicate.firstIsoDate} & ${duplicate.secondIsoDate} · ${ui.daysApartFormat(duplicate.daysApart)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onInspect,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(ui.inspectAction, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CategoryTrendRowCard(
    trend: CategoryTrendItem,
    envelopes: List<EnvelopeConfig>,
    currency: String,
    lang: String,
    hideAmounts: Boolean,
    incColor: androidx.compose.ui.graphics.Color,
    expColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val catName = resolveEnvelopeDisplayName(trend.envelopeKey, envelopes, lang)
    val deltaColor = when {
        trend.isIncrease -> expColor
        trend.isDecrease -> incColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val pctText = formatPercentBadge(trend.percentChange, hideAmounts)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("insight_trend_${trend.envelopeKey}")
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
                    text = catName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${PayCycleEngine.formatAmountOrMask(trend.currentCycleSpent, hideAmounts)} $currency",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = buildString {
                    append(formatSignedDeltaOrMask(trend.delta, currency, hideAmounts))
                    if (pctText != null) append(" ($pctText)")
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = deltaColor
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Advanced Multi-Criteria Search & Filter Bottom Sheet (Room FTS4 + Exact Centimes)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedTransactionFilterSheet(
    state: DashboardState,
    onApplyFilter: ((FilterState) -> FilterState) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val lang = state.profile.language
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val ui = remember(lang) { insightsStringsFor(lang) }
    val initial = state.filterState

    var selectedType by remember { mutableStateOf(initial.selectedType) }
    var startIso by remember { mutableStateOf(initial.startIsoDate.orEmpty()) }
    var endIso by remember { mutableStateOf(initial.endIsoDate.orEmpty()) }
    var minDhText by remember {
        mutableStateOf(initial.minAmount?.toCanonicalDecimalString().orEmpty())
    }
    var maxDhText by remember {
        mutableStateOf(initial.maxAmount?.toCanonicalDecimalString().orEmpty())
    }
    var selectedAccountId by remember { mutableStateOf(initial.selectedAccountId) }
    var selectedCategoryKey by remember { mutableStateOf(initial.selectedCategoryKey) }
    var loanLinkedOnly by remember { mutableStateOf(initial.loanLinkedOnly) }
    var selectedLoanId by remember { mutableStateOf(initial.selectedLoanId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("advanced_filter_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = ui.advancedFiltersTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Transaction Type
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = ui.typeFilterLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val types = listOf(
                        "all" to ui.typeAll,
                        "expense" to ui.typeExpense,
                        "income" to ui.typeIncome,
                        "transfer" to ui.typeTransfer
                    )
                    types.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedType == key,
                            onClick = { selectedType = key },
                            label = { Text(label) },
                            modifier = Modifier.testTag("filter_type_$key")
                        )
                    }
                }
            }

            // Date Range + Quick Presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = ui.dateRangeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val todayIso = remember { PayCycleEngine.todayIso() }
                    val sevenDaysAgoIso = remember(todayIso) {
                        PayCycleEngine.epochDaysToIso(PayCycleEngine.parseEpochDays(todayIso) - 7L)
                    }
                    val thirtyDaysAgoIso = remember(todayIso) {
                        PayCycleEngine.epochDaysToIso(PayCycleEngine.parseEpochDays(todayIso) - 30L)
                    }
                    FilterChip(
                        selected = startIso == state.activeWindow.startIso && endIso == state.activeWindow.endIso,
                        onClick = {
                            startIso = state.activeWindow.startIso
                            endIso = state.activeWindow.endIso
                        },
                        label = { Text(ui.presetCycleLabel) }
                    )
                    FilterChip(
                        selected = startIso == sevenDaysAgoIso && endIso == todayIso,
                        onClick = {
                            startIso = sevenDaysAgoIso
                            endIso = todayIso
                        },
                        label = { Text(ui.preset7dLabel) }
                    )
                    FilterChip(
                        selected = startIso == thirtyDaysAgoIso && endIso == todayIso,
                        onClick = {
                            startIso = thirtyDaysAgoIso
                            endIso = todayIso
                        },
                        label = { Text(ui.preset30dLabel) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = startIso,
                        onValueChange = { startIso = it.take(10) },
                        label = { Text(ui.startDateLabel) },
                        placeholder = { Text("2026-10-01") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("filter_start_date_input")
                    )
                    OutlinedTextField(
                        value = endIso,
                        onValueChange = { endIso = it.take(10) },
                        label = { Text(ui.endDateLabel) },
                        placeholder = { Text("2026-10-31") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("filter_end_date_input")
                    )
                }
            }

            // Exact Long-centimes Amount Range
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${ui.amountRangeLabel} ($cur)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = minDhText,
                        onValueChange = { minDhText = it.take(14) },
                        label = { Text("${ui.minAmountLabel} ($cur)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("filter_min_amount_input")
                    )
                    OutlinedTextField(
                        value = maxDhText,
                        onValueChange = { maxDhText = it.take(14) },
                        label = { Text("${ui.maxAmountLabel} ($cur)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("filter_max_amount_input")
                    )
                }
            }

            // Category & Account quick selectors
            if (state.envelopes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = ui.categoriesLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryKey == "all",
                            onClick = { selectedCategoryKey = "all" },
                            label = { Text(ui.typeAll) }
                        )
                        state.envelopes.forEach { env ->
                            FilterChip(
                                selected = selectedCategoryKey == env.config.key,
                                onClick = { selectedCategoryKey = env.config.key },
                                label = { Text(env.config.localizedName(lang)) }
                            )
                        }
                    }
                }
            }

            if (state.accounts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = ui.accountsLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedAccountId == "all",
                            onClick = { selectedAccountId = "all" },
                            label = { Text(ui.typeAll) }
                        )
                        state.accounts.forEach { acc ->
                            FilterChip(
                                selected = selectedAccountId == acc.account.id,
                                onClick = { selectedAccountId = acc.account.id },
                                label = { Text(acc.account.name.take(16)) }
                            )
                        }
                    }
                }
            }

            // Loan link filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = ui.loanFilterLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ui.loanLinkedOnlyLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = loanLinkedOnly,
                        onCheckedChange = { checked ->
                            loanLinkedOnly = checked
                            if (!checked) selectedLoanId = null
                        },
                        modifier = Modifier.testTag("filter_loan_linked_switch")
                    )
                }
                if (state.loans.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedLoanId == null,
                            onClick = { selectedLoanId = null },
                            label = { Text(ui.allLoansLabel) }
                        )
                        state.loans.forEach { loan ->
                            FilterChip(
                                selected = selectedLoanId == loan.id,
                                onClick = {
                                    selectedLoanId = loan.id
                                    loanLinkedOnly = true
                                },
                                label = { Text(loan.title.take(18)) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onResetAll()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("filter_reset_all_btn")
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(ui.resetAllLabel, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        val parsedMin = Money.fromDhString(minDhText)?.takeIf { !it.isNegative }
                        val parsedMax = Money.fromDhString(maxDhText)?.takeIf { !it.isNegative }
                        val validStart = startIso.trim().takeIf { PayCycleEngine.isValidIsoDate(it) }
                        val validEnd = endIso.trim().takeIf { PayCycleEngine.isValidIsoDate(it) }
                        onApplyFilter { curFilter ->
                            curFilter.copy(
                                selectedType = selectedType,
                                startIsoDate = validStart,
                                endIsoDate = validEnd,
                                minAmount = parsedMin,
                                maxAmount = parsedMax,
                                selectedAccountId = selectedAccountId,
                                selectedCategoryKey = selectedCategoryKey,
                                loanLinkedOnly = loanLinkedOnly,
                                selectedLoanId = selectedLoanId
                            )
                        }
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("filter_apply_btn")
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(ui.applyFiltersLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
