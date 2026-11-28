package com.example.data.local

import com.example.data.EnvelopeConfig
import com.example.data.FirestoreSnapshotMappers
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/**
 * Play-flavor production implementation of [FirestoreImportSource] that reads every collection
 * strictly with [Source.SERVER]. Never writes, updates, or deletes anything in Firestore.
 */
class FirebaseFirestoreServerSource(
    private val db: FirebaseFirestore
) : FirestoreImportSource {

    override suspend fun fetchUserSnapshotFromServer(userId: String): FirestoreUserSnapshot {
        val userDoc = db.collection("users").document(userId)

        val profileSnap = userDoc.get(Source.SERVER).await()
        val profile = if (profileSnap.exists()) {
            FirestoreSnapshotMappers.toUserProfile(profileSnap, userId)
        } else {
            null
        }

        val accountsSnap = userDoc.collection("accounts")
            .whereEqualTo("userId", userId)
            .get(Source.SERVER)
            .await()
        val accounts = accountsSnap.documents.map { doc ->
            FirestoreSnapshotMappers.toAccountItem(doc, userId)
        }

        val transactionsSnap = userDoc.collection("transactions")
            .whereEqualTo("userId", userId)
            .get(Source.SERVER)
            .await()
        val transactions = transactionsSnap.documents.map { doc ->
            FirestoreSnapshotMappers.toTransactionItem(doc, userId)
        }

        val envelopesSnap = userDoc.collection("envelopes")
            .whereEqualTo("userId", userId)
            .get(Source.SERVER)
            .await()
        val envelopes = envelopesSnap.documents.map { doc ->
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
                createdAtEpochMs = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.toDate()?.time ?: 0L,
                updatedAtEpochMs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.toDate()?.time ?: 0L
            ).migratedStorageCopy()
        }

        val itemsSnap = userDoc.collection("items")
            .whereEqualTo("userId", userId)
            .get(Source.SERVER)
            .await()
        val items = itemsSnap.documents.map { doc ->
            FirestoreSnapshotMappers.toFinancialItem(doc, userId)
        }

        val creditsSnap = userDoc.collection("credits")
            .get(Source.SERVER)
            .await()
        val credits = creditsSnap.documents.mapNotNull { doc ->
            runCatching {
                FirestoreSnapshotMappers.toSavedCreditEntity(doc, userId)
            }.getOrNull()
        }

        return FirestoreUserSnapshot(
            profile = profile,
            accounts = accounts,
            transactions = transactions,
            envelopes = envelopes,
            financialItems = items,
            credits = credits
        )
    }
}
