package com.example.data

import com.masroufi.core.Money
import java.util.Locale

const val LOCAL_DEVICE_USER_ID = "local_device_user"

object DefaultCategoryResolver {
    private val NUMERIC_PREFIX_REGEX = Regex("^\\d+\\.\\s*")

    private val TRANSPORT_ALIASES = setOf(
        "transport",
        "env_transport",
        "transport & déplacement (oncf, taxi)",
        "transport & deplacement (oncf, taxi)",
        "transport & déplacement",
        "transport & deplacement",
        "transport & carburant",
        "commute & transit (train, taxi)",
        "commute & transit",
        "النقل والتنقل (القطار، سيارة الأجرة)",
        "النقل والتنقل",
        "الخدمة والتنقل (التران، الطاكسي)",
        "الخدمة والتنقل"
    )

    private val CHARGES_ALIASES = setOf(
        "charges",
        "env_charges",
        "charges fixes & maison (factures)",
        "charges fixes & maison",
        "fixed bills & household",
        "الالتزامات الثابتة والمنزل",
        "التزامات قارة ومصاريف الدار"
    )

    private val CAFE_ALIASES = setOf(
        "cafe",
        "env_cafe",
        "café & sorties (personnel & loisirs)",
        "cafe & sorties (personnel & loisirs)",
        "café & sorties",
        "cafe & sorties",
        "café, resto & sorties",
        "cafe, resto & sorties",
        "café & outings (personal & leisure)",
        "cafe & outings (personal & leisure)",
        "café & outings",
        "cafe & outings",
        "مقهى وخروج (شخصي وترفيه)",
        "مقهى وخروج",
        "قهوة وخروج (مصروف شخصي)",
        "قهوة وخروج"
    )

    private val EPARGNE_ALIASES = setOf(
        "epargne",
        "env_epargne",
        "épargne & investissement",
        "epargne & investissement",
        "savings & investments",
        "الادخار والاستثمار",
        "التوفير والادخار"
    )

    private val MARCHE_ALIASES = setOf(
        "marche",
        "env_marche",
        "marché",
        "groceries",
        "السوق",
        "التقدية"
    )

    private val AUTRE_ALIASES = setOf(
        "autre",
        "env_autre",
        "autre / imprévus",
        "autre / imprevus",
        "général",
        "general",
        "other / unplanned",
        "other",
        "أخرى / مصاريف غير متوقعة",
        "أخرى",
        "حاجة خرى / مكنتش حاسبها",
        "حاجة خرى"
    )

    fun stripNumericPrefix(raw: String): String {
        return raw.trim().replace(NUMERIC_PREFIX_REGEX, "").trim()
    }

    fun canonicalDefaultId(rawNameOrKey: String): String? {
        val cleaned = stripNumericPrefix(rawNameOrKey).lowercase(Locale.ROOT)
        if (cleaned.isEmpty()) return null
        return when (cleaned) {
            in TRANSPORT_ALIASES -> "transport"
            in CHARGES_ALIASES, "charges & maison", "bills & home", "الالتزامات والمنزل", "مصاريف الدار" -> "charges"
            in CAFE_ALIASES, "مقهى وخرجات", "قهوة وخرجات" -> "cafe"
            in EPARGNE_ALIASES, "épargne", "epargne", "savings", "الادخار", "التوفير" -> "epargne"
            in MARCHE_ALIASES -> "marche"
            in AUTRE_ALIASES -> "autre"
            else -> null
        }
    }

    fun localizedDefaultName(canonicalId: String, lang: String): String? {
        val normId = canonicalDefaultId(canonicalId) ?: canonicalId.trim().lowercase(Locale.ROOT)
        return when (normId) {
            "transport" -> when (lang) {
                "ar" -> "النقل"
                "darija" -> "التنقل"
                "en" -> "Transport"
                else -> "Transport"
            }
            "charges" -> when (lang) {
                "ar" -> "الالتزامات والمنزل"
                "darija" -> "مصاريف الدار"
                "en" -> "Bills & Home"
                else -> "Charges & Maison"
            }
            "cafe" -> when (lang) {
                "ar" -> "مقهى وخرجات"
                "darija" -> "قهوة وخرجات"
                "en" -> "Café & Outings"
                else -> "Café & Sorties"
            }
            "epargne" -> when (lang) {
                "ar" -> "الادخار"
                "darija" -> "التوفير"
                "en" -> "Savings"
                else -> "Épargne"
            }
            "marche" -> when (lang) {
                "ar" -> "السوق"
                "darija" -> "التقدية"
                "en" -> "Groceries"
                else -> "Marché"
            }
            "autre" -> when (lang) {
                "ar" -> "أخرى"
                "darija" -> "حاجة خرى"
                "en" -> "Other"
                else -> "Autre"
            }
            else -> null
        }
    }

