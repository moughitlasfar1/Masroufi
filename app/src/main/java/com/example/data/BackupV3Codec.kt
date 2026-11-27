package com.example.data

import com.example.BuildConfig
import com.example.domain.LoanCategory
import com.example.domain.LoanDirection
import com.example.domain.LoanItem
import com.example.domain.LoanRepaymentItem
import com.example.domain.LoanStatus
import com.example.domain.PayCycleEngine
import com.example.ui.DashboardState
import com.example.ui.ParsedBackupPayload
import com.google.firebase.Timestamp
import com.masroufi.core.Money
import com.masroufi.credit.data.SavedCreditEntity
import com.masroufi.fiscal.domain.AnnualBillItem
import com.masroufi.fiscal.domain.AnnualBillPreset
import com.masroufi.fiscal.domain.FiscalRates
import com.masroufi.fiscal.domain.NisabStandard
import com.masroufi.fiscal.domain.ZakatConfig
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

/**
 * Off-Room fiscal configuration snapshot bundled inside Backup v3 payloads.
 */
data class BackupFiscalSnapshot(
    val annualBills: List<AnnualBillItem> = emptyList(),
    val zakatConfig: ZakatConfig? = null
)

/**
 * Thrown when a backup payload declares an unsupported backupVersion.
 */
class UnsupportedBackupVersionException(val detectedVersion: Int) :
    IllegalArgumentException("Version de sauvegarde non prise en charge (v$detectedVersion).")

/**
 * Backup Payload v3 encoder/decoder.
 *
 * Guarantees:
 * - Encodes ALL Room v2 user tables (`user_profiles`, `accounts`, `envelopes`, `transactions`,
 *   `financial_items` including `daretMembers`, and `saved_credits`), plus `customPrefs`,
 *   `learnedCategories`, and `FiscalRepository` (`annualBills` and `zakatConfig`).
 * - Every monetary field is serialized and deserialized strictly as exact integer `Long` centimes
 *   (`*Centimes` keys) with ZERO `Double` conversion.
 * - Includes integrity `metadata` (`exportedAtEpochMillis`, `exportedAtIso`, `appVersion`, and
 *   `recordCounts`) validated strictly before committing to Room.
 */
object BackupV3Codec {
    const val BACKUP_VERSION_V3 = 3
    private const val MAX_BACKUP_ITEMS = 5_000
    private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")
    private val ALLOWED_LANGUAGES = setOf("fr", "ar", "darija", "en")
    private val ALLOWED_ACCOUNT_KINDS = setOf("cash", "bank", "wallet")
    private val ALLOWED_ITEM_TYPES = setOf("goal", "bill", "debt", "daret", "seasonal", "preset", "custom_prefs")
    private val ALLOWED_TX_TYPES = setOf("expense", "income", "transfer", "transfer_out", "transfer_in")
    private val ALLOWED_LOAN_DIRECTIONS = setOf("LIABILITY", "RECEIVABLE")
    private val ALLOWED_LOAN_CATEGORIES = setOf("BANK_CREDIT", "PERSONAL_DEBT", "DARET")
    private val ALLOWED_LOAN_STATUSES = setOf("ACTIVE", "PAID_OFF")

    fun encodeV3Backup(
        state: DashboardState,
        savedCredits: List<SavedCreditEntity> = emptyList(),
        fiscal: BackupFiscalSnapshot? = null,
        appVersion: String = BuildConfig.VERSION_NAME,
        exportedAtEpochMillis: Long = System.currentTimeMillis(),
        exportedAtIso: String = PayCycleEngine.todayIso(),
        loans: List<LoanItem> = state.loans,
        loanRepayments: List<LoanRepaymentItem> = state.loanRepayments
    ): String {
        val allFinItems = state.presets + state.goals + state.bills + state.debts + state.darets + state.seasonals
        val annualBills = fiscal?.annualBills.orEmpty()

        val root = JSONObject().apply {
            put("backupVersion", BACKUP_VERSION_V3)
            put("exportedAtEpochMillis", exportedAtEpochMillis)
            put("exportedAtIso", exportedAtIso)
            put("appVersion", appVersion)

            put(
                "metadata",
                JSONObject().apply {
                    put("exportedAtEpochMillis", exportedAtEpochMillis)
                    put("exportedAtIso", exportedAtIso)
                    put("appVersion", appVersion)
                    put(
                        "recordCounts",
                        JSONObject().apply {
                            put("accounts", state.accounts.size)
                            put("envelopes", state.envelopes.size)
                            put("financialItems", allFinItems.size)
                            put("transactions", state.allTransactions.size)
                            put("savedCredits", savedCredits.size)
                            put("annualBills", annualBills.size)
                            put("loans", loans.size)
                            put("loanRepayments", loanRepayments.size)
                        }
                    )
                }
            )

            put(
                "profile",
                JSONObject().apply {
                    put("baseBudgetCentimes", state.profile.baseBudget.centimes)
                    put("payCycleDay", state.profile.payCycleDay)
                    put("rolloverEnabled", state.profile.rolloverEnabled)
                    put("language", state.profile.language)
                    put("isDarkTheme", state.profile.isDarkTheme)
                }
            )

            put(
                "customPrefs",
                JSONObject().apply {
                    put("accentPalette", state.customPrefs.accentPalette)
                    put("currencySymbol", state.customPrefs.currencySymbol)
                    put("customTitle", state.customPrefs.customTitle)
                    put("customSubtitle", state.customPrefs.customSubtitle)
                    put("visibilityMask", state.customPrefs.toVisibilityMask())
                    put("dashboardOrder", state.customPrefs.normalizedOrder().joinToString(","))
                    put("themeMode", state.customPrefs.normalizedThemeMode())
                    put("eurToMadRate", state.customPrefs.normalizedEurRate())
                    put("usdToMadRate", state.customPrefs.normalizedUsdRate())
                }
            )

            val learnedObj = JSONObject()
            PayCycleEngine.getAllLearnedCategories().forEach { (k, v) ->
                learnedObj.put(k, v)
            }
            put("learnedCategories", learnedObj)

            val accArr = JSONArray()
            state.accounts.forEachIndexed { idx, accWithBal ->
                val a = accWithBal.account
                accArr.put(
                    JSONObject().apply {
                        put("id", a.id)
                        put("name", a.name)
                        put("kind", a.kind)
                        put("currency", "MAD")
                        put("initialBalanceCentimes", a.initialBalance.centimes)
                        put("colorHex", a.colorHex)
                        put("isArchived", false)
                        put("sortOrder", idx)
                    }
                )
            }
            put("accounts", accArr)

            val envArr = JSONArray()
            state.envelopes.forEach { envStatus ->
                val e = envStatus.config
                envArr.put(
                    JSONObject().apply {
                        put("id", e.id)
                        put("key", e.key)
                        put("nameFr", e.nameFr)
                        put("nameAr", e.nameAr)
                        put("nameDarija", e.nameDarija)
                        put("nameEn", e.nameEn)
                        put("percentage", e.percentage)
                        put("hasCap", e.hasCap)
                        put("colorHex", e.colorHex)
                    }
                )
            }
            put("envelopes", envArr)

            val itemsArr = JSONArray()
            allFinItems.forEach { item ->
                itemsArr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("itemType", item.itemType)
                        put("name", item.name)
                        put("subtitle", item.subtitle)
                        put("amountCentimes", item.amount.centimes)
                        if (item.itemType == "daret") {
                            put("secondaryAmountCentimes", 0L)
                            put("daretMembers", item.resolvedDaretMembers())
                        } else {
                            put("secondaryAmountCentimes", item.secondaryAmount.centimes)
                            if (item.daretMembers != null) {
                                put("daretMembers", item.daretMembers)
                            }
                        }
                        put("dayOrTurn", item.dayOrTurn)
                        put("extraInt", item.extraInt)
                        put("dateOrCycle", item.dateOrCycle)
                    }
                )
            }
            put("financialItems", itemsArr)

