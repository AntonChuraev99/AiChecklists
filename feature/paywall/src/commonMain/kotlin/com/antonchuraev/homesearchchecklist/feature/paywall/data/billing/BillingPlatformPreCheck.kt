package com.antonchuraev.homesearchchecklist.feature.paywall.data.billing

interface BillingPlatformPreCheck {
    suspend fun check(): PreCheckResult
}

sealed interface PreCheckResult {
    data object Ok : PreCheckResult
    data class Failed(
        val reason: PreCheckFailReason,
        val debugMessage: String,
    ) : PreCheckResult
}

enum class PreCheckFailReason {
    GoogleApiUnavailable,
    ProductDetailsUnsupported,
}
