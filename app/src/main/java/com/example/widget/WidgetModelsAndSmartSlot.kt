package com.example.widget

import com.example.domain.PayCycleEngine
import com.example.domain.ProjectionAlertLevel
import com.example.domain.Translations
import com.example.ui.DashboardState
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

enum class WidgetKind(val key: String) {
    LEFT_TODAY("left_today"),
    QUICK_ADD("quick_add"),
    MIC_1X1("mic_1x1"),
    ENVELOPES("envelopes"),
    BILLS_DARET("bills_daret"),
    GOAL_CYCLE("goal_cycle"),
    SMART_SLOT("smart_slot");

    companion object {
        fun fromKey(key: String): WidgetKind =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: SMART_SLOT
    }
}

data class WidgetEnvelopeItem(
    val key: String,
    val name: String,
    val spent: Double,
    val totalAvailable: Double,
    val usagePercent: Int,
    val hasCap: Boolean,
    val isOver90Percent: Boolean
)

data class WidgetReminderItem(
    val id: String,
    val itemId: String,
    val kind: String, // "bill" or "daret"
    val title: String,
    val subtitle: String,
    val amount: Double,
    val dueDay: Int,
    val dueWithinDays: Int,
    val actionLabel: String
)

data class WidgetGoalItem(
    val id: String,
    val name: String,
    val savedAmount: Double,
    val targetAmount: Double,
    val progressPercent: Int
)

data class WidgetPresetItem(
    val id: String,
    val name: String,
    val amount: Double,
    val envelopeKey: String
)

data class WidgetUsualShortcutItem(
    val desc: String,
    val amount: Double,
    val envelopeKey: String,
    val categoryName: String = "",
    val accountId: String = "acc_cash",
    val occurrences: Int = 4
)

