package com.antonchuraev.homesearchchecklist.feature.home.presentation

import com.antonchuraev.homesearchchecklist.core.datastore.api.HintsRepository
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleUser
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.SyncRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.SyncState
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.LinkGoogleAccountResult
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ─── Fakes ───────────────────────────────────────────────────────────────────

private class FakeHintsRepository(
    initialShown: Boolean = false,
    initialDismissCount: Int = 0,
) : HintsRepository {
    var marked = false
    private val _state = MutableStateFlow(initialShown)
    override val hamburgerHintShown: Flow<Boolean> = _state
    override suspend fun markHamburgerHintShown() {
        marked = true
        _state.value = true
    }

    private val _dismissCount = MutableStateFlow(initialDismissCount)
    override val syncBannerDismissCount: Flow<Int> = _dismissCount
    val dismissCountValue: Int get() = _dismissCount.value
    override suspend fun incrementSyncBannerDismissCount() {
        _dismissCount.value += 1
    }
}

private class FakeGoogleAuthRepository(
    /** Google credential step outcome. Default keeps the pre-existing "not wired" behaviour. */
    private val signInResult: Result<GoogleUser> = Result.failure(NotImplementedError()),
    private val idToken: String? = null,
) : GoogleAuthRepository {
    override val authState: StateFlow<GoogleAuthState> = MutableStateFlow(GoogleAuthState.NotAuthenticated)
    override suspend fun signInWithGoogle(): Result<GoogleUser> = signInResult
    override suspend fun signOut() {}
    override suspend fun getIdToken(): String? = idToken
    override suspend fun restoreSession() {}
}

private class FakeSyncRepository(
    /** When set, [pushPendingChanges] reports this failure as an [AppResult.Error] return value. */
    private val pushError: Exception? = null,
) : SyncRepository {
    var pushCount = 0
    override val syncState: StateFlow<SyncState> = MutableStateFlow(SyncState.Disabled)
    override fun observeCloudChecklistIds(): Flow<AppResult<List<String>>> = flowOf()
    override fun observeCloudChecklist(cloudId: String): Flow<AppResult<Checklist>> = flowOf()
    override suspend fun pushPendingChanges(): AppResult<Unit> {
        pushCount++
        return pushError?.let { AppResult.Error(it) } ?: AppResult.Success(Unit)
    }
    override suspend fun initialUpload(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun pullAndMerge(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun startListening() {}
    override suspend fun stopListening() {}
}

/** Records [AppLogger.error] calls so tests can assert failures are logged, not swallowed. */
private class FakeAppLogger : AppLogger {
    val errors = mutableListOf<Pair<String, Throwable?>>()
    override fun debug(tag: String, message: String) {}
    override fun info(tag: String, message: String) {}
    override fun warning(tag: String, message: String) {}
    override fun error(tag: String, message: String, throwable: Throwable?) {
        errors.add(message to throwable)
    }
}

private class FakeNavigator : AppNavigator {
    override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
    override val backStack: NavBackStack<NavKey> = NavBackStack()
    override fun showWidgetInstruction() {}
    override fun requestCreateWeeklyChecklist() {}
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
    override fun navigateToAddToChecklistPicker(text: String, purpose: com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose) {}
}

private open class FakeChecklistRepository(
    initialChecklists: List<Checklist> = emptyList(),
) : ChecklistRepository {
    override val checklists: Flow<List<Checklist>> = flowOf(initialChecklists)
    override val weeklyChecklistCount: Flow<Int> = flowOf(0)
    override suspend fun addChecklist(checklist: Checklist): Long = 0L
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
    override suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo> = emptyList()
    override suspend fun setRepeatSchedule(checklistId: Long, rule: ReminderRepeatRule, timeOfDayMinutes: Int, firstTriggerAt: Long) {}
    override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) {}
    override suspend fun clearRepeatSchedule(checklistId: Long) {}
    override suspend fun resetDefaultFillChecks(checklistId: Long) {}
    override suspend fun countActiveRepeatSchedules(): Int = 0
    override suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo> = emptyList()
    override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
    override suspend fun getTotalAdditionalFillCount(): Int = 0
    override suspend fun getWeeklyChecklistCount(): Int = 0
    override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
    override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = flowOf(null)
    override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
    override suspend fun getFillById(id: Long): ChecklistFill? = null
    override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
    override suspend fun addFill(fill: ChecklistFill): Long = 0L
    override suspend fun updateFill(fill: ChecklistFill) {}
    override suspend fun deleteFill(fill: ChecklistFill) {}
    override suspend fun reorderItems(fill: ChecklistFill, checklist: Checklist) {}
    override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> = flowOf(emptyList())
    override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> = emptyList()
    override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
    override suspend fun addAttachment(fillId: Long, itemId: String, attachment: Attachment) {}
    override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) {}
}

