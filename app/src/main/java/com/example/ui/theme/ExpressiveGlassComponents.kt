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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
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
    val primaryGlow = if (isDarkTheme) pal.primaryDark.copy(alpha = 0.035f) else pal.primaryLight.copy(alpha = 0.035f)
    val secondaryGlow = if (isDarkTheme) AccentEmerald.copy(alpha = 0.025f) else AccentSkyBlue.copy(alpha = 0.025f)

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
                    center = Offset(w * 0.85f, h * 0.05f),
                    radius = w * 0.55f
                ),
                radius = w * 0.55f,
                center = Offset(w * 0.85f, h * 0.05f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryGlow, Color.Transparent),
                    center = Offset(w * 0.18f, h * 0.92f),
                    radius = w * 0.50f
                ),
                radius = w * 0.50f,
                center = Offset(w * 0.18f, h * 0.92f)
            )
        }
        content()
    }
}

/**
 * Exact Compose translation of the `.glass3d` CSS specification (Light & Dark):
 * - `--filter-glass3d: blur(32px) brightness(0.85) saturate(2.5)`
 * - `--color-glass3d: hsl(189 80% 10% / 0.2)` -> `Color(0x3305292E)`
 * - `--noise-glass3d: egg-shell` 100px repeating tile shader
 * - 7-stage directional outer `box-shadow` using `hsl(205 20% 10% / 0.1..0.2)` (`#141A1F`)
 * - 5-stage 3D inset specular highlight `box-shadow` using `hsl(205 20% 90% / 0.8, 0.3, 0.15, 0.03)` (`#E0E6EB`)
 */
object Glass3dTokens {
    // hsl(189 80% 10% / 0.20)
    val ColorGlass3d = Color(0xFF05292E).copy(alpha = 0.20f)
    val ColorGlass3dLightOverlay = Color(0xFF05292E).copy(alpha = 0.08f)

    // hsl(205 20% 10%) -> #141A1F
    val ShadowBase = Color(0xFF141A1F)
    val ShadowAlpha20 = ShadowBase.copy(alpha = 0.20f)
    val ShadowAlpha10 = ShadowBase.copy(alpha = 0.10f)

    // hsl(205 20% 90%) -> #E0E6EB
    val InsetHighlightBase = Color(0xFFE0E6EB)
    val InsetHighlight80 = InsetHighlightBase.copy(alpha = 0.80f)
    val InsetHighlight30 = InsetHighlightBase.copy(alpha = 0.30f)
    val InsetHighlight15 = InsetHighlightBase.copy(alpha = 0.15f)
    val InsetHighlight03 = InsetHighlightBase.copy(alpha = 0.03f)

    // Cached 100x100 procedural egg-shell micro-texture tile matching background-size: 100px; background-repeat: repeat
    val eggShellNoiseBrush: androidx.compose.ui.graphics.ShaderBrush by lazy {
        val tileSize = 100
        val bmp = android.graphics.Bitmap.createBitmap(tileSize, tileSize, android.graphics.Bitmap.Config.ARGB_8888)
        val pixels = IntArray(tileSize * tileSize)
        var seed = 0x6D2B79F5L
        for (y in 0 until tileSize) {
            for (x in 0 until tileSize) {
                seed = (seed * 6364136223846793005L) + 1442695040888963407L
                val v = ((seed ushr 32) and 0xFF).toInt()
                // Subtle egg-shell dimple & specular grain
                val isSpeck = (v and 0x07) == 0
                val alpha = if (isSpeck) 9 else 3
                val channel = if ((v and 0x08) == 0) 232 else 18
                pixels[y * tileSize + x] = android.graphics.Color.argb(alpha, channel, channel, channel)
            }
        }
        bmp.setPixels(pixels, 0, tileSize, 0, 0, tileSize, tileSize)
        val imageBitmap = bmp.asImageBitmap()
        androidx.compose.ui.graphics.ShaderBrush(
            androidx.compose.ui.graphics.ImageShader(
                image = imageBitmap,
                tileModeX = androidx.compose.ui.graphics.TileMode.Repeated,
                tileModeY = androidx.compose.ui.graphics.TileMode.Repeated
            )
        )
    }
}

/**
 * Applies the `.glass3d` 7-layer directional outer drop shadow (`::before` backdrop tint + noise,
 * and `::after` 5-layer top-left 3D specular inset bevel) on any rounded component in Light & Dark modes.
 */
