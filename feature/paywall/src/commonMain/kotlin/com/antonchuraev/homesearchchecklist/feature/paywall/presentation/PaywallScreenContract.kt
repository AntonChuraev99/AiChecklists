package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct

data class PaywallState(
    val isLoading: Boolean = true,
    val isPurchasing: Boolean = false,
    val products: List<PaywallProduct> = emptyList(),
    val selectedProductId: String? = null,
    val error: String? = null,
    val purchaseSuccess: Boolean = false,
    val source: String = "unknown"
) : State

sealed interface PaywallIntent : Intent {
    data object LoadProducts : PaywallIntent
    data class SelectProduct(val productId: String) : PaywallIntent
    data object Purchase : PaywallIntent
    data object RestorePurchases : PaywallIntent
    data object DismissError : PaywallIntent
    data object Close : PaywallIntent
}
