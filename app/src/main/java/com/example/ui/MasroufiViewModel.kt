package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountItem
import com.example.data.BackupFiscalSnapshot
import com.example.data.BackupV3Codec
import com.example.data.CustomizationPrefs
import com.example.data.DefaultCategoryResolver
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.MasroufiDataSource
import com.example.data.TransactionItem
import com.example.data.UnsupportedBackupVersionException
import com.example.data.UserProfile
import com.example.domain.AccountWithBalance
import com.example.domain.CycleSpendProjection
import com.example.domain.DetectedSubscription
import com.example.domain.EnvelopeStatus
import com.example.domain.PayCycleEngine
import com.example.domain.PayCycleWindow
import com.example.domain.SmartInsight
import com.example.domain.SmartReminder
import com.example.security.AppSecurityManager
import com.example.security.BackupEnvelopeInfo
import com.example.security.PortableBackupCrypto
import com.example.security.PortableCryptoResult
import com.masroufi.core.Money
import com.masroufi.core.sumMoney
import com.masroufi.credit.data.SavedCreditEntity
import java.math.BigDecimal
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class FilterState(
    val searchQuery: String = "",
    val selectedCategoryKey: String = "all",
    val selectedAccountId: String = "all",
    val selectedType: String = "all", // "all", "expense", "income", "transfer"
    val minAmount: Money? = null
)

data class WhatIfSimulatorState(
    val selectedEnvelopeKey: String = "cafe",
    val cutPercentage: Int = 30
)

data class ParsedBackupPayload(
    val backupVersion: Int,
    val exportedAtIso: String = "",
    val profile: UserProfile?,
    val customPrefs: CustomizationPrefs?,
    val accounts: List<AccountItem>,
    val envelopes: List<EnvelopeConfig>,
    val financialItems: List<FinancialItem>,
    val transactions: List<TransactionItem>,
    val learnedCategories: Map<String, String> = emptyMap(),
    val exportedAtEpochMillis: Long = 0L,
    val appVersion: String = "",
    val savedCredits: List<SavedCreditEntity> = emptyList(),
    val hasSavedCreditsSection: Boolean = false,
    val fiscal: BackupFiscalSnapshot? = null
)

data class BackupImportPreview(
    val isEncrypted: Boolean,
    val backupVersion: Int,
    val exportedAtIso: String,
    val accountsCount: Int,
    val envelopesCount: Int,
    val financialItemsCount: Int,
    val transactionsCount: Int,
    val totalOperationsCount: Int,
    val batchChunksCount: Int,
    val isSingleAtomicBatch: Boolean,
    val savedCreditsCount: Int = 0,
    val annualBillsCount: Int = 0,
    val encryptionVersion: Int = 0,
    val appVersion: String = ""
)

sealed class BackupRestoreResult {
    data class Success(
        val message: String,
        val backupVersion: Int,
        val restoredTransactionsCount: Int,
        val restoredCreditsCount: Int,
        val restoredFiscal: BackupFiscalSnapshot?
    ) : BackupRestoreResult()

    data class WrongPassphraseOrTampered(val message: String) : BackupRestoreResult()
    data class UnsupportedVersion(val version: Int, val message: String) : BackupRestoreResult()
    data class CorruptedOrInvalid(val message: String) : BackupRestoreResult()
    data class CommitFailed(val message: String) : BackupRestoreResult()
}

object BackupSerializer {
    private val FORMULA_PREFIXES = charArrayOf('=', '+', '-', '@')

    /**
     * Neutralizes spreadsheet formula injection (CSV Injection / DDE) and normalizes control characters.
     * - Replaces CR, LF, and TAB with spaces so cells cannot split rows or columns.
     * - Prepends a single quote (') if the first non-whitespace character is '=', '+', '-', or '@'.
     * - Escapes internal double quotes (" -> "") and wraps the field in double quotes.
     */
    fun sanitizeCsvCell(raw: String): String {
        val normalized = raw
            .replace("\r\n", " ")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ')
        val trimmedStart = normalized.trimStart()
        val neutralized = if (trimmedStart.isNotEmpty() && trimmedStart[0] in FORMULA_PREFIXES) {
            "'$normalized"
        } else {
            normalized
        }
        val escapedQuotes = neutralized.replace("\"", "\"\"")
        return "\"$escapedQuotes\""
    }

    fun encodeCsv(transactions: List<TransactionItem>): String {
        val sb = StringBuilder("ID,Type,Montant,Categorie,Compte,Description,Date\n")
        transactions.forEach { t ->
            sb.append(sanitizeCsvCell(t.id)).append(',')
                .append(sanitizeCsvCell(t.type)).append(',')
                .append(sanitizeCsvCell(t.amount.toCanonicalDecimalString())).append(',')
                .append(sanitizeCsvCell(t.category)).append(',')
                .append(sanitizeCsvCell(t.accountId)).append(',')
                .append(sanitizeCsvCell(t.desc)).append(',')
                .append(sanitizeCsvCell(t.isoDate)).append('\n')
        }
        return sb.toString()
    }

    fun encodeV3Backup(
        state: DashboardState,
        savedCredits: List<SavedCreditEntity> = emptyList(),
        fiscal: BackupFiscalSnapshot? = null,
        appVersion: String = com.example.BuildConfig.VERSION_NAME,
        exportedAtEpochMillis: Long = System.currentTimeMillis(),
        exportedAtIso: String = PayCycleEngine.todayIso()
    ): String {
        return BackupV3Codec.encodeV3Backup(
            state = state,
            savedCredits = savedCredits,
            fiscal = fiscal,
            appVersion = appVersion,
            exportedAtEpochMillis = exportedAtEpochMillis,
            exportedAtIso = exportedAtIso
        )
    }

    fun encodeV2Backup(state: DashboardState): String {
        val root = JSONObject().apply {
            put("backupVersion", 2)
            put("exportedAtIso", PayCycleEngine.todayIso())
            // Keep top-level keys for backward compatibility with v1/v2 readers (exact BigDecimal DH)
            put("baseBudget", state.profile.baseBudget.toBigDecimalDh())
            put("payCycleDay", state.profile.payCycleDay)
            put("rolloverEnabled", state.profile.rolloverEnabled)

            put(
                "profile",
                JSONObject().apply {
                    put("baseBudget", state.profile.baseBudget.toBigDecimalDh())
                    put("payCycleDay", state.profile.payCycleDay)
                    put("rolloverEnabled", state.profile.rolloverEnabled)
                    put("language", state.profile.language)
                    put("isDarkTheme", state.profile.isDarkTheme)
                }
            )

            put(
                "customPrefs",
                JSONObject().apply {
                    put("accentPalette", state.customPrefs.accentPalette)
                    put("currencySymbol", state.customPrefs.currencySymbol)
                    put("customTitle", state.customPrefs.customTitle)
                    put("customSubtitle", state.customPrefs.customSubtitle)
                    put("visibilityMask", state.customPrefs.toVisibilityMask())
                    put("dashboardOrder", state.customPrefs.normalizedOrder().joinToString(","))
                    put("themeMode", state.customPrefs.normalizedThemeMode())
                    put("eurToMadRate", state.customPrefs.normalizedEurRate())
                    put("usdToMadRate", state.customPrefs.normalizedUsdRate())
                }
            )
            val learnedObj = JSONObject()
            PayCycleEngine.getAllLearnedCategories().forEach { (k, v) ->
                learnedObj.put(k, v)
            }
            put("learnedCategories", learnedObj)

            val accArr = JSONArray()
            state.accounts.forEach { accWithBal ->
                val a = accWithBal.account
                accArr.put(
                    JSONObject().apply {
                        put("id", a.id)
                        put("name", a.name)
                        put("kind", a.kind)
                        put("initialBalance", a.initialBalance.toBigDecimalDh())
                        put("colorHex", a.colorHex)
                    }
                )
            }
            put("accounts", accArr)

            val envArr = JSONArray()
            state.envelopes.forEach { envStatus ->
                val e = envStatus.config
                envArr.put(
                    JSONObject().apply {
                        put("id", e.id)
                        put("key", e.key)
                        put("nameFr", e.nameFr)
                        put("nameAr", e.nameAr)
                        put("nameDarija", e.nameDarija)
                        put("nameEn", e.nameEn)
                        put("percentage", e.percentage)
                        put("hasCap", e.hasCap)
                        put("colorHex", e.colorHex)
                    }
                )
            }
            put("envelopes", envArr)

            val itemsArr = JSONArray()
            val allFinItems = state.presets + state.goals + state.bills + state.debts + state.darets + state.seasonals
            allFinItems.forEach { item ->
                itemsArr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("itemType", item.itemType)
                        put("name", item.name)
                        put("subtitle", item.subtitle)
                        put("amount", item.amount.toBigDecimalDh())
                        if (item.itemType == "daret") {
                            val members = item.resolvedDaretMembers()
                            put("daretMembers", members)
                            // Backward-compatible mirror for legacy v2 readers
                            put("secondaryAmount", members)
                        } else {
                            put("secondaryAmount", item.secondaryAmount.toBigDecimalDh())
                        }
                        put("dayOrTurn", item.dayOrTurn)
                        put("extraInt", item.extraInt)
                        put("dateOrCycle", item.dateOrCycle)
                    }
                )
            }
            put("financialItems", itemsArr)