    fun localizedDefaultHelper(canonicalId: String, lang: String): String {
        val normId = canonicalDefaultId(canonicalId) ?: canonicalId.trim().lowercase(Locale.ROOT)
        return when (normId) {
            "transport" -> when (lang) {
                "ar" -> "القطار، سيارة الأجرة، الترام، الوقود"
                "darija" -> "التران، الطاكسي، الترام، المازوط"
                "en" -> "ONCF, Taxi, Tram, Fuel"
                else -> "ONCF, Taxi, Tram, Carburant"
            }
            "charges" -> when (lang) {
                "ar" -> "الكراء، الماء والكهرباء، الإنترنت"
                "darija" -> "الكرا، الما والضو، الويفي"
                "en" -> "Rent, Water & Electricity, Internet"
                else -> "Loyer, Eau & Électricité, Internet"
            }
            "cafe" -> when (lang) {
                "ar" -> "مقهى، مطعم، ترفيه"
                "darija" -> "قهوة، ريسطو، خريجات"
                "en" -> "Coffee, Dining, Leisure"
                else -> "Café, Restaurant, Loisirs"
            }
            "epargne" -> when (lang) {
                "ar" -> "صندوق الطوارئ، استثمار"
                "darija" -> "فلوس الدواير، مشاريع"
                "en" -> "Emergency fund, Goals"
                else -> "Fonds d'urgence, Objectifs"
            }
            "marche" -> when (lang) {
                "ar" -> "الخضر، البقالة، مرجان"
                "darija" -> "الخضرة، الحانوت، مرجان"
                "en" -> "Groceries, Market, Supermarket"
                else -> "Courses, Marché, Supermarché"
            }
            "autre" -> when (lang) {
                "ar" -> "مصاريف غير متوقعة"
                "darija" -> "مصاريف ما كنتش حاسبها"
                "en" -> "Unplanned expenses"
                else -> "Imprévus, Divers"
            }
            else -> ""
        }
    }

    fun normalizeStoredCategoryValue(rawCategory: String, rawEnvelopeKey: String = ""): String {
        val stripped = stripNumericPrefix(rawCategory)
        val canonicalFromCat = canonicalDefaultId(stripped)
        if (canonicalFromCat != null) return canonicalFromCat
        if (stripped.isBlank()) {
            val canonicalFromEnv = canonicalDefaultId(rawEnvelopeKey)
            if (canonicalFromEnv != null) return canonicalFromEnv
            return "autre"
        }
        return stripped
    }

    fun resolveTransactionCategoryDisplay(
        rawCategory: String,
        rawEnvelopeKey: String,
        envelopes: List<EnvelopeConfig>,
        lang: String
    ): String {
        val strippedCat = stripNumericPrefix(rawCategory)
        val canonicalCat = canonicalDefaultId(strippedCat)
        val targetKey = canonicalCat ?: rawEnvelopeKey.trim()

        // 1. If the user has an envelope matching targetKey (which may be user-renamed or default), resolve it
        val matchingEnv = envelopes.find {
            it.key.equals(targetKey, ignoreCase = true) ||
                (canonicalCat != null && canonicalDefaultId(it.key) == canonicalCat)
        }
        if (matchingEnv != null && (canonicalCat != null || strippedCat.isBlank() || strippedCat.equals(matchingEnv.key, ignoreCase = true))) {
            return matchingEnv.localizedName(lang)
        }

        // 2. If rawCategory itself is a default envelope ID or known default localized name, resolve at render time
        if (canonicalCat != null) {
            localizedDefaultName(canonicalCat, lang)?.let { return it }
        }

        // 3. If rawCategory matches a custom envelope's stored name in any language, return that envelope's localizedName
        val customEnvMatch = envelopes.find { env ->
            stripNumericPrefix(env.nameFr).equals(strippedCat, ignoreCase = true) ||
                stripNumericPrefix(env.nameEn).equals(strippedCat, ignoreCase = true) ||
                stripNumericPrefix(env.nameAr).equals(strippedCat, ignoreCase = true) ||
                stripNumericPrefix(env.nameDarija).equals(strippedCat, ignoreCase = true)
        }
        if (customEnvMatch != null) {
            return customEnvMatch.localizedName(lang)
        }

        if (strippedCat.isNotBlank()) return strippedCat
        val fallbackCanonical = canonicalDefaultId(rawEnvelopeKey)
        if (fallbackCanonical != null) {
            localizedDefaultName(fallbackCanonical, lang)?.let { return it }
        }
        return localizedDefaultName("autre", lang) ?: "Autre"
    }
}

