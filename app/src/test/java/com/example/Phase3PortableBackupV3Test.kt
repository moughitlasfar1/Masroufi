package com.example

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountItem
import com.example.data.BackupFiscalSnapshot
import com.example.data.BackupV3Codec
import com.example.data.CustomizationPrefs
import com.example.data.DataSourceConfigStore
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.data.local.MasroufiDatabaseProvider
import com.example.data.local.RemoteMirror
import com.example.data.local.RoomMasroufiRepository
import com.example.domain.AccountWithBalance
import com.example.domain.EnvelopeStatus
import com.example.security.AppSecurityManager
import com.example.security.BackupEnvelopeInfo
import com.example.security.DatabaseKeyManager
import com.example.security.PortableBackupCrypto
import com.example.security.PortableCryptoResult
import com.example.ui.BackupRestoreResult
import com.example.ui.BackupSerializer
import com.example.ui.DashboardState
import com.example.ui.MasroufiViewModel
import com.masroufi.core.Money
import com.masroufi.credit.data.RoomCreditRepository
import com.masroufi.credit.data.SavedCreditEntity
import com.masroufi.credit.domain.CreditInput
import com.masroufi.credit.domain.CreditResult
import com.masroufi.credit.domain.CreditType
import com.masroufi.fiscal.data.FiscalRepository
import com.masroufi.fiscal.domain.AnnualBillItem
import com.masroufi.fiscal.domain.AnnualBillPreset
import com.masroufi.fiscal.domain.FiscalRates
import com.masroufi.fiscal.domain.NisabStandard
import com.masroufi.fiscal.domain.ZakatConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
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
 * Pure JVM cross-device portability & cryptographic integrity tests for [PortableBackupCrypto].
 *
 * Intentionally runs WITHOUT Robolectric or Android runtime to prove zero Android Keystore,
 * zero `android.util.Base64`, and zero `org.json` dependency.
 */
class Phase3PortableBackupV3PureJvmTest {

    private val validPassphrase = "Masroufi#2026!Portable"
    private val fastValidIterations = PortableBackupCrypto.MIN_PBKDF2_ITERATIONS // 210,000

    private fun extractJsonStringField(json: String, field: String): String {
        val regex = Regex("\"$field\"\\s*:\\s*\"([^\"]+)\"")
        return regex.find(json)?.groupValues?.get(1)
            ?: error("Missing string field '$field' in envelope")
    }

    private fun flipFirstByteBitInBase64(b64: String): String {
        val bytes = PortableBackupCrypto.PureBase64.decodeStrict(b64)
            ?: error("Invalid base64")
        bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
        return PortableBackupCrypto.PureBase64.encode(bytes)
    }

    @Test
    fun `pure JVM encrypt and decrypt round-trips with default 600k iterations, zero Keystore, and CharArray zeroization`() =
        runBlocking {
            val plaintext = """{"backupVersion":3,"transactions":[{"amountCentimes":1999}]}"""
            val passChars = validPassphrase.toCharArray()

            val encryptedRes = PortableBackupCrypto.encrypt(
                plaintextUtf8 = plaintext,
                passphrase = passChars,
                wipePassphrase = true
            )
            assertTrue("Encryption must succeed in pure JVM", encryptedRes is PortableCryptoResult.Success)
            assertTrue(
                "Caller passphrase CharArray must be zeroized in finally",
                passChars.all { it == '\u0000' }
            )

            val envelope = (encryptedRes as PortableCryptoResult.Success).value
            assertFalse("Ciphertext envelope must never contain plaintext", envelope.contains("1999"))
            assertTrue(envelope.contains("\"encryptionVersion\": 3"))
            assertTrue(envelope.contains("\"kdf\": \"PBKDF2WithHmacSHA256\""))
            assertTrue(envelope.contains("\"iterations\": 600000"))
            assertTrue(envelope.contains("\"cipher\": \"AES/GCM/NoPadding\""))

            val inspect = PortableBackupCrypto.inspectEnvelope(envelope)
            assertTrue(inspect is BackupEnvelopeInfo.PortableV3Encrypted)
            assertEquals(600_000, (inspect as BackupEnvelopeInfo.PortableV3Encrypted).iterations)

            val decryptChars = validPassphrase.toCharArray()
            val decryptedRes = PortableBackupCrypto.decrypt(
                envelopeJson = envelope,
                passphrase = decryptChars,
                wipePassphrase = true
            )
            assertTrue("Decryption must succeed in pure JVM", decryptedRes is PortableCryptoResult.Success)
            assertTrue(
                "Decrypt passphrase CharArray must be zeroized in finally",
                decryptChars.all { it == '\u0000' }
            )
            assertEquals(plaintext, (decryptedRes as PortableCryptoResult.Success).value)
        }

