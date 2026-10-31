package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import com.example.domain.AppStrings
import com.example.domain.PayCycleEngine
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentSkyBlue

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
                            text = "$prefix${PayCycleEngine.formatDh(tx.amount)} DH",
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
                label = { Text("${strings.amountLabel} (DH)") },
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
    onSignOut: () -> Unit
) {
    var budgetText by remember { mutableStateOf(state.profile.baseBudget.toInt().toString()) }
    var payDayText by remember { mutableStateOf(state.profile.payCycleDay.toString()) }
    var rolloverEnabled by remember { mutableStateOf(state.profile.rolloverEnabled) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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

            Button(
                onClick = {
                    val b = budgetText.toDoubleOrNull() ?: 6400.0
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
fun CustomizeEnvelopesSheet(
    state: DashboardState,
    onDismiss: () -> Unit,
    onSaveEnvelope: (EnvelopeConfig) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var newPct by remember { mutableStateOf("10") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Personnaliser les Enveloppes (%)",
                style = MaterialTheme.typography.titleLarge
            )

            state.envelopes.filter { it.config.hasCap }.forEach { envStatus ->
                var pctInput by remember(envStatus.config.id) {
                    mutableStateOf(envStatus.config.percentage.toString())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = envStatus.config.localizedName(state.profile.language),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pctInput,
                        onValueChange = { pctInput = it },
                        suffix = { Text("%") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(100.dp)
                    )
                    FilledTonalButton(
                        onClick = {
                            val p = pctInput.replace(",", ".").toDoubleOrNull() ?: envStatus.config.percentage
                            onSaveEnvelope(envStatus.config.copy(percentage = p.coerceIn(0.0, 100.0)))
                        }
                    ) {
                        Text("OK")
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
                        val p = newPct.toDoubleOrNull() ?: 10.0
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
    onDismiss: () -> Unit,
    onSaveItem: (FinancialItem) -> Unit,
    onSaveAccount: (String, String, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var secondaryText by remember { mutableStateOf("10") }
    var dayOrTurnText by remember { mutableStateOf("1") }
    var dateOrDirText by remember { mutableStateOf("2027-01-01") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val title = when (itemType) {
                "account" -> "Nouveau Compte (Cash / Banque / Wallet)"
                "daret" -> "Nouvelle Daret (Tontine)"
                "debt" -> "Nouveau Crédit / Dette"
                "seasonal" -> "Planificateur Saisonnier"
                "bill" -> "Nouvelle Facture Récurrente"
                "preset" -> "Nouveau Bouton d'Achat Rapide"
                else -> "Nouvel Objectif d'Épargne"
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
                            "account" -> "Solde initial (DH)"
                            "daret" -> "Cotisation mensuelle (DH)"
                            else -> "Montant (DH)"
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (itemType == "daret") {
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
            }

            Button(
                onClick = {
                    val n = name.trim()
                    val amt = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (n.isNotEmpty() && amt > 0.0) {
                        if (itemType == "account") {
                            onSaveAccount(n, "bank", amt)
                        } else {
                            onSaveItem(
                                FinancialItem(
                                    itemType = itemType,
                                    name = n,
                                    subtitle = if (itemType == "preset") "cafe" else n,
                                    amount = amt,
                                    secondaryAmount = if (itemType == "daret") (secondaryText.toDoubleOrNull() ?: 10.0) else 0.0,
                                    dayOrTurn = (dayOrTurnText.toIntOrNull() ?: 1).coerceIn(1, 365),
                                    extraInt = 0,
                                    dateOrCycle = if (itemType == "preset") "acc_cash" else if (itemType == "bill") "" else dateOrDirText
                                )
                            )
                        }
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Enregistrer", fontWeight = FontWeight.ExtraBold)
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
