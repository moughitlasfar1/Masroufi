package com.example

import com.example.data.AccountItem
import com.example.data.EnvelopeConfig
import com.example.data.TransactionItem
import com.example.domain.BankMessageParser
import com.example.domain.BankParseResult
import com.example.domain.PayCycleEngine
import com.example.domain.ProjectionAlertLevel
import com.example.domain.PurchaseVerdict
import com.example.domain.VoiceDarijaParser
import com.masroufi.core.Money
import com.masroufi.core.MoneyParser
import com.masroufi.core.sumMoney
import com.masroufi.credit.domain.CreditCalculator
import com.masroufi.credit.domain.CreditInput
import com.masroufi.credit.domain.CreditType
import com.masroufi.fiscal.domain.AnnualBillCalculator
import com.masroufi.fiscal.domain.FiscalRates
import com.masroufi.fiscal.domain.HawlStatus
import com.masroufi.fiscal.domain.NisabStandard
import com.masroufi.fiscal.domain.ZakatCalculator
import com.masroufi.fiscal.domain.ZakatConfig
import com.masroufi.importer.domain.BankProfile
import com.masroufi.importer.domain.CsvStatementParser
import com.masroufi.importer.domain.ImporterConstants
import com.masroufi.importer.domain.PdfStatementParser
import com.masroufi.importer.domain.ReceiptParser
import com.masroufi.importer.domain.StatementPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Phase 2 — Step 2.1 Golden Test Suite.
 *
 * 1. Verifies exact centime-based [Money] & [MoneyParser] semantics (IEEE-754 traps,
 *    Moroccan/French/English parsing, Moroccan Ryal exact math, overflow protection, formatters).
 * 2. Locks exact golden outputs across current engines & parsers BEFORE Step 2.2 migrates
 *    monetary `Double` fields to `Long` centimes / [Money]:
 *    - [PayCycleEngine]
 *    - [CreditCalculator]
 *    - [AnnualBillCalculator] & [ZakatCalculator]
 *    - [BankMessageParser], [VoiceDarijaParser], [CsvStatementParser], [PdfStatementParser], [ReceiptParser]
 */
class Phase2MoneyGoldenTest {

    // =========================================================================
    // 1. Money & MoneyParser Unit Tests (IEEE-754 Traps, Formats, Ryal, Overflow)
    // =========================================================================

    @Test
    fun money_classicIeee754Traps_areExactInCentimes() {
        val nineteenNinetyNine = Money.fromDhString("19.99")
        assertEquals(Money(1999L), nineteenNinetyNine)
        assertEquals(BigDecimal("19.99"), nineteenNinetyNine!!.toBigDecimalDh())
        assertEquals("19.99", nineteenNinetyNine.toCanonicalDecimalString())

        val tenCents = Money.fromDhString("0.10")!!
        val twentyCents = Money.fromDhString("0.20")!!
        val thirtyCents = Money.fromDhString("0.30")!!
        assertEquals(thirtyCents, tenCents + twentyCents)
        assertEquals(30L, (tenCents + twentyCents).centimes)

        // Classic binary float trap: 100.10 - 100.00 != 0.10 in Double, exact in Money
        val diff = Money.fromDhString("100.10")!! - Money.fromDhString("100.00")!!
        assertEquals(Money(10L), diff)

        // Exact sum of 1000 * 0.01 DH == 10.00 DH with zero drift
        val sum = List(1000) { Money(1L) }.sumMoney()
        assertEquals(Money(1000L), sum)
        assertEquals("10.00", sum.toCanonicalDecimalString())
    }

