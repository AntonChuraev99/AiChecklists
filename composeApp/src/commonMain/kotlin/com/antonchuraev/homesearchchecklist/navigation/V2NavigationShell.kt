package com.antonchuraev.homesearchchecklist.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.calendar_nav_label
import aichecklists.core.designsystem.generated.resources.nav_add_task_fab_content_description
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
import com.antonchuraev.homesearchchecklist.desingsystem.components.inboxNavBarItem
import com.antonchuraev.homesearchchecklist.desingsystem.components.overviewNavBarItem
import com.antonchuraev.homesearchchecklist.desingsystem.components.projectsNavBarItem
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

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
 */
object V2ShellMetrics {
    /**
     * Diameter of the AI button that sits in the middle of the Compact bar (variant A).
     *
     * Full-size, not a 40dp small FAB: it is the single most important action in the whole chrome and
     * the only affordance that is present on every tab.
     */
    val AiButtonSize: Dp = 56.dp

    /**
     * How far the button's TOP edge rises above the bar's top edge — the "raised" part of a raised
     * centre button.
     *
     * 22 of the 56dp overhang, i.e. the circle keeps 34dp inside the bar. Enough that it visually
     * belongs to the bar (it is not a detached floating button) while still breaking its silhouette,
     * which is what makes it read as the primary action rather than a fifth tab. This number is also
     * the whole content reserve — see [FabBandPadding].
     */
    val AiButtonOverhang: Dp = 22.dp

    /**
     * The 2+2 split: total empty width the bar leaves in its middle, and the width of the button's
     * hit area.
     *
     * 76dp against a 56dp circle = 10dp of clearance per side. The hit area spans the WHOLE gap on
     * purpose: a near-miss beside the circle lands in a strip where every other pixel is a target, so
     * "nothing happened" would be the only silent response in the entire bar.
     */
    val AiButtonGapWidth: Dp = 76.dp

    /**
     * Width of the spacer that actually opens the gap inside the bar.
     *
     * M3's `NavigationBar` lays its items out with `Arrangement.spacedBy(8.dp)`, so a spacer placed
     * between two items already gets 8dp on each side. Subtracting those 16dp is what makes the
     * VISIBLE gap equal [AiButtonGapWidth] instead of overshooting it to 92dp.
     *
     * ## Verified, not remembered
     * This is an INTERNAL M3 constant, so it was read off the artifact this project actually resolves
     * rather than from memory: `androidx.compose.material3:material3-android:1.5.0-alpha17` (what CMP
     * 1.11.0 pulls in), `commonMain/androidx/compose/material3/NavigationBar.kt` — the default
     * `NavigationBar` override lays its `Row` out with
     * `horizontalArrangement = Arrangement.spacedBy(NavigationBarItemHorizontalPadding)` (line 156)
     * and `internal val NavigationBarItemHorizontalPadding: Dp = 8.dp` (line 760). The same file also
     * confirms the two other numbers this shell depends on: the bar's `defaultMinSize(minHeight =
     * NavigationBarHeight)` where `NavigationBarHeight = NavigationBarTokens.TallContainerHeight =
     * 80.dp` (= [AppDimens.BottomBarHeight]), and `placeLabelAndIcon`, which sizes an item from its
     * CONTENT height — i.e. a wrapped label really does make the bar taller than 80dp, which is why
     * the button's offset is measured rather than assumed.
     *
     * A bump of the material3 version invalidates the 16dp, and the failure is not visual but
     * behavioural: the hit area is [AiButtonGapWidth] wide, so a narrower real gap would put it over
     * the inner edges of Calendar and Projects and swallow their taps. That is pinned by
     * `V2ShellAiButtonTest.compactShell_tapOnTheInnerEdgeOfANeighbourTab_navigates_insteadOfOpeningChat`,
     * which fails on a composed tree instead of leaving the drift to be found in production.
     */
    val AiButtonBarSpacer: Dp = AiButtonGapWidth - 16.dp

