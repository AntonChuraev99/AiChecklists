package com.antonchuraev.homesearchchecklist.feature.splash.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.navigation.api.NavCommand
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.RestorePurchasesUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetOnboardingVariantUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the [SplashViewModel] init flow.
 *
 * Two groups:
 * - linkWithPaywall logic (idempotent logIn, auto-restore gate, failure handling)
 * - Remote Config gating: whenever onboarding still needs to be shown, the
 *   ViewModel MUST block navigateTo() until fetchAndActivate() completes, so
 *   the resolved A/B variant comes from the server rather than the client-side
 *   default. Without this gate the historical Amplitude distribution collapsed
 *   to a single "interactive" variant (47 vs 0 uniques over 14 days).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakePaywall: FakePaywallRepository
    private lateinit var fakeUserData: FakeUserDataRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakePaywall = FakePaywallRepository()
        fakeUserData = FakeUserDataRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Builds [SplashViewModel] with sensible defaults. Callers may swap in a
     * recording navigator / order-tracking RC provider when they need to assert
     * routing or gating.
     */
    private fun createViewModel(
        userId: String = "test-uuid",
        isOnboardingPassed: Boolean = true,
        isPaywallLinked: Boolean = false,
        paywallConfigured: Boolean = true,
        remoteConfig: RemoteConfigProvider = NoOpRemoteConfigProvider(),
        navigator: AppNavigator = NoOpNavigator(),
    ): SplashViewModel {
        fakeUserData.currentUser = UserData(userId = userId, isOnboardingPassed = isOnboardingPassed)
        fakeUserData.paywallLinked = isPaywallLinked
        fakeUserData.setPaywallLinkedCallCount = 0
        fakePaywall.configured = paywallConfigured
        fakePaywall.logInCalled = false
        fakePaywall.refreshCalled = false
        fakePaywall.restoreCallCount = 0

        val restoreUseCase = RestorePurchasesUseCase(fakePaywall, fakeUserData)

        return SplashViewModel(
            userDataRepository = fakeUserData,
            paywallRepository = fakePaywall,
            restorePurchasesUseCase = restoreUseCase,
            appNavigator = navigator,
            appScope = testScope,
            logger = NoOpLogger(),
            analyticsTracker = NoOpAnalyticsTracker(),
            getOnboardingVariant = GetOnboardingVariantUseCase(remoteConfig, NoOpLogger()),
            completeOnboardingUseCase = CompleteOnboardingUseCase(fakeUserData),
            remoteConfigProvider = remoteConfig,
        )
    }

    // ============================================================
    // linkWithPaywall — existing scenarios, retained verbatim
    // ============================================================

    @Test
    fun linkWithPaywall_newUser_isNewCustomer_logsInMarksLinked_doesNotRestore() = testScope.runTest {
        fakePaywall.loginResult = Result.success(
            LoginResult(subscriptionStatus = SubscriptionStatus.FREE, isNewCustomer = true)
        )

        createViewModel(isPaywallLinked = false)
        advanceUntilIdle()

        assertTrue(fakePaywall.logInCalled, "logIn must be called for new user")
        assertTrue(fakeUserData.paywallLinked, "setPaywallLinked(true) must be called")
        assertTrue(fakePaywall.refreshCalled, "refreshSubscriptionStatus must be called")
        assertFalse(fakePaywall.restoreCallCount > 0, "restore must NOT run when isNewCustomer=true")
    }

    @Test
    fun linkWithPaywall_returningUser_firstLink_logsInAndRestores() = testScope.runTest {
        fakePaywall.loginResult = Result.success(
            LoginResult(subscriptionStatus = SubscriptionStatus.FREE, isNewCustomer = false)
        )

        createViewModel(isPaywallLinked = false)
        advanceUntilIdle()

        assertTrue(fakePaywall.logInCalled, "logIn must be called")
        assertTrue(fakeUserData.paywallLinked, "setPaywallLinked(true) must be set on first link")
        assertTrue(fakePaywall.restoreCallCount > 0, "restorePurchases must run for returning user on first link")
        assertTrue(fakePaywall.refreshCalled, "refreshSubscriptionStatus must be called")
    }

    @Test
    fun linkWithPaywall_alreadyLinked_logInStillCalled_noRestore_linkedNotRewrittenAgain() = testScope.runTest {
        fakePaywall.loginResult = Result.success(
            LoginResult(subscriptionStatus = SubscriptionStatus.FREE, isNewCustomer = false)
        )

        createViewModel(isPaywallLinked = true)
        advanceUntilIdle()

        assertTrue(fakePaywall.logInCalled, "logIn must still be called even when already linked")
        assertFalse(fakePaywall.restoreCallCount > 0, "restore must NOT run when wasLinked=true")
        assertTrue(
            fakeUserData.setPaywallLinkedCallCount == 0,
            "setPaywallLinked must NOT be called again when wasLinked=true"
        )
        assertTrue(fakePaywall.refreshCalled, "refreshSubscriptionStatus must still be called")
    }

    @Test
    fun linkWithPaywall_logInFailure_linkedStaysFalse_noRestore_noRefresh() = testScope.runTest {
        fakePaywall.loginResult = Result.failure(RuntimeException("network error"))

        createViewModel(isPaywallLinked = false)
        advanceUntilIdle()

        assertTrue(fakePaywall.logInCalled, "logIn must be attempted")
        assertFalse(fakeUserData.paywallLinked, "paywallLinked must stay false on logIn failure")
        assertFalse(fakePaywall.restoreCallCount > 0, "restore must NOT run on logIn failure")
        assertFalse(fakePaywall.refreshCalled, "refreshSubscriptionStatus must NOT be called on failure")
    }

    @Test
    fun linkWithPaywall_notConfigured_logInNotCalled() = testScope.runTest {
        createViewModel(paywallConfigured = false)
        advanceUntilIdle()

        assertFalse(fakePaywall.logInCalled, "logIn must not be called when paywall not configured")
        assertFalse(fakePaywall.restoreCallCount > 0, "restore must not run when paywall not configured")
    }

    // ============================================================
    // Remote Config gating — new scenarios for A/B distribution fix
    // ============================================================

    /**
     * Returning user who hasn't finished onboarding: must NOT navigate before
     * fetchAndActivate() returns. The slow-RC fake reports an empty value
     * before fetch and "interactive" after — if SplashViewModel skipped the
     * wait, it would read the empty default and route to slides; the
     * assertion below requires the post-fetch "interactive" path instead.
     */
    @Test
    fun navigateTo_returningUserOnboardingPending_waitsForFetchAndActivate() = testScope.runTest {
        val rc = SlowOrderTrackingRemoteConfig(
            onboardingValueAfterFetch = "interactive",
            fetchDelayMillis = 200L,
        )
        val nav = RecordingNavigator()

        createViewModel(
            userId = "existing-uuid",
            isOnboardingPassed = false,
            remoteConfig = rc,
            navigator = nav,
        )

        // Drain everything that does not require advancing virtual time.
        // If the bug is back (no waiting), navigateTo fires here with the
        // pre-fetch empty RC value → slides.
        runCurrent()
        assertTrue(
            nav.routes.isEmpty(),
            "navigateTo must not fire while fetchAndActivate is still in flight, got=${nav.routes}"
        )

        // Now let the simulated network round-trip complete.
        advanceUntilIdle()

        assertTrue(rc.fetchInvoked, "fetchAndActivate must be invoked for returning user with !isOnboardingPassed")
        assertEquals(
            listOf("interactive_onboarding"),
            nav.routes,
            "must route by server variant 'interactive', not by stale empty client default"
        )
    }

    /**
     * New user: registers, then must wait for fetchAndActivate before showing
     * onboarding. The server variant in this fake is "default" — checks that
     * the user lands on slide onboarding, not the interactive fallback.
     */
    @Test
    fun navigateTo_newUser_waitsForFetchAndActivate_thenRoutesByServerVariant() = testScope.runTest {
        val rc = SlowOrderTrackingRemoteConfig(
            onboardingValueAfterFetch = "default",
            fetchDelayMillis = 200L,
        )
        val nav = RecordingNavigator()

        // userId blank → goes through ensureUserRegistered path
        fakeUserData.nextRegistration = RegistrationData(
            userData = UserData(userId = "new-uuid", isOnboardingPassed = false),
            isNewUser = true,
        )

        createViewModel(
            userId = "",
            isOnboardingPassed = false,
            remoteConfig = rc,
            navigator = nav,
        )

        runCurrent()
        assertTrue(
            nav.routes.isEmpty(),
            "navigateTo must wait for fetchAndActivate even for new users, got=${nav.routes}"
        )

        advanceUntilIdle()

        assertTrue(rc.fetchInvoked, "fetchAndActivate must be invoked for new user")
        assertEquals(
            listOf("onboarding"),
            nav.routes,
            "must route by server variant 'default' (slides), not by empty client default"
        )
    }

    /**
     * Returning user who already finished onboarding: must take the fast
     * path. A slow RC must NOT block navigation to Main — the variant is
     * irrelevant for them and a 3s gate would only delay the first frame.
     */
    @Test
    fun navigateTo_returningUserOnboardingPassed_skipsRcWaitAndGoesToMain() = testScope.runTest {
        val rc = SlowOrderTrackingRemoteConfig(
            onboardingValueAfterFetch = "interactive",
            fetchDelayMillis = 2_000L,
        )
        val nav = RecordingNavigator()

        createViewModel(
            userId = "old-uuid",
            isOnboardingPassed = true,
            remoteConfig = rc,
            navigator = nav,
        )

        // Drain only what is ready synchronously. The Main route should have
        // landed without waiting on the 2s simulated network call.
        runCurrent()

        assertEquals(
            listOf("main"),
            nav.routes,
            "returning user with passed onboarding must skip the RC gate and go straight to main"
        )
    }

    // ============================================================
    // Test doubles
    // ============================================================

    private class FakePaywallRepository : PaywallRepository {

        var configured = true
        var logInCalled = false
        var refreshCalled = false
        var restoreCallCount = 0

        var loginResult: Result<LoginResult> = Result.success(
            LoginResult(SubscriptionStatus.FREE, isNewCustomer = false)
        )

        override val subscriptionStatus: Flow<SubscriptionStatus> =
            MutableStateFlow(SubscriptionStatus.FREE)

        override fun isConfigured(): Boolean = configured

        override suspend fun logIn(appUserId: String): Result<LoginResult> {
            logInCalled = true
            return loginResult
        }

        override suspend fun refreshSubscriptionStatus() {
            refreshCalled = true
        }

        override suspend fun restorePurchases(): RestoreResult {
            restoreCallCount++
            return RestoreResult.Success(SubscriptionStatus.FREE)
        }

        override suspend fun getOfferings() = Result.success(null)
        override suspend fun purchase(packageId: String) = throw UnsupportedOperationException()
        override suspend fun logOut() = Result.success(SubscriptionStatus.FREE)
    }

    private class FakeUserDataRepository : UserDataRepository {

        var currentUser: UserData = UserData(userId = "test-uuid", isOnboardingPassed = true)
        var paywallLinked: Boolean = false
        var setPaywallLinkedCallCount: Int = 0
        var nextRegistration: RegistrationData? = null

        private val _flow = MutableStateFlow(currentUser)

        override fun getUserDataFlow(): StateFlow<UserData> = _flow
        override suspend fun getUserData(): UserData = currentUser
        override suspend fun update(userData: UserData) { currentUser = userData }

        override suspend fun ensureUserRegistered(): Result<RegistrationData> {
            val data = nextRegistration
                ?: RegistrationData(userData = currentUser, isNewUser = false)
            currentUser = data.userData
            return Result.success(data)
        }

        override suspend fun syncWithServer(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = currentUser, isNewUser = false))

        override suspend fun isPaywallLinked(): Boolean = paywallLinked

        override suspend fun setPaywallLinked(linked: Boolean) {
            setPaywallLinkedCallCount++
            paywallLinked = linked
        }

        override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)

        override suspend fun getFirstLaunchAtMillis(): Long = 0L
    }

    private class NoOpNavigator : AppNavigator {
        override val commands: Flow<NavCommand> = emptyFlow()
        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
        override val backStack: StateFlow<List<AppNavRoute>> = MutableStateFlow(emptyList())
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
        override fun onBack() {}
        override fun navigateToOnboarding() {}
        override fun navigateToInteractiveOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) {}
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?) {}
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() {}
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean) {}
        override fun navigateToAnalyzeResultPreview() {}
        override fun navigateToChecklistDetail(checklistId: Long, focusItemId: String?, clearBackStack: Boolean) {}
        override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {}
        override fun navigateToFillsList(checklistId: Long) {}
        override fun navigateToPaywall(source: String) {}
        override fun navigateToPaywallVariant(source: String, forceVariant: String) {}
        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {}
        override fun navigateToShareChecklist(checklistId: Long) {}
        override fun navigateToUpdateFeed() {}
        override fun navigateToSettings() {}
        override fun navigateToToday() {}
        override fun navigateToCalendar() {}
        override fun navigateToAiChat() {}
        override fun navigateToScreenCatalog() {}
        override fun navigateToOnboardings() {}
    }

    /**
     * Records every navigation call as a short tag — used by the routing
     * tests to assert exactly which destination SplashViewModel chose.
     */
    private class RecordingNavigator : AppNavigator {
        val routes = mutableListOf<String>()

        override val commands: Flow<NavCommand> = emptyFlow()
        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
        override val backStack: StateFlow<List<AppNavRoute>> = MutableStateFlow(emptyList())
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
        override fun onBack() {}
        override fun navigateToOnboarding() { routes += "onboarding" }
        override fun navigateToInteractiveOnboarding() { routes += "interactive_onboarding" }
        override fun navigateToMainScreen(clearBackStack: Boolean) { routes += "main" }
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?) {}
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() {}
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean) {}
        override fun navigateToAnalyzeResultPreview() {}
        override fun navigateToChecklistDetail(checklistId: Long, focusItemId: String?, clearBackStack: Boolean) {}
        override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {}
        override fun navigateToFillsList(checklistId: Long) {}
        override fun navigateToPaywall(source: String) {}
        override fun navigateToPaywallVariant(source: String, forceVariant: String) {}
        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {}
        override fun navigateToShareChecklist(checklistId: Long) {}
        override fun navigateToUpdateFeed() {}
        override fun navigateToSettings() {}
        override fun navigateToToday() {}
        override fun navigateToCalendar() {}
        override fun navigateToAiChat() {}
        override fun navigateToScreenCatalog() {}
        override fun navigateToOnboardings() {}
    }

    private class NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    private class NoOpAnalyticsTracker : AnalyticsTracker {
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
    }

    private class NoOpRemoteConfigProvider : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }

    /**
     * Simulates the production fetch flow: empty value before activation,
     * server variant after. fetchAndActivate suspends for [fetchDelayMillis]
     * virtual milliseconds so tests can probe the gate state via runCurrent()
     * before advancing time.
     */
    private class SlowOrderTrackingRemoteConfig(
        private val onboardingValueAfterFetch: String,
        private val fetchDelayMillis: Long,
    ) : RemoteConfigProvider {

        @Volatile var fetchInvoked: Boolean = false
            private set
        @Volatile private var fetched: Boolean = false

        override suspend fun fetchAndActivate(): Boolean {
            fetchInvoked = true
            delay(fetchDelayMillis)
            fetched = true
            return true
        }

        override fun getString(key: String, defaultValue: String): String {
            if (key != RemoteConfigKeys.ONBOARDING) return defaultValue
            return if (fetched) onboardingValueAfterFetch else defaultValue
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }
}
