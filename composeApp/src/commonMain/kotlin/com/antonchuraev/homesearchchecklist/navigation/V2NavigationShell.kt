package com.antonchuraev.homesearchchecklist.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.calendar_nav_label
import aichecklists.core.designsystem.generated.resources.chat_dock_mic_description
import aichecklists.core.designsystem.generated.resources.nav_add_task_fab_content_description
import aichecklists.core.designsystem.generated.resources.nav_ask_pill_label
import aichecklists.core.designsystem.generated.resources.nav_chat_fab_content_description
import aichecklists.core.designsystem.generated.resources.nav_tab_inbox
import aichecklists.core.designsystem.generated.resources.nav_tab_overview
import aichecklists.core.designsystem.generated.resources.nav_tab_projects
import aichecklists.core.designsystem.generated.resources.settings_title
import aichecklists.core.designsystem.generated.resources.update_feed_menu_item
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppNavBarItem
import com.antonchuraev.homesearchchecklist.desingsystem.components.calendarNavBarItem
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.AskGistiBar
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.AskGistiBarStyle
import com.antonchuraev.homesearchchecklist.desingsystem.components.inboxNavBarItem
import com.antonchuraev.homesearchchecklist.desingsystem.components.overviewNavBarItem
import com.antonchuraev.homesearchchecklist.desingsystem.components.projectsNavBarItem
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The four top-level destinations of the v2 (Todoist-style) navigation arm.
 *
 * These are plain `String` ids, not routes, because the shell talks to App.kt through the same
 * `(destination: String) -> Unit` contract [AdaptiveNavigationShell] uses — App.kt owns every
 * back-stack mutation, so the shell stays free of navigation knowledge and of `AppNavRoute`.
 *
 * A SEPARATE constant set from [DrawerDestination] on purpose: adding v2 entries to
 * `DrawerDestination` would change the control arm's chrome (it is iterated by the v1 drawer) and
 * invalidate the A/B comparison. The values MUST stay byte-identical to designsystem's
 * `NAV_BAR_*_ID` constants — the bar reports the tapped item's id straight back here — and they are
 * also the wire values of the `nav_tab_selected` analytics param, so renaming one silently breaks
 * both selection highlighting and the dashboard.
 */
object V2Destination {
    const val Inbox = "v2_inbox"
    const val Calendar = "v2_calendar"
    const val Projects = "v2_projects"
    const val Overview = "v2_overview"
}

/**
 * Layout constants the v2 shell imposes on the screens it hosts.
 *
 * Lives here (not in designsystem) because the numbers describe THIS shell's chrome; a screen only
 * ever receives the resolved [Dp] from App.kt, never this object.
 *
 * ## Redesign B: the raised AI circle is gone
 * The 56dp centre button, its 22dp overhang, its 3dp ring and the 76dp 2+2 gap were deleted with it
 * (DESIGN_SPEC_B §2.5). The AI entry point is now the flat Ask pill INSIDE the bottom plate, so no
 * part of the chrome overhangs the hosted screen any more.
 */
object V2ShellMetrics {
    /**
     * TalkBack position of the Ask pill inside the plate: FIRST, before the four tabs.
     *
     * It sits ABOVE the tabs now, so "read it first" is also the geometric order; the explicit index
     * (on a traversal group holding the pill) only makes that deterministic instead of heuristic.
     * Android-only semantics — a no-op on wasmJs and iOS.
     */
    const val AskPillTraversalIndex: Float = -1f

    /**
     * Seed for the plate's height before `onSizeChanged` lands: the 48dp pill + its 12dp top and 4dp
     * bottom padding, on top of the bar's 80dp. Only a first-frame guess — the plate measures itself,
     * and a wrapped label (fontScale ≥ 1.3, RU/HI) makes it taller.
     */
    val AskPillBand: Dp = 64.dp

    /**
     * The ONLY bottom reserve a Compact v2 tab screen needs: one 8dp breathing gap.
     *
     * The plate needs no reservation because [V2NavigationShell] renders it OUTSIDE the content slot
     * (`Column { Box(weight(1f)) { content() }; plate }`) — the content box already ends at the
     * plate's top edge — and because that same box CONSUMES the plate's measured height and
     * `WindowInsets.navigationBars`, so a hosted screen cannot reserve the strip twice either.
     *
     * Was `AiButtonOverhang + SpacingSm` (~30dp) while the raised circle poked 22dp above the bar
     * into every tab; with the flat pill nothing overhangs, so only the gap is left.
     */
    val FabBandPadding: Dp = AppDimens.SpacingSm
}

