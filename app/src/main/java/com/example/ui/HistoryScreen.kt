package com.example.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DefaultCategoryResolver
import com.example.data.EnvelopeConfig
import com.example.data.TransactionItem
import com.example.domain.AppStrings
import com.example.domain.PayCycleEngine
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.ExpressiveGlassCard
import com.example.ui.theme.ExpressiveGlassSurface
import com.example.ui.theme.distinctEnvelopeColor
import com.example.ui.theme.semanticExpenseColor
import com.example.ui.theme.semanticIncomeColor
import com.example.ui.theme.semanticTransferColor
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    state: DashboardState,
    strings: AppStrings,
    onUpdateFilter: ((FilterState) -> FilterState) -> Unit,
    onEditTransaction: (TransactionItem) -> Unit,
    onDeleteTransaction: (TransactionItem) -> Unit,
    onDuplicateTransaction: (TransactionItem) -> Unit = {},
    onShareJson: (String) -> Unit = {},
    onCopyJson: (String) -> Unit = {},
    onShareCsv: () -> Unit = {},
    onOpenRestoreModal: () -> Unit = {},
    onExportJsonFile: (String) -> Unit = {},
    onExportCsvFile: () -> Unit = {},
    onImportJsonFile: () -> Unit = {},
    onClearClipboard: () -> Unit = {},
    hasRollbackSnapshot: Boolean = false,
    onRollbackRestore: () -> Unit = {},
    onAddFirstTransaction: () -> Unit = {}
) {
    val context = LocalContext.current
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val hide = state.hideAmounts
    val lang = state.profile.language

    val uiPrefs = remember(context) {
        context.getSharedPreferences("masroufi_ui_prefs", Context.MODE_PRIVATE)
    }
    var swipeHintDismissed by remember {
        mutableStateOf(uiPrefs.getBoolean("swipe_hint_dismissed", false))
    }
    fun dismissSwipeHint() {
        if (!swipeHintDismissed) {
            swipeHintDismissed = true
            uiPrefs.edit().putBoolean("swipe_hint_dismissed", true).apply()
        }
    }

    val hasActiveFilter = state.filterState.searchQuery.isNotBlank() ||
        state.filterState.selectedCategoryKey != "all" ||
        state.filterState.selectedAccountId != "all"
    var searchExpanded by remember { mutableStateOf(hasActiveFilter) }

    val todayIso = remember { PayCycleEngine.todayIso() }
    val groupedByDay = remember(state.filteredHistory) {
        state.filteredHistory
            .groupBy { it.isoDate.ifBlank { todayIso } }
            .entries
            .sortedByDescending { it.key }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("history_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "history_top_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.historyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = PayCycleEngine.formatCycleRangeFriendly(
                                state.activeWindow.startIso,
                                state.activeWindow.endIso,
                                lang
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (searchExpanded || hasActiveFilter) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            border = BorderStroke(
                                1.dp,
                                if (searchExpanded || hasActiveFilter) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { searchExpanded = !searchExpanded }
                                .testTag("history_search_toggle_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (hasActiveFilter) Icons.Default.FilterList else Icons.Default.Search,
                                    contentDescription = strings.searchHint,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${state.filteredHistory.size} op.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = searchExpanded) {
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(22.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("history_thumb_filter_bar")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.filterState.searchQuery,
                                onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q) } },
                                placeholder = { Text(strings.searchHint, fontSize = 12.sp, maxLines = 1) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (state.filterState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onUpdateFilter { it.copy(searchQuery = "") } }) {
                                            Icon(Icons.Default.Close, contentDescription = strings.closeDesc, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("history_search_input")
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = state.filterState.selectedCategoryKey == "all",
                                    onClick = { onUpdateFilter { it.copy(selectedCategoryKey = "all") } },
                                    label = { Text(strings.filterAll, fontSize = 11.5.sp) }
                                )
                                state.envelopes.forEach { env ->
                                    FilterChip(
                                        selected = state.filterState.selectedCategoryKey == env.config.key,
                                        onClick = { onUpdateFilter { it.copy(selectedCategoryKey = env.config.key) } },
                                        label = {
                                            Text(env.config.localizedName(state.profile.language), fontSize = 11.5.sp)
                                        }
                                    )
                                }
                                FilterChip(
                                    selected = state.filterState.selectedAccountId == "all",
                                    onClick = { onUpdateFilter { it.copy(selectedAccountId = "all") } },
                                    label = { Text(strings.allAccountsLabel, fontSize = 11.5.sp) }
                                )
                                state.accounts.forEach { acc ->
                                    FilterChip(
                                        selected = state.filterState.selectedAccountId == acc.account.id,
                                        onClick = { onUpdateFilter { it.copy(selectedAccountId = acc.account.id) } },
                                        label = { Text(acc.account.name.take(14), fontSize = 11.5.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.filteredHistory.isNotEmpty() && !swipeHintDismissed) {
                item(key = "swipe_hint_pill") {
                    ExpressiveGlassSurface(
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { dismissSwipeHint() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_swipe_hint")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Swipe,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when (lang) {
                                    "ar" -> "اسحب يميناً للتكرار · يساراً للحذف · اضغط للتعديل"
                                    "darija" -> "جر لليمين باش تكرر · لليسار باش تمسح · ورك باش تبدل"
                                    "en" -> "Swipe right to duplicate · left to delete · tap to edit"
                                    else -> "Glissez à droite pour dupliquer · à gauche pour supprimer"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.closeDesc,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            } else if (state.filteredHistory.isEmpty() && state.upcomingTransactions.isEmpty()) {
                item(key = "empty_history_state") {
                    ExpressiveGlassCard(
                        cornerRadius = 26.dp,
                        accentRimColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_empty_state_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = strings.emptyHomeTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.emptyHomeSub,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onAddFirstTransaction,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.testTag("empty_history_add_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.btnAddAction, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (state.upcomingTransactions.isNotEmpty()) {
                val upcomingExpenses = state.upcomingTransactions.filter { it.type == "expense" }.sumOf { it.amount }
                val isDark = state.profile.isDarkTheme
                val expColor = semanticExpenseColor(isDark)
                item(key = "upcoming_header") {
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        accentBorderColor = AccentAmber,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_upcoming_header")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.upcomingGroupTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentAmber
                            )
                            Text(
                                text = if (hide) "•••• $cur" else "-${PayCycleEngine.formatDh(upcomingExpenses)} $cur",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = expColor,
                                modifier = Modifier.testTag("history_upcoming_total")
                            )
                        }
                    }
                }
                items(state.upcomingTransactions, key = { "upcoming_${it.id}" }) { tx ->
                    val accountName = state.accounts.find { it.account.id == tx.accountId }?.account?.name ?: "Cash"
                    SwipeableTransactionRow(
                        tx = tx,
                        accountName = accountName,
                        currencySymbol = cur,
                        hideAmounts = hide,
                        lang = lang,
                        todayIso = todayIso,
                        envelopes = state.envelopes.map { it.config },
                        isDarkTheme = state.profile.isDarkTheme,
                        deleteLabel = strings.deleteBtn,
                        duplicateLabel = when (lang) {
                            "ar" -> "تكرار"
                            "darija" -> "عاودها"
                            "en" -> "Duplicate"
                            else -> "Dupliquer"
                        },
                        onEditTransaction = { onEditTransaction(tx) },
                        onDuplicateTransaction = {
                            dismissSwipeHint()
                            onDuplicateTransaction(tx)
                        },
                        onDeleteTransaction = {
                            dismissSwipeHint()
                            onDeleteTransaction(tx)
                        }
                    )
                }
            }

            groupedByDay.forEach { (dayIso, dayTxs) ->
                val dayExpenses = dayTxs.filter { it.type == "expense" }.sumOf { it.amount }
                val dayIncomes = dayTxs.filter { it.type == "income" }.sumOf { it.amount }
                val dayNet = dayIncomes - dayExpenses
                val isDark = state.profile.isDarkTheme
                val incColor = semanticIncomeColor(isDark)
                val expColor = semanticExpenseColor(isDark)
                item(key = "day_header_$dayIso") {
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        accentBorderColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_day_header_$dayIso")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = PayCycleEngine.formatFriendlyDate(dayIso, lang, todayIso),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val totalLabel = if (hide) {
                                "•••• $cur"
                            } else if (dayExpenses > 0 && dayIncomes == 0.0) {
                                "-${PayCycleEngine.formatDh(dayExpenses)} $cur"
                            } else if (dayNet >= 0) {
                                "+${PayCycleEngine.formatDh(dayNet)} $cur"
                            } else {
                                "-${PayCycleEngine.formatDh(abs(dayNet))} $cur"
                            }
                            Text(
                                text = totalLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (dayNet > 0 && dayExpenses == 0.0) incColor else expColor,
                                modifier = Modifier.testTag("history_day_total_$dayIso")
                            )
                        }
                    }
                }

                items(dayTxs, key = { it.id }) { tx ->
                    val accountName = state.accounts.find { it.account.id == tx.accountId }?.account?.name ?: "Cash"
                    SwipeableTransactionRow(
                        tx = tx,
                        accountName = accountName,
                        currencySymbol = cur,
                        hideAmounts = hide,
                        lang = lang,
                        todayIso = todayIso,
                        envelopes = state.envelopes.map { it.config },
                        isDarkTheme = state.profile.isDarkTheme,
                        deleteLabel = strings.deleteBtn,
                        duplicateLabel = when (lang) {
                            "ar" -> "تكرار"
                            "darija" -> "عاودها"
                            "en" -> "Duplicate"
                            else -> "Dupliquer"
                        },
                        onEditTransaction = { onEditTransaction(tx) },
                        onDuplicateTransaction = {
                            dismissSwipeHint()
                            onDuplicateTransaction(tx)
                        },
                        onDeleteTransaction = {
                            dismissSwipeHint()
                            onDeleteTransaction(tx)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(88.dp))
            }
        }
    }
}

@Composable
internal fun SwipeableTransactionRow(
    tx: TransactionItem,
    accountName: String,
    currencySymbol: String,
    hideAmounts: Boolean,
    lang: String = "fr",
    todayIso: String = PayCycleEngine.todayIso(),
    envelopes: List<EnvelopeConfig> = emptyList(),
    isDarkTheme: Boolean = false,
    deleteLabel: String,
    duplicateLabel: String = "Dupliquer",
    onEditTransaction: () -> Unit,
    onDuplicateTransaction: () -> Unit = {},
    onDeleteTransaction: () -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val thresholdPx = with(density) { 88.dp.toPx() }
    val maxDragPx = with(density) { 140.dp.toPx() }
    var rawOffsetX by remember(tx.id) { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = rawOffsetX, label = "txSwipeOffset")

    val resolvedCategoryDisplay = remember(tx.category, tx.envelopeKey, lang, envelopes) {
        tx.localizedCategory(lang, envelopes)
    }
    val friendlyDate = remember(tx.isoDate, lang, todayIso) {
        PayCycleEngine.formatFriendlyDate(tx.isoDate, lang, todayIso)
    }
    val amountColor = when (tx.type) {
        "income" -> semanticIncomeColor(isDarkTheme)
        "transfer" -> semanticTransferColor(isDarkTheme)
        else -> semanticExpenseColor(isDarkTheme)
    }
    val catAccent = distinctEnvelopeColor(tx.envelopeKey.ifBlank { tx.category }, isDarkTheme)
    val prefix = when (tx.type) {
        "income" -> "+"
        "transfer" -> "⇄ "
        else -> "-"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
    ) {
        val isSwipingRight = animatedOffsetX > 8f
        val isSwipingLeft = animatedOffsetX < -8f
        if (isSwipingRight || isSwipingLeft) {
            val bgColor = if (isSwipingRight) AccentEmerald.copy(alpha = 0.24f) else AccentCoral.copy(alpha = 0.24f)
            val contentColor = if (isSwipingRight) AccentEmerald else AccentCoral
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = if (isSwipingRight) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isSwipingRight) Icons.Default.ContentCopy else Icons.Default.DeleteOutline,
                        contentDescription = if (isSwipingRight) duplicateLabel else deleteLabel,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isSwipingRight) duplicateLabel else deleteLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 1.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .clip(RoundedCornerShape(20.dp))
                .pointerInput(tx.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            rawOffsetX = (rawOffsetX + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                        },
                        onDragEnd = {
                            when {
                                rawOffsetX >= thresholdPx -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    rawOffsetX = 0f
                                    onDuplicateTransaction()
                                }
                                rawOffsetX <= -thresholdPx -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    rawOffsetX = 0f
                                    onDeleteTransaction()
                                }
                                else -> {
                                    rawOffsetX = 0f
                                }
                            }
                        },
                        onDragCancel = {
                            rawOffsetX = 0f
                        }
                    )
                }
                .clickable { onEditTransaction() }
                .testTag("tx_row_${tx.id}")
        ) {
            val descDisplay = tx.desc.ifBlank { resolvedCategoryDisplay }.let { rawDesc ->
                DefaultCategoryResolver.resolveTransactionCategoryDisplay(
                    rawDesc,
                    tx.envelopeKey,
                    envelopes,
                    lang
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(catAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = resolvedCategoryDisplay.take(2).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = catAccent
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = descDisplay,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$resolvedCategoryDisplay · $accountName · $friendlyDate",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (hideAmounts) "•••• $currencySymbol" else "$prefix${PayCycleEngine.formatDh(tx.amount)} $currencySymbol",
                    style = MaterialTheme.typography.labelLarge,
                    color = amountColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
