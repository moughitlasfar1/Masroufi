package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.domain.PayCycleEngine

object MasroufiWidgetStore {
    private const val PREFS_NAME = "masroufi_widget_prefs"
    private const val KEY_TODAY_SPEND = "today_spend"
    private const val KEY_SAFE_TODAY = "safe_today"
    private const val KEY_CURRENCY = "currency_symbol"
    private const val KEY_HIDE_AMOUNTS = "hide_amounts"
    private const val KEY_LANG = "language"

    fun saveSnapshot(
        context: Context,
        todaySpend: Double,
        safeToSpendToday: Double,
        currencySymbol: String,
        hideAmounts: Boolean,
        language: String
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_TODAY_SPEND, todaySpend.toFloat())
            .putFloat(KEY_SAFE_TODAY, safeToSpendToday.toFloat())
            .putString(KEY_CURRENCY, currencySymbol.ifBlank { "DH" })
            .putBoolean(KEY_HIDE_AMOUNTS, hideAmounts)
            .putString(KEY_LANG, language.ifBlank { "fr" })
            .apply()

        MasroufiAppWidget.updateAllWidgets(context.applicationContext)
        runCatching {
            TileService.requestListeningState(
                context.applicationContext,
                ComponentName(context.applicationContext, MasroufiQuickTileService::class.java)
            )
        }
    }

    fun getTodaySpendFormatted(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hide = prefs.getBoolean(KEY_HIDE_AMOUNTS, false)
        val cur = prefs.getString(KEY_CURRENCY, "DH").orEmpty().ifBlank { "DH" }
        val spend = prefs.getFloat(KEY_TODAY_SPEND, 0f).toDouble()
        return if (hide) "•••• $cur" else "${PayCycleEngine.formatDh(spend)} $cur"
    }

    fun getSafeTodayFormatted(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hide = prefs.getBoolean(KEY_HIDE_AMOUNTS, false)
        val cur = prefs.getString(KEY_CURRENCY, "DH").orEmpty().ifBlank { "DH" }
        val safe = prefs.getFloat(KEY_SAFE_TODAY, 0f).toDouble()
        val lang = prefs.getString(KEY_LANG, "fr").orEmpty()
        val prefix = when (lang) {
            "ar" -> "متاح:"
            "darija" -> "مرتاح:"
            "en" -> "Safe:"
            else -> "Dispo:"
        }
        return if (hide) "$prefix •••• $cur" else "$prefix ${PayCycleEngine.formatDh(safe)} $cur/j"
    }

    fun getHeaderLabel(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_LANG, "fr")) {
            "ar" -> "مصروفي · صرف اليوم"
            "darija" -> "مصروفي · صرف اليوم"
            "en" -> "Masroufi · Today's Spend"
            else -> "Masroufi · Aujourd'hui"
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

    companion object {
        const val ACTION_QUICK_ADD = "com.aistudio.masroufi.ACTION_QUICK_ADD"
        const val ACTION_QUICK_VOICE = "com.aistudio.masroufi.ACTION_QUICK_VOICE"

        fun updateAllWidgets(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, MasroufiAppWidget::class.java))
                for (id in ids) {
                    updateSingleWidget(context, manager, id)
                }
            }
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.masroufi_app_widget)
            views.setTextViewText(R.id.widget_title, MasroufiWidgetStore.getHeaderLabel(context))
            views.setTextViewText(R.id.widget_today_spend, MasroufiWidgetStore.getTodaySpendFormatted(context))
            views.setTextViewText(R.id.widget_safe_badge, MasroufiWidgetStore.getSafeTodayFormatted(context))

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val openAppPending = PendingIntent.getActivity(context, 100, openAppIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_root, openAppPending)

            val addExpenseIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_QUICK_ADD
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val addExpensePending = PendingIntent.getActivity(context, 101, addExpenseIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_btn_add_expense, addExpensePending)

            val voiceIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_QUICK_VOICE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val voicePending = PendingIntent.getActivity(context, 102, voiceIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_btn_voice_darija, voicePending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

class MasroufiQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MasroufiAppWidget.ACTION_QUICK_ADD
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(
                    this,
                    201,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        val todaySpend = MasroufiWidgetStore.getTodaySpendFormatted(this)
        tile.label = "Zid Dépense"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = todaySpend
        }
        tile.updateTile()
    }
}
