package com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository

import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import kotlinx.coroutines.flow.Flow

interface PaywallRepository {

    val subscriptionStatus: Flow<SubscriptionStatus>

    suspend fun getOfferings(): Result<PaywallOffering?>

    suspend fun purchase(packageId: String): PurchaseResult

    suspend fun restorePurchases(): RestoreResult

    suspend fun refreshSubscriptionStatus()

    fun isConfigured(): Boolean
}
