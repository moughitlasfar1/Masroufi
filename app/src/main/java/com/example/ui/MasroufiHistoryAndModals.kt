package com.example.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccountItem
import com.example.data.CustomizationPrefs
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import com.example.domain.AppStrings
import com.example.domain.PayCycleEngine
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.MasroufiPalettes

@Composable
fun HistoryScreen(
    state: DashboardState,
    strings: AppStrings,
    onUpdateFilter: ((FilterState) -> FilterState) -> Unit,
    onEditTransaction: (TransactionItem) -> Unit,
    onDeleteTransaction: (TransactionItem) -> Unit,
    onShareJson: () -> Unit,
    onCopyJson: () -> Unit,
    onShareCsv: () -> Unit,
    onOpenRestoreModal: () -> Unit
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("history_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search & Filter Card
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
                        Text(
                            text = strings.historyTitle,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "${state.filteredHistory.size} op.",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = state.filterState.searchQuery,
                        onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q) } },
                        placeholder = { Text(strings.searchHint) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.filterState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onUpdateFilter { it.copy(searchQuery = "") } }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_search_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category filter chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.filterState.selectedCategoryKey == "all",
                            onClick = { onUpdateFilter { it.copy(selectedCategoryKey = "all") } },
                            label = { Text(strings.filterAll) }
                        )
                        state.envelopes.forEach { env ->
                            FilterChip(
                                selected = state.filterState.selectedCategoryKey == env.config.key,
                                onClick = { onUpdateFilter { it.copy(selectedCategoryKey = env.config.key) } },
                                label = {
                                    Text(env.config.localizedName(state.profile.language).take(18))
                                }
                            )
                        }
                    }

                    // Account filter chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.filterState.selectedAccountId == "all",
                            onClick = { onUpdateFilter { it.copy(selectedAccountId = "all") } },
                            label = { Text("Tous comptes") }
                        )
                        state.accounts.forEach { acc ->
                            FilterChip(
                                selected = state.filterState.selectedAccountId == acc.account.id,
                                onClick = { onUpdateFilter { it.copy(selectedAccountId = acc.account.id) } },
                                label = { Text(acc.account.name.take(16)) }
                            )
                        }
                    }
                }
            }
        }

        // Transactions List (Tap to Edit, Delete button with Undo Snackbar)
        items(state.filteredHistory, key = { it.id }) { tx ->
            val accountName = state.accounts.find { it.account.id == tx.accountId }?.account?.name ?: "Cash"
            val amountColor = when (tx.type) {
                "income" -> AccentEmerald
                "transfer" -> AccentSkyBlue
                else -> AccentCoral
            }
            val prefix = when (tx.type) {
                "income" -> "+"
                "transfer" -> "⇄ "
                else -> "-"
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onEditTransaction(tx) }
                    .testTag("tx_row_${tx.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = tx.desc.ifBlank { tx.category },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = tx.category.take(22),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = accountName.take(14),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = tx.isoDate,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$prefix${PayCycleEngine.formatDh(tx.amount)} $cur",
                            style = MaterialTheme.typography.titleMedium,
                            color = amountColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                        IconButton(
                            onClick = { onDeleteTransaction(tx) },
                            modifier = Modifier.testTag("delete_tx_${tx.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = strings.deleteBtn,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Export & Backup Card
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = strings.backupTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.backupSub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onShareJson,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.btnShareJson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = onCopyJson,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.btnCopyJson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onShareCsv,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.btnShareCsv, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = onOpenRestoreModal,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.btnRestoreJson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(76.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorSheet(
    existingTx: TransactionItem?,
    initialType: String,
    state: DashboardState,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, String, String, String, String, String) -> Unit,
    onDelete: ((TransactionItem) -> Unit)? = null
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    var txType by remember { mutableStateOf(existingTx?.type ?: initialType) }
    var amountText by remember { mutableStateOf(existingTx?.amount?.let { if (it > 0) it.toString() else "" } ?: "") }
    var isoDate by remember { mutableStateOf(existingTx?.isoDate ?: PayCycleEngine.todayIso()) }
    var selectedEnvelopeKey by remember {
        mutableStateOf(
            existingTx?.envelopeKey?.ifBlank { null }
                ?: state.envelopes.firstOrNull()?.config?.key
                ?: "transport"
        )
    }
    var customCategory by remember {
        mutableStateOf(
            existingTx?.category
                ?: state.envelopes.firstOrNull()?.config?.localizedName(state.profile.language)
                ?: "Transport & Carburant"
        )
    }
    var selectedAccountId by remember {
        mutableStateOf(existingTx?.accountId ?: state.accounts.firstOrNull()?.account?.id ?: "acc_cash")
    }
    var toAccountId by remember {
        mutableStateOf(
            existingTx?.toAccountId?.ifBlank { null }
                ?: state.accounts.getOrNull(1)?.account?.id
                ?: "acc_bank"
        )
    }
    var desc by remember { mutableStateOf(existingTx?.desc ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (existingTx != null) strings.editOperationTitle else strings.addOperationTitle,
                style = MaterialTheme.typography.titleLarge
            )

            // Type Switcher (Expense, Income, Transfer)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "expense" to strings.expenseTab,
                    "income" to strings.incomeTab,
                    "transfer" to strings.transferTab
                ).forEach { (typeKey, label) ->
                    FilterChip(
                        selected = txType == typeKey,
                        onClick = { txType = typeKey },
                        label = { Text(label, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("${strings.amountLabel} ($cur)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_amount_input")
            )

            OutlinedTextField(
                value = isoDate,
                onValueChange = { isoDate = it },
                label = { Text("Date (YYYY-MM-DD)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Source Account selector
            Text(
                text = if (txType == "transfer") "Compte source :" else "Compte :",
                style = MaterialTheme.typography.labelLarge
            )
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
                        label = { Text(acc.account.name) }
                    )
                }
            }

            if (txType == "transfer") {
                Text(
                    text = "Vers le compte :",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.accounts.forEach { acc ->
                        FilterChip(
                            selected = toAccountId == acc.account.id,
                            onClick = { toAccountId = acc.account.id },
                            label = { Text(acc.account.name) }
                        )
                    }
                }
            } else if (txType == "expense") {
                Text(
                    text = "Enveloppe / Catégorie :",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.envelopes.forEach { env ->
                        FilterChip(
                            selected = selectedEnvelopeKey == env.config.key,
                            onClick = {
                                selectedEnvelopeKey = env.config.key
                                customCategory = env.config.localizedName(state.profile.language)
                            },
                            label = { Text(env.config.localizedName(state.profile.language).take(22)) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = customCategory,
                onValueChange = { customCategory = it },
                label = { Text("Nom de la catégorie") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("Description / Note") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_desc_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (existingTx != null && onDelete != null) {
                    FilledTonalButton(
                        onClick = {
                            onDelete(existingTx)
                            onDismiss()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(strings.deleteBtn, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        val amt = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            onSave(
                                existingTx?.id ?: "",
                                txType,
                                amt,
                                if (txType == "transfer") "Transfert" else customCategory,
                                if (txType == "transfer") "autre" else selectedEnvelopeKey,
                                selectedAccountId,
                                toAccountId,
                                desc.ifBlank { if (txType == "transfer") "Transfert entre comptes" else customCategory },
                                isoDate
                            )
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .weight(2f)
                        .height(52.dp)
                        .testTag("tx_save_button")
                ) {
                    Text(strings.saveBtn, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    state: DashboardState,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSaveSettings: (Double, Int, Boolean) -> Unit,
    onOpenCustomizationStudio: () -> Unit,
    onSignOut: () -> Unit
) {
    var budgetText by remember { mutableStateOf(state.profile.baseBudget.safeRoundToInt().toString()) }
    var payDayText by remember { mutableStateOf(state.profile.payCycleDay.toString()) }
    var rolloverEnabled by remember { mutableStateOf(state.profile.rolloverEnabled) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(text = strings.settingsTitle, style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = budgetText,
                onValueChange = { budgetText = it },
                label = { Text(strings.salaryLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = payDayText,
                onValueChange = { payDayText = it },
                label = { Text(strings.payCycleDayLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.rolloverToggleLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = rolloverEnabled,
                    onCheckedChange = { rolloverEnabled = it }
                )
            }

            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onOpenCustomizationStudio()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Studio de Personnalisation (Thèmes, Cartes, Devise)", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    val b = budgetText.replace(",", ".").toDoubleOrNull() ?: 6400.0
                    val d = (payDayText.toIntOrNull() ?: 1).coerceIn(1, 28)
                    onSaveSettings(b, d, rolloverEnabled)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(strings.saveBtn, fontWeight = FontWeight.ExtraBold)
            }

            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onSignOut()
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(strings.signOutLabel, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationStudioSheet(
    state: DashboardState,
    onDismiss: () -> Unit,
    onSaveCustomPrefs: (CustomizationPrefs) -> Unit
) {
    var selectedPalette by remember { mutableStateOf(state.customPrefs.accentPalette) }
    var currencySymbol by remember { mutableStateOf(state.customPrefs.currencySymbol.ifBlank { "DH" }) }
    var customTitle by remember { mutableStateOf(state.customPrefs.customTitle) }
    var customSubtitle by remember { mutableStateOf(state.customPrefs.customSubtitle) }
    var showSafeToSpend by remember { mutableStateOf(state.customPrefs.showSafeToSpend) }
    var showAccountsCard by remember { mutableStateOf(state.customPrefs.showAccountsCard) }
    var showQuickPresetsCard by remember { mutableStateOf(state.customPrefs.showQuickPresetsCard) }
    var showWeekSummaryRow by remember { mutableStateOf(state.customPrefs.showWeekSummaryRow) }
    var showMonthlyReviewCard by remember { mutableStateOf(state.customPrefs.showMonthlyReviewCard) }
    var showBillsCard by remember { mutableStateOf(state.customPrefs.showBillsCard) }

    val lang = state.profile.language

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = when (lang) {
                        "darija" -> "تخصيص التطبيق والواجهة (Gostimazation)"
                        "ar" -> "تخصيص المظهر والواجهة"
                        "en" -> "App & UI Customization Studio"
                        else -> "Studio de Personnalisation"
                    },
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // 1. Accent Color Palette Picker
            Text(
                text = when (lang) {
                    "darija" -> "1. ستيل والألوان (Palette Maroc)"
                    "ar" -> "1. لوحة الألوان"
                    "en" -> "1. Moroccan Accent Theme"
                    else -> "1. Palette de Couleurs & Thème"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MasroufiPalettes.forEach { pal ->
                    val isSelected = selectedPalette == pal.key
                    val label = pal.labelFr
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .width(142.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) pal.primaryDark else Color.Transparent,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { selectedPalette = pal.key }
                            .testTag("palette_option_${pal.key}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.linearGradient(listOf(pal.heroStart, pal.heroEnd))),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 2. Currency Symbol
            Text(
                text = when (lang) {
                    "darija" -> "2. رمز العملة (Devise)"
                    "ar" -> "2. رمز العملة"
                    "en" -> "2. Currency Symbol"
                    else -> "2. Symbole de Devise"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("DH", "MAD", "د.م.", "€", "$").forEach { sym ->
                    FilterChip(
                        selected = currencySymbol == sym,
                        onClick = { currencySymbol = sym },
                        label = { Text(sym, fontWeight = FontWeight.ExtraBold) }
                    )
                }
            }

            // 3. Custom App Title & Subtitle
            Text(
                text = when (lang) {
                    "darija" -> "3. سمية التطبيق والعنوان الفرعي"
                    "ar" -> "3. اسم التطبيق والعنوان الفرعي"
                    "en" -> "3. Custom App Header & Tagline"
                    else -> "3. Titre & Sous-titre personnalisés"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = customTitle,
                onValueChange = { customTitle = it.take(40) },
                label = { Text("Nom personnalisé (ex: Masroufi Pro, Budget Dari)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = customSubtitle,
                onValueChange = { customSubtitle = it.take(80) },
                label = { Text("Sous-titre personnalisé (optionnel)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // 4. Home Dashboard Layout Visibility Toggles
            Text(
                text = when (lang) {
                    "darija" -> "4. الكارطات اللي يبانو فـ الصفحة الرئيسية"
                    "ar" -> "4. البطاقات المعروضة في الصفحة الرئيسية"
                    "en" -> "4. Visible Home Screen Cards"
                    else -> "4. Sections visibles sur l'écran d'accueil"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            val toggles = listOf(
                Triple("Afficher Safe-to-Spend / Rythme par jour", showSafeToSpend) { v: Boolean -> showSafeToSpend = v },
                Triple("Afficher Mes Comptes (Cash, Banque, Wallet)", showAccountsCard) { v: Boolean -> showAccountsCard = v },
                Triple("Afficher les Achats Rapides (1-Tap)", showQuickPresetsCard) { v: Boolean -> showQuickPresetsCard = v },
                Triple("Afficher le Résumé de la Semaine", showWeekSummaryRow) { v: Boolean -> showWeekSummaryRow = v },
                Triple("Afficher le Bilan Mensuel automatique", showMonthlyReviewCard) { v: Boolean -> showMonthlyReviewCard = v },
                Triple("Afficher les Factures & Abonnements", showBillsCard) { v: Boolean -> showBillsCard = v }
            )

            toggles.forEach { (title, checked, onChange) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = checked,
                        onCheckedChange = onChange
                    )
                }
            }

            Button(
                onClick = {
                    onSaveCustomPrefs(
                        CustomizationPrefs(
                            accentPalette = selectedPalette,
                            currencySymbol = currencySymbol.ifBlank { "DH" },
                            customTitle = customTitle.trim(),
                            customSubtitle = customSubtitle.trim(),
                            showSafeToSpend = showSafeToSpend,
                            showAccountsCard = showAccountsCard,
                            showQuickPresetsCard = showQuickPresetsCard,
                            showWeekSummaryRow = showWeekSummaryRow,
                            showMonthlyReviewCard = showMonthlyReviewCard,
                            showBillsCard = showBillsCard
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_customization_btn")
            ) {
                Text("Appliquer la personnalisation", fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeEnvelopesSheet(
    state: DashboardState,
    onDismiss: () -> Unit,
    onSaveEnvelope: (EnvelopeConfig) -> Unit,
    onDeleteEnvelope: (String) -> Unit,
    onAutoBalance100: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var newPct by remember { mutableStateOf("10") }
    val totalPct = state.envelopes.filter { it.config.hasCap }.sumOf { it.config.percentage }.safeRoundToInt()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Personnaliser les Enveloppes",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Total alloué : $totalPct% (Recommandé : 100%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (totalPct == 100) AccentEmerald else AccentCoral
                    )
                }
                FilledTonalButton(
                    onClick = onAutoBalance100,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("100%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            state.envelopes.forEach { envStatus ->
                var nameInput by remember(envStatus.config.id, state.profile.language) {
                    mutableStateOf(envStatus.config.localizedName(state.profile.language))
                }
                var pctInput by remember(envStatus.config.id, envStatus.config.percentage) {
                    mutableStateOf(envStatus.config.percentage.safeRoundToInt().toString())
                }
                var hasCapToggle by remember(envStatus.config.id, envStatus.config.hasCap) {
                    mutableStateOf(envStatus.config.hasCap)
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Nom") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (hasCapToggle) {
                                OutlinedTextField(
                                    value = pctInput,
                                    onValueChange = { pctInput = it },
                                    label = { Text("%") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.width(78.dp)
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    val cleanName = nameInput.trim().ifBlank { envStatus.config.nameFr }
                                    val p = if (hasCapToggle) {
                                        (pctInput.replace(",", ".").toDoubleOrNull() ?: envStatus.config.percentage).coerceIn(0.0, 100.0)
                                    } else 0.0
                                    onSaveEnvelope(
                                        envStatus.config.copy(
                                            nameFr = cleanName,
                                            nameAr = cleanName,
                                            nameDarija = cleanName,
                                            nameEn = cleanName,
                                            percentage = p,
                                            hasCap = hasCapToggle
                                        )
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Switch(
                                    checked = hasCapToggle,
                                    onCheckedChange = { checked ->
                                        hasCapToggle = checked
                                        val p = if (checked) {
                                            (pctInput.replace(",", ".").toDoubleOrNull() ?: 10.0).coerceIn(1.0, 100.0)
                                        } else 0.0
                                        onSaveEnvelope(
                                            envStatus.config.copy(
                                                hasCap = checked,
                                                percentage = p
                                            )
                                        )
                                    }
                                )
                                Text(
                                    text = if (hasCapToggle) "Plafond actif" else "Sans plafond",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (state.envelopes.size > 1) {
                                IconButton(
                                    onClick = { onDeleteEnvelope(envStatus.config.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Supprimer enveloppe",
                                        tint = AccentCoral,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Ajouter une catégorie personnalisée :", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nom (ex: Sport, Enfants)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = newPct,
                    onValueChange = { newPct = it },
                    label = { Text("%") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(80.dp)
                )
                Button(
                    onClick = {
                        val n = newName.trim()
                        val p = newPct.replace(",", ".").toDoubleOrNull() ?: 10.0
                        if (n.isNotEmpty()) {
                            val key = n.lowercase().replace(Regex("[^a-z0-9]"), "_").take(20)
                            onSaveEnvelope(
                                EnvelopeConfig(
                                    id = "env_${System.currentTimeMillis()}",
                                    key = key.ifBlank { "custom" },
                                    nameFr = n,
                                    nameAr = n,
                                    nameDarija = n,
                                    nameEn = n,
                                    percentage = p.coerceIn(0.0, 100.0),
                                    hasCap = true,
                                    colorHex = "#10B981"
                                )
                            )
                            newName = ""
                        }
                    }
                ) {
                    Text("+")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFinancialItemSheet(
    itemType: String, // "goal", "bill", "debt", "daret", "seasonal", "preset", "account"
    existingItem: FinancialItem? = null,
    existingAccount: AccountItem? = null,
    state: DashboardState,
    onDismiss: () -> Unit,
    onSaveItem: (FinancialItem) -> Unit,
    onSaveAccount: (String, String, String, Double, String) -> Unit,
    onDeleteAccount: ((String) -> Unit)? = null,
    onDeleteItem: ((String) -> Unit)? = null
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    var name by remember {
        mutableStateOf(existingAccount?.name ?: existingItem?.name ?: "")
    }
    var amountText by remember {
        mutableStateOf(
            when {
                existingAccount != null -> existingAccount.initialBalance.toString()
                existingItem != null -> existingItem.amount.toString()
                else -> ""
            }
        )
    }
    var secondaryText by remember {
        mutableStateOf(existingItem?.secondaryAmount?.let { if (it > 0) it.toString() else "10" } ?: "10")
    }
    var dayOrTurnText by remember {
        mutableStateOf((existingItem?.dayOrTurn ?: 1).toString())
    }
    var dateOrDirText by remember {
        mutableStateOf(existingItem?.dateOrCycle?.ifBlank { "2027-01-01" } ?: "2027-01-01")
    }

    // Account customization fields
    var selectedAccountKind by remember {
        mutableStateOf(existingAccount?.kind ?: "bank")
    }
    var selectedAccountColor by remember {
        mutableStateOf(existingAccount?.colorHex ?: "#38BDF8")
    }

    // Preset customization fields
    var selectedPresetEnvelopeKey by remember {
        mutableStateOf(
            existingItem?.subtitle?.ifBlank { null }
                ?: state.envelopes.firstOrNull()?.config?.key
                ?: "cafe"
        )
    }
    var selectedPresetAccountId by remember {
        mutableStateOf(
            existingItem?.dateOrCycle?.ifBlank { null }
                ?: state.accounts.firstOrNull()?.account?.id
                ?: "acc_cash"
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isEditing = existingAccount != null || existingItem != null
            val title = when (itemType) {
                "account" -> if (isEditing) "Modifier le Compte" else "Nouveau Compte (Cash / Banque / Wallet)"
                "daret" -> if (isEditing) "Modifier la Daret" else "Nouvelle Daret (Tontine)"
                "debt" -> if (isEditing) "Modifier Crédit / Dette" else "Nouveau Crédit / Dette"
                "seasonal" -> if (isEditing) "Modifier Planificateur Saisonnier" else "Planificateur Saisonnier"
                "bill" -> if (isEditing) "Modifier Facture Récurrente" else "Nouvelle Facture Récurrente"
                "preset" -> if (isEditing) "Modifier Achat Rapide (1-Tap)" else "Nouveau Bouton d'Achat Rapide"
                else -> if (isEditing) "Modifier Objectif d'Épargne" else "Nouvel Objectif d'Épargne"
            }
            Text(text = title, style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = {
                    Text(
                        when (itemType) {
                            "account" -> "Solde initial ($cur)"
                            "daret" -> "Cotisation mensuelle ($cur)"
                            else -> "Montant ($cur)"
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (itemType == "account") {
                Text(text = "Type de compte :", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "cash" to "Cash (Espèces)",
                        "bank" to "Banque",
                        "wallet" to "Wallet Mobile"
                    ).forEach { (kindKey, label) ->
                        FilterChip(
                            selected = selectedAccountKind == kindKey,
                            onClick = {
                                selectedAccountKind = kindKey
                                selectedAccountColor = when (kindKey) {
                                    "cash" -> "#10B981"
                                    "wallet" -> "#F59E0B"
                                    else -> "#38BDF8"
                                }
                            },
                            label = { Text(label, fontSize = 11.5.sp) }
                        )
                    }
                }
            } else if (itemType == "preset") {
                Text(text = "Catégorie / Enveloppe associée :", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.envelopes.forEach { env ->
                        FilterChip(
                            selected = selectedPresetEnvelopeKey == env.config.key,
                            onClick = { selectedPresetEnvelopeKey = env.config.key },
                            label = { Text(env.config.localizedName(state.profile.language).take(20)) }
                        )
                    }
                }
                Text(text = "Compte débité par défaut :", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.accounts.forEach { acc ->
                        FilterChip(
                            selected = selectedPresetAccountId == acc.account.id,
                            onClick = { selectedPresetAccountId = acc.account.id },
                            label = { Text(acc.account.name) }
                        )
                    }
                }
            } else if (itemType == "daret") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = secondaryText,
                        onValueChange = { secondaryText = it },
                        label = { Text("Nb membres") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dayOrTurnText,
                        onValueChange = { dayOrTurnText = it },
                        label = { Text("Votre tour n°") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (itemType == "bill") {
                OutlinedTextField(
                    value = dayOrTurnText,
                    onValueChange = { dayOrTurnText = it },
                    label = { Text("Jour d'échéance du mois (1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (itemType == "debt") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dateOrDirText != "borrowed",
                        onClick = { dateOrDirText = "lent" },
                        label = { Text("On me doit (Crédit)") }
                    )
                    FilterChip(
                        selected = dateOrDirText == "borrowed",
                        onClick = { dateOrDirText = "borrowed" },
                        label = { Text("Je dois (Dette)") }
                    )
                }
            } else if (itemType == "seasonal") {
                OutlinedTextField(
                    value = dateOrDirText,
                    onValueChange = { dateOrDirText = it },
                    label = { Text("Date cible (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (existingAccount != null && onDeleteAccount != null && state.accounts.size > 1) {
                    FilledTonalButton(
                        onClick = {
                            onDeleteAccount(existingAccount.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Supprimer", fontWeight = FontWeight.Bold)
                    }
                } else if (existingItem != null && onDeleteItem != null) {
                    FilledTonalButton(
                        onClick = {
                            onDeleteItem(existingItem.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Supprimer", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        val n = name.trim()
                        val amt = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
                        if (n.isNotEmpty() && (amt > 0.0 || itemType == "account")) {
                            if (itemType == "account") {
                                onSaveAccount(
                                    existingAccount?.id ?: "",
                                    n,
                                    selectedAccountKind,
                                    amt.coerceAtLeast(0.0),
                                    selectedAccountColor
                                )
                            } else {
                                onSaveItem(
                                    FinancialItem(
                                        id = existingItem?.id ?: "",
                                        itemType = itemType,
                                        name = n,
                                        subtitle = if (itemType == "preset") selectedPresetEnvelopeKey else (existingItem?.subtitle?.ifBlank { n } ?: n),
                                        amount = amt.coerceAtLeast(0.01),
                                        secondaryAmount = when (itemType) {
                                            "daret" -> (secondaryText.replace(",", ".").toDoubleOrNull() ?: 10.0)
                                            else -> existingItem?.secondaryAmount ?: 0.0
                                        },
                                        dayOrTurn = (dayOrTurnText.toIntOrNull() ?: 1).coerceIn(1, 365),
                                        extraInt = existingItem?.extraInt ?: 0,
                                        dateOrCycle = when (itemType) {
                                            "preset" -> selectedPresetAccountId
                                            "bill" -> existingItem?.dateOrCycle ?: ""
                                            else -> dateOrDirText
                                        }
                                    )
                                )
                            }
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .weight(2f)
                        .height(52.dp)
                ) {
                    Text("Enregistrer", fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreJsonSheet(
    strings: AppStrings,
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit
) {
    var rawJson by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = strings.btnRestoreJson, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = rawJson,
                onValueChange = { rawJson = it },
                label = { Text("Collez votre JSON Masroufi ici...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
            Button(
                onClick = {
                    if (rawJson.isNotBlank()) {
                        onRestore(rawJson)
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(strings.saveBtn, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
