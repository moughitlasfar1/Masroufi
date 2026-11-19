package com.example.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

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
 * Visual identity for a transaction category or budget envelope:
 * - [icon]: Semantic Material icon representing the category.
 * - [foreground]: Readable foreground color for icons, donut segments, and badges in light/dark themes.
 * - [container]: Soft low-alpha tint background so category badges stay calm and never compete with +/- amounts.
 */
data class CategoryVisualStyle(
    val categoryKey: String,
    val icon: ImageVector,
    val foreground: Color,
    val container: Color
)

fun categoryVisualStyle(keyOrName: String, isDark: Boolean = false): CategoryVisualStyle {
    val raw = keyOrName.trim().lowercase()
    return when {
        raw.contains("alim") || raw.contains("course") || raw.contains("food") ||
            raw.contains("march") || raw.contains("grocer") ||
            raw.contains("تغذية") || raw.contains("مقاضي") || raw.contains("ماكلة") -> {
            val fg = if (isDark) Color(0xFF5EEAD4) else Color(0xFF0F766E)
            CategoryVisualStyle(
                categoryKey = "food",
                icon = Icons.Default.ShoppingCart,
                foreground = fg,
                container = fg.copy(alpha = if (isDark) 0.16f else 0.12f)
            )
        }
        raw.contains("logement") || raw.contains("maison") || raw.contains("charge") ||
            raw.contains("loyer") || raw.contains("home") || raw.contains("rent") ||
            raw.contains("سكن") || raw.contains("كراء") || raw.contains("دار") -> {
            val fg = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
            CategoryVisualStyle(
                categoryKey = "housing",
                icon = Icons.Default.Home,
                foreground = fg,
                container = fg.copy(alpha = if (isDark) 0.16f else 0.12f)
            )
        }
        raw.contains("transp") || raw.contains("taxi") || raw.contains("carbur") ||
            raw.contains("tram") || raw.contains("bus") || raw.contains("auto") ||
            raw.contains("تنقل") || raw.contains("طاكسي") || raw.contains("مركوب") -> {
            val fg = if (isDark) Color(0xFFFCD34D) else Color(0xFFB45309)
            CategoryVisualStyle(
                categoryKey = "transport",
                icon = Icons.Default.DirectionsCar,
                foreground = fg,
                container = fg.copy(alpha = if (isDark) 0.16f else 0.12f)
            )
        }
        raw.contains("sant") || raw.contains("health") || raw.contains("pharma") ||
            raw.contains("med") || raw.contains("صحة") || raw.contains("دوا") || raw.contains("طبيب") -> {
            val fg = if (isDark) Color(0xFFF9A8D4) else Color(0xFFBE185D)
            CategoryVisualStyle(
                categoryKey = "health",
                icon = Icons.Default.LocalHospital,
                foreground = fg,
                container = fg.copy(alpha = if (isDark) 0.16f else 0.12f)
            )
        }
        raw.contains("loisir") || raw.contains("caf") || raw.contains("resto") ||
            raw.contains("sortie") || raw.contains("qahwa") || raw.contains("coffee") ||
            raw.contains("ترفيه") || raw.contains("قهوة") || raw.contains("خروج") -> {
            val fg = if (isDark) Color(0xFFC4B5FD) else Color(0xFF6D28D9)
            CategoryVisualStyle(
                categoryKey = "leisure",
                icon = Icons.Default.LocalCafe,
                foreground = fg,
                container = fg.copy(alpha = if (isDark) 0.16f else 0.12f)
            )
        }
        raw.contains("famil") || raw.contains("parent") || raw.contains("aide") ||
            raw.contains("عائلة") || raw.contains("والدين") -> {
            val fg = if (isDark) Color(0xFFFDBA74) else Color(0xFFC2410C)
            CategoryVisualStyle(
                categoryKey = "family",
                icon = Icons.Default.Favorite,
                foreground = fg,
                container = fg.copy(alpha = if (isDark) 0.16f else 0.12f)
            )
        }
        raw.contains("shop") || raw.contains("vetement") || raw.contains("vêtement") ||
            raw.contains("clothes") || raw.contains("تسوق") || raw.contains("حوايج") -> {
            val fg = if (isDark) Color(0xFFD8B4FE) else Color(0xFF7E22CE)
            CategoryVisualStyle(
                categoryKey = "shopping",
                icon = Icons.Default.ShoppingBag,
                foreground = fg,
                container = fg.copy(alpha = if (isDark) 0.16f else 0.12f)
            )
        }
        raw.contains("educ") || raw.contains("ecole") || raw.contains("école") ||
            raw.contains("school") || raw.contains("تعليم") || raw.contains("قراية") -> {
            val fg = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1)
            CategoryVisualStyle(
                categoryKey = "education",
                icon = Icons.Default.School,
                foreground = fg,
                container = fg.copy(alpha = if (isDark) 0.16f else 0.12f)
            )
        }
        raw.contains("factur") || raw.contains("bill") || raw.contains("abon") ||
            raw.contains("telecom") || raw.contains("internet") ||
            raw.contains("فواتير") || raw.contains("فاتورة") || raw.contains("انترنت") -> {
            val fg = if (isDark) Color(0xFFA5B4FC) else Color(0xFF4338CA)
            CategoryVisualStyle(
                categoryKey = "bills",
                icon = Icons.Default.ReceiptLong,
                foreground = fg,
                container = fg.copy(alpha = if (isDark) 0.16f else 0.12f)
            )
        }
        raw.contains("epargn") || raw.contains("épargn") || raw.contains("sav") ||
            raw.contains("ادخار") || raw.contains("توفير") -> {
            val fg = if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
            CategoryVisualStyle(
                categoryKey = "savings",
                icon = Icons.Default.Savings,
                foreground = fg,
                container = fg.copy(alpha = if (isDark) 0.16f else 0.12f)
            )
        }
        raw.contains("transf") || raw.contains("تحويل") -> {
            val fg = semanticTransferColor(isDark)
            CategoryVisualStyle(
                categoryKey = "transfer",
                icon = Icons.Default.SwapHoriz,
                foreground = fg,
                container = fg.copy(alpha = if (isDark) 0.16f else 0.12f)
            )
        }
        else -> {
            // Calm neutral default for unknown or custom categories
            val fg = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
            CategoryVisualStyle(
                categoryKey = "default",
                icon = Icons.Default.Category,
                foreground = fg,
                container = fg.copy(alpha = if (isDark) 0.16f else 0.12f)
            )
        }
    }
}

/**
 * Returns the category's soft foreground tint for charts, progress bars, and envelope icons.
 * Note: Transaction amounts strictly use [semanticIncomeColor] and [semanticExpenseColor], never category tints.
 */
fun distinctEnvelopeColor(keyOrName: String, isDark: Boolean = false): Color {
    return categoryVisualStyle(keyOrName, isDark).foreground
}

fun distinctAccountColor(kind: String, isDark: Boolean = false): Color {
    val norm = kind.trim().lowercase()
    return when {
        norm.contains("bank") || norm.contains("banq") -> if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
        norm.contains("sav") || norm.contains("epargn") -> if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
        else -> if (isDark) VyxelPeriwinkle else MasroufiPrimaryLight
    }
}

fun accountKindIcon(kind: String): ImageVector {
    val norm = kind.trim().lowercase()
    return when {
        norm.contains("bank") || norm.contains("banq") -> Icons.Default.AccountBalance
        norm.contains("sav") || norm.contains("epargn") -> Icons.Default.Savings
        else -> Icons.Default.AccountBalanceWallet
    }
}
