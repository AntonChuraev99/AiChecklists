package com.antonchuraev.homesearchchecklist.feature.paywall.data.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import com.antonchuraev.homesearchchecklist.feature.paywall.data.billing.BillingPlatformPreCheck
import com.antonchuraev.homesearchchecklist.feature.paywall.data.billing.PreCheckResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.toPaywallErrorCode
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.Entitlements
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallErrorCode
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallException
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesDelegate
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode
import com.revenuecat.purchases.kmp.models.StoreProduct
import com.revenuecat.purchases.kmp.models.StoreTransaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

private const val TAG = "PaywallRepository"

class PaywallRepositoryImpl(
    private val logger: AppLogger? = null,
    private val preCheck: BillingPlatformPreCheck,
) : PaywallRepository {

    private val _subscriptionStatus = MutableStateFlow(SubscriptionStatus.FREE)
    override val subscriptionStatus: Flow<SubscriptionStatus> = _subscriptionStatus.asStateFlow()

    @Volatile
    private var cachedPackages: Map<String, Package> = emptyMap()
    private val packagesMutex = Mutex()
    private var listenerRegistered = false

    /**
     * Set up listener for customer info changes.
     * This is important for handling pending purchases that become active later.
     * RevenueCat checks pending purchases on app start and notifies through this listener.
     */
    private fun setupCustomerInfoListener() {
        if (listenerRegistered || !isConfigured()) return

        Purchases.sharedInstance.delegate = object : PurchasesDelegate {
            override fun onCustomerInfoUpdated(customerInfo: CustomerInfo) {
                _subscriptionStatus.value = customerInfo.toSubscriptionStatus()
            }

            override fun onPurchasePromoProduct(
                product: StoreProduct,
                startPurchase: (onError: (error: PurchasesError, userCancelled: Boolean) -> Unit, onSuccess: (storeTransaction: StoreTransaction, customerInfo: CustomerInfo) -> Unit) -> Unit
            ) {
                // App Store promotional purchases - not needed for this app
            }
        }
        listenerRegistered = true
    }

    /**
     * Waits for RevenueCat (and Play Billing underneath) to be fully initialized.
     * Addresses the race condition where getOfferings() is called before Play Billing
     * has established its service connection (BILLING_UNAVAILABLE in analytics).
     * Exponential-ish backoff: 500ms, 1000ms, 1500ms.
     */
    private suspend fun awaitPurchasesReady(maxRetries: Int = 3): Boolean {
        repeat(maxRetries) { attempt ->
            if (isConfigured()) return true
            val delayMs = 500L * (attempt + 1)
            logger?.info(TAG, "[PAYWALL] Purchases not configured yet, attempt ${attempt + 1}/$maxRetries — waiting ${delayMs}ms")
            delay(delayMs)
        }
        return isConfigured()
    }

    override suspend fun getOfferings(): Result<PaywallOffering?> {
        val preCheckResult = preCheck.check()
        if (preCheckResult is PreCheckResult.Failed) {
            logger?.warning(TAG, "[PAYWALL] Pre-flight failed: ${preCheckResult.reason.name} — ${preCheckResult.debugMessage}")
            return Result.failure(
                PaywallException(
                    errorCode = preCheckResult.reason.toPaywallErrorCode(),
                    billingWasReady = false,
                    message = preCheckResult.debugMessage
                )
            )
        }

        if (!awaitPurchasesReady()) {
            logger?.warning(TAG, "[PAYWALL] getOfferings() aborted — Purchases not initialized after retries")
            return Result.failure(
                PaywallException(
                    errorCode = PaywallErrorCode.BILLING_NOT_INITIALIZED,
                    billingWasReady = false,
                    message = "RevenueCat not initialized after retries — Play Billing service may not be connected"
                )
            )
        }

        // Ensure listener is set up for pending purchase updates
        setupCustomerInfoListener()

        logger?.info(TAG, "[PAYWALL] getOfferings() start")

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.getOfferings(
                onError = { error ->
                    logger?.warning(TAG, "[PAYWALL] getOfferings() failed: ${error.code.name} / ${error.underlyingErrorMessage}")
                    // billingWasReady=true here — awaitPurchasesReady() passed, error came from network/backend
                    continuation.resume(Result.failure(error.toPaywallException(billingWasReady = true)))
                },
                onSuccess = { offerings ->
                    // Prefer named offering (PaywallConfig.OFFERING_ID) so the active
                    // offering is locked to this build version. Fall back to
                    // offerings.current if the named one is missing so dashboard
                    // changes can still propagate. Mirrors swapfaceandroid pattern.
                    val currentOffering = offerings.all[PaywallConfig.OFFERING_ID]
                        ?: offerings.current
                    if (currentOffering == null) {
                        continuation.resume(Result.success(null))
                        return@getOfferings
                    }

                    cachedPackages = currentOffering.availablePackages
                        .associateBy { it.identifier }

                    val products = currentOffering.availablePackages.map { pkg ->
                        val storeProduct = pkg.storeProduct

                        // Determine free trial from introductoryDiscount
                        // RevenueCat returns introductoryDiscount for free trials
                        val introPrice = storeProduct.introductoryDiscount

                        // Check if it's a free trial:
                        // 1. introductoryDiscount exists AND price is 0 (free)
                        // 2. OR introductoryDiscount exists AND has "free" in phase type
                        val hasFreeTrial = introPrice != null && (
                            introPrice.price.amountMicros == 0L ||
                            introPrice.subscriptionPeriod.value > 0
                        )

                        val freeTrialDays = if (introPrice != null) {
                            val period = introPrice.subscriptionPeriod
                            when (period.unit.name.lowercase()) {
                                "day" -> period.value
                                "week" -> period.value * 7
                                "month" -> period.value * 30
                                "year" -> period.value * 365
                                else -> 0
                            }
                        } else {
                            0
                        }

                        PaywallProduct(
                            id = storeProduct.id,
                            title = storeProduct.title,
                            description = storeProduct.title,
                            priceString = storeProduct.price.formatted,
                            // amountMicros is in millionths of the currency unit (e.g. 20_000_000 = $20.00)
                            priceAmount = storeProduct.price.amountMicros / 1_000_000.0,
                            priceCurrencyCode = storeProduct.price.currencyCode,
                            periodString = storeProduct.period?.let { period ->
                                "${period.value} ${period.unit.name.lowercase()}"
                            },
                            packageId = pkg.identifier,
                            isPopular = pkg.identifier == "monthly" || pkg.identifier == "\$rc_monthly",
                            hasFreeTrial = hasFreeTrial,
                            freeTrialDays = freeTrialDays
                        )
                    }

                    val skuIds = products.joinToString(",") { it.id }
                    logger?.info(TAG, "[PAYWALL] getOfferings() success: offering=${currentOffering.identifier}, packages=${products.size}, skus=$skuIds")

                    continuation.resume(
                        Result.success(
                            PaywallOffering(
                                id = currentOffering.identifier,
                                products = products
                            )
                        )
                    )
                }
            )
        }
    }

    override suspend fun purchase(packageId: String): PurchaseResult {
        if (!isConfigured()) {
            logger?.warning(TAG, "[PAYWALL] purchase() aborted — RevenueCat not configured")
            return PurchaseResult.Error("RevenueCat not configured")
        }

        val packageToPurchase = packagesMutex.withLock { cachedPackages[packageId] }
            ?: run {
                logger?.warning(TAG, "[PAYWALL] purchase() failed — package not found in cache: $packageId")
                return PurchaseResult.Error("Package not found: $packageId")
            }

        logger?.info(TAG, "[PAYWALL] purchase() start: packageId=$packageId, sku=${packageToPurchase.storeProduct.id}")

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.purchase(
                packageToPurchase = packageToPurchase,
                onError = { error, userCancelled ->
                    if (userCancelled) {
                        logger?.info(TAG, "[PAYWALL] purchase() cancelled by user: packageId=$packageId")
                        continuation.resume(PurchaseResult.Cancelled)
                    } else {
                        logger?.warning(TAG, "[PAYWALL] purchase() failed: ${error.code.name} / ${error.underlyingErrorMessage}")
                        continuation.resume(
                            PurchaseResult.Error(
                                message = error.message,
                                errorCode = error.code.name,
                                underlyingError = error.underlyingErrorMessage
                            )
                        )
                    }
                },
                onSuccess = { storeTransaction: StoreTransaction, customerInfo: CustomerInfo ->
                    val status = customerInfo.toSubscriptionStatus()
                    _subscriptionStatus.value = status

                    // Determine trial status from the purchased package
                    val storeProduct = packageToPurchase.storeProduct
                    val introPrice = storeProduct.introductoryDiscount
                    val hasFreeTrial = introPrice != null && (
                        introPrice.price.amountMicros == 0L ||
                        introPrice.subscriptionPeriod.value > 0
                    )

                    logger?.info(TAG, "[PAYWALL] purchase() success: txId=${storeTransaction.transactionId}, isPremium=${status.isActive}, hasFreeTrial=$hasFreeTrial")

                    continuation.resume(
                        PurchaseResult.Success(
                            subscriptionStatus = status,
                            transactionId = storeTransaction.transactionId,
                            hasFreeTrial = hasFreeTrial
                        )
                    )
                }
            )
        }
    }

    override suspend fun restorePurchases(): RestoreResult {
        if (!isConfigured()) {
            return RestoreResult.Error("RevenueCat not configured")
        }

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.restorePurchases(
                onError = { error ->
                    continuation.resume(
                        RestoreResult.Error(
                            message = error.message,
                            errorCode = error.code.name,
                            underlyingError = error.underlyingErrorMessage
                        )
                    )
                },
                onSuccess = { customerInfo ->
                    val status = customerInfo.toSubscriptionStatus()
                    _subscriptionStatus.value = status

                    if (status.isActive) {
                        continuation.resume(RestoreResult.Success(status))
                    } else {
                        continuation.resume(RestoreResult.NoActiveSubscription)
                    }
                }
            )
        }
    }

    override suspend fun refreshSubscriptionStatus() {
        if (!isConfigured()) return

        // Ensure listener is set up for pending purchase updates
        setupCustomerInfoListener()

        suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.getCustomerInfo(
                onError = { _ ->
                    continuation.resume(Unit)
                },
                onSuccess = { customerInfo ->
                    _subscriptionStatus.value = customerInfo.toSubscriptionStatus()
                    continuation.resume(Unit)
                }
            )
        }
    }

    override fun isConfigured(): Boolean {
        return try {
            Purchases.isConfigured
        } catch (e: Exception) {
            false
        }
    }

    private fun CustomerInfo.toSubscriptionStatus(): SubscriptionStatus {
        val activeEntitlements = entitlements.active.keys
        val premiumEntitlement = entitlements[Entitlements.PREMIUM]
        val isPremium = premiumEntitlement?.isActive == true

        return SubscriptionStatus(
            isActive = isPremium || activeEntitlements.isNotEmpty(),
            activeEntitlements = activeEntitlements,
            expirationDate = null // Will be implemented when product expiration is needed
        )
    }

    override suspend fun logIn(appUserId: String): Result<LoginResult> {
        if (!isConfigured()) {
            return Result.failure(
                PaywallException(
                    errorCode = PaywallErrorCode.NOT_CONFIGURED,
                    message = "RevenueCat not configured"
                )
            )
        }

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.logIn(
                newAppUserID = appUserId,
                onError = { error ->
                    continuation.resume(Result.failure(error.toPaywallException()))
                },
                onSuccess = { customerInfo, created ->
                    val status = customerInfo.toSubscriptionStatus()
                    _subscriptionStatus.value = status
                    continuation.resume(
                        Result.success(
                            LoginResult(
                                subscriptionStatus = status,
                                isNewCustomer = created
                            )
                        )
                    )
                }
            )
        }
    }

    override suspend fun logOut(): Result<SubscriptionStatus> {
        if (!isConfigured()) {
            return Result.failure(
                PaywallException(
                    errorCode = PaywallErrorCode.NOT_CONFIGURED,
                    message = "RevenueCat not configured"
                )
            )
        }

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.logOut(
                onError = { error ->
                    continuation.resume(Result.failure(error.toPaywallException()))
                },
                onSuccess = { customerInfo ->
                    val status = customerInfo.toSubscriptionStatus()
                    _subscriptionStatus.value = status
                    continuation.resume(Result.success(status))
                }
            )
        }
    }

    private fun PurchasesError.toPaywallException(billingWasReady: Boolean = false): PaywallException {
        val errorCode = when (code) {
            PurchasesErrorCode.NetworkError -> PaywallErrorCode.NETWORK_ERROR
            PurchasesErrorCode.OfflineConnectionError -> PaywallErrorCode.OFFLINE
            PurchasesErrorCode.StoreProblemError -> PaywallErrorCode.STORE_PROBLEM
            PurchasesErrorCode.UnknownBackendError -> PaywallErrorCode.UNKNOWN_BACKEND
            PurchasesErrorCode.ConfigurationError -> PaywallErrorCode.CONFIGURATION_ERROR
            PurchasesErrorCode.InvalidCredentialsError -> PaywallErrorCode.INVALID_CREDENTIALS
            PurchasesErrorCode.ProductNotAvailableForPurchaseError -> PaywallErrorCode.PRODUCT_NOT_AVAILABLE
            else -> PaywallErrorCode.UNKNOWN
        }
        return PaywallException(
            errorCode = errorCode,
            underlyingError = underlyingErrorMessage,
            billingWasReady = billingWasReady,
            message = message
        )
    }
}
