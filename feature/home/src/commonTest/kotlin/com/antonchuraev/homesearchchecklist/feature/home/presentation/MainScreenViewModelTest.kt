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
import kotlinx.coroutines.flow.flowOf
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

private class FakeHintsRepository(initialShown: Boolean = false) : HintsRepository {
    var marked = false
    private val _state = MutableStateFlow(initialShown)
    override val hamburgerHintShown: Flow<Boolean> = _state
    override suspend fun markHamburgerHintShown() {
        marked = true
        _state.value = true
    }
}

private class FakeGoogleAuthRepository : GoogleAuthRepository {
    override val authState: StateFlow<GoogleAuthState> = MutableStateFlow(GoogleAuthState.NotAuthenticated)
    override suspend fun signInWithGoogle(): Result<GoogleUser> = Result.failure(NotImplementedError())
    override suspend fun signOut() {}
    override suspend fun getIdToken(): String? = null
    override suspend fun restoreSession() {}
}

private class FakeSyncRepository : SyncRepository {
    override val syncState: StateFlow<SyncState> = MutableStateFlow(SyncState.Disabled)
    override fun observeCloudChecklistIds(): Flow<AppResult<List<String>>> = flowOf()
    override fun observeCloudChecklist(cloudId: String): Flow<AppResult<Checklist>> = flowOf()
    override suspend fun pushPendingChanges(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun initialUpload(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun pullAndMerge(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun startListening() {}
    override suspend fun stopListening() {}
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

private class FakeChecklistRepository : ChecklistRepository {
    override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
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

private class FakeUserDataRepository : UserDataRepository {
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
private fun makeViewModel(hintsRepository: HintsRepository): MainScreenViewModel {
    val fakeChecklistRepo = FakeChecklistRepository()
    val fakePaywallRepo = FakePaywallRepository()
    val fakeUserDataRepo = FakeUserDataRepository()
    val fakeRemoteConfig = FakeRemoteConfigProvider()
    return MainScreenViewModel(
        repository = fakeChecklistRepo,
        appNavigator = FakeNavigator(),
        getSubscriptionStatusUseCase = GetSubscriptionStatusUseCase(fakePaywallRepo),
        userDataRepository = fakeUserDataRepo,
        getUserLimitsUseCase = GetUserLimitsUseCase(
            remoteConfigProvider = fakeRemoteConfig,
            checklistRepository = fakeChecklistRepo,
            paywallRepository = fakePaywallRepo,
            userDataRepository = fakeUserDataRepo,
        ),
        analyticsTracker = FakeAnalyticsTracker(),
        hintsRepository = hintsRepository,
        googleAuthRepository = FakeGoogleAuthRepository(),
        syncRepository = FakeSyncRepository(),
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
}
