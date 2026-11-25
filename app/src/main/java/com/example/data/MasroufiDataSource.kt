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
        userId: String = requireUserId()
    )

    suspend fun adoptLocalDataIntoUser(
        sourceUserId: String = MasroufiRepository.LOCAL_DEVICE_USER_ID,
        targetUserId: String = requireUserId()
    ): Int = 0
}
