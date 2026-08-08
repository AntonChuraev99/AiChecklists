package com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_add_task_row
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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

    // ── Harness ──────────────────────────────────────────────────────────────

    @Composable
    private fun CalendarUnderTest(
        contentBottomPadding: Dp,
        captureEnabled: Boolean = true,
    ) {
        AppTheme(darkTheme = false) {
            CalendarScreen(
                // Empty states on both pages: this test is about the row's geometry against the
                // screen's bottom edge, and an agenda long enough to scroll would only add noise.
                todayState = TodayScreenState.Empty,
                calendarState = CalendarState.Empty,
                drawerState = null,
                onTodayReminderClick = { _, _ -> },
                onTodayCreateChecklistClick = {},
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