    @Test
    fun moneyParser_handlesMoroccanFrenchEnglishAndArabicIndicFormats() {
        // Moroccan / French grouping & decimal comma
        assertEquals(123456L, MoneyParser.parseMoroccanAmountToCentimes("1 234,56 DH"))
        assertEquals(125050L, MoneyParser.parseMoroccanAmountToCentimes("1\u00A0250,50 MAD"))
        assertEquals(125050L, MoneyParser.parseMoroccanAmountToCentimes("1\u202F250,50 dhs"))
        assertEquals(125050L, MoneyParser.parseMoroccanAmountToCentimes("1.250,50"))
        assertEquals(12050L, MoneyParser.parseMoroccanAmountToCentimes("120,5"))
        assertEquals(13L, MoneyParser.parseMoroccanAmountToCentimes("0,125"))

        // US / English formats
        assertEquals(125050L, MoneyParser.parseMoroccanAmountToCentimes("1,250.50"))
        assertEquals(125000L, MoneyParser.parseMoroccanAmountToCentimes("1,250"))
        assertEquals(123456700L, MoneyParser.parseMoroccanAmountToCentimes("1,234,567"))
        assertEquals(1999L, MoneyParser.parseMoroccanAmountToCentimes("19.99"))

        // Negative & accounting formats
        assertEquals(-4599L, MoneyParser.parseMoroccanAmountToCentimes("-45.99"))
        assertEquals(-4599L, MoneyParser.parseMoroccanAmountToCentimes("-45,99 DH"))
        assertEquals(-125050L, MoneyParser.parseMoroccanAmountToCentimes("(1 250,50)"))
        assertEquals(-125050L, MoneyParser.parseMoroccanAmountToCentimes("1 250,50-"))
        assertEquals(0L, MoneyParser.parseMoroccanAmountToCentimes("-0.00"))

        // Leading currency & Arabic-Indic digits
        assertEquals(125050L, MoneyParser.parseMoroccanAmountToCentimes("MAD 1 250,50"))
        assertEquals(125050L, MoneyParser.parseMoroccanAmountToCentimes("١٢٥٠,٥٠ درهم"))
        assertEquals(8500L, MoneyParser.parseMoroccanAmountToCentimes("85 د.م."))
    }

    @Test
    fun moneyParser_rejectsMalformedAndOutOfBoundsStrings() {
        val malformed = listOf(
            "",
            "   ",
            "-",
            "--",
            "+",
            ".",
            ",",
            "abc",
            "12.34.56",
            "1,23,456",
            "1.23,45",
            "19.99.00",
            "--15.00",
            "+-15.00",
            "-15.00-",
            "(-15.00)",
            "1e5",
            "NaN",
            "Infinity",
            "12abc",
            "100000000.01", // exceeds 100M MAD default cap
            "999999999999999999999"
        )
        for (raw in malformed) {
            assertNull("Expected null for malformed input '$raw'", MoneyParser.parseMoroccanAmountToCentimes(raw))
        }
    }

    @Test
    fun money_moroccanRyal_isExactFiveCentimes() {
        assertEquals(Money(5L), Money.fromRyal(1L))
        assertEquals(Money(100L), Money.fromRyal(20L)) // 20 ryal = 1 DH
        val twoHundredRyal = Money.fromRyal(200L)
        assertEquals(Money(1000L), twoHundredRyal)
        assertEquals("10.00", twoHundredRyal.toCanonicalDecimalString())
        assertEquals("10,00 DH", twoHundredRyal.formatDhExact("DH"))

        // 1 dlaf = 1000 ryal = 50 DH = 5000 centimes
        assertEquals(Money(5000L), Money.fromRyal(1000L))
    }

    @Test
    fun money_arithmeticRoundingAndOverflowProtection() {
        val base = Money.fromCentimes(100_000_00L) // 100,000.00 DH
        // Zakat 2.5% exact
        assertEquals(Money(2_500_00L), base.multiplyRate(BigDecimal("0.025")))
        // HALF_UP rounding on 0.5 centime boundary: 10 centimes * 0.05 = 0.5 -> 1 centime
        assertEquals(Money(1L), Money(10L).multiplyRate(BigDecimal("0.05"), RoundingMode.HALF_UP))
        // Divide 100.00 DH / 3 = 33.33 DH (HALF_UP)
        assertEquals(Money(3333L), Money(10000L).divideBy(3L, RoundingMode.HALF_UP))
        // Divide 200.00 DH / 3 = 66.67 DH (HALF_UP)
        assertEquals(Money(6667L), Money(20000L).divideBy(3L, RoundingMode.HALF_UP))
        // abs & unaryMinus
        assertEquals(Money(4599L), Money(-4599L).abs())
        assertEquals(Money(-4599L), -Money(4599L))
        // Legacy double bridge via BigDecimal
        assertEquals(Money(1999L), Money.fromLegacyDoubleDh(19.99))

        // Overflow protections MUST throw ArithmeticException
        assertThrowsArithmetic { Money(Long.MAX_VALUE) + Money(1L) }
        assertThrowsArithmetic { Money(Long.MIN_VALUE) - Money(1L) }
        assertThrowsArithmetic { -Money(Long.MIN_VALUE) }
        assertThrowsArithmetic { Money(Long.MIN_VALUE).abs() }
        assertThrowsArithmetic { Money(Long.MAX_VALUE) * 2L }
        assertThrowsArithmetic { Money.fromRyal(Long.MAX_VALUE / 5L + 1L) }
        assertThrowsArithmetic { Money(100L).divideBy(0L) }
    }

