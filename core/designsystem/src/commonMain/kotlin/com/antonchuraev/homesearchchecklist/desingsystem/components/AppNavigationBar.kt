package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface

// ---------------------------------------------------------------------------
// AppNavigationBarItem — data model for a single navigation tab.
//
// MD3 spec: NavigationBar supports 3–5 destinations on compact screens.
// We expose a simple data class so callers stay declarative and the
// component itself owns all token decisions.
// ---------------------------------------------------------------------------

/**
 * Represents a single destination in [AppNavigationBar].
 *
 * @param id Stable, unique identifier used for equality / selection checks.
 * @param label Human-readable label displayed below the icon.
 * @param selectedIcon Icon shown when this item is active (typically filled variant).
 * @param unselectedIcon Icon shown when this item is inactive (typically outlined variant).
 * @param contentDescription Accessibility description for the icon. If null, the label is used.
 */
data class AppNavBarItem(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String? = null,
)

// ---------------------------------------------------------------------------
// AppNavigationBar — Material 3 NavigationBar wrapper.
//
// Design decisions:
//   - containerColor = AppSurface.docked() (level 2 of the app's depth ladder), with
//     tonalElevation = 0 and a 1dp outlineVariant divider ABOVE the bar. M3 separates
//     surfaces by tone by default, and this app spends that budget once: the bar shares
//     the card tone and is marked by the hairline on its seam with the content, so the
//     bottom chrome cannot drift from the cards it sits under.
//   - Tonal indicator (secondary-container pill behind selected icon): provided
//     by NavigationBarItemDefaults.colors() automatically — no override needed.
//   - windowInsets handled by NavigationBar itself: internally it applies
//     WindowInsets.navigationBars, so we do NOT add navigationBarsPadding() on
//     the caller. AppScaffold's bottomBar slot receives the component and pads
//     the content area via Scaffold's innerPadding automatically.
//   - Touch targets: NavigationBarItem has built-in 48dp touch minimum.
//   - Labels: always shown (MD3 guideline: never icon-only for NavigationBar).
// ---------------------------------------------------------------------------

/**
 * Material 3–compliant bottom navigation bar for Gisti.
 *
 * Wraps [NavigationBar] + [NavigationBarItem] with correct color roles, tonal
 * indicator, and accessibility labels. Designed for 2–5 destinations on compact
 * screens (< 600dp). On medium/expanded screens, @android-expert should swap
 * this for NavigationRail in a future adaptive pass.
 *
 * Example usage:
 * ```
 * AppNavigationBar(
 *     items = listOf(todayItem, listsItem),
 *     selectedItemId = selectedTab,
 *     onItemSelected = { selectedTab = it.id },
 * )
 * ```
 *
 * @param items List of 2–5 [AppNavBarItem] descriptors.
 * @param selectedItemId The [AppNavBarItem.id] of the currently active tab.
 * @param onItemSelected Called when the user taps a tab. Receives the full item.
 * @param modifier Optional modifier applied to the Column wrapping the seam divider AND the
 *   [NavigationBar] — so a caller measuring this component sees the height of both, which is what
 *   the bottom chrome actually occupies.
 */
