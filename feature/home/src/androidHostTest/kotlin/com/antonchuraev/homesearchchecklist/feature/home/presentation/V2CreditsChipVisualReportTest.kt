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
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxPage
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreenState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxTask
import com.antonchuraev.homesearchchecklist.feature.home.presentation.projects.ProjectRow
import com.antonchuraev.homesearchchecklist.feature.home.presentation.projects.ProjectsScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.projects.ProjectsScreenState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayScreenState
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.CreditsBadge
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium.CreditsBadgeProvider
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium.PremiumEntryPoint
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components.CreditsChipSource
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
 * What the credits chip actually LOOKS like in each v2 top bar, across the sizes, themes and font
 * scales that can break it.
 *
 * `V2CreditsChipTest` proves the chip exists and routes; only a picture answers "does it fit". The
 * two bars that can collide are the Inbox (title + subtitle + THREE actions) and Projects (chip plus
 * the "+"), and Medium/Expanded swap `TopAppBar` for `MediumTopAppBar`, which lays its title out on a
 * second line — a different bar, not a wider one.
 *
 * ## Not a golden test
 * Every capture forces [RoborazziTaskType.Record] and writes into `build/ux-report/`, outside
 * `src/androidHostTest/roborazzi/` — so `verifyRoborazziAndroidHostTest` never compares these and
 * `recordRoborazziAndroidHostTest` never enrols them as expectations. Same contract, and same
 * reasons, as `InboxVisualReportTest`.
 *
 * Run — one PNG per test lands in `feature/home/build/ux-report`:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*V2CreditsChipVisualReportTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V2CreditsChipVisualReportTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()
    private var badge = CreditsBadge(credits = 5, isPremium = false)

    @Before
    fun startKoinWithStubs() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single<AnalyticsTracker> { NoopAnalyticsTracker }
                    single<AppLogger> { NoopAppLogger }
                    single<PremiumEntryPoint> { PremiumEntryPoint { _, _ -> } }
                    single<CreditsBadgeProvider> { StubBadgeProvider { badge } }
                }
            )
        }
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        stopKoin()
    }

    // ── Inbox: the busiest bar of the four ───────────────────────────────────

    /** Title + subtitle + chip + Tune + overflow, all on one 411dp bar. */
    @Test
    fun inbox_compact_light() {
        Locale.setDefault(EN)
        composeTestRule.setContent { Harness { InboxUnderTest() } }
        capture("inbox_compact_light_5credits")
    }

    @Test
    fun inbox_compact_dark() {
        Locale.setDefault(EN)
        composeTestRule.setContent { Harness(dark = true) { InboxUnderTest() } }
        capture("inbox_compact_dark_5credits")
    }

    /** Zero credits — the chip becomes the "Get More" CTA, i.e. its WIDEST state. */
    @Test
    fun inbox_compact_zeroCredits_getMoreCta() {
        Locale.setDefault(EN)
        badge = CreditsBadge(credits = 0, isPremium = false)
        composeTestRule.setContent { Harness { InboxUnderTest() } }
        capture("inbox_compact_light_getMoreCta")
    }

    /** Widest state at the largest supported text: "Get More" in Russian at fontScale 1.3. */
    @Test
    fun inbox_compact_ru_getMoreCta_fontScale13() {
        Locale.setDefault(RU)
        badge = CreditsBadge(credits = 0, isPremium = false)
        composeTestRule.setContent { Harness(fontScale = 1.3f) { InboxUnderTest() } }
        capture("inbox_compact_ru_getMoreCta_fontScale13")
    }

    /** Premium: the chip grows a leading "PRO" badge — a third element inside the pill. */
    @Test
    fun inbox_compact_premium() {
        Locale.setDefault(EN)
        badge = CreditsBadge(credits = 287, isPremium = true)
        composeTestRule.setContent { Harness { InboxUnderTest() } }
        capture("inbox_compact_light_premiumPro")
    }

    /** Medium: `MediumTopAppBar`, two-line title — the actions sit on the TOP row, not beside it. */
    @Test
    @Config(qualifiers = "w840dp-h1024dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun inbox_medium_rail() {
        Locale.setDefault(EN)
        composeTestRule.setContent { Harness { InboxUnderTest() } }
        capture("inbox_medium840_light")
    }

    @Test
    @Config(qualifiers = "w1280dp-h1024dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun inbox_expanded_drawer() {
        Locale.setDefault(EN)
        composeTestRule.setContent { Harness { InboxUnderTest() } }
        capture("inbox_expanded1280_light")
    }

    // ── Projects: chip beside the existing "+" ───────────────────────────────

    @Test
    fun projects_compact_light() {
        Locale.setDefault(EN)
        composeTestRule.setContent { Harness { ProjectsUnderTest(empty = false) } }
        capture("projects_compact_light_withAddAction")
    }

    /** Empty state: the "+" is withheld, the chip is not. */
    @Test
    fun projects_compact_emptyState() {
        Locale.setDefault(EN)
        composeTestRule.setContent { Harness { ProjectsUnderTest(empty = true) } }
        capture("projects_compact_light_emptyState")
    }

    // ── Calendar: a bar that had no actions slot at all until now ────────────

    @Test
    fun calendar_compact_light() {
        Locale.setDefault(EN)
        composeTestRule.setContent { Harness { CalendarUnderTest() } }
        capture("calendar_compact_light")
    }

    /**
     * The worst case for THIS tab specifically: its title is CENTRE-aligned (it never opted into
     * `startAlignedTitle`), so a wide actions slot squeezes the title slot from the right rather than
     * simply sitting after it. Widest chip state ("Get More Credits"), longest locale, largest font.
     */
    @Test
    fun calendar_compact_ru_getMoreCta_fontScale13() {
        Locale.setDefault(RU)
        badge = CreditsBadge(credits = 0, isPremium = false)
        composeTestRule.setContent { Harness(fontScale = 1.3f) { CalendarUnderTest() } }
        capture("calendar_compact_ru_getMoreCta_fontScale13")
    }

    /** Past the tight point: the failure mode has to be a truncated title, never an overlap. */
    @Test
    fun calendar_compact_ru_getMoreCta_fontScale15() {
        Locale.setDefault(RU)
        badge = CreditsBadge(credits = 0, isPremium = false)
        composeTestRule.setContent { Harness(fontScale = 1.5f) { CalendarUnderTest() } }
        capture("calendar_compact_ru_getMoreCta_fontScale15")
    }

    @Test
    @Config(qualifiers = "w840dp-h1024dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun calendar_medium_rail() {
        Locale.setDefault(EN)
        composeTestRule.setContent { Harness { CalendarUnderTest() } }
        capture("calendar_medium840_light")
    }

    // ── Harness ──────────────────────────────────────────────────────────────

    @Composable
    private fun InboxUnderTest() {
        InboxScreen(
            state = InboxScreenState.Content(
                pages = listOf(
                    InboxPage(
                        checklistId = 1L,
                        title = "Inbox",
                        isInbox = true,
                        tasks = listOf(
                            InboxTask(item = ChecklistFillItem(text = "Buy bread", checked = false)),
                            InboxTask(item = ChecklistFillItem(text = "Call the dentist", checked = false)),
                        ),
                    )
                ),
            ),
            contentBottomPadding = 0.dp,
            onIntent = {},
            snackbarHostState = SnackbarHostState(),
            swallowRootBack = false,
            createDockOpen = false,
            onCreateDockDismiss = {},
            creditsSource = CreditsChipSource.V2_INBOX,
        )
    }

    @Composable
    private fun ProjectsUnderTest(empty: Boolean) {
        ProjectsScreen(
            state = ProjectsScreenState.Content(
                projects = if (empty) emptyList() else listOf(
                    ProjectRow(
                        checklistId = 1L,
                        title = "Groceries",
                        openCount = 2,
                        totalCount = 3,
                        reminderCount = 1,
                        isComplete = false,
                        isEmpty = false,
                    ),
                ),
            ),
            onIntent = {},
            creditsSource = CreditsChipSource.V2_PROJECTS,
        )
    }

    @Composable
    private fun CalendarUnderTest() {
        CalendarScreen(
            todayState = TodayScreenState.Empty,
            calendarState = CalendarState.Empty,
            drawerState = null,
            onTodayReminderClick = { _, _ -> },
            onTodayCreateChecklistClick = {},
            onTodayRetry = {},
            onCalendarIntent = {},
            creditsSource = CreditsChipSource.V2_CALENDAR,
        )
    }

    @Composable
    private fun Harness(
        dark: Boolean = false,
        fontScale: Float = 1.0f,
        content: @Composable () -> Unit,
    ) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            // Provided, not set through device qualifiers: the activity is already launched by the
            // time a test body runs, so a qualifier change would never reach its Configuration.
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
        ) {
            AppTheme(darkTheme = dark) { content() }
        }
    }

    private fun capture(name: String) {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "$REPORT_DIR/creditschip_$name.png",
            roborazziOptions = REPORT_OPTIONS,
        )
    }

    private class StubBadgeProvider(private val current: () -> CreditsBadge) : CreditsBadgeProvider {
        override fun badge(): Flow<CreditsBadge> = flowOf(current())
        override fun currentBadge(): CreditsBadge = current()
    }

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
        const val REPORT_DIR = "build/ux-report"

        /**
         * Forced Record. Without it these captures would be compared by
         * `verifyRoborazziAndroidHostTest` against files that are not in git.
         */
        val REPORT_OPTIONS = RoborazziOptions(taskType = RoborazziTaskType.Record)

        val EN: Locale = Locale.forLanguageTag("en-US")
        val RU: Locale = Locale.forLanguageTag("ru-RU")
    }
}
