package com.example

import android.content.Context
import androidx.activity.compose.setContent
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
import com.masroufi.core.Money
import com.masroufi.core.sumMoney
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
        assertEquals(3, MasroufiPalettes.size)
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
            AccountItem(id = "acc_cash", name = "Cash", kind = "cash", initialBalance = Money.fromDh(1000), colorHex = "#10B981"),
            AccountItem(id = "acc_bank", name = "Banque", kind = "bank", initialBalance = Money.fromDh(5000), colorHex = "#38BDF8")
        )
        val txs = listOf(
            TransactionItem(
                id = "tx1",
                type = "expense",
                amount = Money.fromDh(200),
                category = "Marché",
                envelopeKey = "marche",
                accountId = "acc_cash",
                isoDate = "2026-05-03"
            ),
            TransactionItem(
                id = "tx2",
                type = "income",
                amount = Money.fromDh(1500),
                category = "Prime",
                envelopeKey = "autre",
                accountId = "acc_bank",
                isoDate = "2026-05-04"
            ),
            TransactionItem(
                id = "tx3",
                type = "transfer",
                amount = Money.fromDh(300),
                category = "Transfert",
                envelopeKey = "autre",
                accountId = "acc_bank",
                toAccountId = "acc_cash",
                isoDate = "2026-05-05"
            ),
            TransactionItem(
                id = "tx_prev",
                type = "expense",
                amount = Money.fromDh(400),
                category = "Marché",
                envelopeKey = "marche",
                accountId = "acc_cash",
                isoDate = "2026-04-05"
            )
        )

        val balances = PayCycleEngine.computeAccountBalances(accounts, txs)
        val cashBal = balances.first { it.account.id == "acc_cash" }.currentBalance
        val bankBal = balances.first { it.account.id == "acc_bank" }.currentBalance
        assertEquals(Money.fromDh(700), cashBal)
        assertEquals(Money.fromDh(6200), bankBal)

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
            baseBudget = Money.fromDh(6000),
            rolloverEnabled = true,
            currentWindow = currentWindow,
            previousWindow = prevWindow,
            allTransactions = txs
        )
        val marche = envelopes.first()
        assertEquals(Money.fromDh(1500), marche.baseAllocated)
        assertEquals(Money.fromDh(400), marche.previousSpent)
        assertEquals(Money.fromDh(1100), marche.rolloverAmount)
        assertEquals(Money.fromDh(2600), marche.totalAvailable)
        assertEquals(Money.fromDh(200), marche.spent)
        assertEquals(Money.fromDh(2400), marche.remaining)
    }

    @Test
    fun `versioned backup v2 exports and restores complete financial state accurately`() {
        val profile = UserProfile(
            id = "u_test",
            userId = "u_test",
            baseBudget = Money.fromDh(8500),
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
            AccountItem(id = "acc_cash", userId = "u_test", name = "Cash", kind = "cash", initialBalance = Money.fromDh(500), colorHex = "#10B981")
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
                amount = Money.fromDh(10000),
                secondaryAmount = Money.fromDh(2500),
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
                amount = Money.fromDh(120),
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
        assertEquals(Money.fromDh(8500), restored.profile!!.baseBudget)
        assertEquals(25, restored.profile!!.payCycleDay)
        assertEquals("darija", restored.profile!!.language)
        assertEquals("majorelle", restored.customPrefs?.accentPalette)
        assertEquals("MAD", restored.customPrefs?.currencySymbol)
        assertEquals(1, restored.accounts.size)
        assertEquals(Money.fromDh(500), restored.accounts.first().initialBalance)
        assertEquals(1, restored.envelopes.size)
        assertEquals(30.0, restored.envelopes.first().percentage, 0.01)
        assertEquals(1, restored.financialItems.size)
        assertEquals("Fonds d'urgence", restored.financialItems.first().name)
        assertEquals(Money.fromDh(2500), restored.financialItems.first().secondaryAmount)
        assertEquals(1, restored.transactions.size)
        assertEquals(Money.fromDh(120), restored.transactions.first().amount)
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
                amount = Money(9550L),
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
        assertEquals("2 450", PayCycleEngine.formatAmountOrMask(Money.fromDh(2450), false).replace('\u00A0', ' ').replace('\u202F', ' '))
        AppSecurityManager.setHideAmountsEnabled(context, true)
        assertTrue(AppSecurityManager.isHideAmountsEnabled(context))
        assertEquals("••••", PayCycleEngine.formatAmountOrMask(Money.fromDh(2450), true))
        assertEquals("•••• DH", PayCycleEngine.formatCurrencyOrMask(Money.fromDh(2450), "DH", true))

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
        assertEquals(Money.fromDh(15), parsedCoffee!!.amount)
        assertEquals("قهوة", parsedCoffee.description)
        assertEquals("cafe", parsedCoffee.envelopeKey)

        val parsedLatinDarija = com.example.domain.VoiceDarijaParser.parse("qahwa 15 dh")
        assertNotNull(parsedLatinDarija)
        assertEquals(Money.fromDh(15), parsedLatinDarija!!.amount)
        assertEquals("Qahwa", parsedLatinDarija.description)
        assertEquals("cafe", parsedLatinDarija.envelopeKey)

        val parsedArabiziDarija = com.example.domain.VoiceDarijaParser.parse("9ahwa 15")
        assertNotNull(parsedArabiziDarija)
        assertEquals(Money.fromDh(15), parsedArabiziDarija!!.amount)
        assertEquals("Qahwa", parsedArabiziDarija.description)
        assertEquals("cafe", parsedArabiziDarija.envelopeKey)

        val parsedSpokenNumber = com.example.domain.VoiceDarijaParser.parse("طاكسي عشرين درهم")
        assertNotNull(parsedSpokenNumber)
        assertEquals(Money.fromDh(20), parsedSpokenNumber!!.amount)
        assertEquals("طاكسي", parsedSpokenNumber.description)
        assertEquals("transport", parsedSpokenNumber.envelopeKey)

        val parsedEnglish = com.example.domain.VoiceDarijaParser.parse("coffee 15")
        assertNotNull(parsedEnglish)
        assertEquals(Money.fromDh(15), parsedEnglish!!.amount)
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
            baseBudget = Money.fromDh(6000),
            rolloverEnabled = true,
            currentWindow = PayCycleEngine.computeWindow("2026-04", 1, "2026-04-15"),
            previousWindow = PayCycleEngine.computeWindow("2026-03", 1, "2026-04-15"),
            allTransactions = emptyList()
        )
        val recentTxs = listOf(
            TransactionItem(id = "1", type = "expense", amount = Money.fromDh(15), envelopeKey = "sorties", isoDate = "2026-04-15"),
            TransactionItem(id = "2", type = "expense", amount = Money.fromDh(40), envelopeKey = "transport", isoDate = "2026-04-14"),
            TransactionItem(id = "3", type = "expense", amount = Money.fromDh(120), envelopeKey = "marche", isoDate = "2026-04-12")
        )
        val top3 = com.example.ui.computeTopThreeRecentCategories(
            DashboardState(envelopes = envStatuses, allTransactions = recentTxs)
        )
        assertEquals(listOf("sorties", "transport", "marche"), top3.map { it.envelopeKey })

        // 3. Home-screen widget & Quick Settings tile store
        com.example.widget.MasroufiWidgetStore.saveSnapshot(
            context = context,
            todaySpend = Money.fromDh(85),
            safeToSpendToday = Money.fromDh(140),
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
                amount = Money.fromDh(45),
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
                amount = Money.fromDh(250),
                category = "Marché",
                envelopeKey = "marche",
                accountId = "acc_bank",
                desc = "Marjane",
                isoDate = "2026-05-03",
                createdAt = com.google.firebase.Timestamp(java.util.Date(nowMs - 120_000L))
            ),
            TransactionItem(
                id = "tx_sub_0",
                type = "expense",
                amount = Money.fromDh(95),
                category = "Fixes",
                envelopeKey = "fixes",
                accountId = "acc_bank",
                desc = "Netflix",
                isoDate = "2026-03-05"
            ),
            TransactionItem(
                id = "tx_sub_1",
                type = "expense",
                amount = Money.fromDh(95),
                category = "Fixes",
                envelopeKey = "fixes",
                accountId = "acc_bank",
                desc = "Netflix",
                isoDate = "2026-04-05"
            ),
            TransactionItem(
                id = "tx_sub_2",
                type = "expense",
                amount = Money.fromDh(95),
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
            amount = Money.fromDh(45),
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
            baseBudget = Money.fromDh(6000),
            rolloverEnabled = true,
            currentWindow = PayCycleEngine.computeWindow("2026-05", 1, "2026-05-08"),
            previousWindow = PayCycleEngine.computeWindow("2026-04", 1, "2026-05-08"),
            allTransactions = txs
        )
        PayCycleEngine.clearLearnedCategories()
        val guess = PayCycleEngine.inferSmartDefaults("Marjane Hay Riad", txs, envStatuses, "acc_cash")
        assertNotNull(guess)
        assertEquals("marche", guess!!.envelopeKey)
        assertEquals("acc_bank", guess.accountId)

        // 4. Multi-currency conversion (MAD, EUR, USD)
        assertEquals(Money.fromDh(1085), PayCycleEngine.convertCurrencyAmount(Money.fromDh(100), "EUR", "MAD"))
        assertEquals(Money.fromDh(995), PayCycleEngine.convertCurrencyAmount(Money.fromDh(100), "USD", "MAD"))

        // 5. Seasonal countdown & suggested monthly saving
        val seasonal = FinancialItem(
            id = "s_aid",
            itemType = "seasonal",
            name = "Aïd al-Adha (Houli)",
            amount = Money.fromDh(4500),
            secondaryAmount = Money.fromDh(1500),
            dateOrCycle = "2026-08-08"
        )
        val countdown = PayCycleEngine.computeSeasonalCountdown(seasonal, "2026-05-08")
        assertEquals(Money.fromDh(3000), countdown.remainingAmount)
        assertTrue(countdown.daysRemaining in 90..95)
        assertEquals(Money.fromDh(750), countdown.suggestedMonthlySaving)

        // 6. Subscription detector
        val subs = PayCycleEngine.detectRecurringSubscriptions(txs, emptyList())
        assertEquals(1, subs.size)
        assertEquals("Netflix", subs.first().name)
        assertEquals(Money.fromDh(95), subs.first().monthlyAmount)

        // 7. Month-end forecast & What-if goal impact
        val forecastEn = PayCycleEngine.formatMonthEndForecast(Money.fromDh(1240), "MAD", "en", false)
        assertTrue(forecastEn.contains(PayCycleEngine.formatDh(Money.fromDh(1240))) && forecastEn.contains("MAD"))
        val goal = FinancialItem(
            id = "g1",
            itemType = "goal",
            name = "Voiture",
            amount = Money.fromDh(6000),
            secondaryAmount = Money.fromDh(3000)
        )
        val whatIfMsg = PayCycleEngine.computeWhatIfGoalImpact(
            envelopeName = "Sorties",
            envelopeMonthlySpend = Money.fromDh(1500),
            cutPercent = 20.0,
            currentMonthlySavingCapacity = Money.fromDh(500),
            goals = listOf(goal),
            currencySymbol = "MAD",
            language = "en",
            hideAmounts = false
        )
        assertTrue(whatIfMsg.contains("sooner"))
    }

    @Test
    fun `Phase 0 trust and text requirements are verified`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Future-dated transactions (date > today) excluded from envelope spent when maxDateIso is passed
        val window = PayCycleEngine.computeWindow("2026-05", 1, "2026-05-10")
        val prevWindow = PayCycleEngine.computeWindow("2026-04", 1, "2026-05-10")
        val envelopes = listOf(
            EnvelopeConfig(id = "e_cafe", key = "cafe", nameFr = "3. Café & Sorties", percentage = 25.0)
        )
        val pastAndFutureTxs = listOf(
            TransactionItem(id = "p1", type = "expense", amount = Money.fromDh(100), category = "cafe", envelopeKey = "cafe", isoDate = "2026-05-05"),
            TransactionItem(id = "f1", type = "expense", amount = Money.fromDh(400), category = "cafe", envelopeKey = "cafe", isoDate = "2026-05-20")
        )
        val computedEnvs = PayCycleEngine.computeEnvelopes(
            envelopes = envelopes,
            baseBudget = Money.fromDh(4000),
            rolloverEnabled = false,
            currentWindow = window,
            previousWindow = prevWindow,
            allTransactions = pastAndFutureTxs,
            maxDateIso = "2026-05-10"
        )
        assertEquals(Money.fromDh(100), computedEnvs.first().spent)

        // 2. Overspend projection: median daily variable spend ignoring scheduled bills & >3x median outliers,
        // gated on daysElapsed >= 7 and transactionCount >= 10, with AMBER vs RED alert levels.
        val tenDailyTxs = (1..9).map { day ->
            TransactionItem(
                id = "d_$day",
                type = "expense",
                amount = Money.fromDh(200),
                category = "cafe",
                envelopeKey = "cafe",
                desc = "Repas $day",
                isoDate = "2026-05-0$day"
            )
        } + listOf(
            // Single-day outlier > 3x median (should be ignored from median variable rate)
            TransactionItem(
                id = "d_10",
                type = "expense",
                amount = Money.fromDh(1500),
                category = "cafe",
                envelopeKey = "cafe",
                desc = "Achat exceptionnel",
                isoDate = "2026-05-10"
            )
        )
        val proj = PayCycleEngine.computeOverspendProjection(
            window = window,
            totalAvailableBudget = Money.fromDh(5000),
            spentSoFar = tenDailyTxs.sumMoney { it.amount },
            scheduledBills = Money.fromDh(300),
            pastOrTodayCycleTransactions = tenDailyTxs,
            bills = emptyList()
        )
        assertTrue(proj.showEstimateAlert)
        assertEquals(Money.fromDh(200), proj.medianDailyVariable)
        assertEquals(com.example.domain.ProjectionAlertLevel.RED, proj.alertLevel)

        // When transactionCount < 10, alert is suppressed
        val projTooFew = PayCycleEngine.computeOverspendProjection(
            window = window,
            totalAvailableBudget = Money.fromDh(1000),
            spentSoFar = Money.fromDh(1000),
            scheduledBills = Money.ZERO,
            pastOrTodayCycleTransactions = tenDailyTxs.take(5),
            bills = emptyList()
        )
        assertFalse(projTooFew.showEstimateAlert)

        // 3. Recurring-charge detection ignores debt repayments ("Remboursement" / sellef) and transfers,
        // requires 3+ occurrences with monthly/weekly spacing and amounts within 10%, and respects "Not a bill" dismiss
        val recurringCandidates = listOf(
            // Valid 3x monthly subscription within 10%
            TransactionItem(id = "sp1", type = "expense", amount = Money.fromDh(50), desc = "Spotify", isoDate = "2026-03-10"),
            TransactionItem(id = "sp2", type = "expense", amount = Money.fromDh(52), desc = "Spotify", isoDate = "2026-04-10"),
            TransactionItem(id = "sp3", type = "expense", amount = Money.fromDh(50), desc = "Spotify", isoDate = "2026-05-10"),
            // Debt repayment 3x monthly -> MUST be ignored
            TransactionItem(id = "rb1", type = "expense", amount = Money.fromDh(500), desc = "Remboursement Sellef Karim", isoDate = "2026-03-10"),
            TransactionItem(id = "rb2", type = "expense", amount = Money.fromDh(500), desc = "Remboursement Sellef Karim", isoDate = "2026-04-10"),
            TransactionItem(id = "rb3", type = "expense", amount = Money.fromDh(500), desc = "Remboursement Sellef Karim", isoDate = "2026-05-10"),
            // Only 2 occurrences -> MUST be ignored
            TransactionItem(id = "tw1", type = "expense", amount = Money.fromDh(100), desc = "Gym Club", isoDate = "2026-04-10"),
            TransactionItem(id = "tw2", type = "expense", amount = Money.fromDh(100), desc = "Gym Club", isoDate = "2026-05-10")
        )
        val detected = PayCycleEngine.detectRecurringSubscriptions(recurringCandidates, emptyList())
        assertEquals(1, detected.size)
        assertEquals("Spotify", detected.first().merchant)

        com.example.security.AppSecurityManager.clearDismissedRecurringBills(context)
        com.example.security.AppSecurityManager.dismissRecurringBill(context, "Spotify")
        val dismissedSet = com.example.security.AppSecurityManager.getDismissedRecurringBills(context)
        val detectedAfterDismiss = PayCycleEngine.detectRecurringSubscriptions(
            recurringCandidates,
            emptyList(),
            dismissedSet
        )
        assertTrue(detectedAfterDismiss.isEmpty())

        // 4. Category names: stable IDs, migration of stored defaults, removal of "1." "2." numeric prefixes,
        // while preserving user-renamed categories
        assertEquals("cafe", com.example.data.DefaultCategoryResolver.canonicalDefaultId("3. Café & Sorties"))
        assertEquals("cafe", com.example.data.DefaultCategoryResolver.canonicalDefaultId("Café & Outings"))
        assertEquals("charges", com.example.data.DefaultCategoryResolver.canonicalDefaultId("2. Charges fixes & Maison"))
        val defaultEnv = EnvelopeConfig(
            id = "env_cafe",
            key = "cafe",
            nameFr = "3. Café & Sorties",
            nameEn = "3. Café & Outings",
            nameAr = "3. مقهى وخرجات",
            nameDarija = "3. قهوة وخرجات"
        )
        assertTrue(defaultEnv.isDefaultUnrenamed())
        assertEquals("Café & Outings", defaultEnv.localizedName("en"))
        assertEquals("Café & Sorties", defaultEnv.localizedName("fr"))
        assertTrue(com.example.data.DefaultCategoryResolver.localizedDefaultHelper("cafe", "fr").contains("Café"))
        assertFalse(defaultEnv.localizedName("fr").startsWith("3."))

        val renamedEnv = defaultEnv.copy(nameFr = "Restaurants & Sushi", nameEn = "Restaurants & Sushi")
        assertFalse(renamedEnv.isDefaultUnrenamed())
        assertEquals("Restaurants & Sushi", renamedEnv.localizedName("fr"))

        // 5. Contrast >= 4.5:1 in dark and light mode for every accent palette
        val lightSurface = com.example.ui.theme.MasroufiSurfaceLight
        val darkSurface = com.example.ui.theme.MasroufiSurfaceDark
        assertTrue(com.example.ui.theme.wcagContrastRatio(com.example.ui.theme.MasroufiOnSurfaceLight, lightSurface) >= 4.5)
        assertTrue(com.example.ui.theme.wcagContrastRatio(com.example.ui.theme.MasroufiOnSurfaceVariantLight, lightSurface) >= 4.5)
        assertTrue(com.example.ui.theme.wcagContrastRatio(com.example.ui.theme.MasroufiOnSurfaceDark, darkSurface) >= 4.5)
        assertTrue(com.example.ui.theme.wcagContrastRatio(com.example.ui.theme.MasroufiOnSurfaceVariantDark, darkSurface) >= 4.5)
        com.example.ui.theme.MasroufiPalettes.forEach { pal ->
            val lightContrast = com.example.ui.theme.wcagContrastRatio(pal.primaryLight, lightSurface)
            val darkContrast = com.example.ui.theme.wcagContrastRatio(pal.primaryDark, darkSurface)
            assertTrue("Palette ${pal.key} light contrast $lightContrast < 4.5", lightContrast >= 4.5)
            assertTrue("Palette ${pal.key} dark contrast $darkContrast < 4.5", darkContrast >= 4.5)
        }

        // 6. Translations completeness and forbidden hard-coded UI literal check
        listOf("fr", "en", "ar", "darija").forEach { langCode ->
            val s = com.example.domain.Translations.forLang(langCode)
            assertTrue(s.upcomingGroupTitle.isNotBlank())
            assertTrue(s.estimateLabel.isNotBlank())
            assertTrue(s.notABillBtn.isNotBlank())
            assertTrue(s.remainingLabel.isNotBlank())
            assertTrue(s.shareBtn.isNotBlank())
            assertTrue(s.editBtn.isNotBlank())
            assertTrue(s.billLabel.isNotBlank())
            assertTrue(s.newLabel.isNotBlank())
            assertTrue(s.currentShortLabel.isNotBlank())
            assertTrue(s.previousShortLabel.isNotBlank())
            assertTrue(s.accountFieldLabel.isNotBlank())
            assertTrue(s.categoryNameLabel.isNotBlank())
            assertTrue(s.applyCustomizationBtn.isNotBlank())
            assertTrue(s.moroccoTemplatesTitle.isNotBlank())
            assertTrue(s.demoModeBtn.isNotBlank())
            assertTrue(s.customizationStudioTitle.isNotBlank())
            assertTrue(s.quickActionsMenuDesc.isNotBlank())
        }

        val uiFiles = listOf(
            java.io.File("src/main/java/com/example/MainActivity.kt"),
            java.io.File("src/main/java/com/example/ui/MasroufiOverviewAndWeekScreens.kt"),
            java.io.File("src/main/java/com/example/ui/MasroufiMonthAndBudgetScreens.kt"),
            java.io.File("src/main/java/com/example/ui/MasroufiHistoryAndModals.kt")
        ).filter { it.exists() }
        val forbiddenLiterals = listOf(
            "Text(\"Mode Démo\"",
            "Text(\"Appliquer la personnalisation\"",
            "Text(\"Nom de la catégorie\"",
            "contentDescription = \"Menu Actions Rapides\"",
            "\"Studio de Personnalisation (Thèmes, Cartes, Devise)\"",
            "\"⚡ Modèles Maroc (jour d'échéance pré-rempli) :\"",
            "\"if (txType == \\\"transfer\\\") \\\"Compte source :\\\" else \\\"Compte :\\\"\""
        )
        uiFiles.forEach { file ->
            val content = file.readText()
            forbiddenLiterals.forEach { forbidden ->
                assertFalse("Forbidden hard-coded literal $forbidden found in ${file.name}", content.contains(forbidden))
            }
        }
    }

    @Test
    fun `MainActivity launches without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.google.firebase.FirebaseApp.clearInstancesForTest()
        assertTrue(com.google.firebase.FirebaseApp.getApps(context).isEmpty())
        org.robolectric.Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            val activity = controller.get()
            assertNotNull(activity)
            assertFalse(com.google.firebase.FirebaseApp.getApps(context).isEmpty())
            assertEquals("gen-lang-client-0849522300", com.google.firebase.FirebaseApp.getInstance().options.projectId)
            val webClientId = com.example.ui.resolveDefaultWebClientId(activity)
            assertTrue(webClientId.endsWith("googleusercontent.com"))
            activity.setContent {
                MainFeatureScreen(currentUserId = "u_test_123")
            }
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        }
    }

    @Test
    fun `Masroufi simplification plan Phases 0 to 7 requirements work accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Phase 0: Optional sign-in / local mode preference & web client ID fallback
        com.example.ui.setLocalModeEnabled(context, false)
        assertFalse(com.example.ui.isLocalModeEnabled(context))
        com.example.ui.setLocalModeEnabled(context, true)
        assertTrue(com.example.ui.isLocalModeEnabled(context))
        assertTrue(com.example.ui.resolveDefaultWebClientId(context).isNotBlank())

        // Phase 4: 4 tabs (Home, Insights, Plan, Activity) and saved tab migration ("mois" -> Insights/Month)
        assertEquals(listOf("vue", "semaine", "categories", "historique"), MAIN_TAB_KEYS)
        assertEquals("semaine" to "month", resolveSavedTabMigration("mois"))
        assertEquals("semaine" to "week", resolveSavedTabMigration("semaine"))
        assertEquals("categories" to "week", resolveSavedTabMigration("categories"))
        assertEquals("historique" to "week", resolveSavedTabMigration("historique"))
        assertEquals("vue" to "week", resolveSavedTabMigration("vue"))

        // Phase 5: "More features" preference defaults to false for new users and true for existing users with data
        context.getSharedPreferences("masroufi_ui_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        assertFalse(com.example.ui.isMoreFeaturesEnabled(context, hasExistingData = false))
        assertTrue(com.example.ui.isMoreFeaturesEnabled(context, hasExistingData = true))
        com.example.ui.setMoreFeaturesEnabled(context, true)
        assertTrue(com.example.ui.isMoreFeaturesEnabled(context, hasExistingData = false))
        com.example.ui.setMoreFeaturesEnabled(context, false)
        assertFalse(com.example.ui.isMoreFeaturesEnabled(context, hasExistingData = true))

        // Phase 6: Shortened 4-tab labels and new keys in FR, EN, AR, and Darija
        val en = Translations.forLang("en")
        assertEquals("Home", en.navVue)
        assertEquals("Insights", en.navSemaine)
        assertEquals("Plan", en.navCat)
        assertEquals("Activity", en.navHist)
        assertEquals("More options", en.moreOptionsLabel)
        assertEquals("More features", en.moreFeaturesTitle)

        val fr = Translations.forLang("fr")
        assertEquals("Accueil", fr.navVue)
        assertEquals("Analyses", fr.navSemaine)
        assertEquals("Plan", fr.navCat)
        assertEquals("Activité", fr.navHist)

        val ar = Translations.forLang("ar")
        assertTrue(ar.isRtl)
        assertTrue(ar.moreOptionsLabel.isNotBlank())
        assertTrue(ar.moreFeaturesTitle.isNotBlank())

        val darija = Translations.forLang("darija")
        assertTrue(darija.isRtl)
        assertTrue(darija.moreOptionsLabel.isNotBlank())
        assertTrue(darija.moreFeaturesTitle.isNotBlank())
    }

    @Test
    fun `Masroufi Improvement Plan Phases 1 to 6 money rounding, unified projection, Darija learner, and friendly dates work accurately`() {
        // 1. Money precision rounding to 2 decimals via BigDecimal centimes (rejects non-finite Double)
        assertEquals(Money(1525L), Money.fromLegacyDoubleDh(15.254))
        assertEquals(Money(1526L), Money.fromLegacyDoubleDh(15.256))
        assertTrue(runCatching { Money.fromLegacyDoubleDh(Double.NaN) }.isFailure)

        // 2. Unified overspend projection: 5276 DH in 16 of 31 days (no single-day >3x outlier) -> ~10222 DH
        val window31 = PayCycleEngine.computeWindow("2026-05", 1, "2026-05-16")
        assertEquals(31, window31.totalDays)
        assertEquals(16, window31.daysRemaining)
        val uniformDailyTxs = (1..16).map { day ->
            val dStr = day.toString().padStart(2, '0')
            TransactionItem(
                id = "tx_u_$day",
                type = "expense",
                amount = Money(32975L), // 5276 / 16 = 329.75 DH exact
                category = "Marché",
                envelopeKey = "marche",
                desc = "Courses $day",
                isoDate = "2026-05-$dStr"
            )
        }
        val unifiedProj = PayCycleEngine.computeOverspendProjection(
            window = window31,
            totalAvailableBudget = Money.fromDh(8000),
            spentSoFar = Money.fromDh(5276),
            scheduledBills = Money.ZERO,
            pastOrTodayCycleTransactions = uniformDailyTxs,
            bills = emptyList()
        )
        assertTrue(
            "Expected projectedTotal ~10222.25 DH, got ${unifiedProj.projectedTotal}",
            kotlin.math.abs(unifiedProj.projectedTotal.centimes - 1_022_225L) <= 100L
        )

        // 3. Expanded Darija vocabulary (gheda, echa, dwa, khobz) & category learner
        val envelopes = listOf(
            EnvelopeConfig(id = "e_cafe", key = "cafe", nameFr = "Café & Sorties", percentage = 20.0),
            EnvelopeConfig(id = "e_marche", key = "marche", nameFr = "Marché", percentage = 30.0),
            EnvelopeConfig(id = "e_autre", key = "autre", nameFr = "Autre", percentage = 10.0)
        )
        val envStatuses = PayCycleEngine.computeEnvelopes(
            envelopes = envelopes,
            baseBudget = Money.fromDh(6000),
            rolloverEnabled = false,
            currentWindow = window31,
            previousWindow = PayCycleEngine.computeWindow("2026-04", 1, "2026-05-16"),
            allTransactions = emptyList()
        )
        assertEquals("cafe", com.example.domain.VoiceDarijaParser.parse("gheda 45")?.envelopeKey)
        assertEquals("cafe", com.example.domain.VoiceDarijaParser.parse("echa 60")?.envelopeKey)
        assertEquals("autre", com.example.domain.VoiceDarijaParser.parse("dwa 120")?.envelopeKey)
        assertEquals("charges", com.example.domain.VoiceDarijaParser.parse("khobz 5")?.envelopeKey)

        PayCycleEngine.clearLearnedCategories()
        PayCycleEngine.learnCategoryForDescription("Boulangerie Atlas", "marche")
        assertEquals("marche", PayCycleEngine.getLearnedCategoryForDescription("boulangerie atlas"))
        val learnedGuess = PayCycleEngine.inferSmartDefaults("Boulangerie Atlas", emptyList(), envStatuses, "acc_cash")
        assertNotNull(learnedGuess)
        assertEquals("marche", learnedGuess!!.envelopeKey)

        // 4. Uncapped envelope usagePercent is 0 (percentages mean only spent vs cap)
        val uncappedEnvs = PayCycleEngine.computeEnvelopes(
            envelopes = listOf(EnvelopeConfig(id = "e_uncapped", key = "autre", nameFr = "Autre", percentage = 15.0, hasCap = false)),
            baseBudget = Money.fromDh(5000),
            rolloverEnabled = false,
            currentWindow = window31,
            previousWindow = PayCycleEngine.computeWindow("2026-04", 1, "2026-05-16"),
            allTransactions = uniformDailyTxs.map { it.copy(envelopeKey = "autre") }
        )
        assertEquals(0, uncappedEnvs.first().usagePercent)

        // 5. Friendly date formatting in FR, EN, AR, Darija
        assertEquals("Aujourd'hui", PayCycleEngine.formatFriendlyDate("2026-05-16", "fr", "2026-05-16"))
        assertEquals("Hier", PayCycleEngine.formatFriendlyDate("2026-05-15", "fr", "2026-05-16"))
        assertEquals("Today", PayCycleEngine.formatFriendlyDate("2026-05-16", "en", "2026-05-16"))
        assertEquals("14 mai", PayCycleEngine.formatFriendlyDate("2026-05-14", "fr", "2026-05-16"))
        assertEquals("1 mai → 31 mai", PayCycleEngine.formatCycleRangeFriendly("2026-05-01", "2026-05-31", "fr"))

        // 6. Moonshine multi-expense sentence parsing & hands-free Budget Q&A over DashboardState
        val multiDarija = com.example.domain.VoiceDarijaParser.parseMultiExpenses(
            "9ahwa 15 w taxi 25 w khobz 5",
            envelopes,
            "darija"
        )
        assertEquals(3, multiDarija.size)
        assertEquals(Money.fromDh(15), multiDarija[0].amount)
        assertEquals("cafe", multiDarija[0].envelopeKey)
        assertEquals(Money.fromDh(25), multiDarija[1].amount)
        assertEquals(Money.fromDh(5), multiDarija[2].amount)

        val multiArabic = com.example.domain.VoiceDarijaParser.parseMultiExpenses(
            "قهوة 15 وطاكسي 25 وخبز 5",
            envelopes,
            "ar"
        )
        assertEquals(3, multiArabic.size)
        assertEquals(Money.fromDh(45), multiArabic.sumMoney { it.amount })

        val sampleState = DashboardState(
            profile = UserProfile(language = "darija"),
            safeToSpendToday = Money.fromDh(165),
            remainingBudget = Money.fromDh(2480),
            cycleExpenses = Money.fromDh(1520),
            totalAvailableBudget = Money.fromDh(4000),
            totalNetWorth = Money.fromDh(9200),
            envelopes = envStatuses,
            hideAmounts = false
        )
        val qaSafeToday = com.example.domain.VoiceDarijaParser.evaluateVoiceUtterance(
            "شحال بقى ليا اليوم؟",
            sampleState
        )
        assertTrue(qaSafeToday is com.example.domain.VoiceBatchParseResult.BudgetQuery)
        val queryObj = qaSafeToday as com.example.domain.VoiceBatchParseResult.BudgetQuery
        assertEquals(com.example.domain.VoiceQueryType.SAFE_TODAY, queryObj.queryType)
        assertTrue(queryObj.displayValue.contains("165"))

        // Verify privacy masking on Moonshine Q&A visual display
        val maskedQa = com.example.domain.VoiceDarijaParser.evaluateVoiceUtterance(
            "Ch7al b9a liya lyoum?",
            sampleState.copy(hideAmounts = true)
        ) as com.example.domain.VoiceBatchParseResult.BudgetQuery
        assertTrue(maskedQa.displayValue.contains("••••"))
    }

    @Test
    fun `smart widgets, midnight rollover, SmartSlotEngine priority rules 1 to 5, quick capture undo, and per-widget style persistence work accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Phase 1: Midnight rollover resets todaySpend to 0.0 and recomputes safeToSpendToday when date advances
        val yesterdaySnapshot = com.example.widget.WidgetSnapshot(
            snapshotDateIso = "2026-05-10",
            todaySpend = Money.fromDh(120),
            safeToSpendToday = Money.fromDh(100),
            remainingBudget = Money.fromDh(1400),
            daysRemainingInCycle = 15,
            currencySymbol = "DH",
            language = "fr"
        )
        assertEquals(Money.fromDh(120), yesterdaySnapshot.effectiveTodaySpend("2026-05-10"))
        assertEquals(Money.ZERO, yesterdaySnapshot.effectiveTodaySpend("2026-05-11"))

        val rolledOver = yesterdaySnapshot.withMidnightRolloverIfNeeded("2026-05-11")
        assertEquals("2026-05-11", rolledOver.snapshotDateIso)
        assertEquals(Money.ZERO, rolledOver.todaySpend)
        assertEquals(14, rolledOver.daysRemainingInCycle)
        assertEquals(Money.fromDh(100), rolledOver.safeToSpendToday) // 1400 / 14 days = 100 DH/day

        // 2. Phase 7: Pure SmartSlotEngine priority rules 1 through 5
        val baseSmartSnap = com.example.widget.WidgetSnapshot(
            snapshotDateIso = "2026-05-11",
            todaySpend = Money.fromDh(45),
            safeToSpendToday = Money.fromDh(150),
            remainingBudget = Money.fromDh(2100),
            cycleExpenses = Money.fromDh(1900),
            totalAvailableBudget = Money.fromDh(4000),
            daysRemainingInCycle = 14,
            alertLevel = "NONE",
            language = "fr",
            currencySymbol = "DH"
        )

        // Priority 1: Bill or Daret due within 2 days
        val snapWithUrgentBill = baseSmartSnap.copy(
            reminders = listOf(
                com.example.widget.WidgetReminderItem(
                    id = "rem_1",
                    itemId = "b_1",
                    kind = "bill",
                    title = "Fibre Orange",
                    subtitle = "Échéance J-1",
                    amount = Money.fromDh(249),
                    dueDay = 12,
                    dueWithinDays = 1,
                    actionLabel = "Payé"
                )
            ),
            envelopes = listOf(
                com.example.widget.WidgetEnvelopeItem(
                    key = "cafe",
                    name = "Café",
                    spent = Money.fromDh(950),
                    totalAvailable = Money.fromDh(1000),
                    usagePercent = 95,
                    hasCap = true,
                    isOver90Percent = true
                )
            ),
            alertLevel = "RED"
        )
        val p1 = com.example.widget.SmartSlotEngine.resolveSmartSlot(snapWithUrgentBill, currentHourOfDay = 10, nowIso = "2026-05-11")
        assertEquals(1, p1.priorityRank)
        assertTrue(p1.headlineValue.contains("Fibre Orange"))
        assertEquals("categories", p1.targetTabKey)

        // Priority 2: Envelope >= 90% when no bill is due within 2 days
        val snapWithOver90Env = snapWithUrgentBill.copy(reminders = emptyList())
        val p2 = com.example.widget.SmartSlotEngine.resolveSmartSlot(snapWithOver90Env, currentHourOfDay = 10, nowIso = "2026-05-11")
        assertEquals(2, p2.priorityRank)
        assertTrue(p2.headlineValue.contains("95%"))
        assertEquals("categories", p2.targetTabKey)

        // Priority 3: High projection alert (RED or AMBER) when no urgent bill or >=90% envelope
        val snapWithProjectionAlert = baseSmartSnap.copy(
            alertLevel = "AMBER",
            monthEndForecastMessage = "Rythme élevé en fin de cycle"
        )
        val p3 = com.example.widget.SmartSlotEngine.resolveSmartSlot(snapWithProjectionAlert, currentHourOfDay = 10, nowIso = "2026-05-11")
        assertEquals(3, p3.priorityRank)
        assertEquals("semaine", p3.targetTabKey)

        // Priority 4: Morning (< 17h) -> Left Today (Safe to spend)
        val p4Morning = com.example.widget.SmartSlotEngine.resolveSmartSlot(baseSmartSnap, currentHourOfDay = 9, nowIso = "2026-05-11")
        assertEquals(4, p4Morning.priorityRank)
        assertTrue(p4Morning.headlineValue.contains("150"))
        assertEquals("vue", p4Morning.targetTabKey)

        // Priority 5: Evening (>= 17h) -> Spent Today
        val p5Evening = com.example.widget.SmartSlotEngine.resolveSmartSlot(baseSmartSnap, currentHourOfDay = 19, nowIso = "2026-05-11")
        assertEquals(5, p5Evening.priorityRank)
        assertTrue(p5Evening.headlineValue.contains("45"))

        // 3. Phase 4: Per-widget style persistence & privacy modes
        val customStyle = com.example.widget.WidgetStyle(
            appWidgetId = 77,
            widgetKind = com.example.widget.WidgetKind.ENVELOPES,
            themeMode = "accent",
            glassEnabled = true,
            glassOpacityLevel = 4,
            density = "compact",
            privacyMode = "always_hide",
            onlyOver90Envelopes = true
        )
        com.example.widget.MasroufiWidgetStore.saveWidgetStyle(context, customStyle)
        val loadedStyle = com.example.widget.MasroufiWidgetStore.loadWidgetStyle(context, 77)
        assertEquals(com.example.widget.WidgetKind.ENVELOPES, loadedStyle.widgetKind)
        assertEquals(4, loadedStyle.glassOpacityLevel)
        assertEquals("compact", loadedStyle.density)
        assertTrue(loadedStyle.shouldMaskAmounts(snapshotHideAmounts = false, appLockEnabled = false))

        // 4. Phase 2: Quick Capture multi-expense batch recording & 5-second Undo
        val todayIso = PayCycleEngine.todayIso()
        com.example.widget.MasroufiWidgetStore.saveWidgetSnapshot(
            context,
            baseSmartSnap.copy(snapshotDateIso = todayIso, todaySpend = Money.fromDh(20), remainingBudget = Money.fromDh(1400), daysRemainingInCycle = 10)
        )
        val batchItems = com.example.domain.VoiceDarijaParser.parseMultiExpenses("9ahwa 15 w taxi 25", emptyList(), "darija")
        assertEquals(2, batchItems.size)

        val recordedTxs = com.example.widget.MasroufiWidgetStore.recordQuickCaptureTransactions(context, batchItems)
        assertEquals(2, recordedTxs.size)
        val afterRecordSnap = com.example.widget.MasroufiWidgetStore.loadSnapshot(context, todayIso)
        assertEquals(Money.fromDh(60), afterRecordSnap.todaySpend) // 20 + 15 + 25 = 60 DH

        // Undo batch restores previous todaySpend and removes from pending queue
        com.example.widget.MasroufiWidgetStore.undoQuickCaptureBatch(context, recordedTxs)
        val afterUndoSnap = com.example.widget.MasroufiWidgetStore.loadSnapshot(context, todayIso)
        assertEquals(Money.fromDh(20), afterUndoSnap.todaySpend)
        val drainedAfterUndo = com.example.widget.MasroufiWidgetStore.consumePendingQuickCaptureTransactions(context)
        assertTrue(drainedAfterUndo.none { it.id in recordedTxs.map { r -> r.id } })
    }

    @Test
    fun `phase 9 quick capture notification, phase 10 bank notification parser and store, and phase 11 voice launcher alias work accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // --- PHASE 9: QuickCaptureNotification ---
        context.getSharedPreferences("masroufi_quick_capture_notif_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        assertFalse(com.example.notifications.QuickCaptureNotification.isEnabled(context))
        assertTrue(com.example.notifications.QuickCaptureNotification.isShowLeftTodayEnabled(context))
        assertFalse(com.example.notifications.QuickCaptureNotification.isShowLockScreenAmountsEnabled(context))
        assertFalse(com.example.notifications.QuickCaptureNotification.isQuietHoursEnabled(context))

        // Quiet hours window (22h -> 07h)
        assertTrue(com.example.notifications.QuickCaptureNotification.isHourInQuietWindow(23, 22, 7))
        assertTrue(com.example.notifications.QuickCaptureNotification.isHourInQuietWindow(2, 22, 7))
        assertFalse(com.example.notifications.QuickCaptureNotification.isHourInQuietWindow(8, 22, 7))
        assertFalse(com.example.notifications.QuickCaptureNotification.isHourInQuietWindow(21, 22, 7))

        val qcSnap = com.example.widget.WidgetSnapshot(
            snapshotDateIso = PayCycleEngine.todayIso(),
            todaySpend = Money.fromDh(50),
            safeToSpendToday = Money.fromDh(180),
            currencySymbol = "DH",
            hideAmounts = false,
            language = "fr",
            presets = listOf(com.example.widget.WidgetPresetItem("p1", "Café", Money.fromDh(15), "cafe"))
        )
        com.example.widget.MasroufiWidgetStore.saveWidgetSnapshot(context, qcSnap)

        // When POST_NOTIFICATIONS is not granted on Android 13+, enabling switches back off cleanly
        org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        com.example.notifications.QuickCaptureNotification.setEnabled(context, true)
        assertFalse(com.example.notifications.QuickCaptureNotification.isEnabled(context))

        // Grant POST_NOTIFICATIONS and enable
        org.robolectric.Shadows.shadowOf(context)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        com.example.notifications.QuickCaptureNotification.setEnabled(context, true)
        assertTrue(com.example.notifications.QuickCaptureNotification.isEnabled(context))
        com.example.notifications.QuickCaptureNotification.setSelectedPresetId(context, "p1")
        val specDefaultLock = com.example.notifications.QuickCaptureNotification.buildSpec(context, currentHourOfDay = 10)
        assertNotNull(specDefaultLock)
        assertEquals(androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE, specDefaultLock!!.visibility)
        assertTrue(specDefaultLock.bodyText.contains("180"))
        assertTrue(specDefaultLock.actionLabels.any { it.contains("Café") })

        // When hideAmounts is on, amounts are masked in QuickCaptureNotification
        com.example.widget.MasroufiWidgetStore.saveWidgetSnapshot(context, qcSnap.copy(hideAmounts = true))
        val specMasked = com.example.notifications.QuickCaptureNotification.buildSpec(context, currentHourOfDay = 10)
        assertNotNull(specMasked)
        assertTrue(specMasked!!.bodyText.contains("••••"))

        // When lock-screen amounts toggle is explicitly enabled and hideAmounts is off -> VISIBILITY_PUBLIC
        com.example.widget.MasroufiWidgetStore.saveWidgetSnapshot(context, qcSnap.copy(hideAmounts = false))
        com.example.notifications.QuickCaptureNotification.setShowLockScreenAmountsEnabled(context, true)
        val specPublicLock = com.example.notifications.QuickCaptureNotification.buildSpec(context, currentHourOfDay = 10)
        assertNotNull(specPublicLock)
        assertEquals(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC, specPublicLock!!.visibility)

        // --- PHASE 10C: Pure Kotlin BankMessageParser (FR, AR, EN, OTP discard, formats) ---
        // 1. OTP / security code messages must be discarded first
        assertTrue(
            com.example.domain.BankMessageParser.isOtpOrSecurityCodeMessage(
                "Votre code de sécurité 3D Secure pour valider votre paiement de 250.00 MAD est 482910"
            )
        )
        assertNull(
            com.example.domain.BankMessageParser.parse(
                rawTitle = "CIH Bank",
                rawBody = "Code OTP: 839201 pour opération de 500 MAD",
                sourcePackage = "com.cih.bank",
                isoDate = "2026-05-11"
            )
        )

        // 2. French debit notification with thousand space separator ("1 234,50 MAD") and merchant ("chez Marjane")
        val frDebit = com.example.domain.BankMessageParser.parse(
            rawTitle = "Attijariwafa",
            rawBody = "Paiement par carte de 1 234,50 MAD chez MARJANE CALIFORNIE le 11/05",
            sourcePackage = "com.attijari.mobile",
            isoDate = "2026-05-11"
        )
        assertNotNull(frDebit)
        assertEquals(Money(123450L), frDebit!!.amount)
        assertEquals("expense", frDebit.direction)
        assertTrue(frDebit.merchant.contains("MARJANE", ignoreCase = true))
        assertEquals("charges", frDebit.suggestedEnvelopeKey)
        assertFalse(frDebit.needsReview)

        // 3. European dot thousand separator ("1.234,50 DH") credit notification
        val frCredit = com.example.domain.BankMessageParser.parse(
            rawTitle = "Banque Populaire",
            rawBody = "Virement reçu de 1.234,50 DH sur votre compte",
            sourcePackage = "com.bp.pocketbank",
            isoDate = "2026-05-11"
        )
        assertNotNull(frCredit)
        assertEquals(Money(123450L), frCredit!!.amount)
        assertEquals("income", frCredit.direction)

        // 4. Arabic notification with Arabic-Indic digits ("١٢٠.٥٠ درهم") and merchant ("لدى مقهى أرابيكا")
        val arDebit = com.example.domain.BankMessageParser.parse(
            rawTitle = "بريد بنك",
            rawBody = "عملية أداء بمبلغ ١٢٠.٥٠ درهم لدى مقهى أرابيكا",
            sourcePackage = "ma.baridbank.mobile",
            isoDate = "2026-05-11"
        )
        assertNotNull(arDebit)
        assertEquals(Money(12050L), arDebit!!.amount)
        assertEquals("expense", arDebit.direction)
        assertTrue(arDebit.merchant.contains("مقهى"))

        // 5. English notification with missing merchant -> needsReview = true
        val enNoMerchant = com.example.domain.BankMessageParser.parse(
            rawTitle = "CFG Bank",
            rawBody = "Card purchase of 85.00 MAD completed.",
            sourcePackage = "com.cfgbank.app",
            isoDate = "2026-05-11"
        )
        assertNotNull(enNoMerchant)
        assertEquals(Money.fromDh(85), enNoMerchant!!.amount)
        assertEquals("expense", enNoMerchant.direction)
        assertTrue(enNoMerchant.needsReview)

        // 6. Masked sample helper replaces digits with 'X'
        val maskedSample = com.example.domain.BankMessageParser.maskSampleText(
            "Paiement carte 9876 de 145,00 MAD chez Carrefour"
        )
        assertFalse(maskedSample.contains("9876"))
        assertFalse(maskedSample.contains("145"))
        assertTrue(maskedSample.contains("XXXX"))

        // 7. Duplicate detection within ±10 minutes of a manual transaction
        val nowMs = System.currentTimeMillis()
        val manualTxs = listOf(
            TransactionItem(
                id = "tx_man_1",
                type = "expense",
                amount = Money.fromDh(145),
                category = "Marché",
                envelopeKey = "marche",
                desc = "Carrefour",
                isoDate = "2026-05-11",
                createdAt = com.google.firebase.Timestamp(java.util.Date(nowMs - 3 * 60_000L))
            )
        )
        val matchedManual = com.example.domain.BankMessageParser.findRecentManualMatch(
            amount = Money.fromDh(145),
            direction = "expense",
            isoDate = "2026-05-11",
            allTransactions = manualTxs,
            nowMs = nowMs
        )
        assertNotNull(matchedManual)
        assertEquals("tx_man_1", matchedManual!!.id)

        // --- PHASE 10A & 10D: BankDetectionStore allowlist, cap (40), 14-day expiry, kill switches, SmartSlot ---
        context.getSharedPreferences("masroufi_bank_detection_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        // Allowlist starts empty
        assertTrue(com.example.notifications.BankDetectionStore.getAllowedPackages(context).isEmpty())

        // Non-allowlisted package is ignored
        assertNull(
            com.example.notifications.BankDetectionStore.processIncomingNotification(
                context = context,
                sourcePackage = "com.untrusted.app",
                rawTitle = "Alert",
                rawBody = "Paiement par carte de 90 MAD chez Marjane"
            )
        )

        // Add package to allowlist and ingest
        com.example.notifications.BankDetectionStore.setPackageAllowed(context, "com.cih.bank", true)
        val ingested = com.example.notifications.BankDetectionStore.processIncomingNotification(
            context = context,
            sourcePackage = "com.cih.bank",
            rawTitle = "CIH Bank",
            rawBody = "Paiement par carte de 90 MAD chez Marjane",
            notificationKey = "n1",
            nowMillis = nowMs
        )
        assertNotNull(ingested)
        assertEquals(1, com.example.notifications.BankDetectionStore.loadPendingItems(context, nowMs).size)

        // Dedupe hash blocks identical notification with the same key
        val duplicateIngest = com.example.notifications.BankDetectionStore.processIncomingNotification(
            context = context,
            sourcePackage = "com.cih.bank",
            rawTitle = "CIH Bank",
            rawBody = "Paiement par carte de 90 MAD chez Marjane",
            notificationKey = "n1",
            nowMillis = nowMs + 10_000L
        )
        assertNull(duplicateIngest)
        assertEquals(1, com.example.notifications.BankDetectionStore.loadPendingItems(context, nowMs).size)

        // 14-day expiry removes stale items automatically
        val fifteenDaysLaterMs = nowMs + 15L * 24L * 3600_000L
        assertEquals(0, com.example.notifications.BankDetectionStore.loadPendingItems(context, fifteenDaysLaterMs).size)

        // 40-item cap enforcement
        for (i in 1..45) {
            com.example.notifications.BankDetectionStore.processIncomingNotification(
                context = context,
                sourcePackage = "com.cih.bank",
                rawTitle = "CIH Bank",
                rawBody = "Paiement par carte de ${10 + i} MAD chez Marchand_$i",
                notificationKey = "n_batch_$i",
                nowMillis = nowMs + i * 1000L
            )
        }
        assertEquals(
            com.example.notifications.BankDetectionStore.MAX_PENDING_ITEMS,
            com.example.notifications.BankDetectionStore.loadPendingItems(context, nowMs + 60_000L).size
        )

        // SmartSlot Rule 1B (between Rule 1 and Rule 2): 3+ pending items or oldest > 24h
        val snapWithPendingDetections = qcSnap.copy(
            snapshotDateIso = "2026-05-11",
            pendingDetectionsCount = 3,
            oldestPendingDetectionHours = 2,
            envelopes = listOf(
                com.example.widget.WidgetEnvelopeItem(
                    key = "cafe",
                    name = "Café",
                    spent = Money.fromDh(950),
                    totalAvailable = Money.fromDh(1000),
                    usagePercent = 95,
                    hasCap = true,
                    isOver90Percent = true
                )
            )
        )
        val smartSlotPending = com.example.widget.SmartSlotEngine.resolveSmartSlot(
            snapWithPendingDetections,
            currentHourOfDay = 10,
            nowIso = "2026-05-11"
        )
        assertEquals(
            com.example.widget.SmartSlotReason.PENDING_BANK_DETECTIONS,
            smartSlotPending.reason
        )

        // Kill switch: Delete all detected data clears pending queue
        com.example.notifications.BankDetectionStore.deleteAllDetectedData(context)
        assertEquals(0, com.example.notifications.BankDetectionStore.loadPendingItems(context).size)

        // --- PHASE 11A: VoiceLauncherAliasManager ---
        assertFalse(com.example.notifications.VoiceLauncherAliasManager.isVoiceAliasEnabled(context))
        com.example.notifications.VoiceLauncherAliasManager.setVoiceAliasEnabled(context, true)
        assertTrue(com.example.notifications.VoiceLauncherAliasManager.isVoiceAliasEnabled(context))
        com.example.notifications.VoiceLauncherAliasManager.setVoiceAliasEnabled(context, false)
        assertFalse(com.example.notifications.VoiceLauncherAliasManager.isVoiceAliasEnabled(context))
    }

    @Test
    fun `GoogleAssistantExpenseBridge resolves spoken expense queries, structured BII parameters, deep links, and OPEN_APP_FEATURE voice launches accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snap = com.example.widget.WidgetSnapshot(
            snapshotDateIso = PayCycleEngine.todayIso(),
            todaySpend = Money.fromDh(30),
            safeToSpendToday = Money.fromDh(170),
            remainingBudget = Money.fromDh(1700),
            daysRemainingInCycle = 10,
            currencySymbol = "DH",
            language = "fr"
        )
        com.example.widget.MasroufiWidgetStore.saveWidgetSnapshot(context, snap)

        // 1. Spoken French/Darija query via Google Assistant SEARCH_ACTION with multiple expenses
        val searchIntent = android.content.Intent("com.google.android.gms.actions.SEARCH_ACTION").apply {
            putExtra(android.app.SearchManager.QUERY, "Ajouter dépense Café 15 DH et Taxi 25 DH")
        }
        val searchResolution = com.example.widget.GoogleAssistantExpenseBridge.resolveIntent(searchIntent, "fr")
        assertTrue(searchResolution is com.example.widget.AssistantExpenseResolution.AutoLoggedBatch)
        val batchFromQuery = (searchResolution as com.example.widget.AssistantExpenseResolution.AutoLoggedBatch).items
        assertEquals(2, batchFromQuery.size)
        assertEquals(Money.fromDh(40), batchFromQuery.sumMoney { it.amount })
        assertEquals("cafe", batchFromQuery[0].envelopeKey)
        assertEquals("transport", batchFromQuery[1].envelopeKey)

        // 2. Structured BII CREATE_MONEY_TRANSFER parameters (amount + merchant)
        val transferIntent = android.content.Intent(com.example.widget.GoogleAssistantExpenseBridge.ACTION_ASSISTANT_ADD_EXPENSE).apply {
            putExtra(com.example.widget.GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_AMOUNT, "85,50")
            putExtra(com.example.widget.GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_MERCHANT, "Marjane")
            putExtra(com.example.widget.GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_CURRENCY, "MAD")
        }
        val transferResolution = com.example.widget.GoogleAssistantExpenseBridge.resolveIntent(transferIntent, "fr")
        assertTrue(transferResolution is com.example.widget.AssistantExpenseResolution.AutoLoggedBatch)
        val batchFromTransfer = (transferResolution as com.example.widget.AssistantExpenseResolution.AutoLoggedBatch).items
        assertEquals(1, batchFromTransfer.size)
        assertEquals(Money(8550L), batchFromTransfer.first().amount)
        assertEquals("Marjane", batchFromTransfer.first().description)

        // 3. Deep link masroufi://assistant/add?query=9ahwa+15+w+khobz+5
        val deepLinkIntent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("masroufi://assistant/add?query=9ahwa%2015%20w%20khobz%205")
        )
        val deepLinkResolution = com.example.widget.GoogleAssistantExpenseBridge.resolveIntent(deepLinkIntent, "darija")
        assertTrue(deepLinkResolution is com.example.widget.AssistantExpenseResolution.AutoLoggedBatch)
        val batchFromDeepLink = (deepLinkResolution as com.example.widget.AssistantExpenseResolution.AutoLoggedBatch).items
        assertEquals(2, batchFromDeepLink.size)
        assertEquals(Money.fromDh(20), batchFromDeepLink.sumMoney { it.amount })

        // 4. OPEN_APP_FEATURE ("ajouter dépense" without inline amount) -> opens Moonshine voice capture live
        val openFeatureIntent = android.content.Intent(com.example.widget.GoogleAssistantExpenseBridge.ACTION_ASSISTANT_ADD_EXPENSE).apply {
            putExtra(com.example.widget.GoogleAssistantExpenseBridge.EXTRA_FEATURE, "ajouter dépense")
        }
        val openFeatureResolution = com.example.widget.GoogleAssistantExpenseBridge.resolveIntent(openFeatureIntent, "fr")
        assertTrue(openFeatureResolution is com.example.widget.AssistantExpenseResolution.OpenVoiceCapture)

        // 5. Translations and sample commands for all 4 languages
        listOf("fr", "en", "ar", "darija").forEach { lang ->
            val s = Translations.forLang(lang)
            assertTrue(s.assistantSectionTitle.isNotBlank())
            assertTrue(s.assistantPinShortcutBtn.isNotBlank())
            assertTrue(s.assistantAutoSavedBadge.isNotBlank())
            assertEquals(3, com.example.widget.GoogleAssistantExpenseBridge.sampleVoiceCommandsForLang(lang).size)
        }
    }

    @Test
    fun `three-way theme mode (System Device, Light, Dark) resolves and persists across CustomizationPrefs, WidgetSnapshot, and Translations accurately`() {
        // 1. Default themeMode is "system" and follows phone dark/light theme
        val defaultPrefs = CustomizationPrefs()
        assertEquals("system", defaultPrefs.normalizedThemeMode())
        assertTrue(defaultPrefs.resolveIsDark(systemInDark = true))
        assertFalse(defaultPrefs.resolveIsDark(systemInDark = false))

        // 2. Explicit "light" and "dark" override phone theme
        val lightPrefs = defaultPrefs.copy(themeMode = "light")
        assertFalse(lightPrefs.resolveIsDark(systemInDark = true))
        assertFalse(lightPrefs.resolveIsDark(systemInDark = false))

        val darkPrefs = defaultPrefs.copy(themeMode = "dark")
        assertTrue(darkPrefs.resolveIsDark(systemInDark = true))
        assertTrue(darkPrefs.resolveIsDark(systemInDark = false))

        // 3. CustomizationPrefs.fromFinancialItem parses 4th pipe segment and defaults to "system" for legacy 3-part items
        val legacyItem = FinancialItem(
            id = "custom_prefs",
            itemType = "custom_prefs",
            name = "Masroufi",
            subtitle = "majorelle|DH|accounts,presets",
            dayOrTurn = 127
        )
        assertEquals("system", CustomizationPrefs.fromFinancialItem(legacyItem).normalizedThemeMode())

        val darkItem = legacyItem.copy(subtitle = "majorelle|DH|accounts,presets|dark")
        assertEquals("dark", CustomizationPrefs.fromFinancialItem(darkItem).normalizedThemeMode())

        // 4. WidgetSnapshot serializes appThemeMode and resolves effective dark state
        val snapSystem = com.example.widget.WidgetSnapshot(appThemeMode = "system", isDarkTheme = false)
        assertTrue(snapSystem.resolveEffectiveIsDark(systemInDark = true))
        assertFalse(snapSystem.resolveEffectiveIsDark(systemInDark = false))

        val snapDarkJson = snapSystem.copy(appThemeMode = "dark", isDarkTheme = true).toJson()
        val snapDecoded = com.example.widget.WidgetSnapshot.fromJson(snapDarkJson)
        assertEquals("dark", snapDecoded.appThemeMode)
        assertTrue(snapDecoded.resolveEffectiveIsDark(systemInDark = false))

        // 5. Translations in FR, EN, AR, and Darija
        listOf("fr", "en", "ar", "darija").forEach { lang ->
            val s = Translations.forLang(lang)
            assertTrue(s.themeModeLabel.isNotBlank())
            assertTrue(s.themeModeSystem.isNotBlank())
            assertTrue(s.themeModeLight.isNotBlank())
            assertTrue(s.themeModeDark.isNotBlank())
        }
    }

    @Test
    fun `Phase 2 Moroccan Ryal, Dlaf, Francs, and expanded commute keywords parse accurately`() {
        val defaultEnvs = listOf(
            EnvelopeConfig(id = "cafe", key = "cafe", nameFr = "Café & Sorties", nameEn = "Coffee & Dining", nameAr = "مقهى", nameDarija = "قهوة"),
            EnvelopeConfig(id = "transport", key = "transport", nameFr = "Transport", nameEn = "Transport", nameAr = "نقل", nameDarija = "نقل"),
            EnvelopeConfig(id = "charges", key = "charges", nameFr = "Charges & Maison", nameEn = "Bills & Home", nameAr = "فواتير", nameDarija = "الدار"),
            EnvelopeConfig(id = "marche", key = "marche", nameFr = "Marché", nameEn = "Groceries", nameAr = "سوق", nameDarija = "تقدية"),
            EnvelopeConfig(id = "autre", key = "autre", nameFr = "Autre", nameEn = "Other", nameAr = "أخرى", nameDarija = "حاجة خرى")
        )

        // 1. Ryal & Dlaf & Francs conversions (1 DH = 20 Ryal, 1 DH = 100 Francs)
        val alfRyal = com.example.domain.VoiceDarijaParser.parse("قهوة ألف ريال", defaultEnvs, "darija")
        assertNotNull(alfRyal)
        assertEquals(Money.fromDh(50), alfRyal!!.amount)
        assertEquals("cafe", alfRyal.envelopeKey)

        val num1000Ryal = com.example.domain.VoiceDarijaParser.parse("taxi 1000 ryal", defaultEnvs, "darija")
        assertNotNull(num1000Ryal)
        assertEquals(Money.fromDh(50), num1000Ryal!!.amount)
        assertEquals("transport", num1000Ryal.envelopeKey)

        val myaRyal = com.example.domain.VoiceDarijaParser.parse("خبز مية ريال", defaultEnvs, "darija")
        assertNotNull(myaRyal)
        assertEquals(Money.fromDh(5), myaRyal!!.amount)
        assertEquals("charges", myaRyal.envelopeKey)

        val num100Ryal = com.example.domain.VoiceDarijaParser.parse("khobz 100 ryal", defaultEnvs, "darija")
        assertNotNull(num100Ryal)
        assertEquals(Money.fromDh(5), num100Ryal!!.amount)

        val echrinAlf = com.example.domain.VoiceDarijaParser.parse("تقدية عشرين ألف ريال", defaultEnvs, "darija")
        assertNotNull(echrinAlf)
        assertEquals(Money.fromDh(1000), echrinAlf!!.amount)

        val khamsmya = com.example.domain.VoiceDarijaParser.parse("غداء خمسمية ريال", defaultEnvs, "darija")
        assertNotNull(khamsmya)
        assertEquals(Money.fromDh(25), khamsmya!!.amount)

        val joujDlaf = com.example.domain.VoiceDarijaParser.parse("مازوط جوج دلاف", defaultEnvs, "darija")
        assertNotNull(joujDlaf)
        assertEquals(Money.fromDh(100), joujDlaf!!.amount)
        assertEquals("transport", joujDlaf.envelopeKey)

        val achraDlaf = com.example.domain.VoiceDarijaParser.parse("الكراء عشرة دلاف", defaultEnvs, "darija")
        assertNotNull(achraDlaf)
        assertEquals(Money.fromDh(500), achraDlaf!!.amount)
        assertEquals("charges", achraDlaf.envelopeKey)

        val francs = com.example.domain.VoiceDarijaParser.parse("parking 500 francs", defaultEnvs, "fr")
        assertNotNull(francs)
        assertEquals(Money.fromDh(5), francs!!.amount)
        assertEquals("transport", francs.envelopeKey)

        // Multi-expense with Ryal
        val multiRyal = com.example.domain.VoiceDarijaParser.parseMultiExpenses(
            "قهوة ميتين ريال و طاكسي ألف ريال",
            defaultEnvs,
            "darija"
        )
        assertEquals(2, multiRyal.size)
        assertEquals(Money.fromDh(10), multiRyal[0].amount)
        assertEquals(Money.fromDh(50), multiRyal[1].amount)

        // 2. Expanded Moroccan commute, fuel, household & delivery keywords
        val commuteCases = listOf(
            "jawaz 50" to "transport",
            "جواز 80" to "transport",
            "navette 40" to "transport",
            "alboraq 180" to "transport",
            "لبراق 160" to "transport",
            "afriquia 200" to "transport",
            "winxo 150" to "transport",
            "shell 300" to "transport",
            "طاكسي كبير 25" to "transport",
            "طاكسي صغير 12" to "transport",
            "كازوال 250" to "transport",
            "ليصانص 100" to "transport",
            "lavage 40" to "transport",
            "la visite 350" to "charges",
            "vignette 650" to "charges",
            "assurance 1200" to "charges",
            "بوتاغاز 45" to "charges",
            "glovo 75" to "cafe",
            "mcdo 65" to "cafe"
        )
        commuteCases.forEach { (phrase, expectedEnv) ->
            val parsed = com.example.domain.VoiceDarijaParser.parse(phrase, defaultEnvs, "fr")
            assertNotNull("Expected non-null parse for '$phrase'", parsed)
            assertEquals("Expected $expectedEnv for '$phrase'", expectedEnv, parsed!!.envelopeKey)
        }
    }

    @Test
    fun `Phase 3 ATM cash withdrawal bank alerts map to Retrait GAB transfer without inflating expenses`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("masroufi_bank_detection_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("masroufi_widget_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val atmSamples = listOf(
            Triple("com.bcp.pocketbank", "Pocket Bank", "Retrait GAB de 500,00 MAD effectué sur votre compte Chaabi Net"),
            Triple("ma.attijariwafabank.lbankalik", "L'bankalik", "Retrait guichet automatique de 300.00 DH le 15/11"),
            Triple("ma.baridbank.mobile", "بريد بنك", "عملية سحب من الشباك بمبلغ 400 درهم"),
            Triple("com.cih.cihmobile", "CIH Mobile", "Opération GAB de 200 MAD effectuée avec succès")
        )

        atmSamples.forEach { (pkg, title, body) ->
            val parsed = com.example.domain.BankMessageParser.parse(
                rawTitle = title,
                rawBody = body,
                sourcePackage = pkg,
                isoDate = "2026-11-15",
                language = "fr"
            )
            assertNotNull("Expected ATM notification to parse for $title", parsed)
            assertEquals("transfer", parsed!!.direction)
            assertEquals("Retrait GAB", parsed.merchant)
            assertEquals("autre", parsed.suggestedEnvelopeKey)
            assertFalse(parsed.needsReview)
        }

        // Standard TPE / e-commerce card payments still map to "expense"
        val tpeDebit = com.example.domain.BankMessageParser.parse(
            rawTitle = "BMCE Direct",
            rawBody = "Paiement TPE de 180,00 MAD chez Decathlon le 15/11",
            sourcePackage = "com.bmce.agencedirecte",
            isoDate = "2026-11-15",
            language = "fr"
        )
        assertNotNull(tpeDebit)
        assertEquals("expense", tpeDebit!!.direction)

        val ecomDebit = com.example.domain.BankMessageParser.parse(
            rawTitle = "Attijariwafa",
            rawBody = "Paiement E-commerce de 95,00 MAD chez Glovo Maroc",
            sourcePackage = "ma.attijariwafabank.lbankalik",
            isoDate = "2026-11-15",
            language = "fr"
        )
        assertNotNull(ecomDebit)
        assertEquals("expense", ecomDebit!!.direction)
        assertEquals("cafe", ecomDebit.suggestedEnvelopeKey)

        // Confirming a detected ATM withdrawal creates a bank->cash transfer without inflating todaySpend
        com.example.widget.MasroufiWidgetStore.saveWidgetSnapshot(
            context,
            com.example.widget.WidgetSnapshot(
                snapshotDateIso = PayCycleEngine.todayIso(),
                todaySpend = Money.fromDh(40),
                safeToSpendToday = Money.fromDh(160),
                remainingBudget = Money.fromDh(1600),
                daysRemainingInCycle = 10
            )
        )
        com.example.notifications.BankDetectionStore.setPackageAllowed(context, "com.bcp.pocketbank", true)
        val detectedAtm = com.example.notifications.BankDetectionStore.processIncomingNotification(
            context = context,
            sourcePackage = "com.bcp.pocketbank",
            rawTitle = "Pocket Bank",
            rawBody = "Retrait GAB de 500,00 MAD effectué ce jour",
            notificationKey = "atm_1"
        )
        assertNotNull(detectedAtm)
        assertEquals("transfer", detectedAtm!!.direction)

        com.example.notifications.BankDetectionStore.confirmDetectedItem(context, detectedAtm)
        val pendingQuick = com.example.widget.MasroufiWidgetStore.consumePendingQuickCaptureTransactions(context)
        assertEquals(1, pendingQuick.size)
        val transferTx = pendingQuick.first()
        assertEquals("transfer", transferTx.type)
        assertEquals(Money.fromDh(500), transferTx.amount)
        assertEquals("acc_bank", transferTx.accountId)
        assertEquals("acc_cash", transferTx.toAccountId)

        val snapAfter = com.example.widget.MasroufiWidgetStore.loadSnapshot(context)
        assertEquals(Money.fromDh(40), snapAfter.todaySpend)
    }

    @Test
    fun `Phases 1, 2 FX, and 4 learned category persistence, local UID fallback, editable FX, Daret pot collection, and notification actions work end-to-end`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PayCycleEngine.clearLearnedCategories()

        // 1. Learned category persistence across PayCycleEngine JSON, FinancialItem (<= 250 chars), and AppSecurityManager
        PayCycleEngine.learnCategoryForDescription("boulangerie paul", "cafe")
        PayCycleEngine.learnCategoryForDescription("station total casa", "transport")
        val exportedJson = PayCycleEngine.exportLearnedCategoriesJson()
        AppSecurityManager.saveLearnedCategoriesJson(context, exportedJson)

        val finItem = PayCycleEngine.buildLearnedCategoriesFinancialItem(userId = com.example.data.MasroufiRepository.LOCAL_DEVICE_USER_ID)
        assertEquals("learned_categories", finItem.id)
        assertEquals("custom_prefs", finItem.itemType)
        assertTrue(finItem.subtitle.length <= 250)
        assertTrue(finItem.dateOrCycle.length <= 250)

        PayCycleEngine.clearLearnedCategories()
        assertNull(PayCycleEngine.getLearnedCategoryForDescription("boulangerie paul"))
        PayCycleEngine.importLearnedCategoriesFromFinancialItem(finItem)
        assertEquals("cafe", PayCycleEngine.getLearnedCategoryForDescription("boulangerie paul"))
        assertEquals("transport", PayCycleEngine.getLearnedCategoryForDescription("station total casa"))

        PayCycleEngine.clearLearnedCategories()
        PayCycleEngine.importLearnedCategoriesJson(AppSecurityManager.loadLearnedCategoriesJson(context).orEmpty())
        assertEquals("cafe", PayCycleEngine.getLearnedCategoryForDescription("boulangerie paul"))

        // 2. Collision safety & editable FX rates in CustomizationPrefs + PayCycleEngine + BackupSerializer
        val prefsWithCustomFx = CustomizationPrefs(
            accentPalette = "emerald",
            currencySymbol = "DH",
            customTitle = "Masroufi",
            themeMode = "system",
            eurToMadRate = 11.20,
            usdToMadRate = 10.15
        )
        val encodedPrefsItem = FinancialItem(
            id = "custom_prefs",
            itemType = "custom_prefs",
            name = prefsWithCustomFx.customTitle,
            subtitle = "emerald|DH|accounts,presets|system|11.20|10.15",
            dayOrTurn = prefsWithCustomFx.toVisibilityMask()
        )
        val decodedPrefs = CustomizationPrefs.fromFinancialItem(encodedPrefsItem)
        assertEquals(11.20, decodedPrefs.normalizedEurRate(), 0.001)
        assertEquals(10.15, decodedPrefs.normalizedUsdRate(), 0.001)

        val convertedEur = PayCycleEngine.convertCurrencyAmount(
            amount = Money.fromDh(10),
            fromCurrency = "EUR",
            toCurrency = "MAD",
            eurToMadRate = decodedPrefs.normalizedEurRate(),
            usdToMadRate = decodedPrefs.normalizedUsdRate()
        )
        assertEquals(Money.fromDh(112), convertedEur)

        val dummyState = DashboardState(
            profile = UserProfile(baseBudget = Money.fromDh(8000), payCycleDay = 1, language = "fr"),
            customPrefs = prefsWithCustomFx
        )
        val v2Json = BackupSerializer.encodeV2Backup(dummyState)
        val decodedBackup = BackupSerializer.decodeBackup(
            rawJson = v2Json,
            currentUserId = com.example.data.MasroufiRepository.LOCAL_DEVICE_USER_ID,
            fallbackProfile = dummyState.profile
        )
        assertNotNull(decodedBackup.customPrefs)
        assertEquals(11.20, decodedBackup.customPrefs!!.eurToMadRate, 0.001)
        assertEquals(10.15, decodedBackup.customPrefs!!.usdToMadRate, 0.001)
        assertEquals("cafe", decodedBackup.learnedCategories["boulangerie paul"])
        assertEquals("local_device_user", com.example.data.MasroufiRepository.LOCAL_DEVICE_USER_ID)

        // 3. Daret pot collection helpers & 4-language parity
        val daret = FinancialItem(
            id = "dar_1",
            itemType = "daret",
            name = "Daret Famille",
            subtitle = "Tour #3",
            amount = Money.fromDh(1000),
            daretMembers = 6,
            dayOrTurn = 3,
            extraInt = 2,
            dateOrCycle = "2026-12-01"
        )
        assertFalse(PayCycleEngine.isDaretPotCollected(daret))
        assertEquals("2026-12-01", PayCycleEngine.daretPayoutDateOnly(daret))

        val collectedDaret = PayCycleEngine.markDaretPotCollected(daret)
        assertTrue(PayCycleEngine.isDaretPotCollected(collectedDaret))
        assertEquals("2026-12-01", PayCycleEngine.daretPayoutDateOnly(collectedDaret))
        assertEquals(3, collectedDaret.extraInt)

        listOf("fr", "en", "ar", "darija").forEach { lang ->
            val s = Translations.forLang(lang)
            assertTrue(s.fxRatesSectionTitle.isNotBlank())
            assertTrue(s.fxEurRateLabel.isNotBlank())
            assertTrue(s.fxUsdRateLabel.isNotBlank())
            assertTrue(s.bankDetectTransferBadge.isNotBlank())
            assertTrue(s.daretCollectPotBtn.isNotBlank())
            assertTrue(s.daretPotCollectedBadge.isNotBlank())
            assertTrue(s.daretCollectAccountBank.isNotBlank())
            assertTrue(s.daretCollectAccountCash.isNotBlank())
            assertTrue(s.daretPotCollectedToast.isNotBlank())
        }

        // 4. Direct notification action snapshot updates (bills 4001 & daret 4002)
        val notifPrefs = context.getSharedPreferences("masroufi_notifications_prefs", Context.MODE_PRIVATE)
        notifPrefs.edit().putString(
            "notif_snapshot_json",
            org.json.JSONObject().apply {
                put("lang", "fr")
                put("currency", "DH")
                put("cycleKey", "2026-11")
                put("upcomingBills", org.json.JSONArray().put(org.json.JSONObject().apply {
                    put("id", "b1")
                    put("name", "Lydec")
                    put("amount", 320.0)
                    put("dueDay", 15)
                }))
                put("activeDarets", org.json.JSONArray().put(org.json.JSONObject().apply {
                    put("id", "d1")
                    put("name", "Daret Amis")
                    put("amount", 500.0)
                    put("myTurn", 2)
                    put("paidRounds", 1)
                    put("totalMembers", 5)
                }))
            }.toString()
        ).commit()

        com.example.notifications.MasroufiNotificationManager.removeBillOrAdvanceDaretInCachedSnapshot(
            context = context,
            itemId = "b1",
            isBill = true
        )
        com.example.notifications.MasroufiNotificationManager.removeBillOrAdvanceDaretInCachedSnapshot(
            context = context,
            itemId = "d1",
            isBill = false
        )
        val updatedNotifRaw = com.example.notifications.MasroufiNotificationManager.loadNotificationSnapshotRaw(context) ?: "{}"
        assertFalse(notifPrefs.contains("notif_snapshot_json"))
        val updatedNotifJson = org.json.JSONObject(updatedNotifRaw)
        assertEquals(0, updatedNotifJson.getJSONArray("upcomingBills").length())
        assertEquals(2, updatedNotifJson.getJSONArray("activeDarets").getJSONObject(0).getInt("paidRounds"))
    }

    @Test
    fun `Feature 1 Can I afford this live purchase impact preview handles zero days, zero safePool, little history vs weekend-weekday weights, runOutIso, envelope usage, and masking`() {
        // 1. Comma and dot amount parsing
        assertEquals(Money(8550L), PayCycleEngine.parseFlexibleAmount("85,50"))
        assertEquals(Money(8550L), PayCycleEngine.parseFlexibleAmount("85.50"))
        assertEquals(Money(123450L), PayCycleEngine.parseFlexibleAmount("1 234,50"))
        assertEquals(Money.ZERO, PayCycleEngine.parseFlexibleAmount(""))

        // 2. Little history (< 14 days): flat weights -> 850 safePool / 10 days = 85 DH/day
        val currentWindow = PayCycleEngine.computeWindow("2026-05", 1, "2026-05-15")
        val cafeEnv = PayCycleEngine.computeEnvelopes(
            envelopes = listOf(EnvelopeConfig(id = "e_cafe", key = "cafe", nameFr = "Café", percentage = 20.0, hasCap = true)),
            baseBudget = Money.fromDh(5000),
            rolloverEnabled = false,
            currentWindow = currentWindow,
            previousWindow = PayCycleEngine.computeWindow("2026-04", 1, "2026-05-15"),
            allTransactions = listOf(
                TransactionItem(id = "tx_c1", type = "expense", amount = Money.fromDh(400), envelopeKey = "cafe", isoDate = "2026-05-10")
            )
        ).first()
        val greenImpact = PayCycleEngine.computePurchaseImpact(
            rawAmountText = "50,00",
            safePool = Money.fromDh(850),
            daysRemaining = 10,
            envelope = cafeEnv
        )
        assertNotNull(greenImpact)
        assertEquals(com.example.domain.PurchaseVerdict.FITS_TODAY, greenImpact!!.verdict)
        assertEquals(com.example.domain.PurchaseVerdict.GREEN, greenImpact.verdict)
        assertEquals(Money.fromDh(85), greenImpact.currentDailyAllowance)
        assertEquals(Money.fromDh(80), greenImpact.newSafeToday)
        assertEquals(Money.fromDh(-5), greenImpact.delta)
        assertEquals(45, greenImpact.envelopeUsageAfter)
        assertEquals(Money.fromDh(550), greenImpact.envelopeRemainingAfter)

        // 3. Amber (LOWERS_ALLOWANCE) verdict with runOutIso from computeOverspendProjection
        val cycleTxs = (1..15).map { d ->
            TransactionItem(
                id = "c_$d",
                type = "expense",
                amount = Money.fromDh(150),
                isoDate = String.format(java.util.Locale.US, "2026-05-%02d", d)
            )
        }
        val amberImpact = PayCycleEngine.computePurchaseImpact(
            amount = Money.fromDh(150),
            safePool = Money.fromDh(850),
            daysRemaining = 10,
            expenseHistory = cycleTxs,
            window = currentWindow,
            envelope = cafeEnv,
            todayIso = "2026-05-15"
        )
        assertEquals(com.example.domain.PurchaseVerdict.LOWERS_ALLOWANCE, amberImpact.verdict)
        assertEquals(com.example.domain.PurchaseVerdict.AMBER, amberImpact.verdict)
        assertEquals(Money.fromDh(85), amberImpact.currentDailyAllowance)
        assertEquals(Money.fromDh(70), amberImpact.newSafeToday)
        assertEquals(Money.fromDh(-15), amberImpact.delta)
        assertEquals(55, amberImpact.envelopeUsageAfter)
        assertNotNull("Expected runOutIso when pace + purchase overspends cycle", amberImpact.runOutIso)

        // 4. Red (OVER_POOL) verdict: exceeds safePool (amount = 1000 > safePool = 850 -> over by 150 DH)
        val redImpact = PayCycleEngine.computePurchaseImpact(
            amount = Money.fromDh(1000),
            safePool = Money.fromDh(850),
            daysRemaining = 10,
            envelope = cafeEnv
        )
        assertEquals(com.example.domain.PurchaseVerdict.OVER_POOL, redImpact.verdict)
        assertEquals(com.example.domain.PurchaseVerdict.RED, redImpact.verdict)
        assertEquals(Money.fromDh(150), redImpact.overSafePoolAmount)
        assertEquals(Money.ZERO, redImpact.newSafeToday)

        // 5. Weekend vs Weekday day-of-week weighting (>= 14 days of history over last 8 weeks; excludes income & transfers)
        val eightWeekHistory = mutableListOf<TransactionItem>()
        val startEpoch = PayCycleEngine.parseEpochDays("2026-04-01")
        for (offset in 0 until 28) {
            val dateStr = PayCycleEngine.epochDaysToIso(startEpoch + offset)
            val isWe = com.example.ui.isWeekendForIsoDate(dateStr)
            eightWeekHistory += TransactionItem(
                id = "hist_exp_$offset",
                type = "expense",
                amount = if (isWe) Money.fromDh(200) else Money.fromDh(50),
                isoDate = dateStr
            )
            // Transfers and incomes must be ignored by day-of-week weighting
            if (offset % 5 == 0) {
                eightWeekHistory += TransactionItem(
                    id = "hist_tr_$offset",
                    type = "transfer",
                    amount = Money.fromDh(2000),
                    isoDate = dateStr
                )
                eightWeekHistory += TransactionItem(
                    id = "hist_inc_$offset",
                    type = "income",
                    amount = Money.fromDh(5000),
                    isoDate = dateStr
                )
            }
        }
        // Compare allowance on Friday 2026-05-15 (weekday) vs Saturday 2026-05-16 (weekend)
        val weekdayImpact = PayCycleEngine.computePurchaseImpact(
            amount = Money.fromDh(60),
            safePool = Money.fromDh(1000),
            daysRemaining = 10,
            expenseHistory = eightWeekHistory,
            window = null,
            envelope = null,
            todayIso = "2026-05-15" // Friday
        )
        val weekendImpact = PayCycleEngine.computePurchaseImpact(
            amount = Money.fromDh(60),
            safePool = Money.fromDh(1000),
            daysRemaining = 10,
            expenseHistory = eightWeekHistory,
            window = null,
            envelope = null,
            todayIso = "2026-05-16" // Saturday
        )
        assertTrue(
            "Expected weekend allowance (${weekendImpact.currentDailyAllowance}) > weekday allowance (${weekdayImpact.currentDailyAllowance})",
            weekendImpact.currentDailyAllowance > weekdayImpact.currentDailyAllowance
        )

        // 6. Edge cases: zero days remaining and zero safePool
        val zeroDaysGreen = PayCycleEngine.computePurchaseImpact(
            amount = Money.fromDh(40),
            safePool = Money.fromDh(100),
            daysRemaining = 0,
            envelope = null
        )
        assertEquals(com.example.domain.PurchaseVerdict.FITS_TODAY, zeroDaysGreen.verdict)
        assertEquals(Money.fromDh(100), zeroDaysGreen.currentDailyAllowance)
        assertEquals(Money.fromDh(60), zeroDaysGreen.newSafeToday)

        val zeroSafePoolRed = PayCycleEngine.computePurchaseImpact(
            amount = Money(2550L),
            safePool = Money.ZERO,
            daysRemaining = 0,
            envelope = null
        )
        assertEquals(com.example.domain.PurchaseVerdict.OVER_POOL, zeroSafePoolRed.verdict)
        assertEquals(Money(2550L), zeroSafePoolRed.overSafePoolAmount)

        // 7. Localized formatting & privacy masking (formatAmountOrMask) across all 4 languages
        listOf("fr", "en", "ar", "darija").forEach { lang ->
            val unmaskedAmber = PayCycleEngine.formatPurchaseImpactLine(
                impact = amberImpact,
                currencySymbol = "DH",
                hideAmounts = false,
                strings = Translations.forLang(lang),
                envelopeName = "Café"
            )
            assertTrue("Expected 85 and 70 in $lang amber line: $unmaskedAmber", unmaskedAmber.contains("85") && unmaskedAmber.contains("70"))
            assertTrue("Expected 55% envelope suffix in $lang: $unmaskedAmber", unmaskedAmber.contains("55%"))

            val maskedRed = PayCycleEngine.formatPurchaseImpactLine(
                impact = redImpact,
                currencySymbol = "DH",
                hideAmounts = true,
                strings = Translations.forLang(lang),
                envelopeName = "Café"
            )
            assertTrue("Expected masked dots in $lang red line: $maskedRed", maskedRed.contains("••••"))
            assertFalse("Unmasked 150 leaked in $lang: $maskedRed", maskedRed.contains("150"))
        }
    }

    @Test
    fun `Feature 3 Evening reminder time picker, adaptive smart timing (18h-22h30 clamp), 3-ignored throttling, pending bank count, and honest streak work accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notifPrefs = context.getSharedPreferences("masroufi_notifications_prefs", Context.MODE_PRIVATE)
        notifPrefs.edit().clear().commit()
        com.example.notifications.BankDetectionStore.deleteAllDetectedData(context)
        org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        // 1. Default time is 20:30, Smart suggestions default is true, and custom time persists cleanly
        assertTrue(com.example.notifications.MasroufiNotificationManager.isSmartSuggestionsEnabled(context))
        assertEquals(20 to 30, com.example.notifications.MasroufiNotificationManager.getEveningReminderTime(context))
        assertEquals("20:30", com.example.notifications.MasroufiNotificationManager.getEveningReminderTimeFormatted(context))
        com.example.notifications.MasroufiNotificationManager.setEveningReminderTime(context, 21, 15)
        assertEquals(21 to 15, com.example.notifications.MasroufiNotificationManager.getEveningReminderTime(context))

        // 2. Adaptive evening reminder timing (Prompt 5):
        // - Too little data (< 5 active days) falls back to user's chosen time (21:15)
        val sparseTxs = listOf(
            TransactionItem(id = "s1", type = "expense", amount = Money.fromDh(20), isoDate = "2026-05-14", hourOfDay = 19)
        )
        assertEquals(
            21 to 15,
            com.example.notifications.MasroufiNotificationManager.computeAdaptiveEveningReminderTime(
                context = context,
                allTransactions = sparseTxs,
                nowIso = "2026-05-15"
            )
        )

        // - 7 days where last expense of the day is at 19:00 -> median = 19 -> reminder 1h later = 20:00
        val normalTxs = (1..7).map { d ->
            TransactionItem(
                id = "n_$d",
                type = "expense",
                amount = Money.fromDh(30),
                isoDate = String.format(java.util.Locale.US, "2026-05-%02d", d + 5),
                hourOfDay = 19
            )
        }
        assertEquals(
            20 to 0,
            com.example.notifications.MasroufiNotificationManager.computeAdaptiveEveningReminderTime(
                context = context,
                allTransactions = normalTxs,
                nowIso = "2026-05-15"
            )
        )

        // - Early spender (median last expense at 14:00 -> +1h = 15:00) clamps to 18:00
        val earlyTxs = (1..6).map { d ->
            TransactionItem(
                id = "early_$d",
                type = "expense",
                amount = Money.fromDh(25),
                isoDate = String.format(java.util.Locale.US, "2026-05-%02d", d + 5),
                hourOfDay = 14
            )
        }
        assertEquals(
            18 to 0,
            com.example.notifications.MasroufiNotificationManager.computeAdaptiveEveningReminderTime(
                context = context,
                allTransactions = earlyTxs,
                nowIso = "2026-05-15"
            )
        )

        // - Late spender (median last expense at 23:00 -> +1h = 24:00) clamps to 22:30
        val lateTxs = (1..6).map { d ->
            TransactionItem(
                id = "late_$d",
                type = "expense",
                amount = Money.fromDh(45),
                isoDate = String.format(java.util.Locale.US, "2026-05-%02d", d + 5),
                hourOfDay = 23
            )
        }
        assertEquals(
            22 to 30,
            com.example.notifications.MasroufiNotificationManager.computeAdaptiveEveningReminderTime(
                context = context,
                allTransactions = lateTxs,
                nowIso = "2026-05-15"
            )
        )

        // - When "Smart suggestions" toggle is OFF, always use user's fixed time (21:15)
        com.example.notifications.MasroufiNotificationManager.setSmartSuggestionsEnabled(context, false)
        assertEquals(
            21 to 15,
            com.example.notifications.MasroufiNotificationManager.computeAdaptiveEveningReminderTime(
                context = context,
                allTransactions = normalTxs,
                nowIso = "2026-05-15"
            )
        )
        com.example.notifications.MasroufiNotificationManager.setSmartSuggestionsEnabled(context, true)

        // 3. Evening notification posts at most once per day with safeToday formatted body
        val todayIso = PayCycleEngine.todayIso()
        val snapJson = org.json.JSONObject().apply {
            put("lang", "en")
            put("currency", "DH")
            put("hideAmounts", false)
            put("cycleKey", todayIso.take(7))
            put("todayTxCount", 0)
            put("safeToday", 62.0)
            put("upcomingBills", org.json.JSONArray())
            put("activeDarets", org.json.JSONArray())
            put("envelopes", org.json.JSONArray())
        }.toString()
        notifPrefs.edit().putString("notif_snapshot_json", snapJson).commit()

        com.example.notifications.MasroufiNotificationManager.evaluateAndPostNotifications(
            context = context,
            fromScheduledEveningWorker = true
        )
        assertEquals(todayIso, com.example.notifications.MasroufiNotificationManager.getLastEveningShownIso(context))

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNm = org.robolectric.Shadows.shadowOf(nm)
        val postedEvening = shadowNm.getNotification(4004)
        assertNotNull("Expected evening notification 4004 to be posted", postedEvening)
        assertNotNull("Expected deleteIntent on evening notification 4004", postedEvening.deleteIntent)
        val eveningText = postedEvening.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertTrue("Expected '62 DH left today' in '$eveningText'", eveningText.contains("62") && eveningText.contains("DH"))
        assertEquals(2, postedEvening.actions?.size ?: 0)

        // Running evaluateAndPostNotifications a second time on the same day does NOT re-post (once-per-day guard)
        nm.cancelAll()
        com.example.notifications.MasroufiNotificationManager.evaluateAndPostNotifications(
            context = context,
            fromScheduledEveningWorker = true
        )
        assertNull("Expected once-per-day guard to skip duplicate evening notification", shadowNm.getNotification(4004))

        // 4. Dismissal tracking & every-other-day throttling after 3 ignored reminders in a row
        val dismissIntent = android.content.Intent(context, com.example.notifications.NotificationActionReceiver::class.java).apply {
            action = com.example.notifications.NotificationActionReceiver.ACTION_DISMISS_EVENING_REMINDER
        }
        repeat(3) {
            com.example.notifications.NotificationActionReceiver().onReceive(context, dismissIntent)
        }
        assertEquals(3, com.example.notifications.MasroufiNotificationManager.getEveningIgnoredStreak(context))
        val tomorrowIso = PayCycleEngine.epochDaysToIso(PayCycleEngine.parseEpochDays(todayIso) + 1L)
        val dayAfterTomorrowIso = PayCycleEngine.epochDaysToIso(PayCycleEngine.parseEpochDays(todayIso) + 2L)
        assertTrue(
            "Expected consecutive day after 3 ignored reminders to be throttled",
            com.example.notifications.MasroufiNotificationManager.shouldThrottleEveningForIgnoredStreak(context, tomorrowIso)
        )
        assertFalse(
            "Expected every-other-day reminder (2 days later) to be allowed",
            com.example.notifications.MasroufiNotificationManager.shouldThrottleEveningForIgnoredStreak(context, dayAfterTomorrowIso)
        )

        // 5. Tapping "Nothing today" resets ignored streak to 0, stores noSpendConfirmedIso, and records verifiedActiveDate
        val nothingIntent = android.content.Intent(context, com.example.notifications.NotificationActionReceiver::class.java).apply {
            action = com.example.notifications.NotificationActionReceiver.ACTION_CONFIRM_NO_SPEND_TODAY
            putExtra(com.example.notifications.NotificationActionReceiver.EXTRA_NOTIF_ID, 4004)
        }
        com.example.notifications.NotificationActionReceiver().onReceive(context, nothingIntent)
        assertEquals(0, com.example.notifications.MasroufiNotificationManager.getEveningIgnoredStreak(context))
        assertEquals(todayIso, com.example.notifications.MasroufiNotificationManager.getNoSpendConfirmedIso(context))
        assertTrue(com.example.notifications.MasroufiNotificationManager.getVerifiedActiveDates(context).contains(todayIso))

        // 6. Honest streak calculation: unverified silent days stop the streak, while verified active / confirmed / income-transfer days count
        val streakTxs = listOf(
            TransactionItem(id = "e1", type = "expense", amount = Money.fromDh(40), isoDate = "2026-05-05"),
            TransactionItem(id = "i1", type = "income", amount = Money.fromDh(500), isoDate = "2026-05-07")
        )
        val honestStreakPartial = PayCycleEngine.computeNoSpendStreakDays(
            allTransactions = streakTxs,
            nowIso = "2026-05-08",
            verifiedActiveDates = setOf("2026-05-08")
        )
        assertEquals(2, honestStreakPartial)

        val honestStreakFull = PayCycleEngine.computeNoSpendStreakDays(
            allTransactions = streakTxs,
            nowIso = "2026-05-08",
            verifiedActiveDates = setOf("2026-05-08", "2026-05-06")
        )
        assertEquals(3, honestStreakFull)
    }

    @Test
    fun `Feature 2 Time-aware one-tap shortcuts handle cold start under 15 txs, min 4 matches, weekday vs weekend split, mode amount, recency tie-breaker, and 3-edit learning`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.notifications.MasroufiNotificationManager.resetAllSmartLearning(context)

        val envelopes = listOf(
            EnvelopeConfig(id = "e_cafe", key = "cafe", nameFr = "Café", percentage = 20.0),
            EnvelopeConfig(id = "e_trans", key = "transport", nameFr = "Transport", percentage = 20.0),
            EnvelopeConfig(id = "e_marche", key = "marche", nameFr = "Marché", percentage = 30.0)
        )
        val window = PayCycleEngine.computeWindow("2026-05", 1, "2026-05-15")
        val envStatuses = PayCycleEngine.computeEnvelopes(
            envelopes = envelopes,
            baseBudget = Money.fromDh(5000),
            rolloverEnabled = false,
            currentWindow = window,
            previousWindow = PayCycleEngine.computeWindow("2026-04", 1, "2026-05-15"),
            allTransactions = emptyList()
        )

        // 1. Cold start (< 15 eligible transactions with hourOfDay) returns emptyList()
        val coldStartTxs = (1..10).map { i ->
            TransactionItem(
                id = "cold_$i",
                type = "expense",
                amount = Money.fromDh(12),
                desc = "Qahwa",
                category = "Café",
                envelopeKey = "cafe",
                isoDate = "2026-05-${i.toString().padStart(2, '0')}",
                hourOfDay = 8
            )
        }
        val coldShortcuts = com.example.ui.computeTimeAwareShortcuts(
            state = DashboardState(envelopes = envStatuses, allTransactions = coldStartTxs),
            nowHour = 8,
            isWeekend = false,
            nowIso = "2026-05-15"
        )
        assertTrue("Expected empty shortcuts when < 15 eligible transactions exist", coldShortcuts.isEmpty())

        // 2. Build history with >= 15 transactions across weekday mornings and weekend mornings (with >= 4 matches per chip)
        val richHistory = mutableListOf<TransactionItem>()
        val weekdayDates = listOf("2026-05-04", "2026-05-05", "2026-05-06", "2026-05-07", "2026-05-08", "2026-05-11")
        weekdayDates.forEachIndexed { idx, d ->
            // Morning Qahwa (6 matches, mode amount = 12 DH, one outlier = 15 DH)
            richHistory += TransactionItem(
                id = "wd_m_q_$idx",
                type = "expense",
                amount = if (idx == 0) Money.fromDh(15) else Money.fromDh(12),
                desc = "Qahwa",
                category = "Café",
                envelopeKey = "cafe",
                isoDate = d,
                hourOfDay = 8
            )
            // Morning Taxi (6 matches, mode amount = 20 DH)
            richHistory += TransactionItem(
                id = "wd_m_t_$idx",
                type = "expense",
                amount = Money.fromDh(20),
                desc = "Taxi",
                category = "Transport",
                envelopeKey = "transport",
                isoDate = d,
                hourOfDay = 9
            )
        }
        // Only 2 matches on weekday morning -> should NOT become a chip (since minMatchesForChip = 4)
        richHistory += TransactionItem(
            id = "wd_rare_1",
            type = "expense",
            amount = Money.fromDh(40),
            desc = "Rare Snack",
            category = "Café",
            envelopeKey = "cafe",
            isoDate = "2026-05-07",
            hourOfDay = 8
        )
        richHistory += TransactionItem(
            id = "wd_rare_2",
            type = "expense",
            amount = Money.fromDh(40),
            desc = "Rare Snack",
            category = "Café",
            envelopeKey = "cafe",
            isoDate = "2026-05-08",
            hourOfDay = 8
        )

        // Weekend mornings: 4 weekend dates
        val weekendDates = listOf("2026-04-25", "2026-04-26", "2026-05-02", "2026-05-03")
        weekendDates.forEachIndexed { idx, d ->
            richHistory += TransactionItem(
                id = "we_m_b_$idx",
                type = "expense",
                amount = Money.fromDh(35),
                desc = "Ftôr Beldi",
                category = "Café",
                envelopeKey = "cafe",
                isoDate = d,
                hourOfDay = 9
            )
            richHistory += TransactionItem(
                id = "we_tie_old_$idx",
                type = "expense",
                amount = Money.fromDh(80),
                desc = "Marjane Weekend",
                category = "Marché",
                envelopeKey = "marche",
                isoDate = d,
                hourOfDay = 10
            )
        }
        // Newer weekend morning item with 4 matches in the same time block -> wins tie-breaker against "Marjane Weekend"
        val newerWeekendDates = listOf("2026-04-26", "2026-05-02", "2026-05-09", "2026-05-10")
        newerWeekendDates.forEachIndexed { idx, d ->
            richHistory += TransactionItem(
                id = "we_tie_new_$idx",
                type = "expense",
                amount = Money.fromDh(50),
                desc = "Croissant",
                category = "Café",
                envelopeKey = "cafe",
                isoDate = d,
                hourOfDay = 10
            )
        }

        assertTrue(richHistory.size >= 15)
        val richState = DashboardState(envelopes = envStatuses, allTransactions = richHistory)

        // Query on Friday 2026-05-15 at 8:00 AM (weekday morning)
        val weekdayMorningShortcuts = com.example.ui.computeTimeAwareShortcuts(
            state = richState,
            nowHour = 8,
            isWeekend = false,
            nowIso = "2026-05-15"
        )
        assertEquals(2, weekdayMorningShortcuts.size) // "Rare Snack" (2 matches < 4) excluded
        assertEquals("Qahwa", weekdayMorningShortcuts[0].desc)
        assertEquals(Money.fromDh(12), weekdayMorningShortcuts[0].amount) // Mode amount (12 DH x5 vs 15 DH x1)
        assertEquals("Taxi", weekdayMorningShortcuts[1].desc)
        assertEquals(Money.fromDh(20), weekdayMorningShortcuts[1].amount)

        // Query on Saturday 2026-05-16 at 9:00 AM (weekend morning)
        val weekendMorningShortcuts = com.example.ui.computeTimeAwareShortcuts(
            state = richState,
            nowHour = 9,
            isWeekend = true,
            nowIso = "2026-05-16"
        )
        assertEquals(3, weekendMorningShortcuts.size)
        // "Croissant" has more recent dates (up to 2026-05-10) so higher recency boost than "Ftôr Beldi" / "Marjane Weekend" (up to 2026-05-03)
        assertEquals("Croissant", weekendMorningShortcuts[0].desc)
        assertEquals(Money.fromDh(50), weekendMorningShortcuts[0].amount)

        // 3. Prompt 7: Editing a pre-filled shortcut amount 3 times updates the suggested amount locally
        assertNull(
            com.example.notifications.MasroufiNotificationManager.recordShortcutAmountEdit(
                context = context,
                desc = "Qahwa",
                suggestedAmount = Money.fromDh(12),
                savedAmount = Money.fromDh(14)
            )
        )
        assertNull(
            com.example.notifications.MasroufiNotificationManager.recordShortcutAmountEdit(
                context = context,
                desc = "Qahwa",
                suggestedAmount = Money.fromDh(12),
                savedAmount = Money.fromDh(14)
            )
        )
        val thirdEditLearned = com.example.notifications.MasroufiNotificationManager.recordShortcutAmountEdit(
            context = context,
            desc = "Qahwa",
            suggestedAmount = Money.fromDh(12),
            savedAmount = Money.fromDh(14)
        )
        assertEquals(Money.fromDh(14), thirdEditLearned)

        val overrides = com.example.notifications.MasroufiNotificationManager.getShortcutAmountOverrides(context)
        val updatedShortcuts = com.example.ui.computeTimeAwareShortcuts(
            state = richState,
            nowHour = 8,
            isWeekend = false,
            nowIso = "2026-05-15",
            editedAmountOverrides = overrides
        )
        assertEquals(Money.fromDh(14), updatedShortcuts.first { it.desc == "Qahwa" }.amount)

        // 4. Reset learning clears learned categories and shortcut amount overrides
        PayCycleEngine.learnCategoryForDescription("Qahwa", "cafe")
        com.example.notifications.MasroufiNotificationManager.resetAllSmartLearning(context)
        assertTrue(com.example.notifications.MasroufiNotificationManager.getShortcutAmountOverrides(context).isEmpty())
        assertNull(PayCycleEngine.getLearnedCategoryForDescription("Qahwa"))
    }

    @Test
    fun `BackgroundWallpaperManager persists Moroccan presets, custom gallery photos, dim levels, and 4-language strings accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mgr = com.example.ui.theme.BackgroundWallpaperManager
        mgr.clearCustomPhoto(context)
        mgr.setWallpaperMode(context, "none")

        assertFalse(mgr.stateFlow.value.isActive)
        assertEquals("none", mgr.stateFlow.value.mode)

        // 1. Built-in Moroccan wallpaper presets
        mgr.setWallpaperMode(context, "zellige")
        assertTrue(mgr.stateFlow.value.isActive)
        assertEquals("zellige", mgr.stateFlow.value.mode)

        mgr.setWallpaperMode(context, "atlas")
        assertTrue(mgr.stateFlow.value.isActive)
        assertEquals("atlas", mgr.stateFlow.value.mode)

        mgr.setWallpaperMode(context, "sahara")
        assertTrue(mgr.stateFlow.value.isActive)
        assertEquals("sahara", mgr.stateFlow.value.mode)

        // 2. Dimming level persistence
        mgr.setDimLevel(context, "light")
        assertEquals("light", mgr.stateFlow.value.dimLevel)
        assertTrue(mgr.stateFlow.value.resolveDimAlpha(isDarkTheme = true) < mgr.stateFlow.value.copy(dimLevel = "strong").resolveDimAlpha(isDarkTheme = true))

        // 3. Custom photo bitmap persistence & reload from disk
        val sampleBmp = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        sampleBmp.eraseColor(android.graphics.Color.rgb(16, 185, 129))
        assertTrue(mgr.saveCustomPhotoBitmap(context, sampleBmp))
        assertEquals("custom", mgr.stateFlow.value.mode)
        assertTrue(mgr.stateFlow.value.hasSavedCustomPhoto)
        assertNotNull(mgr.stateFlow.value.customBitmap)
        assertTrue(mgr.customPhotoFile(context).exists())

        mgr.reloadFromDisk(context)
        assertEquals("custom", mgr.stateFlow.value.mode)
        assertTrue(mgr.stateFlow.value.hasSavedCustomPhoto)
        assertNotNull(mgr.stateFlow.value.customBitmap)

        // 4. Clearing custom photo deletes local file and reverts mode to "none"
        mgr.clearCustomPhoto(context)
        assertFalse(mgr.stateFlow.value.hasSavedCustomPhoto)
        assertNull(mgr.stateFlow.value.customBitmap)
        assertEquals("none", mgr.stateFlow.value.mode)
        assertFalse(mgr.customPhotoFile(context).exists())

        // 5. 4-language parity for background image strings
        listOf("fr", "en", "ar", "darija").forEach { lang ->
            val s = com.example.domain.Translations.forLang(lang)
            assertTrue(s.bgImageSectionLabel.isNotBlank())
            assertTrue(s.bgPickPhotoBtn.isNotBlank())
            assertTrue(s.bgRemovePhotoBtn.isNotBlank())
            assertTrue(s.bgPresetNone.isNotBlank())
            assertTrue(s.bgPresetZellige.isNotBlank())
            assertTrue(s.bgPresetAtlas.isNotBlank())
            assertTrue(s.bgPresetSahara.isNotBlank())
        }
    }

    @Test
    fun `UI UX Upgrade Phase 1 and Phase 2 typography, tokens, category visual tints, hero contextual status, and keypad calculator work accurately`() {
        // 1. Prompt 1: Language-aware typography (PlusJakartaSans for FR/EN, ReadexProArabic for AR/Darija) & >=12sp minimum sizes
        val typoFr = com.example.ui.theme.typographyForLanguage("fr")
        val typoEn = com.example.ui.theme.typographyForLanguage("en")
        val typoAr = com.example.ui.theme.typographyForLanguage("ar")
        val typoDarija = com.example.ui.theme.typographyForLanguage("darija")

        assertEquals(com.example.ui.theme.PlusJakartaSans, typoFr.bodyLarge.fontFamily)
        assertEquals(com.example.ui.theme.PlusJakartaSans, typoEn.bodyLarge.fontFamily)
        assertEquals(com.example.ui.theme.ReadexProArabic, typoAr.bodyLarge.fontFamily)
        assertEquals(com.example.ui.theme.ReadexProArabic, typoDarija.bodyLarge.fontFamily)

        // Minimum 12sp across all 15 slots, and 13sp for Arabic bodySmall
        val allFrStyles = listOf(
            typoFr.displayLarge, typoFr.displayMedium, typoFr.displaySmall,
            typoFr.headlineLarge, typoFr.headlineMedium, typoFr.headlineSmall,
            typoFr.titleLarge, typoFr.titleMedium, typoFr.titleSmall,
            typoFr.bodyLarge, typoFr.bodyMedium, typoFr.bodySmall,
            typoFr.labelLarge, typoFr.labelMedium, typoFr.labelSmall
        )
        allFrStyles.forEach { style ->
            assertTrue("Font size ${style.fontSize.value}sp must be >= 12sp", style.fontSize.value >= 12f)
        }
        assertTrue(typoAr.bodySmall.fontSize.value >= 13f)
        assertTrue(typoDarija.bodySmall.fontSize.value >= 13f)

        // 2. Prompt 2: Design tokens (4/8/12/16/24/32dp spacing, corner radii, and 3 card tiers)
        assertEquals(4f, com.example.ui.theme.MasroufiTokens.Spacing.xs.value, 0.01f)
        assertEquals(8f, com.example.ui.theme.MasroufiTokens.Spacing.sm.value, 0.01f)
        assertEquals(12f, com.example.ui.theme.MasroufiTokens.Spacing.md.value, 0.01f)
        assertEquals(16f, com.example.ui.theme.MasroufiTokens.Spacing.lg.value, 0.01f)
        assertEquals(24f, com.example.ui.theme.MasroufiTokens.Spacing.xl.value, 0.01f)
        assertEquals(32f, com.example.ui.theme.MasroufiTokens.Spacing.xxl.value, 0.01f)
        assertEquals(3, com.example.ui.theme.CardTier.values().size)

        // 3. Prompt 3: Distinct category icons & soft tints in Light and Dark modes + neutral default
        val foodLight = com.example.ui.theme.categoryVisualStyle("Alimentation & Courses", isDark = false)
        val housingDark = com.example.ui.theme.categoryVisualStyle("Charges & Maison", isDark = true)
        val transportAr = com.example.ui.theme.categoryVisualStyle("طاكسي وتنقل", isDark = false)
        val cafeDarija = com.example.ui.theme.categoryVisualStyle("قهوة وخرجات", isDark = true)
        val unknownCustom = com.example.ui.theme.categoryVisualStyle("My Custom Hobby", isDark = false)

        assertEquals("food", foodLight.categoryKey)
        assertEquals("housing", housingDark.categoryKey)
        assertEquals("transport", transportAr.categoryKey)
        assertEquals("leisure", cafeDarija.categoryKey)
        assertEquals("default", unknownCustom.categoryKey)
        assertNotEquals(foodLight.foreground, housingDark.foreground)
        assertTrue(foodLight.container.alpha in 0.08f..0.25f)

        // 4. Prompt 5: Hero contextual status line adapts across cycle states and respects hideAmounts
        val emptyState = com.example.ui.DashboardState(
            profile = UserProfile(language = "fr"),
            cycleTransactions = emptyList()
        )
        assertTrue(com.example.ui.computeHeroContextualStatusLine(emptyState, "DH").contains("+"))

        val txSample = listOf(TransactionItem(id = "t1", type = "expense", amount = Money.fromDh(50), isoDate = "2026-05-10"))
        val overBudgetState = com.example.ui.DashboardState(
            profile = UserProfile(language = "en"),
            cycleTransactions = txSample,
            remainingBudget = Money.fromDh(-120)
        )
        assertTrue(com.example.ui.computeHeroContextualStatusLine(overBudgetState, "DH").contains("attention"))

        val midCycleMasked = com.example.ui.DashboardState(
            profile = UserProfile(language = "fr"),
            cycleTransactions = txSample,
            remainingBudget = Money.fromDh(1200),
            daysRemaining = 15,
            dailyAverageSpend = Money.fromDh(95),
            safeToSpendToday = Money.fromDh(80),
            hideAmounts = true
        )
        assertTrue(com.example.ui.computeHeroContextualStatusLine(midCycleMasked, "DH").contains("••••"))

        // 5. Prompt 7: Calculator input expression evaluation (12+8 -> 20 DH, comma/dot decimals)
        assertEquals(Money.fromDh(20), com.example.ui.evaluateKeypadExpression("12+8"))
        assertEquals(Money.fromDh(60), com.example.ui.evaluateKeypadExpression("45.5+14,5"))
        assertEquals(Money.fromDh(130), com.example.ui.evaluateKeypadExpression("100+25+5"))
        assertEquals(Money.fromDh(35), com.example.ui.evaluateKeypadExpression("35"))
    }
}

