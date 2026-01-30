package com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository

import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
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

    /**
     * Links the current anonymous RevenueCat customer with the app's user ID.
     * Should be called after successful user registration.
     *
     * @param appUserId The server-generated user ID
     * @return Result containing LoginResult if successful, or error
     */
    suspend fun logIn(appUserId: String): Result<LoginResult>

    /**
     * Logs out the current user, generating a new anonymous customer.
     * Typically called when user explicitly signs out (if auth is added later).
     *
     * @return Result containing SubscriptionStatus after logout, or error
     */
    suspend fun logOut(): Result<SubscriptionStatus>
}
