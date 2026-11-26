package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.TransactionItem
import com.example.domain.AppStrings
import com.example.domain.BankMessageParser
import com.example.domain.PayCycleEngine
import com.example.notifications.AllowlistAppOption
import com.example.notifications.BankDetectionStore
import com.example.notifications.DetectedBankTransaction
import com.example.notifications.ManualSetup
import com.example.notifications.MasroufiDetectionLog
import com.example.notifications.ShizukuSetup
import com.example.security.AppSecurityManager
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.ExpressiveGlassSurface

/**
 * Phases 10D, 10E & 10F:
 * Review sheet for auto-detected bank notification expenses ("To confirm") +
 * Privacy contract, Allowlist setup, Manual & Shizuku 1-tap setup, OEM Battery Tips dialog,
 * and Kill Switches (Pause / Delete all detected data / Revoke access).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankDetectionReviewSheet(
    state: DashboardState,
    strings: AppStrings,
    pendingItems: List<DetectedBankTransaction>,
    onConfirmDetection: (DetectedBankTransaction) -> Unit,
    onEditDetection: (DetectedBankTransaction) -> Unit,
    onIgnoreDetection: (DetectedBankTransaction) -> Unit,
    onAlwaysIgnoreMerchant: (DetectedBankTransaction) -> Unit,
    onRefreshPending: () -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val lang = state.profile.language

    var isListenerGranted by remember {
        mutableStateOf(ManualSetup.isNotificationListenerGranted(context))
    }
    var isPaused by remember {
        mutableStateOf(BankDetectionStore.isPaused(context))
    }
    var autoConfirmEnabled by remember {
        mutableStateOf(BankDetectionStore.isAutoConfirmEnabled(context))
    }
    var allowlistPackages by remember {
        mutableStateOf<Set<String>>(BankDetectionStore.getAllowedPackages(context))
    }
    var showAllowlistPicker by remember { mutableStateOf(false) }
    var showAddInstalledAppDialog by remember { mutableStateOf(false) }
    var showDiagnosticLog by remember { mutableStateOf(false) }
    var showBatteryTipsDialog by remember { mutableStateOf(false) }
    var sampleMaskPreview by remember { mutableStateOf("") }

    val listenerHealth by MasroufiDetectionLog.healthFlow.collectAsState()
    val diagnosticEntries by MasroufiDetectionLog.entriesFlow.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isListenerGranted = ManualSetup.isNotificationListenerGranted(context)
                allowlistPackages = BankDetectionStore.getAllowedPackages(context)
                BankDetectionStore.requestListenerRebindIfNeeded(context)
                onRefreshPending()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val candidateApps: List<AllowlistAppOption> = remember(showAllowlistPicker, allowlistPackages) {
        if (showAllowlistPicker) BankDetectionStore.getCandidateApps(context) else emptyList()
    }
    val installedLauncherApps: List<AllowlistAppOption> = remember(showAddInstalledAppDialog, allowlistPackages) {
        if (showAddInstalledAppDialog) BankDetectionStore.getInstalledLauncherApps(context) else emptyList()
    }
    val shizukuAvailable = remember { ShizukuSetup.isShizukuInstalled(context) }

    if (showAddInstalledAppDialog) {
        AlertDialog(
            onDismissRequest = { showAddInstalledAppDialog = false },
            modifier = Modifier.testTag("bank_detect_installed_apps_dialog"),
            title = {
                Text(
                    text = when (lang) {
                        "ar" -> "إضافة تطبيق بنكي أو SMS"
                        "darija" -> "زيد تطبيق بنكي ولا SMS"
                        "en" -> "Add Bank or SMS App"
                        else -> "Ajouter une application"
                    },
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (installedLauncherApps.isEmpty()) {
                        Text(
                            text = "Aucune autre application trouvée.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        installedLauncherApps.forEach { appOpt ->
                            val isChecked = appOpt.packageName in allowlistPackages
                            FilterChip(
                                selected = isChecked,
                                onClick = {
                                    BankDetectionStore.setPackageAllowed(context, appOpt.packageName, !isChecked)
                                    allowlistPackages = BankDetectionStore.getAllowedPackages(context)
                                },
                                label = {
                                    Text(
                                        text = "${appOpt.label} (${appOpt.packageName})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bank_detect_launcher_app_${appOpt.packageName}")
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddInstalledAppDialog = false }) {
                    Text(strings.closeDesc, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showBatteryTipsDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryTipsDialog = false },
            modifier = Modifier.testTag("bank_detect_battery_tips_dialog"),
            title = {
                Text(strings.bankDetectBatteryTipsBtn, fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Text(
                    text = when (lang) {
                        "ar" -> "• شاومي (MIUI / HyperOS): فعّل التشغيل التلقائي واختر 'بدون قيود' للبطارية.\n• سامسونج (One UI): أضف مصروفي إلى التطبيقات غير الخاضعة للسكون.\n• هواوي: فعّل الإدارة اليدوية في إطلاق التطبيقات."
                        "darija" -> "• Xiaomi (MIUI / HyperOS): شعل التشغيل التلقائي ودير 'بلا قيود' فـ البطارية.\n• Samsung (One UI): زيد مصروفي فـ التطبيقات لي ما كتنعسش.\n• Huawei: خلي التطبيق خدام فـ الخلفية."
                        "en" -> "• Xiaomi (MIUI / HyperOS): Enable Autostart and set Battery Saver to 'No restrictions'.\n• Samsung (One UI): Add Masroufi to 'Never auto sleeping apps'.\n• Huawei: Enable Manual launch in App Launch settings."
                        else -> "• Xiaomi (MIUI / HyperOS) : activez le Démarrage automatique et réglez la batterie sur « Aucune restriction ».\n• Samsung (One UI) : ajoutez Masroufi aux « Applications jamais en veille ».\n• Huawei : passez le lancement d'application en gestion manuelle."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            },
            dismissButton = {
                TextButton(onClick = { ManualSetup.openBatteryOptimizationSettings(context) }) {
                    Text("Batterie Android", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatteryTipsDialog = false }) {
                    Text(strings.closeDesc, fontWeight = FontWeight.Bold)
                }
            }
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
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("bank_detection_review_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
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
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = strings.bankDetectSectionTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                TextButton(
                    onClick = { showBatteryTipsDialog = true },
                    modifier = Modifier.testTag("bank_detect_battery_tips_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.bankDetectBatteryTipsBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 10A: Privacy contract banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank_detect_privacy_contract_card")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = strings.bankDetectSectionTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = strings.bankDetectPrivacyContractText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Pending items ("To confirm") section
            ExpressiveGlassSurface(
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                accentBorderColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank_detect_pending_section")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${strings.bankDetectToConfirmTitle} (${pendingItems.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        FilledTonalButton(
                            onClick = {
                                BankDetectionStore.postSampleTestBankNotification(context)
                                onRefreshPending()
                            },
                            modifier = Modifier.testTag("bank_detect_test_notif_btn")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.bankDetectPostTestBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (pendingItems.isEmpty()) {
                        Text(
                            text = strings.bankDetectToConfirmTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .testTag("bank_detect_empty_text")
                        )
                    } else {
                        pendingItems.forEach { item ->
                            val duplicateManualTx: TransactionItem? = remember(item, state.cycleTransactions) {
                                BankMessageParser.findRecentManualMatch(
                                    amount = item.amount,
                                    direction = item.direction,
                                    isoDate = item.isoDate,
                                    allTransactions = state.cycleTransactions,
                                    nowMs = item.createdAtMillis
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    1.dp,
                                    if (item.needsReview || duplicateManualTx != null) AccentAmber.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bank_detect_item_${item.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.merchant.ifBlank { item.suggestedCategoryName },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = "${item.suggestedCategoryName} · ${item.isoDate}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        val amountPrefix = when (item.direction) {
                                            "income" -> "+"
                                            "transfer" -> "⇄ "
                                            else -> "-"
                                        }
                                        val amountColor = when (item.direction) {
                                            "income" -> AccentEmerald
                                            "transfer" -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                        Text(
                                            text = "$amountPrefix${PayCycleEngine.formatAmountOrMask(item.amount, state.hideAmounts)} $cur",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = amountColor
                                        )
                                    }

                                    // Badges: Transfer (ATM), Needs Review, or Possible Duplicate
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (item.direction == "transfer") {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                modifier = Modifier.testTag("bank_detect_transfer_badge_${item.id}")
                                            ) {
                                                Text(
                                                    text = strings.bankDetectTransferBadge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (item.needsReview) {
                                            Surface(
                                                shape = CircleShape,
                                                color = AccentAmber.copy(alpha = 0.18f)
                                            ) {
                                                Text(
                                                    text = strings.bankDetectNeedsReviewBadge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = AccentAmber,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (duplicateManualTx != null) {
                                            Surface(
                                                shape = CircleShape,
                                                color = AccentCoral.copy(alpha = 0.18f),
                                                modifier = Modifier.testTag("bank_detect_duplicate_badge_${item.id}")
                                            ) {
                                                Text(
                                                    text = strings.bankDetectDuplicateBadge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = AccentCoral,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Primary Actions: Confirm / Edit / Ignore
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { onConfirmDetection(item) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("bank_detect_confirm_${item.id}")
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(strings.bankDetectConfirmBtn, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                        }

                                        FilledTonalButton(
                                            onClick = { onEditDetection(item) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("bank_detect_edit_${item.id}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(strings.bankDetectEditBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = { onIgnoreDetection(item) },
                                            modifier = Modifier.testTag("bank_detect_ignore_${item.id}")
                                        ) {
                                            Text(strings.bankDetectIgnoreBtn, fontSize = 12.sp)
                                        }
                                    }

                                    // Secondary Actions: Always ignore merchant / Copy masked sample
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (item.merchant.isNotBlank()) {
                                            TextButton(
                                                onClick = { onAlwaysIgnoreMerchant(item) },
                                                modifier = Modifier.testTag("bank_detect_always_ignore_${item.id}")
                                            ) {
                                                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(strings.bankDetectIgnoreMerchantBtn, fontSize = 12.sp)
                                            }
                                        }

                                        TextButton(
                                            onClick = {
                                                val masked = item.maskedSamplePreview.ifBlank {
                                                    BankMessageParser.maskSampleText(
                                                        "Paiement par carte 1234 de ${item.amount.toCanonicalDecimalString()} ${item.currency} chez ${item.merchant} le ${item.isoDate}"
                                                    )
                                                }
                                                sampleMaskPreview = masked
                                                AppSecurityManager.copySensitiveToClipboard(
                                                    context,
                                                    "Masroufi Masked Sample",
                                                    masked
                                                )
                                                onShowMessage("✓ ${strings.bankDetectCopyMaskedBtn}")
                                            },
                                            modifier = Modifier.testTag("bank_detect_copy_masked_${item.id}")
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(strings.bankDetectCopyMaskedBtn, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (sampleMaskPreview.isNotBlank()) {
                        Text(
                            text = sampleMaskPreview,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("bank_detect_masked_sample_preview")
                        )
                    }
                }
            }

            // 10E & 10F: Setup & Allowlist Controls
            ExpressiveGlassSurface(
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank_detect_setup_section")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.bankDetectGrantAccessBtn,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isListenerGranted) AccentEmerald else AccentAmber
                        )
                        FilledTonalButton(
                            onClick = {
                                ManualSetup.openNotificationAccessSettings(context)
                                isListenerGranted = ManualSetup.isNotificationListenerGranted(context)
                            },
                            modifier = Modifier.testTag("bank_detect_grant_access_btn")
                        ) {
                            Text(strings.bankDetectGrantAccessBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { ManualSetup.openAppDetailsForRestrictedSettings(context) },
                            modifier = Modifier.testTag("bank_detect_open_app_info_btn")
                        ) {
                            Text("Android 13+ Info", fontSize = 12.sp)
                        }

                        if (shizukuAvailable) {
                            FilledTonalButton(
                                onClick = {
                                    ShizukuSetup.tryOneTapGrantOrFallback(context) { _, msg ->
                                        isListenerGranted = ManualSetup.isNotificationListenerGranted(context)
                                        onShowMessage(msg)
                                    }
                                },
                                modifier = Modifier.testTag("bank_detect_shizuku_btn")
                            ) {
                                Text(strings.bankDetectShizukuBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Allowlist app selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.bankDetectAllowlistLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (allowlistPackages.isEmpty()) {
                                    "0"
                                } else {
                                    allowlistPackages.joinToString(", ")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { showAllowlistPicker = !showAllowlistPicker },
                            modifier = Modifier.testTag("bank_detect_toggle_allowlist_btn")
                        ) {
                            Text("(${allowlistPackages.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Listener Health & Zero-PII Diagnostic Log (Prompts 3 & 4)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bank_detect_health_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val permStatus = if (isListenerGranted) "✓" else "✕"
                            val connStatus = if (listenerHealth.isConnected) "✓ Connecté" else "En veille"
                            val lastEventLabel = if (listenerHealth.lastEventMillis > 0L) {
                                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                                    .format(java.util.Date(listenerHealth.lastEventMillis))
                            } else "—"
                            Text(
                                text = "Accès: $permStatus · Service: $connStatus · Dernier événement: $lastEventLabel",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isListenerGranted && listenerHealth.isConnected) AccentEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { showDiagnosticLog = !showDiagnosticLog },
                                    modifier = Modifier.testTag("bank_detect_diag_toggle_btn")
                                ) {
                                    Text(
                                        text = "Journal diagnostic (${diagnosticEntries.size}/50)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = { ManualSetup.openBatteryOptimizationSettings(context) },
                                    modifier = Modifier.testTag("bank_detect_battery_opt_btn")
                                ) {
                                    Text("Batterie", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (showDiagnosticLog) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bank_detect_diag_log_list"),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (diagnosticEntries.isEmpty()) {
                                        Text(
                                            text = "Aucun événement enregistré (aucun montant ni texte n'est journalisé).",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        diagnosticEntries.take(20).forEach { entry ->
                                            Text(
                                                text = "${entry.formattedTime()} · ${entry.sourcePackage} · [${entry.stage}:${entry.reasonCode}] (${entry.messageLength}c)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showAllowlistPicker) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                candidateApps.forEach { appOption ->
                                    val selected = appOption.packageName in allowlistPackages
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            BankDetectionStore.setPackageAllowed(context, appOption.packageName, !selected)
                                            allowlistPackages = BankDetectionStore.getAllowedPackages(context)
                                        },
                                        label = { Text(appOption.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.testTag("bank_detect_allowlist_chip_${appOption.packageName}")
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { showAddInstalledAppDialog = true },
                                modifier = Modifier.testTag("bank_detect_add_app_btn")
                            ) {
                                Text("+ Ajouter une app installée", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Auto-confirm under cap
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.bankDetectAutoConfirmLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = autoConfirmEnabled,
                            onCheckedChange = { enabled ->
                                autoConfirmEnabled = enabled
                                BankDetectionStore.setAutoConfirmEnabled(context, enabled)
                            },
                            modifier = Modifier.testTag("bank_detect_auto_confirm_switch")
                        )
                    }

                    // Kill switch 1: Pause detection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.bankDetectPauseLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isPaused,
                            onCheckedChange = { paused ->
                                isPaused = paused
                                BankDetectionStore.setPaused(context, paused)
                            },
                            modifier = Modifier.testTag("bank_detect_pause_switch")
                        )
                    }

                    // Kill switch 2: Delete all detected data
                    FilledTonalButton(
                        onClick = {
                            BankDetectionStore.deleteAllDetectedData(context)
                            allowlistPackages = BankDetectionStore.getAllowedPackages(context)
                            onRefreshPending()
                            onShowMessage(strings.deletedToast)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bank_detect_delete_all_btn")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.bankDetectDeleteAllBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
