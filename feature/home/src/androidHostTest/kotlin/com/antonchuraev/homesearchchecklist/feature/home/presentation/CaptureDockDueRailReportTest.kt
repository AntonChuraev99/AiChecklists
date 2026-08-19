package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.components.QuickCaptureDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.SourceRowSection
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.DraftDueUiState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.ItemCreateReminderPreset
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.TaskCreateChipsRow
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.TaskDraft
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.withPreset
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxPage
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreenState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxTask
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayScreenState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * Report frames for R1 — the due rail wired into the two capture hosts.
 *
 * RECORDED, not verified, and written outside the golden directory on purpose: these are evidence
 * that travels with the change, and a golden would freeze whatever it first recorded — including a
 * defect — as the reference for it. The rail's own component goldens live in `core:designsystem`
 * (`DueRailScreenshotTest`) and are unaffected by this file.
 *
 * The REAL screens, not a stand-in: `InboxScreen` and `CalendarScreen` with their real
 * `AppScaffold`, their real `bottomBar` slot and their real three-tile scrim. The one thing the
 * frames cannot show is the keyboard — Robolectric reports `WindowInsets.ime = 0` — so every frame
 * here is the BEST case for height, and the 320x568 / fontScale 1.3 / RU frame is the one that has
 * to fit even so.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*CaptureDockDueRailReportTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-normal-long-notround-any-160dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureDockDueRailReportTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()
    private val defaultTimeZone: java.util.TimeZone = java.util.TimeZone.getDefault()

    /** Both screens `koinInject()` these directly; without a root the composition dies. */
    @Before
    fun startKoinRoot() {
        // UTC, so [FixedNow] means the same wall-clock time on every machine. Without it the times
        // under the planner cells shift with the build agent's zone, and two offers that resolve
        // hours apart ("Tonight", "In 1 hour") can land on the same label by coincidence — a frame
        // that reads as a bug in the arithmetic while the arithmetic is fine.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        stopKoin()
        startKoin {
            modules(
                module {
                    single<AnalyticsTracker> { NoopAnalytics }
                    single<AppLogger> { NoopLogger }
                }
            )
        }
    }

    /**
     * `Locale.setDefault` is JVM-global and Gradle reuses one JVM per test task, so an unrestored RU
     * default would silently re-render every LATER test class in Russian.
     */
    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        java.util.TimeZone.setDefault(defaultTimeZone)
        stopKoin()
    }

    // ── BEFORE ───────────────────────────────────────────────────────────────────────────────────

    /**
     * What both docks shipped until this change: the token-preview slot above a scrolling chip row,
     * with Pick time and Repeat switched OFF.
     *
     * Mounted from the same components the hosts used (the chip row is untouched by this work and
     * still serves the checklist detail screen), so the frame is the real "before" rather than a
     * drawing of it.
     */
    @Test
    fun before_dockWithChipRow_360dp_light() = shoot("before_chipRow_360dp_light") {
        LegacyDockStub()
    }

    // ── AFTER: Inbox tab ─────────────────────────────────────────────────────────────────────────

    /** Resting state: "No date" plus three one-tap offers, and the AI source row below the input. */
    @Test
    fun after_inbox_360dp_light_noDate() = shoot("after_inbox_360dp_light_noDate") {
        InboxUnderTest()
    }

    /**
     * The planner open. The AI source row must be GONE from this frame — that is the whole point of
     * the fold — and the six offers must all be on screen without scrolling.
     */
    @Test
    fun after_inbox_360dp_light_expanded() = shoot("after_inbox_360dp_light_expanded") {
        InboxUnderTest(due = DraftDueUiState(plannerExpanded = true))
    }

    /**
     * A date applied. Exactly ONE visual answer: the leading chip carries it with its own `x`, and
     * "Tomorrow" is gone from the offers beside it.
     */
    @Test
    fun after_inbox_360dp_light_dateApplied() = shoot("after_inbox_360dp_light_dateApplied") {
        InboxUnderTest(draft = tomorrowDraft())
    }

    /**
     * Important ON — the control the approved rail mock dropped and this implementation kept.
     *
     * The frame is what decides whether keeping it below the rail is acceptable: it must read as the
     * rail's own second line, not as a stray third row.
     */
    @Test
    fun after_inbox_360dp_light_important() = shoot("after_inbox_360dp_light_important") {
        InboxUnderTest(draft = TaskDraft(text = "Pay the invoice", important = true))
    }

    /** Both planes have to survive the theme swap — chip fills, cell fills, and the seam. */
    @Test
    fun after_inbox_360dp_dark_expanded() =
        shoot("after_inbox_360dp_dark_expanded", dark = true) {
            InboxUnderTest(draft = tomorrowDraft(), due = DraftDueUiState(plannerExpanded = true))
        }

    /**
     * THE ACCEPTANCE FRAME for the source-row fold.
     *
     * The narrowest supported phone, the first accessibility text step, and the locale whose labels
     * run longest, with the planner open. Measured before the fold, this configuration cut the "Link"
     * and "Voice" pills off at the window edge — and it did so with `ime = 0`, i.e. before a real
     * keyboard takes its ~250dp. Everything the dock draws must be inside the frame.
     */
    @Test
    @Config(qualifiers = "w320dp-h568dp-normal-long-notround-any-160dpi-keyshidden-nonav")
    fun after_inbox_320x568_fontScale13_ru_expanded() =
        shoot(
            name = "after_inbox_320x568_fontScale13_ru_expanded",
            fontScale = 1.3f,
            locale = Locale("ru"),
        ) {
            InboxUnderTest(draft = tomorrowDraft(), due = DraftDueUiState(plannerExpanded = true))
        }

    /** The same window and scale, planner CLOSED — the state the user types in. */
    @Test
    @Config(qualifiers = "w320dp-h568dp-normal-long-notround-any-160dpi-keyshidden-nonav")
    fun after_inbox_320x568_fontScale13_ru_collapsed() =
        shoot(
            name = "after_inbox_320x568_fontScale13_ru_collapsed",
            fontScale = 1.3f,
            locale = Locale("ru"),
        ) {
            InboxUnderTest()
        }

    // ── AFTER: Calendar tab ──────────────────────────────────────────────────────────────────────

    /**
     * The second host. Its draft arrives with a preset already applied (this tab draws the day's
     * reminders), so the leading chip carries an answer from the first frame.
     */
    @Test
    fun after_calendar_360dp_light_noDate() = shoot("after_calendar_360dp_light_default") {
        CalendarUnderTest()
    }

    /** The fold has to behave identically on both tabs — one dock, one behaviour. */
    @Test
    fun after_calendar_360dp_light_expanded() = shoot("after_calendar_360dp_light_expanded") {
        CalendarUnderTest(due = DraftDueUiState(plannerExpanded = true))
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────────────────────

    /**
     * A draft with "Tomorrow morning" applied, resolved against [FixedNow] so the label on the frame
     * is stable across runs.
     *
     * The zone is the system default rather than UTC deliberately: it is what the rail itself uses,
     * and pinning one side only would produce a frame whose chip disagrees with its own draft.
     */
    private fun tomorrowDraft(): TaskDraft = TaskDraft(text = "Pay the invoice").withPreset(
        preset = ItemCreateReminderPreset.TOMORROW_MORNING,
        now = Instant.fromEpochMilliseconds(FixedNow),
        timeZone = TimeZone.currentSystemDefault(),
    )

    @Composable
    private fun InboxUnderTest(
        draft: TaskDraft = TaskDraft(),
        due: DraftDueUiState = DraftDueUiState(),
    ) {
        InboxScreen(
            state = InboxScreenState.Content(
                pages = listOf(
                    InboxPage(
                        checklistId = 1L,
                        title = "Inbox",
                        isInbox = true,
                        tasks = listOf(task("Renew the parking permit"), task("Call the dentist")),
                    ),
                ),
                draft = draft,
                due = due,
                // Pinned so the preset labels and the chip's own text are the same on every run —
                // the rail renders against this value, not against the wall clock.
                nowMillis = FixedNow,
            ),
            contentBottomPadding = 0.dp,
            onIntent = {},
            snackbarHostState = SnackbarHostState(),
            swallowRootBack = false,
            createDockOpen = true,
            onCreateDockDismiss = {},
        )
    }

    @Composable
    private fun CalendarUnderTest(due: DraftDueUiState = DraftDueUiState()) {
        CalendarScreen(
            todayState = TodayScreenState.Empty,
            calendarState = CalendarState.Empty,
            drawerState = null,
            onTodayReminderClick = { _, _ -> },
            onTodayRetry = {},
            onCalendarIntent = {},
            // The real seed this tab uses: the day screen pre-selects a reminder so a task captured
            // here stays visible on the screen that captured it.
            draft = TaskDraft(text = "Pay the invoice").withPreset(
                preset = ItemCreateReminderPreset.TONIGHT,
                now = Instant.fromEpochMilliseconds(FixedNow),
                timeZone = TimeZone.currentSystemDefault(),
            ),
            due = due,
            captureDockOpen = true,
            captureEnabled = true,
            onAddTaskRowClick = {},
        )
    }

    /**
     * The dock as both hosts mounted it BEFORE this change — the real [TaskCreateChipsRow] with the
     * two sheet-backed chips switched off, over the real source row.
     */
    @Composable
    private fun LegacyDockStub() {
        QuickCaptureDock(
            text = "Pay the invoice",
            onTextChange = {},
            onAdd = {},
            placeholder = "Add a task…",
            aboveInput = {
                TaskCreateChipsRow(
                    draft = tomorrowDraft(),
                    onAction = {},
                    showPickTime = false,
                    showRepeat = false,
                )
            },
            belowInput = {
                SourceRowSection(title = "Or create a checklist from:", onSelect = {})
            },
        )
    }

    // ── Harness ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Renders [content] and writes the frame.
     *
     * No `waitForIdle` and no semantics lookup anywhere in this file, deliberately: the dock focuses
     * its input on mount and a focused field blinks its caret forever, so any path that first waits
     * for an idle clock never returns. Roborazzi's file capture does not wait, which is why the whole
     * suite goes through it.
     */
    private fun shoot(
        name: String,
        fontScale: Float = 1f,
        dark: Boolean = false,
        locale: Locale = Locale.ENGLISH,
        content: @Composable () -> Unit,
    ) {
        // The JVM default locale, not a Robolectric qualifier: this app's copy comes from Compose
        // Resources (`org.jetbrains.compose.resources`), which resolves `values-ru` off exactly this
        // — a qualifier-only shot renders English while claiming to be the RU frame. Window size and
        // density stay with `@Config`, where they belong.
        Locale.setDefault(locale)

        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = dark) { content() }
            }
        }

        val file = File("$ReportDir/$name.png")
        file.parentFile?.mkdirs()
        composeTestRule.onRoot().captureRoboImage(
            filePath = file.path,
            // Forced Record on a path outside the golden directory: report frames, so
            // `verifyRoborazzi*` neither compares them nor wants them in git.
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }

    private fun task(text: String) = InboxTask(
        item = ChecklistFillItem(
            text = text,
            checked = false,
            priority = 0,
            templateItemId = "template-$text",
        )
    )

    private object NoopLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    private object NoopAnalytics : AnalyticsTracker {
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
    }

    private companion object {
        /** Report frames, deliberately outside any checked-in golden directory. */
        const val ReportDir = "build/due-rail-report"

        /**
         * 2025-08-19, 10:00 UTC — a TUESDAY morning, chosen so all five offers resolve to five
         * distinct, readable labels: 18:00 / 09:00 / Sat 10:00 / 11:00 / Mon 09:00.
         *
         * A Monday would push "Next week" a full seven days out, where the formatter stops naming the
         * weekday and prints a bare date — a true label, but not the one the mock is judged against.
         */
        const val FixedNow = 1_755_597_600_000L
    }
}