/**
 * The v2 (Todoist-style) navigation shell: Inbox · Calendar · Projects · Overview, plus a floating
 * entry point to the AI chat.
 *
 * Mounted by App.kt INSTEAD of [AdaptiveNavigationShell] when — and only when — the resolved
 * `NavVariant` is `V2`. It is a separate composable rather than a branch inside
 * [AdaptiveNavigationShell] precisely so the control arm's shell file stays textually untouched:
 * that is the cheapest available proof that the baseline arm did not move.
 *
 * Layout by window size (mirrors the control shell's breakpoints so the two arms differ in content,
 * never in responsiveness):
 * - Compact (<600dp): the bottom PLATE — the Ask pill (AI chat entry) above four equal tabs
 *   (redesign B). Nothing floats over the content any more.
 * - Medium (600–839dp): [NavigationRail] whose header slot holds the chat FAB.
 * - Expanded (≥840dp): [PermanentNavigationDrawer] with the four tabs plus Settings / Updates,
 *   which have no tab of their own and would otherwise be unreachable at this size.
 *
 * ## Why `content(null)` in ALL THREE variants
 * The `DrawerState?` slot parameter exists only so a screen can render a hamburger and open a modal
 * drawer. v2 has no drawer at any size — its drawer content lives on the Overview tab — so passing
 * `null` everywhere makes `AppScaffold` fall back to no navigation icon and keeps MainScreen's
 * "close the drawer on BACK" handler disabled, with no edit to either screen.
 *
 * ## Why the NavigationBar is NOT in `AppScaffold.bottomBar`
 * `AppScaffold`'s `wrappedBottomBar` adds `windowInsetsPadding(ime ∪ navigationBars)` — except when
 * the screen passes `contentExtendsBehindNavBar = true`, which MainScreen and ChecklistDetailScreen
 * both do. Hosting the bar in that slot would therefore produce double navbar padding on some
 * screens and none on others. Rendering it here, outside the screens, gives one predictable result
 * everywhere. [NavigationBar][androidx.compose.material3.NavigationBar] applies
 * `WindowInsets.navigationBars` itself, so it also paints the gesture-nav strip — which is what
 * replaces the `gistiDockColor()` strip that disappeared with the chat dock in v2.
 *
 * @param selectedTab one of [V2Destination]'s constants — the tab drawn as active.
 * @param onNavigate invoked with a [V2Destination] constant when the user picks a tab. Debounced
 *   here (see below), so App.kt may mutate the back stack unguarded.
 * @param onOpenChat the AI chat entry point. In v2 the bottom chat dock is gone, so this button (the
 *   Ask pill in the Compact plate, the rail header / drawer button on larger windows) is the only
 *   chat affordance on a tab screen. It opens the chat IN PLACE via [overlayContent], over whatever
 *   screen the user is on.
 * @param showCreateFab whether the manual "+" create FAB is offered alongside the AI one. **Medium
 *   and Expanded only** — Compact dropped the floating create button entirely (capture is an inline
 *   add-task row inside the list now), so this flag is inert there. True only where there is a task
 *   list to add to (the Inbox tab and its project pages).
 * @param onOpenCreate tapped "+". The host opens its create surface; the shell owns no create state.
 * @param chatOpen whether the host's chat dock is currently up (and not already on its way out — the
 *   host drops this at the START of the dock's exit so the two movements overlap). Compact-only, and
 *   purely for MOTION: the Ask pill stays composed while it is true so it can hand off to the
 *   dock (fade) instead of being cut mid-frame. It is drawn UNDER [overlayContent], so the
 *   dock's scrim takes every touch while this is true, and the pill drops out of the a11y tree.
 * @param captureOpen whether the host's quick-capture dock is up. On Compact this REPLACES the bottom
 *   chrome: the whole plate (pill + bar) is off, and the dock becomes the
 *   app's bottom edge. That is an owner decision from a device run — "при создании чеклиста в открытом
 *   состоянии не должна быть видна нижняя навигация" — and it reverses the earlier arrangement in
 *   which the bar stayed lit under the dock. See [V2ShellCompactBar] for what the swap costs and for
 *   the strip that is left behind in the bar's place.
 *   On Medium/Expanded it hides the rail / drawer create button instead — that one really would float
 *   over its own dock.
 * @param onOpenSettings / [onOpenUpdates] Expanded-only extras — destinations that are drawer items
 *   in the control arm and Overview-tab rows on Compact/Medium.
 * @param barVisible false hides the plate while a non-tab route (ChecklistDetail,
 *   AiChat, Settings…) is on top of the stack, so the chrome never floats over a detail screen. The
 *   shell itself stays mounted, which is what keeps the tab state alive underneath.
 * @param overlayContent rendered ABOVE the screen at every window size. This is where the v2 chat
 *   dock lives: hosting it once here is what makes "the AI button opens the chat right on this
 *   screen" true for every route the shell renders, including pushed detail screens, with no
 *   per-screen dock wiring. WHERE "above" stops differs by size, deliberately: on Compact it covers
 *   the bar and the FABs (they float OVER the content, so a dock drawn under them would be poked
 *   through), while on Medium/Expanded it is confined to the CONTENT pane so the rail / permanent
 *   drawer stays visible, undimmed and tappable — spanning the whole window there hid the rail's
 *   lower half behind the dock panel and left its upper half bright above the scrim.
 * @param onOpenChatVoice Compact only: the Ask pill's mic — opens the chat dock already recording.
 *   Null (the default) draws NO mic: a mic that does nothing is a dead affordance, and the host
 *   wires the "start in voice" entry separately (feature work, owner decision 2026-10-16).
 * @param content the NavDisplay renderer; always receives `null` for the drawer state.
 */
