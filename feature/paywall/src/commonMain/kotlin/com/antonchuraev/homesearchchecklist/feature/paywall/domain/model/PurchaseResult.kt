package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

sealed interface PurchaseResult {
    data class Success(
        val subscriptionStatus: SubscriptionStatus,
        val transactionId: String? = null,
        val hasFreeTrial: Boolean = false
    ) : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Error(
        val message: String,
        val errorCode: String? = null,
        val underlyingError: String? = null
    ) : PurchaseResult
}

sealed interface RestoreResult {
    data class Success(val subscriptionStatus: SubscriptionStatus) : RestoreResult
    data object NoActiveSubscription : RestoreResult
    data class Error(
        val message: String,
        val errorCode: String? = null,
        val underlyingError: String? = null
    ) : RestoreResult
}
