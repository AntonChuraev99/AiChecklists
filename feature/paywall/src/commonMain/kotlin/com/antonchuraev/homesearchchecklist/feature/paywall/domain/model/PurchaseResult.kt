package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

sealed interface PurchaseResult {
    data class Success(val subscriptionStatus: SubscriptionStatus) : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Error(val message: String) : PurchaseResult
}

sealed interface RestoreResult {
    data class Success(val subscriptionStatus: SubscriptionStatus) : RestoreResult
    data object NoActiveSubscription : RestoreResult
    data class Error(val message: String) : RestoreResult
}