@Composable
fun V2NavigationShell(
    selectedTab: String,
    onNavigate: (destination: String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdates: () -> Unit,
    showCreateFab: Boolean = false,
    onOpenCreate: () -> Unit = {},
    barVisible: Boolean = true,
    chatOpen: Boolean = false,
    captureOpen: Boolean = false,
    overlayContent: (@Composable () -> Unit)? = null,
    onOpenChatVoice: (() -> Unit)? = null,
    content: @Composable (drawerState: DrawerState?) -> Unit,
) {
    // Same 500ms guard as AdaptiveNavigationShell: a rapid double-tap on a tab otherwise races the
    // Koin ViewModel resolution of the destination screen. Duplicated rather than extracted so the
    // control arm's shell file needs no edit at all.
    var navConsumed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun guardedNavigate(destination: String) {
        if (navConsumed) return
        navConsumed = true
        scope.launch {
            delay(500)
            navConsumed = false
        }
        onNavigate(destination)
    }

    // [overlayContent] is handed to each variant rather than drawn here, over all three: the shell's
    // outermost Box spans the WHOLE window, and mounting the dock in it covered the rail and the
    // permanent drawer on Medium/Expanded. Each variant mounts it at the top of the region the dock
    // may legitimately own — the whole screen on Compact, the content pane beside the chrome on the
    // other two.
    Box(modifier = Modifier.fillMaxSize()) {
        when (rememberAppWindowSizeClass()) {
            AppWindowSizeClass.Compact -> V2ShellCompactBar(
                selectedTab = selectedTab,
                onNavigate = ::guardedNavigate,
                onOpenChat = onOpenChat,
                onOpenChatVoice = onOpenChatVoice,
                barVisible = barVisible,
                chatOpen = chatOpen,
                captureOpen = captureOpen,
                overlayContent = overlayContent,
                content = content,
            )

            AppWindowSizeClass.Medium -> V2ShellRail(
                selectedTab = selectedTab,
                onNavigate = ::guardedNavigate,
                onOpenChat = onOpenChat,
                // "A dock is up" is folded into showCreateFab here (and in the drawer below) rather
                // than hiding the rail's whole header: the rail is permanent chrome, so blanking it
                // while a dock is open would make the navigation itself flicker. Only the create
                // button — the one that would float over its own dock — goes away. Derived from the
                // two dock flags instead of taking a third parameter: one source of truth, so the
                // shell cannot be told "no dock is open" and "the chat is open" in the same call.
                showCreateFab = showCreateFab && !chatOpen && !captureOpen,
                onOpenCreate = onOpenCreate,
                overlayContent = overlayContent,
                content = content,
            )

            AppWindowSizeClass.Expanded -> V2ShellPermanent(
                selectedTab = selectedTab,
                onNavigate = ::guardedNavigate,
                onOpenChat = onOpenChat,
                showCreateFab = showCreateFab && !chatOpen && !captureOpen,
                onOpenCreate = onOpenCreate,
                onOpenSettings = onOpenSettings,
                onOpenUpdates = onOpenUpdates,
                overlayContent = overlayContent,
                content = content,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal layout variants
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Compact: the bottom PLATE (redesign B "Soft & tactile") — the Ask pill above four equal tabs, on
 * one chrome surface.
 *
 * ## One node is the plate, and it carries three things together
 * `onSizeChanged`, the `background(shoulder).clip(SheetTop)` pair and the chrome fill all sit on the
 * plate `Column`, never on the bar inside it (DESIGN_SPEC_B §11 grabli 2):
 *  - the MEASURED height covers pill + bar, so the inset the content box consumes is the whole plate
 *    — measuring only the bar would let a hosted screen's keyboard arithmetic lift its dock by the
 *    pill's height too much;
 *  - the shoulder backdrop sits BEFORE the clip: the plate has nothing behind it (the content box
 *    ends at its top edge), so a clip without the backdrop cuts a hole straight through the
 *    composition — Robolectric's `#FAFAFA` window backdrop, `windowBackground` on a device, nothing
 *    at all on wasmJs. `background(…).clip(…)` and never the reverse;
 *  - the fill is painted inside the clip so the 28dp `SheetTop` corners belong to the plate as a
 *    whole, not to the bar half of it.
 *
 * The seed is `BottomBarHeight + AskPillBand + WindowInsets.navigationBars`, so the common path
 * measures what it guessed and never writes the state; a wrapped label still costs the one
 * recomposition it always did.
 *
 * ## The pill and the chat dock
 * Tap anywhere on the pill → [onOpenChat]; the mic (drawn only when [onOpenChatVoice] is non-null)
 * → [onOpenChatVoice]. While [chatOpen] the pill hands off to the dock: alpha 1→0 on the draw phase
 * (no scale — the pill is wide, a scaled pill reads as a zoom glitch), out of the a11y tree, and back
 * on `ButtonReturnMs` as soon as the dock begins its exit. It stays composed meanwhile, under
 * [overlayContent], whose scrim takes every touch.
 *
 * ## While the quick-capture dock is up there is no plate
 * The whole plate — pill and bar — is out of the tree while [captureOpen]: the dock IS the bottom
 * chrome in that state (owner, Pixel 9: "при создании чеклиста в открытом состоянии не должна быть
 * видна нижняя навигация"). Only a strip of chrome exactly `WindowInsets.navigationBars` tall is
 * left, because the dock sits in `AppScaffold`'s bottomBar slot and cannot paint the system-nav
 * strip it is held above. 0px in every Robolectric frame (zero insets) — a device-only check.
 * No cross-fade: both flags come from the host's single `v2CreateDockOpen`, so the plate leaves on
 * the frame the (taller, full-bleed) dock arrives.
 */
@Composable
private fun V2ShellCompactBar(
    selectedTab: String,
    onNavigate: (String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenChatVoice: (() -> Unit)?,
    barVisible: Boolean,
    chatOpen: Boolean,
    captureOpen: Boolean,
    overlayContent: (@Composable () -> Unit)?,
    content: @Composable (DrawerState?) -> Unit,
) {
    val items = listOf(
        inboxNavBarItem(stringResource(Res.string.nav_tab_inbox)),
        // Reuses the existing calendar label — a v2-specific duplicate key would drift in
        // translation against the control arm's drawer item for the same destination.
        calendarNavBarItem(stringResource(Res.string.calendar_nav_label)),
        projectsNavBarItem(stringResource(Res.string.nav_tab_projects)),
        overviewNavBarItem(stringResource(Res.string.nav_tab_overview)),
    )
    // The VISIBLE label is the accessible name (owner decision 2026-10-16: "Ask Gisti or add a
    // task"). A contentDescription that differs from what is on screen breaks voice control
    // ("tap Ask Gisti or add a task" would find nothing), so the old "Ask Gisti" string stays on the
    // rail / drawer buttons only.
    val pillLabel = stringResource(Res.string.nav_ask_pill_label)
    // Reused from the chat dock's own mic — one action, one name.
    val micDescription = stringResource(Res.string.chat_dock_mic_description)

    val density = LocalDensity.current
    val seedPlateHeightPx = with(density) {
        (AppDimens.BottomBarHeight + V2ShellMetrics.AskPillBand).roundToPx()
    } + WindowInsets.navigationBars.getBottom(density)
    var plateHeightPx by remember(seedPlateHeightPx) { mutableIntStateOf(seedPlateHeightPx) }
    val plateHeight = with(density) { plateHeightPx.toDp() }

    // 0 = pill at rest, 1 = fully handed over to the chat dock. Asymmetric by design: the pill is the
    // EXITING element (M3 emphasized-accelerate, shorter than the dock's entrance) and comes back on a
    // short decelerate as soon as the dock BEGINS its exit. Duration 0 when the platform's animator
    // scale is off — `animateFloatAsState` reads MotionDurationScale from the coroutine context.
    val handoff by animateFloatAsState(
        targetValue = if (chatOpen) 1f else 0f,
        animationSpec = if (chatOpen) {
            tween(V2ChatMotion.ButtonHandoffMs, easing = V2ChatMotion.ExitEasing)
        } else {
            tween(V2ChatMotion.ButtonReturnMs, easing = V2ChatMotion.EnterEasing)
        },
        label = "v2AskPillHandoff",
    )

    // "Which parts of the chrome are on" has ONE answer per frame: pill and bar leave together.
    val chromeVisible = barVisible && !captureOpen

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Exactly one bottom reserve in each of the three states:
                    //  - pushed route ([barVisible] false): nothing is painted here, the screen
                    //    insets itself;
                    //  - capture up: only the system-nav strip is left below this box;
                    //  - otherwise: the MEASURED plate (pill + bar + its own nav inset). Insets are
                    //    measured from the bottom of the WINDOW while this box stops one plate
                    //    higher, so a screen lifting its bottomBar by the raw `ime` inset would lift
                    //    it by a plate too much without this consume.
                    .then(
                        when {
                            !barVisible -> Modifier
                            captureOpen -> Modifier.consumeWindowInsets(WindowInsets.navigationBars)
                            else -> Modifier.consumeWindowInsets(WindowInsets(bottom = plateHeight))
                        }
                    ),
            ) {
                content(null)
            }
            when {
                chromeVisible -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { plateHeightPx = it.height }
                        .background(AppSurface.bottomChromeShoulder())
                        .clip(AppShapeTokens.SheetTop)
                        .background(AppSurface.bottomChrome()),
                ) {
                    AskGistiBar(
                        placeholder = pillLabel,
                        onClick = onOpenChat,
                        onMicClick = onOpenChatVoice,
                        micContentDescription = micDescription,
                        style = AskGistiBarStyle.ChromePill,
                        modifier = Modifier
                            .padding(
                                start = AppDimens.SpacingMd,
                                end = AppDimens.SpacingMd,
                                top = AppDimens.SpacingMd,
                                bottom = AppDimens.SpacingXs,
                            )
                            // Outermost semantics: while the dock owns the screen the pill and its
                            // mic leave the a11y tree; otherwise the pill group is read FIRST.
                            .then(
                                if (chatOpen) {
                                    Modifier.clearAndSetSemantics { }
                                } else {
                                    Modifier.semantics {
                                        isTraversalGroup = true
                                        traversalIndex = V2ShellMetrics.AskPillTraversalIndex
                                    }
                                }
                            )
                            // Draw-phase read (lambda form): the hand-off does not recompose the
                            // shell, and with it the hosted screen.
                            .graphicsLayer { alpha = 1f - handoff },
                    )
                    V2SplitNavigationBar(
                        items = items,
                        selectedItemId = selectedTab,
                        onItemSelected = onNavigate,
                    )
                }

                // The plate's replacement while the capture dock owns the bottom: nothing but the
                // system-navigation strip, in the chrome's own tone. See the KDoc.
                barVisible -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppSurface.bottomChrome())
                        .windowInsetsBottomHeight(WindowInsets.navigationBars),
                )
            }
        }

        // Last child of the same fillMaxSize Box the plate lives in, so the chat dock covers it.
        overlayContent?.invoke()
    }
}

/**
 * The tab half of the Compact plate: the four v2 destinations as four EQUAL tabs (redesign B — the
 * 2+2 split around the raised AI circle is gone with the circle).
 *
 * ## Why this is not `AppNavigationBar`
 * Same `AppNavBarItem` descriptors, same `alwaysShowLabel`, same M3 container — but this bar sits
 * inside the plate and paints the bottom-chrome tone (`AppSurface.bottomChrome`), with one hand-rolled
 * pill that slides from tab to tab; `AppNavigationBar` stays on the level-2 `docked` tone the v1
 * layout reads correctly in, and stays byte-identical for every other caller.
 *
 * ## The tab change is a slide, not a morph
 * M3's own indicator cross-fades on a fixed 100ms tween, which at this size reads as a colour
 * appearing; the next version grew a pill out of each icon on the `spatialDefault` spring, and its
 * ~2% bounce read as wobble (owner, 2026-10-18). Now a single pill travels to the new tab on
 * `spatialDefaultTween` — the same decelerating curve with no overshoot — so the eye follows one
 * object from where it was to where it went. Each icon's tint follows the pill's coverage of its tab
 * (white exactly where the pill is under it); the labels, which sit below the pill, keep their own
 * `effectsDefault` fade.
 *
 * The shape (28dp top corners) and the shoulder backdrop are the PLATE's, not this bar's: clipping
 * here would round the corners between the pill and the tabs.
 *
 * ## Labels shrink, they never break mid-word
 * One line, auto-sized down to a `labelSmall` floor ([V2TabLabel]). The old two-line cap broke the
 * one-word labels inside the word ("Календа/рь") at fontScale 1.5.
 */
@Composable
private fun V2SplitNavigationBar(
    items: List<AppNavBarItem>,
    selectedItemId: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val idle = AppSurface.onBottomChrome()
    // The ACTIVE label is the same role at full strength — it sits on the chrome, not on the pill,
    // so it must not take the pill's content colour, and it must not stay dimmed either.
    val activeLabel = idle.copy(alpha = 1f)
    val accent = AppSurface.bottomChromeAccent()
    val onAccent = AppSurface.onBottomChromeAccent()

    val selectedIndex = items.indexOfFirst { it.id == selectedItemId }

    // ONE pill for the whole bar, sliding from the old tab to the new one (owner's pick 2026-10-18,
    // replacing a per-tab pill that grew out of each icon on the bouncy `spatialDefault` spring —
    // "мне не нравится анимация"). The value is a FRACTIONAL TAB INDEX, not an x: the draw below
    // turns it into pixels from the tabs' real measured centres, so unequal tabs, RTL (index 0 on
    // the right) and a window resize mid-flight all come out right without re-targeting anything.
    // Starts AT the selected tab, so a cold start shows the pill in place, never sliding in.
    val pillPosition = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
    // Where the pill was last shown; -1 = hidden (the current route is not one of these tabs).
    val lastShownIndex = remember { intArrayOf(selectedIndex) }
    val reducedMotion = LocalReducedMotion.current
    LaunchedEffect(selectedIndex) {
        val from = lastShownIndex[0]
        lastShownIndex[0] = selectedIndex
        if (selectedIndex < 0) return@LaunchedEffect // nothing to point at: the draw hides the pill
        val target = selectedIndex.toFloat()
        if (from < 0 || reducedMotion) {
            // Re-appearing after a non-tab route, or the user asked for less motion: land in place.
            pillPosition.snapTo(target)
        } else {
            // The tween twin of `spatialDefault`: its decelerating curve with NO overshoot — the
            // spring's ~2% bounce was the part that read as wobble. Interrupting mid-flight (a second
            // tap) re-targets from wherever the pill is. `animateTo` honours MotionDurationScale, so
            // animations switched off in the system also land at once.
            pillPosition.animateTo(target, AppMotion.spatialDefaultTween())
        }
    }
    // Each tab's icon centre in ROOT coordinates, plus the bar's own origin: the draw subtracts one
    // from the other. Written from `onGloballyPositioned`, which runs after layout and before draw of
    // the same frame, and only changes when something actually moves — never per animation frame.
    val iconCentres = remember(items.size) { List(items.size) { mutableStateOf(Offset.Unspecified) } }
    var barOrigin by remember { mutableStateOf(Offset.Zero) }

    // The pill's centre in ROOT coordinates, or Unspecified when there is nothing to point at.
    // Called only from draw lambdas, so every state it reads subscribes the DRAW, not composition.
    fun pillCentreInRoot(): Offset {
        if (selectedIndex < 0 || iconCentres.isEmpty()) return Offset.Unspecified
        val position = pillPosition.value.coerceIn(0f, iconCentres.lastIndex.toFloat())
        val lower = floor(position).toInt()
        val from = iconCentres[lower].value
        val to = iconCentres[ceil(position).toInt()].value
        if (!from.isSpecified || !to.isSpecified) return Offset.Unspecified
        return lerp(from, to, position - lower)
    }

    NavigationBar(
        // Transparent, and the chrome tone is painted by the modifier instead: the pill must sit
        // ABOVE the container and BELOW every tab, which only a draw on the bar's own node between
        // its background and its content can do. Same colour, same rect — identical pixels at rest.
        containerColor = Color.Transparent,
        contentColor = idle,
        tonalElevation = 0.dp,
        modifier = modifier
            .onGloballyPositioned { barOrigin = it.positionInRoot() }
            .background(AppSurface.bottomChrome())
            // Draw phase only: the slide reads `pillPosition` here and nowhere in composition, so
            // the bar does not recompose per frame. Nothing here is measured either — the bar's
            // height is read by the shell as the plate height, and the pill must never move it.
            .drawBehind {
                val centre = pillCentreInRoot()
                if (!centre.isSpecified) return@drawBehind
                val pill = pillRect(centre - barOrigin)
                drawRoundRect(
                    color = accent,
                    topLeft = pill.topLeft,
                    size = pill.size,
                    cornerRadius = CornerRadius(pill.height / 2f),
                )
            },
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = item.id == selectedItemId
            val traversal = index.toFloat()

            // The ICON colour follows the pill, not a clock, and per pixel: the part of the glyph the
            // pill is under is `onAccent`, the rest is `idle`, so the glyph is legible on whichever
            // surface each of its pixels sits on. On its own timer it was a ghost — white on the sand
            // chrome (dark: dark on the dark chrome) for the frames the departing pill had already
            // left it; a blended "coverage" tint was a ghost too, grey on both surfaces at once.
            // Painted in the DRAW phase from the vector itself; see the Icon below.
            val iconPainter = rememberVectorPainter(
                if (isSelected) item.selectedIcon else item.unselectedIcon,
            )
            val pillClip = remember { Path() }

            // The label sits under the pill, not on it, so it keeps its own EFFECT timing — the
            // critically damped token, because a colour that overshoots is a flicker. Both slots get
            // the SAME animated value on purpose: `NavigationBarItem` picks a slot by the `selected`
            // flag, so two different colours would snap on the frame the flag flips.
            val labelColor by animateColorAsState(
                targetValue = if (isSelected) activeLabel else idle,
                animationSpec = AppMotion.effectsDefaultAs(),
                label = "v2NavLabel",
            )

            NavigationBarItem(
                selected = isSelected,
                onClick = { onItemSelected(item.id) },
                icon = {
                    Icon(
                        painter = iconPainter,
                        contentDescription = item.contentDescription ?: item.label,
                        // Transparent: `Icon` keeps the size, the semantics and the slot, and paints
                        // nothing; the glyph is drawn by `drawBehind` below with the SAME painter,
                        // size and tint filter `Icon` uses, so at rest the pixels are its own.
                        tint = Color.Transparent,
                        modifier = Modifier
                            // Reports where the pill should sit under this tab: centred on the icon,
                            // the geometry M3's own indicator uses.
                            .onGloballyPositioned { coordinates ->
                                iconCentres[index].value = coordinates.positionInRoot() + Offset(
                                    x = coordinates.size.width / 2f,
                                    y = coordinates.size.height / 2f,
                                )
                            }
                            .drawBehind {
                                val pillRoot = pillCentreInRoot()
                                val me = iconCentres[index].value
                                val bounds = Rect(Offset.Zero, size)
                                val pill = if (pillRoot.isSpecified && me.isSpecified) {
                                    pillRect(pillRoot - me + size.center)
                                } else {
                                    null
                                }
                                fun glyph(color: Color) = with(iconPainter) {
                                    draw(size, colorFilter = ColorFilter.tint(color))
                                }
                                when {
                                    // At rest every icon takes one of these two branches, which
                                    // are exactly `Icon`'s own tinted draw.
                                    pill == null || !pill.overlaps(bounds) -> glyph(idle)
                                    pillCovers(pill, bounds) -> glyph(onAccent)
                                    else -> {
                                        pillClip.reset()
                                        pillClip.addRoundRect(
                                            RoundRect(pill, CornerRadius(pill.height / 2f)),
                                        )
                                        clipPath(pillClip, ClipOp.Difference) { glyph(idle) }
                                        clipPath(pillClip) { glyph(onAccent) }
                                    }
                                }
                            },
                    )
                },
                label = { V2TabLabel(text = item.label) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    // Transparent: the pill above is the indicator now. M3 still lays its own out,
                    // which is what keeps the icon in the same place as in a stock bar.
                    indicatorColor = Color.Transparent,
                    selectedTextColor = labelColor,
                    unselectedTextColor = labelColor,
                ),
                modifier = Modifier.semantics { traversalIndex = traversal },
            )
        }
    }
}

