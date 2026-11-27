package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountItem
import com.example.data.BackupV3Codec
import com.example.data.EnvelopeConfig
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.domain.AccountWithBalance
import com.example.domain.BankMessageParser
import com.example.domain.BankParseResult
import com.example.domain.EnvelopeStatus
import com.example.notifications.BankNotificationListenerService
import com.example.notifications.MasroufiDetectionLog
import com.example.notifications.NotificationActionReceiver
import com.example.notifications.QuickCaptureBootReceiver
import com.example.security.BackupEnvelopeInfo
import com.example.security.PortableBackupCrypto
import com.example.security.PortableCryptoResult
import com.example.ui.DashboardState
import com.example.widget.QuickCaptureActivity
import com.masroufi.core.Money
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 4 Release Hardening verification tests:
 * 1. Version bump (`1.5.0` / `versionCode = 8`) & Backup v3 metadata propagation.
 * 2. Merged AndroidManifest security invariants (`allowBackup="false"`, `usesCleartextTraffic="false"`,
 *    explicit component export & permission guards).
 * 3. Portable Backup v3 PBKDF2-HMAC-SHA256 + AES-256-GCM encryption/decryption with exact Long centimes.
 * 4. Moroccan bank notification parser accuracy & zero-PII diagnostic log hygiene.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase4ReleaseHardeningTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MasroufiDetectionLog.clear()
    }

    @After
    fun tearDown() {
        MasroufiDetectionLog.clear()
    }

    @Test
    fun `build config reflects v1_5_0 release version and versionCode 8`() {
        assertEquals("1.5.0", BuildConfig.VERSION_NAME)
        assertEquals(8, BuildConfig.VERSION_CODE)
        assertEquals("com.masroufi.app", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `backup v3 export stamps v1_5_0 and round-trips exact centimes through PortableBackupCrypto`() {
        val profile = UserProfile(
            id = "profile",
            userId = "local_device_user",
            baseBudget = Money(850_000L),
            payCycleDay = 5,
            language = "fr"
        )
        val account = AccountItem(
            id = "acc_bank",
            userId = "local_device_user",
            name = "CIH",
            kind = "bank",
            initialBalance = Money(1_234_567L)
        )
        val envelope = EnvelopeConfig(
            id = "alimentation",
            userId = "local_device_user",
            key = "alimentation",
            nameFr = "Alimentation",
            percentage = 30.0
        )
        val tx = TransactionItem(
            id = "tx_release_1",
            userId = "local_device_user",
            type = "expense",
            amount = Money(14_995L), // 149.95 DH exact centimes
            category = "alimentation",
            envelopeKey = "alimentation",
            accountId = "acc_bank",
            desc = "Marjane Market",
            isoDate = "2026-11-27"
        )
        val state = DashboardState(
            profile = profile,
            accounts = listOf(AccountWithBalance(account, account.initialBalance, Money.ZERO, Money.ZERO)),
            envelopes = listOf(
                EnvelopeStatus(
                    config = envelope,
                    baseAllocated = Money.ZERO,
                    rolloverAmount = Money.ZERO,
                    totalAvailable = Money.ZERO,
                    spent = Money.ZERO,
                    previousSpent = Money.ZERO,
                    remaining = Money.ZERO,
                    usagePercent = 0,
                    isOver90Percent = false
                )
            ),
            allTransactions = listOf(tx)
        )

        val plainBackupJson = BackupV3Codec.encodeV3Backup(state = state)
        val root = JSONObject(plainBackupJson)
        assertEquals(BackupV3Codec.BACKUP_VERSION_V3, root.getInt("backupVersion"))
        assertEquals("1.5.0", root.getString("appVersion"))
        assertEquals("1.5.0", root.getJSONObject("metadata").getString("appVersion"))

        val passphrase = "Masroufi!Release#150"
        val encryptResult = PortableBackupCrypto.encryptBlocking(
            plaintextUtf8 = plainBackupJson,
            passphrase = passphrase,
            iterations = PortableBackupCrypto.MIN_PBKDF2_ITERATIONS
        )
        assertTrue("Expected PortableBackupCrypto encryption success", encryptResult is PortableCryptoResult.Success)
        val encryptedEnvelope = (encryptResult as PortableCryptoResult.Success<String>).value

        val headerInfo = PortableBackupCrypto.inspectEnvelope(encryptedEnvelope)
        assertTrue(headerInfo is BackupEnvelopeInfo.PortableV3Encrypted)

        val wrongDecrypt = PortableBackupCrypto.decryptBlocking(encryptedEnvelope, "WrongPassphrase!123")
        assertTrue(
            "Wrong passphrase must fail GCM authentication",
            wrongDecrypt is PortableCryptoResult.WrongPassphraseOrTampered
        )

        val decryptResult = PortableBackupCrypto.decryptBlocking(encryptedEnvelope, passphrase)
        assertTrue("Expected PortableBackupCrypto decryption success", decryptResult is PortableCryptoResult.Success)
        val decryptedJson = (decryptResult as PortableCryptoResult.Success<String>).value

        val decoded = BackupV3Codec.decodeV3Backup(
            obj = JSONObject(decryptedJson),
            currentUserId = "local_device_user",
            fallbackProfile = UserProfile()
        )
        assertEquals(Money(850_000L), decoded.profile?.baseBudget)
        assertEquals(Money(1_234_567L), decoded.accounts.single().initialBalance)
        assertEquals(Money(14_995L), decoded.transactions.single().amount)
        assertEquals("Marjane Market", decoded.transactions.single().desc)
    }

    @Test
    fun `merged manifest enforces allowBackup false, no cleartext traffic, and strict component exports`() {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(context.packageName, 0)

        assertEquals(
            "android:allowBackup must be false",
            0,
            appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP
        )
        assertEquals(
            "android:usesCleartextTraffic must be false",
            0,
            appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC
        )

        val listenerService = pm.getServiceInfo(
            ComponentName(context, BankNotificationListenerService::class.java),
            0
        )
        assertTrue("NotificationListenerService must be exported for system binding", listenerService.exported)
        assertEquals(
            Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
            listenerService.permission
        )

        val bootReceiver = pm.getReceiverInfo(
            ComponentName(context, QuickCaptureBootReceiver::class.java),
            0
        )
        assertFalse("QuickCaptureBootReceiver must not be exported to third-party apps", bootReceiver.exported)

        val actionReceiver = pm.getReceiverInfo(
            ComponentName(context, NotificationActionReceiver::class.java),
            0
        )
        assertFalse("NotificationActionReceiver must not be exported", actionReceiver.exported)

        val quickCaptureActivity = pm.getActivityInfo(
            ComponentName(context, QuickCaptureActivity::class.java),
            0
        )
        assertFalse("QuickCaptureActivity must remain internal (exported=false)", quickCaptureActivity.exported)
    }

    @Test
    fun `moroccan bank parser extracts exact centimes and detection log never stores raw sms text`() {
        val cihSms = "Paiement carte TPE de 245,50 MAD chez MARJANE CASABLANCA. Solde: 4 120,00 MAD"
        val parseCih = BankMessageParser.parseDetailed(
            rawTitle = "CIH BANK",
            rawBody = cihSms,
            sourcePackage = "com.b3g.cih.online",
            postTimeMillis = 1_764_200_000_000L
        )
        assertTrue("Expected CIH debit to parse cleanly", parseCih is BankParseResult.Parsed)
        val cihNotification = (parseCih as BankParseResult.Parsed).notification
        assertEquals(Money(24_550L), cihNotification.amount)
        assertEquals("expense", cihNotification.direction)

        val bcpCredit = "Virement recu de 7 500,00 DH de EMPLOYEUR SA. Nouveau solde 11 620,00 DH"
        val parseBcp = BankMessageParser.parseDetailed(
            rawTitle = "Pocket Bank",
            rawBody = bcpCredit,
            sourcePackage = "ma.gbp.pocketbank",
            postTimeMillis = 1_764_200_060_000L
        )
        assertTrue("Expected Pocket Bank credit to parse cleanly", parseBcp is BankParseResult.Parsed)
        val bcpNotification = (parseBcp as BankParseResult.Parsed).notification
        assertEquals(Money(750_000L), bcpNotification.amount)
        assertEquals("income", bcpNotification.direction)

        // Verify diagnostic log privacy contract: stores only package/stage/reason/length
        MasroufiDetectionLog.record(
            sourcePackage = "com.b3g.cih.online",
            stage = "store",
            reasonCode = MasroufiDetectionLog.REASON_STORED,
            messageLength = cihSms.length
        )
        val entry = MasroufiDetectionLog.snapshotEntries().firstOrNull()
        assertNotNull(entry)
        val serialized = entry.toString()
        assertFalse("Detection log must never retain merchant names", serialized.contains("MARJANE", ignoreCase = true))
        assertFalse("Detection log must never retain monetary amounts", serialized.contains("245"))
        assertFalse("Detection log must never retain balance info", serialized.contains("4 120"))
    }
}
