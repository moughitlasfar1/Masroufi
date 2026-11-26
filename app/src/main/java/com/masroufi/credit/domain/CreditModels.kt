package com.masroufi.credit.domain

import com.masroufi.core.Money

enum class CreditType(
    val label: String,
    val defaultRateHt: Double,
    val maxMonths: Int,
    val extendedMaxMonths: Int,
    val maxDeferredMonths: Int,
    val standardCeiling: Money?
) {
    CONSOMMATION(
        label = "Consommation",
        defaultRateHt = 7.5,
        maxMonths = 84,
        extendedMaxMonths = 120,
        maxDeferredMonths = 3,
        standardCeiling = Money.fromDh(500_000)
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
    val loanAmount: Money = Money.fromDh(100_000),
    val downPayment: Money = Money.ZERO,
    val durationMonths: Int = 60,
    val deferredMonths: Int = 0,
    val annualInterestRateHt: Double = CreditType.CONSOMMATION.defaultRateHt,
    val applyInterestTva: Boolean = true,
    val monthlyNetIncome: Money = Money.fromDh(10_000),
    val existingMonthlyDebt: Money = Money.ZERO,
    val hasExistingMortgage: Boolean = false,
    val isEligibleFor120Months: Boolean = false,
    val borrowerAge: Int = 30,
    val insuranceRate: Double = 0.0
)

data class AmortizationRow(
    val month: Int,
    val isDeferred: Boolean,
    val payment: Money,
    val principal: Money,
    val interestHt: Money,
    val tvaOnInterest: Money,
    val insurance: Money,
    val remainingBalance: Money
)

data class CreditResult(
    val financedPrincipal: Money,
    val monthlyPayment: Money,
    val deferredMonthlyPayment: Money,
    val totalInterestHt: Money,
    val totalTva: Money,
    val totalInsurance: Money,
    val totalCost: Money,
    val totalRepayment: Money,
    val debtToIncomeRatio: Double,
    val maxAllowedDti: Double,
    val isOverIndebted: Boolean,
    val ageAtLastPayment: Double,
    val isAgeLimitExceeded: Boolean,
    val requiresMandatoryAdi: Boolean,
    val warnings: List<String>,
    val amortizationSchedule: List<AmortizationRow>
)
