package com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.calendar_nav_label
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayScreenState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.time.Instant

/**
 * Screenshot MATRIX of the Calendar tab's WEEK GRID — the row of seven day tiles above the agenda,
 * which is what the owner calls "the card with the date".
 *
 * Same contract as [com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxVisualReportTest]:
 * every capture forces [RoborazziTaskType.Record] and writes OUTSIDE `src/androidHostTest/roborazzi/`,
 * into `build/ux-report/`, so `verifyRoborazziAndroidHostTest` never compares these and a report
 * frame can never enrol itself as a golden.
 *
 * Run — one PNG per test into `feature/home/build/ux-report`:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*CalendarWeekGridVisualReportTest*"
 *
 * ## The `legacyType` frames
 * [legacyType] swaps ONLY the typography back to the pre-`1b06a3c8` scale (reproduced verbatim in
 * [LegacyTypography]) while leaving colours, shapes and the composable itself untouched. That pairing
 * is how the tile-clipping defect was attributed: `WeekGridCell` is byte-identical between `675f6408`
 * and this branch, so the type scale was the only variable that could have moved anything — and the
 * paired frames showed the reminder dot round on the old scale and sliced on the new one.
 *
 * They are kept as a standing control, not as a "before": the tile is now sized by
 * `heightIn(min = …)`, so both halves of the pair render correctly and any future divergence between
 * them again points at the type scale.
 *
 * ## Determinism
 * The week grid derives its dates from the SYSTEM clock (`currentTimeMillis()` inside the composable),
 * so the fixtures below are built relative to the real today rather than a pinned instant. These are
 * report frames, never compared to anything, so a moving date costs nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarWeekGridVisualReportTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    // ── 411dp (the owner's Pixel 9 width class) ──────────────────────────────

    @Test
    fun grid_en_fs100_light() = captureGrid("grid_411_en_fs100_light")

    @Test
    fun grid_en_fs100_light_legacyType() =
        captureGrid("grid_411_en_fs100_light_legacyType", legacyType = true)

    @Test
    fun grid_en_fs130_light() = captureGrid("grid_411_en_fs130_light", fontScale = 1.3f)

    @Test
    fun grid_en_fs130_light_legacyType() =
        captureGrid("grid_411_en_fs130_light_legacyType", fontScale = 1.3f, legacyType = true)

    @Test
    fun grid_en_fs100_dark() = captureGrid("grid_411_en_fs100_dark", dark = true)

    @Test
    fun grid_en_fs100_dark_legacyType() =
        captureGrid("grid_411_en_fs100_dark_legacyType", dark = true, legacyType = true)

    @Test
    fun grid_ru_fs100_light() = captureGrid("grid_411_ru_fs100_light", locale = RU)

    @Test
    fun grid_ru_fs130_light() = captureGrid("grid_411_ru_fs130_light", locale = RU, fontScale = 1.3f)

    // ── 360dp — the narrowest phone the tiles have to survive ────────────────

    @Test
    @Config(qualifiers = "w360dp-h780dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun grid_360_en_fs100_light() = captureGrid("grid_360_en_fs100_light")

    @Test
    @Config(qualifiers = "w360dp-h780dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun grid_360_en_fs100_light_legacyType() =
        captureGrid("grid_360_en_fs100_light_legacyType", legacyType = true)

    @Test
    @Config(qualifiers = "w360dp-h780dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun grid_360_en_fs130_light() = captureGrid("grid_360_en_fs130_light", fontScale = 1.3f)

    @Test
    @Config(qualifiers = "w360dp-h780dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun grid_360_en_fs130_light_legacyType() =
        captureGrid("grid_360_en_fs130_light_legacyType", fontScale = 1.3f, legacyType = true)

    @Test
    @Config(qualifiers = "w360dp-h780dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun grid_360_ru_fs130_light() =
        captureGrid("grid_360_ru_fs130_light", locale = RU, fontScale = 1.3f)

    @Test
    @Config(qualifiers = "w360dp-h780dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun grid_360_en_fs100_dark() = captureGrid("grid_360_en_fs100_dark", dark = true)

    // ── Harness ──────────────────────────────────────────────────────────────

    /**
     * Composes the whole Calendar tab, then switches the pager to page 1 (the week grid + agenda) by
     * tapping the tab — the grid is private, so the screen is the smallest thing that can draw it.
     */
    private fun captureGrid(
        name: String,
        dark: Boolean = false,
        locale: Locale = EN,
        fontScale: Float = 1.0f,
        legacyType: Boolean = false,
    ) {
        Locale.setDefault(locale)
        var calendarTabLabel = ""
        composeTestRule.setContent {
            calendarTabLabel = stringResource(Res.string.calendar_nav_label)
            Harness(dark = dark, fontScale = fontScale, legacyType = legacyType) {
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
        composeTestRule.onRoot().captureRoboImage(
            filePath = "$REPORT_DIR/$name.png",
            roborazziOptions = REPORT_OPTIONS,
        )
    }

    @Composable
    private fun Harness(
        dark: Boolean,
        fontScale: Float,
        legacyType: Boolean,
        content: @Composable () -> Unit,
    ) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
        ) {
            AppTheme(darkTheme = dark) {
                if (legacyType) {
                    // Only the type scale is rolled back — colourScheme and shapes default to the
                    // enclosing MaterialTheme's, so the frame isolates the one variable.
                    MaterialTheme(typography = LegacyTypography) { content() }
                } else {
                    content()
                }
            }
        }
    }

    /**
     * A week's worth of reminders around the real today, so the grid shows: today's tile, tiles that
     * carry the reminder dot, past tiles and empty future tiles — the four visual states in one frame.
     */
    private fun agendaFixture(): List<AgendaItem> {
        val now = System.currentTimeMillis()
        val todayDate = Instant.fromEpochMilliseconds(now)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val todayEpochDay = todayDate.toEpochDays().toLong()
        return listOf(
            AgendaItem.DateHeader(epochDay = todayEpochDay - 1, label = "Yesterday", isPastDue = true),
            AgendaItem.ReminderRow(
                TodayReminderInfo.ItemLevel(
                    checklistId = 1L,
                    checklistName = "Groceries",
                    fillId = 1L,
                    itemId = "i1",
                    itemText = "Pay the electricity bill",
                    reminderAt = now - DAY,
                    isRecurring = false,
                ),
            ),
            AgendaItem.DateHeader(epochDay = todayEpochDay, label = "Today"),
            AgendaItem.ReminderRow(
                TodayReminderInfo.ItemLevel(
                    checklistId = 1L,
                    checklistName = "Errands",
                    fillId = 1L,
                    itemId = "i2",
                    itemText = "Pick up the parcel",
                    reminderAt = now,
                    isRecurring = false,
                ),
            ),
            AgendaItem.ReminderRow(
                TodayReminderInfo.ChecklistLevel(
                    checklistId = 2L,
                    checklistName = "Evening routine",
                    reminderAt = now + HOUR,
                    isRecurring = true,
                ),
            ),
            AgendaItem.DateHeader(epochDay = todayEpochDay + 2, label = "In two days"),
            AgendaItem.ReminderRow(
                TodayReminderInfo.ItemLevel(
                    checklistId = 3L,
                    checklistName = "Health",
                    fillId = 3L,
                    itemId = "i3",
                    itemText = "Book the dentist",
                    reminderAt = now + 2 * DAY,
                    isRecurring = false,
                ),
            ),
        )
    }

    private companion object {

        /** Outside `src/**/roborazzi` — see the class KDoc. */
        const val REPORT_DIR = "build/ux-report"

        val REPORT_OPTIONS = RoborazziOptions(taskType = RoborazziTaskType.Record)

        val EN: Locale = Locale.forLanguageTag("en-US")
        val RU: Locale = Locale.forLanguageTag("ru-RU")

        const val HOUR = 60L * 60L * 1000L
        const val DAY = 24L * HOUR

        /**
         * The type scale as it stood at `675f6408` (master before this branch), reproduced verbatim
         * so a "before" frame can be taken without checking the tree out twice.
         */
        val LegacyTypography = Typography(
            displayLarge = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.25).sp,
            ),
            displayMedium = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
            ),
            displaySmall = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
            ),
            headlineLarge = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
            ),
            headlineMedium = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
            headlineSmall = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
            titleLarge = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
            titleMedium = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
            titleSmall = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
            bodyLarge = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            ),
            bodyMedium = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
            ),
            bodySmall = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),
            labelLarge = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
            labelMedium = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
            labelSmall = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        )
    }
}
