package com.antonchuraev.homesearchchecklist.feature.splash.presentation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.FirstChecklistRepository
import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.RestorePurchasesUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetFirstChecklistVariantUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetOnboardingVariantUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
 * Groups:
 * - linkWithPaywall logic (idempotent logIn, auto-restore gate, failure handling)
 * - Remote Config gating: whenever onboarding still needs to be shown, the
 *   ViewModel MUST block navigateTo() until fetchAndActivate() completes, so
 *   the resolved A/B variant comes from the server rather than the client-side
 *   default. Without this gate the historical Amplitude distribution collapsed
 *   to a single "interactive" variant (47 vs 0 uniques over 14 days).
 * - First-checklist A/B experiment: the `first_checklist_variant` user property
 *   is always set; the starter checklist is auto-created only for new users in
 *   the AUTO_CREATE treatment that haven't been seeded yet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakePaywall: FakePaywallRepository
    private lateinit var fakeUserData: FakeUserDataRepository
    private lateinit var fakeChecklist: FakeChecklistRepository
    private lateinit var fakeFirstChecklist: FakeFirstChecklistRepository
    private lateinit var fakeAnalytics: RecordingAnalyticsTracker

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakePaywall = FakePaywallRepository()
        fakeUserData = FakeUserDataRepository()
        fakeChecklist = FakeChecklistRepository()
        fakeFirstChecklist = FakeFirstChecklistRepository()
        fakeAnalytics = RecordingAnalyticsTracker()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Builds [SplashViewModel] with sensible defaults. Callers may swap in a
     * recording navigator / order-tracking RC provider when they need to assert
     * routing or gating, or a [firstChecklistRc] when they need to drive the
     * first-checklist A/B variant.
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
            analyticsTracker = fakeAnalytics,
            getOnboardingVariant = GetOnboardingVariantUseCase(remoteConfig, NoOpLogger()),
            completeOnboardingUseCase = CompleteOnboardingUseCase(fakeUserData),
            remoteConfigProvider = remoteConfig,
            getFirstChecklistVariant = GetFirstChecklistVariantUseCase(remoteConfig, NoOpLogger()),
            checklistRepository = fakeChecklist,
            firstChecklistRepository = fakeFirstChecklist,
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
    // Remote Config gating — A/B distribution fix scenarios
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
    // First-checklist A/B experiment
    // ============================================================

    /**
     * The `first_checklist_variant` user property must be set for EVERY user
     * (so analytics can split cohorts), regardless of whether they are new or
     * which treatment they land in. Here a returning user (isNewUser=false)
     * still gets the property even though no checklist is auto-created.
     */
    @Test
    fun applyFirstChecklistExperiment_anyUser_setsVariantUserProperty() = testScope.runTest {
        // Default RC value for first_checklist_variant is "auto_create" → AUTO_CREATE.
        createViewModel(userId = "existing-uuid", isOnboardingPassed = true)
        advanceUntilIdle()

        assertEquals(
            GetFirstChecklistVariantUseCase.FirstChecklistVariant.AUTO_CREATE.name,
            fakeAnalytics.firstChecklistVariantProperty,
            "first_checklist_variant user property must be set from the resolved variant"
        )
    }

    /**
     * New user in the AUTO_CREATE treatment who hasn't been seeded: the ViewModel
     * enters the auto-create branch and reaches the per-uid seed gate for this uid.
     *
     * NOTE: we deliberately assert the seed-gate *read* rather than the final
     * `addChecklist`. The starter checklist is built via `buildFirstChecklist()`,
     * which loads localized strings through Compose Resources (`getString`) — that
     * path touches `android.content.res.Resources` and is "not mocked" in the JVM
     * host-test environment (it throws and the `runCatching` guard swallows it).
     * The seed-gate read runs *before* the resource call, so it is the deepest
     * deterministic observable of the auto-create decision at this layer. The
     * end-to-end creation is covered on-device, not here.
     */
    @Test
    fun applyFirstChecklistExperiment_newUserAutoCreateNotSeeded_entersCreateBranch() = testScope.runTest {
        // Blank userId routes through ensureUserRegistered with isNewUser=true.
        fakeUserData.nextRegistration = RegistrationData(
            userData = UserData(userId = "new-uuid", isOnboardingPassed = true),
            isNewUser = true,
        )

        createViewModel(userId = "", isOnboardingPassed = true)
        advanceUntilIdle()

        assertTrue(
            fakeFirstChecklist.seedCheckedUids.contains("new-uuid"),
            "new user in AUTO_CREATE must consult the per-uid seed gate before creating"
        )
        assertEquals(
            GetFirstChecklistVariantUseCase.FirstChecklistVariant.AUTO_CREATE.name,
            fakeAnalytics.firstChecklistVariantProperty,
            "the AUTO_CREATE cohort property must be recorded for the new user"
        )
    }

    /**
     * New user in AUTO_CREATE who was ALREADY seeded (e.g. deleted the starter
     * and relaunched): the guard short-circuits BEFORE `buildFirstChecklist()`,
     * so the creation branch is skipped and the seed flag is NOT re-marked. This
     * path is fully deterministic (no Compose Resources are touched).
     */
    @Test
    fun applyFirstChecklistExperiment_newUserAutoCreateAlreadySeeded_doesNotRecreate() = testScope.runTest {
        fakeUserData.nextRegistration = RegistrationData(
            userData = UserData(userId = "seeded-uuid", isOnboardingPassed = true),
            isNewUser = true,
        )
        fakeFirstChecklist.seededUids.add("seeded-uuid")

        createViewModel(userId = "", isOnboardingPassed = true)
        advanceUntilIdle()

        assertTrue(
            fakeChecklist.addedChecklists.isEmpty(),
            "already-seeded user must NOT get a second starter checklist"
        )
        assertFalse(
            fakeFirstChecklist.markedUids.contains("seeded-uuid"),
            "markFirstChecklistCreated must NOT be called again for an already-seeded user"
        )
    }

    /**
     * Returning user (isNewUser=false) in AUTO_CREATE: cohort property is set,
     * but the creation branch is skipped (auto-create is new-users-only), so no
     * starter checklist is added. The skip happens before `buildFirstChecklist()`,
     * keeping this path deterministic.
     */
    @Test
    fun applyFirstChecklistExperiment_returningUserAutoCreate_doesNotCreateStarterChecklist() = testScope.runTest {
        createViewModel(userId = "returning-uuid", isOnboardingPassed = true)
        advanceUntilIdle()

        assertTrue(
            fakeChecklist.addedChecklists.isEmpty(),
            "returning user must NOT get an auto-created starter checklist"
        )
        assertEquals(
            GetFirstChecklistVariantUseCase.FirstChecklistVariant.AUTO_CREATE.name,
            fakeAnalytics.firstChecklistVariantProperty,
            "cohort property must still be set for returning users"
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

        override suspend fun getOfferings(offeringId: String) = Result.success(null)
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

    /**
     * Minimal [ChecklistRepository] fake. Only [addChecklist] is wired (the splash
     * experiment uses nothing else); every other method either returns a benign
     * default or fails loudly via [notUsed] so an unexpected call is caught.
     */
    private class FakeChecklistRepository : ChecklistRepository {
        val addedChecklists = mutableListOf<Checklist>()

        override val checklists: Flow<List<Checklist>> = emptyFlow()

        override suspend fun addChecklist(checklist: Checklist): Long {
            addedChecklists += checklist
            return 1L
        }

        override suspend fun updateChecklist(checklist: Checklist) = notUsed()
        override suspend fun updateChecklistTemplate(checklist: Checklist) = notUsed()
        override suspend fun deleteChecklist(checklist: Checklist) = notUsed()
        override suspend fun getChecklistById(id: Long): Checklist? = null
        override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(null)
        override suspend fun reorderChecklists(orderedIds: List<Long>) = notUsed()

        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) = notUsed()
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) = notUsed()
        override suspend fun setFoldersEnabled(checklistId: Long, value: Boolean) = notUsed()

        override suspend fun setReminder(checklistId: Long, reminderAt: Long?) = notUsed()
        override suspend fun countActiveReminders(): Int = 0
        override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
        override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? = null

        override suspend fun setRepeatSchedule(
            checklistId: Long,
            rule: ReminderRepeatRule,
            timeOfDayMinutes: Int,
            firstTriggerAt: Long,
        ) = notUsed()

        override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) = notUsed()
        override suspend fun clearRepeatSchedule(checklistId: Long) = notUsed()
        override suspend fun resetDefaultFillChecks(checklistId: Long) = notUsed()
        override suspend fun countActiveRepeatSchedules(): Int = 0
        override suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo> = emptyList()
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> = flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addAttachment(
            fillId: Long,
            itemId: String,
            attachment: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment,
        ) = Unit
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) = Unit
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()

        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getWeeklyChecklistCount(): Int = 0
        override val weeklyChecklistCount: Flow<Int> = flowOf(0)
        override suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo> = emptyList()

        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = emptyFlow()
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = emptyFlow()
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = emptyFlow()
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 0L
        override suspend fun updateFill(fill: ChecklistFill) = notUsed()
        override suspend fun deleteFill(fill: ChecklistFill) = notUsed()
        override suspend fun reorderItems(fill: ChecklistFill, checklist: Checklist) = notUsed()

        private fun notUsed(): Nothing = error("FakeChecklistRepository: method not wired for this test")
    }

    private class FakeFirstChecklistRepository : FirstChecklistRepository {
        /** uids that are already seeded before the test runs. */
        val seededUids = mutableSetOf<String>()
        /** uids that the ViewModel marked as seeded during the test. */
        val markedUids = mutableListOf<String>()
        /** uids whose seed flag the ViewModel queried (proves the auto-create branch was entered). */
        val seedCheckedUids = mutableListOf<String>()

        override suspend fun isFirstChecklistCreated(uid: String): Boolean {
            seedCheckedUids += uid
            return uid in seededUids
        }

        override suspend fun markFirstChecklistCreated(uid: String) {
            markedUids += uid
            seededUids += uid
        }
    }

    private class NoOpNavigator : AppNavigator {
        override val backStack: NavBackStack<NavKey> = NavBackStack()
        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
        override fun onBack() {}
        override fun navigateToOnboarding() {}
        override fun navigateToInteractiveOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) {}
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?, initialText: String?) {}
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() {}
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean, initialText: String?) {}
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
        override fun navigateToAddToChecklistPicker(text: String, purpose: AddToChecklistPurpose) {}
    }

    /**
     * Records every navigation call as a short tag — used by the routing
     * tests to assert exactly which destination SplashViewModel chose.
     */
    private class RecordingNavigator : AppNavigator {
        val routes = mutableListOf<String>()

        override val backStack: NavBackStack<NavKey> = NavBackStack()
        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
        override fun onBack() {}
        override fun navigateToOnboarding() { routes += "onboarding" }
        override fun navigateToInteractiveOnboarding() { routes += "interactive_onboarding" }
        override fun navigateToMainScreen(clearBackStack: Boolean) { routes += "main" }
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?, initialText: String?) {}
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() {}
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean, initialText: String?) {}
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
        override fun navigateToAddToChecklistPicker(text: String, purpose: AddToChecklistPurpose) {}
    }

    private class NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    /**
     * Captures the `first_checklist_variant` user property so the A/B tests can
     * assert it is always set regardless of treatment.
     */
    private class RecordingAnalyticsTracker : AnalyticsTracker {
        var firstChecklistVariantProperty: String? = null

        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {
            (properties["first_checklist_variant"] as? String)?.let { firstChecklistVariantProperty = it }
        }
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
