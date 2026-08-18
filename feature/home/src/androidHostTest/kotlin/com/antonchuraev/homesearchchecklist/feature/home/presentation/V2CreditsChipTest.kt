package com.antonchuraev.homesearchchecklist.feature.home.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.credits_display
import aichecklists.core.designsystem.generated.resources.credits_get_more
import aichecklists.core.designsystem.generated.resources.credits_pro_badge
import aichecklists.core.designsystem.generated.resources.projects_add_checklist
import aichecklists.core.designsystem.generated.resources.inbox_display_options
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The paywall has to be reachable from every v2 tab, and each tab has to be TELLABLE APART once it
 * is.
 *
 * The v2 shell shipped with no paywall entry point on any of its four tabs — owner, on a device,
 * 2026-08-13: "я сейчас даже не могу найти где пейвол открыть с главного экрана". The chip that was
 * missing is the product's best converter (4 of 7 purchases in 90 days on the v1 home, 57% of taps
 * reaching the store), so its absence was not a cosmetic gap.
 *
 * Two claims per tab, and BOTH are needed:
 *  1. the chip is drawn in the top bar — the affordance exists;
 *  2. tapping it reports THIS tab's own `source` — the affordance is measurable. A chip that opened
 *     the paywall under a shared or wrong source would be invisible in Amplitude in exactly the same
 *     way an absent chip is, which is the failure mode that let this ship.
 *
 * The fourth tab (Overview) lives in `composeApp` and is pinned by `OverviewCreditsChipTest` there.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*V2CreditsChipTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V2CreditsChipTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var entryPoint: RecordingEntryPoint
    private var badge = CreditsBadge(credits = 5, isPremium = false)

    @Before
    fun startKoin() {
        stopKoin()
        entryPoint = RecordingEntryPoint()
        startKoin {
            modules(
                module {
                    single<AnalyticsTracker> { NoopAnalyticsTracker }
                    single<AppLogger> { NoopAppLogger }
                    single<PremiumEntryPoint> { entryPoint }
                    single<CreditsBadgeProvider> { StubBadgeProvider { badge } }
                }
            )
        }
    }

    @After
    fun stopKoinAfterTest() {
        stopKoin()
    }

    // ── Inbox ────────────────────────────────────────────────────────────────

    @Test
    fun inboxTab_drawsTheCreditsChip_andTapsOpenThePaywallAsTheInboxSurface() {
        var chipLabel = ""
        composeTestRule.setContent {
            chipLabel = stringResource(Res.string.credits_display, 5)
            AppTheme(darkTheme = false) {
                InboxScreen(
                    state = inboxContent(),
                    contentBottomPadding = 0.dp,
                    onIntent = {},
                    snackbarHostState = SnackbarHostState(),
                    swallowRootBack = false,
                    createDockOpen = false,
                    onCreateDockDismiss = {},
                    creditsSource = CreditsChipSource.V2_INBOX,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(chipLabel).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(chipLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(false to "v2_inbox_credits_chip"), entryPoint.opened)
    }

    /**
     * Anti-regression, not a nicety: the Inbox top bar already carried display-options and an
     * overflow, and "put the chip first" is the kind of change that gets made by replacing the
     * `actions` lambda rather than prepending to it.
     */
    @Test
    fun inboxTab_keepsItsExistingToolbarActions_andPutsTheChipFirst() {
        var chipLabel = ""
        var displayOptions = ""
        composeTestRule.setContent {
            chipLabel = stringResource(Res.string.credits_display, 5)
            displayOptions = stringResource(Res.string.inbox_display_options)
            AppTheme(darkTheme = false) {
                InboxScreen(
                    state = inboxContent(),
                    contentBottomPadding = 0.dp,
                    onIntent = {},
                    snackbarHostState = SnackbarHostState(),
                    swallowRootBack = false,
                    createDockOpen = false,
                    onCreateDockDismiss = {},
                    creditsSource = CreditsChipSource.V2_INBOX,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(displayOptions).assertIsDisplayed()

        val chipLeft = composeTestRule.onNodeWithContentDescription(chipLabel)
            .fetchSemanticsNode().boundsInRoot.left
        val tuneLeft = composeTestRule.onNodeWithContentDescription(displayOptions)
            .fetchSemanticsNode().boundsInRoot.left
        assertTrue(
            chipLeft < tuneLeft,
            "the credits chip is the leading action; chip left=$chipLeft, Tune left=$tuneLeft",
        )
    }

    /** No source = the control arm's Inbox does not exist, but previews and tests do. */
    @Test
    fun inboxTab_withoutASource_drawsNoChip() {
        var chipLabel = ""
        composeTestRule.setContent {
            chipLabel = stringResource(Res.string.credits_display, 5)
            AppTheme(darkTheme = false) {
                InboxScreen(
                    state = inboxContent(),
                    contentBottomPadding = 0.dp,
                    onIntent = {},
                    snackbarHostState = SnackbarHostState(),
                    swallowRootBack = false,
                    createDockOpen = false,
                    onCreateDockDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(
            0,
            composeTestRule.onAllNodesWithContentDescription(chipLabel).fetchSemanticsNodes().size,
        )
    }

    // ── Calendar ─────────────────────────────────────────────────────────────

    @Test
    fun calendarTab_drawsTheCreditsChip_andTapsOpenThePaywallAsTheCalendarSurface() {
        var chipLabel = ""
        composeTestRule.setContent {
            chipLabel = stringResource(Res.string.credits_display, 5)
            AppTheme(darkTheme = false) {
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
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(chipLabel).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(chipLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(false to "v2_calendar_credits_chip"), entryPoint.opened)
    }

    /**
     * The Calendar route is reachable from the v1 DRAWER as well, so it is the one shared screen of
     * the four. Adding an unconditional chip here would plant a new paywall entry point inside the
     * A/B control arm and confound the experiment this shell is being measured by.
     */
    @Test
    fun calendarTab_inTheControlArm_drawsNoChip() {
        var chipLabel = ""
        composeTestRule.setContent {
            chipLabel = stringResource(Res.string.credits_display, 5)
            AppTheme(darkTheme = false) {
                CalendarScreen(
                    todayState = TodayScreenState.Empty,
                    calendarState = CalendarState.Empty,
                    drawerState = null,
                    onTodayReminderClick = { _, _ -> },
                    onTodayCreateChecklistClick = {},
                    onTodayRetry = {},
                    onCalendarIntent = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(
            0,
            composeTestRule.onAllNodesWithContentDescription(chipLabel).fetchSemanticsNodes().size,
        )
    }

    // ── Projects ─────────────────────────────────────────────────────────────

    @Test
    fun projectsTab_drawsTheCreditsChip_andTapsOpenThePaywallAsTheProjectsSurface() {
        var chipLabel = ""
        composeTestRule.setContent {
            chipLabel = stringResource(Res.string.credits_display, 5)
            AppTheme(darkTheme = false) {
                ProjectsScreen(
                    state = ProjectsScreenState.Content(projects = listOf(projectRow())),
                    onIntent = {},
                    creditsSource = CreditsChipSource.V2_PROJECTS,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(chipLabel).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(chipLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(false to "v2_projects_credits_chip"), entryPoint.opened)
    }

    /**
     * Projects' "+" is conditional on a non-empty list; the chip is not. On the EMPTY state — a new
     * user, i.e. exactly the population the credit wallet runs out on — the chip must still be there.
     */
    @Test
    fun projectsTab_onTheEmptyState_stillDrawsTheChip_andKeepsTheAddActionConditional() {
        var chipLabel = ""
        var addLabel = ""
        composeTestRule.setContent {
            chipLabel = stringResource(Res.string.credits_display, 5)
            addLabel = stringResource(Res.string.projects_add_checklist)
            AppTheme(darkTheme = false) {
                ProjectsScreen(
                    state = ProjectsScreenState.Content(projects = emptyList()),
                    onIntent = {},
                    creditsSource = CreditsChipSource.V2_PROJECTS,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(chipLabel).assertIsDisplayed()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithContentDescription(addLabel).fetchSemanticsNodes().size,
            "the toolbar \"+\" stays conditional on a non-empty list — the empty state has its own CTA",
        )
    }

    /** Anti-regression on the one action Projects already had. */
    @Test
    fun projectsTab_keepsItsAddAction_andPutsTheChipFirst() {
        var chipLabel = ""
        var addLabel = ""
        composeTestRule.setContent {
            chipLabel = stringResource(Res.string.credits_display, 5)
            addLabel = stringResource(Res.string.projects_add_checklist)
            AppTheme(darkTheme = false) {
                ProjectsScreen(
                    state = ProjectsScreenState.Content(projects = listOf(projectRow())),
                    onIntent = {},
                    creditsSource = CreditsChipSource.V2_PROJECTS,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(addLabel).assertIsDisplayed()
        val chipLeft = composeTestRule.onNodeWithContentDescription(chipLabel)
            .fetchSemanticsNode().boundsInRoot.left
        val addLeft = composeTestRule.onNodeWithContentDescription(addLabel)
            .fetchSemanticsNode().boundsInRoot.left
        assertTrue(chipLeft < addLeft, "the credits chip is the leading action")
    }

    // ── Premium routing, once — the branch is shared, the surface is not ──────

    /**
     * A subscriber must never be sent to the paywall. Pinned on one tab only because all four go
     * through the same [PremiumEntryPoint]; what is per-tab is the source, not the branch.
     */
    @Test
    fun aPremiumUser_isRoutedToSubscriptionStatus_notToThePaywall() {
        badge = CreditsBadge(credits = 40, isPremium = true)
        var chipLabel = ""
        composeTestRule.setContent {
            // A premium chip announces itself as "PRO, N credits" — asserting on that composite
            // label also pins that the PRO badge survived, which a bare count would not.
            chipLabel = stringResource(Res.string.credits_pro_badge) + ", " +
                stringResource(Res.string.credits_display, 40)
            AppTheme(darkTheme = false) {
                ProjectsScreen(
                    state = ProjectsScreenState.Content(projects = listOf(projectRow())),
                    onIntent = {},
                    creditsSource = CreditsChipSource.V2_PROJECTS,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(chipLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(true to "v2_projects_credits_chip"), entryPoint.opened)
    }

    /**
     * Zero credits is when the entry point matters MOST — the free wallet is 100 lifetime credits at
     * 20 per action, so "5 generations, ever" is where every free user ends up. The chip must turn
     * into the upsell there, not disappear.
     */
    @Test
    fun atZeroCredits_theChipBecomesTheUpsell_insteadOfVanishing() {
        badge = CreditsBadge(credits = 0, isPremium = false)
        var getMore = ""
        composeTestRule.setContent {
            getMore = stringResource(Res.string.credits_get_more)
            AppTheme(darkTheme = false) {
                ProjectsScreen(
                    state = ProjectsScreenState.Content(projects = listOf(projectRow())),
                    onIntent = {},
                    creditsSource = CreditsChipSource.V2_PROJECTS,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(getMore).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(getMore).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(false to "v2_projects_credits_chip"), entryPoint.opened)
    }

    // ── Harness ──────────────────────────────────────────────────────────────

    private fun inboxContent() = InboxScreenState.Content(
        pages = listOf(
            InboxPage(
                checklistId = 1L,
                title = "Inbox",
                isInbox = true,
                tasks = listOf(InboxTask(item = ChecklistFillItem(text = "Buy bread", checked = false))),
            )
        ),
    )

    private fun projectRow() = ProjectRow(
        checklistId = 1L,
        title = "Groceries",
        openCount = 2,
        totalCount = 3,
        reminderCount = 0,
        isComplete = false,
        isEmpty = false,
    )

    private class RecordingEntryPoint : PremiumEntryPoint {
        val opened = mutableListOf<Pair<Boolean, String>>()
        override fun open(isPremium: Boolean, source: String) {
            opened += isPremium to source
        }
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
}
