package com.example.data.local

import com.example.data.AccountItem
import com.example.data.DefaultCategoryResolver
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.domain.PayCycleEngine
import com.google.firebase.Timestamp
import com.masroufi.credit.data.SavedCreditEntity
import java.util.Date

internal object MasroufiEntityMappers {

    private val ID_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_\\-]")

    fun sanitizeId(raw: String, fallbackPrefix: String, nowMs: Long = System.currentTimeMillis()): String {
        val cleaned = raw.replace(ID_SANITIZE_REGEX, "_")
        return if (cleaned.isNotBlank()) cleaned else "${fallbackPrefix}_$nowMs"
    }

    private fun timestampToEpochMs(ts: Timestamp?): Long {
        return ts?.toDate()?.time ?: 0L
    }

    private fun epochMsToTimestamp(epochMs: Long): Timestamp? {
        return if (epochMs > 0L) Timestamp(Date(epochMs)) else null
    }

    fun UserProfile.toEntity(uid: String, nowMs: Long = System.currentTimeMillis()): UserProfileEntity {
        val createdMs = timestampToEpochMs(createdAt).takeIf { it > 0L } ?: nowMs
        val updatedMs = timestampToEpochMs(updatedAt).takeIf { it > 0L } ?: nowMs
        return UserProfileEntity(
            userId = uid,
            baseBudget = baseBudget.coerceIn(0.0, 100_000_000.0),
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
        baseBudget = baseBudget,
        payCycleDay = payCycleDay.coerceIn(1, 28),
        rolloverEnabled = rolloverEnabled,
        language = language,
        isDarkTheme = isDarkTheme,
        seeded = seeded,
        createdAt = epochMsToTimestamp(createdAtEpochMs),
        updatedAt = epochMsToTimestamp(updatedAtEpochMs)
    )

    fun AccountItem.toEntity(
        uid: String,
        sortOrder: Int = 0,
        nowMs: Long = System.currentTimeMillis()
    ): AccountEntity {
        val safeId = sanitizeId(id, "acc", nowMs)
        val safeKind = if (kind in listOf("cash", "bank", "wallet")) kind else "cash"
        val createdMs = timestampToEpochMs(createdAt).takeIf { it > 0L } ?: nowMs
        val updatedMs = timestampToEpochMs(updatedAt).takeIf { it > 0L } ?: nowMs
        return AccountEntity(
            id = safeId,
            userId = uid,
            name = name.ifBlank { "Compte" }.take(80),
            kind = safeKind,
            currency = "MAD",
            initialBalance = initialBalance.coerceIn(-100_000_000.0, 100_000_000.0),
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
        initialBalance = initialBalance,
        colorHex = colorHex,
        createdAt = epochMsToTimestamp(createdAtEpochMs),
        updatedAt = epochMsToTimestamp(updatedAtEpochMs)
    )

    fun TransactionItem.toEntity(uid: String, nowMs: Long = System.currentTimeMillis()): TransactionEntity {
        val safeId = sanitizeId(id, "tx", nowMs)
        val safeType = if (type in listOf("expense", "income", "transfer")) type else "expense"
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
            else -> ""
        }
        val normalizedCat = DefaultCategoryResolver.normalizeStoredCategoryValue(category, envelopeKey).take(120)
        val normalizedEnvKey = (
            DefaultCategoryResolver.canonicalDefaultId(envelopeKey)
                ?: DefaultCategoryResolver.canonicalDefaultId(category)
                ?: envelopeKey.ifBlank { "autre" }
            ).take(60)
        val createdMs = timestampToEpochMs(createdAt).takeIf { it > 0L } ?: nowMs
        val updatedMs = timestampToEpochMs(updatedAt).takeIf { it > 0L } ?: nowMs
        return TransactionEntity(
            id = safeId,
            userId = uid,
            type = safeType,
            amount = amount.coerceIn(0.01, 100_000_000.0),
            category = normalizedCat,
            envelopeKey = normalizedEnvKey,
            accountId = safeAccountId,
            toAccountId = safeToAccountId,
            desc = desc.take(250),
            isoDate = validatedDate.take(20),
            hourOfDay = if (hourOfDay in 0..23) hourOfDay else -1,
            createdAtEpochMs = createdMs,
            updatedAtEpochMs = updatedMs
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
            amount = amount,
            category = normCat,
            envelopeKey = normEnv,
            accountId = accountId,
            toAccountId = toAccountId,
            desc = desc,
            isoDate = isoDate,
            hourOfDay = hourOfDay,
            createdAt = epochMsToTimestamp(createdAtEpochMs),
            updatedAt = epochMsToTimestamp(updatedAtEpochMs)
        )
    }

    fun EnvelopeConfig.toEntity(uid: String, nowMs: Long = System.currentTimeMillis()): EnvelopeEntity {
        val migrated = migratedStorageCopy()
        val safeId = sanitizeId(migrated.id, "env", nowMs)
        val safeName = migrated.nameFr.ifBlank { migrated.key.ifBlank { "custom" } }.take(100)
        val createdMs = timestampToEpochMs(migrated.createdAt).takeIf { it > 0L } ?: nowMs
        val updatedMs = timestampToEpochMs(migrated.updatedAt).takeIf { it > 0L } ?: nowMs
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
        createdAt = epochMsToTimestamp(createdAtEpochMs),
        updatedAt = epochMsToTimestamp(updatedAtEpochMs)
    ).migratedStorageCopy()

    fun FinancialItem.toEntity(uid: String, nowMs: Long = System.currentTimeMillis()): FinancialItemEntity {
        val allowedTypes = listOf("goal", "bill", "debt", "daret", "seasonal", "preset", "custom_prefs")
        val safeType = if (itemType in allowedTypes) itemType else "goal"
        val safeId = sanitizeId(id, "item", nowMs)
        val createdMs = timestampToEpochMs(createdAt).takeIf { it > 0L } ?: nowMs
        val updatedMs = timestampToEpochMs(updatedAt).takeIf { it > 0L } ?: nowMs
        return FinancialItemEntity(
            id = safeId,
            userId = uid,
            itemType = safeType,
            name = name.ifBlank { "Élément" }.take(120),
            subtitle = subtitle.take(250),
            amount = amount.coerceIn(0.0, 100_000_000.0),
            secondaryAmount = secondaryAmount.coerceIn(0.0, 100_000_000.0),
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
        amount = amount,
        secondaryAmount = secondaryAmount,
        dayOrTurn = dayOrTurn,
        extraInt = extraInt,
        dateOrCycle = dateOrCycle,
        createdAt = epochMsToTimestamp(createdAtEpochMs),
        updatedAt = epochMsToTimestamp(updatedAtEpochMs)
    )

    fun SavedCreditEntity.toLocalEntity(uid: String, nowMs: Long = System.currentTimeMillis()): SavedCreditLocalEntity {
        val safeId = sanitizeId(id, "credit", nowMs)
        val createdMs = timestampToEpochMs(createdAt).takeIf { it > 0L } ?: nowMs
        return SavedCreditLocalEntity(
            id = safeId,
            userId = uid,
            type = type.ifBlank { "CONSOMMATION" },
            loanAmount = loanAmount.coerceAtLeast(0.0),
            downPayment = downPayment.coerceAtLeast(0.0),
            financedPrincipal = financedPrincipal.coerceAtLeast(0.0),
            durationMonths = durationMonths.coerceAtLeast(1),
            deferredMonths = deferredMonths.coerceAtLeast(0),
            annualInterestRateHt = annualInterestRateHt.coerceIn(0.0, 50.0),
            applyInterestTva = applyInterestTva,
            insuranceRate = insuranceRate.coerceIn(0.0, 20.0),
            monthlyPayment = monthlyPayment.coerceAtLeast(0.0),
            totalCost = totalCost.coerceAtLeast(0.0),
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
        loanAmount = loanAmount,
        downPayment = downPayment,
        financedPrincipal = financedPrincipal,
        durationMonths = durationMonths,
        deferredMonths = deferredMonths,
        annualInterestRateHt = annualInterestRateHt,
        applyInterestTva = applyInterestTva,
        insuranceRate = insuranceRate,
        monthlyPayment = monthlyPayment,
        totalCost = totalCost,
        debtToIncomeRatio = debtToIncomeRatio,
        maxAllowedDti = maxAllowedDti,
        linkToMonthlyExpenses = linkToMonthlyExpenses,
        createdAt = epochMsToTimestamp(createdAtEpochMs)
    )
}
