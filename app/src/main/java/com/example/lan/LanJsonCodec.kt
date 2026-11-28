package com.example.lan

import com.example.data.AccountItem
import com.example.data.EnvelopeConfig
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.domain.AccountWithBalance
import com.example.domain.CashFlowForecastSnapshot
import com.example.domain.FinancialInsightsSnapshot
import com.example.domain.LoanItem
import com.example.domain.LoanRepaymentItem
import com.example.domain.NetWorthSnapshot
import com.example.domain.PayCycleWindow
import com.example.domain.RecurringOccurrence
import com.example.domain.RecurringRuleItem
import com.masroufi.core.Money
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reflection-free manual `JSONObject` / `JSONArray` codec for the Local LAN Companion HTTP API.
 *
 * Strict invariants:
 * - ZERO `Double` or `Float` for any monetary value; all amounts are exact `Long` `*Centimes`.
 * - Optional DH text parsing strictly delegates to [Money.fromDhString] (`MoneyParser` / `BigDecimal`).
 */
object LanJsonCodec {

    fun errorJson(code: String, message: String, extra: Map<String, Any?> = emptyMap()): String {
        val obj = JSONObject()
        obj.put("error", code)
        obj.put("message", message)
        for ((k, v) in extra) {
            if (v != null) obj.put(k, v)
        }
        return obj.toString()
    }

    /**
     * Parses a monetary field from a JSON payload strictly into exact `Long` centimes (`Money`):
     * - Prefers exact integer `amountCentimes` (`Long`). Rejects fractional floating-point numbers!
     * - Falls back to `amountDhText` parsed strictly via [Money.fromDhString] (`BigDecimal`).
     */
    fun parseExactMoney(obj: JSONObject): Money? {
        if (obj.has("amountCentimes") && !obj.isNull("amountCentimes")) {
            val raw = obj.opt("amountCentimes")
            val centimes: Long = when (raw) {
                is Long -> raw
                is Int -> raw.toLong()
                is String -> raw.trim().toLongOrNull() ?: return null
                else -> return null // Reject Double/Float JSON values strictly!
            }
            if (centimes <= 0L || centimes > Money.MAX_STORABLE.centimes) return null
            return Money.fromCentimes(centimes)
        }
        if (obj.has("amountDhText") && !obj.isNull("amountDhText")) {
            val text = obj.optString("amountDhText", "").trim()
            val parsed = Money.fromDhString(text) ?: return null
            if (!parsed.isPositive) return null
            return parsed
        }
        return null
    }

    fun encodeTransaction(tx: TransactionItem): JSONObject = JSONObject().apply {
        put("id", tx.id)
        put("type", tx.type)
        put("amountCentimes", tx.amount.centimes)
        put("category", tx.category)
        put("envelopeKey", tx.envelopeKey)
        put("accountId", tx.accountId)
        put("toAccountId", tx.toAccountId)
        put("transferGroupId", tx.transferGroupId ?: JSONObject.NULL)
        put("loanId", tx.loanId ?: JSONObject.NULL)
        put("recurringOccurrenceKey", tx.recurringOccurrenceKey ?: JSONObject.NULL)
        put("desc", tx.desc)
        put("isoDate", tx.isoDate)
        put("hourOfDay", tx.hourOfDay)
    }

    fun encodeAccountWithBalance(item: AccountWithBalance): JSONObject = JSONObject().apply {
        put("id", item.account.id)
        put("name", item.account.name)
        put("kind", item.account.kind)
        put("colorHex", item.account.colorHex)
        put("initialBalanceCentimes", item.account.initialBalance.centimes)
        put("currentBalanceCentimes", item.currentBalance.centimes)
        put("totalInCentimes", item.totalIn.centimes)
        put("totalOutCentimes", item.totalOut.centimes)
    }

    fun encodeEnvelope(env: EnvelopeConfig, spentCentimes: Long): JSONObject = JSONObject().apply {
        put("id", env.id)
        put("key", env.key)
        put("nameFr", env.nameFr)
        put("nameAr", env.nameAr)
        put("nameDarija", env.nameDarija)
        put("nameEn", env.nameEn)
        put("colorHex", env.colorHex)
        put("spentCentimes", spentCentimes)
    }

    fun encodeLoan(loan: LoanItem): JSONObject = JSONObject().apply {
        put("id", loan.id)
        put("title", loan.title)
        put("counterparty", loan.counterparty)
        put("direction", loan.direction.wire)
        put("category", loan.category.wire)
        put("principalCentimes", loan.principal.centimes)
        put("remainingCentimes", loan.remaining.centimes)
        put("paidCentimes", loan.paidAmount.centimes)
        put("monthlyPaymentCentimes", loan.monthlyPayment.centimes)
        put("status", loan.status.wire)
        put("linkedAccountId", loan.linkedAccountId ?: JSONObject.NULL)
        put("dueIsoDate", loan.dueIsoDate ?: JSONObject.NULL)
        put("notes", loan.notes)
    }

    fun encodeLoanRepayment(rep: LoanRepaymentItem): JSONObject = JSONObject().apply {
        put("id", rep.id)
        put("loanId", rep.loanId)
        put("amountCentimes", rep.amount.centimes)
        put("isoDate", rep.isoDate)
        put("linkedAccountId", rep.linkedAccountId ?: JSONObject.NULL)
        put("linkedTransactionId", rep.linkedTransactionId ?: JSONObject.NULL)
        put("note", rep.note)
    }

