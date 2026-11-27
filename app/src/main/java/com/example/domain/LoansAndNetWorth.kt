package com.example.domain

import com.example.data.AccountItem
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import com.masroufi.core.Money
import java.util.Locale

/**
 * Direction of a tracked loan or personal debt relative to the user's Net Worth.
 * - [LIABILITY]: User owes money (bank credit, borrowed personal debt, unpaid Daret obligation).
 *   Subtracts from Net Worth.
 * - [RECEIVABLE]: Someone owes the user money (Sellef / money lent).
 *   Adds to Net Worth.
 */
enum class LoanDirection(val wire: String) {
    LIABILITY("LIABILITY"),
    RECEIVABLE("RECEIVABLE");

    companion object {
        fun fromWire(raw: String?): LoanDirection = when (raw?.trim()?.uppercase(Locale.ROOT)) {
            "RECEIVABLE", "LENT" -> RECEIVABLE
            else -> LIABILITY
        }
    }
}

/**
 * Classification of a tracked loan/debt item.
 */
enum class LoanCategory(val wire: String) {
    BANK_CREDIT("BANK_CREDIT"),
    PERSONAL_DEBT("PERSONAL_DEBT"),
    DARET("DARET");

    companion object {
        fun fromWire(raw: String?): LoanCategory = when (raw?.trim()?.uppercase(Locale.ROOT)) {
            "BANK_CREDIT", "CREDIT" -> BANK_CREDIT
            "DARET" -> DARET
            else -> PERSONAL_DEBT
        }
    }
}

/**
 * Lifecycle status of a tracked loan.
 * Automatically transitions to [PAID_OFF] when `remaining == Money.ZERO`.
 */
enum class LoanStatus(val wire: String) {
    ACTIVE("ACTIVE"),
    PAID_OFF("PAID_OFF");

    companion object {
        fun fromWire(raw: String?): LoanStatus = when (raw?.trim()?.uppercase(Locale.ROOT)) {
            "PAID_OFF", "PAID", "SETTLED" -> PAID_OFF
            else -> ACTIVE
        }
    }
}

/**
 * Domain model for a tracked loan, personal debt (Sellef), bank credit, or Daret obligation.
 * All monetary amounts use exact integer `Long` centimes via [Money].
 */
data class LoanItem(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val counterparty: String = "",
    val direction: LoanDirection = LoanDirection.LIABILITY,
    val category: LoanCategory = LoanCategory.PERSONAL_DEBT,
    val principal: Money = Money.ZERO,
    val remaining: Money = Money.ZERO,
    val monthlyPayment: Money = Money.ZERO,
    val status: LoanStatus = LoanStatus.ACTIVE,
    val linkedAccountId: String? = null,
    val sourceFinancialItemId: String? = null,
    val sourceSavedCreditId: String? = null,
    val dueIsoDate: String? = null,
    val notes: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
) {
    val paidAmount: Money
        get() = (principal - remaining).coerceAtLeast(Money.ZERO)

    val isPaidOff: Boolean
        get() = status == LoanStatus.PAID_OFF || !remaining.isPositive
}

/**
 * Immutable ledger entry recording a single repayment against a [LoanItem].
 */
data class LoanRepaymentItem(
    val id: String = "",
    val loanId: String = "",
    val userId: String = "",
    val amount: Money = Money.ZERO,
    val isoDate: String = "",
    val linkedAccountId: String? = null,
    val linkedTransactionId: String? = null,
    val note: String = "",
    val createdAtEpochMs: Long = 0L
)

/**
 * Deterministic outcome of a transactional loan repayment attempt with overpayment protection.
 */
sealed class LoanRepaymentResult {
    data class Success(
        val updatedLoan: LoanItem,
        val repayment: LoanRepaymentItem,
        val linkedTransaction: TransactionItem?
    ) : LoanRepaymentResult()

    data class OverpaymentBlocked(
        val loanId: String,
        val remaining: Money,
        val attempted: Money
    ) : LoanRepaymentResult()

    data class AlreadyPaidOff(val loanId: String) : LoanRepaymentResult()

    data object InvalidAmount : LoanRepaymentResult()

    data class LoanNotFound(val loanId: String) : LoanRepaymentResult()
}

/**
 * Deterministic outcome of an atomic paired internal transfer (`transfer_out` + `transfer_in`).
 */
sealed class InternalTransferResult {
    data class Success(
        val transferGroupId: String,
        val outgoingTx: TransactionItem,
        val incomingTx: TransactionItem
    ) : InternalTransferResult()

    data object InvalidAccounts : InternalTransferResult()

    data object InvalidAmount : InternalTransferResult()
}

/**
 * Unified reactive Net Worth snapshot exposed across Dashboard, Accounts, and Loans.
 *
 * Exact accounting invariant (all `Long` centimes, zero `Float`/`Double`):
 * `netWorth = totalLiquidAssets + totalReceivables - totalLiabilities`
 */
data class NetWorthSnapshot(
    val totalLiquidAssets: Money = Money.ZERO,
    val totalReceivables: Money = Money.ZERO,
    val totalLiabilities: Money = Money.ZERO,
    val netWorth: Money = Money.ZERO,
    val accountBalances: List<AccountWithBalance> = emptyList(),
    val activeLoansCount: Int = 0,
    val paidOffLoansCount: Int = 0
)

