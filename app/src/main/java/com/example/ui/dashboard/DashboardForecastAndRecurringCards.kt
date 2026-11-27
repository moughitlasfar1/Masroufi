package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CustomizationPrefs
import com.example.domain.CashFlowForecastSnapshot
import com.example.domain.ForecastHorizon
import com.example.domain.PayCycleEngine
import com.example.domain.RecurrenceDirection
import com.example.domain.RecurrenceFrequency
import com.example.domain.RecurrencePostingMode
import com.example.domain.RecurrenceScheduleEngine
import com.example.domain.RecurringOccurrence
import com.example.domain.RecurringRuleItem
import com.example.ui.DashboardState
import com.example.ui.dashboardSectionLabel
import com.example.ui.theme.ExpressiveGlassCard
import com.example.ui.theme.MasroufiTokens
import com.example.ui.theme.semanticExpenseColor
import com.example.ui.theme.semanticIncomeColor
import com.example.ui.theme.semanticWarningColor
import com.masroufi.core.Money

internal fun localizedFrequencyLabel(freq: RecurrenceFrequency, lang: String): String = when (freq) {
    RecurrenceFrequency.WEEKLY -> when (lang) {
        "ar" -> "أسبوعي"
        "darija" -> "كل سيمانة"
        "en" -> "Weekly"
        else -> "Hebdo"
    }
    RecurrenceFrequency.BIWEEKLY -> when (lang) {
        "ar" -> "كل أسبوعين"
        "darija" -> "كل 15 يوم"
        "en" -> "Biweekly"
        else -> "Bi-hebdo"
    }
    RecurrenceFrequency.MONTHLY -> when (lang) {
        "ar" -> "شهري"
        "darija" -> "كل شهر"
        "en" -> "Monthly"
        else -> "Mensuel"
    }
    RecurrenceFrequency.YEARLY -> when (lang) {
        "ar" -> "سنوي"
        "darija" -> "كل عام"
        "en" -> "Yearly"
        else -> "Annuel"
    }
}

internal fun localizedDirectionLabel(dir: RecurrenceDirection, lang: String): String = when (dir) {
    RecurrenceDirection.EXPENSE -> when (lang) {
        "ar" -> "مصروف"
        "darija" -> "مصروف"
        "en" -> "Expense"
        else -> "Dépense"
    }
    RecurrenceDirection.INCOME -> when (lang) {
        "ar" -> "دخل"
        "darija" -> "مدخول"
        "en" -> "Income"
        else -> "Revenu"
    }
    RecurrenceDirection.LOAN_REPAYMENT -> when (lang) {
        "ar" -> "سداد قرض"
        "darija" -> "خلاص كريدي"
        "en" -> "Loan payment"
        else -> "Remboursement"
    }
}

internal fun localizedPostingModeLabel(mode: RecurrencePostingMode, lang: String): String = when (mode) {
    RecurrencePostingMode.AUTO_POST -> when (lang) {
        "ar" -> "تلقائي"
        "darija" -> "أوتوماتيك"
        "en" -> "Auto-post"
        else -> "Automatique"
    }
    RecurrencePostingMode.CONFIRM -> when (lang) {
        "ar" -> "تأكيد بنقرة"
        "darija" -> "تأكيد بضغطة"
        "en" -> "1-tap confirm"
        else -> "Confirmation 1-tap"
    }
}

/**
 * Material 3 Expressive Cash-Flow Forecast Card aligned with [PayCycleEngine] and [CashFlowForecastSnapshot].
 * Fully respects [DashboardState.hideAmounts] (`••••`), RTL layout mirroring, and 48.dp touch targets.
 */
