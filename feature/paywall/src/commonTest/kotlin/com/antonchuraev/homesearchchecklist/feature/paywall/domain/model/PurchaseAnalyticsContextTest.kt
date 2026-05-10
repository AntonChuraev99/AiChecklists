package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PurchaseAnalyticsContextTest {

    private val fullContext = PurchaseAnalyticsContext(
        country = "US",
        appVersion = "1.14.4",
        playStoreVersion = "41.3.18",
        billingClientReady = true,
        offeringId = "default",
        packageCount = 2,
        availableSkuIds = listOf("premium_yearly", "premium_monthly"),
    )

    private val failureContext = PurchaseAnalyticsContext(
        country = "ET",
        appVersion = "1.14.4",
        playStoreVersion = null,
        billingClientReady = false,
        offeringId = null,
        packageCount = 0,
        availableSkuIds = emptyList(),
    )

    // --- toEventParams() ---

    @Test
    fun toEventParams_includes_country_when_not_null() {
        val params = fullContext.toEventParams()
        assertEquals("US", params["country"])
    }

    @Test
    fun toEventParams_omits_country_when_null() {
        val ctx = fullContext.copy(country = null)
        val params = ctx.toEventParams()
        assertFalse(params.containsKey("country"))
    }

    @Test
    fun toEventParams_includes_billing_client_ready() {
        assertEquals(true, fullContext.toEventParams()["billing_client_ready"])
        assertEquals(false, failureContext.toEventParams()["billing_client_ready"])
    }

    @Test
    fun toEventParams_includes_offering_id_when_present() {
        val params = fullContext.toEventParams()
        assertEquals("default", params["offering_id"])
    }

    @Test
    fun toEventParams_omits_offering_id_when_null() {
        val params = failureContext.toEventParams()
        assertFalse(params.containsKey("offering_id"))
    }

    @Test
    fun toEventParams_includes_package_count() {
        assertEquals(2, fullContext.toEventParams()["package_count"])
        assertEquals(0, failureContext.toEventParams()["package_count"])
    }

    @Test
    fun toEventParams_includes_available_sku_ids_when_not_empty() {
        val params = fullContext.toEventParams()
        assertEquals("premium_yearly,premium_monthly", params["available_sku_ids"])
    }

    @Test
    fun toEventParams_omits_available_sku_ids_when_empty() {
        val params = failureContext.toEventParams()
        assertFalse(params.containsKey("available_sku_ids"))
    }

    @Test
    fun toEventParams_includes_play_store_version_when_not_null() {
        val params = fullContext.toEventParams()
        assertEquals("41.3.18", params["play_store_version"])
    }

    @Test
    fun toEventParams_omits_play_store_version_when_null() {
        val params = failureContext.toEventParams()
        assertFalse(params.containsKey("play_store_version"))
    }

    @Test
    fun toEventParams_truncates_sku_ids_at_100_chars() {
        val longSkus = List(20) { "premium_yearly_plan_${it}_with_very_long_suffix" }
        val ctx = fullContext.copy(availableSkuIds = longSkus)
        val params = ctx.toEventParams()
        val skuIdsParam = params["available_sku_ids"] as? String
        assertTrue(skuIdsParam != null && skuIdsParam.length <= 100)
    }

    // --- billing_client_ready in failure context ---

    @Test
    fun billingNotInitialized_context_has_billingClientReady_false() {
        // This is the critical case — race condition diagnosis.
        // When billingClientReady=false in products_load_failed,
        // it means BILLING_UNAVAILABLE was a timing issue, not a user error.
        assertFalse(failureContext.billingClientReady)
        val params = failureContext.toEventParams()
        assertEquals(false, params["billing_client_ready"])
    }

    @Test
    fun billingReady_context_distinguishes_network_errors() {
        // When billingClientReady=true in products_load_failed,
        // it means billing connected fine but backend/network failed.
        assertTrue(fullContext.billingClientReady)
        val params = fullContext.toEventParams()
        assertEquals(true, params["billing_client_ready"])
    }
}
