package com.example.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.example.domain.EnvelopeStatus
import com.example.domain.PayCycleEngine
import com.example.domain.PurchaseVerdict
import com.example.domain.Translations
import com.example.domain.VoiceDarijaParser
import com.example.domain.VoiceParsedTransaction
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.ExpressiveGlassCard
import com.example.ui.theme.ExpressiveGlassSurface
import com.masroufi.core.Money
import com.masroufi.core.MoneyParser
import com.masroufi.core.sumMoney
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class RecentCategoryShortcut(
    val envelopeKey: String,
    val categoryName: String
)

data class UsualSpendShortcut(
    val desc: String,
    val amount: Money,
    val envelopeKey: String,
    val accountId: String = "acc_cash",
    val categoryName: String = "",
    val score: Double = 0.0,
    val occurrences: Int = 1,
    val latestIsoDate: String = ""
) {
    val description: String get() = desc
    val typicalAmount: Money get() = amount
    val matchCount: Int get() = occurrences
}

typealias TimeAwareShortcut = UsualSpendShortcut

fun timeBlockForHour(hour: Int): Int = when (hour) {
    in 5..10 -> 0   // Morning
    in 11..14 -> 1  // Noon
    in 15..17 -> 2  // Afternoon
    else -> 3       // Evening / Night (18..23, 0..4)
}

fun isWeekendForIsoDate(isoDate: String): Boolean {
    val parts = isoDate.trim().split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size < 3) return false
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
    }
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
}

fun computeTimeAwareShortcuts(
    state: DashboardState,
    nowHour: Int,
    isWeekend: Boolean,
    nowIso: String = PayCycleEngine.todayIso(),
    editedAmountOverrides: Map<String, Money> = emptyMap(),
    minMatchesForChip: Int = 4,
    minTransactionsForWarmStart: Int = 15
): List<UsualSpendShortcut> {
    val lang = state.profile.language
    val envConfigs = state.envelopes.map { it.config }
    val accountItems = state.accounts.map { it.account }
    val nowEpoch = PayCycleEngine.parseEpochDays(nowIso)

    data class TxWithContext(
        val tx: com.example.data.TransactionItem,
        val hour: Int,
        val ageDays: Int,
        val isWeekend: Boolean
    )

    val eligible = state.allTransactions.mapNotNull { tx ->
        if (tx.type != "expense" || !tx.amount.isPositive || tx.isoDate.isBlank()) return@mapNotNull null
        val txEpoch = PayCycleEngine.parseEpochDays(tx.isoDate)
        val ageDays = (nowEpoch - txEpoch).toInt()
        if (ageDays !in 0..90) return@mapNotNull null
        val resolvedHour = tx.resolvedHourOfDay()
        if (resolvedHour !in 0..23) return@mapNotNull null
        TxWithContext(
            tx = tx,
            hour = resolvedHour,
            ageDays = ageDays,
            isWeekend = isWeekendForIsoDate(tx.isoDate)
        )
    }

    // Cold start guard: fewer than 15 transactions with a known hour -> return empty list
    if (eligible.size < minTransactionsForWarmStart) {
        return emptyList()
    }

    val targetTimeBlock = timeBlockForHour(nowHour.coerceIn(0, 23))

    // Group by weekday vs weekend and description
    val sameDayTypeBucket = eligible.filter { it.isWeekend == isWeekend }.ifEmpty { eligible }
    val grouped = sameDayTypeBucket.groupBy { item ->
        val rawLabel = item.tx.desc.trim().ifBlank {
            item.tx.localizedCategory(lang, envConfigs)
        }
        rawLabel.lowercase(Locale.ROOT)
    }

    val candidates = grouped.mapNotNull { (descKey, items) ->
        if (items.size < minMatchesForChip) return@mapNotNull null
        val sortedByRecent = items.sortedWith(
            compareBy<TxWithContext> { it.ageDays }
                .thenByDescending { it.tx.id }
        )
        val latestTx = sortedByRecent.first().tx
        val displayDesc = latestTx.desc.trim().ifBlank {
            latestTx.localizedCategory(lang, envConfigs)
        }

        // Typical amount: learned 3-edit override if present, otherwise mode of exact centime amounts tie-broken by recency
        val amountGroups = sortedByRecent.groupBy { it.tx.amount }
        val modeAmt = amountGroups.entries.sortedWith(
            compareByDescending<Map.Entry<Money, List<TxWithContext>>> { it.value.size }
                .thenBy { it.value.minOf { ctx -> ctx.ageDays } }
                .thenByDescending { it.value.first().tx.id }
        ).first().key
        val typicalAmt = editedAmountOverrides[descKey]?.takeIf { it.isPositive } ?: modeAmt

        var totalScore = 0.0
        for (ctx in items) {
            val blockWeight = if (timeBlockForHour(ctx.hour) == targetTimeBlock) 2.5 else 1.0
            val recencyBoost = 0.35 / (1.0 + (ctx.ageDays / 30.0))
            totalScore += blockWeight + recencyBoost
        }

        val smartGuess = PayCycleEngine.inferSmartDefaults(
            description = displayDesc,
            allTransactions = state.allTransactions,
            envelopes = envConfigs,
            accounts = accountItems,
            lang = lang
        )
        val resolvedEnvKey = smartGuess.envelopeKey.ifBlank { latestTx.envelopeKey }
        val resolvedEnvConfig = envConfigs.find { it.key == resolvedEnvKey }
        val resolvedCatName = resolvedEnvConfig?.localizedName(lang)
            ?: smartGuess.categoryName.ifBlank { latestTx.localizedCategory(lang, envConfigs) }
        val resolvedAccId = smartGuess.accountId.ifBlank { latestTx.accountId.ifBlank { "acc_cash" } }

        UsualSpendShortcut(
            desc = displayDesc,
            amount = typicalAmt,
            envelopeKey = resolvedEnvKey,
            accountId = resolvedAccId,
            categoryName = resolvedCatName,
            score = totalScore,
            occurrences = items.size,
            latestIsoDate = latestTx.isoDate
        )
    }

    return candidates.sortedWith(
        compareByDescending<UsualSpendShortcut> { (it.score * 1000.0).toLong() }
            .thenByDescending { it.latestIsoDate }
            .thenByDescending { it.occurrences }
            .thenBy { it.desc.lowercase(Locale.ROOT) }
    ).take(3)
}

