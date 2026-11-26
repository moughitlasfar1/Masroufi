package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val baseBudgetCentimes: Long = 640_000L,
    val payCycleDay: Int = 1,
    val rolloverEnabled: Boolean = true,
    val language: String = "fr",
    val isDarkTheme: Boolean = false,
    val seeded: Boolean = true,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["userId"])]
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val kind: String = "cash", // "cash", "bank", "wallet"
    val currency: String = "MAD",
    val initialBalanceCentimes: Long = 0L,
    val colorHex: String = "#10B981",
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "isoDate"]),
        Index(value = ["accountId"]),
        Index(value = ["envelopeKey"])
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String = "expense", // "expense", "income", "transfer"
    val amountCentimes: Long = 0L,
    val category: String = "autre",
    val envelopeKey: String = "autre",
    val accountId: String = "acc_cash",
    val toAccountId: String = "",
    val desc: String = "",
    val isoDate: String = "", // YYYY-MM-DD
    val hourOfDay: Int = -1,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)

@Entity(
    tableName = "envelopes",
    indices = [Index(value = ["userId"])]
)
data class EnvelopeEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val key: String,
    val nameFr: String = "",
    val nameAr: String = "",
    val nameDarija: String = "",
    val nameEn: String = "",
    val percentage: Double = 0.0,
    val hasCap: Boolean = true,
    val colorHex: String = "#2563EB",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)

@Entity(
    tableName = "financial_items",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "itemType"])
    ]
)
data class FinancialItemEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val itemType: String = "goal", // "goal", "bill", "debt", "daret", "seasonal", "preset", "custom_prefs"
    val name: String = "",
    val subtitle: String = "",
    val amountCentimes: Long = 0L,
    val secondaryAmountCentimes: Long = 0L,
    val daretMembers: Int? = null,
    val dayOrTurn: Int = 1,
    val extraInt: Int = 0,
    val dateOrCycle: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)

@Entity(
    tableName = "saved_credits",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "createdAtEpochMs"])
    ]
)
data class SavedCreditLocalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String = "CONSOMMATION",
    val loanAmountCentimes: Long = 0L,
    val downPaymentCentimes: Long = 0L,
    val financedPrincipalCentimes: Long = 0L,
    val durationMonths: Int = 60,
    val deferredMonths: Int = 0,
    val annualInterestRateHt: Double = 7.5,
    val applyInterestTva: Boolean = true,
    val insuranceRate: Double = 0.0,
    val monthlyPaymentCentimes: Long = 0L,
    val totalCostCentimes: Long = 0L,
    val debtToIncomeRatio: Double = 0.0,
    val maxAllowedDti: Double = 45.0,
    val linkToMonthlyExpenses: Boolean = false,
    val createdAtEpochMs: Long = 0L
)

/**
 * Tracks one-time Firestore -> Room import verification metadata (used in Phase 1b).
 */
@Entity(tableName = "import_metadata")
data class ImportMetadataEntity(
    @PrimaryKey val userId: String,
    val completedAtEpochMs: Long = 0L,
    val backupChecksumSha256: String = "",
    val profileCount: Int = 0,
    val accountCount: Int = 0,
    val transactionCount: Int = 0,
    val envelopeCount: Int = 0,
    val financialItemCount: Int = 0,
    val creditCount: Int = 0
)