fun Modifier.glass3d(
    cornerRadius: Dp = 26.dp,
    isDark: Boolean = false,
    accentRimColor: Color? = null,
    drawOuterShadows: Boolean = true
): Modifier = drawWithCache {
    val rPx = cornerRadius.toPx()
    val corner = androidx.compose.ui.geometry.CornerRadius(rPx, rPx)

    // 7-stage outer box-shadow offsets from .glass3d CSS:
    // (0, 0), (0.7, 0.8), (1.3, 1.5), (2.3, 2.6), (3.9, 4.4), (6.5, 7.2), (8.0, 9.0)
    val shadowLayers = listOf(
        Triple(0f, 0f, Glass3dTokens.ShadowAlpha20),
        Triple(0.7.dp.toPx(), 0.8.dp.toPx(), Glass3dTokens.ShadowAlpha10),
        Triple(1.3.dp.toPx(), 1.5.dp.toPx(), Glass3dTokens.ShadowAlpha10),
        Triple(2.3.dp.toPx(), 2.6.dp.toPx(), Glass3dTokens.ShadowAlpha10),
        Triple(3.9.dp.toPx(), 4.4.dp.toPx(), Glass3dTokens.ShadowAlpha10),
        Triple(6.5.dp.toPx(), 7.2.dp.toPx(), Glass3dTokens.ShadowAlpha10),
        Triple(8.0.dp.toPx(), 9.0.dp.toPx(), Glass3dTokens.ShadowAlpha20)
    )

    val inset15 = 1.5.dp.toPx()
    val inset20 = 2.0.dp.toPx()
    val inset40 = 4.0.dp.toPx()
    val stroke05 = 0.5.dp.toPx()
    val stroke10 = 1.0.dp.toPx()
    val stroke15 = 1.5.dp.toPx()

    // Top-left 3D inset specular gradient (::after inset 2px 2px, 4px 4px, 1.5px 1.5px hsl(205 20% 90%))
    val specularTopLeftBrush = Brush.linearGradient(
        colors = listOf(
            Glass3dTokens.InsetHighlight80,
            Glass3dTokens.InsetHighlight30,
            Glass3dTokens.InsetHighlight15,
            Glass3dTokens.InsetHighlight03,
            Color.Transparent
        ),
        start = Offset.Zero,
        end = Offset(size.width * 0.65f, size.height * 0.65f)
    )

    // Subtle glass3d cyan-slate glaze (--color-glass3d: hsl(189 80% 10% / 0.2))
    val glass3dBackdropTint = if (isDark) {
        Glass3dTokens.ColorGlass3d
    } else {
        Glass3dTokens.ColorGlass3dLightOverlay
    }

    val accentGlow = accentRimColor?.copy(alpha = if (isDark) 0.42f else 0.34f)

    onDrawWithContent {
        // 1. Multi-stage directional outer shadows (.glass3d box-shadow)
        if (drawOuterShadows) {
            shadowLayers.forEachIndexed { idx, (dx, dy, sColor) ->
                val spread = (idx * 0.35f).dp.toPx()
                drawRoundRect(
                    color = sColor,
                    topLeft = Offset(dx + spread * 0.5f, dy + spread * 0.5f),
                    size = androidx.compose.ui.geometry.Size(
                        (size.width - spread).coerceAtLeast(0f),
                        (size.height - spread).coerceAtLeast(0f)
                    ),
                    cornerRadius = corner
                )
            }
        }

        // 2. Main card surface & child content (z-index: 6)
        drawContent()

        // 3. ::before — --color-glass3d tint + 100px repeating egg-shell noise texture
        drawRoundRect(
            color = glass3dBackdropTint,
            cornerRadius = corner
        )
        drawRoundRect(
            brush = Glass3dTokens.eggShellNoiseBrush,
            cornerRadius = corner
        )

        // 4. ::after — 5-layer inset 3D specular bevels (hsl(205 20% 90% / 0.8, 0.3, 0.15, 0.03, 0.03))
        // Layer A: inset 0 0 0.25px 0.5px hsl(205 20% 90% / 0.03)
        drawRoundRect(
            color = Glass3dTokens.InsetHighlight03,
            cornerRadius = corner,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke05)
        )
        // Layer B: inset 1.5px 1.5px 1.5px -0.75px hsl(205 20% 90% / 0.15) & inset 2px 2px 1px -3px hsl(205 20% 90% / 0.8)
        drawRoundRect(
            brush = specularTopLeftBrush,
            topLeft = Offset(inset15 * 0.5f, inset15 * 0.5f),
            size = androidx.compose.ui.geometry.Size(
                (size.width - inset15).coerceAtLeast(0f),
                (size.height - inset15).coerceAtLeast(0f)
            ),
            cornerRadius = corner,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke15)
        )
        // Layer C: inset 4px 4px 2px -6px hsl(205 20% 90% / 0.3) inner rim
        drawRoundRect(
            color = Glass3dTokens.InsetHighlight30.copy(alpha = if (isDark) 0.22f else 0.38f),
            topLeft = Offset(inset20, inset20),
            size = androidx.compose.ui.geometry.Size(
                (size.width - inset40).coerceAtLeast(0f),
                (size.height - inset40).coerceAtLeast(0f)
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                (rPx - inset20).coerceAtLeast(0f),
                (rPx - inset20).coerceAtLeast(0f)
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke10)
        )
        // Optional semantic accent rim highlight
        if (accentGlow != null) {
            drawRoundRect(
                color = accentGlow,
                cornerRadius = corner,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke10)
            )
        }
    }
}

