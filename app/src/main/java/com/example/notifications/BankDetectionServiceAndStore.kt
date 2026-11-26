package com.example.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.BuildConfig
import com.example.R
import com.example.domain.BankMessageParser
import com.example.domain.BankParseResult
import com.example.domain.PayCycleEngine
import com.example.domain.VoiceParsedTransaction
import com.example.security.SecureStore
import com.example.widget.MasroufiWidgetStore
import com.masroufi.core.Money
import java.math.BigDecimal
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class DetectedBankTransaction(
    val id: String,
    val amount: Money,
    val currency: String,
    val direction: String, // "expense", "income", or "transfer"
    val merchant: String,
    val isoDate: String,
    val sourcePackage: String,
    val sourceAppLabel: String,
    val dedupeHash: String,
    val confidence: Double,
    val needsReview: Boolean,
    val suggestedEnvelopeKey: String,
    val suggestedCategoryName: String,
    val maskedSamplePreview: String = "",
    val possibleDuplicateTxId: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val senderTrustKey: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("amountCentimes", amount.centimes)
        put("amount", amount.toBigDecimalDh())
        put("currency", currency)
        put("direction", direction)
        put("merchant", merchant)
        put("isoDate", isoDate)
        put("sourcePackage", sourcePackage)
        put("sourceAppLabel", sourceAppLabel)
        put("dedupeHash", dedupeHash)
        put("confidence", confidence)
        put("needsReview", needsReview)
        put("suggestedEnvelopeKey", suggestedEnvelopeKey)
        put("suggestedCategoryName", suggestedCategoryName)
        // D10: Never persist maskedSamplePreview by default; generate on-demand only
        put("maskedSamplePreview", "")
        put("possibleDuplicateTxId", possibleDuplicateTxId)
        put("createdAtMillis", createdAtMillis)
        put("senderTrustKey", senderTrustKey)
    }

    companion object {
        fun fromJson(obj: JSONObject): DetectedBankTransaction {
            val parsedAmount = if (obj.has("amountCentimes") && !obj.isNull("amountCentimes")) {
                Money.fromCentimes(obj.optLong("amountCentimes", 0L))
            } else {
                when (val raw = obj.opt("amount")) {
                    is BigDecimal -> runCatching { Money.fromMajorUnits(raw) }.getOrDefault(Money.ZERO)
                    is Number -> runCatching { Money.fromMajorUnits(BigDecimal(raw.toString())) }
                        .getOrElse { runCatching { Money.fromLegacyDoubleDh(raw.toDouble()) }.getOrDefault(Money.ZERO) }
                    is String -> Money.fromDhString(raw) ?: Money.ZERO
                    else -> Money.ZERO
                }
            }
            return DetectedBankTransaction(
                id = obj.optString("id", "det_${System.currentTimeMillis()}"),
                amount = parsedAmount,
                currency = obj.optString("currency", "DH"),
                direction = obj.optString("direction", "expense"),
                merchant = obj.optString("merchant", ""),
                isoDate = obj.optString("isoDate", PayCycleEngine.todayIso()),
                sourcePackage = obj.optString("sourcePackage", "generic"),
                sourceAppLabel = obj.optString("sourceAppLabel", "Banque"),
                dedupeHash = obj.optString("dedupeHash", ""),
                confidence = obj.optDouble("confidence", 0.8),
                needsReview = obj.optBoolean("needsReview", false),
                suggestedEnvelopeKey = obj.optString("suggestedEnvelopeKey", "autre"),
                suggestedCategoryName = obj.optString("suggestedCategoryName", "Autre"),
                maskedSamplePreview = "",
                possibleDuplicateTxId = obj.optString("possibleDuplicateTxId", ""),
                createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
                senderTrustKey = obj.optString("senderTrustKey", "")
            )
        }
    }
}

data class AllowlistAppOption(
    val packageName: String,
    val label: String,
    val isAllowed: Boolean
)

data class ExtractedNotificationMessage(
    val title: String,
    val body: String,
    val timestampMillis: Long
)

/**
 * Phase 10A, 10D & Audit Fixes (D1, D5, D6, D8, D9, D10, S4, S6):
 * On-device encrypted store for allowlisted bank/SMS packages, pending detected items,
 * ignored merchants, confirmed SMS senders, 7-day/500-entry LRU dedupe table, and kill switches.
 */
object BankDetectionStore {
    const val MAX_PENDING_ITEMS = 40
    const val EXPIRY_MILLIS = 14L * 24L * 3600L * 1000L
    const val SEEN_HASH_EXPIRY_MILLIS = 7L * 24L * 3600L * 1000L
    const val MAX_SEEN_HASHES = 500
    const val MAX_THREAD_WATERMARKS = 120
    const val TEST_NOTIFICATION_TAG = "masroufi_bank_test_sample"
    const val AUTO_DETECTED_DESC_PREFIX = "[Auto] "
    private const val LISTENER_HEALTH_WORK_NAME = "masroufi_bank_listener_health"

    private const val PREFS_NAME = "masroufi_bank_detection_prefs"
    private const val KEY_ALLOWED_PACKAGES = "allowed_packages_set"
    private const val KEY_PAUSED = "detection_paused"
    private const val KEY_PENDING_JSON = "pending_detected_items_json"
    private const val KEY_SEEN_HASHES_LEGACY = "seen_dedupe_hashes_set"
    private const val KEY_SEEN_HASHES_V2 = "seen_dedupe_hashes_v2_json"
    private const val KEY_THREAD_WATERMARKS_JSON = "thread_watermarks_v1_json"
    private const val KEY_CONFIRMED_SENDERS = "confirmed_sms_senders_set"
    private const val KEY_IGNORED_MERCHANTS = "ignored_merchants_set"
    private const val KEY_AUTO_CONFIRM_ENABLED = "auto_confirm_enabled"
    private const val KEY_AUTO_CONFIRM_CAP = "auto_confirm_max_cap"
    private const val KEY_AUTO_CONFIRM_CAP_CENTIMES = "auto_confirm_max_cap_centimes"

    // S4: SecureStore keys for sensitive bank-detection queues and hashes
    private const val SECURE_KEY_PENDING = "bank_detect_pending_json_v1"
    private const val SECURE_KEY_SEEN_HASHES = "bank_detect_seen_hashes_v2"
    private const val SECURE_KEY_WATERMARKS = "bank_detect_thread_watermarks_v1"

    private val storeLock = Any()

