package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.MasroufiDatabase
import com.example.data.local.MasroufiEntityMappers.toDomain
import com.example.domain.LoanCategory
import com.example.domain.LoanDirection
import com.example.domain.LoanStatus
import com.example.domain.NetWorthCalculator
import com.masroufi.core.Money
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 5 — Room v2 -> v3 Migration Verification Suite.
 *
 * Proves:
 * 1. Exported `1.json` and `2.json` remain completely untouched with original identity hashes,
 *    and exported `3.json` exists with version 3 and the new `loans` & `loan_repayments` tables.
 * 2. `MIGRATION_2_3` passes Room's strict [MigrationTestHelper.runMigrationsAndValidate] schema,
 *    nullability, primary key, and index verification against `3.json`.
 * 3. Blank `transactions.accountId` rows are backfilled cleanly, and users without accounts receive
 *    a seeded default Cash account without cross-user primary-key collisions.
 * 4. Existing `financial_items` (`itemType = 'debt'`) are losslessly backfilled into `loans`
 *    with exact `Long` centimes, correct direction (`LIABILITY`/`RECEIVABLE`), `ACTIVE`/`PAID_OFF`
 *    status, and `sourceFinancialItemId` linkage so [NetWorthCalculator] never double-counts them.
 * 5. Full chained upgrade `v1 -> v2 -> v3` works end-to-end without data loss.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase5RoomMigrationV2ToV3Test {

    companion object {
        private const val TEST_DB_V2_TO_V3 = "masroufi_phase5_migration_v2_to_v3.db"
        private const val TEST_DB_V1_TO_V3 = "masroufi_phase5_migration_v1_to_v3.db"
        private const val EXPECTED_V1_IDENTITY_HASH = "12cd7d38ab31023e13e7ae593835755f"
        private const val EXPECTED_V2_IDENTITY_HASH = "46d6a2ffc551b71dbbba24bc06d32273"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MasroufiDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun `schema assets 1_json 2_json and 3_json exist and 1_json and 2_json hashes are untouched`() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val v1Json = assets.open("com.example.data.local.MasroufiDatabase/1.json").bufferedReader().use { it.readText() }
        val v2Json = assets.open("com.example.data.local.MasroufiDatabase/2.json").bufferedReader().use { it.readText() }
        val v3Json = assets.open("com.example.data.local.MasroufiDatabase/3.json").bufferedReader().use { it.readText() }

        val v1Db = JSONObject(v1Json).getJSONObject("database")
        val v2Db = JSONObject(v2Json).getJSONObject("database")
        val v3Db = JSONObject(v3Json).getJSONObject("database")

        assertEquals(1, v1Db.getInt("version"))
        assertEquals(EXPECTED_V1_IDENTITY_HASH, v1Db.getString("identityHash"))
        assertEquals(2, v2Db.getInt("version"))
        assertEquals(EXPECTED_V2_IDENTITY_HASH, v2Db.getString("identityHash"))
        assertEquals(3, v3Db.getInt("version"))
        assertTrue(v3Db.getString("identityHash").isNotBlank())

        val v3Entities = v3Db.getJSONArray("entities")
        val tableNames = (0 until v3Entities.length()).map {
            v3Entities.getJSONObject(it).getString("tableName")
        }.toSet()
        assertTrue("Expected loans table in 3.json", "loans" in tableNames)
        assertTrue("Expected loan_repayments table in 3.json", "loan_repayments" in tableNames)
    }

    @Test
    fun `migrate v2 to v3 seeds default account backfills blank accountIds and migrates debts without double counting`() {
        helper.createDatabase(TEST_DB_V2_TO_V3, 2).use { db ->
            // Two users: u_has_acc already has accounts; u_no_acc has ZERO accounts and blank accountId tx;
            // u_no_acc_2 also has ZERO accounts (verifies collision-safe default account seeding).
            db.execSQL(
                """
                INSERT INTO user_profiles (
                    userId, baseBudgetCentimes, payCycleDay, rolloverEnabled, language, isDarkTheme,
                    seeded, createdAtEpochMs, updatedAtEpochMs
                ) VALUES
                    ('u_has_acc', 640000, 1, 1, 'fr', 0, 1, 1000, 1000),
                    ('u_no_acc',  500000, 5, 1, 'darija', 0, 1, 1001, 1001),
                    ('u_no_acc_2', 450000, 1, 0, 'en', 1, 1, 1002, 1002)
                """.trimIndent()
            )

            // u_has_acc owns acc_bank_1 (does NOT hold 'acc_cash' yet)
            db.execSQL(
                """
                INSERT INTO accounts (
                    id, userId, name, kind, currency, initialBalanceCentimes, colorHex,
                    isArchived, sortOrder, createdAtEpochMs, updatedAtEpochMs
                ) VALUES
                    ('acc_bank_1', 'u_has_acc', 'CIH Bank', 'bank', 'MAD', 1000000, '#38BDF8', 0, 0, 1000, 1000)
                """.trimIndent()
            )

            // Transactions: one valid for u_has_acc, two blank-accountId txs for account-less users
            db.execSQL(
                """
                INSERT INTO transactions (
                    id, userId, type, amountCentimes, category, envelopeKey, accountId, toAccountId,
                    `desc`, isoDate, hourOfDay, createdAtEpochMs, updatedAtEpochMs
                ) VALUES
                    ('tx_bank_exp', 'u_has_acc', 'expense', 25050, 'cafe', 'cafe', 'acc_bank_1', '', 'Café', '2026-06-01', 10, 2000, 2000),
                    ('tx_blank_1',  'u_no_acc',  'income',  300000, 'autre', 'autre', '', '', 'Salaire', '2026-06-02', 9, 2001, 2001),
                    ('tx_blank_2',  'u_no_acc_2','expense', 15000, 'transport', 'transport', '   ', '', 'Taxi', '2026-06-03', 8, 2002, 2002)
                """.trimIndent()
            )

            // Legacy financial_items: active borrowed debt, active lent debt, paid-off debt, and non-debt goal
            db.execSQL(
                """
                INSERT INTO financial_items (
                    id, userId, itemType, name, subtitle, amountCentimes, secondaryAmountCentimes,
                    daretMembers, dayOrTurn, extraInt, dateOrCycle, createdAtEpochMs, updatedAtEpochMs
                ) VALUES
                    ('debt_borrowed', 'u_has_acc', 'debt', 'Prêt Youssef', 'Youssef', 200000, 50000, NULL, 1, 0, 'borrowed', 3001, 3002),
                    ('debt_lent',     'u_has_acc', 'debt', 'Sellef Karima', 'Karima', 120000, 20000, NULL, 1, 0, 'lent',     3003, 3004),
                    ('debt_settled',  'u_has_acc', 'debt', 'Ancienne dette', 'Omar',  80000,  80000, NULL, 1, 0, 'borrowed', 3005, 3006),
                    ('goal_trip',     'u_has_acc', 'goal', 'Voyage', 'epargne',      500000, 100000, NULL, 1, 0, '2027-01-01', 3007, 3008)
                """.trimIndent()
            )

            // Saved credit stays in saved_credits (not auto-promoted)
            db.execSQL(
                """
                INSERT INTO saved_credits (
                    id, userId, type, loanAmountCentimes, downPaymentCentimes, financedPrincipalCentimes,
                    durationMonths, deferredMonths, annualInterestRateHt, applyInterestTva,
                    insuranceRate, monthlyPaymentCentimes, totalCostCentimes, debtToIncomeRatio,
                    maxAllowedDti, linkToMonthlyExpenses, createdAtEpochMs
                ) VALUES (
                    'sc_1', 'u_has_acc', 'AUTO', 12000000, 2000000, 10000000,
                    60, 0, 6.5, 1, 0.3, 198000, 11880000, 25.0, 45.0, 0, 4000
                )
                """.trimIndent()
            )
        }

        // Strict Room schema validation against 3.json
        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB_V2_TO_V3,
            3,
            true,
            MasroufiDatabase.MIGRATION_2_3
        )

        migratedDb.use { db ->
            val indices = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { c ->
                while (c.moveToNext()) indices += c.getString(0)
            }
            val expectedNewIndices = setOf(
                "index_transactions_transferGroupId",
                "index_transactions_loanId",
                "index_loans_userId",
                "index_loans_userId_status",
                "index_loans_userId_direction",
                "index_loans_sourceFinancialItemId",
                "index_loans_sourceSavedCreditId",
                "index_loan_repayments_userId",
                "index_loan_repayments_loanId",
                "index_loan_repayments_loanId_isoDate"
            )
            assertTrue("Missing v3 indices: ${expectedNewIndices - indices}", indices.containsAll(expectedNewIndices))
        }

        // Open through Room DAO to verify exact domain mapping & Net Worth deduplication
        val context = ApplicationProvider.getApplicationContext<Context>()
        val roomDb = Room.databaseBuilder(context, MasroufiDatabase::class.java, TEST_DB_V2_TO_V3)
            .addMigrations(
                MasroufiDatabase.MIGRATION_1_2,
                MasroufiDatabase.MIGRATION_2_3,
                MasroufiDatabase.MIGRATION_3_4
            )
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val dao = roomDb.masroufiDao()

                // 1. Default account seeding for BOTH account-less users with zero PK collision
                val uNoAccAccounts = dao.getAccounts("u_no_acc")
                val uNoAcc2Accounts = dao.getAccounts("u_no_acc_2")
                assertEquals(1, uNoAccAccounts.size)
                assertEquals(1, uNoAcc2Accounts.size)
                assertTrue(uNoAccAccounts.first().id != uNoAcc2Accounts.first().id)

                val txBlank1 = dao.getTransactionById("u_no_acc", "tx_blank_1")
                val txBlank2 = dao.getTransactionById("u_no_acc_2", "tx_blank_2")
                assertNotNull(txBlank1)
                assertNotNull(txBlank2)
                assertEquals(uNoAccAccounts.first().id, txBlank1!!.accountId)
                assertEquals(uNoAcc2Accounts.first().id, txBlank2!!.accountId)
                assertNull(txBlank1.transferGroupId)
                assertNull(txBlank1.loanId)

                // 2. Legacy debt backfill into loans (exact centimes, directions, statuses)
                val loans = dao.getLoans("u_has_acc").map { it.toDomain() }.associateBy { it.id }
                assertEquals(3, loans.size) // goal_trip NOT migrated, saved_credit NOT auto-promoted

                val borrowed = loans["loan_debt_debt_borrowed"]!!
                assertEquals(LoanDirection.LIABILITY, borrowed.direction)
                assertEquals(LoanCategory.PERSONAL_DEBT, borrowed.category)
                assertEquals(LoanStatus.ACTIVE, borrowed.status)
                assertEquals(200_000L, borrowed.principal.centimes)
                assertEquals(150_000L, borrowed.remaining.centimes)
                assertEquals("debt_borrowed", borrowed.sourceFinancialItemId)

                val lent = loans["loan_debt_debt_lent"]!!
                assertEquals(LoanDirection.RECEIVABLE, lent.direction)
                assertEquals(LoanStatus.ACTIVE, lent.status)
                assertEquals(120_000L, lent.principal.centimes)
                assertEquals(100_000L, lent.remaining.centimes)
                assertEquals("debt_lent", lent.sourceFinancialItemId)

                val settled = loans["loan_debt_debt_settled"]!!
                assertEquals(LoanStatus.PAID_OFF, settled.status)
                assertEquals(0L, settled.remaining.centimes)

                // 3. Saved credit remains intact and isolated
                assertEquals(1, dao.getSavedCredits("u_has_acc").size)

                // 4. Unified NetWorthCalculator deduplicates migrated legacy debts (ZERO double-counting!)
                val accs = dao.getAccounts("u_has_acc").map { it.toDomain() }
                val txs = dao.getTransactions("u_has_acc").map { it.toDomain() }
                val items = dao.getFinancialItems("u_has_acc").map { it.toDomain() }
                val nw = NetWorthCalculator.compute(accs, txs, loans.values.toList(), items)

                // Liquid = 1_000_000 - 25_050 = 974_950 centimes
                // Receivables = 100_000 centimes (counted ONCE)
                // Liabilities = 150_000 centimes (counted ONCE)
                // Net Worth = 974_950 + 100_000 - 150_000 = 924_950 centimes
                assertEquals(Money.fromCentimes(974_950L), nw.totalLiquidAssets)
                assertEquals(Money.fromCentimes(100_000L), nw.totalReceivables)
                assertEquals(Money.fromCentimes(150_000L), nw.totalLiabilities)
                assertEquals(Money.fromCentimes(924_950L), nw.netWorth)
                assertEquals(2, nw.activeLoansCount)
                assertEquals(1, nw.paidOffLoansCount)
            }
        } finally {
            roomDb.close()
        }
    }

    @Test
    fun `chained migration v1 to v2 to v3 preserves data losslessly`() {
        helper.createDatabase(TEST_DB_V1_TO_V3, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO user_profiles (
                    userId, baseBudget, payCycleDay, rolloverEnabled, language, isDarkTheme,
                    seeded, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('u_chain', 7500.25, 1, 1, 'fr', 0, 1, 1000, 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO transactions (
                    id, userId, type, amount, category, envelopeKey, accountId, toAccountId,
                    `desc`, isoDate, hourOfDay, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('tx_chain', 'u_chain', 'expense', 19.99, 'cafe', 'cafe', '', '', 'Coffee', '2026-06-05', 10, 2000, 2000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO financial_items (
                    id, userId, itemType, name, subtitle, amount, secondaryAmount,
                    dayOrTurn, extraInt, dateOrCycle, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('debt_chain', 'u_chain', 'debt', 'Debt Chain', 'Ali', 500.50, 100.25, 1, 0, 'borrowed', 3000, 3000)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB_V1_TO_V3,
            3,
            true,
            MasroufiDatabase.MIGRATION_1_2,
            MasroufiDatabase.MIGRATION_2_3
        ).close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val roomDb = Room.databaseBuilder(context, MasroufiDatabase::class.java, TEST_DB_V1_TO_V3)
            .addMigrations(
                MasroufiDatabase.MIGRATION_1_2,
                MasroufiDatabase.MIGRATION_2_3,
                MasroufiDatabase.MIGRATION_3_4
            )
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val dao = roomDb.masroufiDao()
                assertEquals(750_025L, dao.getProfile("u_chain")!!.baseBudgetCentimes)
                val accounts = dao.getAccounts("u_chain")
                assertEquals(1, accounts.size)
                assertEquals("acc_cash", accounts.first().id)

                val tx = dao.getTransactionById("u_chain", "tx_chain")!!
                assertEquals(1999L, tx.amountCentimes)
                assertEquals("acc_cash", tx.accountId)

                val loan = dao.getLoanById("u_chain", "loan_debt_debt_chain")!!
                assertEquals(50_050L, loan.principalCentimes)
                assertEquals(40_025L, loan.remainingCentimes)
                assertEquals("LIABILITY", loan.direction)
                assertEquals("ACTIVE", loan.status)
            }
        } finally {
            roomDb.close()
        }
    }
}
