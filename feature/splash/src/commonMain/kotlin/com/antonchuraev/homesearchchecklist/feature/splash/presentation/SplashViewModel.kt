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
import com.antonchuraev.homesearchchecklist.core.common.api.NavExperimentResolver
import com.antonchuraev.homesearchchecklist.core.datastore.api.ActivationPrefsRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.FirstChecklistRepository
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
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
    private val activationPrefsRepository: ActivationPrefsRepository,
    private val navExperimentResolver: NavExperimentResolver,
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
            var rcError: String? = null
            var rcAttempts: Int? = null
            var rcRecoveredOnAttempt: Int? = null
            if (!userData.isOnboardingPassed) {
                // Reactively await the real fetch — NO fixed timeout cap. fetchAndActivate()
                // suspends exactly until the fetch completes: fast network ~1s, slow cold-start
                // network longer. The "dead network" ceiling lives in Firebase RC's own
                // fetchTimeout (FirebaseRemoteConfigProvider.setFetchTimeoutInSeconds), not in a
                // guessed constant here. The previous hard 3s cap aborted slow first-launch
                // fetches on real devices, so the A/B experiment assignment never arrived and the
                // onboarding variant silently fell back to the empty client default (the fallback
                // arm — slides then, ai_welcome since 2026-07-28) — collapsing the live split to
                // 0% "none" in production while emulators (instant fetch) looked fine.
                val rc = fetchAndActivateWithFastRetry()
                rcActivated = rc.activated
                rcFetchMs = rc.totalMs
                rcAttempts = rc.attempts
                rcRecoveredOnAttempt = rc.recoveredOnAttempt
                val fetchError = remoteConfigProvider.lastFetchError()
                rcError = fetchError?.let { "${it::class.simpleName}: ${it.message}" }
                if (!rc.activated) {
                    // Not swallowed anymore: record the real exception so a prod-only signing /
                    // App Check fetch rejection lands in Crashlytics AND in the
                    // onboarding_rc_resolved.rc_error analytics param. Reproducible on the Play
                    // internal-test track, which is signed with the Google Play App Signing key.
                    logger.error(
                        TAG,
                        "RC fetchAndActivate failed before onboarding (variant falls back to client default) — rcError=$rcError",
                        fetchError,
                    )
                }
                log("fetchAndActivate (onboarding pending) activated=${rc.activated} took ${rc.totalMs}ms, attempts=${rc.attempts}, recoveredOnAttempt=${rc.recoveredOnAttempt}, hasUserId=${userData.userId.isNotBlank()}")
            }

            // First-checklist A/B experiment: cohort attribution (all users) + auto-create
            // the starter checklist (new users only). Runs AFTER fetchAndActivate so the
            // variant is read from fresh RC, and BEFORE navigate so the first screen_view
            // already carries the `first_checklist_variant` user property.
            applyFirstChecklistExperiment(userData, isNewUser)

            // Navigation A/B arm — resolved HERE, before navigating, because Splash is the last
            // moment at which no shell is mounted. App.kt latches the arm as soon as a shell
            // appears: resolving it later would swap AdaptiveNavigationShell for V2NavigationShell
            // under a live screen, which disposes and recreates the whole NavDisplay subtree (the
            // user loses scroll position and in-progress edits) and re-roots the back stack
            // mid-task. Deliberately awaited rather than fire-and-forget for the same reason.
            //
            // A DataStore read, or the v2 default for an install that never opened Settings.
            val resolvedVariant = navExperimentResolver.ensureResolved()
            log("nav variant resolved before navigate: $resolvedVariant")

            // NOTE: the v1 Inbox rollback (ReconcileInboxForControlArmUseCase) used to run here and
            // was REMOVED on 2026-08-03. It cleared `isInbox` whenever the user resolved to v1, which
            // was correct while the arm was a permanent RC assignment. Now that v1/v2 is a setting the
            // user can flip back, clearing the flag on every visit to v1 would orphan the Inbox: the
            // next switch to v2 would auto-create a SECOND one and the captured tasks would sit in an
            // ordinary checklist nobody looks at. Reachability in v1 is solved where it belongs, in
            // the screen: MainScreenViewModel lists the unfiltered `checklists` flow, so an Inbox
            // holding tasks shows up as an ordinary row while KEEPING its flag — which is what makes
            // the switch reversible.

            navigateTo(userData.isOnboardingPassed, rcActivated, rcFetchMs, rcError, rcAttempts, rcRecoveredOnAttempt)
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

    /**
     * Fetches + activates Remote Config for the onboarding gate, with up to
     * [RC_MAX_FAST_ATTEMPTS] fast-only attempts.
     *
     * ~25% of first launches still fail fetchAndActivate() (prod analytics, 2026-07-07); the single
     * biggest *transient* cause is "Firebase Installations failed to get installation auth token" —
     * FIS registration races the very first fetch on a cold start. It can need MORE than one retry
     * to settle, so a single retry (the previous behavior) still gave up too early and forced the
     * user onto the empty client default (the fallback arm), contaminating every RC-driven A/B —
     * which is why A/B analysis must filter on rc_activated=true regardless of which arm that is.
     * We now retry
     * up to [RC_MAX_FAST_ATTEMPTS] times, with a small [RC_RETRY_BACKOFF_MS] backoff to let the FIS
     * token propagate — but ONLY while each attempt fails FAST. A slow failure means the SDK fetch
     * timeout elapsed (offline / dead network), where more attempts would just multiply the splash
     * wait, so we stop at the first slow failure. This is NOT a UI timer — each attempt is bounded
     * by the SDK's own setFetchTimeoutInSeconds; we merely re-issue cheap, fast-failing calls.
     * (The remaining bulk of failures are API-key / App Check authorization — a Cloud config fix,
     * not something any client retry can resolve.)
     *
     * @return an [RcFetchResult] carrying activation success, total fetch millis, how many attempts
     *   were issued, and which attempt recovered (1-based; 0 = never) — the last two feed the
     *   onboarding_rc_resolved telemetry so the next release can measure warm-up + retry recovery.
     */
    private suspend fun fetchAndActivateWithFastRetry(): RcFetchResult {
        var totalMs = 0L
        repeat(RC_MAX_FAST_ATTEMPTS) { attempt ->
            val (ok, d) = measureTimedValue {
                try {
                    remoteConfigProvider.fetchAndActivate()
                } catch (e: CancellationException) {
                    // Never swallow cancellation — re-throwing keeps structured concurrency intact.
                    // Prod logs showed JobCancellationException among RC failures: a runCatching that
                    // ate it would keep retrying inside an already-cancelled scope.
                    throw e
                } catch (e: Throwable) {
                    false
                }
            }
            val ms = d.inWholeMilliseconds
            totalMs += ms
            val attemptsSoFar = attempt + 1
            if (ok) {
                return RcFetchResult(
                    activated = true,
                    totalMs = totalMs,
                    attempts = attemptsSoFar,
                    recoveredOnAttempt = attemptsSoFar,
                )
            }
            // Only re-issue FAST, genuinely-transient failures (the FIS installation-token race).
            // Stop otherwise: a slow failure = SDK fetch-timeout (offline); a throttled fetch would
            // just re-throttle; an authorization failure ("not authorized" / API-key / App Check) is
            // a Cloud-config problem, never client-recoverable — prod data shows it is the single
            // largest failure bucket, so retrying it only wastes splash time.
            if (ms > RC_FAST_FAIL_CEILING_MS || !isTransientFetchError(remoteConfigProvider.lastFetchError())) {
                return RcFetchResult(
                    activated = false,
                    totalMs = totalMs,
                    attempts = attemptsSoFar,
                    recoveredOnAttempt = 0,
                )
            }
            if (attempt < RC_MAX_FAST_ATTEMPTS - 1) {
                log("fetchAndActivate failed fast (${ms}ms) — transient retry ${attempt + 1}/${RC_MAX_FAST_ATTEMPTS - 1} (likely FIS token race)")
                delay(RC_RETRY_BACKOFF_MS)
                totalMs += RC_RETRY_BACKOFF_MS
            }
        }
        return RcFetchResult(
            activated = false,
            totalMs = totalMs,
            attempts = RC_MAX_FAST_ATTEMPTS,
            recoveredOnAttempt = 0,
        )
    }

    /**
     * Outcome of [fetchAndActivateWithFastRetry].
     *
     * @property activated did fetchAndActivate() ultimately succeed
     * @property totalMs total time spent across all attempts + backoffs
     * @property attempts how many fetch attempts were issued (1-based)
     * @property recoveredOnAttempt 1-based index of the attempt that succeeded, or 0 if it never did
     */
    private data class RcFetchResult(
        val activated: Boolean,
        val totalMs: Long,
        val attempts: Int,
        val recoveredOnAttempt: Int,
    )

    /**
     * A fast RC fetch failure is worth retrying ONLY if it is a genuine transient (FIS token race).
     * Authorization rejections (API-key restriction / App Check) and throttling are not
     * client-recoverable and must not be re-issued. A null error with a false result (e.g. an empty
     * activation) is treated as transient since a retry is cheap.
     */
    private fun isTransientFetchError(error: Throwable?): Boolean {
        val message = error?.message?.lowercase() ?: return true
        if ("not authorized" in message || "api key" in message) return false
        if ("throttl" in message) return false
        return true
    }

    private fun navigateTo(
        isOnboardingPassed: Boolean,
        rcActivated: Boolean? = null,
        rcFetchMs: Long? = null,
        rcError: String? = null,
        rcAttempts: Int? = null,
        rcRecoveredOnAttempt: Int? = null,
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
                        OnboardingVariant.AI_WELCOME -> "ai_welcome"
                    }
                    analyticsTracker.setUserProperties(mapOf("onboarding_type" to variantName))
                    // Surface RC resolution health so a future "experiment never assigns" bug
                    // (slow-network fetch miss → empty default → forced fallback arm) shows up in
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
                            rcError?.let { put(AnalyticsParams.RC_ERROR, it) }
                            rcAttempts?.let { put(AnalyticsParams.RC_ATTEMPTS, it) }
                            rcRecoveredOnAttempt?.let { put(AnalyticsParams.RC_RECOVERED_ON_ATTEMPT, it) }
                        },
                    )
                    when (variant) {
                        OnboardingVariant.INTERACTIVE -> navigateToInteractiveOnboarding()
                        OnboardingVariant.DEFAULT -> navigateToOnboarding()
                        OnboardingVariant.AI_WELCOME -> navigateToWelcomeOnboarding()
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
     * Applies the first-checklist experiment, branching on the `activation_bundle_v1` RC flag.
     *
     * Always sets the `first_checklist_variant` user property (so the legacy A/B cohort attribution
     * survives in both arms). Then:
     *
     *  - **Activation bundle ON (default):** SKIP the static auto-seed entirely so the new user
     *    lands on the empty MainScreen and gets the AI first-run hero instead. For a brand-new
     *    registration, persist the new-user-pending flag so the user's FIRST AI checklist triggers
     *    the activation funnel (FIRST_AI_CHECKLIST_CREATED + reminder opt-in) downstream.
     *  - **Activation bundle OFF:** EXACT pre-activation behavior — seed the one-time "Your first
     *    checklist" starter template for brand-new users in the `auto_create` treatment. Fully
     *    reversible: flipping the flag back to false restores the legacy flow with no code change.
     *    (Since 2026-07-26 this arm ALSO gets the new-user-pending marker. That is analytics-only:
     *    it makes FIRST_AI_CHECKLIST_CREATED reachable for control users so the two arms are
     *    comparable at all. The reminder opt-in stays treatment-only — it is gated on the flag
     *    value inside ActivationCoordinatorImpl, not on this marker.)
     *
     * Read AFTER fetchAndActivate() (see init), so the flag is fresh. The whole block is guarded so
     * a failure here never blocks or crashes the splash flow.
     */
    private suspend fun applyFirstChecklistExperiment(userData: UserData, isNewUser: Boolean) {
        runCatching {
            val variant = getFirstChecklistVariant()
            analyticsTracker.setUserProperties(mapOf("first_checklist_variant" to variant.name))

            val uid = userData.userId
            val activationBundleEnabled = remoteConfigProvider.getBoolean(
                RemoteConfigKeys.ACTIVATION_BUNDLE_V1,
                RemoteConfigDefaults.ACTIVATION_BUNDLE_V1,
            )

            // Marked for BOTH arms on purpose (changed 2026-07-26). The flag is what lets
            // FIRST_AI_CHECKLIST_CREATED fire exactly once per user; while it was set only in the
            // treatment branch, the event could never reach a control user, so its `variant` param
            // was always "true" and a breakdown by it silently compared treatment against nothing
            // (measured: 27 "true" / 0 "false" over 30d, while control users provably received
            // activation_bundle_v1=false in their activated RC config).
            //
            // Product behavior is unchanged: the reminder opt-in stays treatment-only because it is
            // gated separately on the flag value in ActivationCoordinatorImpl, not on this marker.
            if (isNewUser && uid.isNotBlank()) {
                log("mark new-user-pending uid=${uid.take(8)} (activation bundle ${if (activationBundleEnabled) "ON" else "OFF"})")
                activationPrefsRepository.setNewUserPending(uid)
            }

            if (activationBundleEnabled) {
                // Treatment arm: no static seed — the AI-first activation funnel takes over.
                return@runCatching
            }

            // Control arm: legacy static auto-create (unchanged behavior).
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

        // A fetchAndActivate() failure faster than this is treated as transient (Firebase
        // Installations token race / fast backend error), NOT the SDK fetch-timeout (offline) —
        // only fast failures are retried, so an offline first launch is never multi-waited.
        private const val RC_FAST_FAIL_CEILING_MS = 3000L

        // Total fast-only fetch attempts before proceeding on client defaults. The FIS token race
        // can need more than one retry to settle on a cold start; 3 attempts recover it without a
        // meaningful splash cost (each fast fail is sub-second and gated by RC_FAST_FAIL_CEILING_MS).
        private const val RC_MAX_FAST_ATTEMPTS = 3

        // Small backoff between fast retries to let the Firebase Installations token propagate.
        private const val RC_RETRY_BACKOFF_MS = 200L
    }
}