    private val _pendingItemsFlow = MutableStateFlow<List<DetectedBankTransaction>>(emptyList())
    val pendingItemsFlow: StateFlow<List<DetectedBankTransaction>> = _pendingItemsFlow.asStateFlow()

    val KNOWN_BANK_APPS: List<Pair<String, String>> = listOf(
        "com.b3g.cih.online" to "CIH Mobile",
        "com.attijariwafabank.attijarimobile" to "Attijari Mobile",
        "ma.attijariwafabank.lbankalik" to "L'bankalik (Attijari)",
        "ma.gbp.pocketbank" to "Pocket Bank (Banque Populaire)",
        "com.bmce.bmcedirect" to "BMCE Direct / Bank of Africa",
        "com.cfgbank.app" to "CFG Bank",
        "ma.abb.baridbankmobile" to "Barid Bank Mobile",
        "ma.cdm.mycdm" to "Crédit du Maroc (MyCDM)",
        "com.sgma.sgmaroc" to "Société Générale Maroc"
    )

    /**
     * Safeguard: Maps legacy/older bank package IDs stored by earlier versions of Masroufi to their
     * real Play Store counterparts so existing users are never silently blocked after updating.
     */
    val LEGACY_TO_REAL_BANK_PACKAGE_MAP: Map<String, List<String>> = mapOf(
        "com.cih.cihmobile" to listOf("com.b3g.cih.online"),
        "com.bcp.pocketbank" to listOf("ma.gbp.pocketbank"),
        "ma.attijariwafabank.lbankalik" to listOf(
            "com.attijariwafabank.attijarimobile",
            "ma.attijariwafabank.attijarimobile"
        ),
        "com.bmce.agencedirecte" to listOf("com.bmce.bmcedirect", "ma.bankofafrica.bmcedirect"),
        "com.cfgbank.mobile" to listOf("com.cfgbank.app"),
        "ma.baridbank.mobile" to listOf("ma.abb.baridbankmobile")
    )

    val KNOWN_SMS_APPS: List<Pair<String, String>> = listOf(
        "com.google.android.apps.messaging" to "Google Messages (SMS)",
        "com.samsung.android.messaging" to "Samsung Messages (SMS)",
        "com.android.mms" to "Messages SMS",
        "com.huawei.messaging" to "Huawei Messages (SMS)",
        "com.oneplus.mms" to "OnePlus Messages (SMS)",
        "com.coloros.mms" to "OPPO / ColorOS Messages (SMS)",
        "com.miui.messaging" to "Xiaomi Messages (SMS)"
    )

    val KNOWN_BANK_AND_SMS_APPS: List<Pair<String, String>> = KNOWN_SMS_APPS + KNOWN_BANK_APPS

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    internal fun expandLegacyAllowedPackages(raw: Set<String>): Set<String> {
        if (raw.isEmpty()) return emptySet()
        val expanded = LinkedHashSet<String>(raw.size + 4)
        expanded.addAll(raw)
        for (pkg in raw) {
            LEGACY_TO_REAL_BANK_PACKAGE_MAP[pkg]?.let { counterparts ->
                expanded.addAll(counterparts)
            }
        }
        return expanded
    }