            val txArr = JSONArray()
            state.allTransactions.forEach { t ->
                txArr.put(
                    JSONObject().apply {
                        put("id", t.id)
                        put("type", t.type)
                        put("amountCentimes", t.amount.centimes)
                        put("category", t.category)
                        put("envelopeKey", t.envelopeKey)
                        put("accountId", t.accountId)
                        put("toAccountId", t.toAccountId)
                        put("desc", t.desc)
                        put("isoDate", t.isoDate)
                        if (t.hourOfDay in 0..23) {
                            put("hourOfDay", t.hourOfDay)
                        }
                        t.transferGroupId?.takeIf { it.isNotBlank() }?.let { put("transferGroupId", it) }
                        t.loanId?.takeIf { it.isNotBlank() }?.let { put("loanId", it) }
                    }
                )
            }
            put("transactions", txArr)

            val creditsArr = JSONArray()
            savedCredits.forEach { c ->
                creditsArr.put(
                    JSONObject().apply {
                        put("id", c.id)
                        put("type", c.type)
                        put("loanAmountCentimes", c.loanAmount.centimes)
                        put("downPaymentCentimes", c.downPayment.centimes)
                        put("financedPrincipalCentimes", c.financedPrincipal.centimes)
                        put("durationMonths", c.durationMonths)
                        put("deferredMonths", c.deferredMonths)
                        put("annualInterestRateHt", c.annualInterestRateHt)
                        put("applyInterestTva", c.applyInterestTva)
                        put("insuranceRate", c.insuranceRate)
                        put("monthlyPaymentCentimes", c.monthlyPayment.centimes)
                        put("totalCostCentimes", c.totalCost.centimes)
                        put("debtToIncomeRatio", c.debtToIncomeRatio)
                        put("maxAllowedDti", c.maxAllowedDti)
                        put("linkToMonthlyExpenses", c.linkToMonthlyExpenses)
                        put("createdAtEpochMs", c.createdAt?.toDate()?.time ?: 0L)
                    }
                )
            }
            put("savedCredits", creditsArr)

            val loansArr = JSONArray()
            loans.forEach { loan ->
                loansArr.put(
                    JSONObject().apply {
                        put("id", loan.id)
                        put("title", loan.title)
                        put("counterparty", loan.counterparty)
                        put("direction", loan.direction.wire)
                        put("category", loan.category.wire)
                        put("principalCentimes", loan.principal.centimes)
                        put("remainingCentimes", loan.remaining.centimes)
                        put("monthlyPaymentCentimes", loan.monthlyPayment.centimes)
                        put("status", loan.status.wire)
                        loan.linkedAccountId?.takeIf { it.isNotBlank() }?.let { put("linkedAccountId", it) }
                        loan.sourceFinancialItemId?.takeIf { it.isNotBlank() }?.let { put("sourceFinancialItemId", it) }
                        loan.sourceSavedCreditId?.takeIf { it.isNotBlank() }?.let { put("sourceSavedCreditId", it) }
                        loan.dueIsoDate?.takeIf { it.isNotBlank() }?.let { put("dueIsoDate", it) }
                        put("notes", loan.notes)
                        put("createdAtEpochMs", loan.createdAtEpochMs)
                        put("updatedAtEpochMs", loan.updatedAtEpochMs)
                    }
                )
            }
            put("loans", loansArr)

