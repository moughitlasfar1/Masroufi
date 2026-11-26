package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.FinancialItem
import com.example.data.MasroufiRepository
import com.example.data.TransactionItem
import com.example.domain.PayCycleEngine
import com.example.domain.Translations
import com.example.ui.DashboardState
import com.example.widget.MasroufiAppWidget
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.masroufi.core.Money
import java.math.BigDecimal
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

object MasroufiNotificationManager {
    private const val PREFS_NAME = "masroufi_notifications_prefs"
    private const val KEY_ENABLED_BILLS = "notif_bills_enabled"
    private const val KEY_ENABLED_DARET = "notif_daret_enabled"
    private const val KEY_ENABLED_BUDGET_80 = "notif_budget_80_enabled"
    private const val KEY_ENABLED_EVENING = "notif_evening_enabled"
    private const val KEY_EVENING_HOUR = "notif_evening_hour"
    private const val KEY_EVENING_MINUTE = "notif_evening_minute"
    private const val KEY_ADAPTIVE_EVENING_HOUR = "notif_adaptive_evening_hour"
    private const val KEY_ADAPTIVE_EVENING_MINUTE = "notif_adaptive_evening_minute"
    private const val KEY_LAST_EVENING_SHOWN_ISO = "notif_last_evening_shown_iso"
    private const val KEY_NO_SPEND_CONFIRMED_ISO = "notif_no_spend_confirmed_iso"
    private const val KEY_VERIFIED_ACTIVE_DATES = "notif_verified_active_dates_csv"
    private const val KEY_EVENING_IGNORED_STREAK = "notif_evening_ignored_streak"
    private const val KEY_SMART_SUGGESTIONS_ENABLED = "notif_smart_suggestions_enabled"
    private const val KEY_SHORTCUT_EDIT_HISTORY_JSON = "notif_shortcut_edit_history_json"
    private const val KEY_SHORTCUT_AMOUNT_OVERRIDES_JSON = "notif_shortcut_amount_overrides_json"
    private const val KEY_SNAPSHOT_JSON = "notif_snapshot_json"

    const val DEFAULT_EVENING_HOUR = 20
    const val DEFAULT_EVENING_MINUTE = 30

    const val CHANNEL_BILLS_DARET = "masroufi_channel_bills_daret"
    const val CHANNEL_BUDGET_80 = "masroufi_channel_budget_80"
    const val CHANNEL_EVENING = "masroufi_channel_evening"
    const val CHANNEL_BANK_DETECTED = "masroufi_channel_bank_detected"
    private const val BANK_DETECTED_GROUP_KEY = "com.aistudio.masroufi.BANK_DETECTED_GROUP"
    private const val PERIODIC_WORK_NAME = "masroufi_smart_alerts_periodic"
    const val EVENING_WORK_NAME = "masroufi_evening_exact_reminder"

