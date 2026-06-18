package com.antonchuraev.homesearchchecklist.feature.splash.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.RestorePurchasesUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.FirstChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetFirstChecklistVariantUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetFirstChecklistVariantUseCase.FirstChecklistVariant
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetOnboardingVariantUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetOnboardingVariantUseCase.OnboardingVariant
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.first_checklist_item_1
import aichecklists.core.designsystem.generated.resources.first_checklist_item_2
import aichecklists.core.designsystem.generated.resources.first_checklist_item_3
import aichecklists.core.designsystem.generated.resources.first_checklist_item_4
import aichecklists.core.designsystem.generated.resources.first_checklist_title
import org.jetbrains.compose.resources.getString
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
    private val getOnboardingVariant: GetOnboardingVariantUseCase,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val remoteConfigProvider: RemoteConfigProvider,
    private val getFirstChecklistVariant: GetFirstChecklistVariantUseCase,
    private val checklistRepository: ChecklistRepository,
    private val firstChecklistRepository: FirstChecklistRepository,
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
            var isNewUser = false
            val userData = if (cached.userId.isNotBlank()) {
                analyticsTracker.setUserId(cached.userId)
                cached
            } else {
                val result = userDataRepository.ensureUserRegistered()
                val newUserData = result.getOrNull()?.userData ?: cached

                result.onSuccess { data ->
                    isNewUser = data.isNewUser
                    analyticsTracker.setUserId(data.userData.userId)
                    appScope.launch { linkWithPaywall(data.userData.userId, isNewUser = data.isNewUser) }
                }
                newUserData
            }

            var rcActivated: Boolean? = null
            var rcFetchMs: Long? = null
            if (!userData.isOnboardingPassed) {
                // Reactively await the real fetch — NO fixed timeout cap. fetchAndActivate()
                // suspends exactly until the fetch completes: fast network ~1s, slow cold-start
                // network longer. The "dead network" ceiling lives in Firebase RC's own
                // fetchTimeout (FirebaseRemoteConfigProvider.setFetchTimeoutInSeconds), not in a
                // guessed constant here. The previous hard 3s cap aborted slow first-launch
                // fetches on real devices, so the A/B experiment assignment never arrived and the
                // onboarding variant silently fell back to the empty client default (slides) —
                // collapsing the live split to 0% "none" in production while emulators (instant
                // fetch) looked fine.
                val (activated, fetchDuration) = measureTimedValue {
                    runCatching { remoteConfigProvider.fetchAndActivate() }.getOrDefault(false)
                }
                rcActivated = activated
                rcFetchMs = fetchDuration.inWholeMilliseconds
                if (!activated) {
                    logger.warning(
                        TAG,
                        "RC fetchAndActivate did not complete before onboarding (network slow/unreachable) — " +
                            "variant falls back to client default",
                    )
                }
                log("fetchAndActivate (onboarding pending) activated=$activated took ${fetchDuration.inWholeMilliseconds}ms, hasUserId=${userData.userId.isNotBlank()}")
            }

            // First-checklist A/B experiment: cohort attribution (all users) + auto-create
            // the starter checklist (new users only). Runs AFTER fetchAndActivate so the
            // variant is read from fresh RC, and BEFORE navigate so the first screen_view
            // already carries the `first_checklist_variant` user property.
            applyFirstChecklistExperiment(userData, isNewUser)

            navigateTo(userData.isOnboardingPassed, rcActivated, rcFetchMs)
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

    private fun navigateTo(
        isOnboardingPassed: Boolean,
        rcActivated: Boolean? = null,
        rcFetchMs: Long? = null,
    ) {
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
                    // Surface RC resolution health so a future "experiment never assigns" bug
                    // (slow-network fetch miss → empty default → forced slides) shows up in
                    // analytics, not only in user reports. RC_VALUE_EMPTY=true means the fetch
                    // returned nothing and we fell back to the client default — the exact signal
                    // that silently collapsed the A/B split to 0% none in prod.
                    val rawRcValue = remoteConfigProvider.getString(RemoteConfigKeys.ONBOARDING, "")
                    analyticsTracker.event(
                        AnalyticsEvents.Onboarding.RC_RESOLVED,
                        buildMap {
                            put(AnalyticsParams.VARIANT, variantName)
                            put(AnalyticsParams.RC_VALUE_EMPTY, rawRcValue.isEmpty())
                            rcActivated?.let { put(AnalyticsParams.RC_ACTIVATED, it) }
                            rcFetchMs?.let { put(AnalyticsParams.FETCH_MS, it) }
                        },
                    )
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

    /**
     * Applies the first-checklist A/B experiment.
     *
     * Always sets the `first_checklist_variant` user property (so analytics can split
     * cohorts), and — only for brand-new users in the `auto_create` treatment — seeds a
     * one-time "Your first checklist" starter template.
     *
     * The whole block is guarded so a failure here never blocks or crashes the splash flow.
     */
    private suspend fun applyFirstChecklistExperiment(userData: UserData, isNewUser: Boolean) {
        runCatching {
            val variant = getFirstChecklistVariant()
            analyticsTracker.setUserProperties(mapOf("first_checklist_variant" to variant.name))

            val uid = userData.userId
            val alreadyCreated = uid.isNotBlank() && firstChecklistRepository.isFirstChecklistCreated(uid)

            if (isNewUser && variant == FirstChecklistVariant.AUTO_CREATE && uid.isNotBlank() && !alreadyCreated) {
                // New users have 0 checklists, so they are always under the Free tier limit (4).
                // Splash has no UserLimits access; skipping the gate here is safe by construction.
                log("auto-creating first checklist for new user uid=${uid.take(8)}")
                checklistRepository.addChecklist(buildFirstChecklist())
                firstChecklistRepository.markFirstChecklistCreated(uid)
                analyticsTracker.event(AnalyticsEvents.Onboarding.FIRST_CHECKLIST_AUTO_CREATED, mapOf(AnalyticsParams.VARIANT to variant.name))
            }
        }.onFailure { e ->
            logger.error(TAG, "applyFirstChecklistExperiment failed: ${e.message}", e)
        }
    }

    /**
     * Builds the localized "Your first checklist" starter template (title + 3 tip items).
     * `addChecklist` creates the default fill automatically, so no fill is built here.
     */
    private suspend fun buildFirstChecklist(): Checklist = Checklist(
        name = getString(Res.string.first_checklist_title),
        items = listOf(
            ChecklistItem(text = getString(Res.string.first_checklist_item_1)),
            ChecklistItem(text = getString(Res.string.first_checklist_item_2)),
            ChecklistItem(text = getString(Res.string.first_checklist_item_3)),
            ChecklistItem(text = getString(Res.string.first_checklist_item_4)),
        ),
    )

    private fun log(text: String){
        logger.debug(TAG , text)
    }

    companion object {
        private const val TAG = "SplashViewModel"
    }
}