data class UserProfile(
    val id: String = "",
    val userId: String = "",
    val baseBudget: Money = Money(640_000L),
    val payCycleDay: Int = 1,
    val rolloverEnabled: Boolean = true,
    val language: String = "fr",
    val isDarkTheme: Boolean = false,
    val seeded: Boolean = true,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)

data class TransactionItem(
    val id: String = "",
    val userId: String = "",
    val type: String = "expense", // "expense", "income", "transfer", "transfer_out", "transfer_in"
    val amount: Money = Money.ZERO,
    val category: String = "autre",
    val envelopeKey: String = "autre",
    val accountId: String = "acc_cash",
    val toAccountId: String = "",
    val desc: String = "",
    val isoDate: String = "", // YYYY-MM-DD
    val hourOfDay: Int = -1,
    val transferGroupId: String? = null,
    val loanId: String? = null,
    val recurringOccurrenceKey: String? = null,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
) {
    fun isTransferType(): Boolean =
        type == "transfer" || type == "transfer_out" || type == "transfer_in"

    fun localizedCategory(lang: String, envelopes: List<EnvelopeConfig> = emptyList()): String {
        return DefaultCategoryResolver.resolveTransactionCategoryDisplay(
            rawCategory = category,
            rawEnvelopeKey = envelopeKey,
            envelopes = envelopes,
            lang = lang
        )
    }

    fun resolvedHourOfDay(): Int {
        if (hourOfDay in 0..23) return hourOfDay
        if (createdAtEpochMs <= 0L) return -1
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = createdAtEpochMs }
        return cal.get(java.util.Calendar.HOUR_OF_DAY)
    }
}

data class AccountItem(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val kind: String = "cash", // "cash", "bank", "wallet"
    val initialBalance: Money = Money.ZERO,
    val colorHex: String = "#10B981",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)

data class EnvelopeConfig(
    val id: String = "",
    val userId: String = "",
    val key: String = "",
    val nameFr: String = "",
    val nameAr: String = "",
    val nameDarija: String = "",
    val nameEn: String = "",
    val percentage: Double = 0.0,
    val hasCap: Boolean = true,
    val colorHex: String = "#2563EB",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
) {
    fun isDefaultUnrenamed(): Boolean {
        val keyCanonical = DefaultCategoryResolver.canonicalDefaultId(key.ifBlank { id })
        val frCanonical = DefaultCategoryResolver.canonicalDefaultId(nameFr)
        if (nameFr.isBlank() && keyCanonical != null) return true
        return frCanonical != null && (keyCanonical == null || frCanonical == keyCanonical)
    }

    fun localizedName(lang: String): String {
        if (isDefaultUnrenamed()) {
            val canonical = DefaultCategoryResolver.canonicalDefaultId(key.ifBlank { nameFr.ifBlank { id } })
            if (canonical != null) {
                DefaultCategoryResolver.localizedDefaultName(canonical, lang)?.let { return it }
            }
        }
        val raw = when (lang) {
            "ar" -> nameAr.ifBlank { nameFr }
            "darija" -> nameDarija.ifBlank { nameFr }
            "en" -> nameEn.ifBlank { nameFr }
            else -> nameFr
        }
        val stripped = DefaultCategoryResolver.stripNumericPrefix(raw)
        val canonicalFallback = DefaultCategoryResolver.canonicalDefaultId(stripped)
        if (canonicalFallback != null) {
            DefaultCategoryResolver.localizedDefaultName(canonicalFallback, lang)?.let { return it }
        }
        return stripped.ifBlank { DefaultCategoryResolver.stripNumericPrefix(key) }
    }

    fun migratedStorageCopy(): EnvelopeConfig {
        if (isDefaultUnrenamed()) {
            val canonical = DefaultCategoryResolver.canonicalDefaultId(key.ifBlank { nameFr.ifBlank { id } }) ?: key
            return copy(
                key = canonical,
                nameFr = canonical,
                nameAr = canonical,
                nameDarija = canonical,
                nameEn = canonical
            )
        }
        return copy(
            nameFr = DefaultCategoryResolver.stripNumericPrefix(nameFr),
            nameAr = DefaultCategoryResolver.stripNumericPrefix(nameAr),
            nameDarija = DefaultCategoryResolver.stripNumericPrefix(nameDarija),
            nameEn = DefaultCategoryResolver.stripNumericPrefix(nameEn)
        )
    }
}

