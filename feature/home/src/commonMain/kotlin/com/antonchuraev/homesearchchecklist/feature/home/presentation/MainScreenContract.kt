package com.antonchuraev.homesearchchecklist.feature.home.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits

/**
 * Checklist with progress calculated from default fill
 */
data class ChecklistWithProgress(
    val checklist: Checklist,
    val totalItems: Int,
    val checkedItems: Int
) {
    val progress: Float
        get() = if (totalItems > 0) checkedItems.toFloat() / totalItems else 0f
}

sealed interface MainScreenState : State {

    val userLimits: UserLimits?

    data object Loading : MainScreenState {
        override val userLimits: UserLimits? = null
    }
    data class Success(
        val checklists: List<ChecklistWithProgress>,
        val subscriptionStatus: SubscriptionStatus,
        val formattedExpirationDate: String? = null,
        val aiCredits: Int = 0,
        val showLimitReachedDialog: Boolean = false,
        override val userLimits: UserLimits?,
        /** True while the hamburger-pulse onboarding hint should animate. */
        val showHamburgerHint: Boolean = false,
    ) : MainScreenState
}

sealed interface MainScreenIntent : Intent {

    data object OnAddChecklistClick : MainScreenIntent

    data object OnAddChecklistFromTemplatesClick : MainScreenIntent

    data object OnAiAnalyzeClick : MainScreenIntent

    data class OnChecklistClick(val checklistWithProgress: ChecklistWithProgress) : MainScreenIntent

    data object OnPremiumBannerClick : MainScreenIntent

    data object OnCreditsClick : MainScreenIntent

    data object OnDismissLimitDialog : MainScreenIntent

    data object OnUpgradeToPremiumClick : MainScreenIntent

    data class OnReorderChecklists(val orderedIds: List<Long>) : MainScreenIntent

    data object OnUpdateFeedClick : MainScreenIntent

    /** Emitted when the hamburger-pulse animation finishes (3 cycles) or
     *  the user opens the drawer before animation ends. Persists the flag. */
    data object OnHamburgerHintCompleted : MainScreenIntent
}
