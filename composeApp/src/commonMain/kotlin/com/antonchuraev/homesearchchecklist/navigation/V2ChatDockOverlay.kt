package com.antonchuraev.homesearchchecklist.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.ChatDockItemCreateOverride
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockAnchor
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockFullExpandState
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiGlassChatDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiDockColor
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.rememberDockFullExpandState
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The v2 arm's chat dock, hosted by the SHELL instead of by each screen.
 *
 * ## Why it moved up here
 * In the control arm every screen that wants a chat dock renders its own [GistiGlassChatDock] and the
 * dock is permanently on screen as a peek strip. v2 deleted that strip, and the replacement — "tap the
 * FAB, go to the full chat route" — made the assistant a *destination*: the user lost the screen they
 * were asking about. The product rule is the opposite: the chat helps you WITH the screen you are on,
 * so it must open in place, over that screen, on every one of them.
 *
 * Hosting it once in the shell (rather than adding a dock to Inbox, Calendar, Overview, Projects and
 * ChecklistDetail) is what makes "on every screen" true by construction: any route rendered inside the
 * shell — including ones pushed on top of a tab — gets the same dock with no per-screen wiring.
 *
 * ## Open / close is a state machine, not a flag
 * [AnchoredDraggableState] only knows Peek and Expanded, and Peek is its floor — there is no "gone"
 * anchor. So visibility lives OUTSIDE the drag state: [visible] mounts the dock, the effect below
 * animates it open, and a settle back to Peek is read as "the user swiped it away" and reported via
 * [onDismissRequest]. The host owns the boolean; this composable never hides itself.
 *
 * Anchors do not exist until the dock's reveal panel has been measured (`offset` is NaN until then),
 * which is why opening waits for the first non-NaN offset instead of animating straight away — an
 * `animateTo` against unset anchors is a no-op and the dock would mount already-collapsed.
 *
 * @param visible whether the dock is mounted at all. False renders nothing (no scrim, no dock, no
 *   full overlay), so a closed dock costs one boolean check per composition.
 * @param onDismissRequest fired when the user swipes the dock down to Peek, taps the scrim or presses
 *   BACK. The host flips [visible] to false; the dock never closes itself.
 * @param chatDockContent the app-level morphing chat content — the SAME slot the control arm passes to
 *   MainScreen/ChecklistDetailScreen, so both arms render one implementation of the chat.
 * @param chatFullContent the expanded "third floor" overlay. Rendered above everything, including the
 *   dock, with the dock's live height as its start height so it grows out of it seamlessly.
 * @param peekPlaceholder input placeholder, e.g. "Ask Gisti…".
 * @param chips prompt-chip row hosted inside the dock's morph.
 * @param onExpandedChanged reported when the dock settles to / away from Expanded — the host seeds the
 *   chat context and fires the open analytics off it, exactly as MainScreen does in the control arm.
 */
