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
import com.example.data.local.MasroufiEntityMappers.toLocalEntity
import com.example.domain.InternalTransferResult
import com.example.domain.LoanCategory
import com.example.domain.LoanDirection
import com.example.domain.LoanItem
import com.example.domain.LoanRepaymentItem
import com.example.domain.LoanRepaymentResult
import com.example.domain.LoanStatus
import com.example.domain.PayCycleEngine
import com.example.domain.RecurrencePostingMode
import com.example.domain.RecurrenceScheduleEngine
import com.example.domain.RecurringPostResult
import com.example.domain.RecurringRuleItem
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.masroufi.core.Money
import com.masroufi.credit.data.SavedCreditEntity
import java.util.UUID
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
            if (com.example.BuildConfig.DEBUG) {
                runCatching {
                    Log.w(TAG, "RemoteMirror hook failed after local Room commit ($opName): ${t.javaClass.simpleName}")
                }
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
        FinancialItem(id = "pre_cafe", itemType = "preset", name = "Café & Garo", subtitle = "cafe", amount = Money.fromDh(20), secondaryAmount = Money.ZERO, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_cash"),
        FinancialItem(id = "pre_taxi", itemType = "preset", name = "Petit Taxi", subtitle = "transport", amount = Money.fromDh(15), secondaryAmount = Money.ZERO, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_cash"),
        FinancialItem(id = "pre_bread", itemType = "preset", name = "Khobz & Lben", subtitle = "charges", amount = Money.fromDh(10), secondaryAmount = Money.ZERO, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_cash"),
        FinancialItem(id = "pre_oncf", itemType = "preset", name = "Ticket ONCF", subtitle = "transport", amount = Money.fromDh(50), secondaryAmount = Money.ZERO, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_bank")
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
            baseBudgetCentimes = 640_000L,
            payCycleDay = 1,
            rolloverEnabled = true,
            language = "fr",
            isDarkTheme = false,
            seeded = true,
            createdAtEpochMs = existing?.createdAtEpochMs?.takeIf { it > 0L } ?: now,
            updatedAtEpochMs = now
        )
        val cleanAccounts = listOf(
            AccountItem(id = "acc_cash", userId = uid, name = "Espèces (Cash)", kind = "cash", initialBalance = Money.ZERO, colorHex = "#10B981"),
            AccountItem(id = "acc_bank", userId = uid, name = "Compte Bancaire (CIH/Attijari)", kind = "bank", initialBalance = Money.ZERO, colorHex = "#38BDF8"),
            AccountItem(id = "acc_wallet", userId = uid, name = "Portefeuille Mobile", kind = "wallet", initialBalance = Money.ZERO, colorHex = "#FBBF24")
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
            AccountItem(id = "acc_cash", userId = uid, name = "Espèces (Cash)", kind = "cash", initialBalance = Money.fromDh(1850), colorHex = "#10B981"),
            AccountItem(id = "acc_bank", userId = uid, name = "Compte Bancaire (CIH/Attijari)", kind = "bank", initialBalance = Money.fromDh(5200), colorHex = "#38BDF8"),
            AccountItem(id = "acc_wallet", userId = uid, name = "Portefeuille Mobile", kind = "wallet", initialBalance = Money.fromDh(650), colorHex = "#FBBF24")
        ).mapIndexed { idx, acc -> acc.toEntity(uid, sortOrder = idx, nowMs = now) }

        val demoEnvelopes = defaultStandardEnvelopes(uid).map { it.toEntity(uid, now) }
        val demoTxs = listOf(
            TransactionItem(id = "tx_demo_9", type = "expense", amount = Money.fromDh(5), category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Garo", isoDate = "2026-10-30"),
            TransactionItem(id = "tx_demo_8", type = "expense", amount = Money.fromDh(20), category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Café & garo", isoDate = "2026-10-30"),
            TransactionItem(id = "tx_demo_7", type = "expense", amount = Money.fromDh(5), category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Garo", isoDate = "2026-10-30"),
            TransactionItem(id = "tx_demo_6", type = "expense", amount = Money.fromDh(20), category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Cafe & garo", isoDate = "2026-10-30"),
            TransactionItem(id = "tx_demo_5", type = "expense", amount = Money.fromDh(20), category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Cafe & garo", isoDate = "2026-10-29"),
            TransactionItem(id = "tx_demo_4", type = "expense", amount = Money.fromDh(457), category = "Transport & Carburant", envelopeKey = "transport", accountId = "acc_bank", desc = "ONCF CARTE", isoDate = "2026-10-29"),
            TransactionItem(id = "tx_demo_3", type = "expense", amount = Money.fromDh(20), category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Café & garo", isoDate = "2026-10-29"),
            TransactionItem(id = "tx_demo_2", type = "expense", amount = Money.fromDh(500), category = "Autre / Imprévus", envelopeKey = "autre", accountId = "acc_bank", desc = "Crédit mama", isoDate = "2026-10-29"),
            TransactionItem(id = "tx_demo_1", type = "expense", amount = Money.fromDh(1000), category = "Autre / Imprévus", envelopeKey = "autre", accountId = "acc_bank", desc = "Crédit inaam", isoDate = "2026-10-29")
        ).map { it.toEntity(uid, now) }

        val demoItems = (defaultQuickPresets() + listOf(
            FinancialItem(id = "bill_fibre", itemType = "bill", name = "Fibre Optique & Mobile", subtitle = "charges", amount = Money.fromDh(250), secondaryAmount = Money.ZERO, dayOrTurn = 5, extraInt = 0, dateOrCycle = ""),
            FinancialItem(id = "bill_lydec", itemType = "bill", name = "Eau & Électricité (Lydec/Redal)", subtitle = "charges", amount = Money.fromDh(320), secondaryAmount = Money.ZERO, dayOrTurn = 18, extraInt = 0, dateOrCycle = ""),
            FinancialItem(id = "goal_urgence", itemType = "goal", name = "Fonds d'urgence (Tawfir)", subtitle = "epargne", amount = Money.fromDh(10000), secondaryAmount = Money.fromDh(4200), dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-06-30"),
            FinancialItem(id = "debt_mama", itemType = "debt", name = "Crédit Mama", subtitle = "Mama", amount = Money.fromDh(500), secondaryAmount = Money.ZERO, dayOrTurn = 1, extraInt = 0, dateOrCycle = "lent"),
            FinancialItem(id = "debt_inaam", itemType = "debt", name = "Crédit Inaam", subtitle = "Inaam", amount = Money.fromDh(1000), secondaryAmount = Money.fromDh(200), dayOrTurn = 1, extraInt = 0, dateOrCycle = "lent"),
            FinancialItem(id = "daret_famille", itemType = "daret", name = "Daret Collègues", subtitle = "10 membres", amount = Money.fromDh(1000), secondaryAmount = Money.ZERO, daretMembers = 10, dayOrTurn = 4, extraInt = 3, dateOrCycle = "2027-02-01"),
            FinancialItem(id = "season_ramadan", itemType = "seasonal", name = "Ramadan & Ftous", subtitle = "ramadan", amount = Money.fromDh(3000), secondaryAmount = Money.fromDh(900), dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-02-17"),
            FinancialItem(id = "season_eid", itemType = "seasonal", name = "Aïd al-Adha (Kebch)", subtitle = "eid", amount = Money.fromDh(4500), secondaryAmount = Money.fromDh(1200), dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-05-16"),
            FinancialItem(id = "season_vignette", itemType = "seasonal", name = "Vignette & Assurance Auto", subtitle = "vignette", amount = Money.fromDh(3800), secondaryAmount = Money.fromDh(1500), dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-01-31")
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
            AccountItem(id = "acc_cash", userId = uid, name = "Espèces (Cash)", kind = "cash", initialBalance = Money.ZERO, colorHex = "#10B981"),
            AccountItem(id = "acc_bank", userId = uid, name = "Compte Bancaire (CIH/Attijari)", kind = "bank", initialBalance = Money.ZERO, colorHex = "#38BDF8"),
            AccountItem(id = "acc_wallet", userId = uid, name = "Portefeuille Mobile", kind = "wallet", initialBalance = Money.ZERO, colorHex = "#FBBF24")
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

    override suspend fun getSavedCredits(userId: String): List<SavedCreditEntity> {
        val uid = requireUserId(userId)
        return dao.getSavedCredits(uid).map { it.toDomain() }
    }

    // --- Phase 5: Accounts Atomic Transfers & Loans ---

    override fun observeLoans(userId: String): Flow<List<LoanItem>> {
        val uid = requireUserId(userId)
        return dao.observeLoans(uid).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getLoans(userId: String): List<LoanItem> {
        val uid = requireUserId(userId)
        return dao.getLoans(uid).map { it.toDomain() }
    }

    override fun observeLoanRepayments(userId: String): Flow<List<LoanRepaymentItem>> {
        val uid = requireUserId(userId)
        return dao.observeLoanRepayments(uid).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getLoanRepayments(userId: String): List<LoanRepaymentItem> {
        val uid = requireUserId(userId)
        return dao.getLoanRepayments(uid).map { it.toDomain() }
    }

    override suspend fun upsertLoan(loan: LoanItem, userId: String): String {
        val uid = requireUserId(userId)
        val now = System.currentTimeMillis()
        val existing = loan.id.takeIf { it.isNotBlank() }?.let { dao.getLoanById(uid, it) }
        val entity = loan.toEntity(uid, now).copy(
            createdAtEpochMs = existing?.createdAtEpochMs?.takeIf { it > 0L } ?: now,
            updatedAtEpochMs = now
        )
        dao.upsertLoan(entity)
        return entity.id
    }

    override suspend fun deleteLoan(loanId: String, userId: String) {
        if (loanId.isBlank()) return
        val uid = requireUserId(userId)
        dao.deleteLoanAndRepaymentsAtomically(uid, loanId)
    }

    override suspend fun executeInternalTransfer(
        fromAccountId: String,
        toAccountId: String,
        amount: Money,
        isoDate: String,
        desc: String,
        userId: String
    ): InternalTransferResult {
        val cleanFrom = fromAccountId.trim().take(80)
        val cleanTo = toAccountId.trim().take(80)
        if (cleanFrom.isBlank() || cleanTo.isBlank() || cleanFrom == cleanTo) {
            return InternalTransferResult.InvalidAccounts
        }
        if (!amount.isPositive || amount > Money.MAX_STORABLE) {
            return InternalTransferResult.InvalidAmount
        }
        val uid = requireUserId(userId)
        val validatedDate = try {
            PayCycleEngine.requireOrNormalizeIsoDate(isoDate)
        } catch (_: IllegalArgumentException) {
            PayCycleEngine.todayIso()
        }
        val now = System.currentTimeMillis()
        val groupToken = UUID.randomUUID().toString().replace("-", "").take(12)
        val groupId = "trf_${now}_$groupToken"
        val safeDesc = desc.trim().ifBlank { "Transfert entre comptes" }.take(250)

        val outEntity = TransactionEntity(
            id = "${groupId}_out",
            userId = uid,
            type = "transfer_out",
            amountCentimes = amount.centimes,
            category = "autre",
            envelopeKey = "autre",
            accountId = cleanFrom,
            toAccountId = cleanTo,
            desc = safeDesc,
            isoDate = validatedDate,
            hourOfDay = -1,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            transferGroupId = groupId,
            loanId = null
        )
        val inEntity = TransactionEntity(
            id = "${groupId}_in",
            userId = uid,
            type = "transfer_in",
            amountCentimes = amount.centimes,
            category = "autre",
            envelopeKey = "autre",
            accountId = cleanTo,
            toAccountId = cleanFrom,
            desc = safeDesc,
            isoDate = validatedDate,
            hourOfDay = -1,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            transferGroupId = groupId,
            loanId = null
        )
        dao.executeInternalTransferAtomically(outEntity, inEntity)
        val outDomain = outEntity.toDomain()
        val inDomain = inEntity.toDomain()
        notifyRemoteMirror("executeInternalTransfer") {
            it.onTransactionUpserted(uid, outDomain)
            it.onTransactionUpserted(uid, inDomain)
        }
        return InternalTransferResult.Success(
            transferGroupId = groupId,
            outgoingTx = outDomain,
            incomingTx = inDomain
        )
    }

    override suspend fun deleteTransferGroup(transferGroupId: String, userId: String): Int {
        val cleanGroup = transferGroupId.trim()
        if (cleanGroup.isBlank()) return 0
        val uid = requireUserId(userId)
        return dao.deleteTransactionsByTransferGroup(uid, cleanGroup)
    }

    override suspend fun recordLoanRepayment(
        loanId: String,
        amount: Money,
        linkedAccountId: String?,
        isoDate: String,
        note: String,
        userId: String
    ): LoanRepaymentResult {
        if (!amount.isPositive || amount > Money.MAX_STORABLE) {
            return LoanRepaymentResult.InvalidAmount
        }
        val cleanLoanId = loanId.trim()
        if (cleanLoanId.isBlank()) {
            return LoanRepaymentResult.LoanNotFound(loanId)
        }
        val uid = requireUserId(userId)
        val validatedDate = try {
            PayCycleEngine.requireOrNormalizeIsoDate(isoDate)
        } catch (_: IllegalArgumentException) {
            PayCycleEngine.todayIso()
        }
        val now = System.currentTimeMillis()
        val token = UUID.randomUUID().toString().replace("-", "").take(10)
        val repaymentId = "repay_${now}_$token"
        val cleanLinkedAccount = linkedAccountId?.trim()?.takeIf { it.isNotBlank() }?.take(80)
        val linkedTxId = if (cleanLinkedAccount != null) "tx_loan_${now}_$token" else null

        val result = dao.recordLoanRepaymentAtomically(
            userId = uid,
            loanId = cleanLoanId,
            amountCentimes = amount.centimes,
            isoDate = validatedDate,
            linkedAccountId = cleanLinkedAccount,
            note = note,
            repaymentId = repaymentId,
            linkedTxId = linkedTxId,
            nowMs = now
        )
        if (result is LoanRepaymentResult.Success && result.linkedTransaction != null) {
            notifyRemoteMirror("recordLoanRepayment") {
                it.onTransactionUpserted(uid, result.linkedTransaction)
            }
        }
        return result
    }

    override suspend fun promoteSavedCreditToLoan(
        savedCreditId: String,
        linkedAccountId: String?,
        titleOverride: String?,
        userId: String
    ): Result<LoanItem> {
        val uid = requireUserId(userId)
        val cleanCreditId = savedCreditId.trim()
        if (cleanCreditId.isBlank()) {
            return Result.failure(IllegalArgumentException("Identifiant de crédit invalide."))
        }
        // Idempotent: if already promoted, return the existing tracked LoanItem (no duplicate liability)
        dao.getLoanBySavedCreditId(uid, cleanCreditId)?.let { existing ->
            return Result.success(existing.toDomain())
        }
        val savedCredit = dao.getSavedCreditById(uid, cleanCreditId)
            ?: return Result.failure(NoSuchElementException("Simulation de crédit introuvable ($cleanCreditId)."))

        val now = System.currentTimeMillis()
        val principalCentimes = savedCredit.financedPrincipalCentimes
            .takeIf { it > 0L }
            ?: savedCredit.loanAmountCentimes.coerceAtLeast(0L)
        val resolvedTitle = titleOverride?.trim()?.takeIf { it.isNotBlank() } ?: when (savedCredit.type.uppercase()) {
            "IMMOBILIER" -> "Crédit Immobilier"
            "AUTO" -> "Crédit Auto"
            "MOURABAHA" -> "Financement Mourabaha"
            else -> "Crédit Consommation"
        }
        val entity = LoanEntity(
            id = "loan_credit_${MasroufiEntityMappers.sanitizeId(cleanCreditId, "cr", now)}",
            userId = uid,
            title = resolvedTitle.take(120),
            counterparty = "Banque",
            direction = LoanDirection.LIABILITY.wire,
            category = LoanCategory.BANK_CREDIT.wire,
            principalCentimes = principalCentimes,
            remainingCentimes = principalCentimes,
            monthlyPaymentCentimes = savedCredit.monthlyPaymentCentimes.coerceAtLeast(0L),
            status = if (principalCentimes > 0L) LoanStatus.ACTIVE.wire else LoanStatus.PAID_OFF.wire,
            linkedAccountId = linkedAccountId?.trim()?.takeIf { it.isNotBlank() }?.take(80),
            sourceFinancialItemId = null,
            sourceSavedCreditId = cleanCreditId,
            dueIsoDate = null,
            notes = "${savedCredit.durationMonths} mois",
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        dao.upsertLoan(entity)
        return Result.success(entity.toDomain())
    }

    // --- Phase 6: Recurrence Engine ---

    override fun observeRecurringRules(userId: String): Flow<List<RecurringRuleItem>> {
        val uid = requireUserId(userId)
        return dao.observeRecurringRules(uid).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getRecurringRules(userId: String): List<RecurringRuleItem> {
        val uid = requireUserId(userId)
        return dao.getRecurringRules(uid).map { it.toDomain() }
    }

    override suspend fun upsertRecurringRule(rule: RecurringRuleItem, userId: String): String {
        val uid = requireUserId(userId)
        val now = System.currentTimeMillis()
        val existing = rule.id.takeIf { it.isNotBlank() }?.let { dao.getRecurringRuleById(uid, it) }
        val entity = rule.toEntity(uid, now).copy(
            createdAtEpochMs = existing?.createdAtEpochMs?.takeIf { it > 0L } ?: now,
            updatedAtEpochMs = now
        )
        dao.upsertRecurringRule(entity)
        return entity.id
    }

    override suspend fun deleteRecurringRule(ruleId: String, userId: String) {
        if (ruleId.isBlank()) return
        val uid = requireUserId(userId)
        dao.deleteRecurringRule(uid, ruleId)
    }

    override suspend fun postRecurringOccurrence(
        ruleId: String,
        occurrenceIsoDate: String,
        amountOverride: Money?,
        userId: String
    ): RecurringPostResult {
        val cleanRuleId = ruleId.trim()
        if (cleanRuleId.isBlank()) return RecurringPostResult.RuleNotFound(ruleId)
        if (amountOverride != null && (!amountOverride.isPositive || amountOverride > Money.MAX_STORABLE)) {
            return RecurringPostResult.InvalidRule
        }
        val uid = requireUserId(userId)
        val validatedDate = try {
            PayCycleEngine.requireOrNormalizeIsoDate(occurrenceIsoDate)
        } catch (_: IllegalArgumentException) {
            PayCycleEngine.todayIso()
        }
        val now = System.currentTimeMillis()
        val token = UUID.randomUUID().toString().replace("-", "").take(10)
        val txId = "tx_rec_${now}_$token"
        val repaymentId = "repay_rec_${now}_$token"

        val result = dao.postRecurringOccurrenceAtomically(
            userId = uid,
            ruleId = cleanRuleId,
            occurrenceIsoDate = validatedDate,
            txId = txId,
            repaymentId = repaymentId,
            nowMs = now,
            amountOverrideCentimes = amountOverride?.centimes
        )
        if (result is RecurringPostResult.Success && result.postedTransaction != null) {
            notifyRemoteMirror("postRecurringOccurrence") {
                it.onTransactionUpserted(uid, result.postedTransaction)
            }
        }
        return result
    }

    override suspend fun skipRecurringOccurrence(
        ruleId: String,
        occurrenceIsoDate: String,
        userId: String
    ): Boolean {
        val cleanRuleId = ruleId.trim()
        if (cleanRuleId.isBlank()) return false
        val uid = requireUserId(userId)
        val validatedDate = try {
            PayCycleEngine.requireOrNormalizeIsoDate(occurrenceIsoDate)
        } catch (_: IllegalArgumentException) {
            PayCycleEngine.todayIso()
        }
        return dao.skipRecurringOccurrenceAtomically(
            userId = uid,
            ruleId = cleanRuleId,
            occurrenceIsoDate = validatedDate,
            nowMs = System.currentTimeMillis()
        )
    }

    override suspend fun processDueAutoPostRules(
        todayIso: String,
        userId: String
    ): Int {
        val uid = requireUserId(userId)
        val safeToday = try {
            PayCycleEngine.requireOrNormalizeIsoDate(todayIso)
        } catch (_: IllegalArgumentException) {
            PayCycleEngine.todayIso()
        }
        val rules = dao.getRecurringRules(uid).map { it.toDomain() }
        if (rules.isEmpty()) return 0
        val loansById = dao.getLoans(uid).map { it.toDomain() }.associateBy { it.id }
        val postedKeys = dao.getTransactions(uid)
            .mapNotNull { it.recurringOccurrenceKey?.takeIf(String::isNotBlank) }
            .toSet()

        val dueOccurrences = RecurrenceScheduleEngine.findDueOccurrences(
            rules = rules,
            upToIso = safeToday,
            todayIso = safeToday,
            postedOccurrenceKeys = postedKeys,
            loansById = loansById,
            postingModeFilter = RecurrencePostingMode.AUTO_POST
        )
        var postedCount = 0
        for (occ in dueOccurrences) {
            val res = postRecurringOccurrence(
                ruleId = occ.rule.id,
                occurrenceIsoDate = occ.occurrenceIsoDate,
                amountOverride = null,
                userId = uid
            )
            if (res is RecurringPostResult.Success) {
                postedCount++
            }
        }
        return postedCount
    }

    // --- Phase 7A: Room FTS4 + Multi-Criteria Exact-Centimes Search ---

    override fun observeSearchTransactions(
        criteria: com.example.domain.TransactionSearchCriteria,
        userId: String
    ): Flow<List<TransactionItem>> {
        val uid = requireUserId(userId)
        val ftsMatch = com.example.domain.FtsQuerySanitizer.buildFtsMatchExpression(criteria.query)
        val flow = if (ftsMatch != null) {
            dao.observeSearchTransactionsWithFts(
                userId = uid,
                ftsQuery = ftsMatch,
                startIsoDate = criteria.normalizedStartIso,
                endIsoDate = criteria.normalizedEndIso,
                minAmountCentimes = criteria.minAmountCentimes,
                maxAmountCentimes = criteria.maxAmountCentimes,
                accountId = criteria.normalizedAccountId,
                envelopeKey = criteria.normalizedEnvelopeKey,
                txType = criteria.normalizedType,
                loanId = criteria.normalizedLoanId,
                loanLinkedOnly = criteria.loanLinkedOnly
            )
        } else {
            dao.observeSearchTransactionsFiltered(
                userId = uid,
                startIsoDate = criteria.normalizedStartIso,
                endIsoDate = criteria.normalizedEndIso,
                minAmountCentimes = criteria.minAmountCentimes,
                maxAmountCentimes = criteria.maxAmountCentimes,
                accountId = criteria.normalizedAccountId,
                envelopeKey = criteria.normalizedEnvelopeKey,
                txType = criteria.normalizedType,
                loanId = criteria.normalizedLoanId,
                loanLinkedOnly = criteria.loanLinkedOnly
            )
        }
        return flow.map { list -> list.map { it.toDomain() } }
    }

    override suspend fun searchTransactions(
        criteria: com.example.domain.TransactionSearchCriteria,
        userId: String
    ): List<TransactionItem> {
        val uid = requireUserId(userId)
        val ftsMatch = com.example.domain.FtsQuerySanitizer.buildFtsMatchExpression(criteria.query)
        val entities = if (ftsMatch != null) {
            dao.searchTransactionsWithFts(
                userId = uid,
                ftsQuery = ftsMatch,
                startIsoDate = criteria.normalizedStartIso,
                endIsoDate = criteria.normalizedEndIso,
                minAmountCentimes = criteria.minAmountCentimes,
                maxAmountCentimes = criteria.maxAmountCentimes,
                accountId = criteria.normalizedAccountId,
                envelopeKey = criteria.normalizedEnvelopeKey,
                txType = criteria.normalizedType,
                loanId = criteria.normalizedLoanId,
                loanLinkedOnly = criteria.loanLinkedOnly
            )
        } else {
            dao.searchTransactionsFiltered(
                userId = uid,
                startIsoDate = criteria.normalizedStartIso,
                endIsoDate = criteria.normalizedEndIso,
                minAmountCentimes = criteria.minAmountCentimes,
                maxAmountCentimes = criteria.maxAmountCentimes,
                accountId = criteria.normalizedAccountId,
                envelopeKey = criteria.normalizedEnvelopeKey,
                txType = criteria.normalizedType,
                loanId = criteria.normalizedLoanId,
                loanLinkedOnly = criteria.loanLinkedOnly
            )
        }
        return entities.map { it.toDomain() }
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
        userId: String,
        savedCredits: List<SavedCreditEntity>,
        staleSavedCreditIdsToDelete: List<String>,
        loans: List<LoanItem>,
        loanRepayments: List<LoanRepaymentItem>,
        staleLoanIdsToDelete: List<String>,
        staleLoanRepaymentIdsToDelete: List<String>,
        recurringRules: List<RecurringRuleItem>,
        staleRecurringRuleIdsToDelete: List<String>
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

        val creditEntities = savedCredits.mapIndexed { idx, credit ->
            val cleanId = credit.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "credit_restored_$idx" }
            credit.copy(id = cleanId).toLocalEntity(uid, now)
        }
        val restoredCreditIds = creditEntities.map { it.id }.toSet()

        val loanEntities = loans.mapIndexed { idx, loan ->
            val cleanId = loan.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "loan_restored_$idx" }
            loan.copy(id = cleanId).toEntity(uid, now)
        }
        val restoredLoanIds = loanEntities.map { it.id }.toSet()

        val repaymentEntities = loanRepayments.mapIndexed { idx, rep ->
            val cleanId = rep.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "repay_restored_$idx" }
            rep.copy(id = cleanId).toEntity(uid, now)
        }
        val restoredRepaymentIds = repaymentEntities.map { it.id }.toSet()

        val recurringEntities = recurringRules.mapIndexed { idx, rule ->
            val cleanId = rule.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "rec_restored_$idx" }
            rule.copy(id = cleanId).toEntity(uid, now)
        }
        val restoredRecurringIds = recurringEntities.map { it.id }.toSet()

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
        val safeStaleCredits = staleSavedCreditIdsToDelete.map { it.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") }
            .filter { it.isNotBlank() && it !in restoredCreditIds }
        val safeStaleLoans = staleLoanIdsToDelete.map { it.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") }
            .filter { it.isNotBlank() && it !in restoredLoanIds }
        val safeStaleRepayments = staleLoanRepaymentIdsToDelete.map { it.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") }
            .filter { it.isNotBlank() && it !in restoredRepaymentIds }
        val safeStaleRecurring = staleRecurringRuleIdsToDelete.map { it.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") }
            .filter { it.isNotBlank() && it !in restoredRecurringIds }

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
            staleEnvelopeIdsToDelete = safeStaleEnvelopes,
            savedCredits = creditEntities,
            staleSavedCreditIdsToDelete = safeStaleCredits,
            loans = loanEntities,
            loanRepayments = repaymentEntities,
            staleLoanIdsToDelete = safeStaleLoans,
            staleLoanRepaymentIdsToDelete = safeStaleRepayments,
            recurringRules = recurringEntities,
            staleRecurringRuleIdsToDelete = safeStaleRecurring
        )
        onChunkProgress(1, 1)

        notifyRemoteMirror("restoreFullBackupAtomically") {
            it.onBackupRestored(
                userId = uid,
                restoredTxCount = txEntities.size,
                restoredItemCount = itemEntities.size,
                restoredAccountCount = accountEntities.size,
                restoredEnvelopeCount = envelopeEntities.size,
                restoredCreditCount = creditEntities.size
            )
        }
    }

    companion object {
        private const val TAG = "RoomMasroufiRepo"
    }
}