            val txArr = JSONArray()
            state.allTransactions.forEach { t ->
                txArr.put(
                    JSONObject().apply {
                        put("id", t.id)
                        put("type", t.type)
                        put("amount", t.amount.toBigDecimalDh())
                        put("category", t.category)
                        put("envelopeKey", t.envelopeKey)
                        put("accountId", t.accountId)
                        put("toAccountId", t.toAccountId)
                        put("desc", t.desc)
                        put("iso", t.isoDate)
                        if (t.hourOfDay in 0..23) {
                            put("hourOfDay", t.hourOfDay)
                        }
                    }
                )
            }
            put("transactions", txArr)
        }
        return root.toString(2)
    }

    private const val MAX_BACKUP_ITEMS = 5_000
    private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")
    private val ALLOWED_LANGUAGES = setOf("fr", "ar", "darija", "en")
    private val ALLOWED_ACCOUNT_KINDS = setOf("cash", "bank", "wallet")
    private val ALLOWED_ITEM_TYPES = setOf("goal", "bill", "debt", "daret", "seasonal", "preset", "custom_prefs")
    private val ALLOWED_TX_TYPES = setOf("expense", "income", "transfer")

    private fun requireValidBackupId(id: String, fieldLabel: String): String {
        val trimmed = id.trim()
        require(VALID_ID_REGEX.matches(trimmed)) {
            "Identifiant '$fieldLabel' invalide ('$trimmed'). Caractères autorisés : lettres, chiffres, '-' et '_'."
        }
        return trimmed
    }

    private fun requireFiniteInRange(value: Double, min: Double, max: Double, fieldLabel: String): Double {
        require(!value.isNaN() && !value.isInfinite() && value in min..max) {
            "Valeur numérique '$fieldLabel' hors limites ($value)."
        }
        return value
    }

    private fun readMoneyFromJson(
        obj: JSONObject,
        key: String,
        default: Money,
        min: Money,
        max: Money,
        fieldLabel: String
    ): Money {
        if (!obj.has(key) || obj.isNull(key)) {
            require(default in min..max) {
                "Valeur numérique '$fieldLabel' hors limites (${default.toCanonicalDecimalString()})."
            }
            return default
        }
        val raw = obj.opt(key)
        val parsed = when (raw) {
            is BigDecimal -> runCatching { Money.fromMajorUnits(raw) }.getOrNull()
            is Number -> {
                val asDouble = raw.toDouble()
                if (!asDouble.isFinite()) null
                else runCatching { Money.fromMajorUnits(BigDecimal(raw.toString())) }
                    .getOrElse { runCatching { Money.fromLegacyDoubleDh(asDouble) }.getOrNull() }
            }
            is String -> Money.fromDhString(raw)
            else -> null
        } ?: throw IllegalArgumentException("Valeur numérique '$fieldLabel' invalide ($raw).")

        require(parsed in min..max) {
            "Valeur numérique '$fieldLabel' hors limites (${parsed.toCanonicalDecimalString()})."
        }
        return parsed
    }

    fun decodeBackup(rawJson: String, currentUserId: String, fallbackProfile: UserProfile): ParsedBackupPayload {
        val rawBytes = rawJson.toByteArray(Charsets.UTF_8).size
        require(rawBytes <= AppSecurityManager.MAX_BACKUP_FILE_BYTES) {
            "Sauvegarde trop volumineuse ($rawBytes octets > 10 Mo max)."
        }
        val trimmed = rawJson.trim()
        require(trimmed.isNotEmpty()) { "Le contenu de la sauvegarde est vide." }

        if (trimmed.startsWith("[")) {
            val txArray = JSONArray(trimmed)
            require(txArray.length() <= MAX_BACKUP_ITEMS) {
                "Trop de transactions dans la sauvegarde (${txArray.length()} > $MAX_BACKUP_ITEMS max)."
            }
            return ParsedBackupPayload(
                backupVersion = 1,
                profile = null,
                customPrefs = null,
                accounts = emptyList(),
                envelopes = emptyList(),
                financialItems = emptyList(),
                transactions = parseTransactionsArray(txArray, currentUserId)
            )
        }

        val obj = JSONObject(trimmed)
        val version = obj.optInt("backupVersion", 1)
        when (version) {
            BackupV3Codec.BACKUP_VERSION_V3 -> {
                return BackupV3Codec.decodeV3Backup(obj, currentUserId, fallbackProfile)
            }
            1, 2 -> Unit
            else -> throw UnsupportedBackupVersionException(version)
        }

        val profileObj = obj.optJSONObject("profile")
        val rawBudget = if (profileObj != null && profileObj.has("baseBudget")) {
            readMoneyFromJson(profileObj, "baseBudget", fallbackProfile.baseBudget, Money.ZERO, Money.MAX_STORABLE, "baseBudget")
        } else if (obj.has("baseBudget")) {
            readMoneyFromJson(obj, "baseBudget", fallbackProfile.baseBudget, Money.ZERO, Money.MAX_STORABLE, "baseBudget")
        } else {
            fallbackProfile.baseBudget
        }
        val validBudget = rawBudget.coerceAtLeast(Money.fromDh(100))

        val rawPayDay = if (profileObj != null && profileObj.has("payCycleDay")) {
            profileObj.getInt("payCycleDay")
        } else if (obj.has("payCycleDay")) {
            obj.getInt("payCycleDay")
        } else {
            fallbackProfile.payCycleDay
        }
        require(rawPayDay in 1..28) {
            "Jour de cycle salarial invalide ($rawPayDay). Doit être compris entre 1 et 28."
        }

        val rawLang = if (profileObj != null && profileObj.has("language")) {
            profileObj.getString("language").trim()
        } else {
            fallbackProfile.language
        }
        require(rawLang in ALLOWED_LANGUAGES) {
            "Langue de profil non supportée ('$rawLang')."
        }

        val restoredProfile = UserProfile(
            id = currentUserId,
            userId = currentUserId,
            baseBudget = validBudget,
            payCycleDay = rawPayDay,
            rolloverEnabled = profileObj?.optBoolean("rolloverEnabled", obj.optBoolean("rolloverEnabled", true))
                ?: obj.optBoolean("rolloverEnabled", true),
            language = rawLang,
            isDarkTheme = profileObj?.optBoolean("isDarkTheme", fallbackProfile.isDarkTheme) ?: fallbackProfile.isDarkTheme,
            seeded = true
        )

        val customObj = obj.optJSONObject("customPrefs")
        val restoredCustomPrefs = if (customObj != null) {
            val mask = customObj.optInt("visibilityMask", 127)
            require(mask in 0..366) { "visibilityMask invalide ($mask)." }
            val hasInsightsBit = mask >= 64
            val orderStr = customObj.optString("dashboardOrder", "")
            CustomizationPrefs(
                accentPalette = customObj.optString("accentPalette", "majorelle").ifBlank { "majorelle" }.take(30),
                currencySymbol = customObj.optString("currencySymbol", "DH").ifBlank { "DH" }.take(10),
                customTitle = customObj.optString("customTitle", "").take(80),
                customSubtitle = customObj.optString("customSubtitle", "").take(200),
                showSafeToSpend = (mask and 1) != 0,
                showAccountsCard = (mask and 2) != 0,
                showQuickPresetsCard = (mask and 4) != 0,
                showWeekSummaryRow = (mask and 8) != 0,
                showMonthlyReviewCard = (mask and 16) != 0,
                showBillsCard = (mask and 32) != 0,
                showSmartInsightsCard = if (hasInsightsBit) (mask and 64) != 0 else true,
                dashboardOrder = CustomizationPrefs.normalizeOrderString(orderStr),
                themeMode = CustomizationPrefs.normalizeThemeMode(customObj.optString("themeMode", "system")),
                eurToMadRate = customObj.optDouble("eurToMadRate", 10.85)
                    .let { if (!it.isNaN() && !it.isInfinite() && it in 0.5..500.0) it else 10.85 },
                usdToMadRate = customObj.optDouble("usdToMadRate", 9.95)
                    .let { if (!it.isNaN() && !it.isInfinite() && it in 0.5..500.0) it else 9.95 }
            )
        } else null

        val accArr = obj.optJSONArray("accounts")
        val envArr = obj.optJSONArray("envelopes")
        val itemsArr = obj.optJSONArray("financialItems")
        val txArr = obj.optJSONArray("transactions") ?: JSONArray()

        val totalDeclaredItems = (accArr?.length() ?: 0) +
            (envArr?.length() ?: 0) +
            (itemsArr?.length() ?: 0) +
            txArr.length()
        require(totalDeclaredItems <= MAX_BACKUP_ITEMS) {
            "Sauvegarde dépassant la limite de sécurité ($totalDeclaredItems éléments > $MAX_BACKUP_ITEMS max)."
        }

        val accountsList = mutableListOf<AccountItem>()
        if (accArr != null) {
            for (i in 0 until accArr.length()) {
                val a = accArr.optJSONObject(i) ?: throw IllegalArgumentException("Compte #$i invalide.")
                val id = requireValidBackupId(a.optString("id", "acc_restored_$i"), "account.id")
                val name = a.optString("name", "").trim()
                require(name.length in 1..80) { "Nom du compte '$id' invalide (1..80 caractères requis)." }
                val kind = a.optString("kind", "cash").trim()
                require(kind in ALLOWED_ACCOUNT_KINDS) { "Type de compte '$kind' invalide pour '$id'." }
                val initBal = readMoneyFromJson(
                    a,
                    "initialBalance",
                    Money.ZERO,
                    Money.MIN_STORABLE,
                    Money.MAX_STORABLE,
                    "account.initialBalance"
                )
                val colorHex = a.optString("colorHex", "#10B981").trim().ifBlank { "#10B981" }
                require(colorHex.length in 1..20) { "Couleur invalide pour le compte '$id'." }
                accountsList.add(
                    AccountItem(
                        id = id,
                        userId = currentUserId,
                        name = name,
                        kind = kind,
                        initialBalance = initBal,
                        colorHex = colorHex
                    )
                )
            }
        }

        val envelopesList = mutableListOf<EnvelopeConfig>()
        if (envArr != null) {
            for (i in 0 until envArr.length()) {
                val e = envArr.optJSONObject(i) ?: throw IllegalArgumentException("Enveloppe #$i invalide.")
                val id = requireValidBackupId(e.optString("id", "env_restored_$i"), "envelope.id")
                val key = e.optString("key", "custom_$i").trim()
                require(key.length in 1..60) { "Clé d'enveloppe invalide pour '$id'." }
                val nFr = e.optString("nameFr", "").trim()
                require(nFr.length in 1..100) { "Nom d'enveloppe (FR) invalide pour '$id'." }
                val pct = requireFiniteInRange(e.optDouble("percentage", 0.0), 0.0, 100.0, "envelope.percentage")
                val colorHex = e.optString("colorHex", "#10B981").trim().ifBlank { "#10B981" }
                require(colorHex.length in 1..20) { "Couleur d'enveloppe invalide pour '$id'." }
                envelopesList.add(
                    EnvelopeConfig(
                        id = id,
                        userId = currentUserId,
                        key = key,
                        nameFr = nFr,
                        nameAr = e.optString("nameAr", nFr).trim().ifBlank { nFr }.take(100),
                        nameDarija = e.optString("nameDarija", nFr).trim().ifBlank { nFr }.take(100),
                        nameEn = e.optString("nameEn", nFr).trim().ifBlank { nFr }.take(100),
                        percentage = pct,
                        hasCap = e.optBoolean("hasCap", true),
                        colorHex = colorHex
                    )
                )
            }
        }

        val itemsList = mutableListOf<FinancialItem>()
        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                val itObj = itemsArr.optJSONObject(i) ?: throw IllegalArgumentException("Élément financier #$i invalide.")
                val id = requireValidBackupId(itObj.optString("id", "item_restored_$i"), "financialItem.id")
                val itemType = itObj.optString("itemType", "").trim()
                require(itemType in ALLOWED_ITEM_TYPES) {
                    "Type d'élément financier non autorisé ('$itemType') pour '$id'."
                }
                val name = itObj.optString("name", "").trim()
                require(name.length in 1..120) { "Nom d'élément financier invalide pour '$id'." }
                val subtitle = itObj.optString("subtitle", "")
                require(subtitle.length <= 250) { "Sous-titre trop long pour '$id'." }
                val amount = readMoneyFromJson(
                    itObj,
                    "amount",
                    Money.ZERO,
                    Money.ZERO,
                    Money.MAX_STORABLE,
                    "financialItem.amount"
                )
                val secAmount: Money
                val daretMembersVal: Int?
                if (itemType == "daret") {
                    val rawMembers = if (itObj.has("daretMembers") && !itObj.isNull("daretMembers")) {
                        itObj.optInt("daretMembers", 1)
                    } else {
                        val legacySec = itObj.optDouble("secondaryAmount", 1.0)
                        requireFiniteInRange(legacySec, 0.0, 100_000_000.0, "financialItem.secondaryAmount").roundToInt()
                    }
                    secAmount = Money.ZERO
                    daretMembersVal = rawMembers.coerceAtLeast(1)
                } else {
                    secAmount = readMoneyFromJson(
                        itObj,
                        "secondaryAmount",
                        Money.ZERO,
                        Money.ZERO,
                        Money.MAX_STORABLE,
                        "financialItem.secondaryAmount"
                    )
                    daretMembersVal = null
                }
                val dayOrTurn = itObj.optInt("dayOrTurn", 1)
                require(dayOrTurn in 0..366) { "dayOrTurn hors limites ($dayOrTurn) pour '$id'." }
                val extraInt = itObj.optInt("extraInt", 0)
                require(extraInt in 0..366) { "extraInt hors limites ($extraInt) pour '$id'." }
                val dateOrCycle = itObj.optString("dateOrCycle", "")
                require(dateOrCycle.length <= 250) { "dateOrCycle trop long pour '$id'." }
                itemsList.add(
                    FinancialItem(
                        id = id,
                        userId = currentUserId,
                        itemType = itemType,
                        name = name,
                        subtitle = subtitle,
                        amount = amount,
                        secondaryAmount = secAmount,
                        daretMembers = daretMembersVal,
                        dayOrTurn = dayOrTurn,
                        extraInt = extraInt,
                        dateOrCycle = dateOrCycle
                    )
                )
            }
        }

        val txsList = parseTransactionsArray(txArr, currentUserId)
        val learnedMap = linkedMapOf<String, String>()
        val learnedObj = obj.optJSONObject("learnedCategories")
        if (learnedObj != null) {
            val keys = learnedObj.keys()
            while (keys.hasNext()) {
                val k = keys.next().trim().lowercase()
                val v = learnedObj.optString(k, "").trim().lowercase()
                if (k.length >= 2 && v.isNotBlank()) {
                    learnedMap[k] = v
                }
            }
        }

        return ParsedBackupPayload(
            backupVersion = version,
            exportedAtIso = obj.optString("exportedAtIso", ""),
            profile = restoredProfile,
            customPrefs = restoredCustomPrefs,
            accounts = accountsList,
            envelopes = envelopesList,
            financialItems = itemsList,
            transactions = txsList,
            learnedCategories = learnedMap
        )
    }

    /**
     * Phase 1 pre-flight validation & import preview.
     * Supports both plain JSON backups and AES-256-GCM password-encrypted backups.
     */
    fun previewBackup(
        rawContent: String,
        password: String = "",
        currentUserId: String,
        fallbackProfile: UserProfile
    ): BackupImportPreview {
        val rawBytes = rawContent.toByteArray(Charsets.UTF_8).size
        require(rawBytes <= PortableBackupCrypto.MAX_BACKUP_FILE_BYTES) {
            "Sauvegarde trop volumineuse ($rawBytes octets > 10 Mo max)."
        }
        val envelopeInfo = PortableBackupCrypto.inspectEnvelope(rawContent)
        val (isEnc, encVersion) = when (envelopeInfo) {
            is BackupEnvelopeInfo.PortableV3Encrypted -> true to envelopeInfo.encryptionVersion
            is BackupEnvelopeInfo.LegacyV2Encrypted -> true to envelopeInfo.encryptionVersion
            BackupEnvelopeInfo.LegacyPlaintextJson -> false to 0
            is BackupEnvelopeInfo.UnsupportedVersion ->
                throw UnsupportedBackupVersionException(envelopeInfo.detectedVersion)
            is BackupEnvelopeInfo.Corrupted ->
                throw IllegalArgumentException(envelopeInfo.reason)
        }
        val plainJson = when (
            val dec = PortableBackupCrypto.decryptAnyBackupBlocking(
                rawContent = rawContent,
                passphrase = password.toCharArray(),
                wipePassphrase = true
            )
        ) {
            is PortableCryptoResult.Success -> dec.value
            is PortableCryptoResult.WrongPassphraseOrTampered ->
                throw IllegalArgumentException("Mot de passe incorrect ou sauvegarde corrompue.")
            is PortableCryptoResult.UnsupportedVersion ->
                throw UnsupportedBackupVersionException(dec.version)
            is PortableCryptoResult.FileTooLarge ->
                throw IllegalArgumentException("Sauvegarde trop volumineuse (${dec.actualBytes} octets > 10 Mo max).")
            is PortableCryptoResult.CorruptedFile ->
                throw IllegalArgumentException(dec.detail)
            is PortableCryptoResult.WeakPassphrase ->
                throw IllegalArgumentException("Mot de passe trop court (min ${dec.minLength} caractères).")
        }
        val parsed = decodeBackup(plainJson, currentUserId, fallbackProfile)
        val totalOps = (if (parsed.profile != null) 1 else 0) +
            (if (parsed.customPrefs != null) 1 else 0) +
            parsed.accounts.size +
            parsed.envelopes.size +
            parsed.financialItems.size +
            parsed.transactions.size +
            parsed.savedCredits.size
        val chunks = ceil(totalOps.coerceAtLeast(1) / 400.0).toInt().coerceAtLeast(1)
        return BackupImportPreview(
            isEncrypted = isEnc,
            backupVersion = parsed.backupVersion,
            exportedAtIso = parsed.exportedAtIso,
            accountsCount = parsed.accounts.size,
            envelopesCount = parsed.envelopes.size,
            financialItemsCount = parsed.financialItems.size,
            transactionsCount = parsed.transactions.size,
            totalOperationsCount = totalOps,
            batchChunksCount = chunks,
            isSingleAtomicBatch = chunks == 1,
            savedCreditsCount = parsed.savedCredits.size,
            annualBillsCount = parsed.fiscal?.annualBills?.size ?: 0,
            encryptionVersion = encVersion,
            appVersion = parsed.appVersion
        )
    }

    private fun parseTransactionsArray(txArray: JSONArray, currentUserId: String): List<TransactionItem> {
        require(txArray.length() <= MAX_BACKUP_ITEMS) {
            "Trop de transactions dans la sauvegarde (${txArray.length()} > $MAX_BACKUP_ITEMS max)."
        }
        val list = mutableListOf<TransactionItem>()
        for (i in 0 until txArray.length()) {
            val item = txArray.optJSONObject(i) ?: throw IllegalArgumentException("Transaction #$i invalide.")
            val id = requireValidBackupId(
                item.optString("id", "tx_${System.currentTimeMillis()}_$i"),
                "transaction.id"
            )
            val txType = item.optString("type", "expense").trim()
            require(txType in ALLOWED_TX_TYPES) {
                "Type de transaction non autorisé ('$txType') pour '$id'."
            }
            val amtKey = if (item.has("amount")) "amount" else "montant"
            val amt = readMoneyFromJson(
                item,
                amtKey,
                Money.ZERO,
                Money.ONE_CENTIME,
                Money.MAX_STORABLE,
                "transaction.amount"
            )

            val cat = item.optString("category", "Autre / Imprévus").trim().ifBlank { "Autre / Imprévus" }
            require(cat.length in 1..120) { "Catégorie de transaction invalide pour '$id'." }
            val desc = item.optString("desc", "Transaction")
            require(desc.length <= 250) { "Description trop longue pour la transaction '$id'." }
            val accountId = item.optString("accountId", "acc_cash").trim().ifBlank { "acc_cash" }
            require(accountId.length in 1..80) { "accountId invalide pour la transaction '$id'." }
            val rawToAccountId = item.optString("toAccountId", "").trim()
            val toAccountId = if (txType == "transfer") {
                require(rawToAccountId.isNotBlank() && rawToAccountId != accountId && rawToAccountId.length <= 80) {
                    "Un transfert doit spécifier un compte destinataire distinct du compte source ('$id')."
                }
                rawToAccountId
            } else {
                require(rawToAccountId.isEmpty()) {
                    "Une opération '$txType' ne doit pas définir toAccountId ('$id')."
                }
                ""
            }
            val rawIso = item.optString("iso", item.optString("isoDate", ""))
            val validatedIso = PayCycleEngine.requireOrNormalizeIsoDate(rawIso)
            val envKey = item.optString(
                "envelopeKey",
                PayCycleEngine.resolveEnvelopeKey(cat, desc, emptyList())
            ).trim().ifBlank { "autre" }
            require(envKey.length in 1..60) { "envelopeKey invalide pour '$id'." }

            val rawHour = item.optInt("hourOfDay", -1).let { if (it in 0..23) it else -1 }

            list.add(
                TransactionItem(
                    id = id,
                    userId = currentUserId,
                    type = txType,
                    amount = amt,
                    category = cat,
                    envelopeKey = envKey,
                    accountId = accountId,
                    toAccountId = toAccountId,
                    desc = desc,
                    isoDate = validatedIso,
                    hourOfDay = rawHour
                )
            )
        }
        return list
    }
}