fun computeTimeAwareShortcuts(
    state: DashboardState,
    nowIso: String = PayCycleEngine.todayIso(),
    currentHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    minTransactionsForWarmStart: Int = 15,
    minMatchesForChip: Int = 4,
    editedAmountOverrides: Map<String, Money> = emptyMap()
): List<UsualSpendShortcut> = computeTimeAwareShortcuts(
    state = state,
    nowHour = currentHour,
    isWeekend = isWeekendForIsoDate(nowIso),
    nowIso = nowIso,
    editedAmountOverrides = editedAmountOverrides,
    minMatchesForChip = minMatchesForChip,
    minTransactionsForWarmStart = minTransactionsForWarmStart
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
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
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
                        label = { Text("🎤 $sample", fontSize = 12.sp) },
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

/**
 * Evaluates simple keypad calculator expressions such as "12+8" or "45.5+14,5" into their sum (60.0),
 * falling back to [PayCycleEngine.parseFlexibleAmount] for plain numbers.
 */
fun evaluateKeypadExpression(raw: String): Money {
    val cleaned = raw.trim()
    if (cleaned.isEmpty()) return Money.ZERO
    if (cleaned.contains("+")) {
        return cleaned
            .split("+")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { PayCycleEngine.parseFlexibleAmount(it) }
            .sumMoney()
    }
    return PayCycleEngine.parseFlexibleAmount(cleaned)
}

@OptIn(ExperimentalFoundationApi::class)
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
    onSelectCategoryShortcut: (RecentCategoryShortcut, Boolean) -> Unit,
    onVoiceMicClick: (() -> Unit)? = null,
    txType: String = "expense",
    effectiveExpenseAmount: Money? = null,
    baseCurrencySymbol: String = currencySymbol,
    safePool: Money = Money.ZERO,
    daysRemaining: Int = 30,
    selectedEnvelopeStatus: EnvelopeStatus? = null,
    hideAmounts: Boolean = false,
    timeAwareShortcuts: List<TimeAwareShortcut> = emptyList(),
    onSelectTimeAwareShortcut: ((TimeAwareShortcut, Boolean) -> Unit)? = null,
    smartSuggestionsEnabled: Boolean = true,
    expenseHistory: List<com.example.data.TransactionItem> = emptyList(),
    activeWindow: com.example.domain.PayCycleWindow? = null
) {
    val haptics = LocalHapticFeedback.current
    val strings = remember(language) { Translations.forLang(language) }
    val parsedExpenseAmount = effectiveExpenseAmount ?: evaluateKeypadExpression(amountText)
    val isAmountEmpty = amountText.trim().let { it.isEmpty() || it == "0" }
    val showTimeAwareRow = smartSuggestionsEnabled && txType == "expense" && isAmountEmpty && timeAwareShortcuts.isNotEmpty() && onSelectTimeAwareShortcut != null
    var whyThisShortcut by remember { mutableStateOf<TimeAwareShortcut?>(null) }

    fun formatEvaluatedValue(v: Money): String {
        return if (v.centimes % 100L == 0L) {
            (v.centimes / 100L).toString()
        } else {
            v.toCanonicalDecimalString()
        }
    }

    fun appendDigitOrDot(key: String) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val current = amountText.trim()
        when (key) {
            "C" -> onAmountChange("")
            "⌫" -> if (current.isNotEmpty()) onAmountChange(current.dropLast(1))
            "=" -> {
                val evaluated = evaluateKeypadExpression(current)
                if (evaluated.isPositive) {
                    onAmountChange(formatEvaluatedValue(evaluated))
                }
            }
            "+" -> {
                if (current.isNotEmpty() && !current.endsWith("+") && !current.endsWith(".")) {
                    onAmountChange("$current+")
                }
            }
            "." -> {
                val lastSegment = current.substringAfterLast("+")
                if (!lastSegment.contains(".")) {
                    onAmountChange(if (lastSegment.isEmpty()) "${current}0." else "$current.")
                }
            }
            else -> {
                val next = if (current == "0") key else current + key
                if (next.length <= 16) {
                    onAmountChange(next)
                }
            }
        }
    }

    fun addQuickIncrement(delta: Int) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val currentVal = evaluateKeypadExpression(amountText)
        val nextVal = currentVal + Money.fromDh(delta.toLong())
        onAmountChange(formatEvaluatedValue(nextVal))
    }

    ExpressiveGlassCard(
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(14.dp),
        accentRimColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("large_numeric_keypad_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Amount Display, Mic Button & 2-Tap Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (showTimeAwareRow) {
                            strings.timeAwareShortcutsHint
                        } else {
                            when (language) {
                                "darija" -> "1. كتب المبلغ · 2. ورك على الفئة = تسجلات!"
                                "ar" -> "1. أدخل المبلغ · 2. اضغط الفئة = تم الحفظ!"
                                "en" -> "1. Type amount · 2. Tap category = Done!"
                                else -> "1. Tapez le montant · 2. Touchez une catégorie = Fait !"
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                        if (onVoiceMicClick != null) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onVoiceMicClick()
                                    }
                                    .testTag("keypad_voice_mic_btn")
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = when (language) {
                                            "ar", "darija" -> "إدخال صوتي"
                                            "en" -> "Voice input"
                                            else -> "Saisie vocale"
                                        },
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Feature 1: Live "Can I afford this?" colour-coded line under the amount (expenses only)
                    if (smartSuggestionsEnabled && txType == "expense" && parsedExpenseAmount.isPositive) {
                        val impact = PayCycleEngine.computePurchaseImpact(
                            amount = parsedExpenseAmount,
                            safePool = safePool,
                            daysRemaining = daysRemaining,
                            expenseHistory = expenseHistory,
                            window = activeWindow,
                            envelope = selectedEnvelopeStatus
                        )
                        val verdictColor = when (impact.verdict) {
                            PurchaseVerdict.FITS_TODAY -> AccentEmerald
                            PurchaseVerdict.LOWERS_ALLOWANCE -> AccentAmber
                            PurchaseVerdict.OVER_POOL -> AccentCoral
                        }
                        val envName = selectedEnvelopeStatus?.config?.localizedName(language)
                        val impactText = PayCycleEngine.formatPurchaseImpactLine(
                            impact = impact,
                            currencySymbol = baseCurrencySymbol,
                            hideAmounts = hideAmounts,
                            strings = strings,
                            envelopeName = envName
                        )
                        Text(
                            text = impactText,
                            style = MaterialTheme.typography.labelSmall,
                            color = verdictColor,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .testTag("keypad_purchase_impact_line")
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
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = twoTapInstantSave,
                        onCheckedChange = onToggleTwoTapInstantSave,
                        modifier = Modifier.testTag("two_tap_mode_switch")
                    )
                }
            }

            // Feature 2: Time-Aware 1-Tap Chips when amount is empty, switching to 3 Category Chips once typing starts
            if (showTimeAwareRow) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("time_aware_shortcuts_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        timeAwareShortcuts.take(3).forEachIndexed { idx, shortcut ->
                            val amtLabel = PayCycleEngine.formatAmountOrMask(shortcut.typicalAmount, hideAmounts)
                            val chipText = "${shortcut.description.take(12)} · $amtLabel $baseCurrencySymbol"
                            ExpressiveGlassSurface(
                                shape = RoundedCornerShape(16.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                accentBorderColor = AccentEmerald,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .combinedClickable(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            whyThisShortcut = null
                                            onSelectTimeAwareShortcut?.invoke(shortcut, twoTapInstantSave)
                                        },
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            whyThisShortcut = if (whyThisShortcut == shortcut) null else shortcut
                                        }
                                    )
                                    .testTag("time_aware_shortcut_$idx")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = AccentEmerald,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = chipText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    whyThisShortcut?.let { explained ->
                        val whyText = "${explained.description} : " +
                            strings.shortcutWhyThisFmt.replace("{count}", explained.occurrences.toString())
                        Text(
                            text = whyText,
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentEmerald,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { whyThisShortcut = null }
                                .testTag("time_aware_why_this_banner")
                        )
                    }
                }
            } else if (recentCategories.isNotEmpty()) {
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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+$inc",
                                style = MaterialTheme.typography.labelLarge,
                                color = AccentEmerald,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                val hasPlusExpr = amountText.contains("+") && !amountText.trim().endsWith("+")
                ExpressiveGlassSurface(
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    accentBorderColor = MaterialTheme.colorScheme.primary,
                    onClick = { appendDigitOrDot(if (hasPlusExpr) "=" else "+") },
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("keypad_calc_plus_equals")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (hasPlusExpr) "=" else "+",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                ExpressiveGlassSurface(
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.error,
                    accentBorderColor = MaterialTheme.colorScheme.error,
                    onClick = { appendDigitOrDot("C") },
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("keypad_clear")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "C",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                    }
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
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (key == "⌫") {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Backspace",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface
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
    onCompleteOnboarding: (Money, Int, List<Triple<String, Double, String>>) -> Unit
) {
    val lang = state.profile.language
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }

    var salaryText by remember {
        mutableStateOf(state.profile.baseBudget.formatDhRounded(useGrouping = false))
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
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
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
                    val sal = MoneyParser.parseMoroccanAmountToCentimes(salaryText)?.let { Money.fromCentimes(it) }?.takeIf { it.isPositive } ?: Money.fromDh(6400)
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
        totalAmount: Money,
        friendsNamesCsv: String,
        includeSelf: Boolean,
        recordFullExpenseNow: Boolean
    ) -> Unit
) {
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val lang = state.profile.language
    val strings = com.example.domain.Translations.forLang(lang)
    val haptics = LocalHapticFeedback.current
    var billTitle by remember { mutableStateOf("Dîner / Café entre amis") }
    var totalAmountText by remember { mutableStateOf("300") }
    var friendsCsv by remember { mutableStateOf("Yassine, Mehdi") }
    var includeSelf by remember { mutableStateOf(true) }
    var recordExpenseNow by remember { mutableStateOf(true) }

    val parsedFriends = remember(friendsCsv) {
        friendsCsv.split(",", ";", "\n").map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    val totalVal = MoneyParser.parseMoroccanAmountToCentimes(totalAmountText)?.let { Money.fromCentimes(it) } ?: Money.ZERO
    val divisor = (if (includeSelf) parsedFriends.size + 1 else parsedFriends.size).coerceAtLeast(1)
    val perPerson = if (totalVal.isPositive) totalVal.divideBy(divisor.toLong()) else Money.ZERO

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
                .testTag("split_bill_sellef_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🍕 ${strings.splitBillDesc} (Split Bill → Sellef)",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = when (lang) {
                    "ar" -> "قسّم الفاتورة بين الأصدقاء: يتم تسجيل حصة كل صديق تلقائياً في متابعة السلف مع تذكير واتساب."
                    "darija" -> "قسم الكونط مع صحابك: حق كل واحد كيتزاد أوتوماتيك فـ السلف مع تذكير واتساب."
                    "en" -> "Split a bill with friends: each friend's share is automatically added to your Sellef tracker with WhatsApp reminder."
                    else -> "Divisez une addition entre amis : la part de chaque ami est automatiquement ajoutée dans votre suivi Sellef (On me doit) avec rappel WhatsApp."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = billTitle,
                onValueChange = { billTitle = it },
                label = {
                    Text(
                        when (lang) {
                            "ar" -> "عنوان الخرجة / الفاتورة"
                            "darija" -> "عنوان الخرجة / الفاتورة"
                            "en" -> "Outing / Bill Title"
                            else -> "Titre de la sortie / facture (ex: Restaurant, Foot, Voyage)"
                        }
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("split_bill_title_input")
            )

            OutlinedTextField(
                value = totalAmountText,
                onValueChange = { totalAmountText = it },
                label = {
                    Text(
                        when (lang) {
                            "ar" -> "المبلغ الإجمالي المدفوع ($cur)"
                            "darija" -> "المبلغ كامل اللي تخلص ($cur)"
                            "en" -> "Total Amount Paid ($cur)"
                            else -> "Montant total payé ($cur)"
                        }
                    )
                },
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
                label = {
                    Text(
                        when (lang) {
                            "ar" -> "أسماء الأصدقاء (مفصولة بفاصلة)"
                            "darija" -> "سميات صحابك (مفروقين بفاصلة)"
                            "en" -> "Friends' Names (comma-separated)"
                            else -> "Prénoms des amis (séparés par une virgule)"
                        }
                    )
                },
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
                    text = when (lang) {
                        "ar" -> "تضميني في القسمة (${parsedFriends.size + if (includeSelf) 1 else 0} أشخاص)"
                        "darija" -> "حسبني معاهم فالقسمة (${parsedFriends.size + if (includeSelf) 1 else 0} ناس)"
                        "en" -> "Include me in the split (${parsedFriends.size + if (includeSelf) 1 else 0} people)"
                        else -> "M'inclure dans le partage (${parsedFriends.size + if (includeSelf) 1 else 0} pers.)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
                    text = when (lang) {
                        "ar" -> "تسجيل المصروف الإجمالي أيضاً في عملياتي"
                        "darija" -> "قيد حتى المصروف كامل فـ العمليات ديالي"
                        "en" -> "Also record full expense in my transactions"
                        else -> "Enregistrer aussi la dépense totale dans mes opérations"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
                        text = "${PayCycleEngine.formatDh(perPerson)} $cur / pers.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${parsedFriends.size} Sellef : ${parsedFriends.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            Button(
                onClick = {
                    if (totalVal.isPositive && parsedFriends.isNotEmpty()) {
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
                Text(
                    text = "${strings.splitBillBtnLabel} (${parsedFriends.size} • ${PayCycleEngine.formatDh(perPerson)} $cur)",
                    fontWeight = FontWeight.ExtraBold
                )
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
                text = when (lang) {
                    "ar" -> "رمز دعوة العائلة: $inviteCode"
                    "darija" -> "كود الدعوة ديال الدار: $inviteCode"
                    "en" -> "Your household invite code: $inviteCode"
                    else -> "Code d'invitation de votre foyer : $inviteCode"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
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
                    label = {
                        Text(
                            when (lang) {
                                "ar" -> "رمز الشريك (مثال: MSR-AB12CD)"
                                "darija" -> "كود الشريك (مثال: MSR-AB12CD)"
                                "en" -> "Partner code (e.g. MSR-AB12CD)"
                                else -> "Code partenaire (ex: MSR-AB12CD)"
                            },
                            fontSize = 12.sp
                        )
                    },
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
                        text = if (partnerCodeInput.trim().length >= 4) {
                            when (lang) {
                                "ar" -> "ربط"
                                "darija" -> "ربط"
                                "en" -> "Link"
                                else -> "Lier"
                            }
                        } else {
                            when (lang) {
                                "ar" -> "دعوة"
                                "darija" -> "عرض"
                                "en" -> "Invite"
                                else -> "Inviter"
                            }
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (!linkedPartnerCode.isNullOrBlank()) {
                Text(
                    text = "✓ $linkedPartnerCode",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentEmerald,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

