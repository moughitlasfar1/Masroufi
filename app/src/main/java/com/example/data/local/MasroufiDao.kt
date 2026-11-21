package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(tx: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(txs: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE userId = :userId AND id = :txId")
    suspend fun deleteTransaction(userId: String, txId: String)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedCredit(credit: SavedCreditLocalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedCredits(credits: List<SavedCreditLocalEntity>)

    @Query("DELETE FROM saved_credits WHERE userId = :userId AND id = :creditId")
    suspend fun deleteSavedCredit(userId: String, creditId: String)

    @Query("SELECT COUNT(*) FROM saved_credits WHERE userId = :userId")
    suspend fun countSavedCredits(userId: String): Int

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

    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    suspend fun deleteProfileForUser(userId: String)

    @Transaction
    suspend fun replaceAllUserData(
        userId: String,
        profile: UserProfileEntity?,
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        envelopes: List<EnvelopeEntity>,
        financialItems: List<FinancialItemEntity>,
        savedCredits: List<SavedCreditLocalEntity>
    ) {
        deleteAllTransactionsForUser(userId)
        deleteAllAccountsForUser(userId)
        deleteAllEnvelopesForUser(userId)
        deleteAllFinancialItemsForUser(userId)
        deleteAllSavedCreditsForUser(userId)
        deleteProfileForUser(userId)
        if (profile != null) upsertProfile(profile)
        if (accounts.isNotEmpty()) upsertAccounts(accounts)
        if (transactions.isNotEmpty()) upsertTransactions(transactions)
        if (envelopes.isNotEmpty()) upsertEnvelopes(envelopes)
        if (financialItems.isNotEmpty()) upsertFinancialItems(financialItems)
        if (savedCredits.isNotEmpty()) upsertSavedCredits(savedCredits)
    }
}
