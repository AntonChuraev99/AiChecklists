package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_add_task_row
import aichecklists.core.designsystem.generated.resources.inbox_empty_description
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
import androidx.compose.ui.test.onAllNodesWithText
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The v2 Inbox must always offer a way to add a task, and EXACTLY ONE way at a time.
 *
 * The carrier depends on whether the page has anything on it, and that is the part these tests pin:
 *  - **with tasks** — a row at the END of the list, the Todoist anatomy the owner picked after seeing
 *    both variants rendered;
 *  - **empty** — the placeholder's own `action` slot (owner request, 2026-08-17), with the trailing row
 *    withheld. Both carriers use the same string, so two at once means two identically-named nodes.
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

    // ── A2. On an EMPTY page the action is PART OF THE PLACEHOLDER ───────────

    /**
     * Two claims in one, and the pair is the whole contract of the 2026-08-17 change:
     *
     *  1. **There is exactly ONE add-task control.** Previously the empty page carried a `prominent`
     *     `AddTaskRow` *below* the placeholder; the action now lives in `EmptyState`'s own `action`
     *     slot, and the trailing row is withheld. Leaving both composed would put two nodes with the
     *     identical accessible name "Add task" on one screen — ambiguous to a screen reader and to
     *     every UI test matching that label (which is why `strings.xml` splits that name off in the
     *     first place).
     *  2. **It is INSIDE the placeholder**, not merely somewhere on the page. Asserted as the gap
     *     between the placeholder's description and the control, because that gap is the difference:
     *     `EmptyState` puts `SpacingXl` (24dp) between the two, whereas the previous anatomy put the
     *     row after a box whose height is 60% of the viewport — ~175dp on this 891dp frame. So the
     *     old rendering fails this by an order of magnitude, and the assertion needs no exact
     *     spacing to be re-tuned whenever the empty state's rhythm is.
     *
     * This test REPLACES `inboxScreen_withEmptyInbox_stillShowsTheAddTaskRow`, whose only claim was
     * "an add-task control exists somewhere" — true both before and after, i.e. blind to the change.
     * The invariant it protected (a brand-new user, whose Inbox is empty by definition, must have a
     * visible way to add anything) is claim 1 here, strengthened from "at least one" to "exactly one".
     */
    @Test
    fun inboxScreen_withEmptyInbox_hostsTheAddTaskActionInsideThePlaceholder() {
        var addTaskLabel = ""
        var emptyTitle = ""
        var emptyDescription = ""
        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            emptyTitle = stringResource(Res.string.inbox_empty_title)
            emptyDescription = stringResource(Res.string.inbox_empty_description)
            InboxUnderTest(state = inboxContent(tasks = emptyList()))
        }
        composeTestRule.waitForIdle()

        // Precondition: the empty Inbox page really is what is on screen right now.
        composeTestRule.onNodeWithText(emptyTitle).assertIsDisplayed()

        assertAddTaskActionSitsInsideThePlaceholder(addTaskLabel, emptyDescription)
    }

    /** Same guarantee on a PROJECT page — its empty state is a different branch of the same `when`. */
    @Test
    fun inboxScreen_withEmptyProjectPage_hostsTheAddTaskActionInsideThePlaceholder() {
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

        assertAddTaskActionSitsInsideThePlaceholder(addTaskLabel, emptyDescription)
    }

    /**
     * With TASKS on the page the placeholder is gone, so the trailing row is the only carrier — and
     * still the only one. The mirror image of the assertion above: it is what fails if a future edit
     * "helpfully" leaves the empty-state button composed once the list fills up.
     */
    @Test
    fun inboxScreen_withTasks_hasExactlyOneAddTaskControl() {
        var addTaskLabel = ""
        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            InboxUnderTest(state = inboxContent(tasks = listOf(inboxTask(id = "t1", text = "First task"))))
        }
        composeTestRule.waitForIdle()

        assertEquals(
            1,
            composeTestRule.onAllNodesWithText(addTaskLabel).fetchSemanticsNodes().size,
            "one action, one control: with tasks on the page the trailing row is the carrier and the " +
                "placeholder is not composed at all",
        )
    }

    private fun assertAddTaskActionSitsInsideThePlaceholder(
        addTaskLabel: String,
        emptyDescription: String,
    ) {
        assertEquals(
            1,
            composeTestRule.onAllNodesWithText(addTaskLabel).fetchSemanticsNodes().size,
            "exactly ONE control named \"$addTaskLabel\" may be on an empty page — the placeholder's " +
                "action slot is the carrier there, and the trailing row must stand down",
        )
        composeTestRule.onNodeWithText(addTaskLabel).assertIsDisplayed()

        val descriptionBottom = composeTestRule.onNodeWithText(emptyDescription)
            .fetchSemanticsNode().boundsInRoot.bottom
        val actionTop = composeTestRule.onNodeWithText(addTaskLabel)
            .fetchSemanticsNode().boundsInRoot.top
        val gap = actionTop - descriptionBottom
        val budget = with(composeTestRule.density) { PlaceholderActionGapBudget.toPx() }

        assertTrue(
            gap in 0f..budget,
            "The add-task action must be the placeholder's own action slot, i.e. directly under its " +
                "description (EmptyState puts SpacingXl between them). Measured gap=${gap}px, " +
                "budget=${budget}px ($PlaceholderActionGapBudget). A gap far above the budget means " +
                "the control is a separate row placed AFTER the height-capped empty-state box — the " +
                "anatomy this change replaced.",
        )
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

    private companion object {
        /**
         * How far below the placeholder's description the add-task action may start.
         *
         * `EmptyState` puts `SpacingXl` (24dp) there; the budget is twice that so the assertion does
         * not have to be re-tuned every time that rhythm is, while staying an order of magnitude below
         * what the previous anatomy produced (the row sat after a box sized to 60% of the viewport —
         * ~175dp on this frame).
         */
        val PlaceholderActionGapBudget = 48.dp
    }
}
