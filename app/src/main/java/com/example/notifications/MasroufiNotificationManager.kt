package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.domain.PayCycleEngine
import com.example.ui.DashboardState
import com.example.widget.MasroufiAppWidget
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

object MasroufiNotificationManager {
    private const val PREFS_NAME = "masroufi_notifications_prefs"
    private const val KEY_ENABLED_BILLS = "notif_bills_enabled"
    private const val KEY_ENABLED_DARET = "notif_daret_enabled"
    private const val KEY_ENABLED_BUDGET_80 = "notif_budget_80_enabled"
    private const val KEY_ENABLED_EVENING = "notif_evening_enabled"
    private const val KEY_SNAPSHOT_JSON = "notif_snapshot_json"

    const val CHANNEL_BILLS_DARET = "masroufi_channel_bills_daret"
    const val CHANNEL_BUDGET_80 = "masroufi_channel_budget_80"
    const val CHANNEL_EVENING = "masroufi_channel_evening"
    private const val PERIODIC_WORK_NAME = "masroufi_smart_alerts_periodic"

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
    }

    fun isNotifyEveningEnabled(context: Context): Boolean = isEveningReminderEnabled(context)
    fun setNotifyEveningEnabled(context: Context, enabled: Boolean) = setEveningReminderEnabled(context, enabled)

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
            nm.createNotificationChannels(listOf(chBills, chBudget, chEvening))
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
                            put("name", bill.name)
                            put("amount", bill.amount)
                            put("dueDay", bill.dayOrTurn)
                        }
                    )
                }

            val activeDaretsArr = JSONArray()
            state.darets.forEach { daret ->
                val totalMembers = daret.secondaryAmount.roundToInt().coerceAtLeast(1)
                if (daret.extraInt < totalMembers) {
                    activeDaretsArr.put(
                        JSONObject().apply {
                            put("name", daret.name)
                            put("amount", daret.amount)
                            put("myTurn", daret.dayOrTurn)
                            put("paidRounds", daret.extraInt)
                            put("totalMembers", totalMembers)
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
                            put("spent", env.spent)
                        }
                    )
                }

            val todayTxCount = state.allTransactions.count { it.isoDate == todayIso }
            val totalUsagePct = if (state.totalAvailableBudget > 0) {
                ((state.cycleExpenses / state.totalAvailableBudget) * 100.0).roundToInt()
            } else 0

            val snapshot = JSONObject().apply {
                put("lang", state.profile.language)
                put("currency", cur)
                put("hideAmounts", hide)
                put("todayTxCount", todayTxCount)
                put("totalUsagePct", totalUsagePct)
                put("upcomingBills", upcomingBillsArr)
                put("activeDarets", activeDaretsArr)
                put("over80Envelopes", over80EnvelopesArr)
            }

            prefs(context).edit().putString(KEY_SNAPSHOT_JSON, snapshot.toString()).apply()
            schedulePeriodicWorker(context)
        }
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
        }
        return evaluateAndPostNotifications(context, forceEveningReminder = true)
    }

    internal fun evaluateAndPostNotifications(
        context: Context,
        forceEveningReminder: Boolean = false
    ): Int {
        if (!hasNotificationPermission(context)) return 0
        ensureChannels(context)

        val raw = prefs(context).getString(KEY_SNAPSHOT_JSON, null) ?: return 0
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return 0

        val lang = obj.optString("lang", "fr")
        val cur = obj.optString("currency", "DH")
        val hide = obj.optBoolean("hideAmounts", false)
        var postedCount = 0

        // 1. Upcoming Bills
        if (isBillsAlertEnabled(context)) {
            val bills = obj.optJSONArray("upcomingBills")
            if (bills != null && bills.length() > 0) {
                val first = bills.getJSONObject(0)
                val name = first.optString("name", "Facture")
                val amt = PayCycleEngine.formatCurrencyOrMask(first.optDouble("amount", 0.0), cur, hide)
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
                postNotification(context, 4001, CHANNEL_BILLS_DARET, title, body, openQuickAdd = false)
                postedCount++
            }
        }

        // 2. Daret Turns
        if (isDaretAlertEnabled(context)) {
            val darets = obj.optJSONArray("activeDarets")
            if (darets != null && darets.length() > 0) {
                val first = darets.getJSONObject(0)
                val name = first.optString("name", "Daret")
                val amt = PayCycleEngine.formatCurrencyOrMask(first.optDouble("amount", 0.0), cur, hide)
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
                postNotification(context, 4002, CHANNEL_BILLS_DARET, title, body, openQuickAdd = false)
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

        // 4. Evening Reminder (if after 18h or forced via test button)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val todayTxCount = obj.optInt("todayTxCount", 0)
        if (isEveningReminderEnabled(context) && (forceEveningReminder || (hour in 18..22 && todayTxCount == 0))) {
            val title = when (lang) {
                "ar" -> "تذكير المساء · هل سجلت مصاريف اليوم؟"
                "darija" -> "تذكير العشية · واش قيدتي مصاريف اليوم؟"
                "en" -> "Evening Reminder · Log today's expenses"
                else -> "Rappel du soir · Notez vos dépenses du jour"
            }
            val body = when (lang) {
                "ar" -> "اضغط لتسجيل مصروف في ثانيتين (أرقام كبيرة أو بالصوت بالدارجة)."
                "darija" -> "ورك هنا باش تزيد مصروف فـ جوج ضغطات أو بالصوت بالدارجة."
                "en" -> "Tap to log an expense in 2 taps or by voice in Darija."
                else -> "Touchez pour ajouter vos achats en 2 gestes ou à la voix en Darija."
            }
            postNotification(context, 4004, CHANNEL_EVENING, title, body, openQuickAdd = true)
            postedCount++
        }

        return postedCount
    }

    private fun postNotification(
        context: Context,
        id: Int,
        channelId: String,
        title: String,
        body: String,
        openQuickAdd: Boolean
    ) {
        if (!hasNotificationPermission(context)) return
        runCatching {
            val intent = Intent(context, MainActivity::class.java).apply {
                if (openQuickAdd) {
                    action = MasroufiAppWidget.ACTION_QUICK_ADD
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}

class MasroufiNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        MasroufiNotificationManager.evaluateAndPostNotifications(applicationContext)
        return Result.success()
    }
}