    fun isKnownBankAppPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase(Locale.ROOT)
        return KNOWN_BANK_APPS.any { it.first.equals(pkg, ignoreCase = true) } ||
            LEGACY_TO_REAL_BANK_PACKAGE_MAP.keys.any { it.equals(pkg, ignoreCase = true) } ||
            BankMessageParser.BANK_TEMPLATES.any { tpl ->
                tpl.id != "generic" && tpl.packageHints.any { hint -> pkg == hint || pkg.startsWith("$hint.") }
            }
    }

    fun getAllowedPackages(context: Context): Set<String> = synchronized(storeLock) {
        val stored = prefs(context).getStringSet(KEY_ALLOWED_PACKAGES, emptySet())?.toSet() ?: emptySet()
        val expanded = expandLegacyAllowedPackages(stored)
        if (expanded.size != stored.size) {
            prefs(context).edit().putStringSet(KEY_ALLOWED_PACKAGES, expanded).apply()
        }
        expanded
    }

    fun setPackageAllowed(context: Context, packageName: String, allowed: Boolean) {
        synchronized(storeLock) {
            val current = prefs(context).getStringSet(KEY_ALLOWED_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
            if (allowed) {
                current.add(packageName)
                LEGACY_TO_REAL_BANK_PACKAGE_MAP[packageName]?.let { current.addAll(it) }
            } else {
                current.remove(packageName)
                // If disabling a real Play Store package, also clear any legacy alias that maps to it
                LEGACY_TO_REAL_BANK_PACKAGE_MAP.forEach { (legacyPkg, counterparts) ->
                    if (packageName in counterparts) {
                        current.remove(legacyPkg)
                    }
                }
            }
            prefs(context).edit().putStringSet(KEY_ALLOWED_PACKAGES, current).apply()
        }
    }

    fun isPackageAllowed(context: Context, packageName: String, notificationTag: String? = null): Boolean {
        if (notificationTag == TEST_NOTIFICATION_TAG && packageName == context.packageName) {
            return true
        }
        // Prompt 3: Allow `adb shell cmd notification post` in debug builds only
        if (BuildConfig.DEBUG && packageName == "com.android.shell") {
            return true
        }
        return packageName in getAllowedPackages(context)
    }

    fun isPaused(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PAUSED, false)

    fun setPaused(context: Context, paused: Boolean) {
        prefs(context).edit().putBoolean(KEY_PAUSED, paused).apply()
    }

    fun isAutoConfirmEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CONFIRM_ENABLED, false)

    fun setAutoConfirmEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CONFIRM_ENABLED, enabled).apply()
    }

    fun getAutoConfirmMaxAmount(context: Context): Money {
        val p = prefs(context)
        val allEntries = runCatching { p.all }.getOrDefault(emptyMap())

        if (allEntries.containsKey(KEY_AUTO_CONFIRM_CAP_CENTIMES)) {
            val parsedCentimes = when (val raw = allEntries[KEY_AUTO_CONFIRM_CAP_CENTIMES]) {
                is Long -> raw
                is Int -> raw.toLong()
                is Number -> runCatching { raw.toLong() }.getOrNull()
                is String -> raw.trim().toLongOrNull()
                else -> null
            }
            if (parsedCentimes != null && parsedCentimes > 0L) {
                return Money.fromCentimes(parsedCentimes)
            }
            // Corrupt/invalid centimes entry — remove and fall through cleanly
            p.edit().remove(KEY_AUTO_CONFIRM_CAP_CENTIMES).apply()
        }

        if (allEntries.containsKey(KEY_AUTO_CONFIRM_CAP)) {
            val migrated = when (val raw = allEntries[KEY_AUTO_CONFIRM_CAP]) {
                is Float -> if (raw.isFinite() && raw > 0f) {
                    runCatching { Money.fromLegacyDoubleDh(raw.toDouble()) }.getOrNull()
                } else null
                is Double -> if (raw.isFinite() && raw > 0.0) {
                    runCatching { Money.fromLegacyDoubleDh(raw) }.getOrNull()
                } else null
                is Long -> if (raw > 0L) runCatching { Money.fromDh(raw) }.getOrNull() else null
                is Int -> if (raw > 0) runCatching { Money.fromDh(raw.toLong()) }.getOrNull() else null
                is String -> Money.fromDhString(raw)?.takeIf { it.isPositive }
                else -> null
            }
            if (migrated != null && migrated.isPositive) {
                p.edit()
                    .putLong(KEY_AUTO_CONFIRM_CAP_CENTIMES, migrated.centimes)
                    .remove(KEY_AUTO_CONFIRM_CAP)
                    .apply()
                return migrated
            }
            p.edit().remove(KEY_AUTO_CONFIRM_CAP).apply()
        }

        return Money.fromDh(200)
    }

    fun setAutoConfirmMaxAmount(context: Context, maxAmount: Money) {
        val clamped = maxAmount.coerceAtLeast(Money.fromDh(10))
        prefs(context).edit()
            .putLong(KEY_AUTO_CONFIRM_CAP_CENTIMES, clamped.centimes)
            .remove(KEY_AUTO_CONFIRM_CAP)
            .apply()
    }

    fun getIgnoredMerchants(context: Context): Set<String> = synchronized(storeLock) {
        prefs(context).getStringSet(KEY_IGNORED_MERCHANTS, emptySet())?.toSet() ?: emptySet()
    }

    fun addIgnoredMerchant(context: Context, merchant: String) {
        val norm = merchant.trim().lowercase(Locale.ROOT)
        if (norm.isBlank()) return
        synchronized(storeLock) {
            val updated = (prefs(context).getStringSet(KEY_IGNORED_MERCHANTS, emptySet()) ?: emptySet())
                .toMutableSet()
                .apply { add(norm) }
            prefs(context).edit().putStringSet(KEY_IGNORED_MERCHANTS, updated).apply()
            val remaining = loadPendingItemsLocked(context, System.currentTimeMillis())
                .filterNot { it.merchant.trim().lowercase(Locale.ROOT) == norm }
            savePendingItemsLocked(context, remaining)
        }
    }

    fun getConfirmedSenders(context: Context): Set<String> = synchronized(storeLock) {
        prefs(context).getStringSet(KEY_CONFIRMED_SENDERS, emptySet())?.toSet() ?: emptySet()
    }

    fun markSenderConfirmed(context: Context, senderTrustKey: String) {
        val norm = senderTrustKey.trim().lowercase(Locale.ROOT)
        if (norm.isBlank()) return
        synchronized(storeLock) {
            val updated = (prefs(context).getStringSet(KEY_CONFIRMED_SENDERS, emptySet()) ?: emptySet())
                .toMutableSet()
                .apply { add(norm) }
            prefs(context).edit().putStringSet(KEY_CONFIRMED_SENDERS, updated).apply()
        }
    }

    fun isSenderTrustedForAutoConfirm(
        context: Context,
        sourcePackage: String,
        senderTrustKey: String
    ): Boolean {
        if (isKnownBankAppPackage(sourcePackage)) return true
        val norm = senderTrustKey.trim().lowercase(Locale.ROOT)
        if (norm.isBlank()) return false
        return norm in getConfirmedSenders(context)
    }

    /**
     * D6: Dynamically includes the device's default SMS app (`Telephony.Sms.getDefaultSmsPackage`),
     * known Moroccan bank apps, any currently allowlisted apps, and Masroufi's test simulator.
     */
    fun getCandidateApps(context: Context): List<AllowlistAppOption> {
        val allowed = getAllowedPackages(context)
        val options = LinkedHashMap<String, AllowlistAppOption>()
        val pm = context.packageManager

        val defaultSmsPkg = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        if (!defaultSmsPkg.isNullOrBlank()) {
            val label = resolveAppLabel(pm, defaultSmsPkg) ?: "SMS par défaut ($defaultSmsPkg)"
            options[defaultSmsPkg] = AllowlistAppOption(defaultSmsPkg, "$label (SMS)", defaultSmsPkg in allowed)
        }

        KNOWN_BANK_AND_SMS_APPS.forEach { (pkg, label) ->
            if (!options.containsKey(pkg)) {
                options[pkg] = AllowlistAppOption(pkg, label, pkg in allowed)
            }
        }

        // Preserve any custom apps added by the user via the launcher picker
        allowed.forEach { pkg ->
            if (!options.containsKey(pkg)) {
                val label = resolveAppLabel(pm, pkg) ?: pkg
                options[pkg] = AllowlistAppOption(pkg, label, true)
            }
        }

        val selfPkg = context.packageName
        options[selfPkg] = AllowlistAppOption(selfPkg, "Masroufi (Simulation test)", selfPkg in allowed)
        return options.values.toList()
    }

    /**
     * D6: Lists installed launcher apps using `ACTION_MAIN` + `CATEGORY_LAUNCHER` via `<queries>`
     * in AndroidManifest.xml (no `QUERY_ALL_PACKAGES` permission needed).
     */
    fun getInstalledLauncherApps(context: Context): List<AllowlistAppOption> {
        val allowed = getAllowedPackages(context)
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { pm.queryIntentActivities(mainIntent, 0) }.getOrDefault(emptyList())
        val map = LinkedHashMap<String, AllowlistAppOption>()
        for (ri in resolved) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == context.packageName || map.containsKey(pkg)) continue
            val label = runCatching { ri.loadLabel(pm)?.toString() }.getOrNull().orEmpty().ifBlank { pkg }
            map[pkg] = AllowlistAppOption(pkg, label, pkg in allowed)
        }
        return map.values.sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    private fun resolveAppLabel(pm: PackageManager, packageName: String): String? {
        return runCatching {
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai)?.toString()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /**
     * Loads non-expired pending items from [SecureStore] (migrating & wiping any legacy plaintext copy).
     */
    fun loadPendingItems(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): List<DetectedBankTransaction> = synchronized(storeLock) {
        loadPendingItemsLocked(context, nowMillis)
    }

    private fun loadPendingItemsLocked(
        context: Context,
        nowMillis: Long
    ): List<DetectedBankTransaction> {
        val raw = SecureStore.migrateStringFromLegacyPrefs(
            context = context,
            legacyPrefsName = PREFS_NAME,
            legacyKey = KEY_PENDING_JSON,
            secureKey = SECURE_KEY_PENDING
        )
        if (raw.isNullOrBlank()) {
            _pendingItemsFlow.value = emptyList()
            return emptyList()
        }
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val loaded = mutableListOf<DetectedBankTransaction>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val item = DetectedBankTransaction.fromJson(obj)
            if (nowMillis - item.createdAtMillis <= EXPIRY_MILLIS) {
                loaded.add(item)
            }
        }
        val capped = loaded
            .sortedByDescending { it.createdAtMillis }
            .take(MAX_PENDING_ITEMS)

        if (capped.size != arr.length()) {
            savePendingItemsLocked(context, capped)
        } else {
            _pendingItemsFlow.value = capped
        }
        return capped
    }

    private fun savePendingItemsLocked(context: Context, items: List<DetectedBankTransaction>): Boolean {
        val capped = items.sortedByDescending { it.createdAtMillis }.take(MAX_PENDING_ITEMS)
        val arr = JSONArray()
        capped.forEach { arr.put(it.toJson()) }
        val stored = SecureStore.putString(context, SECURE_KEY_PENDING, arr.toString())
        if (!stored) return false
        // Ensure legacy plaintext key stays removed
        prefs(context).edit().remove(KEY_PENDING_JSON).apply()
        _pendingItemsFlow.value = capped
        MasroufiWidgetStore.refreshWidgetsAndTiles(context)
        return true
    }

    private fun loadActiveDedupeHashesLocked(
        context: Context,
        nowMillis: Long
    ): LinkedHashMap<String, Long> {
        val raw = SecureStore.getString(context, SECURE_KEY_SEEN_HASHES)
        val map = LinkedHashMap<String, Long>()
        if (!raw.isNullOrBlank()) {
            val obj = runCatching { JSONObject(raw) }.getOrNull()
            if (obj != null) {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val ts = obj.optLong(k, 0L)
                    if (ts > 0L && (nowMillis - ts) <= SEEN_HASH_EXPIRY_MILLIS) {
                        map[k] = ts
                    }
                }
            }
        }
        // Clean up legacy plaintext hash set if present
        if (prefs(context).contains(KEY_SEEN_HASHES_LEGACY)) {
            prefs(context).edit().remove(KEY_SEEN_HASHES_LEGACY).apply()
        }
        return map
    }

    /**
     * D5: Thread-safe 7-day / 500-entry LRU dedupe table stored in [SecureStore].
     */
    private fun recordDedupeHashLocked(
        context: Context,
        existingMap: LinkedHashMap<String, Long>,
        hash: String,
        nowMillis: Long
    ) {
        existingMap[hash] = nowMillis

        // Evict oldest entries by timestamp if over MAX_SEEN_HASHES (500)
        val trimmed = if (existingMap.size > MAX_SEEN_HASHES) {
            existingMap.entries.sortedBy { it.value }.takeLast(MAX_SEEN_HASHES)
        } else {
            existingMap.entries.toList()
        }

        val outObj = JSONObject()
        for ((k, v) in trimmed) {
            outObj.put(k, v)
        }
        SecureStore.putString(context, SECURE_KEY_SEEN_HASHES, outObj.toString())
    }

    /**
     * D1: Tracks the latest processed message timestamp per (package, conversationKey) so
     * multi-message MessagingStyle SMS notifications only parse newly arrived messages.
     */
    fun getThreadWatermark(context: Context, sourcePackage: String, conversationKey: String): Long =
        synchronized(storeLock) {
            loadThreadWatermarksLocked(context)["$sourcePackage|$conversationKey"] ?: 0L
        }

    fun updateThreadWatermark(context: Context, sourcePackage: String, conversationKey: String, timestampMillis: Long) {
        synchronized(storeLock) {
            val map = loadThreadWatermarksLocked(context)
            val key = "$sourcePackage|$conversationKey"
            val prev = map[key] ?: 0L
            if (timestampMillis <= prev) return
            map[key] = timestampMillis
            val trimmed = if (map.size > MAX_THREAD_WATERMARKS) {
                map.entries.sortedBy { it.value }.takeLast(MAX_THREAD_WATERMARKS)
            } else {
                map.entries.toList()
            }
            val outObj = JSONObject()
            for ((k, v) in trimmed) {
                outObj.put(k, v)
            }
            SecureStore.putString(context, SECURE_KEY_WATERMARKS, outObj.toString())
        }
    }

    private fun loadThreadWatermarksLocked(context: Context): LinkedHashMap<String, Long> {
        val raw = SecureStore.getString(context, SECURE_KEY_WATERMARKS)
        val map = LinkedHashMap<String, Long>()
        if (!raw.isNullOrBlank()) {
            val obj = runCatching { JSONObject(raw) }.getOrNull()
            if (obj != null) {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.optLong(k, 0L)
                    if (v > 0L) map[k] = v
                }
            }
        }
        return map
    }

    /**
     * D1: Pure helper that splits a notification into individual messages.
     * - If MessagingStyle `messages` are present, returns each message separately (NEVER concatenated!).
     * - Else if InboxStyle `EXTRA_TEXT_LINES` are present, returns each non-blank line separately.
     * - Otherwise safely joins non-redundant `bigText`, `text`, and `subText` without losing context.
     */
    fun extractIndividualMessages(
        title: String,
        text: String,
        bigText: String,
        messagingStyleMessages: List<Triple<String, String, Long>>,
        fallbackPostTimeMillis: Long,
        subText: String = "",
        textLines: List<String> = emptyList(),
        titleBig: String = ""
    ): List<ExtractedNotificationMessage> {
        val effectiveDefaultTitle = titleBig.trim().ifBlank { title.trim() }
        val cleanSubText = subText.trim()

        if (messagingStyleMessages.isNotEmpty()) {
            return messagingStyleMessages.mapNotNull { (sender, body, ts) ->
                val cleanBody = body.trim()
                if (cleanBody.isBlank()) return@mapNotNull null
                val effectiveTitle = sender.trim().ifBlank { effectiveDefaultTitle }
                val effectiveTs = if (ts > 0L) ts else fallbackPostTimeMillis
                ExtractedNotificationMessage(
                    title = effectiveTitle,
                    body = cleanBody,
                    timestampMillis = effectiveTs
                )
            }.sortedBy { it.timestampMillis }
        }

        val nonBlankLines = textLines.map { it.trim() }.filter { it.isNotBlank() }
        if (nonBlankLines.isNotEmpty()) {
            return nonBlankLines.map { line ->
                val bodyWithSub = if (cleanSubText.isNotBlank() &&
                    !line.contains(cleanSubText, ignoreCase = true) &&
                    !effectiveDefaultTitle.contains(cleanSubText, ignoreCase = true)
                ) {
                    "$line\n$cleanSubText"
                } else {
                    line
                }
                ExtractedNotificationMessage(
                    title = effectiveDefaultTitle,
                    body = bodyWithSub,
                    timestampMillis = fallbackPostTimeMillis
                )
            }
        }

        val bodyParts = mutableListOf<String>()
        fun appendNonRedundant(candidate: String) {
            val clean = candidate.trim()
            if (clean.isBlank()) return
            if (bodyParts.any { existing -> existing.contains(clean, ignoreCase = true) }) return
            bodyParts.removeAll { existing -> clean.contains(existing, ignoreCase = true) }
            bodyParts.add(clean)
        }
        appendNonRedundant(bigText)
        appendNonRedundant(text)
        if (cleanSubText.isNotBlank() && !effectiveDefaultTitle.contains(cleanSubText, ignoreCase = true)) {
            appendNonRedundant(cleanSubText)
        }

        val combinedBody = bodyParts.joinToString("\n").trim()
        if (effectiveDefaultTitle.isBlank() && combinedBody.isBlank()) return emptyList()
        return listOf(
            ExtractedNotificationMessage(
                title = effectiveDefaultTitle,
                body = combinedBody,
                timestampMillis = fallbackPostTimeMillis
            )
        )
    }

    /**
     * D1: Processes a MessagingStyle SMS conversation notification, handling ONLY new messages
     * beyond the conversation watermark without gluing older messages together.
     */
    fun processMessagingThreadNotification(
        context: Context,
        sourcePackage: String,
        conversationKey: String,
        messages: List<ExtractedNotificationMessage>,
        notificationTag: String? = null
    ): List<DetectedBankTransaction> {
        if (messages.isEmpty()) return emptyList()
        val watermark = getThreadWatermark(context, sourcePackage, conversationKey)
        val results = mutableListOf<DetectedBankTransaction>()
        var highestTs = watermark

        for (msg in messages.sortedBy { it.timestampMillis }) {
            if (watermark > 0L && msg.timestampMillis <= watermark) {
                MasroufiDetectionLog.record(
                    sourcePackage = sourcePackage,
                    stage = "thread",
                    reasonCode = MasroufiDetectionLog.REASON_OLD_THREAD_MESSAGE,
                    messageLength = msg.title.length + msg.body.length
                )
                continue
            }
            if (msg.timestampMillis > highestTs) {
                highestTs = msg.timestampMillis
            }
            val detected = processIncomingNotification(
                context = context,
                sourcePackage = sourcePackage,
                rawTitle = msg.title,
                rawBody = msg.body,
                notificationKey = "",
                notificationTag = notificationTag,
                nowMillis = msg.timestampMillis
            )
            if (detected != null) {
                results.add(detected)
            }
        }
        if (highestTs > watermark) {
            updateThreadWatermark(context, sourcePackage, conversationKey, highestTs)
        }
        return results
    }

    /**
     * Processes a single notification message from an allowlisted app, logs structured outcome
     * in [MasroufiDetectionLog], deduplicates atomically (D5), and either auto-confirms (D8/S6)
     * or enqueues into "To confirm" and posts a grouped quiet notification (D9).
     */
    fun processIncomingNotification(
        context: Context,
        sourcePackage: String,
        rawTitle: String,
        rawBody: String,
        notificationKey: String = "",
        notificationTag: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): DetectedBankTransaction? {
        val msgLen = rawTitle.length + rawBody.length
        if (isPaused(context)) {
            MasroufiDetectionLog.record(sourcePackage, "filter", MasroufiDetectionLog.REASON_PAUSED, msgLen)
            return null
        }
        if (!isPackageAllowed(context, sourcePackage, notificationTag)) {
            MasroufiDetectionLog.record(sourcePackage, "filter", MasroufiDetectionLog.REASON_NOT_ALLOWED, msgLen)
            return null
        }

        val snap = MasroufiWidgetStore.loadSnapshot(context)
        val parseResult = BankMessageParser.parseDetailed(
            rawTitle = rawTitle,
            rawBody = rawBody,
            sourcePackage = sourcePackage,
            notificationKey = "", // D1: Never include mutable/sticky notificationKey in dedupe hash
            postTimeMillis = nowMillis,
            envelopes = emptyList(),
            language = snap.language
        )

        val parsed = when (parseResult) {
            is BankParseResult.Rejected -> {
                MasroufiDetectionLog.record(sourcePackage, "parse", parseResult.reasonCode, msgLen)
                return null
            }
            is BankParseResult.Parsed -> parseResult.notification
        }

        val senderTrustKey = rawTitle.trim().lowercase(Locale.ROOT).take(48)
            .ifBlank { parsed.bankTemplateId }

        var shouldAutoConfirm = false
        var detected: DetectedBankTransaction? = null

        synchronized(storeLock) {
            // Check ignored merchants
            val ignored = prefs(context).getStringSet(KEY_IGNORED_MERCHANTS, emptySet()) ?: emptySet()
            if (parsed.merchant.isNotBlank() && parsed.merchant.trim().lowercase(Locale.ROOT) in ignored) {
                MasroufiDetectionLog.record(sourcePackage, "filter", MasroufiDetectionLog.REASON_IGNORED_MERCHANT, msgLen)
                return null
            }

            // Atomic dedupe check (7-day / 500-entry LRU) — do not burn hash until persistence succeeds
            val seenHashes = loadActiveDedupeHashesLocked(context, nowMillis)
            if (seenHashes.containsKey(parsed.dedupeHash)) {
                MasroufiDetectionLog.record(sourcePackage, "dedupe", MasroufiDetectionLog.REASON_DUPLICATE, msgLen)
                return null
            }

            val sourceLabel = KNOWN_BANK_AND_SMS_APPS.find { it.first == sourcePackage }?.second
                ?: resolveAppLabel(context.packageManager, sourcePackage)
                ?: "Banque / SMS"

            val item = DetectedBankTransaction(
                id = "det_${nowMillis}_${parsed.dedupeHash.take(6)}",
                amount = parsed.amount,
                currency = parsed.currency,
                direction = parsed.direction,
                merchant = parsed.merchant,
                isoDate = parsed.isoDate,
                sourcePackage = sourcePackage,
                sourceAppLabel = sourceLabel,
                dedupeHash = parsed.dedupeHash,
                confidence = parsed.confidence,
                needsReview = parsed.needsReview,
                suggestedEnvelopeKey = parsed.suggestedEnvelopeKey,
                suggestedCategoryName = parsed.suggestedCategoryName,
                // D10: Do not persist masked sample preview by default
                maskedSamplePreview = "",
                createdAtMillis = nowMillis,
                senderTrustKey = senderTrustKey
            )

            // D8 & S6: Auto-confirm ONLY when enabled, expense, high confidence, under cap,
            // AND sender is trusted (known bank app OR previously confirmed SMS sender)
            val trustedSender = isKnownBankAppPackage(sourcePackage) ||
                senderTrustKey in (prefs(context).getStringSet(KEY_CONFIRMED_SENDERS, emptySet()) ?: emptySet())

            if (isAutoConfirmEnabled(context) &&
                trustedSender &&
                item.direction == "expense" &&
                !item.needsReview &&
                item.confidence >= 0.80 &&
                item.amount <= getAutoConfirmMaxAmount(context)
            ) {
                recordDedupeHashLocked(context, seenHashes, parsed.dedupeHash, nowMillis)
                shouldAutoConfirm = true
                detected = item
            } else {
                val updated = listOf(item) + loadPendingItemsLocked(context, nowMillis)
                val persisted = savePendingItemsLocked(context, updated)
                if (!persisted) {
                    return null
                }
                recordDedupeHashLocked(context, seenHashes, parsed.dedupeHash, nowMillis)
                detected = item
            }
        }

        val finalItem = detected ?: return null
        if (shouldAutoConfirm) {
            MasroufiDetectionLog.record(sourcePackage, "store", MasroufiDetectionLog.REASON_AUTO_CONFIRMED, msgLen)
            confirmDetectedItem(context, finalItem, isAutoConfirmed = true)
        } else {
            MasroufiDetectionLog.record(sourcePackage, "store", MasroufiDetectionLog.REASON_STORED, msgLen)
            // D9: Notify user with quiet grouped Confirm / Edit / Ignore alert
            MasroufiNotificationManager.postBankDetectedNotification(context, finalItem)
        }
        return finalItem
    }

    /**
     * D8 & S6: Confirms a pending detected transaction through the bank-account save path
     * (`acc_bank` for expense/income, `acc_bank -> acc_cash` for ATM transfer) using `item.isoDate`.
     * Marks the sender as trusted for future auto-confirm and posts a 10-minute Undo alert if auto-confirmed.
     */
    fun confirmDetectedItem(
        context: Context,
        item: DetectedBankTransaction,
        isAutoConfirmed: Boolean = false
    ): List<com.example.data.TransactionItem> {
        val baseLabel = item.merchant.ifBlank { item.suggestedCategoryName }
        val descText = if (isAutoConfirmed && !baseLabel.startsWith(AUTO_DETECTED_DESC_PREFIX)) {
            "$AUTO_DETECTED_DESC_PREFIX$baseLabel"
        } else {
            baseLabel
        }
        val created = MasroufiWidgetStore.recordQuickCaptureTransactions(
            context = context,
            items = listOf(
                VoiceParsedTransaction(
                    rawTranscript = "$baseLabel ${item.amount.toCanonicalDecimalString()}",
                    amount = item.amount,
                    description = descText,
                    envelopeKey = if (item.direction == "transfer") "autre" else item.suggestedEnvelopeKey,
                    categoryName = item.suggestedCategoryName,
                    txType = item.direction
                )
            ),
            defaultAccountId = "acc_bank",
            overrideIsoDate = item.isoDate
        )
        if (!isAutoConfirmed && item.senderTrustKey.isNotBlank()) {
            markSenderConfirmed(context, item.senderTrustKey)
        }
        removePendingItem(context, item.id)
        if (isAutoConfirmed && created.isNotEmpty()) {
            MasroufiNotificationManager.postAutoConfirmedUndoNotification(
                context = context,
                item = item,
                createdTx = created.first()
            )
        }
        return created
    }

    fun findPendingItemById(context: Context, id: String): DetectedBankTransaction? =
        synchronized(storeLock) {
            loadPendingItemsLocked(context, System.currentTimeMillis()).firstOrNull { it.id == id }
        }

    fun removePendingItem(context: Context, id: String) {
        synchronized(storeLock) {
            val updated = loadPendingItemsLocked(context, System.currentTimeMillis()).filterNot { it.id == id }
            savePendingItemsLocked(context, updated)
        }
    }

    /**
     * Kill switch: deletes all pending detected items, seen dedupe hashes, and watermarks from the device.
     */
    fun deleteAllDetectedData(context: Context) {
        synchronized(storeLock) {
            SecureStore.remove(context, SECURE_KEY_PENDING)
            SecureStore.remove(context, SECURE_KEY_SEEN_HASHES)
            SecureStore.remove(context, SECURE_KEY_WATERMARKS)
            prefs(context).edit()
                .remove(KEY_PENDING_JSON)
                .remove(KEY_SEEN_HASHES_LEGACY)
                .remove(KEY_SEEN_HASHES_V2)
                .remove(KEY_THREAD_WATERMARKS_JSON)
                .remove(KEY_CONFIRMED_SENDERS)
                .apply()
            _pendingItemsFlow.value = emptyList()
            MasroufiDetectionLog.clear()
            MasroufiWidgetStore.refreshWidgetsAndTiles(context)
        }
    }

    /**
     * Schedules the 15-minute WorkManager health check that calls `requestRebind` if the
     * NotificationListenerService becomes disconnected while permission is granted.
     */
    fun scheduleListenerHealthWorker(context: Context) {
        runCatching {
            val request = PeriodicWorkRequestBuilder<BankListenerHealthWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                LISTENER_HEALTH_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    /**
     * Requests Android to rebind [BankNotificationListenerService] when notification access is
     * granted, detection is not paused, and the listener is currently disconnected.
     */
    fun requestListenerRebindIfNeeded(context: Context) {
        val appCtx = context.applicationContext
        if (ManualSetup.isNotificationListenerGranted(appCtx) &&
            !isPaused(appCtx) &&
            !MasroufiDetectionLog.snapshotHealth().isConnected
        ) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    NotificationListenerService.requestRebind(
                        ComponentName(appCtx, BankNotificationListenerService::class.java)
                    )
                }
            }
        }
    }

    /**
     * Phase 10E Step 4: Posts a sample bank notification (and feeds the parser) so the user can test
     * the flow immediately with one tap.
     */
    fun postSampleTestBankNotification(context: Context): DetectedBankTransaction? {
        val sampleTitle = "CIH Bank"
        val sampleBody = "Paiement par carte de 85,00 MAD chez MARJANE MARKET le 15/11"
        val nowMs = System.currentTimeMillis()
        MasroufiNotificationManager.ensureChannels(context)
        if (MasroufiNotificationManager.hasNotificationPermission(context)) {
            runCatching {
                val notif = NotificationCompat.Builder(context, MasroufiNotificationManager.CHANNEL_BILLS_DARET)
                    .setSmallIcon(R.mipmap.ic_launcher_round)
                    .setContentTitle(sampleTitle)
                    .setContentText(sampleBody)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_TAG, 4088, notif)
            }
        }
        return processIncomingNotification(
            context = context,
            sourcePackage = context.packageName,
            rawTitle = sampleTitle,
            rawBody = sampleBody,
            notificationKey = "",
            notificationTag = TEST_NOTIFICATION_TAG,
            nowMillis = nowMs
        )
    }
}