    @Test
    fun `pure JVM enforces 10-char minimum passphrase, unsupported version, and corrupted file errors`() {
        val weak = PortableBackupCrypto.encryptBlocking("{}", "123456789")
        assertEquals(PortableCryptoResult.WeakPassphrase(10), weak)

        val unsupportedJson = """{"format":"masroufi_backup","masroufiEncrypted":true,"encryptionVersion":99,"kdf":"PBKDF2WithHmacSHA256","iterations":600000,"cipher":"AES/GCM/NoPadding","salt":"AAAA","iv":"AAAA","ciphertext":"AAAA"}"""
        assertEquals(
            BackupEnvelopeInfo.UnsupportedVersion(99),
            PortableBackupCrypto.inspectEnvelope(unsupportedJson)
        )
        assertEquals(
            PortableCryptoResult.UnsupportedVersion(99),
            PortableBackupCrypto.decryptBlocking(unsupportedJson, validPassphrase)
        )

        val truncated = """{"format":"masroufi_backup","masroufiEncrypted":true"""
        assertTrue(PortableBackupCrypto.inspectEnvelope(truncated) is BackupEnvelopeInfo.Corrupted)
        assertTrue(PortableBackupCrypto.decryptBlocking(truncated, validPassphrase) is PortableCryptoResult.CorruptedFile)
    }

    @Test
    fun `pure JVM 1-bit tamper in ciphertext, IV, salt, or AAD header fails GCM authentication`() {
        val plaintext = """{"backupVersion":3,"profile":{"baseBudgetCentimes":640000}}"""
        val enc = PortableBackupCrypto.encryptBlocking(
            plaintextUtf8 = plaintext,
            passphrase = validPassphrase,
            iterations = fastValidIterations
        ) as PortableCryptoResult.Success
        val envelope = enc.value

        // 1. 1-bit tamper in ciphertext
        val origCt = extractJsonStringField(envelope, "ciphertext")
        val tamperedCt = envelope.replace(origCt, flipFirstByteBitInBase64(origCt))
        assertEquals(
            "1-bit ciphertext tamper must fail GCM auth",
            PortableCryptoResult.WrongPassphraseOrTampered,
            PortableBackupCrypto.decryptBlocking(tamperedCt, validPassphrase)
        )

        // 2. 1-bit tamper in IV
        val origIv = extractJsonStringField(envelope, "iv")
        val tamperedIv = envelope.replace(origIv, flipFirstByteBitInBase64(origIv))
        assertEquals(
            "1-bit IV tamper must fail GCM auth",
            PortableCryptoResult.WrongPassphraseOrTampered,
            PortableBackupCrypto.decryptBlocking(tamperedIv, validPassphrase)
        )

        // 3. 1-bit tamper in salt
        val origSalt = extractJsonStringField(envelope, "salt")
        val tamperedSalt = envelope.replace(origSalt, flipFirstByteBitInBase64(origSalt))
        assertEquals(
            "1-bit salt tamper must fail GCM auth",
            PortableCryptoResult.WrongPassphraseOrTampered,
            PortableBackupCrypto.decryptBlocking(tamperedSalt, validPassphrase)
        )

        // 4. 1-bit / value tamper in AAD header (`format`)
        val tamperedFormat = envelope.replace("\"masroufi_backup\"", "\"masroufi_backvp\"")
        assertEquals(
            "AAD header format tamper must fail GCM auth",
            PortableCryptoResult.WrongPassphraseOrTampered,
            PortableBackupCrypto.decryptBlocking(tamperedFormat, validPassphrase)
        )

        // 5. Tamper in AAD header (`iterations`: 210000 -> 210001)
        val tamperedIterations = envelope.replace("$fastValidIterations", "${fastValidIterations + 1}")
        assertEquals(
            "AAD header iterations tamper must fail GCM auth",
            PortableCryptoResult.WrongPassphraseOrTampered,
            PortableBackupCrypto.decryptBlocking(tamperedIterations, validPassphrase)
        )
    }