private class FakePaywallRepository : PaywallRepository {
    override val subscriptionStatus: Flow<SubscriptionStatus> =
        flowOf(SubscriptionStatus.FREE)
    override suspend fun getOfferings(offeringId: String): Result<PaywallOffering?> = Result.success(null)
    override suspend fun purchase(packageId: String): PurchaseResult = PurchaseResult.Cancelled
    override suspend fun restorePurchases(): RestoreResult = RestoreResult.NoActiveSubscription
    override suspend fun refreshSubscriptionStatus() {}
    override fun isConfigured(): Boolean = false
    override suspend fun logIn(appUserId: String): Result<LoginResult> = Result.success(
        LoginResult(subscriptionStatus = SubscriptionStatus.FREE, isNewCustomer = false)
    )
    override suspend fun logOut(): Result<SubscriptionStatus> = Result.success(SubscriptionStatus.FREE)
}

private class FakeUserDataRepository(
    /**
     * Outcome of the account-linking write. Default mirrors the interface default
     * (`Result.failure`) — the very case the ViewModel used to drop on the floor.
     */
    private val linkResult: Result<LinkGoogleAccountResult> =
        Result.failure(IllegalStateException("User not registered")),
) : UserDataRepository {
    var linkCallCount = 0
        private set

    override suspend fun linkGoogleAccount(idToken: String, platform: String): Result<LinkGoogleAccountResult> {
        linkCallCount++
        return linkResult
    }

    private val _userData = MutableStateFlow(UserData())
    override fun getUserDataFlow(): StateFlow<UserData> = _userData
    override suspend fun getUserData(): UserData = _userData.value
    override suspend fun update(userData: UserData) { _userData.value = userData }
    override suspend fun ensureUserRegistered(): Result<RegistrationData> = Result.failure(UnsupportedOperationException())
    override suspend fun syncWithServer(): Result<RegistrationData> = Result.failure(UnsupportedOperationException())
    override suspend fun isPaywallLinked(): Boolean = false
    override suspend fun setPaywallLinked(linked: Boolean) {}
    override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
    override suspend fun getFirstLaunchAtMillis(): Long = 0L
}

private class FakeRemoteConfigProvider : RemoteConfigProvider {
    override suspend fun fetchAndActivate(): Boolean = true
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
    override fun getString(key: String, defaultValue: String): String = defaultValue
    override fun getLong(key: String, defaultValue: Long): Long = defaultValue
}

