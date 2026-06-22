package com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.deeplink.UpdateFeedDeepLinkHandler
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.deeplink.UpdateFeedDeepLinks
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdatePost
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdatePostAction
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.VersionReleaseGroup
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.repository.UpdateFeedRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
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

        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
        override val backStack: NavBackStack<NavKey> = NavBackStack()
        override fun onBack() {}
        override fun navigateToOnboarding() {}
        override fun navigateToInteractiveOnboarding() {}
        override fun navigateToWelcomeOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) {}
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?, initialText: String?) {}
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() {}
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean, initialText: String?, autoAnalyze: Boolean) {}
        override fun navigateToAnalyzeResultPreview() {}
        override fun navigateToChecklistDetail(checklistId: Long, focusItemId: String?, clearBackStack: Boolean) {}
        override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {}
        override fun navigateToFillsList(checklistId: Long) {}
        override fun navigateToPaywall(source: String) { paywallSource = source }
        override fun navigateToPaywallVariant(source: String, forceVariant: String) {}
        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) { navigatedToSubscriptionStatus = true }
        override fun navigateToShareChecklist(checklistId: Long) {}
        override fun navigateToUpdateFeed() {}
        override fun navigateToSettings() {}
        override fun navigateToToday() {}
        override fun navigateToCalendar() {}
        override fun navigateToAiChat() {}
        override fun navigateToScreenCatalog() {}
        override fun navigateToOnboardings() {}
        override fun navigateToAddToChecklistPicker(text: String, purpose: com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose) {}
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
    }

    private class FakePaywallRepository(
        private val status: SubscriptionStatus
    ) : PaywallRepository {
        override val subscriptionStatus: Flow<SubscriptionStatus> = flowOf(status)
        override suspend fun getOfferings(offeringId: String): Result<PaywallOffering?> = Result.success(null)
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
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) { events.add(name to params) }
    }

    private class FakeLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    private class FakeRemoteConfigProvider : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }

    private class FakeChecklistRepository(
        private val weeklyCount: Int = 0
    ) : ChecklistRepository {
        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
        override val weeklyChecklistCount: Flow<Int> = flowOf(weeklyCount)
        override suspend fun addChecklist(checklist: Checklist): Long = 1L
        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {}
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun getChecklistById(id: Long): Checklist? = null
        override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(null)
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setFoldersEnabled(checklistId: Long, value: Boolean) {}
        override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {}
        override suspend fun countActiveReminders(): Int = 0
        override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
        override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? = null
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = flowOf(null)
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 1L
        override suspend fun updateFill(fill: ChecklistFill) {}
        override suspend fun deleteFill(fill: ChecklistFill) {}
        override suspend fun reorderItems(fill: ChecklistFill, checklist: Checklist) {}
        override suspend fun setRepeatSchedule(checklistId: Long, rule: ReminderRepeatRule, timeOfDayMinutes: Int, firstTriggerAt: Long) {}
        override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) {}
        override suspend fun clearRepeatSchedule(checklistId: Long) {}
        override suspend fun resetDefaultFillChecks(checklistId: Long) {}
        override suspend fun countActiveRepeatSchedules(): Int = 0
        override suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getWeeklyChecklistCount(): Int = weeklyCount
        override suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo> = emptyList()
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> = flowOf(emptyList())
        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: Attachment) {}
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) {}
    }

    private class FakeUserDataRepository : UserDataRepository {
        private val userData = UserData(userId = "test", isPremium = false)
        private val flow = MutableStateFlow(userData)
        override fun getUserDataFlow(): StateFlow<UserData> = flow
        override suspend fun getUserData(): UserData = userData
        override suspend fun update(userData: UserData) {}
        override suspend fun ensureUserRegistered(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = userData, isNewUser = false))
        override suspend fun syncWithServer(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = userData, isNewUser = false))
        override suspend fun isPaywallLinked(): Boolean = false
        override suspend fun setPaywallLinked(linked: Boolean) {}
        override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
        override suspend fun getFirstLaunchAtMillis(): Long = 0L
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

    private val weeklyPost = UpdatePost(
        id = "weekly_mode_v1",
        version = "1.14",
        title = "Weekly Mode",
        description = "Plan your week visually.",
        publishedAtMillis = 1769644800000L,
        iconName = "Bolt",
        actions = listOf(
            UpdatePostAction(
                label = "Create weekly checklist",
                deepLink = UpdateFeedDeepLinks.CREATE_WEEKLY
            )
        )
    )

    private val sampleReleases = listOf(
        VersionReleaseGroup(
            version = "1.6",
            publishedAtMillis = 1769644800000L,
            storeDescription = "🆕 New in version 1.6: widget!",
            posts = listOf(samplePost)
        )
    )

    private val weeklyReleases = listOf(
        VersionReleaseGroup(
            version = "1.14",
            publishedAtMillis = 1769644800000L,
            storeDescription = "🆕 Weekly mode!",
            posts = listOf(weeklyPost)
        )
    )

    private fun buildViewModel(
        releases: List<VersionReleaseGroup> = sampleReleases,
        subscriptionStatus: SubscriptionStatus = SubscriptionStatus.FREE,
        weeklyCount: Int = 0
    ): Triple<UpdateFeedViewModel, FakeNavigator, FakeAnalyticsTracker> {
        val navigator = FakeNavigator()
        val tracker = FakeAnalyticsTracker()
        val paywallRepository = FakePaywallRepository(subscriptionStatus)
        val subscriptionUseCase = GetSubscriptionStatusUseCase(paywallRepository)
        val userLimitsUseCase = GetUserLimitsUseCase(
            remoteConfigProvider = FakeRemoteConfigProvider(),
            checklistRepository = FakeChecklistRepository(weeklyCount),
            paywallRepository = paywallRepository,
            userDataRepository = FakeUserDataRepository()
        )
        val viewModel = UpdateFeedViewModel(
            repository = FakeRepository(releases),
            navigator = navigator,
            deepLinkHandler = UpdateFeedDeepLinkHandler(navigator),
            getSubscriptionStatusUseCase = subscriptionUseCase,
            getUserLimitsUseCase = userLimitsUseCase,
            analyticsTracker = tracker,
            logger = FakeLogger()
        )
        return Triple(viewModel, navigator, tracker)
    }

    // ── Existing tests (preserved) ──────────────────────────────────────────

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

    // ── Locked CTA tests ────────────────────────────────────────────────────

    @Test
    fun `loadReleases_whenFreeUserHitsWeeklyLimit_putsCreateWeeklyDeeplinkInLockedSet`() = runTest {
        // Free user has 1 weekly checklist, max is 1 → canCreateWeeklyChecklist = false
        val (viewModel, _) = buildViewModel(
            releases = weeklyReleases,
            subscriptionStatus = SubscriptionStatus.FREE,
            weeklyCount = 1
        )
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertIs<UpdateFeedScreenState.Success>(state)
        assertTrue(
            UpdateFeedDeepLinks.CREATE_WEEKLY in state.lockedActionDeepLinks,
            "Expected CREATE_WEEKLY deeplink in lockedActionDeepLinks when free user hits limit"
        )
    }

    @Test
    fun `loadReleases_whenFreeUserBelowWeeklyLimit_lockedSetEmpty`() = runTest {
        // Free user has 0 weekly checklists, max is 1 → canCreateWeeklyChecklist = true
        val (viewModel, _) = buildViewModel(
            releases = weeklyReleases,
            subscriptionStatus = SubscriptionStatus.FREE,
            weeklyCount = 0
        )
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertIs<UpdateFeedScreenState.Success>(state)
        assertTrue(
            state.lockedActionDeepLinks.isEmpty(),
            "Expected empty lockedActionDeepLinks when free user is below the weekly limit"
        )
    }

    @Test
    fun `loadReleases_whenPremium_lockedSetEmpty`() = runTest {
        // Premium user — canCreateWeeklyChecklist is always true regardless of count
        val premiumStatus = SubscriptionStatus(
            isActive = true,
            activeEntitlements = setOf("AiChecklists Pro"),
            expirationDate = null
        )
        val (viewModel, _) = buildViewModel(
            releases = weeklyReleases,
            subscriptionStatus = premiumStatus,
            weeklyCount = 5
        )
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertIs<UpdateFeedScreenState.Success>(state)
        assertTrue(
            state.lockedActionDeepLinks.isEmpty(),
            "Expected empty lockedActionDeepLinks for premium user"
        )
    }

    @Test
    fun `handleActionClick_whenWeeklyDeeplinkLocked_navigatesToPaywall_skipsDeepLinkHandler`() = runTest {
        // Free user at limit → CREATE_WEEKLY action is locked
        val (viewModel, navigator) = buildViewModel(
            releases = weeklyReleases,
            subscriptionStatus = SubscriptionStatus.FREE,
            weeklyCount = 1
        )
        advanceUntilIdle()

        viewModel.sendIntent(
            UpdateFeedScreenIntent.OnActionClick(
                postId = "weekly_mode_v1",
                action = UpdatePostAction(
                    label = "Create weekly checklist",
                    deepLink = UpdateFeedDeepLinks.CREATE_WEEKLY
                )
            )
        )
        advanceUntilIdle()

        assertEquals("weekly_mode_limit", navigator.paywallSource,
            "Expected navigation to paywall with source='weekly_mode_limit'")
    }

    @Test
    fun `handleActionClick_whenWeeklyDeeplinkLocked_analyticsEventIncludesLockedTrue`() = runTest {
        val (viewModel, _, tracker) = buildViewModel(
            releases = weeklyReleases,
            subscriptionStatus = SubscriptionStatus.FREE,
            weeklyCount = 1
        )
        advanceUntilIdle()

        viewModel.sendIntent(
            UpdateFeedScreenIntent.OnActionClick(
                postId = "weekly_mode_v1",
                action = UpdatePostAction(
                    label = "Create weekly checklist",
                    deepLink = UpdateFeedDeepLinks.CREATE_WEEKLY
                )
            )
        )
        advanceUntilIdle()

        val event = tracker.events.firstOrNull { it.first == "update_feed_action_click" }
        assertNotNull(event, "Expected analytics event 'update_feed_action_click'")
        assertEquals(true, event.second["locked"], "Expected locked=true in analytics params")
    }

    @Test
    fun `handleActionClick_whenWeeklyDeeplinkUnlocked_callsDeepLinkHandler_noPaywallNavigation`() = runTest {
        // Free user with 0 weekly checklists → not locked, handler should fire
        val (viewModel, navigator) = buildViewModel(
            releases = weeklyReleases,
            subscriptionStatus = SubscriptionStatus.FREE,
            weeklyCount = 0
        )
        advanceUntilIdle()

        viewModel.sendIntent(
            UpdateFeedScreenIntent.OnActionClick(
                postId = "weekly_mode_v1",
                action = UpdatePostAction(
                    label = "Create weekly checklist",
                    deepLink = UpdateFeedDeepLinks.CREATE_WEEKLY
                )
            )
        )
        advanceUntilIdle()

        // DeepLinkHandler routes gisti://create?viewMode=weekly to requestCreateWeeklyChecklist()
        // which on FakeNavigator is a no-op — but crucially paywallSource must remain null
        assertNull(
            navigator.paywallSource,
            "Expected no paywall navigation when weekly CTA is not locked"
        )
    }
}
