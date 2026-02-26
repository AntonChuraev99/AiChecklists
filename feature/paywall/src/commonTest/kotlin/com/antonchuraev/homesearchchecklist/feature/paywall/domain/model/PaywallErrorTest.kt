package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaywallErrorTest {

    @Test
    fun paywallException_preserves_errorCode_and_message() {
        val exception = PaywallException(
            errorCode = PaywallErrorCode.NETWORK_ERROR,
            message = "Error performing request."
        )

        assertEquals(PaywallErrorCode.NETWORK_ERROR, exception.errorCode)
        assertEquals("Error performing request.", exception.message)
        assertNull(exception.underlyingError)
    }

    @Test
    fun paywallException_preserves_underlyingError() {
        val exception = PaywallException(
            errorCode = PaywallErrorCode.STORE_PROBLEM,
            underlyingError = "HTTP 503 Service Unavailable",
            message = "There was a problem with the store."
        )

        assertEquals(PaywallErrorCode.STORE_PROBLEM, exception.errorCode)
        assertEquals("HTTP 503 Service Unavailable", exception.underlyingError)
        assertEquals("There was a problem with the store.", exception.message)
    }

    @Test
    fun paywallException_is_exception_subclass() {
        val exception = PaywallException(
            errorCode = PaywallErrorCode.CONFIGURATION_ERROR,
            message = "Config error"
        )

        assertTrue(exception is Exception)
    }

    @Test
    fun paywallErrorCode_has_all_expected_values() {
        val codes = PaywallErrorCode.entries
        assertEquals(9, codes.size)
        assertTrue(codes.contains(PaywallErrorCode.NETWORK_ERROR))
        assertTrue(codes.contains(PaywallErrorCode.OFFLINE))
        assertTrue(codes.contains(PaywallErrorCode.STORE_PROBLEM))
        assertTrue(codes.contains(PaywallErrorCode.UNKNOWN_BACKEND))
        assertTrue(codes.contains(PaywallErrorCode.CONFIGURATION_ERROR))
        assertTrue(codes.contains(PaywallErrorCode.INVALID_CREDENTIALS))
        assertTrue(codes.contains(PaywallErrorCode.PRODUCT_NOT_AVAILABLE))
        assertTrue(codes.contains(PaywallErrorCode.NOT_CONFIGURED))
        assertTrue(codes.contains(PaywallErrorCode.UNKNOWN))
    }
}
