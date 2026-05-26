package com.antonchuraev.homesearchchecklist.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.calendar_nav_label
import aichecklists.core.designsystem.generated.resources.drawer_item_home
import aichecklists.core.designsystem.generated.resources.nav_ai_chat
import aichecklists.core.designsystem.generated.resources.settings_title
import aichecklists.core.designsystem.generated.resources.update_feed_menu_item
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Single adaptive navigation shell replacing the 6 ModalNavigationDrawer
 * duplicates that existed in App.kt before Stage 3.
 *
 * Layout strategy by window size:
 * - Compact  (<600dp): ModalNavigationDrawer with hamburger. DrawerState is
 *   created here and passed to [content] so screens can show a hamburger
 *   icon and call drawerState.open().
 * - Medium   (600–839dp): NavigationRail (5 items, no Help/About/footer).
 *   drawerState = null is passed to [content] — screens must hide the hamburger.
 * - Expanded (≥840dp): PermanentNavigationDrawer with full AppNavigationDrawerContent
 *   (brand header + 5 nav items + Help + About + footer).
 *   drawerState = null is passed to [content] — drawer is always visible.
 *
 * Navigation debounce: a single 500ms navConsumed guard lives here and protects
 * all layout variants from rapid double-taps (previously duplicated 6 times).
 *
 * @param selectedDestination one of [DrawerDestination] string constants
 * @param onNavigate called when the user picks a destination; the shell
 *   calls close() on the modal drawer before delegating here
 * @param onRateApp / [onLeaveFeedback] / [onSupport] / [onPrivacy] / [onTerms]
 *   help/about action callbacks — forwarded to AppNavigationDrawerContent
 * @param versionName shown in the drawer footer (Compact/Expanded only)
 * @param content screen composable that receives the current DrawerState (or
 *   null on Medium/Expanded where the rail/permanent drawer is always visible)
 */
@Composable
fun AdaptiveNavigationShell(
    selectedDestination: String,
    onNavigate: (destination: String) -> Unit,
    onRateApp: () -> Unit,
    onLeaveFeedback: () -> Unit,
    versionName: String,
    content: @Composable (drawerState: DrawerState?) -> Unit,
) {
    // Single debounce guard for all layout variants — prevents Koin DI race on
    // rapid double-tap that previously appeared in the 6-entry duplicate pattern.
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
        AppWindowSizeClass.Compact -> {
            AdaptiveShellCompactModal(
                selectedDestination = selectedDestination,
                onNavigate = ::guardedNavigate,
                onRateApp = onRateApp,
                onLeaveFeedback = onLeaveFeedback,
                versionName = versionName,
                content = content,
            )
        }

        AppWindowSizeClass.Medium -> {
            AdaptiveShellRail(
                selectedDestination = selectedDestination,
                onNavigate = ::guardedNavigate,
                content = content,
            )
        }

        AppWindowSizeClass.Expanded -> {
            AdaptiveShellPermanent(
                selectedDestination = selectedDestination,
                onNavigate = ::guardedNavigate,
                onRateApp = onRateApp,
                onLeaveFeedback = onLeaveFeedback,
                versionName = versionName,
                content = content,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal layout variants
// ─────────────────────────────────────────────────────────────────────────────

/** Compact layout: ModalNavigationDrawer identical to the previous per-entry pattern. */
@Composable
private fun AdaptiveShellCompactModal(
    selectedDestination: String,
    onNavigate: (String) -> Unit,
    onRateApp: () -> Unit,
    onLeaveFeedback: () -> Unit,
    versionName: String,
    content: @Composable (DrawerState?) -> Unit,
) {
    // Fresh Closed DrawerState — plain remember, not rememberDrawerState, so that
    // a back-navigation never restores an Open state (same rationale as the old
    // per-entry blocks in App.kt).
    val drawerState = remember { DrawerState(DrawerValue.Closed) }
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                AppNavigationDrawerContent(
                    selectedItemId = selectedDestination,
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                    onHomeClick = { onNavigate(DrawerDestination.Main) },
                    onTodayClick = { onNavigate(DrawerDestination.Today) },
                    onCalendarClick = { onNavigate(DrawerDestination.Calendar) },
                    onAiChatClick = { onNavigate(DrawerDestination.AiChat) },
                    onUpdateFeedClick = { onNavigate(DrawerDestination.UpdateFeed) },
                    onSettingsClick = { onNavigate(DrawerDestination.Settings) },
                    onRateAppClick = onRateApp,
                    onLeaveFeedbackClick = onLeaveFeedback,
                    versionName = versionName,
                )
            }
        },
    ) {
        content(drawerState)
    }
}

