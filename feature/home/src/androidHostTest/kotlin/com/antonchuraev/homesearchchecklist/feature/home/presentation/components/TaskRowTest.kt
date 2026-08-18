package com.antonchuraev.homesearchchecklist.feature.home.presentation.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.due_tomorrow
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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
 * What a task row must SAY and where a tap on it must LAND.
 *
 * The point of this row's redesign is one sentence: a task with a reminder used to look exactly like
 * a task without one. Nothing in the repo caught that — there is no golden that renders this row —
 * so the claim is asserted here, on the real composable, through the real formatter and the real
 * string table.
 *
 * The hit-zone tests are anti-regression, not new behaviour: the 30/70 split is the part of this row
 * that breaks silently. A slip of a few percent still looks correct in a screenshot while landing
 * taps on the wrong action.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*TaskRowTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskRowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val zone = TimeZone.currentSystemDefault()

    /** Tuesday 2026-05-05, 12:00 local. */
    private val now = local(2026, 5, 5, 12, 0)

    /** Wednesday 2026-05-06, 09:00 local — "Tomorrow 09:00". */
    private val tomorrowMorning = local(2026, 5, 6, 9, 0)

    private fun local(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    private fun task(
        text: String = "Call the dentist",
        checked: Boolean = false,
        priority: Int = 0,
        reminderAt: Long? = null,
    ): ChecklistFillItem {
        val base = ChecklistFillItem(text = text, checked = checked, priority = priority)
        return if (reminderAt == null) base else base.withReminderAt(reminderAt)
    }

    // ── The date is visible on the row ───────────────────────────────────────

    /**
     * Catches the defect the whole redesign exists for: before this row carried a meta line, a task
     * with a reminder was pixel-identical to one without, and the only way to find out when it was
     * due was to open the sheet.
     */
    @Test
    fun comfortableRow_withAReminder_showsTheDueLabel() {
        var expected = ""
        composeTestRule.setContent {
            expected = stringResource(Res.string.due_tomorrow, "09:00")
            RowUnderTest(item = task(reminderAt = tomorrowMorning), compact = false)
        }
        composeTestRule.waitForIdle()

        // Precondition: the row itself composed, so a missing chip cannot be confused with a blank
        // screen.
        composeTestRule.onNodeWithText("Call the dentist").assertIsDisplayed()
        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
    }

    /**
     * The compact layout has no second line, so the chip has to move into the trailing edge rather
     * than disappear. Dropping it there would re-create the original defect for every user who
     * turned compact mode on — a regression that is invisible unless it is asserted.
     */
    @Test
    fun compactRow_withAReminder_stillShowsTheDueLabel() {
        var expected = ""
        composeTestRule.setContent {
            expected = stringResource(Res.string.due_tomorrow, "09:00")
            RowUnderTest(item = task(reminderAt = tomorrowMorning), compact = true)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Call the dentist").assertIsDisplayed()
        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
    }

    /**
     * A task with no date draws no chip at all — not an empty one, not a "No date" placeholder.
     * Asserted because the neutral state costing zero pixels is a deliberate design decision, and
     * the cheapest way to break it is a chip that renders with a blank label.
     */
    @Test
    fun rowWithoutAReminder_showsNoDueChip() {
        var neverShown = ""
        composeTestRule.setContent {
            neverShown = stringResource(Res.string.due_tomorrow, "09:00")
            RowUnderTest(item = task(), compact = false)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Call the dentist").assertIsDisplayed()
        composeTestRule.onNodeWithText(neverShown).assertDoesNotExist()
    }

    // ── The 30/70 split survived the rewrite ─────────────────────────────────

    @Test
    fun tapInTheLeftThird_togglesTheCheckbox() {
        var toggledTo: Boolean? = null
        var detailsOpened = false
        composeTestRule.setContent {
            RowUnderTest(
                item = task(reminderAt = tomorrowMorning),
                compact = false,
                onCheckedChange = { toggledTo = it },
                onDetailsClick = { detailsOpened = true },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().performTouchInput {
            click(Offset(width * LeftZoneProbe, height / 2f))
        }
        composeTestRule.waitForIdle()

        assertEquals(true, toggledTo, "a tap in the left 30% must flip the checkbox")
        assertTrue(!detailsOpened, "the left zone must not open the details sheet")
    }

    @Test
    fun tapInTheRightSeventy_opensTheDetailsSheet() {
        var toggledTo: Boolean? = null
        var detailsOpened = false
        composeTestRule.setContent {
            RowUnderTest(
                item = task(reminderAt = tomorrowMorning),
                compact = false,
                onCheckedChange = { toggledTo = it },
                onDetailsClick = { detailsOpened = true },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().performTouchInput {
            click(Offset(width * RightZoneProbe, height / 2f))
        }
        composeTestRule.waitForIdle()

        assertTrue(detailsOpened, "a tap in the right 70% must open the details sheet")
        assertEquals(null, toggledTo, "the right zone must not flip the checkbox")
    }

    /**
     * The priority marker moved from a trailing star icon to a bar at the leading edge. The bar is
     * PAINTED, not laid out — if it were ever turned into a real child with a `clickable`, it would
     * eat the first 3dp of the checkbox zone and, worse, invite the same treatment for the chips.
     * This asserts the leading edge still belongs to the checkbox.
     */
    @Test
    fun priorityBar_doesNotStealTheLeadingEdge() {
        var toggledTo: Boolean? = null
        composeTestRule.setContent {
            RowUnderTest(
                item = task(priority = 1, reminderAt = tomorrowMorning),
                compact = false,
                onCheckedChange = { toggledTo = it },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().performTouchInput {
            click(Offset(LeadingEdgeProbePx, height / 2f))
        }
        composeTestRule.waitForIdle()

        assertEquals(true, toggledTo, "the leading edge is still part of the checkbox zone")
    }

    @Composable
    private fun RowUnderTest(
        item: ChecklistFillItem,
        compact: Boolean,
        onCheckedChange: (Boolean) -> Unit = {},
        onDetailsClick: () -> Unit = {},
    ) {
        AppTheme(darkTheme = false) {
            TaskRow(
                item = item,
                compact = compact,
                onCheckedChange = onCheckedChange,
                onDetailsClick = onDetailsClick,
                modifier = Modifier.fillMaxWidth(),
                nowMillis = now,
            )
        }
    }

}

/** Horizontal probe inside the 30% checkbox zone. */
private const val LeftZoneProbe = 0.15f

/** Horizontal probe inside the 70% details zone. */
private const val RightZoneProbe = 0.80f

/** A couple of pixels in from the leading edge — where the priority bar is painted. */
private const val LeadingEdgeProbePx = 2f