/**
 * A tab label that never breaks inside a word.
 *
 * Every tab label is one word ("Входящие", "Календарь", "कैलेंडर"), and a one-word label wrapped
 * to two lines is broken MID-WORD ("Входящи/е", "Календа/рь" at 360dp RU fontScale 1.5 — present
 * since the v2 bar shipped). So the label is single-line and steps its size DOWN to fit, floored at
 * `labelSmall` (which still scales with the user's font scale); only below that floor does it
 * ellipsize. The full word stays the accessible name: the icon's `contentDescription` and the
 * label's semantics both carry the untruncated text.
 */
@Composable
private fun V2TabLabel(text: String) {
    val style = MaterialTheme.typography.labelMedium
    Text(
        text = text,
        style = style,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = MaterialTheme.typography.labelSmall.fontSize,
            maxFontSize = style.fontSize,
        ),
    )
}

/** The active destination's pill — M3's own indicator size, so the resting bar is unchanged. */
private val PlinthPillWidth: Dp = 56.dp
private val PlinthPillHeight: Dp = 32.dp

/** The pill's rect centred on [centre], in whatever coordinate space [centre] is in. */
private fun Density.pillRect(centre: Offset): Rect {
    val halfWidth = PlinthPillWidth.toPx() / 2f
    val halfHeight = PlinthPillHeight.toPx() / 2f
    return Rect(
        left = centre.x - halfWidth,
        top = centre.y - halfHeight,
        right = centre.x + halfWidth,
        bottom = centre.y + halfHeight,
    )
}

