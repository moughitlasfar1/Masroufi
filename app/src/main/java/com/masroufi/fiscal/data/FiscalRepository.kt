package com.masroufi.fiscal.data

import android.content.Context
import com.example.data.FinancialItem
import com.example.security.SecureStore
import com.masroufi.core.Money
import com.masroufi.fiscal.domain.AnnualBillCalculator
import com.masroufi.fiscal.domain.AnnualBillItem
import com.masroufi.fiscal.domain.AnnualBillPreset
import com.masroufi.fiscal.domain.FiscalRates
import com.masroufi.fiscal.domain.FiscalUiStrings
import com.masroufi.fiscal.domain.NisabStandard
import com.masroufi.fiscal.domain.ZakatConfig
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Local-only, AES-256-GCM encrypted store (via Masroufi's [SecureStore]) for Annual Bills
 * and Zakat/Hawl settings. Zero database schema changes and zero network calls.
 *
 * The actual reserve funds and monthly recurring contributions are persisted using Masroufi's
 * existing [FinancialItem] records (`itemType = "goal"` and `itemType = "bill"`).
 */
object FiscalRepository {
    private const val KEY_ANNUAL_BILLS_JSON = "fiscal_annual_bills_v1"
    private const val KEY_ZAKAT_CONFIG_JSON = "fiscal_zakat_config_v1"

    fun loadAnnualBills(context: Context): List<AnnualBillItem> {
        val raw = SecureStore.getString(context.applicationContext, KEY_ANNUAL_BILLS_JSON)
            ?: return emptyList()
        return parseAnnualBillsJson(raw)
    }

    fun saveAnnualBills(context: Context, bills: List<AnnualBillItem>) {
        SecureStore.putString(
            context.applicationContext,
            KEY_ANNUAL_BILLS_JSON,
            serializeAnnualBillsJson(bills)
        )
    }

    fun loadZakatConfig(context: Context): ZakatConfig {
        val raw = SecureStore.getString(context.applicationContext, KEY_ZAKAT_CONFIG_JSON)
            ?: return ZakatConfig()
        return parseZakatConfigJson(raw)
    }

    fun saveZakatConfig(context: Context, config: ZakatConfig) {
        SecureStore.putString(
            context.applicationContext,
            KEY_ZAKAT_CONFIG_JSON,
            serializeZakatConfigJson(config)
        )
    }

    fun serializeAnnualBillsJson(bills: List<AnnualBillItem>): String {
        val arr = JSONArray()
        bills.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("preset", b.preset.key)
                    put("title", b.title)
                    put("annualAmountMad", b.annualAmountMad.toBigDecimalDh())
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
        return arr.toString()
    }

    fun parseAnnualBillsJson(raw: String): List<AnnualBillItem> {
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val list = mutableListOf<AnnualBillItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id", "").trim()
            if (id.isEmpty()) continue
            val cvRaw = obj.optString("cvBand", "")
            val fuelRaw = obj.optString("fuelType", "")
            list.add(
                AnnualBillItem(
                    id = id,
                    preset = AnnualBillPreset.fromKey(obj.optString("preset", "custom")),
                    title = obj.optString("title", ""),
                    annualAmountMad = (readJsonMoneyOrNull(obj, "annualAmountMad") ?: Money.ZERO).coerceAtLeast(Money.ZERO),
                    dueMonth = obj.optInt("dueMonth", 1).coerceIn(1, 12),
                    dueYear = obj.optInt("dueYear", 2026).coerceIn(2020, 2150),
                    cvBand = cvRaw.takeIf { it.isNotBlank() }?.let { FiscalRates.HorsepowerBand.fromKey(it) },
                    fuelType = fuelRaw.takeIf { it.isNotBlank() }?.let { FiscalRates.FuelType.fromKey(it) },
                    isManualVignetteAmount = obj.optBoolean("isManualVignetteAmount", false),
                    linkedGoalId = obj.optString("linkedGoalId", ""),
                    linkedBillId = obj.optString("linkedBillId", ""),
                    isPaid = obj.optBoolean("isPaid", false),
                    paidIsoDate = obj.optString("paidIsoDate", ""),
                    createdAtMillis = obj.optLong("createdAtMillis", 0L)
                )
            )
        }
        return list
    }

    fun serializeZakatConfigJson(config: ZakatConfig): String = JSONObject().apply {
        put("nisabStandard", config.nisabStandard.key)
        put("goldPriceMadPerGram", config.goldPriceMadPerGram.toBigDecimalDh())
        put("silverPriceMadPerGram", config.silverPriceMadPerGram.toBigDecimalDh())
        put("pricesAsOfIso", config.pricesAsOfIso)
        put("hawlStartIso", config.hawlStartIso)
        put("linkedAnnualBillId", config.linkedAnnualBillId)
    }.toString()

    fun parseZakatConfigJson(raw: String): ZakatConfig {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return ZakatConfig()
        val gold = readJsonMoneyOrNull(obj, "goldPriceMadPerGram")
            ?.takeIf { it.isPositive } ?: FiscalRates.DEFAULT_GOLD_PRICE_MAD_PER_GRAM
        val silver = readJsonMoneyOrNull(obj, "silverPriceMadPerGram")
            ?.takeIf { it.isPositive } ?: FiscalRates.DEFAULT_SILVER_PRICE_MAD_PER_GRAM
        return ZakatConfig(
            nisabStandard = NisabStandard.fromKey(obj.optString("nisabStandard", NisabStandard.GOLD_85G.key)),
            goldPriceMadPerGram = gold,
            silverPriceMadPerGram = silver,
            pricesAsOfIso = obj.optString("pricesAsOfIso", FiscalRates.DEFAULT_METAL_PRICES_AS_OF_ISO)
                .ifBlank { FiscalRates.DEFAULT_METAL_PRICES_AS_OF_ISO },
            hawlStartIso = obj.optString("hawlStartIso", ""),
            linkedAnnualBillId = obj.optString("linkedAnnualBillId", "")
        )
    }

    private fun readJsonMoneyOrNull(obj: JSONObject, key: String): Money? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return when (val raw = obj.opt(key)) {
            is BigDecimal -> Money.fromMajorUnits(raw)
            is Number -> {
                val d = raw.toDouble()
                if (d.isFinite()) Money.fromLegacyDoubleDh(d) else null
            }
            is String -> Money.fromDhString(raw)
            else -> null
        }
    }

    /**
     * Builds the backing Masroufi Savings Goal (`itemType = "goal"`) and linked monthly
     * recurring contribution (`itemType = "bill"`) for an [AnnualBillItem].
     */
    fun buildBackingGoalAndRecurringBill(
        bill: AnnualBillItem,
        existingGoalSavedAmount: Money,
        payCycleDay: Int,
        strings: FiscalUiStrings,
        currentYear: Int,
        currentMonth: Int
    ): Pair<FinancialItem, FinancialItem> {
        val breakdown = AnnualBillCalculator.computeBreakdown(
            annualAmountMad = bill.annualAmountMad,
            savedAmountMad = existingGoalSavedAmount,
            dueMonth = bill.dueMonth,
            dueYear = bill.dueYear,
            currentYear = currentYear,
            currentMonth = currentMonth,
            rollForwardIfPassed = false
        )
        val goalId = bill.linkedGoalId.ifBlank { "goal_fiscal_${bill.id}" }
        val recurringBillId = bill.linkedBillId.ifBlank { "bill_fiscal_${bill.id}" }
        val monthLabel = strings.monthNames.getOrElse((bill.dueMonth - 1).coerceIn(0, 11)) { bill.dueMonth.toString() }

        val goalItem = FinancialItem(
            id = goalId,
            itemType = "goal",
            name = "${strings.goalReservePrefix} · ${bill.title}",
            subtitle = "${strings.annualBillsTitle} ($monthLabel ${bill.dueYear})",
            amount = breakdown.annualAmountMad,
            secondaryAmount = breakdown.savedAmountMad,
            dayOrTurn = bill.dueMonth.coerceIn(1, 12),
            extraInt = 0,
            dateOrCycle = breakdown.dueDateIso
        )

        val recurringBillItem = FinancialItem(
            id = recurringBillId,
            itemType = "bill",
            name = "${strings.recurringReservePrefix} · ${bill.title}",
            subtitle = "${strings.annualBillsTitle} · $monthLabel ${bill.dueYear}",
            amount = breakdown.monthlyReserveMad,
            secondaryAmount = Money.ZERO,
            dayOrTurn = payCycleDay.coerceIn(1, 28),
            extraInt = 0,
            dateOrCycle = ""
        )
        return goalItem to recurringBillItem
    }
}
