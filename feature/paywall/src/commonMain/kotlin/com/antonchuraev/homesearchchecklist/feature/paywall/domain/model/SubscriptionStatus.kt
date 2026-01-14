package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

data class SubscriptionStatus(
    val isActive: Boolean,
    val activeEntitlements: Set<String>,
    val expirationDate: Long? = null
) {
    companion object {
        val FREE = SubscriptionStatus(
            isActive = false,
            activeEntitlements = emptySet()
        )
    }
}

object Entitlements {
    const val PREMIUM = "premium"
}
