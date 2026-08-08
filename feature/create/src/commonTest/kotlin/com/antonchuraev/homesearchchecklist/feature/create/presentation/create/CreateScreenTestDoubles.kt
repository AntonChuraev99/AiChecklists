package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
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
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Shared test doubles for the v2 "create project" screen tests.
 *
 * Extracted into their own file rather than copied into each test class so the RED tests for the
 * redesign (item order, limit banner, weekly switch) all drive the SAME production seams the shipped
 * screen uses — a per-test private fake drifts and lets a test stay green against a stub the app
 * never touches.
 *
 * Deliberately NOT merged into `CreateChecklistViewModelTest`'s private fakes: that file is the v1
 * regression suite, and editing it while adding v2 behaviour would blur "what already worked" with
 * "what must start working".
 */
internal class RecordingChecklistRepository : ChecklistRepository {
    var loadResult: Checklist? = null
    var lastUpdatedChecklist: Checklist? = null
    var lastAddedChecklist: Checklist? = null

    /** Backs the checklist-count half of [GetUserLimitsUseCase]; see [withChecklistCount]. */
    var checklistsFlow: Flow<List<Checklist>> = emptyFlow()
    override val checklists: Flow<List<Checklist>> get() = checklistsFlow

    fun withChecklistCount(count: Int) = apply {
        checklistsFlow = flowOf(
            List(count) { Checklist(id = it.toLong(), name = "C$it", items = emptyList()) }
        )
    }

    override suspend fun addChecklist(checklist: Checklist): Long {
        lastAddedChecklist = checklist
        return 1L
    }

    override suspend fun updateChecklist(checklist: Checklist) {
        lastUpdatedChecklist = checklist
    }

    override suspend fun updateChecklistTemplate(checklist: Checklist) {
        lastUpdatedChecklist = checklist
    }

    /**
     * Successive answers for [getChecklistById]; falls back to [loadResult] once exhausted.
     *
     * Exists so a test can model "the async load had not landed yet when Save was tapped" — the one
     * window in which the edit path has to re-read the row instead of trusting form state.
     */
    val loadResultSequence: MutableList<Checklist?> = mutableListOf()

    override suspend fun deleteChecklist(checklist: Checklist) = notUsed()
    override suspend fun getChecklistById(id: Long): Checklist? =
        if (loadResultSequence.isNotEmpty()) loadResultSequence.removeAt(0) else loadResult
    override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(loadResult)
    override suspend fun reorderChecklists(orderedIds: List<Long>) = notUsed()

    override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) = notUsed()
    override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) = notUsed()
    override suspend fun setFoldersEnabled(checklistId: Long, value: Boolean) = notUsed()

    /** Set by the ONE-SHOT half of the staged reminder, once the project exists. */
    var reminderSetFor: Pair<Long, Long?>? = null
    /** Set by the REPEAT half: (checklistId, rule, timeOfDayMinutes, firstTriggerAt). */
    var repeatScheduleSetFor: RecordedRepeatSchedule? = null
    /** Drives the free one-shot-reminder gate; see `CreateChecklistViewModel.onReminderClick`. */
    var activeReminderCount: Int = 0
    var activeRepeatScheduleCount: Int = 0

    override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {
        reminderSetFor = checklistId to reminderAt
    }

    override suspend fun countActiveReminders(): Int = activeReminderCount
    override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
    override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? = null

    override suspend fun setRepeatSchedule(
        checklistId: Long,
        rule: ReminderRepeatRule,
        timeOfDayMinutes: Int,
        firstTriggerAt: Long,
    ) {
        repeatScheduleSetFor = RecordedRepeatSchedule(checklistId, rule, timeOfDayMinutes, firstTriggerAt)
    }

    override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) = notUsed()
    override suspend fun clearRepeatSchedule(checklistId: Long) = notUsed()
    override suspend fun resetDefaultFillChecks(checklistId: Long) = notUsed()
    override suspend fun countActiveRepeatSchedules(): Int = activeRepeatScheduleCount
    override suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo> = emptyList()
    override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> =
        flowOf(emptyList())

    override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> = emptyList()
    override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
    override suspend fun addAttachment(fillId: Long, itemId: String, attachment: Attachment) = Unit
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

    private fun notUsed(): Nothing = error("RecordingChecklistRepository: method not wired for this test")
}

internal data class RecordedRepeatSchedule(
    val checklistId: Long,
    val rule: ReminderRepeatRule,
    val timeOfDayMinutes: Int,
    val firstTriggerAt: Long,
)

/**
 * Records the alarms the ViewModel asks for after a project is created.
 *
 * Separate from the repository double on purpose: persisting the reminder and ARMING it are two
 * different failures, and a test that only checked the row would pass against a project whose
 * reminder never fires.
 */
internal class RecordingReminderScheduler : ChecklistReminderScheduler {
    var scheduledReminder: Pair<Long, Long>? = null
    var scheduledRepeat: Pair<Long, Long>? = null

    override fun scheduleReminder(checklistId: Long, triggerAtMillis: Long) {
        scheduledReminder = checklistId to triggerAtMillis
    }

    override fun cancelReminder(checklistId: Long) {}
    override suspend fun rescheduleAllActiveReminders() {}

