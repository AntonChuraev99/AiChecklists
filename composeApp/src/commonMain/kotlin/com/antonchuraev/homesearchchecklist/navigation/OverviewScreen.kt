package com.antonchuraev.homesearchchecklist.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
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
 * dependency — it only needs callbacks. Copying its ~200 lines would guarantee drift: the next menu
 * item added to the drawer would silently be missing from the Overview tab (or vice versa), and in
 * an A/B test that reads as "the v2 arm lost a feature". Reuse makes that impossible. The file is
 * deliberately NOT modified — the control arm renders the very same composable.
 *
 * Note that its Home row emits [DrawerDestination.Main]; App.kt routes that — and Calendar — through
 * the v2 TAB router rather than the v1 drawer router, because the v1 Main branch only pops to an
 * existing `AppNavRoute.Main` and never pushes (in v2 the stack here is always [Inbox, Overview], so
 * "Home" would silently do nothing). "Home" and "Projects" are the same destination in this arm.
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
