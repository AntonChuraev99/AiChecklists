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
    // Must match the entitlement Identifier configured in RevenueCat Dashboard
    // (Project → Entitlements). Mismatch silently sets revenueCatPremium=false
    // everywhere, which is hard to spot because firestorePremium (DataStore)
    // usually papers over it via restoreCreditsAfterPurchase.
    const val PREMIUM = "AiChecklists Pro"
}
