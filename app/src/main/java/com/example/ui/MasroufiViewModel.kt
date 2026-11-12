package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountItem
import com.example.data.CustomizationPrefs
import com.example.data.DefaultCategoryResolver
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.MasroufiRepository
import com.example.data.TransactionItem
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
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class FilterState(
    val searchQuery: String = "",
    val selectedCategoryKey: String = "all",
    val selectedAccountId: String = "all",
    val selectedType: String = "all", // "all", "expense", "income", "transfer"
    val minAmount: Double? = null
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
    val transactions: List<TransactionItem>
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
    val isSingleAtomicBatch: Boolean
)

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
                .append(sanitizeCsvCell(t.amount.toString())).append(',')
                .append(sanitizeCsvCell(t.category)).append(',')
                .append(sanitizeCsvCell(t.accountId)).append(',')
                .append(sanitizeCsvCell(t.desc)).append(',')
                .append(sanitizeCsvCell(t.isoDate)).append('\n')
        }
        return sb.toString()
    }

    fun encodeV2Backup(state: DashboardState): String {
        val root = JSONObject().apply {
            put("backupVersion", 2)
            put("exportedAtIso", PayCycleEngine.todayIso())
            // Keep top-level keys for backward compatibility with v1 readers
            put("baseBudget", state.profile.baseBudget)
            put("payCycleDay", state.profile.payCycleDay)
            put("rolloverEnabled", state.profile.rolloverEnabled)

            put(
                "profile",
                JSONObject().apply {
                    put("baseBudget", state.profile.baseBudget)
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
                }
            )

            val accArr = JSONArray()
            state.accounts.forEach { accWithBal ->
                val a = accWithBal.account
                accArr.put(
                    JSONObject().apply {
                        put("id", a.id)
                        put("name", a.name)
                        put("kind", a.kind)
                        put("initialBalance", a.initialBalance)
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
                        put("amount", item.amount)
                        put("secondaryAmount", item.secondaryAmount)
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
                        put("amount", t.amount)
                        put("category", t.category)
                        put("envelopeKey", t.envelopeKey)
                        put("accountId", t.accountId)
                        put("toAccountId", t.toAccountId)
                        put("desc", t.desc)
                        put("iso", t.isoDate)
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

        val profileObj = obj.optJSONObject("profile")
        val rawBudget = if (profileObj != null && profileObj.has("baseBudget")) {
            profileObj.getDouble("baseBudget")
        } else if (obj.has("baseBudget")) {
            obj.getDouble("baseBudget")
        } else {
            fallbackProfile.baseBudget
        }
        val validBudget = requireFiniteInRange(rawBudget, 0.0, 100_000_000.0, "baseBudget").coerceAtLeast(100.0)

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
                dashboardOrder = CustomizationPrefs.normalizeOrderString(orderStr)
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
                val initBal = requireFiniteInRange(
                    a.optDouble("initialBalance", 0.0),
                    -100_000_000.0,
                    100_000_000.0,
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
                val amount = requireFiniteInRange(itObj.optDouble("amount", 0.0), 0.0, 100_000_000.0, "financialItem.amount")
                val secAmount = requireFiniteInRange(
                    itObj.optDouble("secondaryAmount", 0.0),
                    0.0,
                    100_000_000.0,
                    "financialItem.secondaryAmount"
                )
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
                        dayOrTurn = dayOrTurn,
                        extraInt = extraInt,
                        dateOrCycle = dateOrCycle
                    )
                )
            }
        }

        val txsList = parseTransactionsArray(txArr, currentUserId)

        return ParsedBackupPayload(
            backupVersion = version,
            exportedAtIso = obj.optString("exportedAtIso", ""),
            profile = restoredProfile,
            customPrefs = restoredCustomPrefs,
            accounts = accountsList,
            envelopes = envelopesList,
            financialItems = itemsList,
            transactions = txsList
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
        require(rawBytes <= AppSecurityManager.MAX_BACKUP_FILE_BYTES) {
            "Sauvegarde trop volumineuse ($rawBytes octets > 10 Mo max)."
        }
        val isEnc = AppSecurityManager.isEncryptedBackup(rawContent)
        val plainJson = if (isEnc) {
            AppSecurityManager.decryptBackupJson(rawContent, password)
        } else {
            rawContent
        }
        val parsed = decodeBackup(plainJson, currentUserId, fallbackProfile)
        val totalOps = (if (parsed.profile != null) 1 else 0) +
            (if (parsed.customPrefs != null) 1 else 0) +
            parsed.accounts.size +
            parsed.envelopes.size +
            parsed.financialItems.size +
            parsed.transactions.size
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
            isSingleAtomicBatch = chunks == 1
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
            val amt = if (item.has("amount")) item.getDouble("amount") else item.optDouble("montant", 0.0)
            requireFiniteInRange(amt, 0.01, 100_000_000.0, "transaction.amount")

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
                    isoDate = validatedIso
                )
            )
        }
        return list
    }
}

