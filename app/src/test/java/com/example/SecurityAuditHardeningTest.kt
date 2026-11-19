package com.example

import android.content.Context
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.example.domain.Translations
import com.example.notifications.MasroufiNotificationManager
import com.example.security.AppSecurityManager
import com.example.security.SecureStore
import com.example.ui.generateSignInNonce
import com.example.ui.verifyIdTokenNonce
import com.example.voice.MoonshineVoiceController
import com.example.widget.AssistantEntryValidator
import com.example.widget.MasroufiWidgetStore
import com.example.widget.QuickCaptureReplayGuard
import com.example.widget.WidgetSnapshot
import com.example.widget.WidgetStyle
import com.example.domain.VoiceParsedTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Audit verification tests for S2, S3, S4, S7, S8, S9, and S10.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityAuditHardeningTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("masroufi_security_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("masroufi_widget_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        AssistantEntryValidator.resetRateLimitForTest()
        QuickCaptureReplayGuard.resetForTest()
    }

    @Test
    fun `S2 AssistantEntryValidator enforces amount bounds, control-char stripping, 60-char cap, and 10s rate limit`() {
        // Amount bounds: strictly (0, 100_000]
        assertEquals(null, AssistantEntryValidator.validateAmountOrNull(null))
        assertEquals(45.5, AssistantEntryValidator.validateAmountOrNull("45,5")!!, 0.001)
        assertTrue(AssistantEntryValidator.validateAmountOrNull("0")!!.isNaN())
        assertTrue(AssistantEntryValidator.validateAmountOrNull("-25")!!.isNaN())
        assertTrue(AssistantEntryValidator.validateAmountOrNull("100001")!!.isNaN())
        assertTrue(AssistantEntryValidator.validateAmountOrNull("abc")!!.isNaN())

        // Control chars stripped & length capped at 60
        val dirty = "Café\u0000\u0007\n\u202E" + "x".repeat(100)
        val clean = AssistantEntryValidator.sanitizeText(dirty)
        assertFalse(clean.any { Character.isISOControl(it) })
        assertFalse(clean.contains('\u202E'))
        assertTrue(clean.length <= AssistantEntryValidator.MAX_TEXT_LENGTH)

        // Rate limit: 1 req / 10s
        assertTrue(AssistantEntryValidator.tryAcquireRateLimit(nowElapsedMs = 1_000L))
        assertFalse(AssistantEntryValidator.tryAcquireRateLimit(nowElapsedMs = 5_000L))
        assertFalse(AssistantEntryValidator.tryAcquireRateLimit(nowElapsedMs = 10_999L))
        assertTrue(AssistantEntryValidator.tryAcquireRateLimit(nowElapsedMs = 11_000L))
    }

    @Test
    fun `S2 QuickCaptureReplayGuard blocks identical batch within 60 seconds and allows after`() {
        val batch = listOf(
            VoiceParsedTransaction(
                rawTranscript = "Café 15",
                amount = 15.0,
                description = "Café",
                envelopeKey = "cafe",
                categoryName = "Café & Sorties"
            )
        )
        assertTrue(QuickCaptureReplayGuard.tryConsumeBatch(batch, nowElapsedMs = 100_000L))
        assertFalse(QuickCaptureReplayGuard.tryConsumeBatch(batch, nowElapsedMs = 130_000L))
        assertTrue(QuickCaptureReplayGuard.tryConsumeBatch(batch, nowElapsedMs = 160_001L))
    }

    @Test
    fun `S3 tampered or missing pin_lockout_seal triggers 1-hour maximum lockout`() {
        assertTrue(AppSecurityManager.setPin(context, "258046", iterations = 10_000))
        val now = 500_000L
        // 1 failed attempt writes a valid seal
        assertFalse(AppSecurityManager.verifyPin(context, "000000", nowMs = now))
        assertTrue(AppSecurityManager.verifyLockoutSeal(context))
        assertEquals(0, AppSecurityManager.getRemainingLockoutSeconds(context, nowMs = now))

        // Attacker edits SharedPreferences to clear or tamper with pin_lockout_seal
        context.getSharedPreferences("masroufi_security_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("pin_lockout_seal", "raw1:tampered_seal_data")
            .commit()

        assertFalse(AppSecurityManager.verifyLockoutSeal(context))
        val remainingSec = AppSecurityManager.getRemainingLockoutSeconds(context, nowMs = now + 1000L)
        assertEquals("Tampered lockout seal must enforce 1-hour (3600s) lockout", 3600, remainingSec)
        assertFalse("Even correct PIN must be blocked during tamper-enforced lockout", AppSecurityManager.verifyPin(context, "258046", nowMs = now + 2000L))
    }

    @Test
    fun `S4 SecureStore encrypts values, migrates and wipes legacy plaintext widget snapshot, and masks widgets under app lock`() {
        // Legacy plaintext widget JSON in SharedPreferences
        val legacyPrefs = context.getSharedPreferences("masroufi_widget_prefs", Context.MODE_PRIVATE)
        val snap = WidgetSnapshot(
            snapshotDateIso = "2026-11-19",
            todaySpend = 123.0,
            safeToSpendToday = 456.0,
            remainingBudget = 4560.0,
            daysRemainingInCycle = 10
        )
        legacyPrefs.edit().putString("full_widget_snapshot_json", snap.toJson().toString()).commit()

        // Saving/loading migrates to SecureStore and removes plaintext fields
        val loaded = MasroufiWidgetStore.loadSnapshot(context, "2026-11-19")
        assertEquals(123.0, loaded.todaySpend, 0.01)
        assertFalse("Legacy plaintext JSON must be removed after migration", legacyPrefs.contains("full_widget_snapshot_json"))

        MasroufiWidgetStore.saveWidgetSnapshot(context, snap)
        assertFalse(legacyPrefs.contains("today_spend"))
        assertFalse(legacyPrefs.contains("safe_today"))
        assertFalse(legacyPrefs.contains("full_widget_snapshot_json"))

        // SecureStore raw backing prefs must not store plaintext string
        val secureBacking = context.getSharedPreferences("masroufi_secure_kv_v1", Context.MODE_PRIVATE)
        val rawStored = secureBacking.all.values.joinToString(" ")
        assertFalse(rawStored.contains("4560"))

        // Default WidgetStyle ("follow_app") masks amounts when App Lock is enabled
        val defaultStyle = WidgetStyle()
        assertTrue(defaultStyle.shouldMaskAmounts(snapshotHideAmounts = false, appLockEnabled = true))
        assertFalse(defaultStyle.shouldMaskAmounts(snapshotHideAmounts = false, appLockEnabled = false))
        assertFalse(defaultStyle.copy(privacyMode = "always_show").shouldMaskAmounts(snapshotHideAmounts = true, appLockEnabled = true))

        // Cloud-only mode defaults to false and persists toggle
        assertFalse(AppSecurityManager.isCloudOnlyModeEnabled(context))
        AppSecurityManager.setCloudOnlyModeEnabled(context, true)
        assertTrue(AppSecurityManager.isCloudOnlyModeEnabled(context))
    }

    @Test
    fun `S7 voice labels are honest across all 4 languages and cloud notice preference persists`() {
        for (lang in listOf("fr", "en", "ar", "darija")) {
            val label = Translations.forLang(lang).moonshineBadgeLabel
            assertFalse("Fallback voice badge for $lang must not claim Moonshine On-Device", label.contains("Moonshine", ignoreCase = true))
            assertTrue("Fallback voice badge for $lang should mention Android Speech", label.contains("Android Speech"))
        }
        assertFalse(AppSecurityManager.hasAcknowledgedCloudVoiceNotice(context))
        AppSecurityManager.setCloudVoiceNoticeAcknowledged(context, true)
        assertTrue(AppSecurityManager.hasAcknowledgedCloudVoiceNotice(context))
        // Safe to check on-device availability without throwing
        MoonshineVoiceController.isOnDeviceRecognitionAvailable(context)
    }

    @Test
    fun `S8 Google Sign-In nonce generation and JWT nonce verification reject tampered or missing nonces`() {
        val (raw1, hash1) = generateSignInNonce()
        val (raw2, hash2) = generateSignInNonce()
        assertEquals(64, raw1.length)
        assertEquals(64, hash1.length)
        assertNotEquals(raw1, raw2)
        assertNotEquals(hash1, hash2)

        fun fakeJwtWithPayload(jsonPayload: String): String {
            val header = Base64.encodeToString("""{"alg":"RS256"}""".toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val payload = Base64.encodeToString(jsonPayload.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            return "$header.$payload.sig"
        }

        val validJwt = fakeJwtWithPayload("""{"sub":"123","nonce":"$hash1"}""")
        val wrongNonceJwt = fakeJwtWithPayload("""{"sub":"123","nonce":"$hash2"}""")
        val missingNonceJwt = fakeJwtWithPayload("""{"sub":"123"}""")

        assertTrue(verifyIdTokenNonce(validJwt, hash1))
        assertFalse(verifyIdTokenNonce(wrongNonceJwt, hash1))
        assertFalse(verifyIdTokenNonce(missingNonceJwt, hash1))
        assertFalse(verifyIdTokenNonce("malformed-token", hash1))
    }

    @Test
    fun `S9 backup encryption strictly rejects blank or under-10-char password and encrypts valid passwords`() {
        val sampleJson = """{"backupVersion":2,"baseBudget":5000.0}"""
        val shortResult = runCatching { AppSecurityManager.encryptBackupJson(sampleJson, "") }
        assertTrue("Blank password must be rejected", shortResult.isFailure)

        val nineCharsResult = runCatching { AppSecurityManager.encryptBackupJson(sampleJson, "123456789") }
        assertTrue("9-char password must be rejected", nineCharsResult.isFailure)

        val encrypted = AppSecurityManager.encryptBackupJson(sampleJson, "StrongPass#2026!", iterations = 10_000)
        assertTrue(AppSecurityManager.isEncryptedBackup(encrypted))
        assertFalse(encrypted.contains("5000.0"))
        assertEquals(sampleJson, AppSecurityManager.decryptBackupJson(encrypted, "StrongPass#2026!"))
    }

    @Test
    fun `S10 all app alert notifications set VISIBILITY_PRIVATE and amount-free publicVersion`() {
        val notif = MasroufiNotificationManager.buildAppAlertNotification(
            context = context,
            id = 3001,
            channelId = MasroufiNotificationManager.CHANNEL_BILLS_DARET,
            title = "Facture à venir : Inwi",
            body = "Échéance le 20 du mois (186 DH).",
            openQuickAdd = false,
            lang = "fr"
        )
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, notif.visibility)
        assertNotNull("Public lock-screen version must be attached", notif.publicVersion)
        val pubText = notif.publicVersion.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertFalse("Public lock-screen text must not contain amounts", pubText.contains("186"))
        assertFalse("Public lock-screen text must not contain merchant", pubText.contains("Inwi"))
    }
}
