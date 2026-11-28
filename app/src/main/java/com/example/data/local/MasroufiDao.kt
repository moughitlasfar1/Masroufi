package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.domain.LoanDirection
import com.example.domain.LoanRepaymentResult
import com.example.domain.LoanStatus
import com.example.domain.RecurrenceDirection
import com.example.domain.RecurrenceFrequency
import com.example.domain.RecurrenceScheduleEngine
import com.example.domain.RecurringPostResult
import com.example.data.local.MasroufiEntityMappers.toDomain
import com.masroufi.core.Money
import kotlinx.coroutines.flow.Flow

@Dao
interface MasroufiDao {

    // --- User Profile ---
    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfile(userId: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT COUNT(*) FROM user_profiles WHERE userId = :userId")
    suspend fun countProfiles(userId: String): Int

    // --- Accounts ---
    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY sortOrder ASC, createdAtEpochMs ASC")
    fun observeAccounts(userId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY sortOrder ASC, createdAtEpochMs ASC")
    suspend fun getAccounts(userId: String): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccounts(accounts: List<AccountEntity>)

    @Query("DELETE FROM accounts WHERE userId = :userId AND id = :accountId")
    suspend fun deleteAccount(userId: String, accountId: String)

    @Query("SELECT COUNT(*) FROM accounts WHERE userId = :userId")
    suspend fun countAccounts(userId: String): Int

    // --- Transactions ---
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY isoDate DESC, createdAtEpochMs DESC")
    fun observeTransactions(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND isoDate >= :minIsoDate ORDER BY isoDate DESC, createdAtEpochMs DESC")
    fun observeTransactionsSince(userId: String, minIsoDate: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId")
    suspend fun getTransactions(userId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND id = :txId LIMIT 1")
    suspend fun getTransactionById(userId: String, txId: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(tx: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(txs: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE userId = :userId AND id = :txId")
    suspend fun deleteTransaction(userId: String, txId: String)

    @Query("SELECT * FROM transactions WHERE userId = :userId AND transferGroupId = :transferGroupId")
    suspend fun getTransactionsByTransferGroup(userId: String, transferGroupId: String): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE userId = :userId AND transferGroupId = :transferGroupId")
    suspend fun deleteTransactionsByTransferGroup(userId: String, transferGroupId: String): Int

    @Query("SELECT * FROM transactions WHERE recurringOccurrenceKey = :occurrenceKey LIMIT 1")
    suspend fun getTransactionByOccurrenceKey(occurrenceKey: String): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE userId = :userId")
    suspend fun countTransactions(userId: String): Int

    // --- Phase 7A: Room FTS4 + Multi-Criteria Exact-Centimes Search ---
    @Query(
        """
        SELECT t.* FROM transactions t
        JOIN transactions_fts ON t.rowid = transactions_fts.docid
        WHERE t.userId = :userId
          AND transactions_fts MATCH :ftsQuery
          AND (:startIsoDate IS NULL OR t.isoDate >= :startIsoDate)
          AND (:endIsoDate IS NULL OR t.isoDate <= :endIsoDate)
          AND (:minAmountCentimes IS NULL OR t.amountCentimes >= :minAmountCentimes)
          AND (:maxAmountCentimes IS NULL OR t.amountCentimes <= :maxAmountCentimes)
          AND (:accountId IS NULL OR t.accountId = :accountId OR t.toAccountId = :accountId)
          AND (:envelopeKey IS NULL OR t.envelopeKey = :envelopeKey)
          AND (:txType IS NULL OR (:txType = 'transfer' AND t.type IN ('transfer', 'transfer_out', 'transfer_in')) OR t.type = :txType)
          AND (:loanId IS NULL OR t.loanId = :loanId)
          AND (:loanLinkedOnly = 0 OR (t.loanId IS NOT NULL AND t.loanId != ''))
        ORDER BY t.isoDate DESC, t.createdAtEpochMs DESC
        """
    )
    fun observeSearchTransactionsWithFts(
        userId: String,
        ftsQuery: String,
        startIsoDate: String?,
        endIsoDate: String?,
        minAmountCentimes: Long?,
        maxAmountCentimes: Long?,
        accountId: String?,
        envelopeKey: String?,
        txType: String?,
        loanId: String?,
        loanLinkedOnly: Boolean
    ): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT t.* FROM transactions t
        JOIN transactions_fts ON t.rowid = transactions_fts.docid
        WHERE t.userId = :userId
          AND transactions_fts MATCH :ftsQuery
          AND (:startIsoDate IS NULL OR t.isoDate >= :startIsoDate)
          AND (:endIsoDate IS NULL OR t.isoDate <= :endIsoDate)
          AND (:minAmountCentimes IS NULL OR t.amountCentimes >= :minAmountCentimes)
          AND (:maxAmountCentimes IS NULL OR t.amountCentimes <= :maxAmountCentimes)
          AND (:accountId IS NULL OR t.accountId = :accountId OR t.toAccountId = :accountId)
          AND (:envelopeKey IS NULL OR t.envelopeKey = :envelopeKey)
          AND (:txType IS NULL OR (:txType = 'transfer' AND t.type IN ('transfer', 'transfer_out', 'transfer_in')) OR t.type = :txType)
          AND (:loanId IS NULL OR t.loanId = :loanId)
          AND (:loanLinkedOnly = 0 OR (t.loanId IS NOT NULL AND t.loanId != ''))
        ORDER BY t.isoDate DESC, t.createdAtEpochMs DESC
        """
    )
    suspend fun searchTransactionsWithFts(
        userId: String,
        ftsQuery: String,
        startIsoDate: String?,
        endIsoDate: String?,
        minAmountCentimes: Long?,
        maxAmountCentimes: Long?,
        accountId: String?,
        envelopeKey: String?,
        txType: String?,
        loanId: String?,
        loanLinkedOnly: Boolean
    ): List<TransactionEntity>

    @Query(
        """
        SELECT t.* FROM transactions t
        WHERE t.userId = :userId
          AND (:startIsoDate IS NULL OR t.isoDate >= :startIsoDate)
          AND (:endIsoDate IS NULL OR t.isoDate <= :endIsoDate)
          AND (:minAmountCentimes IS NULL OR t.amountCentimes >= :minAmountCentimes)
          AND (:maxAmountCentimes IS NULL OR t.amountCentimes <= :maxAmountCentimes)
          AND (:accountId IS NULL OR t.accountId = :accountId OR t.toAccountId = :accountId)
          AND (:envelopeKey IS NULL OR t.envelopeKey = :envelopeKey)
          AND (:txType IS NULL OR (:txType = 'transfer' AND t.type IN ('transfer', 'transfer_out', 'transfer_in')) OR t.type = :txType)
          AND (:loanId IS NULL OR t.loanId = :loanId)
          AND (:loanLinkedOnly = 0 OR (t.loanId IS NOT NULL AND t.loanId != ''))
        ORDER BY t.isoDate DESC, t.createdAtEpochMs DESC
        """
    )
    fun observeSearchTransactionsFiltered(
        userId: String,
        startIsoDate: String?,
        endIsoDate: String?,
        minAmountCentimes: Long?,
        maxAmountCentimes: Long?,
        accountId: String?,
        envelopeKey: String?,
        txType: String?,
        loanId: String?,
        loanLinkedOnly: Boolean
    ): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT t.* FROM transactions t
        WHERE t.userId = :userId
          AND (:startIsoDate IS NULL OR t.isoDate >= :startIsoDate)
          AND (:endIsoDate IS NULL OR t.isoDate <= :endIsoDate)
          AND (:minAmountCentimes IS NULL OR t.amountCentimes >= :minAmountCentimes)
          AND (:maxAmountCentimes IS NULL OR t.amountCentimes <= :maxAmountCentimes)
          AND (:accountId IS NULL OR t.accountId = :accountId OR t.toAccountId = :accountId)
          AND (:envelopeKey IS NULL OR t.envelopeKey = :envelopeKey)
          AND (:txType IS NULL OR (:txType = 'transfer' AND t.type IN ('transfer', 'transfer_out', 'transfer_in')) OR t.type = :txType)
          AND (:loanId IS NULL OR t.loanId = :loanId)
          AND (:loanLinkedOnly = 0 OR (t.loanId IS NOT NULL AND t.loanId != ''))
        ORDER BY t.isoDate DESC, t.createdAtEpochMs DESC
        """
    )
    suspend fun searchTransactionsFiltered(
        userId: String,
        startIsoDate: String?,
        endIsoDate: String?,
        minAmountCentimes: Long?,
        maxAmountCentimes: Long?,
        accountId: String?,
        envelopeKey: String?,
        txType: String?,
        loanId: String?,
        loanLinkedOnly: Boolean
    ): List<TransactionEntity>

    // --- Envelopes ---
    @Query("SELECT * FROM envelopes WHERE userId = :userId ORDER BY createdAtEpochMs ASC")
    fun observeEnvelopes(userId: String): Flow<List<EnvelopeEntity>>

    @Query("SELECT * FROM envelopes WHERE userId = :userId")
    suspend fun getEnvelopes(userId: String): List<EnvelopeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEnvelope(envelope: EnvelopeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEnvelopes(envelopes: List<EnvelopeEntity>)

    @Query("DELETE FROM envelopes WHERE userId = :userId AND id = :envelopeId")
    suspend fun deleteEnvelope(userId: String, envelopeId: String)

    @Query("SELECT COUNT(*) FROM envelopes WHERE userId = :userId")
    suspend fun countEnvelopes(userId: String): Int

    // --- Financial Items (Bills, Goals, Debts, Daret, Seasonals, Presets, Custom Prefs) ---
    @Query("SELECT * FROM financial_items WHERE userId = :userId ORDER BY createdAtEpochMs ASC")
    fun observeFinancialItems(userId: String): Flow<List<FinancialItemEntity>>

    @Query("SELECT * FROM financial_items WHERE userId = :userId")
    suspend fun getFinancialItems(userId: String): List<FinancialItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFinancialItem(item: FinancialItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFinancialItems(items: List<FinancialItemEntity>)

    @Query("DELETE FROM financial_items WHERE userId = :userId AND id = :itemId")
    suspend fun deleteFinancialItem(userId: String, itemId: String)

    @Query("SELECT COUNT(*) FROM financial_items WHERE userId = :userId")
    suspend fun countFinancialItems(userId: String): Int

    // --- Saved Credits ---
    @Query("SELECT * FROM saved_credits WHERE userId = :userId ORDER BY createdAtEpochMs DESC")
    fun observeSavedCredits(userId: String): Flow<List<SavedCreditLocalEntity>>

    @Query("SELECT * FROM saved_credits WHERE userId = :userId ORDER BY createdAtEpochMs DESC")
    suspend fun getSavedCredits(userId: String): List<SavedCreditLocalEntity>

    @Query("SELECT * FROM saved_credits WHERE userId = :userId AND id = :creditId LIMIT 1")
    suspend fun getSavedCreditById(userId: String, creditId: String): SavedCreditLocalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedCredit(credit: SavedCreditLocalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedCredits(credits: List<SavedCreditLocalEntity>)

    @Query("DELETE FROM saved_credits WHERE userId = :userId AND id = :creditId")
    suspend fun deleteSavedCredit(userId: String, creditId: String)

    @Query("SELECT COUNT(*) FROM saved_credits WHERE userId = :userId")
    suspend fun countSavedCredits(userId: String): Int

    // --- Phase 5: Loans & Loan Repayments ---
    @Query("SELECT * FROM loans WHERE userId = :userId ORDER BY status ASC, updatedAtEpochMs DESC, createdAtEpochMs DESC")
    fun observeLoans(userId: String): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE userId = :userId ORDER BY status ASC, updatedAtEpochMs DESC, createdAtEpochMs DESC")
    suspend fun getLoans(userId: String): List<LoanEntity>

    @Query("SELECT * FROM loans WHERE userId = :userId AND id = :loanId LIMIT 1")
    suspend fun getLoanById(userId: String, loanId: String): LoanEntity?

    @Query("SELECT * FROM loans WHERE userId = :userId AND sourceSavedCreditId = :savedCreditId LIMIT 1")
    suspend fun getLoanBySavedCreditId(userId: String, savedCreditId: String): LoanEntity?

    @Query("SELECT * FROM loans WHERE userId = :userId AND sourceFinancialItemId = :financialItemId LIMIT 1")
    suspend fun getLoanBySourceFinancialItemId(userId: String, financialItemId: String): LoanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoan(loan: LoanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoans(loans: List<LoanEntity>)

    @Query("DELETE FROM loans WHERE userId = :userId AND id = :loanId")
    suspend fun deleteLoan(userId: String, loanId: String)

    @Query("SELECT * FROM loan_repayments WHERE userId = :userId ORDER BY isoDate DESC, createdAtEpochMs DESC")
    fun observeLoanRepayments(userId: String): Flow<List<LoanRepaymentEntity>>

    @Query("SELECT * FROM loan_repayments WHERE userId = :userId AND loanId = :loanId ORDER BY isoDate DESC, createdAtEpochMs DESC")
    fun observeLoanRepaymentsForLoan(userId: String, loanId: String): Flow<List<LoanRepaymentEntity>>

    @Query("SELECT * FROM loan_repayments WHERE userId = :userId ORDER BY isoDate DESC, createdAtEpochMs DESC")
    suspend fun getLoanRepayments(userId: String): List<LoanRepaymentEntity>

    @Query("SELECT * FROM loan_repayments WHERE userId = :userId AND loanId = :loanId ORDER BY isoDate DESC, createdAtEpochMs DESC")
    suspend fun getLoanRepaymentsForLoan(userId: String, loanId: String): List<LoanRepaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoanRepayment(repayment: LoanRepaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoanRepayments(repayments: List<LoanRepaymentEntity>)

    @Query("DELETE FROM loan_repayments WHERE userId = :userId AND id = :repaymentId")
    suspend fun deleteLoanRepayment(userId: String, repaymentId: String)

    @Query("DELETE FROM loan_repayments WHERE userId = :userId AND loanId = :loanId")
    suspend fun deleteRepaymentsForLoan(userId: String, loanId: String)

    // --- Phase 6: Recurring Rules ---
    @Query("SELECT * FROM recurring_rules WHERE userId = :userId ORDER BY isPaused ASC, nextDueIsoDate ASC, title ASC")
    fun observeRecurringRules(userId: String): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE userId = :userId ORDER BY isPaused ASC, nextDueIsoDate ASC, title ASC")
    suspend fun getRecurringRules(userId: String): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE userId = :userId AND id = :ruleId LIMIT 1")
    suspend fun getRecurringRuleById(userId: String, ruleId: String): RecurringRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurringRule(rule: RecurringRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurringRules(rules: List<RecurringRuleEntity>)

    @Query("DELETE FROM recurring_rules WHERE userId = :userId AND id = :ruleId")
    suspend fun deleteRecurringRule(userId: String, ruleId: String)

    @Query("DELETE FROM recurring_rules WHERE userId = :userId")
    suspend fun deleteAllRecurringRulesForUser(userId: String)

    // --- Import Metadata ---
    @Query("SELECT * FROM import_metadata WHERE userId = :userId LIMIT 1")
    suspend fun getImportMetadata(userId: String): ImportMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportMetadata(metadata: ImportMetadataEntity)

    // --- Atomic User Wipe & Full Snapshot Replace ---
    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun deleteAllTransactionsForUser(userId: String)

    @Query("DELETE FROM accounts WHERE userId = :userId")
    suspend fun deleteAllAccountsForUser(userId: String)

    @Query("DELETE FROM envelopes WHERE userId = :userId")
    suspend fun deleteAllEnvelopesForUser(userId: String)

    @Query("DELETE FROM financial_items WHERE userId = :userId")
    suspend fun deleteAllFinancialItemsForUser(userId: String)

    @Query("DELETE FROM saved_credits WHERE userId = :userId")
    suspend fun deleteAllSavedCreditsForUser(userId: String)

    @Query("DELETE FROM loans WHERE userId = :userId")
    suspend fun deleteAllLoansForUser(userId: String)

    @Query("DELETE FROM loan_repayments WHERE userId = :userId")
    suspend fun deleteAllLoanRepaymentsForUser(userId: String)

    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    suspend fun deleteProfileForUser(userId: String)

    @Transaction
    suspend fun executeInternalTransferAtomically(
        outTx: TransactionEntity,
        inTx: TransactionEntity
    ) {
        upsertTransaction(outTx)
        upsertTransaction(inTx)
    }

    @Transaction
    suspend fun deleteLoanAndRepaymentsAtomically(
        userId: String,
        loanId: String
    ) {
        deleteRepaymentsForLoan(userId, loanId)
        deleteLoan(userId, loanId)
    }

    /**
     * Atomically validates and records a repayment against [loanId] inside a single Room `@Transaction`.
     *
     * Guarantees:
     * - Rejects zero/negative amounts ([LoanRepaymentResult.InvalidAmount]).
     * - Rejects missing loans ([LoanRepaymentResult.LoanNotFound]).
     * - Rejects already paid-off loans ([LoanRepaymentResult.AlreadyPaidOff]).
     * - Blocks overpayment (`amountCentimes > remainingCentimes`) without mutating any table
     *   ([LoanRepaymentResult.OverpaymentBlocked]).
     * - Decrements `remainingCentimes` using exact `Long` centimes and auto-transitions status to
     *   `PAID_OFF` when `newRemaining == 0L`.
     * - Optionally inserts a linked settlement transaction (`transfer_out` for LIABILITY or
     *   `transfer_in` for RECEIVABLE) with `loanId` so Net Worth delta is strictly 0.
     */
    @Transaction
    suspend fun recordLoanRepaymentAtomically(
        userId: String,
        loanId: String,
        amountCentimes: Long,
        isoDate: String,
        linkedAccountId: String?,
        note: String,
        repaymentId: String,
        linkedTxId: String?,
        nowMs: Long,
        recurringOccurrenceKey: String? = null
    ): LoanRepaymentResult {
        if (amountCentimes <= 0L) {
            return LoanRepaymentResult.InvalidAmount
        }
        val currentLoan = getLoanById(userId, loanId)
            ?: return LoanRepaymentResult.LoanNotFound(loanId)

        val currentStatus = LoanStatus.fromWire(currentLoan.status)
        if (currentStatus == LoanStatus.PAID_OFF || currentLoan.remainingCentimes <= 0L) {
            return LoanRepaymentResult.AlreadyPaidOff(loanId)
        }
        if (amountCentimes > currentLoan.remainingCentimes) {
            return LoanRepaymentResult.OverpaymentBlocked(
                loanId = loanId,
                remaining = Money.fromCentimes(currentLoan.remainingCentimes),
                attempted = Money.fromCentimes(amountCentimes)
            )
        }

        val newRemainingCentimes = currentLoan.remainingCentimes - amountCentimes
        val newStatus = if (newRemainingCentimes == 0L) LoanStatus.PAID_OFF else LoanStatus.ACTIVE
        val updatedLoanEntity = currentLoan.copy(
            remainingCentimes = newRemainingCentimes,
            status = newStatus.wire,
            updatedAtEpochMs = nowMs
        )
        upsertLoan(updatedLoanEntity)

        val cleanLinkedAcc = linkedAccountId?.trim()?.takeIf { it.isNotBlank() }?.take(80)
        val cleanOccurrenceKey = recurringOccurrenceKey?.trim()?.takeIf { it.isNotBlank() }?.take(160)
        val createdTxEntity = if (cleanLinkedAcc != null && !linkedTxId.isNullOrBlank()) {
            val direction = LoanDirection.fromWire(currentLoan.direction)
            val txType = if (direction == LoanDirection.RECEIVABLE) "transfer_in" else "transfer_out"
            val txDesc = if (note.isNotBlank()) {
                "${currentLoan.title} — ${note.trim()}"
            } else {
                "Remboursement: ${currentLoan.title}"
            }.take(250)
            TransactionEntity(
                id = linkedTxId,
                userId = userId,
                type = txType,
                amountCentimes = amountCentimes,
                category = "autre",
                envelopeKey = "autre",
                accountId = cleanLinkedAcc,
                toAccountId = "",
                desc = txDesc,
                isoDate = isoDate,
                hourOfDay = -1,
                createdAtEpochMs = nowMs,
                updatedAtEpochMs = nowMs,
                transferGroupId = null,
                loanId = loanId,
                recurringOccurrenceKey = cleanOccurrenceKey
            ).also { upsertTransaction(it) }
        } else {
            null
        }

        val repaymentEntity = LoanRepaymentEntity(
            id = repaymentId,
            loanId = loanId,
            userId = userId,
            amountCentimes = amountCentimes,
            isoDate = isoDate,
            linkedAccountId = cleanLinkedAcc,
            linkedTransactionId = createdTxEntity?.id,
            note = note.trim().take(250),
            createdAtEpochMs = nowMs
        )
        upsertLoanRepayment(repaymentEntity)

        return LoanRepaymentResult.Success(
            updatedLoan = updatedLoanEntity.toDomain(),
            repayment = repaymentEntity.toDomain(),
            linkedTransaction = createdTxEntity?.toDomain()
        )
    }

    /**
     * Atomically and idempotently posts a single occurrence of [ruleId] on [occurrenceIsoDate]
     * inside one Room `@Transaction`.
     *
     * Guarantees:
     * - Deterministic `occurrenceKey = "$ruleId:$occurrenceIsoDate"`.
     * - If a transaction already exists with `recurringOccurrenceKey == occurrenceKey` or the rule's
     *   `lastPostedOccurrenceKey == occurrenceKey`, returns [RecurringPostResult.AlreadyPosted] with ZERO mutations.
     * - If linked to a [LoanEntity], delegates to [recordLoanRepaymentAtomically] (inheriting full
     *   overpayment protection, `PAID_OFF` status transition, and Net Worth Δ = 0), and auto-pauses
     *   the rule once the loan reaches `PAID_OFF`.
     * - Advances `nextDueIsoDate` deterministically using [RecurrenceScheduleEngine.advanceDate]
     *   with month-end clamping (`31 -> Feb 28/29 -> Mar 31`).
     */
    @Transaction
    suspend fun postRecurringOccurrenceAtomically(
        userId: String,
        ruleId: String,
        occurrenceIsoDate: String,
        txId: String,
        repaymentId: String,
        nowMs: Long,
        amountOverrideCentimes: Long? = null
    ): RecurringPostResult {
        val rule = getRecurringRuleById(userId, ruleId)
            ?: return RecurringPostResult.RuleNotFound(ruleId)

        val baseAmount = amountOverrideCentimes ?: rule.amountCentimes
        if (baseAmount <= 0L) {
            return RecurringPostResult.InvalidRule
        }

        val occurrenceKey = RecurrenceScheduleEngine.occurrenceKey(rule.id, occurrenceIsoDate)
        if (rule.lastPostedOccurrenceKey == occurrenceKey || getTransactionByOccurrenceKey(occurrenceKey) != null) {
            return RecurringPostResult.AlreadyPosted(occurrenceKey)
        }

        val frequency = RecurrenceFrequency.fromWire(rule.frequency)
        val anchorDay = rule.anchorDayOfMonth.coerceIn(1, 31)
        val nextDateCandidate = if (occurrenceIsoDate >= rule.nextDueIsoDate) {
            RecurrenceScheduleEngine.advanceDate(occurrenceIsoDate, frequency, anchorDay)
        } else {
            rule.nextDueIsoDate
        }
        val passedEnd = !rule.endIsoDate.isNullOrBlank() && nextDateCandidate > rule.endIsoDate

        val direction = RecurrenceDirection.fromWire(rule.direction)
        val linkedLoanId = rule.linkedLoanId?.trim()?.takeIf { it.isNotBlank() }

        if (direction == RecurrenceDirection.LOAN_REPAYMENT || linkedLoanId != null) {
            val targetLoanId = linkedLoanId ?: return RecurringPostResult.InvalidRule
            val currentLoan = getLoanById(userId, targetLoanId)
                ?: return RecurringPostResult.InvalidRule
            if (LoanStatus.fromWire(currentLoan.status) == LoanStatus.PAID_OFF || currentLoan.remainingCentimes <= 0L) {
                if (!rule.isPaused) {
                    upsertRecurringRule(rule.copy(isPaused = true, updatedAtEpochMs = nowMs))
                }
                return RecurringPostResult.LoanAlreadyPaidOff(targetLoanId)
            }
            val effectiveAmount = if (amountOverrideCentimes != null) {
                amountOverrideCentimes
            } else {
                minOf(rule.amountCentimes, currentLoan.remainingCentimes)
            }
            val loanAccount = rule.accountId.trim().ifBlank { currentLoan.linkedAccountId ?: "acc_cash" }
            val repayRes = recordLoanRepaymentAtomically(
                userId = userId,
                loanId = targetLoanId,
                amountCentimes = effectiveAmount,
                isoDate = occurrenceIsoDate,
                linkedAccountId = loanAccount,
                note = rule.title,
                repaymentId = repaymentId,
                linkedTxId = txId,
                nowMs = nowMs,
                recurringOccurrenceKey = occurrenceKey
            )
            return when (repayRes) {
                is LoanRepaymentResult.Success -> {
                    val loanNowPaidOff = repayRes.updatedLoan.isPaidOff
                    val updatedRule = rule.copy(
                        nextDueIsoDate = nextDateCandidate,
                        lastPostedOccurrenceKey = occurrenceKey,
                        isPaused = rule.isPaused || passedEnd || loanNowPaidOff,
                        updatedAtEpochMs = nowMs
                    )
                    upsertRecurringRule(updatedRule)
                    RecurringPostResult.Success(
                        occurrenceKey = occurrenceKey,
                        postedTransaction = repayRes.linkedTransaction,
                        updatedRule = updatedRule.toDomain(),
                        loanRepaymentResult = repayRes
                    )
                }
                is LoanRepaymentResult.AlreadyPaidOff -> {
                    if (!rule.isPaused) {
                        upsertRecurringRule(rule.copy(isPaused = true, updatedAtEpochMs = nowMs))
                    }
                    RecurringPostResult.LoanAlreadyPaidOff(repayRes.loanId)
                }
                is LoanRepaymentResult.OverpaymentBlocked -> {
                    RecurringPostResult.OverpaymentBlocked(
                        loanId = repayRes.loanId,
                        remaining = repayRes.remaining,
                        attempted = repayRes.attempted
                    )
                }
                is LoanRepaymentResult.InvalidAmount -> RecurringPostResult.InvalidRule
                is LoanRepaymentResult.LoanNotFound -> RecurringPostResult.InvalidRule
            }
        }

        val txType = if (direction == RecurrenceDirection.INCOME) "income" else "expense"
        val txEntity = TransactionEntity(
            id = txId,
            userId = userId,
            type = txType,
            amountCentimes = baseAmount,
            category = rule.category.ifBlank { rule.envelopeKey.ifBlank { "charges" } },
            envelopeKey = rule.envelopeKey.ifBlank { "charges" },
            accountId = rule.accountId.ifBlank { "acc_cash" },
            toAccountId = "",
            desc = rule.title.take(250),
            isoDate = occurrenceIsoDate,
            hourOfDay = -1,
            createdAtEpochMs = nowMs,
            updatedAtEpochMs = nowMs,
            transferGroupId = null,
            loanId = null,
            recurringOccurrenceKey = occurrenceKey
        )
        upsertTransaction(txEntity)

        val updatedRule = rule.copy(
            nextDueIsoDate = nextDateCandidate,
            lastPostedOccurrenceKey = occurrenceKey,
            isPaused = rule.isPaused || passedEnd,
            updatedAtEpochMs = nowMs
        )
        upsertRecurringRule(updatedRule)

        return RecurringPostResult.Success(
            occurrenceKey = occurrenceKey,
            postedTransaction = txEntity.toDomain(),
            updatedRule = updatedRule.toDomain(),
            loanRepaymentResult = null
        )
    }

    @Transaction
    suspend fun skipRecurringOccurrenceAtomically(
        userId: String,
        ruleId: String,
        occurrenceIsoDate: String,
        nowMs: Long
    ): Boolean {
        val rule = getRecurringRuleById(userId, ruleId) ?: return false
        val key = RecurrenceScheduleEngine.occurrenceKey(rule.id, occurrenceIsoDate)
        val existingSkipped = rule.skippedOccurrenceKeys.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
        existingSkipped.add(key)
        val frequency = RecurrenceFrequency.fromWire(rule.frequency)
        val nextDateCandidate = if (occurrenceIsoDate >= rule.nextDueIsoDate) {
            RecurrenceScheduleEngine.advanceDate(occurrenceIsoDate, frequency, rule.anchorDayOfMonth.coerceIn(1, 31))
        } else {
            rule.nextDueIsoDate
        }
        val passedEnd = !rule.endIsoDate.isNullOrBlank() && nextDateCandidate > rule.endIsoDate
        upsertRecurringRule(
            rule.copy(
                nextDueIsoDate = nextDateCandidate,
                skippedOccurrenceKeys = existingSkipped.toList().takeLast(64).joinToString(","),
                isPaused = rule.isPaused || passedEnd,
                updatedAtEpochMs = nowMs
            )
        )
        return true
    }

    @Transaction
    suspend fun upsertItemAndTransactionAtomically(
        item: FinancialItemEntity,
        tx: TransactionEntity
    ) {
        upsertFinancialItem(item)
        upsertTransaction(tx)
    }

    @Transaction
    suspend fun resetUserToCleanAtomically(
        userId: String,
        txIdsToDelete: List<String>,
        itemIdsToDelete: List<String>,
        accountIdsToDelete: List<String>,
        cleanAccounts: List<AccountEntity>
    ) {
        txIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteTransaction(userId, id) }
        itemIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteFinancialItem(userId, id) }
        accountIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteAccount(userId, id) }
        deleteAllLoanRepaymentsForUser(userId)
        deleteAllLoansForUser(userId)
        deleteAllRecurringRulesForUser(userId)
        if (cleanAccounts.isNotEmpty()) upsertAccounts(cleanAccounts)
    }

    @Transaction
    suspend fun restoreBackupSnapshotAtomically(
        userId: String,
        profile: UserProfileEntity?,
        customPrefsItem: FinancialItemEntity?,
        accounts: List<AccountEntity>,
        envelopes: List<EnvelopeEntity>,
        financialItems: List<FinancialItemEntity>,
        transactions: List<TransactionEntity>,
        staleTxIdsToDelete: List<String>,
        staleItemIdsToDelete: List<String>,
        staleAccountIdsToDelete: List<String>,
        staleEnvelopeIdsToDelete: List<String>,
        savedCredits: List<SavedCreditLocalEntity> = emptyList(),
        staleSavedCreditIdsToDelete: List<String> = emptyList(),
        loans: List<LoanEntity> = emptyList(),
        loanRepayments: List<LoanRepaymentEntity> = emptyList(),
        staleLoanIdsToDelete: List<String> = emptyList(),
        staleLoanRepaymentIdsToDelete: List<String> = emptyList(),
        recurringRules: List<RecurringRuleEntity> = emptyList(),
        staleRecurringRuleIdsToDelete: List<String> = emptyList()
    ) {
        if (profile != null) upsertProfile(profile)
        if (customPrefsItem != null) upsertFinancialItem(customPrefsItem)
        if (accounts.isNotEmpty()) upsertAccounts(accounts)
        if (envelopes.isNotEmpty()) upsertEnvelopes(envelopes)
        if (financialItems.isNotEmpty()) upsertFinancialItems(financialItems)
        if (transactions.isNotEmpty()) upsertTransactions(transactions)
        if (savedCredits.isNotEmpty()) upsertSavedCredits(savedCredits)
        if (loans.isNotEmpty()) upsertLoans(loans)
        if (loanRepayments.isNotEmpty()) upsertLoanRepayments(loanRepayments)
        if (recurringRules.isNotEmpty()) upsertRecurringRules(recurringRules)
        staleTxIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteTransaction(userId, id) }
        staleItemIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteFinancialItem(userId, id) }
        staleAccountIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteAccount(userId, id) }
        staleEnvelopeIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteEnvelope(userId, id) }
        staleSavedCreditIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteSavedCredit(userId, id) }
        staleLoanRepaymentIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteLoanRepayment(userId, id) }
        staleLoanIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteLoan(userId, id) }
        staleRecurringRuleIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteRecurringRule(userId, id) }
    }

    @Transaction
    suspend fun replaceAllUserData(
        userId: String,
        profile: UserProfileEntity?,
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        envelopes: List<EnvelopeEntity>,
        financialItems: List<FinancialItemEntity>,
        savedCredits: List<SavedCreditLocalEntity>,
        loans: List<LoanEntity> = emptyList(),
        loanRepayments: List<LoanRepaymentEntity> = emptyList(),
        recurringRules: List<RecurringRuleEntity> = emptyList()
    ) {
        deleteAllTransactionsForUser(userId)
        deleteAllAccountsForUser(userId)
        deleteAllEnvelopesForUser(userId)
        deleteAllFinancialItemsForUser(userId)
        deleteAllSavedCreditsForUser(userId)
        deleteAllLoanRepaymentsForUser(userId)
        deleteAllLoansForUser(userId)
        deleteAllRecurringRulesForUser(userId)
        deleteProfileForUser(userId)
        if (profile != null) upsertProfile(profile)
        if (accounts.isNotEmpty()) upsertAccounts(accounts)
        if (transactions.isNotEmpty()) upsertTransactions(transactions)
        if (envelopes.isNotEmpty()) upsertEnvelopes(envelopes)
        if (financialItems.isNotEmpty()) upsertFinancialItems(financialItems)
        if (savedCredits.isNotEmpty()) upsertSavedCredits(savedCredits)
        if (loans.isNotEmpty()) upsertLoans(loans)
        if (loanRepayments.isNotEmpty()) upsertLoanRepayments(loanRepayments)
        if (recurringRules.isNotEmpty()) upsertRecurringRules(recurringRules)
    }

    // --- Ownership lookups (prevent cross-user PK collision during seeding) ---
    @Query("SELECT userId FROM accounts WHERE id = :accountId LIMIT 1")
    suspend fun getAccountOwner(accountId: String): String?

    @Query("SELECT userId FROM envelopes WHERE id = :envelopeId LIMIT 1")
    suspend fun getEnvelopeOwner(envelopeId: String): String?

    @Query("SELECT userId FROM financial_items WHERE id = :itemId LIMIT 1")
    suspend fun getFinancialItemOwner(itemId: String): String?

    // --- Atomic Local Mode -> Signed-In User Adoption ---
    @Query("UPDATE accounts SET userId = :targetUserId WHERE userId = :sourceUserId")
    suspend fun reassignAccountsOwner(sourceUserId: String, targetUserId: String): Int

    @Query("UPDATE envelopes SET userId = :targetUserId WHERE userId = :sourceUserId")
    suspend fun reassignEnvelopesOwner(sourceUserId: String, targetUserId: String): Int

    @Query("UPDATE transactions SET userId = :targetUserId WHERE userId = :sourceUserId")
    suspend fun reassignTransactionsOwner(sourceUserId: String, targetUserId: String): Int

    @Query("UPDATE financial_items SET userId = :targetUserId WHERE userId = :sourceUserId")
    suspend fun reassignFinancialItemsOwner(sourceUserId: String, targetUserId: String): Int

    @Query("UPDATE saved_credits SET userId = :targetUserId WHERE userId = :sourceUserId")
    suspend fun reassignSavedCreditsOwner(sourceUserId: String, targetUserId: String): Int

    @Query("UPDATE loans SET userId = :targetUserId WHERE userId = :sourceUserId")
    suspend fun reassignLoansOwner(sourceUserId: String, targetUserId: String): Int

    @Query("UPDATE loan_repayments SET userId = :targetUserId WHERE userId = :sourceUserId")
    suspend fun reassignLoanRepaymentsOwner(sourceUserId: String, targetUserId: String): Int

    @Query("UPDATE recurring_rules SET userId = :targetUserId WHERE userId = :sourceUserId")
    suspend fun reassignRecurringRulesOwner(sourceUserId: String, targetUserId: String): Int

    @Transaction
    suspend fun adoptUserDataAtomically(sourceUserId: String, targetUserId: String): Int {
        if (sourceUserId.isBlank() || targetUserId.isBlank() || sourceUserId == targetUserId) {
            return 0
        }
        val sourceProfile = getProfile(sourceUserId)
        val targetProfile = getProfile(targetUserId)

        var adoptedCount = 0
        adoptedCount += reassignAccountsOwner(sourceUserId, targetUserId)
        adoptedCount += reassignEnvelopesOwner(sourceUserId, targetUserId)
        adoptedCount += reassignTransactionsOwner(sourceUserId, targetUserId)
        adoptedCount += reassignFinancialItemsOwner(sourceUserId, targetUserId)
        adoptedCount += reassignSavedCreditsOwner(sourceUserId, targetUserId)
        adoptedCount += reassignLoansOwner(sourceUserId, targetUserId)
        adoptedCount += reassignLoanRepaymentsOwner(sourceUserId, targetUserId)
        adoptedCount += reassignRecurringRulesOwner(sourceUserId, targetUserId)

        if (sourceProfile != null) {
            if (targetProfile == null) {
                upsertProfile(sourceProfile.copy(userId = targetUserId))
                adoptedCount += 1
            }
            deleteProfileForUser(sourceUserId)
        }
        return adoptedCount
    }
}