/**
 * Whether the stadium-shaped [pill] covers the whole of [bounds] — the straight part of the rect, i.e.
 * inset at each end by how far the end cap curves in at the height [bounds] spans. Conservative: a
 * `false` only costs a clipped draw, a wrong `true` would paint a glyph corner white on the chrome.
 */
private fun pillCovers(pill: Rect, bounds: Rect): Boolean {
    val radius = pill.height / 2f
    val halfSpan = maxOf(abs(bounds.top - pill.center.y), abs(bounds.bottom - pill.center.y))
    if (halfSpan > radius) return false
    val capInset = radius - sqrt(radius * radius - halfSpan * halfSpan)
    return bounds.left >= pill.left + capInset && bounds.right <= pill.right - capInset
}

/**
 * Medium: NavigationRail carrying the same four destinations, chat FAB in the rail header.
 *
 * Structure copied from `AdaptiveShellRail` so the two arms feel identical at this size; the
 * destination set differs, and so does the scroll wrapper — this rail carries two FABs the control
 * arm's does not and therefore has to survive a short window. There is no bottom bar here, which is
 * why App.kt passes `0.dp` as the v2 content bottom padding on anything wider than Compact.
 */
@Composable
private fun V2ShellRail(
    selectedTab: String,
    onNavigate: (String) -> Unit,
    onOpenChat: () -> Unit,
    showCreateFab: Boolean,
    onOpenCreate: () -> Unit,
    overlayContent: (@Composable () -> Unit)?,
    content: @Composable (DrawerState?) -> Unit,
) {
    val inboxLabel = stringResource(Res.string.nav_tab_inbox)
    val calendarLabel = stringResource(Res.string.calendar_nav_label)
    val projectsLabel = stringResource(Res.string.nav_tab_projects)
    val overviewLabel = stringResource(Res.string.nav_tab_overview)
    val fabDescription = stringResource(Res.string.nav_chat_fab_content_description)
    val createFabDescription = stringResource(Res.string.nav_add_task_fab_content_description)

    Row(modifier = Modifier.fillMaxSize()) {
        // The rail scrolls, for the same reason AppNavigationDrawerContent does: two 56dp FABs plus
        // four labelled items need ~400dp, and a Medium window can be ~360dp tall (a small phone in
        // landscape), which clipped the last destinations with no way to reach them.
        //
        // The container colour is painted by THIS Box and not left to the rail's own Surface: the
        // Surface sits inside the scroll, so it is measured against the content height and would
        // leave the rest of the rail transparent. Consequence of scrolling: the rail's items are
        // top-aligned instead of vertically centred, because the rail now wraps its content — an
        // acceptable trade for chrome that is reachable at every height, and it puts the FABs where
        // M3 places a rail header anyway.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .verticalScroll(rememberScrollState()),
        ) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                header = {
                    // Same colour role as the Compact raised button — one action, one look. It used
                    // to be primaryContainer here and primary there, which made the flagship
                    // affordance change emphasis with the window width and, worse, ranked it BELOW
                    // the create button on the two sizes that show both.
                    FloatingActionButton(
                        onClick = onOpenChat,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = fabDescription)
                    }
                    // Kept at rail width on purpose: the redesign that replaced this button with an
                    // inline add-task row was scoped to Compact, and a rail is not a list — it has
                    // no row to put a capture in. Note the row DOES currently render at this width
                    // too (feature/home does not gate it by window size), so the Inbox tab offers two
                    // ways into the same dock on a tablet. That is a duplicated affordance, not a
                    // lost one; whichever way the owner settles it, this comment is the place the
                    // answer lands. Demoted to the CONTAINER role now that the AI button owns
                    // `primary`: the two stay distinguishable, and the order of emphasis matches the
                    // product's (chat first, manual create second).
                    if (showCreateFab) {
                        FloatingActionButton(
                            onClick = onOpenCreate,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = createFabDescription)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.width(56.dp))
                },
            ) {
                NavigationRailItem(
                    selected = selectedTab == V2Destination.Inbox,
                    onClick = { onNavigate(V2Destination.Inbox) },
                    icon = { Icon(Icons.Outlined.Inbox, contentDescription = null) },
                    label = { Text(inboxLabel) },
                )
                NavigationRailItem(
                    selected = selectedTab == V2Destination.Calendar,
                    onClick = { onNavigate(V2Destination.Calendar) },
                    icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                    label = { Text(calendarLabel) },
                )
                NavigationRailItem(
                    selected = selectedTab == V2Destination.Projects,
                    onClick = { onNavigate(V2Destination.Projects) },
                    icon = { Icon(Icons.Outlined.ChecklistRtl, contentDescription = null) },
                    label = { Text(projectsLabel) },
                )
                NavigationRailItem(
                    selected = selectedTab == V2Destination.Overview,
                    onClick = { onNavigate(V2Destination.Overview) },
                    icon = { Icon(Icons.Outlined.Apps, contentDescription = null) },
                    label = { Text(overviewLabel) },
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            content(null)
            // Inside the CONTENT box, not beside the rail: the dock and its scrim must stop at the
            // rail's edge so the navigation stays lit and tappable while the chat is open.
            overlayContent?.invoke()
        }
    }
}

