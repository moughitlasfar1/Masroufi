package com.example.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.PayCycleEngine
import com.example.domain.VoiceDarijaParser
import com.example.domain.VoiceParsedTransaction
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.ExpressiveGlassCard
import com.example.ui.theme.ExpressiveGlassSurface
import java.util.Locale

data class RecentCategoryShortcut(
    val envelopeKey: String,
    val categoryName: String
)

fun computeTopThreeRecentCategories(state: DashboardState): List<RecentCategoryShortcut> {
    val lang = state.profile.language
    val recentFromTxs = mutableListOf<RecentCategoryShortcut>()
    for (tx in state.allTransactions.sortedByDescending { it.isoDate }) {
        if (tx.type == "transfer") continue
        val envKey = tx.envelopeKey.ifBlank {
            PayCycleEngine.resolveEnvelopeKey(tx.category, tx.desc, state.envelopes.map { it.config })
        }
        val envConfig = state.envelopes.find { it.config.key == envKey }?.config
        val catLabel = envConfig?.localizedName(lang) ?: tx.category.ifBlank { "Général" }
        if (recentFromTxs.none { it.envelopeKey == envKey }) {
            recentFromTxs.add(RecentCategoryShortcut(envelopeKey = envKey, categoryName = catLabel))
        }
        if (recentFromTxs.size >= 3) break
    }

    if (recentFromTxs.size < 3) {
        for (env in state.envelopes) {
            if (recentFromTxs.none { it.envelopeKey == env.config.key }) {
                recentFromTxs.add(
                    RecentCategoryShortcut(
                        envelopeKey = env.config.key,
                        categoryName = env.config.localizedName(lang)
                    )
                )
            }
            if (recentFromTxs.size >= 3) break
        }
    }

    return recentFromTxs.take(3)
}

