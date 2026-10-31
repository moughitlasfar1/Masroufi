package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MasroufiPrimaryDark,
    onPrimary = Color(0xFF04210F),
    primaryContainer = MasroufiPrimaryContainerDark,
    onPrimaryContainer = MasroufiOnPrimaryContainerDark,
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

private val LightColorScheme = lightColorScheme(
    primary = MasroufiPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = MasroufiPrimaryContainerLight,
    onPrimaryContainer = MasroufiOnPrimaryContainerLight,
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

@Composable
fun MasroufiTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