/**
 * Expanded: always-visible permanent drawer.
 *
 * Carries two extras the Compact/Medium variants do not: Settings and Updates. On smaller windows
 * those are rows inside the Overview tab, but at this size the four tabs are the whole chrome and
 * an Overview visit to reach Settings would be a needless detour — the control arm's permanent
 * drawer exposes them directly too, so this keeps the arms comparable on desktop-class windows.
 */
@Composable
private fun V2ShellPermanent(
    selectedTab: String,
    onNavigate: (String) -> Unit,
    onOpenChat: () -> Unit,
    showCreateFab: Boolean,
    onOpenCreate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdates: () -> Unit,
    overlayContent: (@Composable () -> Unit)?,
    content: @Composable (DrawerState?) -> Unit,
) {
    val inboxLabel = stringResource(Res.string.nav_tab_inbox)
    val calendarLabel = stringResource(Res.string.calendar_nav_label)
    val projectsLabel = stringResource(Res.string.nav_tab_projects)
    val overviewLabel = stringResource(Res.string.nav_tab_overview)
    val settingsLabel = stringResource(Res.string.settings_title)
    val updatesLabel = stringResource(Res.string.update_feed_menu_item)
    val chatLabel = stringResource(Res.string.nav_chat_fab_content_description)
    val createLabel = stringResource(Res.string.nav_add_task_fab_content_description)
    val itemColors = NavigationDrawerItemDefaults.colors(
        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
        unselectedIconColor = MaterialTheme.colorScheme.onSurface,
    )

    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                // The whole sheet scrolls as one region, exactly as AppNavigationDrawerContent does
                // in the control arm. Two 56dp ExtendedFABs plus six rows overflow a SHORT Expanded
                // window — any phone rotated to landscape classifies as Expanded (e.g. 923x411dp),
                // and a large font scale does it on a real tablet — which clipped the bottom rows.
                // Updates and Settings are the ones that fall off, and Settings is the only route
                // back to the classic layout, so the clip made that switch unreachable entirely.
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    // primary/onPrimary, matching the rail header and the Compact raised button —
                    // see the rail for why the AI action holds the loudest role at every width.
                    ExtendedFloatingActionButton(
                        onClick = onOpenChat,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                        text = { Text(chatLabel) },
                        modifier = Modifier.padding(
                            start = AppDimens.SpacingLg,
                            end = AppDimens.SpacingLg,
                            top = AppDimens.SpacingMd,
                            bottom = if (showCreateFab) AppDimens.SpacingSm else AppDimens.SpacingMd,
                        ),
                    )
                    // Same reasoning as the rail, colour role and the duplicated-affordance caveat
                    // included: the inline-row redesign was scoped to Compact, a drawer has no list
                    // row of its own, and this button sits one step below the AI one in emphasis.
                    if (showCreateFab) {
                        ExtendedFloatingActionButton(
                            onClick = onOpenCreate,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text(createLabel) },
                            modifier = Modifier.padding(
                                start = AppDimens.SpacingLg,
                                end = AppDimens.SpacingLg,
                                bottom = AppDimens.SpacingMd,
                            ),
                        )
                    }
                    NavigationDrawerItem(
                        label = { Text(inboxLabel) },
                        icon = { Icon(Icons.Outlined.Inbox, contentDescription = null) },
                        selected = selectedTab == V2Destination.Inbox,
                        onClick = { onNavigate(V2Destination.Inbox) },
                        colors = itemColors,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                    NavigationDrawerItem(
                        label = { Text(calendarLabel) },
                        icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                        selected = selectedTab == V2Destination.Calendar,
                        onClick = { onNavigate(V2Destination.Calendar) },
                        colors = itemColors,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                    NavigationDrawerItem(
                        label = { Text(projectsLabel) },
                        icon = { Icon(Icons.Outlined.ChecklistRtl, contentDescription = null) },
                        selected = selectedTab == V2Destination.Projects,
                        onClick = { onNavigate(V2Destination.Projects) },
                        colors = itemColors,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                    NavigationDrawerItem(
                        label = { Text(overviewLabel) },
                        icon = { Icon(Icons.Outlined.Apps, contentDescription = null) },
                        selected = selectedTab == V2Destination.Overview,
                        onClick = { onNavigate(V2Destination.Overview) },
                        colors = itemColors,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = AppDimens.SpacingLg)
                    )

                    NavigationDrawerItem(
                        label = { Text(updatesLabel) },
                        icon = { Icon(Icons.Outlined.Campaign, contentDescription = null) },
                        // Never "selected": these push a detail route on top of the tab, they are not
                        // tabs themselves, so highlighting one would leave the active tab unmarked.
                        selected = false,
                        onClick = onOpenUpdates,
                        colors = itemColors,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                    NavigationDrawerItem(
                        label = { Text(settingsLabel) },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        selected = false,
                        onClick = onOpenSettings,
                        colors = itemColors,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content(null)
            // Inside the CONTENT box, not over the whole window: the permanent drawer must stay lit
            // and tappable while the chat is open — Settings and Updates live only there.
            overlayContent?.invoke()
        }
    }
}
