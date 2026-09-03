package com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.calendar_nav_label
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayScreenState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The week grid's day tile must CONTAIN what it draws.
 *
 * ## The defect this pins
 * `WeekGridCell` sized itself with `Modifier.aspectRatio(1f)` — height pinned to width, and width is
 * a seventh of the SCREEN, i.e. a dp. Everything inside is measured in sp and grows with the system
 * font scale, and the tile's own `clip()` cut the difference away silently:
 *
 * - 411dp, fontScale 1.0 — the 6dp reminder dot rendered as a dash welded to the bottom edge;
 * - 360dp, fontScale 1.0 — the dot disappeared altogether;
 * - 360dp, fontScale 1.3 — the DAY NUMBER was sliced through the middle.
 *
 * Nothing in `WeekGridCell` changed on this branch (it is byte-identical to `675f6408`); the recut
 * type scale spent the last dp of an already-impossible budget. Which is the point: a tile whose
 * height is fixed in dp while its content is measured in sp was always one type tweak away from
 * this, so the assertion below is about the GEOMETRY, not about any one font size.
 *
 * ## Why a test tag
 * The invariant is "the content lies inside the tile's box", and the tile's box is not reachable any
 * other way: the cell carries no merged semantics, and a text node's bounds cannot tell you where
 * its parent's top edge is.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*WeekGridCellFitTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekGridCellFitTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // ── The tile fits its content ────────────────────────────────────────────

    @Test
    fun weekGridCell_atDefaultFontScale_containsItsDayNumberAndMarker() = assertTilesFitTheirContent(1.0f)

    /**
     * The size the day number was cut in half at. `fontScale 1.3` is a normal accessibility setting,
     * not an extreme — and per `AppDensity`'s rule a comfortable tile is allowed to GROW here, never
     * to clip.
     */
    @Test
    fun weekGridCell_atLargeFontScale_containsItsDayNumberAndMarker() = assertTilesFitTheirContent(1.3f)

    @Test
    @Config(qualifiers = "w360dp-h780dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun weekGridCell_onANarrowPhone_containsItsDayNumberAndMarker() = assertTilesFitTheirContent(1.0f)

    @Test
    @Config(qualifiers = "w360dp-h780dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun weekGridCell_onANarrowPhoneAtLargeFontScale_containsItsDayNumberAndMarker() =
        assertTilesFitTheirContent(1.3f)

    // ── The seven tiles stay one even row ────────────────────────────────────

    /**
     * Growing instead of clipping is only correct if the cells grow TOGETHER — a row of tiles at two
     * different heights would be a worse defect than the one being fixed. They share one anatomy, so
     * they measure alike; this pins that rather than trusting it.
     */
    @Test
    @Config(qualifiers = "w360dp-h780dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun weekGrid_atLargeFontScale_keepsAllSevenTilesTheSameHeight() {
        showCalendarTab(fontScale = 1.3f)

        val heights = composeTestRule.onAllNodesWithTag(WeekGridCellTestTag)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.height }

        assertEquals(7, heights.size, "The week grid must draw seven day tiles")
        assertTrue(
            heights.all { kotlin.math.abs(it - heights.first()) < 1f },
            "All seven day tiles must share one height, otherwise the row reads as broken: $heights",
        )
    }

    // ── Assertion ────────────────────────────────────────────────────────────

    /**
     * For every tile: the weekday letter and the day number lie inside the tile's box, and the marker
     * slot below the number (4dp gap + the 6dp dot) still fits above the tile's bottom padding.
     *
     * The marker itself is a bare `Box` with no semantics, so its ROOM is asserted rather than its
     * node — which is the same thing here, and does not require tagging a decoration.
     */
    private fun assertTilesFitTheirContent(fontScale: Float) {
        showCalendarTab(fontScale = fontScale)

        val density = composeTestRule.density
        val padding = with(density) { AppDimens.SpacingXs.toPx() }
        val markerSlot = with(density) { (MarkerGap + MarkerSize).toPx() }

        val tiles = composeTestRule.onAllNodesWithTag(WeekGridCellTestTag).fetchSemanticsNodes()
        assertEquals(7, tiles.size, "The week grid must draw seven day tiles")

        val monday = mondayOfCurrentWeek()
        tiles.forEachIndexed { index, tile ->
            val box = tile.boundsInRoot
            val dayNumber = monday.plus(index, DateTimeUnit.DAY).dayOfMonth.toString()

            // UNMERGED, and that is load-bearing: `clickable` merges its descendants, so on an
            // interactive tile (today, or a day carrying a reminder) the merged node CARRIES the day
            // number and has the tile's own bounds. Matched on the merged tree, the assertion below
            // would compare the tile against itself and fail no matter how the tile is sized.
            val numberBounds = composeTestRule
                .onAllNodesWithText(dayNumber, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull { it.boundsInRoot.left >= box.left && it.boundsInRoot.right <= box.right }
                ?.boundsInRoot
                ?: error("Day $dayNumber is not drawn inside tile #$index ($box) at all")

            assertTrue(
                numberBounds.top >= box.top,
                "Tile #$index clips the top of day $dayNumber: number top=${numberBounds.top}, " +
                    "tile top=${box.top}. A day tile must grow, not cut its own number.",
            )
            assertTrue(
                numberBounds.bottom + markerSlot + padding <= box.bottom + Tolerance,
                "Tile #$index has no room left for the reminder marker under day $dayNumber: " +
                    "number bottom=${numberBounds.bottom}, needs ${markerSlot + padding}px more, " +
                    "tile bottom=${box.bottom}. This is the dot rendering as a dash on the tile's " +
                    "edge — or vanishing.",
            )
        }
    }

    // ── Harness ──────────────────────────────────────────────────────────────

    /** Composes the Calendar tab and switches the pager to page 1, where the week grid lives. */
    private fun showCalendarTab(fontScale: Float) {
        var calendarTabLabel = ""
        composeTestRule.setContent {
            calendarTabLabel = stringResource(Res.string.calendar_nav_label)
            Harness(fontScale = fontScale) {
                CalendarScreen(
                    todayState = TodayScreenState.Empty,
                    calendarState = CalendarState.Content(agenda = agendaFixture()),
                    drawerState = null,
                    onTodayReminderClick = { _, _ -> },
                    onTodayRetry = {},
                    onCalendarIntent = {},
                    contentBottomPadding = 0.dp,
                    captureEnabled = false,
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText(calendarTabLabel).onFirst().performClick()
        composeTestRule.waitForIdle()
    }

    @Composable
    private fun Harness(fontScale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
        ) {
            AppTheme(darkTheme = false) { content() }
        }
    }

    /**
     * The grid derives its week from the SYSTEM clock inside the composable, so the fixture and the
     * expected day numbers are both derived from the same real "today" rather than a pinned instant.
     */
    private fun mondayOfCurrentWeek(): LocalDate {
        val today = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        return today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
    }

    /** Reminders on today and on two neighbours, so tiles with and without the marker are both drawn. */
    private fun agendaFixture(): List<AgendaItem> {
        val now = System.currentTimeMillis()
        val todayEpochDay = Instant.fromEpochMilliseconds(now)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toLong()
        return listOf(
            AgendaItem.DateHeader(epochDay = todayEpochDay, label = "Today"),
            AgendaItem.ReminderRow(
                TodayReminderInfo.ItemLevel(
                    checklistId = 1L,
                    checklistName = "Errands",
                    fillId = 1L,
                    itemId = "i1",
                    itemText = "Pick up the parcel",
                    reminderAt = now,
                    isRecurring = false,
                ),
            ),
            AgendaItem.DateHeader(epochDay = todayEpochDay + 1, label = "Tomorrow"),
            AgendaItem.ReminderRow(
                TodayReminderInfo.ChecklistLevel(
                    checklistId = 2L,
                    checklistName = "Evening routine",
                    reminderAt = now + DAY,
                    isRecurring = true,
                ),
            ),
        )
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L

        /** The literals `WeekGridCell` lays its marker slot out with. */
        val MarkerGap = 4.dp
        val MarkerSize = 6.dp

        /** One pixel of rounding slack — the assertion is about a clipped shape, not sub-pixel drift. */
        const val Tolerance = 1f
    }
}