data class WeeklyBreakdownResult(
    val weekSpend: List<Money>,
    val weekFutureSpend: List<Money>,
    val prevWeekSpend: List<Money>,
    val weekLabel: String
)

data class DashboardState(
    val isLoading: Boolean = true,
    val profile: UserProfile = UserProfile(),
    val customPrefs: CustomizationPrefs = CustomizationPrefs(),
    val activeWindow: PayCycleWindow = PayCycleEngine.computeWindow("2026-10", 1),
    val accounts: List<AccountWithBalance> = emptyList(),
    val totalNetWorth: Money = Money.ZERO,
    val envelopes: List<EnvelopeStatus> = emptyList(),
    val cycleTransactions: List<TransactionItem> = emptyList(),
    val filteredHistory: List<TransactionItem> = emptyList(),
    val upcomingTransactions: List<TransactionItem> = emptyList(),
    val allTransactions: List<TransactionItem> = emptyList(),
    val cycleExpenses: Money = Money.ZERO,
    val cycleIncomes: Money = Money.ZERO,
    val totalCycleRollover: Money = Money.ZERO,
    val totalAvailableBudget: Money = Money.fromDh(6400),
    val remainingBudget: Money = Money.fromDh(6400),
    val unpaidBillsTotal: Money = Money.ZERO,
    val safePool: Money = Money.ZERO,
    val daysRemaining: Int = 30,
    val safeToSpendToday: Money = Money.ZERO,
    val dailyAverageSpend: Money = Money.ZERO,
    val projectedCycleSpend: Money = Money.ZERO,
    val spendProjection: CycleSpendProjection = CycleSpendProjection(),
    val previousCycleExpenses: Money = Money.ZERO,
    val weekDaysSpend: List<Money> = List(7) { Money.ZERO },
    val weekDaysFutureSpend: List<Money> = List(7) { Money.ZERO },
    val weekTotalSpend: Money = Money.ZERO,
    val prevWeekTotalSpend: Money = Money.ZERO,
    val weekRangeLabel: String = "",
    val mostExpensiveDayIndex: Int = -1,
    val mostExpensiveDayAmount: Money = Money.ZERO,
    val activeWeekDailyAverage: Money = Money.ZERO,
    val topEnvelopeStatus: EnvelopeStatus? = null,
    val bestControlledEnvelope: EnvelopeStatus? = null,
    val worstControlledEnvelope: EnvelopeStatus? = null,
    val presets: List<FinancialItem> = emptyList(),
    val goals: List<FinancialItem> = emptyList(),
    val bills: List<FinancialItem> = emptyList(),
    val debts: List<FinancialItem> = emptyList(),
    val darets: List<FinancialItem> = emptyList(),
    val seasonals: List<FinancialItem> = emptyList(),
    val smartInsights: List<SmartInsight> = emptyList(),
    val smartReminders: List<SmartReminder> = emptyList(),
    val noSpendStreakDays: Int = 0,
    val monthEndForecastMessage: String = "",
    val biggestSpendingDayIso: String = "",
    val biggestSpendingDayAmount: Money = Money.ZERO,
    val detectedSubscriptions: List<DetectedSubscription> = emptyList(),
    val cycleSavings: Money = Money.ZERO,
    val isOfflineMode: Boolean = false,
    val isSyncing: Boolean = false,
    val isCleanFreshAccount: Boolean = false,
    val hideAmounts: Boolean = false,
    val filterState: FilterState = FilterState(),
    val whatIfState: WhatIfSimulatorState = WhatIfSimulatorState(),
    val errorMessage: String? = null
)

