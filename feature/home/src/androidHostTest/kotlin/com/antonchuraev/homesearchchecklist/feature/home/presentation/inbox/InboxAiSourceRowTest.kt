package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.analyze_source_photo
import aichecklists.core.designsystem.generated.resources.analyze_source_voice
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayOptions
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.github.takahirom.roborazzi.captureRoboImage
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
 * The v2 Inbox's second door into Analyze.
 *
 * Why this exists at all: the v2 shell shipped with **no reachable route to Analyze**, while
 * Analyze is how 20 of 40 unique checklist creators create. The funnel "saw the v2 shell → started
 * an analysis" read 31 → 0. So the claims worth pinning are (a) the row is on screen when the list
 * is sparse, (b) it is NOT permanent furniture once the list fills up, and (c) a tap reports the
 * material AND the surface — because an unattributed tap is what made the outage invisible.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*InboxAiSourceRowTest*"
 *   ./gradlew :feature:home:recordRoborazziAndroidHostTest --tests "*InboxAiSourceRowTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxAiSourceRowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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

    @Test
    fun emptyInbox_showsEveryNamedSource() {
        var photo = ""
        var voice = ""
        composeTestRule.setContent {
            photo = stringResource(Res.string.analyze_source_photo)
            voice = stringResource(Res.string.analyze_source_voice)
            InboxUnderTest(state = inboxContent(tasks = emptyList()))
        }
        composeTestRule.waitForIdle()

        // Named materials, not one generic "Analyze" door. The generic door is what recorded ZERO
        // photo/pdf/voice analyses in 30 days.
        composeTestRule.onNodeWithText(photo).assertIsDisplayed()
        composeTestRule.onNodeWithText(voice).assertIsDisplayed()
    }

    @Test
    fun sparseInbox_stillShowsTheRow() {
        var photo = ""
        composeTestRule.setContent {
            photo = stringResource(Res.string.analyze_source_photo)
            InboxUnderTest(state = inboxContent(tasks = listOf(task("Buy bread"))))
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Buy bread").assertIsDisplayed()
        composeTestRule.onNodeWithText(photo).assertIsDisplayed()
    }

    /**
     * The other half of the sparse rule, and the one a "just always show it" implementation fails.
     * A permanent row would sit between the user and their tasks forever.
     */
    @Test
    fun busyInbox_doesNotShowTheRow() {
        var photo = ""
        composeTestRule.setContent {
            photo = stringResource(Res.string.analyze_source_photo)
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

        composeTestRule.onNodeWithText("Buy bread").assertIsDisplayed()
        assertTrue(
            composeTestRule.onAllNodesWithText(photo).fetchSemanticsNodes().isEmpty(),
            "a filled Inbox must not carry the AI source row as permanent furniture",
        )
    }

    /**
     * The pager renders PROJECT pages through the same composable, and this row is Inbox-only.
     *
     * Not a cosmetic scoping rule. The row's two surfaces are `INBOX_EMPTY` and `INBOX_SPARSE`,
     * whose contract in `AiEntry.kt` says "an Inbox page"; firing them from a project page makes the
     * two series mean "Inbox or some project" and there is no way to tell the halves apart after the
     * fact. The second, product-visible half is where the tap LANDS: Analyze opens with no
     * `checklistId`, so a user who tapped from inside a project gets a checklist created somewhere
     * else. The add-task row next to it already draws this distinction (`SOURCE_INBOX` vs
     * `SOURCE_PROJECT`).
     *
     * The affordance is not lost on a project page — the capture dock hosts the same [SourceRow] for
     * the whole tab, one "+" tap away.
     */
    @Test
    fun emptyProjectPage_doesNotShowTheInboxRow() {
        var photo = ""
        composeTestRule.setContent {
            photo = stringResource(Res.string.analyze_source_photo)
            InboxUnderTest(state = projectContent(tasks = emptyList()))
        }
        composeTestRule.waitForIdle()

        assertTrue(
            composeTestRule.onAllNodesWithText(photo).fetchSemanticsNodes().isEmpty(),
            "a project page must not carry the Inbox AI row — it would report INBOX_EMPTY and " +
                "open Analyze with no target checklist",
        )
    }

    /** Same claim on the sparse branch, which is the one the `size <= 2` gate actually opens. */
    @Test
    fun sparseProjectPage_doesNotShowTheInboxRow() {
        var photo = ""
        composeTestRule.setContent {
            photo = stringResource(Res.string.analyze_source_photo)
            InboxUnderTest(state = projectContent(tasks = listOf(task("Buy paint"))))
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Buy paint").assertIsDisplayed()
        assertTrue(
            composeTestRule.onAllNodesWithText(photo).fetchSemanticsNodes().isEmpty(),
            "a sparse project page must not carry the Inbox AI row",
        )
    }

    /**
     * A tap must report WHICH material and WHICH surface, and it must reach the ViewModel as one
     * intent. Splitting reporting from navigation across layers is how the v2 credits chip ended up
     * navigating while reporting nothing.
     */
    @Test
    fun tappingASource_emitsTheKindAndTheSurface() {
        val intents = mutableListOf<InboxIntent>()
        var voice = ""
        composeTestRule.setContent {
            voice = stringResource(Res.string.analyze_source_voice)
            InboxUnderTest(
                state = inboxContent(tasks = emptyList()),
                onIntent = { intents += it },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(voice).performClick()
        composeTestRule.waitForIdle()

        val tap = intents.filterIsInstance<InboxIntent.OnAiSourceTapped>().singleOrNull()
        assertEquals(
            InboxIntent.OnAiSourceTapped(AnalyzeInputKind.VOICE, AiEntrySource.INBOX_EMPTY),
            tap,
            "tapping Voice on an empty Inbox must report VOICE from INBOX_EMPTY, exactly once",
        )
    }

    /** A sparse page reports a DIFFERENT surface — the two states convert differently. */
    @Test
    fun tappingASource_onASparsePage_reportsTheSparseSurface() {
        val intents = mutableListOf<InboxIntent>()
        var photo = ""
        composeTestRule.setContent {
            photo = stringResource(Res.string.analyze_source_photo)
            InboxUnderTest(
                state = inboxContent(tasks = listOf(task("Buy bread"))),
                onIntent = { intents += it },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(photo).performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            InboxIntent.OnAiSourceTapped(AnalyzeInputKind.PHOTO, AiEntrySource.INBOX_SPARSE),
            intents.filterIsInstance<InboxIntent.OnAiSourceTapped>().singleOrNull(),
        )
    }

    // ── Report shots ─────────────────────────────────────────────────────────

    @Test
    fun shot_emptyInbox_withSourceRow() {
        composeTestRule.setContent { InboxUnderTest(state = inboxContent(tasks = emptyList())) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun shot_sparseInbox_withSourceRow() {
        composeTestRule.setContent {
            InboxUnderTest(state = inboxContent(tasks = listOf(task("Buy bread"))))
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    /**
     * With the capture dock up, this block stands down — the dock hosts the SAME four pills.
     *
     * Both at once put EIGHT identical pills on one screen under two different phrasings of one
     * promise, and it happened in the state that IS a new user: an almost-empty Inbox (UI audit,
     * 2026-08-19). The list's copy is the one that yields — under the dismiss overlay it is a
     * control that cannot be tapped, while the dock's is the tab's only live route into Analyze
     * during a capture.
     *
     * COUNTED, not `assertDoesNotExist`: the pills still exist, once, in the dock. An existence
     * assertion would pass on a screen showing neither and fail on the correct one.
     */
    @Test
    fun dockOpen_leavesExactlyOneCopyOfTheSources() {
        var photo = ""
        composeTestRule.setContent {
            photo = stringResource(Res.string.analyze_source_photo)
            InboxUnderTest(
                state = inboxContent(tasks = listOf(task("Buy bread"))),
                createDockOpen = true,
            )
        }
        composeTestRule.waitForIdle()

        assertEquals(
            1,
            composeTestRule.onAllNodesWithText(photo).fetchSemanticsNodes().size,
            "the dock and the list must not both offer the sources",
        )
    }

    @Composable
    private fun InboxUnderTest(
        state: InboxScreenState,
        onIntent: (InboxIntent) -> Unit = {},
        createDockOpen: Boolean = false,
    ) {
        AppTheme(darkTheme = false) {
            InboxScreen(
                state = state,
                contentBottomPadding = 0.dp,
                onIntent = onIntent,
                snackbarHostState = SnackbarHostState(),
                swallowRootBack = false,
                createDockOpen = createDockOpen,
                onCreateDockDismiss = {},
            )
        }
    }

    private fun inboxContent(tasks: List<InboxTask>) = InboxScreenState.Content(
        pages = listOf(InboxPage(checklistId = 1L, title = "Inbox", isInbox = true, tasks = tasks)),
        displayOptions = InboxDisplayOptions(),
        nowMillis = NOW,
    )

    /** A single-page state whose one page is a PROJECT, so the pager settles straight onto it. */
    private fun projectContent(tasks: List<InboxTask>) = InboxScreenState.Content(
        pages = listOf(
            InboxPage(checklistId = 7L, title = "Renovation", isInbox = false, tasks = tasks),
        ),
        displayOptions = InboxDisplayOptions(),
        nowMillis = NOW,
    )

    private fun task(text: String) =
        InboxTask(item = ChecklistFillItem(text = text, checked = false))

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
        /** Pinned so a section-boundary assertion cannot pass at midday and fail near midnight. */
        const val NOW = 1_750_000_000_000L
    }
}
