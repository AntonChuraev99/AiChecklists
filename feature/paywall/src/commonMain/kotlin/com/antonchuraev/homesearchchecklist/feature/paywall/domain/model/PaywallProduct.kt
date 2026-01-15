package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

data class PaywallProduct(
    val id: String,
    val title: String,
    val description: String,
    val priceString: String,
    val periodString: String?,
    val packageId: String,
    val isPopular: Boolean = false,
    val hasFreeTrial: Boolean = false,
    val freeTrialDays: Int = 0
)

data class PaywallOffering(
    val id: String,
    val products: List<PaywallProduct>
)
