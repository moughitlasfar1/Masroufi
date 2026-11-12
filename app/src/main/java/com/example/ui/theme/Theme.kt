package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class AccentPaletteSpec(
    val key: String,
    val labelFr: String,
    val primaryDark: Color,
    val onPrimaryDark: Color,
    val primaryContainerDark: Color,
    val onPrimaryContainerDark: Color,
    val primaryLight: Color,
    val primaryContainerLight: Color,
    val onPrimaryContainerLight: Color,
    val heroStart: Color,
    val heroMid: Color,
    val heroEnd: Color,
    val swatchColor: Color
) {
    fun localizedLabel(lang: String): String = when (key) {
        "emerald" -> when (lang) {
            "ar" -> "زمردي"
            "darija" -> "زمردي"
            "en" -> "Emerald"
            else -> "Émeraude"
        }
        "majorelle" -> when (lang) {
            "ar" -> "نيلي (Indigo)"
            "darija" -> "نيلي (Indigo)"
            "en" -> "Indigo"
            else -> "Indigo"
        }
        "amber" -> when (lang) {
            "ar" -> "ذهبي"
            "darija" -> "ذهبي"
            "en" -> "Gold"
            else -> "Or"
        }
        else -> labelFr
    }
}

val MasroufiPalettes = listOf(
    AccentPaletteSpec(
        key = "emerald",
        labelFr = "Émeraude",
        primaryDark = Color(0xFF34D399),
        onPrimaryDark = Color(0xFF022C22),
        primaryContainerDark = Color(0xFF064E3B),
        onPrimaryContainerDark = Color(0xFFD1FAE5),
        primaryLight = Color(0xFF047857),
        primaryContainerLight = Color(0xFFD1FAE5),
        onPrimaryContainerLight = Color(0xFF064E3B),
        heroStart = Color(0xFF064E3B),
        heroMid = Color(0xFF047857),
        heroEnd = Color(0xFF022C22),
        swatchColor = Color(0xFF10B981)
    ),
    AccentPaletteSpec(
        key = "majorelle",
        labelFr = "Indigo",
        primaryDark = Color(0xFFB8ADFF),
        onPrimaryDark = Color(0xFF1B1347),
        primaryContainerDark = Color(0xFF28234A),
        onPrimaryContainerDark = Color(0xFFF2EFFF),
        primaryLight = Color(0xFF5546C7),
        primaryContainerLight = Color(0xFFEAE6FF),
        onPrimaryContainerLight = Color(0xFF1F1659),
        heroStart = Color(0xFF3F3299),
        heroMid = Color(0xFF5546C7),
        heroEnd = Color(0xFF2A206B),
        swatchColor = Color(0xFF5546C7)
    ),
    AccentPaletteSpec(
        key = "amber",
        labelFr = "Or",
        primaryDark = Color(0xFFFBBF24),
        onPrimaryDark = Color(0xFF451A03),
        primaryContainerDark = Color(0xFF78350F),
        onPrimaryContainerDark = Color(0xFFFEF3C7),
        primaryLight = Color(0xFF92400E),
        primaryContainerLight = Color(0xFFFEF3C7),
        onPrimaryContainerLight = Color(0xFF451A03),
        heroStart = Color(0xFF78350F),
        heroMid = Color(0xFF92400E),
        heroEnd = Color(0xFF451A03),
        swatchColor = Color(0xFFF59E0B)
    )
)

fun paletteForKey(key: String): AccentPaletteSpec {
    val normalized = when (key.trim().lowercase()) {
        "indigo", "majorelle" -> "majorelle"
        "amber", "or", "gold" -> "amber"
        "emerald", "emeraude" -> "emerald"
        else -> key
    }
    return MasroufiPalettes.find { it.key == normalized } ?: MasroufiPalettes.first()
}

fun heroGradientColors(accentPalette: String, isOverBudget: Boolean): List<Color> {
    if (isOverBudget) {
        return listOf(Color(0xFF881337), Color(0xFF9F1239), Color(0xFF4C0519))
    }
    val spec = paletteForKey(accentPalette)
    return listOf(spec.heroStart, spec.heroMid, spec.heroEnd)
}

/**
 * Computes WCAG 2.1 relative luminance and contrast ratio (1.0 .. 21.0) between [foreground] and [background].
 */
