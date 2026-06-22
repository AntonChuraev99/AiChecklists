package com.antonchuraev.homesearchchecklist.feature.home.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
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
        val isGoogleLinked: Boolean = false,
        val googleEmail: String? = null,
        val googleDisplayName: String? = null,
        /**
         * True when the sign-in/sync banner should be shown. Derived in the ViewModel from:
         * not Google-linked, more than one checklist (a brand-new user with an empty list or a
         * single auto-created checklist is not nagged), the lifetime dismiss count is below the
         * permanent-hide threshold, and the banner wasn't dismissed in this session.
         */
        val showSyncBanner: Boolean = false,
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

    data object OnSignInClick : MainScreenIntent

    data object OnSignOutClick : MainScreenIntent

    data object OnAiChatClick : MainScreenIntent

    /** Emitted when the user taps the sync-banner close button. Hides it for this session
     *  (in-memory) and increments the persistent lifetime dismiss count. */
    data object OnDismissSyncBanner : MainScreenIntent
}

sealed interface MainScreenSideEffect : SideEffect {
    data class ShowSnackbar(val messageKey: String) : MainScreenSideEffect
    data object NavigateToAiChat : MainScreenSideEffect
}
