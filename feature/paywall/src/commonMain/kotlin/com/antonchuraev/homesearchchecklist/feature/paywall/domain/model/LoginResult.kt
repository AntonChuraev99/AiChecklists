package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

/**
 * Result of RevenueCat logIn operation.
 *
 * @property subscriptionStatus Current subscription status after login
 * @property isNewCustomer true if a new RevenueCat customer was created,
 *                         false if existing customer was found and merged
 */
data class LoginResult(
    val subscriptionStatus: SubscriptionStatus,
    val isNewCustomer: Boolean
)
