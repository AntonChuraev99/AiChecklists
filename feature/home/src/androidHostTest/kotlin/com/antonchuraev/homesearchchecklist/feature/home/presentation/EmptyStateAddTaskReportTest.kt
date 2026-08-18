package com.antonchuraev.homesearchchecklist.feature.home.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_add_task_row
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailEmptyState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxPage
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreenState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxTask
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayBody
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayScreenState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The screenshot matrix for ONE change: the add-task action moving INSIDE the empty-state placeholder
 * on every screen that can create a task (owner request, 2026-08-17 — the Calendar page's own CTA was
 * the named reference).
 *
 * ## What each frame has to show
 *  - the button is part of the PLACEHOLDER (under its description, inside the illustration block), not
 *    a separate control floating below or beside it;
 *  - there is exactly ONE add-task control on screen — the trailing row / the FAB stands down. Two
 *    controls would both be named "Add task", which is ambiguous to a screen reader and to any UI test
 *    matching that label;
 *  - nothing clips or overflows at `fontScale 1.5` on a 320dp window, which is where a button added
 *    under two paragraphs of centred text runs out of room first.
 *
 * ## before / after pairs
 * [TodayBody] and [ChecklistDetailEmptyState] both take a NULLABLE add-task callback whose null case is
 * documented to reproduce the previous rendering exactly, so this class shoots both sides of the change
 * from one run — `*_before` passes null, `*_after` passes a lambda. No stashing, and the pair is
 * guaranteed to differ only in the thing under review. The Inbox's "before" is the committed golden
 * `InboxAiSourceRowTest.shot_emptyInbox_withSourceRow.png` at the previous commit, because its old
 * rendering (a separate `prominent` row) is no longer expressible.
 *
 * ## Why this is not a golden test
 * Every capture forces [RoborazziTaskType.Record] and writes to `build/ux-report/`, so
 * `verifyRoborazziAndroidHostTest` never compares these files and `recordRoborazziAndroidHostTest`
 * never enrols them as expectations. Same contract as [inbox.InboxVisualReportTest] — see its KDoc.
 *
 * Run — one PNG per test lands in `feature/home/build/ux-report`:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*EmptyStateAddTaskReportTest*"
 *
 * ⛔ Do not write a glob into this KDoc: Kotlin block comments nest, so a slash-star inside one opens a
 * second comment and swallows the rest of the file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmptyStateAddTaskReportTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()

    /** [InboxScreen] `koinInject()`s both of these, so a Koin root has to exist to compose at all. */
    @Before
    fun startKoinWithNoopCollaborators() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single<AnalyticsTracker> { NoopAnalyticsTracker }
                    single<AppLogger> { NoopAppLogger }
                }
            )
        }
    }

    @After
    fun restoreEnvironment() {
        Locale.setDefault(defaultLocale)
        stopKoin()
    }

    // ── 1. Inbox: empty page ─────────────────────────────────────────────────

    @Test
    fun inboxEmpty_412dp_light() = captureInbox("inbox_empty_412_light", PHONE)

    @Test
    fun inboxEmpty_412dp_dark() = captureInbox("inbox_empty_412_dark", PHONE, dark = true)

    /** The tightest frame in the matrix: narrowest supported phone at a large accessibility scale. */
    @Test
    fun inboxEmpty_320dp_fontScale15() =
        captureInbox("inbox_empty_320_fs150", SMALL_PHONE, fontScale = 1.5f)

    /** A project page, whose placeholder is the OTHER branch of the same `when`. */
    @Test
    fun inboxEmptyProject_412dp_light() =
        captureInbox("inbox_empty_project_412_light", PHONE, isInbox = false)

    /** Sanity: with tasks the trailing row is back and the placeholder is gone. */
    @Test
    fun inboxWithTasks_412dp_light() =
        captureInbox("inbox_withtasks_412_light", PHONE, tasks = listOf("Buy bread", "Call the vet"))

    // ── 2. Today body: "All clear" ───────────────────────────────────────────

    @Test
    fun todayEmpty_before_412dp_light() =
        captureToday("today_empty_412_light_BEFORE", PHONE, withAction = false)

    @Test
    fun todayEmpty_after_412dp_light() =
        captureToday("today_empty_412_light_AFTER", PHONE, withAction = true)

    @Test
    fun todayEmpty_after_412dp_dark() =
        captureToday("today_empty_412_dark_AFTER", PHONE, withAction = true, dark = true)

    @Test
    fun todayEmpty_before_320dp_fontScale15() =
        captureToday("today_empty_320_fs150_BEFORE", SMALL_PHONE, withAction = false, fontScale = 1.5f)

    @Test
    fun todayEmpty_after_320dp_fontScale15() =
        captureToday("today_empty_320_fs150_AFTER", SMALL_PHONE, withAction = true, fontScale = 1.5f)

    /** "You're all done" — the other state the shared predicate hands the action to. */
    @Test
    fun todayAllDone_after_412dp_light() =
        captureToday(
            "today_alldone_412_light_AFTER",
            PHONE,
            withAction = true,
            state = TodayScreenState.AllDone,
        )

    /**
     * `NoChecklists` already owns a "Create Checklist" CTA, so the predicate must NOT stack a second
     * button under it. The frame is the proof that it does not.
     */
    @Test
    fun todayNoChecklists_keepsItsOwnCta() =
        captureToday(
            "today_nochecklists_412_light",
            PHONE,
            withAction = true,
            state = TodayScreenState.NoChecklists,
        )

    // ── 3. The Calendar tab as a whole ───────────────────────────────────────

    /**
     * The INTEGRATION frame, and the one that would catch the defect this change could introduce: the
     * placeholder's button AND the pinned row on one screen. It also asserts the count, so a frame
     * nobody looks at still fails.
     */
    @Test
    fun calendarTab_todayEmpty_412dp_light() =
        captureCalendarTab("calendar_today_empty_412_light", PHONE, expectedAddTaskControls = 1)

    @Test
    fun calendarTab_todayEmpty_320dp_fontScale15() =
        captureCalendarTab(
            "calendar_today_empty_320_fs150",
            SMALL_PHONE,
            fontScale = 1.5f,
            expectedAddTaskControls = 1,
        )

    /**
     * Loading is NOT one of the states the placeholder claims, so the pinned row must still be the one
     * control on screen — the negative half of the predicate.
     */
    @Test
    fun calendarTab_todayLoading_keepsThePinnedRow() =
        captureCalendarTab(
            "calendar_today_loading_412_light",
            PHONE,
            todayState = TodayScreenState.Loading,
            expectedAddTaskControls = 1,
        )

    // ── 4. Checklist detail: empty level (v2 arm) ────────────────────────────

    @Test
    fun detailEmpty_before_412dp_light() =
        captureDetailEmpty("detail_empty_412_light_BEFORE", PHONE, withAction = false)

    @Test
    fun detailEmpty_after_412dp_light() =
        captureDetailEmpty("detail_empty_412_light_AFTER", PHONE, withAction = true)

    @Test
    fun detailEmpty_after_412dp_dark() =
        captureDetailEmpty("detail_empty_412_dark_AFTER", PHONE, withAction = true, dark = true)

    @Test
    fun detailEmpty_before_320dp_fontScale15() =
        captureDetailEmpty(
            "detail_empty_320_fs150_BEFORE",
            SMALL_PHONE,
            withAction = false,
            fontScale = 1.5f,
        )

    @Test
    fun detailEmpty_after_320dp_fontScale15() =
        captureDetailEmpty(
            "detail_empty_320_fs150_AFTER",
            SMALL_PHONE,
            withAction = true,
            fontScale = 1.5f,
        )

    // ── Harness ──────────────────────────────────────────────────────────────

    private fun captureInbox(
        name: String,
        qualifiers: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        isInbox: Boolean = true,
        tasks: List<String> = emptyList(),
    ) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.setContent {
            Harness(dark = dark, fontScale = fontScale) {
                InboxScreen(
                    state = InboxScreenState.Content(
                        pages = listOf(
                            InboxPage(
                                checklistId = 1L,
                                title = if (isInbox) "Inbox" else "Renovation",
                                isInbox = isInbox,
                                tasks = tasks.map { text ->
                                    InboxTask(
                                        item = ChecklistFillItem(
                                            text = text,
                                            checked = false,
                                            priority = 0,
                                            templateItemId = "template-$text",
                                        )
                                    )
                                },
                            ),
                        ),
                    ),
                    contentBottomPadding = 0.dp,
                    onIntent = {},
                    snackbarHostState = SnackbarHostState(),
                    swallowRootBack = false,
                    createDockOpen = false,
                    onCreateDockDismiss = {},
                )
            }
        }
        capture(name)
    }

    private fun captureToday(
        name: String,
        qualifiers: String,
        withAction: Boolean,
        dark: Boolean = false,
        fontScale: Float = 1f,
        state: TodayScreenState = TodayScreenState.Empty,
    ) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.setContent {
            Harness(dark = dark, fontScale = fontScale) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    TodayBody(
                        state = state,
                        onReminderClick = { _, _ -> },
                        onCreateChecklistClick = {},
                        onRetry = {},
                        // The v2 Calendar tab is the only host that passes true, and it is the host
                        // this change is about — the capture-aware copy is the one on screen there.
                        canCapture = true,
                        onAddTaskClick = if (withAction) ({}) else null,
                    )
                }
            }
        }
        capture(name)
    }

    private fun captureCalendarTab(
        name: String,
        qualifiers: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        todayState: TodayScreenState = TodayScreenState.Empty,
        expectedAddTaskControls: Int,
    ) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        var addTaskLabel = ""
        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            Harness(dark = dark, fontScale = fontScale) {
                CalendarScreen(
                    todayState = todayState,
                    calendarState = CalendarState.Empty,
                    drawerState = null,
                    onTodayReminderClick = { _, _ -> },
                    onTodayCreateChecklistClick = {},
                    onTodayRetry = {},
                    onCalendarIntent = {},
                    contentBottomPadding = 0.dp,
                    captureEnabled = true,
                    onAddTaskRowClick = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        // A frame nobody opens still has to fail: this is the count the whole change hinges on.
        assertEquals(
            "$name: exactly $expectedAddTaskControls control named \"$addTaskLabel\" may be on screen " +
                "— the placeholder's button and the pinned row are one action, and two nodes with one " +
                "accessible name are ambiguous to a screen reader and to every UI test matching it",
            expectedAddTaskControls,
            composeTestRule.onAllNodesWithText(addTaskLabel).fetchSemanticsNodes().size,
        )
        capture(name)
    }

    private fun captureDetailEmpty(
        name: String,
        qualifiers: String,
        withAction: Boolean,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.setContent {
            Harness(dark = dark, fontScale = fontScale) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    ChecklistDetailEmptyState(
                        onAddTaskClick = if (withAction) ({}) else null,
                    )
                }
            }
        }
        capture(name)
    }

    @Composable
    private fun Harness(dark: Boolean, fontScale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            // Provided rather than set through device qualifiers: the activity is already launched by
            // the time a test body runs, so a qualifier change would not reach its Configuration.
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
        ) {
            AppTheme(darkTheme = dark) { content() }
        }
    }

    private fun capture(name: String) {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "$REPORT_DIR/$name.png",
            roborazziOptions = REPORT_OPTIONS,
        )
    }

    private object NoopAppLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    private object NoopAnalyticsTracker : AnalyticsTracker {
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
    }

    private companion object {
        /** Outside `src/**/roborazzi` — see the class KDoc. */
        const val REPORT_DIR = "build/ux-report"

        /**
         * Forced Record. Without it these captures would be compared by
         * `verifyRoborazziAndroidHostTest` against files that are not in git.
         */
        val REPORT_OPTIONS = RoborazziOptions(taskType = RoborazziTaskType.Record)

        /** A current-generation phone. `nonav` = zero bottom inset, so nothing is hidden by chrome. */
        const val PHONE = "w412dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav"

        /** The narrowest supported phone — where a centred button under two paragraphs runs out first. */
        const val SMALL_PHONE = "w320dp-h568dp-normal-long-notround-any-420dpi-keyshidden-nonav"
    }
}
