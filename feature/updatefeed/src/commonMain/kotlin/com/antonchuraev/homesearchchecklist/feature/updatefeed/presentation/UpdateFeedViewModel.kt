package com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.formatExpirationDate
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.deeplink.UpdateFeedDeepLinkHandler
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.repository.UpdateFeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class UpdateFeedViewModel(
    private val repository: UpdateFeedRepository,
    private val navigator: AppNavigator,
    private val deepLinkHandler: UpdateFeedDeepLinkHandler,
    private val getSubscriptionStatusUseCase: GetSubscriptionStatusUseCase,
    private val analyticsTracker: AnalyticsTracker,
    private val logger: AppLogger
) : AppViewModel<UpdateFeedScreenState, UpdateFeedScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow<UpdateFeedScreenState>(UpdateFeedScreenState.Loading)
    override val screenState: StateFlow<UpdateFeedScreenState> = _screenState

    init {
        sendIntent(UpdateFeedScreenIntent.OnLoad)
    }

    override fun onIntent(intent: UpdateFeedScreenIntent) {
        when (intent) {
            UpdateFeedScreenIntent.OnLoad -> loadReleases()
            UpdateFeedScreenIntent.OnBackClick -> navigator.onBack()
            is UpdateFeedScreenIntent.OnActionClick -> handleActionClick(intent)
            UpdateFeedScreenIntent.OnPremiumBannerClick -> handlePremiumBannerClick()
        }
    }

    private fun loadReleases() {
        viewModelScope.launch {
            combine(
                flow { emit(repository.getReleases()) },
                getSubscriptionStatusUseCase()
            ) { releases, subscriptionStatus ->
                if (releases.isEmpty()) {
                    UpdateFeedScreenState.Empty
                } else {
                    UpdateFeedScreenState.Success(
                        releases = releases,
                        isPremium = subscriptionStatus.isActive,
                        formattedExpirationDate = subscriptionStatus.expirationDate?.let {
                            formatExpirationDate(it)
                        }
                    )
                }
            }.collect { state ->
                _screenState.value = state
            }
        }
    }

    private fun handleActionClick(intent: UpdateFeedScreenIntent.OnActionClick) {
        analyticsTracker.event(
            name = "update_feed_action_click",
            params = mapOf(
                "post_id" to intent.postId,
                "label" to intent.action.label,
                "deep_link" to intent.action.deepLink
            )
        )
        val handled = deepLinkHandler.handle(intent.action.deepLink)
        if (!handled) {
            logger.debug("UpdateFeedViewModel", "Unhandled deeplink: ${intent.action.deepLink}")
        }
    }

    private fun handlePremiumBannerClick() {
        val state = _screenState.value as? UpdateFeedScreenState.Success ?: return
        if (state.isPremium) {
            navigator.navigateToSubscriptionStatus()
        } else {
            navigator.navigateToPaywall(source = "update_feed")
        }
    }
}
