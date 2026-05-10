package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

/**
 * Domain-safe error code for paywall operations.
 * Maps from RevenueCat PurchasesErrorCode at the repository boundary.
 */
enum class PaywallErrorCode {
    NETWORK_ERROR,
    OFFLINE,
    STORE_PROBLEM,
    UNKNOWN_BACKEND,
    CONFIGURATION_ERROR,
    INVALID_CREDENTIALS,
    PRODUCT_NOT_AVAILABLE,
    NOT_CONFIGURED,
    // Play Billing SDK not yet connected when getOfferings() was called.
    // Typically resolves on retry — shown in analytics as BILLING_UNAVAILABLE.
    BILLING_NOT_INITIALIZED,
    UNKNOWN;
}

/**
 * Rich error wrapper for paywall operations.
 * Preserves error code and underlying details from RevenueCat SDK.
 *
 * [billingWasReady] — whether Purchases.isConfigured was true when getOfferings() was called.
 * Used in analytics to distinguish "Play Billing not connected yet" from other errors.
 */
class PaywallException(
    val errorCode: PaywallErrorCode,
    val underlyingError: String? = null,
    val billingWasReady: Boolean = false,
    message: String
) : Exception(message)
