package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Phase 5 Room database migrations.
 */
object MasroufiDatabaseMigrations {

    private val NON_ALNUM_REGEX = Regex("[^a-zA-Z0-9]")

    /**
     * Lossless Room v2 -> v3 migration:
     * 1. Adds nullable `transferGroupId` and `loanId` columns + indices to `transactions`.
     * 2. Normalizes blank/whitespace `transactions.accountId` to `'acc_cash'`, and seeds a
     *    default Cash account (`acc_cash` or collision-safe `acc_cash_<uid>`) for any user
     *    present in `user_profiles` or `transactions` who has zero rows in `accounts`.
     * 3. Creates `loans` and `loan_repayments` tables + indices using exact INTEGER centimes.
     * 4. Losslessly backfills existing `financial_items` (`itemType = 'debt'`) into `loans`
     *    with `sourceFinancialItemId = financial_items.id` to prevent Net Worth double-counting.
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Add nullable links to transactions
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `transferGroupId` TEXT")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `loanId` TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transferGroupId` ON `transactions` (`transferGroupId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_loanId` ON `transactions` (`loanId`)")

            // 2. Backfill blank transaction accountIds & seed default Cash account for account-less users
            db.execSQL(
                """
                UPDATE `transactions`
                SET `accountId` = 'acc_cash'
                WHERE `accountId` IS NULL OR trim(`accountId`) = ''
                """.trimIndent()
            )

            val usersNeedingDefaultAccount = mutableListOf<String>()
            db.query(
                """
                SELECT DISTINCT `userId` FROM (
                    SELECT `userId` FROM `user_profiles`
                    UNION
                    SELECT `userId` FROM `transactions`
                ) AS u
                WHERE trim(u.`userId`) != ''
                  AND NOT EXISTS (
                      SELECT 1 FROM `accounts` a WHERE a.`userId` = u.`userId`
                  )
                """.trimIndent()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val uid = cursor.getString(0)
                    if (!uid.isNullOrBlank()) {
                        usersNeedingDefaultAccount.add(uid)
                    }
                }
            }

            if (usersNeedingDefaultAccount.isNotEmpty()) {
                var defaultCashIdTaken = false
                db.query("SELECT 1 FROM `accounts` WHERE `id` = 'acc_cash' LIMIT 1").use { c ->
                    defaultCashIdTaken = c.moveToFirst()
                }
                val nowMs = System.currentTimeMillis()
                usersNeedingDefaultAccount.forEach { uid ->
                    val seededAccId = if (!defaultCashIdTaken) {
                        defaultCashIdTaken = true
                        "acc_cash"
                    } else {
                        val suffix = uid.replace(NON_ALNUM_REGEX, "").take(10).ifBlank { "u" }
                        "acc_cash_$suffix"
                    }
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `accounts` (
                            `id`, `userId`, `name`, `kind`, `currency`,
                            `initialBalanceCentimes`, `colorHex`, `isArchived`,
                            `sortOrder`, `createdAtEpochMs`, `updatedAtEpochMs`
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf<Any>(
                            seededAccId,
                            uid,
                            "Espèces (Cash)",
                            "cash",
                            "MAD",
                            0L,
                            "#10B981",
                            0,
                            0,
                            nowMs,
                            nowMs
                        )
                    )
                    if (seededAccId != "acc_cash") {
                        db.execSQL(
                            """
                            UPDATE `transactions`
                            SET `accountId` = ?
                            WHERE `userId` = ? AND `accountId` = 'acc_cash'
                            """.trimIndent(),
                            arrayOf<Any>(seededAccId, uid)
                        )
                    }
                }
            }

            // 3. Create loans & loan_repayments tables
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `loans` (
                    `id` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `counterparty` TEXT NOT NULL,
                    `direction` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `principalCentimes` INTEGER NOT NULL,
                    `remainingCentimes` INTEGER NOT NULL,
                    `monthlyPaymentCentimes` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `linkedAccountId` TEXT,
                    `sourceFinancialItemId` TEXT,
                    `sourceSavedCreditId` TEXT,
                    `dueIsoDate` TEXT,
                    `notes` TEXT NOT NULL,
                    `createdAtEpochMs` INTEGER NOT NULL,
                    `updatedAtEpochMs` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_userId` ON `loans` (`userId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_userId_status` ON `loans` (`userId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_userId_direction` ON `loans` (`userId`, `direction`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_sourceFinancialItemId` ON `loans` (`sourceFinancialItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_sourceSavedCreditId` ON `loans` (`sourceSavedCreditId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `loan_repayments` (
                    `id` TEXT NOT NULL,
                    `loanId` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `amountCentimes` INTEGER NOT NULL,
                    `isoDate` TEXT NOT NULL,
                    `linkedAccountId` TEXT,
                    `linkedTransactionId` TEXT,
                    `note` TEXT NOT NULL,
                    `createdAtEpochMs` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_repayments_userId` ON `loan_repayments` (`userId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_repayments_loanId` ON `loan_repayments` (`loanId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_repayments_loanId_isoDate` ON `loan_repayments` (`loanId`, `isoDate`)")

            // 4. Zero-loss backfill of existing financial_items (itemType = 'debt') into loans
            db.execSQL(
                """
                INSERT OR IGNORE INTO `loans` (
                    `id`, `userId`, `title`, `counterparty`, `direction`, `category`,
                    `principalCentimes`, `remainingCentimes`, `monthlyPaymentCentimes`, `status`,
                    `linkedAccountId`, `sourceFinancialItemId`, `sourceSavedCreditId`, `dueIsoDate`,
                    `notes`, `createdAtEpochMs`, `updatedAtEpochMs`
                )
                SELECT
                    'loan_debt_' || `id`,
                    `userId`,
                    CASE WHEN trim(`name`) = '' THEN 'Dette' ELSE `name` END,
                    `subtitle`,
                    CASE WHEN lower(trim(`dateOrCycle`)) = 'borrowed' THEN 'LIABILITY' ELSE 'RECEIVABLE' END,
                    'PERSONAL_DEBT',
                    CASE WHEN `amountCentimes` < 0 THEN 0 ELSE `amountCentimes` END,
                    CASE WHEN (`amountCentimes` - `secondaryAmountCentimes`) < 0 THEN 0 ELSE (`amountCentimes` - `secondaryAmountCentimes`) END,
                    0,
                    CASE WHEN `amountCentimes` <= `secondaryAmountCentimes` THEN 'PAID_OFF' ELSE 'ACTIVE' END,
                    NULL,
                    `id`,
                    NULL,
                    NULL,
                    '',
                    `createdAtEpochMs`,
                    `updatedAtEpochMs`
                FROM `financial_items`
                WHERE `itemType` = 'debt'
                """.trimIndent()
            )
        }
    }
}