private class FakeAnalyticsTracker : AnalyticsTracker {
    override fun setUserId(userId: String) {}
    override fun setUserProperties(properties: Map<String, Any>) {}
    override fun screenView(name: String) {}
    override fun event(name: String, params: Map<String, Any>) {}
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
private fun makeViewModel(
    hintsRepository: HintsRepository,
    checklists: List<Checklist> = emptyList(),
    checklistRepository: FakeChecklistRepository = FakeChecklistRepository(checklists),
    syncRepository: SyncRepository = FakeSyncRepository(),
    logger: AppLogger = FakeAppLogger(),
    googleAuthRepository: GoogleAuthRepository = FakeGoogleAuthRepository(),
    userDataRepository: FakeUserDataRepository = FakeUserDataRepository(),
): MainScreenViewModel {
    val fakePaywallRepo = FakePaywallRepository()
    val fakeUserDataRepo = userDataRepository
    val fakeRemoteConfig = FakeRemoteConfigProvider()
    return MainScreenViewModel(
        repository = checklistRepository,
        appNavigator = FakeNavigator(),
        getSubscriptionStatusUseCase = GetSubscriptionStatusUseCase(fakePaywallRepo),
        userDataRepository = fakeUserDataRepo,
        getUserLimitsUseCase = GetUserLimitsUseCase(
            remoteConfigProvider = fakeRemoteConfig,
            checklistRepository = checklistRepository,
            paywallRepository = fakePaywallRepo,
            userDataRepository = fakeUserDataRepo,
        ),
        analyticsTracker = FakeAnalyticsTracker(),
        hintsRepository = hintsRepository,
        googleAuthRepository = googleAuthRepository,
        syncRepository = syncRepository,
        logger = logger,
    )
}

/** Awaits first non-Loading state from the ViewModel. */
private suspend fun MainScreenViewModel.awaitSuccess(): MainScreenState.Success =
    screenState.first { it is MainScreenState.Success } as MainScreenState.Success

// ─── Tests ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun hamburgerHint_initiallyShown_whenRepoFlowEmitsFalse() = runTest {
        val hints = FakeHintsRepository(initialShown = false)
        val vm = makeViewModel(hints)

        val state = vm.awaitSuccess()

