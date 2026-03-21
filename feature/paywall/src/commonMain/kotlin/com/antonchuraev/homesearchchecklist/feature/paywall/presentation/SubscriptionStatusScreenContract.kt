package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus

data class SubscriptionStatusState(
    val isLoading: Boolean = true,
    val subscriptionStatus: SubscriptionStatus = SubscriptionStatus.FREE,
    val formattedExpirationDate: String? = null,
    val aiCredits: Int = 0,
    val showSuccessMessage: Boolean = false
) : State

sealed interface SubscriptionStatusIntent : Intent {
    data object OnBackClick : SubscriptionStatusIntent
    data object DismissSuccessMessage : SubscriptionStatusIntent
}