data class WidgetStyle(
    val appWidgetId: Int = 0,
    val widgetKind: WidgetKind = WidgetKind.SMART_SLOT,
    val themeMode: String = "auto", // "auto", "light", "dark", "accent"
    val glassEnabled: Boolean = true,
    val glassOpacityLevel: Int = 3, // 1 (25%), 2 (45%), 3 (65%), 4 (85%)
    val density: String = "comfortable", // "compact", "comfortable"
    val cornerRadiusDp: Int = 22,
    val privacyMode: String = "follow_app", // "follow_app", "always_show", "always_hide", "hide_when_locked"
    val onlyOver90Envelopes: Boolean = false,
    val selectedEnvelopeKeys: List<String> = emptyList(),
    val selectedPresetIds: List<String> = emptyList()
) {
    fun shouldMaskAmounts(snapshotHideAmounts: Boolean, appLockEnabled: Boolean): Boolean {
        return when (privacyMode) {
            "always_hide" -> true
            "always_show" -> false
            "hide_when_locked" -> appLockEnabled || snapshotHideAmounts
            else -> snapshotHideAmounts
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("appWidgetId", appWidgetId)
        put("widgetKind", widgetKind.key)
        put("themeMode", themeMode)
        put("glassEnabled", glassEnabled)
        put("glassOpacityLevel", glassOpacityLevel.coerceIn(1, 4))
        put("density", density)
        put("cornerRadiusDp", cornerRadiusDp)
        put("privacyMode", privacyMode)
        put("onlyOver90Envelopes", onlyOver90Envelopes)
        put("selectedEnvelopeKeys", JSONArray(selectedEnvelopeKeys))
        put("selectedPresetIds", JSONArray(selectedPresetIds))
    }

    companion object {
        fun fromJson(obj: JSONObject): WidgetStyle {
            val envArr = obj.optJSONArray("selectedEnvelopeKeys")
            val envKeys = mutableListOf<String>()
            if (envArr != null) {
                for (i in 0 until envArr.length()) {
                    envArr.optString(i)?.takeIf { it.isNotBlank() }?.let { envKeys.add(it) }
                }
            }
            val presetArr = obj.optJSONArray("selectedPresetIds")
            val presetIds = mutableListOf<String>()
            if (presetArr != null) {
                for (i in 0 until presetArr.length()) {
                    presetArr.optString(i)?.takeIf { it.isNotBlank() }?.let { presetIds.add(it) }
                }
            }
            return WidgetStyle(
                appWidgetId = obj.optInt("appWidgetId", 0),
                widgetKind = WidgetKind.fromKey(obj.optString("widgetKind", "smart_slot")),
                themeMode = obj.optString("themeMode", "auto"),
                glassEnabled = obj.optBoolean("glassEnabled", true),
                glassOpacityLevel = obj.optInt("glassOpacityLevel", 3).coerceIn(1, 4),
                density = obj.optString("density", "comfortable"),
                cornerRadiusDp = obj.optInt("cornerRadiusDp", 22),
                privacyMode = obj.optString("privacyMode", "follow_app"),
                onlyOver90Envelopes = obj.optBoolean("onlyOver90Envelopes", false),
                selectedEnvelopeKeys = envKeys,
                selectedPresetIds = presetIds
            )
        }
    }
}

/**
 * Phase 1: Single unified WidgetSnapshot built from [DashboardState] by one pure function.
 */
data class WidgetSnapshot(
    val snapshotDateIso: String = PayCycleEngine.todayIso(),
    val todaySpend: Double = 0.0,
    val safePool: Double = 0.0,
    val safeToSpendToday: Double = 0.0,
    val remainingBudget: Double = 0.0,
    val cycleExpenses: Double = 0.0,
    val totalAvailableBudget: Double = 6400.0,
    val daysRemainingInCycle: Int = 15,
    val alertLevel: String = "NONE", // "NONE", "AMBER", "RED"
    val envelopes: List<WidgetEnvelopeItem> = emptyList(),
    val reminders: List<WidgetReminderItem> = emptyList(),
    val primaryGoal: WidgetGoalItem? = null,
    val weekDaysSpend: List<Double> = List(7) { 0.0 },
    val presets: List<WidgetPresetItem> = emptyList(),
    val usualShortcuts: List<WidgetUsualShortcutItem> = emptyList(),
    val smartSuggestionsEnabled: Boolean = true,
    val monthEndForecastMessage: String = "",
    val noSpendStreakDays: Int = 0,
    val hideAmounts: Boolean = false,
    val appLockEnabled: Boolean = false,
    val language: String = "fr",
    val currencySymbol: String = "DH",
    val accentPalette: String = "majorelle",
    val isDarkTheme: Boolean = false,
    val appThemeMode: String = "system", // "system", "light", "dark"
    val pendingDetectionsCount: Int = 0,
    val oldestPendingDetectionHours: Int = 0,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    /**
     * Resolves whether Dark Mode is active based on [appThemeMode] ("system", "light", "dark")
     * and the phone's current night-mode state.
     */
    fun resolveEffectiveIsDark(systemInDark: Boolean): Boolean = when (appThemeMode.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> systemInDark
    }

    fun resolveEffectiveIsDark(context: android.content.Context): Boolean {
        val nightFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val systemInDark = nightFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
        return resolveEffectiveIsDark(systemInDark)
    }

    /**
     * Automatically resets today's spend to 0.0 if the calendar date has crossed midnight
     * since [snapshotDateIso] was recorded, even if the main app is closed.
     */
    fun effectiveTodaySpend(nowIso: String = PayCycleEngine.todayIso()): Double {
        return if (snapshotDateIso == nowIso) todaySpend else 0.0
    }

    /**
     * Recomputes safe-to-spend on midnight rollover if the user had spent money yesterday.
     */
    fun withMidnightRolloverIfNeeded(nowIso: String = PayCycleEngine.todayIso()): WidgetSnapshot {
        if (snapshotDateIso == nowIso) return this
        val daysDelta = (PayCycleEngine.parseEpochDays(nowIso) - PayCycleEngine.parseEpochDays(snapshotDateIso))
            .toInt()
            .coerceAtLeast(1)
        val newDaysRemaining = max(1, daysRemainingInCycle - daysDelta)
        val recomputedSafeToday = if (remainingBudget > 0.0) {
            PayCycleEngine.roundMoney(remainingBudget / newDaysRemaining.toDouble())
        } else 0.0
        return copy(
            snapshotDateIso = nowIso,
            todaySpend = 0.0,
            daysRemainingInCycle = newDaysRemaining,
            safeToSpendToday = recomputedSafeToday,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun effectiveSafePool(): Double {
        if (safePool > 0.0) return safePool
        return PayCycleEngine.roundMoney(max(0.0, safeToSpendToday * daysRemainingInCycle.coerceAtLeast(1)))
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("snapshotDateIso", snapshotDateIso)
        put("todaySpend", todaySpend)
        put("safePool", safePool)
        put("safeToSpendToday", safeToSpendToday)
        put("remainingBudget", remainingBudget)
        put("cycleExpenses", cycleExpenses)
        put("totalAvailableBudget", totalAvailableBudget)
        put("daysRemainingInCycle", daysRemainingInCycle)
        put("alertLevel", alertLevel)
        put("monthEndForecastMessage", monthEndForecastMessage)
        put("noSpendStreakDays", noSpendStreakDays)
        put("hideAmounts", hideAmounts)
        put("appLockEnabled", appLockEnabled)
        put("language", language)
        put("currencySymbol", currencySymbol)
        put("accentPalette", accentPalette)
        put("isDarkTheme", isDarkTheme)
        put("appThemeMode", appThemeMode)
        put("pendingDetectionsCount", pendingDetectionsCount)
        put("oldestPendingDetectionHours", oldestPendingDetectionHours)
        put("updatedAtMillis", updatedAtMillis)

        put("envelopes", JSONArray().apply {
            envelopes.forEach { env ->
                put(JSONObject().apply {
                    put("key", env.key)
                    put("name", env.name)
                    put("spent", env.spent)
                    put("totalAvailable", env.totalAvailable)
                    put("usagePercent", env.usagePercent)
                    put("hasCap", env.hasCap)
                    put("isOver90Percent", env.isOver90Percent)
                })
            }
        })

        put("reminders", JSONArray().apply {
            reminders.forEach { rem ->
                put(JSONObject().apply {
                    put("id", rem.id)
                    put("itemId", rem.itemId)
                    put("kind", rem.kind)
                    put("title", rem.title)
                    put("subtitle", rem.subtitle)
                    put("amount", rem.amount)
                    put("dueDay", rem.dueDay)
                    put("dueWithinDays", rem.dueWithinDays)
                    put("actionLabel", rem.actionLabel)
                })
            }
        })

        if (primaryGoal != null) {
            put("primaryGoal", JSONObject().apply {
                put("id", primaryGoal.id)
                put("name", primaryGoal.name)
                put("savedAmount", primaryGoal.savedAmount)
                put("targetAmount", primaryGoal.targetAmount)
                put("progressPercent", primaryGoal.progressPercent)
            })
        }

        put("weekDaysSpend", JSONArray().apply {
            weekDaysSpend.forEach { put(it) }
        })

        put("presets", JSONArray().apply {
            presets.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("amount", p.amount)
                    put("envelopeKey", p.envelopeKey)
                })
            }
        })

        put("smartSuggestionsEnabled", smartSuggestionsEnabled)
        put("usualShortcuts", JSONArray().apply {
            usualShortcuts.forEach { s ->
                put(JSONObject().apply {
                    put("desc", s.desc)
                    put("amount", s.amount)
                    put("envelopeKey", s.envelopeKey)
                    put("categoryName", s.categoryName)
                    put("accountId", s.accountId)
                    put("occurrences", s.occurrences)
                })
            }
        })
    }

    companion object {
        /**
         * Single canonical builder converting [DashboardState] into [WidgetSnapshot].
         */
        fun fromDashboardState(
            state: DashboardState,
            nowIso: String = PayCycleEngine.todayIso(),
            appLockEnabled: Boolean = false,
            smartSuggestionsEnabled: Boolean = true,
            editedAmountOverrides: Map<String, Double> = emptyMap()
        ): WidgetSnapshot {
            val lang = state.profile.language.ifBlank { "fr" }
            val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }
            val todaySpend = state.allTransactions
                .filter { it.type == "expense" && it.isoDate == nowIso }
                .sumOf { it.amount }

            val currentDayOfMonth = nowIso.split("-").getOrNull(2)?.toIntOrNull()
                ?: Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

            val envItems = state.envelopes.map { env ->
                WidgetEnvelopeItem(
                    key = env.config.key,
                    name = env.config.localizedName(lang),
                    spent = PayCycleEngine.roundMoney(env.spent),
                    totalAvailable = PayCycleEngine.roundMoney(env.totalAvailable),
                    usagePercent = env.usagePercent,
                    hasCap = env.config.hasCap,
                    isOver90Percent = env.config.hasCap && env.usagePercent >= 90
                )
            }

            val remItems = state.smartReminders.map { rem ->
                val dueDay = rem.item.dayOrTurn.coerceIn(1, 31)
                val rawDiff = dueDay - currentDayOfMonth
                val dueWithin = if (rem.kind == "bill") {
                    if (rawDiff >= 0) rawDiff else rawDiff + 30
                } else {
                    1 // Active unpaid Daret round is actionable now
                }
                WidgetReminderItem(
                    id = rem.id,
                    itemId = rem.item.id,
                    kind = rem.kind,
                    title = rem.title,
                    subtitle = rem.subtitle,
                    amount = PayCycleEngine.roundMoney(rem.amount),
                    dueDay = dueDay,
                    dueWithinDays = dueWithin,
                    actionLabel = rem.actionLabel
                )
            }

            val firstGoal = state.goals.firstOrNull()?.let { g ->
                val target = g.amount.coerceAtLeast(1.0)
                val saved = g.secondaryAmount.coerceAtLeast(0.0)
                val pct = ((saved / target) * 100.0).roundToInt().coerceIn(0, 100)
                WidgetGoalItem(
                    id = g.id,
                    name = g.name,
                    savedAmount = PayCycleEngine.roundMoney(saved),
                    targetAmount = PayCycleEngine.roundMoney(target),
                    progressPercent = pct
                )
            }

            val presetItems = if (state.presets.isNotEmpty()) {
                state.presets.take(4).map { p ->
                    WidgetPresetItem(
                        id = p.id,
                        name = p.name,
                        amount = PayCycleEngine.roundMoney(p.amount),
                        envelopeKey = p.subtitle.ifBlank { "cafe" }
                    )
                }
            } else {
                listOf(
                    WidgetPresetItem("preset_cafe", "Café", 15.0, "cafe"),
                    WidgetPresetItem("preset_taxi", "Taxi", 20.0, "transport"),
                    WidgetPresetItem("preset_khobz", "Marché", 30.0, "charges")
                )
            }

            val usualItems = if (smartSuggestionsEnabled) {
                com.example.ui.computeTimeAwareShortcuts(
                    state = state,
                    nowIso = nowIso,
                    editedAmountOverrides = editedAmountOverrides
                ).map { s ->
                    WidgetUsualShortcutItem(
                        desc = s.desc,
                        amount = s.amount,
                        envelopeKey = s.envelopeKey,
                        categoryName = s.categoryName,
                        accountId = s.accountId,
                        occurrences = s.occurrences
                    )
                }
            } else {
                emptyList()
            }

            val alertStr = when (state.spendProjection.alertLevel) {
                ProjectionAlertLevel.RED -> "RED"
                ProjectionAlertLevel.AMBER -> "AMBER"
                ProjectionAlertLevel.NONE -> "NONE"
            }

            return WidgetSnapshot(
                snapshotDateIso = nowIso,
                todaySpend = PayCycleEngine.roundMoney(todaySpend),
                safePool = PayCycleEngine.roundMoney(state.safePool),
                safeToSpendToday = PayCycleEngine.roundMoney(state.safeToSpendToday),
                remainingBudget = PayCycleEngine.roundMoney(state.remainingBudget),
                cycleExpenses = PayCycleEngine.roundMoney(state.cycleExpenses),
                totalAvailableBudget = PayCycleEngine.roundMoney(state.totalAvailableBudget),
                daysRemainingInCycle = state.daysRemaining.coerceAtLeast(1),
                alertLevel = alertStr,
                envelopes = envItems,
                reminders = remItems,
                primaryGoal = firstGoal,
                weekDaysSpend = state.weekDaysSpend.take(7).let { list ->
                    if (list.size == 7) list else List(7) { idx -> list.getOrElse(idx) { 0.0 } }
                },
                presets = presetItems,
                usualShortcuts = usualItems,
                smartSuggestionsEnabled = smartSuggestionsEnabled,
                monthEndForecastMessage = state.monthEndForecastMessage,
                noSpendStreakDays = state.noSpendStreakDays,
                hideAmounts = state.hideAmounts,
                appLockEnabled = appLockEnabled,
                language = lang,
                currencySymbol = cur,
                accentPalette = state.customPrefs.accentPalette.ifBlank { "majorelle" },
                isDarkTheme = state.profile.isDarkTheme,
                appThemeMode = state.customPrefs.normalizedThemeMode()
            )
        }

        fun fromJson(obj: JSONObject): WidgetSnapshot {
            val envArr = obj.optJSONArray("envelopes")
            val envs = mutableListOf<WidgetEnvelopeItem>()
            if (envArr != null) {
                for (i in 0 until envArr.length()) {
                    val item = envArr.optJSONObject(i) ?: continue
                    envs.add(
                        WidgetEnvelopeItem(
                            key = item.optString("key", "autre"),
                            name = item.optString("name", "Autre"),
                            spent = item.optDouble("spent", 0.0),
                            totalAvailable = item.optDouble("totalAvailable", 0.0),
                            usagePercent = item.optInt("usagePercent", 0),
                            hasCap = item.optBoolean("hasCap", true),
                            isOver90Percent = item.optBoolean("isOver90Percent", false)
                        )
                    )
                }
            }

            val remArr = obj.optJSONArray("reminders")
            val rems = mutableListOf<WidgetReminderItem>()
            if (remArr != null) {
                for (i in 0 until remArr.length()) {
                    val item = remArr.optJSONObject(i) ?: continue
                    rems.add(
                        WidgetReminderItem(
                            id = item.optString("id", ""),
                            itemId = item.optString("itemId", ""),
                            kind = item.optString("kind", "bill"),
                            title = item.optString("title", ""),
                            subtitle = item.optString("subtitle", ""),
                            amount = item.optDouble("amount", 0.0),
                            dueDay = item.optInt("dueDay", 1),
                            dueWithinDays = item.optInt("dueWithinDays", 5),
                            actionLabel = item.optString("actionLabel", "Payer")
                        )
                    )
                }
            }

            val goalObj = obj.optJSONObject("primaryGoal")
            val goal = if (goalObj != null) {
                WidgetGoalItem(
                    id = goalObj.optString("id", ""),
                    name = goalObj.optString("name", ""),
                    savedAmount = goalObj.optDouble("savedAmount", 0.0),
                    targetAmount = goalObj.optDouble("targetAmount", 1.0),
                    progressPercent = goalObj.optInt("progressPercent", 0)
                )
            } else null

            val weekArr = obj.optJSONArray("weekDaysSpend")
            val week = if (weekArr != null && weekArr.length() > 0) {
                List(7) { idx -> weekArr.optDouble(idx, 0.0) }
            } else {
                List(7) { 0.0 }
            }

            val presetArr = obj.optJSONArray("presets")
            val presets = mutableListOf<WidgetPresetItem>()
            if (presetArr != null) {
                for (i in 0 until presetArr.length()) {
                    val item = presetArr.optJSONObject(i) ?: continue
                    presets.add(
                        WidgetPresetItem(
                            id = item.optString("id", "p_$i"),
                            name = item.optString("name", "Café"),
                            amount = item.optDouble("amount", 15.0),
                            envelopeKey = item.optString("envelopeKey", "cafe")
                        )
                    )
                }
            }

            val usualArr = obj.optJSONArray("usualShortcuts")
            val usuals = mutableListOf<WidgetUsualShortcutItem>()
            if (usualArr != null) {
                for (i in 0 until usualArr.length()) {
                    val item = usualArr.optJSONObject(i) ?: continue
                    usuals.add(
                        WidgetUsualShortcutItem(
                            desc = item.optString("desc", ""),
                            amount = item.optDouble("amount", 0.0),
                            envelopeKey = item.optString("envelopeKey", "cafe"),
                            categoryName = item.optString("categoryName", ""),
                            accountId = item.optString("accountId", "acc_cash"),
                            occurrences = item.optInt("occurrences", 4)
                        )
                    )
                }
            }

            val parsedSafeToday = obj.optDouble("safeToSpendToday", 0.0)
            val parsedDaysRem = obj.optInt("daysRemainingInCycle", 15).coerceAtLeast(1)
            val parsedSafePool = obj.optDouble("safePool", parsedSafeToday * parsedDaysRem)

            return WidgetSnapshot(
                snapshotDateIso = obj.optString("snapshotDateIso", PayCycleEngine.todayIso()),
                todaySpend = obj.optDouble("todaySpend", 0.0),
                safePool = parsedSafePool,
                safeToSpendToday = parsedSafeToday,
                remainingBudget = obj.optDouble("remainingBudget", 0.0),
                cycleExpenses = obj.optDouble("cycleExpenses", 0.0),
                totalAvailableBudget = obj.optDouble("totalAvailableBudget", 6400.0),
                daysRemainingInCycle = parsedDaysRem,
                alertLevel = obj.optString("alertLevel", "NONE"),
                envelopes = envs,
                reminders = rems,
                primaryGoal = goal,
                weekDaysSpend = week,
                presets = presets,
                usualShortcuts = usuals,
                smartSuggestionsEnabled = obj.optBoolean("smartSuggestionsEnabled", true),
                monthEndForecastMessage = obj.optString("monthEndForecastMessage", ""),
                noSpendStreakDays = obj.optInt("noSpendStreakDays", 0),
                hideAmounts = obj.optBoolean("hideAmounts", false),
                appLockEnabled = obj.optBoolean("appLockEnabled", false),
                language = obj.optString("language", "fr"),
                currencySymbol = obj.optString("currencySymbol", "DH"),
                accentPalette = obj.optString("accentPalette", "majorelle"),
                isDarkTheme = obj.optBoolean("isDarkTheme", false),
                appThemeMode = obj.optString("appThemeMode", "system"),
                pendingDetectionsCount = obj.optInt("pendingDetectionsCount", 0),
                oldestPendingDetectionHours = obj.optInt("oldestPendingDetectionHours", 0),
                updatedAtMillis = obj.optLong("updatedAtMillis", 0L)
            )
        }
    }
}

