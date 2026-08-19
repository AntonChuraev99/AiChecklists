package com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.calendar_nav_label
import aichecklists.core.designsystem.generated.resources.inbox_add_task_row
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayScreenState
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Geometry of the Calendar tab's PINNED add-task row against the v2 shell's chrome.
 *
 * The shell draws its raised AI button as a SIBLING of the bottom bar, aligned to the shell's bottom
 * centre and composed AFTER the hosted screen — so its top 22dp, and the whole 76dp-wide hit zone
 * around it, sit over the last 22dp of this screen's content box. The Inbox tab is safe by
 * construction (its add-task row is the last ITEM of a list whose `contentPadding` already carries
 * the reserve), but this tab pins its row to the bottom edge of the content, which is exactly where
 * the circle is: the row's bottom third was covered, and a tap on the middle of the row opened the
 * CHAT instead of the capture dock.
 *
 * The invariant asserted here is the fix in its general form — the row keeps the host's reserve
 * clear beneath it — rather than "there is a `padding(bottom = …)` on line N", so it survives the
 * row being re-laid-out and still fails if the reserve is handed to the pager again.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*CalendarAddTaskRowTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarAddTaskRowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // ── The row clears the reserve the host asked for ────────────────────────

    /**
     * Catches the HIGH finding: the pinned row flush against the bar's top edge, under the AI
     * button's overhang.
     *
     * `nonav` in the qualifiers means zero bottom system inset, and `AppScaffold` insets its content
     * by statusBars only — so the content box ends at the root's bottom edge and the distance from
     * the row to the root bottom IS the reserve the shell asked for.
     */
    @Test
    fun calendarScreen_pinnedAddTaskRow_keepsTheHostsBottomReserveClear() {
        var addTaskLabel = ""
        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            CalendarUnderTest(contentBottomPadding = ShellReserve)
        }
        composeTestRule.waitForIdle()

        // The fixture's Today state matters here and it is one `hostsAddTaskAction()` is FALSE for —
        // see [CalendarUnderTest]. On a state it names, the pinned row is deliberately withheld and
        // the only "Add task" node on screen is the PLACEHOLDER's button, which is centred in the
        // page: the geometry assertion below would then measure a control that is nowhere near the
        // bottom edge and pass for the wrong reason, i.e. go green while the invariant it exists for
        // was never exercised.
        composeTestRule.onNodeWithText(addTaskLabel).assertIsDisplayed()

        val rootBottom = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val rowBottom = composeTestRule.onNodeWithText(addTaskLabel)
            .fetchSemanticsNode().boundsInRoot.bottom
        val reservePx = with(composeTestRule.density) { ShellReserve.toPx() }

        assertTrue(
            rootBottom - rowBottom >= reservePx,
            "The pinned add-task row must leave the host's reserve (${ShellReserve}) clear below " +
                "it — that band is where the shell's raised AI button and its hit zone are drawn. " +
                "Row bottom=$rowBottom, content bottom=$rootBottom, gap=${rootBottom - rowBottom}px, " +
                "required=${reservePx}px",
        )
    }

    // ── The row is a COMPACT affordance ──────────────────────────────────────

    /**
     * At rail / permanent-drawer width the shell still renders its own "+" (`showCreateFab` covers
     * this tab), so a row here would be a second door to one action on one screen — and it would
     * make the shell's own rationale for keeping that button ("without it there is no way to add a
     * task at rail width at all") false.
     *
     * Fails on the pre-review build, where `captureEnabled` alone decided the row and the window
     * size was never consulted.
     */
    @Test
    @Config(qualifiers = "w840dp-h1024dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun calendarScreen_atDrawerWidth_leavesCaptureToTheShellsOwnButton() {
        var addTaskLabel = ""
        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            CalendarUnderTest(contentBottomPadding = 0.dp)
        }
        composeTestRule.waitForIdle()

        assertEquals(
            0,
            composeTestRule.onAllNodesWithText(addTaskLabel).fetchSemanticsNodes().size,
            "The inline add-task row belongs to Compact only: at rail and drawer width the shell " +
                "keeps its floating \"+\", and two affordances for one action on one screen is the " +
                "defect, not the feature",
        )
    }

    // ── The control arm draws no row at all ──────────────────────────────────

    /** `captureEnabled = false` is the classic layout, and it is read before the window size. */
    @Test
    fun calendarScreen_withCaptureDisabled_drawsNoRow() {
        var addTaskLabel = ""
        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            CalendarUnderTest(contentBottomPadding = 0.dp, captureEnabled = false)
        }
        composeTestRule.waitForIdle()

        assertEquals(
            0,
            composeTestRule.onAllNodesWithText(addTaskLabel).fetchSemanticsNodes().size,
            "The control arm must not gain a capture row",
        )
    }

    // ── One action, one control — on the CALENDAR page too ───────────────────

    /**
     * The Calendar page's own empty state raises the capture dock now (it used to offer "Create
     * Checklist" and navigate twice). That makes it the same action as the pinned row, so the row has
     * to stand down for it exactly as it does for the Today page's placeholder.
     *
     * Fails on the pre-change build with TWO nodes: `pageHostsAddTask` was scoped to page 0, and the
     * comment beside it asserted page 1 "keeps its own Create Checklist CTA, which is a different
     * action" — true until this change, false after it.
     *
     * The Today page is pinned to `Success` on purpose: a state that hosts NO action of its own, so
     * the count this asserts can only come from the row and the Calendar page's button.
     */
    @Test
    fun calendarScreen_onCalendarPage_withEmptyAgenda_hasExactlyOneAddTaskControl() {
        var addTaskLabel = ""
        var calendarTabLabel = ""
        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            calendarTabLabel = stringResource(Res.string.calendar_nav_label)
            CalendarUnderTest(
                contentBottomPadding = 0.dp,
                todayState = TodayScreenState.Success(
                    dateLabel = "Tuesday, May 6",
                    pastDue = emptyList(),
                    today = emptyList(),
                ),
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText(calendarTabLabel)
            // "Calendar" is on screen TWICE — the top-bar title and the tab. Only the tab is
            // clickable, so that is the disambiguator; an index would silently follow a re-order.
            .filterToOne(hasClickAction())
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            1,
            composeTestRule.onAllNodesWithText(addTaskLabel).fetchSemanticsNodes().size,
            "On the settled Calendar page exactly one control may be named \"$addTaskLabel\": the " +
                "page's placeholder button and the pinned row are ONE action, and two nodes with one " +
                "accessible name are ambiguous to a screen reader and to every UI test matching it",
        )
    }

    /**
     * The other half of the same gate: with an agenda on screen the Calendar page hosts no button, so
     * the pinned row must come back. Without this, hiding the row unconditionally on page 1 would pass
     * the test above and silently delete this tab's only capture route on Compact.
     */
    @Test
    fun calendarScreen_onCalendarPage_withAnAgenda_keepsThePinnedRow() {
        var addTaskLabel = ""
        var calendarTabLabel = ""
        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            calendarTabLabel = stringResource(Res.string.calendar_nav_label)
            CalendarUnderTest(
                contentBottomPadding = 0.dp,
                todayState = TodayScreenState.Success(
                    dateLabel = "Tuesday, May 6",
                    pastDue = emptyList(),
                    today = emptyList(),
                ),
                calendarState = CalendarState.Content(agenda = emptyList()),
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText(calendarTabLabel)
            // "Calendar" is on screen TWICE — the top-bar title and the tab. Only the tab is
            // clickable, so that is the disambiguator; an index would silently follow a re-order.
            .filterToOne(hasClickAction())
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            1,
            composeTestRule.onAllNodesWithText(addTaskLabel).fetchSemanticsNodes().size,
            "A Calendar page that draws an agenda hosts no add-task button of its own, so the pinned " +
                "row is this tab's only capture route on Compact and must be on screen",
        )
    }

    // ── Harness ──────────────────────────────────────────────────────────────

    @Composable
    private fun CalendarUnderTest(
        contentBottomPadding: Dp,
        captureEnabled: Boolean = true,
        todayState: TodayScreenState = TodayScreenState.Error,
        calendarState: CalendarState = CalendarState.Empty,
    ) {
        AppTheme(darkTheme = false) {
            CalendarScreen(
                // `Error`, not `Empty` and no longer `NoChecklists`. This fixture needs a Today page
                // that is a placeholder with no agenda to scroll AND still draws the PINNED row — i.e.
                // a state `hostsAddTaskAction()` returns false for, since the states it names host the
                // action inside the placeholder instead and the row stands down for them. That used to
                // include `NoChecklists`; on 2026-08-19 its "Create Checklist" CTA became the add-task
                // action, so it moved to the true side and this fixture moved with it. `Error` keeps
                // the property that made `NoChecklists` the sharper choice: its placeholder carries a
                // CTA of its OWN ("Retry"), so the pinned row has to coexist with a button rather than
                // merely with an illustration.
                todayState = todayState,
                calendarState = calendarState,
                drawerState = null,
                onTodayReminderClick = { _, _ -> },
                onTodayRetry = {},
                onCalendarIntent = {},
                contentBottomPadding = contentBottomPadding,
                captureEnabled = captureEnabled,
                onAddTaskRowClick = {},
            )
        }
    }

    /**
     * The v2 shell's reserve, mirrored as a literal because `V2ShellMetrics` lives in `composeApp`
     * and a feature module cannot see it. The exact number does not matter to the assertion — it
     * checks that whatever the host asks for is honoured — so this cannot drift into a false pass.
     */
    private val ShellReserve = 30.dp
}
