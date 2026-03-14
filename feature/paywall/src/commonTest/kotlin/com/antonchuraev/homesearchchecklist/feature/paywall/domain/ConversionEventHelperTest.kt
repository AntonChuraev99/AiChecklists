package com.antonchuraev.homesearchchecklist.feature.paywall.domain

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversionEventHelperTest {

    private val fakeTracker = FakeAnalyticsTracker()
    private val helper = ConversionEventHelper(fakeTracker)

    private val premiumStatus = SubscriptionStatus(
        isActive = true,
        activeEntitlements = setOf("premium")
    )

    private val testProduct = PaywallProduct(
        id = "premium_monthly",
        title = "Premium Monthly",
        description = "Premium Monthly",
        priceString = "$1.99",
        periodString = "1 month",
        packageId = "\$rc_monthly",
        hasFreeTrial = true,
        freeTrialDays = 3
    )

    // --- free_trial_start event ---

    @Test
    fun trialPurchase_firesTrialStartEvent() {
        val result = PurchaseResult.Success(
            subscriptionStatus = premiumStatus,
            hasFreeTrial = true
        )

        helper.logConversionEvent(result, testProduct)

        assertEquals(1, fakeTracker.events.size)
        assertEquals("free_trial_start", fakeTracker.events[0].first)
    }

    @Test
    fun trialPurchase_hasCorrectParams() {
        val result = PurchaseResult.Success(
            subscriptionStatus = premiumStatus,
            hasFreeTrial = true
        )

        helper.logConversionEvent(result, testProduct)

        val params = fakeTracker.events[0].second
        assertEquals("premium_monthly", params["item_id"])
        assertEquals("Premium Monthly", params["item_name"])
        assertEquals("3_days", params["trial_duration"])
        assertEquals(0.0, params["value"])
        assertEquals("USD", params["currency"])
        assertFalse(params.containsKey("transaction_id"))
    }

    // --- in_app_purchase event ---

    @Test
    fun directPurchase_firesPurchaseEvent() {
        val result = PurchaseResult.Success(
            subscriptionStatus = premiumStatus,
            transactionId = "GPA.1234-5678",
            hasFreeTrial = false
        )

        helper.logConversionEvent(result, testProduct)

        assertEquals(1, fakeTracker.events.size)
        assertEquals("in_app_purchase", fakeTracker.events[0].first)
    }

    @Test
    fun directPurchase_hasCorrectParams() {
        val result = PurchaseResult.Success(
            subscriptionStatus = premiumStatus,
            transactionId = "GPA.1234-5678",
            hasFreeTrial = false
        )

        helper.logConversionEvent(result, testProduct)

        val params = fakeTracker.events[0].second
        assertEquals("premium_monthly", params["item_id"])
        assertEquals("Premium Monthly", params["item_name"])
        assertEquals("USD", params["currency"])
        assertEquals(1.99, params["value"])
        assertEquals("GPA.1234-5678", params["transaction_id"])
    }

    @Test
    fun directPurchase_nullTransactionId_excludedFromParams() {
        val result = PurchaseResult.Success(
            subscriptionStatus = premiumStatus,
            transactionId = null,
            hasFreeTrial = false
        )

        helper.logConversionEvent(result, testProduct)

        val params = fakeTracker.events[0].second
        assertFalse(params.containsKey("transaction_id"))
        assertEquals("in_app_purchase", fakeTracker.events[0].first)
    }

    // --- event selection ---

    @Test
    fun trialPurchase_neverFiresPurchaseEvent() {
        val result = PurchaseResult.Success(
            subscriptionStatus = premiumStatus,
            hasFreeTrial = true
        )

        helper.logConversionEvent(result, testProduct)

        assertTrue(fakeTracker.events.none { it.first == "in_app_purchase" })
    }

    @Test
    fun directPurchase_neverFiresTrialEvent() {
        val result = PurchaseResult.Success(
            subscriptionStatus = premiumStatus,
            hasFreeTrial = false
        )

        helper.logConversionEvent(result, testProduct)

        assertTrue(fakeTracker.events.none { it.first == "free_trial_start" })
    }
}

/**
 * Fake AnalyticsTracker that records all events for assertion.
 */
private class FakeAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    val userProperties = mutableMapOf<String, Any>()
    private var _userId: String? = null
    var lastScreenView: String? = null

    override fun setUserId(userId: String) { _userId = userId }
    override fun setUserProperties(properties: Map<String, Any>) { userProperties.putAll(properties) }
    override fun screenView(name: String) { lastScreenView = name }
    override fun event(name: String, params: Map<String, Any>) { events.add(name to params) }
}