fun wcagContrastRatio(foreground: Color, background: Color): Double {
    fun channelLum(c: Float): Double {
        val v = c.toDouble().coerceIn(0.0, 1.0)
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    val lum1 = 0.2126 * channelLum(foreground.red) + 0.7152 * channelLum(foreground.green) + 0.0722 * channelLum(foreground.blue)
    val lum2 = 0.2126 * channelLum(background.red) + 0.7152 * channelLum(background.green) + 0.0722 * channelLum(background.blue)
    val lighter = max(lum1, lum2)
    val darker = min(lum1, lum2)
    return (lighter + 0.05) / (darker + 0.05)
}

val ExpressiveShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp)
)

@Composable
fun MasroufiTheme(
    darkTheme: Boolean = false,
    accentPalette: String = "majorelle",
    content: @Composable () -> Unit
) {
    val spec = paletteForKey(accentPalette)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = spec.primaryDark,
            onPrimary = spec.onPrimaryDark,
            primaryContainer = spec.primaryContainerDark,
            onPrimaryContainer = spec.onPrimaryContainerDark,
            secondary = VyxelPeriwinkle,
            onSecondary = VyxelOnPeriwinkle,
            secondaryContainer = MasroufiSurfaceContainerHighDark,
            onSecondaryContainer = MasroufiOnSurfaceDark,
            tertiary = AccentAmber,
            onTertiary = Color(0xFF451A03),
            tertiaryContainer = Color(0xFF241E14),
            onTertiaryContainer = Color(0xFFFEF3C7),
            background = MasroufiSurfaceDark,
            onBackground = MasroufiOnSurfaceDark,
            surface = MasroufiSurfaceDark,
            onSurface = MasroufiOnSurfaceDark,
            surfaceVariant = MasroufiSurfaceContainerHighDark,
            onSurfaceVariant = MasroufiOnSurfaceVariantDark,
            surfaceTint = Color.Transparent,
            inverseSurface = Color(0xFFE8EEF7),
            inverseOnSurface = Color(0xFF0B1120),
            inversePrimary = spec.primaryLight,
            scrim = Color(0xCC000000),
            surfaceContainerLowest = Color(0xFF07090E),
            surfaceContainerLow = Color(0xFF0E111A),
            surfaceContainer = MasroufiSurfaceContainerDark,
            surfaceContainerHigh = MasroufiSurfaceContainerHighDark,
            surfaceContainerHighest = MasroufiSurfaceContainerHighestDark,
            outline = MasroufiOutlineVariantDark,
            outlineVariant = MasroufiOutlineVariantDark,
            error = MasroufiErrorDark,
            errorContainer = MasroufiErrorContainerDark,
            onError = Color(0xFF4C0519),
            onErrorContainer = Color(0xFFFFE4E6)
        )
    } else {
        lightColorScheme(
            primary = spec.primaryLight,
            onPrimary = Color.White,
            primaryContainer = spec.primaryContainerLight,
            onPrimaryContainer = spec.onPrimaryContainerLight,
            secondary = Color(0xFF5546C7),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFEAE6FF),
            onSecondaryContainer = Color(0xFF1F1659),
            tertiary = Color(0xFFB45309),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFEF3C7),
            onTertiaryContainer = Color(0xFF78350F),
            background = MasroufiSurfaceLight,
            onBackground = MasroufiOnSurfaceLight,
            surface = MasroufiSurfaceLight,
            onSurface = MasroufiOnSurfaceLight,
            surfaceVariant = MasroufiSurfaceContainerHighLight,
            onSurfaceVariant = MasroufiOnSurfaceVariantLight,
            surfaceTint = Color.Transparent,
            inverseSurface = Color(0xFF141821),
            inverseOnSurface = Color(0xFFF8FAFC),
            inversePrimary = spec.primaryDark,
            scrim = Color(0x99000000),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF8FAFC),
            surfaceContainer = MasroufiSurfaceContainerLight,
            surfaceContainerHigh = MasroufiSurfaceContainerHighLight,
            surfaceContainerHighest = MasroufiSurfaceContainerHighestLight,
            outline = MasroufiOutlineVariantLight,
            outlineVariant = MasroufiOutlineVariantLight,
            error = MasroufiErrorLight,
            errorContainer = MasroufiErrorContainerLight,
            onError = Color.White,
            onErrorContainer = Color(0xFF881337)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ExpressiveShapes,
        content = content
    )
}
