package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.PayCycleEngine
import com.example.domain.Translations
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.VyxelOnPeriwinkle
import com.example.ui.theme.VyxelPeriwinkle
import com.example.ui.theme.paletteForKey

@Composable
fun DashboardHeader(
    language: String,
    isDarkTheme: Boolean,
    hideAmounts: Boolean,
    isSyncing: Boolean = false,
    isOfflineMode: Boolean = false,
    accentPalette: String = "majorelle",
    startIso: String,
    endIso: String,
    onTogglePrivacy: () -> Unit,
    onOpenSettings: () -> Unit,
    onShiftCycle: (Int) -> Unit
) {
    val pal = paletteForKey(accentPalette)
    val strings = Translations.forLang(language)
    val topBarBg = MaterialTheme.colorScheme.surface
    val circleBtnBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val circleBtnBorder = MaterialTheme.colorScheme.outlineVariant
    val activeSegmentBg = if (isDarkTheme) VyxelPeriwinkle else MaterialTheme.colorScheme.primary
    val activeSegmentFg = if (isDarkTheme) VyxelOnPeriwinkle else Color.White
    val friendlyCycleRange = remember(startIso, endIso, language) {
        PayCycleEngine.formatCycleRangeFriendly(startIso, endIso, language)
    }

    Surface(
        color = topBarBg,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(circleBtnBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = if (isDarkTheme) VyxelPeriwinkle else pal.primaryLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Masroufi",
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        if (isSyncing || isOfflineMode) {
                            val badgeColor = if (isSyncing) AccentAmber else AccentEmerald
                            val badgeLabel = if (isSyncing) {
                                when (language) {
                                    "ar" -> "مزامنة…"
                                    "darija" -> "كيتزامن…"
                                    "en" -> "Syncing…"
                                    else -> "Synchro…"
                                }
                            } else {
                                strings.offlineSavedBadge
                            }
                            Surface(
                                shape = CircleShape,
                                color = badgeColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f)),
                                modifier = Modifier.testTag("sync_status_indicator")
                            ) {
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = circleBtnBg,
                        border = BorderStroke(1.dp, circleBtnBorder),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onTogglePrivacy() }
                            .testTag("privacy_eye_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (hideAmounts) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = strings.privacyLabel,
                                tint = if (hideAmounts) AccentAmber else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = circleBtnBg,
                        border = BorderStroke(1.dp, circleBtnBorder),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onOpenSettings() }
                            .testTag("settings_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = strings.settingsTitle,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            var monthPillDragAccumulator by remember { mutableFloatStateOf(0f) }
            val haptics = LocalHapticFeedback.current
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(friendlyCycleRange) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                monthPillDragAccumulator += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    monthPillDragAccumulator <= -56f -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onShiftCycle(1)
                                    }
                                    monthPillDragAccumulator >= 56f -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onShiftCycle(-1)
                                    }
                                }
                                monthPillDragAccumulator = 0f
                            },
                            onDragCancel = {
                                monthPillDragAccumulator = 0f
                            }
                        )
                    }
                    .testTag("top_bar_month_header_pill")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onShiftCycle(-1) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("month_header_prev_btn"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = strings.previousShortLabel,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = strings.previousShortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = activeSegmentBg,
                        contentColor = activeSegmentFg
                    ) {
                        Text(
                            text = friendlyCycleRange,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeSegmentFg,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onShiftCycle(1) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("month_header_next_btn"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = strings.currentShortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = strings.currentShortLabel,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
