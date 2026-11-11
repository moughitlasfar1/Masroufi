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
            "ar" -> "زمردي فينتك"
            "darija" -> "خضر زمردي"
            "en" -> "Fintech Emerald"
            else -> labelFr
        }
        "majorelle" -> when (lang) {
            "ar" -> "أزرق فيكسيل الليلي (Vyxel)"
            "darija" -> "زرق فيكسيل (Vyxel Dark)"
            "en" -> "Vyxel Periwinkle Dark"
            else -> labelFr
        }
        "amber" -> when (lang) {
            "ar" -> "ذهبي وعنبري فاخر"
            "darija" -> "ذهبي ملكي"
            "en" -> "Luxury Gold & Amber"
            else -> labelFr
        }
        "amethyst" -> when (lang) {
            "ar" -> "بنفسجي ملكي"
            "darija" -> "موف ملكي"
            "en" -> "Royal Amethyst"
            else -> labelFr
        }
        "terracotta" -> when (lang) {
            "ar" -> "ياقوتي مراكشي"
            "darija" -> "أحمر ياقوتي"
            "en" -> "Vibrant Ruby Rose"
            else -> labelFr
        }
        "ocean" -> when (lang) {
            "ar" -> "أزرق المحيط"
            "darija" -> "زرق البحر"
            "en" -> "Pacific Cyan"
            else -> labelFr
        }
        else -> labelFr
    }
}

val MasroufiPalettes = listOf(
    AccentPaletteSpec(
        key = "emerald",
        labelFr = "Émeraude Fintech",
        primaryDark = Color(0xFF34D399),
        onPrimaryDark = Color(0xFF022C22),
        primaryContainerDark = Color(0xFF064E3B),
        onPrimaryContainerDark = Color(0xFFD1FAE5),
        primaryLight = Color(0xFF047857),
        primaryContainerLight = Color(0xFFD1FAE5),
        onPrimaryContainerLight = Color(0xFF064E3B),
        heroStart = Color(0xFF10241E),
        heroMid = Color(0xFF132E26),
        heroEnd = Color(0xFF0B1613),
        swatchColor = Color(0xFF10B981)
    ),
    AccentPaletteSpec(
        key = "majorelle",
        labelFr = "Vyxel Periwinkle & Onyx",
        primaryDark = Color(0xFFB8C7FA),
        onPrimaryDark = Color(0xFF10182E),
        primaryContainerDark = Color(0xFF23293A),
        onPrimaryContainerDark = Color(0xFFE0E8FF),
        primaryLight = Color(0xFF284299),
        primaryContainerLight = Color(0xFFDCE4FF),
        onPrimaryContainerLight = Color(0xFF101B42),
        heroStart = Color(0xFF141822),
        heroMid = Color(0xFF1C2333),
        heroEnd = Color(0xFF0F1219),
        swatchColor = Color(0xFFB8C7FA)
    ),
    AccentPaletteSpec(
        key = "amber",
        labelFr = "Or & Obsidienne Luxe",
        primaryDark = Color(0xFFFBBF24),
        onPrimaryDark = Color(0xFF451A03),
        primaryContainerDark = Color(0xFF78350F),
        onPrimaryContainerDark = Color(0xFFFEF3C7),
        primaryLight = Color(0xFF92400E),
        primaryContainerLight = Color(0xFFFEF3C7),
        onPrimaryContainerLight = Color(0xFF451A03),
        heroStart = Color(0xFF241B10),
        heroMid = Color(0xFF302212),
        heroEnd = Color(0xFF14100B),
        swatchColor = Color(0xFFF59E0B)
    ),
    AccentPaletteSpec(
        key = "amethyst",
        labelFr = "Violet Royal",
        primaryDark = Color(0xFFC4B5FD),
        onPrimaryDark = Color(0xFF2E1065),
        primaryContainerDark = Color(0xFF4C1D95),
        onPrimaryContainerDark = Color(0xFFEDE9FE),
        primaryLight = Color(0xFF6D28D9),
        primaryContainerLight = Color(0xFFEDE9FE),
        onPrimaryContainerLight = Color(0xFF2E1065),
        heroStart = Color(0xFF1B1528),
        heroMid = Color(0xFF261C38),
        heroEnd = Color(0xFF110D1A),
        swatchColor = Color(0xFF8B5CF6)
    ),
    AccentPaletteSpec(
        key = "terracotta",
        labelFr = "Rose Rubis Vif",
        primaryDark = Color(0xFFFDA4AF),
        onPrimaryDark = Color(0xFF4C0519),
        primaryContainerDark = Color(0xFF881337),
        onPrimaryContainerDark = Color(0xFFFFE4E6),
        primaryLight = Color(0xFFBE123C),
        primaryContainerLight = Color(0xFFFFE4E6),
        onPrimaryContainerLight = Color(0xFF4C0519),
        heroStart = Color(0xFF261219),
        heroMid = Color(0xFF361822),
        heroEnd = Color(0xFF160B0F),
        swatchColor = Color(0xFFF43F5E)
    ),
    AccentPaletteSpec(
        key = "ocean",
        labelFr = "Cyan Pacifique",
        primaryDark = Color(0xFF38BDF8),
        onPrimaryDark = Color(0xFF082F49),
        primaryContainerDark = Color(0xFF0369A1),
        onPrimaryContainerDark = Color(0xFFE0F2FE),
        primaryLight = Color(0xFF0369A1),
        primaryContainerLight = Color(0xFFE0F2FE),
        onPrimaryContainerLight = Color(0xFF0C4A6E),
        heroStart = Color(0xFF101E28),
        heroMid = Color(0xFF152936),
        heroEnd = Color(0xFF0B131A),
        swatchColor = Color(0xFF0EA5E9)
    )
)

fun paletteForKey(key: String): AccentPaletteSpec {
    return MasroufiPalettes.find { it.key == key } ?: MasroufiPalettes.first()
}

fun heroGradientColors(accentPalette: String, isOverBudget: Boolean): List<Color> {
    if (isOverBudget) {
        return listOf(Color(0xFF2B1219), Color(0xFF3B1520), Color(0xFF170B0E))
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
    darkTheme: Boolean = true,
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
            background = MasroufiSurfaceDark,
            onBackground = MasroufiOnSurfaceDark,
            surface = MasroufiSurfaceDark,
            onSurface = MasroufiOnSurfaceDark,
            surfaceVariant = MasroufiSurfaceContainerHighDark,
            onSurfaceVariant = MasroufiOnSurfaceVariantDark,
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF0B0D11),
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
            secondary = Color(0xFF284299),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFDCE4FF),
            onSecondaryContainer = Color(0xFF101B42),
            tertiary = Color(0xFFB45309),
            onTertiary = Color.White,
            background = MasroufiSurfaceLight,
            onBackground = MasroufiOnSurfaceLight,
            surface = MasroufiSurfaceLight,
            onSurface = MasroufiOnSurfaceLight,
            surfaceVariant = MasroufiSurfaceContainerHighLight,
            onSurfaceVariant = MasroufiOnSurfaceVariantLight,
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
