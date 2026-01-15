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
            // Ensure user is registered with the server (or retrieve existing user)
            // This uses device ID to prevent abuse from reinstalling the app
            val registrationResult = userDataRepository.ensureUserRegistered()

            // Get user data (either from registration or cached)
            val userData = registrationResult.getOrNull() ?: userDataRepository.getUserData()

            with(appNavigator) {
                when (userData.isOnboardingPassed) {
                    true -> navigateToMainScreen()
                    false -> navigateToOnboarding()
                }
            }
        }
    }
}