data class WeeklyBreakdownResult(
    val weekSpend: List<Double>,
    val weekFutureSpend: List<Double>,
    val prevWeekSpend: List<Double>,
    val weekLabel: String
)

data class DashboardState(
    val isLoading: Boolean = true,
    val profile: UserProfile = UserProfile(),
    val customPrefs: CustomizationPrefs = CustomizationPrefs(),
    val activeWindow: PayCycleWindow = PayCycleEngine.computeWindow("2026-10", 1),
    val accounts: List<AccountWithBalance> = emptyList(),
    val totalNetWorth: Double = 0.0,
    val envelopes: List<EnvelopeStatus> = emptyList(),
    val cycleTransactions: List<TransactionItem> = emptyList(),
    val filteredHistory: List<TransactionItem> = emptyList(),
    val upcomingTransactions: List<TransactionItem> = emptyList(),
    val allTransactions: List<TransactionItem> = emptyList(),
    val cycleExpenses: Double = 0.0,
    val cycleIncomes: Double = 0.0,
    val totalCycleRollover: Double = 0.0,
    val totalAvailableBudget: Double = 6400.0,
    val remainingBudget: Double = 6400.0,
    val unpaidBillsTotal: Double = 0.0,
    val safeToSpendToday: Double = 0.0,
    val dailyAverageSpend: Double = 0.0,
    val projectedCycleSpend: Double = 0.0,
    val spendProjection: CycleSpendProjection = CycleSpendProjection(),
    val previousCycleExpenses: Double = 0.0,
    val weekDaysSpend: List<Double> = List(7) { 0.0 },
    val weekDaysFutureSpend: List<Double> = List(7) { 0.0 },
    val weekTotalSpend: Double = 0.0,
    val prevWeekTotalSpend: Double = 0.0,
    val weekRangeLabel: String = "",
    val mostExpensiveDayIndex: Int = -1,
    val mostExpensiveDayAmount: Double = 0.0,
    val activeWeekDailyAverage: Double = 0.0,
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
    val biggestSpendingDayAmount: Double = 0.0,
    val detectedSubscriptions: List<DetectedSubscription> = emptyList(),
    val cycleSavings: Double = 0.0,
    val isOfflineMode: Boolean = false,
    val isSyncing: Boolean = false,
    val isCleanFreshAccount: Boolean = false,
    val hideAmounts: Boolean = false,
    val filterState: FilterState = FilterState(),
    val whatIfState: WhatIfSimulatorState = WhatIfSimulatorState(),
    val errorMessage: String? = null
)

