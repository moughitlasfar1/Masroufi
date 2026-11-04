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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MasroufiRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeProfile(userId: String = requireUserId()): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        val isDark = snapshot.getBoolean("isDarkTheme")
                            ?: snapshot.getBoolean("darkTheme")
                            ?: true
                        UserProfile(
                            id = snapshot.id,
                            userId = snapshot.getString("userId") ?: userId,
                            baseBudget = snapshot.getDouble("baseBudget") ?: 6400.0,
                            payCycleDay = (snapshot.getLong("payCycleDay") ?: 1L).toInt().coerceIn(1, 28),
                            rolloverEnabled = snapshot.getBoolean("rolloverEnabled") ?: true,
                            language = snapshot.getString("language") ?: "fr",
                            isDarkTheme = isDark,
                            seeded = snapshot.getBoolean("seeded") ?: true,
                            createdAt = snapshot.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                            updatedAt = snapshot.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        )
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

    fun observeTransactions(userId: String = requireUserId()): Flow<List<TransactionItem>> = flow {
        val colRef = db.collection("users").document(userId).collection("transactions")
        emitAll(
            colRef.whereEqualTo("userId", userId).snapshots()
                .map { snapshot ->
                    snapshot.toObjects(TransactionItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                    throw error
                }
        )
    }

    fun observeAccounts(userId: String = requireUserId()): Flow<List<AccountItem>> = flow {
        val colRef = db.collection("users").document(userId).collection("accounts")
        emitAll(
            colRef.whereEqualTo("userId", userId).snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AccountItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                    throw error
                }
        )
    }

    fun observeEnvelopes(userId: String = requireUserId()): Flow<List<EnvelopeConfig>> = flow {
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
                        )
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                    throw error
                }
        )
    }

    fun observeFinancialItems(userId: String = requireUserId()): Flow<List<FinancialItem>> = flow {
        val colRef = db.collection("users").document(userId).collection("items")
        emitAll(
            colRef.whereEqualTo("userId", userId).snapshots()
                .map { snapshot ->
                    snapshot.toObjects(FinancialItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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
            "baseBudget" to profile.baseBudget.coerceIn(0.0, 100000000.0),
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
        return mapOf(
            "userId" to uid,
            "type" to safeType,
            "amount" to tx.amount.coerceIn(0.01, 100000000.0),
            "category" to tx.category.ifBlank { "Général" }.take(120),
            "envelopeKey" to tx.envelopeKey.ifBlank { "autre" }.take(60),
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
            "initialBalance" to account.initialBalance.coerceIn(-100000000.0, 100000000.0),
            "colorHex" to account.colorHex.ifBlank { "#10B981" }.take(20),
            "createdAt" to (account.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun buildEnvelopePayload(uid: String, envelope: EnvelopeConfig): Map<String, Any> {
        val safeName = envelope.nameFr.ifBlank { "Catégorie" }.take(100)
        return mapOf(
            "userId" to uid,
            "key" to envelope.key.ifBlank { "custom" }.take(60),
            "nameFr" to safeName,
            "nameAr" to envelope.nameAr.ifBlank { safeName }.take(100),
            "nameDarija" to envelope.nameDarija.ifBlank { safeName }.take(100),
            "nameEn" to envelope.nameEn.ifBlank { safeName }.take(100),
            "percentage" to envelope.percentage.coerceIn(0.0, 100.0),
            "hasCap" to envelope.hasCap,
            "colorHex" to envelope.colorHex.ifBlank { "#10B981" }.take(20),
            "createdAt" to (envelope.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun buildFinancialItemPayload(uid: String, item: FinancialItem): Map<String, Any> {
        val allowedTypes = listOf("goal", "bill", "debt", "daret", "seasonal", "preset", "custom_prefs")
        val safeType = if (item.itemType in allowedTypes) item.itemType else "goal"
        return mapOf(
            "userId" to uid,
            "itemType" to safeType,
            "name" to item.name.ifBlank { "Élément" }.take(120),
            "subtitle" to item.subtitle.take(250),
            "amount" to item.amount.coerceIn(0.0, 100000000.0),
            "secondaryAmount" to item.secondaryAmount.coerceIn(0.0, 100000000.0),
            "dayOrTurn" to item.dayOrTurn.coerceIn(0, 366),
            "extraInt" to item.extraInt.coerceIn(0, 366),
            "dateOrCycle" to item.dateOrCycle.take(250),
            "createdAt" to (item.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    suspend fun saveProfile(profile: UserProfile) {
        val uid = requireUserId()
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

    suspend fun upsertTransaction(tx: TransactionItem): String {
        val uid = requireUserId()
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

    suspend fun getTransactionById(targetUserId: String, txId: String): TransactionItem? {
        val docRef = db.collection("users").document(targetUserId).collection("transactions").document(txId)
        try {
            val snap = docRef.get().await()
            return snap.toObject(TransactionItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun deleteTransaction(txId: String) {
        if (txId.isBlank()) return
        val uid = requireUserId()
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

    suspend fun upsertAccount(account: AccountItem): String {
        val uid = requireUserId()
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

    suspend fun deleteAccount(accId: String) {
        if (accId.isBlank()) return
        val uid = requireUserId()
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

    suspend fun upsertEnvelope(envelope: EnvelopeConfig): String {
        val uid = requireUserId()
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
    suspend fun upsertEnvelopesAtomically(envelopes: List<EnvelopeConfig>) {
        if (envelopes.isEmpty()) return
        val uid = requireUserId()
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

    suspend fun deleteEnvelope(envId: String) {
        if (envId.isBlank()) return
        val uid = requireUserId()
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

    suspend fun upsertFinancialItem(item: FinancialItem): String {
        val uid = requireUserId()
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
    suspend fun upsertItemAndTransactionAtomically(item: FinancialItem, tx: TransactionItem) {
        val uid = requireUserId()
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

    suspend fun deleteFinancialItem(itemId: String) {
        if (itemId.isBlank()) return
        val uid = requireUserId()
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
            nameFr = "1. Transport & Déplacement (ONCF, Taxi)",
            nameAr = "1. النقل والتنقل (القطار، سيارة الأجرة)",
            nameDarija = "1. الخدمة والتنقل (التران، الطاكسي)",
            nameEn = "1. Commute & Transit (Train, Taxi)",
            percentage = 35.0,
            hasCap = true,
            colorHex = "#10B981"
        ),
        EnvelopeConfig(
            id = "env_charges",
            userId = uid,
            key = "charges",
            nameFr = "2. Charges fixes & Maison (Factures)",
            nameAr = "2. الالتزامات الثابتة والمنزل",
            nameDarija = "2. التزامات قارة ومصاريف الدار",
            nameEn = "2. Fixed Bills & Household",
            percentage = 26.5,
            hasCap = true,
            colorHex = "#38BDF8"
        ),
        EnvelopeConfig(
            id = "env_cafe",
            userId = uid,
            key = "cafe",
            nameFr = "3. Café & Sorties (Personnel & Loisirs)",
            nameAr = "3. مقهى وخروج (شخصي وترفيه)",
            nameDarija = "3. قهوة وخروج (مصروف شخصي)",
            nameEn = "3. Café & Outings (Personal & Leisure)",
            percentage = 18.5,
            hasCap = true,
            colorHex = "#FBBF24"
        ),
        EnvelopeConfig(
            id = "env_epargne",
            userId = uid,
            key = "epargne",
            nameFr = "4. Épargne & Investissement",
            nameAr = "4. الادخار والاستثمار",
            nameDarija = "4. التوفير والادخار",
            nameEn = "4. Savings & Investments",
            percentage = 20.0,
            hasCap = true,
            colorHex = "#A78BFA"
        ),
        EnvelopeConfig(
            id = "env_autre",
            userId = uid,
            key = "autre",
            nameFr = "Autre / Imprévus",
            nameAr = "أخرى / مصاريف غير متوقعة",
            nameDarija = "حاجة خرى / مكنتش حاسبها",
            nameEn = "Other / Unplanned",
            percentage = 0.0,
            hasCap = false,
            colorHex = "#F87171"
        )
    )

    private fun defaultQuickPresets(): List<FinancialItem> = listOf(
        FinancialItem(id = "pre_cafe", itemType = "preset", name = "Café & Garo", subtitle = "cafe", amount = 20.0, secondaryAmount = 0.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_cash"),
        FinancialItem(id = "pre_taxi", itemType = "preset", name = "Petit Taxi", subtitle = "transport", amount = 15.0, secondaryAmount = 0.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_cash"),
        FinancialItem(id = "pre_bread", itemType = "preset", name = "Khobz & Lben", subtitle = "charges", amount = 10.0, secondaryAmount = 0.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_cash"),
        FinancialItem(id = "pre_oncf", itemType = "preset", name = "Ticket ONCF", subtitle = "transport", amount = 50.0, secondaryAmount = 0.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "acc_bank")
    )

    /**
     * Clean first-time setup: initializes a new user with 0 DH balances on the 3 core accounts,
     * standard envelopes, and 1-tap quick-add presets — NO fake transactions or demo balances.
     * Users can optionally load Demo Data at any time with 1 tap.
     */
    suspend fun ensureSeeded() {
        val uid = requireUserId()
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
                baseBudget = 6400.0,
                payCycleDay = 1,
                rolloverEnabled = true,
                language = "fr",
                isDarkTheme = true,
                seeded = true,
                createdAt = existingCreatedAt
            )
            batch.set(profileRef, buildProfilePayload(uid, newProfile))

            // Clean 0 DH Accounts (Cash, Bank, Mobile Wallet)
            val cleanAccounts = listOf(
                AccountItem(id = "acc_cash", userId = uid, name = "Espèces (Cash)", kind = "cash", initialBalance = 0.0, colorHex = "#10B981"),
                AccountItem(id = "acc_bank", userId = uid, name = "Compte Bancaire (CIH/Attijari)", kind = "bank", initialBalance = 0.0, colorHex = "#38BDF8"),
                AccountItem(id = "acc_wallet", userId = uid, name = "Portefeuille Mobile", kind = "wallet", initialBalance = 0.0, colorHex = "#FBBF24")
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
    suspend fun loadDemoData(
        existingAccounts: List<AccountItem> = emptyList(),
        existingEnvelopes: List<EnvelopeConfig> = emptyList(),
        existingItems: List<FinancialItem> = emptyList(),
        existingTransactions: List<TransactionItem> = emptyList()
    ) {
        val uid = requireUserId()
        val profileRef = db.collection("users").document(uid)
        try {
            val accCreatedAt = existingAccounts.associate { it.id to it.createdAt }
            val envCreatedAt = existingEnvelopes.associate { it.id to it.createdAt }
            val itemCreatedAt = existingItems.associate { it.id to it.createdAt }
            val txCreatedAt = existingTransactions.associate { it.id to it.createdAt }

            val batch = db.batch()

            val demoAccounts = listOf(
                AccountItem(id = "acc_cash", userId = uid, name = "Espèces (Cash)", kind = "cash", initialBalance = 1850.0, colorHex = "#10B981", createdAt = accCreatedAt["acc_cash"]),
                AccountItem(id = "acc_bank", userId = uid, name = "Compte Bancaire (CIH/Attijari)", kind = "bank", initialBalance = 5200.0, colorHex = "#38BDF8", createdAt = accCreatedAt["acc_bank"]),
                AccountItem(id = "acc_wallet", userId = uid, name = "Portefeuille Mobile", kind = "wallet", initialBalance = 650.0, colorHex = "#FBBF24", createdAt = accCreatedAt["acc_wallet"])
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
                TransactionItem(id = "tx_demo_9", type = "expense", amount = 5.0, category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Garo", isoDate = "2026-10-30"),
                TransactionItem(id = "tx_demo_8", type = "expense", amount = 20.0, category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Café & garo", isoDate = "2026-10-30"),
                TransactionItem(id = "tx_demo_7", type = "expense", amount = 5.0, category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Garo", isoDate = "2026-10-30"),
                TransactionItem(id = "tx_demo_6", type = "expense", amount = 20.0, category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Cafe & garo", isoDate = "2026-10-30"),
                TransactionItem(id = "tx_demo_5", type = "expense", amount = 20.0, category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Cafe & garo", isoDate = "2026-10-29"),
                TransactionItem(id = "tx_demo_4", type = "expense", amount = 457.0, category = "Transport & Carburant", envelopeKey = "transport", accountId = "acc_bank", desc = "ONCF CARTE", isoDate = "2026-10-29"),
                TransactionItem(id = "tx_demo_3", type = "expense", amount = 20.0, category = "Café & Sorties", envelopeKey = "cafe", accountId = "acc_cash", desc = "Café & garo", isoDate = "2026-10-29"),
                TransactionItem(id = "tx_demo_2", type = "expense", amount = 500.0, category = "Autre / Imprévus", envelopeKey = "autre", accountId = "acc_bank", desc = "Crédit mama", isoDate = "2026-10-29"),
                TransactionItem(id = "tx_demo_1", type = "expense", amount = 1000.0, category = "Autre / Imprévus", envelopeKey = "autre", accountId = "acc_bank", desc = "Crédit inaam", isoDate = "2026-10-29")
            )
            for (tx in demoTxs) {
                val ref = profileRef.collection("transactions").document(tx.id)
                batch.set(ref, buildTransactionPayload(uid, tx.copy(createdAt = txCreatedAt[tx.id])))
            }

            val demoItems = defaultQuickPresets() + listOf(
                FinancialItem(id = "bill_fibre", itemType = "bill", name = "Fibre Optique & Mobile", subtitle = "charges", amount = 250.0, secondaryAmount = 0.0, dayOrTurn = 5, extraInt = 0, dateOrCycle = ""),
                FinancialItem(id = "bill_lydec", itemType = "bill", name = "Eau & Électricité (Lydec/Redal)", subtitle = "charges", amount = 320.0, secondaryAmount = 0.0, dayOrTurn = 18, extraInt = 0, dateOrCycle = ""),
                FinancialItem(id = "goal_urgence", itemType = "goal", name = "Fonds d'urgence (Tawfir)", subtitle = "epargne", amount = 10000.0, secondaryAmount = 4200.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-06-30"),
                FinancialItem(id = "debt_mama", itemType = "debt", name = "Crédit Mama", subtitle = "Mama", amount = 500.0, secondaryAmount = 0.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "lent"),
                FinancialItem(id = "debt_inaam", itemType = "debt", name = "Crédit Inaam", subtitle = "Inaam", amount = 1000.0, secondaryAmount = 200.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "lent"),
                FinancialItem(id = "daret_famille", itemType = "daret", name = "Daret Collègues", subtitle = "10 membres", amount = 1000.0, secondaryAmount = 10.0, dayOrTurn = 4, extraInt = 3, dateOrCycle = "2027-02-01"),
                FinancialItem(id = "season_ramadan", itemType = "seasonal", name = "Ramadan & Ftous", subtitle = "ramadan", amount = 3000.0, secondaryAmount = 900.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-02-17"),
                FinancialItem(id = "season_eid", itemType = "seasonal", name = "Aïd al-Adha (Kebch)", subtitle = "eid", amount = 4500.0, secondaryAmount = 1200.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-05-16"),
                FinancialItem(id = "season_vignette", itemType = "seasonal", name = "Vignette & Assurance Auto", subtitle = "vignette", amount = 3800.0, secondaryAmount = 1500.0, dayOrTurn = 1, extraInt = 0, dateOrCycle = "2027-01-31")
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
    suspend fun resetToCleanAccount(
        existingTxIds: List<String>,
        existingItemIdsToClear: List<String>,
        existingAccounts: List<AccountItem>
    ) {
        val uid = requireUserId()
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
                AccountItem(id = "acc_cash", userId = uid, name = "Espèces (Cash)", kind = "cash", initialBalance = 0.0, colorHex = "#10B981", createdAt = accCreatedAt["acc_cash"]),
                AccountItem(id = "acc_bank", userId = uid, name = "Compte Bancaire (CIH/Attijari)", kind = "bank", initialBalance = 0.0, colorHex = "#38BDF8", createdAt = accCreatedAt["acc_bank"]),
                AccountItem(id = "acc_wallet", userId = uid, name = "Portefeuille Mobile", kind = "wallet", initialBalance = 0.0, colorHex = "#FBBF24", createdAt = accCreatedAt["acc_wallet"])
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
        onChunkProgress: (completedChunks: Int, totalChunks: Int) -> Unit = { _, _ -> }
    ) {
        val uid = requireUserId()
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
