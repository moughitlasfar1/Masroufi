package com.example.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomizationPrefs
import com.example.domain.AppStrings
import com.example.domain.Translations
import com.example.notifications.MasroufiNotificationManager
import com.example.security.AppSecurityManager
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.BackgroundWallpaperLayer
import com.example.ui.theme.BackgroundWallpaperManager
import com.example.ui.theme.ExpressiveGlassSurface
import com.example.ui.theme.MasroufiPalettes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    state: DashboardState,
    strings: AppStrings,
    moreFeaturesEnabled: Boolean = true,
    onMoreFeaturesChanged: (Boolean) -> Unit = {},
    isLocalMode: Boolean = false,
    onLinkGoogleAccount: () -> Unit = {},
    onDismiss: () -> Unit,
    onSaveSettings: (Double, Int, Boolean) -> Unit,
    onToggleTheme: () -> Unit = {},
    onSelectThemeMode: (String) -> Unit = {},
    onSelectLanguage: (String) -> Unit = {},
    onSaveCustomPrefs: (CustomizationPrefs) -> Unit = {},
    onOpenCustomizationStudio: () -> Unit = {},
    onOpenQuickOnboarding: () -> Unit = {},
    onLoadDemoData: () -> Unit = {},
    onResetCleanAccount: () -> Unit = {},
    onFlagSecureChanged: (Boolean) -> Unit = {},
    onAppLockChanged: (Boolean) -> Unit = {},
    onClearClipboard: () -> Unit = {},
    onExportJsonFile: (String) -> Unit = {},
    onExportCsvFile: () -> Unit = {},
    onOpenRestoreModal: () -> Unit = {},
    onOpenBankDetections: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val lang = state.profile.language
    var budgetText by remember { mutableStateOf(state.profile.baseBudget.safeRoundToInt().toString()) }
    var payDayText by remember { mutableStateOf(state.profile.payCycleDay.toString()) }
    var rolloverEnabled by remember { mutableStateOf(state.profile.rolloverEnabled) }
    var localMoreFeatures by remember(moreFeaturesEnabled) { mutableStateOf(moreFeaturesEnabled) }

    var flagSecureEnabled by remember { mutableStateOf(AppSecurityManager.isFlagSecureEnabled(context)) }
    var appLockEnabled by remember { mutableStateOf(AppSecurityManager.isAppLockEnabled(context)) }
    var allowDeviceCredential by remember { mutableStateOf(AppSecurityManager.isDeviceCredentialAllowed(context)) }
    var hasPinConfigured by remember { mutableStateOf(AppSecurityManager.hasPinConfigured(context)) }
    var newPinInput by remember { mutableStateOf("") }
    var pinFeedback by remember { mutableStateOf<String?>(null) }

    val uiPrefs = remember(context) {
        context.getSharedPreferences("masroufi_ui_prefs", Context.MODE_PRIVATE)
    }
    var appLockHintDismissed by remember {
        mutableStateOf(uiPrefs.getBoolean("applock_hint_dismissed", false))
    }

    var notifyBills by remember { mutableStateOf(MasroufiNotificationManager.isNotifyBillsEnabled(context)) }
    var notifyDaret by remember { mutableStateOf(MasroufiNotificationManager.isNotifyDaretEnabled(context)) }
    var notifyBudget80 by remember { mutableStateOf(MasroufiNotificationManager.isNotifyBudget80Enabled(context)) }
    var notifyEvening by remember { mutableStateOf(MasroufiNotificationManager.isNotifyEveningEnabled(context)) }
    var smartSuggestionsEnabled by remember { mutableStateOf(MasroufiNotificationManager.isSmartSuggestionsEnabled(context)) }
    var notifFeedback by remember { mutableStateOf<String?>(null) }

    var backupPassword by remember { mutableStateOf("") }
    var showResetConfirmationDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            MasroufiNotificationManager.triggerImmediateCheck(context)
            notifFeedback = "✓"
        }
    }

    if (showResetConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmationDialog = false },
            modifier = Modifier.testTag("confirm_reset_account_dialog"),
            title = {
                Text(strings.resetConfirmTitle, fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Text(strings.resetConfirmBody)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmationDialog = false
                        onResetCleanAccount()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_reset_account_btn")
                ) {
                    Text(strings.confirmResetBtn, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmationDialog = false },
                    modifier = Modifier.testTag("cancel_reset_account_btn")
                ) {
                    Text(strings.cancelBtn)
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
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isLocalMode) {
                ExpressiveGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    accentBorderColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.linkGoogleBtn,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = strings.offlineSavedBadge,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onDismiss()
                                onLinkGoogleAccount()
                            },
                            modifier = Modifier.testTag("settings_link_google_account_btn")
                        ) {
                            Text(strings.linkGoogleBtn, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // SECTION 1: BUDGET
            ExpressiveGlassSurface(
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_section_budget")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.settingsSecBudget,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )

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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = rolloverEnabled,
                            onCheckedChange = { rolloverEnabled = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onDismiss()
                                onOpenQuickOnboarding()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_open_30s_onboarding_btn")
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("30s", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val b = budgetText.replace(",", ".").toDoubleOrNull() ?: 6400.0
                                val d = (payDayText.toIntOrNull() ?: 1).coerceIn(1, 28)
                                onSaveSettings(b, d, rolloverEnabled)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(2f)
                                .testTag("settings_save_budget_btn")
                        ) {
                            Text(strings.saveBtn, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // SECTION 2: APPARENCE (Instant save: Light/Dark, 3 Palettes, Currency, Language, More features)
            ExpressiveGlassSurface(
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_section_appearance")
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
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = strings.settingsSecAppearance,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // 3-Way Theme Mode Selector: Device (Auto) / Light / Dark (Instant save)
                    val activeThemeMode = state.customPrefs.normalizedThemeMode()
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.themeModeLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple("system", "📱 ${strings.themeModeSystem}", "settings_theme_mode_system"),
                                Triple("light", "☀️ ${strings.themeModeLight}", "settings_theme_mode_light"),
                                Triple("dark", "🌙 ${strings.themeModeDark}", "settings_theme_mode_dark")
                            ).forEach { (modeKey, label, tag) ->
                                val isSelected = activeThemeMode == modeKey
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectThemeMode(modeKey)
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                    },
                                    modifier = Modifier.testTag(tag)
                                )
                            }
                        }
                    }

                    // 3 Palettes (Indigo, Émeraude, Or)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MasroufiPalettes.forEach { pal ->
                            val isSelected = state.customPrefs.accentPalette == pal.key
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    onSaveCustomPrefs(state.customPrefs.copy(accentPalette = pal.key))
                                },
                                label = { Text(pal.localizedLabel(lang), fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("settings_palette_${pal.key}")
                            )
                        }
                    }

                    // Background Image & Moroccan Wallpapers Selector
                    BackgroundWallpaperPickerSection(
                        strings = strings,
                        isDarkTheme = state.profile.isDarkTheme,
                        tagPrefix = "settings"
                    )

                    // Language Selector (FR / دارجة / عربي / EN)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "fr" to "FR",
                            "darija" to "دارجة",
                            "ar" to "عربي",
                            "en" to "EN"
                        ).forEach { (code, label) ->
                            FilterChip(
                                selected = lang == code,
                                onClick = { onSelectLanguage(code) },
                                label = { Text(label, fontWeight = FontWeight.ExtraBold) },
                                modifier = Modifier.testTag("settings_lang_$code")
                            )
                        }
                    }

                    // Currency Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("DH", "MAD", "د.م.", "€", "$").forEach { sym ->
                            FilterChip(
                                selected = state.customPrefs.currencySymbol.ifBlank { "DH" } == sym,
                                onClick = {
                                    onSaveCustomPrefs(state.customPrefs.copy(currencySymbol = sym))
                                },
                                label = { Text(sym, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }

                    // More features toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_more_features_card"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.moreFeaturesTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = strings.moreFeaturesSub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = localMoreFeatures,
                            onCheckedChange = { enabled ->
                                localMoreFeatures = enabled
                                onMoreFeaturesChanged(enabled)
                            },
                            modifier = Modifier.testTag("settings_more_features_switch")
                        )
                    }
                }
            }

            // SECTION 3: SÉCURITÉ
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
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = strings.settingsSecSecurity,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (!appLockEnabled && !appLockHintDismissed) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("app_lock_suggestion_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (lang) {
                                        "ar" -> "💡 نصيحة: فعّل قفل التطبيق لحماية بياناتك."
                                        "darija" -> "💡 نصيحة: فعّل قفل التطبيق باش تحمي حسابك."
                                        "en" -> "💡 Tip: Enable App Lock to protect your budget."
                                        else -> "💡 Conseil : activez le verrouillage PIN / Biométrie."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        appLockHintDismissed = true
                                        uiPrefs.edit().putBoolean("applock_hint_dismissed", true).apply()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = strings.closeDesc,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (lang) {
                                "ar" -> "قفل التطبيق (PIN / بصمة)"
                                "darija" -> "قفل التطبيق (PIN / بصمة)"
                                "en" -> "App Lock (PIN / Biometrics)"
                                else -> "Verrouillage (PIN / Biométrie)"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !hasPinConfigured && !AppSecurityManager.isDeviceSecure(context)) {
                                    pinFeedback = "6-8 digits"
                                } else {
                                    appLockEnabled = enabled
                                    AppSecurityManager.setAppLockEnabled(context, enabled)
                                    onAppLockChanged(enabled)
                                }
                            },
                            modifier = Modifier.testTag("settings_app_lock_switch")
                        )
                    }

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
                            label = { Text("PIN (6-8)", fontSize = 12.sp) },
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
                                    newPinInput = ""
                                    pinFeedback = "✓ PIN"
                                } else {
                                    pinFeedback = "6-8 digits"
                                }
                            },
                            modifier = Modifier.testTag("settings_save_pin_btn")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PIN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!pinFeedback.isNullOrBlank()) {
                        Text(
                            text = pinFeedback!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (lang) {
                                "ar" -> "فتح عبر بصمة الجهاز"
                                "darija" -> "فتح ببصمة التليفون"
                                "en" -> "Allow Biometrics"
                                else -> "Autoriser Biométrie"
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

                    var allowQuickCaptureWhenLocked by remember {
                        mutableStateOf(AppSecurityManager.isQuickCaptureAllowedWhenLocked(context))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.quickCaptureWhenLockedLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = allowQuickCaptureWhenLocked,
                            onCheckedChange = { allowed ->
                                allowQuickCaptureWhenLocked = allowed
                                AppSecurityManager.setQuickCaptureAllowedWhenLocked(context, allowed)
                            },
                            modifier = Modifier.testTag("settings_quick_capture_locked_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (lang) {
                                "ar" -> "منع تصوير الشاشة"
                                "darija" -> "منع السكرين شوت"
                                "en" -> "Block Screenshots"
                                else -> "Bloquer Captures d'écran"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
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
                }
            }

            // SECTION 3B: WIDGETS & TUILES RAPIDES (Phase 3)
            var tileFeedback by remember { mutableStateOf<String?>(null) }
            ExpressiveGlassSurface(
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_widgets_tiles_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.settingsTilesAndWidgetsSection,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                com.example.widget.requestAddMasroufiTile(
                                    context = context,
                                    tileServiceClass = com.example.widget.MasroufiVoiceTileService::class.java,
                                    label = strings.tileVoiceLabel,
                                    onResultMessage = { tileFeedback = it }
                                )
                            },
                            modifier = Modifier.testTag("settings_add_voice_tile_btn")
                        ) {
                            Text(strings.addVoiceTileBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = {
                                com.example.widget.requestAddMasroufiTile(
                                    context = context,
                                    tileServiceClass = com.example.widget.MasroufiQuickTileService::class.java,
                                    label = strings.tileKeypadLabel,
                                    onResultMessage = { tileFeedback = it }
                                )
                            },
                            modifier = Modifier.testTag("settings_add_keypad_tile_btn")
                        ) {
                            Text(strings.addKeypadTileBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = {
                                com.example.widget.requestAddMasroufiTile(
                                    context = context,
                                    tileServiceClass = com.example.widget.MasroufiSafeTodayTileService::class.java,
                                    label = strings.tileLeftTodayLabel,
                                    onResultMessage = { tileFeedback = it }
                                )
                            },
                            modifier = Modifier.testTag("settings_add_safe_tile_btn")
                        ) {
                            Text(strings.addSafeTodayTileBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!tileFeedback.isNullOrBlank()) {
                        Text(
                            text = tileFeedback!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Phase 11A: Optional "Masroufi Voice" launcher icon alias
                    var voiceAliasEnabled by remember {
                        mutableStateOf(com.example.notifications.VoiceLauncherAliasManager.isVoiceAliasEnabled(context))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.voiceLauncherAliasToggleLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = voiceAliasEnabled,
                            onCheckedChange = { enabled ->
                                voiceAliasEnabled = enabled
                                com.example.notifications.VoiceLauncherAliasManager.setVoiceAliasEnabled(context, enabled)
                            },
                            modifier = Modifier.testTag("settings_voice_launcher_alias_switch")
                        )
                    }

                    // Google Assistant ("Ok Google") Expense Integration Section
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_google_assistant_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = strings.assistantSectionTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = strings.assistantSectionSub,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = strings.assistantTryCommandsLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )

                            val sampleAssistantCommands = remember(lang) {
                                com.example.widget.GoogleAssistantExpenseBridge.sampleVoiceCommandsForLang(lang)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                sampleAssistantCommands.forEachIndexed { idx, (spokenLabel, queryPayload) ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            com.example.widget.GoogleAssistantExpenseBridge.launchSimulatedAssistantExpense(
                                                context = context,
                                                sampleQuery = queryPayload
                                            )
                                        },
                                        label = {
                                            Text(spokenLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        },
                                        modifier = Modifier.testTag("settings_assistant_sample_$idx")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        com.example.widget.GoogleAssistantExpenseBridge.requestPinAssistantVoiceShortcut(
                                            context = context,
                                            language = lang,
                                            onResult = { msg -> tileFeedback = msg }
                                        )
                                    },
                                    modifier = Modifier.testTag("settings_pin_assistant_shortcut_btn")
                                ) {
                                    Text(strings.assistantPinShortcutBtn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val samplePhrase = when (lang) {
                                            "ar" -> "قهوة 15 درهم"
                                            "darija" -> "9ahwa 15 DH"
                                            "en" -> "Coffee 15 DH"
                                            else -> "Café 15 DH"
                                        }
                                        com.example.widget.GoogleAssistantExpenseBridge.launchSimulatedAssistantExpense(
                                            context = context,
                                            sampleQuery = samplePhrase
                                        )
                                    },
                                    modifier = Modifier.testTag("settings_test_assistant_command_btn")
                                ) {
                                    Text(strings.assistantTestCommandBtn, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    // Phase 10: Bank Notification Auto-Detection entry
                    val pendingDetectionsCount = remember {
                        com.example.notifications.BankDetectionStore.loadPendingItems(context).size
                    }
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onOpenBankDetections()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_bank_detections_btn")
                    ) {
                        Text(
                            text = if (pendingDetectionsCount > 0) {
                                "${strings.bankDetectSectionTitle} ($pendingDetectionsCount)"
                            } else {
                                strings.bankDetectSectionTitle
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // SECTION 3C: PHASE 9 — OPTIONAL QUICK-CAPTURE NOTIFICATION (Off by default)
            var qcNotifEnabled by remember {
                mutableStateOf(com.example.notifications.QuickCaptureNotification.isEnabled(context))
            }
            var qcShowLeftToday by remember {
                mutableStateOf(com.example.notifications.QuickCaptureNotification.isShowLeftTodayEnabled(context))
            }
            var qcLockScreenAmounts by remember {
                mutableStateOf(com.example.notifications.QuickCaptureNotification.isShowLockScreenAmountsEnabled(context))
            }
            var qcQuietHours by remember {
                mutableStateOf(com.example.notifications.QuickCaptureNotification.isQuietHoursEnabled(context))
            }
            var qcSelectedPresetId by remember {
                mutableStateOf<String>(com.example.notifications.QuickCaptureNotification.getSelectedPresetId(context))
            }
            var qcPermExplanation by remember { mutableStateOf<String?>(null) }

            val qcNotifPermLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    qcNotifEnabled = true
                    qcPermExplanation = null
                    com.example.notifications.QuickCaptureNotification.setEnabled(context, true)
                } else {
                    qcNotifEnabled = false
                    com.example.notifications.QuickCaptureNotification.setEnabled(context, false)
                    qcPermExplanation = strings.quickCaptureNotifPermDeniedMsg
                }
            }

            ExpressiveGlassSurface(
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_quick_capture_notif_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.quickCaptureNotifToggleLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.quickCaptureNotifToggleLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = qcNotifEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (MasroufiNotificationManager.hasNotificationPermission(context)) {
                                        qcNotifEnabled = true
                                        qcPermExplanation = null
                                        com.example.notifications.QuickCaptureNotification.setEnabled(context, true)
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        qcNotifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    qcNotifEnabled = false
                                    com.example.notifications.QuickCaptureNotification.setEnabled(context, false)
                                }
                            },
                            modifier = Modifier.testTag("settings_qc_notif_switch")
                        )
                    }

                    if (!qcPermExplanation.isNullOrBlank()) {
                        Text(
                            text = qcPermExplanation!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentCoral,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("settings_qc_notif_perm_error")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.quickCaptureNotifShowLeftTodayLabel,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = qcShowLeftToday,
                            onCheckedChange = { enabled ->
                                qcShowLeftToday = enabled
                                com.example.notifications.QuickCaptureNotification.setShowLeftTodayEnabled(context, enabled)
                            },
                            modifier = Modifier.testTag("settings_qc_show_left_today_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.quickCaptureNotifLockScreenAmountsLabel,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = qcLockScreenAmounts,
                            onCheckedChange = { enabled ->
                                qcLockScreenAmounts = enabled
                                com.example.notifications.QuickCaptureNotification.setShowLockScreenAmountsEnabled(context, enabled)
                            },
                            modifier = Modifier.testTag("settings_qc_lock_screen_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.quickCaptureNotifQuietHoursLabel,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = qcQuietHours,
                            onCheckedChange = { enabled ->
                                qcQuietHours = enabled
                                com.example.notifications.QuickCaptureNotification.setQuietHoursEnabled(context, enabled)
                            },
                            modifier = Modifier.testTag("settings_qc_quiet_hours_switch")
                        )
                    }

                    if (state.presets.isNotEmpty()) {
                        Text(
                            text = strings.quickCaptureNotifPresetLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.presets.take(5).forEach { preset ->
                                val isSel = qcSelectedPresetId == preset.id
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        val next = if (isSel) "" else preset.id
                                        qcSelectedPresetId = next
                                        com.example.notifications.QuickCaptureNotification.setSelectedPresetId(context, next)
                                    },
                                    label = { Text(preset.name, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.testTag("settings_qc_preset_${preset.id}")
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 4: ALERTES (Instant save)
            ExpressiveGlassSurface(
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = strings.settingsSecAlerts,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.smartSuggestionsTitle,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.smartSuggestionsSub,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    MasroufiNotificationManager.resetAllSmartLearning(context)
                                    notifFeedback = strings.resetLearningToast
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("settings_reset_learning_btn")
                            ) {
                                Text(
                                    text = strings.resetLearningBtn,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Switch(
                                checked = smartSuggestionsEnabled,
                                onCheckedChange = { enabled ->
                                    smartSuggestionsEnabled = enabled
                                    MasroufiNotificationManager.setSmartSuggestionsEnabled(context, enabled)
                                },
                                modifier = Modifier.testTag("settings_smart_suggestions_switch")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.billsTitle,
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
                            text = "80% ${strings.budgetLabel}",
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

                    var eveningReminderTime by remember {
                        mutableStateOf(MasroufiNotificationManager.getEveningReminderTime(context))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (lang) {
                                    "ar" -> "تذكير مسائي / أسبوعي"
                                    "darija" -> "تذكير العشية / السيمانة"
                                    "en" -> "Evening & Weekly Digest"
                                    else -> "Rappel du soir & hebdo"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${strings.eveningReminderTimeLabel} : ${String.format(java.util.Locale.US, "%02d:%02d", eveningReminderTime.first, eveningReminderTime.second)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("settings_evening_time_display")
                            )
                        }
                        Switch(
                            checked = notifyEvening,
                            onCheckedChange = { enabled ->
                                notifyEvening = enabled
                                MasroufiNotificationManager.setNotifyEveningEnabled(context, enabled)
                            },
                            modifier = Modifier.testTag("settings_notify_evening_switch")
                        )
                    }

                    if (notifyEvening) {
                        val timePresets = listOf(
                            19 to 30,
                            20 to 0,
                            20 to 30,
                            21 to 0,
                            21 to 30,
                            22 to 0
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .testTag("settings_evening_time_chips_row"),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            timePresets.forEach { (h, m) ->
                                val label = String.format(java.util.Locale.US, "%02d:%02d", h, m)
                                val isSel = eveningReminderTime.first == h && eveningReminderTime.second == m
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        eveningReminderTime = h to m
                                        MasroufiNotificationManager.setEveningReminderTime(context, h, m)
                                    },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.testTag("settings_evening_time_chip_${h}_${m}")
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    runCatching {
                                        android.app.TimePickerDialog(
                                            context,
                                            { _, pickedHour, pickedMinute ->
                                                eveningReminderTime = pickedHour to pickedMinute
                                                MasroufiNotificationManager.setEveningReminderTime(
                                                    context,
                                                    pickedHour,
                                                    pickedMinute
                                                )
                                            },
                                            eveningReminderTime.first,
                                            eveningReminderTime.second,
                                            true
                                        ).show()
                                    }
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("settings_evening_time_picker_btn")
                            ) {
                                Text(
                                    text = String.format(
                                        java.util.Locale.US,
                                        "🕒 %02d:%02d",
                                        eveningReminderTime.first,
                                        eveningReminderTime.second
                                    ),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (!notifFeedback.isNullOrBlank()) {
                        Text(
                            text = notifFeedback!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // SECTION 5: DONNÉES (Backup, Restore, CSV, Reset with confirmation dialog)
            ExpressiveGlassSurface(
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_section_data")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.settingsSecData,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )

                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = {
                            Text("AES-256-GCM (10+ car.)", fontSize = 11.5.sp)
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_encryption_password_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onExportJsonFile(backupPassword.trim()) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_json_file_btn")
                        ) {
                            Text("JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = {
                                onDismiss()
                                onOpenRestoreModal()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_restore_modal_btn")
                        ) {
                            Text(strings.btnRestoreJson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = onExportCsvFile,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_csv_file_btn")
                        ) {
                            Text("CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    FilledTonalButton(
                        onClick = { showResetConfirmationDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_reset_clean_btn")
                    ) {
                        Text(strings.cleanAccountBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onSignOut()
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
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
    var selectedThemeMode by remember { mutableStateOf(state.customPrefs.normalizedThemeMode()) }
    var eurRateInput by remember { mutableStateOf(state.customPrefs.normalizedEurRate().toString()) }
    var usdRateInput by remember { mutableStateOf(state.customPrefs.normalizedUsdRate().toString()) }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val strings = Translations.forLang(lang)
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

            // 3-Way Theme Mode Selector in Studio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "system" to "📱 ${strings.themeModeSystem}",
                    "light" to "☀️ ${strings.themeModeLight}",
                    "dark" to "🌙 ${strings.themeModeDark}"
                ).forEach { (modeKey, label) ->
                    FilterChip(
                        selected = selectedThemeMode == modeKey,
                        onClick = { selectedThemeMode = modeKey },
                        label = { Text(label, fontWeight = FontWeight.Bold) }
                    )
                }
            }

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
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .width(120.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) pal.primaryLight else Color.Transparent,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { selectedPalette = pal.key }
                            .testTag("palette_option_${pal.key}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
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

            // Background Image & Moroccan Wallpapers Selector in Studio
            BackgroundWallpaperPickerSection(
                strings = strings,
                isDarkTheme = state.profile.isDarkTheme,
                tagPrefix = "studio"
            )

            // Editable EUR & USD Exchange Rates (persisted in CustomizationPrefs)
            Text(
                text = strings.fxRatesSectionTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = eurRateInput,
                    onValueChange = { eurRateInput = it },
                    label = { Text(strings.fxEurRateLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("studio_eur_rate_input")
                )
                OutlinedTextField(
                    value = usdRateInput,
                    onValueChange = { usdRateInput = it },
                    label = { Text(strings.fxUsdRateLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("studio_usd_rate_input")
                )
            }

            Button(
                onClick = {
                    val parsedEur = eurRateInput.replace(",", ".").toDoubleOrNull()
                        ?.takeIf { !it.isNaN() && !it.isInfinite() && it in 0.5..500.0 }
                        ?: state.customPrefs.normalizedEurRate()
                    val parsedUsd = usdRateInput.replace(",", ".").toDoubleOrNull()
                        ?.takeIf { !it.isNaN() && !it.isInfinite() && it in 0.5..500.0 }
                        ?: state.customPrefs.normalizedUsdRate()
                    onSaveCustomPrefs(
                        state.customPrefs.copy(
                            accentPalette = selectedPalette,
                            currencySymbol = currencySymbol.ifBlank { "DH" },
                            themeMode = selectedThemeMode,
                            eurToMadRate = parsedEur,
                            usdToMadRate = parsedUsd
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

@Composable
fun BackgroundWallpaperPickerSection(
    strings: AppStrings,
    isDarkTheme: Boolean,
    tagPrefix: String = "settings"
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        BackgroundWallpaperManager.ensureLoaded(context)
    }
    val wallpaperState by BackgroundWallpaperManager.stateFlow.collectAsState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            BackgroundWallpaperManager.saveCustomPhotoFromUri(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${tagPrefix}_bg_wallpaper_section"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.bgImageSectionLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (wallpaperState.isActive) {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .testTag("${tagPrefix}_bg_live_preview_box")
                ) {
                    BackgroundWallpaperLayer(
                        wallpaperState = wallpaperState,
                        accentColor = MaterialTheme.colorScheme.primary,
                        isDarkTheme = isDarkTheme
                    )
                }
            }
        }

        // Preset & Custom Photo Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = buildList {
                add("none" to strings.bgPresetNone)
                add("zellige" to "✨ ${strings.bgPresetZellige}")
                add("atlas" to "⛰️ ${strings.bgPresetAtlas}")
                add("sahara" to "🏜️ ${strings.bgPresetSahara}")
                if (wallpaperState.hasSavedCustomPhoto) {
                    add("custom" to "🖼️ ${strings.bgPresetCustomPhoto}")
                }
            }
            presets.forEach { (modeKey, label) ->
                val isSelected = wallpaperState.mode == modeKey
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        BackgroundWallpaperManager.setWallpaperMode(context, modeKey)
                    },
                    label = {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                        )
                    },
                    modifier = Modifier.testTag("${tagPrefix}_bg_preset_$modeKey")
                )
            }
        }

        // Action row: Pick photo from gallery + Remove custom photo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.testTag("${tagPrefix}_pick_bg_image_btn")
            ) {
                Text(
                    text = strings.bgPickPhotoBtn,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (wallpaperState.hasSavedCustomPhoto) {
                TextButton(
                    onClick = {
                        BackgroundWallpaperManager.clearCustomPhoto(context)
                    },
                    modifier = Modifier.testTag("${tagPrefix}_clear_bg_image_btn")
                ) {
                    Text(
                        text = strings.bgRemovePhotoBtn,
                        fontSize = 12.sp,
                        color = AccentCoral,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Readability Dimmer level when any background wallpaper/photo is active
        if (wallpaperState.isActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.bgDimLevelLabel} :",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                listOf(
                    "light" to strings.bgDimLight,
                    "medium" to strings.bgDimMedium,
                    "strong" to strings.bgDimStrong
                ).forEach { (dimKey, dimLabel) ->
                    val isSelected = wallpaperState.dimLevel == dimKey
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            BackgroundWallpaperManager.setDimLevel(context, dimKey)
                        },
                        label = {
                            Text(
                                text = dimLabel,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("${tagPrefix}_bg_dim_$dimKey")
                    )
                }
            }
        }
    }
}