class MasroufiViewModel(
    private val repository: MasroufiRepository,
    private val currentUserId: String,
    initialHideAmounts: Boolean = false,
    initialRollbackSnapshot: String? = null,
    initialDismissedSubscriptions: Set<String> = emptySet(),
    private val onRollbackSnapshotChanged: (String?) -> Unit = {},
    private val onDismissSubscription: (String) -> Unit = {}
) : ViewModel() {

    private val _selectedCycleKey = MutableStateFlow<String?>(null)
    private val _filterState = MutableStateFlow(FilterState())
    private val _whatIfState = MutableStateFlow(WhatIfSimulatorState())
    private val _hideAmounts = MutableStateFlow(initialHideAmounts)
    private val _dismissedSubscriptions = MutableStateFlow(initialDismissedSubscriptions)
    private val _lastDeletedTx = MutableStateFlow<TransactionItem?>(null)
    val lastDeletedTx: StateFlow<TransactionItem?> = _lastDeletedTx.asStateFlow()
    private val _lastDeletedItem = MutableStateFlow<FinancialItem?>(null)
    val lastDeletedItem: StateFlow<FinancialItem?> = _lastDeletedItem.asStateFlow()
    private val _pendingWritesCount = MutableStateFlow(0)

    // Instant optimistic local overrides so UI never lags or reverts on rapid clicks (and works in local mode)
    private val isLocalOnlyUser = currentUserId == LOCAL_DEVICE_USER_ID
    private val _optimisticProfile = MutableStateFlow<UserProfile?>(null)
    private val _optimisticCustomPrefs = MutableStateFlow<CustomizationPrefs?>(null)
    private val _localTransactions = MutableStateFlow<List<TransactionItem>?>(null)
    private val _localAccounts = MutableStateFlow<List<AccountItem>?>(
        if (isLocalOnlyUser) {
            listOf(
                AccountItem(id = "acc_cash", userId = currentUserId, name = "Espèces (Cash)", kind = "cash", initialBalance = 0.0, colorHex = "#10B981"),
                AccountItem(id = "acc_bank", userId = currentUserId, name = "Compte Bancaire", kind = "bank", initialBalance = 0.0, colorHex = "#38BDF8"),
                AccountItem(id = "acc_wallet", userId = currentUserId, name = "Portefeuille Mobile", kind = "wallet", initialBalance = 0.0, colorHex = "#F59E0B")
            )
        } else null
    )
    private val _localEnvelopes = MutableStateFlow<List<EnvelopeConfig>?>(
        if (isLocalOnlyUser) {
            listOf("charges", "marche", "transport", "cafe", "epargne", "autre").mapIndexed { idx, key ->
                EnvelopeConfig(
                    id = "env_$key",
                    userId = currentUserId,
                    key = key,
                    nameFr = DefaultCategoryResolver.localizedDefaultName(key, "fr") ?: "Marché",
                    nameAr = DefaultCategoryResolver.localizedDefaultName(key, "ar") ?: "السوق",
                    nameDarija = DefaultCategoryResolver.localizedDefaultName(key, "darija") ?: "التقدية",
                    nameEn = DefaultCategoryResolver.localizedDefaultName(key, "en") ?: "Groceries",
                    percentage = when (key) {
                        "charges" -> 35.0
                        "marche" -> 25.0
                        "transport" -> 15.0
                        "cafe" -> 10.0
                        "epargne" -> 10.0
                        else -> 5.0
                    },
                    hasCap = key != "autre",
                    colorHex = listOf("#284299", "#059669", "#0284C7", "#D97706", "#6D28D9", "#E11D48")[idx % 6]
                )
            }
        } else null
    )
    private val _localItems = MutableStateFlow<List<FinancialItem>?>(
        if (isLocalOnlyUser) {
            listOf(
                FinancialItem(id = "preset_cafe", userId = currentUserId, itemType = "preset", name = "Café", subtitle = "cafe", amount = 15.0, dateOrCycle = "acc_cash"),
                FinancialItem(id = "preset_ pain", userId = currentUserId, itemType = "preset", name = "Courses", subtitle = "marche", amount = 50.0, dateOrCycle = "acc_cash"),
                FinancialItem(id = "preset_taxi", userId = currentUserId, itemType = "preset", name = "Taxi", subtitle = "transport", amount = 20.0, dateOrCycle = "acc_cash")
            )
        } else null
    )
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _customPrefsItemCreatedAt = MutableStateFlow<com.google.firebase.Timestamp?>(null)

    private fun safeLaunch(tag: String, block: suspend () -> Unit) {
        if (isLocalOnlyUser) return
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

    private val transactionsFlow = combine(
        repository.observeTransactions(currentUserId).catch { e ->
            if (com.example.BuildConfig.DEBUG) Log.w("MasroufiVM", "Error observing transactions")
            if (!isLocalOnlyUser) {
                _errorMessage.value = "Impossible de charger les transactions en direct. Vérifiez votre connexion."
            }
            emit(emptyList())
        },
        _localTransactions
    ) { remote, local -> local ?: remote }

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
            combine(_pendingWritesCount, _dismissedSubscriptions) { pCount, dismissed -> pCount to dismissed }
        ) { (c, f, w), (cp, err, hide), (pendingCount, dismissedSubs) ->
            listOf(c, f, w, cp, err, hide, pendingCount > 0, dismissedSubs)
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

        val profile = profileOpt ?: UserProfile(userId = currentUserId)
        val storedPrefsItem = finItems.find { it.itemType == "custom_prefs" || it.id == "custom_prefs" }
        if (storedPrefsItem?.createdAt != null) {
            _customPrefsItemCreatedAt.value = storedPrefsItem.createdAt
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
        val netWorth = accountBalances.sumOf { it.currentBalance }

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
        val futureCycleExpenses = cycleTxs.filter { it.type == "expense" && it.isoDate > todayIso }.sumOf { it.amount }

        val cycleExpenses = pastOrTodayCycleTxs.filter { it.type == "expense" }.sumOf { it.amount }
        val cycleSavings = pastOrTodayCycleTxs.filter {
            it.type == "expense" && (
                it.envelopeKey.equals("epargne", ignoreCase = true) ||
                    DefaultCategoryResolver.canonicalDefaultId(it.category) == "epargne"
                )
        }.sumOf { it.amount }
        val cycleIncomes = pastOrTodayCycleTxs.filter { it.type == "income" }.sumOf { it.amount }
        val prevCycleExpenses = txs.filter {
            it.type == "expense" && PayCycleEngine.isInWindow(it.isoDate, prevWindow) && it.isoDate <= todayIso
        }.sumOf { it.amount }

        val totalRollover = envelopeStatuses.sumOf { it.rolloverAmount }
        val totalAvailable = profile.baseBudget + cycleIncomes + totalRollover
        val remaining = totalAvailable - cycleExpenses

        val bills = finItems.filter { it.itemType == "bill" }.sortedBy { it.dayOrTurn }
        val goals = finItems.filter { it.itemType == "goal" }
        val debts = finItems.filter { it.itemType == "debt" }
        val darets = finItems.filter { it.itemType == "daret" }
        val seasonals = finItems.filter { it.itemType == "seasonal" }

        val unpaidBills = bills.filter { !PayCycleEngine.isBillPaidInCycle(it, activeCycleKey) }
            .sumOf { it.amount }

        // Count future-dated transactions in Safe-to-spend only as committed bills
        val committedBillsAndFuture = unpaidBills + futureCycleExpenses
        val safePool = max(0.0, remaining - committedBillsAndFuture)
        val safeToday = if (window.daysRemaining > 0) safePool / window.daysRemaining else safePool
        val dailyAvg = cycleExpenses / max(1, window.daysElapsed)

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
        val weekTotal = weekSpend.sum()
        val maxDaySpend = weekSpend.maxOrNull() ?: 0.0
        val maxDayIdx = if (maxDaySpend > 0.0) weekSpend.indexOfFirst { it == maxDaySpend } else -1
        val activeDaysCount = weekSpend.count { it > 0.0 }.coerceAtLeast(1)

        val cappedEnvs = envelopeStatuses.filter { it.config.hasCap && it.totalAvailable > 0 }
        val bestEnv = cappedEnvs.minByOrNull { it.usagePercent }
        val worstEnv = cappedEnvs.maxByOrNull { it.usagePercent }
        val topEnv = envelopeStatuses.filter { it.spent > 0 }.maxByOrNull { it.spent }

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
            previousCycleExpenses = if (hasAtLeast14DaysData) prevCycleExpenses else 0.0,
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
            nowIso = anchorIso
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
                tx.amount.toInt().toString().contains(q)
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
            safeToSpendToday = safeToday,
            dailyAverageSpend = dailyAvg,
            projectedCycleSpend = projectedSpend,
            spendProjection = spendProjection,
            previousCycleExpenses = prevCycleExpenses,
            weekDaysSpend = weekSpend,
            weekDaysFutureSpend = weekFutureSpend,
            weekTotalSpend = weekTotal,
            prevWeekTotalSpend = prevWeekSpend.sum(),
            weekRangeLabel = weekLabel,
            mostExpensiveDayIndex = maxDayIdx,
            mostExpensiveDayAmount = maxDaySpend,
            activeWeekDailyAverage = weekTotal / activeDaysCount,
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
            isCleanFreshAccount = txs.isEmpty() && netWorth == 0.0,
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
        safeLaunch("ensureSeeded") {
            repository.ensureSeeded()
        }
    }

    private fun computeWeeklyBreakdown(
        anchorIso: String,
        txs: List<TransactionItem>,
        todayIso: String = PayCycleEngine.todayIso()
    ): WeeklyBreakdownResult {
        val (mondayEpochDay, rangeLabel) = PayCycleEngine.computeMondayEpochDayAndLabel(anchorIso)
        val curWeek = DoubleArray(7)
        val futureWeek = DoubleArray(7)
        val prevWeek = DoubleArray(7)
        for (tx in txs) {
            if (tx.type != "expense") continue
            val txDay = PayCycleEngine.parseEpochDays(tx.isoDate)
            val diff = (txDay - mondayEpochDay).toInt()
            if (diff in 0..6) {
                if (tx.isoDate > todayIso) {
                    futureWeek[diff] += tx.amount
                } else {
                    curWeek[diff] += tx.amount
                }
            } else if (diff in -7..-1 && tx.isoDate <= todayIso) {
                prevWeek[diff + 7] += tx.amount
            }
        }
        return WeeklyBreakdownResult(
            weekSpend = curWeek.toList(),
            weekFutureSpend = futureWeek.toList(),
            prevWeekSpend = prevWeek.toList(),
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
        val updated = cur.copy(isDarkTheme = !cur.isDarkTheme)
        _optimisticProfile.value = updated
        safeLaunch("toggleTheme") {
            repository.saveProfile(updated)
        }
    }

    fun applyDefaultLightRevolutThemeMigration() {
        val curPrefs = uiState.value.customPrefs
        if (curPrefs.accentPalette.isBlank()) {
            saveCustomizationPrefs(curPrefs.copy(accentPalette = "majorelle"))
        }
    }

    fun saveBudgetSettings(baseBudget: Double, payCycleDay: Int, rolloverEnabled: Boolean) {
        val cur = uiState.value.profile
        val updated = cur.copy(
            baseBudget = PayCycleEngine.roundMoney(baseBudget).coerceAtLeast(100.0),
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
        return FinancialItem(
            id = "custom_prefs",
            userId = currentUserId,
            itemType = "custom_prefs",
            name = prefs.customTitle.ifBlank { "Masroufi" }.take(80),
            subtitle = "${prefs.accentPalette}|${prefs.currencySymbol.ifBlank { "DH" }}|$orderCsv",
            amount = 0.0,
            secondaryAmount = 0.0,
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
        amount: Double,
        category: String,
        envelopeKey: String,
        accountId: String,
        toAccountId: String = "",
        desc: String,
        isoDate: String
    ) {
        val roundedAmount = PayCycleEngine.roundMoney(amount)
        if (roundedAmount <= 0.0) return
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
        if (type == "expense" && desc.isNotBlank() && resolvedEnvKey.isNotBlank()) {
            PayCycleEngine.learnCategoryForDescription(desc, resolvedEnvKey)
        }
        val normalizedCat = DefaultCategoryResolver.normalizeStoredCategoryValue(category, resolvedEnvKey)
        val newTx = TransactionItem(
            id = existingId.ifBlank { "tx_${System.currentTimeMillis()}" },
            userId = currentUserId,
            type = type,
            amount = roundedAmount,
            category = normalizedCat,
            envelopeKey = resolvedEnvKey,
            accountId = accountId.ifBlank { "acc_cash" },
            toAccountId = if (type == "transfer") toAccountId else "",
            desc = desc.ifBlank { category.ifBlank { "Opération" } },
            isoDate = validatedDate,
            createdAt = existing?.createdAt
        )
        if (isLocalOnlyUser) {
            val curList = (_localTransactions.value ?: uiState.value.allTransactions).filterNot { it.id == newTx.id }
            _localTransactions.value = listOf(newTx) + curList
            return
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
        val tx = TransactionItem(
            id = "tx_${System.currentTimeMillis()}",
            userId = currentUserId,
            type = "expense",
            amount = PayCycleEngine.roundMoney(preset.amount).coerceAtLeast(1.0),
            category = normalizedCat,
            envelopeKey = envKey,
            accountId = preset.dateOrCycle.ifBlank { "acc_cash" },
            toAccountId = "",
            desc = preset.name,
            isoDate = PayCycleEngine.todayIso()
        )
        _lastDeletedTx.value = tx
        onAdded(tx)
        if (isLocalOnlyUser) {
            val curList = _localTransactions.value ?: uiState.value.allTransactions
            _localTransactions.value = listOf(tx) + curList
            return
        }
        safeLaunch("quickAddPreset") {
            repository.upsertTransaction(tx)
        }
    }

    fun duplicateTransaction(tx: TransactionItem, onDuplicated: (TransactionItem) -> Unit = {}) {
        val duplicated = tx.copy(
            id = "tx_dup_${System.currentTimeMillis()}",
            isoDate = PayCycleEngine.todayIso(),
            createdAt = null,
            updatedAt = null
        )
        onDuplicated(duplicated)
        if (isLocalOnlyUser) {
            val curList = _localTransactions.value ?: uiState.value.allTransactions
            _localTransactions.value = listOf(duplicated) + curList
            return
        }
        safeLaunch("duplicateTransaction") {
            repository.upsertTransaction(duplicated)
        }
    }

    fun completeQuickOnboarding(
        salary: Double,
        payDay: Int,
        threeEnvelopes: List<Triple<String, Double, String>>,
        onDone: () -> Unit = {}
    ) {
        val curProfile = uiState.value.profile
        val updatedProfile = curProfile.copy(
            baseBudget = salary.coerceAtLeast(100.0),
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

        if (isLocalOnlyUser) {
            if (updatedEnvelopes.isNotEmpty()) {
                _localEnvelopes.value = updatedEnvelopes
            }
            onDone()
            return
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
        if (isLocalOnlyUser) {
            _localTransactions.value = (_localTransactions.value ?: uiState.value.allTransactions).filterNot { it.id == tx.id }
            return
        }
        safeLaunch("deleteTransactionWithUndo") {
            repository.deleteTransaction(tx.id)
        }
    }

    fun undoDeleteTransaction() {
        val tx = _lastDeletedTx.value ?: return
        _lastDeletedTx.value = null
        if (isLocalOnlyUser) {
            _localTransactions.value = listOf(tx) + (_localTransactions.value ?: uiState.value.allTransactions)
            return
        }
        safeLaunch("undoDeleteTransaction") {
            repository.upsertTransaction(tx.copy(createdAt = null, updatedAt = null))
        }
    }

    fun saveAccount(id: String = "", name: String, kind: String, initialBalance: Double, colorHex: String) {
        val existing = uiState.value.accounts.find { it.account.id == id }?.account
        safeLaunch("saveAccount") {
            repository.upsertAccount(
                AccountItem(
                    id = id,
                    userId = currentUserId,
                    name = name.ifBlank { "Compte" },
                    kind = if (kind in listOf("cash", "bank", "wallet")) kind else "cash",
                    initialBalance = PayCycleEngine.roundMoney(initialBalance),
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
        safeLaunch("saveFinancialItem") {
            repository.upsertFinancialItem(
                item.copy(
                    userId = currentUserId,
                    amount = PayCycleEngine.roundMoney(item.amount),
                    secondaryAmount = PayCycleEngine.roundMoney(item.secondaryAmount),
                    createdAt = item.createdAt ?: existing?.createdAt
                )
            )
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
            amount = sub.amount.coerceAtLeast(1.0),
            secondaryAmount = 0.0,
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
        totalAmount: Double,
        friendsNamesCsv: String,
        includeSelf: Boolean = true,
        recordFullExpenseNow: Boolean = true,
        accountId: String = "acc_cash",
        envelopeKey: String = "cafe",
        onDone: (Int, Double) -> Unit = { _, _ -> }
    ) {
        if (totalAmount <= 0.0 || totalAmount.isNaN()) return
        val friends = friendsNamesCsv
            .split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (friends.isEmpty()) return
        val divisor = if (includeSelf) friends.size + 1 else friends.size
        val sharePerPerson = ((totalAmount / divisor) * 100.0).roundToInt() / 100.0
        if (sharePerPerson <= 0.0) return

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
                    secondaryAmount = 0.0,
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
        val updatedBill = bill.copy(dateOrCycle = updatedCycles)
        val txId = "tx_bill_${bill.id}_$cycleKey"
        val existingTx = uiState.value.allTransactions.find { it.id == txId }
        val tx = TransactionItem(
            id = txId,
            userId = currentUserId,
            type = "expense",
            amount = bill.amount.coerceAtLeast(1.0),
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

    fun contributeToGoalOrSeasonal(item: FinancialItem, addAmount: Double) {
        val roundedAdd = PayCycleEngine.roundMoney(addAmount)
        if (roundedAdd <= 0.0) return
        val updatedItem = item.copy(secondaryAmount = PayCycleEngine.roundMoney(item.secondaryAmount + roundedAdd))
        val tx = TransactionItem(
            id = "tx_contrib_${item.id}_${System.currentTimeMillis()}",
            userId = currentUserId,
            type = "expense",
            amount = roundedAdd,
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
        val totalMembers = if (daret.secondaryAmount.isNaN()) 1 else daret.secondaryAmount.roundToInt().coerceAtLeast(1)
        val nextPaid = (daret.extraInt + 1).coerceAtMost(totalMembers)
        val updatedDaret = daret.copy(extraInt = nextPaid)
        val txId = "tx_daret_${daret.id}_round_$nextPaid"
        val existingTx = uiState.value.allTransactions.find { it.id == txId }
        val tx = TransactionItem(
            id = txId,
            userId = currentUserId,
            type = "expense",
            amount = PayCycleEngine.roundMoney(daret.amount).coerceAtLeast(1.0),
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

    fun settleDebt(debt: FinancialItem, payment: Double) {
        val roundedPayment = PayCycleEngine.roundMoney(payment)
        if (roundedPayment <= 0.0) return
        val newPaid = PayCycleEngine.roundMoney(debt.secondaryAmount + roundedPayment).coerceAtMost(debt.amount)
        val isLent = debt.dateOrCycle != "borrowed"
        val updatedDebt = debt.copy(secondaryAmount = newPaid)
        val tx = TransactionItem(
            id = "tx_debt_${debt.id}_${System.currentTimeMillis()}",
            userId = currentUserId,
            type = if (isLent) "income" else "expense",
            amount = roundedPayment,
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
     * Complete Version 2 JSON Backup (profile, customPrefs, accounts, envelopes,
     * financialItems [goals, bills, debts, darets, seasonals, presets], and transactions).
     * If [password] is provided (non-blank), encrypts the JSON with AES-256-GCM + PBKDF2-HMAC-SHA256.
     */
    fun exportBackupJson(password: String = ""): String {
        val plain = BackupSerializer.encodeV2Backup(uiState.value)
        return if (password.trim().isNotEmpty()) {
            AppSecurityManager.encryptBackupJson(plain, password.trim())
        } else {
            plain
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

    fun restoreFromJson(rawJson: String, onResult: (Boolean) -> Unit) {
        restoreFromJson(
            rawJson = rawJson,
            password = "",
            replaceExisting = true,
            onResult = { ok, _ -> onResult(ok) }
        )
    }

    /**
     * Two-phase backup restore (SEC-07):
     * Phase 1: Decrypts (if AES-256-GCM encrypted) and validates the entire backup payload in memory
     *          against size/item limits and Firestore rules constraints, and persists an encrypted
     *          pre-restore rollback snapshot of the current state.
     * Phase 2: Executes unified Firestore batch writes (sets + stale deletes in 400-op chunks),
     *          and automatically replays the rollback snapshot if a mid-stream failure occurs.
     */
    fun restoreFromJson(
        rawJson: String,
        password: String = "",
        replaceExisting: Boolean = true,
        isRollbackReplay: Boolean = false,
        onResult: (Boolean, String) -> Unit
    ) {
        safeLaunch("restoreFromJson") {
            val currentState = uiState.value
            val rollbackSnapshot = if (!isRollbackReplay) {
                BackupSerializer.encodeV2Backup(currentState)
            } else null

            // Phase 1: Decrypt (if encrypted) & validate entire backup in memory before any Firestore write
            val parsed = try {
                val rawBytes = rawJson.toByteArray(Charsets.UTF_8).size
                require(rawBytes <= AppSecurityManager.MAX_BACKUP_FILE_BYTES) {
                    "Sauvegarde trop volumineuse ($rawBytes octets > 10 Mo max)."
                }
                val plainJson = if (AppSecurityManager.isEncryptedBackup(rawJson)) {
                    AppSecurityManager.decryptBackupJson(rawJson, password)
                } else {
                    rawJson
                }
                BackupSerializer.decodeBackup(
                    rawJson = plainJson,
                    currentUserId = currentUserId,
                    fallbackProfile = currentState.profile
                )
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) {
                    Log.w("MasroufiVM", "Phase 1 backup validation failed: ${e.message}")
                }
                val msg = e.localizedMessage ?: "Format invalide ou mot de passe erroné."
                _errorMessage.value = "Erreur lors de la validation : $msg"
                onResult(false, msg)
                return@safeLaunch
            }

            // Persist encrypted rollback snapshot before mutating state
            if (rollbackSnapshot != null) {
                _preRestoreRollbackJson.value = rollbackSnapshot
                onRollbackSnapshotChanged(rollbackSnapshot)
            }

            val existingAccMap = currentState.accounts.associate { it.account.id to it.account.createdAt }
            val existingEnvMap = currentState.envelopes.associate { it.config.id to it.config.createdAt }
            val allExistingItems = currentState.presets + currentState.goals + currentState.bills +
                currentState.debts + currentState.darets + currentState.seasonals
            val existingItemMap = allExistingItems.associate { it.id to it.createdAt }
            val existingTxMap = currentState.allTransactions.associate { it.id to it.createdAt }

            val profileWithCreatedAt = parsed.profile?.copy(createdAt = currentState.profile.createdAt)
            if (profileWithCreatedAt != null) {
                _optimisticProfile.value = profileWithCreatedAt
            }
            val customPrefsItem = if (parsed.customPrefs != null) {
                _optimisticCustomPrefs.value = parsed.customPrefs
                buildCustomPrefsFinancialItem(parsed.customPrefs, _customPrefsItemCreatedAt.value)
            } else null

            val accountsWithCreatedAt = parsed.accounts.map { acc ->
                acc.copy(createdAt = existingAccMap[acc.id])
            }
            val envelopesWithCreatedAt = parsed.envelopes.map { env ->
                env.copy(createdAt = existingEnvMap[env.id])
            }
            val itemsWithCreatedAt = parsed.financialItems.map { item ->
                item.copy(createdAt = existingItemMap[item.id])
            }
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

            try {
                var finalChunks = 1
                // Phase 2: Execute unified bounded batches
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
                    onChunkProgress = { _, total -> finalChunks = total }
                )
                onResult(
                    true,
                    "Restauration réussie (${parsed.transactions.size} op., $finalChunks lot(s) synchronisé(s)) !"
                )
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) {
                    Log.e("MasroufiVM", "Phase 2 restore failed; attempting automatic rollback", e)
                }
                // Automatic rollback replay if a multi-chunk restore failed mid-stream (SEC-07)
                if (!isRollbackReplay && !rollbackSnapshot.isNullOrBlank()) {
                    runCatching {
                        val rbParsed = BackupSerializer.decodeBackup(
                            rawJson = rollbackSnapshot,
                            currentUserId = currentUserId,
                            fallbackProfile = currentState.profile
                        )
                        repository.restoreFullBackupAtomically(
                            profile = rbParsed.profile?.copy(createdAt = currentState.profile.createdAt),
                            customPrefsItem = rbParsed.customPrefs?.let {
                                buildCustomPrefsFinancialItem(it, _customPrefsItemCreatedAt.value)
                            },
                            accounts = rbParsed.accounts.map { it.copy(createdAt = existingAccMap[it.id]) },
                            envelopes = rbParsed.envelopes.map { it.copy(createdAt = existingEnvMap[it.id]) },
                            financialItems = rbParsed.financialItems.map { it.copy(createdAt = existingItemMap[it.id]) },
                            transactions = rbParsed.transactions.map { it.copy(createdAt = existingTxMap[it.id]) },
                            staleTxIdsToDelete = parsed.transactions.map { it.id },
                            staleItemIdsToDelete = parsed.financialItems.map { it.id },
                            staleAccountIdsToDelete = parsed.accounts.map { it.id },
                            staleEnvelopeIdsToDelete = parsed.envelopes.map { it.id }
                        )
                    }
                }
                val msg = e.localizedMessage ?: "Erreur réseau lors de la restauration (Rollback appliqué)."
                _errorMessage.value = "Erreur lors de la restauration : $msg"
                onResult(false, msg)
            }
        }
    }

    fun rollbackLastRestore(onResult: (Boolean) -> Unit) {
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
