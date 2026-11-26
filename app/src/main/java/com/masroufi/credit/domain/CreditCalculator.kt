package com.masroufi.credit.domain

import com.masroufi.core.Money
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object CreditCalculator {
    const val MOROCCAN_TVA_RATE = 0.10
    val MOROCCAN_SMIG_MAD: Money = Money.fromDh(3_045)
    val MANDATORY_ADI_THRESHOLD_MAD: Money = Money.fromDh(10_000)
    const val MAX_AGE_AT_FINAL_PAYMENT = 70.0

    private val MC: MathContext = MathContext.DECIMAL128
    private val HUNDRED_BD: BigDecimal = BigDecimal.valueOf(100L)
    private val TWELVE_BD: BigDecimal = BigDecimal.valueOf(12L)
    private val TVA_RATE_BD: BigDecimal = BigDecimal("0.10")
    private val ONE_PLUS_TVA_BD: BigDecimal = BigDecimal("1.10")

    fun resolveMaxDti(input: CreditInput): Double {
        return if (input.creditType == CreditType.IMMOBILIER || input.hasExistingMortgage) {
            when {
                input.monthlyNetIncome > Money.fromDh(60_000) -> 60.0
                input.monthlyNetIncome >= Money.fromDh(35_000) -> 55.0
                else -> 50.0
            }
        } else {
            if (input.monthlyNetIncome <= Money.fromDh(15_000)) 45.0 else 50.0
        }
    }

    fun calculate(input: CreditInput, language: String = "fr"): CreditResult {
        val strings = CreditStrings.forLang(language)
        val sanitizedLoan = input.loanAmount.coerceAtLeast(Money.ZERO)
        val sanitizedDown = input.downPayment.coerceIn(Money.ZERO, sanitizedLoan)
        val financedPrincipal = (sanitizedLoan - sanitizedDown).coerceAtLeast(Money.ZERO)
        val principalBd = financedPrincipal.toBigDecimalDh()

        val totalMonths = input.durationMonths.coerceAtLeast(1)
        val deferredMonths = input.deferredMonths.coerceIn(0, (totalMonths - 1).coerceAtLeast(0))
        val amortizedMonths = (totalMonths - deferredMonths).coerceAtLeast(1)

        val rateHt = input.annualInterestRateHt.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val monthlyRateHt = BigDecimal.valueOf(rateHt)
            .divide(HUNDRED_BD, MC)
            .divide(TWELVE_BD, MC)
        val tvaFactor = if (input.applyInterestTva) ONE_PLUS_TVA_BD else BigDecimal.ONE
        val effectiveMonthlyRate = monthlyRateHt.multiply(tvaFactor, MC)

        val insRate = input.insuranceRate.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val monthlyInsuranceBd = principalBd
            .multiply(BigDecimal.valueOf(insRate), MC)
            .divide(HUNDRED_BD, MC)
            .divide(TWELVE_BD, MC)
        val monthlyInsuranceMoney = Money.fromMajorUnits(monthlyInsuranceBd, RoundingMode.HALF_UP)

        val schedule = ArrayList<AmortizationRow>(totalMonths)
        var remainingBalanceBd = principalBd
        var totalInterestHtBd = BigDecimal.ZERO
        var totalTvaBd = BigDecimal.ZERO
        var totalInsuranceBd = BigDecimal.ZERO

        // 1. Optional partial grace period (différé partiel: interest TTC + ADI insurance only)
        val rawDeferredPaymentBd = if (deferredMonths > 0 && financedPrincipal.isPositive) {
            val defInterestHt = principalBd.multiply(monthlyRateHt, MC)
            val defTva = if (input.applyInterestTva) defInterestHt.multiply(TVA_RATE_BD, MC) else BigDecimal.ZERO
            defInterestHt.add(defTva, MC).add(monthlyInsuranceBd, MC)
        } else {
            BigDecimal.ZERO
        }

        for (month in 1..deferredMonths) {
            val interestHt = principalBd.multiply(monthlyRateHt, MC)
            val tva = if (input.applyInterestTva) interestHt.multiply(TVA_RATE_BD, MC) else BigDecimal.ZERO
            val payment = interestHt.add(tva, MC).add(monthlyInsuranceBd, MC)
            totalInterestHtBd = totalInterestHtBd.add(interestHt, MC)
            totalTvaBd = totalTvaBd.add(tva, MC)
            totalInsuranceBd = totalInsuranceBd.add(monthlyInsuranceBd, MC)
            schedule.add(
                AmortizationRow(
                    month = month,
                    isDeferred = true,
                    payment = Money.fromMajorUnits(payment, RoundingMode.HALF_UP),
                    principal = Money.ZERO,
                    interestHt = Money.fromMajorUnits(interestHt, RoundingMode.HALF_UP),
                    tvaOnInterest = Money.fromMajorUnits(tva, RoundingMode.HALF_UP),
                    insurance = monthlyInsuranceMoney,
                    remainingBalance = Money.fromMajorUnits(remainingBalanceBd, RoundingMode.HALF_UP)
                )
            )
        }

        // 2. Standard amortized installments over remaining months
        val amortizedMonthsBd = BigDecimal.valueOf(amortizedMonths.toLong())
        val standardAnnuityExclInsuranceBd = when {
            !financedPrincipal.isPositive -> BigDecimal.ZERO
            effectiveMonthlyRate.signum() == 0 -> principalBd.divide(amortizedMonthsBd, MC)
            else -> {
                val growth = BigDecimal.ONE.add(effectiveMonthlyRate, MC).pow(amortizedMonths, MC)
                val numerator = principalBd.multiply(effectiveMonthlyRate, MC).multiply(growth, MC)
                val denominator = growth.subtract(BigDecimal.ONE, MC)
                numerator.divide(denominator, MC)
            }
        }
        val rawMonthlyPaymentBd = if (!financedPrincipal.isPositive) {
            BigDecimal.ZERO
        } else {
            standardAnnuityExclInsuranceBd.add(monthlyInsuranceBd, MC)
        }

        for (i in 1..amortizedMonths) {
            val monthIndex = deferredMonths + i
            val isLastMonth = i == amortizedMonths
            val interestTtc = if (!financedPrincipal.isPositive) {
                BigDecimal.ZERO
            } else {
                remainingBalanceBd.multiply(effectiveMonthlyRate, MC)
            }
            val interestHt = if (input.applyInterestTva) {
                interestTtc.divide(ONE_PLUS_TVA_BD, MC)
            } else {
                interestTtc
            }
            val tva = interestTtc.subtract(interestHt, MC)

            val rawPrincipal = when {
                !financedPrincipal.isPositive -> BigDecimal.ZERO
                isLastMonth -> remainingBalanceBd
                effectiveMonthlyRate.signum() == 0 -> principalBd.divide(amortizedMonthsBd, MC)
                else -> standardAnnuityExclInsuranceBd.subtract(interestTtc, MC)
            }
            val principal = rawPrincipal.coerceInBd(BigDecimal.ZERO, remainingBalanceBd)

            remainingBalanceBd = if (isLastMonth) {
                BigDecimal.ZERO
            } else {
                remainingBalanceBd.subtract(principal, MC).max(BigDecimal.ZERO)
            }

            val rowPayment = principal.add(interestTtc, MC).add(monthlyInsuranceBd, MC)
            totalInterestHtBd = totalInterestHtBd.add(interestHt, MC)
            totalTvaBd = totalTvaBd.add(tva, MC)
            totalInsuranceBd = totalInsuranceBd.add(monthlyInsuranceBd, MC)

            schedule.add(
                AmortizationRow(
                    month = monthIndex,
                    isDeferred = false,
                    payment = Money.fromMajorUnits(rowPayment, RoundingMode.HALF_UP),
                    principal = Money.fromMajorUnits(principal, RoundingMode.HALF_UP),
                    interestHt = Money.fromMajorUnits(interestHt, RoundingMode.HALF_UP),
                    tvaOnInterest = Money.fromMajorUnits(tva, RoundingMode.HALF_UP),
                    insurance = monthlyInsuranceMoney,
                    remainingBalance = Money.fromMajorUnits(remainingBalanceBd, RoundingMode.HALF_UP)
                )
            )
        }

        val monthlyPayment = Money.fromMajorUnits(rawMonthlyPaymentBd, RoundingMode.HALF_UP)
        val deferredMonthlyPayment = Money.fromMajorUnits(rawDeferredPaymentBd, RoundingMode.HALF_UP)
        val roundedInterestHt = Money.fromMajorUnits(totalInterestHtBd, RoundingMode.HALF_UP)
        val roundedTva = Money.fromMajorUnits(totalTvaBd, RoundingMode.HALF_UP)
        val roundedInsurance = Money.fromMajorUnits(totalInsuranceBd, RoundingMode.HALF_UP)
        val totalCost = roundedInterestHt + roundedTva + roundedInsurance
        val totalRepayment = financedPrincipal + totalCost

        val income = input.monthlyNetIncome.coerceAtLeast(Money.ZERO)
        val existingDebt = input.existingMonthlyDebt.coerceAtLeast(Money.ZERO)
        val totalMonthlyCharge = monthlyPayment + existingDebt
        val dtiRatio = when {
            income.isPositive -> round2(
                totalMonthlyCharge.toBigDecimalDh()
                    .multiply(HUNDRED_BD, MC)
                    .divide(income.toBigDecimalDh(), MC)
                    .toDouble()
            )
            totalMonthlyCharge.isPositive -> 100.0
            else -> 0.0
        }
        val maxAllowedDti = resolveMaxDti(input)
        val isOverIndebted = dtiRatio > maxAllowedDti

        val ageAtLastPayment = round1(input.borrowerAge + totalMonths / 12.0)
        val isAgeLimitExceeded = ageAtLastPayment > MAX_AGE_AT_FINAL_PAYMENT
        val requiresMandatoryAdi = financedPrincipal > MANDATORY_ADI_THRESHOLD_MAD

        val warnings = buildList {
            if (isOverIndebted) {
                add(
                    strings.warnOverIndebtedFmt
                        .replace("{dti}", formatPercent(dtiRatio))
                        .replace("{cap}", formatPercent(maxAllowedDti))
                )
            }
            if (isAgeLimitExceeded) {
                add(
                    strings.warnAgeLimitFmt
                        .replace("{age}", formatDecimal1(ageAtLastPayment))
                )
            }
            val ceiling = input.creditType.standardCeiling
            if (ceiling != null && financedPrincipal > ceiling) {
                add(strings.warnConsoCeiling)
            }
            if (income.isPositive && income < MOROCCAN_SMIG_MAD) {
                add(strings.warnBelowSmig)
            }
            if (requiresMandatoryAdi && insRate <= 0.0) {
                add(strings.warnMandatoryAdi)
            }
        }

        return CreditResult(
            financedPrincipal = financedPrincipal,
            monthlyPayment = monthlyPayment,
            deferredMonthlyPayment = deferredMonthlyPayment,
            totalInterestHt = roundedInterestHt,
            totalTva = roundedTva,
            totalInsurance = roundedInsurance,
            totalCost = totalCost,
            totalRepayment = totalRepayment,
            debtToIncomeRatio = dtiRatio,
            maxAllowedDti = maxAllowedDti,
            isOverIndebted = isOverIndebted,
            ageAtLastPayment = ageAtLastPayment,
            isAgeLimitExceeded = isAgeLimitExceeded,
            requiresMandatoryAdi = requiresMandatoryAdi,
            warnings = warnings,
            amortizationSchedule = schedule
        )
    }

    /**
     * Formats monetary amounts using Moroccan/French grouping, e.g., `12 500,00 MAD`.
     */
    fun formatMad(amount: Money): String = amount.formatDhExact("MAD")

    fun formatMadCompact(amount: Money): String = amount.formatDhRounded("MAD")

    fun formatPercent(value: Double): String {
        val safe = if (value.isFinite()) value else 0.0
        return if (abs(safe - safe.roundToLong()) < 0.05) {
            safe.roundToLong().toString()
        } else {
            String.format(Locale.US, "%.1f", safe).replace('.', ',')
        }
    }

    fun formatDecimal1(value: Double): String {
        val safe = if (value.isFinite()) value else 0.0
        return if (abs(safe - safe.roundToLong()) < 0.05) {
            safe.roundToLong().toString()
        } else {
            String.format(Locale.US, "%.1f", safe).replace('.', ',')
        }
    }

    private fun BigDecimal.coerceInBd(min: BigDecimal, max: BigDecimal): BigDecimal {
        if (this < min) return min
        if (this > max) return max
        return this
    }

    private fun round2(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return (value * 100.0).roundToLong() / 100.0
    }

    private fun round1(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return (value * 10.0).roundToLong() / 10.0
    }
}
