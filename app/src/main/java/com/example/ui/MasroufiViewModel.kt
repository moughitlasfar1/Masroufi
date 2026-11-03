package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountItem
import com.example.data.CustomizationPrefs
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.MasroufiRepository
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.domain.AccountWithBalance
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

    fun decodeBackup(rawJson: String, currentUserId: String, fallbackProfile: UserProfile): ParsedBackupPayload {
        val trimmed = rawJson.trim()
        if (trimmed.startsWith("[")) {
            val txArray = JSONArray(trimmed)
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
        val restoredProfile = UserProfile(
            id = currentUserId,
            userId = currentUserId,
            baseBudget = (profileObj?.optDouble("baseBudget", obj.optDouble("baseBudget", fallbackProfile.baseBudget))
                ?: obj.optDouble("baseBudget", fallbackProfile.baseBudget)).coerceAtLeast(100.0),
            payCycleDay = (profileObj?.optInt("payCycleDay", obj.optInt("payCycleDay", fallbackProfile.payCycleDay))
                ?: obj.optInt("payCycleDay", fallbackProfile.payCycleDay)).coerceIn(1, 28),
            rolloverEnabled = profileObj?.optBoolean("rolloverEnabled", obj.optBoolean("rolloverEnabled", true))
                ?: obj.optBoolean("rolloverEnabled", true),
            language = profileObj?.optString("language", fallbackProfile.language)?.ifBlank { "fr" } ?: fallbackProfile.language,
            isDarkTheme = profileObj?.optBoolean("isDarkTheme", fallbackProfile.isDarkTheme) ?: fallbackProfile.isDarkTheme,
            seeded = true
        )

        val customObj = obj.optJSONObject("customPrefs")
        val restoredCustomPrefs = if (customObj != null) {
            val mask = customObj.optInt("visibilityMask", 63)
            CustomizationPrefs(
                accentPalette = customObj.optString("accentPalette", "emerald").ifBlank { "emerald" },
                currencySymbol = customObj.optString("currencySymbol", "DH").ifBlank { "DH" },
                customTitle = customObj.optString("customTitle", ""),
                customSubtitle = customObj.optString("customSubtitle", ""),
                showSafeToSpend = (mask and 1) != 0,
                showAccountsCard = (mask and 2) != 0,
                showQuickPresetsCard = (mask and 4) != 0,
                showWeekSummaryRow = (mask and 8) != 0,
                showMonthlyReviewCard = (mask and 16) != 0,
                showBillsCard = (mask and 32) != 0
            )
        } else null

        val accountsList = mutableListOf<AccountItem>()
        val accArr = obj.optJSONArray("accounts")
        if (accArr != null) {
            for (i in 0 until accArr.length()) {
                val a = accArr.optJSONObject(i) ?: continue
                accountsList.add(
                    AccountItem(
                        id = a.optString("id", "acc_restored_$i"),
                        userId = currentUserId,
                        name = a.optString("name", "Compte"),
                        kind = a.optString("kind", "cash"),
                        initialBalance = a.optDouble("initialBalance", 0.0),
                        colorHex = a.optString("colorHex", "#10B981")
                    )
                )
            }
        }

        val envelopesList = mutableListOf<EnvelopeConfig>()
        val envArr = obj.optJSONArray("envelopes")
        if (envArr != null) {
            for (i in 0 until envArr.length()) {
                val e = envArr.optJSONObject(i) ?: continue
                val nFr = e.optString("nameFr", "Catégorie")
                envelopesList.add(
                    EnvelopeConfig(
                        id = e.optString("id", "env_restored_$i"),
                        userId = currentUserId,
                        key = e.optString("key", "custom_$i"),
                        nameFr = nFr,
                        nameAr = e.optString("nameAr", nFr),
                        nameDarija = e.optString("nameDarija", nFr),
                        nameEn = e.optString("nameEn", nFr),
                        percentage = e.optDouble("percentage", 0.0),
                        hasCap = e.optBoolean("hasCap", true),
                        colorHex = e.optString("colorHex", "#10B981")
                    )
                )
            }
        }

        val itemsList = mutableListOf<FinancialItem>()
        val itemsArr = obj.optJSONArray("financialItems")
        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                val itObj = itemsArr.optJSONObject(i) ?: continue
                itemsList.add(
                    FinancialItem(
                        id = itObj.optString("id", "item_restored_$i"),
                        userId = currentUserId,
                        itemType = itObj.optString("itemType", "goal"),
                        name = itObj.optString("name", "Élément"),
                        subtitle = itObj.optString("subtitle", ""),
                        amount = itObj.optDouble("amount", 0.0),
                        secondaryAmount = itObj.optDouble("secondaryAmount", 0.0),
                        dayOrTurn = itObj.optInt("dayOrTurn", 1),
                        extraInt = itObj.optInt("extraInt", 0),
                        dateOrCycle = itObj.optString("dateOrCycle", "")
                    )
                )
            }
        }

        val txArr = obj.optJSONArray("transactions") ?: JSONArray()
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
        val list = mutableListOf<TransactionItem>()
        for (i in 0 until txArray.length()) {
            val item = txArray.optJSONObject(i) ?: continue
            val amt = item.optDouble("amount", item.optDouble("montant", 0.0))
            if (amt > 0.0) {
                val cat = item.optString("category", "Autre / Imprévus")
                val desc = item.optString("desc", "Transaction")
                val rawIso = item.optString("iso", item.optString("isoDate", ""))
                val validatedIso = PayCycleEngine.requireOrNormalizeIsoDate(rawIso)
                list.add(
                    TransactionItem(
                        id = item.optString("id", "tx_${System.currentTimeMillis()}_$i"),
                        userId = currentUserId,
                        type = item.optString("type", "expense"),
                        amount = amt,
                        category = cat,
                        envelopeKey = item.optString(
                            "envelopeKey",
                            PayCycleEngine.resolveEnvelopeKey(cat, desc, emptyList())
                        ),
                        accountId = item.optString("accountId", "acc_cash"),
                        toAccountId = item.optString("toAccountId", ""),
                        desc = desc,
                        isoDate = validatedIso
                    )
                )
            }
        }
        return list
    }
}

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
    val previousCycleExpenses: Double = 0.0,
    val weekDaysSpend: List<Double> = List(7) { 0.0 },
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
    val isCleanFreshAccount: Boolean = false,
    val filterState: FilterState = FilterState(),
    val whatIfState: WhatIfSimulatorState = WhatIfSimulatorState(),
    val errorMessage: String? = null
)

