package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

import com.antonchuraev.homesearchchecklist.feature.paywall.data.billing.PreCheckFailReason

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
    // Google Play Services not available on this device (emulator without GMS, Huawei, etc.)
    GMS_UNAVAILABLE,
    // BillingClient.FeatureType.PRODUCT_DETAILS not supported (old Play Store version).
    PRODUCT_DETAILS_UNSUPPORTED,
    UNKNOWN;
}

/**
 * Whether asking for the catalog again can plausibly succeed.
 *
 * `true` — transient: the network, the store backend, or Play Billing not connected yet.
 * `false` — the device or the project cannot sell right now, and no number of retries changes
 * that (no Google Play services, a Play Store too old for product details, a misconfigured
 * offering). Telling such a user to "check your connection" sends them to fix the wrong thing.
 *
 * Exhaustive on purpose (no `else`): a new code must be classified before it compiles.
 */
fun PaywallErrorCode.isRetryableCatalogError(): Boolean = when (this) {
    PaywallErrorCode.NETWORK_ERROR,
    PaywallErrorCode.OFFLINE,
    PaywallErrorCode.STORE_PROBLEM,
    PaywallErrorCode.UNKNOWN_BACKEND,
    PaywallErrorCode.BILLING_NOT_INITIALIZED,
    PaywallErrorCode.UNKNOWN -> true

    PaywallErrorCode.CONFIGURATION_ERROR,
    PaywallErrorCode.INVALID_CREDENTIALS,
    PaywallErrorCode.PRODUCT_NOT_AVAILABLE,
    PaywallErrorCode.NOT_CONFIGURED,
    PaywallErrorCode.GMS_UNAVAILABLE,
    PaywallErrorCode.PRODUCT_DETAILS_UNSUPPORTED -> false
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

// ---------------------------------------------------------------------------
// Mapping from pre-flight check results to domain error codes
// ---------------------------------------------------------------------------

fun PreCheckFailReason.toPaywallErrorCode(): PaywallErrorCode = when (this) {
    PreCheckFailReason.GoogleApiUnavailable -> PaywallErrorCode.GMS_UNAVAILABLE
    PreCheckFailReason.ProductDetailsUnsupported -> PaywallErrorCode.PRODUCT_DETAILS_UNSUPPORTED
}