enum class SmartSlotReason {
    DUE_BILL_OR_DARET,
    PENDING_BANK_DETECTIONS,
    ENVELOPE_OVER_90,
    HIGH_PROJECTION_ALERT,
    MORNING_LEFT_TODAY,
    EVENING_SPENT_TODAY
}

data class SmartSlotCard(
    val reason: SmartSlotReason,
    val badgeTitle: String,
    val headlineValue: String,
    val subtitle: String,
    val statusColorHex: String, // "#10B981" (green), "#F59E0B" (amber), "#EF4444" (red), "#5546C7" (indigo)
    val targetTabKey: String = "vue"
) {
    val priorityRank: Int
        get() = when (reason) {
            SmartSlotReason.DUE_BILL_OR_DARET -> 1
            SmartSlotReason.PENDING_BANK_DETECTIONS -> 2
            SmartSlotReason.ENVELOPE_OVER_90 -> 2
            SmartSlotReason.HIGH_PROJECTION_ALERT -> 3
            SmartSlotReason.MORNING_LEFT_TODAY -> 4
            SmartSlotReason.EVENING_SPENT_TODAY -> 5
        }
}

/**
 * Phase 7 & 10D: Pure Smart Slot selector implementing the priority rules:
 * 1. A bill or Daret turn due within 2 days.
 * 1B (Phase 10D). 3+ pending bank detections or oldest pending item > 24 hours old.
 * 2. An envelope at or above 90%.
 * 3. A high projection alert for the cycle.
 * 4. Morning (< 17h): "Left today".
 * 5. Evening (>= 17h): "Spent today".
 */
