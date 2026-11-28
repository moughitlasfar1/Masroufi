package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.MasroufiDatabase
import com.example.data.local.TransactionEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 7A — Room v4 -> v5 Migration & FTS4 Verification Suite.
 *
 * Proves:
 * 1. Exported `1.json`, `2.json`, `3.json`, and `4.json` remain strictly untouched with original
 *    identity hashes, and exported `5.json` exists with version 5 and `transactions_fts`.
 * 2. `MIGRATION_4_5` passes Room's strict [MigrationTestHelper.runMigrationsAndValidate] schema
 *    verification against `5.json`.
 * 3. Pre-existing v4 transactions (French accents, Arabic, Darija, English) are immediately
 *    searchable via `transactions_fts` after migration (`rebuild` command).
 * 4. Subsequent Room INSERT, UPDATE, and DELETE operations keep `transactions_fts` synchronized
 *    automatically via the SQLite triggers created in `MIGRATION_4_5`.
 * 5. Full chained migration `v1 -> v2 -> v3 -> v4 -> v5` runs losslessly end-to-end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase7ARoomMigrationV4ToV5Test {

    companion object {
        private const val TEST_DB_V4_TO_V5 = "masroufi_phase7a_migration_v4_to_v5.db"
        private const val TEST_DB_V1_TO_V5 = "masroufi_phase7a_migration_v1_to_v5.db"
        private const val EXPECTED_V1_IDENTITY_HASH = "12cd7d38ab31023e13e7ae593835755f"
        private const val EXPECTED_V2_IDENTITY_HASH = "46d6a2ffc551b71dbbba24bc06d32273"
        private const val EXPECTED_V3_IDENTITY_HASH = "9f8ad30ea004fbd41ccae235dbfd8453"
        private const val EXPECTED_V4_IDENTITY_HASH = "5c045651a12025bfdcee03512f986bd8"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MasroufiDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun `schema assets 1_json through 4_json are untouched and 5_json exports transactions_fts`() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val v1Json = assets.open("com.example.data.local.MasroufiDatabase/1.json").bufferedReader().use { it.readText() }
        val v2Json = assets.open("com.example.data.local.MasroufiDatabase/2.json").bufferedReader().use { it.readText() }
        val v3Json = assets.open("com.example.data.local.MasroufiDatabase/3.json").bufferedReader().use { it.readText() }
        val v4Json = assets.open("com.example.data.local.MasroufiDatabase/4.json").bufferedReader().use { it.readText() }
        val v5Json = assets.open("com.example.data.local.MasroufiDatabase/5.json").bufferedReader().use { it.readText() }

        val v1Db = JSONObject(v1Json).getJSONObject("database")
        val v2Db = JSONObject(v2Json).getJSONObject("database")
        val v3Db = JSONObject(v3Json).getJSONObject("database")
        val v4Db = JSONObject(v4Json).getJSONObject("database")
        val v5Db = JSONObject(v5Json).getJSONObject("database")

        assertEquals(1, v1Db.getInt("version"))
        assertEquals(EXPECTED_V1_IDENTITY_HASH, v1Db.getString("identityHash"))
        assertEquals(2, v2Db.getInt("version"))
        assertEquals(EXPECTED_V2_IDENTITY_HASH, v2Db.getString("identityHash"))
        assertEquals(3, v3Db.getInt("version"))
        assertEquals(EXPECTED_V3_IDENTITY_HASH, v3Db.getString("identityHash"))
        assertEquals(4, v4Db.getInt("version"))
        assertEquals(EXPECTED_V4_IDENTITY_HASH, v4Db.getString("identityHash"))
        assertEquals(5, v5Db.getInt("version"))
        assertTrue(v5Db.getString("identityHash").isNotBlank())

        val v5Entities = v5Db.getJSONArray("entities")
        val tableNames = (0 until v5Entities.length()).map {
            v5Entities.getJSONObject(it).getString("tableName")
        }.toSet()
        assertTrue("Expected transactions_fts in 5.json", "transactions_fts" in tableNames)
    }

    @Test
    fun `migrate v4 to v5 rebuilds FTS4 index for existing rows and keeps triggers synced on insert update delete`() {
        helper.createDatabase(TEST_DB_V4_TO_V5, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO user_profiles (
                    userId, baseBudgetCentimes, payCycleDay, rolloverEnabled, language, isDarkTheme,
                    seeded, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('u_v5', 700000, 1, 1, 'fr', 0, 1, 1000, 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO accounts (
                    id, userId, name, kind, currency, initialBalanceCentimes, colorHex,
                    isArchived, sortOrder, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('acc_cash', 'u_v5', 'Cash', 'cash', 'MAD', 300000, '#10B981', 0, 0, 1000, 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO transactions (
                    id, userId, type, amountCentimes, category, envelopeKey, accountId, toAccountId,
                    transferGroupId, loanId, recurringOccurrenceKey, `desc`, isoDate, hourOfDay, createdAtEpochMs, updatedAtEpochMs
                ) VALUES
                    ('tx_fr', 'u_v5', 'expense', 2500, 'Café', 'cafe', 'acc_cash', '', NULL, NULL, NULL, 'Café Crème Étoile', '2026-07-02', 9, 2001, 2001),
                    ('tx_ar', 'u_v5', 'expense', 45000, 'بقالة', 'alimentation', 'acc_cash', '', NULL, NULL, NULL, 'تقدية مرجان السوبرماركت', '2026-07-03', 14, 2002, 2002),
                    ('tx_en', 'u_v5', 'expense', 12000, 'Transport', 'transport', 'acc_cash', '', NULL, NULL, NULL, 'Train ONCF Rabat', '2026-07-04', 18, 2003, 2003)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB_V4_TO_V5,
            5,
            true,
            MasroufiDatabase.MIGRATION_4_5
        ).close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val roomDb = Room.databaseBuilder(context, MasroufiDatabase::class.java, TEST_DB_V4_TO_V5)
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

                // 1. Pre-existing v4 rows are immediately searchable via FTS4 (unicode61 folds diacritics)
                val frHits = dao.searchTransactionsWithFts("u_v5", "cafe*", null, null, null, null, null, null, null, null, false)
                assertEquals(listOf("tx_fr"), frHits.map { it.id })

                val arHits = dao.searchTransactionsWithFts("u_v5", "مرجان*", null, null, null, null, null, null, null, null, false)
                assertEquals(listOf("tx_ar"), arHits.map { it.id })

                // 2. Trigger sync on INSERT
                dao.upsertTransaction(
                    TransactionEntity(
                        id = "tx_new",
                        userId = "u_v5",
                        type = "expense",
                        amountCentimes = 8500L,
                        category = "Pharmacie",
                        envelopeKey = "sante",
                        accountId = "acc_cash",
                        desc = "Pharmacie Garde Casablanca",
                        isoDate = "2026-07-05",
                        hourOfDay = 20,
                        createdAtEpochMs = 3000L,
                        updatedAtEpochMs = 3000L
                    )
                )
                val insertHits = dao.searchTransactionsWithFts("u_v5", "pharmacie*", null, null, null, null, null, null, null, null, false)
                assertEquals(listOf("tx_new"), insertHits.map { it.id })

                // 3. Trigger sync on UPDATE (old term disappears, new term matches)
                dao.upsertTransaction(
                    TransactionEntity(
                        id = "tx_new",
                        userId = "u_v5",
                        type = "expense",
                        amountCentimes = 8500L,
                        category = "Santé",
                        envelopeKey = "sante",
                        accountId = "acc_cash",
                        desc = "Clinique Dentaire Anfa",
                        isoDate = "2026-07-05",
                        hourOfDay = 20,
                        createdAtEpochMs = 3000L,
                        updatedAtEpochMs = 3001L
                    )
                )
                assertTrue(
                    dao.searchTransactionsWithFts("u_v5", "pharmacie*", null, null, null, null, null, null, null, null, false).isEmpty()
                )
                assertEquals(
                    listOf("tx_new"),
                    dao.searchTransactionsWithFts("u_v5", "dentaire*", null, null, null, null, null, null, null, null, false).map { it.id }
                )

                // 4. Trigger sync on DELETE
                dao.deleteTransaction("u_v5", "tx_new")
                assertTrue(
                    dao.searchTransactionsWithFts("u_v5", "dentaire*", null, null, null, null, null, null, null, null, false).isEmpty()
                )
            }
        } finally {
            roomDb.close()
        }
    }

    @Test
    fun `chained migration v1 to v2 to v3 to v4 to v5 preserves data and enables FTS4 search`() {
        helper.createDatabase(TEST_DB_V1_TO_V5, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO user_profiles (
                    userId, baseBudget, payCycleDay, rolloverEnabled, language, isDarkTheme,
                    seeded, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('u_chain5', 9000.50, 1, 1, 'fr', 0, 1, 1000, 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO transactions (
                    id, userId, type, amount, category, envelopeKey, accountId, toAccountId,
                    `desc`, isoDate, hourOfDay, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('tx_c5', 'u_chain5', 'expense', 149.75, 'Alimentation', 'alimentation', '', '', 'Boulangerie Épi Doré', '2026-07-06', 8, 2000, 2000)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB_V1_TO_V5,
            5,
            true,
            MasroufiDatabase.MIGRATION_1_2,
            MasroufiDatabase.MIGRATION_2_3,
            MasroufiDatabase.MIGRATION_3_4,
            MasroufiDatabase.MIGRATION_4_5
        ).close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val roomDb = Room.databaseBuilder(context, MasroufiDatabase::class.java, TEST_DB_V1_TO_V5)
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
                assertEquals(900_050L, dao.getProfile("u_chain5")!!.baseBudgetCentimes)
                val hits = dao.searchTransactionsWithFts("u_chain5", "boulangerie*", null, null, null, null, null, null, null, null, false)
                assertEquals(1, hits.size)
                assertEquals("tx_c5", hits.first().id)
                assertEquals(14_975L, hits.first().amountCentimes)
            }
        } finally {
            roomDb.close()
        }
    }
}
