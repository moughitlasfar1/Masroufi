package com.example

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.example.notifications.MasroufiNotificationManager
import com.example.notifications.QuickCaptureNotification
import com.example.security.AppSecurityManager
import com.example.ui.ShareCardBitmapRenderer
import com.example.ui.ShareCardFormatter
import com.example.ui.ShareCardLanguage
import com.example.ui.ShareCardPayload
import com.example.widget.MasroufiWidgetStore
import com.example.widget.WidgetSnapshot
import com.example.widget.WidgetStyle
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
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
class ShareCardAndPrivacyAuditTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppSecurityManager.setAppLockEnabled(context, false)
        QuickCaptureNotification.setEnabled(context, false)
    }

    // -------------------------------------------------------------------------
    // Step 2: Daret & Sellef WhatsApp Share Cards (FR / AR / Darija + Masking)
    // -------------------------------------------------------------------------

    @Test
    fun `Daret and Sellef share card models respect FR LTR, AR RTL, Darija RTL and hideAmounts masking`() {
        val daret = ShareCardPayload.DaretCard(
            groupName = "Daret Famille",
            turnNumber = 3,
            totalMembers = 10,
            amountMad = 1500.0,
            recipientName = "Karim",
            payoutDate = "2026-11-30"
        )
        val frVisible = ShareCardFormatter.buildCardModel(daret, ShareCardLanguage.FR, hideAmounts = false)
        assertFalse(frVisible.isRtl)
        assertEquals("Daret Famille", frVisible.headline)
        assertTrue(frVisible.rows.any { it.value.contains("1") && it.value.contains("500") })

        val arMasked = ShareCardFormatter.buildCardModel(daret, ShareCardLanguage.AR, hideAmounts = true)
        assertTrue(arMasked.isRtl)
        val arTextFallback = ShareCardFormatter.buildTextFallback(daret, ShareCardLanguage.AR, hideAmounts = true)
        assertTrue("Masked AR text fallback must contain ••••", arTextFallback.contains("••••"))
        assertFalse("Masked AR text fallback must not leak raw 1500 amount", arTextFallback.contains("1500"))
        assertFalse("Masked AR text fallback must not leak formatted 1 500 amount", arTextFallback.contains("1 500"))

        val sellef = ShareCardPayload.SellefCard(
            personName = "Youssef",
            totalBalanceMad = 850.0,
            remainingMad = 350.0,
            isLent = true
        )
        val darijaMasked = ShareCardFormatter.buildCardModel(sellef, ShareCardLanguage.DARIJA, hideAmounts = true)
        assertTrue(darijaMasked.isRtl)
        val darijaText = ShareCardFormatter.buildTextFallback(sellef, ShareCardLanguage.DARIJA, hideAmounts = true)
        assertTrue(darijaText.contains("Youssef"))
        assertTrue(darijaText.contains("••••"))
        assertFalse(darijaText.contains("850"))
        assertFalse(darijaText.contains("350"))
    }

    @Test
    fun `ShareCardBitmapRenderer renders valid PNG confined inside cacheDir shared_cards and cleans stale files`() {
        val payload = ShareCardPayload.DaretCard(
            groupName = "Daret Bureau",
            turnNumber = 2,
            totalMembers = 6,
            amountMad = 2000.0,
            recipientName = "Salma",
            payoutDate = "2026-12-05"
        )
        val model = ShareCardFormatter.buildCardModel(payload, ShareCardLanguage.AR, hideAmounts = true)
        val bmp1 = ShareCardBitmapRenderer.renderCardBitmap(context, model, isDarkStyle = true)
        assertEquals(1080, bmp1.width)
        assertEquals(720, bmp1.height)

        val file1 = ShareCardBitmapRenderer.saveCardPngToCache(context, bmp1, "daret_test")
        bmp1.recycle()
        assertTrue(file1.exists())
        assertTrue(file1.length() > 0L)
        val expectedDir = File(context.cacheDir, ShareCardBitmapRenderer.SHARED_CARDS_DIR_NAME)
        assertTrue(
            "Saved PNG must remain confined inside cacheDir/shared_cards/",
            file1.canonicalPath.startsWith(expectedDir.canonicalPath + File.separator)
        )

        // Saving a second card should scrub the previous PNG file so stale financial images don't accumulate
        val bmp2 = ShareCardBitmapRenderer.renderCardBitmap(context, model, isDarkStyle = false)
        val file2 = ShareCardBitmapRenderer.saveCardPngToCache(context, bmp2, "daret_test_2")
        bmp2.recycle()
        assertTrue(file2.exists())
        val pngFiles = expectedDir.listFiles { f -> f.isFile && f.name.endsWith(".png") }.orEmpty()
        assertEquals("Only the freshly shared PNG should remain in shared_cards/", 1, pngFiles.size)
        assertEquals(file2.name, pngFiles[0].name)
    }

    // -------------------------------------------------------------------------
    // Step 4: Widget, Notification & Storage Privacy Audit Verification
    // -------------------------------------------------------------------------

    @Test
    fun `WidgetStyle always masks amounts when App Lock is active regardless of privacyMode`() {
        for (mode in listOf("follow_app", "always_show", "always_hide", "hide_when_locked")) {
            val style = WidgetStyle(privacyMode = mode)
            assertTrue(
                "WidgetStyle($mode) must mask amounts when appLockEnabled=true",
                style.shouldMaskAmounts(snapshotHideAmounts = false, appLockEnabled = true)
            )
        }
    }

    @Test
    fun `QuickCaptureNotification masks safeToSpendToday and enforces VISIBILITY_PRIVATE under App Lock`() {
        org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        MasroufiWidgetStore.saveWidgetSnapshot(
            context,
            WidgetSnapshot(
                safeToSpendToday = 275.0,
                hideAmounts = false,
                appLockEnabled = true,
                language = "fr",
                currencySymbol = "DH"
            )
        )
        AppSecurityManager.setAppLockEnabled(context, true)
        QuickCaptureNotification.setEnabled(context, true)
        QuickCaptureNotification.setShowLeftTodayEnabled(context, true)
        QuickCaptureNotification.setShowLockScreenAmountsEnabled(context, true)

        val spec = QuickCaptureNotification.buildSpec(context, currentHourOfDay = 12)
        assertNotNull(spec)
        assertTrue("Notification body must mask amount when App Lock is enabled", spec!!.bodyText.contains("••••"))
        assertFalse("Notification body must not leak raw amount under App Lock", spec.bodyText.contains("275"))
        assertEquals(
            "Notification visibility must remain VISIBILITY_PRIVATE when App Lock is enabled",
            NotificationCompat.VISIBILITY_PRIVATE,
            spec.visibility
        )
    }

    @Test
    fun `MasroufiNotificationManager encrypts snapshot in SecureStore and removes legacy plaintext copy`() {
        val notifPrefs = context.getSharedPreferences("masroufi_notifications_prefs", Context.MODE_PRIVATE)
        val sampleJson = JSONObject().apply {
            put("lang", "fr")
            put("currency", "DH")
            put("hideAmounts", false)
            put("safeToday", 412.0)
            put("upcomingBills", JSONArray())
            put("activeDarets", JSONArray())
        }.toString()

        // Seed legacy plaintext preference and trigger migration read
        notifPrefs.edit().putString("notif_snapshot_json", sampleJson).commit()
        assertTrue(notifPrefs.contains("notif_snapshot_json"))

        val loaded = MasroufiNotificationManager.loadNotificationSnapshotRaw(context)
        assertNotNull(loaded)
        assertTrue(loaded!!.contains("412"))
        assertFalse(
            "Legacy plaintext notif_snapshot_json must be scrubbed after migration to SecureStore",
            notifPrefs.contains("notif_snapshot_json")
        )

        // Direct save must also keep plaintext prefs clean
        MasroufiNotificationManager.saveNotificationSnapshotRaw(context, sampleJson)
        assertFalse(notifPrefs.contains("notif_snapshot_json"))
    }
}
