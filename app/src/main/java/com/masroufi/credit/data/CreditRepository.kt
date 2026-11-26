package com.masroufi.credit.data

import com.example.data.FirestoreSnapshotMappers
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.masroufi.core.Money
import com.masroufi.credit.domain.CreditInput
import com.masroufi.credit.domain.CreditResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

interface CreditDataSource {
    fun currentUid(): String?
    suspend fun saveCredit(
        input: CreditInput,
        result: CreditResult,
        linkToMonthlyExpenses: Boolean
    ): Result<String>
    fun observeSavedCredits(): Flow<List<SavedCreditEntity>>
    suspend fun deleteCredit(creditId: String): Result<Unit>
}

/**
 * Isolated Firestore repository for `users/{uid}/credits/{creditId}`.
 * Always expects a named [FirebaseFirestore] instance obtained via `R.string.firestore_database_id`.
 */
class CreditRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : CreditDataSource {

    override fun currentUid(): String? = auth.currentUser?.uid?.takeIf { it.isNotBlank() }

    override suspend fun saveCredit(
        input: CreditInput,
        result: CreditResult,
        linkToMonthlyExpenses: Boolean
    ): Result<String> {
        val uid = currentUid()
            ?: return Result.failure(IllegalStateException("AUTH_REQUIRED"))

        if (!input.loanAmount.isPositive || input.durationMonths <= 0) {
            return Result.failure(IllegalArgumentException("INVALID_CREDIT_INPUT"))
        }
        val safeDownPayment = input.downPayment.coerceIn(Money.ZERO, input.loanAmount)
        val safeDeferred = input.deferredMonths.coerceIn(0, (input.durationMonths - 1).coerceAtLeast(0))

        val colRef = db.collection("users").document(uid).collection("credits")
        val docRef = colRef.document()

        // Explicit field map matching firestore.rules `isValidSavedCredit` (no `id` field inside payload)
        val payload = hashMapOf<String, Any>(
            "userId" to uid,
            "type" to input.creditType.name,
            "loanAmount" to input.loanAmount.toBigDecimalDh().toDouble(),
            "downPayment" to safeDownPayment.toBigDecimalDh().toDouble(),
            "financedPrincipal" to result.financedPrincipal.coerceIn(Money.ZERO, input.loanAmount).toBigDecimalDh().toDouble(),
            "durationMonths" to input.durationMonths,
            "deferredMonths" to safeDeferred,
            "annualInterestRateHt" to input.annualInterestRateHt.coerceIn(0.0, 50.0),
            "applyInterestTva" to input.applyInterestTva,
            "insuranceRate" to input.insuranceRate.coerceIn(0.0, 20.0),
            "monthlyPayment" to result.monthlyPayment.coerceAtLeast(Money.ZERO).toBigDecimalDh().toDouble(),
            "totalCost" to result.totalCost.coerceAtLeast(Money.ZERO).toBigDecimalDh().toDouble(),
            "debtToIncomeRatio" to result.debtToIncomeRatio.coerceIn(0.0, 10_000.0),
            "maxAllowedDti" to result.maxAllowedDti,
            "linkToMonthlyExpenses" to linkToMonthlyExpenses,
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(IllegalStateException(message, e))
        }
    }

    override fun observeSavedCredits(): Flow<List<SavedCreditEntity>> {
        val uid = currentUid() ?: return flowOf(emptyList())
        val colRef = db.collection("users").document(uid).collection("credits")
        val query = colRef.orderBy("createdAt", Query.Direction.DESCENDING)

        return callbackFlow {
            val registration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val message = handleFirestoreError(error, OperationType.LIST, colRef.path)
                    close(IllegalStateException(message, error))
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    runCatching {
                        FirestoreSnapshotMappers.toSavedCreditEntity(doc, uid)
                    }.getOrNull()
                }.orEmpty()
                trySend(items)
            }
            awaitClose { registration.remove() }
        }
    }

    override suspend fun deleteCredit(creditId: String): Result<Unit> {
        val uid = currentUid()
            ?: return Result.failure(IllegalStateException("AUTH_REQUIRED"))
        if (creditId.isBlank()) {
            return Result.failure(IllegalArgumentException("INVALID_CREDIT_ID"))
        }

        val docRef = db.collection("users").document(uid).collection("credits").document(creditId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(IllegalStateException(message, e))
        }
    }
}
