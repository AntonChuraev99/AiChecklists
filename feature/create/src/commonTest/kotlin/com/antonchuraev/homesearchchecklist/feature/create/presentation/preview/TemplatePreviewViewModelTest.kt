package com.antonchuraev.homesearchchecklist.feature.create.presentation.preview

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.common.api.ChecklistSource
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplateCategory
import com.antonchuraev.homesearchchecklist.feature.create.domain.repository.TemplatesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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

/**
 * Covers the template-library instrumentation added 2026-07-26.
 *
 * Before it, the only signal that the (81-entry) bundled library was used at all was
 * `checklist_created` with source="template" — which fires at the very end and cannot separate
 * "nobody opens templates" from "people open them and abandon the preview". These tests pin the
 * two properties that make the previewed -> used funnel trustworthy: a preview that failed to
 * load must NOT count as a view, and `template_used` must never replace `checklist_created`.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TemplatePreviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var analytics: RecordingAnalyticsTracker

    private val template = ChecklistTemplate(
        id = "morning-routine",
        name = "Morning Routine",
        description = "Start the day right",
        icon = "sun",
        category = "Personal",
        items = listOf("Wake up", "Drink water", "Stretch"),
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        analytics = RecordingAnalyticsTracker()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(found: ChecklistTemplate? = template) = TemplatePreviewViewModel(
        templateId = found?.id ?: "missing",
        appNavigator = FakeNavigator(),
        templatesRepository = FakeTemplatesRepository(found),
        checklistRepository = FakeChecklistRepository(),
        analyticsTracker = analytics,
    )

    @Test
    fun `previewed fires once on successful load carrying slug category and size`() = runTest(testDispatcher) {
        buildViewModel()
        advanceUntilIdle()

        val previewed = analytics.events.filter { it.first == AnalyticsEvents.Template.PREVIEWED }
        assertEquals(1, previewed.size, "template_previewed must fire exactly once per load")

        val params = previewed.single().second
        assertEquals("morning-routine", params[AnalyticsParams.BUNDLED_TEMPLATE_ID])
        assertEquals("Personal", params[AnalyticsParams.TEMPLATE_CATEGORY])
        assertEquals(3, params[AnalyticsParams.ITEM_COUNT])
    }

    @Test
    fun `previewed does not fire when the template cannot be loaded`() = runTest(testDispatcher) {
        buildViewModel(found = null)
        advanceUntilIdle()

        // A template that never rendered is not a view — counting it would silently inflate the
        // previewed -> used funnel denominator and make abandonment look worse than it is.
        assertTrue(
            analytics.events.none { it.first == AnalyticsEvents.Template.PREVIEWED },
            "a failed load must not be counted as a preview",
        )
    }

    @Test
    fun `used fires alongside checklist_created never instead of it`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onIntent(TemplatePreviewScreenIntent.OnCreateChecklist)
        advanceUntilIdle()

        val names = analytics.events.map { it.first }
        assertTrue(AnalyticsEvents.Checklist.CREATED in names, "the create funnel must stay complete")
        assertTrue(AnalyticsEvents.Template.USED in names, "template dimension must be recorded")

        val created = analytics.events.first { it.first == AnalyticsEvents.Checklist.CREATED }.second
        assertEquals(ChecklistSource.TEMPLATE.wire, created[AnalyticsParams.SOURCE])

        val used = analytics.events.first { it.first == AnalyticsEvents.Template.USED }.second
        assertEquals("morning-routine", used[AnalyticsParams.BUNDLED_TEMPLATE_ID])
        assertEquals("Personal", used[AnalyticsParams.TEMPLATE_CATEGORY])
        assertEquals(false, used[AnalyticsParams.WAS_EDITED], "an untouched template is not edited")
    }

    @Test
    fun `used reports was_edited when the user changed the template first`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onIntent(TemplatePreviewScreenIntent.OnRemoveItem(0))
        viewModel.onIntent(TemplatePreviewScreenIntent.OnCreateChecklist)
        advanceUntilIdle()

        val used = analytics.events.first { it.first == AnalyticsEvents.Template.USED }.second
        assertEquals(true, used[AnalyticsParams.WAS_EDITED])
        assertEquals(2, used[AnalyticsParams.ITEM_COUNT], "size must reflect the edited list, not the template")
    }

    // NOT covered here: "every item removed -> nothing created, no `used` event". That branch
    // resolves its error text through Compose Resources (`getString`), which has no loader in a
    // plain JVM host test and throws before the assertion is reached. The guard itself is
    // pre-existing behaviour; verifying it needs an instrumented/Compose test environment.

    // ─── Fakes ────────────────────────────────────────────────────────────────

    private class FakeTemplatesRepository(private val found: ChecklistTemplate?) : TemplatesRepository {
        override suspend fun getTemplates(): List<ChecklistTemplate> = listOfNotNull(found)
        override suspend fun getTemplatesByCategory(): List<TemplateCategory> = emptyList()
        override suspend fun getTemplateById(id: String): ChecklistTemplate? = found
    }

    private class FakeChecklistRepository : ChecklistRepository {
        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
        override val weeklyChecklistCount: Flow<Int> = flowOf(0)
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
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = emptyFlow()
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = emptyFlow()
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = emptyFlow()
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
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo>> = flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment) = Unit
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) = Unit
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getWeeklyChecklistCount(): Int = 0
        override suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo> = emptyList()
    }

    private class FakeNavigator : AppNavigator {
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
        override fun navigateToAnalyzeWithInput(
            inputKind: AnalyzeInputKind,
            entrySource: AiEntrySource,
        ) = Unit

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
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
    }

    private class RecordingAnalyticsTracker : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) { events.add(name to params) }
    }
}
