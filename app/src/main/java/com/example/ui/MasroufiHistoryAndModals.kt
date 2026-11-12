package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccountItem
import com.example.data.FinancialItem
import com.example.domain.AppStrings
import com.example.domain.PayCycleEngine
import com.example.domain.Translations
import com.example.security.AppSecurityManager
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald

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

    var selectedAccountKind by remember {
        mutableStateOf(existingAccount?.kind ?: "bank")
    }
    var selectedAccountColor by remember {
        mutableStateOf(existingAccount?.colorHex ?: "#38BDF8")
    }

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

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
                val itemStrings = Translations.forLang(state.profile.language)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings.btnRestoreJson,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
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
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
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
