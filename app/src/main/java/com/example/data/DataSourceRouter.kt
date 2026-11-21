package com.example.data

import android.content.Context
import com.example.data.local.DatabaseHealthState
import com.example.data.local.MasroufiDatabaseProvider
import com.example.data.local.RoomMasroufiRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

enum class ActiveDataSourceMode {
    FIRESTORE_LEGACY,
    ROOM_PRIMARY
}

/**
 * Persists per-user Firestore -> Room import completion flags and the Firestore fallback
 * kill switch, publishing reactive state so [SwitchingMasroufiRepository] and UI components
 * can flip data sources safely without recreating ViewModels.
 */
object DataSourceConfigStore {
    private const val PREFS_NAME = "masroufi_datasource_prefs"
    private const val KEY_FORCE_FIRESTORE_KILL_SWITCH = "force_firestore_fallback_kill_switch"
    private const val KEY_IMPORT_COMPLETED_PREFIX = "room_import_completed_v1_"
    private const val KEY_ANY_IMPORT_COMPLETED = "any_user_room_import_completed_v1"

    private val _activeMode = MutableStateFlow(ActiveDataSourceMode.FIRESTORE_LEGACY)
    val activeMode: StateFlow<ActiveDataSourceMode> = _activeMode.asStateFlow()

    private val _killSwitchEnabled = MutableStateFlow(false)
    val killSwitchEnabled: StateFlow<Boolean> = _killSwitchEnabled.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isRoomImportCompleted(context: Context, userId: String): Boolean {
        if (userId.isBlank()) return false
        return prefs(context).getBoolean("$KEY_IMPORT_COMPLETED_PREFIX$userId", false)
    }

    fun isAnyUserRoomImportCompleted(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ANY_IMPORT_COMPLETED, false)
    }

    fun markRoomImportCompleted(context: Context, userId: String) {
        if (userId.isBlank()) return
        prefs(context).edit()
            .putBoolean("$KEY_IMPORT_COMPLETED_PREFIX$userId", true)
            .putBoolean(KEY_ANY_IMPORT_COMPLETED, true)
            .apply()
        refreshActiveMode(context, userId)
    }

    fun clearRoomImportCompletedForUser(context: Context, userId: String) {
        if (userId.isBlank()) return
        prefs(context).edit()
            .putBoolean("$KEY_IMPORT_COMPLETED_PREFIX$userId", false)
            .apply()
        refreshActiveMode(context, userId)
    }

    fun isFirestoreKillSwitchEnabled(context: Context): Boolean {
        val enabled = prefs(context).getBoolean(KEY_FORCE_FIRESTORE_KILL_SWITCH, false)
        _killSwitchEnabled.value = enabled
        return enabled
    }

    /**
     * Kill switch: immediately forces Masroufi to fall back to the legacy Firestore repository
     * (e.g., if an import fails or troubleshooting is needed).
     */
    fun setFirestoreKillSwitchEnabled(context: Context, enabled: Boolean, currentUserId: String? = null) {
        prefs(context).edit()
            .putBoolean(KEY_FORCE_FIRESTORE_KILL_SWITCH, enabled)
            .apply()
        _killSwitchEnabled.value = enabled
        if (currentUserId != null) {
            refreshActiveMode(context, currentUserId)
        } else if (enabled) {
            _activeMode.value = ActiveDataSourceMode.FIRESTORE_LEGACY
        }
    }

    fun resolveEffectiveMode(
        context: Context,
        userId: String,
        isRoomHealthy: Boolean = MasroufiDatabaseProvider.healthState.value is DatabaseHealthState.Healthy
    ): ActiveDataSourceMode {
        val killSwitch = isFirestoreKillSwitchEnabled(context)
        if (killSwitch || !isRoomHealthy) {
            return ActiveDataSourceMode.FIRESTORE_LEGACY
        }
        return if (isRoomImportCompleted(context, userId)) {
            ActiveDataSourceMode.ROOM_PRIMARY
        } else {
            ActiveDataSourceMode.FIRESTORE_LEGACY
        }
    }

    fun refreshActiveMode(
        context: Context,
        userId: String,
        isRoomHealthy: Boolean = MasroufiDatabaseProvider.healthState.value is DatabaseHealthState.Healthy
    ): ActiveDataSourceMode {
        val mode = resolveEffectiveMode(context, userId, isRoomHealthy)
        _activeMode.value = mode
        return mode
    }

    internal fun resetForTesting(context: Context) {
        prefs(context).edit().clear().commit()
        _killSwitchEnabled.value = false
        _activeMode.value = ActiveDataSourceMode.FIRESTORE_LEGACY
    }
}