@Composable
fun CashFlowForecastCard(
    snapshot: CashFlowForecastSnapshot,
    horizon: ForecastHorizon,
    currency: String,
    lang: String,
    isDark: Boolean,
    hideAmounts: Boolean,
    onSelectHorizon: (ForecastHorizon) -> Unit,
    modifier: Modifier = Modifier
) {
    fun fmt(v: Money): String = PayCycleEngine.formatAmountOrMask(v, hideAmounts)

    val title = when (lang) {
        "ar" -> "توقعات التدفق النقدي"
        "darija" -> "توقعات الفلوس الجاية"
        "en" -> "Cash-Flow Forecast"
        else -> "Prévision Trésorerie"
    }
    val subtitle = when (lang) {
        "ar" -> "${snapshot.daysRemaining} يوم متبقي · حتى ${snapshot.windowEndIso}"
        "darija" -> "باقي ${snapshot.daysRemaining} يوم · حتى ${snapshot.windowEndIso}"
        "en" -> "${snapshot.daysRemaining}d left · until ${snapshot.windowEndIso}"
        else -> "${snapshot.daysRemaining}j restants · jusqu'au ${snapshot.windowEndIso}"
    }
    val cycleLabel = when (lang) {
        "ar" -> "الدورة"
        "darija" -> "الدورة"
        "en" -> "Pay cycle"
        else -> "Cycle"
    }
    val rollingLabel = when (lang) {
        "ar" -> "30 يوماً"
        "darija" -> "30 يوم"
        "en" -> "30 days"
        else -> "30 jours"
    }
    val startBalLabel = when (lang) {
        "ar" -> "الرصيد السائل"
        "darija" -> "الفلوس دابا"
        "en" -> "Liquid balance"
        else -> "Solde liquide"
    }
    val billsLabel = when (lang) {
        "ar" -> "التزامات قادمة"
        "darija" -> "فواتير وقروض"
        "en" -> "Upcoming bills"
        else -> "Charges à venir"
    }
    val incomeLabel = when (lang) {
        "ar" -> "دخل مجدول"
        "darija" -> "مداخيل جاية"
        "en" -> "Scheduled income"
        else -> "Revenus prévus"
    }
    val endBalLabel = when (lang) {
        "ar" -> "الرصيد المتوقع"
        "darija" -> "الباقي المتوقع"
        "en" -> "Projected end"
        else -> "Solde fin période"
    }
    val dailyLabel = when (lang) {
        "ar" -> "آمن للصرف يومياً"
        "darija" -> "سقف الصرف اليومي"
        "en" -> "Daily safe-to-spend"
        else -> "Disponible / jour"
    }

    val incomeCol = semanticIncomeColor(isDark)
    val expenseCol = semanticExpenseColor(isDark)
    val warnCol = semanticWarningColor(isDark)

    ExpressiveGlassCard(
        cornerRadius = 26.dp,
        accentRimColor = if (snapshot.hasProjectedDeficit) warnCol else MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_forecast_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Horizon toggle (Pay Cycle vs 30d) with 48.dp touch target height
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HorizonChip(
                    label = cycleLabel,
                    selected = horizon == ForecastHorizon.ACTIVE_PAY_CYCLE,
                    onClick = { onSelectHorizon(ForecastHorizon.ACTIVE_PAY_CYCLE) },
                    testTag = "btn_forecast_horizon_cycle"
                )
                HorizonChip(
                    label = rollingLabel,
                    selected = horizon == ForecastHorizon.ROLLING_30_DAYS,
                    onClick = { onSelectHorizon(ForecastHorizon.ROLLING_30_DAYS) },
                    testTag = "btn_forecast_horizon_30d"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Primary projected end-of-cycle + daily safe allowance banner
        Surface(
            shape = MasroufiTokens.Shapes.innerTile,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = endBalLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${fmt(snapshot.projectedEndBalance)} $currency",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (snapshot.projectedEndBalance >= Money.ZERO) incomeCol else expenseCol,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = dailyLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${fmt(snapshot.dailySafeToSpend)} $currency",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ForecastStatPill(
                label = startBalLabel,
                value = "${fmt(snapshot.startingLiquidBalance)} $currency",
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            ForecastStatPill(
                label = billsLabel,
                value = "-${fmt(snapshot.committedUpcomingBills)} $currency",
                valueColor = expenseCol,
                modifier = Modifier.weight(1f)
            )
            ForecastStatPill(
                label = incomeLabel,
                value = "+${fmt(snapshot.scheduledIncome)} $currency",
                valueColor = incomeCol,
                modifier = Modifier.weight(1f)
            )
        }

        if (snapshot.hasProjectedDeficit) {
            Spacer(modifier = Modifier.height(10.dp))
            val deficitDate = snapshot.projectedDeficitDateIso.orEmpty()
            val deficitMsg = when (lang) {
                "ar" -> "تنبيه عجز متوقع يوم $deficitDate (-${fmt(snapshot.projectedDeficitAmount)} $currency)"
                "darija" -> "رد البال: عجز متوقع نهار $deficitDate (-${fmt(snapshot.projectedDeficitAmount)} $currency)"
                "en" -> "Projected deficit on $deficitDate (-${fmt(snapshot.projectedDeficitAmount)} $currency)"
                else -> "Déficit prévu le $deficitDate (-${fmt(snapshot.projectedDeficitAmount)} $currency)"
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forecast_deficit_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = deficitMsg,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizonChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ForecastStatPill(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
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
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Material 3 Expressive Upcoming Recurring Rules card with 1-tap idempotent Post & Skip actions
 * (48.dp accessible touch targets), localized in `fr`, `ar`, `darija`, `en`.
 */
@Composable
fun UpcomingRecurringCard(
    dueOccurrences: List<RecurringOccurrence>,
    totalRulesCount: Int,
    currency: String,
    lang: String,
    isDark: Boolean,
    hideAmounts: Boolean,
    onPostOccurrence: (RecurringOccurrence) -> Unit,
    onSkipOccurrence: (RecurringOccurrence) -> Unit,
    onAddOrEditRule: (RecurringRuleItem?) -> Unit,
    modifier: Modifier = Modifier
) {
    fun fmt(v: Money): String = PayCycleEngine.formatAmountOrMask(v, hideAmounts)

    val title = when (lang) {
        "ar" -> "المواعيد المتكررة القادمة"
        "darija" -> "الالتزامات المتكررة الجاية"
        "en" -> "Upcoming Recurring"
        else -> "Échéances Récurrentes"
    }
    val subtitle = when (lang) {
        "ar" -> "$totalRulesCount قاعدة نشطة"
        "darija" -> "$totalRulesCount التزام مبرمج"
        "en" -> "$totalRulesCount scheduled rule(s)"
        else -> "$totalRulesCount règle(s) planifiée(s)"
    }
    val addLabel = when (lang) {
        "ar" -> "إضافة قاعدة متكررة"
        "darija" -> "زيد التزام متكرر"
        "en" -> "Add recurring rule"
        else -> "Ajouter une récurrence"
    }
    val postDesc = when (lang) {
        "ar" -> "تسجيل الآن"
        "darija" -> "سجّل دابا"
        "en" -> "Post now"
        else -> "Valider maintenant"
    }
    val skipDesc = when (lang) {
        "ar" -> "تخطي هذه المرة"
        "darija" -> "دوز هاد المرة"
        "en" -> "Skip occurrence"
        else -> "Ignorer cette échéance"
    }
    val emptyMsg = when (lang) {
        "ar" -> "لا توجد استحقاقات خلال 14 يوماً. أضف الكراء أو الراتب أو الاشتراك."
        "darija" -> "ما كاين والو فـ 14 يوم الجاية. زيد الكرا ولا الخلصة ولا الاشتراك."
        "en" -> "No occurrences due in the next 14 days. Add rent, salary, or subscriptions."
        else -> "Aucune échéance dans les 14 prochains jours. Ajoutez loyer, salaire ou abonnement."
    }

    val incomeCol = semanticIncomeColor(isDark)
    val expenseCol = semanticExpenseColor(isDark)

    ExpressiveGlassCard(
        cornerRadius = 26.dp,
        accentRimColor = MaterialTheme.colorScheme.secondary,
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_upcoming_recurring_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EventRepeat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            IconButton(
                onClick = { onAddOrEditRule(null) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_add_recurring_rule")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = addLabel,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (dueOccurrences.isEmpty()) {
            Text(
                text = emptyMsg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onAddOrEditRule(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("btn_empty_add_recurring")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(addLabel, fontWeight = FontWeight.Bold)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dueOccurrences.take(4).forEach { occ ->
                    val isInflow = occ.rule.direction == RecurrenceDirection.INCOME
                    val sign = if (isInflow) "+" else "-"
                    val amtColor = if (isInflow) incomeCol else expenseCol
                    val statusBadge = when {
                        occ.isOverdue -> when (lang) {
                            "ar" -> "متأخر"
                            "darija" -> "فايت وقتو"
                            "en" -> "Overdue"
                            else -> "En retard"
                        }
                        occ.isDueToday -> when (lang) {
                            "ar" -> "اليوم"
                            "darija" -> "اليوم"
                            "en" -> "Today"
                            else -> "Aujourd'hui"
                        }
                        else -> occ.occurrenceIsoDate
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            if (occ.isOverdue) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onAddOrEditRule(occ.rule) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = occ.rule.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$statusBadge · ${localizedFrequencyLabel(occ.rule.frequency, lang)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (occ.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "$sign${fmt(occ.effectiveAmount)} $currency",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = amtColor,
                                maxLines = 1
                            )
                            IconButton(
                                onClick = { onPostOccurrence(occ) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_post_occurrence_${occ.rule.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = postDesc,
                                    tint = incomeCol
                                )
                            }
                            IconButton(
                                onClick = { onSkipOccurrence(occ) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_skip_occurrence_${occ.rule.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = skipDesc,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable Dashboard card visibility, Up/Down ordering, and Reset-to-Default editor.
 * Used in BOTH [DashboardLayoutBottomSheet] (Dashboard Customize button) and `CustomizationStudioSheet`.
 */
@Composable
fun DashboardCustomizeSection(
    customPrefs: CustomizationPrefs,
    lang: String,
    onMoveSection: (String, Int) -> Unit,
    onSetSectionVisibility: (String, Boolean) -> Unit,
    onResetToDefault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sectionOrder = customPrefs.resolvedDashboardOrder()
    val headerTitle = when (lang) {
        "ar" -> "ترتيب وإظهار بطاقات الرئيسية"
        "darija" -> "تنظيم بطاقات الصفحة الرئيسية"
        "en" -> "Dashboard Cards & Order"
        else -> "Cartes & Ordre du Tableau de bord"
    }
    val resetLabel = when (lang) {
        "ar" -> "إعادة الترتيب الافتراضي"
        "darija" -> "رجّع الترتيب الأصلي"
        "en" -> "Reset to default"
        else -> "Réinitialiser par défaut"
    }
    val moveUpDesc = when (lang) {
        "ar" -> "تحريك للأعلى"
        "darija" -> "طلّع الفوق"
        "en" -> "Move up"
        else -> "Monter"
    }
    val moveDownDesc = when (lang) {
        "ar" -> "تحريك للأسفل"
        "darija" -> "هبّط لتحت"
        "en" -> "Move down"
        else -> "Descendre"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_customize_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        sectionOrder.forEachIndexed { idx, key ->
            val visible = customPrefs.isSectionVisible(key)
            val label = dashboardSectionLabel(key, lang)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = { onMoveSection(key, -1) },
                        enabled = idx > 0,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_move_up_$key")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = moveUpDesc,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onMoveSection(key, 1) },
                        enabled = idx < sectionOrder.lastIndex,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_move_down_$key")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = moveDownDesc,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Switch(
                        checked = visible,
                        onCheckedChange = { onSetSectionVisibility(key, it) },
                        modifier = Modifier.testTag("switch_section_$key")
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onResetToDefault,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("btn_reset_dashboard_layout")
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = resetLabel, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardLayoutBottomSheet(
    customPrefs: CustomizationPrefs,
    lang: String,
    onMoveSection: (String, Int) -> Unit,
    onSetSectionVisibility: (String, Boolean) -> Unit,
    onResetToDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val closeLabel = when (lang) {
        "ar" -> "تم"
        "darija" -> "صافي"
        "en" -> "Done"
        else -> "Terminer"
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardCustomizeSection(
                customPrefs = customPrefs,
                lang = lang,
                onMoveSection = onMoveSection,
                onSetSectionVisibility = onSetSectionVisibility,
                onResetToDefault = onResetToDefault
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_close_dashboard_layout_sheet")
            ) {
                Text(closeLabel, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Material 3 Bottom Sheet to create, edit, pause, or delete a [RecurringRuleItem].
 * Parses amounts directly into exact `Long` centimes via [Money.fromDhString].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRuleEditorSheet(
    state: DashboardState,
    existingRule: RecurringRuleItem?,
    onSave: (RecurringRuleItem) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = state.profile.language
    val todayIso = remember { PayCycleEngine.todayIso() }
    var title by remember(existingRule) { mutableStateOf(existingRule?.title.orEmpty()) }
    var amountText by remember(existingRule) {
        mutableStateOf(
            existingRule?.amount?.takeIf { it.isPositive }?.toCanonicalDecimalString().orEmpty()
        )
    }
    var direction by remember(existingRule) {
        mutableStateOf(existingRule?.direction ?: RecurrenceDirection.EXPENSE)
    }
    var frequency by remember(existingRule) {
        mutableStateOf(existingRule?.frequency ?: RecurrenceFrequency.MONTHLY)
    }
    var postingMode by remember(existingRule) {
        mutableStateOf(existingRule?.postingMode ?: RecurrencePostingMode.CONFIRM)
    }
    var nextDueIso by remember(existingRule) {
        mutableStateOf(existingRule?.nextDueIsoDate?.ifBlank { todayIso } ?: todayIso)
    }
    var selectedAccountId by remember(existingRule, state.accounts) {
        mutableStateOf(
            existingRule?.accountId?.ifBlank { null }
                ?: state.accounts.firstOrNull()?.account?.id
                ?: "acc_cash"
        )
    }
    var selectedLoanId by remember(existingRule) {
        mutableStateOf(existingRule?.linkedLoanId)
    }
    var isPaused by remember(existingRule) {
        mutableStateOf(existingRule?.isPaused ?: false)
    }

    val activeLoans = remember(state.loans) { state.loans.filter { !it.isPaidOff } }

    val sheetTitle = if (existingRule == null) {
        when (lang) {
            "ar" -> "قاعدة متكررة جديدة"
            "darija" -> "التزام متكرر جديد"
            "en" -> "New Recurring Rule"
            else -> "Nouvelle Récurrence"
        }
    } else {
        when (lang) {
            "ar" -> "تعديل القاعدة المتكررة"
            "darija" -> "تعديل الالتزام المتكرر"
            "en" -> "Edit Recurring Rule"
            else -> "Modifier la Récurrence"
        }
    }
    val saveLabel = when (lang) {
        "ar" -> "حفظ"
        "darija" -> "سجّل"
        "en" -> "Save"
        else -> "Enregistrer"
    }
    val deleteLabel = when (lang) {
        "ar" -> "حذف"
        "darija" -> "مسح"
        "en" -> "Delete"
        else -> "Supprimer"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = sheetTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(120) },
                label = {
                    Text(
                        when (lang) {
                            "ar" -> "العنوان (مثلاً: الكراء، الراتب)"
                            "darija" -> "السمية (مثلاً: الكرا، الخلصة)"
                            "en" -> "Title (e.g. Rent, Salary)"
                            else -> "Intitulé (ex: Loyer, Salaire)"
                        }
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_recurring_title")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = {
                        Text(
                            when (lang) {
                                "ar" -> "المبلغ (${state.customPrefs.currencySymbol})"
                                "darija" -> "الثمن (${state.customPrefs.currencySymbol})"
                                "en" -> "Amount (${state.customPrefs.currencySymbol})"
                                else -> "Montant (${state.customPrefs.currencySymbol})"
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_recurring_amount")
                )
                OutlinedTextField(
                    value = nextDueIso,
                    onValueChange = { nextDueIso = it.take(10) },
                    label = {
                        Text(
                            when (lang) {
                                "ar" -> "تاريخ الاستحقاق"
                                "darija" -> "نهار الاستحقاق"
                                "en" -> "Next due date"
                                else -> "Prochaine date"
                            }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_recurring_date")
                )
            }

            // Direction chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecurrenceDirection.entries.forEach { dir ->
                    FilterChip(
                        selected = direction == dir,
                        onClick = {
                            direction = dir
                            if (dir != RecurrenceDirection.LOAN_REPAYMENT) {
                                selectedLoanId = null
                            } else if (selectedLoanId == null) {
                                selectedLoanId = activeLoans.firstOrNull()?.id
                            }
                        },
                        label = { Text(localizedDirectionLabel(dir, lang), fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Frequency chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecurrenceFrequency.entries.forEach { freq ->
                    FilterChip(
                        selected = frequency == freq,
                        onClick = { frequency = freq },
                        label = { Text(localizedFrequencyLabel(freq, lang), fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Posting mode chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecurrencePostingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = postingMode == mode,
                        onClick = { postingMode = mode },
                        label = { Text(localizedPostingModeLabel(mode, lang), fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Account chips
            if (state.accounts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.accounts.forEach { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.account.id,
                            onClick = { selectedAccountId = acc.account.id },
                            label = { Text(acc.account.name, maxLines = 1) }
                        )
                    }
                }
            }

            // Optional linked loan selector
            if (direction == RecurrenceDirection.LOAN_REPAYMENT && activeLoans.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeLoans.forEach { loan ->
                        FilterChip(
                            selected = selectedLoanId == loan.id,
                            onClick = { selectedLoanId = loan.id },
                            label = { Text(loan.title, maxLines = 1) }
                        )
                    }
                }
            }

            if (existingRule != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (lang) {
                            "ar" -> "إيقاف مؤقت"
                            "darija" -> "موقّف مؤقتاً"
                            "en" -> "Paused"
                            else -> "En pause"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(checked = isPaused, onCheckedChange = { isPaused = it })
                }
            }

            val parsedAmount = Money.fromDhString(amountText)
            val validDate = PayCycleEngine.isValidIsoDate(nextDueIso.trim())
            val loanOk = direction != RecurrenceDirection.LOAN_REPAYMENT || !selectedLoanId.isNullOrBlank()
            val canSave = title.trim().isNotEmpty() && parsedAmount != null && parsedAmount.isPositive && validDate && loanOk

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (existingRule != null && existingRule.id.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            onDelete(existingRule.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("btn_delete_recurring_rule")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = deleteLabel, modifier = Modifier.size(18.dp))
                    }
                }
                Button(
                    onClick = {
                        val safeAmt = parsedAmount ?: return@Button
                        val cleanDate = nextDueIso.trim()
                        val anchorDay = RecurrenceScheduleEngine.parseIsoParts(cleanDate)?.third ?: 1
                        val ruleToSave = RecurringRuleItem(
                            id = existingRule?.id.orEmpty(),
                            userId = existingRule?.userId.orEmpty(),
                            title = title.trim(),
                            direction = direction,
                            amount = safeAmt,
                            frequency = frequency,
                            anchorDayOfMonth = anchorDay,
                            startIsoDate = existingRule?.startIsoDate?.ifBlank { cleanDate } ?: cleanDate,
                            nextDueIsoDate = cleanDate,
                            endIsoDate = existingRule?.endIsoDate,
                            postingMode = postingMode,
                            accountId = selectedAccountId,
                            envelopeKey = existingRule?.envelopeKey?.ifBlank { "charges" } ?: "charges",
                            category = existingRule?.category?.ifBlank { "charges" } ?: "charges",
                            linkedLoanId = if (direction == RecurrenceDirection.LOAN_REPAYMENT) selectedLoanId else null,
                            sourceFinancialItemId = existingRule?.sourceFinancialItemId,
                            isPaused = isPaused,
                            lastPostedOccurrenceKey = existingRule?.lastPostedOccurrenceKey,
                            skippedOccurrenceKeys = existingRule?.skippedOccurrenceKeys ?: emptySet(),
                            createdAtEpochMs = existingRule?.createdAtEpochMs ?: 0L
                        )
                        onSave(ruleToSave)
                        onDismiss()
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("btn_save_recurring_rule")
                ) {
                    Text(saveLabel, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
