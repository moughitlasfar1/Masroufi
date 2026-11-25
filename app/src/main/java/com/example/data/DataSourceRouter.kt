package com.example.data

import android.content.Context
import com.example.data.local.DatabaseHealthState
import com.example.data.local.MasroufiDatabase
import com.example.data.local.MasroufiDatabaseProvider
import com.example.data.local.NoOpRemoteMirror
import com.example.data.local.RemoteMirror
import com.example.data.local.RoomMasroufiRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

enum class ActiveDataSourceMode {
    FIRESTORE_LEGACY,
    ROOM_PRIMARY
}

/**
 * Tracks one-time legacy Firestore -> Room migration metadata flags.
 *
 * Phase 1 enforces strict local-first Room persistence for all normal app operations:
 * [activeMode] is always [ActiveDataSourceMode.ROOM_PRIMARY], and runtime dual-source
 * Firestore switching has been removed.
 */
object DataSourceConfigStore {
    private const val PREFS_NAME = "masroufi_datasource_prefs"
    private const val KEY_FORCE_FIRESTORE_KILL_SWITCH = "force_firestore_fallback_kill_switch"
    private const val KEY_IMPORT_COMPLETED_PREFIX = "room_import_completed_v1_"
    private const val KEY_ANY_IMPORT_COMPLETED = "any_user_room_import_completed_v1"

    private val _activeMode = MutableStateFlow(ActiveDataSourceMode.ROOM_PRIMARY)
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
        _activeMode.value = ActiveDataSourceMode.ROOM_PRIMARY
    }

    fun clearRoomImportCompletedForUser(context: Context, userId: String) {
        if (userId.isBlank()) return
        prefs(context).edit()
            .putBoolean("$KEY_IMPORT_COMPLETED_PREFIX$userId", false)
            .apply()
        _activeMode.value = ActiveDataSourceMode.ROOM_PRIMARY
    }

    fun isFirestoreKillSwitchEnabled(context: Context): Boolean {
        val enabled = prefs(context).getBoolean(KEY_FORCE_FIRESTORE_KILL_SWITCH, false)
        _killSwitchEnabled.value = enabled
        return enabled
    }

    /**
     * Retained for settings preference compatibility; daily operations always remain
     * local-first on Room in Phase 1+.
     */
    fun setFirestoreKillSwitchEnabled(context: Context, enabled: Boolean, currentUserId: String? = null) {
        prefs(context).edit()
            .putBoolean(KEY_FORCE_FIRESTORE_KILL_SWITCH, enabled)
            .apply()
        _killSwitchEnabled.value = enabled
        _activeMode.value = ActiveDataSourceMode.ROOM_PRIMARY
    }

    fun resolveEffectiveMode(
        context: Context,
        userId: String,
        isRoomHealthy: Boolean = MasroufiDatabaseProvider.healthState.value is DatabaseHealthState.Healthy
    ): ActiveDataSourceMode = ActiveDataSourceMode.ROOM_PRIMARY

    fun refreshActiveMode(
        context: Context,
        userId: String,
        isRoomHealthy: Boolean = MasroufiDatabaseProvider.healthState.value is DatabaseHealthState.Healthy
    ): ActiveDataSourceMode {
        _activeMode.value = ActiveDataSourceMode.ROOM_PRIMARY
        return ActiveDataSourceMode.ROOM_PRIMARY
    }

    internal fun resetForTesting(context: Context) {
        prefs(context).edit().clear().commit()
        _killSwitchEnabled.value = false
        _activeMode.value = ActiveDataSourceMode.ROOM_PRIMARY
    }
}

