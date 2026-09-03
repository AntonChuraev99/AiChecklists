package com.antonchuraev.homesearchchecklist.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.ChatDockItemCreateOverride
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockAnchor
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockFullExpandState
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiGlassChatDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.dockProgress
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiDockColor
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.rememberDockFullExpandState
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Motion spec for "the chat dock comes out of the AI button".
 *
 * Both halves of the transition read these: the raised button in [V2NavigationShell]'s Compact bar
 * (the OUTGOING element) and the dock here (the INCOMING one). They used to be independent — a 220ms
 * linear-ish scrim fade next to `animateTo(Expanded)` on its default `tween()` (300ms,
 * FastOutSlowIn), with the button simply cut out of the tree on the same frame — so three things
 * moved on three clocks and nothing pointed at anything.
 *
 * ## The shape
 * The button and the dock share one centre line (both are horizontally centred, and the dock's bottom
 * edge is the closest thing to the button's own anchor), so the cheap, robust form of a container
 * transform applies: the outgoing element scales UP and fades OUT while the incoming one scales UP
 * from the same axis and fades IN, overlapping. The eye reads one object growing, not two objects
 * swapping.
 *
 * | Element | Duration | Easing | From → to |
 * |---|---|---|---|
 * | dock container (scale + alpha) | [OpenMs] 260 | [EnterEasing] | 0.92 → 1.0, 0 → 1 |
 * | dock panel (`animateTo(Expanded)`) | [OpenMs] 260 | [EnterEasing] | Peek → Expanded |
 * | scrim | [OpenMs] 260 | [EnterEasing] | 0 → [ScrimAlpha] |
 * | AI button (scale + alpha) | [ButtonHandoffMs] 180 | [ExitEasing] | 1.0 → 1.24, 1 → 0 |
 * | everything, closing | [CloseMs] 160 | [ExitEasing] | reverse |
 * | AI button returning | [ButtonReturnMs] 140 | [EnterEasing] | reverse |
 *
 * ## Why these numbers
 * - **One duration for the whole entrance.** The scrim, the container and the panel are one movement;
 *   giving them 220 / 260 / 300 is what made the old open look like three.
 * - **260ms** is the M3 "medium container, entering" band. Below ~200ms a surface this large arrives
 *   without a sense of travel; past ~300ms it starts to feel like waiting.
 * - **Entering decelerates, exiting accelerates** (M3 emphasized pair). The dock lands softly; the
 *   button leaves without lingering under the dock that replaces it.
 * - **The button (180ms) is shorter than the dock (260ms)** so it is gone before the dock finishes —
 *   otherwise the two overlap at full opacity in the same place and read as a double image.
 * - **Closing is 160ms, not a mirrored 260.** Dismissal is an acknowledgement, not a reveal: the user
 *   has already decided, and a slow exit is the single most common way a modal surface feels heavy.
 *   The ~1.6:1 open:close ratio is the usual M3 exit/enter proportion.
 *
 * ## Reduced motion / animations disabled
 * Handled by construction, not by a branch. Every step runs through a Compose animation API on the
 * composition's coroutine context, which carries `MotionDurationScale` derived from
 * `Settings.Global.ANIMATOR_DURATION_SCALE`; at scale 0 each `animateTo` completes on its first frame,
 * so the dock is simply THERE with no intermediate position. That is also why nothing in this file is
 * sequenced with `delay()` — `delay` ignores the scale, and a hard-coded wait between two instant
 * animations is exactly the "jump" the requirement forbids.
 * (wasmJs has no equivalent signal wired up: `prefers-reduced-motion` would need a JS bridge, which
 * lives outside this module.)
 */
object V2ChatMotion {
    const val OpenMs: Int = 260
    const val CloseMs: Int = 160
    const val ButtonHandoffMs: Int = 180
    const val ButtonReturnMs: Int = 140

    /** Dimming of the screen behind the dock at rest. */
    const val ScrimAlpha: Float = 0.32f

    /** Scale the dock container grows FROM. Small enough to read as growth, not as a zoom. */
    const val DockEnterScale: Float = 0.92f

    /** Scale the button grows TO as it dissolves into the dock. */
    const val ButtonHandoffScale: Float = 1.24f

    /** M3 "emphasized decelerate" — incoming elements. */
    val EnterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** M3 "emphasized accelerate" — outgoing elements. */
    val ExitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/**
 * How far the dock must have risen off its floor before a return TO that floor may be read as
 * "the user swiped it away".
 *
 * Not a motion value — a gate on a state machine, which is why it lives outside [V2ChatMotion].
 * A quarter of the travel is roughly two frames of the 260ms entrance, so it is invisible on the
 * ordinary path and still wide enough that a grab in the opening's first frames (where the panel is
 * still sitting at Peek and both `settledValue` and `targetValue` read Peek) cannot be mistaken for a
 * dismissal.
 */
private const val DockRisenFraction: Float = 0.25f

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
 *   BACK — but only AFTER the exit animation has finished, because the host flips [visible] to false
 *   in response and that unmounts everything. The dock never closes itself. See [V2ChatMotion] for
 *   the timings and why the exit is shorter than the entrance.
 * @param onClosingChanged true the moment an exit STARTS, false again if the user catches the dock on
 *   its way out and pulls it back up. It exists so the other half of the transition can move at the
 *   same time: the host drops the shell's `chatOpen` on `true`, and the raised AI button starts
 *   growing back while the dock is still fading — without it the two run in series and the middle of
 *   the bar is empty for the whole exit. Whoever consumes it must also handle the `false`: an
 *   interrupted exit leaves the dock on screen, so a button that already came back has to leave again.
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
    onClosingChanged: (Boolean) -> Unit = {},
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

    // The container's emergence: scrim opacity, dock alpha and dock scale all ride this ONE value, so
    // they cannot drift apart. Kept out of composition — every consumer reads it inside a draw-phase
    // lambda (`drawBehind` / `graphicsLayer {}`), so the whole transition costs zero recompositions of
    // the screen underneath.
    val emergence = remember { Animatable(0f) }

    // Read at the END of the exit animation, ~160ms after the tap that started it — long enough for
    // the host to have recomposed with a new lambda instance.
    val onDismissRequestState = rememberUpdatedState(onDismissRequest)
    val onClosingChangedState = rememberUpdatedState(onClosingChanged)

    // The exit latch. EVERY dismissal path — BACK, a scrim tap, and the dock settling back at Peek
    // because the user swiped it away — routes through this instead of calling [onDismissRequest]
    // directly, which is what buys the dock an exit animation at all: the host unmounts this
    // composable the instant its flag flips, so anything that calls back first has already lost the
    // frames it wanted to animate in.
    //
    // It is NOT one-way. It used to be, and that made a 160ms window in which the dock could not be
    // saved: catch it on the way down and drag it back up, and the panel obediently followed the
    // finger while the fade kept running underneath, so the dock vanished from under it and the host
    // was told to unmount. A gesture that is reversible in every other direction has to be reversible
    // here too.
    var closing by remember { mutableStateOf(false) }

    // Open, then own the dismissal — one effect, because the two halves share a precondition.
    //
    // `animateTo` against unset anchors is a no-op, so the PANEL waits for the reveal panel to publish
    // them (offset stops being NaN). The CONTAINER does not wait: it starts fading in on mount, so a
    // degenerate case where anchors never arrive shows a visible dock rather than an invisible one.
    // The panel animation can be INTERRUPTED by the user grabbing the dock; AnchoredDraggableState
    // cancels the losing coroutine through its mutex, so that cancellation is expected and must not
    // kill this effect — only a cancellation of our own scope may. The container animation is a
    // separate child job precisely so that interruption never truncates the fade-in.
    //
    // Dismissal is then armed in a LOOP, and the loop has two guards:
    //
    // 1. The dock must have visibly LEFT THE FLOOR first ([DockRisenFraction]). `animateTo(Expanded)`
    //    returns the instant the user grabs the panel, and a grab in the first frames of the opening
    //    leaves the offset still at Peek — where both values below read Peek and the dock would be
    //    dismissed under a finger that had only just caught it.
    // 2. BOTH values must read Peek: `settledValue` alone is Peek for the whole opening frame, and it
    //    is also Peek while the user drags UP from an interrupted animation — `targetValue` flips at
    //    the drag midpoint, so requiring both means "at rest at the floor, and heading nowhere else".
    //
    // The loop exists for the reversal: when an exit is interrupted the effect below puts `closing`
    // back to false, and the dock — still on screen, back at Expanded — needs its dismissal armed
    // again. On the ordinary path the loop never runs twice: the host unmounts this composable.
    LaunchedEffect(Unit) {
        launch {
            emergence.animateTo(1f, tween(V2ChatMotion.OpenMs, easing = V2ChatMotion.EnterEasing))
        }
        snapshotFlow { dockState.offset.isNaN() }.first { !it }
        try {
            dockState.animateTo(
                DockAnchor.Expanded,
                tween(V2ChatMotion.OpenMs, easing = V2ChatMotion.EnterEasing),
            )
        } catch (cancellation: CancellationException) {
            currentCoroutineContext().ensureActive()
        }
        while (true) {
            snapshotFlow { dockState.dockProgress() }.first { it >= DockRisenFraction }
            snapshotFlow {
                dockState.settledValue == DockAnchor.Peek && dockState.targetValue == DockAnchor.Peek
            }.first { it }
            closing = true
            // Suspends until the exit resolves. It resolves by unmounting us on the ordinary path, so
            // reaching the next iteration means the user pulled the dock back up.
            snapshotFlow { closing }.first { !it }
        }
    }

    // The exit, shared by all three dismissal paths, and interruptible.
    //
    // `animateTo(Peek)` is unconditional and cheap: when the user has already swiped the dock down it
    // is a no-op against an offset that is already at the anchor, so a swipe-away just fades the scrim
    // out from wherever it was. When BACK or a scrim tap arrives with the dock expanded — or still
    // opening — it takes over the drag mutex, cancelling the opening animation, and the same
    // CancellationException contract applies as above.
    //
    // The rescue job is what makes the latch reversible. It waits for the exit to actually take the
    // wheel (`targetValue` reaching Peek — without that first wait a close started while the dock was
    // still opening would read the opening's own Expanded target and abort itself), then for the drag
    // to carry the panel back past the midpoint. Taking `emergence` away from the fade is enough to
    // stop it: an `Animatable` serialises its writers through a mutator mutex, so `animateTo(1f)`
    // cancels `animateTo(0f)` on the spot — which is also why the two are the only writers here and
    // why the host is told from OUTSIDE the animation rather than after it.
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        focusManager.clearFocus()
        onClosingChangedState.value(true)
        launch {
            try {
                dockState.animateTo(
                    DockAnchor.Peek,
                    tween(V2ChatMotion.CloseMs, easing = V2ChatMotion.ExitEasing),
                )
            } catch (cancellation: CancellationException) {
                currentCoroutineContext().ensureActive()
            }
        }
        var rescued = false
        coroutineScope {
            val rescue = launch {
                snapshotFlow { dockState.targetValue }.first { it == DockAnchor.Peek }
                snapshotFlow { dockState.targetValue }.first { it == DockAnchor.Expanded }
                rescued = true
                emergence.animateTo(
                    1f,
                    tween(V2ChatMotion.OpenMs, easing = V2ChatMotion.EnterEasing),
                )
            }
            try {
                emergence.animateTo(
                    0f,
                    tween(V2ChatMotion.CloseMs, easing = V2ChatMotion.ExitEasing),
                )
            } catch (cancellation: CancellationException) {
                // Either the rescue took the animatable (scope still alive → fall through and let it
                // finish restoring), or our own scope is going away and ensureActive rethrows.
                currentCoroutineContext().ensureActive()
            }
            if (!rescued) rescue.cancel()
        }
        if (rescued) {
            closing = false
            onClosingChangedState.value(false)
        } else {
            onDismissRequestState.value()
        }
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
    // Stays enabled while `closing` so a second BACK inside the 160ms exit is swallowed rather than
    // popping a route the user cannot see yet. Not a silent no-op: the dock is visibly leaving. And
    // if that exit is interrupted (the user drags the dock back up), the latch goes back to false, so
    // BACK dismisses again instead of being dead for the rest of the session.
    PlatformBackHandler(enabled = !fullState.isOpen) {
        closing = true
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

    val dockColor = gistiDockColor()

    Box(modifier = Modifier.fillMaxSize()) {
        // Tap-to-dismiss scrim. No ripple: the feedback is the dock sliding away, and an indication
        // ring on a full-screen surface reads as a glitch.
        //
        // `drawBehind` rather than `background(Color.Black.copy(alpha = animated))`: the alpha is read
        // in the DRAW phase, so 260ms of fade does not recompose a full-screen Box (and everything
        // Compose has to skip past to get there) once per frame.
        //
        // `clickable`, deliberately, and NOT the `detectTapGestures` the Inbox capture dock uses: this
        // scrim is modal. Swallowing the initial press is the POINT here — the list under an open chat
        // must not scroll — whereas the capture dock dims nothing and has to leave the list alive.
        // Hoisted out of `drawBehind`: a colour role is a composition read, and reading it inside the
        // draw lambda would put a `MaterialTheme` lookup on every frame of the 260ms fade.
        //
        // `colorScheme.scrim`, not `Color.Black`. Same pixels today — the role IS black in both
        // themes — but the capture dock already dims with the role, and two scrims over the same page
        // written two ways is how they drift apart at the next theme change.
        val scrimColor = MaterialTheme.colorScheme.scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        color = scrimColor,
                        alpha = emergence.value * V2ChatMotion.ScrimAlpha,
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { closing = true },
                ),
        )

        // The system-nav strip painted in the dock's own colour, so the gesture bar reads as part of
        // the dock instead of letting the scrim darken a strip the dock visually continues into.
        // Drawn AFTER the scrim and BEFORE the dock — the order the designsystem rule fixes. It fades
        // on the same clock as the dock: it is part of the dock's surface, so a strip that is already
        // opaque while the dock is still arriving would look like a detached bar at the screen edge.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .drawBehind { drawRect(color = dockColor, alpha = emergence.value) },
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
                // AFTER the insets so the transform's origin is the dock SURFACE's own bottom edge,
                // not the bottom of the window: the panel then grows out of the line it actually
                // rests on, straight up the centre the AI button sits on. Before the insets it grew
                // out of a point behind the gesture bar.
                //
                // Layout is untouched (graphicsLayer is a draw-time transform), so the anchors the
                // drag state measures, `onSizeChanged` below and `imePadding` all see the real dock.
                .graphicsLayer {
                    val progress = emergence.value
                    val scale = lerp(V2ChatMotion.DockEnterScale, 1f, progress)
                    scaleX = scale
                    scaleY = scale
                    alpha = progress
                    transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1f)
                }
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
