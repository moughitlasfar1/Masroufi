package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Locale

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

    private val AUTRE_ALIASES = setOf(
        "autre",
        "env_autre",
        "autre / imprévus",
        "autre / imprevus",
        "général",
        "general",
        "other / unplanned",
        "أخرى / مصاريف غير متوقعة",
        "حاجة خرى / مكنتش حاسبها"
    )

    fun stripNumericPrefix(raw: String): String {
        return raw.trim().replace(NUMERIC_PREFIX_REGEX, "").trim()
    }

    fun canonicalDefaultId(rawNameOrKey: String): String? {
        val cleaned = stripNumericPrefix(rawNameOrKey).lowercase(Locale.ROOT)
        if (cleaned.isEmpty()) return null
        return when (cleaned) {
            in TRANSPORT_ALIASES -> "transport"
            in CHARGES_ALIASES -> "charges"
            in CAFE_ALIASES -> "cafe"
            in EPARGNE_ALIASES -> "epargne"
            in AUTRE_ALIASES -> "autre"
            else -> null
        }
    }

    fun localizedDefaultName(canonicalId: String, lang: String): String? {
        val normId = canonicalDefaultId(canonicalId) ?: canonicalId.trim().lowercase(Locale.ROOT)
        return when (normId) {
            "transport" -> when (lang) {
                "ar" -> "النقل والتنقل (القطار، سيارة الأجرة)"
                "darija" -> "الخدمة والتنقل (التران، الطاكسي)"
                "en" -> "Commute & Transit (Train, Taxi)"
                else -> "Transport & Déplacement (ONCF, Taxi)"
            }
            "charges" -> when (lang) {
                "ar" -> "الالتزامات الثابتة والمنزل"
                "darija" -> "التزامات قارة ومصاريف الدار"
                "en" -> "Fixed Bills & Household"
                else -> "Charges fixes & Maison (Factures)"
            }
            "cafe" -> when (lang) {
                "ar" -> "مقهى وخروج (شخصي وترفيه)"
                "darija" -> "قهوة وخروج (مصروف شخصي)"
                "en" -> "Café & Outings (Personal & Leisure)"
                else -> "Café & Sorties (Personnel & Loisirs)"
            }
            "epargne" -> when (lang) {
                "ar" -> "الادخار والاستثمار"
                "darija" -> "التوفير والادخار"
                "en" -> "Savings & Investments"
                else -> "Épargne & Investissement"
            }
            "autre" -> when (lang) {
                "ar" -> "أخرى / مصاريف غير متوقعة"
                "darija" -> "حاجة خرى / مكنتش حاسبها"
                "en" -> "Other / Unplanned"
                else -> "Autre / Imprévus"
            }
            else -> null
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
        return localizedDefaultName("autre", lang) ?: "Autre / Imprévus"
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val baseBudget: Double = 6400.0,
    val payCycleDay: Int = 1,
    val rolloverEnabled: Boolean = true,
    val language: String = "fr",
    @get:PropertyName("isDarkTheme")
    val isDarkTheme: Boolean = false,
    val seeded: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TransactionItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = "expense", // "expense", "income", "transfer"
    val amount: Double = 0.0,
    val category: String = "autre",
    val envelopeKey: String = "autre",
    val accountId: String = "acc_cash",
    val toAccountId: String = "",
    val desc: String = "",
    val isoDate: String = "", // YYYY-MM-DD
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun localizedCategory(lang: String, envelopes: List<EnvelopeConfig> = emptyList()): String {
        return DefaultCategoryResolver.resolveTransactionCategoryDisplay(
            rawCategory = category,
            rawEnvelopeKey = envelopeKey,
            envelopes = envelopes,
            lang = lang
        )
    }
}

@IgnoreExtraProperties
data class AccountItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val kind: String = "cash", // "cash", "bank", "wallet"
    val initialBalance: Double = 0.0,
    val colorHex: String = "#10B981",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EnvelopeConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val key: String = "",
    val nameFr: String = "",
    val nameAr: String = "",
    val nameDarija: String = "",
    val nameEn: String = "",
    val percentage: Double = 0.0,
    @get:PropertyName("hasCap")
    val hasCap: Boolean = true,
    val colorHex: String = "#2563EB",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
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

@IgnoreExtraProperties
data class FinancialItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemType: String = "goal", // "goal", "bill", "debt", "daret", "seasonal", "preset", "custom_prefs"
    val name: String = "",
    val subtitle: String = "", // personName for debt, category for preset, eventType for seasonal, palette|currency for custom_prefs
    val amount: Double = 0.0, // target / bill amount / debt total / monthlyContribution / preset amount
    val secondaryAmount: Double = 0.0, // savedAmount / paidAmount / totalMembers
    val dayOrTurn: Int = 1, // dueDay (1..31) / myTurnNumber / bitmask for visible cards
    val extraInt: Int = 0, // paidMonthsCount for Daret
    val dateOrCycle: String = "", // deadlineIso / payoutDateIso / comma-separated paidCycles / "lent"|"borrowed" / customSubtitle
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

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
    val dashboardOrder: List<String> = DEFAULT_DASHBOARD_ORDER
) {
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

    fun normalizedOrder(): List<String> {
        val valid = dashboardOrder.filter { it in DEFAULT_DASHBOARD_ORDER }.distinct()
        val missing = DEFAULT_DASHBOARD_ORDER.filter { it !in valid }
        return valid + missing
    }

    fun isSectionVisible(sectionKey: String): Boolean = when (sectionKey) {
        "accounts" -> showAccountsCard
        "presets" -> showQuickPresetsCard
        "insights" -> showSmartInsightsCard
        "bills" -> showBillsCard
        "week_summary" -> showWeekSummaryRow
        "monthly_review" -> showMonthlyReviewCard
        else -> true
    }

    fun withSectionVisibility(sectionKey: String, visible: Boolean): CustomizationPrefs = when (sectionKey) {
        "accounts" -> copy(showAccountsCard = visible)
        "presets" -> copy(showQuickPresetsCard = visible)
        "insights" -> copy(showSmartInsightsCard = visible)
        "bills" -> copy(showBillsCard = visible)
        "week_summary" -> copy(showWeekSummaryRow = visible)
        "monthly_review" -> copy(showMonthlyReviewCard = visible)
        else -> this
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

        fun normalizeOrderString(rawOrder: String?): List<String> {
            if (rawOrder.isNullOrBlank()) return DEFAULT_DASHBOARD_ORDER
            val parsed = rawOrder.split(",").map { it.trim() }.filter { it in DEFAULT_DASHBOARD_ORDER }.distinct()
            val missing = DEFAULT_DASHBOARD_ORDER.filter { it !in parsed }
            return parsed + missing
        }

        fun fromFinancialItem(item: FinancialItem?): CustomizationPrefs {
            if (item == null) return CustomizationPrefs()
            val parts = item.subtitle.split("|")
            val palette = parts.getOrNull(0)?.ifBlank { "majorelle" } ?: "majorelle"
            val currency = parts.getOrNull(1)?.ifBlank { "DH" } ?: "DH"
            val orderList = normalizeOrderString(parts.getOrNull(2))
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
                dashboardOrder = orderList
            )
        }
    }
}
