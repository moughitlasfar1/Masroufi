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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import com.example.security.AppSecurityManager
import com.example.ui.theme.AccentAmber
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    state: DashboardState,
    strings: AppStrings,
    onUpdateFilter: ((FilterState) -> FilterState) -> Unit,
    onEditTransaction: (TransactionItem) -> Unit,
    onDeleteTransaction: (TransactionItem) -> Unit,
    onShareJson: (String) -> Unit,
    onCopyJson: (String) -> Unit,
    onShareCsv: () -> Unit,
    onOpenRestoreModal: () -> Unit,
    onExportJsonFile: (String) -> Unit = {},
    onExportCsvFile: () -> Unit = {},
    onImportJsonFile: () -> Unit = {},
    onClearClipboard: () -> Unit = {},
    hasRollbackSnapshot: Boolean = false,
    onRollbackRestore: () -> Unit = {},
    onAddFirstTransaction: () -> Unit = {}
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val hide = state.hideAmounts
    val lang = state.profile.language
    var backupPassword by remember { mutableStateOf("") }
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

        // Swipe hint pill
        if (state.filteredHistory.isNotEmpty()) {
            item(key = "swipe_hint_pill") {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                "ar" -> "اسحب يميناً للتعديل · اسحب يساراً للحذف (مع زر تراجع)"
                                "darija" -> "جر لليمين باش تبدل · جر لليسار باش تمسح (مع زر رجع)"
                                "en" -> "Swipe right to edit · Swipe left to delete (with Undo)"
                                else -> "Glissez à droite pour modifier · à gauche pour supprimer (avec Annuler)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Actionable Empty State Card
            item(key = "empty_history_state") {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_empty_state_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = when (lang) {
                                "ar" -> "لا توجد عمليات مسجلة في هذه الدورة"
                                "darija" -> "مازال ما قيدتي حتى عملية فهاد الشهر"
                                "en" -> "No transactions recorded in this cycle yet"
                                else -> "Aucune opération enregistrée sur ce cycle"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (lang) {
                                "ar" -> "سجل أول مصروف أو مدخول بضغطة واحدة لمتابعة ميزانيتك."
                                "darija" -> "زيد أول مصروف أو مدخول بضغطة وحدة باش تتبع حساباتك."
                                "en" -> "Log your first expense or income in one tap to start tracking."
                                else -> "Ajoutez votre première dépense ou entrée en un geste."
                            },
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

        // Transactions List (Swipe Right to Edit, Swipe Left to Delete + Tap to Edit + Delete Button)
        items(state.filteredHistory, key = { it.id }) { tx ->
            val accountName = state.accounts.find { it.account.id == tx.accountId }?.account?.name ?: "Cash"
            SwipeableTransactionRow(
                tx = tx,
                accountName = accountName,
                currencySymbol = cur,
                hideAmounts = hide,
                deleteLabel = strings.deleteBtn,
                editLabel = when (lang) {
                    "ar" -> "تعديل"
                    "darija" -> "بدل"
                    "en" -> "Edit"
                    else -> "Modifier"
                },
                onEditTransaction = { onEditTransaction(tx) },
                onDeleteTransaction = { onDeleteTransaction(tx) }
            )
        }

        // Export & Backup Card
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
                            text = strings.backupTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = CircleShape,
                            color = AccentEmerald.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = if (backupPassword.trim().length >= 4) "AES-256-GCM Actif" else "Format Complet v2.0",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentEmerald,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${strings.backupSub} • Inclut comptes, enveloppes, objectifs, Daret, dettes, factures & préférences.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = {
                            Text(
                                when (state.profile.language) {
                                    "ar" -> "كلمة مرور تشفير النسخة الاحتياطية AES-256-GCM (اختياري، 4 أحرف+)"
                                    "darija" -> "كلمة السر لتشفير النسخة بـ AES-256-GCM (اختياري، 4 حروف+)"
                                    "en" -> "AES-256-GCM Backup Password (optional, 4+ chars)"
                                    else -> "Mot de passe chiffrement AES-256-GCM (optionnel, 4+ car.)"
                                },
                                fontSize = 11.5.sp
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_encryption_password_input")
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (state.profile.language) {
                            "ar" -> "ملاحظة: ملف CSV غير مشفر (مخصص لـ Excel ومحمّي ضد حقن الصيغ). استخدم JSON مع كلمة مرور للتشفير بـ AES-256-GCM + PBKDF2."
                            "darija" -> "ملاحظة: ملف CSV ما مشفرش (خاص بـ Excel ومحمّي من الصيغ). استعمل JSON مع كلمة السر باش يتشفر بـ AES-256-GCM."
                            "en" -> "Security note: CSV is unencrypted plaintext for spreadsheets (formula-injection safe). Set a password above to encrypt JSON with AES-256-GCM."
                            else -> "Note : Le CSV est en texte clair pour Excel/Sheets (formules neutralisées). Entrez un mot de passe ci-dessus pour chiffrer le JSON en AES-256-GCM."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onShareJson(backupPassword) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.btnShareJson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { onCopyJson(backupPassword) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_sensitive_json_btn")
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
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_restore_modal_btn")
                        ) {
                            Text(strings.btnRestoreJson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onExportJsonFile(backupPassword) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_json_file_btn")
                        ) {
                            Text(
                                text = when (state.profile.language) {
                                    "ar" -> "حفظ ملف JSON آمن"
                                    "darija" -> "سجل ملف JSON"
                                    "en" -> "Save JSON File"
                                    else -> "Fichier JSON"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        FilledTonalButton(
                            onClick = onExportCsvFile,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_csv_file_btn")
                        ) {
                            Text(
                                text = when (state.profile.language) {
                                    "ar" -> "حفظ ملف CSV (نصي)"
                                    "darija" -> "سجل ملف CSV (نصي)"
                                    "en" -> "Save CSV (Plain)"
                                    else -> "Fichier CSV (Clair)"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onImportJsonFile,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_json_file_btn")
                        ) {
                            Text(
                                text = when (state.profile.language) {
                                    "ar" -> "استيراد ملف JSON"
                                    "darija" -> "جب ملف JSON"
                                    "en" -> "Import JSON File"
                                    else -> "Importer Fichier JSON"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        FilledTonalButton(
                            onClick = onClearClipboard,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("clear_clipboard_btn")
                        ) {
                            Text(
                                text = when (state.profile.language) {
                                    "ar" -> "مسح الحافظة فوراً"
                                    "darija" -> "مسح الكوبي (Clipboard)"
                                    "en" -> "Clear Clipboard"
                                    else -> "Vider Presse-papiers"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (hasRollbackSnapshot) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = onRollbackRestore,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = AccentCoral.copy(alpha = 0.16f),
                                contentColor = AccentCoral
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rollback_restore_btn")
                        ) {
                            Text(
                                text = when (state.profile.language) {
                                    "ar" -> "التراجع عن آخر استرجاع (Rollback إلى الحالة السابقة)"
                                    "darija" -> "رجع للحالة اللي كانت قبل الاسترجاع (Rollback)"
                                    "en" -> "Rollback Last Restore (Restore pre-import snapshot)"
                                    else -> "Annuler la dernière restauration (Rollback)"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
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

            var dateError by remember { mutableStateOf<String?>(null) }

            if (!dateError.isNullOrBlank()) {
                Text(
                    text = dateError!!,
                    color = AccentCoral,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

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
                        val normalizedDate = isoDate.trim().ifEmpty { PayCycleEngine.todayIso() }
                        if (!PayCycleEngine.isValidIsoDate(normalizedDate)) {
                            dateError = "Date invalide ('$normalizedDate'). Format requis : YYYY-MM-DD réel (ex: ${PayCycleEngine.todayIso()})."
                            return@Button
                        }
                        if (amt > 0.0) {
                            dateError = null
                            onSave(
                                existingTx?.id ?: "",
                                txType,
                                amt,
                                if (txType == "transfer") "Transfert" else customCategory,
                                if (txType == "transfer") "autre" else selectedEnvelopeKey,
                                selectedAccountId,
                                toAccountId,
                                desc.ifBlank { if (txType == "transfer") "Transfert entre comptes" else customCategory },
                                normalizedDate
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
    onLoadDemoData: () -> Unit = {},
    onResetCleanAccount: () -> Unit = {},
    onFlagSecureChanged: (Boolean) -> Unit = {},
    onAppLockChanged: (Boolean) -> Unit = {},
    onClearClipboard: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val lang = state.profile.language
    var budgetText by remember { mutableStateOf(state.profile.baseBudget.safeRoundToInt().toString()) }
    var payDayText by remember { mutableStateOf(state.profile.payCycleDay.toString()) }
    var rolloverEnabled by remember { mutableStateOf(state.profile.rolloverEnabled) }

    var flagSecureEnabled by remember { mutableStateOf(AppSecurityManager.isFlagSecureEnabled(context)) }
    var appLockEnabled by remember { mutableStateOf(AppSecurityManager.isAppLockEnabled(context)) }
    var allowDeviceCredential by remember { mutableStateOf(AppSecurityManager.isDeviceCredentialAllowed(context)) }
    var hasPinConfigured by remember { mutableStateOf(AppSecurityManager.hasPinConfigured(context)) }
    var newPinInput by remember { mutableStateOf("") }
    var pinFeedback by remember { mutableStateOf<String?>(null) }

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

            // Security & Privacy Hardening Section
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("security_privacy_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = when (lang) {
                                "ar" -> "الأمان والخصوصية (حماية البيانات المالية)"
                                "darija" -> "الأمان والخصوصية (حماية الفلوس والبيانات)"
                                "en" -> "Security & Privacy Protection"
                                else -> "Sécurité & Confidentialité"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // 1. Screenshot & Screen Recording Protection (FLAG_SECURE)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (lang) {
                                    "ar" -> "منع تصوير وتسجيل الشاشة (FLAG_SECURE)"
                                    "darija" -> "منع السكرين شوت وتصوير الشاشة (FLAG_SECURE)"
                                    "en" -> "Block Screenshots & Screen Recording"
                                    else -> "Bloquer Captures d'écran (FLAG_SECURE)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (lang) {
                                    "ar" -> "يخفي الأرقام المالية في التطبيقات الحديثة ويمنع التجسس المرئي"
                                    "darija" -> "كيخبي الأرقام فـ التطبيقات المفتوحة وكيمنع التصوير"
                                    "en" -> "Hides financial balances in recent apps and blocks screen capture"
                                    else -> "Masque l'aperçu dans les applications récentes"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = flagSecureEnabled,
                            onCheckedChange = { enabled ->
                                flagSecureEnabled = enabled
                                AppSecurityManager.setFlagSecureEnabled(context, enabled)
                                onFlagSecureChanged(enabled)
                            },
                            modifier = Modifier.testTag("settings_flag_secure_switch")
                        )
                    }

                    var clearCacheOnSignOut by remember {
                        mutableStateOf(AppSecurityManager.isClearCacheOnSignOutEnabled(context))
                    }

                    // 2. Local App Lock (PIN / Biometrics)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (lang) {
                                    "ar" -> "قفل التطبيق المحلي (PIN / بصمة الجهاز)"
                                    "darija" -> "قفل التطبيق بـ كود PIN أو بصمة التليفون"
                                    "en" -> "Local App Lock (PIN / Device Biometrics)"
                                    else -> "Verrouillage de l'application (PIN / Biométrie)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (hasPinConfigured) {
                                    when (lang) {
                                        "ar" -> "محمي باشتقاق PBKDF2-HMAC-SHA256 (65,536 تكرار + Salt + Android Keystore)"
                                        "darija" -> "محمي بـ PBKDF2-HMAC-SHA256 (65,536 تكرار + Salt + Android Keystore)"
                                        "en" -> "Protected via PBKDF2-HMAC-SHA256 key derivation (65,536 iter + salt + Keystore)"
                                        else -> "Protégé par dérivation PBKDF2-HMAC-SHA256 (65 536 itér. + sel + Keystore)"
                                    }
                                } else {
                                    when (lang) {
                                        "ar" -> "قم بتعيين رمز PIN (يُنصح بـ 6 أرقام، ويقبل 4–8 أرقام) مع قفل تدريجي"
                                        "darija" -> "دير كود PIN (يُنصح بـ 6 أرقام، وكيقبل 4–8 أرقام) لتفعيل القفل"
                                        "en" -> "Set a PIN below (6 digits recommended, 4–8 supported) with rate-limiting"
                                        else -> "Définissez un PIN ci-dessous (6 chiffres recommandés, 4–8 acceptés)"
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasPinConfigured) AccentEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !hasPinConfigured && !AppSecurityManager.isDeviceSecure(context)) {
                                    pinFeedback = when (lang) {
                                        "ar" -> "يرجى إدخال وحفظ رمز PIN (يُنصح بـ 6 أرقام) أولاً"
                                        "darija" -> "دخل بعدا كود PIN (6 أرقام منصوح بها) لتحت"
                                        "en" -> "Please set a PIN (6 digits recommended) first"
                                        else -> "Veuillez d'abord définir un code PIN (6 chiffres recommandés)"
                                    }
                                } else {
                                    appLockEnabled = enabled
                                    AppSecurityManager.setAppLockEnabled(context, enabled)
                                    onAppLockChanged(enabled)
                                }
                            },
                            modifier = Modifier.testTag("settings_app_lock_switch")
                        )
                    }

                    // PIN Configuration Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newPinInput,
                            onValueChange = { input ->
                                if (input.length <= 8 && input.all { it.isDigit() }) {
                                    newPinInput = input
                                }
                            },
                            label = {
                                Text(
                                    when (lang) {
                                        "ar" -> "رمز PIN جديد (6 أرقام موصى بها)"
                                        "darija" -> "كود PIN جديد (6 أرقام منصوح بها)"
                                        "en" -> "New PIN (6 digits recommended)"
                                        else -> "Nouveau PIN (6 chiffres recomm.)"
                                    },
                                    fontSize = 12.sp
                                )
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_pin_input")
                        )
                        FilledTonalButton(
                            onClick = {
                                if (AppSecurityManager.setPin(context, newPinInput)) {
                                    hasPinConfigured = true
                                    appLockEnabled = true
                                    AppSecurityManager.setAppLockEnabled(context, true)
                                    onAppLockChanged(true)
                                    val len = newPinInput.trim().length
                                    newPinInput = ""
                                    pinFeedback = when (lang) {
                                        "ar" -> "تم اشتقاق وحفظ رمز PIN ($len أرقام) عبر PBKDF2-HMAC-SHA256!"
                                        "darija" -> "تم تسجيل كود PIN ($len أرقام) بـ PBKDF2-HMAC-SHA256!"
                                        "en" -> "$len-digit PIN hashed via PBKDF2-HMAC-SHA256 & App Lock enabled!"
                                        else -> "PIN ($len chiffres) haché via PBKDF2-HMAC-SHA256 et activé !"
                                    }
                                } else {
                                    pinFeedback = when (lang) {
                                        "ar" -> "يجب أن يتكون الرمز من 4 إلى 8 أرقام (يُنصح بـ 6 أرقام)"
                                        "darija" -> "خاص الكود يكون ما بين 4 و 8 أرقام (منصوح بـ 6 أرقام)"
                                        "en" -> "PIN must be 4 to 8 digits (6 digits recommended)"
                                        else -> "Le PIN doit contenir 4 à 8 chiffres (6 recommandés)"
                                    }
                                }
                            },
                            modifier = Modifier.testTag("settings_save_pin_btn")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (lang) {
                                    "ar" -> "حفظ PIN"
                                    "darija" -> "سجل PIN"
                                    "en" -> "Set PIN"
                                    else -> "Définir PIN"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!pinFeedback.isNullOrBlank()) {
                        Text(
                            text = pinFeedback!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Device Credential / Biometric fallback toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (lang) {
                                "ar" -> "السماح بالفتح عبر بصمة / قفل شاشة الجهاز"
                                "darija" -> "فتح القفل ببصمة التليفون"
                                "en" -> "Allow Device Biometrics / Screen Lock"
                                else -> "Autoriser Biométrie / Verrouillage appareil"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = allowDeviceCredential,
                            onCheckedChange = { allowed ->
                                allowDeviceCredential = allowed
                                AppSecurityManager.setDeviceCredentialAllowed(context, allowed)
                            },
                            modifier = Modifier.testTag("settings_device_credential_switch")
                        )
                    }

                    // 3. Clear Local Offline Cache on Sign-Out (F-06)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (lang) {
                                    "ar" -> "مسح التخزين المحلي المؤقت (Cache) عند تسجيل الخروج"
                                    "darija" -> "مسح الكاش المحلي من التليفون ملي تخرج من الحساب"
                                    "en" -> "Wipe Local Offline Cache on Sign-Out"
                                    else -> "Effacer le cache local hors-ligne à la déconnexion"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (lang) {
                                    "ar" -> "تحذير: قد تفقد العمليات المسجلة بدون إنترنت التي لم تتم مزامنتها بعد"
                                    "darija" -> "رد بالك: العمليات اللي تقيدو بلا كونيكسيون ومازال ما تزامنوش غادي يتمسحو"
                                    "en" -> "Warning: Unsynced offline changes will be lost when signing out"
                                    else -> "Attention : les opérations hors-ligne non synchronisées seront perdues"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = clearCacheOnSignOut,
                            onCheckedChange = { enabled ->
                                clearCacheOnSignOut = enabled
                                AppSecurityManager.setClearCacheOnSignOutEnabled(context, enabled)
                            },
                            modifier = Modifier.testTag("settings_clear_cache_signout_switch")
                        )
                    }
                }
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

            // Clean Account vs Optional Demo Data Mode
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Données du compte & Mode Démo",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onLoadDemoData()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_load_demo_btn")
                        ) {
                            Text("Mode Démo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = {
                                onResetCleanAccount()
                                onDismiss()
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_reset_clean_btn")
                        ) {
                            Text("Compte Vierge (0 DH)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
    var showSmartInsightsCard by remember { mutableStateOf(state.customPrefs.showSmartInsightsCard) }
    var dashboardOrder by remember { mutableStateOf(state.customPrefs.normalizedOrder()) }

    val lang = state.profile.language

    fun isVisibleFor(key: String): Boolean = when (key) {
        "accounts" -> showAccountsCard
        "presets" -> showQuickPresetsCard
        "insights" -> showSmartInsightsCard
        "bills" -> showBillsCard
        "week_summary" -> showWeekSummaryRow
        "monthly_review" -> showMonthlyReviewCard
        else -> true
    }

    fun setVisibleFor(key: String, v: Boolean) {
        when (key) {
            "accounts" -> showAccountsCard = v
            "presets" -> showQuickPresetsCard = v
            "insights" -> showSmartInsightsCard = v
            "bills" -> showBillsCard = v
            "week_summary" -> showWeekSummaryRow = v
            "monthly_review" -> showMonthlyReviewCard = v
        }
    }

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

            // 4. Home Dashboard Layout Order & Visibility
            Text(
                text = when (lang) {
                    "darija" -> "4. ترتيب وإظهار الكارطات فـ الصفحة الرئيسية"
                    "ar" -> "4. ترتيب وإظهار البطاقات في الصفحة الرئيسية"
                    "en" -> "4. Home Screen Card Order & Visibility"
                    else -> "4. Ordre & Visibilité des cartes d'accueil"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Afficher Safe-to-Spend / Rythme par jour (dans la carte principale)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = showSafeToSpend,
                    onCheckedChange = { showSafeToSpend = it }
                )
            }

            dashboardOrder.forEachIndexed { idx, sectionKey ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("studio_order_row_$sectionKey")
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
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (idx > 0) {
                                        val next = dashboardOrder.toMutableList()
                                        val item = next.removeAt(idx)
                                        next.add(idx - 1, item)
                                        dashboardOrder = next
                                    }
                                },
                                enabled = idx > 0,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Monter")
                            }
                            IconButton(
                                onClick = {
                                    if (idx < dashboardOrder.lastIndex) {
                                        val next = dashboardOrder.toMutableList()
                                        val item = next.removeAt(idx)
                                        next.add(idx + 1, item)
                                        dashboardOrder = next
                                    }
                                },
                                enabled = idx < dashboardOrder.lastIndex,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Descendre")
                            }
                            Switch(
                                checked = isVisibleFor(sectionKey),
                                onCheckedChange = { setVisibleFor(sectionKey, it) }
                            )
                        }
                    }
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
                            showBillsCard = showBillsCard,
                            showSmartInsightsCard = showSmartInsightsCard,
                            dashboardOrder = dashboardOrder
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
            } else if (itemType == "goal") {
                Text(
                    text = "Cibles rapides :",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("5000", "10000", "20000", "50000").forEach { target ->
                        FilterChip(
                            selected = amountText == target,
                            onClick = { amountText = target },
                            label = { Text("${PayCycleEngine.formatDh(target.toDouble())} $cur") }
                        )
                    }
                }
                OutlinedTextField(
                    value = if (existingItem != null) secondaryText else secondaryText.takeIf { it != "10" }.orEmpty(),
                    onValueChange = { secondaryText = it },
                    label = { Text("Déjà épargné ($cur)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                                            "goal" -> (secondaryText.takeIf { it != "10" || existingItem != null }?.replace(",", ".")?.toDoubleOrNull() ?: existingItem?.secondaryAmount ?: 0.0)
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
    initialRawJson: String = "",
    onPreviewBackup: (String, String) -> Result<BackupImportPreview> = { _, _ -> Result.failure(Exception()) },
    onDismiss: () -> Unit,
    onRestore: (String, String, Boolean) -> Unit,
    onImportFile: () -> Unit = {}
) {
    var rawJson by remember(initialRawJson) { mutableStateOf(initialRawJson) }
    var decryptPassword by remember { mutableStateOf("") }
    var replaceExisting by remember { mutableStateOf(true) }
    var previewState by remember { mutableStateOf<BackupImportPreview?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }

    val isEncrypted = remember(rawJson) { AppSecurityManager.isEncryptedBackup(rawJson) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = strings.btnRestoreJson, style = MaterialTheme.typography.titleLarge)

            FilledTonalButton(
                onClick = {
                    onImportFile()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("restore_sheet_import_file_btn")
            ) {
                Text("📂 Choisir un fichier de sauvegarde (.json)", fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = rawJson,
                onValueChange = {
                    rawJson = it
                    previewState = null
                    previewError = null
                },
                label = { Text("Ou collez votre JSON Masroufi (clair ou chiffré AES-256-GCM)...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .testTag("restore_json_textarea")
            )

            if (isEncrypted) {
                OutlinedTextField(
                    value = decryptPassword,
                    onValueChange = {
                        decryptPassword = it
                        previewState = null
                        previewError = null
                    },
                    label = { Text("Mot de passe de déchiffrement AES-256-GCM") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore_decrypt_password_input")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remplacement complet (nettoyer les anciennes opérations)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Une capture Rollback est enregistrée automatiquement avant la restauration",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = replaceExisting,
                    onCheckedChange = { replaceExisting = it },
                    modifier = Modifier.testTag("restore_replace_existing_switch")
                )
            }

            FilledTonalButton(
                onClick = {
                    if (rawJson.isNotBlank()) {
                        val res = onPreviewBackup(rawJson, decryptPassword)
                        res.onSuccess { prev ->
                            previewState = prev
                            previewError = null
                        }.onFailure { err ->
                            previewState = null
                            previewError = err.localizedMessage ?: "Sauvegarde invalide ou mot de passe incorrect."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("restore_preview_btn")
            ) {
                Text("1. Vérifier & Prévisualiser la sauvegarde (Phase 1)", fontWeight = FontWeight.Bold)
            }

            if (!previewError.isNullOrBlank()) {
                Text(
                    text = previewError!!,
                    color = AccentCoral,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            val prev = previewState
            if (prev != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore_preview_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Aperçu vérifié (v${prev.backupVersion} • ${if (prev.isEncrypted) "Chiffré AES-256-GCM" else "JSON Clair"})",
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentEmerald,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "• Comptes : ${prev.accountsCount} | Enveloppes : ${prev.envelopesCount} | Éléments : ${prev.financialItemsCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Transactions : ${prev.transactionsCount} (${prev.totalOperationsCount} écritures au total)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (prev.isSingleAtomicBatch) {
                                "• Exécution : 1 lot Firestore atomique (< 400 opérations)"
                            } else {
                                "• Exécution : ${prev.batchChunksCount} lots séquentiels avec sauvegarde Rollback préalable"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (rawJson.isNotBlank()) {
                        onRestore(rawJson, decryptPassword, replaceExisting)
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("restore_confirm_btn")
            ) {
                Text("2. Exécuter la restauration (Phase 2)", fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwipeableTransactionRow(
    tx: TransactionItem,
    accountName: String,
    currencySymbol: String,
    hideAmounts: Boolean,
    deleteLabel: String,
    editLabel: String,
    onEditTransaction: () -> Unit,
    onDeleteTransaction: () -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val thresholdPx = with(density) { 88.dp.toPx() }
    val maxDragPx = with(density) { 140.dp.toPx() }
    var rawOffsetX by remember(tx.id) { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = rawOffsetX, label = "txSwipeOffset")

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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
    ) {
        // Background Reveal Banner (Right Swipe = Emerald Edit, Left Swipe = Coral Delete)
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
                        imageVector = if (isSwipingRight) Icons.Default.Edit else Icons.Default.DeleteOutline,
                        contentDescription = if (isSwipingRight) editLabel else deleteLabel,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isSwipingRight) editLabel else deleteLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .clip(RoundedCornerShape(22.dp))
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
                                    onEditTransaction()
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
                            contentDescription = editLabel,
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
                        text = if (hideAmounts) "•••• $currencySymbol" else "$prefix${PayCycleEngine.formatDh(tx.amount)} $currencySymbol",
                        style = MaterialTheme.typography.titleMedium,
                        color = amountColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(
                        onClick = onDeleteTransaction,
                        modifier = Modifier.testTag("delete_tx_${tx.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = deleteLabel,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPresetPickerSheet(
    state: DashboardState,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSelectPreset: (FinancialItem) -> Unit,
    onCreateNewPreset: () -> Unit
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val hide = state.hideAmounts
    val lang = state.profile.language

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = AccentAmber
                    )
                    Text(
                        text = strings.quickPresetsTitle,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        onCreateNewPreset()
                    },
                    modifier = Modifier.testTag("picker_add_new_preset_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Preset", fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = when (lang) {
                    "ar" -> "اضغط على أي مصروف متكرر لتسجيله فوراً بضغطة واحدة:"
                    "darija" -> "ورك على أي مصروف يومي باش يتقيد دابا بضغطة وحدة:"
                    "en" -> "Tap any common expense below to log it immediately in one tap:"
                    else -> "Touchez une dépense fréquente ci-dessous pour l'enregistrer instantanément :"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.presets.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Aucun achat rapide configuré",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Button(
                            onClick = {
                                onDismiss()
                                onCreateNewPreset()
                            }
                        ) {
                            Text("Créer mon premier Preset 1-Tap")
                        }
                    }
                }
            } else {
                state.presets.forEach { preset ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                onSelectPreset(preset)
                                onDismiss()
                            }
                            .testTag("quick_picker_preset_${preset.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = AccentAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${PayCycleEngine.formatAmountOrMask(preset.amount, hide)} $cur",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
