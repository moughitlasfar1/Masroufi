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

    /**
     * Lossless Room v3 -> v4 migration:
     * 1. Adds nullable `recurringOccurrenceKey` column + UNIQUE index to `transactions`
     *    to guarantee idempotent recurring posting at the SQLite constraint level.
     * 2. Creates `recurring_rules` table + indices using exact INTEGER centimes.
     * 3. Backfills existing `financial_items` (`itemType = 'bill'`) into `recurring_rules`
     *    with `postingMode = 'CONFIRM'` and `sourceFinancialItemId = financial_items.id`
     *    so legacy bills are upgraded cleanly without auto-charging or double-counting.
     */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Add nullable recurringOccurrenceKey + UNIQUE index on transactions
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `recurringOccurrenceKey` TEXT")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_recurringOccurrenceKey` ON `transactions` (`recurringOccurrenceKey`)"
            )

            // 2. Create recurring_rules table + indices
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recurring_rules` (
                    `id` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `direction` TEXT NOT NULL,
                    `amountCentimes` INTEGER NOT NULL,
                    `frequency` TEXT NOT NULL,
                    `anchorDayOfMonth` INTEGER NOT NULL,
                    `startIsoDate` TEXT NOT NULL,
                    `nextDueIsoDate` TEXT NOT NULL,
                    `endIsoDate` TEXT,
                    `postingMode` TEXT NOT NULL,
                    `accountId` TEXT NOT NULL,
                    `envelopeKey` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `linkedLoanId` TEXT,
                    `sourceFinancialItemId` TEXT,
                    `isPaused` INTEGER NOT NULL,
                    `lastPostedOccurrenceKey` TEXT,
                    `skippedOccurrenceKeys` TEXT NOT NULL,
                    `createdAtEpochMs` INTEGER NOT NULL,
                    `updatedAtEpochMs` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_userId` ON `recurring_rules` (`userId`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_rules_userId_isPaused_nextDueIsoDate` ON `recurring_rules` (`userId`, `isPaused`, `nextDueIsoDate`)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_linkedLoanId` ON `recurring_rules` (`linkedLoanId`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_rules_sourceFinancialItemId` ON `recurring_rules` (`sourceFinancialItemId`)"
            )

            // 3. Zero-loss backfill of existing legacy monthly bills into recurring_rules
            val todayIso = com.example.domain.PayCycleEngine.todayIso()
            val todayParts = com.example.domain.RecurrenceScheduleEngine.parseIsoParts(todayIso)
            val baseYear = todayParts?.first ?: 2026
            val baseMonth = todayParts?.second ?: 1
            val currentYmKey = String.format(java.util.Locale.US, "%04d-%02d", baseYear, baseMonth)

            data class LegacyBillRow(
                val id: String,
                val userId: String,
                val name: String,
                val subtitle: String,
                val amountCentimes: Long,
                val dayOrTurn: Int,
                val dateOrCycle: String,
                val createdAtEpochMs: Long,
                val updatedAtEpochMs: Long
            )

            val legacyBills = mutableListOf<LegacyBillRow>()
            db.query(
                """
                SELECT `id`, `userId`, `name`, `subtitle`, `amountCentimes`, `dayOrTurn`,
                       `dateOrCycle`, `createdAtEpochMs`, `updatedAtEpochMs`
                FROM `financial_items`
                WHERE `itemType` = 'bill'
                """.trimIndent()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    legacyBills.add(
                        LegacyBillRow(
                            id = cursor.getString(0) ?: continue,
                            userId = cursor.getString(1) ?: "",
                            name = cursor.getString(2) ?: "",
                            subtitle = cursor.getString(3) ?: "",
                            amountCentimes = cursor.getLong(4),
                            dayOrTurn = cursor.getInt(5),
                            dateOrCycle = cursor.getString(6) ?: "",
                            createdAtEpochMs = cursor.getLong(7),
                            updatedAtEpochMs = cursor.getLong(8)
                        )
                    )
                }
            }

            if (legacyBills.isNotEmpty()) {
                // Resolve each user's first available accountId (falling back to 'acc_cash')
                val userDefaultAccount = mutableMapOf<String, String>()
                db.query("SELECT `userId`, `id` FROM `accounts` ORDER BY `sortOrder` ASC, `createdAtEpochMs` ASC").use { c ->
                    while (c.moveToNext()) {
                        val uid = c.getString(0) ?: continue
                        val accId = c.getString(1) ?: continue
                        if (uid !in userDefaultAccount) {
                            userDefaultAccount[uid] = accId
                        }
                    }
                }

                for (bill in legacyBills) {
                    if (bill.amountCentimes <= 0L || bill.userId.isBlank()) continue
                    val anchorDay = bill.dayOrTurn.coerceIn(1, 31)
                    val paidCycles = bill.dateOrCycle.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                    val currentMonthMaxDay = com.example.domain.PayCycleEngine.daysInMonth(baseYear, baseMonth)
                    val currentMonthDue = com.example.domain.RecurrenceScheduleEngine.formatIso(
                        baseYear,
                        baseMonth,
                        anchorDay.coerceAtMost(currentMonthMaxDay)
                    )
                    val nextDueIso = if (currentYmKey in paidCycles) {
                        com.example.domain.RecurrenceScheduleEngine.advanceDate(
                            currentMonthDue,
                            com.example.domain.RecurrenceFrequency.MONTHLY,
                            anchorDay
                        )
                    } else {
                        currentMonthDue
                    }
                    val envKey = bill.subtitle.trim().takeIf { it.isNotBlank() }?.take(60) ?: "charges"
                    val resolvedAcc = userDefaultAccount[bill.userId] ?: "acc_cash"
                    val title = bill.name.trim().ifBlank { "Facture" }.take(120)
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `recurring_rules` (
                            `id`, `userId`, `title`, `direction`, `amountCentimes`, `frequency`,
                            `anchorDayOfMonth`, `startIsoDate`, `nextDueIsoDate`, `endIsoDate`,
                            `postingMode`, `accountId`, `envelopeKey`, `category`,
                            `linkedLoanId`, `sourceFinancialItemId`, `isPaused`,
                            `lastPostedOccurrenceKey`, `skippedOccurrenceKeys`,
                            `createdAtEpochMs`, `updatedAtEpochMs`
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf<Any?>(
                            "rec_bill_${bill.id}",
                            bill.userId,
                            title,
                            "EXPENSE",
                            bill.amountCentimes,
                            "MONTHLY",
                            anchorDay,
                            currentMonthDue,
                            nextDueIso,
                            null,
                            "CONFIRM",
                            resolvedAcc,
                            envKey,
                            envKey,
                            null,
                            bill.id,
                            0,
                            null,
                            "",
                            bill.createdAtEpochMs,
                            bill.updatedAtEpochMs
                        )
                    )
                }
            }
        }
    }
}
