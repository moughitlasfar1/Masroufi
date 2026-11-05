package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// Dark Palette — Revolut Dark Slate & Deep Obsidian (Zero green tint, high contrast)
val MasroufiSurfaceDark = Color(0xFF090D16)
val MasroufiSurfaceContainerDark = Color(0xFF121927)
val MasroufiSurfaceContainerHighDark = Color(0xFF1B2538)
val MasroufiSurfaceContainerHighestDark = Color(0xFF26334D)
val MasroufiOnSurfaceDark = Color(0xFFF8FAFC)
val MasroufiOnSurfaceVariantDark = Color(0xFFCBD5E1)
val MasroufiOutlineVariantDark = Color(0xFF29364F)
val MasroufiPrimaryDark = Color(0xFF60A5FA)
val MasroufiPrimaryBrightDark = Color(0xFF93C5FD)
val MasroufiPrimaryContainerDark = Color(0xFF1E3A8A)
val MasroufiOnPrimaryContainerDark = Color(0xFFDBEAFE)
val MasroufiErrorDark = Color(0xFFFB7185)
val MasroufiErrorContainerDark = Color(0xFF881337)

// Light Palette — Crisp Alabaster & Pure White Cards (High-contrast Fintech Light Mode)
val MasroufiSurfaceLight = Color(0xFFF3F6FA)
val MasroufiSurfaceContainerLight = Color(0xFFFFFFFF)
val MasroufiSurfaceContainerHighLight = Color(0xFFF1F5F9)
val MasroufiSurfaceContainerHighestLight = Color(0xFFE2E8F0)
val MasroufiOnSurfaceLight = Color(0xFF0B1120)
val MasroufiOnSurfaceVariantLight = Color(0xFF334155)
val MasroufiOutlineVariantLight = Color(0xFFCBD5E1)
val MasroufiPrimaryLight = Color(0xFF2563EB)
val MasroufiPrimaryContainerLight = Color(0xFFDBEAFE)
val MasroufiOnPrimaryContainerLight = Color(0xFF1E3A8A)
val MasroufiErrorLight = Color(0xFFE11D48)
val MasroufiErrorContainerLight = Color(0xFFFFE4E6)

// Vibrant Semantic Accents (Income, Expense, Transfer, Categories)
val AccentIndigo = Color(0xFF2563EB)
val AccentSkyBlue = Color(0xFF0EA5E9)
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
    if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

fun semanticWarningColor(isDark: Boolean): Color =
    if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)

private val DistinctEnvelopeColorsLight = listOf(
    Color(0xFF2563EB), // Royal Indigo
    Color(0xFFE11D48), // Crimson Rose
    Color(0xFFD97706), // Warm Amber
    Color(0xFF7C3AED), // Vivid Violet
    Color(0xFF0284C7), // Ocean Cyan
    Color(0xFF059669), // Crisp Emerald
    Color(0xFFDB2777), // Fuchsia Berry
    Color(0xFF0D9488)  // Deep Teal
)

private val DistinctEnvelopeColorsDark = listOf(
    Color(0xFF60A5FA), // Electric Blue
    Color(0xFFFB7185), // Bright Coral Rose
    Color(0xFFFBBF24), // Bright Gold
    Color(0xFFA78BFA), // Bright Violet
    Color(0xFF38BDF8), // Sky Cyan
    Color(0xFF34D399), // Mint Emerald
    Color(0xFFF472B6), // Pink Magenta
    Color(0xFF2DD4BF)  // Aqua Teal
)

/**
 * Returns a distinct, high-contrast semantic color for each budget envelope or category
 * so envelopes are visually differentiated at a glance instead of sharing a single green color.
 */
fun distinctEnvelopeColor(keyOrName: String, isDark: Boolean = false): Color {
    val clean = keyOrName.trim().lowercase()
    val palette = if (isDark) DistinctEnvelopeColorsDark else DistinctEnvelopeColorsLight
    return when {
        clean.contains("marche") || clean.contains("marché") || clean.contains("course") || clean.contains("تقدية") || clean.contains("سوق") -> palette[0]
        clean.contains("cafe") || clean.contains("café") || clean.contains("sortie") || clean.contains("resto") || clean.contains("قهوة") -> palette[2]
        clean.contains("transport") || clean.contains("carburant") || clean.contains("taxi") || clean.contains("طاكسي") || clean.contains("بنزين") -> palette[4]
        clean.contains("charge") || clean.contains("loyer") || clean.contains("logement") || clean.contains("facture") || clean.contains("كراء") || clean.contains("فواتير") -> palette[3]
        clean.contains("sante") || clean.contains("santé") || clean.contains("pharma") || clean.contains("صحة") || clean.contains("دواء") -> palette[1]
        clean.contains("epargne") || clean.contains("épargne") || clean.contains("tawfir") || clean.contains("توفير") -> palette[5]
        clean.contains("shop") || clean.contains("vetement") || clean.contains("loisir") -> palette[6]
        clean.contains("autre") || clean.contains("imprevu") || clean.contains("أخرى") -> palette[7]
        else -> palette[abs(clean.hashCode()) % palette.size]
    }
}

fun distinctAccountColor(kind: String, isDark: Boolean = false): Color {
    return when (kind.lowercase()) {
        "bank" -> if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
        "wallet" -> if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
        else -> if (isDark) Color(0xFF34D399) else Color(0xFF059669)
    }
}
