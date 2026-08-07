package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentOpener
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Route composable for the v2 Inbox tab.
 *
 * Public because App.kt (module `composeApp`) mounts it from `entry<AppNavRoute.Inbox>`; [InboxScreen]
 * stays a pure state-in / intents-out composable so it can be previewed and screenshot-tested without
 * Koin. This split matches `TodayRoute` / `TodayScreen`.
 *
 * The snackbar host is owned HERE rather than inside the screen so the side-effect collector and the
 * host share one instance — collecting in the screen would recreate the host on every state change
 * and drop in-flight messages.
 *
 * @param contentBottomPadding inset the v2 shell reserves for its bottom bar + chat FAB. Defaults to
 *   0.dp so any caller that is not the v2 shell (previews, tests) gets the plain layout.
 * @param swallowRootBack forwarded to [InboxScreen]. Defaults to true (today's behaviour: BACK at the
 *   v2 root is swallowed so it can never finish() the app). The host MUST pass false while this entry
 *   is not the top of the back stack — on Expanded the Inbox is a two-pane listPane that stays
 *   composed beside a pushed ChecklistDetail, and swallowing there kills the BACK that should dismiss
 *   the detail pane.
 * @param createDockOpen whether the capture dock is showing. Hoisted to the shell host rather than
 *   held here because the shell's FABs are drawn ABOVE this screen and must hide while the dock is
 *   up — a flag private to this screen would leave the "+" FAB floating over its own dock.
 * @param onCreateDockDismiss fired when the user dismisses the dock (BACK, scrim tap). The host
 *   clears [createDockOpen]; this screen never hides the dock on its own.
 * @param homeSignal monotonic counter the shell increments every time the Inbox TAB is tapped. The
 *   pager page lives in the screen and survives the tab's pop-to-root, so without this signal the tab
 *   named "Inbox" reopens whatever project page was last swiped to. A counter rather than a Boolean:
 *   a repeated tap on the already-selected tab must still return home.
 */
@Composable
fun InboxRoute(
    contentBottomPadding: Dp = 0.dp,
    swallowRootBack: Boolean = true,
    // No defaults — see InboxScreen: a host that forgets to wire these gets a dead "+" button and a
    // silent compiler, which is how the rail shipped one.
    createDockOpen: Boolean,
    onCreateDockDismiss: () -> Unit,
    homeSignal: Int = 0,
    /**
     * Checklist the pager should open on, hoisted to the SHELL because nothing inside this entry
     * outlives it.
     *
     * Opening a checklist pushes the detail route over this entry, and on the way back both the
     * composition and the ViewModel are gone — measured on an emulator, not assumed: seeding the
     * pager from the ViewModel's own `selectedPage` left it on the Inbox all the same, because that
     * ViewModel had been rebuilt. The shell composable is the nearest scope that survives, so the
     * anchor lives there.
     *
     * A checklist ID rather than a page index: a new checklist is inserted at position 0, so an index
     * silently points at a different list after any create.
     */
    anchorChecklistId: Long? = null,
    onAnchorChecklistChanged: (Long?) -> Unit = {},
    viewModel: InboxViewModel = koinViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val attachmentOpener: AttachmentOpener = koinInject()

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                // The text is already resolved from Compose Resources by the ViewModel — the screen
                // never maps a key, so a message can never silently fall through to a raw literal.
                is InboxSideEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.text)
                // Handing a file to the platform viewer is an ACTION, so it is done once, here, on
                // arrival — not held in state and re-fired on every recomposition.
                is InboxSideEffect.OpenAttachmentExternally ->
                    attachmentOpener.openExternally(effect.path, effect.mimeType)
            }
        }
    }

    InboxScreen(
        state = state,
        contentBottomPadding = contentBottomPadding,
        onIntent = viewModel::sendIntent,
        snackbarHostState = snackbarHostState,
        swallowRootBack = swallowRootBack,
        createDockOpen = createDockOpen,
        onCreateDockDismiss = onCreateDockDismiss,
        homeSignal = homeSignal,
        anchorChecklistId = anchorChecklistId,
        onAnchorChecklistChanged = onAnchorChecklistChanged,
    )
}
