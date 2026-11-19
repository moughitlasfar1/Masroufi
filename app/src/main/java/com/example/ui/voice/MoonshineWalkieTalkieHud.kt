package com.example.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.PayCycleEngine
import com.example.domain.Translations
import com.example.domain.VoiceBatchParseResult
import com.example.domain.VoiceDarijaParser
import com.example.ui.DashboardState
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.glass3d

/**
 * Dual-mode Floating Action Button:
 * - Short tap on `+ Ajouter` opens the fast 5-second keypad sheet.
 * - Long-press on `+` OR tapping the integrated Moonshine Mic pill launches the
 *   Walkie-Talkie Moonshine Voice HUD immediately.
 */
@Composable
fun MoonshineWalkieTalkieFab(
    addLabel: String,
    isListening: Boolean,
    isDarkTheme: Boolean,
    onQuickTapAdd: () -> Unit,
    onStartWalkieTalkie: () -> Unit,
    onStopAndCommitWalkieTalkie: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
        modifier = Modifier
            .glass3d(cornerRadius = 24.dp, isDark = isDarkTheme)
            .testTag("fab_add_expense")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
        ) {
            // Left/Main zone: Tap for Keypad Sheet, Long-Press for Walkie-Talkie Voice
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .pointerInput(isListening) {
                        detectTapGestures(
                            onTap = {
                                if (isListening) {
                                    onStopAndCommitWalkieTalkie()
                                } else {
                                    onQuickTapAdd()
                                }
                            },
                            onLongPress = {
                                onStartWalkieTalkie()
                            }
                        )
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = addLabel,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = addLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right orb: Dedicated 1-tap / hold Moonshine Walkie-Talkie trigger
            Surface(
                shape = CircleShape,
                color = if (isListening) AccentEmerald else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                contentColor = if (isListening) Color.White else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (isListening) {
                            onStopAndCommitWalkieTalkie()
                        } else {
                            onStartWalkieTalkie()
                        }
                    }
                    .testTag("fab_moonshine_walkie_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = "Moonshine Voice",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Floating `.glass3d` Moonshine Walkie-Talkie HUD showing:
 * - On-device badge (`Moonshine · Sur l'appareil`)
 * - 16-bar live acoustic waveform
 * - Live partial transcript + multi-expense / Q&A instant preview chips
 * - 1-tap Darija/Arabic/FR/EN multi-expense and budget Q&A sample chips
 * - Cancel and Auto-Save / Ask action buttons
 */
@Composable
fun MoonshineWalkieTalkieHud(
    visible: Boolean,
    state: DashboardState,
    liveTranscript: String,
    waveformLevels: List<Float>,
    onTranscriptChange: (String) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit
) {
    val lang = state.profile.language
    val strings = Translations.forLang(lang)
    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
    val isDark = state.profile.isDarkTheme

    val liveEvaluation = VoiceDarijaParser.evaluateVoiceUtterance(liveTranscript, state)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(200)),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.92f, animationSpec = tween(150))
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDark) Color(0xFF141824).copy(alpha = 0.96f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            ),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .glass3d(cornerRadius = 28.dp, isDark = isDark)
                .testTag("moonshine_walkie_talkie_hud")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header: On-device Moonshine badge + Cancel button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = strings.moonshineBadgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("moonshine_hud_cancel_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.cancelBtn,
                            tint = AccentCoral
                        )
                    }
                }

                // 16-Bar Live Acoustic Waveform
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("moonshine_waveform_bars"),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    waveformLevels.forEachIndexed { idx, rawLevel ->
                        val animatedFraction by animateFloatAsState(
                            targetValue = rawLevel.coerceIn(0.12f, 1.0f),
                            animationSpec = tween(durationMillis = 60),
                            label = "wave_bar_$idx"
                        )
                        val barColor = when {
                            liveEvaluation is VoiceBatchParseResult.ExpensesBatch -> AccentEmerald
                            liveEvaluation is VoiceBatchParseResult.BudgetQuery -> AccentAmber
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height((32.dp * animatedFraction).coerceAtLeast(5.dp))
                                .clip(CircleShape)
                                .background(barColor)
                        )
                    }
                }

                // Live Transcript Field (supports both live speech & instant typing/tapping)
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
                        .testTag("moonshine_live_transcript_input")
                )

                // Instant Live Preview of Multi-Expenses OR Hands-Free Budget Q&A
                when (liveEvaluation) {
                    is VoiceBatchParseResult.ExpensesBatch -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("moonshine_live_expenses_preview")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                liveEvaluation.items.forEachIndexed { idx, item ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = AccentEmerald.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.4f)),
                                        modifier = Modifier.testTag("moonshine_parsed_chip_$idx")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.description,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${PayCycleEngine.formatAmountOrMask(item.amount, state.hideAmounts)} $cur",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = AccentEmerald
                                            )
                                            Text(
                                                text = "· ${item.categoryName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is VoiceBatchParseResult.BudgetQuery -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AccentAmber.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("moonshine_live_qa_preview")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = liveEvaluation.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = liveEvaluation.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = liveEvaluation.displayValue,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AccentAmber
                                )
                            }
                        }
                    }

                    is VoiceBatchParseResult.Unrecognized -> {
                        Text(
                            text = strings.moonshineAskHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 1-Tap Darija / Multi-Expense / Q&A Sample Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = strings.moonshineTryPhrasesLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VoiceDarijaParser.samplePhrasesForLanguage(lang).forEachIndexed { idx, sample ->
                            FilterChip(
                                selected = liveTranscript == sample,
                                onClick = { onTranscriptChange(sample) },
                                label = {
                                    Text(
                                        text = sample,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.testTag("moonshine_sample_chip_$idx")
                            )
                        }
                    }
                }

                // Action Row: Auto-Save Batch or Hear Q&A Reply
                val canCommit = liveEvaluation is VoiceBatchParseResult.ExpensesBatch ||
                    liveEvaluation is VoiceBatchParseResult.BudgetQuery
                Button(
                    onClick = onCommit,
                    enabled = canCommit,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (liveEvaluation) {
                            is VoiceBatchParseResult.BudgetQuery -> AccentAmber
                            else -> AccentEmerald
                        },
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("moonshine_commit_btn")
                ) {
                    Icon(
                        imageVector = when (liveEvaluation) {
                            is VoiceBatchParseResult.BudgetQuery -> Icons.AutoMirrored.Filled.VolumeUp
                            else -> Icons.Default.Check
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (liveEvaluation) {
                            is VoiceBatchParseResult.ExpensesBatch -> {
                                if (liveEvaluation.items.size > 1) {
                                    strings.moonshineBatchSavedFmt
                                        .replace("{count}", liveEvaluation.items.size.toString())
                                        .replace("{total}", PayCycleEngine.formatAmountOrMask(liveEvaluation.totalAmount, state.hideAmounts))
                                        .replace("{cur}", cur)
                                } else {
                                    val first = liveEvaluation.items.first()
                                    strings.moonshineSingleSavedFmt
                                        .replace("{desc}", first.description)
                                        .replace("{total}", PayCycleEngine.formatAmountOrMask(first.amount, state.hideAmounts))
                                        .replace("{cur}", cur)
                                        .replace("{cat}", first.categoryName)
                                }
                            }
                            is VoiceBatchParseResult.BudgetQuery -> liveEvaluation.title
                            is VoiceBatchParseResult.Unrecognized -> strings.moonshineReleaseToSave
                        },
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

/**
 * Floating answer card displayed when the user asks a hands-free voice question about their budget.
 */
@Composable
fun MoonshineAnswerOverlayCard(
    queryResult: VoiceBatchParseResult.BudgetQuery?,
    isDarkTheme: Boolean,
    onReplayAudio: (VoiceBatchParseResult.BudgetQuery) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = queryResult != null,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.94f),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.94f)
    ) {
        if (queryResult != null) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (isDarkTheme) Color(0xFF151925) else MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.5.dp, AccentAmber.copy(alpha = 0.55f)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .glass3d(cornerRadius = 24.dp, isDark = isDarkTheme)
                    .testTag("moonshine_answer_card")
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AccentAmber.copy(alpha = 0.16f),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { onReplayAudio(queryResult) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = queryResult.title,
                                    tint = AccentAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = queryResult.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = queryResult.displayValue,
                                style = MaterialTheme.typography.headlineSmall,
                                color = AccentAmber,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = queryResult.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("moonshine_answer_dismiss_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
