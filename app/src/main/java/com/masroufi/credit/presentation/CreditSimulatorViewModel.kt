package com.masroufi.credit.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.credit.data.CreditDataSource
import com.masroufi.credit.data.SavedCreditEntity
import com.masroufi.credit.domain.CreditCalculator
import com.masroufi.credit.domain.CreditInput
import com.masroufi.credit.domain.CreditResult
import com.masroufi.credit.domain.CreditStrings
import com.masroufi.credit.domain.CreditType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreditUiState(
    val input: CreditInput = CreditInput(),
    val result: CreditResult = CreditCalculator.calculate(CreditInput()),
    val maxDurationMonths: Int = CreditType.CONSOMMATION.maxMonths,
    val validationErrors: Map<String, String> = emptyMap(),
    val isScheduleExpanded: Boolean = false,
    val isAdvancedProfileExpanded: Boolean = false,
    val language: String = "fr",
    val savedCredits: List<SavedCreditEntity> = emptyList(),
    val isSaving: Boolean = false,
    val isLoadingCredits: Boolean = false,
    val savedCreditsError: String? = null,
    val snackbarMessage: String? = null
)

object CreditValidationKeys {
    const val LOAN_AMOUNT = "loanAmount"
    const val DOWN_PAYMENT = "downPayment"
    const val DURATION = "durationMonths"
    const val DEFERRED = "deferredMonths"
    const val INTEREST_RATE = "annualInterestRateHt"
    const val MONTHLY_INCOME = "monthlyNetIncome"
    const val BORROWER_AGE = "borrowerAge"
    const val INSURANCE_RATE = "insuranceRate"
}

