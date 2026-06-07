package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.ConversionEventHelper
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.getDeviceCountry
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.getPlayStoreVersion
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallException
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseAnalyticsContext
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetOfferingsUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.PurchaseProductUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.RestorePurchasesUseCase
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "PaywallViewModel"

class PaywallViewModel(
    savedStateHandle: SavedStateHandle,
    private val navigator: AppNavigator,
    private val getOfferingsUseCase: GetOfferingsUseCase,
    private val purchaseProductUseCase: PurchaseProductUseCase,
    private val restorePurchasesUseCase: RestorePurchasesUseCase,
    private val analyticsTracker: AnalyticsTracker,
    private val remoteConfigProvider: RemoteConfigProvider,
    private val logger: AppLogger? = null,
    sourceOverride: String? = null
) : AppViewModel<PaywallState, PaywallIntent, Nothing>() {

    private val source: String = sourceOverride
        ?: savedStateHandle[AppNavRoute.Paywall::source.name]
        ?: "unknown"

    private val _screenState = MutableStateFlow(PaywallState(source = source))
    override val screenState: StateFlow<PaywallState> = _screenState.asStateFlow()

    // Lazily populated after getOfferings() completes.
    // Attached to all purchase-related events for diagnostics.
    // Updated and read on the same viewModelScope coroutine — single-threaded access.
    private var analyticsContext: PurchaseAnalyticsContext? = null

    // Maps raw string to PaywallVariant enum; forceVariant (from debug/nav arg) overrides RC.
    private fun parseVariant(raw: String?): PaywallVariant = when (raw) {
        "timeline", "timeline_v1" -> PaywallVariant.Timeline
        "features", "features_v1" -> PaywallVariant.Features
        "compare", "compare_v1"   -> PaywallVariant.Compare
        else -> PaywallVariant.Features  // safe default
    }

    // Match yearly/monthly by id substring + period fallback. Hardcoded full ids
    // ("premium_yearly:main-20") are brittle — RC SDK strips basePlan suffix on
    // Google Play and storeProduct.period was observed null for yearly on KZ.
    // ID substring is the most reliable primary signal.
    private fun selectProductForPlan(
        plan: PaywallPlan,
        products: List<com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct>,
    ) = when (plan) {
        PaywallPlan.Yearly  -> products.find {
            it.id.contains("year", ignoreCase = true) ||
                it.id.contains("annual", ignoreCase = true) ||
                it.periodString?.contains("year") == true
        }
        PaywallPlan.Monthly -> products.find {
            it.id.contains("month", ignoreCase = true) ||
                it.periodString?.contains("month") == true
        }
    }

    init {
        // Resolve variant: forceVariant nav-arg takes priority over Remote Config
        val forceVariant = savedStateHandle[AppNavRoute.Paywall::forceVariant.name] as? String
        val rcVariant = remoteConfigProvider.getString(
            key = RemoteConfigKeys.PAYWALL_VARIANT,
            defaultValue = RemoteConfigDefaults.PAYWALL_VARIANT,
        )
        val resolvedVariant = parseVariant(forceVariant ?: rcVariant)

        // Resolve default plan from Remote Config.
        // "monthly" is the safe default — lower price shown first increases conversion
        // in price-sensitive markets (Ethiopia, LATAM, SEA). Can be remotely switched
        // to "yearly" for markets where annual converts better.
        val rcDefaultPlan = remoteConfigProvider.getString(
            key = RemoteConfigKeys.PAYWALL_DEFAULT_PLAN,
            defaultValue = RemoteConfigDefaults.PAYWALL_DEFAULT_PLAN,
        )
        val resolvedPlan = when (rcDefaultPlan.lowercase()) {
            "yearly" -> PaywallPlan.Yearly
            else -> PaywallPlan.Monthly // safe default for all other values
        }

        _screenState.update { it.copy(variant = resolvedVariant, selectedPlan = resolvedPlan) }

        analyticsTracker.setUserProperties(
            mapOf(
                "paywall_variant" to resolvedVariant.name,
                "paywall_default_plan" to resolvedPlan.name.lowercase(),
            ),
        )
        analyticsTracker.event(
            "paywall_opened",
            buildMap {
                put("source", source)
                put("variant", resolvedVariant.name)
                put("default_plan", resolvedPlan.name.lowercase())
                getDeviceCountry()?.let { put("country", it) }
                getPlayStoreVersion()?.let { put("play_store_version", it) }
            },
        )
        loadProducts()
    }

    override fun onIntent(intent: PaywallIntent) {
        when (intent) {
            PaywallIntent.LoadProducts -> loadProducts()
            is PaywallIntent.SelectProduct -> selectProduct(intent.productId)
            PaywallIntent.Purchase -> purchase()
            PaywallIntent.RestorePurchases -> restorePurchases()
            PaywallIntent.DismissError -> dismissError()
            PaywallIntent.Close -> {
                analyticsTracker.event("paywall_closed", mapOf("source" to source))
                navigator.onBack()
            }
            is PaywallIntent.SelectPlan -> {
                _screenState.update { it.copy(selectedPlan = intent.plan) }
                analyticsTracker.event(
                    "plan_selected",
                    mapOf(
                        "variant" to _screenState.value.variant.name,
                        "plan"    to intent.plan.name,
                    ),
                )
            }
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _screenState.update { it.copy(isLoading = true, error = null) }
            logger?.info(TAG, "[PAYWALL] loadProducts() start — source=$source")

            getOfferingsUseCase()
                .onSuccess { offering ->
                    val products = offering?.products ?: emptyList()

                    // Capture analytics context from successful offering load.
                    analyticsContext = PurchaseAnalyticsContext(
                        country = getDeviceCountry(),
                        appVersion = "",  // Amplitude SDK auto-attaches app_version from manifest
                        playStoreVersion = getPlayStoreVersion(),
                        billingClientReady = true,
                        offeringId = offering?.id,
                        packageCount = products.size,
                        availableSkuIds = products.map { it.id },
                    )

                    if (products.isEmpty()) {
                        analyticsTracker.event("products_load_empty", buildMap {
                            put("source", source)
                            put("reason", if (offering == null) "no_current_offering" else "empty_packages")
                            analyticsContext?.toEventParams()?.let { putAll(it) }
                        })
                        handleEmptyProducts()
                        return@launch
                    }

                    // Trust RevenueCat's trial data — do NOT fabricate a trial. A product
                    // without an introductoryDiscount genuinely has no free trial (e.g. the
                    // *NoTrial offering); showing trial copy for it would be deceptive
                    // (Google Play policy) and simply wrong. The UI selects trial vs
                    // no-trial copy via PaywallUiState.hasFreeTrial, derived from real
                    // freeTrialDays in PaywallRoute.
                    val defaultSelected = products.find { it.isPopular }?.id
                        ?: products.firstOrNull()?.id

                    logger?.info(TAG, "[PAYWALL] loadProducts() success — ${products.size} products loaded: ${products.joinToString { it.id }}")

                    // Fire products_load_success for conversion funnel tracking.
                    analyticsTracker.event("products_load_success", buildMap {
                        put("source", source)
                        analyticsContext?.toEventParams()?.let { putAll(it) }
                    })

                    _screenState.update {
                        it.copy(
                            isLoading = false,
                            products = products,
                            selectedProductId = defaultSelected
                        )
                    }
                }
                .onFailure { error ->
                    logger?.warning(TAG, "[PAYWALL] loadProducts() failed — ${error.message}")

                    // Capture context even on failure — critical for BILLING_UNAVAILABLE diagnosis.
                    // billingWasReady from PaywallException tells us whether Play Billing was
                    // connected before the call (false = race condition, true = network/backend error).
                    val billingWasReady = (error as? PaywallException)?.billingWasReady ?: false
                    analyticsContext = PurchaseAnalyticsContext(
                        country = getDeviceCountry(),
                        appVersion = "",
                        playStoreVersion = getPlayStoreVersion(),
                        billingClientReady = billingWasReady,
                        offeringId = null,
                        packageCount = 0,
                        availableSkuIds = emptyList(),
                    )
                    val params = buildMap<String, Any> {
                        put("source", source)
                        put("error", (error.message ?: "unknown").take(100))
                        if (error is PaywallException) {
                            put("error_code", error.errorCode.name)
                            put("underlying_error", (error.underlyingError ?: "none").take(100))
                        }
                        analyticsContext?.toEventParams()?.let { putAll(it) }
                    }
                    analyticsTracker.event("products_load_failed", params)
                    handleEmptyProducts()
                }
        }
    }

    private suspend fun handleEmptyProducts() {
        val errorMessage = getString(Res.string.paywall_load_error)
        _screenState.update {
            it.copy(
                isLoading = false,
                products = emptyList(),
                error = errorMessage
            )
        }
    }

    private fun selectProduct(productId: String) {
        _screenState.update { it.copy(selectedProductId = productId) }
    }

    private fun purchase() {
        val currentState = _screenState.value
        val selectedProduct = selectProductForPlan(currentState.selectedPlan, currentState.products)
        if (selectedProduct == null) {
            // Expected product missing from offering — show error instead of silently
            // buying a different product (compliance: user paid based on shown price).
            analyticsTracker.event(
                "purchase_product_not_found",
                buildMap {
                    put("source", source)
                    put("selected_plan", currentState.selectedPlan.name)
                    put("available_product_ids", currentState.products.joinToString(",") { it.id }.take(100))
                    put("available_periods", currentState.products.joinToString(",") { it.periodString.orEmpty() }.take(100))
                    analyticsContext?.toEventParams()?.let { putAll(it) }
                },
            )
            viewModelScope.launch {
                val errorMessage = getString(Res.string.paywall_load_error)
                _screenState.update {
                    it.copy(isPurchasing = false, error = errorMessage)
                }
            }
            return
        }

        logger?.info(TAG, "[PAYWALL] purchase() initiated: plan=${currentState.selectedPlan.name}, sku=${selectedProduct.id}, pkg=${selectedProduct.packageId}")

        analyticsTracker.event(
            "purchase_button_clicked",
            buildMap {
                put("source", source)
                put("product_id", selectedProduct.id)
                put("sku_id", selectedProduct.id)
                put("variant", currentState.variant.name)
                put("selected_plan", currentState.selectedPlan.name)
                put("plan_type", currentState.selectedPlan.name.lowercase())
                analyticsContext?.toEventParams()?.let { putAll(it) }
            },
        )

        viewModelScope.launch {
            _screenState.update { it.copy(isPurchasing = true, error = null) }

            when (val result = purchaseProductUseCase(selectedProduct.packageId)) {
                is PurchaseResult.Success -> {
                    analyticsTracker.event("purchase_completed", buildMap {
                        put("source", source)
                        put("product_id", selectedProduct.id)
                        put("sku_id", selectedProduct.id)
                        put("plan_type", currentState.selectedPlan.name.lowercase())
                        put("price_str", selectedProduct.priceString.take(100))
                        analyticsContext?.toEventParams()?.let { putAll(it) }
                    })
                    conversionEventHelper.logConversionEvent(result, selectedProduct)
                    _screenState.update {
                        it.copy(isPurchasing = false, purchaseSuccess = true)
                    }
                    // Navigate to subscription status after successful purchase
                    navigator.navigateToSubscriptionStatus(showSuccessMessage = true)
                }
                is PurchaseResult.Cancelled -> {
                    analyticsTracker.event("purchase_cancelled", buildMap {
                        put("source", source)
                        put("product_id", selectedProduct.id)
                        put("sku_id", selectedProduct.id)
                        put("plan_type", currentState.selectedPlan.name.lowercase())
                        analyticsContext?.toEventParams()?.let { putAll(it) }
                    })
                    _screenState.update { it.copy(isPurchasing = false) }
                }
                is PurchaseResult.Error -> {
                    analyticsTracker.event("purchase_failed", buildMap {
                        put("source", source)
                        put("product_id", selectedProduct.id)
                        put("sku_id", selectedProduct.id)
                        put("plan_type", currentState.selectedPlan.name.lowercase())
                        put("error", result.message.take(100))
                        put("error_code", result.errorCode ?: "unknown")
                        put("underlying_error", (result.underlyingError ?: "none").take(100))
                        analyticsContext?.toEventParams()?.let { putAll(it) }
                    })
                    // Friendly localized message instead of the raw RevenueCat error
                    // (raw text preserved in the analytics event above).
                    _screenState.update {
                        it.copy(isPurchasing = false, error = getString(Res.string.paywall_purchase_failed))
                    }
                }
            }
        }
    }

    private fun restorePurchases() {
        analyticsTracker.event("restore_button_clicked", mapOf("source" to source))

        viewModelScope.launch {
            _screenState.update { it.copy(isRestoring = true, error = null) }

            when (val result = restorePurchasesUseCase()) {
                is RestoreResult.Success -> {
                    analyticsTracker.event("restore_completed", mapOf("source" to source))
                    _screenState.update {
                        it.copy(isRestoring = false, purchaseSuccess = true)
                    }
                    // Navigate to subscription status after successful restore.
                    // showSuccessMessage=true surfaces the "🎉 Welcome to Premium" toast on
                    // the destination — same success feedback as a fresh purchase.
                    navigator.navigateToSubscriptionStatus(showSuccessMessage = true)
                }
                is RestoreResult.NoActiveSubscription -> {
                    analyticsTracker.event("restore_no_subscription", mapOf("source" to source))
                    _screenState.update {
                        it.copy(isRestoring = false, error = getString(Res.string.paywall_no_subscription_found))
                    }
                }
                is RestoreResult.Error -> {
                    analyticsTracker.event("restore_failed", mapOf(
                        "source" to source,
                        "error" to (result.message).take(100),
                        "error_code" to (result.errorCode ?: "unknown"),
                        "underlying_error" to (result.underlyingError ?: "none").take(100)
                    ))
                    // Show a friendly localized message — NOT the raw RevenueCat error
                    // (English-only, sometimes a dev string). Raw text stays in analytics above.
                    _screenState.update {
                        it.copy(isRestoring = false, error = getString(Res.string.paywall_restore_failed))
                    }
                }
            }
        }
    }

    private val conversionEventHelper = ConversionEventHelper(analyticsTracker)

    private fun dismissError() {
        _screenState.update { it.copy(error = null) }
    }
}
