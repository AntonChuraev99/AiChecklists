package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.calendar_nav_label
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.AgendaItem
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayBody
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayReminderItem
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayScreenState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.test.assertTrue

/**
 * A reminder row's PRIMARY line is the task's name, and it gets two lines before it truncates.
 *
 * The name is the only thing that identifies a row. Under it, on its own full line, sits the
 * supporting text (parent checklist + time) — which means nothing on its own. At `maxLines = 1` the
 * cheaper half of that budget was the one being protected: RU copy runs ~30% longer than EN, and at
 * `fontScale 1.3` the per-line budget halves again, so a perfectly ordinary task name truncated
 * mid-word ("Позвонить в поликлинику и запис…") while the subtitle beneath it sat comfortably.
 *
 * Two surfaces render this row from two composables that must not drift — `TodayReminderRow` on the
 * Today page and `CalendarReminderRow` in the Calendar agenda — so both are shot and both are
 * asserted.
 *
 * ## The assertion
 * Each frame renders a SHORT name and a LONG one in the same list, at the same size and scale, and
 * asserts the long one is taller. That is the wrap itself, measured, rather than "line 593 says 2":
 * it survives the row being re-laid-out, and it goes red on `maxLines = 1`, where both names occupy
 * exactly one line and the heights are equal.
 *
 * ## Why this is not a golden test
 * Every capture forces [RoborazziTaskType.Record] into `build/ux-report/`, so
 * `verifyRoborazziAndroidHostTest` never compares these files and `recordRoborazziAndroidHostTest`
 * never enrols them. Same contract as the other `*ReportTest` classes in this package.
 *
 * Run — one PNG per test into `feature/home/build/ux-report`:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*ReminderRowNameWrapReportTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReminderRowNameWrapReportTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    // ── Today page ───────────────────────────────────────────────────────────

    @Test
    fun todayRow_ru_fs130_411dp() =
        captureToday("row_today_411_ru_fs130", PHONE_411, locale = RU, fontScale = 1.3f)

    @Test
    fun todayRow_ru_fs100_411dp() =
        captureToday("row_today_411_ru_fs100", PHONE_411, locale = RU)

    /** The narrowest phone at the largest scale — where the budget runs out first. */
    @Test
    fun todayRow_ru_fs130_360dp() =
        captureToday("row_today_360_ru_fs130", PHONE_360, locale = RU, fontScale = 1.3f)

    @Test
    fun todayRow_en_fs130_411dp_dark() =
        captureToday("row_today_411_en_fs130_dark", PHONE_411, fontScale = 1.3f, dark = true)

    // ── Calendar agenda ──────────────────────────────────────────────────────

    @Test
    fun calendarRow_ru_fs130_411dp() =
        captureCalendarAgenda("row_calendar_411_ru_fs130", PHONE_411, locale = RU, fontScale = 1.3f)

    @Test
    fun calendarRow_ru_fs130_360dp() =
        captureCalendarAgenda("row_calendar_360_ru_fs130", PHONE_360, locale = RU, fontScale = 1.3f)

    // ── Harness ──────────────────────────────────────────────────────────────

    private fun captureToday(
        name: String,
        qualifiers: String,
        locale: Locale = EN,
        fontScale: Float = 1f,
        dark: Boolean = false,
    ) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        Locale.setDefault(locale)
        composeTestRule.setContent {
            Harness(dark = dark, fontScale = fontScale) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    TodayBody(
                        state = TodayScreenState.Success(
                            dateLabel = "Tuesday, May 6",
                            pastDue = emptyList(),
                            today = listOf(
                                todayItem(id = "short", text = SHORT_NAME),
                                todayItem(id = "long", text = LONG_NAME),
                            ),
                        ),
                        onReminderClick = { _, _ -> },
                        onRetry = {},
                    )
                }
            }
        }
        capture(name)
        assertLongNameIsTaller(name)
    }

    private fun captureCalendarAgenda(
        name: String,
        qualifiers: String,
        locale: Locale = EN,
        fontScale: Float = 1f,
        dark: Boolean = false,
    ) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        Locale.setDefault(locale)
        var calendarTabLabel = ""
        composeTestRule.setContent {
            calendarTabLabel = stringResource(Res.string.calendar_nav_label)
            Harness(dark = dark, fontScale = fontScale) {
                CalendarScreen(
                    // Page 1 is the surface under test; the harness taps across to it below. Page 0
                    // is pinned to Loading so nothing on it can answer to the names being measured.
                    todayState = TodayScreenState.Loading,
                    calendarState = CalendarState.Content(
                        agenda = listOf(
                            AgendaItem.DateHeader(epochDay = 0L, label = "Today"),
                            agendaRow(id = "short", text = SHORT_NAME),
                            agendaRow(id = "long", text = LONG_NAME),
                        ),
                    ),
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
        // The tab label is on screen twice — the top-bar title and the tab itself. Only the tab is
        // clickable, so that is the disambiguator; an index would silently follow a re-order.
        composeTestRule.onAllNodesWithText(calendarTabLabel)
            .filterToOne(hasClickAction())
            .performClick()
        composeTestRule.waitForIdle()
        capture(name)
        assertLongNameIsTaller(name)
    }

    /**
     * The wrap, measured: same width, same scale, same style — the only difference is the length of
     * the string, so a taller node can only mean a second line. Equal heights = `maxLines = 1`.
     */
    private fun assertLongNameIsTaller(name: String) {
        composeTestRule.waitForIdle()
        val shortHeight = composeTestRule.onNodeWithText(SHORT_NAME)
            .fetchSemanticsNode().size.height
        val longHeight = composeTestRule.onNodeWithText(LONG_NAME)
            .fetchSemanticsNode().size.height

        assertTrue(
            longHeight > shortHeight,
            "$name: the task NAME must wrap to a second line before it truncates — it is the only " +
                "thing identifying the row, while the supporting line under it gets a full line of " +
                "its own. short=${shortHeight}px, long=${longHeight}px (equal heights mean the long " +
                "name was clipped to one line and ellipsised)",
        )
    }

    @Composable
    private fun Harness(dark: Boolean, fontScale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
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

    private fun todayItem(id: String, text: String) = TodayReminderItem(
        id = id,
        itemName = text,
        checklistName = "Здоровье",
        checklistId = 1L,
        fillId = 1L,
        timeLabel = "09:30",
        isPastDue = false,
    )

    private fun agendaRow(id: String, text: String) = AgendaItem.ReminderRow(
        TodayReminderInfo.ItemLevel(
            checklistId = 1L,
            checklistName = "Здоровье",
            fillId = 1L,
            itemId = id,
            itemText = text,
            reminderAt = FIXED_REMINDER_AT,
            isRecurring = false,
        ),
    )

    private companion object {
        /** Outside `src/**/roborazzi` — these are report frames, never goldens. */
        const val REPORT_DIR = "build/ux-report"

        val REPORT_OPTIONS = RoborazziOptions(taskType = RoborazziTaskType.Record)

        const val PHONE_411 = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav"
        const val PHONE_360 = "w360dp-h780dp-normal-long-notround-any-420dpi-keyshidden-nonav"

        val EN: Locale = Locale.forLanguageTag("en-US")
        val RU: Locale = Locale.forLanguageTag("ru-RU")

        /** Fits one line at every size in the matrix — the control. */
        const val SHORT_NAME = "Купить хлеб"

        /** The owner's example: an ordinary task name that used to truncate mid-word. */
        const val LONG_NAME = "Позвонить в поликлинику и записаться к врачу"

        /** Pinned so the row's time label cannot move between runs. */
        const val FIXED_REMINDER_AT = 1_767_225_000_000L
    }
}