class CreditSimulatorViewModel(
    private val repository: CreditDataSource? = null,
    initialLanguage: String = "fr"
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        buildUiState(
            input = CreditInput(),
            language = initialLanguage,
            current = null
        )
    )
    val uiState: StateFlow<CreditUiState> = _uiState.asStateFlow()

    private var hasAppliedIncomePrefill = false
    private var hasAppliedSavedDebtPrefill = false
    private var userEditedExistingDebt = false
    private var userEditedExistingMortgage = false
    private var savedCreditsJob: Job? = null

    init {
        if (repository != null) {
            startObservingSavedCredits()
        }
    }

    fun startObservingSavedCredits() {
        val repo = repository ?: return
        savedCreditsJob?.cancel()
        _uiState.update { it.copy(isLoadingCredits = true, savedCreditsError = null) }
        savedCreditsJob = viewModelScope.launch {
            repo.observeSavedCredits()
                .catch { error ->
                    val rootMsg = (error.cause ?: error).localizedMessage
                        ?: error.localizedMessage
                        ?: "Erreur de chargement des crédits"
                    _uiState.update {
                        it.copy(
                            isLoadingCredits = false,
                            savedCreditsError = rootMsg
                        )
                    }
                }
                .collect { credits ->
                    _uiState.update { current ->
                        val shouldPrefillDebt = !hasAppliedSavedDebtPrefill && credits.isNotEmpty()
                        if (shouldPrefillDebt) {
                            hasAppliedSavedDebtPrefill = true
                        }
                        val nextInput = if (shouldPrefillDebt) {
                            val totalSavedMonthly = credits.sumOf { it.monthlyPayment }
                            val anyMortgage = credits.any { it.type == CreditType.IMMOBILIER.name }
                            current.input.copy(
                                existingMonthlyDebt = if (!userEditedExistingDebt) totalSavedMonthly else current.input.existingMonthlyDebt,
                                hasExistingMortgage = if (!userEditedExistingMortgage) (current.input.hasExistingMortgage || anyMortgage) else current.input.hasExistingMortgage
                            )
                        } else {
                            current.input
                        }
                        buildUiState(
                            input = nextInput,
                            language = current.language,
                            current = current.copy(
                                savedCredits = credits,
                                isLoadingCredits = false,
                                savedCreditsError = null
                            )
                        )
                    }
                }
        }
    }

    fun setLanguage(language: String) {
        if (_uiState.value.language == language) return
        _uiState.update { current ->
            buildUiState(
                input = current.input,
                language = language,
                current = current
            )
        }
    }

    fun applyMasroufiIncomePrefill(monthlySalaryMad: Double) {
        if (hasAppliedIncomePrefill || monthlySalaryMad <= 0.0) return
        hasAppliedIncomePrefill = true
        _uiState.update { current ->
            buildUiState(
                input = current.input.copy(monthlyNetIncome = monthlySalaryMad),
                language = current.language,
                current = current
            )
        }
    }

    fun onCreditTypeSelected(type: CreditType) {
        _uiState.update { current ->
            val nextEligible120 = if (type == CreditType.CONSOMMATION) current.input.isEligibleFor120Months else false
            val nextMaxDuration = type.maxDurationFor(nextEligible120)
            val clampedDuration = current.input.durationMonths.coerceIn(1, nextMaxDuration)
            val maxDef = minOf(type.maxDeferredMonths, (clampedDuration - 1).coerceAtLeast(0))
            val clampedDeferred = current.input.deferredMonths.coerceIn(0, maxDef)
            val nextInput = current.input.copy(
                creditType = type,
                annualInterestRateHt = type.defaultRateHt,
                isEligibleFor120Months = nextEligible120,
                durationMonths = clampedDuration,
                deferredMonths = clampedDeferred
            )
            buildUiState(input = nextInput, language = current.language, current = current)
        }
    }

    fun onLoanAmountChanged(amount: Double) {
        updateInput { input ->
            val sanitized = if (amount.isFinite()) amount.coerceAtLeast(0.0) else 0.0
            input.copy(loanAmount = sanitized)
        }
    }

    fun onDownPaymentChanged(amount: Double) {
        updateInput { input ->
            val sanitized = if (amount.isFinite()) amount.coerceAtLeast(0.0) else 0.0
            input.copy(downPayment = sanitized)
        }
    }

    fun onDurationMonthsChanged(months: Int) {
        updateInput { input ->
            val maxDeferred = minOf(input.creditType.maxDeferredMonths, (months - 1).coerceAtLeast(0))
            val clampedDeferred = if (months > 0) input.deferredMonths.coerceIn(0, maxDeferred) else 0
            input.copy(
                durationMonths = months,
                deferredMonths = clampedDeferred
            )
        }
    }

    fun onDeferredMonthsChanged(months: Int) {
        updateInput { input ->
            input.copy(deferredMonths = months.coerceAtLeast(0))
        }
    }

    fun onInterestRateChanged(rate: Double) {
        updateInput { input ->
            val sanitized = if (rate.isFinite()) rate else 0.0
            input.copy(annualInterestRateHt = sanitized)
        }
    }

    fun onApplyTvaChanged(apply: Boolean) {
        updateInput { it.copy(applyInterestTva = apply) }
    }

    fun onMonthlyNetIncomeChanged(income: Double) {
        hasAppliedIncomePrefill = true
        updateInput { input ->
            val sanitized = if (income.isFinite()) income.coerceAtLeast(0.0) else 0.0
            input.copy(monthlyNetIncome = sanitized)
        }
    }

    fun onExistingMonthlyDebtChanged(debt: Double) {
        userEditedExistingDebt = true
        updateInput { input ->
            val sanitized = if (debt.isFinite()) debt.coerceAtLeast(0.0) else 0.0
            input.copy(existingMonthlyDebt = sanitized)
        }
    }

    fun onHasExistingMortgageChanged(hasMortgage: Boolean) {
        userEditedExistingMortgage = true
        updateInput { it.copy(hasExistingMortgage = hasMortgage) }
    }

    fun onEligibleFor120MonthsChanged(eligible: Boolean) {
        updateInput { input ->
            val nextEligible = if (input.creditType == CreditType.CONSOMMATION) eligible else false
            val maxMonths = input.creditType.maxDurationFor(nextEligible)
            val clampedDuration = input.durationMonths.coerceIn(1, maxMonths)
            val maxDef = minOf(input.creditType.maxDeferredMonths, (clampedDuration - 1).coerceAtLeast(0))
            input.copy(
                isEligibleFor120Months = nextEligible,
                durationMonths = clampedDuration,
                deferredMonths = input.deferredMonths.coerceIn(0, maxDef)
            )
        }
    }

    fun onBorrowerAgeChanged(age: Int) {
        updateInput { it.copy(borrowerAge = age) }
    }

    fun onInsuranceRateChanged(rate: Double) {
        updateInput { input ->
            val sanitized = if (rate.isFinite()) rate else 0.0
            input.copy(insuranceRate = sanitized)
        }
    }

    fun onToggleScheduleExpanded() {
        _uiState.update { it.copy(isScheduleExpanded = !it.isScheduleExpanded) }
    }

    fun onToggleAdvancedProfileExpanded() {
        _uiState.update { it.copy(isAdvancedProfileExpanded = !it.isAdvancedProfileExpanded) }
    }

    fun loadSavedCredit(entity: SavedCreditEntity) {
        val creditType = runCatching { CreditType.valueOf(entity.type) }.getOrDefault(CreditType.CONSOMMATION)
        val eligible120 = creditType == CreditType.CONSOMMATION && entity.durationMonths > creditType.maxMonths
        _uiState.update { current ->
            val strings = CreditStrings.forLang(current.language)
            val nextInput = current.input.copy(
                creditType = creditType,
                loanAmount = entity.loanAmount,
                downPayment = entity.downPayment,
                durationMonths = entity.durationMonths,
                deferredMonths = entity.deferredMonths,
                annualInterestRateHt = entity.annualInterestRateHt,
                applyInterestTva = entity.applyInterestTva,
                insuranceRate = entity.insuranceRate,
                isEligibleFor120Months = eligible120
            )
            buildUiState(
                input = nextInput,
                language = current.language,
                current = current.copy(snackbarMessage = strings.loadSuccessToast)
            )
        }
    }

    fun saveCurrentCredit(
        linkToMonthlyExpenses: Boolean,
        onSavedSuccess: (CreditInput, CreditResult, Boolean) -> Unit
    ) {
        val current = _uiState.value
        if (current.validationErrors.isNotEmpty() || current.isSaving) return
        val repo = repository ?: return
        val strings = CreditStrings.forLang(current.language)

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val saveRes = repo.saveCredit(
                input = current.input,
                result = current.result,
                linkToMonthlyExpenses = linkToMonthlyExpenses
            )
            saveRes.onSuccess {
                onSavedSuccess(current.input, current.result, linkToMonthlyExpenses)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        snackbarMessage = if (linkToMonthlyExpenses) {
                            strings.saveSuccessWithBillToast
                        } else {
                            strings.saveSuccessToast
                        }
                    )
                }
            }.onFailure { err ->
                val msg = (err.cause ?: err).localizedMessage ?: err.localizedMessage ?: "Erreur lors de l'enregistrement"
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        snackbarMessage = msg
                    )
                }
            }
        }
    }

    fun deleteSavedCredit(creditId: String) {
        val repo = repository ?: return
        val strings = CreditStrings.forLang(_uiState.value.language)
        viewModelScope.launch {
            repo.deleteCredit(creditId)
                .onSuccess {
                    _uiState.update { it.copy(snackbarMessage = strings.deleteSuccessToast) }
                }
                .onFailure { err ->
                    val msg = (err.cause ?: err).localizedMessage ?: err.localizedMessage ?: "Erreur lors de la suppression"
                    _uiState.update { it.copy(snackbarMessage = msg) }
                }
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun updateInput(transform: (CreditInput) -> CreditInput) {
        _uiState.update { current ->
            val nextInput = transform(current.input)
            buildUiState(input = nextInput, language = current.language, current = current)
        }
    }

    private fun buildUiState(
        input: CreditInput,
        language: String,
        current: CreditUiState?
    ): CreditUiState {
        val maxDuration = input.creditType.maxDurationFor(input.isEligibleFor120Months)
        val errors = validate(input, maxDuration, language)
        val result = CreditCalculator.calculate(input, language)
        return (current ?: CreditUiState()).copy(
            input = input,
            result = result,
            maxDurationMonths = maxDuration,
            validationErrors = errors,
            language = language
        )
    }

    private fun validate(
        input: CreditInput,
        maxDuration: Int,
        language: String
    ): Map<String, String> {
        val strings = CreditStrings.forLang(language)
        val errors = linkedMapOf<String, String>()
        if (!input.loanAmount.isFinite() || input.loanAmount <= 0.0 || input.loanAmount > 100_000_000.0) {
            errors[CreditValidationKeys.LOAN_AMOUNT] = strings.errorLoanAmountInvalid
        }
        if (!input.downPayment.isFinite() || input.downPayment < 0.0 || input.downPayment > input.loanAmount) {
            errors[CreditValidationKeys.DOWN_PAYMENT] = strings.errorDownPaymentBounds
        }
        if (input.durationMonths < 1 || input.durationMonths > maxDuration) {
            errors[CreditValidationKeys.DURATION] = strings.errorDurationBoundsFmt
                .replace("{max}", maxDuration.toString())
        }
        val maxDeferred = minOf(input.creditType.maxDeferredMonths, (input.durationMonths - 1).coerceAtLeast(0))
        if (input.deferredMonths < 0 || input.deferredMonths > maxDeferred) {
            errors[CreditValidationKeys.DEFERRED] = strings.errorDeferredBoundsFmt
                .replace("{max}", maxDeferred.toString())
        }
        if (!input.annualInterestRateHt.isFinite() || input.annualInterestRateHt < 0.0 || input.annualInterestRateHt > 50.0) {
            errors[CreditValidationKeys.INTEREST_RATE] = strings.errorRateInvalid
        }
        if (!input.monthlyNetIncome.isFinite() || input.monthlyNetIncome <= 0.0) {
            errors[CreditValidationKeys.MONTHLY_INCOME] = strings.errorIncomeInvalid
        }
        if (input.borrowerAge !in 18..69) {
            errors[CreditValidationKeys.BORROWER_AGE] = strings.errorAgeBounds
        }
        if (!input.insuranceRate.isFinite() || input.insuranceRate < 0.0 || input.insuranceRate > 20.0) {
            errors[CreditValidationKeys.INSURANCE_RATE] = strings.errorInsuranceInvalid
        }
        return errors
    }
}
