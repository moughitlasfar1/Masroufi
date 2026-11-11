package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// Dark Palette — Vyxel OLED Pitch-Black & Matte Charcoal + Soft Periwinkle (#B8C7FA)
val MasroufiSurfaceDark = Color(0xFF000000)
val MasroufiSurfaceContainerDark = Color(0xFF111317)
val MasroufiSurfaceContainerHighDark = Color(0xFF1B1E26)
val MasroufiSurfaceContainerHighestDark = Color(0xFF242832)
val MasroufiOnSurfaceDark = Color(0xFFF5F7FB)
val MasroufiOnSurfaceVariantDark = Color(0xFFA6B0C2)
val MasroufiOutlineVariantDark = Color(0xFF232731)
val MasroufiPrimaryDark = Color(0xFFB8C7FA)
val MasroufiPrimaryBrightDark = Color(0xFFD4DFFF)
val MasroufiPrimaryContainerDark = Color(0xFF23293A)
val MasroufiOnPrimaryContainerDark = Color(0xFFE0E8FF)
val MasroufiErrorDark = Color(0xFFFB7185)
val MasroufiErrorContainerDark = Color(0xFF881337)

// Light Palette — Crisp Alabaster & Pure White Cards (High-contrast Light Mode)
val MasroufiSurfaceLight = Color(0xFFF3F6FA)
val MasroufiSurfaceContainerLight = Color(0xFFFFFFFF)
val MasroufiSurfaceContainerHighLight = Color(0xFFF1F5F9)
val MasroufiSurfaceContainerHighestLight = Color(0xFFE2E8F0)
val MasroufiOnSurfaceLight = Color(0xFF0B1120)
val MasroufiOnSurfaceVariantLight = Color(0xFF334155)
val MasroufiOutlineVariantLight = Color(0xFFCBD5E1)
val MasroufiPrimaryLight = Color(0xFF284299)
val MasroufiPrimaryContainerLight = Color(0xFFDCE4FF)
val MasroufiOnPrimaryContainerLight = Color(0xFF101B42)
val MasroufiErrorLight = Color(0xFFE11D48)
val MasroufiErrorContainerLight = Color(0xFFFFE4E6)

// Vyxel Signature Pill & Surface Tokens
val VyxelPeriwinkle = Color(0xFFB8C7FA)
val VyxelOnPeriwinkle = Color(0xFF10182E)
val VyxelCardDark = Color(0xFF111317)
val VyxelPillDark = Color(0xFF232731)
val VyxelPillBorderDark = Color(0xFF2C313E)

// Vibrant Semantic Accents (Income, Expense, Transfer, Categories)
val AccentIndigo = Color(0xFFB8C7FA)
val AccentSkyBlue = Color(0xFF38BDF8)
val AccentAmber = Color(0xFFF59E0B)
val AccentEmerald = Color(0xFF10B981)
val AccentCoral = Color(0xFFF43F5E)
val AccentViolet = Color(0xFF8B5CF6)
val AccentTeal = Color(0xFF14B8A6)
val AccentMagenta = Color(0xFFEC4899)

fun semanticIncomeColor(isDark: Boolean): Color =
    if (isDark) Color(0xFF34D399) else Color(0xFF047857)

fun semanticExpenseColor(isDark: Boolean): Color =
    if (isDark) Color(0xFFFB7185) else Color(0xFFE11D48)

fun semanticTransferColor(isDark: Boolean): Color =
    if (isDark) Color(0xFFB8C7FA) else Color(0xFF284299)

fun semanticWarningColor(isDark: Boolean): Color =
    if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)

private val DistinctEnvelopeColorsLight = listOf(
    Color(0xFF284299), // Vyxel Deep Periwinkle
    Color(0xFFE11D48), // Crimson Rose
    Color(0xFFD97706), // Warm Amber
    Color(0xFF6D28D9), // Vivid Violet
    Color(0xFF0284C7), // Ocean Cyan
    Color(0xFF059669), // Crisp Emerald
    Color(0xFFDB2777), // Fuchsia Berry
    Color(0xFF0D9488)  // Deep Teal
)

private val DistinctEnvelopeColorsDark = listOf(
    Color(0xFFB8C7FA), // Vyxel Periwinkle Blue
    Color(0xFFFB7185), // Bright Coral Rose
    Color(0xFFFBBF24), // Bright Gold
    Color(0xFFA78BFA), // Bright Violet
    Color(0xFF38BDF8), // Sky Cyan
    Color(0xFF34D399), // Mint Emerald
    Color(0xFFF472B6), // Pink Magenta
    Color(0xFF2DD4BF)  // Aqua Teal
)

/**
 * Phase 6 — Calm the look:
 * Uses one primary accent color across UI elements so color is reserved for +/- amounts.
 */
fun distinctEnvelopeColor(keyOrName: String, isDark: Boolean = false): Color {
    return if (isDark) VyxelPeriwinkle else MasroufiPrimaryLight
}

fun distinctAccountColor(kind: String, isDark: Boolean = false): Color {
    return if (isDark) VyxelPeriwinkle else MasroufiPrimaryLight
}

