package com.masroufi.credit

import com.masroufi.credit.data.SavedCreditEntity
import com.masroufi.credit.domain.CreditCalculator
import com.masroufi.credit.domain.CreditInput
import com.masroufi.credit.domain.CreditType
import com.masroufi.credit.presentation.CreditSimulatorViewModel
import com.masroufi.credit.presentation.CreditValidationKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreditCalculatorTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun resolveMaxDti_withoutMortgage_returns45Or50() {
        val lowIncome = CreditInput(creditType = CreditType.CONSOMMATION, monthlyNetIncome = 15_000.0)
        val highIncome = CreditInput(creditType = CreditType.AUTO, monthlyNetIncome = 15_001.0)

        assertEquals(45.0, CreditCalculator.resolveMaxDti(lowIncome), 0.001)
        assertEquals(50.0, CreditCalculator.resolveMaxDti(highIncome), 0.001)
    }

    @Test
    fun resolveMaxDti_withMortgage_returns50Or55Or60() {
        val standard = CreditInput(creditType = CreditType.IMMOBILIER, monthlyNetIncome = 20_000.0)
        val midTier = CreditInput(creditType = CreditType.IMMOBILIER, monthlyNetIncome = 35_000.0)
        val topTier = CreditInput(creditType = CreditType.IMMOBILIER, monthlyNetIncome = 65_000.0)
        val consoWithExistingMortgage = CreditInput(
            creditType = CreditType.CONSOMMATION,
            monthlyNetIncome = 40_000.0,
            hasExistingMortgage = true
        )

        assertEquals(50.0, CreditCalculator.resolveMaxDti(standard), 0.001)
        assertEquals(55.0, CreditCalculator.resolveMaxDti(midTier), 0.001)
        assertEquals(60.0, CreditCalculator.resolveMaxDti(topTier), 0.001)
        assertEquals(55.0, CreditCalculator.resolveMaxDti(consoWithExistingMortgage), 0.001)
    }

    @Test
    fun calculate_zeroPercentInterest_noDivisionByZeroAndExactSchedule() {
        val input = CreditInput(
            creditType = CreditType.AUTO,
            loanAmount = 120_000.0,
            downPayment = 20_000.0,
            durationMonths = 50,
            annualInterestRateHt = 0.0,
            applyInterestTva = true,
            monthlyNetIncome = 12_000.0,
            insuranceRate = 0.0
        )

        val result = CreditCalculator.calculate(input)
        assertEquals(100_000.0, result.financedPrincipal, 0.01)
        assertEquals(2_000.0, result.monthlyPayment, 0.01)
        assertEquals(0.0, result.totalInterestHt, 0.01)
        assertEquals(0.0, result.totalTva, 0.01)
        assertEquals(100_000.0, result.totalRepayment, 0.01)
        assertEquals(50, result.amortizationSchedule.size)
        assertEquals(0.0, result.amortizationSchedule.last().remainingBalance, 0.01)
    }

    @Test
    fun calculate_htPlus10PercentTva_splitsInterestAndTvaAccurately() {
        val baseInput = CreditInput(
            creditType = CreditType.CONSOMMATION,
            loanAmount = 100_000.0,
            downPayment = 0.0,
            durationMonths = 48,
            annualInterestRateHt = 7.5,
            applyInterestTva = true,
            monthlyNetIncome = 15_000.0,
            insuranceRate = 0.5
        )
        val noTvaResult = CreditCalculator.calculate(baseInput.copy(applyInterestTva = false))
        val withTvaResult = CreditCalculator.calculate(baseInput)

        assertTrue(withTvaResult.monthlyPayment > noTvaResult.monthlyPayment)
        assertEquals(0.0, noTvaResult.totalTva, 0.01)
        assertTrue(withTvaResult.totalTva > 0.0)
        val tvaRatio = withTvaResult.totalTva / withTvaResult.totalInterestHt
        assertEquals(0.10, tvaRatio, 0.005)
        assertEquals(
            withTvaResult.totalInterestHt + withTvaResult.totalTva + withTvaResult.totalInsurance,
            withTvaResult.totalCost,
            0.05
        )
        assertEquals(0.0, withTvaResult.amortizationSchedule.last().remainingBalance, 0.01)
    }

    @Test
    fun calculate_withDeferredMonths_paysInterestAndInsuranceOnlyThenAmortizes() {
        val input = CreditInput(
            creditType = CreditType.IMMOBILIER,
            loanAmount = 500_000.0,
            downPayment = 100_000.0,
            durationMonths = 120,
            deferredMonths = 3,
            annualInterestRateHt = 4.5,
            applyInterestTva = true,
            monthlyNetIncome = 25_000.0,
            insuranceRate = 0.4
        )
        val result = CreditCalculator.calculate(input)

        assertEquals(120, result.amortizationSchedule.size)
        assertTrue(result.deferredMonthlyPayment > 0.0)
        assertTrue(result.deferredMonthlyPayment < result.monthlyPayment)
        for (i in 0 until 3) {
            val row = result.amortizationSchedule[i]
            assertTrue(row.isDeferred)
            assertEquals(0.0, row.principal, 0.01)
            assertEquals(400_000.0, row.remainingBalance, 0.01)
        }
        assertFalse(result.amortizationSchedule[3].isDeferred)
        assertTrue(result.amortizationSchedule[3].principal > 0.0)
        assertEquals(0.0, result.amortizationSchedule.last().remainingBalance, 0.01)
    }

    @Test
    fun calculate_triggersBcpRegulatoryWarningsCorrectlyAcrossAll4Languages() {
        val input = CreditInput(
            creditType = CreditType.CONSOMMATION,
            loanAmount = 550_000.0,
            downPayment = 0.0,
            durationMonths = 84,
            annualInterestRateHt = 7.5,
            applyInterestTva = true,
            monthlyNetIncome = 2_800.0, // Below SMIG (3 045 MAD) and over-indebted
            existingMonthlyDebt = 500.0,
            borrowerAge = 66, // 66 + 7 = 73 > 70
            insuranceRate = 0.0 // Mandatory ADI > 10 000 MAD
        )

        val resultFr = CreditCalculator.calculate(input, language = "fr")
        assertTrue(resultFr.isOverIndebted)
        assertTrue(resultFr.isAgeLimitExceeded)
        assertTrue(resultFr.requiresMandatoryAdi)
        assertEquals(5, resultFr.warnings.size)

        assertEquals(5, CreditCalculator.calculate(input, language = "ar").warnings.size)
        assertEquals(5, CreditCalculator.calculate(input, language = "darija").warnings.size)
        assertEquals(5, CreditCalculator.calculate(input, language = "en").warnings.size)
    }

    @Test
    fun formatMad_usesFrenchMoroccanGroupingAndCommaDecimal() {
        assertEquals("12 500,00 MAD", CreditCalculator.formatMad(12_500.0))
        assertEquals("0,00 MAD", CreditCalculator.formatMad(0.0))
        assertEquals("1 250 000,50 MAD", CreditCalculator.formatMad(1_250_000.5))
    }

    @Test
    fun viewModel_presetSwitchingClampingPrefillAndSavedReload() {
        val vm = CreditSimulatorViewModel(repository = null, initialLanguage = "fr")

        // Masroufi salary pre-fill
        vm.applyMasroufiIncomePrefill(18_000.0)
        assertEquals(18_000.0, vm.uiState.value.input.monthlyNetIncome, 0.01)

        // Enable 120 months on Consommation, set duration to 110, then switch to AUTO -> clamped to 60
        vm.onEligibleFor120MonthsChanged(true)
        assertEquals(120, vm.uiState.value.maxDurationMonths)
        vm.onDurationMonthsChanged(110)
        assertEquals(110, vm.uiState.value.input.durationMonths)

        vm.onCreditTypeSelected(CreditType.AUTO)
        assertEquals(60, vm.uiState.value.maxDurationMonths)
        assertEquals(60, vm.uiState.value.input.durationMonths)
        assertEquals(CreditType.AUTO.defaultRateHt, vm.uiState.value.input.annualInterestRateHt, 0.001)
        assertFalse(vm.uiState.value.input.isEligibleFor120Months)

        // Validation error when downPayment exceeds loanAmount
        vm.onDownPaymentChanged(vm.uiState.value.input.loanAmount + 1_000.0)
        assertTrue(vm.uiState.value.validationErrors.containsKey(CreditValidationKeys.DOWN_PAYMENT))

        // Reloading a saved Immobilier credit restores parameters cleanly
        vm.loadSavedCredit(
            SavedCreditEntity(
                id = "c1",
                userId = "user_1",
                type = CreditType.IMMOBILIER.name,
                loanAmount = 700_000.0,
                downPayment = 100_000.0,
                financedPrincipal = 600_000.0,
                durationMonths = 240,
                deferredMonths = 3,
                annualInterestRateHt = 4.5,
                applyInterestTva = true,
                insuranceRate = 0.4,
                monthlyPayment = 4_200.0
            )
        )
        assertEquals(CreditType.IMMOBILIER, vm.uiState.value.input.creditType)
        assertEquals(240, vm.uiState.value.input.durationMonths)
        assertEquals(3, vm.uiState.value.input.deferredMonths)
        assertTrue(vm.uiState.value.validationErrors.isEmpty())
    }
}
