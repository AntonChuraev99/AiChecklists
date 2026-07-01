package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AiModelArm
import com.antonchuraev.homesearchchecklist.core.common.api.AiModelExperimentTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
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
import kotlin.concurrent.Volatile

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
    sourceOverride: String? = null,
    forceVariantOverride: String? = null,
    // AI-model A/B attribution: read the persisted arm so the purchase events carry which model the
    // user was on. Best-effort + nullable so pure-VM tests can omit it; the purchase never depends
    // on it. See [AiModelExperimentTracker].
    private val aiModelExperimentTracker: AiModelExperimentTracker? = null,
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

    // Persisted AI-model A/B arm, read once at init (deterministic per user + persisted, so a single
    // read is enough). Attached to the purchase funnel events. Null until loaded / when unknown —
    // best-effort, so if the read hasn't finished by tap time the params are simply omitted.
    @Volatile
    private var experimentArm: AiModelArm? = null

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
        // Resolve variant: forceVariant nav-arg takes priority over Remote Config.
        // It MUST arrive via the constructor (Koin parametersOf) — reading it from
        // savedStateHandle is unreliable in Navigation 3 alpha (returns null ~58% of
        // opens), which silently dropped the debug variant override so every debug
        // variant button fell back to the Remote Config variant. savedStateHandle stays
        // as a last-resort fallback only.
        val forceVariant = forceVariantOverride
            ?: savedStateHandle[AppNavRoute.Paywall::forceVariant.name] as? String
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

        // Read the persisted A/B arm off the main path — best-effort attribution for the purchase
        // funnel. Never blocks product load or the purchase itself.
        aiModelExperimentTracker?.let { tracker ->
            viewModelScope.launch {
                experimentArm = runCatching { tracker.current() }.getOrNull()
            }
        }

        loadProducts()
    }

    /**
     * AI-model A/B dimensions for the purchase funnel, or empty when the arm is unknown. Added to
     * both [AnalyticsEvents.Paywall.PURCHASE_BUTTON_CLICKED] and
     * [AnalyticsEvents.Paywall.PURCHASE_COMPLETED] so revenue can be split by which model the user
     * was on — the north-star of the model experiment.
     */
    private fun modelArmParams(): Map<String, Any> = buildMap {
        experimentArm?.let { arm ->
            put(AnalyticsParams.AI_MODEL_VARIANT, arm.variant)
            arm.modelId?.let { put(AnalyticsParams.AI_MODEL_ID, it) }
        }
    }

    override fun onIntent(intent: PaywallIntent) {
        when (intent) {
            PaywallIntent.LoadProducts -> loadProducts()
            is PaywallIntent.SelectProduct -> selectProduct(intent.productId)
            PaywallIntent.Purchase -> purchase()
            PaywallIntent.RestorePurchases -> restorePurchases()
            PaywallIntent.DismissError -> dismissError()
            PaywallIntent.Close -> {
                analyticsTracker.event(
                    AnalyticsEvents.Paywall.CLOSED,
                    mapOf(AnalyticsParams.SOURCE to source),
                )
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

                    // Report the active offer (RevenueCat offering id from paywall_config) as a
                    // user property so analytics / A/B segments can split metrics by which offer
                    // the user was actually shown.
                    offering?.id?.let { activeOffer ->
                        analyticsTracker.setUserProperties(mapOf("current_offer" to activeOffer))
                    }

                    if (products.isEmpty()) {
                        analyticsTracker.event(AnalyticsEvents.Paywall.PRODUCTS_LOAD_EMPTY, buildMap {
                            put(AnalyticsParams.SOURCE, source)
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
                    analyticsTracker.event(AnalyticsEvents.Paywall.PRODUCTS_LOAD_SUCCESS, buildMap {
                        put(AnalyticsParams.SOURCE, source)
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
                        put(AnalyticsParams.SOURCE, source)
                        put(AnalyticsParams.ERROR, (error.message ?: "unknown").take(100))
                        if (error is PaywallException) {
                            put("error_code", error.errorCode.name)
                            put("underlying_error", (error.underlyingError ?: "none").take(100))
                        }
                        analyticsContext?.toEventParams()?.let { putAll(it) }
                    }
                    analyticsTracker.event(AnalyticsEvents.Paywall.PRODUCTS_LOAD_FAILED, params)
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
            AnalyticsEvents.Paywall.PURCHASE_BUTTON_CLICKED,
            buildMap {
                put(AnalyticsParams.SOURCE, source)
                put(AnalyticsParams.PRODUCT_ID, selectedProduct.id)
                put(AnalyticsParams.HAS_FREE_TRIAL, selectedProduct.hasFreeTrial)
                put("sku_id", selectedProduct.id)
                put("variant", currentState.variant.name)
                put("selected_plan", currentState.selectedPlan.name)
                put("plan_type", currentState.selectedPlan.name.lowercase())
                analyticsContext?.toEventParams()?.let { putAll(it) }
                putAll(modelArmParams())
            },
        )

        viewModelScope.launch {
            _screenState.update { it.copy(isPurchasing = true, error = null) }

            when (val result = purchaseProductUseCase(selectedProduct.packageId)) {
                is PurchaseResult.Success -> {
                    analyticsTracker.event(AnalyticsEvents.Paywall.PURCHASE_COMPLETED, buildMap {
                        put(AnalyticsParams.SOURCE, source)
                        put(AnalyticsParams.PRODUCT_ID, selectedProduct.id)
                        put("sku_id", selectedProduct.id)
                        put("plan_type", currentState.selectedPlan.name.lowercase())
                        put("price_str", selectedProduct.priceString.take(100))
                        analyticsContext?.toEventParams()?.let { putAll(it) }
                        putAll(modelArmParams())
                    })
                    conversionEventHelper.logConversionEvent(result, selectedProduct)
                    _screenState.update {
                        it.copy(isPurchasing = false, purchaseSuccess = true)
                    }
                    // Navigate to subscription status after successful purchase
                    navigator.navigateToSubscriptionStatus(showSuccessMessage = true)
                }
                is PurchaseResult.Cancelled -> {
                    analyticsTracker.event(AnalyticsEvents.Paywall.PURCHASE_CANCELLED, buildMap {
                        put(AnalyticsParams.SOURCE, source)
                        put(AnalyticsParams.PRODUCT_ID, selectedProduct.id)
                        put("sku_id", selectedProduct.id)
                        put("plan_type", currentState.selectedPlan.name.lowercase())
                        analyticsContext?.toEventParams()?.let { putAll(it) }
                    })
                    _screenState.update { it.copy(isPurchasing = false) }
                }
                is PurchaseResult.Error -> {
                    analyticsTracker.event(AnalyticsEvents.Paywall.PURCHASE_FAILED, buildMap {
                        put(AnalyticsParams.SOURCE, source)
                        put(AnalyticsParams.PRODUCT_ID, selectedProduct.id)
                        put("sku_id", selectedProduct.id)
                        put("plan_type", currentState.selectedPlan.name.lowercase())
                        put(AnalyticsParams.ERROR, result.message.take(100))
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
        analyticsTracker.event(
            AnalyticsEvents.Paywall.RESTORE_BUTTON_CLICKED,
            mapOf(AnalyticsParams.SOURCE to source),
        )

        viewModelScope.launch {
            _screenState.update { it.copy(isRestoring = true, error = null) }

            when (val result = restorePurchasesUseCase()) {
                is RestoreResult.Success -> {
                    analyticsTracker.event(
                        AnalyticsEvents.Paywall.RESTORE_COMPLETED,
                        mapOf(AnalyticsParams.SOURCE to source),
                    )
                    _screenState.update {
                        it.copy(isRestoring = false, purchaseSuccess = true)
                    }
                    // Navigate to subscription status after successful restore.
                    // showSuccessMessage=true surfaces the "🎉 Welcome to Premium" toast on
                    // the destination — same success feedback as a fresh purchase.
                    navigator.navigateToSubscriptionStatus(showSuccessMessage = true)
                }
                is RestoreResult.NoActiveSubscription -> {
                    analyticsTracker.event(
                        AnalyticsEvents.Paywall.RESTORE_NO_SUBSCRIPTION,
                        mapOf(AnalyticsParams.SOURCE to source),
                    )
                    _screenState.update {
                        it.copy(isRestoring = false, error = getString(Res.string.paywall_no_subscription_found))
                    }
                }
                is RestoreResult.Error -> {
                    analyticsTracker.event(AnalyticsEvents.Paywall.RESTORE_FAILED, mapOf(
                        AnalyticsParams.SOURCE to source,
                        AnalyticsParams.ERROR to (result.message).take(100),
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

    private val conversionEventHelper = ConversionEventHelper(analyticsTracker, logger)

    private fun dismissError() {
        _screenState.update { it.copy(error = null) }
    }
}
