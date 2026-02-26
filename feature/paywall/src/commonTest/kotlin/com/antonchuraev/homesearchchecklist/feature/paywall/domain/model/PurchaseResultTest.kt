package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
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
