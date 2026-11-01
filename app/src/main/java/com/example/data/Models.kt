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
    val isDarkTheme: Boolean = true,
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
    val colorHex: String = "#10B981",
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
    val accentPalette: String = "emerald", // "emerald", "majorelle", "amber", "amethyst", "terracotta", "ocean"
    val currencySymbol: String = "DH", // "DH", "MAD", "د.م.", "€", "$"
    val customTitle: String = "Masroufi",
    val customSubtitle: String = "",
    val showSafeToSpend: Boolean = true,
    val showAccountsCard: Boolean = true,
    val showQuickPresetsCard: Boolean = true,
    val showWeekSummaryRow: Boolean = true,
    val showMonthlyReviewCard: Boolean = true,
    val showBillsCard: Boolean = true
) {
    fun toVisibilityMask(): Int {
        var mask = 0
        if (showSafeToSpend) mask = mask or 1
        if (showAccountsCard) mask = mask or 2
        if (showQuickPresetsCard) mask = mask or 4
        if (showWeekSummaryRow) mask = mask or 8
        if (showMonthlyReviewCard) mask = mask or 16
        if (showBillsCard) mask = mask or 32
        return mask
    }

    companion object {
        fun fromFinancialItem(item: FinancialItem?): CustomizationPrefs {
            if (item == null) return CustomizationPrefs()
            val parts = item.subtitle.split("|")
            val palette = parts.getOrNull(0)?.ifBlank { "emerald" } ?: "emerald"
            val currency = parts.getOrNull(1)?.ifBlank { "DH" } ?: "DH"
            val mask = if (item.dayOrTurn > 0) item.dayOrTurn else 63
            return CustomizationPrefs(
                accentPalette = palette,
                currencySymbol = currency,
                customTitle = item.name.ifBlank { "Masroufi" },
                customSubtitle = item.dateOrCycle,
                showSafeToSpend = (mask and 1) != 0,
                showAccountsCard = (mask and 2) != 0,
                showQuickPresetsCard = (mask and 4) != 0,
                showWeekSummaryRow = (mask and 8) != 0,
                showMonthlyReviewCard = (mask and 16) != 0,
                showBillsCard = (mask and 32) != 0
            )
        }
    }
}
