package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountItem
import com.example.data.CustomizationPrefs
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.domain.PayCycleEngine
import com.example.domain.Translations
import com.example.security.AppSecurityManager
import com.example.ui.BackupSerializer
import com.example.ui.DashboardState
import com.example.ui.theme.MasroufiPalettes
import com.example.ui.theme.paletteForKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context and verify translations`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Masroufi", appName)
        assertEquals(false, Translations.forLang("fr").isRtl)
        assertEquals(true, Translations.forLang("ar").isRtl)
        assertEquals(true, Translations.forLang("darija").isRtl)
    }

    @Test
    fun `custom palettes and pay cycle engine computeWindow and computeCycleWindow work accurately`() {
        assertEquals(6, MasroufiPalettes.size)
        assertEquals("majorelle", paletteForKey("majorelle").key)
        assertEquals("emerald", paletteForKey("unknown_key").key)

        val window = PayCycleEngine.computeWindow(
            cycleKey = "2026-04",
            payCycleDay = 25,
            nowIso = "2026-05-02"
        )
        assertEquals("2026-04-25", window.startIso)
        assertEquals("2026-05-24", window.endIso)
        assertEquals(30, window.totalDays)
        assertEquals(23, window.daysRemaining)

        val offsetWindow = PayCycleEngine.computeCycleWindow(
            payCycleDay = 25,
            monthOffset = 0,
            nowIso = "2026-05-02"
        )
        assertEquals("2026-04-25", offsetWindow.startIso)
        assertEquals("2026-05-24", offsetWindow.endIso)
    }

    @Test
    fun `timezone and DST transitions do not corrupt day arithmetic`() {
        val marchDays = PayCycleEngine.daysBetweenInclusive("2026-03-01", "2026-03-31")
        assertEquals(31, marchDays)

        val octNovDays = PayCycleEngine.daysBetweenInclusive("2026-10-20", "2026-11-05")
        assertEquals(17, octNovDays)

        val d1 = PayCycleEngine.parseEpochDays("2026-03-29")
        val d2 = PayCycleEngine.parseEpochDays("2026-03-30")
        assertEquals(1L, d2 - d1)
    }

    @Test
    fun `account balances and envelope calculations with rollover and previousSpent work accurately`() {
        val accounts = listOf(
            AccountItem(id = "acc_cash", name = "Cash", kind = "cash", initialBalance = 1000.0, colorHex = "#10B981"),
            AccountItem(id = "acc_bank", name = "Banque", kind = "bank", initialBalance = 5000.0, colorHex = "#38BDF8")
        )
        val txs = listOf(
            TransactionItem(
                id = "tx1",
                type = "expense",
                amount = 200.0,
                category = "Marché",
                envelopeKey = "marche",
                accountId = "acc_cash",
                isoDate = "2026-05-03"
            ),
            TransactionItem(
                id = "tx2",
                type = "income",
                amount = 1500.0,
                category = "Prime",
                envelopeKey = "autre",
                accountId = "acc_bank",
                isoDate = "2026-05-04"
            ),
            TransactionItem(
                id = "tx3",
                type = "transfer",
                amount = 300.0,
                category = "Transfert",
                envelopeKey = "autre",
                accountId = "acc_bank",
                toAccountId = "acc_cash",
                isoDate = "2026-05-05"
            ),
            TransactionItem(
                id = "tx_prev",
                type = "expense",
                amount = 400.0,
                category = "Marché",
                envelopeKey = "marche",
                accountId = "acc_cash",
                isoDate = "2026-04-05"
            )
        )

        val balances = PayCycleEngine.computeAccountBalances(accounts, txs)
        val cashBal = balances.first { it.account.id == "acc_cash" }.currentBalance
        val bankBal = balances.first { it.account.id == "acc_bank" }.currentBalance
        assertEquals(700.0, cashBal, 0.01)
        assertEquals(6200.0, bankBal, 0.01)

        val envConfigs = listOf(
            EnvelopeConfig(
                id = "env_marche",
                key = "marche",
                nameFr = "Marché",
                nameAr = "السوق",
                nameDarija = "التقدية",
                nameEn = "Groceries",
                percentage = 25.0,
                hasCap = true,
                colorHex = "#34D399"
            )
        )

        val currentWindow = PayCycleEngine.computeWindow("2026-04", 25, "2026-05-06")
        val prevWindow = PayCycleEngine.computeWindow("2026-03", 25, "2026-05-06")
        val envelopes = PayCycleEngine.computeEnvelopes(
            envelopes = envConfigs,
            baseBudget = 6000.0,
            rolloverEnabled = true,
            currentWindow = currentWindow,
            previousWindow = prevWindow,
            allTransactions = txs
        )
        val marche = envelopes.first()
        assertEquals(1500.0, marche.baseAllocated, 0.01)
        assertEquals(400.0, marche.previousSpent, 0.01)
        assertEquals(1100.0, marche.rolloverAmount, 0.01)
        assertEquals(2600.0, marche.totalAvailable, 0.01)
        assertEquals(200.0, marche.spent, 0.01)
        assertEquals(2400.0, marche.remaining, 0.01)
    }

    @Test
    fun `versioned backup v2 exports and restores complete financial state accurately`() {
        val profile = UserProfile(
            id = "u_test",
            userId = "u_test",
            baseBudget = 8500.0,
            payCycleDay = 25,
            rolloverEnabled = true,
            language = "darija",
            isDarkTheme = false
        )
        val customPrefs = CustomizationPrefs(
            accentPalette = "majorelle",
            currencySymbol = "MAD",
            customTitle = "Masroufi Pro",
            customSubtitle = "Budget Familial"
        )
        val accounts = listOf(
            AccountItem(id = "acc_cash", userId = "u_test", name = "Cash", kind = "cash", initialBalance = 500.0, colorHex = "#10B981")
        )
        val envelopes = listOf(
            EnvelopeConfig(
                id = "env_marche",
                userId = "u_test",
                key = "marche",
                nameFr = "Marché",
                nameAr = "السوق",
                nameDarija = "التقدية",
                nameEn = "Groceries",
                percentage = 30.0,
                hasCap = true,
                colorHex = "#34D399"
            )
        )
        val goals = listOf(
            FinancialItem(
                id = "goal_1",
                userId = "u_test",
                itemType = "goal",
                name = "Fonds d'urgence",
                subtitle = "Sécurité",
                amount = 10000.0,
                secondaryAmount = 2500.0,
                dayOrTurn = 1,
                extraInt = 0,
                dateOrCycle = "2026-12-31"
            )
        )
        val txs = listOf(
            TransactionItem(
                id = "tx_100",
                userId = "u_test",
                type = "expense",
                amount = 120.0,
                category = "Marché",
                envelopeKey = "marche",
                accountId = "acc_cash",
                desc = "Légumes",
                isoDate = "2026-05-02"
            )
        )

        val window = PayCycleEngine.computeWindow("2026-04", 25, "2026-05-02")
        val prevWindow = PayCycleEngine.computeWindow("2026-03", 25, "2026-05-02")
        val state = DashboardState(
            isLoading = false,
            profile = profile,
            customPrefs = customPrefs,
            accounts = PayCycleEngine.computeAccountBalances(accounts, txs),
            envelopes = PayCycleEngine.computeEnvelopes(envelopes, profile.baseBudget, true, window, prevWindow, txs),
            goals = goals,
            allTransactions = txs
        )

        val json = BackupSerializer.encodeV2Backup(state)

        val restored = BackupSerializer.decodeBackup(
            rawJson = json,
            currentUserId = "u_test",
            fallbackProfile = UserProfile(userId = "u_test")
        )
        assertEquals(2, restored.backupVersion)
        assertNotNull(restored.profile)
        assertEquals(8500.0, restored.profile!!.baseBudget, 0.01)
        assertEquals(25, restored.profile!!.payCycleDay)
        assertEquals("darija", restored.profile!!.language)
        assertEquals("majorelle", restored.customPrefs?.accentPalette)
        assertEquals("MAD", restored.customPrefs?.currencySymbol)
        assertEquals(1, restored.accounts.size)
        assertEquals(500.0, restored.accounts.first().initialBalance, 0.01)
        assertEquals(1, restored.envelopes.size)
        assertEquals(30.0, restored.envelopes.first().percentage, 0.01)
        assertEquals(1, restored.financialItems.size)
        assertEquals("Fonds d'urgence", restored.financialItems.first().name)
        assertEquals(2500.0, restored.financialItems.first().secondaryAmount, 0.01)
        assertEquals(1, restored.transactions.size)
        assertEquals(120.0, restored.transactions.first().amount, 0.01)
    }

    @Test
    fun `security manager hashes PIN with PBKDF2 salt and enforces progressive lockout`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppSecurityManager.clearPin(context)
        assertFalse(AppSecurityManager.hasPinConfigured(context))
        assertFalse(AppSecurityManager.isAppLockEnabled(context))

        // Reject invalid non-digit or out-of-range PIN (supports 4..8 digits, 6 recommended)
        assertNull(AppSecurityManager.createSaltedPinHash("12"))
        assertNull(AppSecurityManager.createSaltedPinHash("123456789"))
        assertNull(AppSecurityManager.createSaltedPinHash("abcd"))
        assertFalse(AppSecurityManager.setPin(context, "12"))
        assertFalse(AppSecurityManager.setPin(context, "abcd"))

        // Two PBKDF2-HMAC-SHA256 hashes of the same 6-digit PIN must have distinct salts and distinct hashes
        val first = AppSecurityManager.createSaltedPinHash("258046")
        val second = AppSecurityManager.createSaltedPinHash("258046")
        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first!!.first, second!!.first)
        assertNotEquals(first.second, second.second)

        // Store and verify 6-digit PIN via Context SharedPreferences
        assertTrue(AppSecurityManager.setPin(context, "258046"))
        assertEquals(6, AppSecurityManager.getConfiguredPinLength(context))
        assertTrue(AppSecurityManager.hasPinConfigured(context))
        assertTrue(AppSecurityManager.verifyPin(context, "258046"))

        // Test progressive rate limiting & lockout after 3 failed attempts (F-01)
        assertFalse(AppSecurityManager.verifyPin(context, "000000"))
        assertFalse(AppSecurityManager.verifyPin(context, "111111"))
        assertEquals(0, AppSecurityManager.getRemainingLockoutSeconds(context))
        assertFalse(AppSecurityManager.verifyPin(context, "222222"))
        assertEquals(3, AppSecurityManager.getFailedAttempts(context))
        assertTrue(AppSecurityManager.getRemainingLockoutSeconds(context) > 0)
        // Even the valid PIN must be rejected while locked out
        assertFalse(AppSecurityManager.verifyPin(context, "258046"))

        // Reset lockout and verify valid PIN succeeds again
        AppSecurityManager.resetLockoutState(context)
        assertEquals(0, AppSecurityManager.getRemainingLockoutSeconds(context))
        assertTrue(AppSecurityManager.verifyPin(context, "258046"))

        // Toggle FLAG_SECURE and Clear Cache on Sign-Out preferences
        AppSecurityManager.setFlagSecureEnabled(context, true)
        assertTrue(AppSecurityManager.isFlagSecureEnabled(context))
        AppSecurityManager.setFlagSecureEnabled(context, false)
        assertFalse(AppSecurityManager.isFlagSecureEnabled(context))

        AppSecurityManager.setClearCacheOnSignOutEnabled(context, true)
        assertTrue(AppSecurityManager.isClearCacheOnSignOutEnabled(context))

        // Sensitive clipboard copy & clear
        AppSecurityManager.copySensitiveToClipboard(context, "Test", "{\"secret\":1}")
        AppSecurityManager.clearSensitiveClipboard(context)
    }

    @Test
    fun `CSV export neutralizes formula injection and control characters while preserving normal cells`() {
        assertEquals("\"'=SUM(A1:A2)\"", BackupSerializer.sanitizeCsvCell("=SUM(A1:A2)"))
        assertEquals("\"'+cmd|' /C calc'!A0\"", BackupSerializer.sanitizeCsvCell("+cmd|' /C calc'!A0"))
        assertEquals("\"'-10+20\"", BackupSerializer.sanitizeCsvCell("-10+20"))
        assertEquals("\"'@IMPORTXML(A1,B1)\"", BackupSerializer.sanitizeCsvCell("@IMPORTXML(A1,B1)"))
        assertEquals("\"'   =1+1\"", BackupSerializer.sanitizeCsvCell("   =1+1"))
        assertEquals("\"Ligne 1 Ligne 2 Col\"", BackupSerializer.sanitizeCsvCell("Ligne 1\r\nLigne 2\tCol"))
        assertEquals("\"Café \"\"Spécial\"\"\"", BackupSerializer.sanitizeCsvCell("Café \"Spécial\""))

        val txs = listOf(
            TransactionItem(
                id = "tx_csv_1",
                type = "expense",
                amount = 95.5,
                category = "=HYPERLINK(\"http://evil.example\")",
                envelopeKey = "marche",
                accountId = "acc_cash",
                desc = "@cmd\r\ninjection",
                isoDate = "2026-11-03"
            )
        )
        val csv = BackupSerializer.encodeCsv(txs)
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"http://evil.example\"\")\""))
        assertTrue(csv.contains("\"'@cmd injection\""))
    }

    @Test
    fun `ISO date validation rejects invalid calendar dates and normalizes blank dates safely`() {
        assertTrue(PayCycleEngine.isValidIsoDate("2026-11-03"))
        assertTrue(PayCycleEngine.isValidIsoDate("2028-02-29")) // leap year
        assertFalse(PayCycleEngine.isValidIsoDate("2026-02-29")) // non-leap year
        assertFalse(PayCycleEngine.isValidIsoDate("2026-13-01"))
        assertFalse(PayCycleEngine.isValidIsoDate("2026-04-31"))
        assertFalse(PayCycleEngine.isValidIsoDate("2026/11/03"))
        assertFalse(PayCycleEngine.isValidIsoDate("not-a-date"))

        val normalizedToday = PayCycleEngine.requireOrNormalizeIsoDate("")
        assertTrue(PayCycleEngine.isValidIsoDate(normalizedToday))
        assertEquals("2026-05-15", PayCycleEngine.requireOrNormalizeIsoDate("2026-05-15"))
    }

    @Test
    fun `AES-256-GCM backup encryption and 2-phase backup preview work accurately`() {
        val sampleJson = """{"backupVersion":2,"transactions":[{"id":"tx1","type":"expense","amount":50.0,"category":"Café","isoDate":"2026-11-03"}]}"""
        assertFalse(AppSecurityManager.isEncryptedBackup(sampleJson))

        val encrypted = AppSecurityManager.encryptBackupJson(sampleJson, "StrongPass#2026")
        assertTrue(AppSecurityManager.isEncryptedBackup(encrypted))

        // Preview rejects encrypted payload when password is missing or wrong
        val missingPassResult = runCatching {
            BackupSerializer.previewBackup(encrypted, "", "u_test", UserProfile(userId = "u_test"))
        }
        assertTrue(missingPassResult.isFailure)

        val wrongPassResult = runCatching {
            BackupSerializer.previewBackup(encrypted, "WrongPass", "u_test", UserProfile(userId = "u_test"))
        }
        assertTrue(wrongPassResult.isFailure)

        // Correct password decrypts and produces valid 2-phase preview
        val validPreview = BackupSerializer.previewBackup(encrypted, "StrongPass#2026", "u_test", UserProfile(userId = "u_test"))
        assertTrue(validPreview.isEncrypted)
        assertEquals(2, validPreview.backupVersion)
        assertEquals(1, validPreview.transactionsCount)
        assertTrue(validPreview.isSingleAtomicBatch)
    }
}