    fun encodeRecurringRule(rule: RecurringRuleItem): JSONObject = JSONObject().apply {
        put("id", rule.id)
        put("title", rule.title)
        put("direction", rule.direction.wire)
        put("amountCentimes", rule.amount.centimes)
        put("frequency", rule.frequency.wire)
        put("anchorDayOfMonth", rule.anchorDayOfMonth)
        put("nextDueIsoDate", rule.nextDueIsoDate)
        put("postingMode", rule.postingMode.wire)
        put("accountId", rule.accountId)
        put("envelopeKey", rule.envelopeKey)
        put("category", rule.category)
        put("linkedLoanId", rule.linkedLoanId ?: JSONObject.NULL)
        put("isPaused", rule.isPaused)
    }

    fun encodeRecurringOccurrence(occ: RecurringOccurrence): JSONObject = JSONObject().apply {
        put("ruleId", occ.rule.id)
        put("title", occ.rule.title)
        put("direction", occ.rule.direction.wire)
        put("amountCentimes", occ.rule.amount.centimes)
        put("occurrenceIsoDate", occ.occurrenceIsoDate)
        put("occurrenceKey", occ.occurrenceKey)
        put("isOverdue", occ.isOverdue)
        put("isDueToday", occ.isDueToday)
    }

    fun encodeDashboardPayload(
        profile: UserProfile,
        currencySymbol: String,
        activeWindow: PayCycleWindow,
        cycleExpenses: Money,
        cycleIncomes: Money,
        remainingBudget: Money,
        safeToSpendToday: Money,
        netWorth: NetWorthSnapshot,
        forecast: CashFlowForecastSnapshot,
        insights: FinancialInsightsSnapshot,
        envelopes: List<EnvelopeConfig>,
        envelopeSpentCentimes: Map<String, Long>,
        accounts: List<AccountItem>,
        recentTransactions: List<TransactionItem>
    ): JSONObject = JSONObject().apply {
        put("language", profile.language)
        put("currency", currencySymbol.ifBlank { "DH" })
        put("payCycleDay", profile.payCycleDay)
        put("baseBudgetCentimes", profile.baseBudget.centimes)
        put("cycle", JSONObject().apply {
            put("cycleKey", activeWindow.cycleKey)
            put("startIso", activeWindow.startIso)
            put("endIso", activeWindow.endIso)
            put("totalDays", activeWindow.totalDays)
            put("daysRemaining", activeWindow.daysRemaining)
            put("expensesCentimes", cycleExpenses.centimes)
            put("incomesCentimes", cycleIncomes.centimes)
            put("remainingBudgetCentimes", remainingBudget.centimes)
            put("safeToSpendTodayCentimes", safeToSpendToday.centimes)
        })
        put("netWorth", JSONObject().apply {
            put("totalLiquidAssetsCentimes", netWorth.totalLiquidAssets.centimes)
            put("totalReceivablesCentimes", netWorth.totalReceivables.centimes)
            put("totalLiabilitiesCentimes", netWorth.totalLiabilities.centimes)
            put("netWorthCentimes", netWorth.netWorth.centimes)
            put("activeLoansCount", netWorth.activeLoansCount)
            put("paidOffLoansCount", netWorth.paidOffLoansCount)
        })
        put("forecast", JSONObject().apply {
            put("horizonStartIso", forecast.windowStartIso)
            put("horizonEndIso", forecast.windowEndIso)
            put("startingLiquidBalanceCentimes", forecast.startingLiquidBalance.centimes)
            put("committedUpcomingBillsCentimes", forecast.committedUpcomingBills.centimes)
            put("expectedRecurringIncomeCentimes", forecast.scheduledIncome.centimes)
            put("projectedEndOfPeriodBalanceCentimes", forecast.projectedEndBalance.centimes)
            put("dailySafeToSpendCentimes", forecast.dailySafeToSpend.centimes)
            put("isDeficitWarning", forecast.hasProjectedDeficit)
            put("firstDeficitIsoDate", forecast.projectedDeficitDateIso ?: JSONObject.NULL)
            put("projectedDeficitAmountCentimes", forecast.projectedDeficitAmount.centimes)
        })
        put("insights", JSONObject().apply {
            put("currentCycleExpenseCentimes", insights.currentCycleExpenseTotal.centimes)
            put("previousCycleExpenseCentimes", insights.previousCycleExpenseTotal.centimes)
            put("totalExpenseDeltaCentimes", insights.totalExpenseDelta.centimes)
            put("totalExpensePercentChange", insights.totalExpensePercentChange ?: JSONObject.NULL)
            put("anomaliesCount", insights.anomalies.size)
            put("duplicatesCount", insights.duplicateSuspects.size)
        })
        put("envelopes", JSONArray().apply {
            envelopes.forEach { env ->
                put(encodeEnvelope(env, envelopeSpentCentimes[env.key] ?: 0L))
            }
        })
        put("accounts", JSONArray().apply {
            netWorth.accountBalances.forEach { put(encodeAccountWithBalance(it)) }
            if (netWorth.accountBalances.isEmpty()) {
                accounts.forEach { acc ->
                    put(encodeAccountWithBalance(AccountWithBalance(acc, acc.initialBalance, Money.ZERO, Money.ZERO)))
                }
            }
        })
        put("recentTransactions", JSONArray().apply {
            recentTransactions.take(20).forEach { put(encodeTransaction(it)) }
        })
    }
}