/**
 * D1, D5 & Listener Health: NotificationListenerService that reads only allowlisted bank/SMS apps on-device.
 * Iterates MessagingStyle messages individually (never concatenating an entire SMS thread).
 */
class BankNotificationListenerService : NotificationListenerService() {

    private val exceptionHandler = CoroutineExceptionHandler { _, _ ->
        // Keep listener scope alive across any transient background storage/parse failure
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    override fun onListenerConnected() {
        super.onListenerConnected()
        MasroufiDetectionLog.markListenerConnected()
        BankDetectionStore.scheduleListenerHealthWorker(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (sbn.isOngoing) {
            MasroufiDetectionLog.record(pkg, "filter", MasroufiDetectionLog.REASON_ONGOING, 0)
            return
        }
        val notif = sbn.notification ?: return

        val tag = sbn.tag
        if (BankDetectionStore.isPaused(this)) {
            MasroufiDetectionLog.record(pkg, "filter", MasroufiDetectionLog.REASON_PAUSED, 0)
            return
        }
        if (!BankDetectionStore.isPackageAllowed(this, pkg, tag)) {
            MasroufiDetectionLog.record(pkg, "filter", MasroufiDetectionLog.REASON_NOT_ALLOWED, 0)
            return
        }

        val extras = notif.extras ?: Bundle.EMPTY
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val titleBig = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotBlank() } }
            .orEmpty()

        // D1: Extract MessagingStyle messages individually instead of gluing them together
        val messagingTriples = mutableListOf<Triple<String, String, Long>>()
        @Suppress("DEPRECATION")
        val parcelables = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (parcelables != null && parcelables.isNotEmpty()) {
            for (p in parcelables) {
                val b = p as? Bundle ?: continue
                val mText = b.getCharSequence("text")?.toString().orEmpty()
                if (mText.isBlank()) continue
                val mSender = b.getCharSequence("sender")?.toString().orEmpty()
                    .ifBlank {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            @Suppress("DEPRECATION")
                            (b.getParcelable<android.app.Person>("sender_person"))?.name?.toString().orEmpty()
                        } else ""
                    }
                val mTime = b.getLong("time", sbn.postTime)
                messagingTriples.add(Triple(mSender, mText, mTime))
            }
        }

