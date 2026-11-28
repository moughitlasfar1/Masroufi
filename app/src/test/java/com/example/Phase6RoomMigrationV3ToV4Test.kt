package com.example

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.MasroufiDatabase
import com.example.data.local.MasroufiEntityMappers.toDomain
import com.example.domain.CashFlowForecastEngine
import com.example.domain.ForecastHorizon
import com.example.domain.NetWorthCalculator
import com.example.domain.PayCycleEngine
import com.example.domain.RecurrenceDirection
import com.example.domain.RecurrenceFrequency
import com.example.domain.RecurrencePostingMode
import com.masroufi.core.Money
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6 — Room v3 -> v4 Migration Verification Suite.
 *
 * Proves:
 * 1. Exported `1.json`, `2.json`, and `3.json` remain completely untouched with original identity hashes,
 *    and exported `4.json` exists with version 4, `recurring_rules` table, and `recurringOccurrenceKey`.
 * 2. `MIGRATION_3_4` passes Room's strict [MigrationTestHelper.runMigrationsAndValidate] schema,
 *    nullability, primary key, and unique index verification against `4.json`.
 * 3. Existing legacy `financial_items` (`itemType = 'bill'`) are losslessly backfilled into
 *    `recurring_rules` (`postingMode = 'CONFIRM'`, `sourceFinancialItemId = bill.id`), with
 *    exact `Long` centimes and ZERO double-counting in [CashFlowForecastEngine].
 * 4. The unique index on `transactions(userId, recurringOccurrenceKey)` blocks duplicate
 *    occurrence postings while permitting multiple `NULL` keys for normal transactions.
 * 5. Full chained upgrade `v1 -> v2 -> v3 -> v4` preserves all user data without loss.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase6RoomMigrationV3ToV4Test {

    companion object {
        private const val TEST_DB_V3_TO_V4 = "masroufi_phase6_migration_v3_to_v4.db"
        private const val TEST_DB_V1_TO_V4 = "masroufi_phase6_migration_v1_to_v4.db"
        private const val EXPECTED_V1_IDENTITY_HASH = "12cd7d38ab31023e13e7ae593835755f"
        private const val EXPECTED_V2_IDENTITY_HASH = "46d6a2ffc551b71dbbba24bc06d32273"
        private const val EXPECTED_V3_IDENTITY_HASH = "9f8ad30ea004fbd41ccae235dbfd8453"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MasroufiDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun `schema assets 1_json 2_json 3_json are untouched and 4_json exports recurring_rules and recurringOccurrenceKey`() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val v1Json = assets.open("com.example.data.local.MasroufiDatabase/1.json").bufferedReader().use { it.readText() }
        val v2Json = assets.open("com.example.data.local.MasroufiDatabase/2.json").bufferedReader().use { it.readText() }
        val v3Json = assets.open("com.example.data.local.MasroufiDatabase/3.json").bufferedReader().use { it.readText() }
        val v4Json = assets.open("com.example.data.local.MasroufiDatabase/4.json").bufferedReader().use { it.readText() }

        val v1Db = JSONObject(v1Json).getJSONObject("database")
        val v2Db = JSONObject(v2Json).getJSONObject("database")
        val v3Db = JSONObject(v3Json).getJSONObject("database")
        val v4Db = JSONObject(v4Json).getJSONObject("database")

        assertEquals(1, v1Db.getInt("version"))
        assertEquals(EXPECTED_V1_IDENTITY_HASH, v1Db.getString("identityHash"))
        assertEquals(2, v2Db.getInt("version"))
        assertEquals(EXPECTED_V2_IDENTITY_HASH, v2Db.getString("identityHash"))
        assertEquals(3, v3Db.getInt("version"))
        assertEquals(EXPECTED_V3_IDENTITY_HASH, v3Db.getString("identityHash"))
        assertEquals(4, v4Db.getInt("version"))
        assertTrue(v4Db.getString("identityHash").isNotBlank())

        val v4Entities = v4Db.getJSONArray("entities")
        val entitiesByTable = (0 until v4Entities.length()).associate { idx ->
            val obj = v4Entities.getJSONObject(idx)
            obj.getString("tableName") to obj
        }
        assertTrue("Expected recurring_rules table in 4.json", "recurring_rules" in entitiesByTable)

        val txFields = entitiesByTable["transactions"]!!.getJSONArray("fields")
        val txFieldNames = (0 until txFields.length()).map {
            txFields.getJSONObject(it).getString("columnName")
        }.toSet()
        assertTrue("Expected recurringOccurrenceKey column on transactions in 4.json", "recurringOccurrenceKey" in txFieldNames)
    }

    @Test
    fun `migrate v3 to v4 backfills legacy bills creates unique occurrence index and prevents forecast double counting`() {
        val todayIso = PayCycleEngine.todayIso()
        val currentYm = todayIso.substring(0, 7)

        helper.createDatabase(TEST_DB_V3_TO_V4, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO user_profiles (
                    userId, baseBudgetCentimes, payCycleDay, rolloverEnabled, language, isDarkTheme,
                    seeded, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('u_v4', 750000, 1, 1, 'fr', 0, 1, 1000, 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO accounts (
                    id, userId, name, kind, currency, initialBalanceCentimes, colorHex,
                    isArchived, sortOrder, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('acc_cih', 'u_v4', 'CIH Bank', 'bank', 'MAD', 500000, '#38BDF8', 0, 0, 1000, 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO transactions (
                    id, userId, type, amountCentimes, category, envelopeKey, accountId, toAccountId,
                    transferGroupId, loanId, `desc`, isoDate, hourOfDay, createdAtEpochMs, updatedAtEpochMs
                ) VALUES
                    ('tx_norm_1', 'u_v4', 'expense', 5000, 'cafe', 'cafe', 'acc_cih', '', NULL, NULL, 'Café 1', '$todayIso', 9, 2000, 2000),
                    ('tx_norm_2', 'u_v4', 'expense', 7500, 'cafe', 'cafe', 'acc_cih', '', NULL, NULL, 'Café 2', '$todayIso', 10, 2001, 2001)
                """.trimIndent()
            )
            // Seed 2 legacy bills (one unpaid this month, one already paid this month) + 1 non-bill goal
            db.execSQL(
                """
                INSERT INTO financial_items (
                    id, userId, itemType, name, subtitle, amountCentimes, secondaryAmountCentimes,
                    daretMembers, dayOrTurn, extraInt, dateOrCycle, createdAtEpochMs, updatedAtEpochMs
                ) VALUES
                    ('bill_fibre', 'u_v4', 'bill', 'Inwi Fibre', 'internet', 24999, 0, NULL, 15, 0, '', 3001, 3001),
                    ('bill_rent',  'u_v4', 'bill', 'Loyer',      'loyer',    300000, 0, NULL, 31, 0, '$currentYm', 3002, 3002),
                    ('goal_car',   'u_v4', 'goal', 'Voiture',    'epargne',  5000000, 200000, NULL, 1, 0, '2028-01-01', 3003, 3003)
                """.trimIndent()
            )
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB_V3_TO_V4,
            4,
            true,
            MasroufiDatabase.MIGRATION_3_4
        )

        migratedDb.use { db ->
            val indices = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { c ->
                while (c.moveToNext()) indices += c.getString(0)
            }
            val expectedV4Indices = setOf(
                "index_transactions_recurringOccurrenceKey",
                "index_recurring_rules_userId",
                "index_recurring_rules_userId_isPaused_nextDueIsoDate",
                "index_recurring_rules_linkedLoanId",
                "index_recurring_rules_sourceFinancialItemId"
            )
            assertTrue("Missing v4 indices: ${expectedV4Indices - indices}", indices.containsAll(expectedV4Indices))

            // Unique index on (userId, recurringOccurrenceKey) rejects duplicate occurrence keys
            db.execSQL(
                """
                INSERT INTO transactions (
                    id, userId, type, amountCentimes, category, envelopeKey, accountId, toAccountId,
                    transferGroupId, loanId, recurringOccurrenceKey, `desc`, isoDate, hourOfDay, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('tx_occ_1', 'u_v4', 'expense', 24999, 'internet', 'internet', 'acc_cih', '', NULL, NULL, 'rec_bill_bill_fibre:2026-06-15', 'Inwi', '2026-06-15', 9, 4000, 4000)
                """.trimIndent()
            )
            try {
                db.execSQL(
                    """
                    INSERT INTO transactions (
                        id, userId, type, amountCentimes, category, envelopeKey, accountId, toAccountId,
                        transferGroupId, loanId, recurringOccurrenceKey, `desc`, isoDate, hourOfDay, createdAtEpochMs, updatedAtEpochMs
                    ) VALUES ('tx_occ_dup', 'u_v4', 'expense', 24999, 'internet', 'internet', 'acc_cih', '', NULL, NULL, 'rec_bill_bill_fibre:2026-06-15', 'Dup', '2026-06-15', 9, 4001, 4001)
                    """.trimIndent()
                )
                fail("Expected SQLiteConstraintException for duplicate (userId, recurringOccurrenceKey)")
            } catch (_: SQLiteConstraintException) {
                // Expected: DB-level idempotency constraint holds
            }
            // Clean up synthetic test tx so DAO assertions see original rows
            db.execSQL("DELETE FROM transactions WHERE id = 'tx_occ_1'")
        }

        // Open through Room DAO at version 4
        val context = ApplicationProvider.getApplicationContext<Context>()
        val roomDb = Room.databaseBuilder(context, MasroufiDatabase::class.java, TEST_DB_V3_TO_V4)
            .addMigrations(
                MasroufiDatabase.MIGRATION_1_2,
                MasroufiDatabase.MIGRATION_2_3,
                MasroufiDatabase.MIGRATION_3_4,
                MasroufiDatabase.MIGRATION_4_5
            )
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val dao = roomDb.masroufiDao()
                val rules = dao.getRecurringRules("u_v4").map { it.toDomain() }.associateBy { it.id }
                assertEquals(2, rules.size) // goal_car NOT backfilled

                val fibre = rules["rec_bill_bill_fibre"]!!
                assertEquals("Inwi Fibre", fibre.title)
                assertEquals(RecurrenceDirection.EXPENSE, fibre.direction)
                assertEquals(RecurrenceFrequency.MONTHLY, fibre.frequency)
                assertEquals(RecurrencePostingMode.CONFIRM, fibre.postingMode)
                assertEquals(Money.fromCentimes(24_999L), fibre.amount)
                assertEquals(15, fibre.anchorDayOfMonth)
                assertEquals("acc_cih", fibre.accountId)
                assertEquals("bill_fibre", fibre.sourceFinancialItemId)
                assertTrue(fibre.nextDueIsoDate.startsWith(currentYm))

                val rent = rules["rec_bill_bill_rent"]!!
                assertEquals(Money.fromCentimes(300_000L), rent.amount)
                assertEquals(31, rent.anchorDayOfMonth)
                assertEquals("bill_rent", rent.sourceFinancialItemId)
                // Because currentYm was already in dateOrCycle, rent's nextDueIsoDate advanced to next month
                assertTrue(
                    "Already-paid bill must advance nextDueIsoDate past $currentYm, was ${rent.nextDueIsoDate}",
                    !rent.nextDueIsoDate.startsWith(currentYm)
                )

                // Existing normal transactions retain NULL recurringOccurrenceKey
                val tx1 = dao.getTransactionById("u_v4", "tx_norm_1")
                assertNotNull(tx1)
                assertNull(tx1!!.recurringOccurrenceKey)

                // CashFlowForecastEngine deduplicates migrated bills (counted ONCE via recurring_rules, not also via financial_items)
                val accs = dao.getAccounts("u_v4").map { it.toDomain() }
                val txs = dao.getTransactions("u_v4").map { it.toDomain() }
                val items = dao.getFinancialItems("u_v4").map { it.toDomain() }
                val nw = NetWorthCalculator.compute(accs, txs, emptyList(), items)
                val forecast = CashFlowForecastEngine.compute(
                    netWorthSnapshot = nw,
                    payCycleDay = 1,
                    todayIso = "${currentYm}-01",
                    horizon = ForecastHorizon.ACTIVE_PAY_CYCLE,
                    recurringRules = rules.values.toList(),
                    legacyFinancialItems = items,
                    transactions = txs
                )
                // Only bill_fibre (249.99 DH) is due in currentYm; bill_rent was already paid for currentYm.
                assertEquals(1, forecast.upcomingItems.size)
                assertEquals(Money.fromCentimes(24_999L), forecast.committedUpcomingBills)
            }
        } finally {
            roomDb.close()
        }
    }

    @Test
    fun `chained migration v1 to v2 to v3 to v4 preserves accounts transactions loans and bills losslessly`() {
        helper.createDatabase(TEST_DB_V1_TO_V4, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO user_profiles (
                    userId, baseBudget, payCycleDay, rolloverEnabled, language, isDarkTheme,
                    seeded, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('u_chain4', 8200.50, 1, 1, 'darija', 1, 1, 1000, 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO transactions (
                    id, userId, type, amount, category, envelopeKey, accountId, toAccountId,
                    `desc`, isoDate, hourOfDay, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('tx_c4', 'u_chain4', 'expense', 42.75, 'cafe', 'cafe', '', '', 'Thé', '2026-06-05', 10, 2000, 2000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO financial_items (
                    id, userId, itemType, name, subtitle, amount, secondaryAmount,
                    dayOrTurn, extraInt, dateOrCycle, createdAtEpochMs, updatedAtEpochMs
                ) VALUES
                    ('debt_c4', 'u_chain4', 'debt', 'Dette Ami', 'Hassan', 1000.00, 250.50, 1, 0, 'borrowed', 3000, 3000),
                    ('bill_c4', 'u_chain4', 'bill', 'Lydec Eau', 'charges', 185.25, 0.0, 10, 0, '', 3001, 3001)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB_V1_TO_V4,
            4,
            true,
            MasroufiDatabase.MIGRATION_1_2,
            MasroufiDatabase.MIGRATION_2_3,
            MasroufiDatabase.MIGRATION_3_4
        ).close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val roomDb = Room.databaseBuilder(context, MasroufiDatabase::class.java, TEST_DB_V1_TO_V4)
            .addMigrations(
                MasroufiDatabase.MIGRATION_1_2,
                MasroufiDatabase.MIGRATION_2_3,
                MasroufiDatabase.MIGRATION_3_4,
                MasroufiDatabase.MIGRATION_4_5
            )
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val dao = roomDb.masroufiDao()
                assertEquals(820_050L, dao.getProfile("u_chain4")!!.baseBudgetCentimes)

                val accounts = dao.getAccounts("u_chain4")
                assertEquals(1, accounts.size)
                assertEquals("acc_cash", accounts.first().id)

                val tx = dao.getTransactionById("u_chain4", "tx_c4")!!
                assertEquals(4_275L, tx.amountCentimes)
                assertEquals("acc_cash", tx.accountId)
                assertNull(tx.recurringOccurrenceKey)

                val loan = dao.getLoanById("u_chain4", "loan_debt_debt_c4")!!
                assertEquals(100_000L, loan.principalCentimes)
                assertEquals(74_950L, loan.remainingCentimes)

                val rule = dao.getRecurringRuleById("u_chain4", "rec_bill_bill_c4")!!
                assertEquals(18_525L, rule.amountCentimes)
                assertEquals("CONFIRM", rule.postingMode)
                assertEquals("acc_cash", rule.accountId)
                assertEquals("bill_c4", rule.sourceFinancialItemId)
            }
        } finally {
            roomDb.close()
        }
    }
}