    fun isBillsAlertEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED_BILLS, true)

    fun setBillsAlertEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED_BILLS, enabled).apply()
    }

    fun isNotifyBillsEnabled(context: Context): Boolean = isBillsAlertEnabled(context)
    fun setNotifyBillsEnabled(context: Context, enabled: Boolean) = setBillsAlertEnabled(context, enabled)

    fun isDaretAlertEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED_DARET, true)

    fun setDaretAlertEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED_DARET, enabled).apply()
    }

    fun isNotifyDaretEnabled(context: Context): Boolean = isDaretAlertEnabled(context)
    fun setNotifyDaretEnabled(context: Context, enabled: Boolean) = setDaretAlertEnabled(context, enabled)

    fun isBudget80AlertEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED_BUDGET_80, true)

    fun setBudget80AlertEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED_BUDGET_80, enabled).apply()
    }

    fun isNotifyBudget80Enabled(context: Context): Boolean = isBudget80AlertEnabled(context)
    fun setNotifyBudget80Enabled(context: Context, enabled: Boolean) = setBudget80AlertEnabled(context, enabled)

    fun isEveningReminderEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED_EVENING, true)

    fun setEveningReminderEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED_EVENING, enabled).apply()
        scheduleNextEveningReminder(context)
    }

    fun isNotifyEveningEnabled(context: Context): Boolean = isEveningReminderEnabled(context)
    fun setNotifyEveningEnabled(context: Context, enabled: Boolean) = setEveningReminderEnabled(context, enabled)

    fun getEveningReminderTime(context: Context): Pair<Int, Int> {
        val p = prefs(context)
        val h = p.getInt(KEY_EVENING_HOUR, DEFAULT_EVENING_HOUR).coerceIn(0, 23)
        val m = p.getInt(KEY_EVENING_MINUTE, DEFAULT_EVENING_MINUTE).coerceIn(0, 59)
        return h to m
    }

    fun getEveningReminderTimeFormatted(context: Context): String {
        val (h, m) = getEveningReminderTime(context)
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    fun setEveningReminderTime(context: Context, hour: Int, minute: Int) {
        val clampedHour = hour.coerceIn(0, 23)
        val clampedMinute = minute.coerceIn(0, 59)
        prefs(context).edit()
            .putInt(KEY_EVENING_HOUR, clampedHour)
            .putInt(KEY_EVENING_MINUTE, clampedMinute)
            .apply()
        scheduleNextEveningReminder(context)
    }

    fun getLastEveningShownIso(context: Context): String =
        prefs(context).getString(KEY_LAST_EVENING_SHOWN_ISO, "").orEmpty()

    fun setLastEveningShownIso(context: Context, isoDate: String) {
        prefs(context).edit().putString(KEY_LAST_EVENING_SHOWN_ISO, isoDate.trim()).apply()
    }

    fun getNoSpendConfirmedIso(context: Context): String =
        prefs(context).getString(KEY_NO_SPEND_CONFIRMED_ISO, "").orEmpty()

    fun isNoSpendConfirmedForDate(context: Context, isoDate: String = PayCycleEngine.todayIso()): Boolean =
        getNoSpendConfirmedIso(context) == isoDate.trim()

    fun recordVerifiedActiveDate(context: Context, isoDate: String = PayCycleEngine.todayIso()): Set<String> {
        val clean = isoDate.trim()
        if (!PayCycleEngine.isValidIsoDate(clean)) return getVerifiedActiveDates(context)
        val existing = getVerifiedActiveDates(context)
        val updated = (existing + clean).sorted().takeLast(120).toSet()
        prefs(context).edit()
            .putString(KEY_VERIFIED_ACTIVE_DATES, updated.joinToString(","))
            .apply()
        return updated
    }

    fun getVerifiedActiveDates(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_VERIFIED_ACTIVE_DATES, "").orEmpty()
        val confirmed = getNoSpendConfirmedIso(context)
        val parsed = raw.split(",")
            .map { it.trim() }
            .filter { PayCycleEngine.isValidIsoDate(it) }
            .toMutableSet()
        if (PayCycleEngine.isValidIsoDate(confirmed)) {
            parsed.add(confirmed)
        }
        return parsed
    }

    fun isSmartSuggestionsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SMART_SUGGESTIONS_ENABLED, true)

    fun setSmartSuggestionsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SMART_SUGGESTIONS_ENABLED, enabled).apply()
        runCatching {
            val snap = com.example.widget.MasroufiWidgetStore.loadSnapshot(context)
            com.example.widget.MasroufiWidgetStore.saveWidgetSnapshot(
                context,
                snap.copy(smartSuggestionsEnabled = enabled, updatedAtMillis = System.currentTimeMillis())
            )
        }
        scheduleNextEveningReminder(context)
    }

    fun resetAllSmartLearning(context: Context) {
        PayCycleEngine.clearLearnedCategories()
        com.example.security.AppSecurityManager.saveLearnedCategoriesJson(context, "")
        prefs(context).edit()
            .remove(KEY_ADAPTIVE_EVENING_HOUR)
            .remove(KEY_ADAPTIVE_EVENING_MINUTE)
            .remove(KEY_EVENING_IGNORED_STREAK)
            .remove(KEY_SHORTCUT_EDIT_HISTORY_JSON)
            .remove(KEY_SHORTCUT_AMOUNT_OVERRIDES_JSON)
            .apply()
        scheduleNextEveningReminder(context)
    }

    fun getEveningIgnoredStreak(context: Context): Int =
        prefs(context).getInt(KEY_EVENING_IGNORED_STREAK, 0).coerceAtLeast(0)

    fun incrementEveningIgnoredStreak(context: Context): Int {
        val next = (getEveningIgnoredStreak(context) + 1).coerceAtMost(99)
        prefs(context).edit().putInt(KEY_EVENING_IGNORED_STREAK, next).apply()
        return next
    }

    fun resetEveningIgnoredStreak(context: Context) {
        prefs(context).edit().putInt(KEY_EVENING_IGNORED_STREAK, 0).apply()
    }

    /**
     * Prompt 5.2: After 3 ignored reminders in a row, send only every other day.
     * Returns true when today should be skipped because the reminder was shown yesterday.
     */
    fun shouldThrottleEveningForIgnoredStreak(
        ignoredStreak: Int,
        lastEveningShownIso: String,
        todayIso: String = PayCycleEngine.todayIso()
    ): Boolean {
        if (ignoredStreak < 3) return false
        val cleanLast = lastEveningShownIso.trim()
        val cleanToday = todayIso.trim()
        if (!PayCycleEngine.isValidIsoDate(cleanLast) || !PayCycleEngine.isValidIsoDate(cleanToday)) return false
        val daysDelta = PayCycleEngine.parseEpochDays(cleanToday) - PayCycleEngine.parseEpochDays(cleanLast)
        return daysDelta == 1L
    }

    fun shouldThrottleEveningForIgnoredStreak(
        context: Context,
        todayIso: String = PayCycleEngine.todayIso()
    ): Boolean = shouldThrottleEveningForIgnoredStreak(
        ignoredStreak = getEveningIgnoredStreak(context),
        lastEveningShownIso = getLastEveningShownIso(context),
        todayIso = todayIso
    )

    /**
     * Prompt 5.1 & 5.4: Computes the adaptive evening reminder time:
     * - Finds the median hour of the last expense of the day over the last 30 days (using hourOfDay).
     * - Sends the reminder 1 hour after that, clamped to 18:00–22:30.
     * - Falls back to the user's chosen time if there is too little data (< [minActiveDays]) or when
     *   [smartSuggestionsEnabled] is false.
     */
    fun computeAdaptiveEveningReminderTime(
        transactions: List<TransactionItem>,
        todayIso: String = PayCycleEngine.todayIso(),
        fallbackHour: Int = DEFAULT_EVENING_HOUR,
        fallbackMinute: Int = DEFAULT_EVENING_MINUTE,
        smartSuggestionsEnabled: Boolean = true,
        minActiveDays: Int = 5
    ): Pair<Int, Int> {
        val safeFallback = fallbackHour.coerceIn(0, 23) to fallbackMinute.coerceIn(0, 59)
        if (!smartSuggestionsEnabled || !PayCycleEngine.isValidIsoDate(todayIso)) {
            return safeFallback
        }
        val todayEpoch = PayCycleEngine.parseEpochDays(todayIso)
        val validExpenses = transactions.mapNotNull { tx ->
            if (tx.type != "expense" || !tx.amount.isPositive || !PayCycleEngine.isValidIsoDate(tx.isoDate)) {
                return@mapNotNull null
            }
            val ageDays = (todayEpoch - PayCycleEngine.parseEpochDays(tx.isoDate)).toInt()
            if (ageDays !in 0..30) return@mapNotNull null
            val resolvedHour = tx.resolvedHourOfDay()
            if (resolvedHour !in 0..23) return@mapNotNull null
            tx.isoDate to resolvedHour
        }

        val lastHourByDay = validExpenses
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, hours) -> hours.maxOrNull() ?: 0 }
            .values
            .sorted()

        if (lastHourByDay.size < minActiveDays) {
            return safeFallback
        }

        val medianHour = if (lastHourByDay.size % 2 == 1) {
            lastHourByDay[lastHourByDay.size / 2]
        } else {
            val mid = lastHourByDay.size / 2
            ((lastHourByDay[mid - 1] + lastHourByDay[mid]) / 2.0).roundToInt()
        }

        // Send 1 hour after the median last-expense hour, clamped to 18:00 (1080m) .. 22:30 (1350m)
        val targetTotalMinutes = ((medianHour + 1) * 60).coerceIn(18 * 60, 22 * 60 + 30)
        return (targetTotalMinutes / 60) to (targetTotalMinutes % 60)
    }

    fun computeAdaptiveEveningReminderTime(
        context: Context,
        allTransactions: List<TransactionItem>,
        nowIso: String = PayCycleEngine.todayIso()
    ): Pair<Int, Int> {
        val (fbH, fbM) = getEveningReminderTime(context)
        return computeAdaptiveEveningReminderTime(
            transactions = allTransactions,
            todayIso = nowIso,
            fallbackHour = fbH,
            fallbackMinute = fbM,
            smartSuggestionsEnabled = isSmartSuggestionsEnabled(context)
        )
    }

    fun getEffectiveEveningReminderTime(context: Context): Pair<Int, Int> {
        val fallback = getEveningReminderTime(context)
        if (!isSmartSuggestionsEnabled(context)) return fallback
        val p = prefs(context)
        val adaptiveH = p.getInt(KEY_ADAPTIVE_EVENING_HOUR, -1)
        val adaptiveM = p.getInt(KEY_ADAPTIVE_EVENING_MINUTE, -1)
        return if (adaptiveH in 0..23 && adaptiveM in 0..59) {
            adaptiveH to adaptiveM
        } else {
            fallback
        }
    }

    private fun parseJsonMoney(raw: Any?, default: Money = Money.ZERO): Money = when (raw) {
        null, JSONObject.NULL -> default
        is BigDecimal -> runCatching { Money.fromMajorUnits(raw) }.getOrDefault(default)
        is Number -> runCatching { Money.fromMajorUnits(BigDecimal(raw.toString())) }
            .getOrElse { runCatching { Money.fromLegacyDoubleDh(raw.toDouble()) }.getOrDefault(default) }
        is String -> Money.fromDhString(raw) ?: default
        else -> default
    }

    /**
     * Prompt 7.4: If the user edits a pre-filled shortcut amount 3 times, use the edited amount
     * as the new suggestion. Stored locally in SharedPreferences.
     */
    fun getShortcutAmountOverrides(context: Context): Map<String, Money> {
        val raw = prefs(context).getString(KEY_SHORTCUT_AMOUNT_OVERRIDES_JSON, null) ?: return emptyMap()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<String, Money>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = parseJsonMoney(obj.opt(k), Money.ZERO)
            if (k.isNotBlank() && v.isPositive) {
                result[k] = v
            }
        }
        return result
    }

    fun recordShortcutAmountEdit(
        context: Context,
        desc: String,
        suggestedAmount: Money,
        savedAmount: Money
    ): Money? {
        val normKey = desc.trim().lowercase(Locale.ROOT)
        if (normKey.isEmpty() || !savedAmount.isPositive || savedAmount == suggestedAmount) {
            return getShortcutAmountOverrides(context)[normKey]
        }

        val p = prefs(context)
        val historyObj = runCatching {
            JSONObject(p.getString(KEY_SHORTCUT_EDIT_HISTORY_JSON, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        val existingArr = historyObj.optJSONArray(normKey) ?: JSONArray()
        val edits = mutableListOf<Money>()
        for (i in 0 until existingArr.length()) {
            val v = parseJsonMoney(existingArr.opt(i), Money.ZERO)
            if (v.isPositive) edits.add(v)
        }
        edits.add(savedAmount)
        val trimmedEdits = edits.takeLast(12)
        val nextArr = JSONArray()
        trimmedEdits.forEach { nextArr.put(it.toBigDecimalDh()) }
        historyObj.put(normKey, nextArr)

        var learnedOverride: Money? = null
        val overridesObj = runCatching {
            JSONObject(p.getString(KEY_SHORTCUT_AMOUNT_OVERRIDES_JSON, "{}") ?: "{}")
        }.getOrDefault(JSONObject())

        if (trimmedEdits.size >= 3) {
            // Pick the mode of the edited amounts, tie-broken by the most recent edit
            val grouped = trimmedEdits.withIndex().groupBy({ it.value }, { it.index })
            val bestAmount = grouped.entries.sortedWith(
                compareByDescending<Map.Entry<Money, List<Int>>> { it.value.size }
                    .thenByDescending { it.value.maxOrNull() ?: 0 }
            ).first().key
            learnedOverride = bestAmount
            overridesObj.put(normKey, bestAmount.toBigDecimalDh())
        }

        p.edit()
            .putString(KEY_SHORTCUT_EDIT_HISTORY_JSON, historyObj.toString())
            .putString(KEY_SHORTCUT_AMOUNT_OVERRIDES_JSON, overridesObj.toString())
            .apply()

        return learnedOverride
    }

    fun confirmNoSpendToday(context: Context, todayIso: String = PayCycleEngine.todayIso()) {
        val clean = todayIso.trim()
        prefs(context).edit()
            .putString(KEY_NO_SPEND_CONFIRMED_ISO, clean)
            .putString(KEY_LAST_EVENING_SHOWN_ISO, clean)
            .putInt(KEY_EVENING_IGNORED_STREAK, 0)
            .apply()
        recordVerifiedActiveDate(context, clean)
        runCatching {
            val snap = com.example.widget.MasroufiWidgetStore.loadSnapshot(context, clean)
            if (!snap.effectiveTodaySpend(clean).isPositive) {
                val nextStreak = snap.noSpendStreakDays.coerceAtLeast(1)
                com.example.widget.MasroufiWidgetStore.saveWidgetSnapshot(
                    context,
                    snap.copy(noSpendStreakDays = nextStreak, updatedAtMillis = System.currentTimeMillis())
                )
            }
        }
    }

    fun computeDelayMillisUntilNextEveningReminder(
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val targetCal = (nowCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (targetCal.timeInMillis <= nowMillis + 10_000L) {
            targetCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return (targetCal.timeInMillis - nowMillis).coerceAtLeast(60_000L)
    }

    fun scheduleNextEveningReminder(context: Context) {
        runCatching {
            val appCtx = context.applicationContext
            val wm = WorkManager.getInstance(appCtx)
            if (!isEveningReminderEnabled(appCtx)) {
                wm.cancelUniqueWork(EVENING_WORK_NAME)
                return
            }
            ensureChannels(appCtx)
            val (hour, minute) = getEffectiveEveningReminderTime(appCtx)
            val delayMs = computeDelayMillisUntilNextEveningReminder(hour, minute)
            val req = OneTimeWorkRequestBuilder<EveningReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniqueWork(
                EVENING_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val chBills = NotificationChannel(
                CHANNEL_BILLS_DARET,
                "Factures & Tours Daret",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Rappels d'échéances des factures récurrentes et cotisations Daret"
            }
            val chBudget = NotificationChannel(
                CHANNEL_BUDGET_80,
                "Alertes Budget 80%",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerte lorsqu'une enveloppe ou le budget dépasse 80%"
            }
            val chEvening = NotificationChannel(
                CHANNEL_EVENING,
                "Rappel du soir",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Rappel quotidien du soir pour noter vos dépenses de la journée"
            }
            val chBankDetected = NotificationChannel(
                CHANNEL_BANK_DETECTED,
                "Paiements bancaires détectés",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Alertes discrètes lorsqu'un paiement bancaire ou SMS est prêt à confirmer"
            }
            nm.createNotificationChannels(listOf(chBills, chBudget, chEvening, chBankDetected))
        }
    }

    /**
     * Caches a compact alert evaluation snapshot whenever DashboardState updates and schedules WorkManager.
     */
    fun syncStateAndScheduleWork(context: Context, state: DashboardState) {
        runCatching {
            val todayIso = PayCycleEngine.todayIso()
            val todayDayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            val cycleKey = state.activeWindow.cycleKey
            val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
            val hide = state.hideAmounts

            val upcomingBillsArr = JSONArray()
            state.bills
                .filter { !PayCycleEngine.isBillPaidInCycle(it, cycleKey) }
                .filter { bill ->
                    val diff = bill.dayOrTurn - todayDayOfMonth
                    diff in -2..5
                }
                .forEach { bill ->
                    upcomingBillsArr.put(
                        JSONObject().apply {
                            put("id", bill.id)
                            put("name", bill.name)
                            put("subtitle", bill.subtitle)
                            put("amount", bill.amount.toBigDecimalDh())
                            put("dueDay", bill.dayOrTurn)
                            put("dateOrCycle", bill.dateOrCycle)
                        }
                    )
                }

            val activeDaretsArr = JSONArray()
            state.darets.forEach { daret ->
                val totalMembers = daret.resolvedDaretMembers()
                if (daret.extraInt < totalMembers) {
                    activeDaretsArr.put(
                        JSONObject().apply {
                            put("id", daret.id)
                            put("name", daret.name)
                            put("subtitle", daret.subtitle)
                            put("amount", daret.amount.toBigDecimalDh())
                            put("myTurn", daret.dayOrTurn)
                            put("paidRounds", daret.extraInt)
                            put("totalMembers", totalMembers)
                            put("dateOrCycle", daret.dateOrCycle)
                        }
                    )
                }
            }

            val over80EnvelopesArr = JSONArray()
            state.envelopes
                .filter { it.config.hasCap && it.usagePercent >= 80 }
                .forEach { env ->
                    over80EnvelopesArr.put(
                        JSONObject().apply {
                            put("name", env.config.localizedName(state.profile.language))
                            put("pct", env.usagePercent)
                            put("spent", env.spent.toBigDecimalDh())
                        }
                    )
                }

            val todayTxCount = state.allTransactions.count { it.isoDate == todayIso }
            if (todayTxCount > 0) {
                resetEveningIgnoredStreak(context)
            }
            val totalUsagePct = if (state.totalAvailableBudget.isPositive) {
                ((state.cycleExpenses.centimes.toDouble() / state.totalAvailableBudget.centimes.toDouble()) * 100.0).roundToInt()
            } else 0

            val (userHour, userMinute) = getEveningReminderTime(context)
            val smartEnabled = isSmartSuggestionsEnabled(context)
            val (adaptiveHour, adaptiveMinute) = computeAdaptiveEveningReminderTime(
                transactions = state.allTransactions,
                todayIso = todayIso,
                fallbackHour = userHour,
                fallbackMinute = userMinute,
                smartSuggestionsEnabled = smartEnabled
            )
            val pendingBankCount = runCatching { BankDetectionStore.loadPendingItems(context).size }.getOrDefault(0)

            val snapshot = JSONObject().apply {
                put("lang", state.profile.language)
                put("currency", cur)
                put("cycleKey", cycleKey)
                put("hideAmounts", hide)
                put("safeToday", state.safeToSpendToday.toBigDecimalDh())
                put("todayTxCount", todayTxCount)
                put("pendingBankCount", pendingBankCount)
                put("totalUsagePct", totalUsagePct)
                put("upcomingBills", upcomingBillsArr)
                put("activeDarets", activeDaretsArr)
                put("over80Envelopes", over80EnvelopesArr)
            }

            saveNotificationSnapshotRaw(context, snapshot.toString())
            prefs(context).edit()
                .putInt(KEY_ADAPTIVE_EVENING_HOUR, adaptiveHour)
                .putInt(KEY_ADAPTIVE_EVENING_MINUTE, adaptiveMinute)
                .apply()
            recordVerifiedActiveDate(context, todayIso)
            schedulePeriodicWorker(context)
            scheduleNextEveningReminder(context)
        }
    }

    internal fun saveNotificationSnapshotRaw(context: Context, json: String) {
        val stored = com.example.security.SecureStore.putString(context, KEY_SNAPSHOT_JSON, json)
        // Always scrub legacy plaintext copy once encrypted storage succeeds
        if (stored) {
            prefs(context).edit().remove(KEY_SNAPSHOT_JSON).apply()
        }
    }

    internal fun loadNotificationSnapshotRaw(context: Context): String? {
        return com.example.security.SecureStore.migrateStringFromLegacyPrefs(
            context = context,
            legacyPrefsName = PREFS_NAME,
            legacyKey = KEY_SNAPSHOT_JSON,
            secureKey = KEY_SNAPSHOT_JSON
        )
    }

    fun schedulePeriodicWorker(context: Context) {
        runCatching {
            ensureChannels(context)
            val request = PeriodicWorkRequestBuilder<MasroufiNotificationWorker>(6, TimeUnit.HOURS)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    fun triggerImmediateCheck(context: Context): Int {
        ensureChannels(context)
        runCatching {
            val oneTime = OneTimeWorkRequestBuilder<MasroufiNotificationWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueue(oneTime)
            scheduleNextEveningReminder(context)
        }
        return evaluateAndPostNotifications(context, forceEveningReminder = true)
    }

    internal fun evaluateAndPostNotifications(
        context: Context,
        forceEveningReminder: Boolean = false,
        fromScheduledEveningWorker: Boolean = false
    ): Int {
        if (!hasNotificationPermission(context)) return 0
        ensureChannels(context)

        val raw = loadNotificationSnapshotRaw(context) ?: return 0
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return 0

        val lang = obj.optString("lang", "fr")
        val cur = obj.optString("currency", "DH")
        val hide = obj.optBoolean("hideAmounts", false) ||
            com.example.security.AppSecurityManager.isAppLockEnabled(context)
        var postedCount = 0

        val strings = Translations.forLang(lang)
        val cycleKey = obj.optString("cycleKey", PayCycleEngine.currentCycleKey(1))

        // 1. Upcoming Bills
        if (isBillsAlertEnabled(context)) {
            val bills = obj.optJSONArray("upcomingBills")
            if (bills != null && bills.length() > 0) {
                val first = bills.getJSONObject(0)
                val billId = first.optString("id", "")
                val name = first.optString("name", "Facture")
                val rawAmt = parseJsonMoney(first.opt("amount"), Money.ZERO)
                val amt = PayCycleEngine.formatCurrencyOrMask(rawAmt, cur, hide)
                val day = first.optInt("dueDay", 1)
                val title = when (lang) {
                    "ar" -> "تذكير فاتورة قريباً: $name"
                    "darija" -> "فكر فـ الخلاص ديال: $name"
                    "en" -> "Upcoming Bill: $name"
                    else -> "Facture à venir : $name"
                }
                val body = when (lang) {
                    "ar" -> "تستحق يوم $day من الشهر ($amt)."
                    "darija" -> "الخلاص نهار $day فالشهر ($amt)."
                    "en" -> "Due on day $day ($amt). Tap to review."
                    else -> "Échéance le $day du mois ($amt). Touchez pour régler."
                }
                val actionIntent = if (billId.isNotBlank()) {
                    Intent(context, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_MARK_BILL_PAID
                        putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, 4001)
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, billId)
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_NAME, name)
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_SUBTITLE, first.optString("subtitle", name))
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_AMOUNT_CENTIMES, rawAmt.centimes)
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_AMOUNT, rawAmt.toBigDecimalDh().toDouble())
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_DUE_DAY, day)
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_DATE_OR_CYCLE, first.optString("dateOrCycle", ""))
                        putExtra(NotificationActionReceiver.EXTRA_CYCLE_KEY, cycleKey)
                    }
                } else null
                val paidAction = actionIntent?.let { actIntent ->
                    val actPi = PendingIntent.getBroadcast(
                        context,
                        5001,
                        actIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    NotificationCompat.Action.Builder(0, strings.widgetPaidAction, actPi).build()
                }
                postNotification(context, 4001, CHANNEL_BILLS_DARET, title, body, openQuickAdd = false, notifAction = paidAction)
                postedCount++
            }
        }

        // 2. Daret Turns
        if (isDaretAlertEnabled(context)) {
            val darets = obj.optJSONArray("activeDarets")
            if (darets != null && darets.length() > 0) {
                val first = darets.getJSONObject(0)
                val daretId = first.optString("id", "")
                val name = first.optString("name", "Daret")
                val rawAmt = parseJsonMoney(first.opt("amount"), Money.ZERO)
                val amt = PayCycleEngine.formatCurrencyOrMask(rawAmt, cur, hide)
                val myTurn = first.optInt("myTurn", 1)
                val paid = first.optInt("paidRounds", 0)
                val total = first.optInt("totalMembers", 10)
                val title = when (lang) {
                    "ar" -> "تذكير مساهمة دارت: $name"
                    "darija" -> "نوبة دارت: $name"
                    "en" -> "Daret Turn Reminder: $name"
                    else -> "Suivi Daret : $name"
                }
                val body = when (lang) {
                    "ar" -> "دورك رقم $myTurn · المساهمات $paid/$total ($amt)."
                    "darija" -> "نوبتك رقم $myTurn · خلصتي $paid/$total ($amt)."
                    "en" -> "Your turn #$myTurn · Paid $paid/$total ($amt)."
                    else -> "Votre tour n°$myTurn · Cotisé $paid/$total ($amt)."
                }
                val actionIntent = if (daretId.isNotBlank()) {
                    Intent(context, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_PAY_DARET_ROUND
                        putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, 4002)
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, daretId)
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_NAME, name)
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_SUBTITLE, first.optString("subtitle", ""))
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_AMOUNT_CENTIMES, rawAmt.centimes)
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_AMOUNT, rawAmt.toBigDecimalDh().toDouble())
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_DUE_DAY, myTurn)
                        putExtra(NotificationActionReceiver.EXTRA_DARET_PAID_ROUNDS, paid)
                        putExtra(NotificationActionReceiver.EXTRA_DARET_TOTAL_MEMBERS, total)
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_DATE_OR_CYCLE, first.optString("dateOrCycle", ""))
                    }
                } else null
                val paidAction = actionIntent?.let { actIntent ->
                    val actPi = PendingIntent.getBroadcast(
                        context,
                        5002,
                        actIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    NotificationCompat.Action.Builder(0, strings.widgetPaidAction, actPi).build()
                }
                postNotification(context, 4002, CHANNEL_BILLS_DARET, title, body, openQuickAdd = false, notifAction = paidAction)
                postedCount++
            }
        }

        // 3. 80% Budget Alert
        if (isBudget80AlertEnabled(context)) {
            val envs = obj.optJSONArray("over80Envelopes")
            val totalPct = obj.optInt("totalUsagePct", 0)
            if (envs != null && envs.length() > 0) {
                val first = envs.getJSONObject(0)
                val envName = first.optString("name", "Budget")
                val pct = first.optInt("pct", 80)
                val title = when (lang) {
                    "ar" -> "تنبيه الميزانية 80%: $envName ($pct%)"
                    "darija" -> "رد بالك! وصلتي $pct% فـ $envName"
                    "en" -> "80% Budget Alert: $envName ($pct%)"
                    else -> "Alerte Budget 80% : $envName ($pct%)"
                }
                val body = when (lang) {
                    "ar" -> "اقتربت هذه الفئة من السقف المحدد لهذا الشهر."
                    "darija" -> "هاد الغلاف قرب يتسالا فهاد الشهر. راقب الصرف اليومي."
                    "en" -> "This envelope has reached $pct% of its monthly cap."
                    else -> "Cette enveloppe a atteint $pct% de son plafond mensuel."
                }
                postNotification(context, 4003, CHANNEL_BUDGET_80, title, body, openQuickAdd = false)
                postedCount++
            } else if (totalPct >= 80) {
                val title = when (lang) {
                    "ar" -> "تنبيه: استهلكت $totalPct% من ميزانية الشهر"
                    "darija" -> "رد بالك: صرفتي $totalPct% من ميزانية الشهر"
                    "en" -> "Budget Alert: $totalPct% of monthly budget used"
                    else -> "Alerte Budget : $totalPct% du budget mensuel utilisé"
                }
                val body = when (lang) {
                    "ar" -> "راجع المتاح للصرف اليوم للحفاظ على توازن الشهر."
                    "darija" -> "شوف شحال تقدر تصرف فـ النهار باش تكمل الشهر مرتاح."
                    "en" -> "Check your Safe-to-Spend daily allowance to stay on track."
                    else -> "Vérifiez votre Safe-to-Spend journalier pour finir le mois sereinement."
                }
                postNotification(context, 4003, CHANNEL_BUDGET_80, title, body, openQuickAdd = false)
                postedCount++
            }
        }

        // 4. Evening Reminder (adaptive or user-chosen time, or forced via test button; at most once per day)
        val todayIso = PayCycleEngine.todayIso()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val (targetHour, _) = getEffectiveEveningReminderTime(context)
        val todayTxCount = obj.optInt("todayTxCount", 0)
        val safeToday = parseJsonMoney(obj.opt("safeToday"), Money.ZERO)
        val lastShownIso = getLastEveningShownIso(context)
        val alreadyShownToday = lastShownIso == todayIso
        val alreadyConfirmedNothing = isNoSpendConfirmedForDate(context, todayIso)
        val ignoredStreak = getEveningIgnoredStreak(context)
        val throttledByIgnoredStreak = shouldThrottleEveningForIgnoredStreak(
            ignoredStreak = ignoredStreak,
            lastEveningShownIso = lastShownIso,
            todayIso = todayIso
        )
        val isWithinEveningWindow = fromScheduledEveningWorker || hour in 18..23 || hour == targetHour

        val shouldPostEvening = isEveningReminderEnabled(context) && (
            forceEveningReminder || (
                isWithinEveningWindow &&
                    !alreadyShownToday &&
                    !alreadyConfirmedNothing &&
                    !throttledByIgnoredStreak &&
                    todayTxCount == 0
                )
            )

        if (shouldPostEvening) {
            val title = when (lang) {
                "ar" -> "تذكير المساء · هل سجلت مصاريف اليوم؟"
                "darija" -> "تذكير العشية · واش قيدتي مصاريف اليوم؟"
                "en" -> "Evening Reminder · Log today's expenses"
                else -> "Rappel du soir · Notez vos dépenses du jour"
            }
            val safeFormatted = PayCycleEngine.formatAmountOrMask(safeToday, hide)
            val baseBody = strings.eveningNotifBodyWithSafeFmt
                .replace("{safe}", safeFormatted)
                .replace("{cur}", cur)

            val pendingBankCount = runCatching {
                BankDetectionStore.loadPendingItems(context).size
            }.getOrDefault(0).coerceAtLeast(obj.optInt("pendingBankCount", 0))

            val body = if (pendingBankCount > 0) {
                baseBody + strings.eveningNotifPendingBankSuffixFmt.replace("{count}", pendingBankCount.toString())
            } else {
                baseBody
            }

            val addExpenseIntent = Intent(context, com.example.widget.QuickCaptureActivity::class.java).apply {
                action = MasroufiAppWidget.ACTION_QUICK_ADD
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val addExpensePi = PendingIntent.getActivity(
                context,
                5004,
                addExpenseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val addExpenseAction = NotificationCompat.Action.Builder(
                0,
                strings.eveningNotifAddExpenseAction,
                addExpensePi
            ).build()

            val nothingTodayIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_CONFIRM_NO_SPEND_TODAY
                putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, 4004)
            }
            val nothingTodayPi = PendingIntent.getBroadcast(
                context,
                5005,
                nothingTodayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nothingTodayAction = NotificationCompat.Action.Builder(
                0,
                strings.eveningNotifNothingTodayAction,
                nothingTodayPi
            ).build()

            val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_DISMISS_EVENING_REMINDER
                putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, 4004)
            }
            val dismissPi = PendingIntent.getBroadcast(
                context,
                5006,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val customContentIntent = if (pendingBankCount > 0) {
                Intent(context, MainActivity::class.java).apply {
                    putExtra(com.example.widget.MasroufiAppWidget.EXTRA_TARGET_TAB, "bank_detections")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } else null

            postNotification(
                context = context,
                id = 4004,
                channelId = CHANNEL_EVENING,
                title = title,
                body = body,
                openQuickAdd = pendingBankCount == 0,
                notifAction = addExpenseAction,
                secondNotifAction = nothingTodayAction,
                deletePendingIntent = dismissPi,
                overrideContentIntent = customContentIntent
            )
            setLastEveningShownIso(context, todayIso)
            postedCount++
        }

        return postedCount
    }

    internal fun removeBillOrAdvanceDaretInCachedSnapshot(
        context: Context,
        itemId: String,
        isBill: Boolean
    ) {
        if (itemId.isBlank()) return
        val raw = loadNotificationSnapshotRaw(context) ?: return
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (isBill) {
            val arr = obj.optJSONArray("upcomingBills") ?: return
            val filtered = JSONArray()
            for (i in 0 until arr.length()) {
                val entry = arr.optJSONObject(i) ?: continue
                if (entry.optString("id") != itemId) filtered.put(entry)
            }
            obj.put("upcomingBills", filtered)
        } else {
            val arr = obj.optJSONArray("activeDarets") ?: return
            val updated = JSONArray()
            for (i in 0 until arr.length()) {
                val entry = arr.optJSONObject(i) ?: continue
                if (entry.optString("id") == itemId) {
                    val total = entry.optInt("totalMembers", 10).coerceAtLeast(1)
                    val nextPaid = (entry.optInt("paidRounds", 0) + 1).coerceAtMost(total)
                    if (nextPaid < total) {
                        entry.put("paidRounds", nextPaid)
                        updated.put(entry)
                    }
                } else {
                    updated.put(entry)
                }
            }
            obj.put("activeDarets", updated)
        }
        saveNotificationSnapshotRaw(context, obj.toString())
    }

    /**
     * S10: Builds a lock-screen-safe public notification containing zero amounts, balances,
     * or merchant names so sensitive financial data never leaks on locked screens.
     */
    internal fun buildPublicLockScreenNotification(
        context: Context,
        channelId: String,
        lang: String = "fr"
    ): android.app.Notification {
        val publicTitle = "Masroufi"
        val publicBody = when (lang) {
            "ar" -> "لديك تنبيه مالي جديد. افتح التطبيق للاطلاع."
            "darija" -> "عندك تنبيه جديد فـ مصروفي. حل التطبيق باش تشوف."
            "en" -> "New Masroufi update. Unlock to view."
            else -> "Nouvelle notification Masroufi. Déverrouillez pour voir."
        }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(publicTitle)
            .setContentText(publicBody)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /**
     * D9: Posts a quiet, grouped notification when a new bank transaction is queued in "To confirm",
     * with Confirm, Edit, and Ignore actions and VISIBILITY_PRIVATE lock-screen redaction.
     */
    fun postBankDetectedNotification(
        context: Context,
        item: DetectedBankTransaction
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        runCatching {
            val snap = com.example.widget.MasroufiWidgetStore.loadSnapshot(context)
            val lang = snap.language
            val strings = Translations.forLang(lang)
            val hide = snap.hideAmounts || com.example.security.AppSecurityManager.isAppLockEnabled(context)
            val cur = item.currency.ifBlank { snap.currencySymbol.ifBlank { "DH" } }
            val amtText = PayCycleEngine.formatAmountOrMask(item.amount, hide)
            val merchantText = item.merchant.ifBlank { item.suggestedCategoryName }

            val title = when (lang) {
                "ar" -> "تم رصد عملية بنكية"
                "darija" -> "تكتاشفات عملية بنكية"
                "en" -> "Payment detected"
                else -> "Paiement détecté"
            }
            val body = "$merchantText · $amtText $cur (${item.suggestedCategoryName})"
            val notifId = 4500 + (item.id.hashCode() and 0x1FF)

            val editIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MasroufiAppWidget.EXTRA_TARGET_TAB, "bank_detections")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val editPi = PendingIntent.getActivity(
                context,
                notifId,
                editIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val confirmIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_BANK_CONFIRM
                putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
                putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, item.id)
            }
            val confirmPi = PendingIntent.getBroadcast(
                context,
                notifId + 1000,
                confirmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val ignoreIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_BANK_IGNORE
                putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
                putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, item.id)
            }
            val ignorePi = PendingIntent.getBroadcast(
                context,
                notifId + 2000,
                ignoreIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, CHANNEL_BANK_DETECTED)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setGroup(BANK_DETECTED_GROUP_KEY)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(buildPublicLockScreenNotification(context, CHANNEL_BANK_DETECTED, lang))
                .setAutoCancel(true)
                .setContentIntent(editPi)
                .addAction(0, strings.bankDetectConfirmBtn, confirmPi)
                .addAction(0, strings.bankDetectEditBtn, editPi)
                .addAction(0, strings.bankDetectIgnoreBtn, ignorePi)
                .build()

            NotificationManagerCompat.from(context).notify(notifId, notif)
        }
    }

    /**
     * D8: Posts a 10-minute Undo notification after an auto-confirmed bank transaction.
     */
    fun postAutoConfirmedUndoNotification(
        context: Context,
        item: DetectedBankTransaction,
        createdTx: TransactionItem
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        runCatching {
            val snap = com.example.widget.MasroufiWidgetStore.loadSnapshot(context)
            val lang = snap.language
            val strings = Translations.forLang(lang)
            val hide = snap.hideAmounts || com.example.security.AppSecurityManager.isAppLockEnabled(context)
            val cur = item.currency.ifBlank { snap.currencySymbol.ifBlank { "DH" } }
            val amtText = PayCycleEngine.formatAmountOrMask(item.amount, hide)
            val label = item.merchant.ifBlank { item.suggestedCategoryName }
            val notifId = 4800 + (createdTx.id.hashCode() and 0xFF)

            val undoIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_BANK_UNDO_AUTO
                putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
                putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, createdTx.id)
                putExtra(NotificationActionReceiver.EXTRA_ITEM_AMOUNT_CENTIMES, createdTx.amount.centimes)
                putExtra(NotificationActionReceiver.EXTRA_ITEM_AMOUNT, createdTx.amount.toBigDecimalDh().toDouble())
                putExtra(NotificationActionReceiver.EXTRA_ITEM_NAME, createdTx.desc)
                putExtra(NotificationActionReceiver.EXTRA_ITEM_DATE_OR_CYCLE, createdTx.isoDate)
            }
            val undoPi = PendingIntent.getBroadcast(
                context,
                notifId + 3000,
                undoIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val openIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val openPi = PendingIntent.getActivity(
                context,
                notifId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = when (lang) {
                "ar" -> "تم تسجيل عملية تلقائياً"
                "darija" -> "تقيدات عملية تلقائياً"
                "en" -> "Auto-confirmed payment"
                else -> "Paiement auto-confirmé"
            }
            val body = "$label · $amtText $cur"

            val notif = NotificationCompat.Builder(context, CHANNEL_BANK_DETECTED)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTimeoutAfter(10L * 60L * 1000L) // 10-minute Undo window
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(buildPublicLockScreenNotification(context, CHANNEL_BANK_DETECTED, lang))
                .setAutoCancel(true)
                .setContentIntent(openPi)
                .addAction(0, strings.undoLabel, undoPi)
                .build()

            NotificationManagerCompat.from(context).notify(notifId, notif)
        }
    }

    internal fun buildAppAlertNotification(
        context: Context,
        id: Int,
        channelId: String,
        title: String,
        body: String,
        openQuickAdd: Boolean,
        notifAction: NotificationCompat.Action? = null,
        secondNotifAction: NotificationCompat.Action? = null,
        deletePendingIntent: PendingIntent? = null,
        overrideContentIntent: Intent? = null,
        lang: String = "fr"
    ): android.app.Notification {
        val intent = overrideContentIntent ?: if (openQuickAdd) {
            Intent(context, com.example.widget.QuickCaptureActivity::class.java).apply {
                action = MasroufiAppWidget.ACTION_QUICK_ADD
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // S10: Redact all bill, daret, budget, and evening reminder details on the lock screen
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(buildPublicLockScreenNotification(context, channelId, lang))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (deletePendingIntent != null) {
            builder.setDeleteIntent(deletePendingIntent)
        }
        if (notifAction != null) {
            builder.addAction(notifAction)
        }
        if (secondNotifAction != null) {
            builder.addAction(secondNotifAction)
        }
        return builder.build()
    }

    private fun postNotification(
        context: Context,
        id: Int,
        channelId: String,
        title: String,
        body: String,
        openQuickAdd: Boolean,
        notifAction: NotificationCompat.Action? = null,
        secondNotifAction: NotificationCompat.Action? = null,
        deletePendingIntent: PendingIntent? = null,
        overrideContentIntent: Intent? = null
    ) {
        if (!hasNotificationPermission(context)) return
        runCatching {
            val lang = runCatching {
                com.example.widget.MasroufiWidgetStore.loadSnapshot(context).language
            }.getOrDefault("fr")
            val notif = buildAppAlertNotification(
                context = context,
                id = id,
                channelId = channelId,
                title = title,
                body = body,
                openQuickAdd = openQuickAdd,
                notifAction = notifAction,
                secondNotifAction = secondNotifAction,
                deletePendingIntent = deletePendingIntent,
                overrideContentIntent = overrideContentIntent,
                lang = lang
            )
            NotificationManagerCompat.from(context).notify(id, notif)
        }
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_MARK_BILL_PAID = "com.aistudio.masroufi.ACTION_MARK_BILL_PAID"
        const val ACTION_PAY_DARET_ROUND = "com.aistudio.masroufi.ACTION_PAY_DARET_ROUND"
        const val ACTION_CONFIRM_NO_SPEND_TODAY = "com.aistudio.masroufi.ACTION_CONFIRM_NO_SPEND_TODAY"
        const val ACTION_DISMISS_EVENING_REMINDER = "com.aistudio.masroufi.ACTION_DISMISS_EVENING_REMINDER"
        const val ACTION_BANK_CONFIRM = "com.aistudio.masroufi.ACTION_BANK_CONFIRM"
        const val ACTION_BANK_IGNORE = "com.aistudio.masroufi.ACTION_BANK_IGNORE"
        const val ACTION_BANK_UNDO_AUTO = "com.aistudio.masroufi.ACTION_BANK_UNDO_AUTO"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_ITEM_NAME = "extra_item_name"
        const val EXTRA_ITEM_SUBTITLE = "extra_item_subtitle"
        const val EXTRA_ITEM_AMOUNT = "extra_item_amount"
        const val EXTRA_ITEM_AMOUNT_CENTIMES = "extra_item_amount_centimes"
        const val EXTRA_ITEM_DUE_DAY = "extra_item_due_day"
        const val EXTRA_ITEM_DATE_OR_CYCLE = "extra_item_date_or_cycle"
        const val EXTRA_CYCLE_KEY = "extra_cycle_key"
        const val EXTRA_DARET_PAID_ROUNDS = "extra_daret_paid_rounds"
        const val EXTRA_DARET_TOTAL_MEMBERS = "extra_daret_total_members"

        private fun extractAmountMoney(intent: Intent): Money {
            if (intent.hasExtra(EXTRA_ITEM_AMOUNT_CENTIMES)) {
                return Money.fromCentimes(intent.getLongExtra(EXTRA_ITEM_AMOUNT_CENTIMES, 0L))
            }
            val legacy = intent.getDoubleExtra(EXTRA_ITEM_AMOUNT, 0.0)
            return if (legacy.isFinite()) Money.fromLegacyDoubleDh(legacy) else Money.ZERO
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appCtx = context.applicationContext

        if (action == ACTION_DISMISS_EVENING_REMINDER) {
            MasroufiNotificationManager.incrementEveningIgnoredStreak(appCtx)
            return
        }

        if (action == ACTION_CONFIRM_NO_SPEND_TODAY) {
            val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 4004)
            MasroufiNotificationManager.confirmNoSpendToday(appCtx, PayCycleEngine.todayIso())
            runCatching {
                NotificationManagerCompat.from(appCtx).cancel(notifId)
            }
            return
        }

        if (action == ACTION_BANK_CONFIRM) {
            val itemId = intent.getStringExtra(EXTRA_ITEM_ID).orEmpty()
            val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 4500)
            if (itemId.isNotBlank()) {
                val pendingItem = BankDetectionStore.findPendingItemById(appCtx, itemId)
                if (pendingItem != null) {
                    BankDetectionStore.confirmDetectedItem(appCtx, pendingItem, isAutoConfirmed = false)
                }
            }
            runCatching { NotificationManagerCompat.from(appCtx).cancel(notifId) }
            return
        }

        if (action == ACTION_BANK_IGNORE) {
            val itemId = intent.getStringExtra(EXTRA_ITEM_ID).orEmpty()
            val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 4500)
            if (itemId.isNotBlank()) {
                BankDetectionStore.removePendingItem(appCtx, itemId)
            }
            runCatching { NotificationManagerCompat.from(appCtx).cancel(notifId) }
            return
        }

        if (action == ACTION_BANK_UNDO_AUTO) {
            val txId = intent.getStringExtra(EXTRA_ITEM_ID).orEmpty()
            val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 4800)
            val amount = extractAmountMoney(intent)
            val desc = intent.getStringExtra(EXTRA_ITEM_NAME).orEmpty()
            val isoDate = intent.getStringExtra(EXTRA_ITEM_DATE_OR_CYCLE).orEmpty().ifBlank { PayCycleEngine.todayIso() }
            if (txId.isNotBlank()) {
                com.example.widget.MasroufiWidgetStore.undoQuickCaptureBatch(
                    context = appCtx,
                    createdTxs = listOf(
                        TransactionItem(
                            id = txId,
                            type = "expense",
                            amount = amount,
                            desc = desc,
                            isoDate = isoDate
                        )
                    )
                )
            }
            runCatching { NotificationManagerCompat.from(appCtx).cancel(notifId) }
            return
        }

        if (action != ACTION_MARK_BILL_PAID && action != ACTION_PAY_DARET_ROUND) return
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID).orEmpty()
        if (itemId.isBlank()) return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, if (action == ACTION_MARK_BILL_PAID) 4001 else 4002)
        val name = intent.getStringExtra(EXTRA_ITEM_NAME).orEmpty().ifBlank { "Opération" }
        val subtitle = intent.getStringExtra(EXTRA_ITEM_SUBTITLE).orEmpty()
        val amount = extractAmountMoney(intent).coerceAtLeast(Money.fromDh(1))
        val dueDayOrTurn = intent.getIntExtra(EXTRA_ITEM_DUE_DAY, 1).coerceIn(1, 365)
        val existingDateOrCycle = intent.getStringExtra(EXTRA_ITEM_DATE_OR_CYCLE).orEmpty()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isBill = action == ACTION_MARK_BILL_PAID
                MasroufiNotificationManager.removeBillOrAdvanceDaretInCachedSnapshot(
                    context = appCtx,
                    itemId = itemId,
                    isBill = isBill
                )
                com.example.widget.MasroufiWidgetStore.markBillOrDaretPaidInSnapshot(
                    context = appCtx,
                    itemId = itemId,
                    kind = if (isBill) "bill" else "daret"
                )
                runCatching {
                    NotificationManagerCompat.from(appCtx).cancel(notifId)
                }

                val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull().orEmpty().ifBlank { MasroufiRepository.LOCAL_DEVICE_USER_ID }
                val todayIso = PayCycleEngine.todayIso()
                val (updatedItem, tx) = if (isBill) {
                    val cycleKey = intent.getStringExtra(EXTRA_CYCLE_KEY).orEmpty().ifBlank {
                        PayCycleEngine.currentCycleKey(1, todayIso)
                    }
                    val updatedCycles = (existingDateOrCycle.split(",").filter { it.isNotBlank() } + cycleKey)
                        .distinct()
                        .joinToString(",")
                        .take(250)
                    val bill = FinancialItem(
                        id = itemId,
                        userId = uid,
                        itemType = "bill",
                        name = name.take(120),
                        subtitle = subtitle.take(250),
                        amount = amount,
                        secondaryAmount = Money.ZERO,
                        dayOrTurn = dueDayOrTurn,
                        extraInt = 0,
                        dateOrCycle = updatedCycles
                    )
                    val billTx = TransactionItem(
                        id = "tx_bill_${itemId}_$cycleKey",
                        userId = uid,
                        type = "expense",
                        amount = amount,
                        category = "charges",
                        envelopeKey = "charges",
                        accountId = "acc_bank",
                        toAccountId = "",
                        desc = name.take(250),
                        isoDate = todayIso
                    )
                    bill to billTx
                } else {
                    val totalMembers = intent.getIntExtra(EXTRA_DARET_TOTAL_MEMBERS, 10).coerceAtLeast(1)
                    val prevPaid = intent.getIntExtra(EXTRA_DARET_PAID_ROUNDS, 0).coerceAtLeast(0)
                    val nextPaid = (prevPaid + 1).coerceAtMost(totalMembers)
                    val daret = FinancialItem(
                        id = itemId,
                        userId = uid,
                        itemType = "daret",
                        name = name.take(120),
                        subtitle = subtitle.take(250),
                        amount = amount,
                        secondaryAmount = Money.ZERO,
                        daretMembers = totalMembers,
                        dayOrTurn = dueDayOrTurn,
                        extraInt = nextPaid,
                        dateOrCycle = existingDateOrCycle.take(250)
                    )
                    val daretTx = TransactionItem(
                        id = "tx_daret_${itemId}_round_$nextPaid",
                        userId = uid,
                        type = "expense",
                        amount = amount,
                        category = "epargne",
                        envelopeKey = "epargne",
                        accountId = "acc_bank",
                        toAccountId = "",
                        desc = "Cotisation $name ($nextPaid/$totalMembers)".take(250),
                        isoDate = todayIso
                    )
                    daret to daretTx
                }

                runCatching {
                    val roomDb = com.example.data.local.MasroufiDatabaseProvider.getOpenInstanceOrNull()
                        ?: if (com.example.MasroufiApplication.isSqlCipherNativeLoaded) {
                            com.example.data.local.MasroufiDatabaseProvider.getOrOpen(appCtx)
                        } else null
                    if (roomDb != null) {
                        val repo = com.example.data.local.RoomMasroufiRepository(
                            dao = roomDb.masroufiDao(),
                            remoteMirror = com.example.data.local.NoOpRemoteMirror,
                            userIdProvider = { uid }
                        )
                        withTimeoutOrNull(2500L) {
                            repo.upsertItemAndTransactionAtomically(updatedItem, tx, userId = uid)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class MasroufiNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Also normalize midnight rollover and refresh widgets & tiles every 6 hours
        val snapshot = com.example.widget.MasroufiWidgetStore.loadSnapshot(applicationContext)
        com.example.widget.MasroufiWidgetStore.saveWidgetSnapshot(applicationContext, snapshot)
        MasroufiNotificationManager.evaluateAndPostNotifications(applicationContext)
        return Result.success()
    }
}

class EveningReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        MasroufiNotificationManager.evaluateAndPostNotifications(
            context = applicationContext,
            forceEveningReminder = false,
            fromScheduledEveningWorker = true
        )
        MasroufiNotificationManager.scheduleNextEveningReminder(applicationContext)
        return Result.success()
    }
}
