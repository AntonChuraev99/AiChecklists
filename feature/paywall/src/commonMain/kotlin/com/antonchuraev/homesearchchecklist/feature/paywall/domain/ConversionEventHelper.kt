package com.antonchuraev.homesearchchecklist.feature.paywall.domain

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult

/**
 * Logs Firebase conversion events for Google Ads campaign optimization.
 *
 * Two event types:
 * - "free_trial_start" — when user starts a free trial (primary conversion event)
 * - "in_app_purchase" — when user makes a direct purchase without trial (secondary)
 *
 * These standard Firebase event names are recognized by Google Ads
 * when imported as conversion actions.
 */
class ConversionEventHelper(private val analyticsTracker: AnalyticsTracker) {

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
        }
    }

    companion object {
        const val EVENT_FREE_TRIAL_START = "free_trial_start"
        const val EVENT_IN_APP_PURCHASE = "in_app_purchase"

        const val PARAM_ITEM_ID = "item_id"
        const val PARAM_ITEM_NAME = "item_name"
        const val PARAM_TRIAL_DURATION = "trial_duration"
        const val PARAM_VALUE = "value"
        const val PARAM_CURRENCY = "currency"
        const val PARAM_TRANSACTION_ID = "transaction_id"

        const val PRODUCT_NAME = "Premium Monthly"
        const val CURRENCY_USD = "USD"
        const val PRICE_USD = 1.99
    }
}
