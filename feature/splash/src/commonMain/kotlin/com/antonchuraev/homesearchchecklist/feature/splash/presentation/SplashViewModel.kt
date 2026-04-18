package com.antonchuraev.homesearchchecklist.feature.splash.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class SplashViewModel(
    private val userDataRepository: UserDataRepository,
    private val paywallRepository: PaywallRepository,
    private val restorePurchasesUseCase: RestorePurchasesUseCase,
    private val appNavigator: AppNavigator,
    private val appScope: CoroutineScope,
    private val logger: AppLogger,
    private val analyticsTracker: AnalyticsTracker,
    private val getOnboardingVariant: GetOnboardingVariantUseCase,
    private val remoteConfigProvider: RemoteConfigProvider
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
                // Returning user: navigate immediately from cache; Remote Config fetch
                // runs in background (startBackgroundSync) so A/B variant applies next launch.
                navigateTo(cached.isOnboardingPassed)
            } else {
                // New user (first launch only): must wait for registration
                val result = userDataRepository.ensureUserRegistered()
                val userData = result.getOrNull()?.userData ?: cached

                result.onSuccess { data ->
                    // Analytics userId MUST be set before fetchAndActivate so the
                    // Firebase A/B experiment can attribute this user to a variant.
                    analyticsTracker.setUserId(data.userData.userId)
                    appScope.launch { linkWithPaywall(data.userData.userId, isNewUser = data.isNewUser) }
                }

                // Block navigation until Remote Config is activated, so the correct
                // onboarding variant is known before the first screen is shown.
                // Timeout keeps Splash responsive on poor networks (falls back to defaults).
                val activated = withTimeoutOrNull(REMOTE_CONFIG_FETCH_TIMEOUT) {
                    runCatching { remoteConfigProvider.fetchAndActivate() }.getOrDefault(false)
                }
                log("fetchAndActivate (new user) activated=$activated")

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
            // Refresh Remote Config so A/B variants & feature flags apply on next launch.
            launch { runCatching { remoteConfigProvider.fetchAndActivate() } }
        }
    }

    private fun navigateTo(isOnboardingPassed: Boolean) {
        try {
            with(appNavigator) {
                if (isOnboardingPassed) {
                    navigateToMainScreen(clearBackStack = true)
                } else {
                    val variant = getOnboardingVariant()
                    val variantName = when (variant) {
                        OnboardingVariant.INTERACTIVE -> "interactive"
                        OnboardingVariant.DEFAULT -> "slides"
                    }
                    analyticsTracker.setUserProperties(mapOf("onboarding_type" to variantName))
                    when (variant) {
                        OnboardingVariant.INTERACTIVE -> navigateToInteractiveOnboarding()
                        OnboardingVariant.DEFAULT -> navigateToOnboarding()
                    }
                }
            }
        } catch (e: IllegalStateException) {
            // Navigation lifecycle conflict after process death restore —
            // restored NavBackStackEntry may not have reached CREATED state yet.
            // Safe to ignore: user is already on the correct destination.
            log("navigateTo skipped: ${e.message}")
        }
    }

    /**
     * Links the user with RevenueCat after successful registration.
     *
     * Always calls [PaywallRepository.logIn] regardless of the local linked flag —
     * RevenueCat's logIn is idempotent (no-op when appUserId already matches), so
     * this guarantees drift between the anonymous RC customer and our server UUID
     * is corrected on every launch.
     *
     * Auto-restore runs exactly once: the first time logIn succeeds for a returning
     * user whose account was not yet linked locally ([wasLinked] == false).
     */
    private suspend fun linkWithPaywall(userId: String, isNewUser: Boolean) {
        if (!paywallRepository.isConfigured()) return

        // Read before logIn — used to gate one-time auto-restore
        val wasLinked = userDataRepository.isPaywallLinked()

        paywallRepository.logIn(userId)
            .onSuccess { loginResult ->
                if (!wasLinked) {
                    userDataRepository.setPaywallLinked(true)
                }
                // Defensive refresh: logIn may return cached status; server call ensures fresh data
                paywallRepository.refreshSubscriptionStatus()

                // Auto-restore only on first successful link for returning users
                if (!wasLinked && !isNewUser && !loginResult.isNewCustomer) {
                    restorePurchasesUseCase()
                }

                log("linkWithPaywall: logIn success, isNewCustomer=${loginResult.isNewCustomer}, wasLinked=$wasLinked")
            }
            .onFailure { err ->
                logger.error(TAG, "linkWithPaywall failed: ${err.message}")
            }
    }

    private fun log(text: String){
        logger.debug(TAG , text)
    }

    companion object {
        private const val TAG = "SplashViewModel"
        private val REMOTE_CONFIG_FETCH_TIMEOUT = 3.seconds
    }
}
