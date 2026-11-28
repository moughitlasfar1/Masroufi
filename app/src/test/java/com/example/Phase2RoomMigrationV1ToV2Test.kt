package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.MasroufiDatabase
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
 * Phase 2 — Step 2.2 Room v1 -> v2 Migration Verification Suite.
 *
 * Proves:
 * 1. A real SQLite v1 database constructed from exported `1.json` and populated with
 *    IEEE-754 edge-case monetary values (`19.99`, `0.10`, `0.20`, `1250.50`, `-45.99`, `0.0`),
 *    Daret member counts (`secondaryAmount = 12.0`), Goal saved amounts (`250.50`), and
 *    SavedCredits migrates cleanly via [MasroufiDatabase.MIGRATION_1_2] into Room v2.
 * 2. Every monetary column converts to exact integer centimes with zero data loss.
 * 3. All 7 tables, column types, nullability constraints, primary keys, and all 10 secondary
 *    indices match exported `2.json` via Room's strict [MigrationTestHelper.runMigrationsAndValidate].
 * 4. Migrated rows read cleanly back through Room's generated DAO & entity layer.
 * 5. Both `1.json` (untouched v1 snapshot) and `2.json` (exported v2 snapshot) exist and are valid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2RoomMigrationV1ToV2Test {

    companion object {
        private const val TEST_DB = "masroufi_phase2_migration_v1_to_v2.db"
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
    fun `exported schema assets 1_json and 2_json both exist and have expected versions and hashes`() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val v1Json = assets.open("com.example.data.local.MasroufiDatabase/1.json").bufferedReader().use { it.readText() }
        val v2Json = assets.open("com.example.data.local.MasroufiDatabase/2.json").bufferedReader().use { it.readText() }

        val v1Db = JSONObject(v1Json).getJSONObject("database")
        val v2Db = JSONObject(v2Json).getJSONObject("database")

        assertEquals(1, v1Db.getInt("version"))
        assertEquals(EXPECTED_V1_IDENTITY_HASH, v1Db.getString("identityHash"))
        assertEquals(2, v2Db.getInt("version"))
        assertEquals(EXPECTED_V2_IDENTITY_HASH, v2Db.getString("identityHash"))
    }

    @Test
    fun `migrate v1 to v2 converts all edge-case monetary values to exact centimes and validates schema and indices`() {
        // 1. Build a real SQLite v1 DB directly from 1.json and seed edge-case values
        helper.createDatabase(TEST_DB, 1).use { db ->
            // user_profiles
            db.execSQL(
                """
                INSERT INTO user_profiles (
                    userId, baseBudget, payCycleDay, rolloverEnabled, language, isDarkTheme,
                    seeded, createdAtEpochMs, updatedAtEpochMs
                ) VALUES
                    ('u_mig', 8500.50, 25, 1, 'darija', 1, 1, 1000, 1001),
                    ('u_edge', 19.99, 1, 0, 'fr', 0, 1, 1002, 1003)
                """.trimIndent()
            )

            // accounts
            db.execSQL(
                """
                INSERT INTO accounts (
                    id, userId, name, kind, currency, initialBalance, colorHex,
                    isArchived, sortOrder, createdAtEpochMs, updatedAtEpochMs
                ) VALUES
                    ('acc_pos', 'u_mig', 'CIH Bank', 'bank', 'MAD', 1250.50, '#38BDF8', 0, 0, 1000, 1000),
                    ('acc_zero', 'u_mig', 'Cash', 'cash', 'MAD', 0.0, '#10B981', 0, 1, 1000, 1000),
                    ('acc_neg', 'u_mig', 'Overdraft', 'bank', 'MAD', -45.99, '#EF4444', 0, 2, 1000, 1000)
                """.trimIndent()
            )

            // envelopes (no monetary columns — must survive completely untouched)
            db.execSQL(
                """
                INSERT INTO envelopes (
                    id, userId, `key`, nameFr, nameAr, nameDarija, nameEn,
                    percentage, hasCap, colorHex, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('env_cafe', 'u_mig', 'cafe', 'Café', 'مقهى', 'قهوة', 'Cafe', 22.5, 1, '#D97706', 1000, 1000)
                """.trimIndent()
            )

            // transactions covering every classic IEEE-754 boundary
            db.execSQL(
                """
                INSERT INTO transactions (
                    id, userId, type, amount, category, envelopeKey, accountId, toAccountId,
                    `desc`, isoDate, hourOfDay, createdAtEpochMs, updatedAtEpochMs
                ) VALUES
                    ('tx_1999', 'u_mig', 'expense', 19.99, 'Café', 'cafe', 'acc_pos', '', 'IEEE trap 19.99', '2026-05-01', 9, 2001, 2001),
                    ('tx_010',  'u_mig', 'expense', 0.10,  'Café', 'cafe', 'acc_pos', '', 'Ten centimes',   '2026-05-02', 10, 2002, 2002),
                    ('tx_020',  'u_mig', 'expense', 0.20,  'Café', 'cafe', 'acc_pos', '', 'Twenty centimes','2026-05-03', 11, 2003, 2003),
                    ('tx_1250', 'u_mig', 'income',  1250.50,'Salaire','autre','acc_pos','', 'Prime',         '2026-05-04', 12, 2004, 2004),
                    ('tx_neg',  'u_mig', 'expense', -45.99,'Autre','autre','acc_neg','', 'Ajustement',       '2026-05-05', 13, 2005, 2005),
                    ('tx_zero', 'u_mig', 'expense', 0.0,   'Autre','autre','acc_zero','','Zero row',         '2026-05-06', 14, 2006, 2006)
                """.trimIndent()
            )

            // financial_items: daret (secondaryAmount = 12.0 members), goal (secondaryAmount = 250.50 saved DH), bill
            db.execSQL(
                """
                INSERT INTO financial_items (
                    id, userId, itemType, name, subtitle, amount, secondaryAmount,
                    dayOrTurn, extraInt, dateOrCycle, createdAtEpochMs, updatedAtEpochMs
                ) VALUES
                    ('fi_daret', 'u_mig', 'daret', 'Daret Famille', 'famille', 500.0, 12.0, 4, 2, '2026-05', 3001, 3001),
                    ('fi_goal',  'u_mig', 'goal',  'Voyage',        'epargne', 10000.0, 250.50, 1, 0, '2027-01', 3002, 3002),
                    ('fi_bill',  'u_mig', 'bill',  'Inwi Fibre',    'charges', 249.99, 0.0, 5, 1, '2026-05', 3003, 3003)
                """.trimIndent()
            )

            // saved_credits: 5 monetary columns -> centimes; rates/DTI remain REAL
            db.execSQL(
                """
                INSERT INTO saved_credits (
                    id, userId, type, loanAmount, downPayment, financedPrincipal,
                    durationMonths, deferredMonths, annualInterestRateHt, applyInterestTva,
                    insuranceRate, monthlyPayment, totalCost, debtToIncomeRatio,
                    maxAllowedDti, linkToMonthlyExpenses, createdAtEpochMs
                ) VALUES (
                    'cred_mig', 'u_mig', 'AUTO', 150000.0, 30000.50, 119999.50,
                    60, 3, 6.75, 1, 0.35, 2400.75, 144045.10, 28.4, 45.0, 1, 4000
                )
                """.trimIndent()
            )

            // import_metadata (untouched)
            db.execSQL(
                """
                INSERT INTO import_metadata (
                    userId, completedAtEpochMs, backupChecksumSha256, profileCount,
                    accountCount, transactionCount, envelopeCount, financialItemCount, creditCount
                ) VALUES ('u_mig', 5000, 'sha256_mig_check', 1, 3, 6, 1, 3, 1)
                """.trimIndent()
            )
        }

        // 2. Execute MIGRATION_1_2 with strict Room schema + index validation against 2.json
        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            MasroufiDatabase.MIGRATION_1_2
        )

        migratedDb.use { db ->
            // Verify room_master_table updated to v2 identity hash
            db.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(EXPECTED_V2_IDENTITY_HASH, c.getString(0))
            }

            // Verify all 10 secondary indices exist after table recreation
            val indices = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { c ->
                while (c.moveToNext()) indices += c.getString(0)
            }
            val expectedIndices = setOf(
                "index_accounts_userId",
                "index_transactions_userId",
                "index_transactions_userId_isoDate",
                "index_transactions_accountId",
                "index_transactions_envelopeKey",
                "index_envelopes_userId",
                "index_financial_items_userId",
                "index_financial_items_userId_itemType",
                "index_saved_credits_userId",
                "index_saved_credits_userId_createdAtEpochMs"
            )
            assertTrue("Missing expected v2 indices: ${expectedIndices - indices}", indices.containsAll(expectedIndices))

            // Verify user_profiles
            db.query("SELECT userId, baseBudgetCentimes, payCycleDay, language FROM user_profiles ORDER BY userId").use { c ->
                assertEquals(2, c.count)
                assertTrue(c.moveToFirst())
                assertEquals("u_edge", c.getString(0))
                assertEquals(1999L, c.getLong(1))
                assertTrue(c.moveToNext())
                assertEquals("u_mig", c.getString(0))
                assertEquals(850050L, c.getLong(1))
                assertEquals(25, c.getInt(2))
                assertEquals("darija", c.getString(3))
            }

            // Verify accounts
            val accBalances = mutableMapOf<String, Long>()
            db.query("SELECT id, initialBalanceCentimes FROM accounts WHERE userId = 'u_mig'").use { c ->
                while (c.moveToNext()) accBalances[c.getString(0)] = c.getLong(1)
            }
            assertEquals(125050L, accBalances["acc_pos"])
            assertEquals(0L, accBalances["acc_zero"])
            assertEquals(-4599L, accBalances["acc_neg"])

            // Verify transactions exact centimes (including 0.10 + 0.20 = 30L)
            val txAmounts = mutableMapOf<String, Long>()
            db.query("SELECT id, amountCentimes FROM transactions WHERE userId = 'u_mig'").use { c ->
                while (c.moveToNext()) txAmounts[c.getString(0)] = c.getLong(1)
            }
            assertEquals(6, txAmounts.size)
            assertEquals(1999L, txAmounts["tx_1999"])
            assertEquals(10L, txAmounts["tx_010"])
            assertEquals(20L, txAmounts["tx_020"])
            assertEquals(30L, txAmounts["tx_010"]!! + txAmounts["tx_020"]!!)
            assertEquals(125050L, txAmounts["tx_1250"])
            assertEquals(-4599L, txAmounts["tx_neg"])
            assertEquals(0L, txAmounts["tx_zero"])

            // Verify financial_items (Daret member count moved to daretMembers, non-daret NULL)
            db.query("SELECT id, amountCentimes, secondaryAmountCentimes, daretMembers FROM financial_items WHERE id = 'fi_daret'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(50000L, c.getLong(1))
                assertEquals(0L, c.getLong(2))
                assertTrue(!c.isNull(3))
                assertEquals(12, c.getInt(3))
            }
            db.query("SELECT id, amountCentimes, secondaryAmountCentimes, daretMembers FROM financial_items WHERE id = 'fi_goal'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1000000L, c.getLong(1))
                assertEquals(25050L, c.getLong(2))
                assertTrue("Goal daretMembers must be NULL", c.isNull(3))
            }
            db.query("SELECT id, amountCentimes, secondaryAmountCentimes, daretMembers FROM financial_items WHERE id = 'fi_bill'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(24999L, c.getLong(1))
                assertEquals(0L, c.getLong(2))
                assertTrue("Bill daretMembers must be NULL", c.isNull(3))
            }

            // Verify saved_credits monetary centimes + untouched REAL rates/DTI
            db.query(
                """
                SELECT loanAmountCentimes, downPaymentCentimes, financedPrincipalCentimes,
                       monthlyPaymentCentimes, totalCostCentimes,
                       annualInterestRateHt, insuranceRate, debtToIncomeRatio, maxAllowedDti
                FROM saved_credits WHERE id = 'cred_mig'
                """.trimIndent()
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(15000000L, c.getLong(0))
                assertEquals(3000050L, c.getLong(1))
                assertEquals(11999950L, c.getLong(2))
                assertEquals(240075L, c.getLong(3))
                assertEquals(14404510L, c.getLong(4))
                assertEquals(6.75, c.getDouble(5), 0.0001)
                assertEquals(0.35, c.getDouble(6), 0.0001)
                assertEquals(28.4, c.getDouble(7), 0.0001)
                assertEquals(45.0, c.getDouble(8), 0.0001)
            }

            // Verify untouched envelopes and import_metadata
            db.query("SELECT percentage FROM envelopes WHERE id = 'env_cafe'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(22.5, c.getDouble(0), 0.0001)
            }
            db.query("SELECT backupChecksumSha256, transactionCount FROM import_metadata WHERE userId = 'u_mig'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("sha256_mig_check", c.getString(0))
                assertEquals(6, c.getInt(1))
            }
        }

        // 3. Open the migrated DB through real Room + MasroufiDao to verify end-to-end entity mapping
        val context = ApplicationProvider.getApplicationContext<Context>()
        val roomDb = Room.databaseBuilder(context, MasroufiDatabase::class.java, TEST_DB)
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
                val profile = dao.getProfile("u_mig")
                assertNotNull(profile)
                assertEquals(850050L, profile!!.baseBudgetCentimes)

                val txs = dao.getTransactions("u_mig").associateBy { it.id }
                assertEquals(6, txs.size)
                assertEquals(1999L, txs["tx_1999"]!!.amountCentimes)
                assertEquals(125050L, txs["tx_1250"]!!.amountCentimes)
                assertEquals(-4599L, txs["tx_neg"]!!.amountCentimes)

                val items = dao.getFinancialItems("u_mig").associateBy { it.id }
                assertEquals(12, items["fi_daret"]!!.daretMembers)
                assertEquals(0L, items["fi_daret"]!!.secondaryAmountCentimes)
                assertNull(items["fi_goal"]!!.daretMembers)
                assertEquals(25050L, items["fi_goal"]!!.secondaryAmountCentimes)

                val credits = dao.getSavedCredits("u_mig")
                assertEquals(1, credits.size)
                assertEquals(11999950L, credits.first().financedPrincipalCentimes)
                assertEquals(240075L, credits.first().monthlyPaymentCentimes)
            }
        } finally {
            roomDb.close()
        }
    }
}
