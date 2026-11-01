package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    val heroEnd: Color,
    val swatchColor: Color
)

val MasroufiPalettes = listOf(
    AccentPaletteSpec(
        key = "emerald",
        labelFr = "Émeraude Atlas",
        primaryDark = Color(0xFF6FDCA0),
        onPrimaryDark = Color(0xFF04210F),
        primaryContainerDark = Color(0xFF0F5132),
        onPrimaryContainerDark = Color(0xFFB9F6D0),
        primaryLight = Color(0xFF059669),
        primaryContainerLight = Color(0xFFB9F6D0),
        onPrimaryContainerLight = Color(0xFF042F1E),
        heroStart = Color(0xFF176B43),
        heroEnd = Color(0xFF0E3D27),
        swatchColor = Color(0xFF10B981)
    ),
    AccentPaletteSpec(
        key = "majorelle",
        labelFr = "Bleu Majorelle",
        primaryDark = Color(0xFF7DD3FC),
        onPrimaryDark = Color(0xFF082F49),
        primaryContainerDark = Color(0xFF075985),
        onPrimaryContainerDark = Color(0xFFE0F2FE),
        primaryLight = Color(0xFF0284C7),
        primaryContainerLight = Color(0xFFBAE6FD),
        onPrimaryContainerLight = Color(0xFF0C4A6E),
        heroStart = Color(0xFF1D4ED8),
        heroEnd = Color(0xFF1E3A8A),
        swatchColor = Color(0xFF38BDF8)
    ),
    AccentPaletteSpec(
        key = "amber",
        labelFr = "Or de Marrakech",
        primaryDark = Color(0xFFFCD34D),
        onPrimaryDark = Color(0xFF451A03),
        primaryContainerDark = Color(0xFF78350F),
        onPrimaryContainerDark = Color(0xFFFEF3C7),
        primaryLight = Color(0xFFD97706),
        primaryContainerLight = Color(0xFFFDE68A),
        onPrimaryContainerLight = Color(0xFF451A03),
        heroStart = Color(0xFFB45309),
        heroEnd = Color(0xFF78350F),
        swatchColor = Color(0xFFFBBF24)
    ),
    AccentPaletteSpec(
        key = "amethyst",
        labelFr = "Violet Impérial",
        primaryDark = Color(0xFFC4B5FD),
        onPrimaryDark = Color(0xFF2E1065),
        primaryContainerDark = Color(0xFF5B21B6),
        onPrimaryContainerDark = Color(0xFFEDE9FE),
        primaryLight = Color(0xFF7C3AED),
        primaryContainerLight = Color(0xFFDDD6FE),
        onPrimaryContainerLight = Color(0xFF2E1065),
        heroStart = Color(0xFF6D28D9),
        heroEnd = Color(0xFF3B0764),
        swatchColor = Color(0xFFA78BFA)
    ),
    AccentPaletteSpec(
        key = "terracotta",
        labelFr = "Terracotta Kasbah",
        primaryDark = Color(0xFFFDA4AF),
        onPrimaryDark = Color(0xFF4C0519),
        primaryContainerDark = Color(0xFF881337),
        onPrimaryContainerDark = Color(0xFFFFE4E6),
        primaryLight = Color(0xFFE11D48),
        primaryContainerLight = Color(0xFFFECDD3),
        onPrimaryContainerLight = Color(0xFF4C0519),
        heroStart = Color(0xFFBE123C),
        heroEnd = Color(0xFF6B0F24),
        swatchColor = Color(0xFFF43F5E)
    ),
    AccentPaletteSpec(
        key = "ocean",
        labelFr = "Océan Essaouira",
        primaryDark = Color(0xFF5EEAD4),
        onPrimaryDark = Color(0xFF042F2E),
        primaryContainerDark = Color(0xFF115E59),
        onPrimaryContainerDark = Color(0xFFCCFBF1),
        primaryLight = Color(0xFF0D9488),
        primaryContainerLight = Color(0xFF99F6E4),
        onPrimaryContainerLight = Color(0xFF042F2E),
        heroStart = Color(0xFF0F766E),
        heroEnd = Color(0xFF134E4A),
        swatchColor = Color(0xFF14B8A6)
    )
)

fun paletteForKey(key: String): AccentPaletteSpec {
    return MasroufiPalettes.find { it.key == key } ?: MasroufiPalettes.first()
}

fun heroGradientColors(accentPalette: String, isOverBudget: Boolean): List<Color> {
    if (isOverBudget) {
        return listOf(Color(0xFF7F1D1D), Color(0xFF450A0A))
    }
    val spec = paletteForKey(accentPalette)
    return listOf(spec.heroStart, spec.heroEnd)
}

@Composable
fun MasroufiTheme(
    darkTheme: Boolean = true,
    accentPalette: String = "emerald",
    content: @Composable () -> Unit
) {
    val spec = paletteForKey(accentPalette)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = spec.primaryDark,
            onPrimary = spec.onPrimaryDark,
            primaryContainer = spec.primaryContainerDark,
            onPrimaryContainer = spec.onPrimaryContainerDark,
            secondary = AccentSkyBlue,
            onSecondary = Color(0xFF00263B),
            tertiary = AccentAmber,
            background = MasroufiSurfaceDark,
            onBackground = MasroufiOnSurfaceDark,
            surface = MasroufiSurfaceDark,
            onSurface = MasroufiOnSurfaceDark,
            surfaceVariant = MasroufiSurfaceContainerHighDark,
            onSurfaceVariant = MasroufiOnSurfaceVariantDark,
            surfaceContainerLowest = Color(0xFF090E0B),
            surfaceContainerLow = Color(0xFF131B16),
            surfaceContainer = MasroufiSurfaceContainerDark,
            surfaceContainerHigh = MasroufiSurfaceContainerHighDark,
            surfaceContainerHighest = MasroufiSurfaceContainerHighestDark,
            outline = MasroufiOutlineVariantDark,
            outlineVariant = MasroufiOutlineVariantDark,
            error = MasroufiErrorDark,
            errorContainer = MasroufiErrorContainerDark,
            onErrorContainer = Color(0xFFFFDAD6)
        )
    } else {
        lightColorScheme(
            primary = spec.primaryLight,
            onPrimary = Color.White,
            primaryContainer = spec.primaryContainerLight,
            onPrimaryContainer = spec.onPrimaryContainerLight,
            secondary = Color(0xFF0284C7),
            onSecondary = Color.White,
            tertiary = Color(0xFFD97706),
            background = MasroufiSurfaceLight,
            onBackground = MasroufiOnSurfaceLight,
            surface = MasroufiSurfaceLight,
            onSurface = MasroufiOnSurfaceLight,
            surfaceVariant = MasroufiSurfaceContainerHighLight,
            onSurfaceVariant = MasroufiOnSurfaceVariantLight,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFEEF7F1),
            surfaceContainer = MasroufiSurfaceContainerLight,
            surfaceContainerHigh = MasroufiSurfaceContainerHighLight,
            surfaceContainerHighest = MasroufiSurfaceContainerHighestLight,
            outline = MasroufiOutlineVariantLight,
            outlineVariant = MasroufiOutlineVariantLight,
            error = MasroufiErrorLight,
            errorContainer = MasroufiErrorContainerLight,
            onErrorContainer = Color(0xFF7F1D1D)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
