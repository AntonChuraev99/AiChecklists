package com.antonchuraev.homesearchchecklist.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.calendar_nav_label
import aichecklists.core.designsystem.generated.resources.nav_chat_fab_content_description
import aichecklists.core.designsystem.generated.resources.nav_tab_inbox
import aichecklists.core.designsystem.generated.resources.nav_tab_overview
import aichecklists.core.designsystem.generated.resources.nav_tab_projects
import aichecklists.core.designsystem.generated.resources.settings_title
import aichecklists.core.designsystem.generated.resources.update_feed_menu_item
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppNavigationBar
import com.antonchuraev.homesearchchecklist.desingsystem.components.calendarNavBarItem
import com.antonchuraev.homesearchchecklist.desingsystem.components.inboxNavBarItem
import com.antonchuraev.homesearchchecklist.desingsystem.components.overviewNavBarItem
import com.antonchuraev.homesearchchecklist.desingsystem.components.projectsNavBarItem
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
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
     * The ONLY bottom reserve a Compact v2 tab screen needs: the floating chat FAB, which is the only
     * chrome that actually overlaps the content (FAB 56 + gap 8).
     *
     * The bar needs no reservation because [V2NavigationShell] renders it OUTSIDE the content slot
     * (`Column { Box(weight(1f)) { content() }; AppNavigationBar() }`) — the content box already ends
     * at the bar's top edge — and because that same box now CONSUMES `WindowInsets.navigationBars`
     * while the bar is visible, so a hosted screen cannot reserve the system strip twice either.
     *
     * There used to be a second, larger constant (bar + gap + FAB + gap = 152dp) for the screens that
     * pass `contentExtendsBehindNavBar = true`. It was wrong on both counts: that flag only disables
     * AppScaffold's inset, it does not make the content box grow past the bar — so the extra bar
     * height became a permanently blank ~88dp band under the last card, on top of the screen's own
     * `navBottom`. One value, applied to every Compact tab, is the whole rule now.
     */
    val FabBandPadding: Dp = 64.dp
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
 * - Compact (<600dp): bottom [AppNavigationBar] + a chat FAB floating 8dp above it.
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
 * @param onOpenChat the AI chat entry point. In v2 the bottom chat dock is gone, so this FAB (rail
 *   header / drawer button on larger windows) is the only chat affordance on a tab screen.
 * @param onOpenSettings / [onOpenUpdates] Expanded-only extras — destinations that are drawer items
 *   in the control arm and Overview-tab rows on Compact/Medium.
 * @param barVisible false hides the bar and the FAB while a non-tab route (ChecklistDetail, AiChat,
 *   Settings…) is on top of the stack, so the chrome never floats over a detail screen. The shell
 *   itself stays mounted, which is what keeps the tab state alive underneath.
 * @param content the NavDisplay renderer; always receives `null` for the drawer state.
 */
@Composable
fun V2NavigationShell(
    selectedTab: String,
    onNavigate: (destination: String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdates: () -> Unit,
    barVisible: Boolean = true,
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

    when (rememberAppWindowSizeClass()) {
        AppWindowSizeClass.Compact -> V2ShellCompactBar(
            selectedTab = selectedTab,
            onNavigate = ::guardedNavigate,
            onOpenChat = onOpenChat,
            barVisible = barVisible,
            content = content,
        )

        AppWindowSizeClass.Medium -> V2ShellRail(
            selectedTab = selectedTab,
            onNavigate = ::guardedNavigate,
            onOpenChat = onOpenChat,
            content = content,
        )

        AppWindowSizeClass.Expanded -> V2ShellPermanent(
            selectedTab = selectedTab,
            onNavigate = ::guardedNavigate,
            onOpenChat = onOpenChat,
            onOpenSettings = onOpenSettings,
            onOpenUpdates = onOpenUpdates,
            content = content,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal layout variants
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Compact: bottom navigation bar + floating chat FAB.
 *
 * The FAB is a sibling of the Column (not a Scaffold `floatingActionButton`) so its offset can be
 * expressed against the bar's own height. Inset order matters: `navigationBarsPadding()` is applied
 * FIRST (outermost), then the bar-height offset — the reverse would place the FAB inside the
 * gesture-nav strip on devices with a non-zero bottom inset.
 */
@Composable
private fun V2ShellCompactBar(
    selectedTab: String,
    onNavigate: (String) -> Unit,
    onOpenChat: () -> Unit,
    barVisible: Boolean,
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
    val fabDescription = stringResource(Res.string.nav_chat_fab_content_description)

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
                    .then(
                        if (barVisible) {
                            Modifier.consumeWindowInsets(WindowInsets.navigationBars)
                        } else {
                            Modifier
                        }
                    ),
            ) {
                content(null)
            }
            if (barVisible) {
                AppNavigationBar(
                    items = items,
                    selectedItemId = selectedTab,
                    onItemSelected = { onNavigate(it.id) },
                )
            }
        }

        if (barVisible) {
            FloatingActionButton(
                onClick = onOpenChat,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(
                        end = AppDimens.ScreenPaddingHorizontal,
                        bottom = AppDimens.BottomBarHeight + AppDimens.SpacingSm,
                    ),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = fabDescription)
            }
        }
    }
}

/**
 * Medium: NavigationRail carrying the same four destinations, chat FAB in the rail header.
 *
 * Structure copied from `AdaptiveShellRail` so the two arms feel identical at this size; only the
 * destination set differs. There is no bottom bar here, which is why App.kt passes `0.dp` as the
 * v2 content bottom padding on anything wider than Compact.
 */
@Composable
private fun V2ShellRail(
    selectedTab: String,
    onNavigate: (String) -> Unit,
    onOpenChat: () -> Unit,
    content: @Composable (DrawerState?) -> Unit,
) {
    val inboxLabel = stringResource(Res.string.nav_tab_inbox)
    val calendarLabel = stringResource(Res.string.calendar_nav_label)
    val projectsLabel = stringResource(Res.string.nav_tab_projects)
    val overviewLabel = stringResource(Res.string.nav_tab_overview)
    val fabDescription = stringResource(Res.string.nav_chat_fab_content_description)

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            header = {
                FloatingActionButton(
                    onClick = onOpenChat,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = fabDescription)
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

        Box(modifier = Modifier.fillMaxSize()) {
            content(null)
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
    onOpenSettings: () -> Unit,
    onOpenUpdates: () -> Unit,
    content: @Composable (DrawerState?) -> Unit,
) {
    val inboxLabel = stringResource(Res.string.nav_tab_inbox)
    val calendarLabel = stringResource(Res.string.calendar_nav_label)
    val projectsLabel = stringResource(Res.string.nav_tab_projects)
    val overviewLabel = stringResource(Res.string.nav_tab_overview)
    val settingsLabel = stringResource(Res.string.settings_title)
    val updatesLabel = stringResource(Res.string.update_feed_menu_item)
    val chatLabel = stringResource(Res.string.nav_chat_fab_content_description)
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
                ExtendedFloatingActionButton(
                    onClick = onOpenChat,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                    text = { Text(chatLabel) },
                    modifier = Modifier.padding(
                        horizontal = AppDimens.SpacingLg,
                        vertical = AppDimens.SpacingMd,
                    ),
                )
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

                HorizontalDivider(modifier = Modifier.padding(horizontal = AppDimens.SpacingLg))

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
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content(null)
        }
    }
}
