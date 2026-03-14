package com.antonchuraev.homesearchchecklist.feature.splash.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.RestorePurchasesUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetOnboardingVariantUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetOnboardingVariantUseCase.OnboardingVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.measureTimedValue

class SplashViewModel(
    private val userDataRepository: UserDataRepository,
    private val paywallRepository: PaywallRepository,
    private val restorePurchasesUseCase: RestorePurchasesUseCase,
    private val appNavigator: AppNavigator,
    private val appScope: CoroutineScope,
    private val logger: AppLogger,
    private val analyticsTracker: AnalyticsTracker,
    private val getOnboardingVariant: GetOnboardingVariantUseCase
) : ViewModel() {

    init {
        // Background sync — completely independent, on appScope
        log("start init")
        startBackgroundSync()
        log("started startBackgroundSync")
        // Navigation — fast path, only reads cache
        viewModelScope.launch {

            log("start getUserData()")
            val (cached, duration) = measureTimedValue {
                userDataRepository.getUserData()
            }
            log("getUserData() took ${duration.inWholeMilliseconds}ms, userId=${cached.userId.take(8)}, isBlank:${cached.userId.isBlank()}")

            if (cached.userId.isNotBlank()) {
                // Set analytics userId BEFORE navigation to avoid unattributed screen_view events
                analyticsTracker.setUserId(cached.userId)
                navigateTo(cached.isOnboardingPassed)
            } else {
                // New user (first launch only): must wait for registration
                val result = userDataRepository.ensureUserRegistered()
                val userData = result.getOrNull()?.userData ?: cached

                result.onSuccess { data ->
                    analyticsTracker.setUserId(data.userData.userId)
                    appScope.launch { linkWithPaywall(data.userData.userId, isNewUser = data.isNewUser) }
                }

                navigateTo(userData.isOnboardingPassed)
            }
        }
    }



    private fun startBackgroundSync() {
        appScope.launch {
            val cached = userDataRepository.getUserData()
            if (cached.userId.isBlank()) return@launch

            analyticsTracker.setUserId(cached.userId)
            launch { runCatching { userDataRepository.syncWithServer() } }
            launch { runCatching { linkWithPaywall(cached.userId, isNewUser = false) } }
        }
    }

    private fun navigateTo(isOnboardingPassed: Boolean) {
        with(appNavigator) {
            if (isOnboardingPassed) {
                navigateToMainScreen(clearBackStack = true)
            } else {
                when (getOnboardingVariant()) {
                    OnboardingVariant.INTERACTIVE -> navigateToInteractiveOnboarding()
                    OnboardingVariant.DEFAULT -> navigateToOnboarding()
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

                // For returning users, auto-restore purchases + credits via UseCase
                if (!isNewUser && !loginResult.isNewCustomer) {
                    restorePurchasesUseCase()
                }
            }
    }

    private fun log(text: String){
        logger.debug(TAG , text)
    }

    companion object {
        private const val TAG = "SplashViewModel"
    }
}
