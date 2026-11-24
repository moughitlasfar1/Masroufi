package com.masroufi.fiscal

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
            350.0,
            FiscalRates.vignetteAnnualAmountMad(
                FiscalRates.HorsepowerBand.UNDER_8_CV,
                FiscalRates.FuelType.ESSENCE
            )!!,
            0.001
        )
        assertEquals(
            700.0,
            FiscalRates.vignetteAnnualAmountMad(
                FiscalRates.HorsepowerBand.UNDER_8_CV,
                FiscalRates.FuelType.DIESEL
            )!!,
            0.001
        )
        assertEquals(
            650.0,
            FiscalRates.vignetteAnnualAmountMad(
                FiscalRates.HorsepowerBand.CV_8_TO_10,
                FiscalRates.FuelType.ESSENCE
            )!!,
            0.001
        )
        assertEquals(
            1500.0,
            FiscalRates.vignetteAnnualAmountMad(
                FiscalRates.HorsepowerBand.CV_8_TO_10,
                FiscalRates.FuelType.DIESEL
            )!!,
            0.001
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
            annualAmountMad = 1500.0,
            savedAmountMad = 300.0,
            dueMonth = 12,
            dueYear = 2026,
            currentYear = 2026,
            currentMonth = 6,
            rollForwardIfPassed = false
        )
        assertEquals(1500.0, breakdown.annualAmountMad, 0.001)
        assertEquals(300.0, breakdown.savedAmountMad, 0.001)
        assertEquals(1200.0, breakdown.remainingToSaveMad, 0.001)
        assertEquals(6, breakdown.monthsRemaining)
        assertEquals(200.0, breakdown.monthlyReserveMad, 0.001)
        assertEquals(0.2f, breakdown.progressFraction, 0.001f)
        assertFalse(breakdown.isDueThisMonth)
        assertFalse(breakdown.isDueDatePassed)
        assertFalse(breakdown.rolledToNextYear)
        assertEquals("2026-12-01", breakdown.dueDateIso)
    }

    @Test
    fun `edge case when due month is this month sets monthsRemaining to 1 and full remaining reserve`() {
        val breakdown = AnnualBillCalculator.computeBreakdown(
            annualAmountMad = 700.0,
            savedAmountMad = 250.0,
            dueMonth = 1,
            dueYear = 2026,
            currentYear = 2026,
            currentMonth = 1,
            rollForwardIfPassed = false
        )
        assertEquals(1, breakdown.monthsRemaining)
        assertEquals(450.0, breakdown.monthlyReserveMad, 0.001)
        assertTrue(breakdown.isDueThisMonth)
        assertFalse(breakdown.isDueDatePassed)
        assertFalse(breakdown.rolledToNextYear)
    }

    @Test
    fun `edge case when due date passed handles both overdue existing bill and next-year rollover`() {
        // Existing unpaid bill whose due date already passed
        val overdue = AnnualBillCalculator.computeBreakdown(
            annualAmountMad = 650.0,
            savedAmountMad = 150.0,
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
        assertEquals(500.0, overdue.monthlyReserveMad, 0.001)

        // New bill whose chosen due month already passed this year -> rolls forward to next year
        val rolled = AnnualBillCalculator.computeBreakdown(
            annualAmountMad = 900.0,
            savedAmountMad = 0.0,
            dueMonth = 1,
            dueYear = 2026,
            currentYear = 2026,
            currentMonth = 4,
            rollForwardIfPassed = true
        )
        assertTrue(rolled.rolledToNextYear)
        assertFalse(rolled.isDueDatePassed)
        assertEquals(2027, rolled.dueYear)
        assertEquals(9, rolled.monthsRemaining) // April 2026 -> January 2027 = 9 months
        assertEquals(100.0, rolled.monthlyReserveMad, 0.001)
    }

    @Test
    fun `fully funded annual bill reports 0 remaining and 0 monthly reserve`() {
        val funded = AnnualBillCalculator.computeBreakdown(
            annualAmountMad = 1200.0,
            savedAmountMad = 1400.0,
            dueMonth = 10,
            dueYear = 2026,
            currentYear = 2026,
            currentMonth = 5
        )
        assertEquals(0.0, funded.remainingToSaveMad, 0.001)
        assertEquals(0.0, funded.monthlyReserveMad, 0.001)
        assertEquals(1.0f, funded.progressFraction, 0.001f)
    }

    @Test
    fun `FiscalRepository serializes annual bills and maps backing goal plus recurring monthly bill`() {
        val bill = AnnualBillItem(
            id = "vig_2027",
            preset = AnnualBillPreset.VIGNETTE,
            title = "Vignette Clio",
            annualAmountMad = 700.0,
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
            existingGoalSavedAmount = 100.0,
            payCycleDay = 5,
            strings = FiscalStrings.forLang("fr"),
            currentYear = 2026,
            currentMonth = 7
        )
        assertEquals("goal", goal.itemType)
        assertEquals("goal_fiscal_vig_2027", goal.id)
        assertEquals(700.0, goal.amount, 0.001)
        assertEquals(100.0, goal.secondaryAmount, 0.001)

        assertEquals("bill", recurringBill.itemType)
        assertEquals("bill_fiscal_vig_2027", recurringBill.id)
        // Remaining 600 DH over 6 months (Jul 2026 -> Jan 2027) = 100 DH/month
        assertEquals(100.0, recurringBill.amount, 0.001)
        assertEquals(5, recurringBill.dayOrTurn)
    }

    // -------------------------------------------------------------------------
    // Step 5: Zakat & HijrahDate Hawl Calculator
    // -------------------------------------------------------------------------

    @Test
    fun `zakat nisab threshold uses 85g gold or 595g silver from editable offline prices`() {
        val goldCfg = ZakatConfig(
            nisabStandard = NisabStandard.GOLD_85G,
            goldPriceMadPerGram = 900.0,
            silverPriceMadPerGram = 12.0
        )
        assertEquals(76_500.0, ZakatCalculator.computeNisabThresholdMad(goldCfg), 0.001)

        val silverCfg = goldCfg.copy(nisabStandard = NisabStandard.SILVER_595G)
        assertEquals(7_140.0, ZakatCalculator.computeNisabThresholdMad(silverCfg), 0.001)
    }

    @Test
    fun `zakat calculation handles below nisab, hawl running, and due now using HijrahDate`() {
        val config = ZakatConfig(
            nisabStandard = NisabStandard.GOLD_85G,
            goldPriceMadPerGram = 900.0, // Nisab = 76,500 MAD
            silverPriceMadPerGram = 11.0,
            pricesAsOfIso = "2026-01-01",
            hawlStartIso = "2026-01-01"
        )

        // 1. Below Nisab: accounts 50,000 + savings 30,000 - borrowed Sellef 10,000 = 70,000 < 76,500
        val below = ZakatCalculator.calculate(
            accountsTotalMad = 50_000.0,
            savingsTotalMad = 30_000.0,
            shortTermDebtsOwedMad = 10_000.0,
            config = config,
            todayIso = "2026-06-01"
        )
        assertEquals(70_000.0, below.eligibleWealthMad, 0.001)
        assertEquals(0.0, below.zakatDueMad, 0.001)
        assertEquals(HawlStatus.BELOW_NISAB, below.status)

        // 2. Hawl Running: accounts 80,000 + savings 40,000 - borrowed Sellef 20,000 = 100,000 >= 76,500
        // 2.5% of 100,000 = 2,500 MAD
        val running = ZakatCalculator.calculate(
            accountsTotalMad = 80_000.0,
            savingsTotalMad = 40_000.0,
            shortTermDebtsOwedMad = 20_000.0,
            config = config,
            todayIso = "2026-06-01"
        )
        assertEquals(100_000.0, running.eligibleWealthMad, 0.001)
        assertEquals(2_500.0, running.zakatDueMad, 0.001)
        assertEquals(HawlStatus.HAWL_RUNNING, running.status)
        // One Hijri year from 2026-01-01 is ~354–355 days (~2026-12-21), so on 2026-06-01 ~200+ days remain
        assertTrue("Expected daysRemaining in 190..215, got ${running.daysRemaining}", running.daysRemaining in 190..215)
        assertTrue("Hijri start label should end with H", running.hawlHijriStartLabel.endsWith("H"))
        assertTrue("Hijri due label should end with H", running.hawlHijriDueLabel.endsWith("H"))
        assertTrue("Monthly reserve suggestion should be > 0", running.monthlyReserveSuggestionMad > 0.0)

        // 3. Due Now: after 1 full lunar year has passed (2027-01-05 > ~2026-12-21)
        val dueNow = ZakatCalculator.calculate(
            accountsTotalMad = 100_000.0,
            savingsTotalMad = 0.0,
            shortTermDebtsOwedMad = 0.0,
            config = config,
            todayIso = "2027-01-05"
        )
        assertEquals(HawlStatus.DUE_NOW, dueNow.status)
        assertEquals(0, dueNow.daysRemaining)
        assertEquals(2_500.0, dueNow.zakatDueMad, 0.001)
        assertEquals(2_500.0, dueNow.monthlyReserveSuggestionMad, 0.001)
    }

    @Test
    fun `zakat config JSON round-trip and 4-language fiscal strings are complete`() {
        val cfg = ZakatConfig(
            nisabStandard = NisabStandard.SILVER_595G,
            goldPriceMadPerGram = 950.0,
            silverPriceMadPerGram = 13.5,
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