    /**
     * TalkBack traversal position of the AI button.
     *
     * ## What this actually guarantees — and what it does not
     * `traversalIndex` sorts SIBLINGS inside the nearest traversal group. The button is a sibling of
     * the bar's `Column`, not of the four `NavigationBarItem`s (they sit two levels deeper), so this
     * 2f is compared against the Column — not against the per-item indices — and the resulting order
     * is "the four destinations, then the AI button". It is deliberately NOT the interleaved order an
     * earlier revision of this comment claimed: putting the button between Calendar and Projects would
     * require it to be a CHILD of the bar, which is exactly what it cannot be (see
     * [V2NavigationShell]'s Compact variant — a rectangle-clipped `Surface` swallows the overhang).
     *
     * The value is kept, rather than dropped, because it makes the fallback deterministic: without it
     * the geometric heuristic could read the button FIRST (its top edge is the highest node of the
     * bottom chrome). "Primary action announced after the tabs it belongs to" is a predictable order;
     * "sometimes first, sometimes last" is not.
     *
     * Note it is Android-only semantics — a no-op on wasmJs and iOS, both of which render this shell —
     * and the actual TalkBack walk has not been verified on a device.
     */
    const val AiButtonTraversalIndex: Float = 2f

    /**
     * The ONLY bottom reserve a Compact v2 tab screen needs: the part of the AI button that overhangs
     * the content, plus one 8dp breathing gap.
     *
     * The bar needs no reservation because [V2NavigationShell] renders it OUTSIDE the content slot
     * (`Column { Box(weight(1f)) { content() }; bar }`) — the content box already ends at the bar's
     * top edge — and because that same box CONSUMES `WindowInsets.navigationBars` while the bar is
     * visible, so a hosted screen cannot reserve the system strip twice either.
     *
     * ## Why this is now ~30dp and not 64 / 132
     * Two earlier constants are gone with the right-hand floating stack that justified them:
     * `FabBandPadding = 64dp` (one 56dp FAB + 8dp) and `FabStackBandPadding = 132dp` (AI + "+" +
     * gaps). Nothing floats over the bottom-right corner any more — capture is an inline row in the
     * list — so the only chrome still overlapping the content is the 22dp of circle poking above the
     * bar, in the middle. A single value for every Compact tab is the whole rule; a second, larger
     * reserve is exactly the mistake that once left a blank ~88dp band under the last card on
     * Projects.
     */
    val FabBandPadding: Dp = AiButtonOverhang + AppDimens.SpacingSm
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
 * - Compact (<600dp): bottom navigation bar whose four destinations are split 2+2 around a raised
 *   56dp AI button in the middle (variant A). Nothing floats in the bottom-right corner any more.
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
 *   raised centre circle on Compact, the rail header / drawer button on larger windows) is the only
 *   chat affordance on a tab screen. It opens the chat IN PLACE via [overlayContent], over whatever
 *   screen the user is on.
 * @param showCreateFab whether the manual "+" create FAB is offered alongside the AI one. **Medium
 *   and Expanded only** — Compact dropped the floating create button entirely (capture is an inline
 *   add-task row inside the list now), so this flag is inert there. True only where there is a task
 *   list to add to (the Inbox tab and its project pages).
 * @param onOpenCreate tapped "+". The host opens its create surface; the shell owns no create state.
 * @param chatOpen whether the host's chat dock is currently up (and not already on its way out — the
 *   host drops this at the START of the dock's exit so the two movements overlap). Compact-only, and
 *   purely for MOTION: the raised AI button stays composed while it is true so it can hand off to the
 *   dock (scale up + fade) instead of being cut mid-frame. It is drawn UNDER [overlayContent], so the
 *   dock's scrim takes every touch while this is true, and the button drops out of the a11y tree.
 * @param captureOpen whether the host's quick-capture dock is up. Also motion, and NOT the same
 *   movement as [chatOpen]: capture dims nothing, so the bar stays fully visible under it. The button
 *   therefore does not fade — it sinks the [V2ShellMetrics.AiButtonOverhang] back into the bar, which
 *   both keeps the 2+2 gap filled (an empty 76dp notch is the one thing the split must never look
 *   like) and keeps the circle off the capture input it would otherwise be drawn over. It stays
 *   tappable there: a tap swaps capture for chat, which is what the host's own onOpenChat does.
 *   On Medium/Expanded it hides the rail / drawer create button instead — that one really would float
 *   over its own dock.
 * @param onOpenSettings / [onOpenUpdates] Expanded-only extras — destinations that are drawer items
 *   in the control arm and Overview-tab rows on Compact/Medium.
 * @param barVisible false hides the bar and the AI button while a non-tab route (ChecklistDetail,
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
 * Compact: bottom navigation bar split 2+2 around a raised 56dp AI button (variant A).
 *
 * ## Why the button is a SIBLING of the bar and not one of its children
 * It overhangs the bar's top edge by [V2ShellMetrics.AiButtonOverhang], and Compose does not deliver
 * touch events outside a parent's bounds — `NavigationBar` is a rectangle-clipped `Surface`, so a
 * circle parented to it would render clipped AND swallow every tap on the overhanging half. Drawn
 * here, as the child of the shell's own Box that follows the Column, it is both fully visible and
 * fully tappable, and it stays UNDER [overlayContent] so the chat's scrim takes over the moment the
 * dock opens.
 *
 * ## Why the offset is measured, not `AppDimens.BottomBarHeight`
 * The bar is `defaultMinSize(minHeight = 80.dp)` plus its own `WindowInsets.navigationBars`, and M3
 * sizes each item from its CONTENT (`placeLabelAndIcon`), so at `fontScale ≥ 1.3` — or in a locale
 * whose labels wrap — it is taller than that minimum. Everything that has to line up with the bar is
 * therefore sized from its REAL measured height (`onSizeChanged`): the button's hit area AND the
 * inset the content box consumes.
 *
 * The seed is `BottomBarHeight + WindowInsets.navigationBars`, not the bare 80dp constant. Insets are
 * measured from the bottom of the WINDOW and the bar applies its own, so on a device with 3-button
 * navigation the bare constant is up to 48dp short — the circle drew that far below its place on the
 * first frame and jumped once `onSizeChanged` landed. With the inset in the seed the common path
 * measures exactly what it guessed, so the state is never written and the shell never recomposes for
 * it; a wrapped label still costs the one recomposition it always did.
 *
 * ## Where the hit area stops
 * It spans the full gap and the full bar HEIGHT, bottom-aligned so it also covers the gesture strip
 * the bar paints — a tap anywhere in the middle of the bar opens the chat, which is the only way a
 * raised circle in a strip of targets never produces a silent miss. It deliberately does NOT extend
 * over the overhang: `Modifier.clickable` installs a pointer-input node that does not share events
 * with siblings, so a hit area covering the 22dp above the bar would eat every gesture STARTED in the
 * bottom-centre strip of the content — including the flick that scrolls the list, begun exactly where
 * a thumb rests. Above the bar only the circle itself answers, which is what any FAB does.
 */
@Composable
private fun V2ShellCompactBar(
    selectedTab: String,
    onNavigate: (String) -> Unit,
    onOpenChat: () -> Unit,
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
    val aiDescription = stringResource(Res.string.nav_chat_fab_content_description)

    val density = LocalDensity.current
    // Seeded with the inset included — see the KDoc: the bare 80dp constant is short by the whole
    // gesture/button strip on the first frame, which is what made the circle jump into place.
    val seedBarHeightPx = with(density) { AppDimens.BottomBarHeight.roundToPx() } +
        WindowInsets.navigationBars.getBottom(density)
    var barHeightPx by remember(seedBarHeightPx) { mutableIntStateOf(seedBarHeightPx) }
    val barHeight = with(density) { barHeightPx.toDp() }

    // 0 = button at rest, 1 = fully handed over to the chat dock. Asymmetric by design: the button is
    // the EXITING element of the transition (M3 emphasized-accelerate, and shorter than the dock's
    // entrance, so it is gone before the dock finishes growing), and it comes back on a short
    // decelerate as soon as the dock BEGINS its own exit (the host drops chatOpen there, not at the
    // end of it), so the two overlap the way they do on the way in. Duration 0 when the platform's
    // animator scale is off — `animateFloatAsState` reads MotionDurationScale from the composition's
    // coroutine context, so "system animations disabled" lands here for free.
    val handoff by animateFloatAsState(
        targetValue = if (chatOpen) 1f else 0f,
        animationSpec = if (chatOpen) {
            tween(V2ChatMotion.ButtonHandoffMs, easing = V2ChatMotion.ExitEasing)
        } else {
            tween(V2ChatMotion.ButtonReturnMs, easing = V2ChatMotion.EnterEasing)
        },
        label = "v2AiButtonHandoff",
    )

    // 0 = raised, 1 = sunk flush into the bar. The quick-capture dock's answer to the same question
    // the chat answers with `handoff`, and a different one because capture dims nothing: the bar stays
    // in full view under it, so fading the circle out would leave a 76dp hole in the middle of a bar
    // the user is looking at, and leaving it raised would draw it across the capture input (the dock
    // sits in the scaffold's bottomBar slot, i.e. its bottom edge IS the bar's top edge). Sinking it
    // solves both, on the same clocks as the chat hand-off so the bottom chrome has one motion vocabulary.
    val tuckAway by animateFloatAsState(
        targetValue = if (captureOpen) 1f else 0f,
        animationSpec = if (captureOpen) {
            tween(V2ChatMotion.ButtonHandoffMs, easing = V2ChatMotion.ExitEasing)
        } else {
            tween(V2ChatMotion.ButtonReturnMs, easing = V2ChatMotion.EnterEasing)
        },
        label = "v2AiButtonTuck",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // While the bar is on screen it OWNS the navigation-bar inset: it renders below
                    // this box and applies the inset itself. Consuming it here stops the hosted screen
                    // from reserving the same strip a second time — AppScaffold, its bottomBar slot and
                    // MainScreen's own navBottom arithmetic all read WindowInsets.navigationBars, so
                    // without this the reserve stacked and left a dead band (bar height + inset) under
                    // the last row. Not consumed when the bar is hidden: then nothing covers the
                    // system nav and the screen must inset itself as usual.
                    //
                    // The BAR'S OWN HEIGHT is consumed too, and that part is about the KEYBOARD.
                    // Insets are measured from the bottom of the WINDOW, but this box stops one bar
                    // higher — so a hosted screen lifting its bottomBar by the raw `ime` inset lifts
                    // it by a bar height too much and strands the input in a blank band above the
                    // keyboard (seen live on the Inbox capture dock). Consuming the bar states the
                    // truth: that much of any bottom inset is already accounted for by this box
                    // ending where it does.
                    //
                    // The MEASURED height, not `navigationBars + BottomBarHeight`. The two agree on
                    // the common path (the bar is 80dp plus exactly that inset), and they stop
                    // agreeing at fontScale ≥ 1.3, where a wrapped label makes the bar taller — the
                    // very case the button's offset is measured for. Two answers to "how tall is the
                    // bar" in one composable is how one of them silently rots.
                    .then(
                        if (barVisible) {
                            Modifier.consumeWindowInsets(WindowInsets(bottom = barHeight))
                        } else {
                            Modifier
                        }
                    ),
            ) {
                content(null)
            }
            if (barVisible) {
                V2SplitNavigationBar(
                    items = items,
                    selectedItemId = selectedTab,
                    onItemSelected = onNavigate,
                    // The bar rounds its own top ONLY when it is the topmost bottom-chrome surface.
                    // With the capture dock up, the dock's rounded top is the slab's top and the
                    // bar's corners are not merely redundant, they cut a notch: the dock's bottom
                    // edge is square and full-bleed, the bar's corner curves away from it, and the
                    // ~10px triangle between them shows the PAGE. That notch is older than this
                    // change — it measured 7px against the 24dp corner — but it was invisible while
                    // the dock was #FFFFFF and the page #FBFAF8 (ΔL* 1.7). One shared grey makes it
                    // a bright nick in the seam the owner asked to be smooth (ΔL* 10.5), so the
                    // geometry has to go rather than the colour.
                    //
                    // Gated on the SAME flag that mounts the dock and moves the shadow band, so the
                    // corner cannot lag the dock by a frame in either direction.
                    roundedTop = !captureOpen,
                    modifier = Modifier.onSizeChanged { barHeightPx = it.height },
                )
            }
        }

        // The plinth's shadow: an OVERLAY anchored to the bar's top edge, not a band inside the bar.
        //
        // Both halves of that matter. It has to be outside the measured node — the shell reads the
        // bar's height to place the raised button and to consume the bottom inset exactly once, and
        // 16dp of band inside it would lift the button off the bar's edge and put the first-frame
        // jump back (see this function's KDoc). And it has to be drawn OVER the content rather than
        // reserving space above it, because that is what a shadow is: the list scrolls under it and
        // is shaded by it, instead of stopping 16dp short of a bar it is already stopping short of.
        //
        // No pointer input, so the strip stays transparent to touch and the list keeps every gesture
        // that starts there — the same rule the AI button's positioning box follows.
        //
        // ⛔ NOT while the capture dock is up. Being drawn after the content column is what lets this
        // shade a scrolling list, and it is also what makes it shade a RAISED surface that happens to
        // occupy the same band: with the dock open it painted a 7% ramp across the bottom of the AI
        // pill row (measured 241 → 233 on the pill fill), which reads as the dock sliding UNDER the
        // bar — the opposite of what a raised dock is. There is nothing to separate in that state
        // either: the dock is full-bleed to the bar's top edge and brings its own top hairline, so the
        // band it would shade contains no content at all.
        //
        // Gated on the same flag the host uses to mount the dock (`v2CreateDockOpen` drives both), so
        // the shadow leaves and returns on the dock's own frames and cannot lag it. The CHAT dock
        // needs no such gate — it arrives via `overlayContent`, which is drawn after this.
        if (barVisible && !captureOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = barHeight)
                    .height(AppSurface.bottomChromeShadowHeight())
                    .background(AppSurface.bottomChromeShadow()),
            )
        }

        // Composed for as long as the bar is — no dock unmounts it. Both docks are MOTION states
        // here, not visibility ones: cutting the circle out of the tree on the frame a dock opens is
        // the "jump" the whole V2ChatMotion table exists to remove, and while the capture dock is up
        // it would also leave the 2+2 split as a bare 76dp notch in a bar the user is still looking
        // at (capture draws no scrim). While the CHAT is open the button is unreachable anyway — the
        // dock's scrim is drawn after it and takes every touch — and `clearAndSetSemantics` takes it
        // out of the a11y tree with it, so "open" costs nothing but the outgoing animation.
        if (barVisible) {
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(V2ShellMetrics.AiButtonGapWidth)
                    // Tall enough to POSITION the circle above the bar. This box installs no pointer
                    // input of its own, so the strip beside the circle stays transparent to touch and
                    // the list underneath keeps every gesture that starts there.
                    .height(barHeight + V2ShellMetrics.AiButtonOverhang)
                    // Outermost on purpose: it wipes the click action and the description below it,
                    // which is exactly what "the dock owns the screen now" should mean to TalkBack.
                    .then(if (chatOpen) Modifier.clearAndSetSemantics { } else Modifier),
            ) {
                // The gap filler: the WHOLE 2+2 split, and only as tall as the bar. Bottom-aligned so
                // it also covers the strip the bar paints over the gesture nav — a near-miss beside
                // the circle lands on a target instead of on nothing, which is the only reason the
                // hit area is wider than the circle at all.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(barHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            // No ripple on the gap: the visible feedback is the FAB's own state layer
                            // plus the dock rising. A ripple over a 76dp transparent rectangle
                            // sitting on the bar reads as a rendering glitch.
                            indication = null,
                            role = Role.Button,
                            onClick = onOpenChat,
                        )
                        .semantics {
                            contentDescription = aiDescription
                            traversalIndex = V2ShellMetrics.AiButtonTraversalIndex
                        },
                )
                FloatingActionButton(
                    onClick = onOpenChat,
                    shape = CircleShape,
                    // The loudest role available ON THE BOTTOM CHROME — the same pair the active
                    // tab's pill takes, which is why it is the chrome token and not `primary` spelled
                    // out here. With the ink plinth the token had to branch (`primary` #1565C0
                    // measured 2.6:1 on #322F35, a blue smudge); on the grey chrome it resolves to
                    // the ordinary `primary` in BOTH themes — 4.19:1 light, 9.75:1 dark. The
                    // indirection stays because the next re-tune of the chrome must not have to find
                    // this call site again.
                    //
                    // The rail and the drawer keep reading `primary` directly: they are not on the
                    // bottom chrome, and one action reading "loudest available here" at every width
                    // is the rule, not one literal role. The CIRCLE shape stays Compact-only on
                    // purpose — it exists to break the bar's silhouette, and a rail header has none.
                    containerColor = AppSurface.bottomChromeAccent(),
                    contentColor = AppSurface.onBottomChromeAccent(),
                    modifier = Modifier
                        .size(V2ShellMetrics.AiButtonSize)
                        // Read in the DRAW phase (lambda form), so neither the 180ms hand-off nor the
                        // sink recomposes the shell — and with it the hosted screen.
                        .graphicsLayer {
                            val scale = lerp(1f, V2ChatMotion.ButtonHandoffScale, handoff)
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - handoff
                            translationY = V2ShellMetrics.AiButtonOverhang.toPx() * tuckAway
                        }
                        // The hit area above is the one node TalkBack and the tests see; leaving the
                        // FAB's own button semantics in place would announce the AI entry point
                        // twice and make `onNodeWithContentDescription` ambiguous. Its POINTER input
                        // survives this, which is what keeps the overhanging half tappable.
                        .clearAndSetSemantics { },
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                }
            }
        }

        // Last child of the same fillMaxSize Box the bar and the button live in, so the dock keeps
        // covering both — on Compact they float OVER the content and a dock drawn under them would
        // be poked through by taps that do nothing.
        overlayContent?.invoke()
    }
}

