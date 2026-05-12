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
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
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
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val remoteConfigProvider: RemoteConfigProvider
) : ViewModel() {

    init {
        // Background sync — completely independent, on appScope
        log("start init")
        startBackgroundSync()
        log("started startBackgroundSync")
        viewModelScope.launch {

            log("start getUserData()")
            val (cached, duration) = measureTimedValue {
                userDataRepository.getUserData()
            }
            log("getUserData() took ${duration.inWholeMilliseconds}ms, userId=${cached.userId.take(8)}, isBlank:${cached.userId.isBlank()}")

            // Step 1: ensure we have a userId BEFORE fetching Remote Config so
            // Firebase A/B Testing can attribute the user to an experiment cohort.
            val userData = if (cached.userId.isNotBlank()) {
                analyticsTracker.setUserId(cached.userId)
                cached
            } else {
                // New user (first launch only): must wait for registration.
                val result = userDataRepository.ensureUserRegistered()
                val newUserData = result.getOrNull()?.userData ?: cached

                result.onSuccess { data ->
                    analyticsTracker.setUserId(data.userData.userId)
                    appScope.launch { linkWithPaywall(data.userData.userId, isNewUser = data.isNewUser) }
                }
                newUserData
            }

            // Step 2: whenever onboarding still needs to be shown, BLOCK
            // navigation until Remote Config is activated. Without this gate
            // the A/B variant resolver reads stale defaults and every user
            // collapses into the same client-side fallback variant — exactly
            // the bug we are fixing here (see Amplitude 14d distribution:
            // interactive_onboarding=47 uniques vs slides=0).
            //
            // Returning users who already passed onboarding skip the wait —
            // their variant no longer matters and a 3s gate would only delay
            // the first frame for no benefit. RC refresh still happens via
            // startBackgroundSync so other RC-driven features stay fresh.
            if (!userData.isOnboardingPassed) {
                val activated = withTimeoutOrNull(REMOTE_CONFIG_FETCH_TIMEOUT) {
                    runCatching { remoteConfigProvider.fetchAndActivate() }.getOrDefault(false)
                }
                log("fetchAndActivate (onboarding pending) activated=$activated, hasUserId=${userData.userId.isNotBlank()}")
            }

            navigateTo(userData.isOnboardingPassed)
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
                        OnboardingVariant.NONE -> "none"
                    }
                    analyticsTracker.setUserProperties(mapOf("onboarding_type" to variantName))
                    when (variant) {
                        OnboardingVariant.INTERACTIVE -> navigateToInteractiveOnboarding()
                        OnboardingVariant.DEFAULT -> navigateToOnboarding()
                        OnboardingVariant.NONE -> {
                            // Skip onboarding entirely; persist as passed so future launches
                            // bypass the variant check even if RC flips back to interactive/default.
                            appScope.launch { runCatching { completeOnboardingUseCase() } }
                            navigateToMainScreen(clearBackStack = true)
                        }
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