@Composable
fun AppNavigationBar(
    items: List<AppNavBarItem>,
    selectedItemId: String,
    onItemSelected: (AppNavBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Level 2 "Docked": the bar is anchored to the window edge, not raised above it, so what marks
    // it is the 1dp hairline on the seam it shares with the content — never a tonal step and never
    // a shadow. The divider is a sibling ABOVE the bar (the bar owns the navigation-bar inset at its
    // own bottom, so a divider inside it would sit below the labels, not on the seam).
    Column(modifier = modifier) {
        HorizontalDivider(
            thickness = 1.dp,
            color = AppSurface.dockedSeam(),
        )
        NavigationBar(
            containerColor = AppSurface.docked(),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 0.dp,
        ) {
            items.forEach { item ->
                val isSelected = item.id == selectedItemId
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onItemSelected(item) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.contentDescription ?: item.label,
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        // Tonal pill: secondary-container (M3 default for nav bar indicator)
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Canonical item definitions used across the app.
// Placed here so App.kt, TodayScreen, and MainScreen can all import from
// a single source of truth without duplicating the icon/label choices.
// ---------------------------------------------------------------------------

/** "Today" tab — sun icon (outlined/filled pair). */
fun todayNavBarItem(label: String) = AppNavBarItem(
    id = NAV_BAR_TODAY_ID,
    label = label,
    selectedIcon = Icons.Outlined.WbSunny,    // filled variant not in Outlined set; use same
    unselectedIcon = Icons.Outlined.WbSunny,  // active/inactive differentiated via indicator pill
    contentDescription = label,
)

/** "Lists" tab — checklist icon (outlined/filled pair). */
fun listsNavBarItem(label: String) = AppNavBarItem(
    id = NAV_BAR_LISTS_ID,
    label = label,
    selectedIcon = Icons.Outlined.ChecklistRtl,
    unselectedIcon = Icons.Outlined.ChecklistRtl,
    contentDescription = label,
)

const val NAV_BAR_TODAY_ID = "today"
const val NAV_BAR_LISTS_ID = "lists"

// ---------------------------------------------------------------------------
// v2 navigation A/B arm — the four Todoist-style destinations.
//
// A separate id namespace ("v2_*") from the ids above on purpose: the control arm's chrome must
// stay untouched, so nothing here may rename or reuse an existing id. The values are duplicated as
// `V2Destination`'s constants in composeApp — they MUST match byte-for-byte, because the shell
// compares the selected id against them and the same strings are the wire values of the
// `nav_tab_selected` analytics param.
//
// Every factory takes `label: String` (never a resource lookup) — designsystem must not resolve
// user-facing strings, the calling screen does that via stringResource.
// ---------------------------------------------------------------------------

const val NAV_BAR_INBOX_ID = "v2_inbox"
const val NAV_BAR_CALENDAR_ID = "v2_calendar"
const val NAV_BAR_PROJECTS_ID = "v2_projects"
const val NAV_BAR_OVERVIEW_ID = "v2_overview"

/** v2 "Inbox" tab — the quick-capture home. */
fun inboxNavBarItem(label: String) = AppNavBarItem(
    id = NAV_BAR_INBOX_ID,
    label = label,
    // Same vector for both states — active/inactive is conveyed by the tonal indicator pill,
    // matching todayNavBarItem/listsNavBarItem above.
    selectedIcon = Icons.Outlined.Inbox,
    unselectedIcon = Icons.Outlined.Inbox,
    contentDescription = label,
)

/** v2 "Calendar" tab. */
fun calendarNavBarItem(label: String) = AppNavBarItem(
    id = NAV_BAR_CALENDAR_ID,
    label = label,
    selectedIcon = Icons.Outlined.CalendarMonth,
    unselectedIcon = Icons.Outlined.CalendarMonth,
    contentDescription = label,
)

/** v2 "Projects" tab — the existing checklist list under its v2 name. */
fun projectsNavBarItem(label: String) = AppNavBarItem(
    id = NAV_BAR_PROJECTS_ID,
    label = label,
    selectedIcon = Icons.Outlined.ChecklistRtl,
    unselectedIcon = Icons.Outlined.ChecklistRtl,
    contentDescription = label,
)

/** v2 "Overview" tab — everything the navigation drawer holds in the control arm. */
fun overviewNavBarItem(label: String) = AppNavBarItem(
    id = NAV_BAR_OVERVIEW_ID,
    label = label,
    selectedIcon = Icons.Outlined.Apps,
    unselectedIcon = Icons.Outlined.Apps,
    contentDescription = label,
)

// ---------------------------------------------------------------------------
// Note: Compose Previews for this component live in androidMain.
// commonMain does not have access to @Preview without the multiplatform
// preview plugin. Add previews in:
//   core/designsystem/src/androidMain/.../components/AppNavigationBarPreviews.kt
// ---------------------------------------------------------------------------