object SmartSlotEngine {

    fun resolveSmartSlot(
        snapshot: WidgetSnapshot,
        currentHourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        style: WidgetStyle = WidgetStyle(),
        nowIso: String = PayCycleEngine.todayIso()
    ): SmartSlotCard {
        val fresh = snapshot.withMidnightRolloverIfNeeded(nowIso)
        val strings = Translations.forLang(fresh.language)
        val cur = fresh.currencySymbol.ifBlank { "DH" }
        val mask = style.shouldMaskAmounts(fresh.hideAmounts, fresh.appLockEnabled)

        // Rule 1: A bill or Daret turn due within 2 days
        val urgentReminder = fresh.reminders.firstOrNull { it.dueWithinDays in 0..2 }
        if (urgentReminder != null) {
            val amtStr = PayCycleEngine.formatCurrencyOrMask(urgentReminder.amount, cur, mask)
            return SmartSlotCard(
                reason = SmartSlotReason.DUE_BILL_OR_DARET,
                badgeTitle = strings.widgetBillsDaretTitle,
                headlineValue = "${urgentReminder.title} · $amtStr",
                subtitle = urgentReminder.subtitle,
                statusColorHex = "#F59E0B",
                targetTabKey = "categories"
            )
        }

        // Rule 1B (Phase 10D, placed between rules 1 and 2): 3+ pending bank items or oldest > 24h
        if (fresh.pendingDetectionsCount >= 3 || fresh.oldestPendingDetectionHours > 24) {
            val countStr = strings.bankDetectPendingChipFmt.replace("{count}", fresh.pendingDetectionsCount.coerceAtLeast(1).toString())
            return SmartSlotCard(
                reason = SmartSlotReason.PENDING_BANK_DETECTIONS,
                badgeTitle = strings.bankDetectToConfirmTitle,
                headlineValue = countStr,
                subtitle = strings.bankDetectSectionTitle,
                statusColorHex = "#F59E0B",
                targetTabKey = "vue"
            )
        }

        // Rule 2: An envelope at or above 90%
        val criticalEnv = fresh.envelopes
            .filter { it.isOver90Percent }
            .maxByOrNull { it.usagePercent }
        if (criticalEnv != null) {
            val spentStr = PayCycleEngine.formatCurrencyOrMask(criticalEnv.spent, cur, mask)
            return SmartSlotCard(
                reason = SmartSlotReason.ENVELOPE_OVER_90,
                badgeTitle = strings.widgetEnvelopesTitle,
                headlineValue = "${criticalEnv.name} · ${criticalEnv.usagePercent}% ($spentStr)",
                subtitle = strings.widgetEnvelopesTitle,
                statusColorHex = if (criticalEnv.usagePercent >= 100) "#EF4444" else "#F59E0B",
                targetTabKey = "categories"
            )
        }

        // Rule 3: A high projection alert for the cycle
        if (fresh.alertLevel == "RED" || fresh.alertLevel == "AMBER") {
            val remStr = PayCycleEngine.formatCurrencyOrMask(fresh.remainingBudget, cur, mask)
            return SmartSlotCard(
                reason = SmartSlotReason.HIGH_PROJECTION_ALERT,
                badgeTitle = strings.estimateLabel,
                headlineValue = remStr,
                subtitle = fresh.monthEndForecastMessage.ifBlank { strings.cycleBalanceLabel },
                statusColorHex = if (fresh.alertLevel == "RED") "#EF4444" else "#F59E0B",
                targetTabKey = "semaine"
            )
        }

        // Rule 4: Morning (< 17h) -> "Left today"
        if (currentHourOfDay < 17) {
            val safeStr = PayCycleEngine.formatCurrencyOrMask(fresh.safeToSpendToday, cur, mask)
            return SmartSlotCard(
                reason = SmartSlotReason.MORNING_LEFT_TODAY,
                badgeTitle = strings.widgetLeftTodayTitle,
                headlineValue = safeStr,
                subtitle = "${strings.cycleBalanceLabel} : ${PayCycleEngine.formatCurrencyOrMask(fresh.remainingBudget, cur, mask)}",
                statusColorHex = "#10B981",
                targetTabKey = "vue"
            )
        }

        // Rule 5: Evening (>= 17h) -> "Spent today"
        val todaySpendVal = fresh.effectiveTodaySpend(nowIso)
        val spentStr = PayCycleEngine.formatCurrencyOrMask(todaySpendVal, cur, mask)
        return SmartSlotCard(
            reason = SmartSlotReason.EVENING_SPENT_TODAY,
            badgeTitle = strings.widgetSpentTodayTitle,
            headlineValue = spentStr,
            subtitle = "${strings.widgetSafePrefix} ${PayCycleEngine.formatCurrencyOrMask(fresh.safeToSpendToday, cur, mask)}",
            statusColorHex = "#5546C7",
            targetTabKey = "vue"
        )
    }
}
