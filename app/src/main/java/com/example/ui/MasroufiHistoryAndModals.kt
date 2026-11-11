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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import com.example.ui.theme.ExpressiveGlassCard
import com.example.ui.theme.ExpressiveGlassSurface
import com.example.ui.theme.ExpressivePillChip
import com.example.ui.theme.distinctEnvelopeColor
import com.example.ui.theme.semanticExpenseColor
import com.example.ui.theme.semanticIncomeColor
import com.example.ui.theme.semanticTransferColor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NotificationsActive
import com.example.notifications.MasroufiNotificationManager
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
    onDuplicateTransaction: (TransactionItem) -> Unit = {},
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
    var passwordValidationError by remember { mutableStateOf<String?>(null) }
    var pendingUnencryptedExportAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val todayIso = remember { PayCycleEngine.todayIso() }
    val groupedByDay = remember(state.filteredHistory) {
        state.filteredHistory
            .groupBy { it.isoDate.ifBlank { todayIso } }
            .entries
            .sortedByDescending { it.key }
    }

    fun formatDayHeaderLabel(iso: String): String {
        val todayEpoch = PayCycleEngine.parseEpochDays(todayIso)
        val dayEpoch = PayCycleEngine.parseEpochDays(iso)
        val diff = (todayEpoch - dayEpoch).toInt()
        return when (diff) {
            0 -> when (lang) {
                "ar" -> "اليوم ($iso)"
                "darija" -> "اليوم ($iso)"
                "en" -> "Today ($iso)"
                else -> "Aujourd'hui ($iso)"
            }
            1 -> when (lang) {
                "ar" -> "أمس ($iso)"
                "darija" -> "البارح ($iso)"
                "en" -> "Yesterday ($iso)"
                else -> "Hier ($iso)"
            }
            else -> iso
        }
    }

    fun handleJsonExportRequest(action: (String) -> Unit) {
        val trimmedPwd = backupPassword.trim()
        when {
            trimmedPwd.length >= AppSecurityManager.MIN_BACKUP_PASSWORD_LENGTH -> {
                passwordValidationError = null
                action(trimmedPwd)
            }
            trimmedPwd.isNotEmpty() -> {
                passwordValidationError = when (lang) {
                    "ar" -> "يجب أن تتكون كلمة مرور التشفير من ${AppSecurityManager.MIN_BACKUP_PASSWORD_LENGTH} أحرف على الأقل."
                    "darija" -> "خاص كلمة السر ديال التشفير تكون فيها ${AppSecurityManager.MIN_BACKUP_PASSWORD_LENGTH} حروف على الأقل."
                    "en" -> "Encryption password must be at least ${AppSecurityManager.MIN_BACKUP_PASSWORD_LENGTH} characters."
                    else -> "Le mot de passe de chiffrement doit contenir au moins ${AppSecurityManager.MIN_BACKUP_PASSWORD_LENGTH} caractères."
                }
            }
            else -> {
                passwordValidationError = null
                pendingUnencryptedExportAction = { action("") }
            }
        }
    }

    val confirmAction = pendingUnencryptedExportAction
    if (confirmAction != null) {
        AlertDialog(
            onDismissRequest = { pendingUnencryptedExportAction = null },
            modifier = Modifier.testTag("unencrypted_export_warning_dialog"),
            title = {
                Text(
                    text = when (lang) {
                        "ar" -> "تصدير غير مشفر (نص واضح)"
                        "darija" -> "تصدير بلا تشفير (نص واضح)"
                        "en" -> "Unencrypted Plaintext Export"
                        else -> "Export non chiffré (texte clair)"
                    },
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = when (lang) {
                        "ar" -> "تحذير أمني: هذا الملف غير مشفر ويحتوي على سجل معاملاتك المالية بنص واضح. لحماية بياناتك بـ AES-256-GCM، ألغِ العملية وأدخل كلمة مرور من 10 أحرف أو أكثر."
                        "darija" -> "رد بالك: هاد الملف ما مشفرش وكيبين المعاملات المالية ديالك بنص واضح. إلا بغيتي تشفرو بـ AES-256-GCM، دير كلمة سر فيها 10 حروف أو كثر."
                        "en" -> "Security warning: This export is unencrypted plaintext containing your financial history. To encrypt with AES-256-GCM, cancel and set a 10+ character password."
                        else -> "Avertissement : Cet export est en texte clair non chiffré et contient votre historique financier. Pour chiffrer en AES-256-GCM, annulez et définissez un mot de passe de 10+ caractères."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRun = confirmAction
                        pendingUnencryptedExportAction = null
                        toRun()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCoral,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_unencrypted_export_btn")
                ) {
                    Text(
                        text = when (lang) {
                            "ar" -> "متابعة بدون تشفير"
                            "darija" -> "كمل بلا تشفير"
                            "en" -> "Export Unencrypted"
                            else -> "Exporter en clair"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingUnencryptedExportAction = null },
                    modifier = Modifier.testTag("cancel_unencrypted_export_btn")
                ) {
                    Text(
                        text = when (lang) {
                            "ar" -> "إلغاء"
                            "darija" -> "إلغاء"
                            "en" -> "Cancel"
                            else -> "Annuler"
                        }
                    )
                }
            }
        )
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
            // Header & Swipe Gesture Hint
            item(key = "history_top_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.historyTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                    ) {
                        Text(
                            text = "${state.filteredHistory.size} op. · ${groupedByDay.size} j",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (state.filteredHistory.isNotEmpty()) {
                item(key = "swipe_hint_pill") {
                    ExpressiveGlassSurface(
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                    "ar" -> "اسحب يميناً للتكرار · اسحب يساراً للحذف (مع تراجع) · اضغط للتعديل"
                                    "darija" -> "جر لليمين باش تكرر · جر لليسار باش تمسح (مع رجع) · ورك باش تبدل"
                                    "en" -> "Swipe right to duplicate · Swipe left to delete (with Undo) · Tap to edit"
                                    else -> "Glissez à droite pour dupliquer · à gauche pour supprimer (Annuler) · Touchez pour modifier"
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

            // Upcoming (Future-dated) Transactions Group at the top
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
                        envelopes = state.envelopes.map { it.config },
                        isDarkTheme = state.profile.isDarkTheme,
                        deleteLabel = strings.deleteBtn,
                        duplicateLabel = when (lang) {
                            "ar" -> "تكرار"
                            "darija" -> "عاودها"
                            "en" -> "Duplicate"
                            else -> "Dupliquer"
                        },
                        editLabel = strings.editBtn,
                        onEditTransaction = { onEditTransaction(tx) },
                        onDuplicateTransaction = { onDuplicateTransaction(tx) },
                        onDeleteTransaction = { onDeleteTransaction(tx) }
                    )
                }
            }

            // Day-Grouped Transactions with Daily Totals
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
                                text = formatDayHeaderLabel(dayIso),
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
                        envelopes = state.envelopes.map { it.config },
                        isDarkTheme = state.profile.isDarkTheme,
                        deleteLabel = strings.deleteBtn,
                        duplicateLabel = when (lang) {
                            "ar" -> "تكرار"
                            "darija" -> "عاودها"
                            "en" -> "Duplicate"
                            else -> "Dupliquer"
                        },
                        editLabel = strings.editBtn,
                        onEditTransaction = { onEditTransaction(tx) },
                        onDuplicateTransaction = { onDuplicateTransaction(tx) },
                        onDeleteTransaction = { onDeleteTransaction(tx) }
                    )
                }
            }

            // Export & Backup Card
            item {
                ExpressiveGlassCard(
                    cornerRadius = 28.dp,
                    accentRimColor = AccentEmerald,
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                                text = if (backupPassword.trim().length >= AppSecurityManager.MIN_BACKUP_PASSWORD_LENGTH) {
                                    "AES-256-GCM Actif (600k PBKDF2)"
                                } else {
                                    "Format Complet v2.0"
                                },
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
                        onValueChange = {
                            backupPassword = it
                            passwordValidationError = null
                        },
                        label = {
                            Text(
                                when (state.profile.language) {
                                    "ar" -> "كلمة مرور تشفير النسخة الاحتياطية AES-256-GCM (10 أحرف+)"
                                    "darija" -> "كلمة السر لتشفير النسخة بـ AES-256-GCM (10 حروف+)"
                                    "en" -> "AES-256-GCM Backup Password (10+ chars)"
                                    else -> "Mot de passe chiffrement AES-256-GCM (10+ car.)"
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

                    if (!passwordValidationError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = passwordValidationError!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentCoral,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("backup_password_error_text")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (state.profile.language) {
                            "ar" -> "ملاحظة: ملف CSV غير مشفر (مخصص لـ Excel ومحمّي ضد حقن الصيغ). استخدم JSON مع كلمة مرور (10 أحرف+) للتشفير بـ AES-256-GCM + PBKDF2 (600,000 تكرار)."
                            "darija" -> "ملاحظة: ملف CSV ما مشفرش (خاص بـ Excel ومحمّي من الصيغ). استعمل JSON مع كلمة السر (10 حروف+) باش يتشفر بـ AES-256-GCM."
                            "en" -> "Security note: CSV is unencrypted plaintext for spreadsheets (formula-injection safe). Set a 10+ char password above to encrypt JSON with AES-256-GCM (600k PBKDF2)."
                            else -> "Note : Le CSV est en texte clair pour Excel/Sheets (formules neutralisées). Entrez un mot de passe (10+ car.) ci-dessus pour chiffrer le JSON en AES-256-GCM (600k PBKDF2)."
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
                            onClick = { handleJsonExportRequest(onShareJson) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.btnShareJson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { handleJsonExportRequest(onCopyJson) },
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
                            onClick = { pendingUnencryptedExportAction = onShareCsv },
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
                            onClick = { handleJsonExportRequest(onExportJsonFile) },
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
                            onClick = { pendingUnencryptedExportAction = onExportCsvFile },
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
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Floating Glass Thumb-Reach Search & Filter Bar (with bottom clearance for the floating dock & FAB)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 84.dp)
        ) {
            ExpressiveGlassSurface(
                shape = RoundedCornerShape(26.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                accentBorderColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_thumb_filter_bar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category & Account Filter Chips within thumb reach
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
                                    Text(env.config.localizedName(state.profile.language).take(16), fontSize = 11.5.sp)
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

                    // Thumb-reach Search Field (paired with FAB clearance on the end)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 92.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.filterState.searchQuery,
                            onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q) } },
                            placeholder = { Text(strings.searchHint, fontSize = 12.sp, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (state.filterState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onUpdateFilter { it.copy(searchQuery = "") } }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("history_search_input")
                        )
                    }
                }
            }
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
    autoLaunchVoice: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, String, String, String, String, String) -> Unit,
    onDelete: ((TransactionItem) -> Unit)? = null
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val lang = state.profile.language
    val haptics = LocalHapticFeedback.current

    // Smart defaults from last-used transaction when creating a new operation
    val initialSmartDefault = remember(existingTx) {
        if (existingTx == null) {
            PayCycleEngine.inferSmartDefaults(
                description = "",
                allTransactions = state.allTransactions,
                envelopes = state.envelopes.map { it.config },
                accounts = state.accounts.map { it.account },
                lang = lang
            )
        } else null
    }

    var txType by remember { mutableStateOf(existingTx?.type ?: initialType) }
    var amountText by remember {
        mutableStateOf(
            existingTx?.amount?.let {
                if (it > 0) {
                    if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                } else ""
            } ?: ""
        )
    }
    var isoDate by remember { mutableStateOf(existingTx?.isoDate ?: PayCycleEngine.todayIso()) }
    var selectedEnvelopeKey by remember {
        mutableStateOf(
            existingTx?.envelopeKey?.ifBlank { null }
                ?: initialSmartDefault?.envelopeKey
                ?: state.envelopes.firstOrNull()?.config?.key
                ?: "transport"
        )
    }
    var customCategory by remember {
        mutableStateOf(
            existingTx?.category
                ?: initialSmartDefault?.categoryName
                ?: state.envelopes.firstOrNull()?.config?.localizedName(state.profile.language)
                ?: "Transport & Carburant"
        )
    }
    var selectedAccountId by remember {
        mutableStateOf(
            existingTx?.accountId
                ?: initialSmartDefault?.accountId
                ?: state.accounts.firstOrNull()?.account?.id
                ?: "acc_cash"
        )
    }
    var toAccountId by remember {
        mutableStateOf(
            existingTx?.toAccountId?.ifBlank { null }
                ?: state.accounts.getOrNull(1)?.account?.id
                ?: "acc_bank"
        )
    }
    var desc by remember { mutableStateOf(existingTx?.desc ?: "") }
    var twoTapInstantSave by remember { mutableStateOf(existingTx == null) }
    var showAdvancedFields by remember { mutableStateOf(existingTx != null) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var smartDefaultHint by remember { mutableStateOf<String?>(null) }
    var duplicateWarningTx by remember { mutableStateOf<TransactionItem?>(null) }

    // Multi-currency state (MAD, EUR, USD for MRE users and trips)
    val baseCurrencyCode = PayCycleEngine.normalizeCurrencyCode(cur)
    var entryCurrency by remember { mutableStateOf(baseCurrencyCode) }
    var eurRateText by remember { mutableStateOf(PayCycleEngine.DEFAULT_EUR_TO_MAD.toString()) }
    var usdRateText by remember { mutableStateOf(PayCycleEngine.DEFAULT_USD_TO_MAD.toString()) }

    val recentCategories = remember(state.allTransactions, state.envelopes, lang) {
        computeTopThreeRecentCategories(state)
    }

    fun computeConvertedAmount(): Double {
        val rawAmt = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
        val eurRate = eurRateText.replace(",", ".").toDoubleOrNull() ?: PayCycleEngine.DEFAULT_EUR_TO_MAD
        val usdRate = usdRateText.replace(",", ".").toDoubleOrNull() ?: PayCycleEngine.DEFAULT_USD_TO_MAD
        return PayCycleEngine.convertCurrencyAmount(
            amount = rawAmt,
            fromCurrency = entryCurrency,
            toCurrency = baseCurrencyCode,
            eurToMadRate = eurRate,
            usdToMadRate = usdRate
        )
    }

    fun tryCommitTransaction(
        overrideCategory: String = customCategory,
        overrideEnvKey: String = selectedEnvelopeKey,
        ignoreDuplicateCheck: Boolean = false
    ): Boolean {
        val rawAmt = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
        val convertedAmt = computeConvertedAmount()
        val normalizedDate = isoDate.trim().ifEmpty { PayCycleEngine.todayIso() }
        if (!PayCycleEngine.isValidIsoDate(normalizedDate)) {
            dateError = "Date invalide ('$normalizedDate'). Format requis : YYYY-MM-DD réel (ex: ${PayCycleEngine.todayIso()})."
            showAdvancedFields = true
            return false
        }
        if (txType == "transfer" && (toAccountId.isBlank() || toAccountId == selectedAccountId)) {
            dateError = "Veuillez sélectionner un compte destinataire différent du compte source."
            return false
        }
        if (convertedAmt <= 0.0) {
            return false
        }

        // Duplicate warning check when adding a new transaction
        if (existingTx == null && !ignoreDuplicateCheck) {
            val dup = PayCycleEngine.findRecentDuplicateTransaction(
                type = txType,
                amount = convertedAmt,
                envelopeKey = if (txType == "transfer") "autre" else overrideEnvKey,
                category = if (txType == "transfer") "Transfert" else overrideCategory,
                allTransactions = state.allTransactions,
                nowIso = normalizedDate
            )
            if (dup != null) {
                duplicateWarningTx = dup
                return false
            }
        }

        dateError = null
        duplicateWarningTx = null
        val baseDesc = desc.ifBlank { if (txType == "transfer") "Transfert entre comptes" else overrideCategory }
        val finalDesc = if (entryCurrency != baseCurrencyCode && rawAmt > 0.0) {
            "$baseDesc (${PayCycleEngine.formatDh(rawAmt)} $entryCurrency)"
        } else {
            baseDesc
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onSave(
            existingTx?.id ?: "",
            txType,
            convertedAmt,
            if (txType == "transfer") "Transfert" else overrideCategory,
            if (txType == "transfer") "autre" else overrideEnvKey,
            selectedAccountId,
            if (txType == "transfer") toAccountId else "",
            finalDesc,
            normalizedDate
        )
        onDismiss()
        return true
    }

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
                Text(
                    text = if (existingTx != null) strings.editOperationTitle else strings.addOperationTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                ExpressivePillChip(
                    label = when (lang) {
                        "ar" -> if (showAdvancedFields) "إخفاء الخيارات" else "خيارات متقدمة"
                        "darija" -> if (showAdvancedFields) "خبي التفاصيل" else "خيارات متقدمة"
                        "en" -> if (showAdvancedFields) "Less Options" else "Advanced Options"
                        else -> if (showAdvancedFields) "Moins d'options" else "Options avancées"
                    },
                    selected = showAdvancedFields,
                    onClick = { showAdvancedFields = !showAdvancedFields },
                    icon = Icons.Default.Tune,
                    testTag = "tx_advanced_options_toggle"
                )
            }

            // Expressive Glass Type Switcher (Expense, Income, Transfer)
            ExpressiveGlassSurface(
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple("expense", strings.expenseTab, AccentCoral),
                        Triple("income", strings.incomeTab, AccentEmerald),
                        Triple("transfer", strings.transferTab, AccentSkyBlue)
                    ).forEach { (typeKey, label, accent) ->
                        val isSelected = txType == typeKey
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) accent.copy(alpha = 0.22f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, accent.copy(alpha = 0.65f)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    txType = typeKey
                                }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 1. Voice Entry in Darija / AR / FR / EN ("9ahwa 15" / "coffee 15" / "قهوة 15")
            VoiceDarijaEntrySection(
                state = state,
                autoLaunchVoice = autoLaunchVoice,
                onVoiceParsed = { parsed ->
                    txType = parsed.txType
                    amountText = if (parsed.amount % 1.0 == 0.0) {
                        parsed.amount.toInt().toString()
                    } else {
                        parsed.amount.toString()
                    }
                    desc = parsed.description
                    selectedEnvelopeKey = parsed.envelopeKey
                    customCategory = parsed.categoryName
                }
            )

            // 2. Large Numeric Keypad + 3 Most Recent Categories (2-Tap Entry)
            LargeNumericKeypadSection(
                amountText = amountText,
                currencySymbol = if (entryCurrency == baseCurrencyCode) cur else entryCurrency,
                language = lang,
                twoTapInstantSave = twoTapInstantSave,
                onToggleTwoTapInstantSave = { twoTapInstantSave = it },
                recentCategories = recentCategories,
                selectedEnvelopeKey = selectedEnvelopeKey,
                onAmountChange = {
                    amountText = it
                    duplicateWarningTx = null
                },
                onSelectCategoryShortcut = { shortcut, commitImmediately ->
                    selectedEnvelopeKey = shortcut.envelopeKey
                    customCategory = shortcut.categoryName
                    if (commitImmediately && txType != "transfer") {
                        tryCommitTransaction(
                            overrideCategory = shortcut.categoryName,
                            overrideEnvKey = shortcut.envelopeKey
                        )
                    }
                }
            )

            // Duplicate Warning Banner
            if (duplicateWarningTx != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AccentAmber.copy(alpha = 0.18f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AccentAmber, RoundedCornerShape(16.dp))
                        .testTag("duplicate_tx_warning_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "⚠️ Doublon détecté : ${PayCycleEngine.formatDh(duplicateWarningTx!!.amount)} $cur dans '${duplicateWarningTx!!.category}' a déjà été ajouté il y a quelques instants.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentAmber,
                            fontWeight = FontWeight.Bold
                        )
                        FilledTonalButton(
                            onClick = {
                                tryCommitTransaction(ignoreDuplicateCheck = true)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_confirm_duplicate_tx")
                        ) {
                            Text(strings.addAnywayBtn, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Source Account & Envelope Quick Pills
            ExpressiveGlassSurface(
                shape = RoundedCornerShape(22.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (txType == "transfer") strings.sourceAccountFieldLabel else strings.accountFieldLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
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
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
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
                            text = "Toutes les Enveloppes / Catégories :",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.envelopes.forEach { env ->
                                val catLabel = env.config.localizedName(state.profile.language)
                                FilterChip(
                                    selected = selectedEnvelopeKey == env.config.key,
                                    onClick = {
                                        selectedEnvelopeKey = env.config.key
                                        customCategory = catLabel
                                        duplicateWarningTx = null
                                        if (twoTapInstantSave) {
                                            tryCommitTransaction(
                                                overrideCategory = catLabel,
                                                overrideEnvKey = env.config.key
                                            )
                                        }
                                    },
                                    label = { Text(catLabel.take(22)) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = desc,
                        onValueChange = { newDesc ->
                            desc = newDesc
                            if (existingTx == null && newDesc.trim().length >= 2) {
                                val guessed = PayCycleEngine.inferSmartDefaults(
                                    description = newDesc,
                                    allTransactions = state.allTransactions,
                                    envelopes = state.envelopes.map { it.config },
                                    accounts = state.accounts.map { it.account },
                                    lang = lang
                                )
                                selectedEnvelopeKey = guessed.envelopeKey
                                customCategory = guessed.categoryName
                                selectedAccountId = guessed.accountId
                                smartDefaultHint = "✨ Catégorie & compte suggérés : ${guessed.categoryName}"
                            } else {
                                smartDefaultHint = null
                            }
                        },
                        label = { Text("Description / Note (Smart Default auto)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tx_desc_input")
                    )

                    if (!smartDefaultHint.isNullOrBlank()) {
                        Text(
                            text = smartDefaultHint!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentEmerald,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("smart_default_hint_text")
                        )
                    }
                }
            }

            // 2B. Multi-Currency Selector (MAD / EUR / USD for MRE users & trips)
            ExpressiveGlassSurface(
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                accentBorderColor = AccentSkyBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("multi_currency_bar")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌍 Multi-devises (MAD / EUR / USD) :",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("MAD", "EUR", "USD").forEach { code ->
                                FilterChip(
                                    selected = entryCurrency == code,
                                    onClick = { entryCurrency = code },
                                    label = { Text(code, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.testTag("currency_chip_$code")
                                )
                            }
                        }
                    }
                    if (entryCurrency != baseCurrencyCode) {
                        val isEur = entryCurrency == "EUR"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = if (isEur) eurRateText else usdRateText,
                                onValueChange = { if (isEur) eurRateText = it else usdRateText = it },
                                label = { Text("Taux 1 $entryCurrency → MAD") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("fx_rate_input")
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "= ${PayCycleEngine.formatDh(computeConvertedAmount())} $cur",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("fx_converted_preview_text")
                                )
                            }
                        }
                    }
                }
            }

            // Collapsible Advanced Details (Manual Amount Field, Custom Category Name & Date)
            AnimatedVisibility(visible = showAdvancedFields || !dateError.isNullOrBlank()) {
                ExpressiveGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = {
                                amountText = it
                                duplicateWarningTx = null
                            },
                            label = { Text("${strings.amountLabel} (${if (entryCurrency == baseCurrencyCode) cur else entryCurrency})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tx_amount_input")
                        )

                        OutlinedTextField(
                            value = customCategory,
                            onValueChange = { customCategory = it },
                            label = { Text(strings.categoryNameLabel) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = isoDate,
                            onValueChange = { isoDate = it },
                            label = { Text("Date (YYYY-MM-DD)") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

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
                        tryCommitTransaction()
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(2f)
                        .height(54.dp)
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
    onOpenQuickOnboarding: () -> Unit = {},
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

    var notifyBills by remember { mutableStateOf(MasroufiNotificationManager.isNotifyBillsEnabled(context)) }
    var notifyDaret by remember { mutableStateOf(MasroufiNotificationManager.isNotifyDaretEnabled(context)) }
    var notifyBudget80 by remember { mutableStateOf(MasroufiNotificationManager.isNotifyBudget80Enabled(context)) }
    var notifyEvening by remember { mutableStateOf(MasroufiNotificationManager.isNotifyEveningEnabled(context)) }
    var notifFeedback by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            MasroufiNotificationManager.triggerImmediateCheck(context)
            notifFeedback = when (lang) {
                "ar" -> "تم تفعيل الإشعارات وإرسال فحص فوري!"
                "darija" -> "تفعلو الإشعارات وتصيفط التذكير دابا!"
                "en" -> "Notifications enabled & immediate check scheduled!"
                else -> "Notifications activées et vérification lancée !"
            }
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
            Text(text = strings.settingsTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)

            ExpressiveGlassSurface(
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                }
            }

            // Security & Privacy Hardening Section
            ExpressiveGlassSurface(
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                accentBorderColor = MaterialTheme.colorScheme.primary,
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
                                        "ar" -> "محمي باشتقاق PBKDF2-HMAC-SHA256 (600,000 تكرار + Salt + Android Keystore)"
                                        "darija" -> "محمي بـ PBKDF2-HMAC-SHA256 (600,000 تكرار + Salt + Android Keystore)"
                                        "en" -> "Protected via PBKDF2-HMAC-SHA256 key derivation (600,000 iter + salt + Keystore)"
                                        else -> "Protégé par dérivation PBKDF2-HMAC-SHA256 (600 000 itér. + sel + Keystore)"
                                    }
                                } else {
                                    when (lang) {
                                        "ar" -> "قم بتعيين رمز PIN من 6 إلى 8 أرقام مع قفل تدريجي"
                                        "darija" -> "دير كود PIN من 6 حتى 8 أرقام لتفعيل القفل"
                                        "en" -> "Set a 6 to 8 digit PIN below with progressive rate-limiting"
                                        else -> "Définissez un PIN de 6 à 8 chiffres ci-dessous avec anti-brute-force"
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
                                        "ar" -> "يرجى إدخال وحفظ رمز PIN (6 إلى 8 أرقام) أولاً"
                                        "darija" -> "دخل بعدا كود PIN (6 حتى 8 أرقام) لتحت"
                                        "en" -> "Please set a 6 to 8 digit PIN first"
                                        else -> "Veuillez d'abord définir un code PIN (6 à 8 chiffres)"
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
                                        "ar" -> "رمز PIN جديد (6 إلى 8 أرقام)"
                                        "darija" -> "كود PIN جديد (6 حتى 8 أرقام)"
                                        "en" -> "New PIN (6 to 8 digits)"
                                        else -> "Nouveau PIN (6 à 8 chiffres)"
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
                                        "ar" -> "تم اشتقاق وحفظ رمز PIN ($len أرقام) عبر PBKDF2-HMAC-SHA256 (600k)!"
                                        "darija" -> "تم تسجيل كود PIN ($len أرقام) بـ PBKDF2-HMAC-SHA256 (600k)!"
                                        "en" -> "$len-digit PIN hashed via PBKDF2-HMAC-SHA256 (600k) & App Lock enabled!"
                                        else -> "PIN ($len chiffres) haché via PBKDF2-HMAC-SHA256 (600k) et activé !"
                                    }
                                } else {
                                    pinFeedback = when (lang) {
                                        "ar" -> "يجب أن يتكون الرمز من 6 إلى 8 أرقام"
                                        "darija" -> "خاص الكود يكون ما بين 6 و 8 أرقام"
                                        "en" -> "PIN must be 6 to 8 digits"
                                        else -> "Le PIN doit contenir 6 à 8 chiffres"
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

            // WorkManager Notifications Card (Bills, Daret, 80% Budget, Evening Reminder)
            ExpressiveGlassSurface(
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                accentBorderColor = AccentAmber,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_notifications_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = AccentAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = when (lang) {
                                "ar" -> "إشعارات وتنبيهات ذكية (WorkManager)"
                                "darija" -> "إشعارات وتذكيرات ذكية (الفواتير، دارت، 80%)"
                                "en" -> "Smart Alerts & Reminders (WorkManager)"
                                else -> "Alertes & Rappels Intelligents (WorkManager)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentAmber,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (lang) {
                                "ar" -> "الفواتير القادمة والاشتراكات"
                                "darija" -> "الفواتير اللي قربو يتخلصو"
                                "en" -> "Upcoming Bills & Subscriptions"
                                else -> "Factures & abonnements à venir"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = notifyBills,
                            onCheckedChange = { enabled ->
                                notifyBills = enabled
                                MasroufiNotificationManager.setNotifyBillsEnabled(context, enabled)
                            },
                            modifier = Modifier.testTag("settings_notify_bills_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (lang) {
                                "ar" -> "جولات دارت (القرعة الشهرية)"
                                "darija" -> "نوبة ومساهمة دارت"
                                "en" -> "Daret Turns & Contributions"
                                else -> "Tours & cotisations Daret"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = notifyDaret,
                            onCheckedChange = { enabled ->
                                notifyDaret = enabled
                                MasroufiNotificationManager.setNotifyDaretEnabled(context, enabled)
                            },
                            modifier = Modifier.testTag("settings_notify_daret_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (lang) {
                                "ar" -> "تنبيه استهلاك 80% من الميزانية أو الأظرفة"
                                "darija" -> "تنبيه ملي توصل 80% من الميزانية"
                                "en" -> "80% Budget & Envelope Threshold Alert"
                                else -> "Alerte seuil 80% du budget / enveloppes"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = notifyBudget80,
                            onCheckedChange = { enabled ->
                                notifyBudget80 = enabled
                                MasroufiNotificationManager.setNotifyBudget80Enabled(context, enabled)
                            },
                            modifier = Modifier.testTag("settings_notify_budget80_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (lang) {
                                "ar" -> "تذكير مسائي لتسجيل مصاريف اليوم"
                                "darija" -> "تذكير فـ العشية باش تقيد مصاريف النهار"
                                "en" -> "Evening Daily Expense Reminder"
                                else -> "Rappel du soir pour noter vos dépenses"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = notifyEvening,
                            onCheckedChange = { enabled ->
                                notifyEvening = enabled
                                MasroufiNotificationManager.setNotifyEveningEnabled(context, enabled)
                            },
                            modifier = Modifier.testTag("settings_notify_evening_switch")
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                MasroufiNotificationManager.triggerImmediateCheck(context)
                                notifFeedback = when (lang) {
                                    "ar" -> "تم جدولة فحص الإشعارات الآن!"
                                    "darija" -> "تبرمج فحص الإشعارات دابا!"
                                    "en" -> "Immediate alert check scheduled!"
                                    else -> "Vérification des alertes programmée !"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_test_notifications_btn")
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (lang) {
                                "ar" -> "تفعيل واختبار الإشعارات الآن"
                                "darija" -> "فعل وجرب الإشعارات دابا"
                                "en" -> "Enable & Check Alerts Now"
                                else -> "Activer & Vérifier les alertes maintenant"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!notifFeedback.isNullOrBlank()) {
                        Text(
                            text = notifFeedback!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Household or Partner Account via Invite
            HouseholdInviteSectionCard(
                userId = state.profile.userId.ifBlank { "masroufi_user" },
                lang = lang,
                onShareInvite = { inviteText ->
                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, inviteText)
                    }
                    runCatching {
                        context.startActivity(
                            android.content.Intent.createChooser(sendIntent, "Inviter un partenaire")
                        )
                    }
                }
            )

            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onOpenQuickOnboarding()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_open_30s_onboarding_btn")
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (lang) {
                        "ar" -> "إعداد سريع 30 ثانية (الراتب، اليوم و 3 أظرفة)"
                        "darija" -> "إعداد دغيا فـ 30 ثانية (المانضة، النهار و 3 أظرفة)"
                        "en" -> "30-Second Quick Setup (Salary, Pay Day & 3 Envelopes)"
                        else -> "Config Express 30s (Salaire, Jour & 3 Enveloppes)"
                    },
                    fontWeight = FontWeight.Bold
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
                Text(strings.customizationStudioFullBtn, fontWeight = FontWeight.Bold)
            }

            // Clean Account vs Optional Demo Data Mode
            ExpressiveGlassSurface(
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.demoModeBtn,
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
                            Text(strings.demoModeBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            Text(strings.cleanAccountBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                shape = RoundedCornerShape(20.dp),
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
            val strings = com.example.domain.Translations.forLang(lang)
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
                    text = strings.customizationStudioTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
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
                    val label = pal.localizedLabel(lang)
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
                label = { Text(strings.customTitleFieldLabel) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = customSubtitle,
                onValueChange = { customSubtitle = it.take(80) },
                label = { Text(strings.customSubtitleFieldLabel) },
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
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = strings.moveUpDesc)
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
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = strings.moveDownDesc)
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
                Text(strings.applyCustomizationBtn, fontWeight = FontWeight.ExtraBold)
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
    var itemSubtitleText by remember {
        mutableStateOf(existingItem?.subtitle ?: "")
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
                "daret" -> if (isEditing) "Modifier la Daret" else "Nouvelle Daret (Jam3iya)"
                "debt" -> if (isEditing) "Modifier Sellef (Crédit / Dette)" else "Nouveau Sellef (Crédit / Dette)"
                "seasonal" -> if (isEditing) "Modifier Enveloppe Saisonnière" else "Enveloppe Saisonnière (Ramadan, Aïd, Rentrée)"
                "bill" -> if (isEditing) "Modifier Facture Récurrente" else "Nouvelle Facture Récurrente"
                "preset" -> if (isEditing) "Modifier Achat Rapide (1-Tap)" else "Nouveau Bouton d'Achat Rapide"
                else -> if (isEditing) "Modifier Objectif d'Épargne" else "Nouvel Objectif d'Épargne"
            }
            Text(text = title, style = MaterialTheme.typography.titleLarge)

            // Moroccan Bill Templates inside Bill modal
            if (itemType == "bill") {
                val billTemplates = remember { PayCycleEngine.moroccanBillTemplates() }
                Text(
                    text = "Modèles rapides Maroc (Lydec, Redal, Amendis, ONEE, IAM, Inwi, Orange, Loyer) :",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    billTemplates.forEach { tpl ->
                        FilterChip(
                            selected = name.contains(tpl.name, ignoreCase = true),
                            onClick = {
                                name = "${tpl.emoji} ${tpl.name}"
                                amountText = tpl.defaultAmount.toInt().toString()
                                dayOrTurnText = tpl.defaultDueDay.toString()
                            },
                            label = { Text("${tpl.emoji} ${tpl.name} (J${tpl.defaultDueDay})", fontSize = 11.sp) }
                        )
                    }
                }
            } else if (itemType == "seasonal") {
                val seasonalTemplates = remember { PayCycleEngine.moroccanSeasonalTemplates() }
                Text(
                    text = "Modèles saisonniers Maroc :",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    seasonalTemplates.forEach { tpl ->
                        FilterChip(
                            selected = name.contains(tpl.name, ignoreCase = true),
                            onClick = {
                                name = "${tpl.emoji} ${tpl.name}"
                                amountText = tpl.targetAmount.toInt().toString()
                                dateOrDirText = tpl.targetIsoDate
                            },
                            label = { Text("${tpl.emoji} ${tpl.name}", fontSize = 11.sp) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (itemType == "debt") "Nom de la personne (Qui ?)" else "Nom") },
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
                OutlinedTextField(
                    value = itemSubtitleText,
                    onValueChange = { itemSubtitleText = it },
                    label = { Text("À qui le tour ce mois-ci ? (ex: Tour de Yassine)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dateOrDirText,
                    onValueChange = { dateOrDirText = it },
                    label = { Text("Date de réception prévue (YYYY-MM-DD ou Mois)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                        label = { Text("On me doit (Sellef prêté)") }
                    )
                    FilterChip(
                        selected = dateOrDirText == "borrowed",
                        onClick = { dateOrDirText = "borrowed" },
                        label = { Text("Je dois (Sellef emprunté)") }
                    )
                }
                OutlinedTextField(
                    value = itemSubtitleText,
                    onValueChange = { itemSubtitleText = it },
                    label = { Text("Motif / Note (ex: Dîner, Avance, Café)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                val itemStrings = com.example.domain.Translations.forLang(state.profile.language)
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
                        Text(itemStrings.deleteBtn, fontWeight = FontWeight.Bold)
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
                        Text(itemStrings.deleteBtn, fontWeight = FontWeight.Bold)
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
                                        subtitle = when (itemType) {
                                            "preset" -> selectedPresetEnvelopeKey
                                            "daret", "debt" -> itemSubtitleText.trim().ifBlank { existingItem?.subtitle ?: "" }
                                            else -> existingItem?.subtitle?.ifBlank { n } ?: n
                                        },
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
                    Text(itemStrings.saveBtn, fontWeight = FontWeight.ExtraBold)
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
    lang: String = "fr",
    envelopes: List<EnvelopeConfig> = emptyList(),
    isDarkTheme: Boolean = false,
    deleteLabel: String,
    editLabel: String,
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
            .clip(RoundedCornerShape(22.dp))
    ) {
        // Background Reveal Banner (Right Swipe = Emerald Duplicate, Left Swipe = Coral Delete)
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
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 2.dp,
            border = BorderStroke(
                1.dp,
                if (isDarkTheme) Color(0xFF222630) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .clip(RoundedCornerShape(26.dp))
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
                com.example.data.DefaultCategoryResolver.resolveTransactionCategoryDisplay(
                    rawDesc,
                    tx.envelopeKey,
                    envelopes,
                    lang
                )
            }
            val pillSurfaceColor = if (isDarkTheme) Color(0xFF232731) else MaterialTheme.colorScheme.surfaceContainerHigh

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Row: Vyxel Rounded-Square Icon Badge + Account/Date Metadata + Bold Title + Circular Duplicate Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(catAccent.copy(alpha = if (isDarkTheme) 0.24f else 0.16f))
                                .border(1.dp, catAccent.copy(alpha = 0.48f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = resolvedCategoryDisplay.take(2).uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = catAccent
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$accountName — ${tx.isoDate}",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = descDisplay,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Circular Bookmark/Duplicate button on top-right (matching Vyxel card top-right circle)
                    Surface(
                        shape = CircleShape,
                        color = pillSurfaceColor,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDuplicateTransaction)
                            .testTag("duplicate_tx_${tx.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = duplicateLabel,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Bottom Row: Category Pill Tag on left + Circular Delete Button & Amount Pill Button on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = accountName.take(12),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (isDarkTheme) Color(0xFF1A1E26) else catAccent.copy(alpha = 0.12f),
                            border = BorderStroke(
                                1.dp,
                                if (isDarkTheme) Color(0xFF282D39) else catAccent.copy(alpha = 0.30f)
                            )
                        ) {
                            Text(
                                text = resolvedCategoryDisplay.take(22),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = pillSurfaceColor,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onDeleteTransaction)
                                .testTag("delete_tx_${tx.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = deleteLabel,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = pillSurfaceColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
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
                    Text(strings.addPresetBtn, fontWeight = FontWeight.Bold)
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
