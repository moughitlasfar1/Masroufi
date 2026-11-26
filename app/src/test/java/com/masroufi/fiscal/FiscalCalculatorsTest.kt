package com.masroufi.fiscal

import com.masroufi.core.Money
import com.masroufi.fiscal.data.FiscalRepository
import com.masroufi.fiscal.domain.AnnualBillCalculator
import com.masroufi.fiscal.domain.AnnualBillItem
import com.masroufi.fiscal.domain.AnnualBillPreset
import com.masroufi.fiscal.domain.FiscalRates
import com.masroufi.fiscal.domain.FiscalStrings
import com.masroufi.fiscal.domain.HawlStatus
import com.masroufi.fiscal.domain.NisabStandard
import com.masroufi.fiscal.domain.ZakatCalculator
import com.masroufi.fiscal.domain.ZakatConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FiscalCalculatorsTest {

    // -------------------------------------------------------------------------
    // Step 1: Vignette (TSAVA) rates & Annual Bills Reserve Calculator
    // -------------------------------------------------------------------------

    @Test
    fun `vignette starter rates match DGI starter bands and leave above 10 CV as TODO null`() {
        assertTrue(FiscalRates.VIGNETTE_RATES_AS_OF_ISO.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
        assertTrue(FiscalRates.VIGNETTE_SOURCE_COMMENT.contains("DGI"))

        assertEquals(
            Money.fromDh(350),
            FiscalRates.vignetteAnnualAmountMad(
                FiscalRates.HorsepowerBand.UNDER_8_CV,
                FiscalRates.FuelType.ESSENCE
            )
        )
        assertEquals(
            Money.fromDh(700),
            FiscalRates.vignetteAnnualAmountMad(
                FiscalRates.HorsepowerBand.UNDER_8_CV,
                FiscalRates.FuelType.DIESEL
            )
        )
        assertEquals(
            Money.fromDh(650),
            FiscalRates.vignetteAnnualAmountMad(
                FiscalRates.HorsepowerBand.CV_8_TO_10,
                FiscalRates.FuelType.ESSENCE
            )
        )
        assertEquals(
            Money.fromDh(1500),
            FiscalRates.vignetteAnnualAmountMad(
                FiscalRates.HorsepowerBand.CV_8_TO_10,
                FiscalRates.FuelType.DIESEL
            )
        )

        // > 10 CV bands must stay TODO (null) until verified against official DGI tables
        assertNull(
            FiscalRates.vignetteAnnualAmountMad(
                FiscalRates.HorsepowerBand.CV_11_TO_14,
                FiscalRates.FuelType.ESSENCE
            )
        )
        assertNull(
            FiscalRates.vignetteAnnualAmountMad(
                FiscalRates.HorsepowerBand.CV_11_TO_14,
                FiscalRates.FuelType.DIESEL
            )
        )
        assertNull(
            FiscalRates.vignetteAnnualAmountMad(
                FiscalRates.HorsepowerBand.CV_15_PLUS,
                FiscalRates.FuelType.ESSENCE
            )
        )
        assertNull(
            FiscalRates.vignetteAnnualAmountMad(
                FiscalRates.HorsepowerBand.CV_15_PLUS,
                FiscalRates.FuelType.DIESEL
            )
        )
    }

    @Test
    fun `annual bill monthly reserve splits remaining across remaining months accurately`() {
        val breakdown = AnnualBillCalculator.computeBreakdown(
            annualAmountMad = Money.fromDh(1500),
            savedAmountMad = Money.fromDh(300),
            dueMonth = 12,
            dueYear = 2026,
            currentYear = 2026,
            currentMonth = 6,
            rollForwardIfPassed = false
        )
        assertEquals(Money.fromDh(1500), breakdown.annualAmountMad)
        assertEquals(Money.fromDh(300), breakdown.savedAmountMad)
        assertEquals(Money.fromDh(1200), breakdown.remainingToSaveMad)
        assertEquals(6, breakdown.monthsRemaining)
        assertEquals(Money.fromDh(200), breakdown.monthlyReserveMad)
        assertEquals(0.2f, breakdown.progressFraction, 0.001f)
        assertFalse(breakdown.isDueThisMonth)
        assertFalse(breakdown.isDueDatePassed)
        assertFalse(breakdown.rolledToNextYear)
        assertEquals("2026-12-01", breakdown.dueDateIso)
    }

    @Test
    fun `edge case when due month is this month sets monthsRemaining to 1 and full remaining reserve`() {
        val breakdown = AnnualBillCalculator.computeBreakdown(
            annualAmountMad = Money.fromDh(700),
            savedAmountMad = Money.fromDh(250),
            dueMonth = 1,
            dueYear = 2026,
            currentYear = 2026,
            currentMonth = 1,
            rollForwardIfPassed = false
        )
        assertEquals(1, breakdown.monthsRemaining)
        assertEquals(Money.fromDh(450), breakdown.monthlyReserveMad)
        assertTrue(breakdown.isDueThisMonth)
        assertFalse(breakdown.isDueDatePassed)
        assertFalse(breakdown.rolledToNextYear)
    }

    @Test
    fun `edge case when due date passed handles both overdue existing bill and next-year rollover`() {
        val overdue = AnnualBillCalculator.computeBreakdown(
            annualAmountMad = Money.fromDh(650),
            savedAmountMad = Money.fromDh(150),
            dueMonth = 1,
            dueYear = 2026,
            currentYear = 2026,
            currentMonth = 4,
            rollForwardIfPassed = false
        )
        assertTrue(overdue.isDueDatePassed)
        assertFalse(overdue.isDueThisMonth)
        assertFalse(overdue.rolledToNextYear)
        assertEquals(1, overdue.monthsRemaining)
        assertEquals(Money.fromDh(500), overdue.monthlyReserveMad)

        val rolled = AnnualBillCalculator.computeBreakdown(
            annualAmountMad = Money.fromDh(900),
            savedAmountMad = Money.ZERO,
            dueMonth = 1,
            dueYear = 2026,
            currentYear = 2026,
            currentMonth = 4,
            rollForwardIfPassed = true
        )
        assertTrue(rolled.rolledToNextYear)
        assertFalse(rolled.isDueDatePassed)
        assertEquals(2027, rolled.dueYear)
        assertEquals(9, rolled.monthsRemaining)
        assertEquals(Money.fromDh(100), rolled.monthlyReserveMad)
    }

    @Test
    fun `fully funded annual bill reports 0 remaining and 0 monthly reserve`() {
        val funded = AnnualBillCalculator.computeBreakdown(
            annualAmountMad = Money.fromDh(1200),
            savedAmountMad = Money.fromDh(1400),
            dueMonth = 10,
            dueYear = 2026,
            currentYear = 2026,
            currentMonth = 5
        )
        assertEquals(Money.ZERO, funded.remainingToSaveMad)
        assertEquals(Money.ZERO, funded.monthlyReserveMad)
        assertEquals(1.0f, funded.progressFraction, 0.001f)
    }

    @Test
    fun `FiscalRepository serializes annual bills and maps backing goal plus recurring monthly bill`() {
        val bill = AnnualBillItem(
            id = "vig_2027",
            preset = AnnualBillPreset.VIGNETTE,
            title = "Vignette Clio",
            annualAmountMad = Money.fromDh(700),
            dueMonth = 1,
            dueYear = 2027,
            cvBand = FiscalRates.HorsepowerBand.UNDER_8_CV,
            fuelType = FiscalRates.FuelType.DIESEL,
            linkedGoalId = "goal_fiscal_vig_2027",
            linkedBillId = "bill_fiscal_vig_2027"
        )
        val json = FiscalRepository.serializeAnnualBillsJson(listOf(bill))
        val parsed = FiscalRepository.parseAnnualBillsJson(json)
        assertEquals(1, parsed.size)
        assertEquals(bill.id, parsed[0].id)
        assertEquals(AnnualBillPreset.VIGNETTE, parsed[0].preset)
        assertEquals(FiscalRates.HorsepowerBand.UNDER_8_CV, parsed[0].cvBand)
        assertEquals(FiscalRates.FuelType.DIESEL, parsed[0].fuelType)

        val (goal, recurringBill) = FiscalRepository.buildBackingGoalAndRecurringBill(
            bill = bill,
            existingGoalSavedAmount = Money.fromDh(100),
            payCycleDay = 5,
            strings = FiscalStrings.forLang("fr"),
            currentYear = 2026,
            currentMonth = 7
        )
        assertEquals("goal", goal.itemType)
        assertEquals("goal_fiscal_vig_2027", goal.id)
        assertEquals(Money.fromDh(700), goal.amount)
        assertEquals(Money.fromDh(100), goal.secondaryAmount)

        assertEquals("bill", recurringBill.itemType)
        assertEquals("bill_fiscal_vig_2027", recurringBill.id)
        assertEquals(Money.fromDh(100), recurringBill.amount)
        assertEquals(5, recurringBill.dayOrTurn)
    }

    // -------------------------------------------------------------------------
    // Step 5: Zakat & HijrahDate Hawl Calculator
    // -------------------------------------------------------------------------

    @Test
    fun `zakat nisab threshold uses 85g gold or 595g silver from editable offline prices`() {
        val goldCfg = ZakatConfig(
            nisabStandard = NisabStandard.GOLD_85G,
            goldPriceMadPerGram = Money.fromDh(900),
            silverPriceMadPerGram = Money.fromDh(12)
        )
        assertEquals(Money.fromDh(76_500), ZakatCalculator.computeNisabThresholdMad(goldCfg))

        val silverCfg = goldCfg.copy(nisabStandard = NisabStandard.SILVER_595G)
        assertEquals(Money.fromDh(7_140), ZakatCalculator.computeNisabThresholdMad(silverCfg))
    }

    @Test
    fun `zakat calculation handles below nisab, hawl running, and due now using HijrahDate`() {
        val config = ZakatConfig(
            nisabStandard = NisabStandard.GOLD_85G,
            goldPriceMadPerGram = Money.fromDh(900),
            silverPriceMadPerGram = Money.fromDh(11),
            pricesAsOfIso = "2026-01-01",
            hawlStartIso = "2026-01-01"
        )

        val below = ZakatCalculator.calculate(
            accountsTotalMad = Money.fromDh(50_000),
            savingsTotalMad = Money.fromDh(30_000),
            shortTermDebtsOwedMad = Money.fromDh(10_000),
            config = config,
            todayIso = "2026-06-01"
        )
        assertEquals(Money.fromDh(70_000), below.eligibleWealthMad)
        assertEquals(Money.ZERO, below.zakatDueMad)
        assertEquals(HawlStatus.BELOW_NISAB, below.status)

        val running = ZakatCalculator.calculate(
            accountsTotalMad = Money.fromDh(80_000),
            savingsTotalMad = Money.fromDh(40_000),
            shortTermDebtsOwedMad = Money.fromDh(20_000),
            config = config,
            todayIso = "2026-06-01"
        )
        assertEquals(Money.fromDh(100_000), running.eligibleWealthMad)
        assertEquals(Money.fromDh(2_500), running.zakatDueMad)
        assertEquals(HawlStatus.HAWL_RUNNING, running.status)
        assertTrue("Expected daysRemaining in 190..215, got ${running.daysRemaining}", running.daysRemaining in 190..215)
        assertTrue("Hijri start label should end with H", running.hawlHijriStartLabel.endsWith("H"))
        assertTrue("Hijri due label should end with H", running.hawlHijriDueLabel.endsWith("H"))
        assertTrue("Monthly reserve suggestion should be > 0", running.monthlyReserveSuggestionMad.isPositive)

        val dueNow = ZakatCalculator.calculate(
            accountsTotalMad = Money.fromDh(100_000),
            savingsTotalMad = Money.ZERO,
            shortTermDebtsOwedMad = Money.ZERO,
            config = config,
            todayIso = "2027-01-05"
        )
        assertEquals(HawlStatus.DUE_NOW, dueNow.status)
        assertEquals(0, dueNow.daysRemaining)
        assertEquals(Money.fromDh(2_500), dueNow.zakatDueMad)
        assertEquals(Money.fromDh(2_500), dueNow.monthlyReserveSuggestionMad)
    }

    @Test
    fun `zakat config JSON round-trip and 4-language fiscal strings are complete`() {
        val cfg = ZakatConfig(
            nisabStandard = NisabStandard.SILVER_595G,
            goldPriceMadPerGram = Money.fromDh(950),
            silverPriceMadPerGram = Money(1350L),
            pricesAsOfIso = "2026-03-15",
            hawlStartIso = "2026-02-10",
            linkedAnnualBillId = "zakat_2027"
        )
        val parsed = FiscalRepository.parseZakatConfigJson(FiscalRepository.serializeZakatConfigJson(cfg))
        assertEquals(cfg, parsed)

        for (lang in listOf("fr", "en", "ar", "darija")) {
            val s = FiscalStrings.forLang(lang)
            assertTrue(s.annualBillsTitle.isNotBlank())
            assertTrue(s.zakatTitle.isNotBlank())
            assertTrue(s.zakatDisclaimer.isNotBlank())
            assertEquals(12, s.monthNames.size)
        }
    }
}