    @Test
    fun money_formattersMatchBothExactAndRoundedConventions() {
        val m = Money(125_050L) // 1 250,50 DH
        assertEquals("1250.50", m.toCanonicalDecimalString())
        assertEquals("1 250,50", m.formatDhExact())
        assertEquals("1 250,50 DH", m.formatDhExact("DH"))
        assertEquals("1 251", m.formatDhRounded())
        assertEquals("1 251 DH", m.formatDhRounded("DH"))
        assertEquals("•••• DH", m.formatCurrencyOrMask("DH", hideAmounts = true))
        assertEquals("1 250,50 DH", m.formatCurrencyOrMask("DH", hideAmounts = false, includeDecimals = true))
        assertEquals("1 251 DH", m.formatCurrencyOrMask("DH", hideAmounts = false, includeDecimals = false))

        assertEquals("-45,99 DH", Money(-4599L).formatDhExact("DH"))
        assertEquals("-46 DH", Money(-4550L).formatDhRounded("DH"))
        assertEquals("0", Money(-49L).formatDhRounded()) // never "-0"
    }

    // =========================================================================
    // 2. Golden Snapshots — PayCycleEngine
    // =========================================================================

    @Test
    fun golden_payCycleEngine_windowBalancesEnvelopesProjectionAndImpact() {
        val currentWindow = PayCycleEngine.computeWindow(
            cycleKey = "2026-05",
            payCycleDay = 25,
            nowIso = "2026-06-03"
        )
        assertEquals("2026-05-25", currentWindow.startIso)
        assertEquals("2026-06-24", currentWindow.endIso)
        assertEquals(31, currentWindow.totalDays)
        assertEquals(10, currentWindow.daysElapsed)
        assertEquals(22, currentWindow.daysRemaining)
        assertTrue(currentWindow.isCurrentCycle)

        val previousWindow = PayCycleEngine.computeWindow(
            cycleKey = "2026-04",
            payCycleDay = 25,
            nowIso = "2026-06-03"
        )
        assertEquals("2026-04-25", previousWindow.startIso)
        assertEquals("2026-05-24", previousWindow.endIso)
        assertEquals(30, previousWindow.totalDays)

        val accounts = listOf(
            AccountItem(id = "acc_bank", name = "Banque", kind = "bank", initialBalance = 5000.0),
            AccountItem(id = "acc_cash", name = "Espèces", kind = "cash", initialBalance = 350.50)
        )
        val envelopes = listOf(
            EnvelopeConfig(id = "env_charges", key = "charges", nameFr = "Charges", percentage = 50.0, hasCap = true),
            EnvelopeConfig(id = "env_cafe", key = "cafe", nameFr = "Café", percentage = 20.0, hasCap = true)
        )
        val transactions = listOf(
            // Previous cycle expenses (for rollover verification)
            TransactionItem(id = "tx_p1", type = "expense", amount = 2200.0, envelopeKey = "charges", accountId = "acc_bank", isoDate = "2026-05-05"),
            TransactionItem(id = "tx_p2", type = "expense", amount = 1300.0, envelopeKey = "cafe", accountId = "acc_cash", isoDate = "2026-05-10"),
            // Current cycle movements
            TransactionItem(id = "tx_c1", type = "income", amount = 8000.0, envelopeKey = "autre", accountId = "acc_bank", isoDate = "2026-05-25"),
            TransactionItem(id = "tx_c2", type = "transfer", amount = 1000.0, envelopeKey = "autre", accountId = "acc_bank", toAccountId = "acc_cash", isoDate = "2026-05-26"),
            TransactionItem(id = "tx_c3", type = "expense", amount = 1250.75, envelopeKey = "charges", accountId = "acc_bank", isoDate = "2026-05-27"),
            TransactionItem(id = "tx_c4", type = "expense", amount = 45.50, envelopeKey = "cafe", accountId = "acc_cash", isoDate = "2026-05-28")
        )

        val balances = PayCycleEngine.computeAccountBalances(accounts, transactions).associateBy { it.account.id }
        // acc_bank: 5000 + 8000 - (2200 + 1000 + 1250.75) = 8549.25
        assertEquals(8549.25, balances["acc_bank"]!!.currentBalance, 0.001)
        assertEquals(8000.0, balances["acc_bank"]!!.totalIn, 0.001)
        assertEquals(4450.75, balances["acc_bank"]!!.totalOut, 0.001)
        // acc_cash: 350.50 + 1000 - (1300 + 45.50) = 5.00
        assertEquals(5.0, balances["acc_cash"]!!.currentBalance, 0.001)
        assertEquals(1000.0, balances["acc_cash"]!!.totalIn, 0.001)
        assertEquals(1345.50, balances["acc_cash"]!!.totalOut, 0.001)

        val envStatuses = PayCycleEngine.computeEnvelopes(
            envelopes = envelopes,
            baseBudget = 6000.0,
            rolloverEnabled = true,
            currentWindow = currentWindow,
            previousWindow = previousWindow,
            allTransactions = transactions
        ).associateBy { it.config.key }

        val charges = envStatuses["charges"]!!
        assertEquals(3000.0, charges.baseAllocated, 0.001)
        assertEquals(800.0, charges.rolloverAmount, 0.001)
        assertEquals(3800.0, charges.totalAvailable, 0.001)
        assertEquals(1250.75, charges.spent, 0.001)
        assertEquals(2549.25, charges.remaining, 0.001)
        assertEquals(33, charges.usagePercent)

        val cafe = envStatuses["cafe"]!!
        assertEquals(1200.0, cafe.baseAllocated, 0.001)
        assertEquals(0.0, cafe.rolloverAmount, 0.001)
        assertEquals(1200.0, cafe.totalAvailable, 0.001)
        assertEquals(45.50, cafe.spent, 0.001)
        assertEquals(1154.50, cafe.remaining, 0.001)
        assertEquals(4, cafe.usagePercent)

        // Overspend projection golden snapshot (10 variable expenses x 150 DH = 1500 DH over 10 elapsed days)
        val variableTxs = (0 until 10).map { offset ->
            val iso = PayCycleEngine.epochDaysToIso(PayCycleEngine.parseEpochDays("2026-05-25") + offset)
            TransactionItem(
                id = "tx_var_$offset",
                type = "expense",
                amount = 150.0,
                envelopeKey = "cafe",
                category = "Café",
                desc = "Repas",
                isoDate = iso
            )
        }
        val projection = PayCycleEngine.computeOverspendProjection(
            window = currentWindow,
            totalAvailableBudget = 5000.0,
            spentSoFar = 1500.0,
            scheduledBills = 1000.0,
            pastOrTodayCycleTransactions = variableTxs
        )
        assertEquals(5650.0, projection.projectedTotal, 0.001)
        assertEquals(5650.0, projection.optimisticTotal, 0.001)
        assertEquals(150.0, projection.medianDailyVariable, 0.001)
        assertEquals(150.0, projection.lowerQuartileDailyVariable, 0.001)
        assertEquals(ProjectionAlertLevel.RED, projection.alertLevel)
        assertTrue(projection.showEstimateAlert)

        // Purchase impact golden snapshots (safePool = 2200 DH, 22 days remaining -> 100 DH/day)
        val fitsToday = PayCycleEngine.computePurchaseImpact(
            amount = 80.0,
            safePool = 2200.0,
            daysRemaining = 22,
            todayIso = "2026-06-03"
        )
        assertEquals(PurchaseVerdict.FITS_TODAY, fitsToday.verdict)
        assertEquals(100.0, fitsToday.currentDailyAllowance, 0.001)
        assertEquals(96.36, fitsToday.newSafeToday, 0.001)
        assertEquals(20.0, fitsToday.remainingTodayAllowance, 0.001)
        assertEquals(0.0, fitsToday.overSafePoolAmount, 0.001)

        val lowers = PayCycleEngine.computePurchaseImpact(
            amount = 440.0,
            safePool = 2200.0,
            daysRemaining = 22,
            todayIso = "2026-06-03"
        )
        assertEquals(PurchaseVerdict.LOWERS_ALLOWANCE, lowers.verdict)
        assertEquals(80.0, lowers.newSafeToday, 0.001)
        assertEquals(-20.0, lowers.delta, 0.001)

        val overPool = PayCycleEngine.computePurchaseImpact(
            amount = 2500.0,
            safePool = 2200.0,
            daysRemaining = 22,
            todayIso = "2026-06-03"
        )
        assertEquals(PurchaseVerdict.OVER_POOL, overPool.verdict)
        assertEquals(0.0, overPool.newSafeToday, 0.001)
        assertEquals(300.0, overPool.overSafePoolAmount, 0.001)
    }

