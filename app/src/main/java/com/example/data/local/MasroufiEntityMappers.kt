package com.example.data.local

import com.example.data.AccountItem
import com.example.data.DefaultCategoryResolver
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.domain.LoanCategory
import com.example.domain.LoanDirection
import com.example.domain.LoanItem
import com.example.domain.LoanRepaymentItem
import com.example.domain.LoanStatus
import com.example.domain.PayCycleEngine
import com.example.domain.RecurrenceDirection
import com.example.domain.RecurrenceFrequency
import com.example.domain.RecurrencePostingMode
import com.example.domain.RecurrenceScheduleEngine
import com.example.domain.RecurringRuleItem
import com.masroufi.core.Money
import com.masroufi.credit.data.SavedCreditEntity

internal object MasroufiEntityMappers {

    private val ID_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_\\-]")

    fun sanitizeId(raw: String, fallbackPrefix: String, nowMs: Long = System.currentTimeMillis()): String {
        val cleaned = raw.replace(ID_SANITIZE_REGEX, "_")
        return if (cleaned.isNotBlank()) cleaned else "${fallbackPrefix}_$nowMs"
    }

    fun UserProfile.toEntity(uid: String, nowMs: Long = System.currentTimeMillis()): UserProfileEntity {
        val createdMs = createdAtEpochMs.takeIf { it > 0L } ?: nowMs
        val updatedMs = updatedAtEpochMs.takeIf { it > 0L } ?: nowMs
        return UserProfileEntity(
            userId = uid,
            baseBudgetCentimes = baseBudget.coerceIn(Money.ZERO, Money.MAX_STORABLE).centimes,
            payCycleDay = payCycleDay.coerceIn(1, 28),
            rolloverEnabled = rolloverEnabled,
            language = if (language in listOf("fr", "ar", "darija", "en")) language else "fr",
            isDarkTheme = isDarkTheme,
            seeded = seeded,
            createdAtEpochMs = createdMs,
            updatedAtEpochMs = updatedMs
        )
    }

    fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
        id = userId,
        userId = userId,
        baseBudget = Money.fromCentimes(baseBudgetCentimes),
        payCycleDay = payCycleDay.coerceIn(1, 28),
        rolloverEnabled = rolloverEnabled,
        language = language,
        isDarkTheme = isDarkTheme,
        seeded = seeded,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )

    fun AccountItem.toEntity(
        uid: String,
        sortOrder: Int = 0,
        nowMs: Long = System.currentTimeMillis()
    ): AccountEntity {
        val safeId = sanitizeId(id, "acc", nowMs)
        val safeKind = if (kind in listOf("cash", "bank", "wallet")) kind else "cash"
        val createdMs = createdAtEpochMs.takeIf { it > 0L } ?: nowMs
        val updatedMs = updatedAtEpochMs.takeIf { it > 0L } ?: nowMs
        return AccountEntity(
            id = safeId,
            userId = uid,
            name = name.ifBlank { "Compte" }.take(80),
            kind = safeKind,
            currency = "MAD",
            initialBalanceCentimes = initialBalance.coerceIn(Money.MIN_STORABLE, Money.MAX_STORABLE).centimes,
            colorHex = colorHex.ifBlank { "#10B981" }.take(20),
            isArchived = false,
            sortOrder = sortOrder,
            createdAtEpochMs = createdMs,
            updatedAtEpochMs = updatedMs
        )
    }

    fun AccountEntity.toDomain(): AccountItem = AccountItem(
        id = id,
        userId = userId,
        name = name,
        kind = kind,
        initialBalance = Money.fromCentimes(initialBalanceCentimes),
        colorHex = colorHex,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )

    fun TransactionItem.toEntity(uid: String, nowMs: Long = System.currentTimeMillis()): TransactionEntity {
        val safeId = sanitizeId(id, "tx", nowMs)
        val safeType = if (type in listOf("expense", "income", "transfer", "transfer_out", "transfer_in")) {
            type
        } else {
            "expense"
        }
        val validatedDate = PayCycleEngine.requireOrNormalizeIsoDate(isoDate)
        val safeAccountId = accountId.ifBlank { "acc_cash" }.take(80)
        val safeToAccountId = when (safeType) {
            "transfer" -> {
                val candidate = toAccountId.trim().take(80)
                if (candidate.isNotBlank() && candidate != safeAccountId) {
                    candidate
                } else {
                    if (safeAccountId == "acc_cash") "acc_bank" else "acc_cash"
                }
            }
            "transfer_out", "transfer_in" -> toAccountId.trim().take(80)
            else -> ""
        }
        val normalizedCat = DefaultCategoryResolver.normalizeStoredCategoryValue(category, envelopeKey).take(120)
        val normalizedEnvKey = (
            DefaultCategoryResolver.canonicalDefaultId(envelopeKey)
                ?: DefaultCategoryResolver.canonicalDefaultId(category)
                ?: envelopeKey.ifBlank { "autre" }
            ).take(60)
        val createdMs = createdAtEpochMs.takeIf { it > 0L } ?: nowMs
        val updatedMs = updatedAtEpochMs.takeIf { it > 0L } ?: nowMs
        val cleanTransferGroup = transferGroupId?.trim()?.takeIf { it.isNotBlank() }?.take(128)
        val cleanLoanId = loanId?.trim()?.takeIf { it.isNotBlank() }?.take(128)
        val cleanOccurrenceKey = recurringOccurrenceKey?.trim()?.takeIf { it.isNotBlank() }?.take(160)
        return TransactionEntity(
            id = safeId,
            userId = uid,
            type = safeType,
            amountCentimes = amount.coerceIn(Money.ONE_CENTIME, Money.MAX_STORABLE).centimes,
            category = normalizedCat,
            envelopeKey = normalizedEnvKey,
            accountId = safeAccountId,
            toAccountId = safeToAccountId,
            desc = desc.take(250),
            isoDate = validatedDate.take(20),
            hourOfDay = if (hourOfDay in 0..23) hourOfDay else -1,
            createdAtEpochMs = createdMs,
            updatedAtEpochMs = updatedMs,
            transferGroupId = cleanTransferGroup,
            loanId = cleanLoanId,
            recurringOccurrenceKey = cleanOccurrenceKey
        )
    }

    fun TransactionEntity.toDomain(): TransactionItem {
        val normCat = DefaultCategoryResolver.normalizeStoredCategoryValue(category, envelopeKey)
        val normEnv = DefaultCategoryResolver.canonicalDefaultId(envelopeKey)
            ?: DefaultCategoryResolver.canonicalDefaultId(category)
            ?: envelopeKey
        return TransactionItem(
            id = id,
            userId = userId,
            type = type,
            amount = Money.fromCentimes(amountCentimes),
            category = normCat,
            envelopeKey = normEnv,
            accountId = accountId,
            toAccountId = toAccountId,
            desc = desc,
            isoDate = isoDate,
            hourOfDay = hourOfDay,
            transferGroupId = transferGroupId,
            loanId = loanId,
            recurringOccurrenceKey = recurringOccurrenceKey,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    fun EnvelopeConfig.toEntity(uid: String, nowMs: Long = System.currentTimeMillis()): EnvelopeEntity {
        val migrated = migratedStorageCopy()
        val safeId = sanitizeId(migrated.id, "env", nowMs)
        val safeName = migrated.nameFr.ifBlank { migrated.key.ifBlank { "custom" } }.take(100)
        val createdMs = migrated.createdAtEpochMs.takeIf { it > 0L } ?: nowMs
        val updatedMs = migrated.updatedAtEpochMs.takeIf { it > 0L } ?: nowMs
        return EnvelopeEntity(
            id = safeId,
            userId = uid,
            key = migrated.key.ifBlank { "custom" }.take(60),
            nameFr = safeName,
            nameAr = migrated.nameAr.ifBlank { safeName }.take(100),
            nameDarija = migrated.nameDarija.ifBlank { safeName }.take(100),
            nameEn = migrated.nameEn.ifBlank { safeName }.take(100),
            percentage = migrated.percentage.coerceIn(0.0, 100.0),
            hasCap = migrated.hasCap,
            colorHex = migrated.colorHex.ifBlank { "#10B981" }.take(20),
            createdAtEpochMs = createdMs,
            updatedAtEpochMs = updatedMs
        )
    }

    fun EnvelopeEntity.toDomain(): EnvelopeConfig = EnvelopeConfig(
        id = id,
        userId = userId,
        key = key,
        nameFr = nameFr,
        nameAr = nameAr,
        nameDarija = nameDarija,
        nameEn = nameEn,
        percentage = percentage,
        hasCap = hasCap,
        colorHex = colorHex,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    ).migratedStorageCopy()

    fun FinancialItem.toEntity(uid: String, nowMs: Long = System.currentTimeMillis()): FinancialItemEntity {
        val allowedTypes = listOf("goal", "bill", "debt", "daret", "seasonal", "preset", "custom_prefs")
        val safeType = if (itemType in allowedTypes) itemType else "goal"
        val safeId = sanitizeId(id, "item", nowMs)
        val createdMs = createdAtEpochMs.takeIf { it > 0L } ?: nowMs
        val updatedMs = updatedAtEpochMs.takeIf { it > 0L } ?: nowMs
        val resolvedSecondaryCentimes = if (safeType == "daret") {
            0L
        } else {
            secondaryAmount.coerceIn(Money.ZERO, Money.MAX_STORABLE).centimes
        }
        val resolvedDaretMembers = if (safeType == "daret") {
            (daretMembers ?: 1).coerceIn(1, 366)
        } else {
            null
        }
        return FinancialItemEntity(
            id = safeId,
            userId = uid,
            itemType = safeType,
            name = name.ifBlank { "Élément" }.take(120),
            subtitle = if (safeType == "custom_prefs") subtitle.take(500) else subtitle.take(250),
            amountCentimes = amount.coerceIn(Money.ZERO, Money.MAX_STORABLE).centimes,
            secondaryAmountCentimes = resolvedSecondaryCentimes,
            daretMembers = resolvedDaretMembers,
            dayOrTurn = dayOrTurn.coerceIn(0, 366),
            extraInt = extraInt.coerceIn(0, 366),
            dateOrCycle = dateOrCycle.take(250),
            createdAtEpochMs = createdMs,
            updatedAtEpochMs = updatedMs
        )
    }

    fun FinancialItemEntity.toDomain(): FinancialItem = FinancialItem(
        id = id,
        userId = userId,
        itemType = itemType,
        name = name,
        subtitle = subtitle,
        amount = Money.fromCentimes(amountCentimes),
        secondaryAmount = if (itemType == "daret") Money.ZERO else Money.fromCentimes(secondaryAmountCentimes),
        daretMembers = if (itemType == "daret") (daretMembers ?: 1).coerceAtLeast(1) else daretMembers,
        dayOrTurn = dayOrTurn,
        extraInt = extraInt,
        dateOrCycle = dateOrCycle,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )

    fun SavedCreditEntity.toLocalEntity(uid: String, nowMs: Long = System.currentTimeMillis()): SavedCreditLocalEntity {
        val safeId = sanitizeId(id, "credit", nowMs)
        val createdMs = createdAtEpochMs.takeIf { it > 0L } ?: nowMs
        return SavedCreditLocalEntity(
            id = safeId,
            userId = uid,
            type = type.ifBlank { "CONSOMMATION" },
            loanAmountCentimes = loanAmount.coerceAtLeast(Money.ZERO).centimes,
            downPaymentCentimes = downPayment.coerceAtLeast(Money.ZERO).centimes,
            financedPrincipalCentimes = financedPrincipal.coerceAtLeast(Money.ZERO).centimes,
            durationMonths = durationMonths.coerceAtLeast(1),
            deferredMonths = deferredMonths.coerceAtLeast(0),
            annualInterestRateHt = annualInterestRateHt.coerceIn(0.0, 50.0),
            applyInterestTva = applyInterestTva,
            insuranceRate = insuranceRate.coerceIn(0.0, 20.0),
            monthlyPaymentCentimes = monthlyPayment.coerceAtLeast(Money.ZERO).centimes,
            totalCostCentimes = totalCost.coerceAtLeast(Money.ZERO).centimes,
            debtToIncomeRatio = debtToIncomeRatio.coerceAtLeast(0.0),
            maxAllowedDti = maxAllowedDti,
            linkToMonthlyExpenses = linkToMonthlyExpenses,
            createdAtEpochMs = createdMs
        )
    }

    fun SavedCreditLocalEntity.toDomain(): SavedCreditEntity = SavedCreditEntity(
        id = id,
        userId = userId,
        type = type,
        loanAmount = Money.fromCentimes(loanAmountCentimes),
        downPayment = Money.fromCentimes(downPaymentCentimes),
        financedPrincipal = Money.fromCentimes(financedPrincipalCentimes),
        durationMonths = durationMonths,
        deferredMonths = deferredMonths,
        annualInterestRateHt = annualInterestRateHt,
        applyInterestTva = applyInterestTva,
        insuranceRate = insuranceRate,
        monthlyPayment = Money.fromCentimes(monthlyPaymentCentimes),
        totalCost = Money.fromCentimes(totalCostCentimes),
        debtToIncomeRatio = debtToIncomeRatio,
        maxAllowedDti = maxAllowedDti,
        linkToMonthlyExpenses = linkToMonthlyExpenses,
        createdAtEpochMs = createdAtEpochMs
    )

    fun LoanItem.toEntity(uid: String, nowMs: Long = System.currentTimeMillis()): LoanEntity {
        val safeId = sanitizeId(id, "loan", nowMs)
        val safePrincipal = principal.coerceIn(Money.ZERO, Money.MAX_STORABLE).centimes
        val safeRemaining = remaining.coerceIn(Money.ZERO, Money.fromCentimes(safePrincipal)).centimes
        val safeMonthly = monthlyPayment.coerceIn(Money.ZERO, Money.MAX_STORABLE).centimes
        val effectiveStatus = if (safeRemaining == 0L || status == LoanStatus.PAID_OFF) {
            LoanStatus.PAID_OFF
        } else {
            LoanStatus.ACTIVE
        }
        val createdMs = createdAtEpochMs.takeIf { it > 0L } ?: nowMs
        val updatedMs = updatedAtEpochMs.takeIf { it > 0L } ?: nowMs
        return LoanEntity(
            id = safeId,
            userId = uid,
            title = title.trim().ifBlank { "Prêt" }.take(120),
            counterparty = counterparty.trim().take(120),
            direction = direction.wire,
            category = category.wire,
            principalCentimes = safePrincipal,
            remainingCentimes = if (effectiveStatus == LoanStatus.PAID_OFF) 0L else safeRemaining,
            monthlyPaymentCentimes = safeMonthly,
            status = effectiveStatus.wire,
            linkedAccountId = linkedAccountId?.trim()?.takeIf { it.isNotBlank() }?.take(80),
            sourceFinancialItemId = sourceFinancialItemId?.trim()?.takeIf { it.isNotBlank() }?.take(128),
            sourceSavedCreditId = sourceSavedCreditId?.trim()?.takeIf { it.isNotBlank() }?.take(128),
            dueIsoDate = dueIsoDate?.trim()?.takeIf { it.isNotBlank() }?.take(20),
            notes = notes.trim().take(250),
            createdAtEpochMs = createdMs,
            updatedAtEpochMs = updatedMs
        )
    }

    fun LoanEntity.toDomain(): LoanItem = LoanItem(
        id = id,
        userId = userId,
        title = title,
        counterparty = counterparty,
        direction = LoanDirection.fromWire(direction),
        category = LoanCategory.fromWire(category),
        principal = Money.fromCentimes(principalCentimes.coerceAtLeast(0L)),
        remaining = Money.fromCentimes(remainingCentimes.coerceAtLeast(0L)),
        monthlyPayment = Money.fromCentimes(monthlyPaymentCentimes.coerceAtLeast(0L)),
        status = LoanStatus.fromWire(status),
        linkedAccountId = linkedAccountId,
        sourceFinancialItemId = sourceFinancialItemId,
        sourceSavedCreditId = sourceSavedCreditId,
        dueIsoDate = dueIsoDate,
        notes = notes,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )

    fun LoanRepaymentItem.toEntity(uid: String, nowMs: Long = System.currentTimeMillis()): LoanRepaymentEntity {
        val safeId = sanitizeId(id, "repay", nowMs)
        val validatedIso = PayCycleEngine.requireOrNormalizeIsoDate(isoDate)
        return LoanRepaymentEntity(
            id = safeId,
            loanId = loanId.trim().take(128),
            userId = uid,
            amountCentimes = amount.coerceIn(Money.ONE_CENTIME, Money.MAX_STORABLE).centimes,
            isoDate = validatedIso.take(20),
            linkedAccountId = linkedAccountId?.trim()?.takeIf { it.isNotBlank() }?.take(80),
            linkedTransactionId = linkedTransactionId?.trim()?.takeIf { it.isNotBlank() }?.take(128),
            note = note.trim().take(250),
            createdAtEpochMs = createdAtEpochMs.takeIf { it > 0L } ?: nowMs
        )
    }

    fun LoanRepaymentEntity.toDomain(): LoanRepaymentItem = LoanRepaymentItem(
        id = id,
        loanId = loanId,
        userId = userId,
        amount = Money.fromCentimes(amountCentimes.coerceAtLeast(0L)),
        isoDate = isoDate,
        linkedAccountId = linkedAccountId,
        linkedTransactionId = linkedTransactionId,
        note = note,
        createdAtEpochMs = createdAtEpochMs
    )

    fun RecurringRuleItem.toEntity(uid: String, nowMs: Long = System.currentTimeMillis()): RecurringRuleEntity {
        val safeId = sanitizeId(id, "rec", nowMs)
        val safeStart = if (PayCycleEngine.isValidIsoDate(startIsoDate)) startIsoDate.trim() else PayCycleEngine.todayIso()
        val safeNext = if (PayCycleEngine.isValidIsoDate(nextDueIsoDate)) nextDueIsoDate.trim() else safeStart
        val safeEnd = endIsoDate?.trim()?.takeIf { PayCycleEngine.isValidIsoDate(it) }
        val resolvedAnchor = if (anchorDayOfMonth in 1..31) {
            anchorDayOfMonth
        } else {
            RecurrenceScheduleEngine.parseIsoParts(safeStart)?.third ?: 1
        }
        val cleanSkipped = skippedOccurrenceKeys
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .takeLast(64)
            .joinToString(",")
        return RecurringRuleEntity(
            id = safeId,
            userId = uid,
            title = title.trim().ifBlank { "Récurrence" }.take(120),
            direction = direction.wire,
            amountCentimes = amount.coerceIn(Money.ONE_CENTIME, Money.MAX_STORABLE).centimes,
            frequency = frequency.wire,
            anchorDayOfMonth = resolvedAnchor.coerceIn(1, 31),
            startIsoDate = safeStart.take(20),
            nextDueIsoDate = safeNext.take(20),
            endIsoDate = safeEnd?.take(20),
            postingMode = postingMode.wire,
            accountId = accountId.trim().ifBlank { "acc_cash" }.take(80),
            envelopeKey = envelopeKey.trim().ifBlank { "charges" }.take(60),
            category = category.trim().ifBlank { "charges" }.take(120),
            linkedLoanId = linkedLoanId?.trim()?.takeIf { it.isNotBlank() }?.take(128),
            sourceFinancialItemId = sourceFinancialItemId?.trim()?.takeIf { it.isNotBlank() }?.take(128),
            isPaused = isPaused,
            lastPostedOccurrenceKey = lastPostedOccurrenceKey?.trim()?.takeIf { it.isNotBlank() }?.take(160),
            skippedOccurrenceKeys = cleanSkipped.take(1500),
            createdAtEpochMs = createdAtEpochMs.takeIf { it > 0L } ?: nowMs,
            updatedAtEpochMs = updatedAtEpochMs.takeIf { it > 0L } ?: nowMs
        )
    }

    fun RecurringRuleEntity.toDomain(): RecurringRuleItem = RecurringRuleItem(
        id = id,
        userId = userId,
        title = title,
        direction = RecurrenceDirection.fromWire(direction),
        amount = Money.fromCentimes(amountCentimes.coerceAtLeast(0L)),
        frequency = RecurrenceFrequency.fromWire(frequency),
        anchorDayOfMonth = anchorDayOfMonth.coerceIn(1, 31),
        startIsoDate = startIsoDate,
        nextDueIsoDate = nextDueIsoDate,
        endIsoDate = endIsoDate,
        postingMode = RecurrencePostingMode.fromWire(postingMode),
        accountId = accountId.ifBlank { "acc_cash" },
        envelopeKey = envelopeKey.ifBlank { "charges" },
        category = category.ifBlank { "charges" },
        linkedLoanId = linkedLoanId,
        sourceFinancialItemId = sourceFinancialItemId,
        isPaused = isPaused,
        lastPostedOccurrenceKey = lastPostedOccurrenceKey,
        skippedOccurrenceKeys = skippedOccurrenceKeys.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet(),
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )
}
