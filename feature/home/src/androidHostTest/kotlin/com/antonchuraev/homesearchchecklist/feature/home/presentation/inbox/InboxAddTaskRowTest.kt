package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_add_task_row
import aichecklists.core.designsystem.generated.resources.inbox_empty_title
import aichecklists.core.designsystem.generated.resources.inbox_project_empty_description
import aichecklists.core.designsystem.generated.resources.inbox_quick_add_placeholder
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
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
import kotlin.test.assertTrue

/**
 * The v2 Inbox must always offer a way to add a task, and that way is a row at the END of the list
 * — the Todoist anatomy the owner picked after seeing both variants rendered.
 *
 * Tested through the real [InboxScreen] composable rather than through state, because every claim
 * here is about what is COMPOSED and where: "the row exists", "it is below the last task", "it is
 * still there when the list is empty". None of that is visible in [InboxScreenState].
 *
 * Robolectric + Compose UI test (the infrastructure `FolderComponentsScreenshotTest` already uses in
 * this module); `isIncludeAndroidResources` is what lets `stringResource` resolve the designsystem
 * strings the screen reads.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*InboxAddTaskRowTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxAddTaskRowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * [InboxScreen] resolves an [AnalyticsTracker] with `koinInject()` for its screen-view event, so
     * a Koin root has to exist or the composition dies before anything is drawn.
     */
    @Before
    fun startKoinWithNoopAnalytics() {
        stopKoin()
        startKoin {
            // AppLogger joined the graph when the v2 Inbox started hosting v1's ItemDetailsSheet:
            // the screen koinInject()s it directly, so the test scope has to carry it too.
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

    // ── A1. Present as the LAST element of a non-empty list ──────────────────

    /**
     * Catches: no add-task row on the Inbox at all (today's state — capture is hidden behind the
     * shell's floating "+"), and a row that is composed somewhere other than after the last task
     * (pinned above the bar, or at the top of the list).
     *
     * The position is asserted geometrically rather than by list index because "last element" is a
     * claim about what the user sees at the bottom of their tasks; a row rendered in the scaffold's
     * bottomBar would pass an index check and still be the wrong anatomy.
     */
    @Test
    fun inboxScreen_withTasks_showsAddTaskRowBelowTheLastTask() {
        var addTaskLabel = ""
        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            InboxUnderTest(
                state = inboxContent(
                    tasks = listOf(
                        inboxTask(id = "t1", text = "First task"),
                        inboxTask(id = "t2", text = "Second task"),
                    ),
                ),
            )
        }
        composeTestRule.waitForIdle()

        // Precondition, so a missing add-row can never be confused with a screen that failed to
        // compose at all: the task list itself is on screen.
        composeTestRule.onNodeWithText("Second task").assertIsDisplayed()

        composeTestRule.onNodeWithText(addTaskLabel).assertIsDisplayed()

        val lastTaskBottom = composeTestRule.onNodeWithText("Second task")
            .fetchSemanticsNode().boundsInRoot.bottom
        val addRowTop = composeTestRule.onNodeWithText(addTaskLabel)
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue(
            addRowTop >= lastTaskBottom,
            "The add-task row must sit AFTER the last task (row top=$addRowTop, " +
                "last task bottom=$lastTaskBottom)",
        )
    }

    // ── A2. Present when the Inbox is EMPTY ──────────────────────────────────

    /**
     * Catches the defect this redesign exists to fix: with no tasks the screen renders an
     * `EmptyState` INSTEAD of the list, so a row that lives inside the list is composed nowhere — and
     * a brand-new user, whose Inbox is empty by definition, has no visible way to add anything.
     *
     * Whether the fix moves the EmptyState inside the LazyColumn or renders the row under it is the
     * implementer's call; the invariant is that the row is on screen whenever the screen is.
     */
    @Test
    fun inboxScreen_withEmptyInbox_stillShowsTheAddTaskRow() {
        var addTaskLabel = ""
        var emptyTitle = ""
        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            emptyTitle = stringResource(Res.string.inbox_empty_title)
            InboxUnderTest(state = inboxContent(tasks = emptyList()))
        }
        composeTestRule.waitForIdle()

        // Precondition: the empty Inbox page really is what is on screen right now.
        composeTestRule.onNodeWithText(emptyTitle).assertIsDisplayed()

        composeTestRule.onNodeWithText(addTaskLabel).assertIsDisplayed()
    }

    /** Same guarantee on a PROJECT page — its empty state is a different branch of the same `when`. */
    @Test
    fun inboxScreen_withEmptyProjectPage_stillShowsTheAddTaskRow() {
        var addTaskLabel = ""
        var emptyDescription = ""
        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            emptyDescription = stringResource(Res.string.inbox_project_empty_description)
            InboxUnderTest(
                state = InboxScreenState.Content(
                    pages = listOf(
                        InboxPage(checklistId = 7L, title = "Groceries", isInbox = false, tasks = emptyList()),
                    ),
                ),
            )
        }
        composeTestRule.waitForIdle()

        // Precondition: the empty PROJECT page is on screen (its own `when` branch).
        composeTestRule.onNodeWithText(emptyDescription).assertIsDisplayed()

        composeTestRule.onNodeWithText(addTaskLabel).assertIsDisplayed()
    }

    // ── A3. Tapping the row opens the capture dock ───────────────────────────

    /**
     * Catches: a row that looks tappable and does nothing — the exact failure this arm already
     * shipped once on the rail, and a violation of the project's feedback-on-every-action rule.
     *
     * The host owns `createDockOpen`, so the harness below plays App.kt: any intent the screen
     * raises that is NOT the pager's own `OnPageSelected` is treated as "open the capture dock".
     * That encodes the seam this test expects the implementer to use — a new
     * `InboxIntent.OnAddTaskRowClick` routed through the existing `onIntent` channel. If the row is
     * instead wired to a new callback parameter, the first assertion below is the one that fails and
     * this harness needs its one line changed to match.
     *
     * ⚠️ `dockOpen` is a TEST-LOCAL state object, declared OUTSIDE `setContent`. Declared inside it
     * (`var dockOpen by mutableStateOf(false)`, no `remember`) the harness reports a false failure
     * that reads exactly like a broken screen: the body of `setContent` is a composable, reading the
     * flag subscribes the root scope and writing it invalidates that scope, so the body re-runs and
     * allocates a BRAND NEW `false` state — the dock is raised and thrown away inside one frame, and
     * the assertion below fails with "the dock is not displayed" while the production code is fine.
     * Hoisting it out of the composition is what makes the flag survive; `remember` would work too,
     * but a plain field also lets the test body inspect it.
     */
    @Test
    fun inboxScreen_tappingAddTaskRow_opensTheQuickCaptureDock() {
        var addTaskLabel = ""
        var placeholder = ""
        val raisedIntents = mutableListOf<InboxIntent>()
        var dockOpen by mutableStateOf(false)
        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            placeholder = stringResource(Res.string.inbox_quick_add_placeholder)
            InboxUnderTest(
                state = inboxContent(tasks = listOf(inboxTask(id = "t1", text = "First task"))),
                createDockOpen = dockOpen,
                onIntent = { intent ->
                    raisedIntents += intent
                    if (intent !is InboxIntent.OnPageSelected) dockOpen = true
                },
                onCreateDockDismiss = { dockOpen = false },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(addTaskLabel).performClick()
        composeTestRule.waitForIdle()

        assertTrue(
            raisedIntents.any { it !is InboxIntent.OnPageSelected },
            "Tapping the add-task row must report the tap to the host through onIntent " +
                "(expected a new InboxIntent.OnAddTaskRowClick); intents seen: $raisedIntents",
        )
        composeTestRule.onNodeWithText(placeholder).assertIsDisplayed()
    }

    // ── A4. The flag may not outlive the dock ────────────────────────────────

    /**
     * Catches: a screen with no capture dock AND no bottom navigation.
     *
     * The dock needs a page to capture into, so it is withheld on Loading and on Error. That used to
     * be a cosmetic detail — the bottom bar was still there underneath, so the user simply saw the
     * bar again. Since the v2 shell started surrendering the whole bottom navigation while
     * `createDockOpen` is true, the same mismatch strands the user: no dock, no tabs, no AI button.
     * On Android BACK still rescues them; on wasmJs `PlatformBackHandler` is a no-op, so there is no
     * way out at all.
     *
     * The state IS reachable after a successful load — this screen's state comes from a Flow that can
     * emit an Error on a later refresh — which is why this is a guard and not an impossible branch.
     *
     * Asserted on the callback rather than on a pixel: the fix must hand the flag back to the HOST
     * (the owner of both the dock and the shell's chrome), not hide the mismatch locally.
     */
    @Test
    fun inboxScreen_whenTheDockCannotRender_reportsTheDockClosed() {
        var dismissCount = 0
        composeTestRule.setContent {
            InboxUnderTest(
                state = InboxScreenState.Error(message = "Could not load", canRetry = true),
                createDockOpen = true,
                onCreateDockDismiss = { dismissCount++ },
            )
        }
        composeTestRule.waitForIdle()

        assertTrue(
            dismissCount > 0,
            "With no page to capture into the dock is not composed, so the host must be told to " +
                "drop the flag — otherwise the shell keeps its navigation hidden for a dock that is " +
                "not on screen",
        )
    }

    /** The mirror image: with a page to capture into, nothing may close the dock behind the user. */
    @Test
    fun inboxScreen_whenTheDockCanRender_doesNotReportItClosed() {
        var dismissCount = 0
        composeTestRule.setContent {
            InboxUnderTest(
                state = inboxContent(tasks = listOf(inboxTask(id = "t1", text = "First task"))),
                createDockOpen = true,
                onCreateDockDismiss = { dismissCount++ },
            )
        }
        composeTestRule.waitForIdle()

        assertTrue(
            dismissCount == 0,
            "The dock is on screen, so nothing may report it closed (saw $dismissCount dismissals)",
        )
    }

    // ── Harness ──────────────────────────────────────────────────────────────

    @Composable
    private fun InboxUnderTest(
        state: InboxScreenState,
        createDockOpen: Boolean = false,
        onIntent: (InboxIntent) -> Unit = {},
        onCreateDockDismiss: () -> Unit = {},
    ) {
        AppTheme(darkTheme = false) {
            InboxScreen(
                state = state,
                contentBottomPadding = 0.dp,
                onIntent = onIntent,
                snackbarHostState = SnackbarHostState(),
                swallowRootBack = false,
                createDockOpen = createDockOpen,
                onCreateDockDismiss = onCreateDockDismiss,
            )
        }
    }

    private fun inboxContent(tasks: List<InboxTask>) = InboxScreenState.Content(
        pages = listOf(
            InboxPage(checklistId = 1L, title = "Inbox", isInbox = true, tasks = tasks),
        ),
    )

    // InboxTask wraps the whole ChecklistFillItem since the v2 Inbox started hosting v1's
    // ItemDetailsSheet — the flat fields it used to carry are projections now. `id` stays a label
    // for readability: these assertions match on row text, never on the generated fill-item id.
    private fun inboxTask(id: String, text: String) = InboxTask(
        item = ChecklistFillItem(
            text = text,
            checked = false,
            priority = 0,
            templateItemId = "template-$id",
        )
    )

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
