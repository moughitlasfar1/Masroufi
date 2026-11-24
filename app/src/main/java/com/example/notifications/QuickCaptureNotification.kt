package com.example.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.domain.PayCycleEngine
import com.example.domain.Translations
import com.example.widget.MasroufiAppWidget
import com.example.widget.MasroufiWidgetStore
import com.example.widget.QuickCaptureActivity
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Phase 9: Optional silent, ongoing Quick-Capture notification with Voice (🎤), Keypad (＋),
 * and an optional 1-tap Preset action button.
 *
 * - Off by default to keep Masroufi distraction-free.
 * - Low importance channel (`quick_capture`), no sound, no vibration, `setOngoing(true)`,
 *   `setOnlyAlertOnce(true)`, `setShowWhen(false)`.
 * - Lock screen privacy: `VISIBILITY_PRIVATE` with a public version hiding all amounts
 *   unless the user explicitly enables lock-screen amounts.
 * - Quiet hours support with automatic WorkManager transitions.
 */
object QuickCaptureNotification {
    const val CHANNEL_QUICK_CAPTURE = "quick_capture"
    const val NOTIF_ID_QUICK_CAPTURE = 4099

    private const val PREFS_NAME = "masroufi_quick_capture_notif_prefs"
    private const val KEY_ENABLED = "qc_notif_enabled"
    private const val KEY_SHOW_LEFT_TODAY = "qc_notif_show_left_today"
    private const val KEY_LOCK_SCREEN_AMOUNTS = "qc_notif_lock_screen_amounts"
    private const val KEY_QUIET_HOURS_ENABLED = "qc_notif_quiet_hours_enabled"
    private const val KEY_QUIET_START_HOUR = "qc_notif_quiet_start_hour"
    private const val KEY_QUIET_END_HOUR = "qc_notif_quiet_end_hour"
    private const val KEY_SELECTED_PRESET_ID = "qc_notif_selected_preset_id"
    private const val WORK_QUIET_TRANSITION = "masroufi_qc_notif_quiet_transition"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            refresh(context)
        } else {
            cancel(context)
        }
    }

    fun isShowLeftTodayEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_LEFT_TODAY, true)

    fun setShowLeftTodayEnabled(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_LEFT_TODAY, show).apply()
        if (isEnabled(context)) refresh(context)
    }

    fun isShowLockScreenAmountsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCK_SCREEN_AMOUNTS, false)

    fun setShowLockScreenAmountsEnabled(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK_SCREEN_AMOUNTS, show).apply()
        if (isEnabled(context)) refresh(context)
    }

    fun isQuietHoursEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_QUIET_HOURS_ENABLED, false)

    fun setQuietHoursEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_QUIET_HOURS_ENABLED, enabled).apply()
        if (isEnabled(context)) refresh(context)
    }

    fun getQuietStartHour(context: Context): Int =
        prefs(context).getInt(KEY_QUIET_START_HOUR, 22).coerceIn(0, 23)

    fun getQuietEndHour(context: Context): Int =
        prefs(context).getInt(KEY_QUIET_END_HOUR, 7).coerceIn(0, 23)

    fun setQuietHoursWindow(context: Context, startHour: Int, endHour: Int) {
        prefs(context).edit()
            .putInt(KEY_QUIET_START_HOUR, startHour.coerceIn(0, 23))
            .putInt(KEY_QUIET_END_HOUR, endHour.coerceIn(0, 23))
            .apply()
        if (isEnabled(context)) refresh(context)
    }

    fun getSelectedPresetId(context: Context): String =
        prefs(context).getString(KEY_SELECTED_PRESET_ID, "").orEmpty()

    fun setSelectedPresetId(context: Context, presetId: String) {
        prefs(context).edit().putString(KEY_SELECTED_PRESET_ID, presetId).apply()
        if (isEnabled(context)) refresh(context)
    }

    /**
     * Pure helper checking if [currentHour] falls inside `[startHour, endHour)` (supports overnight ranges like 22 -> 7).
     */
    fun isHourInQuietWindow(currentHour: Int, startHour: Int, endHour: Int): Boolean {
        val h = currentHour.coerceIn(0, 23)
        val s = startHour.coerceIn(0, 23)
        val e = endHour.coerceIn(0, 23)
        if (s == e) return false
        return if (s < e) {
            h in s until e
        } else {
            h >= s || h < e
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_QUICK_CAPTURE,
                "Masroufi · Ajout rapide",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Barre silencieuse avec boutons Voix (Moonshine) et Clavier"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Builds the notification text and lock-screen visibility state so Robolectric tests and
     * runtime notification posting share the exact same deterministic logic.
     */
    data class QuickCaptureNotifSpec(
        val title: String,
        val bodyText: String,
        val publicBodyText: String,
        val visibility: Int,
        val actionLabels: List<String>
    )

    fun buildSpec(
        context: Context,
        currentHourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): QuickCaptureNotifSpec? {
        if (!isEnabled(context)) return null
        if (isQuietHoursEnabled(context)) {
            val startH = getQuietStartHour(context)
            val endH = getQuietEndHour(context)
            if (isHourInQuietWindow(currentHourOfDay, startH, endH)) {
                return null
            }
        }
        val snapshot = MasroufiWidgetStore.loadSnapshot(context)
        val strings = Translations.forLang(snapshot.language)
        val cur = snapshot.currencySymbol.ifBlank { "DH" }
        val showLeft = isShowLeftTodayEnabled(context)
        val showLockAmounts = isShowLockScreenAmountsEnabled(context)
        val appLockActive = snapshot.appLockEnabled ||
            com.example.security.AppSecurityManager.isAppLockEnabled(context)
        val effectiveHide = snapshot.hideAmounts || appLockActive

        val bodyText = if (showLeft) {
            val formatted = PayCycleEngine.formatCurrencyOrMask(
                snapshot.safeToSpendToday,
                cur,
                effectiveHide
            )
            "${strings.widgetLeftTodayTitle}: $formatted"
        } else {
            strings.quickCaptureNotifPublicText
        }

        val publicText = strings.quickCaptureNotifPublicText
        val visibility = if (showLockAmounts && !effectiveHide) {
            NotificationCompat.VISIBILITY_PUBLIC
        } else {
            NotificationCompat.VISIBILITY_PRIVATE
        }

        val selectedPresetId = getSelectedPresetId(context)
        val chosenPreset = if (selectedPresetId.isNotBlank()) {
            snapshot.presets.find { it.id == selectedPresetId }
        } else null

        val actions = mutableListOf(
            "🎤 ${strings.widgetVoiceBtn}",
            "＋ ${strings.btnAddAction}"
        )
        if (chosenPreset != null) {
            actions.add("+ ${chosenPreset.name}")
        }

        return QuickCaptureNotifSpec(
            title = "Masroufi",
            bodyText = bodyText,
            publicBodyText = publicText,
            visibility = visibility,
            actionLabels = actions
        )
    }

    fun refresh(
        context: Context,
        currentHourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): Boolean {
        val appCtx = context.applicationContext
        if (!isEnabled(appCtx)) {
            cancel(appCtx)
            return false
        }
        if (!MasroufiNotificationManager.hasNotificationPermission(appCtx)) {
            // Permission was revoked or denied; turn toggle off cleanly
            prefs(appCtx).edit().putBoolean(KEY_ENABLED, false).apply()
            cancel(appCtx)
            return false
        }

        scheduleNextQuietHoursTransition(appCtx)

        val spec = buildSpec(appCtx, currentHourOfDay)
        if (spec == null) {
            // Currently inside quiet hours: hide notification until quiet hours end
            cancelNotificationOnly(appCtx)
            return false
        }

        ensureChannel(appCtx)
        val snapshot = MasroufiWidgetStore.loadSnapshot(appCtx)
        val strings = Translations.forLang(snapshot.language)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        // Content tap opens Voice QuickCapture directly
        val voiceIntent = Intent(appCtx, QuickCaptureActivity::class.java).apply {
            action = MasroufiAppWidget.ACTION_QUICK_VOICE
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val voicePi = PendingIntent.getActivity(appCtx, 9001, voiceIntent, flags)

        val addIntent = Intent(appCtx, QuickCaptureActivity::class.java).apply {
            action = MasroufiAppWidget.ACTION_QUICK_ADD
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val addPi = PendingIntent.getActivity(appCtx, 9002, addIntent, flags)

        // Public lock-screen version without amounts
        val publicNotification: Notification = NotificationCompat.Builder(appCtx, CHANNEL_QUICK_CAPTURE)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Masroufi")
            .setContentText(spec.publicBodyText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .build()

        val builder = NotificationCompat.Builder(appCtx, CHANNEL_QUICK_CAPTURE)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(spec.title)
            .setContentText(spec.bodyText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(spec.visibility)
            .setPublicVersion(publicNotification)
            .setContentIntent(voicePi)
            .addAction(
                R.mipmap.ic_launcher_round,
                "🎤 ${strings.widgetVoiceBtn}",
                voicePi
            )
            .addAction(
                R.mipmap.ic_launcher_round,
                "＋ ${strings.btnAddAction}",
                addPi
            )

        val selectedPresetId = getSelectedPresetId(appCtx)
        val chosenPreset = if (selectedPresetId.isNotBlank()) {
            snapshot.presets.find { it.id == selectedPresetId }
        } else null

        if (chosenPreset != null) {
            val presetIntent = Intent(appCtx, QuickCaptureActivity::class.java).apply {
                action = MasroufiAppWidget.ACTION_QUICK_PRESET
                putExtra(MasroufiAppWidget.EXTRA_PRESET_NAME, chosenPreset.name)
                putExtra(MasroufiAppWidget.EXTRA_PRESET_AMOUNT, chosenPreset.amount)
                putExtra(MasroufiAppWidget.EXTRA_PRESET_ENVELOPE, chosenPreset.envelopeKey)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val presetPi = PendingIntent.getActivity(appCtx, 9003, presetIntent, flags)
            builder.addAction(
                R.mipmap.ic_launcher_round,
                "+ ${chosenPreset.name}",
                presetPi
            )
        }

        runCatching {
            NotificationManagerCompat.from(appCtx).notify(NOTIF_ID_QUICK_CAPTURE, builder.build())
        }
        return true
    }

    fun cancel(context: Context) {
        val appCtx = context.applicationContext
        cancelNotificationOnly(appCtx)
        runCatching {
            WorkManager.getInstance(appCtx).cancelUniqueWork(WORK_QUIET_TRANSITION)
        }
    }

    private fun cancelNotificationOnly(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context.applicationContext).cancel(NOTIF_ID_QUICK_CAPTURE)
        }
    }

    private fun scheduleNextQuietHoursTransition(context: Context) {
        if (!isQuietHoursEnabled(context)) return
        runCatching {
            val now = Calendar.getInstance()
            val curHour = now.get(Calendar.HOUR_OF_DAY)
            val startH = getQuietStartHour(context)
            val endH = getQuietEndHour(context)
            val targetHour = if (isHourInQuietWindow(curHour, startH, endH)) endH else startH

            val nextTransition = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val delayMs = (nextTransition.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
            val req = OneTimeWorkRequestBuilder<QuickCaptureQuietHoursWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_QUIET_TRANSITION,
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }
}

class QuickCaptureQuietHoursWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        QuickCaptureNotification.refresh(applicationContext)
        return Result.success()
    }
}

/**
 * Phase 9: Re-posts the optional quick-capture notification on device reboot or app update
 * when the user has enabled it.
 */
class QuickCaptureBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (QuickCaptureNotification.isEnabled(context)) {
                QuickCaptureNotification.refresh(context)
            }
            if (MasroufiNotificationManager.isEveningReminderEnabled(context)) {
                MasroufiNotificationManager.scheduleNextEveningReminder(context)
            }
        }
    }
}
