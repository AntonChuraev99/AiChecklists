package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetOfferingsUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.PurchaseProductUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.RestorePurchasesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PaywallViewModel(
    private val navigator: AppNavigator,
    private val getOfferingsUseCase: GetOfferingsUseCase,
    private val purchaseProductUseCase: PurchaseProductUseCase,
    private val restorePurchasesUseCase: RestorePurchasesUseCase
) : AppViewModel<PaywallState, PaywallIntent, Nothing>() {

    companion object {
        // Mock product for testing when RevenueCat returns empty
        private val MOCK_PRODUCT = PaywallProduct(
            id = "mock_premium_monthly",
            title = "Premium Monthly",
            description = "Full access to all features",
            priceString = "$1.99",
            periodString = "month",
            packageId = "mock_package",
            isPopular = true,
            hasFreeTrial = true,
            freeTrialDays = 3
        )
    }

    private val _screenState = MutableStateFlow(PaywallState())
    override val screenState: StateFlow<PaywallState> = _screenState.asStateFlow()

    init {
        loadProducts()
    }

    override fun onIntent(intent: PaywallIntent) {
        when (intent) {
            PaywallIntent.LoadProducts -> loadProducts()
            is PaywallIntent.SelectProduct -> selectProduct(intent.productId)
            PaywallIntent.Purchase -> purchase()
            PaywallIntent.RestorePurchases -> restorePurchases()
            PaywallIntent.DismissError -> dismissError()
            PaywallIntent.Close -> navigator.onBack()
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _screenState.update { it.copy(isLoading = true, error = null) }

            getOfferingsUseCase()
                .onSuccess { offering ->
                    var products = offering?.products ?: emptyList()

                    // Use mock product if no products returned (for testing)
                    if (products.isEmpty()) {
                        products = listOf(MOCK_PRODUCT)
                    } else {
                        // Apply default free trial if RevenueCat didn't return trial info
                        // This allows testing different trial configurations in Google Play Console
                        products = products.map { product ->
                            if (!product.hasFreeTrial && product.freeTrialDays == 0) {
                                // Use default trial days from config
                                product.copy(
                                    hasFreeTrial = true,
                                    freeTrialDays = PaywallConfig.DEFAULT_FREE_TRIAL_DAYS
                                )
                            } else {
                                product
                            }
                        }
                    }

                    val defaultSelected = products.find { it.isPopular }?.id
                        ?: products.firstOrNull()?.id

                    _screenState.update {
                        it.copy(
                            isLoading = false,
                            products = products,
                            selectedProductId = defaultSelected
                        )
                    }
                }
                .onFailure { error ->
                    // On failure, still show mock product for testing
                    _screenState.update {
                        it.copy(
                            isLoading = false,
                            products = listOf(MOCK_PRODUCT),
                            selectedProductId = MOCK_PRODUCT.id,
                            error = null // Don't show error, show mock product instead
                        )
                    }
                }
        }
    }

    private fun selectProduct(productId: String) {
        _screenState.update { it.copy(selectedProductId = productId) }
    }

    private fun purchase() {
        val selectedProduct = _screenState.value.products
            .find { it.id == _screenState.value.selectedProductId }
            ?: return

        viewModelScope.launch {
            _screenState.update { it.copy(isPurchasing = true, error = null) }

            when (val result = purchaseProductUseCase(selectedProduct.packageId)) {
                is PurchaseResult.Success -> {
                    _screenState.update {
                        it.copy(isPurchasing = false, purchaseSuccess = true)
                    }
                    // Navigate to subscription status after successful purchase
                    navigator.navigateToSubscriptionStatus(showSuccessMessage = true)
                }
                is PurchaseResult.Cancelled -> {
                    _screenState.update { it.copy(isPurchasing = false) }
                }
                is PurchaseResult.Error -> {
                    _screenState.update {
                        it.copy(isPurchasing = false, error = result.message)
                    }
                }
            }
        }
    }

    private fun restorePurchases() {
        viewModelScope.launch {
            _screenState.update { it.copy(isPurchasing = true, error = null) }

            when (val result = restorePurchasesUseCase()) {
                is RestoreResult.Success -> {
                    _screenState.update {
                        it.copy(isPurchasing = false, purchaseSuccess = true)
                    }
                    // Navigate to subscription status after successful restore
                    navigator.navigateToSubscriptionStatus()
                }
                is RestoreResult.NoActiveSubscription -> {
                    _screenState.update {
                        it.copy(isPurchasing = false, error = "No active subscription found")
                    }
                }
                is RestoreResult.Error -> {
                    _screenState.update {
                        it.copy(isPurchasing = false, error = result.message)
                    }
                }
            }
        }
    }

    private fun dismissError() {
        _screenState.update { it.copy(error = null) }
    }
}
