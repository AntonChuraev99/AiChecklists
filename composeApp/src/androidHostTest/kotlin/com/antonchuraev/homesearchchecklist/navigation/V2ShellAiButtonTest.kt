package com.antonchuraev.homesearchchecklist.navigation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.calendar_nav_label
import aichecklists.core.designsystem.generated.resources.nav_add_task_fab_content_description
import aichecklists.core.designsystem.generated.resources.nav_chat_fab_content_description
import aichecklists.core.designsystem.generated.resources.nav_tab_inbox
import aichecklists.core.designsystem.generated.resources.nav_tab_overview
import aichecklists.core.designsystem.generated.resources.nav_tab_projects
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The v2 Compact chrome: the AI entry point moves INTO the bottom bar (a raised circle in the middle
 * of a 2+2 split) and the right-hand floating stack goes away with it — capture is an inline row in
 * the list now.
 *
 * Every claim here is positional or about hit-testing, so it is asserted on a composed tree:
 * "which callback a tap at these coordinates reaches" is precisely the question a state-level test
 * cannot answer, and it is the question this layout gets wrong most easily (Compose does not deliver
 * touches outside a parent's bounds, and `NavigationBar` is a rectangle-clipped `Surface`).
 *
 * Run:
 *   ./gradlew :composeApp:testAndroidHostTest --tests "*V2ShellAiButtonTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V2ShellAiButtonTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private companion object {
        const val CONTENT_MARKER = "SCREEN CONTENT UNDER THE SHELL"
    }

    // ── B5. The AI button lives in the bar, centred, and is a BUTTON ─────────

    /**
     * Catches: the AI affordance still floating in the bottom-RIGHT corner instead of sitting in the
     * middle of the bar.
     *
     * Horizontal centring is the discriminator — presence alone passes today, because the corner FAB
     * already carries the same content description. A raised circle centred over a 2+2 bar is the
     * anatomy the owner chose (variant A), and it is what makes the four destinations read as two
     * pairs rather than a row with a stray button beside it.
     */
    @Test
    fun compactShell_rendersTheAiButtonCentredInTheBottomBar() {
        var chatLabel = ""
        composeTestRule.setContent {
            chatLabel = stringResource(Res.string.nav_chat_fab_content_description)
            V2ShellUnderTest()
        }
        composeTestRule.waitForIdle()

        // Precondition: the shell composed and its hosted screen is on screen.
        composeTestRule.onNodeWithText(CONTENT_MARKER).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(chatLabel).assertIsDisplayed()

        val rootWidth = composeTestRule.onRoot().fetchSemanticsNode().size.width
        val buttonCentreX = composeTestRule.onNodeWithContentDescription(chatLabel)
            .fetchSemanticsNode().boundsInRoot.center.x
        val tolerancePx = with(composeTestRule.density) { 24.dp.toPx() }

        assertTrue(
            abs(buttonCentreX - rootWidth / 2f) <= tolerancePx,
            "The AI button must be horizontally centred in the bar " +
                "(centre=$buttonCentreX, window centre=${rootWidth / 2f}, tolerance=$tolerancePx)",
        )
    }

    /**
     * Catches: the AI entry point implemented as a fifth `NavigationBarItem`.
     *
     * It is an ACTION, not a place — TalkBack announcing "tab" would promise navigation to a
     * destination that does not exist, and a tab is also the wiring that would route the tap through
     * `onNavigate` instead of `onOpenChat` (see the context test below).
     */
    @Test
    fun compactShell_aiButtonIsAnnouncedAsAButtonNotATab() {
        var chatLabel = ""
        composeTestRule.setContent {
            chatLabel = stringResource(Res.string.nav_chat_fab_content_description)
            V2ShellUnderTest()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(chatLabel).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        )
    }

    // ── A4 / B4. No floating "+" on Compact any more ─────────────────────────

    /**
     * Catches: the right-hand create FAB surviving the redesign.
     *
     * `showCreateFab = true` is passed on purpose — App.kt still reports "this tab can take a
     * capture" for the Inbox. The Compact shell must stop turning that into a floating button, since
     * the inline add-task row in the list is the capture affordance now; two of them would compete,
     * and the collapsed FAB-band reserve (132dp → ~32dp) assumes only one exists.
     */
    @Test
    fun compactShell_onTheInboxTab_rendersNoFloatingCreateFab() {
        var createLabel = ""
        composeTestRule.setContent {
            createLabel = stringResource(Res.string.nav_add_task_fab_content_description)
            V2ShellUnderTest(showCreateFab = true)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(CONTENT_MARKER).assertIsDisplayed()
        val createFabNodes = composeTestRule
            .onAllNodesWithContentDescription(createLabel)
            .fetchSemanticsNodes()
        assertEquals(
            0,
            createFabNodes.size,
            "Compact must not render a floating create FAB any more — capture is the inline " +
                "add-task row in the list",
        )
    }

    // ── B6 / B7. The tap reaches the host's chat callback, not navigation ────

    /**
     * Catches two failures at once, both of which ship silently:
     *
     * 1. A tap that lands in the BAR GAP beside the raised circle does nothing. The circle overhangs
     *    the bar's top edge, and Compose does not deliver touches outside a parent's bounds — so a
     *    circle parented to `NavigationBar` (a rectangle-clipped `Surface`) swallows the overhanging
     *    half, and the gap under it is dead space in a strip where every other pixel is a target.
     * 2. The tap reaching `onNavigate` instead of `onOpenChat`. That is the wiring a fifth nav item
     *    would produce — and `onOpenChat` is the ONLY callback that clears `chatSheetContextId`, so
     *    routing the tap anywhere else leaves a chat opened from the bar still anchored to a
     *    checklist the user has already left (see V2ChatEntryPointSourceGuardTest for that half).
     */
    @Test
    fun compactShell_tapInTheBarGap_opensChat_withoutNavigating() {
        var inboxLabel = ""
        var openChatCount = 0
        val navigations = mutableListOf<String>()
        composeTestRule.setContent {
            inboxLabel = stringResource(Res.string.nav_tab_inbox)
            V2ShellUnderTest(
                onOpenChat = { openChatCount++ },
                onNavigate = { navigations += it },
            )
        }
        composeTestRule.waitForIdle()

        // The bar's vertical middle, read off a real destination rather than a constant: at a large
        // font scale the bar is taller than AppDimens.BottomBarHeight.
        val barCentreY = composeTestRule.onNodeWithText(inboxLabel).fetchSemanticsNode()
            .boundsInRoot.center.y
        val rootWidth = composeTestRule.onRoot().fetchSemanticsNode().size.width

        composeTestRule.onRoot().performTouchInput {
            click(Offset(rootWidth / 2f, barCentreY))
        }
        composeTestRule.waitForIdle()

        assertEquals(
            1,
            openChatCount,
            "A tap in the middle of the bar must open the chat — the AI hit area spans the whole " +
                "gap between the two pairs of destinations, so a near-miss is never dead space",
        )
        assertTrue(
            navigations.isEmpty(),
            "The AI tap must not be routed through onNavigate (it would skip the checklist-context " +
                "reset that only onOpenChat performs); navigations seen: $navigations",
        )
    }

    /**
     * One tap = one open. Guards the analytics contract from the other side: `ai_chat_opened` is
     * fired off the host's open callback, so a hit area that overlaps itself (circle + gap both
     * clickable) would double every chat open in the arm the experiment is measuring.
     */
    @Test
    fun compactShell_tapOnTheAiButton_opensTheChatExactlyOnce() {
        var chatLabel = ""
        var openChatCount = 0
        composeTestRule.setContent {
            chatLabel = stringResource(Res.string.nav_chat_fab_content_description)
            V2ShellUnderTest(onOpenChat = { openChatCount++ })
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(chatLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, openChatCount, "One tap on the AI button must open the chat exactly once")
    }

    /**
     * The other half of the same hit area: the RAISED part of the circle must stay tappable.
     *
     * This is the constraint the whole "sibling of the bar, not a child" layout exists for — Compose
     * does not deliver touches outside a parent's bounds, so a circle parented to the rectangle-clipped
     * `NavigationBar` swallows every tap on its overhanging half. It is also the invariant most at risk
     * from the fix below it: the gap hit area was trimmed to the bar's height so it stops eating the
     * content's gestures, and trimming the circle's own bounds with it would silently kill the top 22dp
     * of the primary action.
     */
    @Test
    fun compactShell_tapOnTheRaisedHalfOfTheCircle_opensTheChat() {
        var inboxLabel = ""
        var openChatCount = 0
        composeTestRule.setContent {
            inboxLabel = stringResource(Res.string.nav_tab_inbox)
            V2ShellUnderTest(onOpenChat = { openChatCount++ })
        }
        composeTestRule.waitForIdle()

        val barTop = composeTestRule.onNodeWithText(inboxLabel).fetchSemanticsNode().boundsInRoot.top
        val rootWidth = composeTestRule.onRoot().fetchSemanticsNode().size.width
        val overhangY = barTop - with(composeTestRule.density) { 10.dp.toPx() }

        composeTestRule.onRoot().performTouchInput {
            click(Offset(rootWidth / 2f, overhangY))
        }
        composeTestRule.waitForIdle()

        assertEquals(
            1,
            openChatCount,
            "The part of the circle that rises above the bar must still open the chat — that is the " +
                "whole reason it is a sibling of the bar rather than one of its children",
        )
    }

    /**
     * Catches: the AI hit area eating the content's gestures.
     *
     * The hit area spans the 76dp gap so a near-miss beside the circle is never dead space. When it
     * ALSO covered the 22dp of overhang it covered a 76×22dp strip of the SCREEN, at the bottom centre
     * — where a thumb starts a scroll. `Modifier.clickable` installs a pointer-input node that does not
     * share events with siblings, so every gesture begun in that strip died there instead of reaching
     * the list, on every tab, whether or not anything was drawn under it.
     *
     * Asserted as "the content gets the tap", not as "the chat does not open", because the second half
     * alone would also pass if the strip simply swallowed the event.
     */
    @Test
    fun compactShell_tapBesideTheCircleAboveTheBar_reachesTheContent_notTheChat() {
        var inboxLabel = ""
        var openChatCount = 0
        var contentTaps = 0
        composeTestRule.setContent {
            inboxLabel = stringResource(Res.string.nav_tab_inbox)
            V2ShellUnderTest(
                onOpenChat = { openChatCount++ },
                content = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { contentTaps++ }
                    ) { Text(CONTENT_MARKER) }
                },
            )
        }
        composeTestRule.waitForIdle()

        val barTop = composeTestRule.onNodeWithText(inboxLabel).fetchSemanticsNode().boundsInRoot.top
        val rootWidth = composeTestRule.onRoot().fetchSemanticsNode().size.width
        // 32dp off centre: outside the 56dp circle (28dp half-width), inside the 76dp gap (38dp).
        // 10dp above the bar: inside the 22dp overhang.
        val besideCircleX = rootWidth / 2f + with(composeTestRule.density) { 32.dp.toPx() }
        val overhangY = barTop - with(composeTestRule.density) { 10.dp.toPx() }

        composeTestRule.onRoot().performTouchInput {
            click(Offset(besideCircleX, overhangY))
        }
        composeTestRule.waitForIdle()

        assertEquals(
            1,
            contentTaps,
            "A tap beside the circle but above the bar belongs to the screen — the AI hit area must " +
                "stop at the bar's top edge, or it takes a 76x22dp bite out of every tab's content",
        )
        assertEquals(0, openChatCount, "That tap must not open the chat")
    }

    /**
     * Catches: the AI affordance disappearing — hit area and all — while the quick-capture dock is up.
     *
     * The button used to be composed only when `fabsVisible || chatOpen`, and the host clears
     * `fabsVisible` for BOTH docks. Opening capture (the inline "+ Add task" row) therefore removed the
     * circle and its hit area while leaving the bar's 76dp gap exactly where it was: a notch in the
     * middle of a bar that capture does not dim, and 76dp of the bar where a tap did nothing at all.
     *
     * Capture dims nothing, so unlike the chat this is a state the user LOOKS at.
     */
    @Test
    fun compactShell_whileTheCaptureDockIsUp_theBarCentreStillOpensTheChat() {
        var inboxLabel = ""
        var openChatCount = 0
        composeTestRule.setContent {
            inboxLabel = stringResource(Res.string.nav_tab_inbox)
            V2ShellUnderTest(captureOpen = true, onOpenChat = { openChatCount++ })
        }
        composeTestRule.waitForIdle()

        val barCentreY = composeTestRule.onNodeWithText(inboxLabel).fetchSemanticsNode()
            .boundsInRoot.center.y
        val rootWidth = composeTestRule.onRoot().fetchSemanticsNode().size.width

        composeTestRule.onRoot().performTouchInput {
            click(Offset(rootWidth / 2f, barCentreY))
        }
        composeTestRule.waitForIdle()

        assertEquals(
            1,
            openChatCount,
            "The middle of the bar must stay a target while the capture dock is up — the alternative " +
                "is an empty 76dp notch that swallows taps",
        )
    }

    /**
     * Catches: the hit area overlapping the destinations beside it.
     *
     * Its width is [V2ShellMetrics.AiButtonGapWidth] (76dp) while the spacer that opens the gap is
     * 60dp — the difference is the 8dp-per-side `Arrangement.spacedBy` that M3's `NavigationBar`
     * applies INTERNALLY. That is a private constant of a library this project pins to an alpha, so the
     * arithmetic is verified against the artifact in [V2ShellMetrics.AiButtonBarSpacer]'s KDoc AND
     * pinned here: if a material3 bump changes the spacing, the 76dp area starts overhanging Calendar
     * and Projects and a tap on their inner edge opens the chat instead of switching tabs — a failure
     * with no visual symptom whatsoever.
     */
    @Test
    fun compactShell_tapOnTheInnerEdgeOfANeighbourTab_navigates_insteadOfOpeningChat() {
        var calendarLabel = ""
        var openChatCount = 0
        val navigations = mutableListOf<String>()
        composeTestRule.setContent {
            calendarLabel = stringResource(Res.string.calendar_nav_label)
            V2ShellUnderTest(
                onOpenChat = { openChatCount++ },
                onNavigate = { navigations += it },
            )
        }
        composeTestRule.waitForIdle()

        // Calendar is the last destination BEFORE the gap, so its trailing edge is the one the AI hit
        // area would encroach on first.
        val calendarBounds = composeTestRule.onNodeWithText(calendarLabel)
            .fetchSemanticsNode().boundsInRoot
        val insideEdgeX = calendarBounds.right - with(composeTestRule.density) { 2.dp.toPx() }

        composeTestRule.onRoot().performTouchInput {
            click(Offset(insideEdgeX, calendarBounds.center.y))
        }
        composeTestRule.waitForIdle()

        assertEquals(
            listOf(V2Destination.Calendar),
            navigations,
            "A tap on the inner edge of the destination next to the gap must switch tabs",
        )
        assertEquals(
            0,
            openChatCount,
            "The AI hit area must stay inside the gap — 76dp against a 60dp spacer only works while " +
                "M3 keeps its 8dp-per-side item spacing",
        )
    }

    // ── B9. Medium and Expanded are untouched ────────────────────────────────

    /**
     * The redesign is Compact-only. At rail width the AI button stays in the rail HEADER (left edge)
     * and the create FAB stays with it — there is no bottom bar to move into, and the inline
     * add-task row does not replace a rail button.
     *
     * The x-position assertion is what makes this more than a presence check: it fails if the AI
     * button migrates to a centred bottom affordance at every size.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp-normal-long-notround-any-320dpi-keyshidden-nonav")
    fun mediumShell_keepsTheRailWithBothButtons() {
        var chatLabel = ""
        var createLabel = ""
        var inboxLabel = ""
        var calendarLabel = ""
        var projectsLabel = ""
        var overviewLabel = ""
        composeTestRule.setContent {
            chatLabel = stringResource(Res.string.nav_chat_fab_content_description)
            createLabel = stringResource(Res.string.nav_add_task_fab_content_description)
            inboxLabel = stringResource(Res.string.nav_tab_inbox)
            calendarLabel = stringResource(Res.string.calendar_nav_label)
            projectsLabel = stringResource(Res.string.nav_tab_projects)
            overviewLabel = stringResource(Res.string.nav_tab_overview)
            V2ShellUnderTest(showCreateFab = true)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(chatLabel).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(createLabel).assertIsDisplayed()
        listOf(inboxLabel, calendarLabel, projectsLabel, overviewLabel).forEach { label ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }

        val rootWidth = composeTestRule.onRoot().fetchSemanticsNode().size.width
        val chatCentreX = composeTestRule.onNodeWithContentDescription(chatLabel)
            .fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(
            chatCentreX < rootWidth / 4f,
            "At Medium the AI button stays in the rail header on the leading edge " +
                "(centre=$chatCentreX, window width=$rootWidth)",
        )
    }

    /** Expanded keeps the permanent drawer: two extended FABs, four tabs, Updates and Settings. */
    @Test
    @Config(sdk = [34], qualifiers = "w1000dp-h1280dp-normal-long-notround-any-320dpi-keyshidden-nonav")
    fun expandedShell_keepsThePermanentDrawerWithBothButtons() {
        var chatLabel = ""
        var createLabel = ""
        var inboxLabel = ""
        var overviewLabel = ""
        composeTestRule.setContent {
            chatLabel = stringResource(Res.string.nav_chat_fab_content_description)
            createLabel = stringResource(Res.string.nav_add_task_fab_content_description)
            inboxLabel = stringResource(Res.string.nav_tab_inbox)
            overviewLabel = stringResource(Res.string.nav_tab_overview)
            V2ShellUnderTest(showCreateFab = true)
        }
        composeTestRule.waitForIdle()

        // The two extended FABs are matched on the UNMERGED tree: M3's
        // ExtendedFloatingActionButton wraps its text slot in `clearAndSetSemantics`, so the label
        // never reaches the button's merged node (their icons pass contentDescription = null, which
        // is why the merged nodes carry no label at all — a pre-existing a11y gap in this drawer,
        // out of scope here but worth knowing before touching it).
        composeTestRule.onNodeWithText(chatLabel, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(createLabel, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(inboxLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(overviewLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(CONTENT_MARKER).assertIsDisplayed()
    }

    // ── Harness ──────────────────────────────────────────────────────────────

    @Composable
    private fun V2ShellUnderTest(
        selectedTab: String = V2Destination.Inbox,
        showCreateFab: Boolean = false,
        captureOpen: Boolean = false,
        onOpenChat: () -> Unit = {},
        onNavigate: (String) -> Unit = {},
        content: @Composable () -> Unit = {
            Box(modifier = Modifier.fillMaxSize()) { Text(CONTENT_MARKER) }
        },
    ) {
        AppTheme(darkTheme = false) {
            V2NavigationShell(
                selectedTab = selectedTab,
                onNavigate = onNavigate,
                onOpenChat = onOpenChat,
                onOpenSettings = {},
                onOpenUpdates = {},
                showCreateFab = showCreateFab,
                onOpenCreate = {},
                barVisible = true,
                captureOpen = captureOpen,
                overlayContent = null,
                content = { content() },
            )
        }
    }
}
