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
        Index(value = ["envelopeKey"]),
        Index(value = ["transferGroupId"]),
        Index(value = ["loanId"]),
        Index(value = ["recurringOccurrenceKey"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String = "expense", // "expense", "income", "transfer", "transfer_out", "transfer_in"
    val amountCentimes: Long = 0L,
    val category: String = "autre",
    val envelopeKey: String = "autre",
    val accountId: String = "acc_cash",
    val toAccountId: String = "",
    val desc: String = "",
    val isoDate: String = "", // YYYY-MM-DD
    val hourOfDay: Int = -1,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val transferGroupId: String? = null,
    val loanId: String? = null,
    val recurringOccurrenceKey: String? = null
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

/**
 * Room v3 tracked loan / debt / receivable entity using exact `Long` centimes.
 */
@Entity(
    tableName = "loans",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "status"]),
        Index(value = ["userId", "direction"]),
        Index(value = ["sourceFinancialItemId"]),
        Index(value = ["sourceSavedCreditId"])
    ]
)
data class LoanEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val counterparty: String = "",
    val direction: String = "LIABILITY", // "LIABILITY" | "RECEIVABLE"
    val category: String = "PERSONAL_DEBT", // "BANK_CREDIT" | "PERSONAL_DEBT" | "DARET"
    val principalCentimes: Long = 0L,
    val remainingCentimes: Long = 0L,
    val monthlyPaymentCentimes: Long = 0L,
    val status: String = "ACTIVE", // "ACTIVE" | "PAID_OFF"
    val linkedAccountId: String? = null,
    val sourceFinancialItemId: String? = null,
    val sourceSavedCreditId: String? = null,
    val dueIsoDate: String? = null,
    val notes: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)

/**
 * Room v3 immutable ledger of repayments recorded against a [LoanEntity].
 */
@Entity(
    tableName = "loan_repayments",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["loanId"]),
        Index(value = ["loanId", "isoDate"])
    ]
)
data class LoanRepaymentEntity(
    @PrimaryKey val id: String,
    val loanId: String,
    val userId: String,
    val amountCentimes: Long = 0L,
    val isoDate: String = "",
    val linkedAccountId: String? = null,
    val linkedTransactionId: String? = null,
    val note: String = "",
    val createdAtEpochMs: Long = 0L
)

/**
 * Room v4 recurring rule entity for scheduled expenses, incomes, and loan installments
 * using exact `Long` centimes.
 */
@Entity(
    tableName = "recurring_rules",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "isPaused", "nextDueIsoDate"]),
        Index(value = ["linkedLoanId"]),
        Index(value = ["sourceFinancialItemId"])
    ]
)
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val direction: String = "EXPENSE", // "EXPENSE" | "INCOME" | "LOAN_REPAYMENT"
    val amountCentimes: Long = 0L,
    val frequency: String = "MONTHLY", // "WEEKLY" | "BIWEEKLY" | "MONTHLY" | "YEARLY"
    val anchorDayOfMonth: Int = 1,
    val startIsoDate: String = "",
    val nextDueIsoDate: String = "",
    val endIsoDate: String? = null,
    val postingMode: String = "CONFIRM", // "AUTO_POST" | "CONFIRM"
    val accountId: String = "acc_cash",
    val envelopeKey: String = "charges",
    val category: String = "charges",
    val linkedLoanId: String? = null,
    val sourceFinancialItemId: String? = null,
    val isPaused: Boolean = false,
    val lastPostedOccurrenceKey: String? = null,
    val skippedOccurrenceKeys: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)
