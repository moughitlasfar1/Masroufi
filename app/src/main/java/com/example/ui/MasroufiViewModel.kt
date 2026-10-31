package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountItem
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.MasroufiRepository
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.domain.AccountWithBalance
import com.example.domain.EnvelopeStatus
import com.example.domain.PayCycleEngine
import com.example.domain.PayCycleWindow
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt
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

data class DashboardState(
    val isLoading: Boolean = true,
    val profile: UserProfile = UserProfile(),
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

    init {
        viewModelScope.launch {
            try {
                repository.ensureSeeded()
            } catch (e: Exception) {
                Log.w("MasroufiVM", "Seed check failed", e)
            }
        }
    }

    private val profileFlow = repository.observeProfile(currentUserId)
        .catch { e ->
            Log.w("MasroufiVM", "Error observing profile", e)
            emit(UserProfile(userId = currentUserId))
        }

    private val transactionsFlow = repository.observeTransactions(currentUserId)
        .catch { e ->
            Log.w("MasroufiVM", "Error observing transactions", e)
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
        combine(profileFlow, transactionsFlow, accountsFlow) { p, t, a -> Triple(p, t, a) },
        combine(envelopesFlow, itemsFlow, _selectedCycleKey) { e, i, c -> Triple(e, i, c) },
        combine(_filterState, _whatIfState) { f, w -> Pair(f, w) }
    ) { (profileOpt, txs, accs), (envs, finItems, selCycle), (filter, whatIf) ->
        val profile = profileOpt ?: UserProfile(userId = currentUserId)
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
        val unpaidBills = bills.filter { !PayCycleEngine.isBillPaidInCycle(it, activeCycleKey) }
            .sumOf { it.amount }

        val safePool = max(0.0, remaining - unpaidBills)
        val safeToday = if (window.daysRemaining > 0) safePool / window.daysRemaining else safePool
        val dailyAvg = cycleExpenses / max(1, window.daysElapsed)
        val projectedSpend = if (window.isCurrentCycle) dailyAvg * window.totalDays else cycleExpenses

        // Weekly calculations (Monday - Sunday of anchor date)
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
            goals = finItems.filter { it.itemType == "goal" },
            bills = bills,
            debts = finItems.filter { it.itemType == "debt" },
            darets = finItems.filter { it.itemType == "daret" },
            seasonals = finItems.filter { it.itemType == "seasonal" },
            filterState = filter,
            whatIfState = whatIf
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
        val parts = anchorIso.split("-").mapNotNull { it.toIntOrNull() }
        val cal = Calendar.getInstance()
        if (parts.size == 3) {
            cal.set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
        }
        cal.set(Calendar.MILLISECOND, 0)
        val dow = cal.get(Calendar.DAY_OF_WEEK) // Sun=1 .. Sat=7
        val offsetFromMonday = (dow + 5) % 7
        cal.add(Calendar.DAY_OF_MONTH, -offsetFromMonday)
        val mondayEpochDay = cal.timeInMillis / 86_400_000L
        val monStr = "%02d/%02d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
        cal.add(Calendar.DAY_OF_MONTH, 6)
        val sunStr = "%02d/%02d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)

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
        return Triple(curWeek.toList(), prevWeek.toList(), "$monStr → $sunStr")
    }

    fun shiftCycle(delta: Int) {
        val cur = uiState.value.activeWindow.cycleKey
        _selectedCycleKey.value = PayCycleEngine.shiftCycleKey(cur, delta)
    }

    fun updateLanguage(lang: String) {
        val cur = uiState.value.profile
        viewModelScope.launch {
            repository.saveProfile(cur.copy(language = lang))
        }
    }

    fun toggleTheme() {
        val cur = uiState.value.profile
        viewModelScope.launch {
            repository.saveProfile(cur.copy(isDarkTheme = !cur.isDarkTheme))
        }
    }

    fun saveBudgetSettings(baseBudget: Double, payCycleDay: Int, rolloverEnabled: Boolean) {
        val cur = uiState.value.profile
        viewModelScope.launch {
            repository.saveProfile(
                cur.copy(
                    baseBudget = baseBudget.coerceAtLeast(100.0),
                    payCycleDay = payCycleDay.coerceIn(1, 28),
                    rolloverEnabled = rolloverEnabled
                )
            )
            _selectedCycleKey.value = null
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
        if (amount <= 0.0) return
        val existing = uiState.value.allTransactions.find { it.id == existingId }
        val resolvedEnvKey = envelopeKey.ifBlank {
            PayCycleEngine.resolveEnvelopeKey(category, desc, uiState.value.envelopes.map { it.config })
        }
        viewModelScope.launch {
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
                    desc = desc.ifBlank { category },
                    isoDate = isoDate.ifBlank { PayCycleEngine.todayIso() },
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
            amount = preset.amount,
            category = catName,
            envelopeKey = preset.subtitle.ifBlank { "cafe" },
            accountId = preset.dateOrCycle.ifBlank { "acc_cash" },
            toAccountId = "",
            desc = preset.name,
            isoDate = PayCycleEngine.todayIso()
        )
        viewModelScope.launch {
            repository.upsertTransaction(tx)
            _lastDeletedTx.value = tx
            onAdded(tx)
        }
    }

    fun deleteTransactionWithUndo(tx: TransactionItem) {
        _lastDeletedTx.value = tx
        viewModelScope.launch {
            repository.deleteTransaction(tx.id)
        }
    }

    fun undoDeleteTransaction() {
        val tx = _lastDeletedTx.value ?: return
        _lastDeletedTx.value = null
        viewModelScope.launch {
            repository.upsertTransaction(tx.copy(createdAt = null, updatedAt = null))
        }
    }

    fun saveAccount(id: String = "", name: String, kind: String, initialBalance: Double, colorHex: String) {
        val existing = uiState.value.accounts.find { it.account.id == id }?.account
        viewModelScope.launch {
            repository.upsertAccount(
                AccountItem(
                    id = id,
                    userId = currentUserId,
                    name = name,
                    kind = kind,
                    initialBalance = initialBalance,
                    colorHex = colorHex,
                    createdAt = existing?.createdAt
                )
            )
        }
    }

    fun saveEnvelopeConfig(config: EnvelopeConfig) {
        viewModelScope.launch {
            repository.upsertEnvelope(config)
        }
    }

    fun saveFinancialItem(item: FinancialItem) {
        viewModelScope.launch {
            repository.upsertFinancialItem(item)
        }
    }

    fun deleteFinancialItem(id: String) {
        viewModelScope.launch {
            repository.deleteFinancialItem(id)
        }
    }

    fun payRecurringBill(bill: FinancialItem) {
        val cycleKey = uiState.value.activeWindow.cycleKey
        if (PayCycleEngine.isBillPaidInCycle(bill, cycleKey)) return
        val updatedCycles = (bill.dateOrCycle.split(",").filter { it.isNotBlank() } + cycleKey).distinct().joinToString(",")
        viewModelScope.launch {
            repository.upsertFinancialItem(bill.copy(dateOrCycle = updatedCycles))
            repository.upsertTransaction(
                TransactionItem(
                    userId = currentUserId,
                    type = "expense",
                    amount = bill.amount,
                    category = "Charges fixes & Maison",
                    envelopeKey = "charges",
                    accountId = "acc_bank",
                    desc = bill.name,
                    isoDate = PayCycleEngine.todayIso()
                )
            )
        }
    }

    fun contributeToGoalOrSeasonal(item: FinancialItem, addAmount: Double) {
        if (addAmount <= 0.0) return
        viewModelScope.launch {
            repository.upsertFinancialItem(item.copy(secondaryAmount = item.secondaryAmount + addAmount))
            repository.upsertTransaction(
                TransactionItem(
                    userId = currentUserId,
                    type = "expense",
                    amount = addAmount,
                    category = "Épargne & Investissement",
                    envelopeKey = "epargne",
                    accountId = "acc_bank",
                    desc = item.name,
                    isoDate = PayCycleEngine.todayIso()
                )
            )
        }
    }

    fun payDaretRound(daret: FinancialItem) {
        val totalMembers = daret.secondaryAmount.roundToInt().coerceAtLeast(1)
        val nextPaid = (daret.extraInt + 1).coerceAtMost(totalMembers)
        viewModelScope.launch {
            repository.upsertFinancialItem(daret.copy(extraInt = nextPaid))
            repository.upsertTransaction(
                TransactionItem(
                    userId = currentUserId,
                    type = "expense",
                    amount = daret.amount,
                    category = "Épargne & Investissement",
                    envelopeKey = "epargne",
                    accountId = "acc_bank",
                    desc = "Cotisation ${daret.name} ($nextPaid/$totalMembers)",
                    isoDate = PayCycleEngine.todayIso()
                )
            )
        }
    }

    fun settleDebt(debt: FinancialItem, payment: Double) {
        if (payment <= 0.0) return
        val newPaid = (debt.secondaryAmount + payment).coerceAtMost(debt.amount)
        val isLent = debt.dateOrCycle != "borrowed"
        viewModelScope.launch {
            repository.upsertFinancialItem(debt.copy(secondaryAmount = newPaid))
            repository.upsertTransaction(
                TransactionItem(
                    userId = currentUserId,
                    type = if (isLent) "income" else "expense",
                    amount = payment,
                    category = "Autre / Imprévus",
                    envelopeKey = "autre",
                    accountId = "acc_cash",
                    desc = "Remboursement: ${debt.name}",
                    isoDate = PayCycleEngine.todayIso()
                )
            )
        }
    }

    fun updateFilter(update: (FilterState) -> FilterState) {
        _filterState.value = update(_filterState.value)
    }

    fun updateWhatIf(envelopeKey: String, cutPct: Int) {
        _whatIfState.value = WhatIfSimulatorState(envelopeKey, cutPct.coerceIn(5, 80))
    }

    fun exportBackupJson(): String {
        val s = uiState.value
        val root = JSONObject().apply {
            put("baseBudget", s.profile.baseBudget)
            put("payCycleDay", s.profile.payCycleDay)
            put("rolloverEnabled", s.profile.rolloverEnabled)
            val txArr = JSONArray()
            s.allTransactions.forEach { t ->
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

    fun exportCsv(): String {
        val sb = StringBuilder("ID,Type,Montant,Categorie,Compte,Description,Date\n")
        uiState.value.allTransactions.forEach { t ->
            val cleanDesc = t.desc.replace("\"", "\"\"")
            val cleanCat = t.category.replace("\"", "\"\"")
            sb.append("\"${t.id}\",\"${t.type}\",\"${t.amount}\",\"$cleanCat\",\"${t.accountId}\",\"$cleanDesc\",\"${t.isoDate}\"\n")
        }
        return sb.toString()
    }

    fun restoreFromJson(rawJson: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val trimmed = rawJson.trim()
                val txArray = if (trimmed.startsWith("[")) {
                    JSONArray(trimmed)
                } else {
                    val obj = JSONObject(trimmed)
                    val b = obj.optDouble("baseBudget", uiState.value.profile.baseBudget)
                    val pDay = obj.optInt("payCycleDay", uiState.value.profile.payCycleDay)
                    saveBudgetSettings(b, pDay, obj.optBoolean("rolloverEnabled", true))
                    obj.optJSONArray("transactions") ?: JSONArray()
                }
                for (i in 0 until txArray.length()) {
                    val item = txArray.getJSONObject(i)
                    val amt = item.optDouble("amount", item.optDouble("montant", 0.0))
                    if (amt > 0.0) {
                        val cat = item.optString("category", "Autre / Imprévus")
                        val desc = item.optString("desc", "Transaction")
                        val iso = item.optString("iso", PayCycleEngine.todayIso())
                        repository.upsertTransaction(
                            TransactionItem(
                                id = item.optString("id", "tx_${System.currentTimeMillis()}_$i"),
                                userId = currentUserId,
                                type = item.optString("type", "expense"),
                                amount = amt,
                                category = cat,
                                envelopeKey = item.optString("envelopeKey", PayCycleEngine.resolveEnvelopeKey(cat, desc, emptyList())),
                                accountId = item.optString("accountId", "acc_cash"),
                                toAccountId = item.optString("toAccountId", ""),
                                desc = desc,
                                isoDate = iso
                            )
                        )
                    }
                }
                onResult(true)
            } catch (e: Exception) {
                Log.e("MasroufiVM", "Restore JSON failed", e)
                onResult(false)
            }
        }
    }
}
