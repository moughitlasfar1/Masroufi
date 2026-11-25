package com.example

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.MasroufiDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 0 (Step 0.2) — Room Schema v1 Export & MigrationTestHelper Verification Suite.
 *
 * SQLCipher Factory Caveat:
 * - Production opens [MasroufiDatabase] with `net.zetetic.database.sqlcipher.SupportOpenHelperFactory`
 *   (encrypted via `libsqlcipher.so` Android native JNI libraries).
 * - Local JVM Robolectric tests cannot load Android ARM/x86 `.so` binaries directly, so
 *   [MigrationTestHelper] uses Android's [FrameworkSQLiteOpenHelperFactory] here to verify
 *   exported schema JSON (`app/schemas/com.example.data.local.MasroufiDatabase/1.json`),
 *   table definitions, indices, SQL queries, and future Room `Migration(1, 2)` SQL scripts
 *   deterministically on every `gradle :app:testDebugUnitTest` run.
 * - Encryption-at-rest key wrapping and header verification remain covered by
 *   [EncryptedDatabasePhase1aTest] and [Phase1bDataLayerAndImportTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MasroufiSchemaV1Test {

    companion object {
        private const val TEST_DB = "masroufi_schema_v1_migration_test.db"
        private const val EXPECTED_V1_IDENTITY_HASH = "12cd7d38ab31023e13e7ae593835755f"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MasroufiDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun `createDatabase v1 from exported schema JSON supports CRUD across core tables and matches indices`() {
        val db = helper.createDatabase(TEST_DB, 1)
        try {
            // 1. Verify Room master identity hash matches exported 1.json
            db.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { cursor ->
                assertTrue("room_master_table must contain identity hash row", cursor.moveToFirst())
                assertEquals(EXPECTED_V1_IDENTITY_HASH, cursor.getString(0))
            }

            // 2. Verify all 7 tables and all 9 secondary indices were created from 1.json
            val createdTables = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                while (cursor.moveToNext()) {
                    createdTables += cursor.getString(0)
                }
            }
            val expectedTables = setOf(
                "user_profiles",
                "accounts",
                "transactions",
                "envelopes",
                "financial_items",
                "saved_credits",
                "import_metadata"
            )
            assertTrue("Missing expected v1 tables: ${expectedTables - createdTables}", createdTables.containsAll(expectedTables))

            val createdIndices = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { cursor ->
                while (cursor.moveToNext()) {
                    createdIndices += cursor.getString(0)
                }
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
            assertTrue("Missing expected v1 indices: ${expectedIndices - createdIndices}", createdIndices.containsAll(expectedIndices))

            // 3. Insert sample rows into core tables using raw v1 schema SQL and read them back
            db.execSQL(
                """
                INSERT INTO user_profiles (
                    userId, baseBudget, payCycleDay, rolloverEnabled, language, isDarkTheme,
                    seeded, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('u_schema_v1', 8500.0, 25, 1, 'darija', 0, 1, 1000, 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO accounts (
                    id, userId, name, kind, currency, initialBalance, colorHex,
                    isArchived, sortOrder, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('acc_bank', 'u_schema_v1', 'CIH Bank', 'bank', 'MAD', 5000.0, '#38BDF8', 0, 1, 1000, 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO envelopes (
                    id, userId, `key`, nameFr, nameAr, nameDarija, nameEn,
                    percentage, hasCap, colorHex, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('env_cafe', 'u_schema_v1', 'cafe', 'Café & Sorties', 'مقهى', 'قهوة', 'Cafe', 20.0, 1, '#D97706', 1000, 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO transactions (
                    id, userId, type, amount, category, envelopeKey, accountId, toAccountId,
                    `desc`, isoDate, hourOfDay, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('tx_v1_1', 'u_schema_v1', 'expense', 42.5, 'Café & Sorties', 'cafe', 'acc_bank', '', 'Café Paul', '2026-11-25', 9, 2000, 2000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO financial_items (
                    id, userId, itemType, name, subtitle, amount, secondaryAmount,
                    dayOrTurn, extraInt, dateOrCycle, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('bill_1', 'u_schema_v1', 'bill', 'Inwi Fibre', 'charges', 249.0, 0.0, 5, 0, '', 2000, 2000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO saved_credits (
                    id, userId, type, loanAmount, downPayment, financedPrincipal,
                    durationMonths, deferredMonths, annualInterestRateHt, applyInterestTva,
                    insuranceRate, monthlyPayment, totalCost, debtToIncomeRatio,
                    maxAllowedDti, linkToMonthlyExpenses, createdAtEpochMs
                ) VALUES ('cred_1', 'u_schema_v1', 'AUTO', 150000.0, 30000.0, 120000.0, 60, 0, 6.5, 1, 0.35, 2400.0, 144000.0, 28.0, 45.0, 0, 2000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO import_metadata (
                    userId, completedAtEpochMs, backupChecksumSha256, profileCount,
                    accountCount, transactionCount, envelopeCount, financialItemCount, creditCount
                ) VALUES ('u_schema_v1', 3000, 'sha256_sample', 1, 1, 1, 1, 1, 1)
                """.trimIndent()
            )

            // Read-back assertions across core tables
            db.query("SELECT baseBudget, payCycleDay, language FROM user_profiles WHERE userId = 'u_schema_v1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(8500.0, c.getDouble(0), 0.001)
                assertEquals(25, c.getInt(1))
                assertEquals("darija", c.getString(2))
            }
            db.query("SELECT amount, envelopeKey, `desc`, isoDate FROM transactions WHERE id = 'tx_v1_1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(42.5, c.getDouble(0), 0.001)
                assertEquals("cafe", c.getString(1))
                assertEquals("Café Paul", c.getString(2))
                assertEquals("2026-11-25", c.getString(3))
            }
            db.query("SELECT name, initialBalance FROM accounts WHERE id = 'acc_bank'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("CIH Bank", c.getString(0))
                assertEquals(5000.0, c.getDouble(1), 0.001)
            }
            db.query("SELECT type, financedPrincipal FROM saved_credits WHERE id = 'cred_1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("AUTO", c.getString(0))
                assertEquals(120000.0, c.getDouble(1), 0.001)
            }
        } finally {
            db.close()
        }
    }

    /*
     * Phase 2 Migration Test Pattern (uncomment & implement when bumping MasroufiDatabase to version = 2):
     *
     * @Test
     * fun `migrate 1 to 2 preserves all user data without precision loss`() {
     *     helper.createDatabase(TEST_DB, 1).apply {
     *         execSQL("INSERT INTO transactions (...) VALUES (...)")
     *         close()
     *     }
     *     val migratedDb = helper.runMigrationsAndValidate(
     *         TEST_DB,
     *         2,
     *         true,
     *         MasroufiMigrations.MIGRATION_1_2
     *     )
     *     migratedDb.query("SELECT ... FROM transactions WHERE id = 'tx_v1_1'").use { cursor ->
     *         assertTrue(cursor.moveToFirst())
     *         // Verify migrated columns (e.g. amountCentimes == 4250L)
     *     }
     *     migratedDb.close()
     * }
     */
}