@Composable
fun VoiceDarijaEntrySection(
    state: DashboardState,
    autoLaunchVoice: Boolean = false,
    onVoiceParsed: (VoiceParsedTransaction) -> Unit
) {
    val lang = state.profile.language
    val haptics = LocalHapticFeedback.current
    var phraseInput by remember { mutableStateOf("") }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    var showPhraseBar by remember { mutableStateOf(autoLaunchVoice) }

    fun applyPhrase(raw: String) {
        val parsed = VoiceDarijaParser.parse(
            rawInput = raw,
            envelopes = state.envelopes.map { it.config },
            language = lang
        )
        if (parsed != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            voiceStatus = "✓ ${parsed.description} · ${PayCycleEngine.formatDh(parsed.amount)} ${state.customPrefs.currencySymbol.ifBlank { "DH" }} (${parsed.categoryName})"
            onVoiceParsed(parsed)
        } else {
            voiceStatus = when (lang) {
                "ar" -> "مثال: قهوة 15 أو طاكسي 20"
                "darija" -> "قول مثلاً: قهوة 15 أو طاكسي 20"
                "en" -> "Example: Coffee 15 or Taxi 20"
                else -> "Exemple : Café 15 ou Taxi 20"
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val firstSpoken = matches?.firstOrNull().orEmpty()
            if (firstSpoken.isNotBlank()) {
                phraseInput = firstSpoken
                applyPhrase(firstSpoken)
            }
        } else {
            showPhraseBar = true
        }
    }

    fun triggerVoiceRecognizer() {
        showPhraseBar = true
        val speechLang = when (lang) {
            "darija" -> "ar-MA"
            "ar" -> "ar-MA"
            "en" -> "en-US"
            else -> "fr-FR"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLang)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                when (lang) {
                    "darija" -> "قول مثلاً: قهوة 15 أو طاكسي 20"
                    "ar" -> "قل مثلاً: قهوة 15 أو طاكسي 20"
                    "en" -> "Say e.g. 'coffee 15' or 'taxi 20'"
                    else -> "Dites ex: 'café 15' ou 'taxi 20'"
                }
            )
        }
        runCatching {
            speechLauncher.launch(intent)
        }.onFailure {
            voiceStatus = when (lang) {
                "darija" -> "كتب أو ورك على مثال بالدارجة لتحت (مثلاً: قهوة 15)"
                "ar" -> "اكتب أو اختر عبارة صوتية سريعة بالأسفل (مثل: قهوة 15)"
                "en" -> "Type or tap a Darija/voice phrase below (e.g. coffee 15)"
                else -> "Saisissez ou touchez une phrase vocale Darija ci-dessous (ex: café 15)"
            }
        }
    }

    LaunchedEffect(autoLaunchVoice) {
        if (autoLaunchVoice) {
            triggerVoiceRecognizer()
        }
    }

    ExpressiveGlassSurface(
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        accentBorderColor = AccentEmerald,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_darija_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Darija",
                            tint = AccentEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = when (lang) {
                                "darija" -> "إدخال صوتي بالدارجة (قول: قهوة 15)"
                                "ar" -> "الإدخال الصوتي بالدارجة (قل: قهوة 15)"
                                "en" -> "Voice Entry in Darija (e.g. 'coffee 15')"
                                else -> "Saisie Vocale Darija (ex: « qahwa 15 »)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = voiceStatus ?: when (lang) {
                                "darija" -> "كيقرا المبلغ والسمية والفئة أوتوماتيكياً"
                                "ar" -> "يملأ المبلغ والوصف والفئة تلقائياً"
                                "en" -> "Auto-fills amount, title & category"
                                else -> "Pré-remplit montant, titre et catégorie"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (voiceStatus != null) AccentEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalButton(
                    onClick = { triggerVoiceRecognizer() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("voice_entry_mic_btn")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (lang) {
                            "darija" -> "تكلم"
                            "ar" -> "تحدث"
                            "en" -> "Speak"
                            else -> "Parler"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quick 1-tap Darija/voice sample chips + inline phrase input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VoiceDarijaParser.samplePhrasesForLanguage(lang).forEachIndexed { idx, sample ->
                    FilterChip(
                        selected = phraseInput == sample,
                        onClick = {
                            phraseInput = sample
                            applyPhrase(sample)
                        },
                        label = { Text("🎤 $sample", fontSize = 11.5.sp) },
                        modifier = Modifier.testTag("voice_sample_chip_$idx")
                    )
                }
            }

            if (showPhraseBar) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = phraseInput,
                        onValueChange = { phraseInput = it },
                        placeholder = {
                            Text(
                                when (lang) {
                                    "darija" -> "قهوة 15، طاكسي 20، طاكوس 35..."
                                    "ar" -> "قهوة 15، طاكسي 20..."
                                    "en" -> "coffee 15, qahwa 15, taxi 20..."
                                    else -> "qahwa 15, café 15, taxi 20..."
                                },
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voice_phrase_input")
                    )
                    Button(
                        onClick = { applyPhrase(phraseInput) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("voice_phrase_apply_btn")
                    ) {
                        Text(
                            text = when (lang) {
                                "darija" -> "عمر"
                                "ar" -> "تعبئة"
                                "en" -> "Fill"
                                else -> "Remplir"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LargeNumericKeypadSection(
    amountText: String,
    currencySymbol: String,
    language: String,
    twoTapInstantSave: Boolean,
    onToggleTwoTapInstantSave: (Boolean) -> Unit,
    recentCategories: List<RecentCategoryShortcut>,
    selectedEnvelopeKey: String,
    onAmountChange: (String) -> Unit,
    onSelectCategoryShortcut: (RecentCategoryShortcut, Boolean) -> Unit
) {
    val haptics = LocalHapticFeedback.current

    fun appendDigitOrDot(key: String) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val current = amountText.trim()
        when (key) {
            "C" -> onAmountChange("")
            "⌫" -> if (current.isNotEmpty()) onAmountChange(current.dropLast(1))
            "." -> {
                if (!current.contains(".")) {
                    onAmountChange(if (current.isEmpty()) "0." else "$current.")
                }
            }
            else -> {
                val next = if (current == "0") key else current + key
                if (next.length <= 10) {
                    onAmountChange(next)
                }
            }
        }
    }

    fun addQuickIncrement(delta: Int) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val currentVal = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
        val nextVal = currentVal + delta
        val formatted = if (nextVal % 1.0 == 0.0) {
            nextVal.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", nextVal)
        }
        onAmountChange(formatted)
    }

    ExpressiveGlassCard(
        cornerRadius = 26.dp,
        contentPadding = PaddingValues(14.dp),
        accentRimColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("large_numeric_keypad_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Amount Display & 2-Tap Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (language) {
                            "darija" -> "1. كتب المبلغ · 2. ورك على الفئة = تسجلات!"
                            "ar" -> "1. أدخل المبلغ · 2. اضغط الفئة = تم الحفظ!"
                            "en" -> "1. Type amount · 2. Tap category = Done in 2 taps!"
                            else -> "1. Tapez le montant · 2. Touchez une catégorie = Fait !"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = amountText.ifBlank { "0" },
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("keypad_amount_display")
                        )
                        Text(
                            text = currencySymbol,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "2-Tap",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = twoTapInstantSave,
                        onCheckedChange = onToggleTwoTapInstantSave,
                        modifier = Modifier.testTag("two_tap_mode_switch")
                    )
                }
            }

            // 3 Most Recent Categories as Prominent 1-Tap Shortcuts
            if (recentCategories.isNotEmpty()) {
                Text(
                    text = when (language) {
                        "darija" -> "آخر 3 فئات استعملتي (ضغطة وحدة للحفظ):"
                        "ar" -> "آخر 3 فئات مستخدمة (ضغطة واحدة للحفظ):"
                        "en" -> "3 Most Recent Categories (1-tap to finish):"
                        else -> "3 Catégories Récentes (1 geste pour valider) :"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recentCategories.forEachIndexed { idx, shortcut ->
                        val isSelected = selectedEnvelopeKey == shortcut.envelopeKey
                        ExpressiveGlassSurface(
                            shape = RoundedCornerShape(16.dp),
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                            accentBorderColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectCategoryShortcut(shortcut, twoTapInstantSave)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("recent_category_shortcut_$idx")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (twoTapInstantSave) Icons.Default.Bolt else Icons.Default.History,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = shortcut.categoryName.take(14),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick +10 / +20 / +50 / +100 / C row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(10, 20, 50, 100).forEach { inc ->
                    ExpressiveGlassSurface(
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        accentBorderColor = AccentEmerald,
                        onClick = { addQuickIncrement(inc) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("keypad_plus_$inc")
                    ) {
                        Text(
                            text = "+$inc",
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentEmerald,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                ExpressiveGlassSurface(
                    shape = RoundedCornerShape(12.dp),
                    containerColor = AccentCoral.copy(alpha = 0.16f),
                    accentBorderColor = AccentCoral,
                    onClick = { appendDigitOrDot("C") },
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("keypad_clear")
                ) {
                    Text(
                        text = "C",
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentCoral,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // Large Numeric Keypad 4x3 Grid
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf(".", "0", "⌫")
            )
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { key ->
                        val tag = when (key) {
                            "." -> "keypad_dot"
                            "⌫" -> "keypad_backspace"
                            else -> "keypad_digit_$key"
                        }
                        ExpressiveGlassSurface(
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            onClick = { appendDigitOrDot(key) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag(tag)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (key == "⌫") {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Backspace",
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp
                                    )
                                }
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
fun QuickOnboarding30sSheet(
    state: DashboardState,
    onDismiss: () -> Unit,
    onCompleteOnboarding: (Double, Int, List<Triple<String, Double, String>>) -> Unit
) {
    val lang = state.profile.language
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }

    var salaryText by remember {
        mutableStateOf(state.profile.baseBudget.safeRoundToInt().toString())
    }
    var payDayText by remember {
        mutableStateOf(state.profile.payCycleDay.toString())
    }

    val env0 = state.envelopes.getOrNull(0)?.config
    val env1 = state.envelopes.getOrNull(1)?.config
    val env2 = state.envelopes.getOrNull(2)?.config

    var env1Name by remember {
        mutableStateOf(env0?.localizedName(lang) ?: "Charges & Maison")
    }
    var env1Pct by remember {
        mutableStateOf("50")
    }

    var env2Name by remember {
        mutableStateOf(env1?.localizedName(lang) ?: "Transport & Quotidien")
    }
    var env2Pct by remember {
        mutableStateOf("30")
    }

    var env3Name by remember {
        mutableStateOf(env2?.localizedName(lang) ?: "Épargne & Projets")
    }
    var env3Pct by remember {
        mutableStateOf("20")
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("onboarding_30s_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AccentEmerald.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentEmerald
                    )
                }
                Column {
                    Text(
                        text = when (lang) {
                            "darija" -> "إعداد سريع فـ 30 ثانية"
                            "ar" -> "إعداد الميزانية في 30 ثانية"
                            "en" -> "30-Second Quick Setup"
                            else -> "Configuration Express en 30 Secondes"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = when (lang) {
                            "darija" -> "الخلصة، نهار الخلصة، و 3 أغلفة أساسية"
                            "ar" -> "الراتب، يوم الراتب، و 3 أوعية أساسية للميزانية"
                            "en" -> "Salary, pay day, and your 3 core budget envelopes"
                            else -> "Salaire, jour de paie et vos 3 enveloppes principales"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Step 1: Monthly Salary
            Text(
                text = when (lang) {
                    "darija" -> "1. شحال كتشد فـ الشهر ($cur) ؟"
                    "ar" -> "1. الراتب أو الميزانية الشهرية ($cur)"
                    "en" -> "1. Monthly Salary / Budget ($cur)"
                    else -> "1. Salaire / Budget mensuel ($cur)"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("4000", "6000", "8000", "10000", "15000").forEach { presetSalary ->
                    FilterChip(
                        selected = salaryText == presetSalary,
                        onClick = { salaryText = presetSalary },
                        label = { Text("$presetSalary $cur", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("onboarding_salary_chip_$presetSalary")
                    )
                }
            }

            OutlinedTextField(
                value = salaryText,
                onValueChange = { salaryText = it },
                label = { Text("Montant mensuel ($cur)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_salary_input")
            )

            // Step 2: Pay Day (1 - 28)
            Text(
                text = when (lang) {
                    "darija" -> "2. نهار كتدوز الخلصة (1 - 28)"
                    "ar" -> "2. يوم استلام الراتب (1 - 28)"
                    "en" -> "2. Monthly Pay Day (1 - 28)"
                    else -> "2. Jour de virement du salaire (1 - 28)"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("1", "5", "10", "15", "25", "28").forEach { d ->
                    FilterChip(
                        selected = payDayText == d,
                        onClick = { payDayText = d },
                        label = { Text("Jour $d", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("onboarding_payday_chip_$d")
                    )
                }
            }

            OutlinedTextField(
                value = payDayText,
                onValueChange = { payDayText = it },
                label = { Text("Jour du mois (1-28)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_payday_input")
            )

            // Step 3: Three Envelopes (50% / 30% / 20%)
            Text(
                text = when (lang) {
                    "darija" -> "3. تلاتة ديال الأغلفة الأساسية (50% / 30% / 20%)"
                    "ar" -> "3. الأوعية الثلاثة الأساسية (50% / 30% / 20%)"
                    "en" -> "3. Three Core Envelopes (50% / 30% / 20%)"
                    else -> "3. Vos 3 Enveloppes Clés (50% / 30% / 20%)"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            // Envelope 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = env1Name,
                    onValueChange = { env1Name = it },
                    label = { Text("Enveloppe 1 (Essentiels)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("onboarding_env1_name")
                )
                OutlinedTextField(
                    value = env1Pct,
                    onValueChange = { env1Pct = it },
                    label = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .width(82.dp)
                        .testTag("onboarding_env1_pct")
                )
            }

            // Envelope 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = env2Name,
                    onValueChange = { env2Name = it },
                    label = { Text("Enveloppe 2 (Quotidien)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("onboarding_env2_name")
                )
                OutlinedTextField(
                    value = env2Pct,
                    onValueChange = { env2Pct = it },
                    label = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .width(82.dp)
                        .testTag("onboarding_env2_pct")
                )
            }

            // Envelope 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = env3Name,
                    onValueChange = { env3Name = it },
                    label = { Text("Enveloppe 3 (Épargne)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("onboarding_env3_name")
                )
                OutlinedTextField(
                    value = env3Pct,
                    onValueChange = { env3Pct = it },
                    label = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .width(82.dp)
                        .testTag("onboarding_env3_pct")
                )
            }

            Button(
                onClick = {
                    val sal = salaryText.replace(",", ".").toDoubleOrNull() ?: 6400.0
                    val day = (payDayText.toIntOrNull() ?: 1).coerceIn(1, 28)
                    val p1 = (env1Pct.replace(",", ".").toDoubleOrNull() ?: 50.0).coerceIn(5.0, 90.0)
                    val p2 = (env2Pct.replace(",", ".").toDoubleOrNull() ?: 30.0).coerceIn(5.0, 90.0)
                    val p3 = (env3Pct.replace(",", ".").toDoubleOrNull() ?: 20.0).coerceIn(5.0, 90.0)
                    val envs = listOf(
                        Triple(env1Name.ifBlank { "Charges & Maison" }, p1, env0?.key ?: "charges"),
                        Triple(env2Name.ifBlank { "Transport & Quotidien" }, p2, env1?.key ?: "transport"),
                        Triple(env3Name.ifBlank { "Épargne & Projets" }, p3, env2?.key ?: "epargne")
                    )
                    onCompleteOnboarding(sal, day, envs)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_save_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (lang) {
                        "darija" -> "بدا دابا (حفظ فـ 30 ثانية)"
                        "ar" -> "حفظ وبدء الاستخدام"
                        "en" -> "Complete 30s Setup"
                        else -> "Terminer la configuration 30s"
                    },
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitBillToSellefSheet(
    state: DashboardState,
    onDismiss: () -> Unit,
    onSplitBill: (
        billTitle: String,
        totalAmount: Double,
        friendsNamesCsv: String,
        includeSelf: Boolean,
        recordFullExpenseNow: Boolean
    ) -> Unit
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val haptics = LocalHapticFeedback.current
    var billTitle by remember { mutableStateOf("Dîner / Café entre amis") }
    var totalAmountText by remember { mutableStateOf("300") }
    var friendsCsv by remember { mutableStateOf("Yassine, Mehdi") }
    var includeSelf by remember { mutableStateOf(true) }
    var recordExpenseNow by remember { mutableStateOf(true) }

    val parsedFriends = remember(friendsCsv) {
        friendsCsv.split(",", ";", "\n").map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    val totalVal = totalAmountText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val divisor = (if (includeSelf) parsedFriends.size + 1 else parsedFriends.size).coerceAtLeast(1)
    val perPerson = if (totalVal > 0.0) totalVal / divisor else 0.0

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("split_bill_sellef_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🍕 Partager une note (Split Bill → Sellef)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Divisez une addition entre amis : la part de chaque ami est automatiquement ajoutée dans votre suivi Sellef (On me doit) avec rappel WhatsApp.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = billTitle,
                onValueChange = { billTitle = it },
                label = { Text("Titre de la sortie / facture (ex: Restaurant, Foot, Voyage)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("split_bill_title_input")
            )

            OutlinedTextField(
                value = totalAmountText,
                onValueChange = { totalAmountText = it },
                label = { Text("Montant total payé ($cur)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("split_bill_amount_input")
            )

            OutlinedTextField(
                value = friendsCsv,
                onValueChange = { friendsCsv = it },
                label = { Text("Prénoms des amis (séparés par une virgule)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("split_bill_friends_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "M'inclure dans le partage (${parsedFriends.size + if (includeSelf) 1 else 0} pers.)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = includeSelf,
                    onCheckedChange = { includeSelf = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enregistrer aussi la dépense totale dans mes opérations",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = recordExpenseNow,
                    onCheckedChange = { recordExpenseNow = it }
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Part par personne : ${PayCycleEngine.formatDh(perPerson)} $cur",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${parsedFriends.size} entrée(s) Sellef créée(s) : ${parsedFriends.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            Button(
                onClick = {
                    if (totalVal > 0.0 && parsedFriends.isNotEmpty()) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSplitBill(
                            billTitle,
                            totalVal,
                            friendsCsv,
                            includeSelf,
                            recordExpenseNow
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("split_bill_confirm_btn")
            ) {
                Text("Créer les ${parsedFriends.size} Sellef (${PayCycleEngine.formatDh(perPerson)} $cur/pers.)", fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun HouseholdInviteSectionCard(
    userId: String,
    lang: String,
    onShareInvite: (String) -> Unit
) {
    val inviteCode = remember(userId) {
        val clean = userId.filter { it.isLetterOrDigit() }.uppercase()
        "MSR-${clean.take(6).padEnd(6, 'X')}"
    }
    var partnerCodeInput by remember { mutableStateOf("") }
    var linkedPartnerCode by remember { mutableStateOf<String?>(null) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("household_sharing_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (lang) {
                    "ar" -> "👨‍👩‍👧 حساب العائلة أو الشريك (دعوة مشتركة)"
                    "darija" -> "👨‍👩‍👧 حساب الدار أو الشريك (Household Invite)"
                    "en" -> "👨‍👩‍👧 Household or Partner Account via Invite"
                    else -> "👨‍👩‍👧 Compte Foyer / Partenaire (Invitation)"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Code d'invitation de votre foyer : $inviteCode",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = partnerCodeInput,
                    onValueChange = { partnerCodeInput = it.uppercase().take(14) },
                    label = { Text("Code partenaire (ex: MSR-AB12CD)", fontSize = 11.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("household_partner_code_input")
                )
                FilledTonalButton(
                    onClick = {
                        if (partnerCodeInput.trim().length >= 4) {
                            linkedPartnerCode = partnerCodeInput.trim()
                        } else {
                            onShareInvite(
                                "Salam ! Rejoins notre budget foyer sur Masroufi avec le code d'invitation : $inviteCode 🏠"
                            )
                        }
                    },
                    modifier = Modifier.testTag("btn_household_invite_action")
                ) {
                    Text(
                        text = if (partnerCodeInput.trim().length >= 4) "Lier" else "Inviter",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (!linkedPartnerCode.isNullOrBlank()) {
                Text(
                    text = "✓ Foyer lié au code partenaire : $linkedPartnerCode",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentEmerald,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

