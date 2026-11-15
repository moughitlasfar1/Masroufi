package com.example.widget

import android.app.PendingIntent
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.View
import android.widget.RemoteViews
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.DefaultCategoryResolver
import com.example.data.MasroufiRepository
import com.example.data.TransactionItem
import com.example.domain.PayCycleEngine
import com.example.domain.Translations
import com.example.domain.VoiceParsedTransaction
import com.example.security.AppSecurityManager
import com.example.ui.DashboardState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max

private val Context.widgetDataStore by preferencesDataStore(name = "masroufi_widget_datastore")

object MasroufiWidgetStore {
    private const val PREFS_NAME = "masroufi_widget_prefs"
    private const val KEY_TODAY_SPEND = "today_spend"
    private const val KEY_SAFE_TODAY = "safe_today"
    private const val KEY_CURRENCY = "currency_symbol"
    private const val KEY_HIDE_AMOUNTS = "hide_amounts"
    private const val KEY_LANG = "language"
    private const val KEY_FULL_SNAPSHOT_JSON = "full_widget_snapshot_json"
    private const val KEY_PENDING_QUICK_TXS_JSON = "pending_quick_capture_txs_json"
    private const val KEY_STYLE_PREFIX = "widget_style_"

    private val DS_SNAPSHOT_KEY = stringPreferencesKey("full_widget_snapshot_json")

    fun saveFullSnapshot(
        context: Context,
        state: DashboardState
    ): WidgetSnapshot {
        val appLock = AppSecurityManager.isAppLockEnabled(context)
        val snapshot = WidgetSnapshot.fromDashboardState(
            state = state,
            nowIso = PayCycleEngine.todayIso(),
            appLockEnabled = appLock
        )
        saveWidgetSnapshot(context, snapshot)
        return snapshot
    }

