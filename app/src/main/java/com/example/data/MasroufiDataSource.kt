package com.example.data

import kotlinx.coroutines.flow.Flow

/**
 * Storage-agnostic repository contract for Masroufi financial data.
 *
 * Implemented by:
 * - [com.example.data.local.RoomMasroufiRepository] (primary local-first encrypted Room source of truth)
 * - [LocalRoomMasroufiDataSource] (thin single-repo Room facade bound to [com.example.data.local.MasroufiDatabaseProvider])
 * - [MasroufiRepository] (isolated legacy Firestore utility & emulator rules test target)
 */
interface MasroufiDataSource {

    fun requireUserId(explicitUserId: String? = null): String

    fun observeProfile(userId: String = requireUserId()): Flow<UserProfile?>

    fun observeTransactions(
        userId: String = requireUserId(),
        minIsoDate: String? = null
    ): Flow<List<TransactionItem>>

    fun observeAccounts(userId: String = requireUserId()): Flow<List<AccountItem>>

    fun observeEnvelopes(userId: String = requireUserId()): Flow<List<EnvelopeConfig>>

    fun observeFinancialItems(userId: String = requireUserId()): Flow<List<FinancialItem>>

    suspend fun saveProfile(profile: UserProfile, userId: String = requireUserId())

    suspend fun upsertTransaction(tx: TransactionItem, userId: String = requireUserId()): String

    suspend fun getTransactionById(targetUserId: String, txId: String): TransactionItem?

    suspend fun deleteTransaction(txId: String, userId: String = requireUserId())

    suspend fun upsertAccount(account: AccountItem, userId: String = requireUserId()): String

    suspend fun deleteAccount(accId: String, userId: String = requireUserId())

    suspend fun upsertEnvelope(envelope: EnvelopeConfig, userId: String = requireUserId()): String

    suspend fun upsertEnvelopesAtomically(envelopes: List<EnvelopeConfig>, userId: String = requireUserId())

    suspend fun deleteEnvelope(envId: String, userId: String = requireUserId())

    suspend fun upsertFinancialItem(item: FinancialItem, userId: String = requireUserId()): String

    suspend fun upsertItemAndTransactionAtomically(
        item: FinancialItem,
        tx: TransactionItem,
        userId: String = requireUserId()
    )

    suspend fun deleteFinancialItem(itemId: String, userId: String = requireUserId())

    suspend fun ensureSeeded(userId: String = requireUserId())

    suspend fun loadDemoData(
        existingAccounts: List<AccountItem> = emptyList(),
        existingEnvelopes: List<EnvelopeConfig> = emptyList(),
        existingItems: List<FinancialItem> = emptyList(),
        existingTransactions: List<TransactionItem> = emptyList(),
        userId: String = requireUserId()
    )

    suspend fun resetToCleanAccount(
        existingTxIds: List<String>,
        existingItemIdsToClear: List<String>,
        existingAccounts: List<AccountItem>,
        userId: String = requireUserId()
    )

    suspend fun getSavedCredits(userId: String = requireUserId()): List<com.masroufi.credit.data.SavedCreditEntity> = emptyList()

    // --- Phase 5: Accounts Atomic Transfers & Loans ---
    fun observeLoans(userId: String = requireUserId()): Flow<List<com.example.domain.LoanItem>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getLoans(userId: String = requireUserId()): List<com.example.domain.LoanItem> = emptyList()

    fun observeLoanRepayments(userId: String = requireUserId()): Flow<List<com.example.domain.LoanRepaymentItem>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getLoanRepayments(userId: String = requireUserId()): List<com.example.domain.LoanRepaymentItem> = emptyList()

    suspend fun upsertLoan(
        loan: com.example.domain.LoanItem,
        userId: String = requireUserId()
    ): String = loan.id

    suspend fun deleteLoan(loanId: String, userId: String = requireUserId()) {}

    suspend fun executeInternalTransfer(
        fromAccountId: String,
        toAccountId: String,
        amount: com.masroufi.core.Money,
        isoDate: String,
        desc: String = "",
        userId: String = requireUserId()
    ): com.example.domain.InternalTransferResult = com.example.domain.InternalTransferResult.InvalidAccounts

