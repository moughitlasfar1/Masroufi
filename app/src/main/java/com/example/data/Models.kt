package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val baseBudget: Double = 6400.0,
    val payCycleDay: Int = 1,
    val rolloverEnabled: Boolean = true,
    val language: String = "fr",
    val isDarkTheme: Boolean = true,
    val seeded: Boolean = false,
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
    val itemType: String = "goal", // "goal", "bill", "debt", "daret", "seasonal", "preset"
    val name: String = "",
    val subtitle: String = "", // personName for debt, category for preset, eventType for seasonal
    val amount: Double = 0.0, // target / bill amount / debt total / monthlyContribution / preset amount
    val secondaryAmount: Double = 0.0, // savedAmount / paidAmount / totalMembers
    val dayOrTurn: Int = 1, // dueDay (1..31) / myTurnNumber
    val extraInt: Int = 0, // paidMonthsCount for Daret
    val dateOrCycle: String = "", // deadlineIso / payoutDateIso / comma-separated paidCycles / "lent"|"borrowed"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
