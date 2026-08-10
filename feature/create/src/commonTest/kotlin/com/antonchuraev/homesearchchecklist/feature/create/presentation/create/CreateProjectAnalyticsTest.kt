package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.ChecklistSource
import com.antonchuraev.homesearchchecklist.core.common.api.CreateFormVariant
import com.antonchuraev.homesearchchecklist.core.common.api.CreatedListKind
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * What the create screen REPORTS, as opposed to what it does.
 *
 * The v2 "New project" form shipped emitting a `checklist_created` byte-identical to the classic
 * form's (`source = manual`, `item_count = n`) and a `recurring_limit_hit` with no params at all,
 * so neither the redesign nor its Weekly toggle could be measured — not even at 100% rollout, where
 * there is no control arm left to compare against and the payload is the only evidence.
 *
 * Every assertion here fails against that shipped code, and the two arms are asserted as a PAIR:
 * a variant param hardcoded to "v2" satisfies a single-arm test just as well as a real read does,
 * which is the same failure mode as the limit banner quoting a compiled-in number.
 *
 * Run: ./gradlew :feature:create:testAndroidHostTest --tests "*CreateProjectAnalyticsTest*"
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateProjectAnalyticsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var repository: RecordingChecklistRepository
    private lateinit var navigator: RecordingCreateNavigator
    private lateinit var analytics: RecordingCreateAnalytics

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = RecordingChecklistRepository().withChecklistCount(0)
        navigator = RecordingCreateNavigator()
        analytics = RecordingCreateAnalytics()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * @param useProjectForm the ONLY thing that differs between the arms in production
     *   (`App.kt` passes `navVariant == NavVariant.V2`), so it is the only thing a test may vary to
     *   claim the reported arm tracks the rendered form.
     */
    private fun createViewModel(
        useProjectForm: Boolean,
        analyticsTracker: RecordingCreateAnalytics = analytics,
    ): CreateChecklistViewModel =
        CreateChecklistViewModel(
            editChecklistId = null,
            initialText = null,
            checklistRepository = repository,
            appNavigator = navigator,
            analyticsTracker = analyticsTracker,
            getUserLimitsUseCase = GetUserLimitsUseCase(
                remoteConfigProvider = ConfigurableRemoteConfigProvider(
                    RemoteConfigDefaults.MAX_CHECKLISTS_FREE
                ),
                checklistRepository = repository,
                paywallRepository = FakeCreatePaywallRepository(isPremium = false),
                userDataRepository = FakeCreateUserDataRepository(isPremium = false),
            ),
            reminderScheduler = RecordingReminderScheduler(),
            logger = RecordingCreateLogger(),
            useProjectForm = useProjectForm,
        )

    private fun RecordingCreateAnalytics.singleEvent(name: String): Map<String, Any> {
        val matching = events.filter { it.first == name }
        assertEquals(1, matching.size, "Expected exactly one `$name`, got ${events.map { it.first }}")
        return matching.single().second
    }

    // ── The create itself is attributable to a form ──────────────────────────

    /**
     * Catches: a v2 create that is indistinguishable from a classic one.
     *
     * `source` is asserted UNCHANGED on purpose. Re-pointing it at a new [ChecklistSource] would
     * have made the arm visible too — and silently redefined the reference value every shipped
     * create dashboard counts, which is a worse defect than the one being fixed.
     */
    @Test
    fun createProject_fromTheV2Form_reportsTheArmWithoutMovingTheSource() = testScope.runTest {
        val viewModel = createViewModel(useProjectForm = true)

        viewModel.onIntent(CreateChecklistIntent.OnNameChange("Groceries"))
        viewModel.onIntent(CreateChecklistIntent.OnNewItemTextChange("milk"))
        viewModel.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        viewModel.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        val created = analytics.singleEvent(AnalyticsEvents.Checklist.CREATED)
        assertEquals(
            CreateFormVariant.V2.wire,
            created[AnalyticsParams.FORM_VARIANT],
            "A create from the redesigned form must say so — otherwise 1.19.0 is unmeasurable",
        )
        assertEquals(
            ChecklistSource.MANUAL.wire,
            created[AnalyticsParams.SOURCE],
            "`source` is the reference value of the live create dashboards and must not move",
        )
        assertEquals(1, created[AnalyticsParams.ITEM_COUNT], "The existing item_count must survive")
    }

    /**
     * The other half of the pair: the classic form must report the OTHER value.
     *
     * Without this, `AnalyticsParams.FORM_VARIANT to "v2"` written as a literal passes — and the
     * split stops discriminating the moment a user flips the shell back in Settings, which is
     * precisely the population the release needs to compare against at 100% rollout.
     */
    @Test
    fun createProject_fromTheClassicForm_reportsTheOtherArm() = testScope.runTest {
        val viewModel = createViewModel(useProjectForm = false)

        viewModel.onIntent(CreateChecklistIntent.OnNameChange("Groceries"))
        viewModel.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        val created = analytics.singleEvent(AnalyticsEvents.Checklist.CREATED)
        assertEquals(
            CreateFormVariant.CLASSIC.wire,
            created[AnalyticsParams.FORM_VARIANT],
            "The classic form must report its own arm, not the redesign's",
        )
        assertNotEquals(
            CreateFormVariant.V2.wire,
            created[AnalyticsParams.FORM_VARIANT],
            "A hardcoded arm would make the split useless — the two forms must differ",
        )
    }

    // ── Weekly is not "an empty project" ─────────────────────────────────────

    /**
     * Catches: a Weekly project reported exactly like an empty standard one.
     *
     * Asserted as a DIFF between two real emissions rather than as one expected value, because the
     * defect was never a wrong value — it was two different product acts producing the same payload
     * (`source = manual`, `item_count = 0`). Both of those are asserted equal here, so the test also
     * pins WHY a separate dimension was needed instead of an `item_count` heuristic.
     */
    @Test
    fun createWeeklyProject_isDistinguishableFromAnEmptyStandardCreate() = testScope.runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        val weeklyAnalytics = RecordingCreateAnalytics()
        val weeklyForm = createViewModel(useProjectForm = true, analyticsTracker = weeklyAnalytics)
        advanceUntilIdle()
        weeklyForm.onIntent(CreateChecklistIntent.OnNameChange("My week"))
        // Empty form -> the switch applies without the destructive-change confirmation.
        weeklyForm.onIntent(CreateChecklistIntent.OnWeeklyToggled(true))
        weeklyForm.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        val standardAnalytics = RecordingCreateAnalytics()
        val standardForm = createViewModel(useProjectForm = true, analyticsTracker = standardAnalytics)
        standardForm.onIntent(CreateChecklistIntent.OnNameChange("Empty project"))
        standardForm.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        val weekly = weeklyAnalytics.singleEvent(AnalyticsEvents.Checklist.CREATED)
        val standard = standardAnalytics.singleEvent(AnalyticsEvents.Checklist.CREATED)

        assertEquals(
            standard[AnalyticsParams.SOURCE],
            weekly[AnalyticsParams.SOURCE],
            "Both are the create form, so both keep source=manual — that is what made them identical",
        )
        assertEquals(
            standard[AnalyticsParams.ITEM_COUNT],
            weekly[AnalyticsParams.ITEM_COUNT],
            "Both carry item_count=0, so item_count can never be the discriminator",
        )
        assertEquals(
            CreatedListKind.WEEKLY.wire,
            weekly[AnalyticsParams.LIST_KIND],
            "The Weekly toggle must be visible in the payload it produces",
        )
        assertEquals(
            CreatedListKind.STANDARD.wire,
            standard[AnalyticsParams.LIST_KIND],
            "Emitted for BOTH kinds — an absent value cannot be counted as a share of anything",
        )
        assertEquals(
            CreateFormVariant.V2.wire,
            weekly[AnalyticsParams.FORM_VARIANT],
            "The Weekly path is a create too and must carry the arm like every other one",
        )
    }

    // ── The project ceiling is visible, and says which affordance met it ─────

    /**
     * Catches: the limit banner producing no event of its own.
     *
     * The banner is a v2-only surface whose only trace was `paywall_opened{source=checklist_limit}`
     * — a value it SHARES with the Save gate, and one that carries no arm. So the single affordance
     * the redesign added was the one create-screen path still invisible after the create event was
     * instrumented.
     */
    @Test
    fun onLimitBannerUpgradeClick_reportsTheBannerAndTheArm() = testScope.runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        repository.withChecklistCount(RemoteConfigDefaults.MAX_CHECKLISTS_FREE.toInt())

        val viewModel = createViewModel(useProjectForm = true)
        advanceUntilIdle()

        viewModel.onIntent(CreateChecklistIntent.OnLimitBannerUpgradeClick)
        advanceUntilIdle()

        val limitHit = analytics.singleEvent(AnalyticsEvents.Checklist.LIMIT_HIT)
        assertEquals(
            AnalyticsEvents.Checklist.LIMIT_SOURCE_CREATE_BANNER,
            limitHit[AnalyticsParams.SOURCE],
            "A banner tap must be separable from the Save gate — they are different acts",
        )
        assertEquals(
            CreateFormVariant.V2.wire,
            limitHit[AnalyticsParams.FORM_VARIANT],
            "The banner exists only on the redesigned form and must report it",
        )
        assertEquals(
            "checklist_limit",
            navigator.paywallSource,
            "The existing paywall source stays put — the new event is additive, not a replacement",
        )
    }

    /**
     * The other affordance for the SAME ceiling, on the OTHER arm.
     *
     * Asserted as a pair with the banner test: one source value used for both would make
     * `checklist_limit_hit` exactly as undifferentiated as the `paywall_opened` it was added to
     * disambiguate, and the classic arm proves the variant is read rather than written.
     */
    @Test
    fun onSaveClick_atTheProjectLimit_reportsTheSaveGateAndTheOtherArm() = testScope.runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        repository.withChecklistCount(RemoteConfigDefaults.MAX_CHECKLISTS_FREE.toInt())

        val viewModel = createViewModel(useProjectForm = false)
        advanceUntilIdle()

        viewModel.onIntent(CreateChecklistIntent.OnNameChange("Sixth project"))
        viewModel.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        val limitHit = analytics.singleEvent(AnalyticsEvents.Checklist.LIMIT_HIT)
        assertEquals(
            AnalyticsEvents.Checklist.LIMIT_SOURCE_CREATE_SAVE,
            limitHit[AnalyticsParams.SOURCE],
            "A refused Save is not a banner tap",
        )
        assertEquals(
            CreateFormVariant.CLASSIC.wire,
            limitHit[AnalyticsParams.FORM_VARIANT],
            "The Save gate fires in BOTH arms, so it must report which one it fired in",
        )
        assertTrue(
            analytics.events.none { it.first == AnalyticsEvents.Checklist.CREATED },
            "The ceiling refused the create — counting it in the create funnel would be a lie",
        )
    }

    // ── The recurring quota says WHERE it bit ────────────────────────────────

    /**
     * Catches: `recurring_limit_hit` with no params.
     *
     * The same constant is emitted from two places in `ChecklistDetailViewModel`, so an unqualified
     * event cannot tell "the create form's staged reminder was refused" from "the detail screen
     * refused a repeat" — the create screen's whole share of the recurring paywall was invisible.
     */
    @Test
    fun onReminderTabSelected_atTheRecurringLimit_reportsWhichScreenRefused() = testScope.runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        repository.activeRepeatScheduleCount = RemoteConfigDefaults.MAX_RECURRING_REMINDERS_FREE.toInt()

        val viewModel = createViewModel(useProjectForm = true)
        advanceUntilIdle()

        viewModel.onIntent(CreateChecklistIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        advanceUntilIdle()

        val limitHit = analytics.singleEvent(AnalyticsEvents.Reminder.RECURRING_LIMIT_HIT)
        assertEquals(
            // The shared constant, not a literal: a typo in production must redden this test rather
            // than quietly open a fourth series nobody is looking at.
            AnalyticsEvents.Reminder.LIMIT_SOURCE_CREATE_PROJECT,
            limitHit[AnalyticsParams.SOURCE],
            "The create screen's recurring-limit hits must be separable from the detail screen's",
        )
        assertEquals(
            CreateFormVariant.V2.wire,
            limitHit[AnalyticsParams.FORM_VARIANT],
            "Which form hit the ceiling is the question the release is asking",
        )
        assertEquals(
            "create_recurring_limit",
            navigator.paywallSource,
            "The existing paywall routing must be untouched by the instrumentation",
        )
    }
}
