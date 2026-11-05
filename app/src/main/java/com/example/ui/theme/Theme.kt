package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
)

val MasroufiPalettes = listOf(
    AccentPaletteSpec(
        key = "emerald",
        labelFr = "Émeraude Fintech",
        primaryDark = Color(0xFF34D399),
        onPrimaryDark = Color(0xFF022C22),
        primaryContainerDark = Color(0xFF064E3B),
        onPrimaryContainerDark = Color(0xFFD1FAE5),
        primaryLight = Color(0xFF059669),
        primaryContainerLight = Color(0xFFD1FAE5),
        onPrimaryContainerLight = Color(0xFF064E3B),
        heroStart = Color(0xFF059669),
        heroMid = Color(0xFF0D9488),
        heroEnd = Color(0xFF064E3B),
        swatchColor = Color(0xFF10B981)
    ),
    AccentPaletteSpec(
        key = "majorelle",
        labelFr = "Bleu Indigo Revolut",
        primaryDark = Color(0xFF60A5FA),
        onPrimaryDark = Color(0xFF082F49),
        primaryContainerDark = Color(0xFF1E3A8A),
        onPrimaryContainerDark = Color(0xFFDBEAFE),
        primaryLight = Color(0xFF2563EB),
        primaryContainerLight = Color(0xFFDBEAFE),
        onPrimaryContainerLight = Color(0xFF1E3A8A),
        heroStart = Color(0xFF2563EB),
        heroMid = Color(0xFF4F46E5),
        heroEnd = Color(0xFF1E1B4B),
        swatchColor = Color(0xFF2563EB)
    ),
    AccentPaletteSpec(
        key = "amber",
        labelFr = "Or & Obsidienne Luxe",
        primaryDark = Color(0xFFFBBF24),
        onPrimaryDark = Color(0xFF451A03),
        primaryContainerDark = Color(0xFF78350F),
        onPrimaryContainerDark = Color(0xFFFEF3C7),
        primaryLight = Color(0xFFD97706),
        primaryContainerLight = Color(0xFFFEF3C7),
        onPrimaryContainerLight = Color(0xFF451A03),
        heroStart = Color(0xFFD97706),
        heroMid = Color(0xFFB45309),
        heroEnd = Color(0xFF451A03),
        swatchColor = Color(0xFFF59E0B)
    ),
    AccentPaletteSpec(
        key = "amethyst",
        labelFr = "Violet Royal",
        primaryDark = Color(0xFFA78BFA),
        onPrimaryDark = Color(0xFF2E1065),
        primaryContainerDark = Color(0xFF4C1D95),
        onPrimaryContainerDark = Color(0xFFEDE9FE),
        primaryLight = Color(0xFF7C3AED),
        primaryContainerLight = Color(0xFFEDE9FE),
        onPrimaryContainerLight = Color(0xFF2E1065),
        heroStart = Color(0xFF7C3AED),
        heroMid = Color(0xFF6366F1),
        heroEnd = Color(0xFF2E1065),
        swatchColor = Color(0xFF8B5CF6)
    ),
    AccentPaletteSpec(
        key = "terracotta",
        labelFr = "Rose Rubis Vif",
        primaryDark = Color(0xFFFB7185),
        onPrimaryDark = Color(0xFF4C0519),
        primaryContainerDark = Color(0xFF881337),
        onPrimaryContainerDark = Color(0xFFFFE4E6),
        primaryLight = Color(0xFFE11D48),
        primaryContainerLight = Color(0xFFFFE4E6),
        onPrimaryContainerLight = Color(0xFF4C0519),
        heroStart = Color(0xFFE11D48),
        heroMid = Color(0xFFBE123C),
        heroEnd = Color(0xFF4C0519),
        swatchColor = Color(0xFFF43F5E)
    ),
    AccentPaletteSpec(
        key = "ocean",
        labelFr = "Cyan Pacifique",
        primaryDark = Color(0xFF38BDF8),
        onPrimaryDark = Color(0xFF082F49),
        primaryContainerDark = Color(0xFF0369A1),
        onPrimaryContainerDark = Color(0xFFE0F2FE),
        primaryLight = Color(0xFF0284C7),
        primaryContainerLight = Color(0xFFE0F2FE),
        onPrimaryContainerLight = Color(0xFF0C4A6E),
        heroStart = Color(0xFF0284C7),
        heroMid = Color(0xFF0D9488),
        heroEnd = Color(0xFF082F49),
        swatchColor = Color(0xFF0EA5E9)
    )
)

fun paletteForKey(key: String): AccentPaletteSpec {
    return MasroufiPalettes.find { it.key == key } ?: MasroufiPalettes.first()
}

fun heroGradientColors(accentPalette: String, isOverBudget: Boolean): List<Color> {
    if (isOverBudget) {
        return listOf(Color(0xFFE11D48), Color(0xFF9F1239), Color(0xFF4C0519))
    }
    val spec = paletteForKey(accentPalette)
    return listOf(spec.heroStart, spec.heroMid, spec.heroEnd)
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
            secondary = AccentSkyBlue,
            onSecondary = Color(0xFF00263B),
            secondaryContainer = Color(0xFF1E293B),
            onSecondaryContainer = Color(0xFFE2E8F0),
            tertiary = AccentAmber,
            onTertiary = Color(0xFF451A03),
            background = MasroufiSurfaceDark,
            onBackground = MasroufiOnSurfaceDark,
            surface = MasroufiSurfaceDark,
            onSurface = MasroufiOnSurfaceDark,
            surfaceVariant = MasroufiSurfaceContainerHighDark,
            onSurfaceVariant = MasroufiOnSurfaceVariantDark,
            surfaceContainerLowest = Color(0xFF060911),
            surfaceContainerLow = Color(0xFF0E1420),
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
            secondary = Color(0xFF0284C7),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE0F2FE),
            onSecondaryContainer = Color(0xFF0C4A6E),
            tertiary = Color(0xFFD97706),
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
