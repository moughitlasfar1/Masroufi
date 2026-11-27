package com.example.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.BuildConfig
import com.example.data.MasroufiRepository
import com.example.data.local.MasroufiDatabaseProvider
import com.example.data.local.RoomMasroufiRepository
import com.example.domain.PayCycleEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic WorkManager worker that idempotently posts due `AUTO_POST` recurring rules
 * against the local encrypted Room database.
 *
 * Uses deterministic occurrence keys (`ruleId:occurrenceIsoDate`) inside Room `@Transaction`,
 * so retries or overlapping app-launch runs never duplicate transactions or loan repayments.
 */
class RecurringAutoPostWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = MasroufiDatabaseProvider.getOrOpen(applicationContext)
                ?: return@withContext Result.retry()
            val uid = runCatching { Firebase.auth.currentUser?.uid }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: MasroufiRepository.LOCAL_DEVICE_USER_ID
            val repo = RoomMasroufiRepository(
                dao = db.masroufiDao(),
                userIdProvider = { uid }
            )
            val todayIso = PayCycleEngine.todayIso()
            val postedCount = repo.processDueAutoPostRules(todayIso = todayIso, userId = uid)
            if (BuildConfig.DEBUG && postedCount > 0) {
                runCatching {
                    Log.d(TAG, "Auto-posted $postedCount due recurring occurrence(s)")
                }
            }
            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) {
                runCatching {
                    Log.w(TAG, "Recurring auto-post failed: ${t.javaClass.simpleName}")
                }
            }
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "RecurringAutoPostWorker"
        const val UNIQUE_WORK_NAME = "masroufi_recurring_autopost_periodic"

        fun schedulePeriodic(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<RecurringAutoPostWorker>(
                    6,
                    TimeUnit.HOURS
                ).build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                    )
            }.onFailure { t ->
                if (BuildConfig.DEBUG) {
                    runCatching {
                        Log.w(TAG, "Failed to schedule periodic auto-post worker: ${t.javaClass.simpleName}")
                    }
                }
            }
        }
    }
}
