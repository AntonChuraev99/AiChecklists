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
    UNKNOWN;
}

/**
 * Rich error wrapper for paywall operations.
 * Preserves error code and underlying details from RevenueCat SDK.
 */
class PaywallException(
    val errorCode: PaywallErrorCode,
    val underlyingError: String? = null,
    message: String
) : Exception(message)