data class FinancialItem(
    val id: String = "",
    val userId: String = "",
    val itemType: String = "goal", // "goal", "bill", "debt", "daret", "seasonal", "preset", "custom_prefs"
    val name: String = "",
    val subtitle: String = "", // personName for debt, category for preset, eventType for seasonal, palette|currency for custom_prefs
    val amount: Money = Money.ZERO, // target / bill amount / debt total / monthlyContribution / preset amount
    val secondaryAmount: Money = Money.ZERO, // savedAmount / paidAmount (0 for daret)
    val daretMembers: Int? = null, // totalMembers for itemType == "daret"
    val dayOrTurn: Int = 1, // dueDay (1..31) / myTurnNumber / bitmask for visible cards
    val extraInt: Int = 0, // paidMonthsCount for Daret
    val dateOrCycle: String = "", // deadlineIso / payoutDateIso / comma-separated paidCycles / "lent"|"borrowed" / customSubtitle
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
) {
    fun resolvedDaretMembers(): Int = (daretMembers ?: 1).coerceAtLeast(1)
}

data class CustomizationPrefs(
    val accentPalette: String = "majorelle", // "emerald", "majorelle", "amber", "amethyst", "terracotta", "ocean"
    val currencySymbol: String = "DH", // "DH", "MAD", "د.م.", "€", "$"
    val customTitle: String = "Masroufi",
    val customSubtitle: String = "",
    val showSafeToSpend: Boolean = true,
    val showAccountsCard: Boolean = true,
    val showQuickPresetsCard: Boolean = true,
    val showWeekSummaryRow: Boolean = true,
    val showMonthlyReviewCard: Boolean = true,
    val showBillsCard: Boolean = true,
    val showSmartInsightsCard: Boolean = true,
    val showForecastCard: Boolean = true,
    val showUpcomingRecurringCard: Boolean = true,
    val showRecentTransactionsCard: Boolean = true,
    val dashboardOrder: List<String> = DEFAULT_DASHBOARD_ORDER,
    val extendedDashboardOrder: List<String> = DEFAULT_EXTENDED_DASHBOARD_ORDER,
    val themeMode: String = "system", // "system", "light", "dark"
    val eurToMadRate: Double = 10.85,
    val usdToMadRate: Double = 9.95
) {
    fun normalizedThemeMode(): String = normalizeThemeMode(themeMode)

    fun normalizedEurRate(): Double = if (eurToMadRate.isFinite() && eurToMadRate in 0.5..500.0) eurToMadRate else 10.85

    fun normalizedUsdRate(): Double = if (usdToMadRate.isFinite() && usdToMadRate in 0.5..500.0) usdToMadRate else 9.95

    fun resolveIsDark(systemInDark: Boolean): Boolean = when (normalizedThemeMode()) {
        "light" -> false
        "dark" -> true
        else -> systemInDark
    }

    fun toVisibilityMask(): Int {
        var mask = 0
        if (showSafeToSpend) mask = mask or 1
        if (showAccountsCard) mask = mask or 2
        if (showQuickPresetsCard) mask = mask or 4
        if (showWeekSummaryRow) mask = mask or 8
        if (showMonthlyReviewCard) mask = mask or 16
        if (showBillsCard) mask = mask or 32
        if (showSmartInsightsCard) mask = mask or 64
        return mask
    }

    fun toPhase6VisibilityMask(): Int {
        var mask = 0
        if (showForecastCard) mask = mask or 1
        if (showUpcomingRecurringCard) mask = mask or 2
        if (showRecentTransactionsCard) mask = mask or 4
        return mask
    }

    fun normalizedOrder(): List<String> {
        val valid = dashboardOrder.filter { it in DEFAULT_DASHBOARD_ORDER }.distinct()
        val missing = DEFAULT_DASHBOARD_ORDER.filter { it !in valid }
        return valid + missing
    }

    fun resolvedDashboardOrder(): List<String> {
        val validExt = extendedDashboardOrder.filter { it in DEFAULT_EXTENDED_DASHBOARD_ORDER }.distinct()
        val missingExt = DEFAULT_EXTENDED_DASHBOARD_ORDER.filter { it !in validExt }
        val normalizedExt = validExt + missingExt
        if (normalizedExt != DEFAULT_EXTENDED_DASHBOARD_ORDER) {
            return normalizedExt
        }
        val legacyNorm = normalizedOrder()
        if (legacyNorm != DEFAULT_DASHBOARD_ORDER) {
            return listOf("forecast", "upcoming_recurring") + legacyNorm + listOf("recent_transactions")
        }
        return DEFAULT_EXTENDED_DASHBOARD_ORDER
    }

    fun isSectionVisible(sectionKey: String): Boolean = when (sectionKey) {
        "forecast" -> showForecastCard
        "upcoming_recurring" -> showUpcomingRecurringCard
        "accounts" -> showAccountsCard
        "presets" -> showQuickPresetsCard
        "insights" -> showSmartInsightsCard
        "bills" -> showBillsCard
        "week_summary" -> showWeekSummaryRow
        "monthly_review" -> showMonthlyReviewCard
        "recent_transactions" -> showRecentTransactionsCard
        else -> true
    }

    fun withSectionVisibility(sectionKey: String, visible: Boolean): CustomizationPrefs = when (sectionKey) {
        "forecast" -> copy(showForecastCard = visible)
        "upcoming_recurring" -> copy(showUpcomingRecurringCard = visible)
        "accounts" -> copy(showAccountsCard = visible)
        "presets" -> copy(showQuickPresetsCard = visible)
        "insights" -> copy(showSmartInsightsCard = visible)
        "bills" -> copy(showBillsCard = visible)
        "week_summary" -> copy(showWeekSummaryRow = visible)
        "monthly_review" -> copy(showMonthlyReviewCard = visible)
        "recent_transactions" -> copy(showRecentTransactionsCard = visible)
        else -> this
    }

    fun withMovedSection(sectionKey: String, direction: Int): CustomizationPrefs {
        val currentExt = resolvedDashboardOrder().toMutableList()
        val idx = currentExt.indexOf(sectionKey)
        if (idx == -1) return this
        val target = (idx + direction).coerceIn(0, currentExt.lastIndex)
        if (target == idx) return this
        val removed = currentExt.removeAt(idx)
        currentExt.add(target, removed)
        val updatedLegacy = currentExt.filter { it in DEFAULT_DASHBOARD_ORDER }
        return copy(
            dashboardOrder = if (updatedLegacy.size == DEFAULT_DASHBOARD_ORDER.size) updatedLegacy else normalizedOrder(),
            extendedDashboardOrder = currentExt
        )
    }

    fun resetDashboardLayout(): CustomizationPrefs = copy(
        showSafeToSpend = true,
        showAccountsCard = true,
        showQuickPresetsCard = true,
        showWeekSummaryRow = true,
        showMonthlyReviewCard = true,
        showBillsCard = true,
        showSmartInsightsCard = true,
        showForecastCard = true,
        showUpcomingRecurringCard = true,
        showRecentTransactionsCard = true,
        dashboardOrder = DEFAULT_DASHBOARD_ORDER,
        extendedDashboardOrder = DEFAULT_EXTENDED_DASHBOARD_ORDER
    )

    fun encodeSubtitlePipe(): String {
        val legacyOrder = normalizedOrder().joinToString(",")
        val extOrder = resolvedDashboardOrder().joinToString(",")
        return "$accentPalette|$currencySymbol|$legacyOrder|${normalizedThemeMode()}|${normalizedEurRate()}|${normalizedUsdRate()}|${toPhase6VisibilityMask()}|$extOrder"
    }

    companion object {
        val DEFAULT_DASHBOARD_ORDER = listOf(
            "accounts",
            "presets",
            "insights",
            "bills",
            "week_summary",
            "monthly_review"
        )

        val DEFAULT_EXTENDED_DASHBOARD_ORDER = listOf(
            "forecast",
            "upcoming_recurring",
            "accounts",
            "presets",
            "insights",
            "bills",
            "week_summary",
            "monthly_review",
            "recent_transactions"
        )

        fun normalizeThemeMode(raw: String?): String = when (raw?.trim()?.lowercase(Locale.ROOT)) {
            "light" -> "light"
            "dark" -> "dark"
            else -> "system"
        }

        fun normalizeOrderString(rawOrder: String?): List<String> {
            if (rawOrder.isNullOrBlank()) return DEFAULT_DASHBOARD_ORDER
            val parsed = rawOrder.split(",").map { it.trim() }.filter { it in DEFAULT_DASHBOARD_ORDER }.distinct()
            val missing = DEFAULT_DASHBOARD_ORDER.filter { it !in parsed }
            return parsed + missing
        }

        fun normalizeExtendedOrderString(rawOrder: String?): List<String> {
            if (rawOrder.isNullOrBlank()) return DEFAULT_EXTENDED_DASHBOARD_ORDER
            val parsed = rawOrder.split(",").map { it.trim() }.filter { it in DEFAULT_EXTENDED_DASHBOARD_ORDER }.distinct()
            val missing = DEFAULT_EXTENDED_DASHBOARD_ORDER.filter { it !in parsed }
            return parsed + missing
        }

        fun fromFinancialItem(item: FinancialItem?): CustomizationPrefs {
            if (item == null) return CustomizationPrefs()
            val parts = item.subtitle.split("|")
            val palette = parts.getOrNull(0)?.ifBlank { "majorelle" } ?: "majorelle"
            val currency = parts.getOrNull(1)?.ifBlank { "DH" } ?: "DH"
            val orderList = normalizeOrderString(parts.getOrNull(2))
            val parsedThemeMode = normalizeThemeMode(parts.getOrNull(3))
            val parsedEurRate = parts.getOrNull(4)?.replace(",", ".")?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it in 0.5..500.0 } ?: 10.85
            val parsedUsdRate = parts.getOrNull(5)?.replace(",", ".")?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it in 0.5..500.0 } ?: 9.95
            val rawPhase6Mask = parts.getOrNull(6)?.trim()?.toIntOrNull()
            val showForecast = if (rawPhase6Mask != null) (rawPhase6Mask and 1) != 0 else true
            val showUpcoming = if (rawPhase6Mask != null) (rawPhase6Mask and 2) != 0 else true
            val showRecent = if (rawPhase6Mask != null) (rawPhase6Mask and 4) != 0 else true
            val extOrder = normalizeExtendedOrderString(parts.getOrNull(7))
            val rawMask = if (item.dayOrTurn > 0) item.dayOrTurn else 127
            val hasInsightsBit = item.dayOrTurn >= 64 || item.dayOrTurn <= 0
            return CustomizationPrefs(
                accentPalette = palette,
                currencySymbol = currency,
                customTitle = item.name.ifBlank { "Masroufi" },
                customSubtitle = item.dateOrCycle,
                showSafeToSpend = (rawMask and 1) != 0,
                showAccountsCard = (rawMask and 2) != 0,
                showQuickPresetsCard = (rawMask and 4) != 0,
                showWeekSummaryRow = (rawMask and 8) != 0,
                showMonthlyReviewCard = (rawMask and 16) != 0,
                showBillsCard = (rawMask and 32) != 0,
                showSmartInsightsCard = if (hasInsightsBit) (rawMask and 64) != 0 else true,
                showForecastCard = showForecast,
                showUpcomingRecurringCard = showUpcoming,
                showRecentTransactionsCard = showRecent,
                dashboardOrder = orderList,
                extendedDashboardOrder = extOrder,
                themeMode = parsedThemeMode,
                eurToMadRate = parsedEurRate,
                usdToMadRate = parsedUsdRate
            )
        }
    }
}