    // =========================================================================
    // 3. Golden Snapshots — CreditCalculator
    // =========================================================================

    @Test
    fun golden_creditCalculator_standardZeroRateAndDeferredAmortization() {
        // A. Zero-rate amortization edge case (0% interest, 0% insurance)
        val zeroRate = CreditCalculator.calculate(
            CreditInput(
                creditType = CreditType.CONSOMMATION,
                loanAmount = 12_000.0,
                downPayment = 0.0,
                durationMonths = 12,
                deferredMonths = 0,
                annualInterestRateHt = 0.0,
                applyInterestTva = true,
                monthlyNetIncome = 10_000.0,
                insuranceRate = 0.0
            )
        )
        assertEquals(12_000.0, zeroRate.financedPrincipal, 0.001)
        assertEquals(1_000.0, zeroRate.monthlyPayment, 0.001)
        assertEquals(0.0, zeroRate.totalInterestHt, 0.001)
        assertEquals(0.0, zeroRate.totalTva, 0.001)
        assertEquals(0.0, zeroRate.totalInsurance, 0.001)
        assertEquals(0.0, zeroRate.totalCost, 0.001)
        assertEquals(12_000.0, zeroRate.totalRepayment, 0.001)
        assertEquals(12, zeroRate.amortizationSchedule.size)
        assertEquals(0.0, zeroRate.amortizationSchedule.last().remainingBalance, 0.001)

        // B. Standard Moroccan Consommation credit (80,000 MAD financed, 7.5% HT + 10% TVA, 0.6% insurance, 60m)
        val standard = CreditCalculator.calculate(
            CreditInput(
                creditType = CreditType.CONSOMMATION,
                loanAmount = 100_000.0,
                downPayment = 20_000.0,
                durationMonths = 60,
                deferredMonths = 0,
                annualInterestRateHt = 7.5,
                applyInterestTva = true,
                monthlyNetIncome = 10_000.0,
                existingMonthlyDebt = 500.0,
                borrowerAge = 35,
                insuranceRate = 0.6
            )
        )
        assertEquals(80_000.0, standard.financedPrincipal, 0.001)
        assertEquals(1_671.70, standard.monthlyPayment, 0.01)
        assertEquals(16_274.59, standard.totalInterestHt, 0.05)
        assertEquals(1_627.46, standard.totalTva, 0.05)
        assertEquals(2_400.0, standard.totalInsurance, 0.01)
        assertEquals(20_302.05, standard.totalCost, 0.05)
        assertEquals(100_302.05, standard.totalRepayment, 0.05)
        assertEquals(60, standard.amortizationSchedule.size)
        assertEquals(0.0, standard.amortizationSchedule.last().remainingBalance, 0.001)
        assertFalse(standard.isOverIndebted)

        // C. Deferred grace period (3 deferred months out of 24, 100,000 MAD, 6% HT + 10% TVA)
        val deferred = CreditCalculator.calculate(
            CreditInput(
                creditType = CreditType.AUTO,
                loanAmount = 100_000.0,
                downPayment = 0.0,
                durationMonths = 24,
                deferredMonths = 3,
                annualInterestRateHt = 6.0,
                applyInterestTva = true,
                monthlyNetIncome = 15_000.0,
                insuranceRate = 0.0
            )
        )
        assertEquals(550.0, deferred.deferredMonthlyPayment, 0.001)
        assertEquals(24, deferred.amortizationSchedule.size)
        for (idx in 0 until 3) {
            val row = deferred.amortizationSchedule[idx]
            assertTrue(row.isDeferred)
            assertEquals(0.0, row.principal, 0.001)
            assertEquals(500.0, row.interestHt, 0.001)
            assertEquals(50.0, row.tvaOnInterest, 0.001)
            assertEquals(550.0, row.payment, 0.001)
            assertEquals(100_000.0, row.remainingBalance, 0.001)
        }
        assertFalse(deferred.amortizationSchedule[3].isDeferred)
        assertEquals(0.0, deferred.amortizationSchedule.last().remainingBalance, 0.001)
    }

