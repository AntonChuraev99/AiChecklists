package com.antonchuraev.homesearchchecklist.feature.paywall.domain

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult

/**
 * Logs purchase conversion / revenue events for Google Analytics & Google Ads.
 *
 * Three event types:
 * - "free_trial_start" — user starts a free trial (Google Ads primary conversion).
 * - "in_app_purchase"  — direct purchase without trial (existing Google Ads conversion).
 * - "purchase"         — GA4 RESERVED ecommerce event; the only one GA4 aggregates into
 *                        revenue / ROAS. Fired for DIRECT (non-trial) payments with the real
 *                        price and currency from the store product.
 *
 * All names route through [AnalyticsTracker.event] → Firebase Analytics (Google) + Amplitude.
 */
class ConversionEventHelper(
    private val analyticsTracker: AnalyticsTracker,
    private val logger: AppLogger? = null,
) {

    fun logConversionEvent(result: PurchaseResult.Success, product: PaywallProduct) {
        if (product.hasFreeTrial) {
            analyticsTracker.event(EVENT_FREE_TRIAL_START, mapOf(
                PARAM_ITEM_ID to product.id,
                PARAM_ITEM_NAME to PRODUCT_NAME,
                PARAM_TRIAL_DURATION to "${product.freeTrialDays}_days",
                PARAM_VALUE to 0.0,
                PARAM_CURRENCY to CURRENCY_USD
            ))
        } else {
            val params = mutableMapOf<String, Any>(
                PARAM_ITEM_ID to product.id,
                PARAM_ITEM_NAME to PRODUCT_NAME,
                PARAM_CURRENCY to CURRENCY_USD,
                PARAM_VALUE to PRICE_USD
            )
            result.transactionId?.let { params[PARAM_TRANSACTION_ID] = it }
            analyticsTracker.event(EVENT_IN_APP_PURCHASE, params)

            // GA4 standard ecommerce `purchase` — the ONLY event GA4 aggregates into
            // revenue / Google Ads ROAS. Additive to in_app_purchase above; carries the
            // REAL price + currency from the store product (not the hardcoded 1.99/USD),
            // so yearly plans and local currencies report correct revenue.
            // Intentionally DIRECT-payment only: a trial start has no charge today, so it
            // stays free_trial_start only — no premature/fake revenue in GA4 for trials.
            logStandardPurchaseEvent(result, product)
        }
    }

    /**
     * Fires the GA4-reserved `purchase` event with real revenue data.
     *
     * Skipped (with a warning) when transaction_id or currency is missing:
     * GA4 silently drops revenue for a `purchase` that has a value but no currency,
     * and the April-2026 24h dedup window requires a real, unique transaction_id.
     * Sandbox purchases (null transactionId) are excluded from GA4 revenue regardless.
     */
    private fun logStandardPurchaseEvent(result: PurchaseResult.Success, product: PaywallProduct) {
        val transactionId = result.transactionId
        if (transactionId.isNullOrBlank()) {
            logger?.warning(TAG, "GA4 purchase skipped: transactionId is null/blank (sandbox?) sku=${product.id}")
            return
        }
        if (product.priceCurrencyCode.isBlank()) {
            logger?.warning(TAG, "GA4 purchase skipped: blank currency code for sku=${product.id}")
            return
        }
        analyticsTracker.event(
            EVENT_PURCHASE,
            mapOf(
                PARAM_TRANSACTION_ID to transactionId,
                PARAM_VALUE to product.priceAmount,
                PARAM_CURRENCY to product.priceCurrencyCode,
            ),
        )
    }

    companion object {
        const val EVENT_FREE_TRIAL_START = "free_trial_start"
        const val EVENT_IN_APP_PURCHASE = "in_app_purchase"

        /** GA4 reserved ecommerce event name — recognized for revenue aggregation. */
        const val EVENT_PURCHASE = "purchase"

        const val PARAM_ITEM_ID = "item_id"
        const val PARAM_ITEM_NAME = "item_name"
        const val PARAM_TRIAL_DURATION = "trial_duration"
        const val PARAM_VALUE = "value"
        const val PARAM_CURRENCY = "currency"
        const val PARAM_TRANSACTION_ID = "transaction_id"

        const val PRODUCT_NAME = "Premium Monthly"
        const val CURRENCY_USD = "USD"
        const val PRICE_USD = 1.99

        private const val TAG = "ConversionEvent"
    }
}
