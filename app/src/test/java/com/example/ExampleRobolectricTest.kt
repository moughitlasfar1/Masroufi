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
    fun `security manager enforces 6-8 digit PIN with 600k PBKDF2, monotonic lockout up to 1h, and secure defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("masroufi_security_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        // SEC-05: Secure defaults on fresh install (FLAG_SECURE = true, clearCacheOnSignOut = true)
        assertTrue(AppSecurityManager.isFlagSecureEnabled(context))
        assertTrue(AppSecurityManager.isClearCacheOnSignOutEnabled(context))
        assertFalse(AppSecurityManager.hasPinConfigured(context))
        assertFalse(AppSecurityManager.isAppLockEnabled(context))

        // SEC-06: Reject PINs shorter than 6 digits or longer than 8 digits
        assertNull(AppSecurityManager.createSaltedPinHash("12"))
        assertNull(AppSecurityManager.createSaltedPinHash("1234"))
        assertNull(AppSecurityManager.createSaltedPinHash("12345"))
        assertNull(AppSecurityManager.createSaltedPinHash("123456789"))
        assertNull(AppSecurityManager.createSaltedPinHash("abcdef"))
        assertFalse(AppSecurityManager.setPin(context, "1234"))
        assertFalse(AppSecurityManager.setPin(context, "12345"))
        assertFalse(AppSecurityManager.setPin(context, "abcdef"))

        // Verify progressive lockout escalation tiers up to 1 hour (SEC-06)
        assertEquals(600_000, AppSecurityManager.PBKDF2_ITERATIONS)
        assertEquals(0L, AppSecurityManager.computeLockoutDurationMs(2))
        assertEquals(30_000L, AppSecurityManager.computeLockoutDurationMs(3))
        assertEquals(60_000L, AppSecurityManager.computeLockoutDurationMs(4))
        assertEquals(300_000L, AppSecurityManager.computeLockoutDurationMs(5))
        assertEquals(900_000L, AppSecurityManager.computeLockoutDurationMs(6))
        assertEquals(1_800_000L, AppSecurityManager.computeLockoutDurationMs(7))
        assertEquals(3_600_000L, AppSecurityManager.computeLockoutDurationMs(8))

        // Two PBKDF2-HMAC-SHA256 hashes of the same 6-digit PIN have distinct salts & hashes
        val first = AppSecurityManager.createSaltedPinHash("258046", iterations = 10_000)
        val second = AppSecurityManager.createSaltedPinHash("258046", iterations = 10_000)
        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first!!.first, second!!.first)
        assertNotEquals(first.second, second.second)

        // Store and verify 6-digit PIN via Context SharedPreferences
        assertTrue(AppSecurityManager.setPin(context, "258046", iterations = 600_000))
        assertEquals(6, AppSecurityManager.getConfiguredPinLength(context))
        assertTrue(AppSecurityManager.hasPinConfigured(context))
        assertTrue(AppSecurityManager.verifyPin(context, "258046"))

        // Test monotonic progressive rate limiting & lockout after 3 failed attempts
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

        // SEC-02: Verify PIN recovery pending & force interactive sign-in flags
        assertFalse(AppSecurityManager.isPendingPinRecovery(context))
        assertFalse(AppSecurityManager.isForceInteractiveSignInRequired(context))
        AppSecurityManager.beginPinRecoverySignOut(context)
        assertTrue(AppSecurityManager.isPendingPinRecovery(context))
        assertTrue(AppSecurityManager.isForceInteractiveSignInRequired(context))
        // PIN must STILL be configured until interactive sign-in completes
        assertTrue(AppSecurityManager.hasPinConfigured(context))
        AppSecurityManager.completePinRecoveryAfterInteractiveSignIn(context)
        assertFalse(AppSecurityManager.isPendingPinRecovery(context))
        assertFalse(AppSecurityManager.isForceInteractiveSignInRequired(context))
        assertFalse(AppSecurityManager.hasPinConfigured(context))

        // SEC-07: Persistent encrypted rollback snapshot round-trip
        val sampleRollback = """{"backupVersion":2,"baseBudget":6400.0}"""
        assertTrue(AppSecurityManager.saveEncryptedRollbackSnapshot(context, sampleRollback))
        assertEquals(sampleRollback, AppSecurityManager.loadEncryptedRollbackSnapshot(context))
        AppSecurityManager.clearEncryptedRollbackSnapshot(context)
        assertNull(AppSecurityManager.loadEncryptedRollbackSnapshot(context))
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
    fun `AES-256-GCM backup encryption enforces 10+ char password, binds AAD header, and validates Phase 1 schema`() {
        val sampleJson = """{"backupVersion":2,"transactions":[{"id":"tx1","type":"expense","amount":50.0,"category":"Café","isoDate":"2026-11-03"}]}"""
        assertFalse(AppSecurityManager.isEncryptedBackup(sampleJson))

        // SEC-03: Passwords shorter than 10 chars must be rejected
        assertTrue(runCatching { AppSecurityManager.encryptBackupJson(sampleJson, "Short123") }.isFailure)

        val encrypted = AppSecurityManager.encryptBackupJson(sampleJson, "StrongPass#2026")
        assertTrue(AppSecurityManager.isEncryptedBackup(encrypted))

        // Preview rejects encrypted payload when password is missing or wrong
        assertTrue(
            runCatching {
                BackupSerializer.previewBackup(encrypted, "", "u_test", UserProfile(userId = "u_test"))
            }.isFailure
        )
        assertTrue(
            runCatching {
                BackupSerializer.previewBackup(encrypted, "WrongPass#2026", "u_test", UserProfile(userId = "u_test"))
            }.isFailure
        )

        // SEC-03: Tampering with envelope header fields (bound as GCM AAD) must fail authentication
        val tamperedHeader = encrypted.replace("\"iterations\": 600000", "\"iterations\": 600001")
        assertTrue(
            runCatching {
                AppSecurityManager.decryptBackupJson(tamperedHeader, "StrongPass#2026")
            }.isFailure
        )

        // Correct password decrypts and produces valid 2-phase preview
        val validPreview = BackupSerializer.previewBackup(encrypted, "StrongPass#2026", "u_test", UserProfile(userId = "u_test"))
        assertTrue(validPreview.isEncrypted)
        assertEquals(2, validPreview.backupVersion)
        assertEquals(1, validPreview.transactionsCount)
        assertTrue(validPreview.isSingleAtomicBatch)

        // SEC-07 & SEC-08: Phase 1 schema validation rejects invalid payCycleDay, invalid itemType, or invalid transfer
        val badPayDayJson = """{"backupVersion":2,"profile":{"baseBudget":6000,"payCycleDay":31,"language":"fr"}}"""
        assertTrue(
            runCatching {
                BackupSerializer.decodeBackup(badPayDayJson, "u_test", UserProfile(userId = "u_test"))
            }.isFailure
        )

        val badTransferJson = """{"backupVersion":2,"transactions":[{"id":"tx_t1","type":"transfer","amount":100.0,"category":"Transfert","accountId":"acc_cash","toAccountId":"acc_cash","isoDate":"2026-11-03"}]}"""
        assertTrue(
            runCatching {
                BackupSerializer.decodeBackup(badTransferJson, "u_test", UserProfile(userId = "u_test"))
            }.isFailure
        )
    }

    @Test
    fun `banking UX privacy eye persistence, last active tab, and customizable dashboard card order work accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Privacy Eye Toggle persistence & amount masking
        AppSecurityManager.setHideAmountsEnabled(context, false)
        assertFalse(AppSecurityManager.isHideAmountsEnabled(context))
        assertEquals("2 450", PayCycleEngine.formatAmountOrMask(2450.0, false).replace('\u00A0', '\u202F'))
        AppSecurityManager.setHideAmountsEnabled(context, true)
        assertTrue(AppSecurityManager.isHideAmountsEnabled(context))
        assertEquals("••••", PayCycleEngine.formatAmountOrMask(2450.0, true))
        assertEquals("•••• DH", PayCycleEngine.formatCurrencyOrMask(2450.0, "DH", true))

        // 2. Last Active Tab persistence
        AppSecurityManager.setLastActiveTab(context, "categories")
        assertEquals("categories", AppSecurityManager.getLastActiveTab(context))
        AppSecurityManager.setLastActiveTab(context, "invalid_tab")
        assertEquals("categories", AppSecurityManager.getLastActiveTab(context))

        // 3. Customizable Home Dashboard Card Order & Visibility
        val customOrder = listOf("presets", "accounts", "week_summary", "monthly_review", "bills", "insights")
        val prefs = CustomizationPrefs(
            accentPalette = "amber",
            currencySymbol = "DH",
            showAccountsCard = true,
            showQuickPresetsCard = true,
            showBillsCard = false,
            dashboardOrder = customOrder
        )
        assertEquals(customOrder, prefs.normalizedOrder())
        assertFalse(prefs.isSectionVisible("bills"))
        val updatedPrefs = prefs.withSectionVisibility("bills", true)
        assertTrue(updatedPrefs.isSectionVisible("bills"))

        val storedItem = FinancialItem(
            id = "custom_prefs",
            itemType = "custom_prefs",
            name = "Masroufi",
            subtitle = "amber|DH|${customOrder.joinToString(",")}",
            dayOrTurn = prefs.toVisibilityMask()
        )
        val decoded = CustomizationPrefs.fromFinancialItem(storedItem)
        assertEquals("amber", decoded.accentPalette)
        assertEquals(customOrder, decoded.normalizedOrder())
        assertFalse(decoded.showBillsCard)
    }

    @Test
    fun `Darija voice parser, top 3 recent categories, widget store, and notification prefs work accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Voice entry in Darija / Arabic / French / English
        val parsedCoffee = com.example.domain.VoiceDarijaParser.parse("قهوة 15")
        assertNotNull(parsedCoffee)
        assertEquals(15.0, parsedCoffee!!.amount, 0.01)
        assertEquals("قهوة", parsedCoffee.description)
        assertEquals("cafe", parsedCoffee.envelopeKey)

        val parsedLatinDarija = com.example.domain.VoiceDarijaParser.parse("qahwa 15 dh")
        assertNotNull(parsedLatinDarija)
        assertEquals(15.0, parsedLatinDarija!!.amount, 0.01)
        assertEquals("Qahwa", parsedLatinDarija.description)
        assertEquals("cafe", parsedLatinDarija.envelopeKey)

        val parsedArabiziDarija = com.example.domain.VoiceDarijaParser.parse("9ahwa 15")
        assertNotNull(parsedArabiziDarija)
        assertEquals(15.0, parsedArabiziDarija!!.amount, 0.01)
        assertEquals("Qahwa", parsedArabiziDarija.description)
        assertEquals("cafe", parsedArabiziDarija.envelopeKey)

        val parsedSpokenNumber = com.example.domain.VoiceDarijaParser.parse("طاكسي عشرين درهم")
        assertNotNull(parsedSpokenNumber)
        assertEquals(20.0, parsedSpokenNumber!!.amount, 0.01)
        assertEquals("طاكسي", parsedSpokenNumber.description)
        assertEquals("transport", parsedSpokenNumber.envelopeKey)

        val parsedEnglish = com.example.domain.VoiceDarijaParser.parse("coffee 15")
        assertNotNull(parsedEnglish)
        assertEquals(15.0, parsedEnglish!!.amount, 0.01)
        assertEquals("Coffee", parsedEnglish.description)
        assertEquals("cafe", parsedEnglish.envelopeKey)

        // 2. Top 3 most recent categories for 2-tap numeric keypad
        val envelopes = listOf(
            EnvelopeConfig(id = "e1", key = "fixes", nameFr = "Fixes", percentage = 35.0),
            EnvelopeConfig(id = "e2", key = "marche", nameFr = "Marché", percentage = 25.0),
            EnvelopeConfig(id = "e3", key = "transport", nameFr = "Transport", percentage = 15.0),
            EnvelopeConfig(id = "e4", key = "sorties", nameFr = "Sorties", percentage = 15.0)
        )
        val envStatuses = PayCycleEngine.computeEnvelopes(
            envelopes = envelopes,
            baseBudget = 6000.0,
            rolloverEnabled = true,
            currentWindow = PayCycleEngine.computeWindow("2026-04", 1, "2026-04-15"),
            previousWindow = PayCycleEngine.computeWindow("2026-03", 1, "2026-04-15"),
            allTransactions = emptyList()
        )
        val recentTxs = listOf(
            TransactionItem(id = "1", type = "expense", amount = 15.0, envelopeKey = "sorties", isoDate = "2026-04-15"),
            TransactionItem(id = "2", type = "expense", amount = 40.0, envelopeKey = "transport", isoDate = "2026-04-14"),
            TransactionItem(id = "3", type = "expense", amount = 120.0, envelopeKey = "marche", isoDate = "2026-04-12")
        )
        val top3 = com.example.ui.computeTopThreeRecentCategories(
            DashboardState(envelopes = envStatuses, allTransactions = recentTxs)
        )
        assertEquals(listOf("sorties", "transport", "marche"), top3.map { it.envelopeKey })

        // 3. Home-screen widget & Quick Settings tile store
        com.example.widget.MasroufiWidgetStore.saveSnapshot(
            context = context,
            todaySpend = 85.0,
            safeToSpendToday = 140.0,
            currencySymbol = "DH",
            hideAmounts = false,
            language = "darija"
        )
        assertTrue(com.example.widget.MasroufiWidgetStore.getTodaySpendFormatted(context).contains("85"))
        assertTrue(com.example.widget.MasroufiWidgetStore.getSafeTodayFormatted(context).contains("140"))

        // 4. WorkManager notification preferences
        com.example.notifications.MasroufiNotificationManager.setNotifyBillsEnabled(context, false)
        assertFalse(com.example.notifications.MasroufiNotificationManager.isNotifyBillsEnabled(context))
        com.example.notifications.MasroufiNotificationManager.setNotifyBillsEnabled(context, true)
        assertTrue(com.example.notifications.MasroufiNotificationManager.isNotifyBillsEnabled(context))
    }

    @Test
    fun `quick wins, Morocco-specific features, and insights work accurately`() {
        val nowMs = System.currentTimeMillis()
        val txs = listOf(
            TransactionItem(
                id = "tx_1",
                type = "expense",
                amount = 45.0,
                category = "Café",
                envelopeKey = "sorties",
                accountId = "acc_cash",
                desc = "Café Paul",
                isoDate = "2026-05-01",
                createdAt = com.google.firebase.Timestamp(java.util.Date(nowMs - 60_000L))
            ),
            TransactionItem(
                id = "tx_2",
                type = "expense",
                amount = 250.0,
                category = "Marché",
                envelopeKey = "marche",
                accountId = "acc_bank",
                desc = "Marjane",
                isoDate = "2026-05-03",
                createdAt = com.google.firebase.Timestamp(java.util.Date(nowMs - 120_000L))
            ),
            TransactionItem(
                id = "tx_sub_1",
                type = "expense",
                amount = 95.0,
                category = "Fixes",
                envelopeKey = "fixes",
                accountId = "acc_bank",
                desc = "Netflix",
                isoDate = "2026-04-05"
            ),
            TransactionItem(
                id = "tx_sub_2",
                type = "expense",
                amount = 95.0,
                category = "Fixes",
                envelopeKey = "fixes",
                accountId = "acc_bank",
                desc = "Netflix",
                isoDate = "2026-05-05"
            )
        )

        // 1. No-spend streak (last expense on 2026-05-05, today is 2026-05-08 -> 3 days: May 6, 7, 8)
        assertEquals(3, PayCycleEngine.computeNoSpendStreakDays(txs, "2026-05-08"))

        // 2. Duplicate warning within a few minutes
        val duplicate = PayCycleEngine.findRecentDuplicateTransaction(
            amount = 45.0,
            envelopeKey = "sorties",
            category = "Café",
            allTransactions = txs,
            nowMs = nowMs
        )
        assertNotNull(duplicate)
        assertEquals("tx_1", duplicate!!.id)

        // 3. Smart defaults from description and history
        val envelopes = listOf(
            EnvelopeConfig(id = "e1", key = "fixes", nameFr = "Fixes", percentage = 35.0),
            EnvelopeConfig(id = "e2", key = "marche", nameFr = "Marché", percentage = 25.0),
            EnvelopeConfig(id = "e3", key = "sorties", nameFr = "Sorties", percentage = 15.0)
        )
        val envStatuses = PayCycleEngine.computeEnvelopes(
            envelopes = envelopes,
            baseBudget = 6000.0,
            rolloverEnabled = true,
            currentWindow = PayCycleEngine.computeWindow("2026-05", 1, "2026-05-08"),
            previousWindow = PayCycleEngine.computeWindow("2026-04", 1, "2026-05-08"),
            allTransactions = txs
        )
        val guess = PayCycleEngine.inferSmartDefaults("Marjane Hay Riad", txs, envStatuses, "acc_cash")
        assertNotNull(guess)
        assertEquals("marche", guess!!.envelopeKey)
        assertEquals("acc_bank", guess.accountId)

        // 4. Multi-currency conversion (MAD, EUR, USD)
        assertEquals(1085.0, PayCycleEngine.convertCurrencyAmount(100.0, "EUR", "MAD"), 0.01)
        assertEquals(995.0, PayCycleEngine.convertCurrencyAmount(100.0, "USD", "MAD"), 0.01)

        // 5. Seasonal countdown & suggested monthly saving
        val seasonal = FinancialItem(
            id = "s_aid",
            itemType = "seasonal",
            name = "Aïd al-Adha (Houli)",
            amount = 4500.0,
            secondaryAmount = 1500.0,
            dateOrCycle = "2026-08-08"
        )
        val countdown = PayCycleEngine.computeSeasonalCountdown(seasonal, "2026-05-08")
        assertEquals(3000.0, countdown.remainingAmount, 0.01)
        assertTrue(countdown.daysRemaining in 90..95)
        assertEquals(750.0, countdown.suggestedMonthlySaving, 0.01)

        // 6. Subscription detector
        val subs = PayCycleEngine.detectRecurringSubscriptions(txs, emptyList())
        assertEquals(1, subs.size)
        assertEquals("Netflix", subs.first().name)
        assertEquals(95.0, subs.first().monthlyAmount, 0.01)

        // 7. Month-end forecast & What-if goal impact
        val forecastEn = PayCycleEngine.formatMonthEndForecast(1240.0, "MAD", "en", false)
        assertTrue(forecastEn.contains(PayCycleEngine.formatDh(1240.0)) && forecastEn.contains("MAD"))
        val goal = FinancialItem(
            id = "g1",
            itemType = "goal",
            name = "Voiture",
            amount = 6000.0,
            secondaryAmount = 3000.0
        )
        val whatIfMsg = PayCycleEngine.computeWhatIfGoalImpact(
            envelopeName = "Sorties",
            envelopeMonthlySpend = 1500.0,
            cutPercent = 20.0,
            currentMonthlySavingCapacity = 500.0,
            goals = listOf(goal),
            currencySymbol = "MAD",
            language = "en",
            hideAmounts = false
        )
        assertTrue(whatIfMsg.contains("sooner"))
    }
}