    // =========================================================================
    // 4. Golden Snapshots — FiscalModelsAndCalculators (Zakat, Nisab, Vignette)
    // =========================================================================

    @Test
    fun golden_fiscalCalculators_zakatNisabAndAnnualVignette() {
        // Vignette (TSAVA) starter table
        assertEquals(350.0, FiscalRates.vignetteAnnualAmountMad(FiscalRates.HorsepowerBand.UNDER_8_CV, FiscalRates.FuelType.ESSENCE)!!, 0.001)
        assertEquals(700.0, FiscalRates.vignetteAnnualAmountMad(FiscalRates.HorsepowerBand.UNDER_8_CV, FiscalRates.FuelType.DIESEL)!!, 0.001)
        assertEquals(650.0, FiscalRates.vignetteAnnualAmountMad(FiscalRates.HorsepowerBand.CV_8_TO_10, FiscalRates.FuelType.ESSENCE)!!, 0.001)
        assertEquals(1500.0, FiscalRates.vignetteAnnualAmountMad(FiscalRates.HorsepowerBand.CV_8_TO_10, FiscalRates.FuelType.DIESEL)!!, 0.001)
        assertNull(FiscalRates.vignetteAnnualAmountMad(FiscalRates.HorsepowerBand.CV_11_TO_14, FiscalRates.FuelType.ESSENCE))
        assertNull(FiscalRates.vignetteAnnualAmountMad(FiscalRates.HorsepowerBand.CV_15_PLUS, FiscalRates.FuelType.DIESEL))

        // Annual bill monthly reserve breakdown
        val breakdown = AnnualBillCalculator.computeBreakdown(
            annualAmountMad = 1500.0,
            savedAmountMad = 300.0,
            dueMonth = 1,
            dueYear = 2027,
            currentYear = 2026,
            currentMonth = 7,
            rollForwardIfPassed = false
        )
        assertEquals(1200.0, breakdown.remainingToSaveMad, 0.001)
        assertEquals(6, breakdown.monthsRemaining)
        assertEquals(200.0, breakdown.monthlyReserveMad, 0.001)
        assertEquals(0.2f, breakdown.progressFraction, 0.0001f)
        assertEquals("2027-01-01", breakdown.dueDateIso)

        // Zakat — Gold 85g Nisab (85 * 900 = 76,500 MAD), eligible wealth = 100,000 MAD -> Zakat = 2,500 MAD
        val goldConfig = ZakatConfig(
            nisabStandard = NisabStandard.GOLD_85G,
            goldPriceMadPerGram = 900.0,
            silverPriceMadPerGram = 11.0,
            hawlStartIso = "2026-01-15"
        )
        assertEquals(76_500.0, ZakatCalculator.computeNisabThresholdMad(goldConfig), 0.001)

        val aboveNisab = ZakatCalculator.calculate(
            accountsTotalMad = 90_000.0,
            savingsTotalMad = 20_000.0,
            shortTermDebtsOwedMad = 10_000.0,
            config = goldConfig,
            todayIso = "2026-06-15"
        )
        assertEquals(100_000.0, aboveNisab.eligibleWealthMad, 0.001)
        assertEquals(76_500.0, aboveNisab.nisabThresholdMad, 0.001)
        assertEquals(2_500.0, aboveNisab.zakatDueMad, 0.001)
        assertEquals(HawlStatus.HAWL_RUNNING, aboveNisab.status)

        // Silver 595g Nisab (595 * 11 = 6,545 MAD)
        val silverConfig = goldConfig.copy(nisabStandard = NisabStandard.SILVER_595G)
        assertEquals(6_545.0, ZakatCalculator.computeNisabThresholdMad(silverConfig), 0.001)

        // Below Nisab -> Zakat = 0.0
        val belowNisab = ZakatCalculator.calculate(
            accountsTotalMad = 50_000.0,
            savingsTotalMad = 0.0,
            shortTermDebtsOwedMad = 0.0,
            config = goldConfig,
            todayIso = "2026-06-15"
        )
        assertEquals(0.0, belowNisab.zakatDueMad, 0.001)
        assertEquals(HawlStatus.BELOW_NISAB, belowNisab.status)
    }

