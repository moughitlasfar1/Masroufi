package com.example

import com.example.data.AccountItem
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.domain.AccountWithBalance
import com.example.domain.BankMessageParser
import com.example.domain.BankParseResult
import com.example.domain.EnvelopeStatus
import com.example.domain.PayCycleEngine
import com.example.domain.ProjectionAlertLevel
import com.example.domain.PurchaseVerdict
import com.example.domain.VoiceDarijaParser
import com.example.ui.BackupSerializer
import com.example.ui.DashboardState
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2 — Step 2.1 & 2.2 Golden Test Suite.
 *
 * 1. Verifies exact centime-based [Money] & [MoneyParser] semantics (IEEE-754 traps,
 *    Moroccan/French/English parsing, Moroccan Ryal exact math, overflow protection, formatters).
 * 2. Verifies exact centime parity across all migrated `Money`-backed engines & parsers:
 *    - [PayCycleEngine]
 *    - [CreditCalculator]
 *    - [AnnualBillCalculator] & [ZakatCalculator]
 *    - [BankMessageParser], [VoiceDarijaParser], [CsvStatementParser], [PdfStatementParser], [ReceiptParser]
 * 3. Verifies backward-compatibility boundaries:
 *    - Legacy v2 backup JSON (DH decimals `19.99`, `0.10`, `1250.50`, Daret `secondaryAmount: 10`)
 *      decodes cleanly into exact centimes and round-trips cleanly through [BackupSerializer].
 *    - Legacy Firestore DH double conversion via [Money.fromLegacyDoubleDh] has zero IEEE-754 drift.
 *    - `duplicateSignature` and `tx_imp_<hash>` match exact pre-migration Step 2.1 golden hashes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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

        val diff = Money.fromDhString("100.10")!! - Money.fromDhString("100.00")!!
        assertEquals(Money(10L), diff)

        val sum = List(1000) { Money(1L) }.sumMoney()
        assertEquals(Money(1000L), sum)
        assertEquals("10.00", sum.toCanonicalDecimalString())
    }

    @Test
    fun moneyParser_handlesMoroccanFrenchEnglishAndArabicIndicFormats() {
        assertEquals(123456L, MoneyParser.parseMoroccanAmountToCentimes("1 234,56 DH"))
        assertEquals(125050L, MoneyParser.parseMoroccanAmountToCentimes("1\u00A0250,50 MAD"))
        assertEquals(125050L, MoneyParser.parseMoroccanAmountToCentimes("1\u202F250,50 dhs"))
        assertEquals(125050L, MoneyParser.parseMoroccanAmountToCentimes("1.250,50"))
        assertEquals(12050L, MoneyParser.parseMoroccanAmountToCentimes("120,5"))
        assertEquals(13L, MoneyParser.parseMoroccanAmountToCentimes("0,125"))

        assertEquals(125050L, MoneyParser.parseMoroccanAmountToCentimes("1,250.50"))
        assertEquals(125000L, MoneyParser.parseMoroccanAmountToCentimes("1,250"))
        assertEquals(123456700L, MoneyParser.parseMoroccanAmountToCentimes("1,234,567"))
        assertEquals(1999L, MoneyParser.parseMoroccanAmountToCentimes("19.99"))

        assertEquals(-4599L, MoneyParser.parseMoroccanAmountToCentimes("-45.99"))
        assertEquals(-4599L, MoneyParser.parseMoroccanAmountToCentimes("-45,99 DH"))
        assertEquals(-125050L, MoneyParser.parseMoroccanAmountToCentimes("(1 250,50)"))
        assertEquals(-125050L, MoneyParser.parseMoroccanAmountToCentimes("1 250,50-"))
        assertEquals(0L, MoneyParser.parseMoroccanAmountToCentimes("-0.00"))

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
            "100000000.01",
            "999999999999999999999"
        )
        for (raw in malformed) {
            assertNull("Expected null for malformed input '$raw'", MoneyParser.parseMoroccanAmountToCentimes(raw))
        }
    }

    @Test
    fun money_moroccanRyal_isExactFiveCentimes() {
        assertEquals(Money(5L), Money.fromRyal(1L))
        assertEquals(Money(100L), Money.fromRyal(20L))
        val twoHundredRyal = Money.fromRyal(200L)
        assertEquals(Money(1000L), twoHundredRyal)
        assertEquals("10.00", twoHundredRyal.toCanonicalDecimalString())
        assertEquals("10,00 DH", twoHundredRyal.formatDhExact("DH"))
        assertEquals(Money(5000L), Money.fromRyal(1000L))
    }

    @Test
    fun money_arithmeticRoundingAndOverflowProtection() {
        val base = Money.fromCentimes(100_000_00L)
        assertEquals(Money(2_500_00L), base.multiplyRate(BigDecimal("0.025")))
        assertEquals(Money(1L), Money(10L).multiplyRate(BigDecimal("0.05"), RoundingMode.HALF_UP))
        assertEquals(Money(3333L), Money(10000L).divideBy(3L, RoundingMode.HALF_UP))
        assertEquals(Money(6667L), Money(20000L).divideBy(3L, RoundingMode.HALF_UP))
        assertEquals(Money(4599L), Money(-4599L).abs())
        assertEquals(Money(-4599L), -Money(4599L))
        assertEquals(Money(1999L), Money.fromLegacyDoubleDh(19.99))
        assertEquals(Money(10L), Money.fromLegacyDoubleDh(0.10))
        assertEquals(Money(125050L), Money.fromLegacyDoubleDh(1250.50))

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
        val m = Money(125_050L)
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
        assertEquals("0", Money(-49L).formatDhRounded())
    }

    // =========================================================================
    // 2. Golden Snapshots — PayCycleEngine (Money-backed)
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
            AccountItem(id = "acc_bank", name = "Banque", kind = "bank", initialBalance = Money.fromDh(5000)),
            AccountItem(id = "acc_cash", name = "Espèces", kind = "cash", initialBalance = Money(35050L))
        )
        val envelopes = listOf(
            EnvelopeConfig(id = "env_charges", key = "charges", nameFr = "Charges", percentage = 50.0, hasCap = true),
            EnvelopeConfig(id = "env_cafe", key = "cafe", nameFr = "Café", percentage = 20.0, hasCap = true)
        )
        val transactions = listOf(
            TransactionItem(id = "tx_p1", type = "expense", amount = Money.fromDh(2200), envelopeKey = "charges", accountId = "acc_bank", isoDate = "2026-05-05"),
            TransactionItem(id = "tx_p2", type = "expense", amount = Money.fromDh(1300), envelopeKey = "cafe", accountId = "acc_cash", isoDate = "2026-05-10"),
            TransactionItem(id = "tx_c1", type = "income", amount = Money.fromDh(8000), envelopeKey = "autre", accountId = "acc_bank", isoDate = "2026-05-25"),
            TransactionItem(id = "tx_c2", type = "transfer", amount = Money.fromDh(1000), envelopeKey = "autre", accountId = "acc_bank", toAccountId = "acc_cash", isoDate = "2026-05-26"),
            TransactionItem(id = "tx_c3", type = "expense", amount = Money(125075L), envelopeKey = "charges", accountId = "acc_bank", isoDate = "2026-05-27"),
            TransactionItem(id = "tx_c4", type = "expense", amount = Money(4550L), envelopeKey = "cafe", accountId = "acc_cash", isoDate = "2026-05-28")
        )

        val balances = PayCycleEngine.computeAccountBalances(accounts, transactions).associateBy { it.account.id }
        // acc_bank: 5000 + 8000 - (2200 + 1000 + 1250.75) = 8549.25 -> 854925L
        assertEquals(Money(854925L), balances["acc_bank"]!!.currentBalance)
        assertEquals(Money.fromDh(8000), balances["acc_bank"]!!.totalIn)
        assertEquals(Money(445075L), balances["acc_bank"]!!.totalOut)
        // acc_cash: 350.50 + 1000 - (1300 + 45.50) = 5.00 -> 500L
        assertEquals(Money(500L), balances["acc_cash"]!!.currentBalance)
        assertEquals(Money.fromDh(1000), balances["acc_cash"]!!.totalIn)
        assertEquals(Money(134550L), balances["acc_cash"]!!.totalOut)

        val envStatuses = PayCycleEngine.computeEnvelopes(
            envelopes = envelopes,
            baseBudget = Money.fromDh(6000),
            rolloverEnabled = true,
            currentWindow = currentWindow,
            previousWindow = previousWindow,
            allTransactions = transactions
        ).associateBy { it.config.key }

        val charges = envStatuses["charges"]!!
        assertEquals(Money.fromDh(3000), charges.baseAllocated)
        assertEquals(Money.fromDh(800), charges.rolloverAmount)
        assertEquals(Money.fromDh(3800), charges.totalAvailable)
        assertEquals(Money(125075L), charges.spent)
        assertEquals(Money(254925L), charges.remaining)
        assertEquals(33, charges.usagePercent)

        val cafe = envStatuses["cafe"]!!
        assertEquals(Money.fromDh(1200), cafe.baseAllocated)
        assertEquals(Money.ZERO, cafe.rolloverAmount)
        assertEquals(Money.fromDh(1200), cafe.totalAvailable)
        assertEquals(Money(4550L), cafe.spent)
        assertEquals(Money(115450L), cafe.remaining)
        assertEquals(4, cafe.usagePercent)

        val variableTxs = (0 until 10).map { offset ->
            val iso = PayCycleEngine.epochDaysToIso(PayCycleEngine.parseEpochDays("2026-05-25") + offset)
            TransactionItem(
                id = "tx_var_$offset",
                type = "expense",
                amount = Money.fromDh(150),
                envelopeKey = "cafe",
                category = "Café",
                desc = "Repas",
                isoDate = iso
            )
        }
        val projection = PayCycleEngine.computeOverspendProjection(
            window = currentWindow,
            totalAvailableBudget = Money.fromDh(5000),
            spentSoFar = Money.fromDh(1500),
            scheduledBills = Money.fromDh(1000),
            pastOrTodayCycleTransactions = variableTxs
        )
        assertEquals(Money.fromDh(5650), projection.projectedTotal)
        assertEquals(Money.fromDh(5650), projection.optimisticTotal)
        assertEquals(Money.fromDh(150), projection.medianDailyVariable)
        assertEquals(Money.fromDh(150), projection.lowerQuartileDailyVariable)
        assertEquals(ProjectionAlertLevel.RED, projection.alertLevel)
        assertTrue(projection.showEstimateAlert)

        val fitsToday = PayCycleEngine.computePurchaseImpact(
            amount = Money.fromDh(80),
            safePool = Money.fromDh(2200),
            daysRemaining = 22,
            todayIso = "2026-06-03"
        )
        assertEquals(PurchaseVerdict.FITS_TODAY, fitsToday.verdict)
        assertEquals(Money.fromDh(100), fitsToday.currentDailyAllowance)
        assertEquals(Money(9636L), fitsToday.newSafeToday)
        assertEquals(Money.fromDh(20), fitsToday.remainingTodayAllowance)
        assertEquals(Money.ZERO, fitsToday.overSafePoolAmount)

        val lowers = PayCycleEngine.computePurchaseImpact(
            amount = Money.fromDh(440),
            safePool = Money.fromDh(2200),
            daysRemaining = 22,
            todayIso = "2026-06-03"
        )
        assertEquals(PurchaseVerdict.LOWERS_ALLOWANCE, lowers.verdict)
        assertEquals(Money.fromDh(80), lowers.newSafeToday)
        assertEquals(Money.fromDh(-20), lowers.delta)

        val overPool = PayCycleEngine.computePurchaseImpact(
            amount = Money.fromDh(2500),
            safePool = Money.fromDh(2200),
            daysRemaining = 22,
            todayIso = "2026-06-03"
        )
        assertEquals(PurchaseVerdict.OVER_POOL, overPool.verdict)
        assertEquals(Money.ZERO, overPool.newSafeToday)
        assertEquals(Money.fromDh(300), overPool.overSafePoolAmount)
    }

    // =========================================================================
    // 3. Golden Snapshots — CreditCalculator (Money-backed)
    // =========================================================================

    @Test
    fun golden_creditCalculator_standardZeroRateAndDeferredAmortization() {
        val zeroRate = CreditCalculator.calculate(
            CreditInput(
                creditType = CreditType.CONSOMMATION,
                loanAmount = Money.fromDh(12_000),
                downPayment = Money.ZERO,
                durationMonths = 12,
                deferredMonths = 0,
                annualInterestRateHt = 0.0,
                applyInterestTva = true,
                monthlyNetIncome = Money.fromDh(10_000),
                insuranceRate = 0.0
            )
        )
        assertEquals(Money.fromDh(12_000), zeroRate.financedPrincipal)
        assertEquals(Money.fromDh(1_000), zeroRate.monthlyPayment)
        assertEquals(Money.ZERO, zeroRate.totalInterestHt)
        assertEquals(Money.ZERO, zeroRate.totalTva)
        assertEquals(Money.ZERO, zeroRate.totalInsurance)
        assertEquals(Money.ZERO, zeroRate.totalCost)
        assertEquals(Money.fromDh(12_000), zeroRate.totalRepayment)
        assertEquals(12, zeroRate.amortizationSchedule.size)
        assertEquals(Money.ZERO, zeroRate.amortizationSchedule.last().remainingBalance)

        val standard = CreditCalculator.calculate(
            CreditInput(
                creditType = CreditType.CONSOMMATION,
                loanAmount = Money.fromDh(100_000),
                downPayment = Money.fromDh(20_000),
                durationMonths = 60,
                deferredMonths = 0,
                annualInterestRateHt = 7.5,
                applyInterestTva = true,
                monthlyNetIncome = Money.fromDh(10_000),
                existingMonthlyDebt = Money.fromDh(500),
                borrowerAge = 35,
                insuranceRate = 0.6
            )
        )
        assertEquals(Money.fromDh(80_000), standard.financedPrincipal)
        assertEquals(167170L, standard.monthlyPayment.centimes)
        assertTrue(
            "Expected totalInterestHt within 5 centimes of 1627459L, got ${standard.totalInterestHt.centimes}",
            kotlin.math.abs(standard.totalInterestHt.centimes - 1627459L) <= 5L
        )
        assertTrue(
            "Expected totalTva within 5 centimes of 162746L, got ${standard.totalTva.centimes}",
            kotlin.math.abs(standard.totalTva.centimes - 162746L) <= 5L
        )
        assertEquals(Money.fromDh(2_400), standard.totalInsurance)
        assertTrue(
            "Expected totalCost within 10 centimes of 2030205L, got ${standard.totalCost.centimes}",
            kotlin.math.abs(standard.totalCost.centimes - 2030205L) <= 10L
        )
        assertEquals(standard.financedPrincipal + standard.totalCost, standard.totalRepayment)
        assertEquals(60, standard.amortizationSchedule.size)
        assertEquals(Money.ZERO, standard.amortizationSchedule.last().remainingBalance)
        assertFalse(standard.isOverIndebted)

        val deferred = CreditCalculator.calculate(
            CreditInput(
                creditType = CreditType.AUTO,
                loanAmount = Money.fromDh(100_000),
                downPayment = Money.ZERO,
                durationMonths = 24,
                deferredMonths = 3,
                annualInterestRateHt = 6.0,
                applyInterestTva = true,
                monthlyNetIncome = Money.fromDh(15_000),
                insuranceRate = 0.0
            )
        )
        assertEquals(Money.fromDh(550), deferred.deferredMonthlyPayment)
        assertEquals(24, deferred.amortizationSchedule.size)
        for (idx in 0 until 3) {
            val row = deferred.amortizationSchedule[idx]
            assertTrue(row.isDeferred)
            assertEquals(Money.ZERO, row.principal)
            assertEquals(Money.fromDh(500), row.interestHt)
            assertEquals(Money.fromDh(50), row.tvaOnInterest)
            assertEquals(Money.fromDh(550), row.payment)
            assertEquals(Money.fromDh(100_000), row.remainingBalance)
        }
        assertFalse(deferred.amortizationSchedule[3].isDeferred)
        assertEquals(Money.ZERO, deferred.amortizationSchedule.last().remainingBalance)
    }

    // =========================================================================
    // 4. Golden Snapshots — FiscalModelsAndCalculators (Money-backed)
    // =========================================================================

    @Test
    fun golden_fiscalCalculators_zakatNisabAndAnnualVignette() {
        assertEquals(Money.fromDh(350), FiscalRates.vignetteAnnualAmountMad(FiscalRates.HorsepowerBand.UNDER_8_CV, FiscalRates.FuelType.ESSENCE))
        assertEquals(Money.fromDh(700), FiscalRates.vignetteAnnualAmountMad(FiscalRates.HorsepowerBand.UNDER_8_CV, FiscalRates.FuelType.DIESEL))
        assertEquals(Money.fromDh(650), FiscalRates.vignetteAnnualAmountMad(FiscalRates.HorsepowerBand.CV_8_TO_10, FiscalRates.FuelType.ESSENCE))
        assertEquals(Money.fromDh(1500), FiscalRates.vignetteAnnualAmountMad(FiscalRates.HorsepowerBand.CV_8_TO_10, FiscalRates.FuelType.DIESEL))
        assertNull(FiscalRates.vignetteAnnualAmountMad(FiscalRates.HorsepowerBand.CV_11_TO_14, FiscalRates.FuelType.ESSENCE))
        assertNull(FiscalRates.vignetteAnnualAmountMad(FiscalRates.HorsepowerBand.CV_15_PLUS, FiscalRates.FuelType.DIESEL))

        val breakdown = AnnualBillCalculator.computeBreakdown(
            annualAmountMad = Money.fromDh(1500),
            savedAmountMad = Money.fromDh(300),
            dueMonth = 1,
            dueYear = 2027,
            currentYear = 2026,
            currentMonth = 7,
            rollForwardIfPassed = false
        )
        assertEquals(Money.fromDh(1200), breakdown.remainingToSaveMad)
        assertEquals(6, breakdown.monthsRemaining)
        assertEquals(Money.fromDh(200), breakdown.monthlyReserveMad)
        assertEquals(0.2f, breakdown.progressFraction, 0.0001f)
        assertEquals("2027-01-01", breakdown.dueDateIso)

        val goldConfig = ZakatConfig(
            nisabStandard = NisabStandard.GOLD_85G,
            goldPriceMadPerGram = Money.fromDh(900),
            silverPriceMadPerGram = Money.fromDh(11),
            hawlStartIso = "2026-01-15"
        )
        assertEquals(Money.fromDh(76_500), ZakatCalculator.computeNisabThresholdMad(goldConfig))

        val aboveNisab = ZakatCalculator.calculate(
            accountsTotalMad = Money.fromDh(90_000),
            savingsTotalMad = Money.fromDh(20_000),
            shortTermDebtsOwedMad = Money.fromDh(10_000),
            config = goldConfig,
            todayIso = "2026-06-15"
        )
        assertEquals(Money.fromDh(100_000), aboveNisab.eligibleWealthMad)
        assertEquals(Money.fromDh(76_500), aboveNisab.nisabThresholdMad)
        assertEquals(Money.fromDh(2_500), aboveNisab.zakatDueMad)
        assertEquals(HawlStatus.HAWL_RUNNING, aboveNisab.status)

        val silverConfig = goldConfig.copy(nisabStandard = NisabStandard.SILVER_595G)
        assertEquals(Money.fromDh(6_545), ZakatCalculator.computeNisabThresholdMad(silverConfig))

        val belowNisab = ZakatCalculator.calculate(
            accountsTotalMad = Money.fromDh(50_000),
            savingsTotalMad = Money.ZERO,
            shortTermDebtsOwedMad = Money.ZERO,
            config = goldConfig,
            todayIso = "2026-06-15"
        )
        assertEquals(Money.ZERO, belowNisab.zakatDueMad)
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
        assertEquals(Money(125050L), debit.amount)
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
        assertEquals(Money.fromDh(6500), credit.amount)
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
        assertEquals(Money.fromDh(400), atm.amount)
        assertEquals("transfer", atm.direction)
        assertEquals("Retrait GAB", atm.merchant)
    }

    @Test
    fun golden_voiceDarijaParser_ryalDlafFrancsArabiziAndMultiBatch() {
        val arabizi = VoiceDarijaParser.parse("9ahwa 15")
        assertNotNull(arabizi)
        assertEquals(Money.fromDh(15), arabizi!!.amount)
        assertEquals("cafe", arabizi.envelopeKey)

        val ryal = VoiceDarijaParser.parse("qahwa 200 ryal")
        assertNotNull(ryal)
        assertEquals(Money.fromRyal(200L), ryal!!.amount)
        assertEquals(Money(1000L), ryal.amount)

        val dlaf = VoiceDarijaParser.parse("tqadya جوج دلاف")
        assertNotNull(dlaf)
        assertEquals(Money.fromDh(100), dlaf!!.amount)

        val francs = VoiceDarijaParser.parse("khobz 500 francs")
        assertNotNull(francs)
        assertEquals(Money.fromDh(5), francs!!.amount)

        val multi = VoiceDarijaParser.parseMultiExpenses("9ahwa 15, taxi 25 w khobz 5")
        assertEquals(3, multi.size)
        assertEquals(listOf(Money.fromDh(15), Money.fromDh(25), Money.fromDh(5)), multi.map { it.amount })
    }

    @Test
    fun golden_importer_csvPdfRunningBalanceReceiptAndDeterministicIdParity() {
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
        assertEquals(Money(125050L), csvRows[0].debitMad)
        assertEquals(Money.fromDh(500), csvRows[1].debitMad)
        assertEquals(Money.fromDh(6000), csvRows[2].creditMad)

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

        // Prove byte-identical parity with Step 2.1 pre-migration deterministic ID & duplicateSignature
        val legacyPreMigrationPayload = "2026-05-12|${String.format(Locale.US, "%.2f", 1250.50)}|${
            candidates[0].cleanedLabel.lowercase(Locale.ROOT).trim()
        }|acc_bank"
        val legacyDigest = MessageDigest.getInstance("SHA-256")
            .digest(legacyPreMigrationPayload.toByteArray(Charsets.UTF_8))
        val expectedPreMigrationId = "tx_imp_" + (0 until 8).joinToString("") {
            String.format(Locale.US, "%02x", legacyDigest[it])
        }

        val computedId = ImporterConstants.buildDeterministicId(
            isoDate = "2026-05-12",
            amountMad = Money(125050L),
            normalizedLabel = candidates[0].cleanedLabel,
            accountId = "acc_bank",
            occurrenceIndex = 0
        )
        assertEquals(expectedPreMigrationId, computedId)
        assertEquals(expectedPreMigrationId, candidates[0].deterministicId)

        // Verify duplicateSignature detects an already-imported transaction with exact centime parity
        val existingTx = TransactionItem(
            id = "tx_existing_legacy",
            type = "expense",
            amount = Money(125050L),
            accountId = "acc_bank",
            desc = candidates[0].cleanedLabel,
            isoDate = "2026-05-12"
        )
        val dedupedCandidates = StatementPipeline.buildCandidates(
            rows = csvRows,
            existingTransactions = listOf(existingTx),
            envelopes = emptyList(),
            accounts = accounts,
            targetBankAccountId = "acc_bank"
        )
        assertTrue("Expected row 0 to be flagged as duplicate via duplicateSignature", dedupedCandidates[0].isProbableDuplicate)
        assertFalse(dedupedCandidates[0].isSelected)

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
        assertEquals(Money(24550L), pdfResult.rows[0].debitMad)
        assertNull(pdfResult.rows[0].creditMad)
        assertEquals(Money.fromDh(1500), pdfResult.rows[1].creditMad)
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
        assertEquals(Money(15840L), parsedReceipt.totalAmountMad)
        assertEquals("2026-05-14", parsedReceipt.isoDate)
        assertEquals("MARJANE MARKET", parsedReceipt.merchantName)
        assertFalse(parsedReceipt.arabicWeakHint)
    }

    // =========================================================================
    // 6. Compatibility Boundaries — Legacy v2 Backup JSON & Round-Trip
    // =========================================================================

    @Test
    fun backupSerializer_decodesLegacyV2JsonDecimalsToExactCentimesAndRoundTrips() {
        val legacyV2Json = """
            {
              "backupVersion": 2,
              "exportedAtIso": "2026-05-15",
              "baseBudget": 8500.50,
              "payCycleDay": 25,
              "rolloverEnabled": true,
              "profile": {
                "baseBudget": 8500.50,
                "payCycleDay": 25,
                "rolloverEnabled": true,
                "language": "fr",
                "isDarkTheme": false
              },
              "accounts": [
                {
                  "id": "acc_bank",
                  "name": "CIH Bank",
                  "kind": "bank",
                  "initialBalance": 1250.50,
                  "colorHex": "#38BDF8"
                }
              ],
              "envelopes": [
                {
                  "id": "env_cafe",
                  "key": "cafe",
                  "nameFr": "Café",
                  "nameAr": "مقهى",
                  "nameDarija": "قهوة",
                  "nameEn": "Cafe",
                  "percentage": 20.0,
                  "hasCap": true,
                  "colorHex": "#D97706"
                }
              ],
              "financialItems": [
                {
                  "id": "fi_daret_legacy",
                  "itemType": "daret",
                  "name": "Daret Famille",
                  "subtitle": "famille",
                  "amount": 500.0,
                  "secondaryAmount": 10,
                  "dayOrTurn": 3,
                  "extraInt": 1,
                  "dateOrCycle": "2026-05"
                },
                {
                  "id": "fi_goal_legacy",
                  "itemType": "goal",
                  "name": "Voyage",
                  "subtitle": "epargne",
                  "amount": 10000.0,
                  "secondaryAmount": 250.50,
                  "dayOrTurn": 1,
                  "extraInt": 0,
                  "dateOrCycle": "2027-01"
                }
              ],
              "transactions": [
                {
                  "id": "tx_1999",
                  "type": "expense",
                  "amount": 19.99,
                  "category": "Café",
                  "envelopeKey": "cafe",
                  "accountId": "acc_bank",
                  "toAccountId": "",
                  "desc": "Espresso",
                  "iso": "2026-05-12",
                  "hourOfDay": 9
                },
                {
                  "id": "tx_1250",
                  "type": "income",
                  "amount": 1250.50,
                  "category": "Salaire",
                  "envelopeKey": "autre",
                  "accountId": "acc_bank",
                  "toAccountId": "",
                  "desc": "Prime",
                  "iso": "2026-05-13",
                  "hourOfDay": 14
                }
              ]
            }
        """.trimIndent()

        val decoded = BackupSerializer.decodeBackup(
            rawJson = legacyV2Json,
            currentUserId = "u_backup_test",
            fallbackProfile = UserProfile(id = "u_backup_test", userId = "u_backup_test")
        )

        assertEquals(2, decoded.backupVersion)
        assertEquals(Money(850050L), decoded.profile!!.baseBudget)
        assertEquals(1, decoded.accounts.size)
        assertEquals(Money(125050L), decoded.accounts[0].initialBalance)

        val daret = decoded.financialItems.first { it.id == "fi_daret_legacy" }
        assertEquals(Money.fromDh(500), daret.amount)
        assertEquals(10, daret.daretMembers)
        assertEquals(10, daret.resolvedDaretMembers())
        assertEquals(Money.ZERO, daret.secondaryAmount)

        val goal = decoded.financialItems.first { it.id == "fi_goal_legacy" }
        assertEquals(Money.fromDh(10000), goal.amount)
        assertEquals(Money(25050L), goal.secondaryAmount)
        assertNull(goal.daretMembers)

        val txMap = decoded.transactions.associateBy { it.id }
        assertEquals(Money(1999L), txMap["tx_1999"]!!.amount)
        assertEquals(Money(125050L), txMap["tx_1250"]!!.amount)

        // Round-trip encode -> decode preserves exact centimes & daretMembers
        val state = DashboardState(
            profile = decoded.profile!!,
            accounts = decoded.accounts.map { AccountWithBalance(it, it.initialBalance, Money.ZERO, Money.ZERO) },
            envelopes = decoded.envelopes.map { EnvelopeStatus(it, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, 0, false) },
            goals = listOf(goal),
            darets = listOf(daret),
            allTransactions = decoded.transactions
        )
        val reEncodedJson = BackupSerializer.encodeV2Backup(state)
        val reDecoded = BackupSerializer.decodeBackup(
            rawJson = reEncodedJson,
            currentUserId = "u_backup_test",
            fallbackProfile = UserProfile(id = "u_backup_test", userId = "u_backup_test")
        )
        assertEquals(Money(850050L), reDecoded.profile!!.baseBudget)
        assertEquals(Money(125050L), reDecoded.accounts.first().initialBalance)
        assertEquals(Money(1999L), reDecoded.transactions.first { it.id == "tx_1999" }.amount)
        assertEquals(Money(125050L), reDecoded.transactions.first { it.id == "tx_1250" }.amount)
        assertEquals(10, reDecoded.financialItems.first { it.id == "fi_daret_legacy" }.resolvedDaretMembers())
        assertEquals(Money(25050L), reDecoded.financialItems.first { it.id == "fi_goal_legacy" }.secondaryAmount)
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