/**
 * Medium layout: NavigationRail with 5 primary destinations.
 * drawerState = null → screens should not render hamburger icon.
 */
@Composable
private fun AdaptiveShellRail(
    selectedDestination: String,
    onNavigate: (String) -> Unit,
    content: @Composable (DrawerState?) -> Unit,
) {
    val homeLabel = stringResource(Res.string.drawer_item_home)
    val calendarLabel = stringResource(Res.string.calendar_nav_label)
    val aiChatLabel = stringResource(Res.string.nav_ai_chat)
    val updatesLabel = stringResource(Res.string.update_feed_menu_item)
    val settingsLabel = stringResource(Res.string.settings_title)

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            header = {
                // Brand icon at the top of the rail (optional MD3 FAB slot reuse)
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .widthIn(min = 56.dp)
                        .then(Modifier.width(24.dp)),
                )
                HorizontalDivider(modifier = Modifier.width(56.dp))
            },
        ) {
            NavigationRailItem(
                selected = selectedDestination == DrawerDestination.Main,
                onClick = { onNavigate(DrawerDestination.Main) },
                icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                label = { Text(homeLabel) },
            )
            NavigationRailItem(
                selected = selectedDestination == DrawerDestination.Calendar,
                onClick = { onNavigate(DrawerDestination.Calendar) },
                icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                label = { Text(calendarLabel) },
            )
            NavigationRailItem(
                selected = selectedDestination == DrawerDestination.AiChat,
                onClick = { onNavigate(DrawerDestination.AiChat) },
                icon = { Icon(Icons.Outlined.SmartToy, contentDescription = null) },
                label = { Text(aiChatLabel) },
            )
            NavigationRailItem(
                selected = selectedDestination == DrawerDestination.UpdateFeed,
                onClick = { onNavigate(DrawerDestination.UpdateFeed) },
                icon = { Icon(Icons.Outlined.Campaign, contentDescription = null) },
                label = { Text(updatesLabel) },
            )
            NavigationRailItem(
                selected = selectedDestination == DrawerDestination.Settings,
                onClick = { onNavigate(DrawerDestination.Settings) },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                label = { Text(settingsLabel) },
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            content(null)
        }
    }
}

/**
 * Expanded layout: PermanentNavigationDrawer always visible.
 * Full AppNavigationDrawerContent (brand header + 5 nav + Help + About + footer).
 * drawerState = null → screens should not render hamburger icon.
 */
@Composable
private fun AdaptiveShellPermanent(
    selectedDestination: String,
    onNavigate: (String) -> Unit,
    onRateApp: () -> Unit,
    onLeaveFeedback: () -> Unit,
    versionName: String,
    content: @Composable (DrawerState?) -> Unit,
) {
    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                AppNavigationDrawerContent(
                    selectedItemId = selectedDestination,
                    onCloseDrawer = { /* no-op: permanent drawer has no close action */ },
                    onHomeClick = { onNavigate(DrawerDestination.Main) },
                    onTodayClick = { onNavigate(DrawerDestination.Today) },
                    onCalendarClick = { onNavigate(DrawerDestination.Calendar) },
                    onAiChatClick = { onNavigate(DrawerDestination.AiChat) },
                    onUpdateFeedClick = { onNavigate(DrawerDestination.UpdateFeed) },
                    onSettingsClick = { onNavigate(DrawerDestination.Settings) },
                    onRateAppClick = onRateApp,
                    onLeaveFeedbackClick = onLeaveFeedback,
                    versionName = versionName,
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content(null)
        }
    }
}