        assertTrue(state.showHamburgerHint, "Hint should be visible when repo emits false")
    }

    @Test
    fun hamburgerHint_hidden_whenRepoFlowEmitsTrue() = runTest {
        val hints = FakeHintsRepository(initialShown = true)
        val vm = makeViewModel(hints)

        val state = vm.awaitSuccess()

        assertFalse(state.showHamburgerHint, "Hint should be hidden when repo emits true")
    }

    @Test
    fun onHamburgerHintCompleted_intent_callsMarkHamburgerHintShown() = runTest {
        val hints = FakeHintsRepository(initialShown = false)
        val vm = makeViewModel(hints)

        // Ensure ViewModel is active before sending intent
        vm.awaitSuccess()
        vm.sendIntent(MainScreenIntent.OnHamburgerHintCompleted)

        // Give coroutine a chance to run (UnconfinedTestDispatcher runs eagerly)
        assertTrue(hints.marked, "markHamburgerHintShown() should be called after OnHamburgerHintCompleted")
    }

    @Test
    fun onHamburgerHintCompleted_intent_updatesStateToHidden() = runTest {
        val hints = FakeHintsRepository(initialShown = false)
        val vm = makeViewModel(hints)

        vm.awaitSuccess()
        vm.sendIntent(MainScreenIntent.OnHamburgerHintCompleted)

        val updatedState = vm.awaitSuccess()
        assertFalse(
            updatedState.showHamburgerHint,
            "After OnHamburgerHintCompleted, hint should be hidden in subsequent state"
        )
    }

    // ─── Google sign-in feedback ─────────────────────────────────────────────

    /**
     * Crashlytics fa36c2ee: linkGoogleAccount RETURNS Result.failure ("User not registered") — it
     * does not throw — so the old try/catch never saw it and the failed Result was dropped. The user
     * got "Signed in successfully" on an account linked to nothing. Every user action needs a
     * truthful response, so a failed link must surface the error snackbar instead.
     */
    @Test
    fun signInClick_whenLinkGoogleAccountReturnsFailure_showsErrorNotSuccess() = runTest {
        val userDataRepo = FakeUserDataRepository(
            linkResult = Result.failure(IllegalStateException("User not registered")),
        )
        val vm = makeViewModel(
            FakeHintsRepository(),
            googleAuthRepository = FakeGoogleAuthRepository(
                signInResult = Result.success(
                    GoogleUser(firebaseUid = "uid-1", email = "u@gmail.com", displayName = "U"),
                ),
                idToken = "id-token",
            ),
            userDataRepository = userDataRepo,
        )
        vm.awaitSuccess()

        val effects = mutableListOf<MainScreenSideEffect>()
        backgroundScope.launch(Dispatchers.Main) { vm.sideEffect.collect { effects.add(it) } }

        vm.sendIntent(MainScreenIntent.OnSignInClick)

        assertEquals(1, userDataRepo.linkCallCount, "Linking must be attempted after a successful Google sign-in")
        val snackbarKeys = effects.filterIsInstance<MainScreenSideEffect.ShowSnackbar>().map { it.messageKey }
        assertTrue(
            snackbarKeys.contains("sign_in_unavailable"),
            "A failed account link must tell the user why — got $snackbarKeys",
        )
        assertFalse(
            snackbarKeys.contains("google_sign_in_success"),
            "A failed account link must NOT report success — got $snackbarKeys",
        )
    }

    @Test
    fun signInClick_whenLinkGoogleAccountSucceeds_reportsSuccess() = runTest {
        val vm = makeViewModel(
            FakeHintsRepository(),
            googleAuthRepository = FakeGoogleAuthRepository(
                signInResult = Result.success(
                    GoogleUser(firebaseUid = "uid-1", email = "u@gmail.com", displayName = "U"),
                ),
                idToken = "id-token",
            ),
            userDataRepository = FakeUserDataRepository(
                linkResult = Result.success(
                    LinkGoogleAccountResult(
                        googleEmail = "u@gmail.com",
                        aiCredits = 100,
                        isPremium = false,
                        bonusCreditsGranted = 100,
                        isExistingAccount = false,
                    ),
                ),
            ),
        )
        vm.awaitSuccess()

        val effects = mutableListOf<MainScreenSideEffect>()
        backgroundScope.launch(Dispatchers.Main) { vm.sideEffect.collect { effects.add(it) } }

        vm.sendIntent(MainScreenIntent.OnSignInClick)

        val snackbarKeys = effects.filterIsInstance<MainScreenSideEffect.ShowSnackbar>().map { it.messageKey }
        assertTrue(
            snackbarKeys.contains("google_sign_in_success"),
            "A successful link must still confirm success — got $snackbarKeys",
        )
    }

    // ─── Sync banner visibility ──────────────────────────────────────────────

    @Test
    fun syncBanner_hidden_whenChecklistListEmpty() = runTest {
        val vm = makeViewModel(FakeHintsRepository(), checklists = emptyList())

        val state = vm.awaitSuccess()

        assertFalse(state.showSyncBanner, "New user with an empty list must not see the sync banner")
    }

    @Test
    fun syncBanner_hidden_whenSingleChecklist() = runTest {
        val vm = makeViewModel(FakeHintsRepository(), checklists = checklists(1))

        val state = vm.awaitSuccess()

        assertFalse(
            state.showSyncBanner,
            "A single (typically auto-created) checklist isn't worth syncing — banner stays hidden"
        )
    }

    @Test
    fun syncBanner_shown_whenMoreThanOneChecklist_andNotGoogleLinked() = runTest {
        val vm = makeViewModel(FakeHintsRepository(), checklists = checklists(2))

        val state = vm.awaitSuccess()

        assertTrue(
            state.showSyncBanner,
            "Once the user has >1 checklist and isn't signed in, the sync banner should appear"
        )
    }

    @Test
    fun syncBanner_hidden_whenDismissCountAtPermanentThreshold() = runTest {
        // 3 lifetime dismissals = permanent hide, even with multiple checklists and a fresh session.
        val vm = makeViewModel(
            FakeHintsRepository(initialDismissCount = 3),
            checklists = checklists(2),
        )

        val state = vm.awaitSuccess()

        assertFalse(state.showSyncBanner, "After 3 lifetime dismissals the banner never shows again")
    }

    @Test
    fun onDismissSyncBanner_hidesBannerThisSession_andIncrementsCount() = runTest {
        val hints = FakeHintsRepository(initialDismissCount = 0)
        val vm = makeViewModel(hints, checklists = checklists(2))

        val before = vm.awaitSuccess()
        assertTrue(before.showSyncBanner, "Precondition: banner visible before dismiss")

        vm.sendIntent(MainScreenIntent.OnDismissSyncBanner)

        val after = vm.awaitSuccess()
        assertFalse(after.showSyncBanner, "Dismiss hides the banner for the rest of this session")
        assertEquals(1, hints.dismissCountValue, "Dismiss must bump the persistent lifetime count")
    }

    // ── Failure observability ───────────────────────────────────────────────

    /**
     * Regression guard for the prod incident where the Lists tab hung on an infinite spinner:
     * the checklist stream threw before its first emission, which cancelled the `defaultStateIn`
     * sharing scope and pinned screenState on Loading forever — no crash, no log, no user signal.
     *
     * The failure must be loud: a rendered Error state AND an AppLogger.error entry.
     */
    @Test
    fun screenState_repositoryThrows_emitsErrorState_andLogsError() = runTest {
        val cause = RuntimeException("DB failure")
        val logger = FakeAppLogger()
        val vm = makeViewModel(
            FakeHintsRepository(),
            checklistRepository = throwingRepository(cause),
            logger = logger,
        )

        val state = vm.screenState.first { it !is MainScreenState.Loading }

        assertIs<MainScreenState.Error>(state)
        // Identity (===) would be wrong here: the throw crosses the channel-based `combine`, and
        // kotlinx stacktrace recovery hands `catch` a COPY carrying the same type and message.
        val logged = logger.errors.firstOrNull { it.first == "main_checklists_fetch_failed" }
        assertTrue(logged != null, "Must log the greppable event tag; got ${logger.errors}")
        assertEquals(
            cause.message,
            logged.second?.message,
            "The original throwable must be attached (Crashlytics recordException)",
        )
    }

    /**
     * The prod NPE carried `message == null`, which is exactly why [MainScreenState.Error] holds no
     * payload: a raw exception message is untranslated and often absent. It belongs in the log only.
     */
    @Test
    fun screenState_repositoryThrowsNullMessage_stillEmitsErrorState() = runTest {
        val logger = FakeAppLogger()
        val vm = makeViewModel(
            FakeHintsRepository(),
            checklistRepository = throwingRepository(NullPointerException()),
            logger = logger,
        )

        assertIs<MainScreenState.Error>(vm.screenState.first { it !is MainScreenState.Loading })
        assertTrue(logger.errors.isNotEmpty(), "AppLogger.error must be called on repository failure")
    }

    @Test
    fun onRetry_afterError_resubscribes_andEmitsSuccess() = runTest {
        // ONE stable flow instance that re-reads `shouldThrow` on every collect — this mirrors a real
        // Room flow (re-collect = re-query). A getter handing out a fresh flow per call would not:
        // `checklistsWithProgress` evaluates `repository.checklists` once at VM construction, so the
        // VM would hold the throwing instance forever and no retry could ever succeed.
        val repo = object : FakeChecklistRepository() {
            var shouldThrow = true
            override val checklists: Flow<List<Checklist>> = flow {
                if (shouldThrow) throw RuntimeException("DB failure")
                emit(checklists(2))
            }
        }
        val vm = makeViewModel(FakeHintsRepository(), checklistRepository = repo)

        assertIs<MainScreenState.Error>(vm.screenState.first { it !is MainScreenState.Loading })

        // Heal the repository, then retry: flatMapLatest must re-subscribe from scratch.
        repo.shouldThrow = false
        vm.sendIntent(MainScreenIntent.OnRetry)

        val recovered = vm.awaitSuccess()
        assertEquals(2, recovered.checklists.size, "Retry must re-fetch the healed checklist stream")
    }

    /**
     * A retry that fails again must still change the observed state, or Try Again reads as broken.
     */
    @Test
    fun onRetry_whenRepositoryKeepsFailing_emitsLoadingBeforeErrorAgain() = runTest {
        val vm = makeViewModel(
            FakeHintsRepository(),
            checklistRepository = throwingRepository(RuntimeException("DB failure")),
        )

        val seen = mutableListOf<MainScreenState>()
        // backgroundScope inherits runTest's StandardTestDispatcher — Dispatchers.setMain does not
        // apply to it, so an unpinned collector would not start before the asserts below.
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.screenState.toList(seen)
        }
        assertIs<MainScreenState.Error>(seen.last())
        val beforeRetry = seen.size

        vm.sendIntent(MainScreenIntent.OnRetry)

        // screenState is a StateFlow, so it conflates equal values, and Error is a data object
        // equal to itself. Without a distinct value in between, the second failure emits
        // Error == Error, nothing recomposes, and the screen freezes with no feedback at all.
        val afterRetry = seen.drop(beforeRetry)
        assertTrue(
            afterRetry.any { it is MainScreenState.Loading },
            "Retry must emit a distinct state so the UI reacts; observed after retry: $afterRetry",
        )
        assertIs<MainScreenState.Error>(seen.last())
        job.cancel()
    }

    /**
     * The init-block sync push observes the same failing Room stream. A throw there used to die
     * uncaught, silently killing the push job. It must be logged instead — and it must not take the
     * screen down with it.
     */
    @Test
    fun initPendingSyncPush_repositoryThrows_isLogged_andDoesNotCrash() = runTest {
        val logger = FakeAppLogger()
        makeViewModel(
            FakeHintsRepository(),
            checklistRepository = throwingRepository(RuntimeException("DB failure")),
            logger = logger,
        )

        assertTrue(
            logger.errors.any { it.first == "main_pending_sync_observe_failed" },
            "Upstream failure in the init collect must be logged; got ${logger.errors}",
        )
    }

    /**
     * pushPendingChanges reports failure as an AppResult.Error RETURN VALUE (it does not throw), so
     * the result has to be read or the failure vanishes. It stays non-fatal: the collection survives
     * so the next emission retries the push.
     */
    @Test
    fun initPendingSyncPush_pushReturnsError_isLogged_andCollectionSurvives() = runTest {
        val cause = IllegalStateException("network down")
        val logger = FakeAppLogger()
        val sync = FakeSyncRepository(pushError = cause)
        val vm = makeViewModel(
            FakeHintsRepository(),
            checklists = checklists(1),
            syncRepository = sync,
            logger = logger,
        )

        // The screen itself is unaffected by a failed background push.
        assertIs<MainScreenState.Success>(vm.awaitSuccess())
        assertTrue(sync.pushCount > 0, "Precondition: the push was attempted")
        assertTrue(
            logger.errors.any { it.first == "main_pending_sync_push_failed" && it.second === cause },
            "A push returning AppResult.Error must be logged, not discarded; got ${logger.errors}",
        )
    }
}

/** A repository whose checklist stream fails with [cause] before emitting anything. */
private fun throwingRepository(cause: Throwable): FakeChecklistRepository =
    object : FakeChecklistRepository() {
        override val checklists: Flow<List<Checklist>> = flow { throw cause }
    }

/** Builds [count] minimal checklists for sync-banner visibility tests. */
private fun checklists(count: Int): List<Checklist> =
    (1..count).map { Checklist(id = it.toLong(), name = "List $it", items = emptyList()) }
