package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val baseBudget: Double = 6400.0,
    val payCycleDay: Int = 1,
    val rolloverEnabled: Boolean = true,
    val language: String = "fr",
    @get:PropertyName("isDarkTheme")
    val isDarkTheme: Boolean = false,
    val seeded: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TransactionItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = "expense", // "expense", "income", "transfer"
    val amount: Double = 0.0,
    val category: String = "Autre / Imprévus",
    val envelopeKey: String = "autre",
    val accountId: String = "acc_cash",
    val toAccountId: String = "",
    val desc: String = "",
    val isoDate: String = "", // YYYY-MM-DD
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AccountItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val kind: String = "cash", // "cash", "bank", "wallet"
    val initialBalance: Double = 0.0,
    val colorHex: String = "#10B981",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EnvelopeConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val key: String = "",
    val nameFr: String = "",
    val nameAr: String = "",
    val nameDarija: String = "",
    val nameEn: String = "",
    val percentage: Double = 0.0,
    @get:PropertyName("hasCap")
    val hasCap: Boolean = true,
    val colorHex: String = "#2563EB",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun localizedName(lang: String): String = when (lang) {
        "ar" -> nameAr.ifBlank { nameFr }
        "darija" -> nameDarija.ifBlank { nameFr }
        "en" -> nameEn.ifBlank { nameFr }
        else -> nameFr
    }
}

@IgnoreExtraProperties
data class FinancialItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemType: String = "goal", // "goal", "bill", "debt", "daret", "seasonal", "preset", "custom_prefs"
    val name: String = "",
    val subtitle: String = "", // personName for debt, category for preset, eventType for seasonal, palette|currency for custom_prefs
    val amount: Double = 0.0, // target / bill amount / debt total / monthlyContribution / preset amount
    val secondaryAmount: Double = 0.0, // savedAmount / paidAmount / totalMembers
    val dayOrTurn: Int = 1, // dueDay (1..31) / myTurnNumber / bitmask for visible cards
    val extraInt: Int = 0, // paidMonthsCount for Daret
    val dateOrCycle: String = "", // deadlineIso / payoutDateIso / comma-separated paidCycles / "lent"|"borrowed" / customSubtitle
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CustomizationPrefs(
    val accentPalette: String = "majorelle", // "emerald", "majorelle", "amber", "amethyst", "terracotta", "ocean"
    val currencySymbol: String = "DH", // "DH", "MAD", "د.م.", "€", "$"
    val customTitle: String = "Masroufi",
    val customSubtitle: String = "",
    val showSafeToSpend: Boolean = true,
    val showAccountsCard: Boolean = true,
    val showQuickPresetsCard: Boolean = true,
    val showWeekSummaryRow: Boolean = true,
    val showMonthlyReviewCard: Boolean = true,
    val showBillsCard: Boolean = true,
    val showSmartInsightsCard: Boolean = true,
    val dashboardOrder: List<String> = DEFAULT_DASHBOARD_ORDER
) {
    fun toVisibilityMask(): Int {
        var mask = 0
        if (showSafeToSpend) mask = mask or 1
        if (showAccountsCard) mask = mask or 2
        if (showQuickPresetsCard) mask = mask or 4
        if (showWeekSummaryRow) mask = mask or 8
        if (showMonthlyReviewCard) mask = mask or 16
        if (showBillsCard) mask = mask or 32
        if (showSmartInsightsCard) mask = mask or 64
        return mask
    }

    fun normalizedOrder(): List<String> {
        val valid = dashboardOrder.filter { it in DEFAULT_DASHBOARD_ORDER }.distinct()
        val missing = DEFAULT_DASHBOARD_ORDER.filter { it !in valid }
        return valid + missing
    }

    fun isSectionVisible(sectionKey: String): Boolean = when (sectionKey) {
        "accounts" -> showAccountsCard
        "presets" -> showQuickPresetsCard
        "insights" -> showSmartInsightsCard
        "bills" -> showBillsCard
        "week_summary" -> showWeekSummaryRow
        "monthly_review" -> showMonthlyReviewCard
        else -> true
    }

    fun withSectionVisibility(sectionKey: String, visible: Boolean): CustomizationPrefs = when (sectionKey) {
        "accounts" -> copy(showAccountsCard = visible)
        "presets" -> copy(showQuickPresetsCard = visible)
        "insights" -> copy(showSmartInsightsCard = visible)
        "bills" -> copy(showBillsCard = visible)
        "week_summary" -> copy(showWeekSummaryRow = visible)
        "monthly_review" -> copy(showMonthlyReviewCard = visible)
        else -> this
    }

    companion object {
        val DEFAULT_DASHBOARD_ORDER = listOf(
            "accounts",
            "presets",
            "insights",
            "bills",
            "week_summary",
            "monthly_review"
        )

        fun normalizeOrderString(rawOrder: String?): List<String> {
            if (rawOrder.isNullOrBlank()) return DEFAULT_DASHBOARD_ORDER
            val parsed = rawOrder.split(",").map { it.trim() }.filter { it in DEFAULT_DASHBOARD_ORDER }.distinct()
            val missing = DEFAULT_DASHBOARD_ORDER.filter { it !in parsed }
            return parsed + missing
        }

        fun fromFinancialItem(item: FinancialItem?): CustomizationPrefs {
            if (item == null) return CustomizationPrefs()
            val parts = item.subtitle.split("|")
            val palette = parts.getOrNull(0)?.ifBlank { "emerald" } ?: "emerald"
            val currency = parts.getOrNull(1)?.ifBlank { "DH" } ?: "DH"
            val orderList = normalizeOrderString(parts.getOrNull(2))
            val rawMask = if (item.dayOrTurn > 0) item.dayOrTurn else 127
            val hasInsightsBit = item.dayOrTurn >= 64 || item.dayOrTurn <= 0
            return CustomizationPrefs(
                accentPalette = palette,
                currencySymbol = currency,
                customTitle = item.name.ifBlank { "Masroufi" },
                customSubtitle = item.dateOrCycle,
                showSafeToSpend = (rawMask and 1) != 0,
                showAccountsCard = (rawMask and 2) != 0,
                showQuickPresetsCard = (rawMask and 4) != 0,
                showWeekSummaryRow = (rawMask and 8) != 0,
                showMonthlyReviewCard = (rawMask and 16) != 0,
                showBillsCard = (rawMask and 32) != 0,
                showSmartInsightsCard = if (hasInsightsBit) (rawMask and 64) != 0 else true,
                dashboardOrder = orderList
            )
        }
    }
}
