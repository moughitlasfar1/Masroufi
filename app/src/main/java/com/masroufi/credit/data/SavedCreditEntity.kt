package com.masroufi.credit.data

import androidx.annotation.Keep
import com.masroufi.core.Money

@Keep
data class SavedCreditEntity(
    val id: String = "",
    val userId: String = "",
    val type: String = "CONSOMMATION",
    val loanAmount: Money = Money.ZERO,
    val downPayment: Money = Money.ZERO,
    val financedPrincipal: Money = Money.ZERO,
    val durationMonths: Int = 60,
    val deferredMonths: Int = 0,
    val annualInterestRateHt: Double = 7.5,
    val applyInterestTva: Boolean = true,
    val insuranceRate: Double = 0.0,
    val monthlyPayment: Money = Money.ZERO,
    val totalCost: Money = Money.ZERO,
    val debtToIncomeRatio: Double = 0.0,
    val maxAllowedDti: Double = 45.0,
    val linkToMonthlyExpenses: Boolean = false,
    val createdAtEpochMs: Long = 0L
)