        // Only drop group summaries when they carry neither MessagingStyle messages nor InboxStyle textLines
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0 &&
            messagingTriples.isEmpty() &&
            textLines.isEmpty()
        ) {
            MasroufiDetectionLog.record(pkg, "filter", MasroufiDetectionLog.REASON_GROUP_SUMMARY, 0)
            return
        }

        val extractedMessages = BankDetectionStore.extractIndividualMessages(
            title = title,
            text = text,
            bigText = bigText,
            messagingStyleMessages = messagingTriples,
            fallbackPostTimeMillis = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
            subText = subText,
            textLines = textLines,
            titleBig = titleBig
        )
        if (extractedMessages.isEmpty()) {
            MasroufiDetectionLog.record(pkg, "extract", MasroufiDetectionLog.REASON_EMPTY, 0)
            return
        }

        val conversationKey = sbn.key ?: "${pkg}_${title.take(32)}"
        val isMessagingThread = messagingTriples.isNotEmpty()

        serviceScope.launch {
            runCatching {
                if (isMessagingThread) {
                    BankDetectionStore.processMessagingThreadNotification(
                        context = applicationContext,
                        sourcePackage = pkg,
                        conversationKey = conversationKey,
                        messages = extractedMessages,
                        notificationTag = tag
                    )
                } else {
                    for (msg in extractedMessages) {
                        BankDetectionStore.processIncomingNotification(
                            context = applicationContext,
                            sourcePackage = pkg,
                            rawTitle = msg.title,
                            rawBody = msg.body,
                            notificationKey = "",
                            notificationTag = tag,
                            nowMillis = msg.timestampMillis
                        )
                    }
                }
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MasroufiDetectionLog.markListenerDisconnected()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestRebind(ComponentName(this, BankNotificationListenerService::class.java))
            }
        }
    }
}

