package com.antonchuraev.aichecklists

import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Fake [PaywallRepository] for screenshot tests.
 *
 * Returns two pre-loaded subscription tiles so the Paywall screen renders
 * in its "loaded" state without RevenueCat being initialized.
 * None of the mutation methods (purchase, restore) are ever called during
 * screenshot capture — they just return reasonable canned values.
 */
class FakePaywallRepository : PaywallRepository {

    private val freeUser = SubscriptionStatus.FREE

    // Real product: only the monthly subscription exists in Play Console today.
    // Annual is a TODO (see project memory) — don't show it in screenshots.
    private val fakeOffering = PaywallOffering(
        id = "default",
        products = listOf(
            PaywallProduct(
                id = "gisti_monthly",
                title = "Monthly",
                description = "Monthly",
                priceString = "$1.99",
                periodString = "1 month",
                packageId = "monthly",
                isPopular = true,
                hasFreeTrial = true,
                freeTrialDays = 3
            )
        )
    )

    // subscriptionStatus: free user (non-premium) so the paywall renders the "subscribe" state
    override val subscriptionStatus: Flow<SubscriptionStatus> = flowOf(freeUser)

    // Returns two loaded products — PaywallViewModel.loadProducts() populates products list
    override suspend fun getOfferings(): Result<PaywallOffering?> =
        Result.success(fakeOffering)

    // Never called during screenshot capture
    override suspend fun purchase(packageId: String): PurchaseResult =
        PurchaseResult.Cancelled

    // Never called during screenshot capture
    override suspend fun restorePurchases(): RestoreResult =
        RestoreResult.NoActiveSubscription

    // No-op: RevenueCat not configured; nothing to refresh
    override suspend fun refreshSubscriptionStatus() = Unit

    // Returns true so PaywallRepositoryImpl.getOfferings() "not configured" guard is skipped
    override fun isConfigured(): Boolean = true

    // Never called during screenshot capture
    override suspend fun logIn(appUserId: String): Result<LoginResult> =
        Result.success(LoginResult(subscriptionStatus = freeUser, isNewCustomer = false))

    // Never called during screenshot capture
    override suspend fun logOut(): Result<SubscriptionStatus> =
        Result.success(freeUser)
}
