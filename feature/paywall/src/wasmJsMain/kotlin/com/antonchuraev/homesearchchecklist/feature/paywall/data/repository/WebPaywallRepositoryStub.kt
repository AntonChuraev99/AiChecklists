package com.antonchuraev.homesearchchecklist.feature.paywall.data.repository

import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Web stub for PaywallRepository. In-app purchases are not available on the web target.
 * Always returns FREE subscription status and no-ops for all purchase operations.
 */
class WebPaywallRepositoryStub : PaywallRepository {

    override val subscriptionStatus: Flow<SubscriptionStatus> =
        MutableStateFlow(SubscriptionStatus.FREE)

    override suspend fun getOfferings(): Result<PaywallOffering?> =
        Result.success(null)

    override suspend fun purchase(packageId: String): PurchaseResult =
        PurchaseResult.Error("Purchases are not supported on web.")

    override suspend fun restorePurchases(): RestoreResult =
        RestoreResult.NoActiveSubscription

    override suspend fun refreshSubscriptionStatus() = Unit

    override fun isConfigured(): Boolean = false

    override suspend fun logIn(appUserId: String): Result<LoginResult> =
        Result.failure(UnsupportedOperationException("Not supported on web."))

    override suspend fun logOut(): Result<SubscriptionStatus> =
        Result.success(SubscriptionStatus.FREE)
}