/**
 * Prompt 4: 15-minute WorkManager health worker that requests rebind of [BankNotificationListenerService]
 * if notification access is granted but the listener is currently disconnected.
 */
class BankListenerHealthWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        BankDetectionStore.requestListenerRebindIfNeeded(applicationContext)
        return Result.success()
    }
}

/**
 * Phase 10E & 10F: PrivilegedSetup interface with ShizukuSetup and ManualSetup implementations.
 */
interface PrivilegedSetup {
    fun isNotificationListenerGranted(context: Context): Boolean
    fun openNotificationAccessSettings(context: Context)
    fun openAppDetailsForRestrictedSettings(context: Context)
}

object ManualSetup : PrivilegedSetup {
    internal fun isListenerEnabledInSettingValue(
        enabledListenersFlat: String?,
        expectedComponent: ComponentName
    ): Boolean {
        if (enabledListenersFlat.isNullOrBlank()) return false
        val expectedFull = expectedComponent.flattenToString()
        val expectedShort = expectedComponent.flattenToShortString()
        val expectedSimpleClass = expectedComponent.className.substringAfterLast('.')

        return enabledListenersFlat.split(':').any { rawEntry ->
            val entry = rawEntry.trim()
            if (entry.isEmpty()) return@any false
            if (entry == expectedFull || entry == expectedShort) return@any true
            val parsed = ComponentName.unflattenFromString(entry) ?: return@any false
            if (parsed == expectedComponent) return@any true
            parsed.packageName == expectedComponent.packageName &&
                (parsed.className == expectedComponent.className ||
                    parsed.className.endsWith(".$expectedSimpleClass"))
        }
    }

