package com.masroufi.credit.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

object CreditCalculator {
    const val MOROCCAN_TVA_RATE = 0.10
    const val MOROCCAN_SMIG_MAD = 3_045.0
    const val MANDATORY_ADI_THRESHOLD_MAD = 10_000.0
    const val MAX_AGE_AT_FINAL_PAYMENT = 70.0

    fun resolveMaxDti(input: CreditInput): Double {
        return if (input.creditType == CreditType.IMMOBILIER || input.hasExistingMortgage) {
            when {
                input.monthlyNetIncome > 60_000.0 -> 60.0
                input.monthlyNetIncome >= 35_000.0 -> 55.0
                else -> 50.0
            }
        } else {
            if (input.monthlyNetIncome <= 15_000.0) 45.0 else 50.0
        }
    }

    fun calculate(input: CreditInput, language: String = "fr"): CreditResult {
        val strings = CreditStrings.forLang(language)
        val sanitizedLoan = input.loanAmount.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val sanitizedDown = input.downPayment.takeIf { it.isFinite() }?.coerceIn(0.0, sanitizedLoan) ?: 0.0
        val financedPrincipal = round2((sanitizedLoan - sanitizedDown).coerceAtLeast(0.0))

        val totalMonths = input.durationMonths.coerceAtLeast(1)
        val deferredMonths = input.deferredMonths.coerceIn(0, (totalMonths - 1).coerceAtLeast(0))
        val amortizedMonths = (totalMonths - deferredMonths).coerceAtLeast(1)

        val rateHt = input.annualInterestRateHt.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val monthlyRateHt = (rateHt / 100.0) / 12.0
        val tvaFactor = if (input.applyInterestTva) 1.0 + MOROCCAN_TVA_RATE else 1.0
        val effectiveMonthlyRate = monthlyRateHt * tvaFactor

        val insRate = input.insuranceRate.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val monthlyInsurance = financedPrincipal * (insRate / 100.0) / 12.0

        val schedule = ArrayList<AmortizationRow>(totalMonths)
        var remainingBalance = financedPrincipal
        var totalInterestHt = 0.0
        var totalTva = 0.0
        var totalInsurance = 0.0

        // 1. Optional partial grace period (différé partiel: interest TTC + ADI insurance only)
        val rawDeferredPayment = if (deferredMonths > 0 && financedPrincipal > 0.0) {
            val defInterestHt = financedPrincipal * monthlyRateHt
            val defTva = if (input.applyInterestTva) defInterestHt * MOROCCAN_TVA_RATE else 0.0
            defInterestHt + defTva + monthlyInsurance
        } else {
            0.0
        }

        for (month in 1..deferredMonths) {
            val interestHt = financedPrincipal * monthlyRateHt
            val tva = if (input.applyInterestTva) interestHt * MOROCCAN_TVA_RATE else 0.0
            val payment = interestHt + tva + monthlyInsurance
            totalInterestHt += interestHt
            totalTva += tva
            totalInsurance += monthlyInsurance
            schedule.add(
                AmortizationRow(
                    month = month,
                    isDeferred = true,
                    payment = round2(payment),
                    principal = 0.0,
                    interestHt = round2(interestHt),
                    tvaOnInterest = round2(tva),
                    insurance = round2(monthlyInsurance),
                    remainingBalance = round2(remainingBalance)
                )
            )
        }

        // 2. Standard amortized installments over remaining months
        val standardAnnuityExclInsurance = when {
            financedPrincipal <= 0.0 -> 0.0
            effectiveMonthlyRate == 0.0 -> financedPrincipal / amortizedMonths.toDouble()
            else -> {
                val growth = (1.0 + effectiveMonthlyRate).pow(amortizedMonths.toDouble())
                financedPrincipal * (effectiveMonthlyRate * growth) / (growth - 1.0)
            }
        }
        val rawMonthlyPayment = if (financedPrincipal <= 0.0) 0.0 else standardAnnuityExclInsurance + monthlyInsurance

        for (i in 1..amortizedMonths) {
            val monthIndex = deferredMonths + i
            val isLastMonth = i == amortizedMonths
            val interestTtc = if (financedPrincipal <= 0.0) 0.0 else remainingBalance * effectiveMonthlyRate
            val interestHt = if (input.applyInterestTva) interestTtc / (1.0 + MOROCCAN_TVA_RATE) else interestTtc
            val tva = interestTtc - interestHt

            val principal = when {
                financedPrincipal <= 0.0 -> 0.0
                isLastMonth -> remainingBalance
                effectiveMonthlyRate == 0.0 -> (financedPrincipal / amortizedMonths.toDouble()).coerceIn(0.0, remainingBalance)
                else -> (standardAnnuityExclInsurance - interestTtc).coerceIn(0.0, remainingBalance)
            }

            remainingBalance = (remainingBalance - principal).coerceAtLeast(0.0)
            if (isLastMonth) {
                remainingBalance = 0.0
            }

            val rowPayment = principal + interestTtc + monthlyInsurance
            totalInterestHt += interestHt
            totalTva += tva
            totalInsurance += monthlyInsurance

            schedule.add(
                AmortizationRow(
                    month = monthIndex,
                    isDeferred = false,
                    payment = round2(rowPayment),
                    principal = round2(principal),
                    interestHt = round2(interestHt),
                    tvaOnInterest = round2(tva),
                    insurance = round2(monthlyInsurance),
                    remainingBalance = round2(remainingBalance)
                )
            )
        }

        val monthlyPayment = round2(rawMonthlyPayment)
        val deferredMonthlyPayment = round2(rawDeferredPayment)
        val roundedInterestHt = round2(totalInterestHt)
        val roundedTva = round2(totalTva)
        val roundedInsurance = round2(totalInsurance)
        val totalCost = round2(roundedInterestHt + roundedTva + roundedInsurance)
        val totalRepayment = round2(financedPrincipal + totalCost)

        val income = input.monthlyNetIncome.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val existingDebt = input.existingMonthlyDebt.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val totalMonthlyCharge = monthlyPayment + existingDebt
        val dtiRatio = when {
            income > 0.0 -> round2((totalMonthlyCharge / income) * 100.0)
            totalMonthlyCharge > 0.0 -> 100.0
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
            if (income > 0.0 && income < MOROCCAN_SMIG_MAD) {
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
     * Formats numbers using Moroccan/French grouping, e.g., `12 500,00 MAD`.
     */
    fun formatMad(amount: Double): String {
        val safe = if (amount.isFinite()) amount else 0.0
        val sign = if (safe < -0.004) "-" else ""
        val totalCents = (abs(safe) * 100.0).roundToLong()
        val wholePart = totalCents / 100L
        val centsPart = totalCents % 100L
        val groupedWhole = groupWithSpaces(wholePart)
        val centsText = String.format(Locale.US, "%02d", centsPart)
        return "$sign$groupedWhole,$centsText MAD"
    }

    fun formatMadCompact(amount: Double): String {
        val safe = if (amount.isFinite()) amount else 0.0
        val sign = if (safe < -0.5) "-" else ""
        val wholePart = abs(safe).roundToLong()
        return "$sign${groupWithSpaces(wholePart)} MAD"
    }

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

    private fun groupWithSpaces(value: Long): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val sb = StringBuilder(digits.length + digits.length / 3)
        val rem = digits.length % 3
        if (rem > 0) {
            sb.append(digits, 0, rem)
            if (digits.length > rem) sb.append(' ')
        }
        var i = rem
        while (i < digits.length) {
            sb.append(digits, i, i + 3)
            i += 3
            if (i < digits.length) sb.append(' ')
        }
        return sb.toString()
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
