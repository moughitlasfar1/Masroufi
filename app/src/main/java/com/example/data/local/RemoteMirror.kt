package com.example.data.local

import com.example.data.AccountItem
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.masroufi.credit.data.SavedCreditEntity

/**
 * Post-commit hook invoked strictly AFTER a local Room write commits.
 *
 * Phase 1 wires [NoOpRemoteMirror] by default so all daily operations remain 100% local-first
 * with zero Firestore or network calls. Optional future E2EE sync can plug a concrete
 * implementation in here without changing ViewModels, UI, or Room persistence semantics.
 *
 * Safety contract: Any exception thrown by a [RemoteMirror] implementation is caught and logged
 * by the repository and MUST NEVER roll back or fail the committed local Room transaction.
 */
interface RemoteMirror {
    suspend fun onProfileUpserted(userId: String, profile: UserProfile) {}
    suspend fun onTransactionUpserted(userId: String, transaction: TransactionItem) {}
    suspend fun onTransactionDeleted(userId: String, transactionId: String) {}
    suspend fun onAccountUpserted(userId: String, account: AccountItem) {}
    suspend fun onAccountDeleted(userId: String, accountId: String) {}
    suspend fun onEnvelopeUpserted(userId: String, envelope: EnvelopeConfig) {}
    suspend fun onEnvelopesUpserted(userId: String, envelopes: List<EnvelopeConfig>) {}
    suspend fun onEnvelopeDeleted(userId: String, envelopeId: String) {}
    suspend fun onFinancialItemUpserted(userId: String, item: FinancialItem) {}
    suspend fun onItemAndTransactionUpserted(
        userId: String,
        item: FinancialItem,
        transaction: TransactionItem
    ) {}
    suspend fun onFinancialItemDeleted(userId: String, itemId: String) {}
    suspend fun onSavedCreditUpserted(userId: String, credit: SavedCreditEntity) {}
    suspend fun onSavedCreditDeleted(userId: String, creditId: String) {}
    suspend fun onUserSeeded(userId: String) {}
    suspend fun onDemoDataLoaded(userId: String) {}
    suspend fun onAccountReset(
        userId: String,
        deletedTxIds: List<String>,
        deletedItemIds: List<String>,
        deletedAccountIds: List<String>
    ) {}
    suspend fun onBackupRestored(
        userId: String,
        restoredTxCount: Int,
        restoredItemCount: Int,
        restoredAccountCount: Int,
        restoredEnvelopeCount: Int
    ) {}
    suspend fun onBackupRestored(
        userId: String,
        restoredTxCount: Int,
        restoredItemCount: Int,
        restoredAccountCount: Int,
        restoredEnvelopeCount: Int,
        restoredCreditCount: Int
    ) {
        onBackupRestored(
            userId = userId,
            restoredTxCount = restoredTxCount,
            restoredItemCount = restoredItemCount,
            restoredAccountCount = restoredAccountCount,
            restoredEnvelopeCount = restoredEnvelopeCount
        )
    }
    suspend fun onLocalDataAdopted(
        sourceUserId: String,
        targetUserId: String,
        adoptedRowCount: Int
    ) {}
}

/**
 * Default local-only [RemoteMirror] performing zero network or cloud operations.
 */
object NoOpRemoteMirror : RemoteMirror
