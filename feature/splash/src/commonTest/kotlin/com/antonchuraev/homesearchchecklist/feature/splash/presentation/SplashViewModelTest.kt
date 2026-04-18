package com.antonchuraev.homesearchchecklist.feature.splash.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import androidx.navigation.NavController
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.RestorePurchasesUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetOnboardingVariantUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the [SplashViewModel] linkWithPaywall logic.
 *
 * Scenarios:
 * - new user: logIn called, setPaywallLinked(true), restore NOT called
 * - returning user, first link (wasLinked=false, isNewCustomer=false): logIn, setPaywallLinked(true), restore called
 * - returning user, already linked (wasLinked=true): logIn still called, setPaywallLinked NOT called again, restore NOT called
 * - logIn failure: setPaywallLinked stays false, no restore
 * - paywall not configured: logIn not called
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
     * Builds [SplashViewModel] with returning-user defaults (onboarding passed, no new registration flow).
     * linkWithPaywall is called from [startBackgroundSync] when userId is present.
     */
    private fun createViewModel(
        userId: String = "test-uuid",
        isOnboardingPassed: Boolean = true,
        isPaywallLinked: Boolean = false,
        paywallConfigured: Boolean = true,
    ): SplashViewModel {
        fakeUserData.currentUser = UserData(userId = userId, isOnboardingPassed = isOnboardingPassed)
        fakeUserData.paywallLinked = isPaywallLinked
        fakeUserData.setPaywallLinkedCallCount = 0
        fakePaywall.configured = paywallConfigured
        fakePaywall.logInCalled = false
        fakePaywall.refreshCalled = false
        fakePaywall.restoreCallCount = 0

        val restoreUseCase = RestorePurchasesUseCase(fakePaywall, fakeUserData)

        val noOpRemoteConfig = NoOpRemoteConfigProvider()
        return SplashViewModel(
            userDataRepository = fakeUserData,
            paywallRepository = fakePaywall,
            restorePurchasesUseCase = restoreUseCase,
            appNavigator = NoOpNavigator(),
            appScope = testScope,
            logger = NoOpLogger(),
            analyticsTracker = NoOpAnalyticsTracker(),
            getOnboardingVariant = GetOnboardingVariantUseCase(noOpRemoteConfig),
            remoteConfigProvider = noOpRemoteConfig,
        )
    }

    // ---- new user ----

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
        // isNewCustomer=true → no restore
        assertFalse(fakePaywall.restoreCallCount > 0, "restore must NOT run when isNewCustomer=true")
    }

    // ---- returning user, first link ----

    @Test
    fun linkWithPaywall_returningUser_firstLink_logsInAndRestores() = testScope.runTest {
        fakePaywall.loginResult = Result.success(
            LoginResult(subscriptionStatus = SubscriptionStatus.FREE, isNewCustomer = false)
        )

        // wasLinked=false → first link, isNewUser implicit (from startBackgroundSync: isNewUser=false)
        createViewModel(isPaywallLinked = false)
        advanceUntilIdle()

        assertTrue(fakePaywall.logInCalled, "logIn must be called")
        assertTrue(fakeUserData.paywallLinked, "setPaywallLinked(true) must be set on first link")
        assertTrue(fakePaywall.restoreCallCount > 0, "restorePurchases must run for returning user on first link")
        assertTrue(fakePaywall.refreshCalled, "refreshSubscriptionStatus must be called")
    }

    // ---- returning user, already linked ----

    @Test
    fun linkWithPaywall_alreadyLinked_logInStillCalled_noRestore_linkedNotRewrittenAgain() = testScope.runTest {
        fakePaywall.loginResult = Result.success(
            LoginResult(subscriptionStatus = SubscriptionStatus.FREE, isNewCustomer = false)
        )

        createViewModel(isPaywallLinked = true)
        advanceUntilIdle()

        // Critical: logIn must still be called even when wasLinked=true (idempotent, fixes RC drift)
        assertTrue(fakePaywall.logInCalled, "logIn must still be called even when already linked")
        // wasLinked=true → restore must NOT run
        assertFalse(fakePaywall.restoreCallCount > 0, "restore must NOT run when wasLinked=true")
        // setPaywallLinked must not be called again (count stays at 0 since setup reset it to 0)
        assertTrue(
            fakeUserData.setPaywallLinkedCallCount == 0,
            "setPaywallLinked must NOT be called again when wasLinked=true"
        )
        assertTrue(fakePaywall.refreshCalled, "refreshSubscriptionStatus must still be called")
    }

    // ---- logIn failure ----

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

    // ---- paywall not configured ----

    @Test
    fun linkWithPaywall_notConfigured_logInNotCalled() = testScope.runTest {
        createViewModel(paywallConfigured = false)
        advanceUntilIdle()

        assertFalse(fakePaywall.logInCalled, "logIn must not be called when paywall not configured")
        assertFalse(fakePaywall.restoreCallCount > 0, "restore must not run when paywall not configured")
    }

    // ---- Fakes ----

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

        private val _flow = MutableStateFlow(currentUser)

        override fun getUserDataFlow(): StateFlow<UserData> = _flow
        override suspend fun getUserData(): UserData = currentUser
        override suspend fun update(userData: UserData) { currentUser = userData }

        override suspend fun ensureUserRegistered(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = currentUser, isNewUser = false))

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
        override fun installNavController(navController: NavController) {}
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
        override fun navigateToChecklistDetail(checklistId: Long, clearBackStack: Boolean) {}
        override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {}
        override fun navigateToFillsList(checklistId: Long) {}
        override fun navigateToPaywall(source: String) {}
        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {}
        override fun navigateToShareChecklist(checklistId: Long) {}
        override fun navigateToUpdateFeed() {}
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

}
