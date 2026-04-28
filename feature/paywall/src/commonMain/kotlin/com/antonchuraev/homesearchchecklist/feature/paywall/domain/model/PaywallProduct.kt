package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

data class PaywallProduct(
    val id: String,
    val title: String,
    val description: String,
    val priceString: String,
    /** Numeric price in the product's currency (e.g. 20.0 for "$20.00"). Used for savings % calculation. */
    val priceAmount: Double = 0.0,
    /** ISO 4217 currency code (e.g. "USD"). Used for cross-currency consistency checks. */
    val priceCurrencyCode: String = "",
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
