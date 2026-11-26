package com.example

import com.example.domain.BankMessageParser
import com.example.domain.BankParseResult
import com.example.notifications.MasroufiDetectionLog
import com.masroufi.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BankMessageParserAuditTest {

    @Test
    fun `D2 balance before debit picks transaction amount instead of balance`() {
        val result = BankMessageParser.parseDetailed(
            rawTitle = "CIH Bank",
            rawBody = "Solde 5 230,45 MAD. Achat carte 120,00 MAD chez MARJANE le 19/11",
            sourcePackage = "com.cih.cihmobile",
            isoDate = "2026-11-19"
        )
        assertTrue(result is BankParseResult.Parsed)
        val parsed = (result as BankParseResult.Parsed).notification
        assertEquals(Money.fromDh(120), parsed.amount)
        assertEquals("expense", parsed.direction)
        assertEquals("MARJANE", parsed.merchant)
    }

    @Test
    fun `D2 balance-only notification is rejected with no_amount`() {
        val result = BankMessageParser.parseDetailed(
            rawTitle = "Attijari Mobile",
            rawBody = "Votre solde disponible est de 4 850,00 MAD. Plafond carte 10 000 MAD.",
            sourcePackage = "ma.attijariwafabank.lbankalik",
            isoDate = "2026-11-19"
        )
        assertTrue(result is BankParseResult.Rejected)
        assertEquals(MasroufiDetectionLog.REASON_NO_AMOUNT, (result as BankParseResult.Rejected).reasonCode)
    }

    @Test
    fun `D3 Achat carte 85 MAD does not match at inside Achat and leaves merchant empty with needsReview`() {
        val parsed = BankMessageParser.parse(
            rawTitle = "CIH Bank",
            rawBody = "Achat carte 85 MAD le 15/11",
            sourcePackage = "com.cih.cihmobile",
            isoDate = "2026-11-15"
        )
        assertNotNull(parsed)
        assertEquals(Money.fromDh(85), parsed!!.amount)
        assertEquals("", parsed.merchant)
        assertFalse(parsed.merchant.contains("carte", ignoreCase = true))
        assertTrue(parsed.needsReview)
    }

    @Test
    fun `D3 labeled Commercant field extracts clean merchant name`() {
        val parsed = BankMessageParser.parse(
            rawTitle = "BMCE Direct",
            rawBody = "Transaction de 210,50 MAD. Commerçant: ACIMA MAARIF le 19/11",
            sourcePackage = "com.bmce.agencedirecte",
            isoDate = "2026-11-19"
        )
        assertNotNull(parsed)
        assertEquals(Money(21050L), parsed!!.amount)
        assertEquals("ACIMA MAARIF", parsed.merchant)
        assertFalse(parsed.needsReview)
    }

    @Test
    fun `D4 outgoing transfer, incoming transfer, and refund directions are detected accurately`() {
        val outgoing = BankMessageParser.parse(
            rawTitle = "CIH Bank",
            rawBody = "Virement émis de 1 500,00 MAD vers compte tiers",
            sourcePackage = "com.cih.cihmobile",
            isoDate = "2026-11-19"
        )
        assertNotNull(outgoing)
        assertEquals("expense", outgoing!!.direction)
        assertEquals(Money.fromDh(1500), outgoing.amount)

        val incoming = BankMessageParser.parse(
            rawTitle = "Banque Populaire",
            rawBody = "Vous avez reçu un virement de 6 500,00 MAD sur votre compte",
            sourcePackage = "com.bcp.pocketbank",
            isoDate = "2026-11-19"
        )
        assertNotNull(incoming)
        assertEquals("income", incoming!!.direction)
        assertEquals(Money.fromDh(6500), incoming.amount)

        val refund = BankMessageParser.parse(
            rawTitle = "CIH Bank",
            rawBody = "Remboursement achat carte de 149,00 MAD chez ZARA MAROC",
            sourcePackage = "com.cih.cihmobile",
            isoDate = "2026-11-19"
        )
        assertNotNull(refund)
        assertEquals("income", refund!!.direction)
        assertEquals(Money.fromDh(149), refund.amount)
    }

    @Test
    fun `D4 real BCP RETRAIT GAB SMS parses as ATM transfer`() {
        val parsed = BankMessageParser.parse(
            rawTitle = "BCP",
            rawBody = "RETRAIT GAB MCPLATINUM ONOFF A.B.C.P BP 500,00 MAD le 19/11",
            sourcePackage = "com.google.android.apps.messaging",
            isoDate = "2026-11-19"
        )
        assertNotNull(parsed)
        assertEquals("transfer", parsed!!.direction)
        assertEquals(Money.fromDh(500), parsed.amount)
        assertEquals("Retrait GAB", parsed.merchant)
        assertEquals("bp", parsed.bankTemplateId)
    }

    @Test
    fun `D7 3D Secure confirmation without code is accepted while 3D Secure OTP with 6 digits is rejected`() {
        val confirmedPayment = BankMessageParser.parse(
            rawTitle = "CIH Bank",
            rawBody = "Paiement 3D Secure de 320,00 MAD chez GLOVO effectué",
            sourcePackage = "com.cih.cihmobile",
            isoDate = "2026-11-19"
        )
        assertNotNull("Completed 3D Secure payment with no OTP digits should parse", confirmedPayment)
        assertEquals(Money.fromDh(320), confirmedPayment!!.amount)

        val otp3ds = BankMessageParser.parseDetailed(
            rawTitle = "CIH Bank",
            rawBody = "3D Secure: utilisez 482910 pour paiement de 320,00 MAD chez GLOVO",
            sourcePackage = "com.cih.cihmobile",
            isoDate = "2026-11-19"
        )
        assertTrue(otp3ds is BankParseResult.Rejected)
        assertEquals(MasroufiDetectionLog.REASON_OTP, (otp3ds as BankParseResult.Rejected).reasonCode)

        val validationCodeWithCurrency = BankMessageParser.parseDetailed(
            rawTitle = "Attijariwafa",
            rawBody = "Code 739104 pour valider votre paiement de 200 MAD chez MARJANE",
            sourcePackage = "ma.attijariwafabank.lbankalik",
            isoDate = "2026-11-19"
        )
        assertTrue(validationCodeWithCurrency is BankParseResult.Rejected)
        assertEquals(MasroufiDetectionLog.REASON_OTP, (validationCodeWithCurrency as BankParseResult.Rejected).reasonCode)
    }

    @Test
    fun `D7 Arabic debit with balance picks debit amount and rejects Arabic OTP`() {
        val arDebit = BankMessageParser.parse(
            rawTitle = "بريد بنك",
            rawBody = "الرصيد 3500 درهم. تم خصم ١٨٠.٥٠ درهم لدى مرجان",
            sourcePackage = "ma.baridbank.mobile",
            isoDate = "2026-11-19"
        )
        assertNotNull(arDebit)
        assertEquals(Money(18050L), arDebit!!.amount)
        assertEquals("expense", arDebit.direction)

        val arOtp = BankMessageParser.parse(
            rawTitle = "بريد بنك",
            rawBody = "رمز 554120 لتأكيد عملية بمبلغ 200 درهم",
            sourcePackage = "ma.baridbank.mobile",
            isoDate = "2026-11-19"
        )
        assertNull(arOtp)
    }

    @Test
    fun `D11 template matching does not false-match short substrings like bp or boa`() {
        val parsed = BankMessageParser.parse(
            rawTitle = "Aboard Shop",
            rawBody = "Paiement de 95,00 MAD chez SUBPRIME STORE",
            sourcePackage = "com.random.aboardapp",
            isoDate = "2026-11-19"
        )
        assertNotNull(parsed)
        assertEquals("generic", parsed!!.bankTemplateId)
    }

    @Test
    fun `D1 dedupe hash ignores notificationKey and derives isoDate from postTimeMillis`() {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            set(2026, Calendar.NOVEMBER, 19, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val ts = cal.timeInMillis

        val a = BankMessageParser.parse(
            rawTitle = "CIH Bank",
            rawBody = "Paiement par carte de 85,00 MAD chez MARJANE",
            sourcePackage = "com.google.android.apps.messaging",
            notificationKey = "0|com.google.android.apps.messaging|1|thread_A|1000",
            postTimeMillis = ts
        )
        val b = BankMessageParser.parse(
            rawTitle = "CIH Bank",
            rawBody = "Paiement par carte de 85,00 MAD chez MARJANE",
            sourcePackage = "com.google.android.apps.messaging",
            notificationKey = "0|com.google.android.apps.messaging|999|thread_Z|1000",
            postTimeMillis = ts + 5_000L
        )
        val c = BankMessageParser.parse(
            rawTitle = "CIH Bank",
            rawBody = "Paiement par carte de 120,00 MAD chez CARREFOUR",
            sourcePackage = "com.google.android.apps.messaging",
            notificationKey = "0|com.google.android.apps.messaging|1|thread_A|1000",
            postTimeMillis = ts + 10_000L
        )
        assertNotNull(a)
        assertNotNull(b)
        assertNotNull(c)
        assertEquals("2026-11-19", a!!.isoDate)
        assertEquals("Same notification with different key must share dedupeHash", a.dedupeHash, b!!.dedupeHash)
        assertNotEquals("Different SMS in same thread must have distinct dedupeHash", a.dedupeHash, c!!.dedupeHash)
    }
}