/**
 * Thin single-repository Room facade implementing [MasroufiDataSource].
 *
 * Binds reactively to [MasroufiDatabaseProvider.dbGeneration] so ViewModels never need to be
 * recreated when the encrypted Room database opens asynchronously on cold start or reopens
 * after recovery. Never touches Firestore or requires network connectivity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalRoomMasroufiDataSource(
    context: Context,
    private val remoteMirror: RemoteMirror = NoOpRemoteMirror,
    private val userIdProvider: () -> String = {
        runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: MasroufiRepository.LOCAL_DEVICE_USER_ID
    }
) : MasroufiDataSource {

    private val appContext = context.applicationContext

    @Volatile
    private var cachedDbRef: MasroufiDatabase? = null

    @Volatile
    private var cachedRepo: RoomMasroufiRepository? = null

    private fun currentOpenRepoOrNull(): RoomMasroufiRepository? {
        val openDb = MasroufiDatabaseProvider.getOpenInstanceOrNull() ?: return null
        if (openDb === cachedDbRef) {
            cachedRepo?.let { return it }
        }
        return RoomMasroufiRepository(
            dao = openDb.masroufiDao(),
            remoteMirror = remoteMirror,
            userIdProvider = userIdProvider
        ).also {
            cachedDbRef = openDb
            cachedRepo = it
        }
    }

    private suspend fun requireOpenRepo(): RoomMasroufiRepository? {
        currentOpenRepoOrNull()?.let { return it }
        val db = withContext(Dispatchers.IO) {
            MasroufiDatabaseProvider.getOrOpen(appContext)
        } ?: return null
        return RoomMasroufiRepository(
            dao = db.masroufiDao(),
            remoteMirror = remoteMirror,
            userIdProvider = userIdProvider
        ).also {
            cachedDbRef = db
            cachedRepo = it
        }
    }

    override fun requireUserId(explicitUserId: String?): String {
        return explicitUserId?.takeIf { it.isNotBlank() } ?: userIdProvider()
    }

    override fun observeProfile(userId: String): Flow<UserProfile?> =
        MasroufiDatabaseProvider.dbGeneration.flatMapLatest {
            currentOpenRepoOrNull()?.observeProfile(userId) ?: flowOf(null)
        }

    override fun observeTransactions(userId: String, minIsoDate: String?): Flow<List<TransactionItem>> =
        MasroufiDatabaseProvider.dbGeneration.flatMapLatest {
            currentOpenRepoOrNull()?.observeTransactions(userId, minIsoDate) ?: flowOf(emptyList())
        }

    override fun observeAccounts(userId: String): Flow<List<AccountItem>> =
        MasroufiDatabaseProvider.dbGeneration.flatMapLatest {
            currentOpenRepoOrNull()?.observeAccounts(userId) ?: flowOf(emptyList())
        }

    override fun observeEnvelopes(userId: String): Flow<List<EnvelopeConfig>> =
        MasroufiDatabaseProvider.dbGeneration.flatMapLatest {
            currentOpenRepoOrNull()?.observeEnvelopes(userId) ?: flowOf(emptyList())
        }

    override fun observeFinancialItems(userId: String): Flow<List<FinancialItem>> =
        MasroufiDatabaseProvider.dbGeneration.flatMapLatest {
            currentOpenRepoOrNull()?.observeFinancialItems(userId) ?: flowOf(emptyList())
        }

    override suspend fun saveProfile(profile: UserProfile, userId: String) {
        requireOpenRepo()?.saveProfile(profile, userId)
    }

    override suspend fun upsertTransaction(tx: TransactionItem, userId: String): String {
        val repo = requireOpenRepo() ?: return tx.id
        return repo.upsertTransaction(tx, userId)
    }

    override suspend fun getTransactionById(targetUserId: String, txId: String): TransactionItem? {
        return requireOpenRepo()?.getTransactionById(targetUserId, txId)
    }

    override suspend fun deleteTransaction(txId: String, userId: String) {
        requireOpenRepo()?.deleteTransaction(txId, userId)
    }

    override suspend fun upsertAccount(account: AccountItem, userId: String): String {
        val repo = requireOpenRepo() ?: return account.id
        return repo.upsertAccount(account, userId)
    }

    override suspend fun deleteAccount(accId: String, userId: String) {
        requireOpenRepo()?.deleteAccount(accId, userId)
    }

    override suspend fun upsertEnvelope(envelope: EnvelopeConfig, userId: String): String {
        val repo = requireOpenRepo() ?: return envelope.id
        return repo.upsertEnvelope(envelope, userId)
    }

    override suspend fun upsertEnvelopesAtomically(envelopes: List<EnvelopeConfig>, userId: String) {
        requireOpenRepo()?.upsertEnvelopesAtomically(envelopes, userId)
    }

    override suspend fun deleteEnvelope(envId: String, userId: String) {
        requireOpenRepo()?.deleteEnvelope(envId, userId)
    }

    override suspend fun upsertFinancialItem(item: FinancialItem, userId: String): String {
        val repo = requireOpenRepo() ?: return item.id
        return repo.upsertFinancialItem(item, userId)
    }

    override suspend fun upsertItemAndTransactionAtomically(
        item: FinancialItem,
        tx: TransactionItem,
        userId: String
    ) {
        requireOpenRepo()?.upsertItemAndTransactionAtomically(item, tx, userId)
    }

    override suspend fun deleteFinancialItem(itemId: String, userId: String) {
        requireOpenRepo()?.deleteFinancialItem(itemId, userId)
    }

    override suspend fun ensureSeeded(userId: String) {
        requireOpenRepo()?.ensureSeeded(userId)
    }

    override suspend fun loadDemoData(
        existingAccounts: List<AccountItem>,
        existingEnvelopes: List<EnvelopeConfig>,
        existingItems: List<FinancialItem>,
        existingTransactions: List<TransactionItem>,
        userId: String
    ) {
        requireOpenRepo()?.loadDemoData(
            existingAccounts,
            existingEnvelopes,
            existingItems,
            existingTransactions,
            userId
        )
    }

    override suspend fun resetToCleanAccount(
        existingTxIds: List<String>,
        existingItemIdsToClear: List<String>,
        existingAccounts: List<AccountItem>,
        userId: String
    ) {
        requireOpenRepo()?.resetToCleanAccount(
            existingTxIds,
            existingItemIdsToClear,
            existingAccounts,
            userId
        )
    }

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
    ) {
        requireOpenRepo()?.restoreFullBackupAtomically(
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

    override suspend fun adoptLocalDataIntoUser(sourceUserId: String, targetUserId: String): Int {
        return requireOpenRepo()?.adoptLocalDataIntoUser(sourceUserId, targetUserId) ?: 0
    }
}
