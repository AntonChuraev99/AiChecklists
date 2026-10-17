package com.antonchuraev.homesearchchecklist.navigation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.calendar_nav_label
import aichecklists.core.designsystem.generated.resources.chat_dock_mic_description
import aichecklists.core.designsystem.generated.resources.nav_add_task_fab_content_description
import aichecklists.core.designsystem.generated.resources.nav_ask_pill_label
import aichecklists.core.designsystem.generated.resources.nav_chat_fab_content_description
import aichecklists.core.designsystem.generated.resources.nav_tab_inbox
import aichecklists.core.designsystem.generated.resources.nav_tab_overview
import aichecklists.core.designsystem.generated.resources.nav_tab_projects
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
 * The v2 Compact chrome after redesign B: the raised centre AI circle is gone, and the AI entry
 * point is the flat **Ask pill** at the top of the bottom PLATE, above four equal tabs
 * (DESIGN_SPEC_B §2.5).
 *
 * Every claim is positional or about hit-testing, so it is asserted on a composed tree — "which
 * callback a tap at these coordinates reaches" is the question a state-level test cannot answer.
 * The previous file pinned the 2+2 split, the 76dp gap and the overhanging half of the circle; those
 * cases are gone with the circle, not weakened — the new cases below pin what replaced them.
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
        const val CONTENT_TAG = "hostedContent"
    }

    // ── The pill: where it is and what it is ────────────────────────────────

    /**
     * Catches: the AI entry point left anywhere but ABOVE the tabs, inside the plate — e.g. a
     * revived floating circle, or the pill rendered under the bar.
     *
     * Asserted geometrically: the pill's bottom edge is above every tab's top edge, it spans most of
     * the window (it is a pill, not a button), and it is centred.
     */
    @Test
    fun compactShell_rendersTheAskPillAboveTheFourTabs() {
        var pillLabel = ""
        var inboxLabel = ""
        composeTestRule.setContent {
            pillLabel = stringResource(Res.string.nav_ask_pill_label)
            inboxLabel = stringResource(Res.string.nav_tab_inbox)
            V2ShellUnderTest()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(CONTENT_MARKER).assertIsDisplayed()
        val pill = composeTestRule.onNodeWithText(pillLabel).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val tab = composeTestRule.onNodeWithText(inboxLabel).fetchSemanticsNode().boundsInRoot
        val rootWidth = composeTestRule.onRoot().fetchSemanticsNode().size.width.toFloat()

        assertTrue(pill.bottom <= tab.top, "The pill must sit above the tabs (pill=$pill, tab=$tab)")
        assertTrue(
            pill.width >= rootWidth * 0.8f,
            "The pill must span the plate (width=${pill.width}, window=$rootWidth)",
        )
        val tolerancePx = with(composeTestRule.density) { 4.dp.toPx() }
        assertTrue(
            abs(pill.center.x - rootWidth / 2f) <= tolerancePx,
            "The pill must be centred (centre=${pill.center.x}, window centre=${rootWidth / 2f})",
        )
    }

    /** It is an ACTION, not a place: `Role.Button`, never a fifth tab. */
    @Test
    fun compactShell_askPillIsAnnouncedAsAButton() {
        var pillLabel = ""
        composeTestRule.setContent {
            pillLabel = stringResource(Res.string.nav_ask_pill_label)
            V2ShellUnderTest()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(pillLabel).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        )
    }

    /**
     * Catches: the hosted screen running UNDER the plate. The content box must end where the plate
     * starts — the plate's MEASURED height (pill + bar) is what the box consumes, so a regression to
     * measuring the bar alone shows up here as content overlapping the pill.
     */
    @Test
    fun compactShell_contentEndsAboveThePlate() {
        var pillLabel = ""
        composeTestRule.setContent {
            pillLabel = stringResource(Res.string.nav_ask_pill_label)
            V2ShellUnderTest()
        }
        composeTestRule.waitForIdle()

        val contentBottom = composeTestRule.onNodeWithTag(CONTENT_TAG).fetchSemanticsNode()
            .boundsInRoot.bottom
        val pillTop = composeTestRule.onNodeWithText(pillLabel).fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            contentBottom <= pillTop,
            "Hosted content must end above the plate (content bottom=$contentBottom, pill top=$pillTop)",
        )
    }

    // ── No floating "+" on Compact ───────────────────────────────────────────

    /**
     * `showCreateFab = true` is passed on purpose — App.kt still reports "this tab can take a
     * capture" for the Inbox. Compact must not turn that into a floating button: the inline
     * add-task row in the list is the capture affordance.
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
        assertEquals(
            0,
            composeTestRule.onAllNodesWithContentDescription(createLabel).fetchSemanticsNodes().size,
            "Compact must not render a floating create FAB — capture is the inline add-task row",
        )
    }

    // ── Taps reach the right callback ────────────────────────────────────────

    /**
     * One tap = one open, and never through `onNavigate`. `ai_chat_opened` / `nav_chat_fab_tapped`
     * are fired off the host's open callback, so a double-firing hit area would double every chat open;
     * routing through `onNavigate` would skip the checklist-context reset only `onOpenChat` performs.
     */
    @Test
    fun compactShell_tapOnThePill_opensTheChatExactlyOnce_withoutNavigating() {
        var pillLabel = ""
        var openChatCount = 0
        var voiceCount = 0
        val navigations = mutableListOf<String>()
        composeTestRule.setContent {
            pillLabel = stringResource(Res.string.nav_ask_pill_label)
            V2ShellUnderTest(
                onOpenChat = { openChatCount++ },
                onOpenChatVoice = { voiceCount++ },
                onNavigate = { navigations += it },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(pillLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, openChatCount, "One tap on the pill must open the chat exactly once")
        assertEquals(0, voiceCount, "A tap on the pill body must not start voice")
        assertTrue(navigations.isEmpty(), "The pill must not route through onNavigate: $navigations")
    }

    /**
     * Catches: a dead strip at the pill's leading edge (the sparkle tile). The WHOLE pill except the
     * mic is one target — a tap on the sparkle is the most natural "Ask Gisti" tap there is.
     */
    @Test
    fun compactShell_tapOnThePillsSparkleEdge_opensTheChat() {
        var pillLabel = ""
        var openChatCount = 0
        composeTestRule.setContent {
            pillLabel = stringResource(Res.string.nav_ask_pill_label)
            V2ShellUnderTest(onOpenChat = { openChatCount++ })
        }
        composeTestRule.waitForIdle()

        val pill = composeTestRule.onNodeWithText(pillLabel).fetchSemanticsNode().boundsInRoot
        val sparkleX = pill.left + with(composeTestRule.density) { 24.dp.toPx() }
        composeTestRule.onRoot().performTouchInput { click(Offset(sparkleX, pill.center.y)) }
        composeTestRule.waitForIdle()

        assertEquals(1, openChatCount, "A tap on the pill's sparkle must open the chat")
    }

    /**
     * The mic is its own target and its own action (owner decision 2026-10-16): it reaches
     * `onOpenChatVoice`, never `onOpenChat`, and is announced as a button with the chat mic's name.
     */
    @Test
    fun compactShell_tapOnTheMic_startsVoice_notTheTextChat() {
        var micLabel = ""
        var openChatCount = 0
        var voiceCount = 0
        composeTestRule.setContent {
            micLabel = stringResource(Res.string.chat_dock_mic_description)
            V2ShellUnderTest(
                onOpenChat = { openChatCount++ },
                onOpenChatVoice = { voiceCount++ },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(micLabel)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, voiceCount, "The mic must start the voice entry exactly once")
        assertEquals(0, openChatCount, "The mic must not ALSO open the text chat")
    }

    /**
     * Contract with the host: `onOpenChatVoice == null` draws NO mic. A mic that does nothing is a
     * dead affordance, and App.kt compiles unchanged against the default.
     */
    @Test
    fun compactShell_withoutAVoiceEntry_drawsNoMic() {
        var micLabel = ""
        var pillLabel = ""
        composeTestRule.setContent {
            micLabel = stringResource(Res.string.chat_dock_mic_description)
            pillLabel = stringResource(Res.string.nav_ask_pill_label)
            V2ShellUnderTest(onOpenChatVoice = null)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(pillLabel).assertIsDisplayed()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithContentDescription(micLabel).fetchSemanticsNodes().size,
            "No voice entry → no mic",
        )
    }

    /**
     * Catches: the tabs still split around a gap. With the circle gone the four tabs are EQUAL
     * (spec §2.5 R2), and a tap on Calendar's inner edge — the side the old gap sat on — navigates.
     */
    @Test
    fun compactShell_fourEqualTabs_innerEdgeTapNavigates() {
        val labels = mutableListOf<String>()
        var calendarLabel = ""
        var openChatCount = 0
        val navigations = mutableListOf<String>()
        composeTestRule.setContent {
            calendarLabel = stringResource(Res.string.calendar_nav_label)
            labels.clear()
            labels += stringResource(Res.string.nav_tab_inbox)
            labels += calendarLabel
            labels += stringResource(Res.string.nav_tab_projects)
            labels += stringResource(Res.string.nav_tab_overview)
            V2ShellUnderTest(
                onOpenChat = { openChatCount++ },
                onNavigate = { navigations += it },
            )
        }
        composeTestRule.waitForIdle()

        val centres = labels.map {
            composeTestRule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.center.x
        }
        val steps = centres.zipWithNext { a, b -> b - a }
        val tolerancePx = with(composeTestRule.density) { 2.dp.toPx() }
        assertTrue(
            steps.all { abs(it - steps.first()) <= tolerancePx },
            "The four tabs must be evenly spaced, no centre gap (label centres=$centres)",
        )

        val calendar = composeTestRule.onNodeWithText(calendarLabel).fetchSemanticsNode().boundsInRoot
        val step = steps.first()
        // Halfway between Calendar's label centre and the slot boundary toward Projects.
        val innerX = calendar.center.x + step / 4f
        composeTestRule.onRoot().performTouchInput { click(Offset(innerX, calendar.center.y)) }
        composeTestRule.waitForIdle()

        assertEquals(listOf(V2Destination.Calendar), navigations)
        assertEquals(0, openChatCount, "A tab tap must never open the chat")
    }

    // ── States that change the plate ─────────────────────────────────────────

    /**
     * Catches: any part of the plate surviving under the quick-capture dock (owner, Pixel 9: "при
     * создании чеклиста в открытом состоянии не должна быть видна нижняя навигация"). Four probes,
     * because "hidden" can be half-right in four ways: no tab label, no pill, no mic, and a tap where
     * the plate used to be reaches nothing — while the hosted screen is still there.
     */
    @Test
    fun compactShell_whileTheCaptureDockIsUp_thePlateIsGone() {
        var inboxLabel = ""
        var pillLabel = ""
        var micLabel = ""
        var openChatCount = 0
        var voiceCount = 0
        val navigations = mutableListOf<String>()
        composeTestRule.setContent {
            inboxLabel = stringResource(Res.string.nav_tab_inbox)
            pillLabel = stringResource(Res.string.nav_ask_pill_label)
            micLabel = stringResource(Res.string.chat_dock_mic_description)
            V2ShellUnderTest(
                captureOpen = true,
                onOpenChat = { openChatCount++ },
                onOpenChatVoice = { voiceCount++ },
                onNavigate = { navigations += it },
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(inboxLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText(pillLabel).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(micLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText(CONTENT_MARKER).assertIsDisplayed()

        val root = composeTestRule.onRoot().fetchSemanticsNode().size
        composeTestRule.onRoot().performTouchInput {
            click(Offset(root.width / 2f, root.height - 8f))
            click(Offset(root.width / 2f, root.height - 120f))
        }
        composeTestRule.waitForIdle()

        assertEquals(0, openChatCount, "No chat affordance may survive under the capture dock")
        assertEquals(0, voiceCount, "No voice affordance may survive under the capture dock")
        assertTrue(navigations.isEmpty(), "No destination may survive under the dock: $navigations")
    }

    /**
     * While the chat dock owns the screen the pill hands off (fades) but stays composed; it must be
     * OUT of the a11y tree meanwhile, or TalkBack reads an "Ask Gisti" button under the open chat.
     */
    @Test
    fun compactShell_whileTheChatIsOpen_thePillLeavesTheA11yTree() {
        var pillLabel = ""
        var micLabel = ""
        composeTestRule.setContent {
            pillLabel = stringResource(Res.string.nav_ask_pill_label)
            micLabel = stringResource(Res.string.chat_dock_mic_description)
            V2ShellUnderTest(chatOpen = true)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(pillLabel).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(micLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText(CONTENT_MARKER).assertIsDisplayed()
    }

    // ── Medium and Expanded are untouched ────────────────────────────────────

    /**
     * The plate is Compact-only. At rail width the AI button stays in the rail HEADER (left edge)
     * with the create FAB, keeps its "Ask Gisti" name, and there is no pill.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp-normal-long-notround-any-320dpi-keyshidden-nonav")
    fun mediumShell_keepsTheRailWithBothButtons() {
        var chatLabel = ""
        var createLabel = ""
        var pillLabel = ""
        val tabs = mutableListOf<String>()
        composeTestRule.setContent {
            chatLabel = stringResource(Res.string.nav_chat_fab_content_description)
            createLabel = stringResource(Res.string.nav_add_task_fab_content_description)
            pillLabel = stringResource(Res.string.nav_ask_pill_label)
            tabs.clear()
            tabs += stringResource(Res.string.nav_tab_inbox)
            tabs += stringResource(Res.string.calendar_nav_label)
            tabs += stringResource(Res.string.nav_tab_projects)
            tabs += stringResource(Res.string.nav_tab_overview)
            V2ShellUnderTest(showCreateFab = true)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(chatLabel).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(createLabel).assertIsDisplayed()
        tabs.forEach { composeTestRule.onNodeWithText(it).assertIsDisplayed() }
        composeTestRule.onNodeWithText(pillLabel).assertDoesNotExist()

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

        // Matched on the UNMERGED tree: M3's ExtendedFloatingActionButton wraps its text slot in
        // `clearAndSetSemantics`, so the label never reaches the button's merged node.
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
        chatOpen: Boolean = false,
        onOpenChat: () -> Unit = {},
        onOpenChatVoice: (() -> Unit)? = {},
        onNavigate: (String) -> Unit = {},
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
                chatOpen = chatOpen,
                captureOpen = captureOpen,
                overlayContent = null,
                onOpenChatVoice = onOpenChatVoice,
                content = {
                    Box(modifier = Modifier.fillMaxSize().testTag(CONTENT_TAG)) {
                        Text(CONTENT_MARKER)
                    }
                },
            )
        }
    }
}