    fun saveWidgetSnapshot(
        context: Context,
        snapshot: WidgetSnapshot
    ) {
        val appCtx = context.applicationContext
        val normalized = snapshot.withMidnightRolloverIfNeeded(PayCycleEngine.todayIso())
        val jsonStr = normalized.toJson().toString()

        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_TODAY_SPEND, normalized.todaySpend.toFloat())
            .putFloat(KEY_SAFE_TODAY, normalized.safeToSpendToday.toFloat())
            .putString(KEY_CURRENCY, normalized.currencySymbol.ifBlank { "DH" })
            .putBoolean(KEY_HIDE_AMOUNTS, normalized.hideAmounts)
            .putString(KEY_LANG, normalized.language.ifBlank { "fr" })
            .putString(KEY_FULL_SNAPSHOT_JSON, jsonStr)
            .apply()

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                appCtx.widgetDataStore.edit { ds ->
                    ds[DS_SNAPSHOT_KEY] = jsonStr
                }
            }
        }

        syncDynamicShortcuts(appCtx, normalized)
        refreshWidgetsAndTiles(appCtx)
        WidgetRefreshWorker.schedulePeriodicAndMidnight(appCtx)
    }

    fun saveSnapshot(
        context: Context,
        todaySpend: Double,
        safeToSpendToday: Double,
        currencySymbol: String,
        hideAmounts: Boolean,
        language: String
    ) {
        val existing = loadSnapshot(context)
        val updated = existing.copy(
            snapshotDateIso = PayCycleEngine.todayIso(),
            todaySpend = PayCycleEngine.roundMoney(todaySpend),
            safeToSpendToday = PayCycleEngine.roundMoney(safeToSpendToday),
            currencySymbol = currencySymbol.ifBlank { "DH" },
            hideAmounts = hideAmounts,
            language = language.ifBlank { "fr" },
            appLockEnabled = AppSecurityManager.isAppLockEnabled(context),
            updatedAtMillis = System.currentTimeMillis()
        )
        saveWidgetSnapshot(context, updated)
    }

    fun loadSnapshot(context: Context, nowIso: String = PayCycleEngine.todayIso()): WidgetSnapshot {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_FULL_SNAPSHOT_JSON, null)
        val baseSnapshot = if (!rawJson.isNullOrBlank()) {
            runCatching { WidgetSnapshot.fromJson(JSONObject(rawJson)) }.getOrNull()
        } else null

        val resolved = baseSnapshot ?: WidgetSnapshot(
            snapshotDateIso = nowIso,
            todaySpend = prefs.getFloat(KEY_TODAY_SPEND, 0f).toDouble(),
            safeToSpendToday = prefs.getFloat(KEY_SAFE_TODAY, 0f).toDouble(),
            currencySymbol = prefs.getString(KEY_CURRENCY, "DH").orEmpty().ifBlank { "DH" },
            hideAmounts = prefs.getBoolean(KEY_HIDE_AMOUNTS, false),
            appLockEnabled = AppSecurityManager.isAppLockEnabled(appCtx),
            language = prefs.getString(KEY_LANG, "fr").orEmpty().ifBlank { "fr" }
        )

        val pendingList = runCatching {
            com.example.notifications.BankDetectionStore.loadPendingItems(appCtx)
        }.getOrDefault(emptyList())
        val oldestHours = if (pendingList.isNotEmpty()) {
            val oldestMs = pendingList.minOf { it.createdAtMillis }
            ((System.currentTimeMillis() - oldestMs) / (3600_000L)).toInt().coerceAtLeast(0)
        } else 0

        val rolledOver = resolved.withMidnightRolloverIfNeeded(nowIso).copy(
            appLockEnabled = AppSecurityManager.isAppLockEnabled(appCtx),
            pendingDetectionsCount = max(resolved.pendingDetectionsCount, pendingList.size),
            oldestPendingDetectionHours = max(resolved.oldestPendingDetectionHours, oldestHours)
        )
        if (rolledOver.snapshotDateIso != resolved.snapshotDateIso) {
            // Persist midnight reset immediately so subsequent reads are up to date
            prefs.edit()
                .putFloat(KEY_TODAY_SPEND, rolledOver.todaySpend.toFloat())
                .putFloat(KEY_SAFE_TODAY, rolledOver.safeToSpendToday.toFloat())
                .putString(KEY_FULL_SNAPSHOT_JSON, rolledOver.toJson().toString())
                .apply()
        }
        return rolledOver
    }

    fun saveWidgetStyle(context: Context, style: WidgetStyle) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("$KEY_STYLE_PREFIX${style.appWidgetId}", style.toJson().toString())
            .apply()
        refreshWidgetsAndTiles(context.applicationContext)
    }

    fun loadWidgetStyle(context: Context, appWidgetId: Int): WidgetStyle {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("$KEY_STYLE_PREFIX$appWidgetId", null)
        if (!raw.isNullOrBlank()) {
            runCatching {
                return WidgetStyle.fromJson(JSONObject(raw)).copy(appWidgetId = appWidgetId)
            }
        }
        return WidgetStyle(appWidgetId = appWidgetId)
    }

    fun deleteWidgetStyle(context: Context, appWidgetId: Int) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("$KEY_STYLE_PREFIX$appWidgetId").apply()
    }

    /**
     * Records a batch of quick-captured voice or keypad expenses directly from [QuickCaptureActivity]
     * or a widget preset button without opening [MainActivity].
     * Updates [WidgetSnapshot] immediately, queues the transactions for local/cloud sync,
     * and returns the created transaction IDs for 5-second Undo.
     */
    fun recordQuickCaptureTransactions(
        context: Context,
        items: List<VoiceParsedTransaction>
    ): List<TransactionItem> {
        if (items.isEmpty()) return emptyList()
        val appCtx = context.applicationContext
        val currentSnapshot = loadSnapshot(appCtx)
        val todayIso = PayCycleEngine.todayIso()
        val baseMs = System.currentTimeMillis()
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull().orEmpty().ifBlank { "local_device_user" }

        val createdTxs = items.mapIndexedNotNull { idx, item ->
            val rounded = PayCycleEngine.roundMoney(item.amount)
            if (rounded <= 0.0) return@mapIndexedNotNull null
            if (item.txType == "expense" && item.description.isNotBlank() && item.envelopeKey.isNotBlank()) {
                PayCycleEngine.learnCategoryForDescription(item.description, item.envelopeKey)
            }
            val normalizedCat = DefaultCategoryResolver.normalizeStoredCategoryValue(item.categoryName, item.envelopeKey)
            TransactionItem(
                id = "tx_qc_${baseMs}_$idx",
                userId = uid,
                type = item.txType.ifBlank { "expense" },
                amount = rounded,
                category = normalizedCat,
                envelopeKey = item.envelopeKey.ifBlank { "autre" },
                accountId = "acc_cash",
                toAccountId = "",
                desc = item.description.ifBlank { item.categoryName },
                isoDate = todayIso
            )
        }
        if (createdTxs.isEmpty()) return emptyList()

        val addedExpenseTotal = PayCycleEngine.roundMoney(
            createdTxs.filter { it.type == "expense" }.sumOf { it.amount }
        )
        val newTodaySpend = PayCycleEngine.roundMoney(currentSnapshot.effectiveTodaySpend(todayIso) + addedExpenseTotal)
        val newCycleExpenses = PayCycleEngine.roundMoney(currentSnapshot.cycleExpenses + addedExpenseTotal)
        val newRemainingBudget = PayCycleEngine.roundMoney(max(0.0, currentSnapshot.remainingBudget - addedExpenseTotal))
        val daysRem = currentSnapshot.daysRemainingInCycle.coerceAtLeast(1)
        val newSafeToday = PayCycleEngine.roundMoney(max(0.0, currentSnapshot.safeToSpendToday - (addedExpenseTotal / daysRem)))

        val updatedEnvelopes = currentSnapshot.envelopes.map { env ->
            val deltaForEnv = createdTxs.filter { it.type == "expense" && it.envelopeKey == env.key }.sumOf { it.amount }
            if (deltaForEnv > 0.0) {
                val nextSpent = PayCycleEngine.roundMoney(env.spent + deltaForEnv)
                val nextPct = if (env.hasCap && env.totalAvailable > 0.0) {
                    ((nextSpent / env.totalAvailable) * 100.0).toInt()
                } else 0
                env.copy(
                    spent = nextSpent,
                    usagePercent = nextPct,
                    isOver90Percent = env.hasCap && nextPct >= 90
                )
            } else env
        }

        val updatedSnapshot = currentSnapshot.copy(
            snapshotDateIso = todayIso,
            todaySpend = newTodaySpend,
            cycleExpenses = newCycleExpenses,
            remainingBudget = newRemainingBudget,
            safeToSpendToday = newSafeToday,
            envelopes = updatedEnvelopes,
            updatedAtMillis = System.currentTimeMillis()
        )
        saveWidgetSnapshot(appCtx, updatedSnapshot)

        // Append to pending quick-capture queue so MasroufiViewModel / Firestore picks them up
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingArr = runCatching {
            JSONArray(prefs.getString(KEY_PENDING_QUICK_TXS_JSON, "[]") ?: "[]")
        }.getOrDefault(JSONArray())

        createdTxs.forEach { tx ->
            existingArr.put(
                JSONObject().apply {
                    put("id", tx.id)
                    put("userId", tx.userId)
                    put("type", tx.type)
                    put("amount", tx.amount)
                    put("category", tx.category)
                    put("envelopeKey", tx.envelopeKey)
                    put("accountId", tx.accountId)
                    put("desc", tx.desc)
                    put("isoDate", tx.isoDate)
                }
            )
        }
        prefs.edit().putString(KEY_PENDING_QUICK_TXS_JSON, existingArr.toString()).apply()

        // Also persist directly to Firestore offline cache if a Firebase user is signed in
        runCatching {
            val firebaseUser = Firebase.auth.currentUser
            if (firebaseUser != null) {
                val dbId = appCtx.getString(R.string.firestore_database_id)
                val repo = MasroufiRepository(FirebaseFirestore.getInstance(dbId))
                CoroutineScope(Dispatchers.IO).launch {
                    createdTxs.forEach { tx ->
                        runCatching { repo.upsertTransaction(tx) }
                    }
                }
            }
        }

        WidgetRefreshWorker.enqueueImmediate(appCtx)
        return createdTxs
    }

    /**
     * Reverts a batch of quick-captured transactions when the user taps Undo within 5 seconds.
     */
    fun undoQuickCaptureBatch(
        context: Context,
        createdTxs: List<TransactionItem>
    ) {
        if (createdTxs.isEmpty()) return
        val appCtx = context.applicationContext
        val idsToUndo = createdTxs.map { it.id }.toSet()
        val currentSnapshot = loadSnapshot(appCtx)
        val todayIso = PayCycleEngine.todayIso()

        val revertedExpenseTotal = PayCycleEngine.roundMoney(
            createdTxs.filter { it.type == "expense" }.sumOf { it.amount }
        )
        val daysRem = currentSnapshot.daysRemainingInCycle.coerceAtLeast(1)
        val updatedSnapshot = currentSnapshot.copy(
            todaySpend = PayCycleEngine.roundMoney(max(0.0, currentSnapshot.effectiveTodaySpend(todayIso) - revertedExpenseTotal)),
            cycleExpenses = PayCycleEngine.roundMoney(max(0.0, currentSnapshot.cycleExpenses - revertedExpenseTotal)),
            remainingBudget = PayCycleEngine.roundMoney(currentSnapshot.remainingBudget + revertedExpenseTotal),
            safeToSpendToday = PayCycleEngine.roundMoney(currentSnapshot.safeToSpendToday + (revertedExpenseTotal / daysRem)),
            updatedAtMillis = System.currentTimeMillis()
        )
        saveWidgetSnapshot(appCtx, updatedSnapshot)

        // Remove from pending queue
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingArr = runCatching {
            JSONArray(prefs.getString(KEY_PENDING_QUICK_TXS_JSON, "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        val filteredArr = JSONArray()
        for (i in 0 until existingArr.length()) {
            val obj = existingArr.optJSONObject(i) ?: continue
            if (obj.optString("id") !in idsToUndo) {
                filteredArr.put(obj)
            }
        }
        prefs.edit().putString(KEY_PENDING_QUICK_TXS_JSON, filteredArr.toString()).apply()

        // Also delete from Firestore offline cache if signed in
        runCatching {
            val firebaseUser = Firebase.auth.currentUser
            if (firebaseUser != null) {
                val dbId = appCtx.getString(R.string.firestore_database_id)
                val repo = MasroufiRepository(FirebaseFirestore.getInstance(dbId))
                CoroutineScope(Dispatchers.IO).launch {
                    createdTxs.forEach { tx ->
                        runCatching { repo.deleteTransaction(tx.id) }
                    }
                }
            }
        }

        WidgetRefreshWorker.enqueueImmediate(appCtx)
    }

    /**
     * Drains any pending quick-capture transactions recorded while [MainActivity] was closed
     * so local-mode or active ViewModel state stays 100% synchronized.
     */
    fun consumePendingQuickCaptureTransactions(context: Context): List<TransactionItem> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PENDING_QUICK_TXS_JSON, null) ?: return emptyList()
        prefs.edit().remove(KEY_PENDING_QUICK_TXS_JSON).apply()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val list = mutableListOf<TransactionItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                TransactionItem(
                    id = obj.optString("id", "tx_qc_$i"),
                    userId = obj.optString("userId", "local_device_user"),
                    type = obj.optString("type", "expense"),
                    amount = obj.optDouble("amount", 0.0),
                    category = obj.optString("category", "Autre"),
                    envelopeKey = obj.optString("envelopeKey", "autre"),
                    accountId = obj.optString("accountId", "acc_cash"),
                    desc = obj.optString("desc", "Achat"),
                    isoDate = obj.optString("isoDate", PayCycleEngine.todayIso())
                )
            )
        }
        return list
    }

    fun getTodaySpendFormatted(context: Context, style: WidgetStyle = WidgetStyle()): String {
        val snap = loadSnapshot(context)
        val cur = snap.currencySymbol.ifBlank { "DH" }
        val hide = style.shouldMaskAmounts(snap.hideAmounts, snap.appLockEnabled)
        val spend = snap.effectiveTodaySpend()
        return if (hide) "•••• $cur" else "${PayCycleEngine.formatDh(spend)} $cur"
    }

    fun getSafeTodayFormatted(context: Context, style: WidgetStyle = WidgetStyle()): String {
        val snap = loadSnapshot(context)
        val strings = Translations.forLang(snap.language)
        val cur = snap.currencySymbol.ifBlank { "DH" }
        val hide = style.shouldMaskAmounts(snap.hideAmounts, snap.appLockEnabled)
        val prefix = strings.widgetSafePrefix
        return if (hide) "$prefix •••• $cur" else "$prefix ${PayCycleEngine.formatDh(snap.safeToSpendToday)} $cur/j"
    }

    fun getHeaderLabel(context: Context): String {
        val snap = loadSnapshot(context)
        return Translations.forLang(snap.language).widgetHeaderToday
    }

    fun refreshWidgetsAndTiles(context: Context) {
        val appCtx = context.applicationContext
        MasroufiAppWidget.updateAllWidgets(appCtx)
        runCatching {
            if (com.example.notifications.QuickCaptureNotification.isEnabled(appCtx)) {
                com.example.notifications.QuickCaptureNotification.refresh(appCtx)
            }
        }
        runCatching {
            TileService.requestListeningState(appCtx, ComponentName(appCtx, MasroufiQuickTileService::class.java))
            TileService.requestListeningState(appCtx, ComponentName(appCtx, MasroufiVoiceTileService::class.java))
            TileService.requestListeningState(appCtx, ComponentName(appCtx, MasroufiSafeTodayTileService::class.java))
        }
    }

    /**
     * Phase 8: Publishes dynamic launcher shortcuts for Moonshine Voice, Quick Keypad, and top Presets.
     */
    private fun syncDynamicShortcuts(context: Context, snapshot: WidgetSnapshot) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        runCatching {
            val sm = context.getSystemService(ShortcutManager::class.java) ?: return
            val strings = Translations.forLang(snapshot.language)
            val cur = snapshot.currencySymbol.ifBlank { "DH" }
            val dynamicList = mutableListOf<ShortcutInfo>()

            snapshot.presets.take(2).forEachIndexed { idx, preset ->
                val presetIntent = Intent(context, QuickCaptureActivity::class.java).apply {
                    action = MasroufiAppWidget.ACTION_QUICK_PRESET
                    putExtra(MasroufiAppWidget.EXTRA_PRESET_NAME, preset.name)
                    putExtra(MasroufiAppWidget.EXTRA_PRESET_AMOUNT, preset.amount)
                    putExtra(MasroufiAppWidget.EXTRA_PRESET_ENVELOPE, preset.envelopeKey)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val label = "${preset.name} (${PayCycleEngine.formatDh(preset.amount)} $cur)"
                val shortcut = ShortcutInfo.Builder(context, "dyn_preset_$idx")
                    .setShortLabel(preset.name.take(12))
                    .setLongLabel("${strings.widgetQuickAddBtn} $label")
                    .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher_round))
                    .setIntent(presetIntent)
                    .build()
                dynamicList.add(shortcut)
            }
            sm.dynamicShortcuts = dynamicList
            GoogleAssistantExpenseBridge.pushAssistantDynamicShortcuts(context, snapshot)
        }
    }
}

