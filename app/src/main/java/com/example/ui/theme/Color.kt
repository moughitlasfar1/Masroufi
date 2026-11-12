package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// Dark Palette — Deep Charcoal & Indigo (#B8ADFF)
val MasroufiSurfaceDark = Color(0xFF0B0D13)
val MasroufiSurfaceContainerDark = Color(0xFF131620)
val MasroufiSurfaceContainerHighDark = Color(0xFF1B1F2C)
val MasroufiSurfaceContainerHighestDark = Color(0xFF252A3A)
val MasroufiOnSurfaceDark = Color(0xFFF8FAFC)
val MasroufiOnSurfaceVariantDark = Color(0xFFC8D1E3)
val MasroufiOutlineVariantDark = Color(0xFF2B3142)
val MasroufiPrimaryDark = Color(0xFFB8ADFF)
val MasroufiPrimaryBrightDark = Color(0xFFD6CEFF)
val MasroufiPrimaryContainerDark = Color(0xFF28234A)
val MasroufiOnPrimaryContainerDark = Color(0xFFF2EFFF)
val MasroufiErrorDark = Color(0xFFFB7185)
val MasroufiErrorContainerDark = Color(0xFF881337)

// Light Palette — Crisp Alabaster & Pure White Cards with Indigo (#5546C7)
val MasroufiSurfaceLight = Color(0xFFF5F6FA)
val MasroufiSurfaceContainerLight = Color(0xFFFFFFFF)
val MasroufiSurfaceContainerHighLight = Color(0xFFEEF1F8)
val MasroufiSurfaceContainerHighestLight = Color(0xFFE2E7F2)
val MasroufiOnSurfaceLight = Color(0xFF0F172A)
val MasroufiOnSurfaceVariantLight = Color(0xFF334155)
val MasroufiOutlineVariantLight = Color(0xFFCBD5E1)
val MasroufiPrimaryLight = Color(0xFF5546C7)
val MasroufiPrimaryContainerLight = Color(0xFFEAE6FF)
val MasroufiOnPrimaryContainerLight = Color(0xFF1F1659)
val MasroufiErrorLight = Color(0xFFE11D48)
val MasroufiErrorContainerLight = Color(0xFFFFE4E6)

// Indigo Accent Tokens (kept with compatible aliases for existing call sites)
val VyxelPeriwinkle = Color(0xFFB8ADFF)
val VyxelOnPeriwinkle = Color(0xFF1B1347)
val VyxelCardDark = Color(0xFF131620)
val VyxelPillDark = Color(0xFF1E2230)
val VyxelPillBorderDark = Color(0xFF2B3142)

// Semantic Accents (Income, Expense, Transfer, Warning)
val AccentIndigo = Color(0xFFB8ADFF)
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
    if (isDark) Color(0xFFB8ADFF) else Color(0xFF5546C7)

fun semanticWarningColor(isDark: Boolean): Color =
    if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)

/**
 * Calm the look: Uses one primary accent color across UI elements so color is reserved for +/- amounts.
 */
fun distinctEnvelopeColor(keyOrName: String, isDark: Boolean = false): Color {
    return if (isDark) VyxelPeriwinkle else MasroufiPrimaryLight
}

fun distinctAccountColor(kind: String, isDark: Boolean = false): Color {
    return if (isDark) VyxelPeriwinkle else MasroufiPrimaryLight
}


