package com.example.data

import android.content.Context
import com.example.R
import com.example.domain.PayCycleEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.masroufi.core.Money
import com.masroufi.credit.data.SavedCreditEntity
import java.math.BigDecimal
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Explicit, zero-reflection Firestore [DocumentSnapshot] mappers for domain models carrying
 * `@JvmInline value class Money`. Converts legacy Firestore DH numbers via [Money.fromLegacyDoubleDh]
 * (`BigDecimal.valueOf`) and never uses `doc.toObject(...)` on classes with inline value fields.
 */
object FirestoreSnapshotMappers {

    fun DocumentSnapshot.readMoneyDh(field: String, default: Money = Money.ZERO): Money {
        val raw = get(field) ?: return default
        return when (raw) {
            is BigDecimal -> runCatching { Money.fromMajorUnits(raw) }.getOrDefault(default)
            is Number -> {
                val d = raw.toDouble()
                if (d.isFinite()) runCatching { Money.fromLegacyDoubleDh(d) }.getOrDefault(default) else default
            }
            is String -> Money.fromDhString(raw) ?: default
            else -> default
        }
    }

    fun toUserProfile(doc: DocumentSnapshot, fallbackUserId: String): UserProfile {
        val isDark = doc.getBoolean("isDarkTheme")
            ?: doc.getBoolean("darkTheme")
            ?: false
        return UserProfile(
            id = doc.id,
            userId = doc.getString("userId") ?: fallbackUserId,
            baseBudget = doc.readMoneyDh("baseBudget", Money.fromDh(6400)),
            payCycleDay = (doc.getLong("payCycleDay") ?: 1L).toInt().coerceIn(1, 28),
            rolloverEnabled = doc.getBoolean("rolloverEnabled") ?: true,
            language = doc.getString("language") ?: "fr",
            isDarkTheme = isDark,
            seeded = doc.getBoolean("seeded") ?: true,
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    fun toAccountItem(doc: DocumentSnapshot, fallbackUserId: String = ""): AccountItem {
        return AccountItem(
            id = doc.id,
            userId = doc.getString("userId") ?: fallbackUserId,
            name = doc.getString("name") ?: "",
            kind = doc.getString("kind") ?: "cash",
            initialBalance = doc.readMoneyDh("initialBalance", Money.ZERO),
            colorHex = doc.getString("colorHex") ?: "#10B981",
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    fun toTransactionItem(doc: DocumentSnapshot, fallbackUserId: String = ""): TransactionItem {
        val rawCat = doc.getString("category") ?: "autre"
        val rawEnv = doc.getString("envelopeKey") ?: "autre"
        val normCat = DefaultCategoryResolver.normalizeStoredCategoryValue(rawCat, rawEnv)
        val normEnv = DefaultCategoryResolver.canonicalDefaultId(rawEnv)
            ?: DefaultCategoryResolver.canonicalDefaultId(rawCat)
            ?: rawEnv
        return TransactionItem(
            id = doc.id,
            userId = doc.getString("userId") ?: fallbackUserId,
            type = doc.getString("type") ?: "expense",
            amount = doc.readMoneyDh("amount", Money.ZERO),
            category = normCat,
            envelopeKey = normEnv,
            accountId = doc.getString("accountId") ?: "acc_cash",
            toAccountId = doc.getString("toAccountId") ?: "",
            desc = doc.getString("desc") ?: "",
            isoDate = doc.getString("isoDate") ?: "",
            hourOfDay = (doc.getLong("hourOfDay") ?: -1L).toInt(),
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    fun toFinancialItem(doc: DocumentSnapshot, fallbackUserId: String = ""): FinancialItem {
        val itemType = doc.getString("itemType") ?: "goal"
        val isDaret = itemType == "daret"
        val resolvedDaretMembers = if (isDaret) {
            val explicit = doc.getLong("daretMembers")?.toInt()
            val legacy = (doc.get("secondaryAmount") as? Number)?.toDouble()
                ?.takeIf { it.isFinite() }?.roundToInt()
            (explicit ?: legacy ?: 1).coerceAtLeast(1)
        } else {
            null
        }
        val secondaryMoney = if (isDaret) Money.ZERO else doc.readMoneyDh("secondaryAmount", Money.ZERO)
        return FinancialItem(
            id = doc.id,
            userId = doc.getString("userId") ?: fallbackUserId,
            itemType = itemType,
            name = doc.getString("name") ?: "",
            subtitle = doc.getString("subtitle") ?: "",
            amount = doc.readMoneyDh("amount", Money.ZERO),
            secondaryAmount = secondaryMoney,
            daretMembers = resolvedDaretMembers,
            dayOrTurn = (doc.getLong("dayOrTurn") ?: 1L).toInt(),
            extraInt = (doc.getLong("extraInt") ?: 0L).toInt(),
            dateOrCycle = doc.getString("dateOrCycle") ?: "",
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    fun toSavedCreditEntity(doc: DocumentSnapshot, fallbackUserId: String = ""): SavedCreditEntity {
        return SavedCreditEntity(
            id = doc.id,
            userId = doc.getString("userId") ?: fallbackUserId,
            type = doc.getString("type") ?: "CONSOMMATION",
            loanAmount = doc.readMoneyDh("loanAmount", Money.ZERO),
            downPayment = doc.readMoneyDh("downPayment", Money.ZERO),
            financedPrincipal = doc.readMoneyDh("financedPrincipal", Money.ZERO),
            durationMonths = (doc.getLong("durationMonths") ?: 60L).toInt(),
            deferredMonths = (doc.getLong("deferredMonths") ?: 0L).toInt(),
            annualInterestRateHt = doc.getDouble("annualInterestRateHt") ?: 7.5,
            applyInterestTva = doc.getBoolean("applyInterestTva") ?: true,
            insuranceRate = doc.getDouble("insuranceRate") ?: 0.0,
            monthlyPayment = doc.readMoneyDh("monthlyPayment", Money.ZERO),
            totalCost = doc.readMoneyDh("totalCost", Money.ZERO),
            debtToIncomeRatio = doc.getDouble("debtToIncomeRatio") ?: 0.0,
            maxAllowedDti = doc.getDouble("maxAllowedDti") ?: 45.0,
            linkToMonthlyExpenses = doc.getBoolean("linkToMonthlyExpenses") ?: false,
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }
}

class MasroufiRepository(private val db: FirebaseFirestore) : MasroufiDataSource {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val LOCAL_DEVICE_USER_ID = "local_device_user"
    }

    private val auth = Firebase.auth

    override fun requireUserId(explicitUserId: String?): String {
        return explicitUserId?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.uid
            ?: LOCAL_DEVICE_USER_ID
    }

    override fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        FirestoreSnapshotMappers.toUserProfile(snapshot, userId)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
                    throw error
                }
        )
    }

    override fun observeTransactions(
        userId: String,
        minIsoDate: String?
    ): Flow<List<TransactionItem>> = flow {
        val colRef = db.collection("users").document(userId).collection("transactions")
        val cleanMinIso = minIsoDate?.trim()?.takeIf { it.isNotBlank() && PayCycleEngine.isValidIsoDate(it) }
        val query = if (cleanMinIso != null) {
            colRef.whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("isoDate", cleanMinIso)
        } else {
            colRef.whereEqualTo("userId", userId)
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.documents.map { doc ->
                        FirestoreSnapshotMappers.toTransactionItem(doc, userId)
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                    throw error
                }
        )
    }

    override fun observeAccounts(userId: String): Flow<List<AccountItem>> = flow {
        val colRef = db.collection("users").document(userId).collection("accounts")
        emitAll(
            colRef.whereEqualTo("userId", userId).snapshots()
                .map { snapshot ->
                    snapshot.documents.map { doc ->
                        FirestoreSnapshotMappers.toAccountItem(doc, userId)
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                    throw error
                }
        )
    }

    override fun observeEnvelopes(userId: String): Flow<List<EnvelopeConfig>> = flow {
        val colRef = db.collection("users").document(userId).collection("envelopes")
        emitAll(
            colRef.whereEqualTo("userId", userId).snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        val hasCapVal = doc.getBoolean("hasCap") ?: true
                        EnvelopeConfig(
                            id = doc.id,
                            userId = doc.getString("userId") ?: userId,
                            key = doc.getString("key") ?: "",
                            nameFr = doc.getString("nameFr") ?: "",
                            nameAr = doc.getString("nameAr") ?: "",
                            nameDarija = doc.getString("nameDarija") ?: "",
                            nameEn = doc.getString("nameEn") ?: "",
                            percentage = doc.getDouble("percentage") ?: 0.0,
                            hasCap = hasCapVal,
                            colorHex = doc.getString("colorHex") ?: "#10B981",
                            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                            updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ).migratedStorageCopy()
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                    throw error
                }
        )
    }

    override fun observeFinancialItems(userId: String): Flow<List<FinancialItem>> = flow {
        val colRef = db.collection("users").document(userId).collection("items")
        emitAll(
            colRef.whereEqualTo("userId", userId).snapshots()
                .map { snapshot ->
                    snapshot.documents.map { doc ->
                        FirestoreSnapshotMappers.toFinancialItem(doc, userId)
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                    throw error
                }
        )
    }

    private fun buildProfilePayload(uid: String, profile: UserProfile): Map<String, Any> {
        return mapOf(
            "userId" to uid,
            "baseBudget" to profile.baseBudget.coerceIn(Money.ZERO, Money.MAX_STORABLE).toBigDecimalDh().toDouble(),
            "payCycleDay" to profile.payCycleDay.coerceIn(1, 28),
            "rolloverEnabled" to profile.rolloverEnabled,
            "language" to (if (profile.language in listOf("fr", "ar", "darija", "en")) profile.language else "fr"),
            "isDarkTheme" to profile.isDarkTheme,
            "seeded" to true,
            "createdAt" to (profile.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun buildTransactionPayload(uid: String, tx: TransactionItem): Map<String, Any> {
        val safeType = if (tx.type in listOf("expense", "income", "transfer")) tx.type else "expense"
        val validatedDate = PayCycleEngine.requireOrNormalizeIsoDate(tx.isoDate)
        val safeAccountId = tx.accountId.ifBlank { "acc_cash" }.take(80)
        val safeToAccountId = when (safeType) {
            "transfer" -> {
                val candidate = tx.toAccountId.trim().take(80)
                if (candidate.isNotBlank() && candidate != safeAccountId) {
                    candidate
                } else {
                    if (safeAccountId == "acc_cash") "acc_bank" else "acc_cash"
                }
            }
            else -> ""
        }
        val normalizedCat = DefaultCategoryResolver.normalizeStoredCategoryValue(tx.category, tx.envelopeKey).take(120)
        val normalizedEnvKey = (
            DefaultCategoryResolver.canonicalDefaultId(tx.envelopeKey)
                ?: DefaultCategoryResolver.canonicalDefaultId(tx.category)
                ?: tx.envelopeKey.ifBlank { "autre" }
        ).take(60)
        return mapOf(
            "userId" to uid,
            "type" to safeType,
            "amount" to tx.amount.coerceIn(Money.ONE_CENTIME, Money.MAX_STORABLE).toBigDecimalDh().toDouble(),
            "category" to normalizedCat,
            "envelopeKey" to normalizedEnvKey,
            "accountId" to safeAccountId,
            "toAccountId" to safeToAccountId,
            "desc" to tx.desc.take(250),
            "isoDate" to validatedDate.take(20),
            "createdAt" to (tx.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun buildAccountPayload(uid: String, account: AccountItem): Map<String, Any> {
        val safeKind = if (account.kind in listOf("cash", "bank", "wallet")) account.kind else "cash"
        return mapOf(
            "userId" to uid,
            "name" to account.name.ifBlank { "Compte" }.take(80),
            "kind" to safeKind,
            "initialBalance" to account.initialBalance.coerceIn(Money.MIN_STORABLE, Money.MAX_STORABLE).toBigDecimalDh().toDouble(),
            "colorHex" to account.colorHex.ifBlank { "#10B981" }.take(20),
            "createdAt" to (account.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun buildEnvelopePayload(uid: String, envelope: EnvelopeConfig): Map<String, Any> {
        val migrated = envelope.migratedStorageCopy()
        val safeName = migrated.nameFr.ifBlank { migrated.key.ifBlank { "custom" } }.take(100)
        return mapOf(
            "userId" to uid,
            "key" to migrated.key.ifBlank { "custom" }.take(60),
            "nameFr" to safeName,
            "nameAr" to migrated.nameAr.ifBlank { safeName }.take(100),
            "nameDarija" to migrated.nameDarija.ifBlank { safeName }.take(100),
            "nameEn" to migrated.nameEn.ifBlank { safeName }.take(100),
            "percentage" to migrated.percentage.coerceIn(0.0, 100.0),
            "hasCap" to migrated.hasCap,
            "colorHex" to migrated.colorHex.ifBlank { "#10B981" }.take(20),
            "createdAt" to (migrated.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun buildFinancialItemPayload(uid: String, item: FinancialItem): Map<String, Any> {
        val allowedTypes = listOf("goal", "bill", "debt", "daret", "seasonal", "preset", "custom_prefs")
        val safeType = if (item.itemType in allowedTypes) item.itemType else "goal"
        val secondaryValue = if (safeType == "daret") {
            item.resolvedDaretMembers().toDouble()
        } else {
            item.secondaryAmount.coerceIn(Money.ZERO, Money.MAX_STORABLE).toBigDecimalDh().toDouble()
        }
        return mapOf(
            "userId" to uid,
            "itemType" to safeType,
            "name" to item.name.ifBlank { "Élément" }.take(120),
            "subtitle" to item.subtitle.take(250),
            "amount" to item.amount.coerceIn(Money.ZERO, Money.MAX_STORABLE).toBigDecimalDh().toDouble(),
            "secondaryAmount" to secondaryValue,
            "dayOrTurn" to item.dayOrTurn.coerceIn(0, 366),
            "extraInt" to item.extraInt.coerceIn(0, 366),
            "dateOrCycle" to item.dateOrCycle.take(250),
            "createdAt" to (item.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    override suspend fun saveProfile(profile: UserProfile, userId: String) {
        val uid = requireUserId(userId)
        val docRef = db.collection("users").document(uid)
        try {
            docRef.set(buildProfilePayload(uid, profile)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    override suspend fun upsertTransaction(tx: TransactionItem, userId: String): String {
        val uid = requireUserId(userId)
        val colRef = db.collection("users").document(uid).collection("transactions")
        val cleanId = tx.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val docRef = if (cleanId.isNotBlank()) colRef.document(cleanId) else colRef.document("tx_${System.currentTimeMillis()}")
        try {
            docRef.set(buildTransactionPayload(uid, tx)).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    override suspend fun getTransactionById(targetUserId: String, txId: String): TransactionItem? {
        val docRef = db.collection("users").document(targetUserId).collection("transactions").document(txId)
        try {
            val snap = docRef.get().await()
            return if (snap.exists()) FirestoreSnapshotMappers.toTransactionItem(snap, targetUserId) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    override suspend fun deleteTransaction(txId: String, userId: String) {
        if (txId.isBlank()) return
        val uid = requireUserId(userId)
        val docRef = db.collection("users").document(uid).collection("transactions").document(txId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    override suspend fun upsertAccount(account: AccountItem, userId: String): String {
        val uid = requireUserId(userId)
        val colRef = db.collection("users").document(uid).collection("accounts")
        val cleanId = account.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val docRef = if (cleanId.isNotBlank()) colRef.document(cleanId) else colRef.document("acc_${System.currentTimeMillis()}")
        try {
            docRef.set(buildAccountPayload(uid, account)).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    override suspend fun deleteAccount(accId: String, userId: String) {
        if (accId.isBlank()) return
        val uid = requireUserId(userId)
        val docRef = db.collection("users").document(uid).collection("accounts").document(accId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    override suspend fun upsertEnvelope(envelope: EnvelopeConfig, userId: String): String {
        val uid = requireUserId(userId)
        val colRef = db.collection("users").document(uid).collection("envelopes")
        val cleanId = envelope.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val docRef = if (cleanId.isNotBlank()) colRef.document(cleanId) else colRef.document("env_${System.currentTimeMillis()}")
        try {
            docRef.set(buildEnvelopePayload(uid, envelope)).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    /**
     * Atomic batch update for multiple envelopes (e.g. Auto-Balance to 100%).
     */
    override suspend fun upsertEnvelopesAtomically(envelopes: List<EnvelopeConfig>, userId: String) {
        if (envelopes.isEmpty()) return
        val uid = requireUserId(userId)
        val colRef = db.collection("users").document(uid).collection("envelopes")
        try {
            val batch = db.batch()
            envelopes.forEachIndexed { idx, env ->
                val cleanId = env.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                val docRef = if (cleanId.isNotBlank()) colRef.document(cleanId) else colRef.document("env_${System.currentTimeMillis()}_$idx")
                batch.set(docRef, buildEnvelopePayload(uid, env))
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, colRef.path)
            throw e
        }
    }

    override suspend fun deleteEnvelope(envId: String, userId: String) {
        if (envId.isBlank()) return
        val uid = requireUserId(userId)
        val docRef = db.collection("users").document(uid).collection("envelopes").document(envId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    override suspend fun upsertFinancialItem(item: FinancialItem, userId: String): String {
        val uid = requireUserId(userId)
        val colRef = db.collection("users").document(uid).collection("items")
        val cleanId = item.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val docRef = if (cleanId.isNotBlank()) colRef.document(cleanId) else colRef.document("item_${System.currentTimeMillis()}")
        try {
            docRef.set(buildFinancialItemPayload(uid, item)).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    /**
     * Atomic Firestore batch write for financial operations that both update a FinancialItem
     * (bill, goal, seasonal planner, Daret round, or debt) and record the corresponding TransactionItem.
     * Prevents partial writes if connectivity drops mid-operation.
     */
    override suspend fun upsertItemAndTransactionAtomically(
        item: FinancialItem,
        tx: TransactionItem,
        userId: String
    ) {
        val uid = requireUserId(userId)
        val userRef = db.collection("users").document(uid)
        val itemCleanId = item.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val itemRef = if (itemCleanId.isNotBlank()) {
            userRef.collection("items").document(itemCleanId)
        } else {
            userRef.collection("items").document("item_${System.currentTimeMillis()}")
        }
        val txCleanId = tx.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val txRef = if (txCleanId.isNotBlank()) {
            userRef.collection("transactions").document(txCleanId)
        } else {
            userRef.collection("transactions").document("tx_${System.currentTimeMillis()}")
        }
        try {
            val batch = db.batch()
            batch.set(itemRef, buildFinancialItemPayload(uid, item))
            batch.set(txRef, buildTransactionPayload(uid, tx))
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            throw e
        }
    }

    override suspend fun deleteFinancialItem(itemId: String, userId: String) {
        if (itemId.isBlank()) return
        val uid = requireUserId(userId)
        val docRef = db.collection("users").document(uid).collection("items").document(itemId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private fun defaultStandardEnvelopes(uid: String): List<EnvelopeConfig> = listOf(
        EnvelopeConfig(
            id = "env_transport",
            userId = uid,
            key = "transport",
            nameFr = "transport",
            nameAr = "transport",
            nameDarija = "transport",
            nameEn = "transport",
            percentage = 35.0,
            hasCap = true,
            colorHex = "#0EA5E9"
        ),
        EnvelopeConfig(
            id = "env_charges",
            userId = uid,
            key = "charges",
            nameFr = "charges",
            nameAr = "charges",
            nameDarija = "charges",
            nameEn = "charges",
            percentage = 26.5,
            hasCap = true,
            colorHex = "#7C3AED"
        ),
        EnvelopeConfig(
            id = "env_cafe",
            userId = uid,
            key = "cafe",
            nameFr = "cafe",
            nameAr = "cafe",
            nameDarija = "cafe",
            nameEn = "cafe",
            percentage = 18.5,
            hasCap = true,
            colorHex = "#D97706"
        ),
        EnvelopeConfig(
            id = "env_epargne",
            userId = uid,
            key = "epargne",
            nameFr = "epargne",
            nameAr = "epargne",
            nameDarija = "epargne",
            nameEn = "epargne",
            percentage = 20.0,
            hasCap = true,
            colorHex = "#059669"
        ),
        EnvelopeConfig(
            id = "env_autre",
            userId = uid,
            key = "autre",
            nameFr = "autre",
            nameAr = "autre",
            nameDarija = "autre",
            nameEn = "autre",
            percentage = 0.0,
            hasCap = false,
            colorHex = "#E11D48"
        )
    )

    private fun defaultQuickPresets(): List<FinancialItem> = listOf(
        FinancialItem(id = "pre_cafe", itemType = "preset", name = "Café & Garo", subtitle = "cafe", amount = Money.fromDh(20), secondaryAmount = Money.ZERO, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_cash"),
        FinancialItem(id = "pre_taxi", itemType = "preset", name = "Petit Taxi", subtitle = "transport", amount = Money.fromDh(15), secondaryAmount = Money.ZERO, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_cash"),
        FinancialItem(id = "pre_bread", itemType = "preset", name = "Khobz & Lben", subtitle = "charges", amount = Money.fromDh(10), secondaryAmount = Money.ZERO, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_cash"),
        FinancialItem(id = "pre_oncf", itemType = "preset", name = "Ticket ONCF", subtitle = "transport", amount = Money.fromDh(50), secondaryAmount = Money.ZERO, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_bank")
    )

    /**
     * Clean first-time setup: initializes a new user with 0 DH balances on the 3 core accounts,
     * standard envelopes, and 1-tap quick-add presets — NO fake transactions or demo balances.
     * Users can optionally load Demo Data at any time with 1 tap.
     */
    override suspend fun ensureSeeded(userId: String) {
        val uid = requireUserId(userId)
        val profileRef = db.collection("users").document(uid)
        try {
            val snap = profileRef.get().await()
            if (snap.exists() && snap.getBoolean("seeded") == true) {
                return
            }
            val existingCreatedAt = if (snap.exists()) {
                snap.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null

            val batch = db.batch()
            val newProfile = UserProfile(
                id = uid,
                userId = uid,
                baseBudget = Money.fromDh(6400),
                payCycleDay = 1,
                rolloverEnabled = true,
                language = "fr",
                isDarkTheme = false,
                seeded = true,
                createdAt = existingCreatedAt
            )
            batch.set(profileRef, buildProfilePayload(uid, newProfile))

            // Clean 0 DH Accounts (Cash, Bank, Mobile Wallet)
            val cleanAccounts = listOf(
                AccountItem(id = "acc_cash", userId = uid, name = "Espèces (Cash)", kind = "cash", initialBalance = Money.ZERO, colorHex = "#10B981"),
                AccountItem(id = "acc_bank", userId = uid, name = "Compte Bancaire (CIH/Attijari)", kind = "bank", initialBalance = Money.ZERO, colorHex = "#38BDF8"),
                AccountItem(id = "acc_wallet", userId = uid, name = "Portefeuille Mobile", kind = "wallet", initialBalance = Money.ZERO, colorHex = "#FBBF24")
            )
            for (acc in cleanAccounts) {
                val ref = profileRef.collection("accounts").document(acc.id)
                batch.set(ref, buildAccountPayload(uid, acc))
            }

            // Standard Envelopes
            for (env in defaultStandardEnvelopes(uid)) {
                val ref = profileRef.collection("envelopes").document(env.id)
                batch.set(ref, buildEnvelopePayload(uid, env))
            }

            // Helpful 1-Tap Quick Presets (no fake transactions)
            for (preset in defaultQuickPresets()) {
                val ref = profileRef.collection("items").document(preset.id)
                batch.set(ref, buildFinancialItemPayload(uid, preset))
            }

            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, profileRef.path)
            throw e
        }
    }

    /**
     * Optional Demo Mode: loads realistic Moroccan sample accounts, transactions, bills,
     * savings goals, Daret tontine, debts, and seasonal planners in a single atomic batch.
     */
    override suspend fun loadDemoData(
        existingAccounts: List<AccountItem>,
        existingEnvelopes: List<EnvelopeConfig>,
        existingItems: List<FinancialItem>,
        existingTransactions: List<TransactionItem>,
        userId: String
    ) {
        val uid = requireUserId(userId)
        val profileRef = db.collection("users").document(uid)
        try {
            val accCreatedAt = existingAccounts.associate { it.id to it.createdAt }
            val envCreatedAt = existingEnvelopes.associate { it.id to it.createdAt }
            val itemCreatedAt = existingItems.associate { it.id to it.createdAt }
            val txCreatedAt = existingTransactions.associate { it.id to it.createdAt }

            val batch = db.batch()

            val demoAccounts = listOf(
                AccountItem(id = "acc_cash", userId = uid, name = "Espèces (Cash)", kind = "cash", initialBalance = Money.fromDh(1850), colorHex = "#10B981", createdAt = accCreatedAt["acc_cash"]),
                AccountItem(id = "acc_bank", userId = uid, name = "Compte Bancaire (CIH/Attijari)", kind = "bank", initialBalance = Money.fromDh(5200), colorHex = "#38BDF8", createdAt = accCreatedAt["acc_bank"]),
                AccountItem(id = "acc_wallet", userId = uid, name = "Portefeuille Mobile", kind = "wallet", initialBalance = Money.fromDh(650), colorHex = "#FBBF24", createdAt = accCreatedAt["acc_wallet"])
            )
            for (acc in demoAccounts) {
                val ref = profileRef.collection("accounts").document(acc.id)
                batch.set(ref, buildAccountPayload(uid, acc))
            }

            for (env in defaultStandardEnvelopes(uid)) {
                val ref = profileRef.collection("envelopes").document(env.id)
                batch.set(ref, buildEnvelopePayload(uid, env.copy(createdAt = envCreatedAt[env.id])))
            }

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
            )
            for (tx in demoTxs) {
                val ref = profileRef.collection("transactions").document(tx.id)
                batch.set(ref, buildTransactionPayload(uid, tx.copy(createdAt = txCreatedAt[tx.id])))
            }

            val demoItems = defaultQuickPresets() + listOf(
                FinancialItem(id = "bill_fibre", itemType = "bill", name = "Fibre Optique & Mobile", subtitle = "charges", amount = Money.fromDh(250), secondaryAmount = Money.ZERO, dayOrTurn = 5, extraInt = 0, dateOrCycle = ""),
                FinancialItem(id = "bill_lydec", itemType = "bill", name = "Eau & Électricité (Lydec/Redal)", subtitle = "charges", amount = Money.fromDh(320), secondaryAmount = Money.ZERO, dayOrTurn = 18, extraInt = 0, dateOrCycle = ""),
                FinancialItem(id = "goal_urgence", itemType = "goal", name = "Fonds d'urgence (Tawfir)", subtitle = "epargne", amount = Money.fromDh(10000), secondaryAmount = Money.fromDh(4200), dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-06-30"),
                FinancialItem(id = "debt_mama", itemType = "debt", name = "Crédit Mama", subtitle = "Mama", amount = Money.fromDh(500), secondaryAmount = Money.ZERO, dayOrTurn = 1, extraInt = 0, dateOrCycle = "lent"),
                FinancialItem(id = "debt_inaam", itemType = "debt", name = "Crédit Inaam", subtitle = "Inaam", amount = Money.fromDh(1000), secondaryAmount = Money.fromDh(200), dayOrTurn = 1, extraInt = 0, dateOrCycle = "lent"),
                FinancialItem(id = "daret_famille", itemType = "daret", name = "Daret Collègues", subtitle = "10 membres", amount = Money.fromDh(1000), secondaryAmount = Money.ZERO, daretMembers = 10, dayOrTurn = 4, extraInt = 3, dateOrCycle = "2027-02-01"),
                FinancialItem(id = "season_ramadan", itemType = "seasonal", name = "Ramadan & Ftous", subtitle = "ramadan", amount = Money.fromDh(3000), secondaryAmount = Money.fromDh(900), dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-02-17"),
                FinancialItem(id = "season_eid", itemType = "seasonal", name = "Aïd al-Adha (Kebch)", subtitle = "eid", amount = Money.fromDh(4500), secondaryAmount = Money.fromDh(1200), dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-05-16"),
                FinancialItem(id = "season_vignette", itemType = "seasonal", name = "Vignette & Assurance Auto", subtitle = "vignette", amount = Money.fromDh(3800), secondaryAmount = Money.fromDh(1500), dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-01-31")
            )
            for (item in demoItems) {
                val ref = profileRef.collection("items").document(item.id)
                batch.set(ref, buildFinancialItemPayload(uid, item.copy(createdAt = itemCreatedAt[item.id])))
            }

            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, profileRef.path)
            throw e
        }
    }

    /**
     * Resets the user's account to a clean state (0 DH balances, zero transactions, zero demo debts/goals).
     */
    override suspend fun resetToCleanAccount(
        existingTxIds: List<String>,
        existingItemIdsToClear: List<String>,
        existingAccounts: List<AccountItem>,
        userId: String
    ) {
        val uid = requireUserId(userId)
        val profileRef = db.collection("users").document(uid)
        try {
            val accCreatedAt = existingAccounts.associate { it.id to it.createdAt }
            val refsToDelete = mutableListOf<DocumentReference>()
            existingTxIds.forEach { id ->
                if (id.isNotBlank()) refsToDelete.add(profileRef.collection("transactions").document(id))
            }
            existingItemIdsToClear.forEach { id ->
                if (id.isNotBlank()) refsToDelete.add(profileRef.collection("items").document(id))
            }
            existingAccounts.forEach { acc ->
                val id = acc.id
                if (id !in listOf("acc_cash", "acc_bank", "acc_wallet") && id.isNotBlank()) {
                    refsToDelete.add(profileRef.collection("accounts").document(id))
                }
            }

            for (chunk in refsToDelete.chunked(400)) {
                val delBatch = db.batch()
                chunk.forEach { delBatch.delete(it) }
                delBatch.commit().await()
            }

            val cleanBatch = db.batch()
            val cleanAccounts = listOf(
                AccountItem(id = "acc_cash", userId = uid, name = "Espèces (Cash)", kind = "cash", initialBalance = Money.ZERO, colorHex = "#10B981", createdAt = accCreatedAt["acc_cash"]),
                AccountItem(id = "acc_bank", userId = uid, name = "Compte Bancaire (CIH/Attijari)", kind = "bank", initialBalance = Money.ZERO, colorHex = "#38BDF8", createdAt = accCreatedAt["acc_bank"]),
                AccountItem(id = "acc_wallet", userId = uid, name = "Portefeuille Mobile", kind = "wallet", initialBalance = Money.ZERO, colorHex = "#FBBF24", createdAt = accCreatedAt["acc_wallet"])
            )
            for (acc in cleanAccounts) {
                cleanBatch.set(profileRef.collection("accounts").document(acc.id), buildAccountPayload(uid, acc))
            }
            cleanBatch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, profileRef.path)
            throw e
        }
    }

    private sealed interface BatchOp {
        data class SetOp(val ref: DocumentReference, val payload: Map<String, Any>) : BatchOp
        data class DeleteOp(val ref: DocumentReference) : BatchOp
    }

    /**
     * Two-phase backup restore (SEC-07):
     * 1. Pre-builds and validates all Firestore payloads in memory before any network write.
     * 2. Combines set operations and stale-record delete operations into a single unified list
     *    of batch operations so that <= 400 total operations execute in a single atomic WriteBatch.
     */
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
        savedCredits: List<com.masroufi.credit.data.SavedCreditEntity>,
        staleSavedCreditIdsToDelete: List<String>,
        loans: List<com.example.domain.LoanItem>,
        loanRepayments: List<com.example.domain.LoanRepaymentItem>,
        staleLoanIdsToDelete: List<String>,
        staleLoanRepaymentIdsToDelete: List<String>,
        recurringRules: List<com.example.domain.RecurringRuleItem>,
        staleRecurringRuleIdsToDelete: List<String>
    ) {
        val uid = requireUserId(userId)
        val userRef = db.collection("users").document(uid)
        try {
            // Phase 1: Pre-build every set & delete operation in memory before touching Firestore
            val allOperations = mutableListOf<BatchOp>()

            if (profile != null) {
                allOperations.add(BatchOp.SetOp(userRef, buildProfilePayload(uid, profile)))
            }
            if (customPrefsItem != null) {
                val ref = userRef.collection("items").document("custom_prefs")
                allOperations.add(
                    BatchOp.SetOp(ref, buildFinancialItemPayload(uid, customPrefsItem.copy(id = "custom_prefs")))
                )
            }

            val restoredAccountIds = mutableSetOf<String>()
            accounts.forEachIndexed { idx, acc ->
                val cleanId = acc.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "acc_restored_$idx" }
                restoredAccountIds.add(cleanId)
                val ref = userRef.collection("accounts").document(cleanId)
                allOperations.add(BatchOp.SetOp(ref, buildAccountPayload(uid, acc)))
            }

            val restoredEnvelopeIds = mutableSetOf<String>()
            envelopes.forEachIndexed { idx, env ->
                val cleanId = env.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "env_restored_$idx" }
                restoredEnvelopeIds.add(cleanId)
                val ref = userRef.collection("envelopes").document(cleanId)
                allOperations.add(BatchOp.SetOp(ref, buildEnvelopePayload(uid, env)))
            }

            val restoredItemIds = mutableSetOf<String>()
            if (customPrefsItem != null) restoredItemIds.add("custom_prefs")
            financialItems.forEachIndexed { idx, item ->
                val cleanId = item.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "item_restored_$idx" }
                restoredItemIds.add(cleanId)
                val ref = userRef.collection("items").document(cleanId)
                allOperations.add(BatchOp.SetOp(ref, buildFinancialItemPayload(uid, item)))
            }

            val restoredTxIds = mutableSetOf<String>()
            transactions.forEachIndexed { idx, tx ->
                val cleanId = tx.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "tx_restored_$idx" }
                restoredTxIds.add(cleanId)
                val ref = userRef.collection("transactions").document(cleanId)
                allOperations.add(BatchOp.SetOp(ref, buildTransactionPayload(uid, tx)))
            }

            // Collect stale documents to remove into the same unified batch list
            staleTxIdsToDelete.forEach { oldId ->
                val cleanOld = oldId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                if (cleanOld.isNotBlank() && cleanOld !in restoredTxIds) {
                    allOperations.add(BatchOp.DeleteOp(userRef.collection("transactions").document(cleanOld)))
                }
            }
            staleItemIdsToDelete.forEach { oldId ->
                val cleanOld = oldId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                if (cleanOld.isNotBlank() && cleanOld !in restoredItemIds) {
                    allOperations.add(BatchOp.DeleteOp(userRef.collection("items").document(cleanOld)))
                }
            }
            staleAccountIdsToDelete.forEach { oldId ->
                val cleanOld = oldId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                if (cleanOld.isNotBlank() && cleanOld !in restoredAccountIds && accounts.isNotEmpty()) {
                    allOperations.add(BatchOp.DeleteOp(userRef.collection("accounts").document(cleanOld)))
                }
            }
            staleEnvelopeIdsToDelete.forEach { oldId ->
                val cleanOld = oldId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                if (cleanOld.isNotBlank() && cleanOld !in restoredEnvelopeIds && envelopes.isNotEmpty()) {
                    allOperations.add(BatchOp.DeleteOp(userRef.collection("envelopes").document(cleanOld)))
                }
            }

            // Phase 2: Execute unified bounded batches (max 400 ops per batch)
            val opChunks = allOperations.chunked(400)
            val totalChunks = opChunks.size.coerceAtLeast(1)
            var completed = 0

            for (chunk in opChunks) {
                val batch = db.batch()
                for (op in chunk) {
                    when (op) {
                        is BatchOp.SetOp -> batch.set(op.ref, op.payload)
                        is BatchOp.DeleteOp -> batch.delete(op.ref)
                    }
                }
                batch.commit().await()
                completed++
                onChunkProgress(completed, totalChunks)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            throw e
        }
    }
}
