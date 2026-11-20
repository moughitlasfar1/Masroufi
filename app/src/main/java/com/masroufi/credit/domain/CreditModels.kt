package com.masroufi.credit.domain

enum class CreditType(
    val label: String,
    val defaultRateHt: Double,
    val maxMonths: Int,
    val extendedMaxMonths: Int,
    val maxDeferredMonths: Int,
    val standardCeiling: Double?
) {
    CONSOMMATION(
        label = "Consommation",
        defaultRateHt = 7.5,
        maxMonths = 84,
        extendedMaxMonths = 120,
        maxDeferredMonths = 3,
        standardCeiling = 500_000.0
    ),
    AUTO(
        label = "Auto (Neuve)",
        defaultRateHt = 6.0,
        maxMonths = 60,
        extendedMaxMonths = 60,
        maxDeferredMonths = 3,
        standardCeiling = null
    ),
    IMMOBILIER(
        label = "Immobilier",
        defaultRateHt = 4.5,
        maxMonths = 300,
        extendedMaxMonths = 300,
        maxDeferredMonths = 3,
        standardCeiling = null
    );

    fun maxDurationFor(isEligibleFor120Months: Boolean): Int {
        return if (this == CONSOMMATION && isEligibleFor120Months) extendedMaxMonths else maxMonths
    }
}

data class CreditInput(
    val creditType: CreditType = CreditType.CONSOMMATION,
    val loanAmount: Double = 100_000.0,
    val downPayment: Double = 0.0,
    val durationMonths: Int = 60,
    val deferredMonths: Int = 0,
    val annualInterestRateHt: Double = CreditType.CONSOMMATION.defaultRateHt,
    val applyInterestTva: Boolean = true,
    val monthlyNetIncome: Double = 10_000.0,
    val existingMonthlyDebt: Double = 0.0,
    val hasExistingMortgage: Boolean = false,
    val isEligibleFor120Months: Boolean = false,
    val borrowerAge: Int = 30,
    val insuranceRate: Double = 0.0
)

data class AmortizationRow(
    val month: Int,
    val isDeferred: Boolean,
    val payment: Double,
    val principal: Double,
    val interestHt: Double,
    val tvaOnInterest: Double,
    val insurance: Double,
    val remainingBalance: Double
)

data class CreditResult(
    val financedPrincipal: Double,
    val monthlyPayment: Double,
    val deferredMonthlyPayment: Double,
    val totalInterestHt: Double,
    val totalTva: Double,
    val totalInsurance: Double,
    val totalCost: Double,
    val totalRepayment: Double,
    val debtToIncomeRatio: Double,
    val maxAllowedDti: Double,
    val isOverIndebted: Boolean,
    val ageAtLastPayment: Double,
    val isAgeLimitExceeded: Boolean,
    val requiresMandatoryAdi: Boolean,
    val warnings: List<String>,
    val amortizationSchedule: List<AmortizationRow>
)
