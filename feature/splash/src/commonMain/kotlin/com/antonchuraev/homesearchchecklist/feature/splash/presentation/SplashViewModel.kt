package com.antonchuraev.homesearchchecklist.feature.splash.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.launch

class SplashViewModel(
    private val userDataRepository: UserDataRepository,
    private val paywallRepository: PaywallRepository,
    private val appNavigator: AppNavigator
) : ViewModel() {

    init {
        viewModelScope.launch {
            // Ensure user is registered with the server (or retrieve existing user)
            // This uses device ID to prevent abuse from reinstalling the app
            val registrationResult = userDataRepository.ensureUserRegistered()

            // Link with RevenueCat (fire-and-forget, don't block navigation)
            registrationResult.onSuccess { registrationData ->
                launch {
                    linkWithPaywall(
                        userId = registrationData.userData.userId,
                        isNewUser = registrationData.isNewUser
                    )
                }
            }

            // Get user data (either from registration or cached)
            val userData = registrationResult.getOrNull()?.userData
                ?: userDataRepository.getUserData()

            with(appNavigator) {
                when (userData.isOnboardingPassed) {
                    true -> navigateToMainScreen(clearBackStack = true)
                    false -> navigateToOnboarding()
                }
            }
        }
    }

    /**
     * Links the user with RevenueCat after successful registration.
     * For returning users (isNewUser=false), also triggers auto-restore.
     * For already linked users, refreshes subscription status from RevenueCat.
     */
    private suspend fun linkWithPaywall(userId: String, isNewUser: Boolean) {
        // Check if RevenueCat is configured
        if (!paywallRepository.isConfigured()) {
            return
        }

        // If already linked, just refresh subscription status
        if (userDataRepository.isPaywallLinked()) {
            paywallRepository.refreshSubscriptionStatus()
            return
        }

        // Link with RevenueCat
        paywallRepository.logIn(userId)
            .onSuccess { loginResult ->
                userDataRepository.setPaywallLinked(true)

                // For returning users, auto-restore purchases
                if (!isNewUser && !loginResult.isNewCustomer) {
                    paywallRepository.restorePurchases()
                }
            }
    }
}