    override fun scheduleRepeat(checklistId: Long, triggerAtMillis: Long) {
        scheduledRepeat = checklistId to triggerAtMillis
    }

    override fun cancelRepeat(checklistId: Long) {}
    override suspend fun rescheduleAllActiveRepeats() {}

    override fun scheduleItemReminder(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) {}
    override fun cancelItemReminder(checklistId: Long, fillId: Long, itemId: String) {}
    override fun scheduleItemRepeat(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) {}
    override fun cancelItemRepeat(checklistId: Long, fillId: Long, itemId: String) {}
}

internal class RecordingCreateLogger : AppLogger {
    val warnings = mutableListOf<String>()
    val errors = mutableListOf<String>()

    override fun debug(tag: String, message: String) {}
    override fun info(tag: String, message: String) {}
    override fun warning(tag: String, message: String) { warnings += message }
    override fun error(tag: String, message: String, throwable: Throwable?) { errors += message }
}

internal class RecordingCreateNavigator : AppNavigator {
    var backInvoked = false
    var navigatedToMainScreen = false
    var paywallSource: String? = null
    /** Non-null once the v2 arm lands in the project it just created. */
    var detailChecklistId: Long? = null

    override val backStack: NavBackStack<NavKey> = NavBackStack()

    private val _events = MutableSharedFlow<AppNavEvent>()
    override val events: SharedFlow<AppNavEvent> = _events.asSharedFlow()

    override fun showWidgetInstruction() {}
    override fun requestCreateWeeklyChecklist() {}
    override fun onBack() { backInvoked = true }
    override fun navigateToOnboarding() {}
    override fun navigateToInteractiveOnboarding() {}
    override fun navigateToWelcomeOnboarding() {}
    override fun navigateToMainScreen(clearBackStack: Boolean) { navigatedToMainScreen = true }
    override fun navigateToDebugMenu() {}
    override fun navigateToStoreScreenshot() {}
    override fun navigateToCreateChecklistScreen(templateId: Int?, initialText: String?) {}
    override fun navigateToEditChecklist(checklistId: Long) {}
    override fun navigateToTemplatesScreen() {}
    override fun navigateToTemplatePreview(templateId: String) {}
    override fun navigateToAnalyzeScreen(
        checklistId: Long?,
        fillDefault: Boolean,
        initialText: String?,
        autoAnalyze: Boolean,
    ) {}
    override fun navigateToAnalyzeResultPreview() {}
    override fun navigateToChecklistDetail(checklistId: Long, focusItemId: String?, clearBackStack: Boolean) {
        detailChecklistId = checklistId
    }
    override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {}
    override fun navigateToFillsList(checklistId: Long) {}
    override fun navigateToPaywall(source: String) { paywallSource = source }
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
    override fun navigateToAddToChecklistPicker(
        text: String,
        purpose: com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose,
    ) {}
}

internal class RecordingCreateAnalytics : AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    override fun setUserId(userId: String) {}
    override fun setUserProperties(properties: Map<String, Any>) {}
    override fun screenView(name: String) {}
    override fun event(name: String, params: Map<String, Any>) { events.add(name to params) }
}

internal class FakeCreatePaywallRepository(private val isPremium: Boolean = false) : PaywallRepository {
    override val subscriptionStatus: Flow<SubscriptionStatus> = flowOf(
        if (isPremium) {
            SubscriptionStatus(isActive = true, activeEntitlements = setOf("AiChecklists Pro"))
        } else {
            SubscriptionStatus.FREE
        }
    )

    override suspend fun getOfferings(offeringId: String): Result<PaywallOffering?> = Result.success(null)
    override suspend fun purchase(packageId: String): PurchaseResult = PurchaseResult.Error("stub")
    override suspend fun restorePurchases(): RestoreResult = RestoreResult.Error("stub")
    override suspend fun refreshSubscriptionStatus() {}
    override fun isConfigured(): Boolean = false
    override suspend fun logIn(appUserId: String): Result<LoginResult> = Result.failure(NotImplementedError())
    override suspend fun logOut(): Result<SubscriptionStatus> = Result.failure(NotImplementedError())
}

internal class FakeCreateUserDataRepository(private val isPremium: Boolean = false) : UserDataRepository {
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

/**
 * Remote config whose free-checklist limit is settable per test.
 *
 * The limit is a PARAMETER here on purpose: the whole point of the limit tests is that the number on
 * screen tracks Remote Config rather than a constant compiled into the client (the project already
 * shipped `FREE_CHECKLIST_LIMIT = 4` against an RC value of 5). A fake pinned to the default would
 * pass against a hardcoded 5 just as happily.
 */
internal class ConfigurableRemoteConfigProvider(
    private val maxChecklistsFree: Long,
) : RemoteConfigProvider {
    override suspend fun fetchAndActivate(): Boolean = true
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
    override fun getString(key: String, defaultValue: String): String = defaultValue
    override fun getLong(key: String, defaultValue: Long): Long = when (key) {
        RemoteConfigKeys.MAX_CHECKLISTS_FREE -> maxChecklistsFree
        else -> defaultValue
    }
}