/**
 * The Compact bar itself: the four v2 destinations, split 2+2 around the gap the raised AI button
 * occupies.
 *
 * ## Why this is not `AppNavigationBar`
 * It is the same component, item-for-item — same `AppNavBarItem` descriptors from designsystem, same
 * `alwaysShowLabel`, same M3 container — with two differences the shared wrapper cannot express. The
 * structural one: a `Spacer` in the middle of its `RowScope` content. `NavigationBarItem` applies
 * `weight(1f)` to itself, so the four items split whatever the spacer leaves; there is no modifier a
 * caller can pass to `AppNavigationBar` that produces a centre gap. The visual one: this bar is the
 * app's bottom EDGE and paints the shared bottom chrome (`AppSurface.bottomChrome`), while `AppNavigationBar`
 * stays on the level-2 `docked` tone that reads correctly in the v1 layout. Keeping the shared
 * component untouched also keeps it byte-identical for every other caller, which is the same reason
 * the v2 shell duplicates `guardedNavigate` instead of extracting it.
 *
 * `traversalIndex` is set per item to keep the four in visual order; slot 2 is left free for the AI
 * button, though see [V2ShellMetrics.AiButtonTraversalIndex] for what that does and does not buy.
 *
 * ## Labels wrap, they are not truncated
 * They were `maxLines = 1, overflow = Ellipsis` for one revision, on the reasoning that the 76dp gap
 * leaves ~61dp per item on a 320dp window and an unconstrained label would grow the bar. It grows the
 * bar — that is the correct outcome, and it is measured (see [V2ShellCompactBar]). Truncating instead
 * turns "Календарь", "Проекты" and "कैलेंडर" into "Кал…" at fontScale 1.3, i.e. the treatment arm
 * ships an unreadable bar against a control arm that wraps the same words: a legibility difference
 * the A/B would then charge to the redesign. Two lines is the cap — `AppNavigationBar` has none, but
 * it also has no gap eating 76dp of the row, and a three-line item would push the bar past a third of
 * a short window.
 */