/**
 * Reusable `.glass3d` 3D floating glass card for Light and Dark modes with multi-layer directional
 * slate drop shadows, `hsl(189 80% 10% / 0.2)` glass glaze, 100px egg-shell noise, and 5-layer
 * `hsl(205 20% 90%)` inset specular bevels while preserving >= 4.5:1 WCAG text contrast.
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
    // Vyxel matte charcoal card surface (#111317) in Dark Mode, crisp white in Light Mode
    val glassFill = if (isDark) {
        containerColor.copy(alpha = 0.96f)
    } else {
        containerColor.copy(alpha = 0.96f)
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    val animatedRadius by animateDpAsState(
        targetValue = if (isPressed && onClick != null) (cornerRadius - 4.dp).coerceAtLeast(16.dp) else cornerRadius,
        animationSpec = ExpressiveMotion.bouncySpring(),
        label = "glass_card_radius"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.98f else 1f,
        animationSpec = ExpressiveMotion.bouncySpring(),
        label = "glass_card_scale"
    )

    val topRim = accentRimColor?.copy(alpha = if (isDark) 0.38f else 0.40f)
        ?: if (isDark) Color(0xFF262A35) else Glass3dTokens.InsetHighlight80
    val bottomRim = accentRimColor?.copy(alpha = 0.14f)
        ?: if (isDark) Color(0xFF1A1D25) else Glass3dTokens.ShadowAlpha20

    val shape = RoundedCornerShape(animatedRadius)

    Surface(
        shape = shape,
        color = glassFill,
        tonalElevation = if (isDark) 2.dp else 1.dp,
        shadowElevation = if (isDark) 6.dp else 4.dp,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(topRim, bottomRim))
        ),
        modifier = modifier
            .scale(animatedScale)
            .glass3d(
                cornerRadius = animatedRadius,
                isDark = isDark,
                accentRimColor = accentRimColor,
                drawOuterShadows = true
            )
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
 * Reusable `.glass3d` inner tile for nested items inside cards, headers, or sheets (Vyxel #1C2027 / #232731 pill surface).
 */
@Composable
fun ExpressiveGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    accentBorderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fill = containerColor.copy(alpha = if (isDark) 0.95f else 0.95f)
    val borderColor = accentBorderColor?.copy(alpha = 0.36f)
        ?: if (isDark) Color(0xFF2A2F3B) else Glass3dTokens.InsetHighlight80
    val haptics = LocalHapticFeedback.current

    Surface(
        shape = shape,
        color = fill,
        shadowElevation = if (isDark) 2.dp else 1.dp,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .glass3d(
                cornerRadius = 20.dp,
                isDark = isDark,
                accentRimColor = accentBorderColor,
                drawOuterShadows = false
            )
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
/**
 * Vyxel-Style Floating Translucent Glass Navigation Dock:
 * - Translucent obsidian-charcoal capsule (`RoundedCornerShape(36.dp)`) with specular top rim
 * - Active tab features a raised Soft-Periwinkle (`#B8C7FA`) circular badge (`48.dp`) that pops out above
 *   the top edge of the dock with a subtle emerald/periwinkle ambient halo underneath, matching Vyxel Apps.
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
    val dockFill = if (isDark) Color(0xFF13161D).copy(alpha = 0.92f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f)
    val rimTop = if (isDark) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.90f)
    val rimBottom = if (isDark) Color(0xFF252A36) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val activeBubbleColor = if (isDark) VyxelPeriwinkle else MaterialTheme.colorScheme.primary
    val activeBubbleContentColor = if (isDark) VyxelOnPeriwinkle else Color.White
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Main glass capsule dock
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = dockFill,
            tonalElevation = 8.dp,
            shadowElevation = 14.dp,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(rimTop, rimBottom))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(top = 10.dp)
                .glass3d(
                    cornerRadius = 36.dp,
                    isDark = isDark,
                    drawOuterShadows = true
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { pageIdx, (key, label, icon) ->
                    val isSelected = activeTabKey == key
                    val bubbleLift by animateDpAsState(
                        targetValue = if (isSelected) (-8).dp else 0.dp,
                        animationSpec = ExpressiveMotion.bouncySpring(),
                        label = "dock_lift_$key"
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            if (isDark) VyxelPeriwinkle else MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = ExpressiveMotion.snappySpring(),
                        label = "dock_label_$key"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = { onSelectTab(pageIdx, key) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLongPressTab(key)
                                }
                            )
                            .testTag("nav_tab_$key"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(y = bubbleLift)
                                .size(if (isSelected) 46.dp else 32.dp)
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .shadow(10.dp, CircleShape)
                                            .clip(CircleShape)
                                            .background(activeBubbleColor)
                                            .border(2.dp, if (isDark) Color(0xFF0A0D13) else Color.White, CircleShape)
                                    } else {
                                        Modifier.clip(CircleShape)
                                    }
                                )
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
                                tint = if (isSelected) activeBubbleContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(if (isSelected) 23.dp else 21.dp)
                            )
                        }
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = labelColor
                        )
                    }
                }
            }
        }
    }
}
