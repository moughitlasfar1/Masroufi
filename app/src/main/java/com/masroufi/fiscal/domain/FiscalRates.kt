package com.masroufi.fiscal.domain

/**
 * Centralized Moroccan fiscal & Zakat reference values.
 *
 * IMPORTANT:
 * - Every rate carries an explicit "as of" date and source comment.
 * - Vignette (TSAVA) bands above 10 CV are intentionally left as `null` (`TODO`)
 *   so no unverified tax figures are ever invented; the UI unlocks manual MAD input for those bands.
 * - Zakat metal prices are offline starter placeholders that the user edits locally in settings.
 *   No network requests are ever made.
 */
object FiscalRates {

    /** Effective date for the starter Vignette (TSAVA) tariff table below. */
    const val VIGNETTE_RATES_AS_OF_ISO: String = "2026-01-01"

    /** Official source reference to verify against before updating tariff values. */
    const val VIGNETTE_SOURCE_COMMENT: String =
        "DGI Maroc — Taxe Spéciale Annuelle sur les Véhicules (TSAVA / Vignette), Loi de Finances; vérifier les tranches > 10 CV avant publication."

    enum class FuelType(val key: String) {
        ESSENCE("essence"),
        DIESEL("diesel");

        companion object {
            fun fromKey(raw: String?): FuelType =
                entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) } ?: ESSENCE
        }
    }

    enum class HorsepowerBand(val key: String) {
        UNDER_8_CV("under_8"),
        CV_8_TO_10("8_to_10"),
        // TODO(DGI): Verify official DGI TSAVA tariff for 11–14 CV (essence & diesel) before hardcoding.
        CV_11_TO_14("11_to_14"),
        // TODO(DGI): Verify official DGI TSAVA tariff for 15+ CV (essence & diesel) before hardcoding.
        CV_15_PLUS("15_plus");

        companion object {
            fun fromKey(raw: String?): HorsepowerBand =
                entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) } ?: UNDER_8_CV
        }
    }

    /**
     * Returns the starter annual Vignette (TSAVA) amount in MAD, or `null` when the band is marked
     * `TODO` and requires manual user entry verified against DGI.
     */
    fun vignetteAnnualAmountMad(band: HorsepowerBand, fuel: FuelType): Double? = when (band) {
        HorsepowerBand.UNDER_8_CV -> when (fuel) {
            FuelType.ESSENCE -> 350.0
            FuelType.DIESEL -> 700.0
        }
        HorsepowerBand.CV_8_TO_10 -> when (fuel) {
            FuelType.ESSENCE -> 650.0
            FuelType.DIESEL -> 1_500.0
        }
        // TODO(DGI): Do not invent rates for 11–14 CV; require manual user override until verified.
        HorsepowerBand.CV_11_TO_14 -> null
        // TODO(DGI): Do not invent rates for 15+ CV; require manual user override until verified.
        HorsepowerBand.CV_15_PLUS -> null
    }

    // -------------------------------------------------------------------------
    // Zakat constants & offline starter metal prices (user-editable, no network)
    // -------------------------------------------------------------------------

    /** Standard Zakat rate on eligible wealth held for one lunar year (Hawl): 2.5%. */
    const val ZAKAT_RATE: Double = 0.025

    /** Classical Nisab weight in pure gold (24K equivalent): 85 grams. */
    const val NISAB_GOLD_GRAMS: Double = 85.0

    /** Alternative Nisab weight in pure silver: 595 grams. */
    const val NISAB_SILVER_GRAMS: Double = 595.0

    /**
     * Starter offline placeholder for gold price (MAD per gram).
     * Placeholder to verify locally — user edits directly in the Zakat screen.
     */
    const val DEFAULT_GOLD_PRICE_MAD_PER_GRAM: Double = 900.0

    /**
     * Starter offline placeholder for silver price (MAD per gram).
     * Placeholder to verify locally — user edits directly in the Zakat screen.
     */
    const val DEFAULT_SILVER_PRICE_MAD_PER_GRAM: Double = 11.0

    /** Effective "as of" date for the starter metal price placeholders. */
    const val DEFAULT_METAL_PRICES_AS_OF_ISO: String = "2026-01-01"

    /** Source comment reminding that metal prices are user-maintained offline estimates. */
    const val ZAKAT_METAL_SOURCE_COMMENT: String =
        "Prix indicatifs hors-ligne à vérifier auprès du marché marocain (Bank Al-Maghrib / bijouterie) et à ajuster manuellement."
}