    @Test
    fun `pure JVM encrypting identical plaintext twice yields distinct salt, IV, and ciphertext`() {
        val plaintext = """{"backupVersion":3,"accounts":[{"initialBalanceCentimes":-4599}]}"""
        val enc1 = (PortableBackupCrypto.encryptBlocking(plaintext, validPassphrase, fastValidIterations) as PortableCryptoResult.Success).value
        val enc2 = (PortableBackupCrypto.encryptBlocking(plaintext, validPassphrase, fastValidIterations) as PortableCryptoResult.Success).value

        assertNotEquals("Salts must be freshly generated", extractJsonStringField(enc1, "salt"), extractJsonStringField(enc2, "salt"))
        assertNotEquals("IVs must be freshly generated", extractJsonStringField(enc1, "iv"), extractJsonStringField(enc2, "iv"))
        assertNotEquals("Ciphertexts must differ", extractJsonStringField(enc1, "ciphertext"), extractJsonStringField(enc2, "ciphertext"))

        assertEquals(plaintext, (PortableBackupCrypto.decryptBlocking(enc1, validPassphrase) as PortableCryptoResult.Success).value)
        assertEquals(plaintext, (PortableBackupCrypto.decryptBlocking(enc2, validPassphrase) as PortableCryptoResult.Success).value)
    }
}

