package com.masroufi.credit.data

import android.content.Context
import android.util.Log
import com.example.data.MasroufiRepository
import com.example.data.local.MasroufiDao
import com.example.data.local.MasroufiDatabaseProvider
import com.example.data.local.MasroufiEntityMappers.toDomain
import com.example.data.local.MasroufiEntityMappers.toLocalEntity
import com.example.data.local.NoOpRemoteMirror
import com.example.data.local.RemoteMirror
import com.google.firebase.Timestamp
import com.masroufi.core.Money
import com.masroufi.credit.domain.CreditInput
import com.masroufi.credit.domain.CreditResult
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Local-first encrypted Room implementation of [CreditDataSource] backed by `saved_credits`.
 *
 * Works identically offline, in Local Mode (`local_device_user`), and when signed in with Google.
 * Never touches Firestore or requires network connectivity. Invokes [remoteMirror] strictly
 * post-commit with full exception isolation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomCreditRepository private constructor(
    private val directDao: MasroufiDao?,
    private val appContext: Context?,
    private val userIdProvider: () -> String,
    private val remoteMirror: RemoteMirror = NoOpRemoteMirror
) : CreditDataSource {

    constructor(
        dao: MasroufiDao,
        userIdProvider: () -> String = { MasroufiRepository.LOCAL_DEVICE_USER_ID },
        remoteMirror: RemoteMirror = NoOpRemoteMirror
    ) : this(
        directDao = dao,
        appContext = null,
        userIdProvider = userIdProvider,
        remoteMirror = remoteMirror
    )

    constructor(
        context: Context,
        userIdProvider: () -> String,
        remoteMirror: RemoteMirror = NoOpRemoteMirror
    ) : this(
        directDao = null,
        appContext = context.applicationContext,
        userIdProvider = userIdProvider,
        remoteMirror = remoteMirror
    )

    override fun currentUid(): String =
        userIdProvider().takeIf { it.isNotBlank() } ?: MasroufiRepository.LOCAL_DEVICE_USER_ID

    private fun openDaoOrNull(): MasroufiDao? {
        return directDao ?: MasroufiDatabaseProvider.getOpenInstanceOrNull()?.masroufiDao()
    }

    private suspend fun requireDao(): MasroufiDao? {
        openDaoOrNull()?.let { return it }
        val ctx = appContext ?: return null
        return withContext(Dispatchers.IO) {
            MasroufiDatabaseProvider.getOrOpen(ctx)?.masroufiDao()
        }
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
                Log.w(TAG, "RemoteMirror hook failed after local Room credit commit ($opName): ${t.message}")
            }
        }
    }

    override suspend fun saveCredit(
        input: CreditInput,
        result: CreditResult,
        linkToMonthlyExpenses: Boolean
    ): Result<String> {
        if (!input.loanAmount.isPositive || input.durationMonths <= 0) {
            return Result.failure(IllegalArgumentException("INVALID_CREDIT_INPUT"))
        }
        val dao = requireDao()
            ?: return Result.failure(IllegalStateException("LOCAL_DB_UNAVAILABLE"))

        val uid = currentUid()
        val now = System.currentTimeMillis()
        val creditId = "credit_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val safeDownPayment = input.downPayment.coerceIn(Money.ZERO, input.loanAmount)
        val safeDeferred = input.deferredMonths.coerceIn(0, (input.durationMonths - 1).coerceAtLeast(0))

        val domainCredit = SavedCreditEntity(
            id = creditId,
            userId = uid,
            type = input.creditType.name,
            loanAmount = input.loanAmount,
            downPayment = safeDownPayment,
            financedPrincipal = result.financedPrincipal.coerceIn(Money.ZERO, input.loanAmount),
            durationMonths = input.durationMonths,
            deferredMonths = safeDeferred,
            annualInterestRateHt = input.annualInterestRateHt.coerceIn(0.0, 50.0),
            applyInterestTva = input.applyInterestTva,
            insuranceRate = input.insuranceRate.coerceIn(0.0, 20.0),
            monthlyPayment = result.monthlyPayment.coerceAtLeast(Money.ZERO),
            totalCost = result.totalCost.coerceAtLeast(Money.ZERO),
            debtToIncomeRatio = result.debtToIncomeRatio.coerceIn(0.0, 10_000.0),
            maxAllowedDti = result.maxAllowedDti,
            linkToMonthlyExpenses = linkToMonthlyExpenses,
            createdAt = Timestamp(Date(now))
        )

        return try {
            val localEntity = domainCredit.toLocalEntity(uid, now)
            dao.upsertSavedCredit(localEntity)
            val committed = localEntity.toDomain()
            notifyRemoteMirror("saveCredit") { it.onSavedCreditUpserted(uid, committed) }
            Result.success(localEntity.id)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeSavedCredits(): Flow<List<SavedCreditEntity>> {
        val uid = currentUid()
        if (directDao != null) {
            return directDao.observeSavedCredits(uid).map { rows -> rows.map { it.toDomain() } }
        }
        return MasroufiDatabaseProvider.dbGeneration.flatMapLatest {
            val dao = openDaoOrNull() ?: return@flatMapLatest flowOf(emptyList())
            dao.observeSavedCredits(uid).map { rows -> rows.map { it.toDomain() } }
        }
    }

    override suspend fun deleteCredit(creditId: String): Result<Unit> {
        if (creditId.isBlank()) {
            return Result.failure(IllegalArgumentException("INVALID_CREDIT_ID"))
        }
        val dao = requireDao()
            ?: return Result.failure(IllegalStateException("LOCAL_DB_UNAVAILABLE"))
        val uid = currentUid()
        return try {
            dao.deleteSavedCredit(uid, creditId)
            notifyRemoteMirror("deleteCredit") { it.onSavedCreditDeleted(uid, creditId) }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "RoomCreditRepo"
    }
}
