package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_error_retry
import aichecklists.core.designsystem.generated.resources.inbox_section_anytime
import aichecklists.core.designsystem.generated.resources.inbox_section_overdue
import aichecklists.core.designsystem.generated.resources.inbox_section_today
import aichecklists.core.designsystem.generated.resources.inbox_section_upcoming
import aichecklists.core.designsystem.generated.resources.plan_nudge_title
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayOptions
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.stringResource
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
import kotlin.test.assertTrue

/**
 * What the grouped Inbox actually DRAWS.
 *
 * [InboxSectionsTest] pins the grouping rule; this pins the rendering of it, and the two are not the
 * same claim. The headline invariant — "with every task undated the list looks exactly as it does
 * today" — is a statement about pixels on a screen, and a data structure carrying a null header
 * proves only half of it: a screen that rendered a heading for a null header would pass that test
 * and ship the defect.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*InboxSectionsScreenTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxSectionsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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
    fun stopKoinAfterTest() {
        stopKoin()
    }

    // ── 🔴 The invariant ─────────────────────────────────────────────────────

    /**
     * THE test of this step. For the overwhelming majority of users nothing has a date, and their
     * Inbox must be the list they already know: no heading anywhere on it.
     *
     * All four headings are checked, not just "Anytime": a wrong implementation that grouped by
     * something else would still put a heading on screen, and the claim is that there is none.
     */
    @Test
    fun inboxScreen_withEveryTaskUndated_drawsNoSectionHeadingAtAll() {
        var headings = emptyList<String>()
        composeTestRule.setContent {
            headings = sectionHeadings()
            InboxUnderTest(
                state = inboxContent(
                    tasks = listOf(
                        task("Buy bread"),
                        task("Call the dentist"),
                        task("Renew the passport"),
                    ),
                ),
            )
        }
        composeTestRule.waitForIdle()

        // Precondition: the list itself really is on screen, so "no heading" cannot be satisfied by
        // a screen that failed to compose.
        composeTestRule.onNodeWithText("Buy bread").assertIsDisplayed()

        headings.forEach { heading ->
            assertTrue(
                composeTestRule.onAllNodesWithText(heading).fetchSemanticsNodes().isEmpty(),
                "an all-undated Inbox must render no section heading; found \"$heading\"",
            )
        }
    }

    /**
     * The trap the spec names (§10 п.14): deciding on "are there dated tasks" instead of counting
     * non-empty groups puts a solitary "Overdue" over the list of a user who is simply behind.
     */
    @Test
    fun inboxScreen_withEveryTaskOverdue_stillDrawsNoSectionHeading() {
        var headings = emptyList<String>()
        composeTestRule.setContent {
            headings = sectionHeadings()
            InboxUnderTest(
                state = inboxContent(
                    tasks = listOf(
                        task("Buy bread", dueAt = NOW - HOUR),
                        task("Call the dentist", dueAt = NOW - 2 * HOUR),
                    ),
                ),
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Buy bread").assertIsDisplayed()

        headings.forEach { heading ->
            assertTrue(
                composeTestRule.onAllNodesWithText(heading).fetchSemanticsNodes().isEmpty(),
                "one non-empty group is still one group; found \"$heading\"",
            )
        }
    }

    /** Two groups is where the headings earn their space. */
    @Test
    fun inboxScreen_withOverdueAndUndatedTasks_drawsBothHeadings() {
        var overdue = ""
        var anytime = ""
        composeTestRule.setContent {
            overdue = stringResource(Res.string.inbox_section_overdue)
            anytime = stringResource(Res.string.inbox_section_anytime)
            InboxUnderTest(
                state = inboxContent(
                    tasks = listOf(
                        task("Buy bread", dueAt = NOW - HOUR),
                        task("Call the dentist"),
                    ),
                ),
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(overdue).assertIsDisplayed()
        composeTestRule.onNodeWithText(anytime).assertIsDisplayed()

        // And in that order — the overdue run is above the undated one, not merely present.
        val overdueTop = composeTestRule.onNodeWithText(overdue).fetchSemanticsNode().boundsInRoot.top
        val anytimeTop = composeTestRule.onNodeWithText(anytime).fetchSemanticsNode().boundsInRoot.top
        assertTrue(overdueTop < anytimeTop, "Overdue must be drawn above Anytime")
    }

    /** Grouping switched off is today's screen, even when the dates would produce two groups. */
    @Test
    fun inboxScreen_withGroupingOff_drawsNoHeadingEvenWithTwoGroupsWorthOfDates() {
        var headings = emptyList<String>()
        composeTestRule.setContent {
            headings = sectionHeadings()
            InboxUnderTest(
                state = inboxContent(
                    tasks = listOf(task("Buy bread", dueAt = NOW - HOUR), task("Call the dentist")),
                    displayOptions = InboxDisplayOptions(groupByDate = false),
                ),
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Buy bread").assertIsDisplayed()
        headings.forEach { heading ->
            assertTrue(
                composeTestRule.onAllNodesWithText(heading).fetchSemanticsNodes().isEmpty(),
                "grouping is off, so nothing may be grouped; found \"$heading\"",
            )
        }
    }

    // ── Plan nudge ───────────────────────────────────────────────────────────

    /**
     * Catches a button that leads nowhere. The daily review does not exist yet, so no host can pass
     * `onPlanDayClick` — and an invitation whose tap does nothing is worse than no invitation.
     */
    @Test
    fun inboxScreen_withoutAPlanDayHost_doesNotDrawTheNudge() {
        var nudgeTitle = ""
        composeTestRule.setContent {
            nudgeTitle = stringResource(Res.string.plan_nudge_title)
            InboxUnderTest(
                state = inboxContent(
                    tasks = listOf(task("a"), task("b"), task("c"), task("d")),
                ),
                onPlanDayClick = null,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("a").assertIsDisplayed()
        assertTrue(
            composeTestRule.onAllNodesWithText(nudgeTitle).fetchSemanticsNodes().isEmpty(),
            "the nudge must not be composed while nothing can handle its tap",
        )
    }

    /** Below three undated tasks there is nothing worth sitting down to plan. */
    @Test
    fun inboxScreen_withTwoUndatedTasks_doesNotDrawTheNudge() {
        var nudgeTitle = ""
        composeTestRule.setContent {
            nudgeTitle = stringResource(Res.string.plan_nudge_title)
            InboxUnderTest(
                state = inboxContent(tasks = listOf(task("a"), task("b"))),
                onPlanDayClick = {},
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("a").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText(nudgeTitle).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun inboxScreen_withThreeUndatedTasksAndAHost_drawsTheNudgeBelowTheLastTask() {
        var nudgeTitle = ""
        composeTestRule.setContent {
            nudgeTitle = stringResource(Res.string.plan_nudge_title)
            InboxUnderTest(
                state = inboxContent(tasks = listOf(task("a"), task("b"), task("c"))),
                onPlanDayClick = {},
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(nudgeTitle).assertIsDisplayed()

        // ⛔ Never at the top: up there this copy reads as a heading, and a heading phrased like an
        // invitation to catch up reads as an accusation.
        val lastTaskBottom = composeTestRule.onNodeWithText("c").fetchSemanticsNode().boundsInRoot.bottom
        val nudgeTop = composeTestRule.onNodeWithText(nudgeTitle).fetchSemanticsNode().boundsInRoot.top
        assertTrue(nudgeTop >= lastTaskBottom, "the nudge belongs at the TAIL of the undated run")
    }

    /** A recent swipe hides it for a day; the ViewModel resolves the window, the screen obeys it. */
    @Test
    fun inboxScreen_withARecentlyDismissedNudge_doesNotDrawIt() {
        var nudgeTitle = ""
        composeTestRule.setContent {
            nudgeTitle = stringResource(Res.string.plan_nudge_title)
            InboxUnderTest(
                state = inboxContent(
                    tasks = listOf(task("a"), task("b"), task("c")),
                ).copy(planNudgeDismissed = true),
                onPlanDayClick = {},
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("a").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText(nudgeTitle).fetchSemanticsNodes().isEmpty())
    }

    // ── Error state ──────────────────────────────────────────────────────────

    /**
     * Catches the defect this state exists for: before it, a failed load left the tab on a spinner
     * that never resolved, so the screen said "still working" for the rest of the session.
     */
    @Test
    fun inboxScreen_withErrorState_showsTheReasonAndRetriesOnTap() {
        var retryLabel = ""
        val raised = mutableListOf<InboxIntent>()
        composeTestRule.setContent {
            retryLabel = stringResource(Res.string.inbox_error_retry)
            InboxUnderTest(
                state = InboxScreenState.Error(message = "Something went wrong"),
                onIntent = { raised += it },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
        composeTestRule.onNodeWithText(retryLabel).performClick()
        composeTestRule.waitForIdle()

        assertTrue(
            raised.contains(InboxIntent.OnRetryLoad),
            "the retry button must raise OnRetryLoad; intents seen: $raised",
        )
    }

    /** `canRetry = false` renders the reason alone rather than a button that cannot help. */
    @Test
    fun inboxScreen_withUnretryableError_showsNoRetryButton() {
        var retryLabel = ""
        composeTestRule.setContent {
            retryLabel = stringResource(Res.string.inbox_error_retry)
            InboxUnderTest(
                state = InboxScreenState.Error(message = "Storage is full", canRetry = false),
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Storage is full").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText(retryLabel).fetchSemanticsNodes().isEmpty())
    }

    // ── Harness ──────────────────────────────────────────────────────────────

    @Composable
    private fun sectionHeadings(): List<String> = listOf(
        stringResource(Res.string.inbox_section_overdue),
        stringResource(Res.string.inbox_section_today),
        stringResource(Res.string.inbox_section_upcoming),
        stringResource(Res.string.inbox_section_anytime),
    )

    @Composable
    private fun InboxUnderTest(
        state: InboxScreenState,
        onIntent: (InboxIntent) -> Unit = {},
        onPlanDayClick: (() -> Unit)? = null,
    ) {
        AppTheme(darkTheme = false) {
            InboxScreen(
                state = state,
                contentBottomPadding = 0.dp,
                onIntent = onIntent,
                snackbarHostState = SnackbarHostState(),
                swallowRootBack = false,
                createDockOpen = false,
                onCreateDockDismiss = {},
                onPlanDayClick = onPlanDayClick,
            )
        }
    }

    private fun inboxContent(
        tasks: List<InboxTask>,
        displayOptions: InboxDisplayOptions = InboxDisplayOptions(),
    ) = InboxScreenState.Content(
        pages = listOf(InboxPage(checklistId = 1L, title = "Inbox", isInbox = true, tasks = tasks)),
        displayOptions = displayOptions,
        // Pinned. Left to `currentTimeMillis()` these assertions would pass at midday and fail near
        // midnight, and the failure would look like a bug in the grouping rule.
        nowMillis = NOW,
    )

    private fun task(text: String, dueAt: Long? = null) = InboxTask(
        item = ChecklistFillItem(text = text, checked = false).let {
            if (dueAt == null) it else it.withReminderAt(dueAt)
        },
    )

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
        const val HOUR = 60L * 60L * 1000L

        /** Midday in a DST-free zone — see [InboxSectionsTest] for why the hour is not arbitrary. */
        val NOW = LocalDateTime(2026, 8, 13, 12, 0)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
    }
}
