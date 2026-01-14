package com.antonchuraev.homesearchchecklist.feature.home.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus

sealed interface MainScreenState : State {
    data object Loading : MainScreenState
    data class Success(
        val checklists: List<Checklist>,
        val subscriptionStatus: SubscriptionStatus = SubscriptionStatus.FREE,
        val formattedExpirationDate: String? = null
    ) : MainScreenState
}

sealed interface MainScreenIntent : Intent {

    data object OnAddChecklistClick : MainScreenIntent

    data object OnAddChecklistFromTemplatesClick : MainScreenIntent

    data object OnAiAnalyzeClick : MainScreenIntent

    data class OnChecklistClick(val checklist: Checklist) : MainScreenIntent

    data object OnPremiumBannerClick : MainScreenIntent
}
