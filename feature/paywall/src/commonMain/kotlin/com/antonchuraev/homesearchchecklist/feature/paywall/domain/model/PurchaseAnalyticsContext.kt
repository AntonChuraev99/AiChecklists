package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

/**
 * Contextual diagnostics captured once per paywall session and attached to
 * purchase-related Amplitude events (paywall_opened, products_load_success,
 * products_load_failed, purchase_button_clicked, purchase_completed,
 * purchase_failed, purchase_cancelled, purchase_product_not_found).
 *
 * Designed to expose the blind spots revealed by the 2026-05-10 analytics audit:
 * - 11 BILLING_UNAVAILABLE in US → billingClientReady=false in products_load_failed
 * - 0 purchase_failed events → purchase_product_not_found had no sku context
 * - Country + Play Store version needed to diagnose regional availability gaps
 */
data class PurchaseAnalyticsContext(
    /** ISO 3166-1 alpha-2 country code from device locale (e.g. "US", "ET", "KZ"). */
    val country: String?,
    /** App versionName from build config (e.g. "1.14.4"). */
    val appVersion: String,
    /** Google Play Store versionName — available on Android only, null on iOS/web. */
    val playStoreVersion: String?,
    /** Whether RevenueCat (and underlying Play Billing) was ready when getOfferings() was called. */
    val billingClientReady: Boolean,
    /** RC offering identifier resolved by PaywallRepositoryImpl (e.g. "default"). */
    val offeringId: String?,
    /** Number of packages in the resolved offering. */
    val packageCount: Int,
    /** All SKU IDs available in the offering (for diagnosing missing yearly in some countries). */
    val availableSkuIds: List<String>,
) {
    /**
     * Returns a flat Map<String, Any> ready for Amplitude event properties.
     * All string values are truncated to 100 chars (Firebase Analytics limit).
     */
    fun toEventParams(): Map<String, Any> = buildMap {
        country?.let { put("country", it.take(100)) }
        put("app_version", appVersion.take(100))
        playStoreVersion?.let { put("play_store_version", it.take(100)) }
        put("billing_client_ready", billingClientReady)
        offeringId?.let { put("offering_id", it.take(100)) }
        put("package_count", packageCount)
        if (availableSkuIds.isNotEmpty()) {
            put("available_sku_ids", availableSkuIds.joinToString(",").take(100))
        }
    }
}
