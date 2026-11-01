package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialItem
import com.example.domain.AppStrings
import com.example.domain.EnvelopeStatus
import com.example.domain.PayCycleEngine
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.AccentViolet
import kotlin.math.abs
import kotlin.math.max

@Composable
fun MonthScreen(
    state: DashboardState,
    strings: AppStrings,
    onUpdateWhatIf: (String, Int) -> Unit,
    onContributeSeasonal: (FinancialItem) -> Unit,
    onOpenNewSeasonal: (FinancialItem?) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val pct = if (state.totalAvailableBudget > 0) {
        ((state.cycleExpenses / state.totalAvailableBudget) * 100.0).safeRoundToInt()
    } else 0
    val isOver = state.remainingBudget < 0

    val selectedEnv = state.envelopes.find { it.config.key == state.whatIfState.selectedEnvelopeKey }
        ?: state.envelopes.firstOrNull()
    val baseSpendForSim = max(selectedEnv?.spent ?: 0.0, selectedEnv?.baseAllocated ?: 500.0)
    val monthlySaved = baseSpendForSim * (state.whatIfState.cutPercentage / 100.0)
    val yearlySaved = monthlySaved * 12.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("month_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Monthly Tracking Summary Card
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = strings.monthlyTracking,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = strings.monthlySub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${strings.incomes}: ${PayCycleEngine.formatDh(state.totalAvailableBudget)} $cur",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${strings.expenses}: ${PayCycleEngine.formatDh(state.cycleExpenses)} $cur",
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentCoral
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (pct / 100f).safeFraction() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = if (isOver) AccentCoral else AccentEmerald,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = strings.netBalance,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${if (isOver) "-" else ""}${PayCycleEngine.formatDh(abs(state.remainingBudget))} $cur",
                        style = MaterialTheme.typography.displayMedium,
                        color = if (isOver) AccentCoral else AccentEmerald
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${strings.extraIncome}: ${PayCycleEngine.formatDh(state.cycleIncomes)} $cur",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${strings.endOfMonthProj}: ${PayCycleEngine.formatDh(state.projectedCycleSpend)} $cur",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Advanced Analytics Card (Month-over-Month Comparison & Trends)
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("advanced_analytics_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = AccentViolet
                        )
                        Column {
                            Text(
                                text = when (state.profile.language) {
                                    "darija" -> "تحليلات متقدمة ومقارنة الشهر"
                                    "ar" -> "تحليلات متقدمة ومقارنة الأشهر"
                                    "en" -> "Advanced Analytics & Month Comparison"
                                    else -> "Analytique Avancée & Comparaison Mensuelle"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            val projectedDelta = state.totalAvailableBudget - state.projectedCycleSpend
                            Text(
                                text = if (projectedDelta >= 0) {
                                    "Projection fin de cycle : Excédent estimé de +${PayCycleEngine.formatDh(projectedDelta)} $cur"
                                } else {
                                    "Projection fin de cycle : Déficit estimé de -${PayCycleEngine.formatDh(abs(projectedDelta))} $cur"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (projectedDelta >= 0) AccentEmerald else AccentCoral
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    state.envelopes.forEach { env ->
                        val curSpent = env.spent
                        val prevSpent = env.previousSpent
                        val diff = curSpent - prevSpent
                        val badgeText = when {
                            prevSpent <= 0.0 && curSpent <= 0.0 -> "Stable (0 $cur)"
                            prevSpent <= 0.0 -> "Nouveau (${PayCycleEngine.formatDh(curSpent)} $cur)"
                            diff <= 0.0 -> "-${PayCycleEngine.formatDh(abs(diff))} $cur vs mois préc."
                            else -> "+${PayCycleEngine.formatDh(diff)} $cur vs mois préc."
                        }
                        val badgeColor = if (diff <= 0.0) AccentEmerald else AccentCoral
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
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
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Actuel: ${PayCycleEngine.formatDh(curSpent)} $cur · Préc.: ${PayCycleEngine.formatDh(prevSpent)} $cur",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 11.5.sp,
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

        // 3. "What-If" Savings Simulator Card
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = AccentSkyBlue
                        )
                        Column {
                            Text(
                                text = strings.whatIfTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentSkyBlue
                            )
                            Text(
                                text = strings.whatIfSub,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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

                    Spacer(modifier = Modifier.height(8.dp))

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

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.whatIfResult
                                .replace("{pct}", state.whatIfState.cutPercentage.toString())
                                .replace("{m1}", PayCycleEngine.formatDh(monthlySaved))
                                .replace("{m12}", PayCycleEngine.formatDh(yearlySaved)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }

        // 4. Seasonal & Yearly Planners (Ramadan, Eid, Rentrée, Vignette)
        item {
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = null,
                                tint = AccentAmber
                            )
                            Column {
                                Text(
                                    text = strings.seasonalTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentAmber
                                )
                                Text(
                                    text = strings.seasonalSub,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { onOpenNewSeasonal(null) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    state.seasonals.forEach { item ->
                        val prog = if (item.amount > 0) (item.secondaryAmount / item.amount).toFloat().safeFraction() else 0f
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onOpenNewSeasonal(item) }
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
                                            Text(text = item.name, style = MaterialTheme.typography.labelLarge)
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                modifier = Modifier.size(13.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "${PayCycleEngine.formatDh(item.secondaryAmount)} / ${PayCycleEngine.formatDh(item.amount)} $cur · ${item.dateOrCycle}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FilledTonalButton(
                                            onClick = { onContributeSeasonal(item) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("+ 200 $cur", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                LinearProgressIndicator(
                                    progress = { prog },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = AccentAmber,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(76.dp))
        }
    }
}

@Composable
fun BudgetAndDaretScreen(
    state: DashboardState,
    strings: AppStrings,
    onOpenCustomizeEnvelopes: () -> Unit,
    onPayDaretRound: (FinancialItem) -> Unit,
    onOpenNewDaret: (FinancialItem?) -> Unit,
    onSettleDebt: (FinancialItem) -> Unit,
    onOpenNewDebt: (FinancialItem?) -> Unit,
    onContributeGoal: (FinancialItem) -> Unit,
    onOpenNewGoal: (FinancialItem?) -> Unit,
    onOpenNewBill: (FinancialItem?) -> Unit,
    onPayBill: (FinancialItem) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("budget_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Category Donut Chart + Customizable Envelopes
        item {
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
                                text = strings.envelopesTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = strings.envelopesSub,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = onOpenCustomizeEnvelopes,
                            modifier = Modifier.testTag("btn_customize_envelopes")
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = strings.editEnvelopesBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Interactive Category Donut Chart
                    CategoryDonutChart(
                        envelopes = state.envelopes,
                        totalSpent = state.cycleExpenses,
                        lang = state.profile.language,
                        currencySymbol = cur
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    state.envelopes.forEach { envStatus ->
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .clickable { onOpenCustomizeEnvelopes() }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = envStatus.config.localizedName(state.profile.language),
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (envStatus.config.hasCap) {
                                            "${PayCycleEngine.formatDh(envStatus.totalAvailable)} $cur (${envStatus.config.percentage.safeRoundToInt()}%)"
                                        } else {
                                            strings.noCap
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (envStatus.rolloverAmount > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = strings.rolloverBadge.replace("{amt}", PayCycleEngine.formatDh(envStatus.rolloverAmount)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentAmber
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${strings.expenses}: ${PayCycleEngine.formatDh(envStatus.spent)} $cur",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${envStatus.usagePercent}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (envStatus.isOver90Percent) AccentCoral else MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LinearProgressIndicator(
                                    progress = { (envStatus.usagePercent / 100f).safeFraction() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(CircleShape),
                                    color = if (envStatus.isOver90Percent) AccentCoral else AccentEmerald,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Moroccan Daret Tracker Card
        item {
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Groups, contentDescription = null, tint = AccentViolet)
                            Column {
                                Text(
                                    text = strings.daretTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentViolet
                                )
                                Text(
                                    text = strings.daretSub,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FilledTonalButton(onClick = { onOpenNewDaret(null) }) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    state.darets.forEach { daret ->
                        val totalMembers = daret.secondaryAmount.safeRoundToInt().coerceAtLeast(1)
                        val totalPot = daret.amount * totalMembers
                        val progress = (daret.extraInt.toFloat() / totalMembers.toFloat()).safeFraction()
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onOpenNewDaret(daret) }
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
                                                .replace("{pot}", PayCycleEngine.formatDh(totalPot)),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${PayCycleEngine.formatDh(daret.amount)} $cur/mois · Payé: ${daret.extraInt}/$totalMembers · Réception: ${daret.dateOrCycle}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 11.5.sp,
                                            color = AccentViolet
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { onPayDaretRound(daret) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = strings.daretPayRound, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { onDeleteItem(daret.id) }) {
                                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete")
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = AccentViolet,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Debts & Loans (Crédits & Dettes)
        item {
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Handshake, contentDescription = null, tint = AccentSkyBlue)
                            Text(
                                text = strings.debtsTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentSkyBlue
                            )
                        }
                        FilledTonalButton(onClick = { onOpenNewDebt(null) }) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    state.debts.forEach { debt ->
                        val isLent = debt.dateOrCycle != "borrowed"
                        val remDebt = max(0.0, debt.amount - debt.secondaryAmount)
                        val prog = if (debt.amount > 0) (debt.secondaryAmount / debt.amount).toFloat().safeFraction() else 0f
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onOpenNewDebt(debt) }
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
                                        Text(
                                            text = "Reste: ${PayCycleEngine.formatDh(remDebt)} $cur / ${PayCycleEngine.formatDh(debt.amount)} $cur",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (remDebt > 0) {
                                        FilledTonalButton(
                                            onClick = { onSettleDebt(debt) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(text = strings.recordRepayment, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    IconButton(onClick = { onDeleteItem(debt.id) }) {
                                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete")
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { prog },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = if (isLent) AccentEmerald else AccentCoral,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Savings Goals & Recurring Bills Management
        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Default.Savings, contentDescription = null, tint = AccentEmerald)
                            Text(text = strings.goalsTitle, style = MaterialTheme.typography.labelSmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { onOpenNewGoal(null) }) {
                                Text("+ Objectif", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(onClick = { onOpenNewBill(null) }) {
                                Text("+ Facture", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    state.goals.forEach { goal ->
                        val prog = if (goal.amount > 0) (goal.secondaryAmount / goal.amount).toFloat().safeFraction() else 0f
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onOpenNewGoal(goal) }
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
                                            text = "${PayCycleEngine.formatDh(goal.secondaryAmount)} / ${PayCycleEngine.formatDh(goal.amount)} $cur (${(prog * 100f).safeRoundToInt()}%)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { onContributeGoal(goal) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("+ 200 $cur", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { onDeleteItem(goal.id) }) {
                                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete")
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { prog },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = AccentEmerald,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(76.dp))
        }
    }
}

@Composable
fun CategoryDonutChart(
    envelopes: List<EnvelopeStatus>,
    totalSpent: Double,
    lang: String,
    currencySymbol: String = "DH"
) {
    val palette = listOf(AccentEmerald, AccentSkyBlue, AccentAmber, AccentViolet, AccentCoral)
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
                if (totalSpent <= 0.0) {
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
                        if (env.spent > 0) {
                            val sweep = ((env.spent / totalSpent) * 360.0).toFloat()
                            if (sweep.isFinite() && sweep > 0f) {
                                drawArc(
                                    color = palette[index % palette.size],
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
                    text = PayCycleEngine.formatDh(totalSpent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
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
                val sharePct = if (totalSpent > 0) ((env.spent / totalSpent) * 100.0).safeRoundToInt() else 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(palette[index % palette.size])
                    )
                    Text(
                        text = env.config.localizedName(lang).take(20),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        text = "$sharePct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
