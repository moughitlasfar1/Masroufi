package com.example.widget

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.data.DefaultCategoryResolver
import com.example.data.TransactionItem
import com.example.domain.PayCycleEngine
import com.example.domain.Translations
import com.example.domain.VoiceDarijaParser
import com.example.domain.VoiceParsedTransaction
import com.example.security.AppSecurityManager
import com.example.security.MasroufiSecurityLockScreen
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.MasroufiTheme
import com.example.ui.theme.glass3d
import com.example.voice.MoonshineVoiceController
import kotlinx.coroutines.delay

/**
 * Phase 2: Lightweight, translucent Quick Capture Activity (`excludeFromRecents="true"`, `taskAffinity=""`)
 * launched from Home Screen Widgets, Quick Settings Tiles, Launcher Shortcuts, or Smart Notifications.
 *
 * Captures expenses by Moonshine Voice, Compact Keypad, or 1-Tap Preset without opening `MainActivity`,
 * updates `WidgetSnapshot` + Firestore offline cache immediately, shows a 5-second Undo bar, and finishes.
 */
class QuickCaptureActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action ?: MasroufiAppWidget.ACTION_QUICK_ADD
        val presetName = intent?.getStringExtra(MasroufiAppWidget.EXTRA_PRESET_NAME).orEmpty()
        val presetAmount = intent?.getDoubleExtra(MasroufiAppWidget.EXTRA_PRESET_AMOUNT, 0.0) ?: 0.0
        val presetEnvelope = intent?.getStringExtra(MasroufiAppWidget.EXTRA_PRESET_ENVELOPE).orEmpty()

        setContent {
            val context = LocalContext.current
            val snapshot = remember { MasroufiWidgetStore.loadSnapshot(context) }

            MasroufiTheme(
                darkTheme = snapshot.isDarkTheme,
                accentPalette = snapshot.accentPalette
            ) {
                val requiresPinUnlock = remember {
                    AppSecurityManager.isAppLockEnabled(context) &&
                        !AppSecurityManager.isQuickCaptureAllowedWhenLocked(context)
                }
                var isUnlocked by remember { mutableStateOf(!requiresPinUnlock) }

                if (!isUnlocked) {
                    MasroufiSecurityLockScreen(
                        language = snapshot.language,
                        accentPalette = snapshot.accentPalette,
                        onUnlocked = { isUnlocked = true },
                        onSignOutRecovery = { finish() }
                    )
                } else {
                    QuickCaptureOverlayContent(
                        initialAction = action,
                        presetName = presetName,
                        presetAmount = presetAmount,
                        presetEnvelope = presetEnvelope,
                        snapshot = snapshot,
                        onCloseActivity = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun QuickCaptureOverlayContent(
    initialAction: String,
    presetName: String,
    presetAmount: Double,
    presetEnvelope: String,
    snapshot: WidgetSnapshot,
    onCloseActivity: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = Translations.forLang(snapshot.language)
    val cur = snapshot.currencySymbol.ifBlank { "DH" }
    val isDark = snapshot.isDarkTheme

    var mode by remember {
        mutableStateOf(
            when (initialAction) {
                MasroufiAppWidget.ACTION_QUICK_VOICE -> "voice"
                MasroufiAppWidget.ACTION_QUICK_PRESET -> "preset"
                else -> "keypad"
            }
        )
    }

    var savedBatch by remember { mutableStateOf<List<TransactionItem>>(emptyList()) }
    var undoSecondsRemaining by remember { mutableIntStateOf(0) }

    val voiceController = remember(context) {
        MoonshineVoiceController(context.applicationContext, scope)
    }
    val isListening by voiceController.isListening.collectAsState()
    val liveTranscript by voiceController.liveTranscript.collectAsState()
    val waveformLevels by voiceController.waveformLevels.collectAsState()

    var hasAudioPerm by remember {
        mutableStateOf(MoonshineVoiceController.hasRecordAudioPermission(context))
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPerm = granted
        if (granted) {
            voiceController.startListening(snapshot.language)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceController.shutdown()
        }
    }

    // Start Moonshine listening immediately in Voice mode, or auto-log preset in Preset mode
    LaunchedEffect(mode) {
        if (mode == "voice" && savedBatch.isEmpty()) {
            if (MoonshineVoiceController.hasRecordAudioPermission(context)) {
                voiceController.startListening(snapshot.language)
            } else {
                audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else if (mode == "preset" && savedBatch.isEmpty() && presetAmount > 0.0) {
            val envObj = snapshot.envelopes.find { it.key == presetEnvelope }
            val catLabel = envObj?.name ?: DefaultCategoryResolver.resolveTransactionCategoryDisplay(
                presetEnvelope,
                presetEnvelope,
                emptyList(),
                snapshot.language
            )
            val created = MasroufiWidgetStore.recordQuickCaptureTransactions(
                context = context,
                items = listOf(
                    VoiceParsedTransaction(
                        rawTranscript = "$presetName $presetAmount",
                        amount = presetAmount,
                        description = presetName.ifBlank { catLabel },
                        envelopeKey = presetEnvelope.ifBlank { "cafe" },
                        categoryName = catLabel,
                        txType = "expense"
                    )
                )
            )
            savedBatch = created
            undoSecondsRemaining = 5
        }
    }

    // 5-second Undo countdown timer before auto-closing the overlay
    LaunchedEffect(savedBatch) {
        if (savedBatch.isNotEmpty()) {
            undoSecondsRemaining = 5
            while (undoSecondsRemaining > 0) {
                delay(1000L)
                undoSecondsRemaining -= 1
            }
            onCloseActivity()
        }
    }

    val dismissInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f))
            .clickable(
                interactionSource = dismissInteraction,
                indication = null,
                onClick = {
                    voiceController.cancelListening()
                    onCloseActivity()
                }
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        val cardInteraction = remember { MutableInteractionSource() }
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            color = if (isDark) Color(0xFF141824).copy(alpha = 0.97f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
            shadowElevation = 18.dp,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .glass3d(cornerRadius = 28.dp, isDark = isDark)
                .clickable(
                    interactionSource = cardInteraction,
                    indication = null,
                    onClick = {}
                )
                .testTag("quick_capture_root_card")
        ) {
            if (savedBatch.isNotEmpty()) {
                QuickCaptureUndoConfirmationBar(
                    savedBatch = savedBatch,
                    currencySymbol = cur,
                    hideAmounts = snapshot.hideAmounts,
                    secondsRemaining = undoSecondsRemaining,
                    strings = strings,
                    onUndo = {
                        MasroufiWidgetStore.undoQuickCaptureBatch(context, savedBatch)
                        savedBatch = emptyList()
                        onCloseActivity()
                    },
                    onDoneNow = onCloseActivity
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header with Mode Switcher (Voice <-> Keypad) and Close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = mode == "voice",
                                onClick = {
                                    mode = "voice"
                                    if (MoonshineVoiceController.hasRecordAudioPermission(context)) {
                                        voiceController.startListening(snapshot.language)
                                    }
                                },
                                label = { Text(strings.widgetVoiceBtn, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("qc_switch_voice_chip")
                            )
                            FilterChip(
                                selected = mode == "keypad",
                                onClick = {
                                    voiceController.cancelListening()
                                    mode = "keypad"
                                },
                                label = { Text(strings.addOperationTitle, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Dialpad,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("qc_switch_keypad_chip")
                            )
                        }

                        IconButton(
                            onClick = {
                                voiceController.cancelListening()
                                onCloseActivity()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("qc_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.cancelBtn
                            )
                        }
                    }

                    if (mode == "voice") {
                        QuickCaptureVoiceSection(
                            snapshot = snapshot,
                            liveTranscript = liveTranscript,
                            waveformLevels = waveformLevels,
                            isListening = isListening,
                            hasAudioPerm = hasAudioPerm,
                            onRequestPerm = { audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            onTranscriptChange = { voiceController.updateTranscriptManual(it) },
                            onCommitItems = { parsedItems ->
                                voiceController.cancelListening()
                                val created = MasroufiWidgetStore.recordQuickCaptureTransactions(context, parsedItems)
                                if (created.isNotEmpty()) {
                                    savedBatch = created
                                }
                            }
                        )
                    } else {
                        QuickCaptureKeypadSection(
                            snapshot = snapshot,
                            onCommitExpense = { amount, envKey, catName, note ->
                                val created = MasroufiWidgetStore.recordQuickCaptureTransactions(
                                    context = context,
                                    items = listOf(
                                        VoiceParsedTransaction(
                                            rawTranscript = "$catName $amount",
                                            amount = amount,
                                            description = note.ifBlank { catName },
                                            envelopeKey = envKey,
                                            categoryName = catName,
                                            txType = "expense"
                                        )
                                    )
                                )
                                if (created.isNotEmpty()) {
                                    savedBatch = created
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCaptureVoiceSection(
    snapshot: WidgetSnapshot,
    liveTranscript: String,
    waveformLevels: List<Float>,
    isListening: Boolean,
    hasAudioPerm: Boolean,
    onRequestPerm: () -> Unit,
    onTranscriptChange: (String) -> Unit,
    onCommitItems: (List<VoiceParsedTransaction>) -> Unit
) {
    val strings = Translations.forLang(snapshot.language)
    val cur = snapshot.currencySymbol.ifBlank { "DH" }
    val parsedItems = remember(liveTranscript, snapshot.language) {
        VoiceDarijaParser.parseMultiExpenses(
            rawInput = liveTranscript,
            envelopes = emptyList(),
            language = snapshot.language
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Moonshine badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = strings.moonshineBadgeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (!hasAudioPerm) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AccentAmber.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = strings.moonshineMicPermDenied,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onRequestPerm,
                        modifier = Modifier.testTag("qc_grant_mic_btn")
                    ) {
                        Text(strings.saveBtn)
                    }
                }
            }
        }

        // 16-bar acoustic waveform
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("qc_voice_waveform"),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            waveformLevels.forEachIndexed { idx, level ->
                val anim by animateFloatAsState(
                    targetValue = if (isListening) level.coerceIn(0.14f, 1f) else 0.18f,
                    animationSpec = tween(60),
                    label = "qc_wave_$idx"
                )
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height((30.dp * anim).coerceAtLeast(5.dp))
                        .clip(CircleShape)
                        .background(if (parsedItems.isNotEmpty()) AccentEmerald else MaterialTheme.colorScheme.primary)
                )
            }
        }

        OutlinedTextField(
            value = liveTranscript,
            onValueChange = onTranscriptChange,
            placeholder = {
                Text(
                    text = strings.moonshineListeningLabel,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qc_voice_transcript_input")
        )

        // Instant parsed multi-expense preview chips
        AnimatedVisibility(
            visible = parsedItems.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                parsedItems.forEachIndexed { idx, item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AccentEmerald.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.45f)),
                        modifier = Modifier.testTag("qc_parsed_chip_$idx")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${PayCycleEngine.formatAmountOrMask(item.amount, snapshot.hideAmounts)} $cur",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentEmerald
                            )
                        }
                    }
                }
            }
        }

        // Sample phrases row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VoiceDarijaParser.samplePhrasesForLanguage(snapshot.language).take(3).forEachIndexed { idx, phrase ->
                FilterChip(
                    selected = liveTranscript == phrase,
                    onClick = { onTranscriptChange(phrase) },
                    label = { Text(phrase, fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("qc_sample_phrase_$idx")
                )
            }
        }

        val totalAmount = PayCycleEngine.roundMoney(parsedItems.sumOf { it.amount })
        Button(
            onClick = { onCommitItems(parsedItems) },
            enabled = parsedItems.isNotEmpty(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentEmerald,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qc_voice_save_btn")
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (parsedItems.isNotEmpty()) {
                    "${strings.saveBtn} (${PayCycleEngine.formatAmountOrMask(totalAmount, snapshot.hideAmounts)} $cur)"
                } else {
                    strings.moonshineReleaseToSave
                },
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun QuickCaptureKeypadSection(
    snapshot: WidgetSnapshot,
    onCommitExpense: (Double, String, String, String) -> Unit
) {
    val strings = Translations.forLang(snapshot.language)
    val cur = snapshot.currencySymbol.ifBlank { "DH" }
    var rawDigits by remember { mutableStateOf("") }

    val categories = remember(snapshot.envelopes, snapshot.language) {
        if (snapshot.envelopes.isNotEmpty()) {
            snapshot.envelopes.map { it.key to it.name }
        } else {
            listOf("cafe", "marche", "transport", "charges", "autre").map { envKey ->
                envKey to DefaultCategoryResolver.resolveTransactionCategoryDisplay(
                    envKey,
                    envKey,
                    emptyList(),
                    snapshot.language
                )
            }
        }
    }
    var selectedCategory by remember { mutableStateOf(categories.first()) }

    val parsedAmount = rawDigits.toDoubleOrNull() ?: 0.0

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Amount display
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (rawDigits.isEmpty()) "0 $cur" else "$rawDigits $cur",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (parsedAmount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("qc_keypad_amount_display")
                )
                IconButton(
                    onClick = {
                        if (rawDigits.isNotEmpty()) {
                            rawDigits = rawDigits.dropLast(1)
                        }
                    },
                    modifier = Modifier.testTag("qc_keypad_backspace_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace"
                    )
                }
            }
        }

        // Category chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { pair ->
                val isSelected = selectedCategory.first == pair.first
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = pair },
                    label = { Text(pair.second, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium) },
                    modifier = Modifier.testTag("qc_cat_chip_${pair.first}")
                )
            }
        }

        // 3x4 Compact Numeric Keypad
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", "00")
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { rowKeys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowKeys.forEach { key ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (key == "." && rawDigits.contains(".")) return@clickable
                                    if (rawDigits.length < 8) {
                                        rawDigits += key
                                    }
                                }
                                .testTag("qc_key_$key")
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                if (parsedAmount > 0.0) {
                    onCommitExpense(
                        parsedAmount,
                        selectedCategory.first,
                        selectedCategory.second,
                        selectedCategory.second
                    )
                }
            },
            enabled = parsedAmount > 0.0,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qc_keypad_save_btn")
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = strings.saveBtn,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun QuickCaptureUndoConfirmationBar(
    savedBatch: List<TransactionItem>,
    currencySymbol: String,
    hideAmounts: Boolean,
    secondsRemaining: Int,
    strings: com.example.domain.AppStrings,
    onUndo: () -> Unit,
    onDoneNow: () -> Unit
) {
    val total = PayCycleEngine.roundMoney(savedBatch.sumOf { it.amount })
    val formattedTotal = PayCycleEngine.formatCurrencyOrMask(total, currencySymbol, hideAmounts)
    val summaryLabel = if (savedBatch.size == 1) {
        "${savedBatch.first().desc} · $formattedTotal"
    } else {
        "${savedBatch.size} × · $formattedTotal"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag("qc_undo_confirmation_bar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = CircleShape,
                color = AccentEmerald.copy(alpha = 0.2f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = AccentEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = "✓ ${strings.saveBtn} (${secondsRemaining}s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentEmerald,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = summaryLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(
                onClick = onUndo,
                modifier = Modifier.testTag("qc_undo_btn")
            ) {
                Text(
                    text = strings.undoLabel,
                    color = AccentCoral,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            IconButton(
                onClick = onDoneNow,
                modifier = Modifier.testTag("qc_done_now_btn")
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null)
            }
        }
    }
}