/**
 * End-to-end Phase 3 verification tests across Room v2, [RoomMasroufiRepository],
 * [MasroufiViewModel], [BackupV3Codec], [FiscalRepository], and legacy v2 backward compatibility.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3PortableBackupV3Test {

    private lateinit var context: Context
    private val testDbName = "phase3_portable_backup_v3_test.db"
    private val testPassphrase = "Masroufi#Phase3!2026"
    private val testIterations = PortableBackupCrypto.MIN_PBKDF2_ITERATIONS

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MasroufiDatabaseProvider.resetForTesting()
        DatabaseKeyManager.deleteDatabaseArtifacts(context, testDbName)
        DataSourceConfigStore.resetForTesting(context)
        context.getSharedPreferences("masroufi_secure_kv_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        MasroufiDatabaseProvider.resetForTesting()
        DatabaseKeyManager.deleteDatabaseArtifacts(context, testDbName)
        DataSourceConfigStore.resetForTesting(context)
    }

    private fun openTestDb() = MasroufiDatabaseProvider.getOrOpen(
        context = context,
        dbName = testDbName,
        customFactory = FrameworkSQLiteOpenHelperFactory()
    )!!

    @Test
    fun `full v3 round-trip across all Room v2 tables, daretMembers, saved_credits, and FiscalRepository preserves exact Long centimes`() =
        runBlocking(Dispatchers.IO) {
            val db = openTestDb()
            val dao = db.masroufiDao()
            val uid = "uid_phase3_roundtrip"

            var mirrorRestoredCalls = 0
            var mirrorRestoredCredits = -1
            val spyMirror = object : RemoteMirror {
                override suspend fun onBackupRestored(
                    userId: String,
                    restoredTxCount: Int,
                    restoredItemCount: Int,
                    restoredAccountCount: Int,
                    restoredEnvelopeCount: Int,
                    restoredCreditCount: Int
                ) {
                    mirrorRestoredCalls++
                    mirrorRestoredCredits = restoredCreditCount
                }
            }

            val repo = RoomMasroufiRepository(
                dao = dao,
                remoteMirror = spyMirror,
                userIdProvider = { uid }
            )

            val sourceProfile = UserProfile(
                id = uid,
                userId = uid,
                baseBudget = Money(640_050L),
                payCycleDay = 5,
                rolloverEnabled = true,
                language = "fr",
                isDarkTheme = true,
                seeded = true
            )
            val sourceAccounts = listOf(
                AccountItem(
                    id = "acc_cih",
                    userId = uid,
                    name = "CIH Bank",
                    kind = "bank",
                    initialBalance = Money(-4599L), // Exact negative centimes (-45.99 DH)
                    colorHex = "#2563EB"
                ),
                AccountItem(
                    id = "acc_cash",
                    userId = uid,
                    name = "Espèces",
                    kind = "cash",
                    initialBalance = Money(125050L), // 1250.50 DH
                    colorHex = "#10B981"
                )
            )
            val sourceEnvelopes = listOf(
                EnvelopeConfig(
                    id = "env_alim",
                    userId = uid,
                    key = "alim",
                    nameFr = "Alimentation",
                    nameAr = "تغذية",
                    nameDarija = "ماكلة",
                    nameEn = "Food",
                    percentage = 35.0,
                    hasCap = true,
                    colorHex = "#10B981"
                )
            )
            val sourceDaret = FinancialItem(
                id = "daret_famille",
                userId = uid,
                itemType = "daret",
                name = "Daret Famille",
                subtitle = "Tour 4",
                amount = Money(100_000L), // 1000.00 DH
                secondaryAmount = Money.ZERO,
                daretMembers = 10,
                dayOrTurn = 4,
                extraInt = 2,
                dateOrCycle = "2026-11"
            )
            val sourceGoal = FinancialItem(
                id = "goal_voiture",
                userId = uid,
                itemType = "goal",
                name = "Fonds Voiture",
                subtitle = "Épargne",
                amount = Money(125050L),
                secondaryAmount = Money(1999L),
                dayOrTurn = 1,
                extraInt = 0,
                dateOrCycle = "2027-06-01"
            )
            val sourceTxs = listOf(
                TransactionItem(
                    id = "tx_marjane",
                    userId = uid,
                    type = "expense",
                    amount = Money(1999L), // 19.99 DH exact centimes
                    category = "alim",
                    envelopeKey = "alim",
                    accountId = "acc_cih",
                    desc = "Marjane Market",
                    isoDate = "2026-11-20",
                    hourOfDay = 14
                ),
                TransactionItem(
                    id = "tx_salaire",
                    userId = uid,
                    type = "income",
                    amount = Money(125050L), // 1250.50 DH exact centimes
                    category = "revenu",
                    envelopeKey = "autre",
                    accountId = "acc_cih",
                    desc = "Prime projet",
                    isoDate = "2026-11-21",
                    hourOfDay = 9
                )
            )
            val sourceCredit = SavedCreditEntity(
                id = "credit_auto_01",
                userId = uid,
                type = CreditType.AUTO.name,
                loanAmount = Money(15_000_000L), // 150,000.00 DH
                downPayment = Money(3_000_000L),
                financedPrincipal = Money(12_000_000L),
                durationMonths = 60,
                deferredMonths = 2,
                annualInterestRateHt = 6.25,
                applyInterestTva = true,
                insuranceRate = 0.45,
                monthlyPayment = Money(245_075L), // 2,450.75 DH
                totalCost = Money(2_704_500L),
                debtToIncomeRatio = 28.4,
                maxAllowedDti = 45.0,
                linkToMonthlyExpenses = true,
                createdAtEpochMs = 1732650000000L
            )
            val sourceFiscal = BackupFiscalSnapshot(
                annualBills = listOf(
                    AnnualBillItem(
                        id = "bill_vignette_2026",
                        preset = AnnualBillPreset.VIGNETTE,
                        title = "Vignette Voiture",
                        annualAmountMad = Money(150_050L), // 1,500.50 DH
                        dueMonth = 1,
                        dueYear = 2027,
                        cvBand = FiscalRates.HorsepowerBand.CV_8_TO_10,
                        fuelType = FiscalRates.FuelType.DIESEL,
                        isManualVignetteAmount = false,
                        linkedGoalId = "goal_vignette",
                        linkedBillId = "bill_vignette",
                        isPaid = false,
                        paidIsoDate = "",
                        createdAtMillis = 1732600000000L
                    )
                ),
                zakatConfig = ZakatConfig(
                    nisabStandard = NisabStandard.GOLD_85G,
                    goldPriceMadPerGram = Money(82_550L), // 825.50 DH/g
                    silverPriceMadPerGram = Money(1_125L), // 11.25 DH/g
                    pricesAsOfIso = "2026-11-20",
                    hawlStartIso = "2026-01-15",
                    linkedAnnualBillId = "bill_zakat"
                )
            )

            val sourceState = DashboardState(
                profile = sourceProfile,
                customPrefs = CustomizationPrefs(accentPalette = "emerald", currencySymbol = "MAD"),
                accounts = sourceAccounts.map {
                    AccountWithBalance(it, it.initialBalance, Money.ZERO, Money.ZERO)
                },
                envelopes = sourceEnvelopes.map {
                    EnvelopeStatus(
                        config = it,
                        baseAllocated = Money.ZERO,
                        rolloverAmount = Money.ZERO,
                        totalAvailable = Money.ZERO,
                        spent = Money.ZERO,
                        previousSpent = Money.ZERO,
                        remaining = Money.ZERO,
                        usagePercent = 0,
                        isOver90Percent = false
                    )
                },
                goals = listOf(sourceGoal),
                darets = listOf(sourceDaret),
                allTransactions = sourceTxs
            )

            // 1. Encode Backup v3 JSON & assert exact Long centimes keys (zero Double decimal money)
            val plainV3 = BackupV3Codec.encodeV3Backup(
                state = sourceState,
                savedCredits = listOf(sourceCredit),
                fiscal = sourceFiscal,
                appVersion = "1.2.1",
                exportedAtEpochMillis = 1732657000000L,
                exportedAtIso = "2026-11-26"
            )
            val rootObj = JSONObject(plainV3)
            assertEquals(3, rootObj.getInt("backupVersion"))
            assertEquals(1999L, rootObj.getJSONArray("transactions").getJSONObject(0).getLong("amountCentimes"))
            assertEquals(-4599L, rootObj.getJSONArray("accounts").getJSONObject(0).getLong("initialBalanceCentimes"))
            assertEquals(245_075L, rootObj.getJSONArray("savedCredits").getJSONObject(0).getLong("monthlyPaymentCentimes"))
            assertEquals(10, rootObj.getJSONArray("financialItems").getJSONObject(1).getInt("daretMembers"))
            assertEquals(150_050L, rootObj.getJSONObject("fiscal").getJSONArray("annualBills").getJSONObject(0).getLong("annualAmountCentimes"))
            assertEquals(82_550L, rootObj.getJSONObject("fiscal").getJSONObject("zakatConfig").getLong("goldPricePerGramCentimes"))

            // 2. Encrypt with PortableBackupCrypto (zero Keystore)
            val encRes = PortableBackupCrypto.encrypt(
                plaintextUtf8 = plainV3,
                passphrase = testPassphrase,
                iterations = testIterations
            )
            assertTrue(encRes is PortableCryptoResult.Success)
            val encryptedFile = (encRes as PortableCryptoResult.Success).value

            // 3. Seed an existing stale credit & tx in Room to confirm atomic replacement
            val creditRepo = RoomCreditRepository(dao = dao, userIdProvider = { uid })
            val staleCreditId = creditRepo.saveCredit(
                input = CreditInput(
                    creditType = CreditType.CONSOMMATION,
                    loanAmount = Money(5_000_000L),
                    downPayment = Money.ZERO,
                    durationMonths = 24,
                    deferredMonths = 0,
                    annualInterestRateHt = 8.0,
                    applyInterestTva = true,
                    insuranceRate = 0.0
                ),
                result = CreditResult(
                    financedPrincipal = Money(5_000_000L),
                    monthlyPayment = Money(230_000L),
                    deferredMonthlyPayment = Money.ZERO,
                    totalInterestHt = Money(472_727L),
                    totalTva = Money(47_273L),
                    totalInsurance = Money.ZERO,
                    totalCost = Money(520_000L),
                    totalRepayment = Money(5_520_000L),
                    debtToIncomeRatio = 10.0,
                    maxAllowedDti = 45.0,
                    isOverIndebted = false,
                    ageAtLastPayment = 32.0,
                    isAgeLimitExceeded = false,
                    requiresMandatoryAdi = false,
                    warnings = emptyList(),
                    amortizationSchedule = emptyList()
                ),
                linkToMonthlyExpenses = false
            ).getOrThrow()
            assertEquals(1, dao.getSavedCredits(uid).size)

            // 4. Restore into Room via MasroufiViewModel.restoreBackupPortable
            val vm = MasroufiViewModel(repository = repo, currentUserId = uid)
            val outcome = vm.restoreBackupPortable(
                rawJson = encryptedFile,
                passphrase = testPassphrase.toCharArray(),
                replaceExisting = true,
                wipePassphrase = true
            )
            assertTrue("Expected Success restore, got $outcome", outcome is BackupRestoreResult.Success)
            val success = outcome as BackupRestoreResult.Success
            assertEquals(3, success.backupVersion)
            assertEquals(2, success.restoredTransactionsCount)
            assertEquals(1, success.restoredCreditsCount)

            // 5. Verify EVERY Room v2 table has exact Long centimes & stale credit was replaced atomically
            val restoredProfile = dao.getProfile(uid)
            assertNotNull(restoredProfile)
            assertEquals(640_050L, restoredProfile!!.baseBudgetCentimes)
            assertEquals(5, restoredProfile.payCycleDay)

            val restoredAccs = dao.getAccounts(uid).associateBy { it.id }
            assertEquals(-4599L, restoredAccs["acc_cih"]?.initialBalanceCentimes)
            assertEquals(125050L, restoredAccs["acc_cash"]?.initialBalanceCentimes)

            val restoredEnvs = dao.getEnvelopes(uid)
            assertEquals(1, restoredEnvs.size)
            assertEquals("alim", restoredEnvs[0].key)

            val restoredItems = dao.getFinancialItems(uid).associateBy { it.id }
            assertEquals(10, restoredItems["daret_famille"]?.daretMembers)
            assertEquals(100_000L, restoredItems["daret_famille"]?.amountCentimes)
            assertEquals(125050L, restoredItems["goal_voiture"]?.amountCentimes)
            assertEquals(1999L, restoredItems["goal_voiture"]?.secondaryAmountCentimes)

            val restoredTxs = dao.getTransactions(uid).associateBy { it.id }
            assertEquals(1999L, restoredTxs["tx_marjane"]?.amountCentimes)
            assertEquals(125050L, restoredTxs["tx_salaire"]?.amountCentimes)

            val restoredCredits = dao.getSavedCredits(uid)
            assertEquals("Stale credit must be replaced by backed-up credit", 1, restoredCredits.size)
            assertNotEquals(staleCreditId, restoredCredits[0].id)
            assertEquals("credit_auto_01", restoredCredits[0].id)
            assertEquals(15_000_000L, restoredCredits[0].loanAmountCentimes)
            assertEquals(245_075L, restoredCredits[0].monthlyPaymentCentimes)
            assertEquals(2_704_500L, restoredCredits[0].totalCostCentimes)

            // 6. Verify FiscalRepository payload round-tripped with exact centimes
            val restoredFiscal = success.restoredFiscal
            assertNotNull(restoredFiscal)
            assertEquals(1, restoredFiscal!!.annualBills.size)
            assertEquals(Money(150_050L), restoredFiscal.annualBills[0].annualAmountMad)
            assertEquals(Money(82_550L), restoredFiscal.zakatConfig?.goldPriceMadPerGram)
            assertEquals(Money(1_125L), restoredFiscal.zakatConfig?.silverPriceMadPerGram)

            // Persist & reload through FiscalRepository to prove storage parity
            FiscalRepository.saveAnnualBills(context, restoredFiscal.annualBills)
            FiscalRepository.saveZakatConfig(context, restoredFiscal.zakatConfig!!)
            assertEquals(Money(150_050L), FiscalRepository.loadAnnualBills(context)[0].annualAmountMad)
            assertEquals(Money(82_550L), FiscalRepository.loadZakatConfig(context).goldPriceMadPerGram)

            // 7. Verify RemoteMirror.onBackupRestored fired strictly post-commit
            assertEquals(1, mirrorRestoredCalls)
            assertEquals(1, mirrorRestoredCredits)
        }

    @Test
    fun `wrong passphrase fails with typed auth error and leaves existing Room DB 100 percent untouched`() =
        runBlocking(Dispatchers.IO) {
            val db = openTestDb()
            val dao = db.masroufiDao()
            val uid = "uid_wrong_pass_guard"

            var mirrorCalls = 0
            val repo = RoomMasroufiRepository(
                dao = dao,
                remoteMirror = object : RemoteMirror {
                    override suspend fun onBackupRestored(
                        userId: String,
                        restoredTxCount: Int,
                        restoredItemCount: Int,
                        restoredAccountCount: Int,
                        restoredEnvelopeCount: Int,
                        restoredCreditCount: Int
                    ) {
                        mirrorCalls++
                    }
                },
                userIdProvider = { uid }
            )

            // Seed existing live user data
            repo.saveProfile(UserProfile(id = uid, userId = uid, baseBudget = Money(888_800L)), uid)
            repo.upsertTransaction(
                TransactionItem(
                    id = "tx_existing_keep",
                    userId = uid,
                    type = "expense",
                    amount = Money(4250L),
                    category = "alim",
                    envelopeKey = "alim",
                    accountId = "acc_cash",
                    desc = "Existing Data",
                    isoDate = "2026-11-22"
                ),
                uid
            )

            // Create a valid encrypted v3 backup for a different dataset
            val otherState = DashboardState(
                profile = UserProfile(id = uid, userId = uid, baseBudget = Money(100_000L))
            )
            val plainV3 = BackupV3Codec.encodeV3Backup(otherState)
            val encrypted = (PortableBackupCrypto.encrypt(plainV3, testPassphrase, testIterations) as PortableCryptoResult.Success).value

            val vm = MasroufiViewModel(repository = repo, currentUserId = uid)
            val outcome = vm.restoreBackupPortable(
                rawJson = encrypted,
                passphrase = "WrongPassphrase#9999".toCharArray(),
                replaceExisting = true
            )

            assertTrue("Must return WrongPassphraseOrTampered", outcome is BackupRestoreResult.WrongPassphraseOrTampered)
            assertEquals("RemoteMirror must not fire on failed restore", 0, mirrorCalls)
            assertEquals("Existing profile must be 100% untouched", 888_800L, dao.getProfile(uid)?.baseBudgetCentimes)
            val txs = dao.getTransactions(uid)
            assertEquals("Existing transactions must be 100% untouched", 1, txs.size)
            assertEquals("tx_existing_keep", txs[0].id)
            assertEquals(4250L, txs[0].amountCentimes)
        }

    @Test
    fun `legacy v2 backup plaintext and encrypted restores DH decimals and Daret secondaryAmount cleanly`() =
        runBlocking(Dispatchers.IO) {
            val db = openTestDb()
            val dao = db.masroufiDao()
            val uid = "uid_legacy_v2_compat"
            val repo = RoomMasroufiRepository(dao = dao, userIdProvider = { uid })

            val legacyV2Json = """
                {
                  "backupVersion": 2,
                  "exportedAtIso": "2026-10-15",
                  "profile": {
                    "baseBudget": 6400.00,
                    "payCycleDay": 1,
                    "rolloverEnabled": true,
                    "language": "fr",
                    "isDarkTheme": false
                  },
                  "accounts": [
                    {
                      "id": "acc_legacy_bank",
                      "name": "BCP",
                      "kind": "bank",
                      "initialBalance": -45.99,
                      "colorHex": "#2563EB"
                    }
                  ],
                  "envelopes": [
                    {
                      "id": "env_alim",
                      "key": "alim",
                      "nameFr": "Alimentation",
                      "percentage": 40.0,
                      "hasCap": true,
                      "colorHex": "#10B981"
                    }
                  ],
                  "financialItems": [
                    {
                      "id": "daret_legacy_1",
                      "itemType": "daret",
                      "name": "Daret Bureau",
                      "subtitle": "10 membres",
                      "amount": 500.00,
                      "secondaryAmount": 10,
                      "dayOrTurn": 3,
                      "extraInt": 1,
                      "dateOrCycle": "2026-10"
                    }
                  ],
                  "transactions": [
                    {
                      "id": "tx_v2_1",
                      "type": "expense",
                      "amount": 19.99,
                      "category": "alim",
                      "envelopeKey": "alim",
                      "accountId": "acc_legacy_bank",
                      "toAccountId": "",
                      "desc": "Café Crème",
                      "iso": "2026-10-14"
                    },
                    {
                      "id": "tx_v2_2",
                      "type": "income",
                      "amount": 1250.50,
                      "category": "revenu",
                      "envelopeKey": "autre",
                      "accountId": "acc_legacy_bank",
                      "toAccountId": "",
                      "desc": "Virement client",
                      "iso": "2026-10-15"
                    }
                  ]
                }
            """.trimIndent()

            // Verify legacy v2 encrypted envelope (from AppSecurityManager) is auto-detected & restored cleanly
            val legacyV2Encrypted = AppSecurityManager.encryptBackupJson(
                plainJson = legacyV2Json,
                password = testPassphrase,
                iterations = 10_000
            )
            assertTrue(PortableBackupCrypto.inspectEnvelope(legacyV2Encrypted) is BackupEnvelopeInfo.LegacyV2Encrypted)

            val vm = MasroufiViewModel(repository = repo, currentUserId = uid)
            val outcome = vm.restoreBackupPortable(
                rawJson = legacyV2Encrypted,
                passphrase = testPassphrase.toCharArray(),
                replaceExisting = true
            )
            assertTrue("Legacy v2 encrypted restore must succeed, got $outcome", outcome is BackupRestoreResult.Success)
            assertEquals(2, (outcome as BackupRestoreResult.Success).backupVersion)

            val restoredAcc = dao.getAccounts(uid).single()
            assertEquals(-4599L, restoredAcc.initialBalanceCentimes)

            val restoredDaret = dao.getFinancialItems(uid).first { it.id == "daret_legacy_1" }
            assertEquals("Legacy Daret secondaryAmount=10 must map to daretMembers=10", 10, restoredDaret.daretMembers)
            assertEquals(50_000L, restoredDaret.amountCentimes)
            assertEquals(0L, restoredDaret.secondaryAmountCentimes)

            val restoredTxs = dao.getTransactions(uid).associateBy { it.id }
            assertEquals(1999L, restoredTxs["tx_v2_1"]?.amountCentimes)
            assertEquals(125050L, restoredTxs["tx_v2_2"]?.amountCentimes)

            // Also verify legacy plaintext v2 JSON restores directly without a passphrase
            assertEquals(BackupEnvelopeInfo.LegacyPlaintextJson, PortableBackupCrypto.inspectEnvelope(legacyV2Json))
            val plainOutcome = vm.restoreBackupPortable(
                rawJson = legacyV2Json,
                passphrase = CharArray(0),
                replaceExisting = true
            )
            assertTrue("Legacy plaintext v2 restore must succeed", plainOutcome is BackupRestoreResult.Success)
        }

    @Test
    fun `corrupted decrypted payload or tampered metadata recordCounts aborts cleanly without touching Room`() =
        runBlocking(Dispatchers.IO) {
            val db = openTestDb()
            val dao = db.masroufiDao()
            val uid = "uid_atomic_corrupt_guard"
            val repo = RoomMasroufiRepository(dao = dao, userIdProvider = { uid })

            // Seed existing data
            repo.saveProfile(UserProfile(id = uid, userId = uid, baseBudget = Money(777_700L)), uid)
            repo.upsertTransaction(
                TransactionItem(
                    id = "tx_keep_safe",
                    userId = uid,
                    type = "expense",
                    amount = Money(9999L),
                    category = "alim",
                    envelopeKey = "alim",
                    accountId = "acc_cash",
                    desc = "Keep Safe",
                    isoDate = "2026-11-23"
                ),
                uid
            )

            val vm = MasroufiViewModel(repository = repo, currentUserId = uid)

            // Case 1: Encrypted valid envelope containing malformed JSON payload
            val encryptedMalformed = (PortableBackupCrypto.encrypt(
                plaintextUtf8 = """{"backupVersion": 3, "profile": { BROKEN JSON""",
                passphrase = testPassphrase,
                iterations = testIterations
            ) as PortableCryptoResult.Success).value

            val res1 = vm.restoreBackupPortable(
                rawJson = encryptedMalformed,
                passphrase = testPassphrase.toCharArray(),
                replaceExisting = true
            )
            assertTrue("Malformed decrypted JSON must return CorruptedOrInvalid", res1 is BackupRestoreResult.CorruptedOrInvalid)
            assertEquals(777_700L, dao.getProfile(uid)?.baseBudgetCentimes)
            assertEquals(1, dao.getTransactions(uid).size)
            assertEquals("tx_keep_safe", dao.getTransactions(uid)[0].id)

            // Case 2: Encrypted v3 payload with tampered metadata.recordCounts mismatch
            val validState = DashboardState(profile = UserProfile(id = uid, userId = uid, baseBudget = Money(300_000L)))
            val validV3 = BackupV3Codec.encodeV3Backup(validState)
            val mismatchedV3 = validV3.replace("\"transactions\": 0", "\"transactions\": 5")
            val encryptedMismatch = (PortableBackupCrypto.encrypt(
                plaintextUtf8 = mismatchedV3,
                passphrase = testPassphrase,
                iterations = testIterations
            ) as PortableCryptoResult.Success).value

            val res2 = vm.restoreBackupPortable(
                rawJson = encryptedMismatch,
                passphrase = testPassphrase.toCharArray(),
                replaceExisting = true
            )
            assertTrue("Mismatched metadata.recordCounts must fail validation before Room commit", res2 is BackupRestoreResult.CorruptedOrInvalid)
            assertEquals(777_700L, dao.getProfile(uid)?.baseBudgetCentimes)
            assertEquals(1, dao.getTransactions(uid).size)
            assertEquals(9999L, dao.getTransactions(uid)[0].amountCentimes)
        }
}
