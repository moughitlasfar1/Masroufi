package com.example.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.TransactionItem
import com.example.domain.AppStrings
import com.example.domain.PayCycleEngine
import com.example.ui.DashboardState
import com.example.ui.theme.ExpressiveGlassCard
import com.example.ui.theme.ExpressiveGlassSurface
import com.example.ui.theme.semanticExpenseColor
import com.example.ui.theme.semanticIncomeColor
import com.example.ui.theme.semanticTransferColor

@Composable
fun DashboardRecentTransactions(
    state: DashboardState,
    strings: AppStrings,
    recentTransactions: List<TransactionItem>,
    onEditTransaction: (TransactionItem) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onAddFirstTransaction: () -> Unit
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val hide = state.hideAmounts
    val lang = state.profile.language
    val todayIso = remember { PayCycleEngine.todayIso() }
    fun fmt(value: Double): String = PayCycleEngine.formatAmountOrMask(value, hide)

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

        if (recentTransactions.isEmpty()) {
            DashboardEmptyState(
                strings = strings,
                onAddFirstExpense = onAddFirstTransaction
            )
        } else {
            val isDark = state.profile.isDarkTheme
            val envConfigs = state.envelopes.map { it.config }
            val accMap = state.accounts.associate { it.account.id to it.account.name }
            recentTransactions.forEach { tx ->
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
                val friendlyDate = PayCycleEngine.formatFriendlyDate(tx.isoDate, lang, todayIso)
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
                                    text = "$catDisplay · $accDisplay · $friendlyDate",
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
}