/**
 * Pure Kotlin exact `Long` centimes accounting engine for Account Balances and Unified Net Worth.
 *
 * Zero `Float` or `Double` arithmetic is used anywhere in this calculator.
 */
object NetWorthCalculator {

    /**
     * Deterministically computes per-account balances from `initialBalance` and all transactions.
     *
     * Supports both:
     * - Phase 5 paired internal transfers (`transfer_out` on source account, `transfer_in` on destination account)
     * - Legacy single-row internal transfers (`type == "transfer"` with `accountId` -> `toAccountId`)
     * - Loan settlement transactions (`transfer_out` debiting linked account for LIABILITY repayments,
     *   `transfer_in` crediting linked account for RECEIVABLE repayments)
     */
    fun computeAccountBalances(
        accounts: List<AccountItem>,
        transactions: List<TransactionItem>
    ): List<AccountWithBalance> {
        return accounts.map { acc ->
            var totalInCentimes = 0L
            var totalOutCentimes = 0L
            for (tx in transactions) {
                val amtCentimes = tx.amount.centimes
                when (tx.type) {
                    "income", "transfer_in" -> {
                        if (tx.accountId == acc.id) {
                            totalInCentimes += amtCentimes
                        }
                    }
                    "expense", "transfer_out" -> {
                        if (tx.accountId == acc.id) {
                            totalOutCentimes += amtCentimes
                        }
                    }
                    "transfer" -> {
                        if (tx.accountId == acc.id) {
                            totalOutCentimes += amtCentimes
                        }
                        if (tx.toAccountId == acc.id) {
                            totalInCentimes += amtCentimes
                        }
                    }
                }
            }
            val totalIn = Money.fromCentimes(totalInCentimes)
            val totalOut = Money.fromCentimes(totalOutCentimes)
            val currentBalance = Money.fromCentimes(
                acc.initialBalance.centimes + totalInCentimes - totalOutCentimes
            )
            AccountWithBalance(
                account = acc,
                currentBalance = currentBalance,
                totalIn = totalIn,
                totalOut = totalOut
            )
        }
    }

    /**
     * Computes the unified [NetWorthSnapshot] across all accounts, transactions, tracked [loans],
     * and any unmigrated legacy `debt` rows in [legacyFinancialItems].
     *
     * Deduplication guarantee:
     * Any legacy `financial_items` debt whose `id` is referenced by a [LoanItem.sourceFinancialItemId]
     * is excluded from the legacy fallback sum so liabilities/receivables are never double-counted.
     */
    fun compute(
        accounts: List<AccountItem>,
        transactions: List<TransactionItem>,
        loans: List<LoanItem> = emptyList(),
        legacyFinancialItems: List<FinancialItem> = emptyList()
    ): NetWorthSnapshot {
        val accountBalances = computeAccountBalances(accounts, transactions)
        var liquidCentimes = 0L
        for (bal in accountBalances) {
            liquidCentimes += bal.currentBalance.centimes
        }

        val migratedLegacyIds = HashSet<String>()
        var receivablesCentimes = 0L
        var liabilitiesCentimes = 0L
        var activeCount = 0
        var paidOffCount = 0

        for (loan in loans) {
            loan.sourceFinancialItemId?.takeIf { it.isNotBlank() }?.let { migratedLegacyIds.add(it) }
            val remCentimes = loan.remaining.centimes.coerceAtLeast(0L)
            val effectivePaidOff = loan.status == LoanStatus.PAID_OFF || remCentimes == 0L
            if (effectivePaidOff) {
                paidOffCount++
            } else {
                activeCount++
                when (loan.direction) {
                    LoanDirection.RECEIVABLE -> receivablesCentimes += remCentimes
                    LoanDirection.LIABILITY -> liabilitiesCentimes += remCentimes
                }
            }
        }

        for (item in legacyFinancialItems) {
            if (item.itemType != "debt") continue
            if (item.id.isNotBlank() && item.id in migratedLegacyIds) continue
            val remCentimes = (item.amount.centimes - item.secondaryAmount.centimes).coerceAtLeast(0L)
            if (remCentimes == 0L) {
                paidOffCount++
            } else {
                activeCount++
                val isBorrowed = item.dateOrCycle.trim().equals("borrowed", ignoreCase = true)
                if (isBorrowed) {
                    liabilitiesCentimes += remCentimes
                } else {
                    receivablesCentimes += remCentimes
                }
            }
        }

        val netWorthCentimes = liquidCentimes + receivablesCentimes - liabilitiesCentimes
        return NetWorthSnapshot(
            totalLiquidAssets = Money.fromCentimes(liquidCentimes),
            totalReceivables = Money.fromCentimes(receivablesCentimes),
            totalLiabilities = Money.fromCentimes(liabilitiesCentimes),
            netWorth = Money.fromCentimes(netWorthCentimes),
            accountBalances = accountBalances,
            activeLoansCount = activeCount,
            paidOffLoansCount = paidOffCount
        )
    }
}
