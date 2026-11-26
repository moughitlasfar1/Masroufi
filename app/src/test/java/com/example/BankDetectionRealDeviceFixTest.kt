package com.example

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.domain.BankMessageParser
import com.example.domain.BankParseResult
import com.example.notifications.BankDetectionStore
import com.example.notifications.BankNotificationListenerService
import com.example.notifications.ManualSetup
import com.example.notifications.MasroufiDetectionLog
import com.example.notifications.ShizukuSetup
import com.example.widget.MasroufiWidgetStore
import com.masroufi.core.Money
import com.masroufi.core.MoneyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BankDetectionRealDeviceFixTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BankDetectionStore.deleteAllDetectedData(context)
        context.getSharedPreferences("masroufi_bank_detection_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("masroufi_widget_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    // =========================================================================
    // 1. Real Moroccan Bank SMS & Push Payloads + MoneyParser Edge Cases
    // =========================================================================

    @Test
    fun `CIH push with debite, no-space MAD, 4-digit year, and long Solde sentence parses cleanly`() {
        val result = BankMessageParser.parseDetailed(
            rawTitle = "CIH Mobile",
            rawBody = "Votre compte a été débité de 150,00MAD chez MARJANE le 26/11/2026. Nouveau solde disponible après opération est de 4 850,00 MAD.",
            sourcePackage = "com.b3g.cih.online",
            isoDate = "2026-11-26"
        )
        assertTrue("Expected Parsed, got $result", result is BankParseResult.Parsed)
        val parsed = (result as BankParseResult.Parsed).notification
        assertEquals(Money(15_000L), parsed.amount)
        assertEquals("MAD", parsed.currency)
        assertEquals("expense", parsed.direction)
        assertEquals("MARJANE", parsed.merchant)
        assertEquals("cih", parsed.bankTemplateId)
    }

    @Test
    fun `Pocket Bank push with Confirmation, masked card 4582, no-space DH and trailing dot parses cleanly`() {
        val result = BankMessageParser.parseDetailed(
            rawTitle = "Pocket Bank",
            rawBody = "Confirmation paiement carte ****4582 de 85.50DH chez BIM MAROC.",
            sourcePackage = "ma.gbp.pocketbank",
            isoDate = "2026-11-26"
        )
        assertTrue("Expected Parsed, got $result", result is BankParseResult.Parsed)
        val parsed = (result as BankParseResult.Parsed).notification
        assertEquals(Money(8_550L), parsed.amount)
        assertEquals("DH", parsed.currency)
        assertEquals("expense", parsed.direction)
        assertEquals("BIM MAROC", parsed.merchant)
        assertEquals("bp", parsed.bankTemplateId)
    }

    @Test
    fun `Attijari, BMCE, CFG, Barid, CDM, and SGMA real package payloads parse with exact centimes`() {
        val attijari = BankMessageParser.parse(
            rawTitle = "Attijari Mobile",
            rawBody = "Paiement TPE de 1 250,00 DHS chez DECATHLON CASABLANCA le 26/11/2026",
            sourcePackage = "com.attijariwafabank.attijarimobile",
            isoDate = "2026-11-26"
        )
        assertNotNull(attijari)
        assertEquals(Money(125_000L), attijari!!.amount)
        assertEquals("expense", attijari.direction)
        assertEquals("attijari", attijari.bankTemplateId)

        val bmce = BankMessageParser.parse(
            rawTitle = "BMCE Direct",
            rawBody = "Votre carte 4582 a été débitée de 49,99MAD chez GLOVO.",
            sourcePackage = "com.bmce.bmcedirect",
            isoDate = "2026-11-26"
        )
        assertNotNull(bmce)
        assertEquals(Money(4_999L), bmce!!.amount)
        assertEquals("expense", bmce.direction)
        assertEquals("boa", bmce.bankTemplateId)

        val cfg = BankMessageParser.parse(
            rawTitle = "CFG Bank",
            rawBody = "Opération prélevée de 300,00 MAD chez ORANGE MAROC.",
            sourcePackage = "com.cfgbank.app",
            isoDate = "2026-11-26"
        )
        assertNotNull(cfg)
        assertEquals(Money(30_000L), cfg!!.amount)
        assertEquals("cfg", cfg.bankTemplateId)

        val cdm = BankMessageParser.parse(
            rawTitle = "MyCDM",
            rawBody = "Compte crédité de 7 500,00 MAD - Salaire novembre.",
            sourcePackage = "ma.cdm.mycdm",
            isoDate = "2026-11-26"
        )
        assertNotNull(cdm)
        assertEquals(Money(750_000L), cdm!!.amount)
        assertEquals("income", cdm.direction)
        assertEquals("cdm", cdm.bankTemplateId)

        val sgma = BankMessageParser.parse(
            rawTitle = "SG Maroc",
            rawBody = "Achat en ligne 199.90MAD chez JUMIA MAROC",
            sourcePackage = "com.sgma.sgmaroc",
            isoDate = "2026-11-26"
        )
        assertNotNull(sgma)
        assertEquals(Money(19_990L), sgma!!.amount)
        assertEquals("sgma", sgma.bankTemplateId)

        val baridArabic = BankMessageParser.parse(
            rawTitle = "بريد بنك",
            rawBody = "تم اقتطاع 320,00 درهم لدى أسواق السلام. الرصيد المتاح 2 400,00 درهم.",
            sourcePackage = "ma.abb.baridbankmobile",
            isoDate = "2026-11-26"
        )
        assertNotNull(baridArabic)
        assertEquals(Money(32_000L), baridArabic!!.amount)
        assertEquals("expense", baridArabic.direction)
        assertEquals("barid", baridArabic.bankTemplateId)
    }

    @Test
    fun `MoneyParser directly parses no-space currency tokens and trailing sentence dots`() {
        assertEquals(15_000L, MoneyParser.parseMoroccanAmountToCentimes("150,00MAD"))
        assertEquals(8_550L, MoneyParser.parseMoroccanAmountToCentimes("85.50DH."))
        assertEquals(25_000L, MoneyParser.parseMoroccanAmountToCentimes("250DHS."))
        assertEquals(125_050L, MoneyParser.parseMoroccanAmountToCentimes("1 250,50 MAD."))
        assertEquals(15_000L, MoneyParser.parseMoroccanAmountToCentimes("MAD150,00"))
    }

    @Test
    fun `Real OTP messages remain strictly rejected with REASON_OTP`() {
        val otpFr = BankMessageParser.parseDetailed(
            rawTitle = "CIH Bank",
            rawBody = "Code 482910 pour valider votre paiement de 2000,00 MAD chez MARJANE",
            sourcePackage = "com.b3g.cih.online"
        )
        assertTrue(otpFr is BankParseResult.Rejected)
        assertEquals(MasroufiDetectionLog.REASON_OTP, (otpFr as BankParseResult.Rejected).reasonCode)

        val otp3ds = BankMessageParser.parseDetailed(
            rawTitle = "Attijari Mobile",
            rawBody = "3DS OTP 991234 pour confirmer votre opération de 150 MAD",
            sourcePackage = "com.attijariwafabank.attijarimobile"
        )
        assertTrue(otp3ds is BankParseResult.Rejected)
        assertEquals(MasroufiDetectionLog.REASON_OTP, (otp3ds as BankParseResult.Rejected).reasonCode)
    }

    // =========================================================================
    // 2. Notification Extras Extraction (EXTRA_BIG_TEXT, EXTRA_SUB_TEXT, EXTRA_TEXT_LINES)
    // =========================================================================

    @Test
    fun `extractIndividualMessages reads EXTRA_BIG_TEXT, EXTRA_SUB_TEXT, and EXTRA_TEXT_LINES safely`() {
        val now = 1_795_090_000_000L

        // Case 1: Truncated EXTRA_TEXT + full EXTRA_BIG_TEXT + EXTRA_SUB_TEXT
        val bigTextMsgs = BankDetectionStore.extractIndividualMessages(
            title = "CIH Mobile",
            text = "Votre compte a été débité...",
            bigText = "Votre compte a été débité de 120,00 MAD chez MARJANE",
            messagingStyleMessages = emptyList(),
            fallbackPostTimeMillis = now,
            subText = "Compte Chèque"
        )
        assertEquals(1, bigTextMsgs.size)
        assertTrue(bigTextMsgs.first().body.contains("120,00 MAD chez MARJANE"))
        assertTrue(bigTextMsgs.first().body.contains("Compte Chèque"))

        // Case 2: InboxStyle EXTRA_TEXT_LINES splits multi-line stacked bank/SMS alerts
        val inboxMsgs = BankDetectionStore.extractIndividualMessages(
            title = "Messages",
            text = "2 nouveaux messages",
            bigText = "",
            messagingStyleMessages = emptyList(),
            fallbackPostTimeMillis = now,
            textLines = listOf(
                "CIH Bank: Paiement carte de 45,00 MAD chez ACIMA",
                "BCP: Retrait GAB de 300,00 MAD le 26/11"
            )
        )
        assertEquals(2, inboxMsgs.size)
        assertTrue(inboxMsgs[0].body.contains("45,00 MAD"))
        assertTrue(inboxMsgs[1].body.contains("300,00 MAD"))

        // Verify end-to-end storage of both InboxStyle lines via Google Messages
        val smsPkg = "com.google.android.apps.messaging"
        BankDetectionStore.setPackageAllowed(context, smsPkg, true)
        inboxMsgs.forEach { msg ->
            BankDetectionStore.processIncomingNotification(
                context = context,
                sourcePackage = smsPkg,
                rawTitle = msg.title,
                rawBody = msg.body,
                nowMillis = msg.timestampMillis
            )
        }
        val pending = BankDetectionStore.loadPendingItems(context, now + 1000L)
        assertEquals(2, pending.size)
        assertEquals(setOf(Money(4_500L), Money(30_000L)), pending.map { it.amount }.toSet())
    }

    // =========================================================================
    // 3. Legacy Float autoConfirmCap & Widget Float Prefs Migration Safety
    // =========================================================================

    @Test
    fun `legacy Float autoConfirmCap migrates cleanly to Long centimes and removes old Float key`() {
        val prefs = context.getSharedPreferences("masroufi_bank_detection_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("auto_confirm_max_cap_centimes")
            .putFloat("auto_confirm_max_cap", 350.5f)
            .commit()

        val migrated = BankDetectionStore.getAutoConfirmMaxAmount(context)
        assertEquals(Money(35_050L), migrated)
        assertEquals(35_050L, prefs.getLong("auto_confirm_max_cap_centimes", -1L))
        assertFalse("Legacy Float key should be removed after migration", prefs.contains("auto_confirm_max_cap"))
    }

    @Test
    fun `corrupted pref types in autoConfirmCap or widget legacy floats never crash`() {
        val bankPrefs = context.getSharedPreferences("masroufi_bank_detection_prefs", Context.MODE_PRIVATE)
        bankPrefs.edit()
            .putString("auto_confirm_max_cap_centimes", "not_a_number")
            .commit()
        assertEquals(Money.fromDh(200), BankDetectionStore.getAutoConfirmMaxAmount(context))

        val widgetPrefs = context.getSharedPreferences("masroufi_widget_prefs", Context.MODE_PRIVATE)
        widgetPrefs.edit()
            .putFloat("today_spend", Float.NaN)
            .putString("safe_today", "bad_float")
            .commit()
        val snap = MasroufiWidgetStore.loadSnapshot(context, nowIso = "2026-11-26")
        assertEquals(Money.ZERO, snap.todaySpend)
        assertEquals(Money.ZERO, snap.safeToSpendToday)
    }

    // =========================================================================
    // 4. Legacy Allowlist Package Upgrade Safeguard (#3)
    // =========================================================================

    @Test
    fun `legacy bank package IDs in allowlist transparently allow real Play Store counterparts`() {
        val prefs = context.getSharedPreferences("masroufi_bank_detection_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(
                "allowed_packages_set",
                setOf(
                    "com.cih.cihmobile",
                    "com.bcp.pocketbank",
                    "com.bmce.agencedirecte",
                    "com.cfgbank.mobile",
                    "ma.baridbank.mobile"
                )
            )
            .commit()

        assertTrue(BankDetectionStore.isPackageAllowed(context, "com.b3g.cih.online"))
        assertTrue(BankDetectionStore.isPackageAllowed(context, "ma.gbp.pocketbank"))
        assertTrue(BankDetectionStore.isPackageAllowed(context, "com.bmce.bmcedirect"))
        assertTrue(BankDetectionStore.isPackageAllowed(context, "com.cfgbank.app"))
        assertTrue(BankDetectionStore.isPackageAllowed(context, "ma.abb.baridbankmobile"))

        // Disabling the real package also clears its legacy alias so the user can turn it off
        BankDetectionStore.setPackageAllowed(context, "com.b3g.cih.online", false)
        assertFalse(BankDetectionStore.isPackageAllowed(context, "com.b3g.cih.online"))
    }

    // =========================================================================
    // 5. ComponentName Listener Matching, Shizuku Command & Active UserId
    // =========================================================================

    @Test
    fun `isNotificationListenerGranted matches full and short ComponentNames for com_masroufi_app`() {
        val expected = ComponentName("com.masroufi.app", BankNotificationListenerService::class.java.name)

        // Full flattened component name
        assertTrue(
            ManualSetup.isListenerEnabledInSettingValue(
                "com.other.app/.OtherListener:com.masroufi.app/com.example.notifications.BankNotificationListenerService",
                expected
            )
        )
        // Shortened relative class form
        assertTrue(
            ManualSetup.isListenerEnabledInSettingValue(
                "com.masroufi.app/.notifications.BankNotificationListenerService",
                expected
            )
        )
        // Unrelated package or wrong service class must return false
        assertFalse(
            ManualSetup.isListenerEnabledInSettingValue(
                "com.other.app/com.example.notifications.BankNotificationListenerService",
                expected
            )
        )
        assertFalse(
            ManualSetup.isListenerEnabledInSettingValue(
                "com.masroufi.app/com.example.OtherService",
                expected
            )
        )

        // End-to-end via Settings.Secure in Robolectric
        val actualComponent = ComponentName(context, BankNotificationListenerService::class.java)
        Settings.Secure.putString(
            context.contentResolver,
            "enabled_notification_listeners",
            actualComponent.flattenToString()
        )
        assertTrue(ManualSetup.isNotificationListenerGranted(context))

        Settings.Secure.putString(
            context.contentResolver,
            "enabled_notification_listeners",
            "com.other.app/.Listener"
        )
        assertFalse(ManualSetup.isNotificationListenerGranted(context))
    }

    @Test
    fun `ShizukuSetup allow_listener command does not double-prefix package name`() {
        val cmds = ShizukuSetup.buildAllowlistedSetupCommands(context)
        val allowCmd = cmds.first { it.startsWith("cmd notification allow_listener ") }
        val componentArg = allowCmd.removePrefix("cmd notification allow_listener ")
        val slashes = componentArg.count { it == '/' }
        assertEquals("Expected single pkg/class separator in '$allowCmd'", 1, slashes)
        assertTrue(componentArg.startsWith("${context.packageName}/"))
        assertTrue(componentArg.endsWith(BankNotificationListenerService::class.java.name))
    }

    @Test
    fun `MasroufiWidgetStore resolves persisted active userId when Firebase auth is absent`() {
        MasroufiWidgetStore.setActiveUserId(context, "google_uid_test_123")
        assertEquals("google_uid_test_123", MasroufiWidgetStore.resolveActiveUserId(context))
    }
}