    override fun isNotificationListenerGranted(context: Context): Boolean {
        return runCatching {
            val expected = ComponentName(context, BankNotificationListenerService::class.java)
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            if (flat != null) {
                isListenerEnabledInSettingValue(flat, expected)
            } else {
                NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            }
        }.getOrDefault(false)
    }

    override fun openNotificationAccessSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun openAppDetailsForRestrictedSettings(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            openAppDetailsForRestrictedSettings(context)
        }
    }
}

/**
 * Phase 10F: Optional Shizuku one-tap setup helper.
 */
object ShizukuSetup : PrivilegedSetup by ManualSetup {
    private const val SHIZUKU_MANAGER_PKG = "moe.shizuku.privileged.api"

    fun isShizukuInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_MANAGER_PKG, 0)
            true
        }.getOrDefault(false)
    }

    fun buildAllowlistedSetupCommands(context: Context): List<String> {
        val pkg = context.packageName
        require(pkg.matches(Regex("""^[a-zA-Z0-9_.]+$"""))) { "Invalid package name" }
        val component = ComponentName(context, BankNotificationListenerService::class.java).flattenToString()
        return listOf(
            "cmd appops set $pkg ACCESS_RESTRICTED_SETTINGS allow",
            "cmd notification allow_listener $component"
        )
    }

    fun tryOneTapGrantOrFallback(context: Context, onResult: (Boolean, String) -> Unit) {
        if (!isShizukuInstalled(context)) {
            ManualSetup.openNotificationAccessSettings(context)
            onResult(false, "Shizuku non détecté : ouverture des paramètres Android.")
            return
        }
        val commands = buildAllowlistedSetupCommands(context)
        val succeeded = runCatching {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val pingBinder = shizukuClass.getMethod("pingBinder").invoke(null) as? Boolean ?: false
            if (!pingBinder) return@runCatching false
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            for (cmd in commands) {
                val proc = newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
                proc?.waitFor()
            }
            ManualSetup.isNotificationListenerGranted(context)
        }.getOrDefault(false)

        if (succeeded) {
            onResult(true, "✓ Accès aux notifications activé via Shizuku !")
        } else {
            ManualSetup.openNotificationAccessSettings(context)
            onResult(false, "Ouverture des paramètres d'accès aux notifications…")
        }
    }
}

