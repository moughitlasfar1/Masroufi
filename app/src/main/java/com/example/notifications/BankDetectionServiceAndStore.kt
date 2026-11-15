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
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.R
import com.example.domain.BankMessageParser
import com.example.domain.PayCycleEngine
import com.example.domain.VoiceParsedTransaction
import com.example.widget.MasroufiWidgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class DetectedBankTransaction(
    val id: String,
    val amount: Double,
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
    val maskedSamplePreview: String,
    val possibleDuplicateTxId: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("amount", amount)
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
        put("maskedSamplePreview", maskedSamplePreview)
        put("possibleDuplicateTxId", possibleDuplicateTxId)
        put("createdAtMillis", createdAtMillis)
    }

    companion object {
        fun fromJson(obj: JSONObject): DetectedBankTransaction = DetectedBankTransaction(
            id = obj.optString("id", "det_${System.currentTimeMillis()}"),
            amount = obj.optDouble("amount", 0.0),
            currency = obj.optString("currency", "DH"),
            direction = obj.optString("direction", "expense"),
            merchant = obj.optString("merchant", "Paiement"),
            isoDate = obj.optString("isoDate", PayCycleEngine.todayIso()),
            sourcePackage = obj.optString("sourcePackage", "generic"),
            sourceAppLabel = obj.optString("sourceAppLabel", "Banque"),
            dedupeHash = obj.optString("dedupeHash", ""),
            confidence = obj.optDouble("confidence", 0.8),
            needsReview = obj.optBoolean("needsReview", false),
            suggestedEnvelopeKey = obj.optString("suggestedEnvelopeKey", "autre"),
            suggestedCategoryName = obj.optString("suggestedCategoryName", "Autre"),
            maskedSamplePreview = obj.optString("maskedSamplePreview", ""),
            possibleDuplicateTxId = obj.optString("possibleDuplicateTxId", ""),
            createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
        )
    }
}

data class AllowlistAppOption(
    val packageName: String,
    val label: String,
    val isAllowed: Boolean
)

/**
 * Phase 10A & 10D: On-device store for allowlisted bank/SMS packages, pending detected items,
 * ignored merchants, kill switches, and optional per-app auto-confirm rules.
 *
 * Privacy guarantees:
 * - Allowlist starts empty (`emptySet()`).
 * - Stores only structured [DetectedBankTransaction] items locally; never stores raw notification text.
 * - Pending queue is capped at [MAX_PENDING_ITEMS] and items expire after 14 days ([EXPIRY_MILLIS]).
 */
object BankDetectionStore {
    const val MAX_PENDING_ITEMS = 40
    const val EXPIRY_MILLIS = 14L * 24L * 3600L * 1000L
    const val TEST_NOTIFICATION_TAG = "masroufi_bank_test_sample"
    const val AUTO_DETECTED_DESC_PREFIX = "[Auto] "

    private const val PREFS_NAME = "masroufi_bank_detection_prefs"
    private const val KEY_ALLOWED_PACKAGES = "allowed_packages_set"
    private const val KEY_PAUSED = "detection_paused"
    private const val KEY_PENDING_JSON = "pending_detected_items_json"
    private const val KEY_SEEN_HASHES = "seen_dedupe_hashes_set"
    private const val KEY_IGNORED_MERCHANTS = "ignored_merchants_set"
    private const val KEY_AUTO_CONFIRM_ENABLED = "auto_confirm_enabled"
    private const val KEY_AUTO_CONFIRM_CAP = "auto_confirm_max_cap"

    private val _pendingItemsFlow = MutableStateFlow<List<DetectedBankTransaction>>(emptyList())
    val pendingItemsFlow: StateFlow<List<DetectedBankTransaction>> = _pendingItemsFlow.asStateFlow()

    val KNOWN_BANK_AND_SMS_APPS: List<Pair<String, String>> = listOf(
        "com.google.android.apps.messaging" to "Google Messages (SMS)",
        "com.samsung.android.messaging" to "Samsung Messages (SMS)",
        "com.android.mms" to "Messages SMS",
        "com.cih.cihmobile" to "CIH Mobile",
        "ma.attijariwafabank.lbankalik" to "Attijari Mobile / L'bankalik",
        "com.bcp.pocketbank" to "Pocket Bank (Banque Populaire)",
        "com.bmce.agencedirecte" to "BMCE / Bank of Africa",
        "com.cfgbank.mobile" to "CFG Bank",
        "ma.baridbank.mobile" to "Barid Bank Mobile"
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllowedPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ALLOWED_PACKAGES, emptySet())?.toSet() ?: emptySet()

    fun setPackageAllowed(context: Context, packageName: String, allowed: Boolean) {
        val current = getAllowedPackages(context).toMutableSet()
        if (allowed) current.add(packageName) else current.remove(packageName)
        prefs(context).edit().putStringSet(KEY_ALLOWED_PACKAGES, current).apply()
    }

