package com.antonchuraev.homesearchchecklist.feature.splash.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.launch

class SplashViewModel(
    private val userDataRepository: UserDataRepository,
    private val appNavigator: AppNavigator
) : ViewModel() {

    init {
        viewModelScope.launch {
            val userData = userDataRepository.getUserData()

            with(appNavigator) {
                when (userData.isOnboardingPassed) {
                    true -> navigateToMainScreen()
                    false -> navigateToOnboarding()
                }
            }
        }
    }
}


