package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PurchaseResultTest {

    @Test
    fun purchaseResult_error_singleArg_backward_compatible() {
        val error = PurchaseResult.Error("Something went wrong")

        assertEquals("Something went wrong", error.message)
        assertNull(error.errorCode)
        assertNull(error.underlyingError)
    }

    @Test
    fun purchaseResult_error_with_full_details() {
        val error = PurchaseResult.Error(
            message = "Error performing request.",
            errorCode = "NetworkError",
            underlyingError = "java.net.UnknownHostException"
        )

        assertEquals("Error performing request.", error.message)
        assertEquals("NetworkError", error.errorCode)
        assertEquals("java.net.UnknownHostException", error.underlyingError)
    }

    @Test
    fun purchaseResult_error_is_sealed_interface_member() {
        val error: PurchaseResult = PurchaseResult.Error("test")
        assertTrue(error is PurchaseResult.Error)
    }

    @Test
    fun purchaseResult_success_defaultValues_noMetadata() {
        val status = SubscriptionStatus(isActive = true, activeEntitlements = setOf("premium"))
        val success = PurchaseResult.Success(subscriptionStatus = status)

        assertNull(success.transactionId)
        assertFalse(success.hasFreeTrial)
        assertTrue(success.subscriptionStatus.isActive)
    }

    @Test
    fun purchaseResult_success_withTrialMetadata() {
        val status = SubscriptionStatus(isActive = true, activeEntitlements = setOf("premium"))
        val success = PurchaseResult.Success(
            subscriptionStatus = status,
            transactionId = null,
            hasFreeTrial = true
        )

        assertNull(success.transactionId)
        assertTrue(success.hasFreeTrial)
    }

    @Test
    fun purchaseResult_success_withTransactionId() {
        val status = SubscriptionStatus(isActive = true, activeEntitlements = setOf("premium"))
        val success = PurchaseResult.Success(
            subscriptionStatus = status,
            transactionId = "GPA.1234-5678-9012",
            hasFreeTrial = false
        )

        assertEquals("GPA.1234-5678-9012", success.transactionId)
        assertFalse(success.hasFreeTrial)
    }

    @Test
    fun purchaseResult_success_isCheck_worksWithDataClass() {
        val status = SubscriptionStatus(isActive = true, activeEntitlements = setOf("premium"))
        val result: PurchaseResult = PurchaseResult.Success(
            subscriptionStatus = status,
            transactionId = "GPA.test",
            hasFreeTrial = true
        )

        assertTrue(result is PurchaseResult.Success)
        if (result is PurchaseResult.Success) {
            assertEquals("GPA.test", result.transactionId)
            assertTrue(result.hasFreeTrial)
        }
    }

    @Test
    fun restoreResult_error_singleArg_backward_compatible() {
        val error = RestoreResult.Error("Restore failed")

        assertEquals("Restore failed", error.message)
        assertNull(error.errorCode)
        assertNull(error.underlyingError)
    }

    @Test
    fun restoreResult_error_with_full_details() {
        val error = RestoreResult.Error(
            message = "There was a problem with the store.",
            errorCode = "StoreProblemError",
            underlyingError = "billing service disconnected"
        )

        assertEquals("There was a problem with the store.", error.message)
        assertEquals("StoreProblemError", error.errorCode)
        assertEquals("billing service disconnected", error.underlyingError)
    }

    @Test
    fun restoreResult_error_is_sealed_interface_member() {
        val error: RestoreResult = RestoreResult.Error("test")
        assertTrue(error is RestoreResult.Error)
    }
}