@Composable
fun V2ChatDockOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    chatDockContent: @Composable (
        AnchoredDraggableState<DockAnchor>,
        String,
        Dp,
        @Composable () -> Unit,
        ChatDockItemCreateOverride?,
        () -> Unit,
    ) -> Unit,
    chatFullContent: @Composable (DockFullExpandState, Int) -> Unit,
    peekPlaceholder: String,
    chips: @Composable () -> Unit,
    onExpandedChanged: (Boolean) -> Unit = {},
) {
    if (!visible) return

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    // Fresh state per mount: the dock is disposed when it closes, so a re-open always starts from
    // Peek and re-runs the open animation. Keeping it across mounts would re-show an already-Expanded
    // dock with no transition.
    val dockState = remember { AnchoredDraggableState(initialValue = DockAnchor.Peek) }
    val fullState = rememberDockFullExpandState()
    // Retained for call-site compatibility only — GistiGlassChatDock no longer samples it (the dock
    // is an opaque hairline panel now, not a blur). No hazeSource is registered anywhere in the v2
    // shell, and none is needed.
    val hazeState = rememberHazeState()

    var dockHeightPx by remember { mutableStateOf(0) }

    // Open, then own the dismissal — one effect, because the two halves share a precondition.
    //
    // `animateTo` against unset anchors is a no-op, so opening waits for the reveal panel to publish
    // them (offset stops being NaN). The animation can be INTERRUPTED by the user grabbing the dock;
    // AnchoredDraggableState cancels the losing coroutine through its mutex, so that cancellation is
    // expected and must not kill this effect — only a cancellation of our own scope may.
    //
    // Dismissal is armed only AFTER that, and requires BOTH values to read Peek: `settledValue` alone
    // is Peek for the whole opening frame (it would dismiss on mount), and it is also Peek while the
    // user is dragging UP from an interrupted animation — `targetValue` flips at the drag midpoint,
    // so requiring both means "at rest at the floor, and heading nowhere else".
    LaunchedEffect(Unit) {
        snapshotFlow { dockState.offset.isNaN() }.first { !it }
        try {
            dockState.animateTo(DockAnchor.Expanded)
        } catch (cancellation: CancellationException) {
            currentCoroutineContext().ensureActive()
        }
        snapshotFlow {
            dockState.settledValue == DockAnchor.Peek && dockState.targetValue == DockAnchor.Peek
        }.first { it }
        onDismissRequest()
    }

    // Report the OPEN transition to the host exactly once (it seeds the chat context and fires
    // ai_chat_opened off this), and report the close TERMINALLY on dispose.
    //
    // Deliberately NOT `LaunchedEffect(expanded) { onExpandedChanged(expanded) }` over a derived
    // boolean. That mirror also emits its INITIAL value: the dock mounts at Peek, so it would report
    // `false` first — and two of the three entry points (the Projects chips, the detail screen's
    // top-bar action) have already set the host's chat-open flag before mounting this overlay. The
    // spurious `false` cleared that flag and the subsequent `true` re-set it, firing `ai_chat_opened`
    // TWICE for one open on those paths and once via the FAB — i.e. the arm would report a different
    // number of chat opens per entry point, against a control arm that reports one. The experiment's
    // headline metric is chat engagement, so that alone would have made the A/B unreadable.
    // (Same family as the CSAT one-shot bug: a state-shaped condition driving a one-shot action.)
    val onExpandedChangedState = rememberUpdatedState(onExpandedChanged)
    LaunchedEffect(Unit) {
        snapshotFlow { dockState.targetValue }.first { it == DockAnchor.Expanded }
        onExpandedChangedState.value(true)
    }
    DisposableEffect(Unit) {
        // Terminal report: BACK and scrim taps dismiss the dock without it ever settling anywhere, so
        // without this the host would keep believing the chat is open (stale context banner, and the
        // next open would not re-fire its analytics).
        onDispose { onExpandedChangedState.value(false) }
    }

    // BACK: full overlay first (it is on top), then the dock itself. Two handlers instead of one
    // branching handler so the disabled one never swallows a BACK meant for the navigator.
    PlatformBackHandler(enabled = fullState.isOpen) {
        scope.launch { fullState.close() }
    }
    PlatformBackHandler(enabled = !fullState.isOpen) {
        focusManager.clearFocus()
        onDismissRequest()
    }

    // Answer-height cap: status bar → keyboard top. Computed HERE (the host) because a deep read
    // inside the dock returns 0 once an ancestor has applied imePadding. Unspecified = keyboard down.
    val imeBottomDp = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val containerHDp = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val dockAvailableDp = if (imeBottomDp > navBottomDp + 8.dp) {
        (containerHDp - imeBottomDp - statusTopDp).coerceAtLeast(0.dp)
    } else {
        Dp.Unspecified
    }

    val scrimAlpha by animateFloatAsState(
        targetValue = 0.32f,
        animationSpec = tween(durationMillis = 220),
        label = "v2DockScrim",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Tap-to-dismiss scrim. No ripple: the feedback is the dock sliding away, and an indication
        // ring on a full-screen surface reads as a glitch.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        focusManager.clearFocus()
                        onDismissRequest()
                    },
                ),
        )

        // The system-nav strip painted in the dock's own colour, so the gesture bar reads as part of
        // the dock instead of letting the scrim darken a strip the dock visually continues into.
        // Drawn AFTER the scrim and BEFORE the dock — the order the designsystem rule fixes.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(gistiDockColor()),
        )

        GistiGlassChatDock(
            hazeState = hazeState,
            bottomPadding = AppDimens.SpacingSm,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Capped BEFORE fillMaxWidth (a max after a fixed width is ignored): the dock is one
                // line of input, and a desktop-class window stretched it across ~1000dp of pane,
                // which is not a usable text field. Inert on Compact, where the window is narrower
                // than the cap. The nav-strip sibling above stays full width on purpose — it exists
                // to keep the whole gesture bar out of the scrim, not to match the dock's width.
                .adaptiveContentWidth()
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .onSizeChanged { dockHeightPx = it.height },
            pillContent = {
                chatDockContent(
                    dockState,
                    peekPlaceholder,
                    dockAvailableDp,
                    chips,
                    // Chat mode only — item creation has its own surface on the Inbox tab.
                    null,
                    { scope.launch { fullState.open() } },
                )
            },
        )
    }

    // Sibling of the anchor Box so it covers the top bar too (it applies its own statusBarsPadding).
    chatFullContent(fullState, dockHeightPx)
}
