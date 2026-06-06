package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeNavigator: FakeAppNavigator
    private lateinit var fakeAnalyticsTracker: RecordingAnalyticsTracker
    private lateinit var fakeUserDataRepository: FakeUserDataRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeNavigator = FakeAppNavigator()
        fakeAnalyticsTracker = RecordingAnalyticsTracker()
        fakeUserDataRepository = FakeUserDataRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): OnboardingViewModel {
        return OnboardingViewModel(
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
            navigator = fakeNavigator,
            completeOnboardingUseCase = CompleteOnboardingUseCase(fakeUserDataRepository),
            analyticsTracker = fakeAnalyticsTracker
        )
    }

    // --- Init ---

    @Test
    fun init_tracksOnboardingStarted() = runTest {
        createViewModel()

        assertTrue(fakeAnalyticsTracker.hasEvent("onboarding_started"))
        assertEquals("slides", fakeAnalyticsTracker.getEventParam("onboarding_started", "variant"))
    }

    @Test
    fun init_inDebugMode_doesNotTrackOnboardingStarted() = runTest {
        OnboardingViewModel(
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
            navigator = fakeNavigator,
            completeOnboardingUseCase = CompleteOnboardingUseCase(fakeUserDataRepository),
            analyticsTracker = fakeAnalyticsTracker,
            isDebugBuild = true,
        )

        assertTrue(!fakeAnalyticsTracker.hasEvent("onboarding_started"))
        assertTrue(!fakeAnalyticsTracker.hasEvent("onboarding_vm_created"))
    }

    @Test
    fun initialState_isFirstPage() = runTest {
        val vm = createViewModel()

        val state = vm.screenState.value
        assertEquals(0, state.currentPage)
        // 5 pages: 4 feature slides + 1 paywall page (see OnboardingState.totalPages).
        assertEquals(5, state.totalPages)
    }

    // --- Page navigation ---

    @Test
    fun onNextPage_advancesToNextPage() = runTest {
        val vm = createViewModel()

        vm.onIntent(OnboardingIntent.OnNextPage)

        assertEquals(1, vm.screenState.value.currentPage)
    }

    @Test
    fun onNextPage_lastPage_completesOnboarding() = runTest {
        val vm = createViewModel()

        // Navigate to last page (index 4 = paywall page; 5 pages total)
        repeat(4) { vm.onIntent(OnboardingIntent.OnNextPage) }
        assertEquals(4, vm.screenState.value.currentPage)

        // Next from last page completes onboarding
        vm.onIntent(OnboardingIntent.OnNextPage)
        advanceUntilIdle()

        assertTrue(fakeNavigator.navigatedToMainScreen)
        assertTrue(fakeAnalyticsTracker.hasEvent("onboarding_completed"))
        assertEquals("slides", fakeAnalyticsTracker.getEventParam("onboarding_completed", "variant"))
    }

    @Test
    fun onPageSelected_tracksPageViewed() = runTest {
        val vm = createViewModel()

        vm.onIntent(OnboardingIntent.OnPageSelected(2))

        assertEquals(2, vm.screenState.value.currentPage)
        assertTrue(fakeAnalyticsTracker.hasEvent("onboarding_page_viewed"))
        assertEquals("2", fakeAnalyticsTracker.getEventParam("onboarding_page_viewed", "page"))
    }

    // --- Skip ---

    @Test
    fun onSkip_fromMiddlePage_tracksSkippedAndCompleted() = runTest {
        val vm = createViewModel()

        vm.onIntent(OnboardingIntent.OnPageSelected(1))
        vm.onIntent(OnboardingIntent.OnSkip)
        advanceUntilIdle()

        assertTrue(fakeNavigator.navigatedToMainScreen)
        assertTrue(fakeAnalyticsTracker.hasEvent("onboarding_skipped"))
        assertEquals("slides", fakeAnalyticsTracker.getEventParam("onboarding_skipped", "variant"))
        assertEquals("1", fakeAnalyticsTracker.getEventParam("onboarding_skipped", "page"))
        assertTrue(fakeAnalyticsTracker.hasEvent("onboarding_completed"))
    }

    // --- Fakes ---

    private class FakeAppNavigator : AppNavigator {
        var navigatedToMainScreen = false

        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
        override val backStack: NavBackStack<NavKey> = NavBackStack()
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
        override fun onBack() {}
        override fun navigateToOnboarding() {}
        override fun navigateToInteractiveOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) {
            navigatedToMainScreen = true
        }
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

    private class FakeUserDataRepository : UserDataRepository {
        private val dataFlow = MutableStateFlow(UserData())

        override fun getUserDataFlow(): StateFlow<UserData> = dataFlow
        override suspend fun getUserData(): UserData = dataFlow.value
        override suspend fun update(userData: UserData) { dataFlow.value = userData }
        override suspend fun ensureUserRegistered(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = UserData(), isNewUser = false))
        override suspend fun syncWithServer(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = UserData(), isNewUser = false))
        override suspend fun isPaywallLinked(): Boolean = false
        override suspend fun setPaywallLinked(linked: Boolean) {}
        override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
        override suspend fun getFirstLaunchAtMillis(): Long = 0L
    }

    private class RecordingAnalyticsTracker : AnalyticsTracker {
        private val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {
            events.add(name to params)
        }

        fun hasEvent(name: String): Boolean = events.any { it.first == name }

        fun getEventParam(name: String, param: String): Any? =
            events.firstOrNull { it.first == name }?.second?.get(param)
    }
}
