package com.antonchuraev.homesearchchecklist.feature.create.presentation.templates

import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.navigation.api.NavCommand
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplateCategory
import com.antonchuraev.homesearchchecklist.feature.create.domain.repository.TemplatesRepository
import com.antonchuraev.homesearchchecklist.feature.create.domain.usecase.CreateWeeklyChecklistUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TemplatesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── Fakes ────────────────────────────────────────────────────────────────

    private class FakeTemplatesRepository : TemplatesRepository {
        override suspend fun getTemplates(): List<ChecklistTemplate> = emptyList()
        override suspend fun getTemplatesByCategory(): List<TemplateCategory> = emptyList()
        override suspend fun getTemplateById(id: String): ChecklistTemplate? = null
    }

    private class FakeChecklistRepository(
        private val checklistCount: Int = 0,
        private val weeklyCount: Int = 0
    ) : ChecklistRepository {
        override val checklists: Flow<List<Checklist>> = flowOf(
            List(checklistCount) {
                Checklist(id = it.toLong(), name = "Checklist $it", items = emptyList())
            }
        )
        override val weeklyChecklistCount: Flow<Int> = flowOf(weeklyCount)
        override suspend fun addChecklist(checklist: Checklist): Long = 1L
        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {}
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun getChecklistById(id: Long): Checklist? = null
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {}
        override suspend fun countActiveReminders(): Int = 0
        override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
        override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? = null
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = emptyFlow()
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = emptyFlow()
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = emptyFlow()
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 1L
        override suspend fun updateFill(fill: ChecklistFill) {}
        override suspend fun deleteFill(fill: ChecklistFill) {}
        override suspend fun setRepeatSchedule(checklistId: Long, rule: ReminderRepeatRule, timeOfDayMinutes: Int, firstTriggerAt: Long) {}
        override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) {}
        override suspend fun clearRepeatSchedule(checklistId: Long) {}
        override suspend fun resetDefaultFillChecks(checklistId: Long) {}
        override suspend fun countActiveRepeatSchedules(): Int = 0
        override suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo> = emptyList()
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getWeeklyChecklistCount(): Int = weeklyCount
        override suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo> = emptyList()
    }

    private class FakePaywallRepository(
        private val isPremium: Boolean = false
    ) : PaywallRepository {
        override val subscriptionStatus: Flow<SubscriptionStatus> = flowOf(
            if (isPremium) SubscriptionStatus(isActive = true, activeEntitlements = setOf("AiChecklists Pro"))
            else SubscriptionStatus.FREE
        )
        override suspend fun getOfferings(): Result<PaywallOffering?> = Result.success(null)
        override suspend fun purchase(packageId: String): PurchaseResult = PurchaseResult.Error("stub")
        override suspend fun restorePurchases(): RestoreResult = RestoreResult.Error("stub")
        override suspend fun refreshSubscriptionStatus() {}
        override fun isConfigured(): Boolean = false
        override suspend fun logIn(appUserId: String): Result<LoginResult> = Result.failure(NotImplementedError())
        override suspend fun logOut(): Result<SubscriptionStatus> = Result.failure(NotImplementedError())
    }

    private class FakeUserDataRepository(private val isPremium: Boolean = false) : UserDataRepository {
        private val data = UserData(userId = "test", isPremium = isPremium)
        override fun getUserDataFlow(): StateFlow<UserData> = MutableStateFlow(data)
        override suspend fun getUserData(): UserData = data
        override suspend fun update(userData: UserData) {}
        override suspend fun ensureUserRegistered(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = data, isNewUser = false))
        override suspend fun syncWithServer(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = data, isNewUser = false))
        override suspend fun isPaywallLinked(): Boolean = false
        override suspend fun setPaywallLinked(linked: Boolean) {}
        override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
        override suspend fun getFirstLaunchAtMillis(): Long = 0L
    }

    private class FakeRemoteConfigProvider : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = when (key) {
            RemoteConfigKeys.MAX_CHECKLISTS_FREE -> RemoteConfigDefaults.MAX_CHECKLISTS_FREE
            else -> defaultValue
        }
    }

    private class FakeNavigator : AppNavigator {
        var paywallSource: String? = null
        var navigatedToCreateChecklist = false
        var backInvoked = false

        override val commands: Flow<NavCommand> = emptyFlow()
        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
        override fun onBack() { backInvoked = true }
        override fun navigateToOnboarding() {}
        override fun navigateToInteractiveOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) {}
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?) { navigatedToCreateChecklist = true }
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
        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {}
        override fun navigateToShareChecklist(checklistId: Long) {}
        override fun navigateToUpdateFeed() {}
        override fun navigateToSettings() {}
        override fun navigateToToday() {}
        override fun navigateToScreenCatalog() {}
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun buildViewModel(
        checklistCount: Int = 0,
        isPremium: Boolean = false
    ): Pair<TemplatesViewModel, FakeNavigator> {
        val navigator = FakeNavigator()
        val checklistRepo = FakeChecklistRepository(checklistCount = checklistCount)
        val paywallRepo = FakePaywallRepository(isPremium = isPremium)
        val userLimitsUseCase = GetUserLimitsUseCase(
            remoteConfigProvider = FakeRemoteConfigProvider(),
            checklistRepository = checklistRepo,
            paywallRepository = paywallRepo,
            userDataRepository = FakeUserDataRepository(isPremium = isPremium)
        )
        val viewModel = TemplatesViewModel(
            appNavigator = navigator,
            templatesRepository = FakeTemplatesRepository(),
            checklistRepository = checklistRepo,
            createWeeklyChecklistUseCase = CreateWeeklyChecklistUseCase(checklistRepo, userLimitsUseCase),
            getUserLimitsUseCase = userLimitsUseCase
        )
        return viewModel to navigator
    }

    // ─── canCreateChecklist state ─────────────────────────────────────────────

    @Test
    fun `whenFreeAtChecklistLimit_canCreateChecklistIsFalse`() = runTest {
        // Free user has 4 checklists, default max is 4 → canCreateChecklist = false
        val maxFree = RemoteConfigDefaults.MAX_CHECKLISTS_FREE.toInt()
        val (viewModel, _) = buildViewModel(checklistCount = maxFree, isPremium = false)
        advanceUntilIdle()

        assertFalse(
            viewModel.screenState.value.canCreateChecklist,
            "Expected canCreateChecklist=false when free user is at limit ($maxFree/$maxFree)"
        )
    }

    @Test
    fun `whenFreeBelowChecklistLimit_canCreateChecklistIsTrue`() = runTest {
        val (viewModel, _) = buildViewModel(checklistCount = 0, isPremium = false)
        advanceUntilIdle()

        assertTrue(
            viewModel.screenState.value.canCreateChecklist,
            "Expected canCreateChecklist=true when free user is below limit"
        )
    }

    @Test
    fun `whenPremium_canCreateChecklistIsTrue`() = runTest {
        // Premium user with 100 checklists — no limit applies
        val (viewModel, _) = buildViewModel(checklistCount = 100, isPremium = true)
        advanceUntilIdle()

        assertTrue(
            viewModel.screenState.value.canCreateChecklist,
            "Expected canCreateChecklist=true for premium user regardless of count"
        )
    }

    // ─── OnCreateManuallyClick routing ────────────────────────────────────────

    @Test
    fun `OnCreateManuallyClick_whenLocked_navigatesToPaywall_skipsCreate`() = runTest {
        val maxFree = RemoteConfigDefaults.MAX_CHECKLISTS_FREE.toInt()
        val (viewModel, navigator) = buildViewModel(checklistCount = maxFree, isPremium = false)
        advanceUntilIdle()

        viewModel.sendIntent(TemplatesScreenIntent.OnCreateManuallyClick)
        advanceUntilIdle()

        assertEquals("checklist_limit", navigator.paywallSource,
            "Expected paywall navigation with source='checklist_limit'")
        assertFalse(navigator.navigatedToCreateChecklist,
            "Expected no navigation to create screen when locked")
    }

    @Test
    fun `OnCreateManuallyClick_whenUnlocked_navigatesToCreateScreen_skipsPaywall`() = runTest {
        val (viewModel, navigator) = buildViewModel(checklistCount = 0, isPremium = false)
        advanceUntilIdle()

        viewModel.sendIntent(TemplatesScreenIntent.OnCreateManuallyClick)
        advanceUntilIdle()

        assertTrue(navigator.navigatedToCreateChecklist,
            "Expected navigation to create screen when not locked")
        assertNull(navigator.paywallSource,
            "Expected no paywall navigation when not locked")
    }
}
