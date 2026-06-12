package com.antonchuraev.homesearchchecklist.feature.paywall.domain

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult

/**
 * Logs purchase conversion / revenue events for Google Analytics & Google Ads.
 *
 * Two event types:
 * - "free_trial_start" — user starts a free trial (Google Ads primary conversion).
 * - "purchase"         — GA4 RESERVED ecommerce event; the only one GA4 aggregates into
 *                        revenue / ROAS. Fired for DIRECT (non-trial) payments with the real
 *                        price and currency from the store product.
 *
 * The legacy manual "in_app_purchase" event was REMOVED 2026-06-12: it duplicated Firebase's
 * auto-collected `in_app_purchase` (Play Billing) and risked double-counting in GA4. The
 * GA4-reserved `purchase` event carries the real revenue, so any Google Ads conversion that
 * was keyed on `in_app_purchase` must be repointed to `purchase` in the Ads console — otherwise
 * that conversion action stops receiving events.
 *
 * All names route through [AnalyticsTracker.event] → Firebase Analytics (Google) + Amplitude.
 */
class ConversionEventHelper(
    private val analyticsTracker: AnalyticsTracker,
    private val logger: AppLogger? = null,
) {

    fun logConversionEvent(result: PurchaseResult.Success, product: PaywallProduct) {
        if (product.hasFreeTrial) {
            analyticsTracker.event(AnalyticsEvents.Conversion.FREE_TRIAL_START, mapOf(
                AnalyticsParams.ITEM_ID to product.id,
                AnalyticsParams.ITEM_NAME to PRODUCT_NAME,
                AnalyticsParams.TRIAL_DURATION to "${product.freeTrialDays}_days",
                AnalyticsParams.VALUE to 0.0,
                AnalyticsParams.CURRENCY to CURRENCY_USD
            ))
        } else {
            // GA4 standard ecommerce `purchase` — the ONLY event GA4 aggregates into
            // revenue / Google Ads ROAS. Carries the REAL price + currency from the store
            // product (not a hardcoded 1.99/USD), so yearly plans and local currencies report
            // correct revenue. DIRECT-payment only: a trial start has no charge today, so it
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
            AnalyticsEvents.Conversion.PURCHASE,
            mapOf(
                AnalyticsParams.TRANSACTION_ID to transactionId,
                AnalyticsParams.VALUE to product.priceAmount,
                AnalyticsParams.CURRENCY to product.priceCurrencyCode,
            ),
        )
    }

    companion object {
        // Event names and param keys are centralized in AnalyticsEvents.Conversion /
        // AnalyticsParams (see imports). These remaining consts are local VALUE constants
        // (not wire keys), so they stay here.
        const val PRODUCT_NAME = "Premium Monthly"
        const val CURRENCY_USD = "USD"

        private const val TAG = "ConversionEvent"
    }
}
