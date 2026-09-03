package com.antonchuraev.homesearchchecklist.feature.home.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.item_create_chip_important
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiItemCreateAction
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.DraftDueUiState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.TaskDraft
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxIntent
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxPage
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreenState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxTask
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayScreenState
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "Important" is reachable from BOTH capture tabs, in the input row, and it reports its taps.
 *
 * ## Why this exists at the host level
 * The toggle moved on 2026-09-03: from the due rail's pinned `trailing` slot into the input field's
 * trailing slot beside the "+" (owner: "кнопка добавить в избранное… очень плохо выглядит и
 * находится в плохом месте"). The COMPONENT half of that lives in `core:designsystem`
 * (`DueRailScreenshotTest`), which can only prove that a dock handed a `trailingToggle` renders it.
 *
 * What no component test can prove is that both hosts actually hand it one. That is the failure this
 * class is written against, and it is not hypothetical: this dock is deliberately ONE component
 * shared by two tabs precisely because a control wired into one screen and forgotten in the other is
 * the drift that has shipped here before. The Calendar tab is the one that gets forgotten — its
 * draft arrives pre-seeded, so it looks "already wired" at a glance.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*CaptureDockImportantToggleTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h891dp-normal-long-notround-any-160dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureDockImportantToggleTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** Both screens `koinInject()` these directly; without a root the composition dies. */
    @Before
    fun startKoinRoot() {
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

    @After
    fun stopKoinAfterTest() = stopKoin()

    @Test
    fun inboxCaptureDock_showsTheToggleAndReportsItsTap() {
        var label = ""
        val actions = mutableListOf<GistiItemCreateAction>()
        composeTestRule.setContent {
            label = stringResource(Res.string.item_create_chip_important)
            AppTheme(darkTheme = false) {
                InboxUnderTest(onAction = { actions += it })
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "the Inbox capture dock must render exactly one Important toggle",
            listOf(ToggleableState.Off),
            composeTestRule.toggleStatesOf(label),
        )

        composeTestRule.onNodeWithContentDescription(label).performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "the tap must reach the host as the IMPORTANT create-chip action",
            listOf(GistiItemCreateAction.IMPORTANT),
            actions,
        )
    }

    /** A draft that already has the flag must announce it as ON, not as a second, differently named control. */
    @Test
    fun inboxCaptureDock_announcesAnAlreadyImportantDraftAsOn() {
        var label = ""
        composeTestRule.setContent {
            label = stringResource(Res.string.item_create_chip_important)
            AppTheme(darkTheme = false) {
                InboxUnderTest(draft = TaskDraft(text = "Pay the invoice", important = true))
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(
            listOf(ToggleableState.On),
            composeTestRule.toggleStatesOf(label),
        )
    }

    /**
     * The Calendar tab, same control, same seat.
     *
     * It is also the tab whose planner state used to hide the toggle: this mounts it with the planner
     * OPEN, the one state the rail-pinned version was unreachable in.
     */
    @Test
    fun calendarCaptureDock_showsTheToggleWithThePlannerOpen_andReportsItsTap() {
        var label = ""
        val actions = mutableListOf<GistiItemCreateAction>()
        composeTestRule.setContent {
            label = stringResource(Res.string.item_create_chip_important)
            AppTheme(darkTheme = false) {
                CalendarScreen(
                    todayState = TodayScreenState.Empty,
                    calendarState = CalendarState.Empty,
                    drawerState = null,
                    onTodayReminderClick = { _, _ -> },
                    onTodayRetry = {},
                    onCalendarIntent = {},
                    draft = TaskDraft(text = "Pay the invoice"),
                    due = DraftDueUiState(plannerExpanded = true),
                    onCreateChipAction = { actions += it },
                    captureDockOpen = true,
                    captureEnabled = true,
                    onAddTaskRowClick = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "the Calendar capture dock must render the toggle too — and it must survive the planner " +
                "being open, which is the state the rail-pinned version folded away in",
            listOf(ToggleableState.Off),
            composeTestRule.toggleStatesOf(label),
        )

        composeTestRule.onNodeWithContentDescription(label).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(GistiItemCreateAction.IMPORTANT), actions)
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    @Composable
    private fun InboxUnderTest(
        draft: TaskDraft = TaskDraft(text = "Pay the invoice"),
        onAction: (GistiItemCreateAction) -> Unit = {},
    ) {
        InboxScreen(
            state = InboxScreenState.Content(
                pages = listOf(
                    InboxPage(
                        checklistId = 1L,
                        title = "Inbox",
                        isInbox = true,
                        tasks = listOf(task("Renew the parking permit")),
                    ),
                ),
                draft = draft,
                due = DraftDueUiState(),
                nowMillis = FixedNow,
            ),
            contentBottomPadding = 0.dp,
            onIntent = { intent ->
                if (intent is InboxIntent.OnCreateChipAction) onAction(intent.action)
            },
            snackbarHostState = SnackbarHostState(),
            swallowRootBack = false,
            createDockOpen = true,
            onCreateDockDismiss = {},
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
        /** 2025-08-19 10:00 UTC — pinned so the rail's labels do not move with the wall clock. */
        const val FixedNow = 1_755_597_600_000L
    }
}

/**
 * Every `SemanticsProperties.ToggleableState` carried by a node named [description], in tree order.
 *
 * A LIST, not a single lookup: "there is exactly one such control on the screen" is half the claim —
 * a host that mounted the toggle twice (once in the rail, once in the input row) would satisfy any
 * `onNodeWithContentDescription` assertion by ambiguity error only, which is a different failure.
 */
private fun ComposeContentTestRule.toggleStatesOf(description: String): List<ToggleableState?> =
    onAllNodesWithContentDescription(description).fetchSemanticsNodes().map {
        it.config.getOrElseNullable(SemanticsProperties.ToggleableState) { null }
    }
