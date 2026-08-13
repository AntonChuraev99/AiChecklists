package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayOptions
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxLayout
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppDueChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppNavBarItem
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppNavigationBar
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiScheduleState
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.format.dueLabelSpec
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.format.label
import com.antonchuraev.homesearchchecklist.feature.home.presentation.components.TaskRow
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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
import java.util.Locale

/**
 * The screenshot MATRIX of the v2 Inbox vertical — what the owner looks at instead of installing a
 * build.
 *
 * ## Why this is not a golden test
 * Every capture here forces [RoborazziTaskType.Record] and writes OUTSIDE `src/androidHostTest/
 * roborazzi/`, into `build/ux-report/`. Two consequences, both deliberate:
 *
 * - `verifyRoborazziAndroidHostTest` never compares these files, so a report frame can never turn
 *   the module's golden verification red, and a missing `build/` on a fresh clone is not a failure.
 * - `recordRoborazziAndroidHostTest` never routes them into the golden folder, so this class cannot
 *   quietly enrol a new, unreviewed frame as an expectation.
 *
 * They are development instruments and report attachments — the goldens are the ones under
 * `roborazzi/`, owned by [InboxSectionsScreenTest] and friends.
 *
 * Run — one PNG per test lands in `feature/home/build/ux-report`:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*InboxVisualReportTest*"
 *
 * ⛔ Do not write a glob into this KDoc: Kotlin block comments nest, so a slash-star inside one
 * opens a second comment and swallows the rest of the file ("Syntax error: Unclosed comment").
 *
 * ## Determinism
 * The clock is pinned ([NOW]), so is the locale (set and restored per test) and the font scale
 * (provided through [LocalDensity] rather than through device qualifiers, which cannot be changed
 * after the activity has launched). Nothing here animates: the section-arrival highlight settles
 * before `waitForIdle` returns because the fixtures never change section mid-capture.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxVisualReportTest {

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

    // ── Defect 1: the due chip clipping the hour at a large font scale ────────

    @Test
    fun dueChip_en_fontScale100() = captureChipSheet("chip_en_fs100", EN, 1.0f)

    @Test
    fun dueChip_en_fontScale130() = captureChipSheet("chip_en_fs130", EN, 1.3f)

    @Test
    fun dueChip_en_fontScale150() = captureChipSheet("chip_en_fs150", EN, 1.5f)

    @Test
    fun dueChip_ru_fontScale100() = captureChipSheet("chip_ru_fs100", RU, 1.0f)

    @Test
    fun dueChip_ru_fontScale130() = captureChipSheet("chip_ru_fs130", RU, 1.3f)

    @Test
    fun dueChip_ru_fontScale150() = captureChipSheet("chip_ru_fs150", RU, 1.5f)

    /**
     * Every chip variant as a task row actually assembles it — the state the brief asks for
     * ("сегодня / завтра / просрочено / повтор / без даты / с будильником").
     */
    @Test
    fun taskRows_allChipVariants_light() =
        captureRowSheet("rows_variants_light", EN, 1.0f, dark = false, compact = false)

    @Test
    fun taskRows_allChipVariants_dark() =
        captureRowSheet("rows_variants_dark", EN, 1.0f, dark = true, compact = false)

    @Test
    fun taskRows_allChipVariants_fontScale130() =
        captureRowSheet("rows_variants_fs130", EN, 1.3f, dark = false, compact = false)

    @Test
    fun taskRows_allChipVariants_ru_fontScale130() =
        captureRowSheet("rows_variants_ru_fs130", RU, 1.3f, dark = false, compact = false)

    @Test
    fun taskRows_allChipVariants_ru_fontScale150() =
        captureRowSheet("rows_variants_ru_fs150", RU, 1.5f, dark = false, compact = false)

    /** The compact row at its single-line limit — the last scale that still fits on one line. */
    @Test
    fun taskRows_compact_allChipVariants_fontScale100() =
        captureRowSheet("rows_variants_compact_fs100", EN, 1.0f, dark = false, compact = true)

    @Test
    fun taskRows_compact_allChipVariants_ru_fontScale100() =
        captureRowSheet("rows_variants_compact_ru_fs100", RU, 1.0f, dark = false, compact = true)

    /** One notch up, and the compact row hands the date a line of its own. */
    @Test
    fun taskRows_compact_allChipVariants_fontScale130() =
        captureRowSheet("rows_variants_compact_fs130", EN, 1.3f, dark = false, compact = true)

    @Test
    fun taskRows_compact_allChipVariants_fontScale150() =
        captureRowSheet("rows_variants_compact_fs150", EN, 1.5f, dark = false, compact = true)

    // ── Defect 2: the error state ────────────────────────────────────────────

    @Test
    fun inboxError_light() = captureScreen(
        name = "error_light",
        state = InboxScreenState.Error(message = ERROR_MESSAGE_EN),
    )

    @Test
    fun inboxError_dark() = captureScreen(
        name = "error_dark",
        state = InboxScreenState.Error(message = ERROR_MESSAGE_EN),
        dark = true,
    )

    @Test
    fun inboxError_ru_fontScale150() = captureScreen(
        name = "error_ru_fs150",
        state = InboxScreenState.Error(message = ERROR_MESSAGE_RU),
        locale = RU,
        fontScale = 1.5f,
    )

    // ── The list, section by section ─────────────────────────────────────────

    @Test
    fun inboxSections_light() = captureScreen("sections_light", sectionedState())

    @Test
    fun inboxSections_dark() = captureScreen("sections_dark", sectionedState(), dark = true)

    @Test
    fun inboxSections_fontScale150() =
        captureScreen("sections_fs150", sectionedState(), fontScale = 1.5f)

    @Test
    fun inboxSections_ru_fontScale150() = captureScreen(
        name = "sections_ru_fs150",
        state = sectionedState(),
        locale = RU,
        fontScale = 1.5f,
    )

    @Test
    fun inboxSections_hi() = captureScreen("sections_hi", sectionedState(), locale = HI)

    /** ⛔ No heading anywhere: one non-empty group is still one group. */
    @Test
    fun inboxAllUndated_light() = captureScreen(
        name = "all_undated_light",
        state = contentState(
            listOf(
                task("Buy bread"),
                task("Call the dentist", priority = 1),
                task("Renew the passport"),
                task("Water the plants", checked = true),
            ),
        ),
    )

    /** ⛔ Also no heading: a user who is simply behind must not get a solitary "Overdue". */
    @Test
    fun inboxAllOverdue_light() = captureScreen(
        name = "all_overdue_light",
        state = contentState(
            listOf(
                task("Buy bread", dueAt = NOW - HOUR),
                task("Call the dentist", dueAt = NOW - 3 * HOUR, priority = 1),
                task("Renew the passport", dueAt = NOW - 2 * DAY),
            ),
        ),
    )

    @Test
    fun inboxCompact_light() = captureScreen(
        name = "compact_light",
        state = sectionedState(layout = InboxLayout.COMPACT),
    )

    @Test
    fun inboxCompact_dark() = captureScreen(
        name = "compact_dark",
        state = sectionedState(layout = InboxLayout.COMPACT),
        dark = true,
    )

    @Test
    fun inboxCompact_ru_fontScale150() = captureScreen(
        name = "compact_ru_fs150",
        state = sectionedState(layout = InboxLayout.COMPACT),
        locale = RU,
        fontScale = 1.5f,
    )

    @Test
    fun inboxSkeleton_light() = captureScreen("skeleton_light", InboxScreenState.Loading)

    @Test
    fun inboxSkeleton_dark() = captureScreen("skeleton_dark", InboxScreenState.Loading, dark = true)

    /** The plan-your-day invitation, which only exists at the TAIL of the undated run. */
    @Test
    fun inboxPlanNudge_light() = captureScreen(
        name = "nudge_light",
        state = contentState(
            listOf(task("Buy bread"), task("Call the dentist"), task("Renew the passport")),
        ),
        planDayHost = true,
    )

    @Test
    fun inboxPlanNudge_ru_fontScale150() = captureScreen(
        name = "nudge_ru_fs150",
        state = contentState(
            listOf(task("Купить хлеб"), task("Позвонить стоматологу"), task("Продлить паспорт")),
        ),
        planDayHost = true,
        locale = RU,
        fontScale = 1.5f,
    )

    // ── Harness ──────────────────────────────────────────────────────────────

    /**
     * The bar is composed UNDER the list on purpose: the whole weight of separating the white bar
     * from the cream page now rests on one 1dp hairline, and that seam is only judgeable with both
     * surfaces in the same frame.
     */
    private fun captureScreen(
        name: String,
        state: InboxScreenState,
        dark: Boolean = false,
        locale: Locale = EN,
        fontScale: Float = 1.0f,
        planDayHost: Boolean = false,
    ) {
        Locale.setDefault(locale)
        composeTestRule.setContent {
            Harness(dark = dark, fontScale = fontScale) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        InboxScreen(
                            state = state,
                            contentBottomPadding = 0.dp,
                            onIntent = {},
                            snackbarHostState = SnackbarHostState(),
                            swallowRootBack = false,
                            createDockOpen = false,
                            onCreateDockDismiss = {},
                            onPlanDayClick = if (planDayHost) ({}) else null,
                        )
                    }
                    AppNavigationBar(
                        items = NAV_ITEMS,
                        selectedItemId = "inbox",
                        onItemSelected = {},
                    )
                }
            }
        }
        capture(name)
    }

    /** The chip on its own, on the page background, at one font scale. */
    private fun captureChipSheet(name: String, locale: Locale, fontScale: Float) {
        Locale.setDefault(locale)
        composeTestRule.setContent {
            Harness(dark = false, fontScale = fontScale) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chipFixtures().forEach { item ->
                        val due = item.dueLabelSpec(nowMillis = NOW)
                        if (due != null) {
                            AppDueChip(
                                state = due.state,
                                label = due.form.label(),
                                isRepeating = item.repeatRule != null,
                                hasAlarm = item.alarmEnabled,
                            )
                        }
                    }
                    AppDueChip(state = GistiScheduleState.Someday, label = "Someday")
                }
            }
        }
        capture(name)
    }

    /** The same fixtures as whole task rows, which is where the width budget is actually spent. */
    private fun captureRowSheet(
        name: String,
        locale: Locale,
        fontScale: Float,
        dark: Boolean,
        compact: Boolean,
    ) {
        Locale.setDefault(locale)
        composeTestRule.setContent {
            Harness(dark = dark, fontScale = fontScale) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 8.dp),
                ) {
                    (chipFixtures() + task("No date at all")).forEach { item ->
                        TaskRow(
                            item = item,
                            compact = compact,
                            onCheckedChange = {},
                            onDetailsClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            nowMillis = NOW,
                        )
                    }
                }
            }
        }
        capture(name)
    }

    @Composable
    private fun Harness(dark: Boolean, fontScale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            // The font scale is provided rather than set through device qualifiers: the activity is
            // already launched by the time a test body runs, so a qualifier change would not reach
            // its Configuration.
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

    /** One task per due-chip variant, in the order the chip's own KDoc lists them. */
    private fun chipFixtures(): List<ChecklistFillItem> = listOf(
        task("Pick up the parcel", dueAt = todayAt(14, 0)),
        // Carries an attachment as well, so the meta row has to fit TWO chips. That is the case the
        // due chip's `weight(1f, fill = false)` exists for: the attachment count is measured first
        // and the date takes what is left, instead of the date claiming the line and squeezing it.
        task("Pay the electricity bill", dueAt = NOW - 1 * DAY, attachments = 2),
        task("Book the dentist", dueAt = todayAt(9, 0) + DAY, alarm = true),
        task("Team retro", dueAt = NOW + 3 * DAY),
        task("Renew the domain", dueAt = NOW + 30 * DAY),
        weeklyTask("Water the plants"),
        // The longest label this chip can produce in normal use: a Mon–Fri repeat, whose Russian
        // form is "Пн,Вт,Ср,Чт,Пт 18:30" plus a repeat glyph. If any fixture squeezes the clock
        // out, it is this one.
        weeklyTask("Stand-up", days = setOf(1, 2, 3, 4, 5)),
    )

    private fun sectionedState(layout: InboxLayout = InboxLayout.CARDS) = contentState(
        tasks = listOf(
            task("Pay the electricity bill", dueAt = NOW - 1 * DAY, priority = 1),
            task("Pick up the parcel", dueAt = todayAt(14, 0)),
            task("Book the dentist", dueAt = todayAt(9, 0) + DAY, alarm = true),
            weeklyTask("Water the plants"),
            task("Renew the passport"),
            task("Buy bread", checked = true),
        ),
        layout = layout,
    )

    private fun contentState(
        tasks: List<ChecklistFillItem>,
        layout: InboxLayout = InboxLayout.CARDS,
    ) = InboxScreenState.Content(
        pages = listOf(
            InboxPage(
                checklistId = 1L,
                title = "Inbox",
                isInbox = true,
                tasks = tasks.map { InboxTask(item = it) },
            ),
        ),
        displayOptions = InboxDisplayOptions(layout = layout),
        nowMillis = NOW,
    )

    private fun task(
        text: String,
        checked: Boolean = false,
        priority: Int = 0,
        dueAt: Long? = null,
        alarm: Boolean = false,
        attachments: Int = 0,
    ): ChecklistFillItem {
        val base = ChecklistFillItem(text = text, checked = checked, priority = priority)
        val dated = if (dueAt == null) base else base.withReminderAt(dueAt)
        return (0 until attachments).fold(dated.withAlarmEnabled(alarm)) { item, index ->
            item.withAttachmentAdded(
                Attachment(
                    id = "att_$index",
                    path = "local/$index",
                    fileName = "receipt_$index.pdf",
                    sizeBytes = 1_024L,
                    createdAt = NOW,
                ),
            )
        }
    }

    private fun weeklyTask(
        text: String,
        days: Set<Int> = setOf(1, 3),
    ): ChecklistFillItem = ChecklistFillItem(text = text, checked = false)
        .withRepeatRule(
            rule = ReminderRepeatRule(type = RepeatType.WEEKLY, weekDays = days),
            timeOfDayMinutes = 18 * 60 + 30,
            nextAt = NOW + 2 * DAY,
        )
        .withAlarmEnabled(true)

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

        val EN: Locale = Locale.forLanguageTag("en-US")
        val RU: Locale = Locale.forLanguageTag("ru-RU")
        val HI: Locale = Locale.forLanguageTag("hi-IN")

        const val HOUR = 60L * 60L * 1000L
        const val DAY = 24L * HOUR

        /** Thursday 2026-08-13, 12:00 local — midday, so nothing straddles a day boundary. */
        val NOW: Long = LocalDateTime(2026, 8, 13, 12, 0)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

        /**
         * These arrive from the ViewModel already localized, so they are literals here on purpose —
         * a report fixture, not user-facing copy.
         */
        const val ERROR_MESSAGE_EN = "Couldn't load your tasks. Check your connection and try again."
        const val ERROR_MESSAGE_RU =
            "Не удалось загрузить задачи. Проверьте подключение и попробуйте снова."

        val NAV_ITEMS = listOf(
            AppNavBarItem(
                id = "inbox",
                label = "Inbox",
                selectedIcon = Icons.Filled.Inbox,
                unselectedIcon = Icons.Outlined.Inbox,
            ),
            AppNavBarItem(
                id = "today",
                label = "Today",
                selectedIcon = Icons.Filled.WbSunny,
                unselectedIcon = Icons.Outlined.WbSunny,
            ),
            AppNavBarItem(
                id = "calendar",
                label = "Calendar",
                selectedIcon = Icons.Filled.CalendarMonth,
                unselectedIcon = Icons.Outlined.CalendarMonth,
            ),
        )
    }
}

/** Today at [hour]:[minute], from the pinned clock. */
private fun todayAt(hour: Int, minute: Int): Long =
    LocalDateTime(2026, 8, 13, hour, minute).toInstant(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
