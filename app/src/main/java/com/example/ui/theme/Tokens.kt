package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized Material 3 Expressive design tokens for Masroufi.
 * Defines the 4/8/12/16/24/32dp spacing grid, corner radii, and the three card tiers:
 * - [CardTier.HERO]: Signature gradient surface (e.g., Safe to Spend hero card).
 * - [CardTier.STANDARD]: Solid surfaceContainer card used for all regular content cards.
 * - [CardTier.GLASS]: Translucent glass accent surface reserved for the dock, floating panels, and overlays.
 */
enum class CardTier {
    HERO,
    STANDARD,
    GLASS
}

object MasroufiTokens {
    object Spacing {
        val xs: Dp = 4.dp
        val sm: Dp = 8.dp
        val md: Dp = 12.dp
        val lg: Dp = 16.dp
        val xl: Dp = 24.dp
        val xxl: Dp = 32.dp
    }

    object CornerRadius {
        val chip: Dp = 16.dp
        val chipSelected: Dp = 14.dp
        val innerTile: Dp = 18.dp
        val cardCompact: Dp = 24.dp
        val card: Dp = 26.dp
        val heroCard: Dp = 28.dp
        val sheet: Dp = 30.dp
        val dock: Dp = 36.dp
    }

    object Shapes {
        val chip = RoundedCornerShape(CornerRadius.chip)
        val innerTile = RoundedCornerShape(CornerRadius.innerTile)
        val cardCompact = RoundedCornerShape(CornerRadius.cardCompact)
        val card = RoundedCornerShape(CornerRadius.card)
        val heroCard = RoundedCornerShape(CornerRadius.heroCard)
        val sheet = RoundedCornerShape(topStart = CornerRadius.sheet, topEnd = CornerRadius.sheet)
        val dock = RoundedCornerShape(CornerRadius.dock)
    }
}
