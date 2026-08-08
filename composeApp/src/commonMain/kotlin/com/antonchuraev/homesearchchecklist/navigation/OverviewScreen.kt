package com.antonchuraev.homesearchchecklist.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.overview_title
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The "Overview" tab of the v2 navigation arm — everything the navigation drawer holds in the
 * control arm, as a first-class destination.
 *
 * v2 has no drawer at any window size (the shell hands every screen `drawerState = null`, so no
 * hamburger renders), which would otherwise strand Today, MCP, Updates, Settings, the account row,
 * the rate/feedback actions and the legal links. This screen is where they live instead.
 *
 * ## Why it re-hosts [AppNavigationDrawerContent] instead of reimplementing it
 * The drawer content is already a fully parameterised public composable with no drawer-specific
 * dependency — it only needs callbacks. Copying its ~500 lines would guarantee drift: the next menu
 * item added to the drawer would silently be missing from the Overview tab (or vice versa), which
 * reads as "v2 lost a feature". Reuse makes that impossible.
 *
 * The shared file carries exactly two v2 accommodations, both opt-in and both defaulted to the
 * classic layout's previous behaviour: `bottomContentPadding` (scroll room under the bottom bar) and
 * `hiddenDestinationIds` (below). Anything beyond that belongs here, not there.
 *
 * ## What this tab deliberately does NOT list
 * Projects and Calendar. Both are tabs in the bottom bar, so a row here would be a second door to a
 * room the user is already standing next to — and it made Overview read as a rival navigation
 * surface. Todoist's Overview links to no other tab either
 * (`docs/reference/todoist-ui-reference/06-overview-settings-and-projects.png`).
 *
 * The rows still exist for the classic layout, where this content IS the navigation; they are
 * filtered out here, not deleted.
 *
 * @param contentBottomPadding inset reserved for the Compact bottom bar + chat FAB
 *   ([V2ShellMetrics.ContentBottomPadding]); `0.dp` on Medium/Expanded, which have no bottom bar.
 * @param onNavigate receives a [DrawerDestination] constant — App.kt forwards it to the SAME
 *   destination router the v1 drawer uses, so Today/AiChat/Mcp/UpdateFeed/Settings behave
 *   identically in both arms.
 * @param onRateApp / [onLeaveFeedback] CSAT entry points, forwarded verbatim from App.kt.
 * @param versionName drawer footer version label.
 * @param isGoogleLinked / [googleEmail] / [googleDisplayName] / [onSignInClick] / [onSignOutClick]
 *   the account block — identical wiring to the control arm's drawer.
 */
// AppScaffold's scrollBehavior parameter is typed TopAppBarScrollBehavior, still
// @ExperimentalMaterial3Api — an experimental type in the signature forces the opt-in onto every
// call site even though AppScaffold itself already opted in.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    contentBottomPadding: Dp,
    onNavigate: (destination: String) -> Unit,
    onRateApp: () -> Unit,
    onLeaveFeedback: () -> Unit,
    versionName: String,
    isGoogleLinked: Boolean,
    googleEmail: String?,
    googleDisplayName: String?,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    val analytics: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analytics.screenView(AnalyticsScreens.OVERVIEW) }

    AppScaffold(title = stringResource(Res.string.overview_title)) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppNavigationDrawerContent(
                // Handed to the content as TRAILING SCROLL ROOM, not as padding on this Box: the
                // drawer content is a fillMaxHeight().verticalScroll() Column, so padding the host
                // would shrink its viewport and leave a dead band instead of letting the footer
                // scroll clear of the bottom bar and the chat FAB.
                bottomContentPadding = contentBottomPadding,
                // Projects and Calendar are one tap away in the bottom bar; repeating them here put
                // two doors on one room and made this tab read as a second navigation surface
                // competing with the bar. Todoist's Overview links to no other tab at all.
                //
                // The rows are HIDDEN, not removed: the same composable still renders them in the
                // classic layout, where it IS the navigation.
                hiddenDestinationIds = setOf(DrawerDestination.Main, DrawerDestination.Calendar),
                // Material's 12.dp ItemPadding floats the pill off the edges of a DRAWER SHEET; this
                // is a full-width PAGE. With it, each row's icon landed at 12 + the item's own
                // internal 16 = 28.dp while the section labels above them sat at 16.dp — the rows
                // read as a stray inset gutter, misaligned with their own headers. Zero here puts
                // the icon on the same 16.dp gutter as every label, divider and the promo badge,
                // and lets the press ripple run edge to edge like any full-width list row.
                itemPadding = PaddingValues(0.dp),
                // No drawer item is "current" while the user is standing on the Overview tab —
                // every row here is a push target, not the screen itself.
                selectedItemId = "",
                // There is no drawer to close in v2; the rows navigate directly.
                onCloseDrawer = {},
                onHomeClick = { onNavigate(DrawerDestination.Main) },
                onTodayClick = { onNavigate(DrawerDestination.Today) },
                onCalendarClick = { onNavigate(DrawerDestination.Calendar) },
                onAiChatClick = { onNavigate(DrawerDestination.AiChat) },
                onMcpClick = { onNavigate(DrawerDestination.Mcp) },
                onUpdateFeedClick = { onNavigate(DrawerDestination.UpdateFeed) },
                onSettingsClick = { onNavigate(DrawerDestination.Settings) },
                onRateAppClick = onRateApp,
                onLeaveFeedbackClick = onLeaveFeedback,
                versionName = versionName,
                isGoogleLinked = isGoogleLinked,
                googleEmail = googleEmail,
                googleDisplayName = googleDisplayName,
                onSignInClick = onSignInClick,
                onSignOutClick = onSignOutClick,
            )
        }
    }
}