/**
 * Runtime router implementing [MasroufiDataSource].
 *
 * ViewModels observe a single [SwitchingMasroufiRepository] instance and never need to be
 * recreated when the verified Firestore -> Room import completes or when the Firestore
 * fallback kill switch is toggled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwitchingMasroufiRepository(
    private val firestoreFallback: MasroufiDataSource,
    private val roomRepositoryProvider: () -> RoomMasroufiRepository?,
    private val activeModeFlow: StateFlow<ActiveDataSourceMode> = DataSourceConfigStore.activeMode
) : MasroufiDataSource {

    private fun currentBackend(mode: ActiveDataSourceMode = activeModeFlow.value): MasroufiDataSource {
        if (mode == ActiveDataSourceMode.ROOM_PRIMARY) {
            roomRepositoryProvider()?.let { return it }
        }
        return firestoreFallback
    }

    override fun requireUserId(explicitUserId: String?): String =
        currentBackend().requireUserId(explicitUserId)

    override fun observeProfile(userId: String): Flow<UserProfile?> =
        activeModeFlow.flatMapLatest { mode -> currentBackend(mode).observeProfile(userId) }

    override fun observeTransactions(userId: String, minIsoDate: String?): Flow<List<TransactionItem>> =
        activeModeFlow.flatMapLatest { mode -> currentBackend(mode).observeTransactions(userId, minIsoDate) }

    override fun observeAccounts(userId: String): Flow<List<AccountItem>> =
        activeModeFlow.flatMapLatest { mode -> currentBackend(mode).observeAccounts(userId) }

    override fun observeEnvelopes(userId: String): Flow<List<EnvelopeConfig>> =
        activeModeFlow.flatMapLatest { mode -> currentBackend(mode).observeEnvelopes(userId) }

    override fun observeFinancialItems(userId: String): Flow<List<FinancialItem>> =
        activeModeFlow.flatMapLatest { mode -> currentBackend(mode).observeFinancialItems(userId) }

    override suspend fun saveProfile(profile: UserProfile, userId: String) =
        currentBackend().saveProfile(profile, userId)

    override suspend fun upsertTransaction(tx: TransactionItem, userId: String): String =
        currentBackend().upsertTransaction(tx, userId)

    override suspend fun getTransactionById(targetUserId: String, txId: String): TransactionItem? =
        currentBackend().getTransactionById(targetUserId, txId)

    override suspend fun deleteTransaction(txId: String, userId: String) =
        currentBackend().deleteTransaction(txId, userId)

    override suspend fun upsertAccount(account: AccountItem, userId: String): String =
        currentBackend().upsertAccount(account, userId)

    override suspend fun deleteAccount(accId: String, userId: String) =
        currentBackend().deleteAccount(accId, userId)

    override suspend fun upsertEnvelope(envelope: EnvelopeConfig, userId: String): String =
        currentBackend().upsertEnvelope(envelope, userId)

    override suspend fun upsertEnvelopesAtomically(envelopes: List<EnvelopeConfig>, userId: String) =
        currentBackend().upsertEnvelopesAtomically(envelopes, userId)

    override suspend fun deleteEnvelope(envId: String, userId: String) =
        currentBackend().deleteEnvelope(envId, userId)

    override suspend fun upsertFinancialItem(item: FinancialItem, userId: String): String =
        currentBackend().upsertFinancialItem(item, userId)

    override suspend fun upsertItemAndTransactionAtomically(
        item: FinancialItem,
        tx: TransactionItem,
        userId: String
    ) = currentBackend().upsertItemAndTransactionAtomically(item, tx, userId)

    override suspend fun deleteFinancialItem(itemId: String, userId: String) =
        currentBackend().deleteFinancialItem(itemId, userId)

    override suspend fun ensureSeeded(userId: String) =
        currentBackend().ensureSeeded(userId)

    override suspend fun loadDemoData(
        existingAccounts: List<AccountItem>,
        existingEnvelopes: List<EnvelopeConfig>,
        existingItems: List<FinancialItem>,
        existingTransactions: List<TransactionItem>,
        userId: String
    ) = currentBackend().loadDemoData(
        existingAccounts,
        existingEnvelopes,
        existingItems,
        existingTransactions,
        userId
    )

    override suspend fun resetToCleanAccount(
        existingTxIds: List<String>,
        existingItemIdsToClear: List<String>,
        existingAccounts: List<AccountItem>,
        userId: String
    ) = currentBackend().resetToCleanAccount(
        existingTxIds,
        existingItemIdsToClear,
        existingAccounts,
        userId
    )

    override suspend fun restoreFullBackupAtomically(
        profile: UserProfile?,
        customPrefsItem: FinancialItem?,
        accounts: List<AccountItem>,
        envelopes: List<EnvelopeConfig>,
        financialItems: List<FinancialItem>,
        transactions: List<TransactionItem>,
        staleTxIdsToDelete: List<String>,
        staleItemIdsToDelete: List<String>,
        staleAccountIdsToDelete: List<String>,
        staleEnvelopeIdsToDelete: List<String>,
        onChunkProgress: (completedChunks: Int, totalChunks: Int) -> Unit,
        userId: String
    ) = currentBackend().restoreFullBackupAtomically(
        profile,
        customPrefsItem,
        accounts,
        envelopes,
        financialItems,
        transactions,
        staleTxIdsToDelete,
        staleItemIdsToDelete,
        staleAccountIdsToDelete,
        staleEnvelopeIdsToDelete,
        onChunkProgress,
        userId
    )
}