/**
 * Phase 1: Periodic & midnight-aware background worker that guarantees widgets and Quick Settings
 * tiles always show fresh todaySpend and safeToSpendToday even when Masroufi is closed.
 */
class WidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Loading the snapshot automatically triggers midnight date-rollover normalization and persists it
        val snapshot = MasroufiWidgetStore.loadSnapshot(applicationContext)
        MasroufiWidgetStore.saveWidgetSnapshot(applicationContext, snapshot)
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "masroufi_widget_periodic_refresh"
        private const val MIDNIGHT_WORK_NAME = "masroufi_widget_midnight_refresh"

        fun schedulePeriodicAndMidnight(context: Context) {
            runCatching {
                val appCtx = context.applicationContext
                val wm = WorkManager.getInstance(appCtx)

                // 1. Periodic refresh every 1 hour
                val periodicReq = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(1, TimeUnit.HOURS)
                    .build()
                wm.enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicReq
                )

                // 2. Next midnight rollover one-time trigger
                val now = Calendar.getInstance()
                val nextMidnight = (now.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val delayMs = (nextMidnight.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
                val midnightReq = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .addTag(MIDNIGHT_WORK_NAME)
                    .build()
                wm.enqueue(midnightReq)
            }
        }

        fun enqueueImmediate(context: Context) {
            runCatching {
                val req = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
                WorkManager.getInstance(context.applicationContext).enqueue(req)
            }
        }
    }
}

class MasroufiAppWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateSingleWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) {
            MasroufiWidgetStore.deleteWidgetStyle(context, id)
        }
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.aistudio.masroufi.ACTION_QUICK_ADD"
        const val ACTION_QUICK_VOICE = "com.aistudio.masroufi.ACTION_QUICK_VOICE"
        const val ACTION_QUICK_PRESET = "com.aistudio.masroufi.ACTION_QUICK_PRESET"
        const val EXTRA_PRESET_NAME = "extra_preset_name"
        const val EXTRA_PRESET_AMOUNT = "extra_preset_amount"
        const val EXTRA_PRESET_ENVELOPE = "extra_preset_envelope"
        const val EXTRA_TARGET_TAB = "extra_target_tab"

        fun updateAllWidgets(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, MasroufiAppWidget::class.java))
                for (id in ids) {
                    updateSingleWidget(context, manager, id)
                }
            }
        }

        internal fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val snapshot = MasroufiWidgetStore.loadSnapshot(context)
            val style = MasroufiWidgetStore.loadWidgetStyle(context, appWidgetId)
            val strings = Translations.forLang(snapshot.language)
            val cur = snapshot.currencySymbol.ifBlank { "DH" }
            val mask = style.shouldMaskAmounts(snapshot.hideAmounts, snapshot.appLockEnabled)

            val options = runCatching { appWidgetManager.getAppWidgetOptions(appWidgetId) }.getOrNull()
            val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250
            val minHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) ?: 110
            val is1x1Cell = minWidthDp < 100 && minHeightDp < 100
            val isCompactHeight = minHeightDp < 95 || style.density == "compact"

            val views = RemoteViews(context.packageName, R.layout.masroufi_app_widget)

            // 1. Apply background drawable (4 Glass opacity levels or solid Light/Dark/Accent)
            val bgRes = if (style.glassEnabled) {
                when (style.glassOpacityLevel.coerceIn(1, 4)) {
                    1 -> R.drawable.bg_widget_glass_lvl1
                    2 -> R.drawable.bg_widget_glass_lvl2
                    3 -> R.drawable.bg_widget_glass_lvl3
                    else -> R.drawable.bg_widget_glass_lvl4
                }
            } else {
                when (style.themeMode) {
                    "light" -> R.drawable.bg_widget_solid_light
                    "accent" -> R.drawable.bg_widget_solid_accent
                    "auto" -> if (snapshot.isDarkTheme) R.drawable.bg_widget_solid_dark else R.drawable.bg_widget_solid_light
                    else -> R.drawable.bg_widget_solid_dark
                }
            }
            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)

            val isLightSolid = !style.glassEnabled &&
                (style.themeMode == "light" || (style.themeMode == "auto" && !snapshot.isDarkTheme))
            val primaryTextColor = if (isLightSolid) Color.parseColor("#0F172A") else Color.WHITE
            val secondaryTextColor = if (isLightSolid) Color.parseColor("#475569") else Color.parseColor("#CBD5E1")
            views.setTextColor(R.id.widget_title, secondaryTextColor)
            views.setTextColor(R.id.widget_today_spend, primaryTextColor)
            views.setTextColor(R.id.widget_secondary_line, secondaryTextColor)

            // 2. Populate content based on configured WidgetKind (or 1x1 responsive cell size)
            val effectiveKind = if (is1x1Cell) WidgetKind.MIC_1X1 else style.widgetKind
            var targetTab = "vue"

            when (effectiveKind) {
                WidgetKind.MIC_1X1 -> {
                    views.setTextViewText(R.id.widget_title, "🎤")
                    views.setTextViewText(R.id.widget_safe_badge, "")
                    views.setTextViewText(R.id.widget_today_spend, strings.widgetVoiceBtn)
                    views.setViewVisibility(R.id.widget_secondary_line, View.GONE)
                    views.setViewVisibility(R.id.widget_action_row, View.GONE)
                }

                WidgetKind.LEFT_TODAY -> {
                    val safeStr = PayCycleEngine.formatCurrencyOrMask(snapshot.safeToSpendToday, cur, mask)
                    val spentStr = PayCycleEngine.formatCurrencyOrMask(snapshot.effectiveTodaySpend(), cur, mask)
                    val statusHex = when (snapshot.alertLevel) {
                        "RED" -> "#EF4444"
                        "AMBER" -> "#F59E0B"
                        else -> "#10B981"
                    }
                    views.setTextViewText(R.id.widget_title, strings.widgetLeftTodayTitle)
                    views.setTextViewText(R.id.widget_safe_badge, "${strings.widgetSpentTodayTitle}: $spentStr")
                    views.setTextColor(R.id.widget_safe_badge, Color.parseColor(statusHex))
                    views.setTextViewText(R.id.widget_today_spend, safeStr)
                    views.setTextColor(R.id.widget_today_spend, Color.parseColor(statusHex))
                    if (snapshot.pendingDetectionsCount > 0) {
                        views.setViewVisibility(R.id.widget_secondary_line, View.VISIBLE)
                        views.setTextViewText(
                            R.id.widget_secondary_line,
                            strings.bankDetectPendingChipFmt.replace("{count}", snapshot.pendingDetectionsCount.toString())
                        )
                    } else {
                        views.setViewVisibility(R.id.widget_secondary_line, View.GONE)
                    }
                    views.setViewVisibility(R.id.widget_action_row, if (isCompactHeight) View.GONE else View.VISIBLE)
                    views.setViewVisibility(R.id.widget_btn_preset_two, View.GONE)
                }

                WidgetKind.QUICK_ADD -> {
                    val p1 = snapshot.presets.getOrNull(0)
                    val p2 = snapshot.presets.getOrNull(1)
                    views.setTextViewText(R.id.widget_title, strings.widgetHeaderToday)
                    views.setTextViewText(R.id.widget_safe_badge, MasroufiWidgetStore.getSafeTodayFormatted(context, style))
                    views.setTextViewText(R.id.widget_today_spend, MasroufiWidgetStore.getTodaySpendFormatted(context, style))
                    views.setViewVisibility(R.id.widget_secondary_line, View.GONE)
                    views.setViewVisibility(R.id.widget_action_row, View.VISIBLE)
                    if (p1 != null) {
                        views.setTextViewText(R.id.widget_btn_add_expense, "+ ${p1.name} ${PayCycleEngine.formatDh(p1.amount)}")
                    } else {
                        views.setTextViewText(R.id.widget_btn_add_expense, strings.widgetQuickAddBtn)
                    }
                    if (p2 != null) {
                        views.setViewVisibility(R.id.widget_btn_preset_two, View.VISIBLE)
                        views.setTextViewText(R.id.widget_btn_preset_two, "+ ${p2.name} ${PayCycleEngine.formatDh(p2.amount)}")
                    } else {
                        views.setViewVisibility(R.id.widget_btn_preset_two, View.GONE)
                    }
                }

                WidgetKind.ENVELOPES -> {
                    targetTab = "categories"
                    val candidates = if (style.onlyOver90Envelopes) {
                        snapshot.envelopes.filter { it.isOver90Percent }.ifEmpty { snapshot.envelopes.take(2) }
                    } else if (style.selectedEnvelopeKeys.isNotEmpty()) {
                        snapshot.envelopes.filter { it.key in style.selectedEnvelopeKeys }.ifEmpty { snapshot.envelopes.take(2) }
                    } else {
                        snapshot.envelopes.sortedByDescending { it.usagePercent }.take(2)
                    }
                    val topEnv = candidates.firstOrNull()
                    val secondEnv = candidates.getOrNull(1)
                    views.setTextViewText(R.id.widget_title, strings.widgetEnvelopesTitle)
                    views.setTextViewText(
                        R.id.widget_safe_badge,
                        if (topEnv != null && topEnv.hasCap) "${topEnv.usagePercent}%" else ""
                    )
                    views.setTextViewText(
                        R.id.widget_today_spend,
                        if (topEnv != null) {
                            "${topEnv.name}: ${PayCycleEngine.formatCurrencyOrMask(topEnv.spent, cur, mask)}"
                        } else MasroufiWidgetStore.getTodaySpendFormatted(context, style)
                    )
                    if (secondEnv != null) {
                        views.setViewVisibility(R.id.widget_secondary_line, View.VISIBLE)
                        val pctSuffix = if (secondEnv.hasCap) " (${secondEnv.usagePercent}%)" else ""
                        views.setTextViewText(
                            R.id.widget_secondary_line,
                            "${secondEnv.name}: ${PayCycleEngine.formatCurrencyOrMask(secondEnv.spent, cur, mask)}$pctSuffix"
                        )
                    } else {
                        views.setViewVisibility(R.id.widget_secondary_line, View.GONE)
                    }
                    views.setViewVisibility(R.id.widget_action_row, if (isCompactHeight) View.GONE else View.VISIBLE)
                    views.setViewVisibility(R.id.widget_btn_preset_two, View.GONE)
                }

                WidgetKind.BILLS_DARET -> {
                    targetTab = "categories"
                    val firstRem = snapshot.reminders.firstOrNull()
                    views.setTextViewText(R.id.widget_title, strings.widgetBillsDaretTitle)
                    views.setTextViewText(
                        R.id.widget_safe_badge,
                        firstRem?.let { "J-${it.dueWithinDays.coerceAtLeast(0)}" } ?: "✓"
                    )
                    views.setTextViewText(
                        R.id.widget_today_spend,
                        if (firstRem != null) {
                            "${firstRem.title} · ${PayCycleEngine.formatCurrencyOrMask(firstRem.amount, cur, mask)}"
                        } else MasroufiWidgetStore.getSafeTodayFormatted(context, style)
                    )
                    views.setViewVisibility(R.id.widget_secondary_line, if (firstRem != null) View.VISIBLE else View.GONE)
                    views.setTextViewText(R.id.widget_secondary_line, firstRem?.subtitle.orEmpty())
                    views.setViewVisibility(R.id.widget_action_row, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_btn_preset_two, View.GONE)
                }

                WidgetKind.GOAL_CYCLE -> {
                    targetTab = "semaine"
                    val goal = snapshot.primaryGoal
                    views.setTextViewText(R.id.widget_title, strings.widgetGoalCycleTitle)
                    views.setTextViewText(
                        R.id.widget_safe_badge,
                        if (snapshot.noSpendStreakDays > 0) "🔥 ${snapshot.noSpendStreakDays}j" else "${snapshot.daysRemainingInCycle}j"
                    )
                    views.setTextViewText(
                        R.id.widget_today_spend,
                        if (goal != null) {
                            "${goal.name} · ${goal.progressPercent}%"
                        } else {
                            "${PayCycleEngine.formatCurrencyOrMask(snapshot.cycleExpenses, cur, mask)} / ${PayCycleEngine.formatCurrencyOrMask(snapshot.totalAvailableBudget, cur, mask)}"
                        }
                    )
                    views.setViewVisibility(R.id.widget_secondary_line, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_secondary_line,
                        snapshot.monthEndForecastMessage.ifBlank {
                            MasroufiWidgetStore.getSafeTodayFormatted(context, style)
                        }
                    )
                    views.setViewVisibility(R.id.widget_action_row, if (isCompactHeight) View.GONE else View.VISIBLE)
                    views.setViewVisibility(R.id.widget_btn_preset_two, View.GONE)
                }

                WidgetKind.SMART_SLOT -> {
                    val smartCard = SmartSlotEngine.resolveSmartSlot(
                        snapshot = snapshot,
                        style = style
                    )
                    targetTab = smartCard.targetTabKey
                    views.setTextViewText(R.id.widget_title, smartCard.badgeTitle)
                    views.setTextViewText(R.id.widget_safe_badge, MasroufiWidgetStore.getSafeTodayFormatted(context, style))
                    views.setTextColor(R.id.widget_safe_badge, Color.parseColor(smartCard.statusColorHex))
                    views.setTextViewText(R.id.widget_today_spend, smartCard.headlineValue)
                    views.setViewVisibility(R.id.widget_secondary_line, View.VISIBLE)
                    views.setTextViewText(R.id.widget_secondary_line, smartCard.subtitle)
                    views.setViewVisibility(R.id.widget_action_row, if (isCompactHeight) View.GONE else View.VISIBLE)
                    views.setViewVisibility(R.id.widget_btn_preset_two, View.GONE)
                }
            }

            if (effectiveKind != WidgetKind.QUICK_ADD && effectiveKind != WidgetKind.MIC_1X1) {
                views.setTextViewText(R.id.widget_btn_add_expense, strings.widgetQuickAddBtn)
            }
            views.setTextViewText(R.id.widget_btn_voice_darija, "🎤 ${strings.widgetVoiceBtn}")

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            // Root click: For 1x1 Mic widget, opens Voice QuickCapture directly; otherwise opens MainActivity at targetTab
            if (effectiveKind == WidgetKind.MIC_1X1) {
                val micIntent = Intent(context, QuickCaptureActivity::class.java).apply {
                    action = ACTION_QUICK_VOICE
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val micPending = PendingIntent.getActivity(context, 1000 + appWidgetId, micIntent, flags)
                views.setOnClickPendingIntent(R.id.widget_root, micPending)
            } else {
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra(EXTRA_TARGET_TAB, targetTab)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val openAppPending = PendingIntent.getActivity(context, 1000 + appWidgetId, openAppIntent, flags)
                views.setOnClickPendingIntent(R.id.widget_root, openAppPending)
            }

            // Button 1: Either logs Preset #1 (in QUICK_ADD mode) or opens QuickCaptureActivity in Keypad mode
            val btn1Intent = if (effectiveKind == WidgetKind.QUICK_ADD && snapshot.presets.isNotEmpty()) {
                val p1 = snapshot.presets[0]
                Intent(context, QuickCaptureActivity::class.java).apply {
                    action = ACTION_QUICK_PRESET
                    putExtra(EXTRA_PRESET_NAME, p1.name)
                    putExtra(EXTRA_PRESET_AMOUNT, p1.amount)
                    putExtra(EXTRA_PRESET_ENVELOPE, p1.envelopeKey)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } else {
                Intent(context, QuickCaptureActivity::class.java).apply {
                    action = ACTION_QUICK_ADD
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
            val btn1Pending = PendingIntent.getActivity(context, 2000 + appWidgetId, btn1Intent, flags)
            views.setOnClickPendingIntent(R.id.widget_btn_add_expense, btn1Pending)

            // Button 2 (Preset #2 in QUICK_ADD mode)
            if (effectiveKind == WidgetKind.QUICK_ADD && snapshot.presets.size >= 2) {
                val p2 = snapshot.presets[1]
                val btn2Intent = Intent(context, QuickCaptureActivity::class.java).apply {
                    action = ACTION_QUICK_PRESET
                    putExtra(EXTRA_PRESET_NAME, p2.name)
                    putExtra(EXTRA_PRESET_AMOUNT, p2.amount)
                    putExtra(EXTRA_PRESET_ENVELOPE, p2.envelopeKey)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val btn2Pending = PendingIntent.getActivity(context, 3000 + appWidgetId, btn2Intent, flags)
                views.setOnClickPendingIntent(R.id.widget_btn_preset_two, btn2Pending)
            }

            // Voice button: Opens QuickCaptureActivity in Moonshine Voice mode
            val voiceIntent = Intent(context, QuickCaptureActivity::class.java).apply {
                action = ACTION_QUICK_VOICE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val voicePending = PendingIntent.getActivity(context, 4000 + appWidgetId, voiceIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_btn_voice_darija, voicePending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

/**
 * Tile 1 (Existing service name preserved): Quick Keypad Add + Today's Spend subtitle.
 */
class MasroufiQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val snap = MasroufiWidgetStore.loadSnapshot(this)
        val strings = Translations.forLang(snap.language)
        tile.state = Tile.STATE_ACTIVE
        tile.label = strings.tileKeypadLabel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = MasroufiWidgetStore.getTodaySpendFormatted(this)
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        launchQuickCaptureFromTile(this, MasroufiAppWidget.ACTION_QUICK_ADD, 201)
    }
}

/**
 * Tile 2 (Phase 3): Moonshine Voice Add Tile — opens the translucent voice overlay directly from the shade.
 */
class MasroufiVoiceTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val snap = MasroufiWidgetStore.loadSnapshot(this)
        val strings = Translations.forLang(snap.language)
        tile.state = Tile.STATE_ACTIVE
        tile.label = strings.tileVoiceLabel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = strings.widgetVoiceBtn
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        launchQuickCaptureFromTile(this, MasroufiAppWidget.ACTION_QUICK_VOICE, 202)
    }
}

/**
 * Tile 3 (Phase 3): Left Today (Safe-to-Spend) Tile — subtitle shows safeToSpendToday respecting hideAmounts.
 */
class MasroufiSafeTodayTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val snap = MasroufiWidgetStore.loadSnapshot(this)
        val strings = Translations.forLang(snap.language)
        tile.state = Tile.STATE_ACTIVE
        tile.label = strings.tileLeftTodayLabel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = MasroufiWidgetStore.getSafeTodayFormatted(this)
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        launchQuickCaptureFromTile(this, MasroufiAppWidget.ACTION_QUICK_VOICE, 203)
    }
}

internal fun launchQuickCaptureFromTile(
    service: TileService,
    action: String,
    requestCode: Int
) {
    val intent = Intent(service, QuickCaptureActivity::class.java).apply {
        this.action = action
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                service,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            service.startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            service.startActivityAndCollapse(intent)
        }
    }
}

/**
 * Phase 3: Prompts the user to add one of the 3 Masroufi Quick Settings tiles on Android 13+ (TIRAMISU).
 * Returns true if the system dialog was requested, or false on Android 12 and below.
 */
fun requestAddMasroufiTile(
    context: Context,
    tileServiceClass: Class<out TileService>,
    label: String,
    onResultMessage: (String) -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val sbm = context.getSystemService(StatusBarManager::class.java)
        if (sbm != null) {
            sbm.requestAddTileService(
                ComponentName(context, tileServiceClass),
                label,
                Icon.createWithResource(context, R.mipmap.ic_launcher_round),
                context.mainExecutor
            ) { resultCode ->
                val msg = if (resultCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    resultCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                ) {
                    "✓ $label"
                } else {
                    label
                }
                onResultMessage(msg)
            }
            return
        }
    }
    onResultMessage("Ajoutez « $label » depuis le volet des raccourcis rapides Android.")
}
