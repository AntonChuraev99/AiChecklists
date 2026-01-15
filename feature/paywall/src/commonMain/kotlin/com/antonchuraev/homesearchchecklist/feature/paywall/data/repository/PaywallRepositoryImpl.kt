package com.antonchuraev.homesearchchecklist.feature.paywall.data.repository

import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.Entitlements
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Offerings
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.StoreTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PaywallRepositoryImpl(
    private val scope: CoroutineScope
) : PaywallRepository {

    private val _subscriptionStatus = MutableStateFlow(SubscriptionStatus.FREE)
    override val subscriptionStatus: Flow<SubscriptionStatus> = _subscriptionStatus.asStateFlow()

    private var cachedOfferings: Offerings? = null
    private var cachedPackages: Map<String, Package> = emptyMap()

    override suspend fun getOfferings(): Result<PaywallOffering?> {
        if (!isConfigured()) {
            return Result.failure(IllegalStateException("RevenueCat not configured"))
        }

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.getOfferings(
                onError = { error ->
                    continuation.resume(Result.failure(Exception(error.message)))
                },
                onSuccess = { offerings ->
                    cachedOfferings = offerings
                    val currentOffering = offerings.current
                    if (currentOffering == null) {
                        continuation.resume(Result.success(null))
                        return@getOfferings
                    }

                    // Cache packages for later purchase
                    val packagesMap = mutableMapOf<String, Package>()
                    currentOffering.availablePackages.forEach { pkg ->
                        packagesMap[pkg.identifier] = pkg
                    }
                    cachedPackages = packagesMap

                    val products = currentOffering.availablePackages.map { pkg ->
                        val storeProduct = pkg.storeProduct

                        // Check for free trial in introductory price
                        val introPrice = storeProduct.introductoryDiscount
                        val hasFreeTrial = introPrice?.price?.amountMicros == 0L
                        val freeTrialDays = if (hasFreeTrial && introPrice != null) {
                            when (introPrice.subscriptionPeriod.unit.name.lowercase()) {
                                "day" -> introPrice.subscriptionPeriod.value
                                "week" -> introPrice.subscriptionPeriod.value * 7
                                "month" -> introPrice.subscriptionPeriod.value * 30
                                else -> 0
                            }
                        } else 0

                        PaywallProduct(
                            id = storeProduct.id,
                            title = storeProduct.title,
                            description = storeProduct.title,
                            priceString = storeProduct.price.formatted,
                            periodString = storeProduct.period?.let { period ->
                                "${period.value} ${period.unit.name.lowercase()}"
                            },
                            packageId = pkg.identifier,
                            isPopular = pkg.identifier == "monthly" || pkg.identifier == "\$rc_monthly",
                            hasFreeTrial = hasFreeTrial,
                            freeTrialDays = freeTrialDays
                        )
                    }

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
            return PurchaseResult.Error("RevenueCat not configured")
        }

        val packageToPurchase = cachedPackages[packageId]
            ?: return PurchaseResult.Error("Package not found: $packageId")

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.purchase(
                packageToPurchase = packageToPurchase,
                onError = { error, userCancelled ->
                    if (userCancelled) {
                        continuation.resume(PurchaseResult.Cancelled)
                    } else {
                        continuation.resume(PurchaseResult.Error(error.message))
                    }
                },
                onSuccess = { _: StoreTransaction, customerInfo: CustomerInfo ->
                    val status = customerInfo.toSubscriptionStatus()
                    _subscriptionStatus.value = status
                    continuation.resume(PurchaseResult.Success(status))
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
                    continuation.resume(RestoreResult.Error(error.message))
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
}
