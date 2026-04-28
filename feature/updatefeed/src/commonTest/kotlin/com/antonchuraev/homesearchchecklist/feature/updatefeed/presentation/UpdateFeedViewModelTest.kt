package com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.navigation.api.NavCommand
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.deeplink.UpdateFeedDeepLinkHandler
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdatePost
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.VersionReleaseGroup
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.repository.UpdateFeedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateFeedViewModelTest {

    // UnconfinedTestDispatcher executes coroutines eagerly — no advanceUntilIdle needed
    // for viewModelScope coroutines that use Dispatchers.Main.
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Fakes ----

    private class FakeRepository(
        private val releases: List<VersionReleaseGroup> = emptyList()
    ) : UpdateFeedRepository {
        override suspend fun getReleases(): List<VersionReleaseGroup> = releases
    }

    private class FakeNavigator : AppNavigator {
        var paywallSource: String? = null
        var navigatedToSubscriptionStatus = false

        override val commands: Flow<NavCommand> = emptyFlow()
        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
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
        override fun navigateToPaywall(source: String) { paywallSource = source }
        override fun navigateToPaywallVariant(source: String, forceVariant: String) {}
        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) { navigatedToSubscriptionStatus = true }
        override fun navigateToShareChecklist(checklistId: Long) {}
        override fun navigateToUpdateFeed() {}
        override fun navigateToSettings() {}
        override fun navigateToScreenCatalog() {}
        override fun showWidgetInstruction() {}
    }

    private class FakePaywallRepository(
        private val status: SubscriptionStatus
    ) : PaywallRepository {
        override val subscriptionStatus: Flow<SubscriptionStatus> = flowOf(status)
        override suspend fun getOfferings(): Result<PaywallOffering?> = Result.success(null)
        override suspend fun purchase(packageId: String): PurchaseResult = PurchaseResult.Error("fake")
        override suspend fun restorePurchases(): RestoreResult = RestoreResult.Error("fake")
        override suspend fun refreshSubscriptionStatus() {}
        override fun isConfigured(): Boolean = false
        override suspend fun logIn(appUserId: String): Result<LoginResult> = Result.success(
            LoginResult(subscriptionStatus = status, isNewCustomer = false)
        )
        override suspend fun logOut(): Result<SubscriptionStatus> = Result.success(status)
    }

    private class FakeAnalyticsTracker : AnalyticsTracker {
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
    }

    private class FakeLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    private val samplePost = UpdatePost(
        id = "widget_v1",
        version = "1.6",
        title = "Add the home screen widget",
        description = "Pin any checklist to your home screen.",
        publishedAtMillis = 1769644800000L,
        iconName = "Widgets",
        actions = emptyList()
    )

    private val sampleReleases = listOf(
        VersionReleaseGroup(
            version = "1.6",
            publishedAtMillis = 1769644800000L,
            storeDescription = "🆕 New in version 1.6: widget!",
            posts = listOf(samplePost)
        )
    )

    private fun buildViewModel(
        releases: List<VersionReleaseGroup> = sampleReleases,
        subscriptionStatus: SubscriptionStatus = SubscriptionStatus.FREE
    ): Pair<UpdateFeedViewModel, FakeNavigator> {
        val navigator = FakeNavigator()
        val useCase = GetSubscriptionStatusUseCase(FakePaywallRepository(subscriptionStatus))
        val viewModel = UpdateFeedViewModel(
            repository = FakeRepository(releases),
            navigator = navigator,
            deepLinkHandler = UpdateFeedDeepLinkHandler(navigator),
            getSubscriptionStatusUseCase = useCase,
            analyticsTracker = FakeAnalyticsTracker(),
            logger = FakeLogger()
        )
        return viewModel to navigator
    }

    @Test
    fun `loadReleases_withReleasesAndInactivePremium_emitsSuccessWithIsPremiumFalse`() = runTest {
        val (viewModel, _) = buildViewModel(
            releases = sampleReleases,
            subscriptionStatus = SubscriptionStatus.FREE
        )
        advanceUntilIdle()
        val state = viewModel.screenState.value
        assertIs<UpdateFeedScreenState.Success>(state)
        assertEquals(false, state.isPremium)
        assertNull(state.formattedExpirationDate)
        assertEquals(1, state.releases.size)
    }

    @Test
    fun `loadReleases_withReleasesAndActivePremium_emitsSuccessWithIsPremiumTrue`() = runTest {
        val activeStatus = SubscriptionStatus(
            isActive = true,
            activeEntitlements = setOf("premium"),
            expirationDate = null
        )
        val (viewModel, _) = buildViewModel(
            releases = sampleReleases,
            subscriptionStatus = activeStatus
        )
        advanceUntilIdle()
        val state = viewModel.screenState.value
        assertIs<UpdateFeedScreenState.Success>(state)
        assertTrue(state.isPremium)
        assertNull(state.formattedExpirationDate)
    }

    @Test
    fun `loadReleases_withEmptyReleases_emitsEmptyState`() = runTest {
        val (viewModel, _) = buildViewModel(releases = emptyList())
        advanceUntilIdle()
        assertIs<UpdateFeedScreenState.Empty>(viewModel.screenState.value)
    }

    @Test
    fun `loadReleases_successState_releasesContainCorrectVersionAndPosts`() = runTest {
        val twoPostRelease = VersionReleaseGroup(
            version = "1.11",
            publishedAtMillis = 1773360000000L,
            storeDescription = "🆕 New in version 1.11",
            posts = listOf(
                samplePost.copy(id = "templates_v1", version = "1.11"),
                samplePost.copy(id = "discover_more_v1", version = "1.11")
            )
        )
        val (viewModel, _) = buildViewModel(releases = listOf(twoPostRelease))
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertIs<UpdateFeedScreenState.Success>(state)
        assertEquals(1, state.releases.size)
        assertEquals("1.11", state.releases[0].version)
        assertEquals(2, state.releases[0].posts.size)
    }

    @Test
    fun `loadReleases_withReleaseNoteOnlyGroup_stateContainsGroupWithEmptyPostsAndStoreDescription`() = runTest {
        val releaseNoteOnly = VersionReleaseGroup(
            version = "1.12",
            publishedAtMillis = 1774041600000L,
            storeDescription = "🆕 New in version 1.12: recurring reminders",
            posts = emptyList()
        )
        val (viewModel, _) = buildViewModel(releases = listOf(releaseNoteOnly))
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertIs<UpdateFeedScreenState.Success>(state)
        assertEquals(1, state.releases.size)
        val group = state.releases[0]
        assertEquals("1.12", group.version)
        assertTrue(group.posts.isEmpty())
        assertNotNull(group.storeDescription)
    }

    @Test
    fun `loadReleases_withSixGroups_stateContainsSixReleases`() = runTest {
        val sixGroups = (1..6).map { i ->
            VersionReleaseGroup(
                version = "1.$i",
                publishedAtMillis = 1000L * i,
                storeDescription = if (i % 2 == 0) "Notes for 1.$i" else null,
                posts = listOf(samplePost.copy(id = "post_$i", version = "1.$i"))
            )
        }
        val (viewModel, _) = buildViewModel(releases = sixGroups)
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertIs<UpdateFeedScreenState.Success>(state)
        assertEquals(6, state.releases.size)
    }

    @Test
    fun `onPremiumBannerClick_whenNotPremium_navigatesToPaywallWithUpdateFeedSource`() = runTest {
        val (viewModel, navigator) = buildViewModel(
            releases = sampleReleases,
            subscriptionStatus = SubscriptionStatus.FREE
        )
        advanceUntilIdle()
        viewModel.sendIntent(UpdateFeedScreenIntent.OnPremiumBannerClick)
        advanceUntilIdle()
        assertEquals("update_feed", navigator.paywallSource)
    }

    @Test
    fun `onPremiumBannerClick_whenPremium_navigatesToSubscriptionStatus`() = runTest {
        val activeStatus = SubscriptionStatus(
            isActive = true,
            activeEntitlements = setOf("premium"),
            expirationDate = null
        )
        val (viewModel, navigator) = buildViewModel(
            releases = sampleReleases,
            subscriptionStatus = activeStatus
        )
        advanceUntilIdle()
        viewModel.sendIntent(UpdateFeedScreenIntent.OnPremiumBannerClick)
        advanceUntilIdle()
        assertTrue(navigator.navigatedToSubscriptionStatus)
    }
}
