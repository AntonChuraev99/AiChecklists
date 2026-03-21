package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.formatExpirationDate
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubscriptionStatusViewModel(
    private val navigator: AppNavigator,
    private val getSubscriptionStatusUseCase: GetSubscriptionStatusUseCase,
    private val userDataRepository: UserDataRepository
) : AppViewModel<SubscriptionStatusState, SubscriptionStatusIntent, Nothing>() {

    private val _screenState = MutableStateFlow(SubscriptionStatusState())
    override val screenState: StateFlow<SubscriptionStatusState> = _screenState.asStateFlow()

    init {
        observeData()
    }

    fun setShowSuccessMessage(show: Boolean) {
        _screenState.update { it.copy(showSuccessMessage = show) }
    }

    override fun onIntent(intent: SubscriptionStatusIntent) {
        when (intent) {
            SubscriptionStatusIntent.OnBackClick -> navigator.onBack()
            SubscriptionStatusIntent.DismissSuccessMessage -> {
                _screenState.update { it.copy(showSuccessMessage = false) }
            }
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                getSubscriptionStatusUseCase(),
                userDataRepository.getUserDataFlow().map { it.aiCredits }
            ) { status, aiCredits ->
                Pair(status, aiCredits)
            }.collect { (status, aiCredits) ->
                _screenState.update {
                    it.copy(
                        isLoading = false,
                        subscriptionStatus = status,
                        formattedExpirationDate = status.expirationDate?.let { timestamp ->
                            formatExpirationDate(timestamp)
                        },
                        aiCredits = aiCredits
                    )
                }
            }
        }
    }
}


