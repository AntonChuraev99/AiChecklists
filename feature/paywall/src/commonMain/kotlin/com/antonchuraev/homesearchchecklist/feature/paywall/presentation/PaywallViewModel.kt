package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
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
                    val products = offering?.products ?: emptyList()
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
                    _screenState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load products"
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
