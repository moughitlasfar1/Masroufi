package com.example.data.local

import android.util.Log
import com.example.data.AccountItem
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.MasroufiDataSource
import com.example.data.MasroufiRepository
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.data.local.MasroufiEntityMappers.toDomain
import com.example.data.local.MasroufiEntityMappers.toEntity
import com.example.domain.PayCycleEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Strict local-first encrypted Room implementation of [MasroufiDataSource].
 *
 * - Single source of truth for all daily reads and writes (never requires network or Firestore).
 * - All reads observe local Room tables reactively via Kotlin [Flow].
 * - All writes commit atomically to Room first.
 * - After each local commit succeeds, invokes [remoteMirror] (defaults to [NoOpRemoteMirror]).
 *   Any exception thrown by [remoteMirror] is caught and logged safely and NEVER rolls back or
 *   fails the committed Room write.
 */
class RoomMasroufiRepository(
    private val dao: MasroufiDao,
    private val remoteMirror: RemoteMirror = NoOpRemoteMirror,
    private val userIdProvider: () -> String = {
        runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: MasroufiRepository.LOCAL_DEVICE_USER_ID
    }
) : MasroufiDataSource {

    override fun requireUserId(explicitUserId: String?): String {
        return explicitUserId?.takeIf { it.isNotBlank() } ?: userIdProvider()
    }

    private suspend inline fun notifyRemoteMirror(
        opName: String,
        crossinline action: suspend (RemoteMirror) -> Unit
    ) {
        if (remoteMirror === NoOpRemoteMirror) return
        try {
            action(remoteMirror)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            runCatching {
                Log.w(TAG, "RemoteMirror hook failed after local Room commit ($opName): ${t.message}")
            }
        }
    }

    override fun observeProfile(userId: String): Flow<UserProfile?> {
        val uid = requireUserId(userId)
        return dao.observeProfile(uid).map { it?.toDomain() }
    }

    override fun observeTransactions(
        userId: String,
        minIsoDate: String?
    ): Flow<List<TransactionItem>> {
        val uid = requireUserId(userId)
        val cleanMinIso = minIsoDate?.trim()?.takeIf { it.isNotBlank() && PayCycleEngine.isValidIsoDate(it) }
        val source = if (cleanMinIso != null) {
            dao.observeTransactionsSince(uid, cleanMinIso)
        } else {
            dao.observeTransactions(uid)
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

    override fun observeAccounts(userId: String): Flow<List<AccountItem>> {
        val uid = requireUserId(userId)
        return dao.observeAccounts(uid).map { list -> list.map { it.toDomain() } }
    }

    override fun observeEnvelopes(userId: String): Flow<List<EnvelopeConfig>> {
        val uid = requireUserId(userId)
        return dao.observeEnvelopes(uid).map { list -> list.map { it.toDomain() } }
    }

    override fun observeFinancialItems(userId: String): Flow<List<FinancialItem>> {
        val uid = requireUserId(userId)
        return dao.observeFinancialItems(uid).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun saveProfile(profile: UserProfile, userId: String) {
        val uid = requireUserId(userId)
        val now = System.currentTimeMillis()
        val existing = dao.getProfile(uid)
        val entity = profile.toEntity(uid, now).copy(
            createdAtEpochMs = existing?.createdAtEpochMs?.takeIf { it > 0L } ?: now,
            updatedAtEpochMs = now,
            seeded = true
        )
        dao.upsertProfile(entity)
        val committed = entity.toDomain()
        notifyRemoteMirror("saveProfile") { it.onProfileUpserted(uid, committed) }
    }

    override suspend fun upsertTransaction(tx: TransactionItem, userId: String): String {
        val uid = requireUserId(userId)
        val now = System.currentTimeMillis()
        val entity = tx.toEntity(uid, now)
        dao.upsertTransaction(entity)
        val committed = entity.toDomain()
        notifyRemoteMirror("upsertTransaction") { it.onTransactionUpserted(uid, committed) }
        return entity.id
    }

    override suspend fun getTransactionById(targetUserId: String, txId: String): TransactionItem? {
        if (txId.isBlank()) return null
        return dao.getTransactionById(targetUserId, txId)?.toDomain()
    }

    override suspend fun deleteTransaction(txId: String, userId: String) {
        if (txId.isBlank()) return
        val uid = requireUserId(userId)
        dao.deleteTransaction(uid, txId)
        notifyRemoteMirror("deleteTransaction") { it.onTransactionDeleted(uid, txId) }
    }

    override suspend fun upsertAccount(account: AccountItem, userId: String): String {
        val uid = requireUserId(userId)
        val now = System.currentTimeMillis()
        val entity = account.toEntity(uid, nowMs = now)
        dao.upsertAccount(entity)
        val committed = entity.toDomain()
        notifyRemoteMirror("upsertAccount") { it.onAccountUpserted(uid, committed) }
        return entity.id
    }

    override suspend fun deleteAccount(accId: String, userId: String) {
        if (accId.isBlank()) return
        val uid = requireUserId(userId)
        dao.deleteAccount(uid, accId)
        notifyRemoteMirror("deleteAccount") { it.onAccountDeleted(uid, accId) }
    }

    override suspend fun upsertEnvelope(envelope: EnvelopeConfig, userId: String): String {
        val uid = requireUserId(userId)
        val now = System.currentTimeMillis()
        val entity = envelope.toEntity(uid, now)
        dao.upsertEnvelope(entity)
        val committed = entity.toDomain()
        notifyRemoteMirror("upsertEnvelope") { it.onEnvelopeUpserted(uid, committed) }
        return entity.id
    }

    override suspend fun upsertEnvelopesAtomically(envelopes: List<EnvelopeConfig>, userId: String) {
        if (envelopes.isEmpty()) return
        val uid = requireUserId(userId)
        val now = System.currentTimeMillis()
        val entities = envelopes.mapIndexed { idx, env ->
            val withId = if (env.id.isBlank()) env.copy(id = "env_${now}_$idx") else env
            withId.toEntity(uid, now)
        }
        dao.upsertEnvelopes(entities)
        val committed = entities.map { it.toDomain() }
        notifyRemoteMirror("upsertEnvelopesAtomically") { it.onEnvelopesUpserted(uid, committed) }
    }

    override suspend fun deleteEnvelope(envId: String, userId: String) {
        if (envId.isBlank()) return
        val uid = requireUserId(userId)
        dao.deleteEnvelope(uid, envId)
        notifyRemoteMirror("deleteEnvelope") { it.onEnvelopeDeleted(uid, envId) }
    }

    override suspend fun upsertFinancialItem(item: FinancialItem, userId: String): String {
        val uid = requireUserId(userId)
        val now = System.currentTimeMillis()
        val entity = item.toEntity(uid, now)
        dao.upsertFinancialItem(entity)
        val committed = entity.toDomain()
        notifyRemoteMirror("upsertFinancialItem") { it.onFinancialItemUpserted(uid, committed) }
        return entity.id
    }

    override suspend fun upsertItemAndTransactionAtomically(
        item: FinancialItem,
        tx: TransactionItem,
        userId: String
    ) {
        val uid = requireUserId(userId)
        val now = System.currentTimeMillis()
        val itemEntity = item.toEntity(uid, now)
        val txEntity = tx.toEntity(uid, now)
        dao.upsertItemAndTransactionAtomically(itemEntity, txEntity)
        val committedItem = itemEntity.toDomain()
        val committedTx = txEntity.toDomain()
        notifyRemoteMirror("upsertItemAndTransactionAtomically") {
            it.onItemAndTransactionUpserted(uid, committedItem, committedTx)
        }
    }

    override suspend fun deleteFinancialItem(itemId: String, userId: String) {
        if (itemId.isBlank()) return
        val uid = requireUserId(userId)
        dao.deleteFinancialItem(uid, itemId)
        notifyRemoteMirror("deleteFinancialItem") { it.onFinancialItemDeleted(uid, itemId) }
    }

    override suspend fun adoptLocalDataIntoUser(
        sourceUserId: String,
        targetUserId: String
    ): Int {
        val target = requireUserId(targetUserId)
        if (sourceUserId.isBlank() || sourceUserId == target) return 0
        val adopted = dao.adoptUserDataAtomically(sourceUserId, target)
        if (adopted > 0) {
            notifyRemoteMirror("adoptLocalDataIntoUser") {
                it.onLocalDataAdopted(sourceUserId, target, adopted)
            }
        }
        return adopted
    }

    private fun defaultStandardEnvelopes(uid: String): List<EnvelopeConfig> = listOf(
        EnvelopeConfig(id = "env_transport", userId = uid, key = "transport", nameFr = "transport", nameAr = "transport", nameDarija = "transport", nameEn = "transport", percentage = 35.0, hasCap = true, colorHex = "#0EA5E9"),
        EnvelopeConfig(id = "env_charges", userId = uid, key = "charges", nameFr = "charges", nameAr = "charges", nameDarija = "charges", nameEn = "charges", percentage = 26.5, hasCap = true, colorHex = "#7C3AED"),
        EnvelopeConfig(id = "env_cafe", userId = uid, key = "cafe", nameFr = "cafe", nameAr = "cafe", nameDarija = "cafe", nameEn = "cafe", percentage = 18.5, hasCap = true, colorHex = "#D97706"),
        EnvelopeConfig(id = "env_epargne", userId = uid, key = "epargne", nameFr = "epargne", nameAr = "epargne", nameDarija = "epargne", nameEn = "epargne", percentage = 20.0, hasCap = true, colorHex = "#059669"),
        EnvelopeConfig(id = "env_autre", userId = uid, key = "autre", nameFr = "autre", nameAr = "autre", nameDarija = "autre", nameEn = "autre", percentage = 0.0, hasCap = false, colorHex = "#E11D48")
    )

    private fun defaultQuickPresets(): List<FinancialItem> = listOf(
        FinancialItem(id = "pre_cafe", itemType = "preset", name = "Café & Garo", subtitle = "cafe", amount = 20.0, secondaryAmount = 0.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_cash"),
        FinancialItem(id = "pre_taxi", itemType = "preset", name = "Petit Taxi", subtitle = "transport", amount = 15.0, secondaryAmount = 0.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_cash"),
        FinancialItem(id = "pre_bread", itemType = "preset", name = "Khobz & Lben", subtitle = "charges", amount = 10.0, secondaryAmount = 0.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_cash"),
        FinancialItem(id = "pre_oncf", itemType = "preset", name = "Ticket ONCF", subtitle = "transport", amount = 50.0, secondaryAmount = 0.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_bank")
    )

    private fun collisionSafeSeedId(baseId: String, ownerUid: String?, currentUid: String): String {
        if (ownerUid == null || ownerUid == currentUid) return baseId
        val suffix = currentUid.replace(Regex("[^a-zA-Z0-9]"), "").take(10).ifBlank { "u" }
        return "${baseId}_$suffix"
    }

    override suspend fun ensureSeeded(userId: String) {
        val uid = requireUserId(userId)

        // When signing in with a real account, atomically adopt any local_device_user Room data first
        // so Local Mode records are never orphaned or overwritten by fresh default seeds.
        if (uid != MasroufiRepository.LOCAL_DEVICE_USER_ID) {
            adoptLocalDataIntoUser(MasroufiRepository.LOCAL_DEVICE_USER_ID, uid)
        }

        val existing = dao.getProfile(uid)
        if (existing != null && existing.seeded) {
            return
        }
        val now = System.currentTimeMillis()
        val newProfile = UserProfileEntity(
            userId = uid,
            baseBudget = 6400.0,
            payCycleDay = 1,
            rolloverEnabled = true,
            language = "fr",
            isDarkTheme = false,
            seeded = true,
            createdAtEpochMs = existing?.createdAtEpochMs?.takeIf { it > 0L } ?: now,
            updatedAtEpochMs = now
        )
        val cleanAccounts = listOf(
            AccountItem(id = "acc_cash", userId = uid, name = "Espèces (Cash)", kind = "cash", initialBalance = 0.0, colorHex = "#10B981"),
            AccountItem(id = "acc_bank", userId = uid, name = "Compte Bancaire (CIH/Attijari)", kind = "bank", initialBalance = 0.0, colorHex = "#38BDF8"),
            AccountItem(id = "acc_wallet", userId = uid, name = "Portefeuille Mobile", kind = "wallet", initialBalance = 0.0, colorHex = "#FBBF24")
        ).mapIndexed { idx, acc ->
            val safeId = collisionSafeSeedId(acc.id, dao.getAccountOwner(acc.id), uid)
            acc.copy(id = safeId).toEntity(uid, sortOrder = idx, nowMs = now)
        }
        val envelopes = defaultStandardEnvelopes(uid).map { env ->
            val safeId = collisionSafeSeedId(env.id, dao.getEnvelopeOwner(env.id), uid)
            env.copy(id = safeId).toEntity(uid, now)
        }
        val presets = defaultQuickPresets().map { item ->
            val safeId = collisionSafeSeedId(item.id, dao.getFinancialItemOwner(item.id), uid)
            item.copy(id = safeId).toEntity(uid, now)
        }

        dao.upsertProfile(newProfile)
        dao.upsertAccounts(cleanAccounts)
        dao.upsertEnvelopes(envelopes)
        dao.upsertFinancialItems(presets)

        notifyRemoteMirror("ensureSeeded") { it.onUserSeeded(uid) }
    }

    override suspend fun loadDemoData(
        existingAccounts: List<AccountItem>,
        existingEnvelopes: List<EnvelopeConfig>,
        existingItems: List<FinancialItem>,
        existingTransactions: List<TransactionItem>,
        userId: String
    ) {
        val uid = requireUserId(userId)
        val now = System.currentTimeMillis()
        val demoAccounts = listOf(
            AccountItem(id = "acc_cash", userId = uid, name = "Espèces (Cash)", kind = "cash", initialBalance = 1850.0, colorHex = "#10B981"),
            AccountItem(id = "acc_bank", userId = uid, name = "Compte Bancaire (CIH/Attijari)", kind = "bank", initialBalance = 5200.0, colorHex = "#38BDF8"),
            AccountItem(id = "acc_wallet", userId = uid, name = "Portefeuille Mobile", kind = "wallet", initialBalance = 650.0, colorHex = "#FBBF24")
        ).mapIndexed { idx, acc -> acc.toEntity(uid, sortOrder = idx, nowMs = now) }

        val demoEnvelopes = defaultStandardEnvelopes(uid).map { it.toEntity(uid, now) }
        val demoTxs = listOf(
            TransactionItem(id = "tx_demo_9", type = "expense", amount = 5.0, category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Garo", isoDate = "2026-10-30"),
            TransactionItem(id = "tx_demo_8", type = "expense", amount = 20.0, category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Café & garo", isoDate = "2026-10-30"),
            TransactionItem(id = "tx_demo_7", type = "expense", amount = 5.0, category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Garo", isoDate = "2026-10-30"),
            TransactionItem(id = "tx_demo_6", type = "expense", amount = 20.0, category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Cafe & garo", isoDate = "2026-10-30"),
            TransactionItem(id = "tx_demo_5", type = "expense", amount = 20.0, category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Cafe & garo", isoDate = "2026-10-29"),
            TransactionItem(id = "tx_demo_4", type = "expense", amount = 457.0, category = "Transport & Carburant", envelopeKey = "transport", accountId = "acc_bank", desc = "ONCF CARTE", isoDate = "2026-10-29"),
            TransactionItem(id = "tx_demo_3", type = "expense", amount = 20.0, category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Café & garo", isoDate = "2026-10-29"),
            TransactionItem(id = "tx_demo_2", type = "expense", amount = 500.0, category = "Autre / Imprévus", envelopeKey = "autre", accountId = "acc_bank", desc = "Crédit mama", isoDate = "2026-10-29"),
            TransactionItem(id = "tx_demo_1", type = "expense", amount = 1000.0, category = "Autre / Imprévus", envelopeKey = "autre", accountId = "acc_bank", desc = "Crédit inaam", isoDate = "2026-10-29")
        ).map { it.toEntity(uid, now) }

        val demoItems = (defaultQuickPresets() + listOf(
            FinancialItem(id = "bill_fibre", itemType = "bill", name = "Fibre Optique & Mobile", subtitle = "charges", amount = 250.0, secondaryAmount = 0.0, dayOrTurn = 5, extraInt = 0, dateOrCycle = ""),
            FinancialItem(id = "bill_lydec", itemType = "bill", name = "Eau & Électricité (Lydec/Redal)", subtitle = "charges", amount = 320.0, secondaryAmount = 0.0, dayOrTurn = 18, extraInt = 0, dateOrCycle = ""),
            FinancialItem(id = "goal_urgence", itemType = "goal", name = "Fonds d'urgence (Tawfir)", subtitle = "epargne", amount = 10000.0, secondaryAmount = 4200.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-06-30"),
            FinancialItem(id = "debt_mama", itemType = "debt", name = "Crédit Mama", subtitle = "Mama", amount = 500.0, secondaryAmount = 0.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "lent"),
            FinancialItem(id = "debt_inaam", itemType = "debt", name = "Crédit Inaam", subtitle = "Inaam", amount = 1000.0, secondaryAmount = 200.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "lent"),
            FinancialItem(id = "daret_famille", itemType = "daret", name = "Daret Collègues", subtitle = "10 membres", amount = 1000.0, secondaryAmount = 10.0, dayOrTurn = 4, extraInt = 3, dateOrCycle = "2027-02-01"),
            FinancialItem(id = "season_ramadan", itemType = "seasonal", name = "Ramadan & Ftous", subtitle = "ramadan", amount = 3000.0, secondaryAmount = 900.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-02-17"),
            FinancialItem(id = "season_eid", itemType = "seasonal", name = "Aïd al-Adha (Kebch)", subtitle = "eid", amount = 4500.0, secondaryAmount = 1200.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-05-16"),
            FinancialItem(id = "season_vignette", itemType = "seasonal", name = "Vignette & Assurance Auto", subtitle = "vignette", amount = 3800.0, secondaryAmount = 1500.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-01-31")
        )).map { it.toEntity(uid, now) }

        dao.upsertAccounts(demoAccounts)
        dao.upsertEnvelopes(demoEnvelopes)
        dao.upsertTransactions(demoTxs)
        dao.upsertFinancialItems(demoItems)

        notifyRemoteMirror("loadDemoData") { it.onDemoDataLoaded(uid) }
    }

    override suspend fun resetToCleanAccount(
        existingTxIds: List<String>,
        existingItemIdsToClear: List<String>,
        existingAccounts: List<AccountItem>,
        userId: String
    ) {
        val uid = requireUserId(userId)
        val now = System.currentTimeMillis()
        val extraAccountIds = existingAccounts
            .map { it.id }
            .filter { it.isNotBlank() && it !in listOf("acc_cash", "acc_bank", "acc_wallet") }

        val cleanAccounts = listOf(
            AccountItem(id = "acc_cash", userId = uid, name = "Espèces (Cash)", kind = "cash", initialBalance = 0.0, colorHex = "#10B981"),
            AccountItem(id = "acc_bank", userId = uid, name = "Compte Bancaire (CIH/Attijari)", kind = "bank", initialBalance = 0.0, colorHex = "#38BDF8"),
            AccountItem(id = "acc_wallet", userId = uid, name = "Portefeuille Mobile", kind = "wallet", initialBalance = 0.0, colorHex = "#FBBF24")
        ).mapIndexed { idx, acc -> acc.toEntity(uid, sortOrder = idx, nowMs = now) }

        dao.resetUserToCleanAtomically(
            userId = uid,
            txIdsToDelete = existingTxIds,
            itemIdsToDelete = existingItemIdsToClear,
            accountIdsToDelete = extraAccountIds,
            cleanAccounts = cleanAccounts
        )

        notifyRemoteMirror("resetToCleanAccount") {
            it.onAccountReset(uid, existingTxIds, existingItemIdsToClear, extraAccountIds)
        }
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
        val uid = requireUserId(userId)
        val now = System.currentTimeMillis()

        val profileEntity = profile?.toEntity(uid, now)
        val customPrefsEntity = customPrefsItem?.copy(id = "custom_prefs")?.toEntity(uid, now)

        val accountEntities = accounts.mapIndexed { idx, acc ->
            val cleanId = acc.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "acc_restored_$idx" }
            acc.copy(id = cleanId).toEntity(uid, sortOrder = idx, nowMs = now)
        }
        val restoredAccountIds = accountEntities.map { it.id }.toSet()

        val envelopeEntities = envelopes.mapIndexed { idx, env ->
            val cleanId = env.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "env_restored_$idx" }
            env.copy(id = cleanId).toEntity(uid, now)
        }
        val restoredEnvelopeIds = envelopeEntities.map { it.id }.toSet()

        val itemEntities = financialItems.mapIndexed { idx, item ->
            val cleanId = item.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "item_restored_$idx" }
            item.copy(id = cleanId).toEntity(uid, now)
        }
        val restoredItemIds = itemEntities.map { it.id }.toMutableSet().apply {
            if (customPrefsEntity != null) add("custom_prefs")
        }

        val txEntities = transactions.mapIndexed { idx, tx ->
            val cleanId = tx.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "tx_restored_$idx" }
            tx.copy(id = cleanId).toEntity(uid, now)
        }
        val restoredTxIds = txEntities.map { it.id }.toSet()

        val safeStaleTxs = staleTxIdsToDelete.map { it.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") }
            .filter { it.isNotBlank() && it !in restoredTxIds }
        val safeStaleItems = staleItemIdsToDelete.map { it.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") }
            .filter { it.isNotBlank() && it !in restoredItemIds }
        val safeStaleAccounts = if (accountEntities.isNotEmpty()) {
            staleAccountIdsToDelete.map { it.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") }
                .filter { it.isNotBlank() && it !in restoredAccountIds }
        } else emptyList()
        val safeStaleEnvelopes = if (envelopeEntities.isNotEmpty()) {
            staleEnvelopeIdsToDelete.map { it.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") }
                .filter { it.isNotBlank() && it !in restoredEnvelopeIds }
        } else emptyList()

        dao.restoreBackupSnapshotAtomically(
            userId = uid,
            profile = profileEntity,
            customPrefsItem = customPrefsEntity,
            accounts = accountEntities,
            envelopes = envelopeEntities,
            financialItems = itemEntities,
            transactions = txEntities,
            staleTxIdsToDelete = safeStaleTxs,
            staleItemIdsToDelete = safeStaleItems,
            staleAccountIdsToDelete = safeStaleAccounts,
            staleEnvelopeIdsToDelete = safeStaleEnvelopes
        )
        onChunkProgress(1, 1)

        notifyRemoteMirror("restoreFullBackupAtomically") {
            it.onBackupRestored(
                userId = uid,
                restoredTxCount = txEntities.size,
                restoredItemCount = itemEntities.size,
                restoredAccountCount = accountEntities.size,
                restoredEnvelopeCount = envelopeEntities.size
            )
        }
    }

    companion object {
        private const val TAG = "RoomMasroufiRepo"
    }
}
