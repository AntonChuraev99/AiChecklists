package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

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
//   - containerColor = NavigationBarDefaults.containerColor (surfaceContainer in M3).
//     MD3 spec: NavigationBar uses surfaceContainer, not surface. This ensures
//     the correct tonal elevation separating it from the content area.
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
 * @param modifier Optional modifier applied to the [NavigationBar] container.
 */
@Composable
fun AppNavigationBar(
    items: List<AppNavBarItem>,
    selectedItemId: String,
    onItemSelected: (AppNavBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = NavigationBarDefaults.containerColor,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = NavigationBarDefaults.Elevation,
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
// Note: Compose Previews for this component live in androidMain.
// commonMain does not have access to @Preview without the multiplatform
// preview plugin. Add previews in:
//   core/designsystem/src/androidMain/.../components/AppNavigationBarPreviews.kt
// ---------------------------------------------------------------------------
