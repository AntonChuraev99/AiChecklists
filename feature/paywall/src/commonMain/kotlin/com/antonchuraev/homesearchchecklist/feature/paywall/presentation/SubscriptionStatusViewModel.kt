package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubscriptionStatusViewModel(
    private val navigator: AppNavigator,
    private val getSubscriptionStatusUseCase: GetSubscriptionStatusUseCase
) : AppViewModel<SubscriptionStatusState, SubscriptionStatusIntent, Nothing>() {

    private val _screenState = MutableStateFlow(SubscriptionStatusState())
    override val screenState: StateFlow<SubscriptionStatusState> = _screenState.asStateFlow()

    init {
        observeSubscriptionStatus()
    }

    override fun onIntent(intent: SubscriptionStatusIntent) {
        when (intent) {
            SubscriptionStatusIntent.OnBackClick -> navigator.onBack()
            SubscriptionStatusIntent.OnManageSubscriptionClick -> {
                // TODO: Open platform-specific subscription management
                // Android: Play Store subscriptions
                // iOS: App Store subscriptions
            }
        }
    }

    private fun observeSubscriptionStatus() {
        viewModelScope.launch {
            getSubscriptionStatusUseCase().collect { status ->
                _screenState.update {
                    it.copy(
                        isLoading = false,
                        subscriptionStatus = status,
                        formattedExpirationDate = status.expirationDate?.let { timestamp ->
                            formatExpirationDate(timestamp)
                        }
                    )
                }
            }
        }
    }
}

expect fun formatExpirationDate(timestamp: Long): String