@Composable
private fun V2SplitNavigationBar(
    items: List<AppNavBarItem>,
    selectedItemId: String,
    onItemSelected: (String) -> Unit,
    roundedTop: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val gapAfter = items.size / 2
    // Level 2′ "bottomChrome", NOT the `docked` tone AppNavigationBar uses — see
    // AppSurface.bottomChrome for the measurements that separate the two, and for why the capture
    // dock and the chat dock now read the very same accessor. `modifier` (the host's onSizeChanged) is on the
    // NavigationBar itself and on nothing else: the number the shell wants is the height of the
    // OPAQUE slab, because that is what it positions the raised AI button off and what it consumes
    // from the bottom inset. The shadow band above the bar is drawn by the shell as an overlay for
    // exactly that reason — 16dp of it inside this node would push the button off the bar's edge.
    val idle = AppSurface.onBottomChrome()
    // The ACTIVE label is the same role at full strength — it sits on the chrome, not on the pill,
    // so it must not take the pill's content colour, and it must not stay dimmed either.
    val activeLabel = idle.copy(alpha = 1f)
    val accent = AppSurface.bottomChromeAccent()
    val onAccent = AppSurface.onBottomChromeAccent()

    NavigationBar(
        containerColor = AppSurface.bottomChrome(),
        contentColor = idle,
        tonalElevation = 0.dp,
        // Clipped, not shaped by a Surface: `NavigationBar` gives no shape parameter, and the top
        // corners are the whole reason the bar reads as a slab the page slides behind rather
        // than as a full-bleed footer. Only the TOP two — the bottom edge is the window's.
        //
        // The radius is the SHARED bottom-chrome token, not a local constant: the capture dock lands
        // on this bar's top edge, so the two have to agree on the shape of the edge they share.
        // [roundedTop] is false while the dock owns that edge — see the call site.
        //
        // ## The backdrop is not optional, and it must sit BEFORE the clip
        // Every other `SheetTop` surface in the app is a shaped `Surface` drawn over content, so its
        // clipped shoulders reveal the page behind it. This bar has nothing behind it: it is the last
        // child of the shell's Column and the content box above ends at its top edge, so the clip cut
        // a hole straight through the composition. Measured on the recorded goldens at x=2, two rows
        // below the bar's top edge, the shoulders read `#FAFAFA` in BOTH themes — Robolectric's window
        // backdrop, i.e. a colour this app never chose. On a device it is `android:windowBackground`
        // (cream `#FBFAF8`, a bright nick beside the `#DEDCD6` chrome — what the owner reported as
        // "по краям нижнего бара проглядывается белый цвет, видимо задник"); on wasmJs there is no
        // `windowBackground` to fall back to at all.
        //
        // `background(…).clip(…)` and never the reverse: in a modifier chain the LEFT element wraps
        // the right, so the backdrop is drawn across the node's full rectangle and the clip then
        // applies only to what follows it — the bar's own `Surface`. Swap the two and the backdrop is
        // clipped as well, which paints the hole a second time.
        //
        // Painted here rather than as an underlay `Box` in [V2ShellCompactBar] on purpose: a sibling
        // would have to be sized from `barHeight`, and that state lags the real bar by one frame
        // whenever a label wraps (fontScale ≥ 1.3), flashing the hole on exactly the frame the bar
        // grows. As a modifier it shares the bar's bounds by construction, at every font scale, and it
        // adds no layout node — `onSizeChanged` still measures the same box, so the raised AI button's
        // offset and the consumed bottom inset are untouched.
        //
        // Paired with [roundedTop] rather than applied unconditionally, and the reason is that there
        // is nothing to reveal in the square state — not that it costs less. Both branches would draw
        // the same full-bar rectangle; what differs is whether any of it survives the clip. With the
        // capture dock up the bar covers its own bounds completely, so a backdrop there fills a hole
        // that does not exist, and `V2BarShoulderFillTest` pins that state precisely so a future
        // change cannot round these corners under the dock and re-open the hole.
        //
        // Drawn across the WHOLE node rather than only in the two 28dp corner sectors it can actually
        // show through. A `drawBehind` narrowed to those sectors would have to re-derive the arc from
        // `SheetTop`'s radius, i.e. keep a second copy of the shape — the same "the probe has to know
        // the radius" fragility the shoulder test refuses for its own scan. The cost of not doing that
        // is one opaque rect per frame, immediately overpainted by the bar's own Surface; the cost of
        // doing it is a radius that can drift out from under the clip silently.
        modifier = if (roundedTop) {
            modifier
                .background(AppSurface.bottomChromeShoulder())
                .clip(AppShapeTokens.SheetTop)
        } else {
            modifier
        },
    ) {
        items.forEachIndexed { index, item ->
            if (index == gapAfter) {
                Spacer(modifier = Modifier.width(V2ShellMetrics.AiButtonBarSpacer))
            }
            val isSelected = item.id == selectedItemId
            // Items after the gap shift one slot down the traversal order to leave room for the
            // button; see V2ShellMetrics.AiButtonTraversalIndex.
            val traversal = (index + if (index >= gapAfter) 1 else 0).toFloat()

            // 0 = no pill, 1 = full pill. The bar's only movement, and the reason it is hand-rolled
            // rather than left to M3's `indicatorColor`: M3 cross-fades its indicator on a fixed
            // 100ms tween, which at this size reads as a colour appearing rather than as a shape
            // arriving. `spatialDefault` is the app's token for "something moved into place", and a
            // spring is also what makes the tab change feel answered rather than switched.
            val pill by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = AppMotion.spatialDefault,
                label = "v2NavPill",
            )
            // Colour is an EFFECT, so it rides the critically damped token: an icon colour that
            // overshoots is a flicker. Both the selected and unselected slots receive the SAME
            // animated value on purpose — `NavigationBarItem` picks between its two colour slots by
            // the `selected` flag, so handing it two different colours makes the transition snap on
            // the frame the flag flips, whatever spec is attached to it.
            val iconColor by animateColorAsState(
                targetValue = if (isSelected) onAccent else idle,
                animationSpec = AppMotion.effectsDefaultAs(),
                label = "v2NavIcon",
            )
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
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.contentDescription ?: item.label,
                        // Painted BEHIND the icon and deliberately outside its bounds: Compose does
                        // not clip a node's drawing to its layout size, so the pill costs nothing in
                        // measurement — and measurement is exactly what must not move here (the bar's
                        // height is read by the shell to place the raised AI button). Same geometry
                        // M3's own indicator uses, so at rest the bar is pixel-identical to a stock
                        // one; only the motion differs. Both reads happen in the DRAW phase, so the
                        // spring animates without recomposing the bar.
                        modifier = Modifier.drawBehind {
                            val progress = pill
                            if (progress <= 0f) return@drawBehind
                            val height = PlinthPillHeight.toPx()
                            val width = PlinthPillWidth.toPx() * progress
                            drawRoundRect(
                                color = accent,
                                topLeft = Offset(
                                    x = (size.width - width) / 2f,
                                    y = (size.height - height) / 2f,
                                ),
                                size = Size(width, height),
                                cornerRadius = CornerRadius(height / 2f),
                            )
                        },
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    // Transparent: the pill above is the indicator now. M3 still lays its own out,
                    // which is what keeps the icon in the same place as in a stock bar.
                    indicatorColor = Color.Transparent,
                    selectedIconColor = iconColor,
                    unselectedIconColor = iconColor,
                    selectedTextColor = labelColor,
                    unselectedTextColor = labelColor,
                ),
                modifier = Modifier.semantics { traversalIndex = traversal },
            )
        }
    }
}

/** The active destination's pill — M3's own indicator size, so the resting bar is unchanged. */
private val PlinthPillWidth: Dp = 56.dp
private val PlinthPillHeight: Dp = 32.dp

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