    fun isPackageAllowed(context: Context, packageName: String, notificationTag: String? = null): Boolean {
        if (notificationTag == TEST_NOTIFICATION_TAG && packageName == context.packageName) {
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

    fun getAutoConfirmMaxAmount(context: Context): Double =
        prefs(context).getFloat(KEY_AUTO_CONFIRM_CAP, 200f).toDouble()

    fun setAutoConfirmMaxAmount(context: Context, maxAmount: Double) {
        prefs(context).edit().putFloat(KEY_AUTO_CONFIRM_CAP, maxAmount.toFloat().coerceAtLeast(10f)).apply()
    }

    fun getIgnoredMerchants(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_IGNORED_MERCHANTS, emptySet())?.toSet() ?: emptySet()

    fun addIgnoredMerchant(context: Context, merchant: String) {
        val norm = merchant.trim().lowercase()
        if (norm.isBlank()) return
        val updated = getIgnoredMerchants(context).toMutableSet().apply { add(norm) }
        prefs(context).edit().putStringSet(KEY_IGNORED_MERCHANTS, updated).apply()
        // Also remove any pending items from this merchant
        val remaining = loadPendingItems(context).filterNot { it.merchant.trim().lowercase() == norm }
        savePendingItemsInternal(context, remaining)
    }

    fun getCandidateApps(context: Context): List<AllowlistAppOption> {
        val allowed = getAllowedPackages(context)
        val options = mutableMapOf<String, AllowlistAppOption>()
        KNOWN_BANK_AND_SMS_APPS.forEach { (pkg, label) ->
            options[pkg] = AllowlistAppOption(pkg, label, pkg in allowed)
        }
        // Also include the app itself for debug/sample testing
        val selfPkg = context.packageName
        options[selfPkg] = AllowlistAppOption(selfPkg, "Masroufi (Simulation test)", selfPkg in allowed)
        return options.values.toList()
    }

    /**
     * Loads non-expired pending items (automatically pruning items older than 14 days
     * and enforcing [MAX_PENDING_ITEMS]).
     */
    fun loadPendingItems(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): List<DetectedBankTransaction> {
        val p = prefs(context)
        val raw = p.getString(KEY_PENDING_JSON, null)
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
            savePendingItemsInternal(context, capped)
        } else {
            _pendingItemsFlow.value = capped
        }
        return capped
    }

    private fun savePendingItemsInternal(context: Context, items: List<DetectedBankTransaction>) {
        val capped = items.sortedByDescending { it.createdAtMillis }.take(MAX_PENDING_ITEMS)
        val arr = JSONArray()
        capped.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_PENDING_JSON, arr.toString()).apply()
        _pendingItemsFlow.value = capped
        MasroufiWidgetStore.refreshWidgetsAndTiles(context)
    }

    /**
     * Processes a notification from an allowlisted app, discards OTPs, deduplicates,
     * and either auto-confirms (if eligible) or enqueues into "To confirm".
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
        if (isPaused(context)) return null
        if (!isPackageAllowed(context, sourcePackage, notificationTag)) return null

        val snap = MasroufiWidgetStore.loadSnapshot(context)
        val parsed = BankMessageParser.parse(
            rawTitle = rawTitle,
            rawBody = rawBody,
            sourcePackage = sourcePackage,
            notificationKey = notificationKey,
            isoDate = PayCycleEngine.todayIso(),
            envelopes = emptyList(),
            language = snap.language
        ) ?: return null

        // Check ignored merchants
        if (parsed.merchant.trim().lowercase() in getIgnoredMerchants(context)) {
            return null
        }

        // Check dedupe hash
        val seenHashes = prefs(context).getStringSet(KEY_SEEN_HASHES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (parsed.dedupeHash in seenHashes) {
            return null
        }
        seenHashes.add(parsed.dedupeHash)
        val trimmedHashes = if (seenHashes.size > 200) seenHashes.toList().takeLast(150).toSet() else seenHashes
        prefs(context).edit().putStringSet(KEY_SEEN_HASHES, trimmedHashes).apply()

        val sourceLabel = KNOWN_BANK_AND_SMS_APPS.find { it.first == sourcePackage }?.second ?: "Banque / SMS"
        val detected = DetectedBankTransaction(
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
            maskedSamplePreview = parsed.maskedSamplePreview,
            createdAtMillis = nowMillis
        )

        // Check optional auto-confirm rule: only for expenses, above confidence bar, under cap
        if (isAutoConfirmEnabled(context) &&
            detected.direction == "expense" &&
            !detected.needsReview &&
            detected.confidence >= 0.80 &&
            detected.amount <= getAutoConfirmMaxAmount(context)
        ) {
            confirmDetectedItem(context, detected, isAutoConfirmed = true)
            return detected
        }

        val updated = listOf(detected) + loadPendingItems(context, nowMillis)
        savePendingItemsInternal(context, updated)
        return detected
    }

    /**
     * Confirms a pending detected transaction, saving it with `[Auto] ` prefix if auto-confirmed
     * (compatibly with `firestore.rules` without modifying schema fields).
     */
    fun confirmDetectedItem(
        context: Context,
        item: DetectedBankTransaction,
        isAutoConfirmed: Boolean = false
    ) {
        val descText = if (isAutoConfirmed && !item.merchant.startsWith(AUTO_DETECTED_DESC_PREFIX)) {
            "$AUTO_DETECTED_DESC_PREFIX${item.merchant}"
        } else {
            item.merchant
        }
        MasroufiWidgetStore.recordQuickCaptureTransactions(
            context = context,
            items = listOf(
                VoiceParsedTransaction(
                    rawTranscript = "${item.merchant} ${item.amount}",
                    amount = item.amount,
                    description = descText,
                    envelopeKey = item.suggestedEnvelopeKey,
                    categoryName = item.suggestedCategoryName,
                    txType = item.direction
                )
            )
        )
        removePendingItem(context, item.id)
    }

