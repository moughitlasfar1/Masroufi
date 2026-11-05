package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Centralized Material 3 Expressive & Floating Glass Design System for Masroufi.
 * Provides spring-physics modifiers, ambient atmospheric backgrounds, translucent glass panels,
 * floating navigation docks, expressive chips, and animated progress indicators.
 */
object ExpressiveMotion {
    fun <T> bouncySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> snappySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> fluidSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )
}

/**
 * Adds Material 3 Expressive spring scale feedback and optional haptic pulse on press.
 */
fun Modifier.expressivePressable(
    pressedScale: Float = 0.96f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = ExpressiveMotion.bouncySpring(),
        label = "expressive_press_scale"
    )
    val base = this.scale(scale)
    if (onClick != null) {
        base.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
    } else {
        base
    }
}

/**
 * Full-bleed atmospheric background with soft radial light pools that give depth
 * to floating translucent glass surfaces in both Dark and Light themes.
 */
@Composable
fun ExpressiveAmbientBackground(
    accentPalette: String,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val pal = paletteForKey(accentPalette)
    val bgColor = MaterialTheme.colorScheme.background
    val primaryGlow = if (isDarkTheme) pal.primaryDark.copy(alpha = 0.06f) else pal.primaryLight.copy(alpha = 0.035f)
    val secondaryGlow = if (isDarkTheme) pal.heroMid.copy(alpha = 0.05f) else AccentSkyBlue.copy(alpha = 0.025f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryGlow, Color.Transparent),
                    center = Offset(w * 0.85f, h * 0.06f),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(w * 0.85f, h * 0.06f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryGlow, Color.Transparent),
                    center = Offset(w * 0.12f, h * 0.35f),
                    radius = w * 0.55f
                ),
                radius = w * 0.55f,
                center = Offset(w * 0.12f, h * 0.35f)
            )
        }
        content()
    }
}

/**
 * Reusable high-contrast Fintech card with crisp neutral surface, subtle elevation,
 * clean specular border, and spring-morphing press interaction.
 */
@Composable
fun ExpressiveGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    accentRimColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val glassFill = containerColor.copy(alpha = if (isDark) 0.96f else 0.99f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    val animatedRadius by animateDpAsState(
        targetValue = if (isPressed && onClick != null) (cornerRadius - 6.dp).coerceAtLeast(14.dp) else cornerRadius,
        animationSpec = ExpressiveMotion.bouncySpring(),
        label = "glass_card_radius"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.98f else 1f,
        animationSpec = ExpressiveMotion.bouncySpring(),
        label = "glass_card_scale"
    )

    val topRim = accentRimColor?.copy(alpha = if (isDark) 0.45f else 0.40f)
        ?: if (isDark) Color(0xFF29364F) else Color(0xFFE2E8F0)
    val bottomRim = accentRimColor?.copy(alpha = 0.18f)
        ?: if (isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1).copy(alpha = 0.75f)

    val shape = RoundedCornerShape(animatedRadius)

    Surface(
        shape = shape,
        color = glassFill,
        tonalElevation = if (isDark) 2.dp else 0.dp,
        shadowElevation = if (isDark) 4.dp else 2.dp,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(topRim, bottomRim))
        ),
        modifier = modifier
            .scale(animatedScale)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    )
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Reusable crisp inner tile for nested items inside cards or sheets.
 */
@Composable
fun ExpressiveGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    accentBorderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fill = containerColor.copy(alpha = if (isDark) 0.95f else 0.98f)
    val borderColor = accentBorderColor?.copy(alpha = 0.42f)
        ?: if (isDark) Color(0xFF29364F) else Color(0xFFE2E8F0)
    val haptics = LocalHapticFeedback.current

    Surface(
        shape = shape,
        color = fill,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                } else Modifier
            )
    ) {
        Box(content = content)
    }
}

/**
 * Expressive Section Header with glowing icon badge, title, subtitle, and optional trailing content.
 */
@Composable
fun ExpressiveSectionHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = trailingContent
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    accentColor.copy(alpha = 0.24f),
                                    accentColor.copy(alpha = 0.10f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = accentColor.copy(alpha = 0.32f),
                            shape = RoundedCornerShape(13.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = trailing
            )
        }
    }
}

/**
 * Spring-animated expressive progress bar with rounded capsule fill and soft track.
 */
@Composable
fun ExpressiveAnimatedProgressBar(
    progress: Float,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    val safeTarget = if (progress.isNaN() || progress.isInfinite()) 0f else progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = safeTarget,
        animationSpec = ExpressiveMotion.fluidSpring(),
        label = "expressive_progress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor)
    ) {
        if (animatedProgress > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress.coerceIn(0.03f, 1f))
                    .height(height)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                color.copy(alpha = 0.85f),
                                color
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Expressive morphing filter chip with spring corner radius & color transitions.
 */
@Composable
fun ExpressivePillChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    icon: ImageVector? = leadingIcon,
    testTag: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val haptics = LocalHapticFeedback.current
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) 14.dp else 22.dp,
        animationSpec = ExpressiveMotion.bouncySpring(),
        label = "chip_corner"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
        animationSpec = ExpressiveMotion.snappySpring(),
        label = "chip_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        animationSpec = ExpressiveMotion.snappySpring(),
        label = "chip_border"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = ExpressiveMotion.snappySpring(),
        label = "chip_content"
    )

    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        shape = shape,
        color = bgColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(shape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Floating Translucent Glass Navigation Dock with spring-animated pill selection
 * and long-press contextual shortcut support.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressiveGlassBottomDock(
    tabs: List<Triple<String, String, ImageVector>>,
    activeTabKey: String,
    onSelectTab: (Int, String) -> Unit,
    onLongPressTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val dockFill = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (isDark) 0.90f else 0.95f)
    val rimTop = if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.85f)
    val rimBottom = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = dockFill,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(rimTop, rimBottom))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { pageIdx, (key, label, icon) ->
                    val isSelected = activeTabKey == key
                    val pillScale by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.94f,
                        animationSpec = ExpressiveMotion.bouncySpring(),
                        label = "dock_scale_$key"
                    )
                    val pillCorner by animateDpAsState(
                        targetValue = if (isSelected) 22.dp else 16.dp,
                        animationSpec = ExpressiveMotion.bouncySpring(),
                        label = "dock_corner_$key"
                    )
                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.85f else 0.95f)
                        } else {
                            Color.Transparent
                        },
                        animationSpec = ExpressiveMotion.snappySpring(),
                        label = "dock_bg_$key"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = ExpressiveMotion.snappySpring(),
                        label = "dock_fg_$key"
                    )

                    Surface(
                        shape = RoundedCornerShape(pillCorner),
                        color = pillBg,
                        border = if (isSelected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        } else null,
                        modifier = Modifier
                            .weight(1f)
                            .scale(pillScale)
                            .clip(RoundedCornerShape(pillCorner))
                            .combinedClickable(
                                onClick = {
                                    onSelectTab(pageIdx, key)
                                },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLongPressTab(key)
                                }
                            )
                            .testTag("nav_tab_$key")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .combinedClickable(
                                        onClick = { onSelectTab(pageIdx, key) },
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onLongPressTab(key)
                                        }
                                    )
                                    .testTag("nav_icon_$key"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = contentColor,
                                    modifier = Modifier.size(if (isSelected) 21.dp else 19.dp)
                                )
                            }
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = if (isSelected) 11.sp else 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}
