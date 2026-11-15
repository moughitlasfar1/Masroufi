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
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
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
                            put("id", bill.id)
                            put("name", bill.name)
                            put("subtitle", bill.subtitle)
                            put("amount", bill.amount)
                            put("dueDay", bill.dayOrTurn)
                            put("dateOrCycle", bill.dateOrCycle)
                        }
                    )
                }

            val activeDaretsArr = JSONArray()
            state.darets.forEach { daret ->
                val totalMembers = daret.secondaryAmount.roundToInt().coerceAtLeast(1)
                if (daret.extraInt < totalMembers) {
                    activeDaretsArr.put(
                        JSONObject().apply {
                            put("id", daret.id)
                            put("name", daret.name)
                            put("subtitle", daret.subtitle)
                            put("amount", daret.amount)
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
                put("cycleKey", cycleKey)
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

        val strings = Translations.forLang(lang)
        val cycleKey = obj.optString("cycleKey", PayCycleEngine.currentCycleKey(1))

        // 1. Upcoming Bills
        if (isBillsAlertEnabled(context)) {
            val bills = obj.optJSONArray("upcomingBills")
            if (bills != null && bills.length() > 0) {
                val first = bills.getJSONObject(0)
                val billId = first.optString("id", "")
                val name = first.optString("name", "Facture")
                val rawAmt = first.optDouble("amount", 0.0)
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
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_AMOUNT, rawAmt)
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
                val rawAmt = first.optDouble("amount", 0.0)
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
                        putExtra(NotificationActionReceiver.EXTRA_ITEM_AMOUNT, rawAmt)
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

    internal fun removeBillOrAdvanceDaretInCachedSnapshot(
        context: Context,
        itemId: String,
        isBill: Boolean
    ) {
        if (itemId.isBlank()) return
        val raw = prefs(context).getString(KEY_SNAPSHOT_JSON, null) ?: return
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
        prefs(context).edit().putString(KEY_SNAPSHOT_JSON, obj.toString()).apply()
    }

    private fun postNotification(
        context: Context,
        id: Int,
        channelId: String,
        title: String,
        body: String,
        openQuickAdd: Boolean,
        notifAction: NotificationCompat.Action? = null
    ) {
        if (!hasNotificationPermission(context)) return
        runCatching {
            val intent = if (openQuickAdd) {
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
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            if (notifAction != null) {
                builder.addAction(notifAction)
            }

            NotificationManagerCompat.from(context).notify(id, builder.build())
        }
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_MARK_BILL_PAID = "com.aistudio.masroufi.ACTION_MARK_BILL_PAID"
        const val ACTION_PAY_DARET_ROUND = "com.aistudio.masroufi.ACTION_PAY_DARET_ROUND"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_ITEM_NAME = "extra_item_name"
        const val EXTRA_ITEM_SUBTITLE = "extra_item_subtitle"
        const val EXTRA_ITEM_AMOUNT = "extra_item_amount"
        const val EXTRA_ITEM_DUE_DAY = "extra_item_due_day"
        const val EXTRA_ITEM_DATE_OR_CYCLE = "extra_item_date_or_cycle"
        const val EXTRA_CYCLE_KEY = "extra_cycle_key"
        const val EXTRA_DARET_PAID_ROUNDS = "extra_daret_paid_rounds"
        const val EXTRA_DARET_TOTAL_MEMBERS = "extra_daret_total_members"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_MARK_BILL_PAID && action != ACTION_PAY_DARET_ROUND) return

        val appCtx = context.applicationContext
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID).orEmpty()
        if (itemId.isBlank()) return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, if (action == ACTION_MARK_BILL_PAID) 4001 else 4002)
        val name = intent.getStringExtra(EXTRA_ITEM_NAME).orEmpty().ifBlank { "Opération" }
        val subtitle = intent.getStringExtra(EXTRA_ITEM_SUBTITLE).orEmpty()
        val amount = intent.getDoubleExtra(EXTRA_ITEM_AMOUNT, 0.0).coerceAtLeast(1.0)
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
                        amount = PayCycleEngine.roundMoney(amount),
                        secondaryAmount = 0.0,
                        dayOrTurn = dueDayOrTurn,
                        extraInt = 0,
                        dateOrCycle = updatedCycles
                    )
                    val billTx = TransactionItem(
                        id = "tx_bill_${itemId}_$cycleKey",
                        userId = uid,
                        type = "expense",
                        amount = PayCycleEngine.roundMoney(amount),
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
                        amount = PayCycleEngine.roundMoney(amount),
                        secondaryAmount = totalMembers.toDouble(),
                        dayOrTurn = dueDayOrTurn,
                        extraInt = nextPaid,
                        dateOrCycle = existingDateOrCycle.take(250)
                    )
                    val daretTx = TransactionItem(
                        id = "tx_daret_${itemId}_round_$nextPaid",
                        userId = uid,
                        type = "expense",
                        amount = PayCycleEngine.roundMoney(amount),
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
                    val dbId = appCtx.getString(R.string.firestore_database_id)
                    val repo = MasroufiRepository(FirebaseFirestore.getInstance(dbId))
                    if (uid != MasroufiRepository.LOCAL_DEVICE_USER_ID) {
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