class MasroufiViewModel(
    private val repository: MasroufiDataSource,
    private val currentUserId: String,
    initialHideAmounts: Boolean = false,
    initialRollbackSnapshot: String? = null,
    initialDismissedSubscriptions: Set<String> = emptySet(),
    initialLearnedCategoriesJson: String? = null,
    initialVerifiedActiveDates: Set<String>? = null,
    private val onRollbackSnapshotChanged: (String?) -> Unit = {},
    private val onDismissSubscription: (String) -> Unit = {},
    private val onLearnedCategoriesUpdated: (String) -> Unit = {}
) : ViewModel() {

    private val _selectedCycleKey = MutableStateFlow<String?>(null)
    private val _historyMinIsoDate = MutableStateFlow<String?>(null)
    private val _filterState = MutableStateFlow(FilterState())
    private val _whatIfState = MutableStateFlow(WhatIfSimulatorState())
    private val _hideAmounts = MutableStateFlow(initialHideAmounts)
    private val _dismissedSubscriptions = MutableStateFlow(initialDismissedSubscriptions)
    private val _verifiedActiveDates = MutableStateFlow<Set<String>?>(initialVerifiedActiveDates)
    private val recentLocalTxHours = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val _lastDeletedTx = MutableStateFlow<TransactionItem?>(null)
    val lastDeletedTx: StateFlow<TransactionItem?> = _lastDeletedTx.asStateFlow()
    private val _lastDeletedItem = MutableStateFlow<FinancialItem?>(null)
    val lastDeletedItem: StateFlow<FinancialItem?> = _lastDeletedItem.asStateFlow()
    private val _pendingWritesCount = MutableStateFlow(0)
    private val _learnedCategoriesCreatedAt = MutableStateFlow<com.google.firebase.Timestamp?>(null)
    private var lastSyncedLearnedSignature: String = ""

    fun setVerifiedActiveDates(dates: Set<String>?) {
        _verifiedActiveDates.value = dates
    }

    fun recordActiveOrConfirmedDate(isoDate: String = PayCycleEngine.todayIso()) {
        val current = _verifiedActiveDates.value ?: emptySet()
        _verifiedActiveDates.value = current + isoDate
    }

    // Instant optimistic local overrides (nullable; null means observe Room repository directly)
    private val isLocalOnlyUser = currentUserId == LOCAL_DEVICE_USER_ID
    private val _isBulkImporting = MutableStateFlow(false)
    val isBulkImporting: StateFlow<Boolean> = _isBulkImporting.asStateFlow()
    private val _optimisticProfile = MutableStateFlow<UserProfile?>(null)
    private val _optimisticCustomPrefs = MutableStateFlow<CustomizationPrefs?>(null)
    private val _localTransactions = MutableStateFlow<List<TransactionItem>?>(null)
    private val _localAccounts = MutableStateFlow<List<AccountItem>?>(null)
    private val _localEnvelopes = MutableStateFlow<List<EnvelopeConfig>?>(null)
    private val _localItems = MutableStateFlow<List<FinancialItem>?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _customPrefsItemCreatedAt = MutableStateFlow<com.google.firebase.Timestamp?>(null)

    private fun safeLaunch(tag: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            var markedSlowSync = false
            val slowSyncJob = launch {
                kotlinx.coroutines.delay(3000L)
                markedSlowSync = true
                _pendingWritesCount.value = _pendingWritesCount.value + 1
            }
            try {
                block()
                _errorMessage.value = null
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) {
                    Log.w("MasroufiVM", "Non-fatal error in $tag")
                }
                _errorMessage.value = "Synchronisation hors-ligne ou erreur réseau ($tag). Vos modifications seront resynchronisées."
            } finally {
                slowSyncJob.cancel()
                if (markedSlowSync) {
                    _pendingWritesCount.value = (_pendingWritesCount.value - 1).coerceAtLeast(0)
                }
            }
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun retrySync() {
        _errorMessage.value = null
        safeLaunch("retrySync") {
            repository.ensureSeeded()
        }
    }

    private val profileFlow = repository.observeProfile(currentUserId)
        .catch { e ->
            if (com.example.BuildConfig.DEBUG) Log.w("MasroufiVM", "Error observing profile")
            if (!isLocalOnlyUser) {
                _errorMessage.value = "Connexion cloud momentanément indisponible. Mode cache actif."
            }
            emit(UserProfile(userId = currentUserId))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val transactionsFlow = combine(
        _historyMinIsoDate.flatMapLatest { minIso ->
            repository.observeTransactions(currentUserId, minIsoDate = minIso).catch { e ->
                if (com.example.BuildConfig.DEBUG) Log.w("MasroufiVM", "Error observing transactions")
                if (!isLocalOnlyUser) {
                    _errorMessage.value = "Impossible de charger les transactions en direct. Vérifiez votre connexion."
                }
                emit(emptyList())
            }
        },
        _localTransactions
    ) { remote, local ->
        val raw = local ?: remote
        raw.map { tx ->
            if (tx.hourOfDay in 0..23) {
                tx
            } else {
                val cachedHour = recentLocalTxHours[tx.id]
                if (cachedHour != null && cachedHour in 0..23) tx.copy(hourOfDay = cachedHour) else tx
            }
        }
    }

    fun setTransactionHistoryMinDate(minIsoDate: String?) {
        _historyMinIsoDate.value = minIsoDate
    }

    fun loadFullTransactionHistory() {
        _historyMinIsoDate.value = null
    }

    private val accountsFlow = combine(
        repository.observeAccounts(currentUserId).catch { e ->
            if (com.example.BuildConfig.DEBUG) Log.w("MasroufiVM", "Error observing accounts")
            emit(emptyList())
        },
        _localAccounts
    ) { remote, local -> local ?: remote }

    private val envelopesFlow = combine(
        repository.observeEnvelopes(currentUserId).catch { e ->
            if (com.example.BuildConfig.DEBUG) Log.w("MasroufiVM", "Error observing envelopes")
            emit(emptyList())
        },
        _localEnvelopes
    ) { remote, local -> local ?: remote }

    private val itemsFlow = combine(
        repository.observeFinancialItems(currentUserId).catch { e ->
            if (com.example.BuildConfig.DEBUG) Log.w("MasroufiVM", "Error observing financial items")
            emit(emptyList())
        },
        _localItems
    ) { remote, local -> local ?: remote }

    val uiState: StateFlow<DashboardState> = combine(
        combine(profileFlow, _optimisticProfile, transactionsFlow) { p, optP, t -> Triple(optP ?: p, t, Unit) },
        combine(accountsFlow, envelopesFlow, itemsFlow) { a, e, i -> Triple(a, e, i) },
        combine(
            combine(_selectedCycleKey, _filterState, _whatIfState) { c, f, w -> Triple(c, f, w) },
            combine(_optimisticCustomPrefs, _errorMessage, _hideAmounts) { cp, err, hide -> Triple(cp, err, hide) },
            combine(_pendingWritesCount, _dismissedSubscriptions, _verifiedActiveDates) { pCount, dismissed, verified ->
                Triple(pCount, dismissed, verified)
            }
        ) { (c, f, w), (cp, err, hide), (pendingCount, dismissedSubs, verifiedDates) ->
            listOf(c, f, w, cp, err, hide, pendingCount > 0, dismissedSubs, verifiedDates)
        }
    ) { (profileOpt, txs, _), (accs, envs, finItems), extraList ->
        val selCycle = extraList[0] as? String
        val filter = extraList[1] as FilterState
        val whatIf = extraList[2] as WhatIfSimulatorState
        val optCustomPrefs = extraList[3] as? CustomizationPrefs
        val errMsg = extraList[4] as? String
        val hideAmounts = extraList[5] as? Boolean ?: false
        val isSyncingNow = extraList[6] as? Boolean ?: false
        @Suppress("UNCHECKED_CAST")
        val dismissedSubs = (extraList.getOrNull(7) as? Set<String>) ?: emptySet()
        @Suppress("UNCHECKED_CAST")
        val verifiedDates = extraList.getOrNull(8) as? Set<String>

        val profile = profileOpt ?: UserProfile(userId = currentUserId)
        val storedPrefsItem = finItems.find {
            it.id == "custom_prefs" ||
                (it.itemType == "custom_prefs" && it.id != "learned_categories" && it.name != "learned_categories")
        }
        if (storedPrefsItem?.createdAt != null) {
            _customPrefsItemCreatedAt.value = storedPrefsItem.createdAt
        }
        val learnedCategoriesItem = finItems.find { it.id == "learned_categories" }
        if (learnedCategoriesItem != null) {
            if (learnedCategoriesItem.createdAt != null) {
                _learnedCategoriesCreatedAt.value = learnedCategoriesItem.createdAt
            }
            val sig = "${learnedCategoriesItem.subtitle}|${learnedCategoriesItem.dateOrCycle}"
            if (sig != lastSyncedLearnedSignature && sig != "|") {
                lastSyncedLearnedSignature = sig
                PayCycleEngine.importLearnedCategoriesFromFinancialItem(learnedCategoriesItem)
                onLearnedCategoriesUpdated(PayCycleEngine.exportLearnedCategoriesJson())
            }
        }
        val customPrefs = optCustomPrefs ?: CustomizationPrefs.fromFinancialItem(storedPrefsItem)
        val curSymbol = customPrefs.currencySymbol.ifBlank { "DH" }
        val todayIso = PayCycleEngine.todayIso()

        val defaultCycle = PayCycleEngine.currentCycleKey(profile.payCycleDay, todayIso)
        val activeCycleKey = selCycle ?: defaultCycle
        val window = PayCycleEngine.computeWindow(activeCycleKey, profile.payCycleDay, todayIso)
        val prevCycleKey = PayCycleEngine.shiftCycleKey(activeCycleKey, -1)
        val prevWindow = PayCycleEngine.computeWindow(prevCycleKey, profile.payCycleDay, todayIso)

        val sortedAccounts = accs.sortedBy { it.name }
        val accountBalances = PayCycleEngine.computeAccountBalances(sortedAccounts, txs)
        val netWorth = accountBalances.map { it.currentBalance }.sumMoney()

        val sortedEnvelopes = envs.sortedByDescending { it.hasCap }.sortedByDescending { it.percentage }
        val envelopeStatuses = PayCycleEngine.computeEnvelopes(
            envelopes = sortedEnvelopes,
            baseBudget = profile.baseBudget,
            rolloverEnabled = profile.rolloverEnabled,
            currentWindow = window,
            previousWindow = prevWindow,
            allTransactions = txs,
            maxDateIso = todayIso
        )

        val cycleTxs = txs.filter { PayCycleEngine.isInWindow(it.isoDate, window) }
            .sortedWith(compareByDescending<TransactionItem> { it.isoDate }.thenByDescending { it.id })

        // Phase 0: Exclude future-dated transactions (date > today) from spent-so-far, daily average, and cycle balance
        val pastOrTodayCycleTxs = cycleTxs.filter { it.isoDate <= todayIso }
        val futureCycleExpenses = cycleTxs.filter { it.type == "expense" && it.isoDate > todayIso }.map { it.amount }.sumMoney()

        val cycleExpenses = pastOrTodayCycleTxs.filter { it.type == "expense" }.map { it.amount }.sumMoney()
        val cycleSavings = pastOrTodayCycleTxs.filter {
            it.type == "expense" && (
                it.envelopeKey.equals("epargne", ignoreCase = true) ||
                    DefaultCategoryResolver.canonicalDefaultId(it.category) == "epargne"
                )
        }.map { it.amount }.sumMoney()
        val cycleIncomes = pastOrTodayCycleTxs.filter { it.type == "income" }.map { it.amount }.sumMoney()
        val prevCycleExpenses = txs.filter {
            it.type == "expense" && PayCycleEngine.isInWindow(it.isoDate, prevWindow) && it.isoDate <= todayIso
        }.map { it.amount }.sumMoney()

        val totalRollover = envelopeStatuses.map { it.rolloverAmount }.sumMoney()
        val totalAvailable = profile.baseBudget + cycleIncomes + totalRollover
        val remaining = totalAvailable - cycleExpenses

        val bills = finItems.filter { it.itemType == "bill" }.sortedBy { it.dayOrTurn }
        val goals = finItems.filter { it.itemType == "goal" }
        val debts = finItems.filter { it.itemType == "debt" }
        val darets = finItems.filter { it.itemType == "daret" }
        val seasonals = finItems.filter { it.itemType == "seasonal" }

        val unpaidBills = bills.filter { !PayCycleEngine.isBillPaidInCycle(it, activeCycleKey) }
            .map { it.amount }
            .sumMoney()

        // Count future-dated transactions in Safe-to-spend only as committed bills
        val committedBillsAndFuture = unpaidBills + futureCycleExpenses
        val safePool = (remaining - committedBillsAndFuture).coerceAtLeast(Money.ZERO)
        val safeToday = if (window.daysRemaining > 0) safePool.divideBy(window.daysRemaining.toLong()) else safePool
        val dailyAvg = cycleExpenses.divideBy(max(1, window.daysElapsed).toLong())

        val spendProjection = PayCycleEngine.computeOverspendProjection(
            window = window,
            totalAvailableBudget = totalAvailable,
            spentSoFar = cycleExpenses,
            scheduledBills = committedBillsAndFuture,
            pastOrTodayCycleTransactions = pastOrTodayCycleTxs,
            bills = bills
        )
        val projectedSpend = spendProjection.projectedTotal

        // Timezone/DST-safe weekly calculations (Monday - Sunday of anchor date)
        val anchorIso = if (window.isCurrentCycle) todayIso else minOf(window.endIso, todayIso)
        val weeklyBreakdown = computeWeeklyBreakdown(anchorIso, txs, todayIso)
        val weekSpend = weeklyBreakdown.weekSpend
        val weekFutureSpend = weeklyBreakdown.weekFutureSpend
        val prevWeekSpend = weeklyBreakdown.prevWeekSpend
        val weekLabel = weeklyBreakdown.weekLabel
        val weekTotal = weekSpend.sumMoney()
        val maxDaySpend = weekSpend.maxOrNull() ?: Money.ZERO
        val maxDayIdx = if (maxDaySpend.isPositive) weekSpend.indexOfFirst { it == maxDaySpend } else -1
        val activeDaysCount = weekSpend.count { it.isPositive }.coerceAtLeast(1)

        val cappedEnvs = envelopeStatuses.filter { it.config.hasCap && it.totalAvailable.isPositive }
        val bestEnv = cappedEnvs.minByOrNull { it.usagePercent }
        val worstEnv = cappedEnvs.maxByOrNull { it.usagePercent }
        val topEnv = envelopeStatuses.filter { it.spent.isPositive }.maxByOrNull { it.spent }

        val earliestTxIso = txs.minByOrNull { it.isoDate }?.isoDate
        val historySpanDays = if (earliestTxIso != null) {
            PayCycleEngine.daysBetweenInclusive(earliestTxIso, todayIso)
        } else 0
        val hasAtLeast14DaysData = window.daysElapsed >= 14 || historySpanDays >= 14

        val baseInsights = PayCycleEngine.computeSmartInsights(
            lang = profile.language,
            currency = curSymbol,
            window = window,
            totalAvailableBudget = totalAvailable,
            cycleExpenses = cycleExpenses,
            previousCycleExpenses = if (hasAtLeast14DaysData) prevCycleExpenses else Money.ZERO,
            safeToSpendToday = safeToday,
            dailyAverageSpend = dailyAvg,
            envelopes = envelopeStatuses,
            projectedCycleSpend = projectedSpend
        )
        val anomalyInsights = if (hasAtLeast14DaysData) {
            PayCycleEngine.computeAnomalyNudges(
                allTransactions = txs.filter { it.isoDate <= todayIso },
                envelopes = sortedEnvelopes,
                lang = profile.language,
                nowIso = anchorIso
            )
        } else emptyList()
        // Phase 4: Cap the Assistant at the 2 most useful insights
        val smartInsights = (anomalyInsights + baseInsights).take(2)

        val noSpendStreak = PayCycleEngine.computeNoSpendStreakDays(
            cycleTransactions = pastOrTodayCycleTxs,
            window = window,
            nowIso = anchorIso,
            verifiedActiveDates = verifiedDates
        )
        val (biggestDayIso, biggestDayAmt) = PayCycleEngine.computeBiggestSpendingDayInCycle(pastOrTodayCycleTxs)
        val forecastMsg = if (spendProjection.showEstimateAlert) {
            PayCycleEngine.formatMonthEndForecast(
                totalAvailableBudget = totalAvailable,
                projectedCycleSpend = projectedSpend,
                currency = curSymbol,
                lang = profile.language,
                hideAmounts = hideAmounts
            )
        } else ""
        val detectedSubs = PayCycleEngine.detectRecurringSubscriptions(
            allTransactions = txs.filter { it.isoDate <= todayIso },
            existingBills = bills,
            dismissedMerchants = dismissedSubs
        )

        val smartReminders = PayCycleEngine.computeSmartReminders(
            cycleKey = activeCycleKey,
            currency = curSymbol,
            bills = bills,
            darets = darets,
            debts = emptyList() // Deduplicated: debts appear once in Sellef
        )

        fun matchesFilter(tx: TransactionItem): Boolean {
            val q = filter.searchQuery.trim().lowercase()
            val locCat = tx.localizedCategory(profile.language, sortedEnvelopes).lowercase()
            val matchesQuery = q.isEmpty() ||
                tx.desc.lowercase().contains(q) ||
                tx.category.lowercase().contains(q) ||
                locCat.contains(q) ||
                tx.amount.formatDhRounded(useGrouping = false).contains(q) ||
                tx.amount.toCanonicalDecimalString().contains(q)
            val resolvedKey = tx.envelopeKey.ifBlank {
                PayCycleEngine.resolveEnvelopeKey(tx.category, tx.desc, sortedEnvelopes)
            }
            val matchesCat = filter.selectedCategoryKey == "all" || resolvedKey == filter.selectedCategoryKey
            val matchesAcc = filter.selectedAccountId == "all" ||
                tx.accountId == filter.selectedAccountId ||
                tx.toAccountId == filter.selectedAccountId
            val matchesType = filter.selectedType == "all" || tx.type == filter.selectedType
            val matchesMin = filter.minAmount == null || tx.amount >= filter.minAmount
            return matchesQuery && matchesCat && matchesAcc && matchesType && matchesMin
        }

        val filteredList = pastOrTodayCycleTxs.filter { matchesFilter(it) }
        val upcomingList = txs.filter { it.isoDate > todayIso && matchesFilter(it) }
            .sortedWith(compareBy<TransactionItem> { it.isoDate }.thenByDescending { it.id })

        DashboardState(
            isLoading = false,
            profile = profile,
            customPrefs = customPrefs,
            activeWindow = window,
            accounts = accountBalances,
            totalNetWorth = netWorth,
            envelopes = envelopeStatuses,
            cycleTransactions = cycleTxs,
            filteredHistory = filteredList,
            upcomingTransactions = upcomingList,
            allTransactions = txs,
            cycleExpenses = cycleExpenses,
            cycleIncomes = cycleIncomes,
            totalCycleRollover = totalRollover,
            totalAvailableBudget = totalAvailable,
            remainingBudget = remaining,
            unpaidBillsTotal = unpaidBills,
            safePool = safePool,
            daysRemaining = window.daysRemaining,
            safeToSpendToday = safeToday,
            dailyAverageSpend = dailyAvg,
            projectedCycleSpend = projectedSpend,
            spendProjection = spendProjection,
            previousCycleExpenses = prevCycleExpenses,
            weekDaysSpend = weekSpend,
            weekDaysFutureSpend = weekFutureSpend,
            weekTotalSpend = weekTotal,
            prevWeekTotalSpend = prevWeekSpend.sumMoney(),
            weekRangeLabel = weekLabel,
            mostExpensiveDayIndex = maxDayIdx,
            mostExpensiveDayAmount = maxDaySpend,
            activeWeekDailyAverage = weekTotal.divideBy(activeDaysCount.toLong()),
            topEnvelopeStatus = topEnv,
            bestControlledEnvelope = bestEnv,
            worstControlledEnvelope = worstEnv,
            presets = finItems.filter { it.itemType == "preset" },
            goals = goals,
            bills = bills,
            debts = debts,
            darets = darets,
            seasonals = seasonals,
            smartInsights = smartInsights,
            smartReminders = smartReminders,
            noSpendStreakDays = noSpendStreak,
            monthEndForecastMessage = forecastMsg,
            biggestSpendingDayIso = biggestDayIso,
            biggestSpendingDayAmount = biggestDayAmt,
            detectedSubscriptions = detectedSubs,
            cycleSavings = cycleSavings,
            isOfflineMode = isLocalOnlyUser,
            isSyncing = isSyncingNow,
            isCleanFreshAccount = txs.isEmpty() && netWorth.isZero,
            hideAmounts = hideAmounts,
            filterState = filter,
            whatIfState = whatIf,
            errorMessage = errMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DashboardState(hideAmounts = initialHideAmounts)
    )

    init {
        if (!initialLearnedCategoriesJson.isNullOrBlank()) {
            PayCycleEngine.importLearnedCategoriesJson(initialLearnedCategoriesJson, merge = true)
        }
        safeLaunch("ensureSeeded") {
            repository.ensureSeeded()
        }
    }

    fun clearLearnedCategories() {
        PayCycleEngine.clearLearnedCategories()
        onLearnedCategoriesUpdated("")
        safeLaunch("clearLearnedCategories") {
            repository.deleteFinancialItem("learned_categories")
        }
    }

    private fun persistLearnedCategories() {
        val json = PayCycleEngine.exportLearnedCategoriesJson()
        onLearnedCategoriesUpdated(json)
        val item = PayCycleEngine.buildLearnedCategoriesFinancialItem(
            userId = currentUserId,
            existingCreatedAt = _learnedCategoriesCreatedAt.value
        )
        lastSyncedLearnedSignature = "${item.subtitle}|${item.dateOrCycle}"
        safeLaunch("persistLearnedCategories") {
            repository.upsertFinancialItem(item)
        }
    }

    private fun computeWeeklyBreakdown(
        anchorIso: String,
        txs: List<TransactionItem>,
        todayIso: String = PayCycleEngine.todayIso()
    ): WeeklyBreakdownResult {
        val (mondayEpochDay, rangeLabel) = PayCycleEngine.computeMondayEpochDayAndLabel(anchorIso)
        val curWeek = LongArray(7)
        val futureWeek = LongArray(7)
        val prevWeek = LongArray(7)
        for (tx in txs) {
            if (tx.type != "expense") continue
            val txDay = PayCycleEngine.parseEpochDays(tx.isoDate)
            val diff = (txDay - mondayEpochDay).toInt()
            if (diff in 0..6) {
                if (tx.isoDate > todayIso) {
                    futureWeek[diff] = Math.addExact(futureWeek[diff], tx.amount.centimes)
                } else {
                    curWeek[diff] = Math.addExact(curWeek[diff], tx.amount.centimes)
                }
            } else if (diff in -7..-1 && tx.isoDate <= todayIso) {
                prevWeek[diff + 7] = Math.addExact(prevWeek[diff + 7], tx.amount.centimes)
            }
        }
        return WeeklyBreakdownResult(
            weekSpend = curWeek.map { Money.fromCentimes(it) },
            weekFutureSpend = futureWeek.map { Money.fromCentimes(it) },
            prevWeekSpend = prevWeek.map { Money.fromCentimes(it) },
            weekLabel = rangeLabel
        )
    }

    fun dismissDetectedSubscription(merchant: String) {
        val clean = merchant.trim().lowercase(java.util.Locale.ROOT)
        if (clean.isEmpty()) return
        _dismissedSubscriptions.value = _dismissedSubscriptions.value + clean
        onDismissSubscription(clean)
    }

    fun shiftCycle(delta: Int) {
        val cur = uiState.value.activeWindow.cycleKey
        _selectedCycleKey.value = PayCycleEngine.shiftCycleKey(cur, delta)
    }

    fun updateLanguage(lang: String) {
        val cur = uiState.value.profile
        val updated = cur.copy(language = lang)
        _optimisticProfile.value = updated
        safeLaunch("updateLanguage") {
            repository.saveProfile(updated)
        }
    }

    fun toggleTheme() {
        val cur = uiState.value.profile
        val nextDark = !cur.isDarkTheme
        val updated = cur.copy(isDarkTheme = nextDark)
        _optimisticProfile.value = updated
        val curPrefs = uiState.value.customPrefs
        val updatedPrefs = curPrefs.copy(themeMode = if (nextDark) "dark" else "light")
        saveCustomizationPrefs(updatedPrefs)
        safeLaunch("toggleTheme") {
            repository.saveProfile(updated)
        }
    }

    fun setThemeMode(mode: String, systemInDark: Boolean = false) {
        val normalizedMode = CustomizationPrefs.normalizeThemeMode(mode)
        val curPrefs = uiState.value.customPrefs
        val updatedPrefs = curPrefs.copy(themeMode = normalizedMode)
        saveCustomizationPrefs(updatedPrefs)

        val resolvedDark = updatedPrefs.resolveIsDark(systemInDark)
        val curProfile = uiState.value.profile
        if (curProfile.isDarkTheme != resolvedDark) {
            val updatedProfile = curProfile.copy(isDarkTheme = resolvedDark)
            _optimisticProfile.value = updatedProfile
            safeLaunch("setThemeMode") {
                repository.saveProfile(updatedProfile)
            }
        }
    }

    fun applyDefaultLightRevolutThemeMigration() {
        val curPrefs = uiState.value.customPrefs
        if (curPrefs.accentPalette.isBlank()) {
            saveCustomizationPrefs(curPrefs.copy(accentPalette = "majorelle"))
        }
    }

    fun saveBudgetSettings(baseBudget: Money, payCycleDay: Int, rolloverEnabled: Boolean) {
        val cur = uiState.value.profile
        val updated = cur.copy(
            baseBudget = baseBudget.coerceAtLeast(Money.fromDh(100)),
            payCycleDay = payCycleDay.coerceIn(1, 28),
            rolloverEnabled = rolloverEnabled
        )
        _optimisticProfile.value = updated
        _selectedCycleKey.value = null
        safeLaunch("saveBudgetSettings") {
            repository.saveProfile(updated)
        }
    }

    fun setHideAmounts(hide: Boolean) {
        _hideAmounts.value = hide
    }

    fun toggleHideAmounts(): Boolean {
        val next = !_hideAmounts.value
        _hideAmounts.value = next
        return next
    }

    fun saveCustomizationPrefs(prefs: CustomizationPrefs) {
        _optimisticCustomPrefs.value = prefs
        val item = buildCustomPrefsFinancialItem(prefs, _customPrefsItemCreatedAt.value)
        safeLaunch("saveCustomizationPrefs") {
            repository.upsertFinancialItem(item)
        }
    }

    fun moveDashboardSection(sectionKey: String, direction: Int) {
        val curPrefs = uiState.value.customPrefs
        val order = curPrefs.normalizedOrder().toMutableList()
        val idx = order.indexOf(sectionKey)
        if (idx == -1) return
        val targetIdx = (idx + direction).coerceIn(0, order.lastIndex)
        if (targetIdx == idx) return
        val item = order.removeAt(idx)
        order.add(targetIdx, item)
        saveCustomizationPrefs(curPrefs.copy(dashboardOrder = order))
    }

    fun setDashboardSectionVisibility(sectionKey: String, visible: Boolean) {
        val curPrefs = uiState.value.customPrefs
        saveCustomizationPrefs(curPrefs.withSectionVisibility(sectionKey, visible))
    }

    private fun buildCustomPrefsFinancialItem(
        prefs: CustomizationPrefs,
        existingCreatedAt: com.google.firebase.Timestamp? = null
    ): FinancialItem {
        val orderCsv = prefs.normalizedOrder().joinToString(",")
        val eurStr = String.format(java.util.Locale.US, "%.4f", prefs.normalizedEurRate())
        val usdStr = String.format(java.util.Locale.US, "%.4f", prefs.normalizedUsdRate())
        val encodedSub = "${prefs.accentPalette}|${prefs.currencySymbol.ifBlank { "DH" }}|$orderCsv|${prefs.normalizedThemeMode()}|$eurStr|$usdStr".take(250)
        return FinancialItem(
            id = "custom_prefs",
            userId = currentUserId,
            itemType = "custom_prefs",
            name = prefs.customTitle.ifBlank { "Masroufi" }.take(80),
            subtitle = encodedSub,
            amount = Money.ZERO,
            secondaryAmount = Money.ZERO,
            dayOrTurn = prefs.toVisibilityMask(),
            extraInt = 0,
            dateOrCycle = prefs.customSubtitle.take(200),
            createdAt = existingCreatedAt
        )
    }

    fun loadDemoData(onDone: () -> Unit = {}) {
        val s = uiState.value
        val existingAccs = s.accounts.map { it.account }
        val existingEnvs = s.envelopes.map { it.config }
        val existingItems = s.presets + s.goals + s.bills + s.debts + s.darets + s.seasonals
        val existingTxs = s.allTransactions
        safeLaunch("loadDemoData") {
            repository.loadDemoData(
                existingAccounts = existingAccs,
                existingEnvelopes = existingEnvs,
                existingItems = existingItems,
                existingTransactions = existingTxs
            )
            onDone()
        }
    }

    fun resetToCleanAccount(onDone: () -> Unit = {}) {
        val s = uiState.value
        val txIds = s.allTransactions.map { it.id }
        val itemIds = (s.goals + s.bills + s.debts + s.darets + s.seasonals).map { it.id }
        val accs = s.accounts.map { it.account }
        safeLaunch("resetToCleanAccount") {
            repository.resetToCleanAccount(
                existingTxIds = txIds,
                existingItemIdsToClear = itemIds,
                existingAccounts = accs
            )
            onDone()
        }
    }

    fun saveTransaction(
        existingId: String = "",
        type: String,
        amount: Money,
        category: String,
        envelopeKey: String,
        accountId: String,
        toAccountId: String = "",
        desc: String,
        isoDate: String
    ) {
        if (!amount.isPositive) return
        val validatedDate = try {
            PayCycleEngine.requireOrNormalizeIsoDate(isoDate)
        } catch (e: IllegalArgumentException) {
            _errorMessage.value = e.message
            return
        }
        val existing = uiState.value.allTransactions.find { it.id == existingId }
        val resolvedEnvKey = envelopeKey.ifBlank {
            PayCycleEngine.resolveEnvelopeKey(category, desc, uiState.value.envelopes.map { it.config })
        }
        var learnedNewCategory = false
        if (type == "expense" && desc.isNotBlank() && resolvedEnvKey.isNotBlank()) {
            PayCycleEngine.learnCategoryForDescription(desc, resolvedEnvKey)
            learnedNewCategory = true
        }
        if (learnedNewCategory) {
            persistLearnedCategories()
        }
        val normalizedCat = DefaultCategoryResolver.normalizeStoredCategoryValue(category, resolvedEnvKey)
        val resolvedHour = existing?.hourOfDay?.takeIf { it in 0..23 }
            ?: java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val generatedId = existingId.ifBlank { "tx_${System.currentTimeMillis()}" }
        recentLocalTxHours[generatedId] = resolvedHour
        val newTx = TransactionItem(
            id = generatedId,
            userId = currentUserId,
            type = type,
            amount = amount,
            category = normalizedCat,
            envelopeKey = resolvedEnvKey,
            accountId = accountId.ifBlank { "acc_cash" },
            toAccountId = if (type == "transfer") toAccountId else "",
            desc = desc.ifBlank { category.ifBlank { "Opération" } },
            isoDate = validatedDate,
            hourOfDay = resolvedHour,
            createdAt = existing?.createdAt
        )
        if (_verifiedActiveDates.value != null) {
            recordActiveOrConfirmedDate(validatedDate)
        }
        safeLaunch("saveTransaction") {
            repository.upsertTransaction(newTx.copy(id = existingId))
        }
    }

    fun quickAddPreset(preset: FinancialItem, onAdded: (TransactionItem) -> Unit = {}) {
        val env = uiState.value.envelopes.find { it.config.key == preset.subtitle }?.config
        val envKey = preset.subtitle.ifBlank { "cafe" }
        val rawCat = if (env != null && !env.isDefaultUnrenamed()) env.nameFr else envKey
        val normalizedCat = DefaultCategoryResolver.normalizeStoredCategoryValue(rawCat, envKey)
        val nowHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val txId = "tx_${System.currentTimeMillis()}"
        recentLocalTxHours[txId] = nowHour
        val today = PayCycleEngine.todayIso()
        val tx = TransactionItem(
            id = txId,
            userId = currentUserId,
            type = "expense",
            amount = preset.amount.coerceAtLeast(Money.fromDh(1)),
            category = normalizedCat,
            envelopeKey = envKey,
            accountId = preset.dateOrCycle.ifBlank { "acc_cash" },
            toAccountId = "",
            desc = preset.name,
            isoDate = today,
            hourOfDay = nowHour
        )
        if (_verifiedActiveDates.value != null) {
            recordActiveOrConfirmedDate(today)
        }
        _lastDeletedTx.value = tx
        onAdded(tx)
        safeLaunch("quickAddPreset") {
            repository.upsertTransaction(tx)
        }
    }

    fun duplicateTransaction(tx: TransactionItem, onDuplicated: (TransactionItem) -> Unit = {}) {
        val nowHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val dupId = "tx_dup_${System.currentTimeMillis()}"
        recentLocalTxHours[dupId] = nowHour
        val duplicated = tx.copy(
            id = dupId,
            isoDate = PayCycleEngine.todayIso(),
            hourOfDay = nowHour,
            createdAt = null,
            updatedAt = null
        )
        onDuplicated(duplicated)
        safeLaunch("duplicateTransaction") {
            repository.upsertTransaction(duplicated)
        }
    }

    fun completeQuickOnboarding(
        salary: Money,
        payDay: Int,
        threeEnvelopes: List<Triple<String, Double, String>>,
        onDone: () -> Unit = {}
    ) {
        val curProfile = uiState.value.profile
        val updatedProfile = curProfile.copy(
            baseBudget = salary.coerceAtLeast(Money.fromDh(100)),
            payCycleDay = payDay.coerceIn(1, 28),
            rolloverEnabled = true
        )
        _optimisticProfile.value = updatedProfile
        _selectedCycleKey.value = null

        val existingEnvs = uiState.value.envelopes.map { it.config }
        val paletteHexes = listOf("#10B981", "#38BDF8", "#F59E0B")
        val updatedEnvelopes = threeEnvelopes.mapIndexed { idx, (label, pct, envKey) ->
            val cleanKey = envKey.ifBlank { "env_${idx + 1}" }
            val existing = existingEnvs.find { it.key == cleanKey } ?: existingEnvs.getOrNull(idx)
            val cleanName = label.trim().ifBlank { existing?.nameFr ?: "Enveloppe ${idx + 1}" }
            EnvelopeConfig(
                id = existing?.id?.ifBlank { null } ?: "env_onb_${idx + 1}",
                userId = currentUserId,
                key = existing?.key?.ifBlank { null } ?: cleanKey,
                nameFr = cleanName,
                nameAr = cleanName,
                nameDarija = cleanName,
                nameEn = cleanName,
                percentage = pct.coerceIn(1.0, 100.0),
                hasCap = true,
                colorHex = existing?.colorHex?.ifBlank { null } ?: paletteHexes.getOrElse(idx) { "#10B981" },
                createdAt = existing?.createdAt
            )
        }

        safeLaunch("completeQuickOnboarding") {
            repository.saveProfile(updatedProfile)
            if (updatedEnvelopes.isNotEmpty()) {
                repository.upsertEnvelopesAtomically(updatedEnvelopes)
            }
            onDone()
        }
    }

    fun deleteTransactionWithUndo(tx: TransactionItem) {
        _lastDeletedTx.value = tx
        safeLaunch("deleteTransactionWithUndo") {
            repository.deleteTransaction(tx.id)
        }
    }

    fun undoDeleteTransaction() {
        val tx = _lastDeletedTx.value ?: return
        _lastDeletedTx.value = null
        safeLaunch("undoDeleteTransaction") {
            repository.upsertTransaction(tx.copy(createdAt = null, updatedAt = null))
        }
    }

    /**
     * Saves one or multiple transactions parsed by Moonshine Voice in a single batch,
     * rounding amounts via [PayCycleEngine.roundMoney] and learning description -> category mappings.
     * Returns the created [TransactionItem] list so the 5-second Undo snackbar can revert them.
     */
    fun saveVoiceBatchTransactions(
        items: List<com.example.domain.VoiceParsedTransaction>,
        accountId: String = ""
    ): List<TransactionItem> {
        if (items.isEmpty()) return emptyList()
        val s = uiState.value
        val resolvedAccountId = accountId.ifBlank {
            s.accounts.firstOrNull()?.account?.id ?: "acc_cash"
        }
        val today = PayCycleEngine.todayIso()
        val baseTime = System.currentTimeMillis()
        val envConfigs = s.envelopes.map { it.config }

        var learnedAny = false
        val createdTxs = items.mapIndexedNotNull { idx, parsed ->
            if (!parsed.amount.isPositive) return@mapIndexedNotNull null
            val resolvedEnvKey = parsed.envelopeKey.ifBlank {
                PayCycleEngine.resolveEnvelopeKey(parsed.categoryName, parsed.description, envConfigs)
            }
            if (parsed.txType == "expense" && parsed.description.isNotBlank() && resolvedEnvKey.isNotBlank()) {
                PayCycleEngine.learnCategoryForDescription(parsed.description, resolvedEnvKey)
                learnedAny = true
            }
            val normalizedCat = DefaultCategoryResolver.normalizeStoredCategoryValue(
                parsed.categoryName,
                resolvedEnvKey
            )
            val nowHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val voiceTxId = "tx_voice_${baseTime}_$idx"
            recentLocalTxHours[voiceTxId] = nowHour
            TransactionItem(
                id = voiceTxId,
                userId = currentUserId,
                type = parsed.txType.ifBlank { "expense" },
                amount = parsed.amount,
                category = normalizedCat,
                envelopeKey = resolvedEnvKey,
                accountId = resolvedAccountId,
                toAccountId = "",
                desc = parsed.description.ifBlank { parsed.categoryName },
                isoDate = today,
                hourOfDay = nowHour
            )
        }

        if (createdTxs.isEmpty()) return emptyList()
        if (learnedAny) {
            persistLearnedCategories()
        }

        safeLaunch("saveVoiceBatchTransactions") {
            createdTxs.forEach { tx ->
                repository.upsertTransaction(tx)
            }
        }
        return createdTxs
    }

    /**
     * Bulk bank statement importer (CSV / PDF).
     * Writes directly through [MasroufiDataSource.upsertTransaction] in chunks of 25
     * with deterministic IDs (`tx_imp_<hash>`) and historical statement dates — never calling
     * `recordQuickCaptureTransactions` — while holding [isBulkImporting] true so the widget
     * and notification snapshot is recomputed once at the end.
     */
    fun importStatementTransactions(
        transactions: List<TransactionItem>,
        onChunkProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
        onComplete: (importedCount: Int) -> Unit = {}
    ) {
        val validTxs = transactions
            .filter { it.amount.isPositive && PayCycleEngine.isValidIsoDate(it.isoDate) }
            .map { tx -> tx.copy(userId = currentUserId) }
        if (validTxs.isEmpty()) {
            onComplete(0)
            return
        }

        val total = validTxs.size
        var learnedAny = false
        validTxs.forEach { tx ->
            if (tx.type == "expense" && tx.desc.isNotBlank() && tx.envelopeKey.isNotBlank()) {
                PayCycleEngine.learnCategoryForDescription(tx.desc, tx.envelopeKey)
                learnedAny = true
            }
        }
        if (learnedAny) {
            persistLearnedCategories()
        }

        safeLaunch("importStatementTransactions") {
            _isBulkImporting.value = true
            var completed = 0
            try {
                val chunks = validTxs.chunked(com.masroufi.importer.domain.ImporterConstants.IMPORT_CHUNK_SIZE)
                for (chunk in chunks) {
                    for (tx in chunk) {
                        repository.upsertTransaction(tx, currentUserId)
                        completed++
                    }
                    onChunkProgress(completed, total)
                }
            } finally {
                _isBulkImporting.value = false
            }
            onComplete(completed)
        }
    }

    fun deleteTransactionsBatch(txs: List<TransactionItem>) {
        if (txs.isEmpty()) return
        safeLaunch("deleteTransactionsBatch") {
            txs.forEach { tx ->
                repository.deleteTransaction(tx.id)
            }
        }
    }

    fun saveAccount(id: String = "", name: String, kind: String, initialBalance: Money, colorHex: String) {
        val existing = uiState.value.accounts.find { it.account.id == id }?.account
        safeLaunch("saveAccount") {
            repository.upsertAccount(
                AccountItem(
                    id = id,
                    userId = currentUserId,
                    name = name.ifBlank { "Compte" },
                    kind = if (kind in listOf("cash", "bank", "wallet")) kind else "cash",
                    initialBalance = initialBalance,
                    colorHex = colorHex.ifBlank { "#10B981" },
                    createdAt = existing?.createdAt
                )
            )
        }
    }

    fun deleteAccount(accId: String) {
        if (accId.isBlank()) return
        safeLaunch("deleteAccount") {
            repository.deleteAccount(accId)
        }
    }

    fun saveEnvelopeConfig(config: EnvelopeConfig) {
        val existing = uiState.value.envelopes.find { it.config.id == config.id }?.config
        safeLaunch("saveEnvelopeConfig") {
            repository.upsertEnvelope(
                config.copy(
                    userId = currentUserId,
                    createdAt = config.createdAt ?: existing?.createdAt
                )
            )
        }
    }

    fun deleteEnvelopeConfig(envId: String) {
        if (envId.isBlank()) return
        safeLaunch("deleteEnvelopeConfig") {
            repository.deleteEnvelope(envId)
        }
    }

    fun autoBalanceEnvelopesTo100() {
        val capped = uiState.value.envelopes.map { it.config }.filter { it.hasCap }
        if (capped.isEmpty()) return
        val sum = capped.sumOf { it.percentage }
        safeLaunch("autoBalanceEnvelopesTo100") {
            val updatedList = if (sum <= 0.0) {
                val equalShare = (100.0 / capped.size * 10.0).roundToInt() / 10.0
                capped.map { env -> env.copy(percentage = equalShare) }
            } else {
                var running = 0.0
                capped.mapIndexed { idx, env ->
                    val normalized = if (idx == capped.lastIndex) {
                        max(0.0, ((100.0 - running) * 10.0).roundToInt() / 10.0)
                    } else {
                        val v = ((env.percentage / sum) * 100.0 * 10.0).roundToInt() / 10.0
                        running += v
                        v
                    }
                    env.copy(percentage = normalized)
                }
            }
            repository.upsertEnvelopesAtomically(updatedList)
        }
    }

    fun saveFinancialItem(item: FinancialItem) {
        val s = uiState.value
        val existing = (s.presets + s.goals + s.bills + s.debts + s.darets + s.seasonals).find { it.id == item.id }
        val normalized = if (item.itemType == "daret") {
            item.copy(
                userId = currentUserId,
                secondaryAmount = Money.ZERO,
                daretMembers = item.resolvedDaretMembers(),
                createdAt = item.createdAt ?: existing?.createdAt
            )
        } else {
            item.copy(
                userId = currentUserId,
                createdAt = item.createdAt ?: existing?.createdAt
            )
        }
        safeLaunch("saveFinancialItem") {
            repository.upsertFinancialItem(normalized)
        }
    }

    fun deleteFinancialItem(id: String) {
        if (id.isBlank()) return
        val s = uiState.value
        val existing = (s.presets + s.goals + s.bills + s.debts + s.darets + s.seasonals).find { it.id == id }
        if (existing != null) {
            _lastDeletedItem.value = existing
        }
        safeLaunch("deleteFinancialItem") {
            repository.deleteFinancialItem(id)
        }
    }

    fun undoDeleteFinancialItem() {
        val item = _lastDeletedItem.value ?: return
        _lastDeletedItem.value = null
        safeLaunch("undoDeleteFinancialItem") {
            repository.upsertFinancialItem(item.copy(createdAt = null, updatedAt = null))
        }
    }

    fun convertSubscriptionToBill(sub: DetectedSubscription, onConverted: (FinancialItem) -> Unit = {}) {
        val bill = FinancialItem(
            id = "bill_sub_${System.currentTimeMillis()}",
            userId = currentUserId,
            itemType = "bill",
            name = sub.name.take(100),
            subtitle = "Abonnement détecté",
            amount = sub.amount.coerceAtLeast(Money.fromDh(1)),
            secondaryAmount = Money.ZERO,
            dayOrTurn = sub.typicalDayOfMonth.coerceIn(1, 28),
            extraInt = 0,
            dateOrCycle = ""
        )
        onConverted(bill)
        safeLaunch("convertSubscriptionToBill") {
            repository.upsertFinancialItem(bill)
        }
    }

    fun splitBillIntoSellef(
        billTitle: String,
        totalAmount: Money,
        friendsNamesCsv: String,
        includeSelf: Boolean = true,
        recordFullExpenseNow: Boolean = true,
        accountId: String = "acc_cash",
        envelopeKey: String = "cafe",
        onDone: (Int, Money) -> Unit = { _, _ -> }
    ) {
        if (!totalAmount.isPositive) return
        val friends = friendsNamesCsv
            .split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (friends.isEmpty()) return
        val divisor = if (includeSelf) friends.size + 1 else friends.size
        val sharePerPerson = totalAmount.divideBy(divisor.toLong())
        if (!sharePerPerson.isPositive) return

        val cleanTitle = billTitle.trim().ifBlank { "Sortie / Restaurant" }
        val nowMs = System.currentTimeMillis()
        safeLaunch("splitBillIntoSellef") {
            if (recordFullExpenseNow) {
                val env = uiState.value.envelopes.find { it.config.key == envelopeKey }?.config
                val rawCat = if (env != null && !env.isDefaultUnrenamed()) env.nameFr else envelopeKey
                val normalizedCat = DefaultCategoryResolver.normalizeStoredCategoryValue(rawCat, envelopeKey)
                repository.upsertTransaction(
                    TransactionItem(
                        id = "tx_split_$nowMs",
                        userId = currentUserId,
                        type = "expense",
                        amount = totalAmount,
                        category = normalizedCat,
                        envelopeKey = envelopeKey,
                        accountId = accountId.ifBlank { "acc_cash" },
                        desc = "$cleanTitle (Partagé ×$divisor)",
                        isoDate = PayCycleEngine.todayIso()
                    )
                )
            }
            friends.forEachIndexed { idx, friendName ->
                val debtItem = FinancialItem(
                    id = "debt_split_${nowMs}_$idx",
                    userId = currentUserId,
                    itemType = "debt",
                    name = friendName.take(80),
                    subtitle = cleanTitle.take(120),
                    amount = sharePerPerson,
                    secondaryAmount = Money.ZERO,
                    dayOrTurn = 1,
                    extraInt = 0,
                    dateOrCycle = "lent"
                )
                repository.upsertFinancialItem(debtItem)
            }
            onDone(friends.size, sharePerPerson)
        }
    }

    fun payRecurringBill(bill: FinancialItem) {
        val cycleKey = uiState.value.activeWindow.cycleKey
        if (PayCycleEngine.isBillPaidInCycle(bill, cycleKey)) return
        val updatedCycles = (bill.dateOrCycle.split(",").filter { it.isNotBlank() } + cycleKey).distinct().joinToString(",")
        val updatedBill = bill.copy(dateOrCycle = updatedCycles, userId = currentUserId)
        val txId = "tx_bill_${bill.id}_$cycleKey"
        val existingTx = uiState.value.allTransactions.find { it.id == txId }
        val tx = TransactionItem(
            id = txId,
            userId = currentUserId,
            type = "expense",
            amount = bill.amount.coerceAtLeast(Money.fromDh(1)),
            category = "charges",
            envelopeKey = "charges",
            accountId = "acc_bank",
            desc = bill.name,
            isoDate = PayCycleEngine.todayIso(),
            createdAt = existingTx?.createdAt
        )
        safeLaunch("payRecurringBill") {
            repository.upsertItemAndTransactionAtomically(updatedBill, tx)
        }
    }

    fun contributeToGoalOrSeasonal(item: FinancialItem, addAmount: Money) {
        if (!addAmount.isPositive) return
        val updatedItem = item.copy(
            secondaryAmount = item.secondaryAmount + addAmount,
            userId = currentUserId
        )
        val tx = TransactionItem(
            id = "tx_contrib_${item.id}_${System.currentTimeMillis()}",
            userId = currentUserId,
            type = "expense",
            amount = addAmount,
            category = "epargne",
            envelopeKey = "epargne",
            accountId = "acc_bank",
            desc = item.name,
            isoDate = PayCycleEngine.todayIso()
        )
        safeLaunch("contributeToGoalOrSeasonal") {
            repository.upsertItemAndTransactionAtomically(updatedItem, tx)
        }
    }

    fun payDaretRound(daret: FinancialItem) {
        val totalMembers = daret.resolvedDaretMembers()
        val nextPaid = (daret.extraInt + 1).coerceAtMost(totalMembers)
        val updatedDaret = daret.copy(
            extraInt = nextPaid,
            daretMembers = totalMembers,
            secondaryAmount = Money.ZERO,
            userId = currentUserId
        )
        val txId = "tx_daret_${daret.id}_round_$nextPaid"
        val existingTx = uiState.value.allTransactions.find { it.id == txId }
        val tx = TransactionItem(
            id = txId,
            userId = currentUserId,
            type = "expense",
            amount = daret.amount.coerceAtLeast(Money.fromDh(1)),
            category = "epargne",
            envelopeKey = "epargne",
            accountId = "acc_bank",
            desc = "Cotisation ${daret.name} ($nextPaid/$totalMembers)",
            isoDate = PayCycleEngine.todayIso(),
            createdAt = existingTx?.createdAt
        )
        safeLaunch("payDaretRound") {
            repository.upsertItemAndTransactionAtomically(updatedDaret, tx)
        }
    }

    fun collectDaretPot(daret: FinancialItem, targetAccountId: String = "acc_bank") {
        if (PayCycleEngine.isDaretPotCollected(daret)) return
        val totalMembers = daret.resolvedDaretMembers()
        val totalPot = daret.amount.coerceAtLeast(Money.fromDh(1)) * totalMembers.toLong()
        val updatedDaret = PayCycleEngine.markDaretPotCollected(daret).copy(
            daretMembers = totalMembers,
            secondaryAmount = Money.ZERO,
            userId = currentUserId
        )
        val validAccId = targetAccountId.takeIf {
            it == "acc_bank" || it == "acc_cash" || uiState.value.accounts.any { acc -> acc.account.id == it }
        } ?: "acc_bank"
        val txId = "tx_daret_pot_${daret.id}_turn_${daret.dayOrTurn}"
        val existingTx = uiState.value.allTransactions.find { it.id == txId }
        val tx = TransactionItem(
            id = txId,
            userId = currentUserId,
            type = "income",
            amount = totalPot,
            category = "epargne",
            envelopeKey = "epargne",
            accountId = validAccId,
            toAccountId = "",
            desc = "Pot Daret ${daret.name} (Tour ${daret.dayOrTurn}/$totalMembers)".take(250),
            isoDate = PayCycleEngine.todayIso(),
            createdAt = existingTx?.createdAt
        )
        safeLaunch("collectDaretPot") {
            repository.upsertItemAndTransactionAtomically(updatedDaret, tx)
        }
    }

    fun settleDebt(debt: FinancialItem, payment: Money) {
        if (!payment.isPositive) return
        val newPaid = (debt.secondaryAmount + payment).coerceAtMost(debt.amount)
        val isLent = debt.dateOrCycle != "borrowed"
        val updatedDebt = debt.copy(secondaryAmount = newPaid)
        val tx = TransactionItem(
            id = "tx_debt_${debt.id}_${System.currentTimeMillis()}",
            userId = currentUserId,
            type = if (isLent) "income" else "expense",
            amount = payment,
            category = "autre",
            envelopeKey = "autre",
            accountId = "acc_cash",
            desc = "Remboursement: ${debt.name}",
            isoDate = PayCycleEngine.todayIso()
        )
        safeLaunch("settleDebt") {
            repository.upsertItemAndTransactionAtomically(updatedDebt, tx)
        }
    }

    fun updateFilter(update: (FilterState) -> FilterState) {
        _filterState.value = update(_filterState.value)
    }

    fun updateWhatIf(envelopeKey: String, cutPct: Int) {
        _whatIfState.value = WhatIfSimulatorState(envelopeKey, cutPct.coerceIn(5, 80))
    }

    private val _preRestoreRollbackJson = MutableStateFlow<String?>(initialRollbackSnapshot)
    val preRestoreRollbackJson: StateFlow<String?> = _preRestoreRollbackJson.asStateFlow()

    /**
     * Phase 3: Portable Backup v3 (all Room v2 user tables incl. saved_credits + FiscalRepository,
     * exact Long centimes, PBKDF2-HMAC-SHA256 600k iterations + AES-256-GCM with AAD, zero Keystore).
     * Runs KDF & encryption off the main thread on [Dispatchers.Default].
     */
    suspend fun exportBackupV3(
        passphrase: CharArray,
        fiscal: BackupFiscalSnapshot? = null,
        appVersion: String = com.example.BuildConfig.VERSION_NAME,
        wipePassphrase: Boolean = true
    ): PortableCryptoResult<String> {
        val currentState = uiState.value
        val savedCredits = withContext(Dispatchers.IO) {
            runCatching { repository.getSavedCredits(currentUserId) }.getOrDefault(emptyList())
        }
        val plainV3 = BackupSerializer.encodeV3Backup(
            state = currentState,
            savedCredits = savedCredits,
            fiscal = fiscal,
            appVersion = appVersion
        )
        return PortableBackupCrypto.encrypt(
            plaintextUtf8 = plainV3,
            passphrase = passphrase,
            iterations = PortableBackupCrypto.DEFAULT_PBKDF2_ITERATIONS,
            wipePassphrase = wipePassphrase
        )
    }

    /**
     * Synchronous Backup v3 encrypted JSON export (enforces >= 10 char passphrase).
     */
    fun exportBackupJson(password: String): String {
        val cleanPwd = password.trim()
        require(cleanPwd.length >= PortableBackupCrypto.MIN_PASSPHRASE_LENGTH) {
            "Un mot de passe d'au moins ${PortableBackupCrypto.MIN_PASSPHRASE_LENGTH} caractères est obligatoire pour exporter une sauvegarde JSON chiffrée."
        }
        val plain = BackupSerializer.encodeV3Backup(uiState.value)
        return when (val res = PortableBackupCrypto.encryptBlocking(plain, cleanPwd)) {
            is PortableCryptoResult.Success -> res.value
            is PortableCryptoResult.WeakPassphrase ->
                throw IllegalArgumentException("Un mot de passe d'au moins ${res.minLength} caractères est obligatoire.")
            is PortableCryptoResult.FileTooLarge ->
                throw IllegalArgumentException("Sauvegarde trop volumineuse (${res.actualBytes} octets).")
            else -> throw IllegalArgumentException("Échec du chiffrement de la sauvegarde.")
        }
    }

    /**
     * Exports all transactions to CSV with formula-injection protection (`=`, `+`, `-`, `@` neutralized).
     */
    fun exportCsv(): String {
        return BackupSerializer.encodeCsv(uiState.value.allTransactions)
    }

    fun previewBackupImport(rawContent: String, password: String = ""): Result<BackupImportPreview> {
        return runCatching {
            BackupSerializer.previewBackup(
                rawContent = rawContent,
                password = password,
                currentUserId = currentUserId,
                fallbackProfile = uiState.value.profile
            )
        }
    }

    suspend fun previewBackupImportAsync(
        rawContent: String,
        passphrase: CharArray,
        wipePassphrase: Boolean = true
    ): Result<BackupImportPreview> = withContext(Dispatchers.Default) {
        val passCopy = passphrase.copyOf()
        if (wipePassphrase) java.util.Arrays.fill(passphrase, '\u0000')
        try {
            runCatching {
                val rawBytes = rawContent.toByteArray(Charsets.UTF_8).size
                require(rawBytes <= PortableBackupCrypto.MAX_BACKUP_FILE_BYTES) {
                    "Sauvegarde trop volumineuse ($rawBytes octets > 10 Mo max)."
                }
                val info = PortableBackupCrypto.inspectEnvelope(rawContent)
                val (isEnc, encVersion) = when (info) {
                    is BackupEnvelopeInfo.PortableV3Encrypted -> true to info.encryptionVersion
                    is BackupEnvelopeInfo.LegacyV2Encrypted -> true to info.encryptionVersion
                    BackupEnvelopeInfo.LegacyPlaintextJson -> false to 0
                    is BackupEnvelopeInfo.UnsupportedVersion ->
                        throw UnsupportedBackupVersionException(info.detectedVersion)
                    is BackupEnvelopeInfo.Corrupted ->
                        throw IllegalArgumentException(info.reason)
                }
                val plainJson = when (
                    val dec = PortableBackupCrypto.decryptAnyBackupBlocking(rawContent, passCopy, wipePassphrase = true)
                ) {
                    is PortableCryptoResult.Success -> dec.value
                    is PortableCryptoResult.WrongPassphraseOrTampered ->
                        throw IllegalArgumentException("Mot de passe incorrect ou sauvegarde corrompue.")
                    is PortableCryptoResult.UnsupportedVersion ->
                        throw UnsupportedBackupVersionException(dec.version)
                    is PortableCryptoResult.FileTooLarge ->
                        throw IllegalArgumentException("Sauvegarde trop volumineuse (${dec.actualBytes} octets > 10 Mo max).")
                    is PortableCryptoResult.CorruptedFile ->
                        throw IllegalArgumentException(dec.detail)
                    is PortableCryptoResult.WeakPassphrase ->
                        throw IllegalArgumentException("Mot de passe trop court.")
                }
                val parsed = BackupSerializer.decodeBackup(plainJson, currentUserId, uiState.value.profile)
                val totalOps = (if (parsed.profile != null) 1 else 0) +
                    (if (parsed.customPrefs != null) 1 else 0) +
                    parsed.accounts.size +
                    parsed.envelopes.size +
                    parsed.financialItems.size +
                    parsed.transactions.size +
                    parsed.savedCredits.size
                val chunks = ceil(totalOps.coerceAtLeast(1) / 400.0).toInt().coerceAtLeast(1)
                BackupImportPreview(
                    isEncrypted = isEnc,
                    backupVersion = parsed.backupVersion,
                    exportedAtIso = parsed.exportedAtIso,
                    accountsCount = parsed.accounts.size,
                    envelopesCount = parsed.envelopes.size,
                    financialItemsCount = parsed.financialItems.size,
                    transactionsCount = parsed.transactions.size,
                    totalOperationsCount = totalOps,
                    batchChunksCount = chunks,
                    isSingleAtomicBatch = chunks == 1,
                    savedCreditsCount = parsed.savedCredits.size,
                    annualBillsCount = parsed.fiscal?.annualBills?.size ?: 0,
                    encryptionVersion = encVersion,
                    appVersion = parsed.appVersion
                )
            }
        } finally {
            java.util.Arrays.fill(passCopy, '\u0000')
        }
    }

    fun restoreFromJson(rawJson: String, onResult: (Boolean) -> Unit) {
        restoreFromJson(
            rawJson = rawJson,
            password = "",
            replaceExisting = true,
            onResult = { ok, _ -> onResult(ok) }
        )
    }

    /**
     * Phase 3 atomic, memory-first backup restore supporting Backup v3 (portable encrypted)
     * and legacy v1/v2 (encrypted or plaintext) backups.
     *
     * Strict guarantees:
     * 1. Decryption, JSON parsing, ID/range checks, and metadata count checks run entirely in memory
     *    on [Dispatchers.Default] BEFORE touching Room, optimistic UI state, or FiscalRepository.
     * 2. Room writes (profile, customPrefs, accounts, envelopes, financialItems, transactions,
     *    saved_credits, and stale deletions) commit inside a single atomic Room `@Transaction`
     *    scoped to [currentUserId].
     * 3. Optimistic UI state, learned categories, rollback snapshot persistence, fiscal restoration,
     *    and RemoteMirror.onBackupRestored happen strictly AFTER the Room transaction commits.
     */
    suspend fun restoreBackupPortable(
        rawJson: String,
        passphrase: CharArray,
        replaceExisting: Boolean = true,
        currentFiscal: BackupFiscalSnapshot? = null,
        isRollbackReplay: Boolean = false,
        wipePassphrase: Boolean = true
    ): BackupRestoreResult {
        val currentState = uiState.value

        // Phase 1: Decrypt & validate entire payload strictly in memory on Dispatchers.Default
        val decResult = PortableBackupCrypto.decryptAnyBackup(
            rawContent = rawJson,
            passphrase = passphrase,
            wipePassphrase = wipePassphrase
        )
        val plainJson = when (decResult) {
            is PortableCryptoResult.Success -> decResult.value
            is PortableCryptoResult.WrongPassphraseOrTampered -> {
                return BackupRestoreResult.WrongPassphraseOrTampered(
                    "Mot de passe incorrect ou fichier altéré. Aucune donnée modifiée."
                )
            }
            is PortableCryptoResult.UnsupportedVersion -> {
                return BackupRestoreResult.UnsupportedVersion(
                    version = decResult.version,
                    message = "Version de chiffrement non prise en charge (v${decResult.version})."
                )
            }
            is PortableCryptoResult.FileTooLarge -> {
                return BackupRestoreResult.CorruptedOrInvalid(
                    "Sauvegarde trop volumineuse (${decResult.actualBytes} octets > 10 Mo max)."
                )
            }
            is PortableCryptoResult.CorruptedFile -> {
                return BackupRestoreResult.CorruptedOrInvalid(
                    "Fichier de sauvegarde corrompu : ${decResult.detail}"
                )
            }
            is PortableCryptoResult.WeakPassphrase -> {
                return BackupRestoreResult.WrongPassphraseOrTampered(
                    "Mot de passe trop court (min ${decResult.minLength} caractères)."
                )
            }
        }

        val parsed = try {
            withContext(Dispatchers.Default) {
                BackupSerializer.decodeBackup(
                    rawJson = plainJson,
                    currentUserId = currentUserId,
                    fallbackProfile = currentState.profile
                )
            }
        } catch (e: UnsupportedBackupVersionException) {
            return BackupRestoreResult.UnsupportedVersion(
                version = e.detectedVersion,
                message = e.localizedMessage ?: "Version de sauvegarde non prise en charge."
            )
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) {
                Log.w("MasroufiVM", "Phase 1 backup validation failed: ${e.javaClass.simpleName}")
            }
            return BackupRestoreResult.CorruptedOrInvalid(
                e.localizedMessage ?: "Format de sauvegarde invalide ou corrompu."
            )
        }

        // Phase 2: Prepare complete snapshot & commit inside a single atomic Room @Transaction
        val existingCredits = withContext(Dispatchers.IO) {
            runCatching { repository.getSavedCredits(currentUserId) }.getOrDefault(emptyList())
        }
        val rollbackSnapshot = if (!isRollbackReplay) {
            BackupSerializer.encodeV3Backup(
                state = currentState,
                savedCredits = existingCredits,
                fiscal = currentFiscal
            )
        } else null

        val existingAccMap = currentState.accounts.associate { it.account.id to it.account.createdAt }
        val existingEnvMap = currentState.envelopes.associate { it.config.id to it.config.createdAt }
        val allExistingItems = currentState.presets + currentState.goals + currentState.bills +
            currentState.debts + currentState.darets + currentState.seasonals
        val existingItemMap = allExistingItems.associate { it.id to it.createdAt }
        val existingTxMap = currentState.allTransactions.associate { it.id to it.createdAt }

        val profileWithCreatedAt = parsed.profile?.copy(createdAt = currentState.profile.createdAt)
        val customPrefsItem = parsed.customPrefs?.let {
            buildCustomPrefsFinancialItem(it, _customPrefsItemCreatedAt.value)
        }

        // Build learned categories item without mutating global PayCycleEngine yet
        val learnedRestoreItems = if (parsed.learnedCategories.isNotEmpty()) {
            val merged = LinkedHashMap(PayCycleEngine.getAllLearnedCategories()).apply {
                putAll(parsed.learnedCategories)
            }
            val mergedJson = JSONObject().apply {
                merged.forEach { (k, v) -> put(k, v) }
            }.toString()
            listOf(
                FinancialItem(
                    id = "learned_categories",
                    userId = currentUserId,
                    itemType = "custom_prefs",
                    name = "Catégories apprises",
                    subtitle = "",
                    amount = Money.ZERO,
                    secondaryAmount = Money.ZERO,
                    dayOrTurn = 1,
                    extraInt = 0,
                    dateOrCycle = mergedJson.take(250),
                    createdAt = _learnedCategoriesCreatedAt.value
                )
            )
        } else emptyList()

        val accountsWithCreatedAt = parsed.accounts.map { acc ->
            acc.copy(createdAt = existingAccMap[acc.id])
        }
        val envelopesWithCreatedAt = parsed.envelopes.map { env ->
            env.copy(createdAt = existingEnvMap[env.id])
        }
        val itemsWithCreatedAt = parsed.financialItems
            .filterNot { it.id == "learned_categories" }
            .map { item -> item.copy(createdAt = existingItemMap[item.id]) } + learnedRestoreItems
        val txsWithCreatedAt = parsed.transactions.map { tx ->
            tx.copy(createdAt = existingTxMap[tx.id])
        }

        val staleTxIds = if (replaceExisting) currentState.allTransactions.map { it.id } else emptyList()
        val staleItemIds = if (replaceExisting && parsed.backupVersion >= 2) {
            allExistingItems.map { it.id }
        } else emptyList()
        val staleAccIds = if (replaceExisting && parsed.accounts.isNotEmpty()) {
            currentState.accounts.map { it.account.id }
        } else emptyList()
        val staleEnvIds = if (replaceExisting && parsed.envelopes.isNotEmpty()) {
            currentState.envelopes.map { it.config.id }
        } else emptyList()
        val staleCreditIds = if (replaceExisting && parsed.hasSavedCreditsSection) {
            existingCredits.map { it.id }
        } else emptyList()

        return try {
            var finalChunks = 1
            withContext(Dispatchers.IO) {
                repository.restoreFullBackupAtomically(
                    profile = profileWithCreatedAt,
                    customPrefsItem = customPrefsItem,
                    accounts = accountsWithCreatedAt,
                    envelopes = envelopesWithCreatedAt,
                    financialItems = itemsWithCreatedAt,
                    transactions = txsWithCreatedAt,
                    staleTxIdsToDelete = staleTxIds,
                    staleItemIdsToDelete = staleItemIds,
                    staleAccountIdsToDelete = staleAccIds,
                    staleEnvelopeIdsToDelete = staleEnvIds,
                    onChunkProgress = { _, total -> finalChunks = total },
                    userId = currentUserId,
                    savedCredits = parsed.savedCredits,
                    staleSavedCreditIdsToDelete = staleCreditIds
                )
            }

            // Strictly post-commit mutations (only executed once Room @Transaction succeeded)
            if (rollbackSnapshot != null) {
                _preRestoreRollbackJson.value = rollbackSnapshot
                onRollbackSnapshotChanged(rollbackSnapshot)
            }
            if (profileWithCreatedAt != null) {
                _optimisticProfile.value = profileWithCreatedAt
            }
            if (parsed.customPrefs != null) {
                _optimisticCustomPrefs.value = parsed.customPrefs
            }
            if (parsed.learnedCategories.isNotEmpty()) {
                parsed.learnedCategories.forEach { (k, v) ->
                    PayCycleEngine.learnCategoryForDescription(k, v)
                }
                onLearnedCategoriesUpdated(PayCycleEngine.exportLearnedCategoriesJson())
            }
            _errorMessage.value = null

            val creditsNote = if (parsed.savedCredits.isNotEmpty()) {
                ", ${parsed.savedCredits.size} crédit(s)"
            } else ""
            BackupRestoreResult.Success(
                message = "Restauration v${parsed.backupVersion} réussie (${parsed.transactions.size} op.$creditsNote, $finalChunks lot atomique) !",
                backupVersion = parsed.backupVersion,
                restoredTransactionsCount = parsed.transactions.size,
                restoredCreditsCount = parsed.savedCredits.size,
                restoredFiscal = parsed.fiscal
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) {
                Log.e("MasroufiVM", "Phase 2 atomic restore failed; Room transaction rolled back", e)
            }
            val msg = e.localizedMessage ?: "Erreur lors de la restauration atomique. Vos données sont intactes."
            _errorMessage.value = "Erreur lors de la restauration : $msg"
            BackupRestoreResult.CommitFailed(msg)
        }
    }

    /**
     * Legacy callback wrapper around [restoreBackupPortable].
     */
    fun restoreFromJson(
        rawJson: String,
        password: String = "",
        replaceExisting: Boolean = true,
        isRollbackReplay: Boolean = false,
        currentFiscal: BackupFiscalSnapshot? = null,
        onFiscalRestored: (BackupFiscalSnapshot) -> Unit = {},
        onResult: (Boolean, String) -> Unit
    ) {
        safeLaunch("restoreFromJson") {
            val outcome = restoreBackupPortable(
                rawJson = rawJson,
                passphrase = password.toCharArray(),
                replaceExisting = replaceExisting,
                currentFiscal = currentFiscal,
                isRollbackReplay = isRollbackReplay,
                wipePassphrase = true
            )
            when (outcome) {
                is BackupRestoreResult.Success -> {
                    outcome.restoredFiscal?.let(onFiscalRestored)
                    onResult(true, outcome.message)
                }
                is BackupRestoreResult.WrongPassphraseOrTampered -> {
                    _errorMessage.value = outcome.message
                    onResult(false, outcome.message)
                }
                is BackupRestoreResult.UnsupportedVersion -> {
                    _errorMessage.value = outcome.message
                    onResult(false, outcome.message)
                }
                is BackupRestoreResult.CorruptedOrInvalid -> {
                    _errorMessage.value = outcome.message
                    onResult(false, outcome.message)
                }
                is BackupRestoreResult.CommitFailed -> {
                    _errorMessage.value = outcome.message
                    onResult(false, outcome.message)
                }
            }
        }
    }

    fun rollbackLastRestore(
        onFiscalRestored: (BackupFiscalSnapshot) -> Unit = {},
        onResult: (Boolean) -> Unit
    ) {
        val snapshot = _preRestoreRollbackJson.value
        if (snapshot.isNullOrBlank()) {
            onResult(false)
            return
        }
        restoreFromJson(
            rawJson = snapshot,
            password = "",
            replaceExisting = true,
            isRollbackReplay = true,
            onFiscalRestored = onFiscalRestored,
            onResult = { ok, _ ->
                if (ok) {
                    _preRestoreRollbackJson.value = null
                    onRollbackSnapshotChanged(null)
                }
                onResult(ok)
            }
        )
    }
}
