package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.domain.LoanDirection
import com.example.domain.LoanRepaymentResult
import com.example.domain.LoanStatus
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

    @Query("SELECT COUNT(*) FROM transactions WHERE userId = :userId")
    suspend fun countTransactions(userId: String): Int

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
        nowMs: Long
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
                loanId = loanId
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
        staleLoanRepaymentIdsToDelete: List<String> = emptyList()
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
        staleTxIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteTransaction(userId, id) }
        staleItemIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteFinancialItem(userId, id) }
        staleAccountIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteAccount(userId, id) }
        staleEnvelopeIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteEnvelope(userId, id) }
        staleSavedCreditIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteSavedCredit(userId, id) }
        staleLoanRepaymentIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteLoanRepayment(userId, id) }
        staleLoanIdsToDelete.forEach { id -> if (id.isNotBlank()) deleteLoan(userId, id) }
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
        loanRepayments: List<LoanRepaymentEntity> = emptyList()
    ) {
        deleteAllTransactionsForUser(userId)
        deleteAllAccountsForUser(userId)
        deleteAllEnvelopesForUser(userId)
        deleteAllFinancialItemsForUser(userId)
        deleteAllSavedCreditsForUser(userId)
        deleteAllLoanRepaymentsForUser(userId)
        deleteAllLoansForUser(userId)
        deleteProfileForUser(userId)
        if (profile != null) upsertProfile(profile)
        if (accounts.isNotEmpty()) upsertAccounts(accounts)
        if (transactions.isNotEmpty()) upsertTransactions(transactions)
        if (envelopes.isNotEmpty()) upsertEnvelopes(envelopes)
        if (financialItems.isNotEmpty()) upsertFinancialItems(financialItems)
        if (savedCredits.isNotEmpty()) upsertSavedCredits(savedCredits)
        if (loans.isNotEmpty()) upsertLoans(loans)
        if (loanRepayments.isNotEmpty()) upsertLoanRepayments(loanRepayments)
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