/**
 * Phase 11A: Manages the optional "Masroufi Voice" launcher activity-alias.
 */
object VoiceLauncherAliasManager {
    private const val ALIAS_CLASS_NAME = "com.example.widget.MasroufiVoiceLauncherAlias"

    fun isVoiceAliasEnabled(context: Context): Boolean {
        return runCatching {
            val cn = ComponentName(context.packageName, ALIAS_CLASS_NAME)
            val state = context.packageManager.getComponentEnabledSetting(cn)
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }.getOrDefault(false)
    }

    fun setVoiceAliasEnabled(context: Context, enabled: Boolean) {
        runCatching {
            val cn = ComponentName(context.packageName, ALIAS_CLASS_NAME)
            val newState = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                cn,
                newState,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}

/**
 * Manages the opt-in "Masroufi +" launcher activity-alias so users can map
 * Double-press Power / Side Button -> Open app -> "Masroufi +" in Android OEM settings
 * (Samsung Side button, Xiaomi/Redmi/POCO Gesture shortcuts, OnePlus/Nothing/Pixel Quick Tap).
 */
object QuickAddLauncherAliasManager {
    const val ALIAS_CLASS_NAME = "com.example.widget.MasroufiQuickAddLauncherAlias"

    fun isQuickAddAliasEnabled(context: Context): Boolean {
        return runCatching {
            val cn = ComponentName(context.packageName, ALIAS_CLASS_NAME)
            val state = context.packageManager.getComponentEnabledSetting(cn)
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }.getOrDefault(false)
    }

    fun setQuickAddAliasEnabled(context: Context, enabled: Boolean) {
        runCatching {
            val cn = ComponentName(context.packageName, ALIAS_CLASS_NAME)
            val newState = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                cn,
                newState,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    /**
     * Opens the closest available system Power/Side button or Gesture settings screen,
     * falling back safely to Android System Settings without crashing on any OEM ROM.
     */
    fun openSystemPowerButtonSettings(context: Context): Boolean {
        val candidates = listOf(
            // Samsung One UI Side button settings
            Intent().setClassName(
                "com.android.settings",
                "com.samsung.android.settings.usefulfeature.functionkey.FunctionKeySettings"
            ),
            // Xiaomi MIUI / HyperOS Gesture & Button shortcuts
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.KeySettingsActivity"
            ),
            // Standard Android System Gestures settings
            Intent("android.settings.GESTURE_SETTINGS"),
            // Universal fallback: main Android Settings
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )
        for (candidate in candidates) {
            candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching {
                context.startActivity(candidate)
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        return false
    }

    /** Launches QuickCaptureActivity directly in Add Expense keypad mode for instant preview/testing. */
    fun launchQuickAddTest(context: Context) {
        runCatching {
            val intent = Intent(context, com.example.widget.QuickCaptureActivity::class.java).apply {
                action = com.example.widget.MasroufiAppWidget.ACTION_QUICK_ADD
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
}