    suspend fun deleteTransferGroup(transferGroupId: String, userId: String = requireUserId()): Int = 0

    suspend fun recordLoanRepayment(
        loanId: String,
        amount: com.masroufi.core.Money,
        linkedAccountId: String? = null,
        isoDate: String = com.example.domain.PayCycleEngine.todayIso(),
        note: String = "",
        userId: String = requireUserId()
    ): com.example.domain.LoanRepaymentResult = com.example.domain.LoanRepaymentResult.LoanNotFound(loanId)

    suspend fun promoteSavedCreditToLoan(
        savedCreditId: String,
        linkedAccountId: String? = null,
        titleOverride: String? = null,
        userId: String = requireUserId()
    ): Result<com.example.domain.LoanItem> = Result.failure(UnsupportedOperationException("Not supported"))

    // --- Phase 6: Recurrence Engine ---
    fun observeRecurringRules(userId: String = requireUserId()): Flow<List<com.example.domain.RecurringRuleItem>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getRecurringRules(userId: String = requireUserId()): List<com.example.domain.RecurringRuleItem> = emptyList()

    suspend fun upsertRecurringRule(
        rule: com.example.domain.RecurringRuleItem,
        userId: String = requireUserId()
    ): String = rule.id

    suspend fun deleteRecurringRule(ruleId: String, userId: String = requireUserId()) {}

    suspend fun postRecurringOccurrence(
        ruleId: String,
        occurrenceIsoDate: String,
        amountOverride: com.masroufi.core.Money? = null,
        userId: String = requireUserId()
    ): com.example.domain.RecurringPostResult = com.example.domain.RecurringPostResult.RuleNotFound(ruleId)

    suspend fun skipRecurringOccurrence(
        ruleId: String,
        occurrenceIsoDate: String,
        userId: String = requireUserId()
    ): Boolean = false

    suspend fun processDueAutoPostRules(
        todayIso: String = com.example.domain.PayCycleEngine.todayIso(),
        userId: String = requireUserId()
    ): Int = 0

    // --- Phase 7A: Room FTS4 + Multi-Criteria Search ---
    fun observeSearchTransactions(
        criteria: com.example.domain.TransactionSearchCriteria,
        userId: String = requireUserId()
    ): Flow<List<TransactionItem>> = observeTransactions(userId)

    suspend fun searchTransactions(
        criteria: com.example.domain.TransactionSearchCriteria,
        userId: String = requireUserId()
    ): List<TransactionItem> = emptyList()

    suspend fun restoreFullBackupAtomically(
        profile: UserProfile?,
        customPrefsItem: FinancialItem?,
        accounts: List<AccountItem>,
        envelopes: List<EnvelopeConfig>,
        financialItems: List<FinancialItem>,
        transactions: List<TransactionItem>,
        staleTxIdsToDelete: List<String> = emptyList(),
        staleItemIdsToDelete: List<String> = emptyList(),
        staleAccountIdsToDelete: List<String> = emptyList(),
        staleEnvelopeIdsToDelete: List<String> = emptyList(),
        onChunkProgress: (completedChunks: Int, totalChunks: Int) -> Unit = { _, _ -> },
        userId: String = requireUserId(),
        savedCredits: List<com.masroufi.credit.data.SavedCreditEntity> = emptyList(),
        staleSavedCreditIdsToDelete: List<String> = emptyList(),
        loans: List<com.example.domain.LoanItem> = emptyList(),
        loanRepayments: List<com.example.domain.LoanRepaymentItem> = emptyList(),
        staleLoanIdsToDelete: List<String> = emptyList(),
        staleLoanRepaymentIdsToDelete: List<String> = emptyList(),
        recurringRules: List<com.example.domain.RecurringRuleItem> = emptyList(),
        staleRecurringRuleIdsToDelete: List<String> = emptyList()
    )

    suspend fun adoptLocalDataIntoUser(
        sourceUserId: String = MasroufiRepository.LOCAL_DEVICE_USER_ID,
        targetUserId: String = requireUserId()
    ): Int = 0
}