    // =========================================================================
    // 5. Golden Snapshots — BankMessageParser, VoiceDarijaParser & Importer
    // =========================================================================

    @Test
    fun golden_bankMessageParser_debitCreditAtmAndIgnoreSolde() {
        val debitRes = BankMessageParser.parseDetailed(
            rawTitle = "CIH Bank",
            rawBody = "Paiement TPE de 1 250,50 DH chez MARJANE le 15/05/2026. Solde disponible: 8 420,00 DH",
            sourcePackage = "com.cih.cihmobile",
            isoDate = "2026-05-15"
        )
        assertTrue(debitRes is BankParseResult.Parsed)
        val debit = (debitRes as BankParseResult.Parsed).notification
        assertEquals(1250.50, debit.amount, 0.001)
        assertEquals("DH", debit.currency)
        assertEquals("expense", debit.direction)
        assertEquals("MARJANE", debit.merchant)
        assertEquals("cih", debit.bankTemplateId)

        val creditRes = BankMessageParser.parseDetailed(
            rawTitle = "Attijariwafa Bank",
            rawBody = "Vous avez reçu un virement de 6 500,00 MAD de SOCIETE ATLAS",
            sourcePackage = "ma.attijariwafabank.lbankalik",
            isoDate = "2026-05-01"
        )
        assertTrue(creditRes is BankParseResult.Parsed)
        val credit = (creditRes as BankParseResult.Parsed).notification
        assertEquals(6500.0, credit.amount, 0.001)
        assertEquals("MAD", credit.currency)
        assertEquals("income", credit.direction)

        val atmRes = BankMessageParser.parseDetailed(
            rawTitle = "Banque Populaire",
            rawBody = "Retrait GAB de 400,00 DH le 10/05/2026",
            sourcePackage = "com.bcp.pocketbank",
            isoDate = "2026-05-10"
        )
        assertTrue(atmRes is BankParseResult.Parsed)
        val atm = (atmRes as BankParseResult.Parsed).notification
        assertEquals(400.0, atm.amount, 0.001)
        assertEquals("transfer", atm.direction)
        assertEquals("Retrait GAB", atm.merchant)
    }