            val repaymentsArr = JSONArray()
            loanRepayments.forEach { rep ->
                repaymentsArr.put(
                    JSONObject().apply {
                        put("id", rep.id)
                        put("loanId", rep.loanId)
                        put("amountCentimes", rep.amount.centimes)
                        put("isoDate", rep.isoDate)
                        rep.linkedAccountId?.takeIf { it.isNotBlank() }?.let { put("linkedAccountId", it) }
                        rep.linkedTransactionId?.takeIf { it.isNotBlank() }?.let { put("linkedTransactionId", it) }
                        put("note", rep.note)
                        put("createdAtEpochMs", rep.createdAtEpochMs)
                    }
                )
            }
            put("loanRepayments", repaymentsArr)

            if (fiscal != null) {
                put(
                    "fiscal",
                    JSONObject().apply {
                        val billsArr = JSONArray()
                        fiscal.annualBills.forEach { b ->
                            billsArr.put(
                                JSONObject().apply {
                                    put("id", b.id)
                                    put("preset", b.preset.key)
                                    put("title", b.title)
                                    put("annualAmountCentimes", b.annualAmountMad.centimes)
                                    put("dueMonth", b.dueMonth)
                                    put("dueYear", b.dueYear)
                                    put("cvBand", b.cvBand?.key ?: "")
                                    put("fuelType", b.fuelType?.key ?: "")
                                    put("isManualVignetteAmount", b.isManualVignetteAmount)
                                    put("linkedGoalId", b.linkedGoalId)
                                    put("linkedBillId", b.linkedBillId)
                                    put("isPaid", b.isPaid)
                                    put("paidIsoDate", b.paidIsoDate)
                                    put("createdAtMillis", b.createdAtMillis)
                                }
                            )
                        }
                        put("annualBills", billsArr)

                        val z = fiscal.zakatConfig
                        if (z != null) {
                            put(
                                "zakatConfig",
                                JSONObject().apply {
                                    put("nisabStandard", z.nisabStandard.key)
                                    put("goldPricePerGramCentimes", z.goldPriceMadPerGram.centimes)
                                    put("silverPricePerGramCentimes", z.silverPriceMadPerGram.centimes)
                                    put("pricesAsOfIso", z.pricesAsOfIso)
                                    put("hawlStartIso", z.hawlStartIso)
                                    put("linkedAnnualBillId", z.linkedAnnualBillId)
                                }
                            )
                        }
                    }
                )
            }
        }
        return root.toString(2)
    }

    fun decodeV3Backup(
        obj: JSONObject,
        currentUserId: String,
        fallbackProfile: UserProfile
    ): ParsedBackupPayload {
        val version = obj.optInt("backupVersion", -1)
        if (version != BACKUP_VERSION_V3) {
            throw UnsupportedBackupVersionException(version)
        }

        val metadataObj = obj.optJSONObject("metadata")
        val exportedAtEpochMillis = metadataObj?.optLong("exportedAtEpochMillis", obj.optLong("exportedAtEpochMillis", 0L))
            ?: obj.optLong("exportedAtEpochMillis", 0L)
        val exportedAtIso = metadataObj?.optString("exportedAtIso", obj.optString("exportedAtIso", ""))
            ?: obj.optString("exportedAtIso", "")
        val appVersion = metadataObj?.optString("appVersion", obj.optString("appVersion", ""))
            ?: obj.optString("appVersion", "")

        val profileObj = obj.optJSONObject("profile")
            ?: throw IllegalArgumentException("Section 'profile' manquante dans la sauvegarde v3.")
        val rawBudget = readExactCentimes(
            profileObj,
            "baseBudgetCentimes",
            Money.ZERO.centimes,
            Money.MAX_STORABLE.centimes,
            "profile.baseBudgetCentimes"
        )
        val validBudget = rawBudget.coerceAtLeast(Money.fromDh(100))

        val rawPayDay = readExactInt(profileObj, "payCycleDay", 1, 28, "profile.payCycleDay")
        val rawLang = profileObj.optString("language", fallbackProfile.language).trim()
        require(rawLang in ALLOWED_LANGUAGES) {
            "Langue de profil non supportée ('$rawLang')."
        }

        val restoredProfile = UserProfile(
            id = currentUserId,
            userId = currentUserId,
            baseBudget = validBudget,
            payCycleDay = rawPayDay,
            rolloverEnabled = profileObj.optBoolean("rolloverEnabled", true),
            language = rawLang,
            isDarkTheme = profileObj.optBoolean("isDarkTheme", fallbackProfile.isDarkTheme),
            seeded = true
        )

        val customObj = obj.optJSONObject("customPrefs")
        val restoredCustomPrefs = if (customObj != null) {
            val mask = customObj.optInt("visibilityMask", 127)
            require(mask in 0..366) { "visibilityMask invalide ($mask)." }
            val hasInsightsBit = mask >= 64
            val orderStr = customObj.optString("dashboardOrder", "")
            CustomizationPrefs(
                accentPalette = customObj.optString("accentPalette", "majorelle").ifBlank { "majorelle" }.take(30),
                currencySymbol = customObj.optString("currencySymbol", "DH").ifBlank { "DH" }.take(10),
                customTitle = customObj.optString("customTitle", "").take(80),
                customSubtitle = customObj.optString("customSubtitle", "").take(200),
                showSafeToSpend = (mask and 1) != 0,
                showAccountsCard = (mask and 2) != 0,
                showQuickPresetsCard = (mask and 4) != 0,
                showWeekSummaryRow = (mask and 8) != 0,
                showMonthlyReviewCard = (mask and 16) != 0,
                showBillsCard = (mask and 32) != 0,
                showSmartInsightsCard = if (hasInsightsBit) (mask and 64) != 0 else true,
                dashboardOrder = CustomizationPrefs.normalizeOrderString(orderStr),
                themeMode = CustomizationPrefs.normalizeThemeMode(customObj.optString("themeMode", "system")),
                eurToMadRate = customObj.optDouble("eurToMadRate", 10.85)
                    .let { if (!it.isNaN() && !it.isInfinite() && it in 0.5..500.0) it else 10.85 },
                usdToMadRate = customObj.optDouble("usdToMadRate", 9.95)
                    .let { if (!it.isNaN() && !it.isInfinite() && it in 0.5..500.0) it else 9.95 }
            )
        } else null

        val accArr = obj.optJSONArray("accounts")
            ?: throw IllegalArgumentException("Section 'accounts' manquante dans la sauvegarde v3.")
        val envArr = obj.optJSONArray("envelopes")
            ?: throw IllegalArgumentException("Section 'envelopes' manquante dans la sauvegarde v3.")
        val itemsArr = obj.optJSONArray("financialItems")
            ?: throw IllegalArgumentException("Section 'financialItems' manquante dans la sauvegarde v3.")
        val txArr = obj.optJSONArray("transactions")
            ?: throw IllegalArgumentException("Section 'transactions' manquante dans la sauvegarde v3.")
        val creditsArr = obj.optJSONArray("savedCredits") ?: JSONArray()
        val hasLoansSection = obj.has("loans") && !obj.isNull("loans")
        val loansArr = obj.optJSONArray("loans") ?: JSONArray()
        val hasRepaymentsSection = obj.has("loanRepayments") && !obj.isNull("loanRepayments")
        val repaymentsArr = obj.optJSONArray("loanRepayments") ?: JSONArray()
        val fiscalObj = obj.optJSONObject("fiscal")
        val annualBillsArr = fiscalObj?.optJSONArray("annualBills")

        // Verify metadata.recordCounts strictly before accepting payload
        val countsObj = metadataObj?.optJSONObject("recordCounts")
        if (countsObj != null) {
            verifyCount(countsObj, "accounts", accArr.length())
            verifyCount(countsObj, "envelopes", envArr.length())
            verifyCount(countsObj, "financialItems", itemsArr.length())
            verifyCount(countsObj, "transactions", txArr.length())
            verifyCount(countsObj, "savedCredits", creditsArr.length())
            if (countsObj.has("annualBills")) {
                verifyCount(countsObj, "annualBills", annualBillsArr?.length() ?: 0)
            }
            if (countsObj.has("loans")) {
                verifyCount(countsObj, "loans", loansArr.length())
            }
            if (countsObj.has("loanRepayments")) {
                verifyCount(countsObj, "loanRepayments", repaymentsArr.length())
            }
        }

        val totalDeclaredItems = accArr.length() + envArr.length() + itemsArr.length() +
            txArr.length() + creditsArr.length() + loansArr.length() + repaymentsArr.length() +
            (annualBillsArr?.length() ?: 0)
        require(totalDeclaredItems <= MAX_BACKUP_ITEMS) {
            "Sauvegarde dépassant la limite de sécurité ($totalDeclaredItems éléments > $MAX_BACKUP_ITEMS max)."
        }

        val accountsList = mutableListOf<AccountItem>()
        for (i in 0 until accArr.length()) {
            val a = accArr.optJSONObject(i) ?: throw IllegalArgumentException("Compte #$i invalide.")
            val id = requireValidBackupId(a.optString("id", ""), "account.id")
            val name = a.optString("name", "").trim()
            require(name.length in 1..80) { "Nom du compte '$id' invalide." }
            val kind = a.optString("kind", "cash").trim()
            require(kind in ALLOWED_ACCOUNT_KINDS) { "Type de compte '$kind' invalide pour '$id'." }
            val initBal = readExactCentimes(
                a,
                "initialBalanceCentimes",
                Money.MIN_STORABLE.centimes,
                Money.MAX_STORABLE.centimes,
                "account.initialBalanceCentimes"
            )
            val colorHex = a.optString("colorHex", "#10B981").trim().ifBlank { "#10B981" }
            require(colorHex.length in 1..20) { "Couleur invalide pour le compte '$id'." }
            accountsList.add(
                AccountItem(
                    id = id,
                    userId = currentUserId,
                    name = name,
                    kind = kind,
                    initialBalance = initBal,
                    colorHex = colorHex
                )
            )
        }

        val envelopesList = mutableListOf<EnvelopeConfig>()
        for (i in 0 until envArr.length()) {
            val e = envArr.optJSONObject(i) ?: throw IllegalArgumentException("Enveloppe #$i invalide.")
            val id = requireValidBackupId(e.optString("id", ""), "envelope.id")
            val key = e.optString("key", "").trim()
            require(key.length in 1..60) { "Clé d'enveloppe invalide pour '$id'." }
            val nFr = e.optString("nameFr", "").trim()
            require(nFr.length in 1..100) { "Nom d'enveloppe (FR) invalide pour '$id'." }
            val pct = requireFiniteInRange(e.optDouble("percentage", Double.NaN), 0.0, 100.0, "envelope.percentage")
            val colorHex = e.optString("colorHex", "#10B981").trim().ifBlank { "#10B981" }
            require(colorHex.length in 1..20) { "Couleur d'enveloppe invalide pour '$id'." }
            envelopesList.add(
                EnvelopeConfig(
                    id = id,
                    userId = currentUserId,
                    key = key,
                    nameFr = nFr,
                    nameAr = e.optString("nameAr", nFr).trim().ifBlank { nFr }.take(100),
                    nameDarija = e.optString("nameDarija", nFr).trim().ifBlank { nFr }.take(100),
                    nameEn = e.optString("nameEn", nFr).trim().ifBlank { nFr }.take(100),
                    percentage = pct,
                    hasCap = e.optBoolean("hasCap", true),
                    colorHex = colorHex
                )
            )
        }

        val itemsList = mutableListOf<FinancialItem>()
        for (i in 0 until itemsArr.length()) {
            val itObj = itemsArr.optJSONObject(i) ?: throw IllegalArgumentException("Élément financier #$i invalide.")
            val id = requireValidBackupId(itObj.optString("id", ""), "financialItem.id")
            val itemType = itObj.optString("itemType", "").trim()
            require(itemType in ALLOWED_ITEM_TYPES) {
                "Type d'élément financier non autorisé ('$itemType') pour '$id'."
            }
            val name = itObj.optString("name", "").trim()
            require(name.length in 1..120) { "Nom d'élément financier invalide pour '$id'." }
            val subtitle = itObj.optString("subtitle", "")
            require(subtitle.length <= 250) { "Sous-titre trop long pour '$id'." }
            val amount = readExactCentimes(
                itObj,
                "amountCentimes",
                Money.ZERO.centimes,
                Money.MAX_STORABLE.centimes,
                "financialItem.amountCentimes"
            )
            val secAmount: Money
            val daretMembersVal: Int?
            if (itemType == "daret") {
                secAmount = Money.ZERO
                daretMembersVal = readExactInt(itObj, "daretMembers", 1, 366, "financialItem.daretMembers")
            } else {
                secAmount = readExactCentimes(
                    itObj,
                    "secondaryAmountCentimes",
                    Money.ZERO.centimes,
                    Money.MAX_STORABLE.centimes,
                    "financialItem.secondaryAmountCentimes",
                    defaultCentimes = 0L
                )
                daretMembersVal = if (itObj.has("daretMembers") && !itObj.isNull("daretMembers")) {
                    readExactInt(itObj, "daretMembers", 1, 366, "financialItem.daretMembers")
                } else null
            }
            val dayOrTurn = readExactInt(itObj, "dayOrTurn", 0, 366, "financialItem.dayOrTurn", defaultValue = 1)
            val extraInt = readExactInt(itObj, "extraInt", 0, 366, "financialItem.extraInt", defaultValue = 0)
            val dateOrCycle = itObj.optString("dateOrCycle", "")
            require(dateOrCycle.length <= 250) { "dateOrCycle trop long pour '$id'." }
            itemsList.add(
                FinancialItem(
                    id = id,
                    userId = currentUserId,
                    itemType = itemType,
                    name = name,
                    subtitle = subtitle,
                    amount = amount,
                    secondaryAmount = secAmount,
                    daretMembers = daretMembersVal,
                    dayOrTurn = dayOrTurn,
                    extraInt = extraInt,
                    dateOrCycle = dateOrCycle
                )
            )
        }

        val txsList = mutableListOf<TransactionItem>()
        for (i in 0 until txArr.length()) {
            val item = txArr.optJSONObject(i) ?: throw IllegalArgumentException("Transaction #$i invalide.")
            val id = requireValidBackupId(item.optString("id", ""), "transaction.id")
            val txType = item.optString("type", "").trim()
            require(txType in ALLOWED_TX_TYPES) {
                "Type de transaction non autorisé ('$txType') pour '$id'."
            }
            val amt = readExactCentimes(
                item,
                "amountCentimes",
                Money.ONE_CENTIME.centimes,
                Money.MAX_STORABLE.centimes,
                "transaction.amountCentimes"
            )
            val cat = item.optString("category", "Autre / Imprévus").trim().ifBlank { "Autre / Imprévus" }
            require(cat.length in 1..120) { "Catégorie invalide pour '$id'." }
            val desc = item.optString("desc", "Transaction")
            require(desc.length <= 250) { "Description trop longue pour '$id'." }
            val accountId = item.optString("accountId", "acc_cash").trim().ifBlank { "acc_cash" }
            require(accountId.length in 1..80) { "accountId invalide pour '$id'." }
            val rawToAccountId = item.optString("toAccountId", "").trim()
            val toAccountId = when (txType) {
                "transfer" -> {
                    require(rawToAccountId.isNotBlank() && rawToAccountId != accountId && rawToAccountId.length <= 80) {
                        "Un transfert doit spécifier un compte destinataire distinct ('$id')."
                    }
                    rawToAccountId
                }
                "transfer_out", "transfer_in" -> {
                    require(rawToAccountId.length <= 80) {
                        "toAccountId invalide pour '$id'."
                    }
                    rawToAccountId
                }
                else -> {
                    require(rawToAccountId.isEmpty()) {
                        "Une opération '$txType' ne doit pas définir toAccountId ('$id')."
                    }
                    ""
                }
            }
            val rawIso = item.optString("isoDate", item.optString("iso", ""))
            val validatedIso = PayCycleEngine.requireOrNormalizeIsoDate(rawIso)
            val envKey = item.optString(
                "envelopeKey",
                PayCycleEngine.resolveEnvelopeKey(cat, desc, emptyList())
            ).trim().ifBlank { "autre" }
            require(envKey.length in 1..60) { "envelopeKey invalide pour '$id'." }
            val rawHour = item.optInt("hourOfDay", -1).let { if (it in 0..23) it else -1 }
            val transferGroupId = item.optString("transferGroupId", "").trim()
                .takeIf { it.isNotBlank() }
                ?.let { requireValidBackupId(it, "transaction.transferGroupId") }
            val loanId = item.optString("loanId", "").trim()
                .takeIf { it.isNotBlank() }
                ?.let { requireValidBackupId(it, "transaction.loanId") }

            txsList.add(
                TransactionItem(
                    id = id,
                    userId = currentUserId,
                    type = txType,
                    amount = amt,
                    category = cat,
                    envelopeKey = envKey,
                    accountId = accountId,
                    toAccountId = toAccountId,
                    desc = desc,
                    isoDate = validatedIso,
                    hourOfDay = rawHour,
                    transferGroupId = transferGroupId,
                    loanId = loanId
                )
            )
        }

        val creditsList = mutableListOf<SavedCreditEntity>()
        for (i in 0 until creditsArr.length()) {
            val c = creditsArr.optJSONObject(i) ?: throw IllegalArgumentException("Crédit sauvegardé #$i invalide.")
            val id = requireValidBackupId(c.optString("id", ""), "savedCredit.id")
            val creditType = c.optString("type", "CONSOMMATION").trim().ifBlank { "CONSOMMATION" }.take(60)
            val loanAmt = readExactCentimes(c, "loanAmountCentimes", 0L, Money.MAX_STORABLE.centimes, "savedCredit.loanAmountCentimes")
            val downAmt = readExactCentimes(c, "downPaymentCentimes", 0L, Money.MAX_STORABLE.centimes, "savedCredit.downPaymentCentimes")
            val principalAmt = readExactCentimes(c, "financedPrincipalCentimes", 0L, Money.MAX_STORABLE.centimes, "savedCredit.financedPrincipalCentimes")
            val monthlyAmt = readExactCentimes(c, "monthlyPaymentCentimes", 0L, Money.MAX_STORABLE.centimes, "savedCredit.monthlyPaymentCentimes")
            val totalCostAmt = readExactCentimes(c, "totalCostCentimes", 0L, Money.MAX_STORABLE.centimes, "savedCredit.totalCostCentimes")
            val durationMonths = readExactInt(c, "durationMonths", 1, 600, "savedCredit.durationMonths")
            val deferredMonths = readExactInt(c, "deferredMonths", 0, 120, "savedCredit.deferredMonths", defaultValue = 0)
            val rateHt = requireFiniteInRange(c.optDouble("annualInterestRateHt", 0.0), 0.0, 50.0, "savedCredit.annualInterestRateHt")
            val insRate = requireFiniteInRange(c.optDouble("insuranceRate", 0.0), 0.0, 20.0, "savedCredit.insuranceRate")
            val dti = requireFiniteInRange(c.optDouble("debtToIncomeRatio", 0.0), 0.0, 10_000.0, "savedCredit.debtToIncomeRatio")
            val maxDti = requireFiniteInRange(c.optDouble("maxAllowedDti", 45.0), 0.0, 100.0, "savedCredit.maxAllowedDti")
            val createdMs = readExactLongOrDefault(c, "createdAtEpochMs", 0L)

            creditsList.add(
                SavedCreditEntity(
                    id = id,
                    userId = currentUserId,
                    type = creditType,
                    loanAmount = loanAmt,
                    downPayment = downAmt,
                    financedPrincipal = principalAmt,
                    durationMonths = durationMonths,
                    deferredMonths = deferredMonths,
                    annualInterestRateHt = rateHt,
                    applyInterestTva = c.optBoolean("applyInterestTva", true),
                    insuranceRate = insRate,
                    monthlyPayment = monthlyAmt,
                    totalCost = totalCostAmt,
                    debtToIncomeRatio = dti,
                    maxAllowedDti = maxDti,
                    linkToMonthlyExpenses = c.optBoolean("linkToMonthlyExpenses", false),
                    createdAt = if (createdMs > 0L) Timestamp(Date(createdMs)) else null
                )
            )
        }

        val learnedMap = linkedMapOf<String, String>()
        val learnedObj = obj.optJSONObject("learnedCategories")
        if (learnedObj != null) {
            val keys = learnedObj.keys()
            while (keys.hasNext()) {
                val k = keys.next().trim().lowercase()
                val v = learnedObj.optString(k, "").trim().lowercase()
                if (k.length >= 2 && v.isNotBlank()) {
                    learnedMap[k] = v
                }
            }
        }

        val parsedFiscal = if (fiscalObj != null) {
            val billsList = mutableListOf<AnnualBillItem>()
            if (annualBillsArr != null) {
                for (i in 0 until annualBillsArr.length()) {
                    val b = annualBillsArr.optJSONObject(i)
                        ?: throw IllegalArgumentException("Charge annuelle #$i invalide.")
                    val id = requireValidBackupId(b.optString("id", ""), "fiscal.annualBill.id")
                    val title = b.optString("title", "").trim()
                    require(title.length in 1..120) { "Titre de charge annuelle invalide pour '$id'." }
                    val annualAmt = readExactCentimes(
                        b,
                        "annualAmountCentimes",
                        0L,
                        Money.MAX_STORABLE.centimes,
                        "fiscal.annualBill.annualAmountCentimes"
                    )
                    val dueMonth = readExactInt(b, "dueMonth", 1, 12, "fiscal.annualBill.dueMonth")
                    val dueYear = readExactInt(b, "dueYear", 2020, 2150, "fiscal.annualBill.dueYear")
                    val cvRaw = b.optString("cvBand", "")
                    val fuelRaw = b.optString("fuelType", "")
                    billsList.add(
                        AnnualBillItem(
                            id = id,
                            preset = AnnualBillPreset.fromKey(b.optString("preset", "custom")),
                            title = title,
                            annualAmountMad = annualAmt,
                            dueMonth = dueMonth,
                            dueYear = dueYear,
                            cvBand = cvRaw.takeIf { it.isNotBlank() }?.let { FiscalRates.HorsepowerBand.fromKey(it) },
                            fuelType = fuelRaw.takeIf { it.isNotBlank() }?.let { FiscalRates.FuelType.fromKey(it) },
                            isManualVignetteAmount = b.optBoolean("isManualVignetteAmount", false),
                            linkedGoalId = b.optString("linkedGoalId", "").take(128),
                            linkedBillId = b.optString("linkedBillId", "").take(128),
                            isPaid = b.optBoolean("isPaid", false),
                            paidIsoDate = b.optString("paidIsoDate", "").take(20),
                            createdAtMillis = readExactLongOrDefault(b, "createdAtMillis", 0L)
                        )
                    )
                }
            }

            val zObj = fiscalObj.optJSONObject("zakatConfig")
            val parsedZakat = if (zObj != null) {
                val gold = readExactCentimes(
                    zObj,
                    "goldPricePerGramCentimes",
                    Money.ONE_CENTIME.centimes,
                    Money.MAX_STORABLE.centimes,
                    "fiscal.zakatConfig.goldPricePerGramCentimes"
                )
                val silver = readExactCentimes(
                    zObj,
                    "silverPricePerGramCentimes",
                    Money.ONE_CENTIME.centimes,
                    Money.MAX_STORABLE.centimes,
                    "fiscal.zakatConfig.silverPricePerGramCentimes"
                )
                ZakatConfig(
                    nisabStandard = NisabStandard.fromKey(
                        zObj.optString("nisabStandard", NisabStandard.GOLD_85G.key)
                    ),
                    goldPriceMadPerGram = gold,
                    silverPriceMadPerGram = silver,
                    pricesAsOfIso = zObj.optString("pricesAsOfIso", FiscalRates.DEFAULT_METAL_PRICES_AS_OF_ISO)
                        .ifBlank { FiscalRates.DEFAULT_METAL_PRICES_AS_OF_ISO },
                    hawlStartIso = zObj.optString("hawlStartIso", "").take(20),
                    linkedAnnualBillId = zObj.optString("linkedAnnualBillId", "").take(128)
                )
            } else null

            BackupFiscalSnapshot(
                annualBills = billsList,
                zakatConfig = parsedZakat
            )
        } else null

        val loansList = mutableListOf<LoanItem>()
        for (i in 0 until loansArr.length()) {
            val l = loansArr.optJSONObject(i) ?: throw IllegalArgumentException("Prêt #$i invalide.")
            val id = requireValidBackupId(l.optString("id", ""), "loan.id")
            val title = l.optString("title", "").trim()
            require(title.length in 1..120) { "Titre du prêt '$id' invalide." }
            val counterparty = l.optString("counterparty", "").trim()
            require(counterparty.length <= 120) { "Contrepartie trop longue pour le prêt '$id'." }
            val dirWire = l.optString("direction", "LIABILITY").trim().uppercase()
            require(dirWire in ALLOWED_LOAN_DIRECTIONS) { "Direction de prêt invalide ('$dirWire') pour '$id'." }
            val catWire = l.optString("category", "PERSONAL_DEBT").trim().uppercase()
            require(catWire in ALLOWED_LOAN_CATEGORIES) { "Catégorie de prêt invalide ('$catWire') pour '$id'." }
            val statusWire = l.optString("status", "ACTIVE").trim().uppercase()
            require(statusWire in ALLOWED_LOAN_STATUSES) { "Statut de prêt invalide ('$statusWire') pour '$id'." }
            val principal = readExactCentimes(
                l,
                "principalCentimes",
                Money.ZERO.centimes,
                Money.MAX_STORABLE.centimes,
                "loan.principalCentimes"
            )
            val remaining = readExactCentimes(
                l,
                "remainingCentimes",
                Money.ZERO.centimes,
                principal.centimes,
                "loan.remainingCentimes"
            )
            val monthly = readExactCentimes(
                l,
                "monthlyPaymentCentimes",
                Money.ZERO.centimes,
                Money.MAX_STORABLE.centimes,
                "loan.monthlyPaymentCentimes",
                defaultCentimes = 0L
            )
            val linkedAcc = l.optString("linkedAccountId", "").trim()
                .takeIf { it.isNotBlank() }
                ?.also { require(it.length <= 80) { "linkedAccountId invalide pour le prêt '$id'." } }
            val sourceFinId = l.optString("sourceFinancialItemId", "").trim()
                .takeIf { it.isNotBlank() }
                ?.let { requireValidBackupId(it, "loan.sourceFinancialItemId") }
            val sourceCreditId = l.optString("sourceSavedCreditId", "").trim()
                .takeIf { it.isNotBlank() }
                ?.let { requireValidBackupId(it, "loan.sourceSavedCreditId") }
            val dueIso = l.optString("dueIsoDate", "").trim()
                .takeIf { it.isNotBlank() }
                ?.let { PayCycleEngine.requireOrNormalizeIsoDate(it) }
            val notes = l.optString("notes", "")
            require(notes.length <= 250) { "Notes trop longues pour le prêt '$id'." }

            loansList.add(
                LoanItem(
                    id = id,
                    userId = currentUserId,
                    title = title,
                    counterparty = counterparty,
                    direction = LoanDirection.fromWire(dirWire),
                    category = LoanCategory.fromWire(catWire),
                    principal = principal,
                    remaining = remaining,
                    monthlyPayment = monthly,
                    status = LoanStatus.fromWire(statusWire),
                    linkedAccountId = linkedAcc,
                    sourceFinancialItemId = sourceFinId,
                    sourceSavedCreditId = sourceCreditId,
                    dueIsoDate = dueIso,
                    notes = notes,
                    createdAtEpochMs = readExactLongOrDefault(l, "createdAtEpochMs", 0L),
                    updatedAtEpochMs = readExactLongOrDefault(l, "updatedAtEpochMs", 0L)
                )
            )
        }

        val repaymentsList = mutableListOf<LoanRepaymentItem>()
        for (i in 0 until repaymentsArr.length()) {
            val r = repaymentsArr.optJSONObject(i) ?: throw IllegalArgumentException("Remboursement #$i invalide.")
            val id = requireValidBackupId(r.optString("id", ""), "loanRepayment.id")
            val loanId = requireValidBackupId(r.optString("loanId", ""), "loanRepayment.loanId")
            val amt = readExactCentimes(
                r,
                "amountCentimes",
                Money.ONE_CENTIME.centimes,
                Money.MAX_STORABLE.centimes,
                "loanRepayment.amountCentimes"
            )
            val isoDate = PayCycleEngine.requireOrNormalizeIsoDate(r.optString("isoDate", ""))
            val linkedAcc = r.optString("linkedAccountId", "").trim()
                .takeIf { it.isNotBlank() }
                ?.also { require(it.length <= 80) { "linkedAccountId invalide pour '$id'." } }
            val linkedTx = r.optString("linkedTransactionId", "").trim()
                .takeIf { it.isNotBlank() }
                ?.let { requireValidBackupId(it, "loanRepayment.linkedTransactionId") }
            val note = r.optString("note", "")
            require(note.length <= 250) { "Note trop longue pour le remboursement '$id'." }

            repaymentsList.add(
                LoanRepaymentItem(
                    id = id,
                    loanId = loanId,
                    userId = currentUserId,
                    amount = amt,
                    isoDate = isoDate,
                    linkedAccountId = linkedAcc,
                    linkedTransactionId = linkedTx,
                    note = note,
                    createdAtEpochMs = readExactLongOrDefault(r, "createdAtEpochMs", 0L)
                )
            )
        }

        return ParsedBackupPayload(
            backupVersion = BACKUP_VERSION_V3,
            exportedAtIso = exportedAtIso,
            exportedAtEpochMillis = exportedAtEpochMillis,
            appVersion = appVersion,
            profile = restoredProfile,
            customPrefs = restoredCustomPrefs,
            accounts = accountsList,
            envelopes = envelopesList,
            financialItems = itemsList,
            transactions = txsList,
            learnedCategories = learnedMap,
            savedCredits = creditsList,
            hasSavedCreditsSection = obj.has("savedCredits"),
            fiscal = parsedFiscal,
            loans = loansList,
            loanRepayments = repaymentsList,
            hasLoansSection = hasLoansSection,
            hasLoanRepaymentsSection = hasRepaymentsSection
        )
    }

    private fun verifyCount(countsObj: JSONObject, key: String, actual: Int) {
        if (!countsObj.has(key) || countsObj.isNull(key)) return
        val expected = readExactInt(countsObj, key, 0, MAX_BACKUP_ITEMS, "metadata.recordCounts.$key")
        require(expected == actual) {
            "Intégrité de sauvegarde invalide : metadata.recordCounts.$key ($expected) != $actual."
        }
    }

    private fun requireValidBackupId(id: String, fieldLabel: String): String {
        val trimmed = id.trim()
        require(VALID_ID_REGEX.matches(trimmed)) {
            "Identifiant '$fieldLabel' invalide ('$trimmed')."
        }
        return trimmed
    }

    private fun requireFiniteInRange(value: Double, min: Double, max: Double, fieldLabel: String): Double {
        require(!value.isNaN() && !value.isInfinite() && value in min..max) {
            "Valeur numérique '$fieldLabel' hors limites ($value)."
        }
        return value
    }

    /**
     * Strictly parses an exact integer `Long` centimes value from JSON with ZERO `Double` conversion.
     */
    private fun readExactCentimes(
        obj: JSONObject,
        key: String,
        minCentimes: Long,
        maxCentimes: Long,
        fieldLabel: String,
        defaultCentimes: Long? = null
    ): Money {
        if (!obj.has(key) || obj.isNull(key)) {
            if (defaultCentimes != null) {
                require(defaultCentimes in minCentimes..maxCentimes) {
                    "Valeur par défaut '$fieldLabel' hors limites ($defaultCentimes)."
                }
                return Money.fromCentimes(defaultCentimes)
            }
            throw IllegalArgumentException("Champ monétaire exact '$fieldLabel' manquant.")
        }
        val raw = obj.opt(key)
        val centimes: Long = when (raw) {
            is Long -> raw
            is Int -> raw.toLong()
            is Short -> raw.toLong()
            is Byte -> raw.toLong()
            else -> throw IllegalArgumentException(
                "Champ monétaire '$fieldLabel' doit être un entier Long exact en centimes (reçu: $raw)."
            )
        }
        require(centimes in minCentimes..maxCentimes) {
            "Montant '$fieldLabel' hors limites ($centimes centimes)."
        }
        return Money.fromCentimes(centimes)
    }

    private fun readExactInt(
        obj: JSONObject,
        key: String,
        min: Int,
        max: Int,
        fieldLabel: String,
        defaultValue: Int? = null
    ): Int {
        if (!obj.has(key) || obj.isNull(key)) {
            if (defaultValue != null) return defaultValue
            throw IllegalArgumentException("Champ entier '$fieldLabel' manquant.")
        }
        val raw = obj.opt(key)
        val value: Int = when (raw) {
            is Int -> raw
            is Long -> {
                require(raw in Int.MIN_VALUE..Int.MAX_VALUE) { "Entier '$fieldLabel' hors limites ($raw)." }
                raw.toInt()
            }
            else -> throw IllegalArgumentException("Champ '$fieldLabel' doit être un entier (reçu: $raw).")
        }
        require(value in min..max) { "Valeur '$fieldLabel' hors limites ($value)." }
        return value
    }

    private fun readExactLongOrDefault(obj: JSONObject, key: String, defaultValue: Long): Long {
        if (!obj.has(key) || obj.isNull(key)) return defaultValue
        return when (val raw = obj.opt(key)) {
            is Long -> raw
            is Int -> raw.toLong()
            else -> defaultValue
        }
    }
}