class MasroufiViewModel(
    private val repository: MasroufiRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedCycleKey = MutableStateFlow<String?>(null)
    private val _filterState = MutableStateFlow(FilterState())
    private val _whatIfState = MutableStateFlow(WhatIfSimulatorState())
    private val _lastDeletedTx = MutableStateFlow<TransactionItem?>(null)
    val lastDeletedTx: StateFlow<TransactionItem?> = _lastDeletedTx.asStateFlow()

    // Instant optimistic local overrides so UI never lags or reverts on rapid clicks
    private val _optimisticProfile = MutableStateFlow<UserProfile?>(null)
    private val _optimisticCustomPrefs = MutableStateFlow<CustomizationPrefs?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    init {
        safeLaunch("ensureSeeded") {
            repository.ensureSeeded()
        }
    }

    private fun safeLaunch(tag: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                _errorMessage.value = null
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w("MasroufiVM", "Non-fatal error in $tag: ${e.message}", e)
                _errorMessage.value = "Synchronisation hors-ligne ou erreur réseau (${e.localizedMessage?.take(60) ?: tag}). Vos modifications seront resynchronisées."
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
            Log.w("MasroufiVM", "Error observing profile", e)
            _errorMessage.value = "Connexion cloud momentanément indisponible. Mode cache actif."
            emit(UserProfile(userId = currentUserId))
        }

    private val transactionsFlow = repository.observeTransactions(currentUserId)
        .catch { e ->
            Log.w("MasroufiVM", "Error observing transactions", e)
            _errorMessage.value = "Impossible de charger les transactions en direct. Vérifiez votre connexion."
            emit(emptyList())
        }

    private val accountsFlow = repository.observeAccounts(currentUserId)
        .catch { e ->
            Log.w("MasroufiVM", "Error observing accounts", e)
            emit(emptyList())
        }

    private val envelopesFlow = repository.observeEnvelopes(currentUserId)
        .catch { e ->
            Log.w("MasroufiVM", "Error observing envelopes", e)
            emit(emptyList())
        }

    private val itemsFlow = repository.observeFinancialItems(currentUserId)
        .catch { e ->
            Log.w("MasroufiVM", "Error observing financial items", e)
            emit(emptyList())
        }

    val uiState: StateFlow<DashboardState> = combine(
        combine(profileFlow, _optimisticProfile, transactionsFlow) { p, optP, t -> Triple(optP ?: p, t, Unit) },
        combine(accountsFlow, envelopesFlow, itemsFlow) { a, e, i -> Triple(a, e, i) },
        combine(_selectedCycleKey, _filterState, _whatIfState, _optimisticCustomPrefs, _errorMessage) { c, f, w, cp, err ->
            listOf(c, f, w, cp, err)
        }
    ) { (profileOpt, txs, _), (accs, envs, finItems), extraList ->
        val selCycle = extraList[0] as? String
        val filter = extraList[1] as FilterState
        val whatIf = extraList[2] as WhatIfSimulatorState
        val optCustomPrefs = extraList[3] as? CustomizationPrefs
        val errMsg = extraList[4] as? String

        val profile = profileOpt ?: UserProfile(userId = currentUserId)
        val storedPrefsItem = finItems.find { it.itemType == "custom_prefs" || it.id == "custom_prefs" }
        val customPrefs = optCustomPrefs ?: CustomizationPrefs.fromFinancialItem(storedPrefsItem)
        val curSymbol = customPrefs.currencySymbol.ifBlank { "DH" }

        val defaultCycle = PayCycleEngine.currentCycleKey(profile.payCycleDay)
        val activeCycleKey = selCycle ?: defaultCycle
        val window = PayCycleEngine.computeWindow(activeCycleKey, profile.payCycleDay)
        val prevCycleKey = PayCycleEngine.shiftCycleKey(activeCycleKey, -1)
        val prevWindow = PayCycleEngine.computeWindow(prevCycleKey, profile.payCycleDay)

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
            allTransactions = txs
        )

        val cycleTxs = txs.filter { PayCycleEngine.isInWindow(it.isoDate, window) }
            .sortedWith(compareByDescending<TransactionItem> { it.isoDate }.thenByDescending { it.id })

        val cycleExpenses = cycleTxs.filter { it.type == "expense" }.sumOf { it.amount }
        val cycleIncomes = cycleTxs.filter { it.type == "income" }.sumOf { it.amount }
        val prevCycleExpenses = txs.filter { it.type == "expense" && PayCycleEngine.isInWindow(it.isoDate, prevWindow) }
            .sumOf { it.amount }

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

        val safePool = max(0.0, remaining - unpaidBills)
        val safeToday = if (window.daysRemaining > 0) safePool / window.daysRemaining else safePool
        val dailyAvg = cycleExpenses / max(1, window.daysElapsed)
        val projectedSpend = if (window.isCurrentCycle) dailyAvg * window.totalDays else cycleExpenses

        // Timezone/DST-safe weekly calculations (Monday - Sunday of anchor date)
        val anchorIso = if (window.isCurrentCycle) PayCycleEngine.todayIso() else window.endIso
        val (weekSpend, prevWeekSpend, weekLabel) = computeWeeklyBreakdown(anchorIso, txs)
        val weekTotal = weekSpend.sum()
        val maxDaySpend = weekSpend.maxOrNull() ?: 0.0
        val maxDayIdx = if (maxDaySpend > 0.0) weekSpend.indexOfFirst { it == maxDaySpend } else -1
        val activeDaysCount = weekSpend.count { it > 0.0 }.coerceAtLeast(1)

        val cappedEnvs = envelopeStatuses.filter { it.config.hasCap && it.totalAvailable > 0 }
        val bestEnv = cappedEnvs.minByOrNull { it.usagePercent }
        val worstEnv = cappedEnvs.maxByOrNull { it.usagePercent }
        val topEnv = envelopeStatuses.filter { it.spent > 0 }.maxByOrNull { it.spent }

        val smartInsights = PayCycleEngine.computeSmartInsights(
            lang = profile.language,
            currency = curSymbol,
            window = window,
            totalAvailableBudget = totalAvailable,
            cycleExpenses = cycleExpenses,
            previousCycleExpenses = prevCycleExpenses,
            safeToSpendToday = safeToday,
            dailyAverageSpend = dailyAvg,
            envelopes = envelopeStatuses
        )

        val smartReminders = PayCycleEngine.computeSmartReminders(
            cycleKey = activeCycleKey,
            currency = curSymbol,
            bills = bills,
            darets = darets,
            debts = debts
        )

        val filteredList = cycleTxs.filter { tx ->
            val q = filter.searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                tx.desc.lowercase().contains(q) ||
                tx.category.lowercase().contains(q) ||
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
            matchesQuery && matchesCat && matchesAcc && matchesType && matchesMin
        }

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
            previousCycleExpenses = prevCycleExpenses,
            weekDaysSpend = weekSpend,
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
            isCleanFreshAccount = txs.isEmpty() && netWorth == 0.0,
            filterState = filter,
            whatIfState = whatIf,
            errorMessage = errMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DashboardState()
    )

    private fun computeWeeklyBreakdown(
        anchorIso: String,
        txs: List<TransactionItem>
    ): Triple<List<Double>, List<Double>, String> {
        val (mondayEpochDay, rangeLabel) = PayCycleEngine.computeMondayEpochDayAndLabel(anchorIso)
        val curWeek = DoubleArray(7)
        val prevWeek = DoubleArray(7)
        for (tx in txs) {
            if (tx.type != "expense") continue
            val txDay = PayCycleEngine.parseEpochDays(tx.isoDate)
            val diff = (txDay - mondayEpochDay).toInt()
            if (diff in 0..6) {
                curWeek[diff] += tx.amount
            } else if (diff in -7..-1) {
                prevWeek[diff + 7] += tx.amount
            }
        }
        return Triple(curWeek.toList(), prevWeek.toList(), rangeLabel)
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

    fun saveBudgetSettings(baseBudget: Double, payCycleDay: Int, rolloverEnabled: Boolean) {
        val cur = uiState.value.profile
        val updated = cur.copy(
            baseBudget = baseBudget.coerceAtLeast(100.0),
            payCycleDay = payCycleDay.coerceIn(1, 28),
            rolloverEnabled = rolloverEnabled
        )
        _optimisticProfile.value = updated
        _selectedCycleKey.value = null
        safeLaunch("saveBudgetSettings") {
            repository.saveProfile(updated)
        }
    }

    fun saveCustomizationPrefs(prefs: CustomizationPrefs) {
        _optimisticCustomPrefs.value = prefs
        val item = buildCustomPrefsFinancialItem(prefs)
        safeLaunch("saveCustomizationPrefs") {
            repository.upsertFinancialItem(item)
        }
    }

    private fun buildCustomPrefsFinancialItem(prefs: CustomizationPrefs): FinancialItem {
        return FinancialItem(
            id = "custom_prefs",
            userId = currentUserId,
            itemType = "custom_prefs",
            name = prefs.customTitle.ifBlank { "Masroufi" }.take(80),
            subtitle = "${prefs.accentPalette}|${prefs.currencySymbol.ifBlank { "DH" }}",
            amount = 0.0,
            secondaryAmount = 0.0,
            dayOrTurn = prefs.toVisibilityMask(),
            extraInt = 0,
            dateOrCycle = prefs.customSubtitle.take(200)
        )
    }

    fun loadDemoData(onDone: () -> Unit = {}) {
        safeLaunch("loadDemoData") {
            repository.loadDemoData()
            onDone()
        }
    }

    fun resetToCleanAccount(onDone: () -> Unit = {}) {
        val s = uiState.value
        val txIds = s.allTransactions.map { it.id }
        val itemIds = (s.goals + s.bills + s.debts + s.darets + s.seasonals).map { it.id }
        val accIds = s.accounts.map { it.account.id }
        safeLaunch("resetToCleanAccount") {
            repository.resetToCleanAccount(
                existingTxIds = txIds,
                existingItemIdsToClear = itemIds,
                existingAccountIds = accIds
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
        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) return
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
        safeLaunch("saveTransaction") {
            repository.upsertTransaction(
                TransactionItem(
                    id = existingId,
                    userId = currentUserId,
                    type = type,
                    amount = amount,
                    category = category.ifBlank { "Général" },
                    envelopeKey = resolvedEnvKey,
                    accountId = accountId.ifBlank { "acc_cash" },
                    toAccountId = if (type == "transfer") toAccountId else "",
                    desc = desc.ifBlank { category.ifBlank { "Opération" } },
                    isoDate = validatedDate,
                    createdAt = existing?.createdAt
                )
            )
        }
    }

    fun quickAddPreset(preset: FinancialItem, onAdded: (TransactionItem) -> Unit = {}) {
        val env = uiState.value.envelopes.find { it.config.key == preset.subtitle }?.config
        val catName = env?.localizedName(uiState.value.profile.language) ?: preset.name
        val tx = TransactionItem(
            id = "tx_${System.currentTimeMillis()}",
            userId = currentUserId,
            type = "expense",
            amount = preset.amount.coerceAtLeast(1.0),
            category = catName.ifBlank { preset.name },
            envelopeKey = preset.subtitle.ifBlank { "cafe" },
            accountId = preset.dateOrCycle.ifBlank { "acc_cash" },
            toAccountId = "",
            desc = preset.name,
            isoDate = PayCycleEngine.todayIso()
        )
        _lastDeletedTx.value = tx
        onAdded(tx)
        safeLaunch("quickAddPreset") {
            repository.upsertTransaction(tx)
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

    fun saveAccount(id: String = "", name: String, kind: String, initialBalance: Double, colorHex: String) {
        val existing = uiState.value.accounts.find { it.account.id == id }?.account
        safeLaunch("saveAccount") {
            repository.upsertAccount(
                AccountItem(
                    id = id,
                    userId = currentUserId,
                    name = name.ifBlank { "Compte" },
                    kind = if (kind in listOf("cash", "bank", "wallet")) kind else "cash",
                    initialBalance = if (initialBalance.isNaN() || initialBalance.isInfinite()) 0.0 else initialBalance,
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
        safeLaunch("saveEnvelopeConfig") {
            repository.upsertEnvelope(config.copy(userId = currentUserId))
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
        safeLaunch("saveFinancialItem") {
            repository.upsertFinancialItem(item.copy(userId = currentUserId))
        }
    }

    fun deleteFinancialItem(id: String) {
        if (id.isBlank()) return
        safeLaunch("deleteFinancialItem") {
            repository.deleteFinancialItem(id)
        }
    }

    fun payRecurringBill(bill: FinancialItem) {
        val cycleKey = uiState.value.activeWindow.cycleKey
        if (PayCycleEngine.isBillPaidInCycle(bill, cycleKey)) return
        val updatedCycles = (bill.dateOrCycle.split(",").filter { it.isNotBlank() } + cycleKey).distinct().joinToString(",")
        val updatedBill = bill.copy(dateOrCycle = updatedCycles)
        val tx = TransactionItem(
            id = "tx_bill_${bill.id}_$cycleKey",
            userId = currentUserId,
            type = "expense",
            amount = bill.amount.coerceAtLeast(1.0),
            category = "Charges fixes & Maison",
            envelopeKey = "charges",
            accountId = "acc_bank",
            desc = bill.name,
            isoDate = PayCycleEngine.todayIso()
        )
        safeLaunch("payRecurringBill") {
            repository.upsertItemAndTransactionAtomically(updatedBill, tx)
        }
    }

    fun contributeToGoalOrSeasonal(item: FinancialItem, addAmount: Double) {
        if (addAmount <= 0.0 || addAmount.isNaN()) return
        val updatedItem = item.copy(secondaryAmount = item.secondaryAmount + addAmount)
        val tx = TransactionItem(
            id = "tx_contrib_${item.id}_${System.currentTimeMillis()}",
            userId = currentUserId,
            type = "expense",
            amount = addAmount,
            category = "Épargne & Investissement",
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
        val tx = TransactionItem(
            id = "tx_daret_${daret.id}_round_$nextPaid",
            userId = currentUserId,
            type = "expense",
            amount = daret.amount.coerceAtLeast(1.0),
            category = "Épargne & Investissement",
            envelopeKey = "epargne",
            accountId = "acc_bank",
            desc = "Cotisation ${daret.name} ($nextPaid/$totalMembers)",
            isoDate = PayCycleEngine.todayIso()
        )
        safeLaunch("payDaretRound") {
            repository.upsertItemAndTransactionAtomically(updatedDaret, tx)
        }
    }

    fun settleDebt(debt: FinancialItem, payment: Double) {
        if (payment <= 0.0 || payment.isNaN()) return
        val newPaid = (debt.secondaryAmount + payment).coerceAtMost(debt.amount)
        val isLent = debt.dateOrCycle != "borrowed"
        val updatedDebt = debt.copy(secondaryAmount = newPaid)
        val tx = TransactionItem(
            id = "tx_debt_${debt.id}_${System.currentTimeMillis()}",
            userId = currentUserId,
            type = if (isLent) "income" else "expense",
            amount = payment,
            category = "Autre / Imprévus",
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

    private val _preRestoreRollbackJson = MutableStateFlow<String?>(null)
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
     * Two-phase backup restore:
     * Phase 1: Decrypts (if AES-256-GCM encrypted) and validates the entire backup payload in memory,
     *          and saves a pre-restore rollback snapshot of the current state.
     * Phase 2: Executes Firestore batch writes (and deletes stale records if [replaceExisting] is true),
     *          automatically rolling back if a mid-stream failure occurs.
     */
    fun restoreFromJson(
        rawJson: String,
        password: String = "",
        replaceExisting: Boolean = true,
        onResult: (Boolean, String) -> Unit
    ) {
        safeLaunch("restoreFromJson") {
            val currentState = uiState.value
            val rollbackSnapshot = BackupSerializer.encodeV2Backup(currentState)
            try {
                // Phase 1: Decrypt (if encrypted) & validate entire backup before any Firestore write
                val plainJson = if (AppSecurityManager.isEncryptedBackup(rawJson)) {
                    AppSecurityManager.decryptBackupJson(rawJson, password)
                } else {
                    rawJson
                }
                val parsed = BackupSerializer.decodeBackup(
                    rawJson = plainJson,
                    currentUserId = currentUserId,
                    fallbackProfile = currentState.profile
                )

                // Save rollback snapshot before mutating state
                _preRestoreRollbackJson.value = rollbackSnapshot

                if (parsed.profile != null) {
                    _optimisticProfile.value = parsed.profile
                }
                val customPrefsItem = if (parsed.customPrefs != null) {
                    _optimisticCustomPrefs.value = parsed.customPrefs
                    buildCustomPrefsFinancialItem(parsed.customPrefs)
                } else null

                val staleTxIds = if (replaceExisting) currentState.allTransactions.map { it.id } else emptyList()
                val staleItemIds = if (replaceExisting && parsed.backupVersion >= 2) {
                    (currentState.presets + currentState.goals + currentState.bills +
                        currentState.debts + currentState.darets + currentState.seasonals).map { it.id }
                } else emptyList()
                val staleAccIds = if (replaceExisting && parsed.accounts.isNotEmpty()) {
                    currentState.accounts.map { it.account.id }
                } else emptyList()
                val staleEnvIds = if (replaceExisting && parsed.envelopes.isNotEmpty()) {
                    currentState.envelopes.map { it.config.id }
                } else emptyList()

                var finalChunks = 1
                // Phase 2: Execute bounded batches with stale record cleanup
                repository.restoreFullBackupAtomically(
                    profile = parsed.profile,
                    customPrefsItem = customPrefsItem,
                    accounts = parsed.accounts,
                    envelopes = parsed.envelopes,
                    financialItems = parsed.financialItems,
                    transactions = parsed.transactions,
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
                Log.e("MasroufiVM", "Restore JSON failed", e)
                val msg = e.localizedMessage ?: "Format invalide ou mot de passe erroné."
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
            onResult = { ok, _ ->
                if (ok) _preRestoreRollbackJson.value = null
                onResult(ok)
            }
        )
    }
}