    @Test
    fun golden_voiceDarijaParser_ryalDlafFrancsArabiziAndMultiBatch() {
        val arabizi = VoiceDarijaParser.parse("9ahwa 15")
        assertNotNull(arabizi)
        assertEquals(15.0, arabizi!!.amount, 0.001)
        assertEquals("cafe", arabizi.envelopeKey)

        val ryal = VoiceDarijaParser.parse("qahwa 200 ryal")
        assertNotNull(ryal)
        assertEquals(10.0, ryal!!.amount, 0.001)
        assertEquals(Money.fromRyal(200L).toBigDecimalDh().toDouble(), ryal.amount, 0.001)

        val dlaf = VoiceDarijaParser.parse("tqadya جوج دلاف")
        assertNotNull(dlaf)
        assertEquals(100.0, dlaf!!.amount, 0.001)

        val francs = VoiceDarijaParser.parse("khobz 500 francs")
        assertNotNull(francs)
        assertEquals(5.0, francs!!.amount, 0.001)

        val multi = VoiceDarijaParser.parseMultiExpenses("9ahwa 15, taxi 25 w khobz 5")
        assertEquals(3, multi.size)
        assertEquals(listOf(15.0, 25.0, 5.0), multi.map { it.amount })
    }

    @Test
    fun golden_importer_csvPdfRunningBalanceReceiptAndDeterministicId() {
        // A. CSV parsing + StatementPipeline + deterministic ID compatibility with Money
        val csv = """
            Banque Populaire - Releve de compte
            Date operation;Libelle;Debit;Credit
            12/05/2026;PAIEMENT CARTE MARJANE;1 250,50;
            13/05/2026;RETRAIT GAB AGENCE CASA;500,00;
            15/05/2026;VIREMENT RECU SALAIRE;;6 000,00
        """.trimIndent()

        val inspection = CsvStatementParser.inspectCsv(csv.toByteArray(Charsets.UTF_8))
        assertEquals(BankProfile.BCP, inspection.detectedBankProfile)
        assertEquals(';', inspection.delimiter)

        val csvRows = CsvStatementParser.extractRows(
            rawRows = inspection.allRawRows,
            mapping = inspection.suggestedMapping,
            headerRowIndex = inspection.headerRowIndex
        )
        assertEquals(3, csvRows.size)
        assertEquals(1250.50, csvRows[0].debitMad!!, 0.001)
        assertEquals(500.0, csvRows[1].debitMad!!, 0.001)
        assertEquals(6000.0, csvRows[2].creditMad!!, 0.001)

        val accounts = listOf(
            AccountItem(id = "acc_bank", name = "BCP", kind = "bank"),
            AccountItem(id = "acc_cash", name = "Cash", kind = "cash")
        )
        val candidates = StatementPipeline.buildCandidates(
            rows = csvRows,
            existingTransactions = emptyList(),
            envelopes = emptyList(),
            accounts = accounts,
            targetBankAccountId = "acc_bank"
        )
        assertEquals(3, candidates.size)
        assertEquals("expense", candidates[0].txType)
        assertEquals("transfer", candidates[1].txType)
        assertTrue(candidates[1].isAtmTransfer)
        assertEquals("acc_cash", candidates[1].toAccountId)
        assertEquals("income", candidates[2].txType)

        // Lock deterministic ID hash & verify Money.toCanonicalDecimalString() matches %.2f format
        val expectedId = ImporterConstants.buildDeterministicId(
            isoDate = "2026-05-12",
            amountMad = 1250.50,
            normalizedLabel = candidates[0].cleanedLabel,
            accountId = "acc_bank",
            occurrenceIndex = 0
        )
        assertEquals(expectedId, candidates[0].deterministicId)
        assertEquals("1250.50", Money.fromDhString("1 250,50")!!.toCanonicalDecimalString())

        // B. PDF parser with opening + running balance inference
        val pdfText = """
            CIH Bank - Releve de compte
            Solde anterieur : 10 000,00
            12/05/2026 ACHAT MARJANE 245,50 9 754,50
            15/05/2026 VIREMENT CLIENT 1 500,00 11 254,50
        """.trimIndent()

        val pdfResult = PdfStatementParser.parseText(pdfText)
        assertEquals(BankProfile.CIH, pdfResult.detectedBankProfile)
        assertEquals(2, pdfResult.rows.size)
        assertEquals(245.50, pdfResult.rows[0].debitMad!!, 0.001)
        assertNull(pdfResult.rows[0].creditMad)
        assertEquals(1500.0, pdfResult.rows[1].creditMad!!, 0.001)
        assertNull(pdfResult.rows[1].debitMad)

        // C. Receipt parser golden snapshot
        val receiptText = """
            MARJANE MARKET
            14/05/2026 18:42
            PAIN COMPLET 12,00
            HUILE OLIVE 120,00
            SOUS TOTAL 132,00
            TVA 20% 26,40
            TOTAL TTC 158,40 DH
            ESPECES 200,00
            RENDU MONNAIE 41,60
        """.trimIndent()

        val parsedReceipt = ReceiptParser.parse(receiptText, todayIso = "2026-05-20")
        assertEquals(158.40, parsedReceipt.totalAmountMad!!, 0.001)
        assertEquals("2026-05-14", parsedReceipt.isoDate)
        assertEquals("MARJANE MARKET", parsedReceipt.merchantName)
        assertFalse(parsedReceipt.arabicWeakHint)
    }

    private fun assertThrowsArithmetic(block: () -> Unit) {
        try {
            block()
            fail("Expected ArithmeticException to be thrown")
        } catch (_: ArithmeticException) {
            // Expected
        }
    }
}