    fun removePendingItem(context: Context, id: String) {
        val updated = loadPendingItems(context).filterNot { it.id == id }
        savePendingItemsInternal(context, updated)
    }

    /**
     * Kill switch: deletes all pending detected items and seen dedupe hashes from the device.
     */
    fun deleteAllDetectedData(context: Context) {
        prefs(context).edit()
            .remove(KEY_PENDING_JSON)
            .remove(KEY_SEEN_HASHES)
            .apply()
        _pendingItemsFlow.value = emptyList()
        MasroufiWidgetStore.refreshWidgetsAndTiles(context)
    }

    /**
     * Phase 10E Step 4: Posts a sample bank notification (and feeds the parser) so the user can test
     * the flow immediately with one tap.
     */
    fun postSampleTestBankNotification(context: Context): DetectedBankTransaction? {
        val sampleTitle = "CIH Bank"
        val sampleBody = "Paiement par carte de 85,00 MAD chez MARJANE MARKET le 15/11"
        MasroufiNotificationManager.ensureChannels(context)
        if (MasroufiNotificationManager.hasNotificationPermission(context)) {
            runCatching {
                val notif = NotificationCompat.Builder(context, MasroufiNotificationManager.CHANNEL_BILLS_DARET)
                    .setSmallIcon(R.mipmap.ic_launcher_round)
                    .setContentTitle(sampleTitle)
                    .setContentText(sampleBody)
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
            notificationKey = "test_${System.currentTimeMillis()}",
            notificationTag = TEST_NOTIFICATION_TAG
        )
    }
}

/**
 * Phase 10B: NotificationListenerService that reads only allowlisted bank/SMS apps on-device.
 */
class BankNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (sbn.isOngoing) return
        val notif = sbn.notification ?: return
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val pkg = sbn.packageName ?: return
        val tag = sbn.tag
        if (BankDetectionStore.isPaused(this)) return
        if (!BankDetectionStore.isPackageAllowed(this, pkg, tag)) return

        val extras = notif.extras ?: Bundle.EMPTY
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        // Also support MessagingStyle conversation messages for SMS apps
        val msgStyleText = buildString {
            val parcelables = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (parcelables != null) {
                for (p in parcelables) {
                    val b = p as? Bundle ?: continue
                    val mText = b.getCharSequence("text")?.toString().orEmpty()
                    if (mText.isNotBlank()) {
                        append(mText).append(" ")
                    }
                }
            }
        }.trim()

        val combinedBody = listOf(bigText.ifBlank { text }, msgStyleText)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        if (title.isBlank() && combinedBody.isBlank()) return
        val key = sbn.key ?: "${pkg}_${sbn.postTime}"

        serviceScope.launch {
            BankDetectionStore.processIncomingNotification(
                context = applicationContext,
                sourcePackage = pkg,
                rawTitle = title,
                rawBody = combinedBody,
                notificationKey = key,
                notificationTag = tag
            )
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        runCatching {
            requestRebind(ComponentName(this, BankNotificationListenerService::class.java))
        }
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
    override fun isNotificationListenerGranted(context: Context): Boolean {
        return runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
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
}

/**
 * Phase 10F: Optional Shizuku one-tap setup helper.
 * Validates package name and ComponentName strictly (no user input) and builds only the two
 * allowlisted setup commands:
 * 1) `cmd appops set <package> ACCESS_RESTRICTED_SETTINGS allow`
 * 2) `cmd notification allow_listener <package>/<listener class>`
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
            "cmd notification allow_listener $pkg/$component"
        )
    }

    fun tryOneTapGrantOrFallback(context: Context, onResult: (Boolean, String) -> Unit) {
        if (!isShizukuInstalled(context)) {
            ManualSetup.openNotificationAccessSettings(context)
            onResult(false, "Shizuku non détecté : ouverture des paramètres Android.")
            return
        }
        val commands = buildAllowlistedSetupCommands(context)
        // Attempt Shizuku execution if binder is active; fallback to manual settings screen if not
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
 * Phase 11A: Manages the optional "Masroufi Voice" launcher activity-alias so users can launch
 * voice capture directly from the home screen or via "Hey Google, open Masroufi Voice".
 * Off by default (`enabled = false` in AndroidManifest.xml).
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
