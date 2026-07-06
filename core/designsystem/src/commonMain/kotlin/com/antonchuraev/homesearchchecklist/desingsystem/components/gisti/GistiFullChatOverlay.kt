package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_panel_collapse
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/** The two positions of the FULL-screen chat overlay. */
enum class FullAnchor { Docked, Full }

/**
 * Per-screen host state for the **FULL** chat overlay (the third "floor" above the peek/expanded
 * dock). Deliberately a SEPARATE [AnchoredDraggableState] from the dock's own `DockAnchor` state —
 * Full is NOT a third dock anchor. Adding a third anchor to the dock would force its content-measured
 * reveal panel to re-measure and has twice broken the keyboard-up Expanded answer, so Full lives
 * entirely on top as its own layer with its own gesture state.
 *
 * Reveal maths mirror the dock: [FullAnchor.Full] is offset 0 (fully open, covers the screen);
 * [FullAnchor.Docked] is offset `range` (collapsed to the dock's height). The overlay reads
 * `anchored.offset` ONLY inside layout/graphicsLayer lambdas (it is `@FrequentlyChangingValue`).
 *
 * @property anchored The gesture/animation state. Anchors are (re)published by [GistiFullChatOverlay]
 *                    once it measures the screen height and the dock start height.
 */
@Stable
class DockFullExpandState internal constructor(
    val anchored: AnchoredDraggableState<FullAnchor>,
) {
    /**
     * Snapshot-observable "is the overlay open (or opening)". Cheap composition read (flips only at
     * the drag midpoint / on animateTo). Host uses it for the BACK handler + z-order intent. During a
     * close animation this is already false (target is Docked) while the overlay is still shrinking —
     * the overlay keeps rendering off its offset via its own snapshotFlow gate, so that is correct.
     */
    val isOpen: Boolean get() = anchored.targetValue == FullAnchor.Full

    /** Open the overlay (animate to full screen). Safe before anchors are measured (NaN-guarded). */
    suspend fun open() {
        if (!anchored.offset.isNaN()) anchored.animateTo(FullAnchor.Full)
    }

    /** Collapse the overlay back onto the dock. Safe before anchors are measured (NaN-guarded). */
    suspend fun close() {
        if (!anchored.offset.isNaN()) anchored.animateTo(FullAnchor.Docked)
    }
}

/** Remembers a per-screen [DockFullExpandState]. NEVER share one instance across two-pane panes. */
@Composable
fun rememberDockFullExpandState(): DockFullExpandState {
    val anchored = remember { AnchoredDraggableState(initialValue = FullAnchor.Docked) }
    return remember { DockFullExpandState(anchored) }
}

/**
 * Full-screen chat overlay that grows from the dock's Expanded position up to the whole screen. It is
 * an OPAQUE layer drawn ABOVE the dock (higher z), so while it is open the dock beneath is fully
 * covered. The host places it as the LAST child of a `fillMaxSize` Box that wraps the whole screen
 * (so it covers the top bar too), and adds a BACK handler bound to [DockFullExpandState.isOpen].
 *
 * Decoupling: `core/designsystem` never imports `feature/aichat`. The full conversation history and
 * the pinned input arrive as slots ([historyContent] / [inputContent]) supplied by App.kt.
 *
 * Reveal: the surface is bottom-anchored, `fillMaxWidth`, height = `screenHeight − anchored.offset`
 * (read in the layout lambda). Top corners lerp 28dp→0dp and a fast alpha fade-in mask the handoff
 * from the dock (both driven inside `graphicsLayer`). `statusBarsPadding()` insets the content once
 * near full. Drag DOWN on the top handle collapses; the chevron button collapses; the host's BACK
 * handler collapses.
 *
 * @param state            Per-screen [DockFullExpandState].
 * @param dockStartHeightPx The dock's live Expanded height in px — the overlay's collapsed start
 *                          height, so it grows seamlessly out of the dock (0 ⇒ grows from the very
 *                          bottom, still fine because the fade masks it).
 * @param historyContent   Slot: the full scrollable message history (App.kt passes ChatMessageList).
 * @param inputContent     Slot: the pinned chat input row (App.kt passes ChatInputRow).
 * @param onCollapse       Called by the chevron / drag-settle-to-Docked to collapse (host also
 *                          re-seats the dock at Expanded under the shrinking overlay).
 * @param recordingOverlay Optional slot rendered above the input while recording (zero height idle).
 */
@Composable
fun GistiFullChatOverlay(
    state: DockFullExpandState,
    dockStartHeightPx: Int,
    historyContent: @Composable () -> Unit,
    inputContent: @Composable () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    recordingOverlay: (@Composable () -> Unit)? = null,
) {
    val snapSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    }
    val fling = AnchoredDraggableDefaults.flingBehavior(
        state = state.anchored,
        positionalThreshold = { distance -> distance * 0.5f },
        animationSpec = snapSpec,
    )
    val collapseLabel = stringResource(Res.string.chat_panel_collapse)
    val dockColor = gistiDockColor()

    // Composition gate: mount the (heavy) history + input only while the overlay is visible (offset
    // below the Docked anchor). Driven off a snapshotFlow so reading the frequently-changing offset
    // never recomposes — only the boolean flip does. Stays true through the WHOLE close animation
    // (offset > 0 until fully settled) so the content does not vanish mid-shrink.
    var rendered by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        var screenHeightPx by remember { mutableStateOf(0) }

        // Publish anchors once we know the screen height + dock start height. Full = offset 0 (covers
        // the screen); Docked = offset `range` (== the dock's height). Preserve the current target
        // across a re-measure (keyboard toggle / dock height change) so a content re-measure never
        // flips the intent (mirrors the dock's updateAnchors(newTarget)).
        LaunchedEffect(screenHeightPx, dockStartHeightPx) {
            if (screenHeightPx > 0) {
                val range = (screenHeightPx - dockStartHeightPx).coerceAtLeast(0).toFloat()
                state.anchored.updateAnchors(
                    DraggableAnchors {
                        FullAnchor.Full at 0f
                        FullAnchor.Docked at range
                    },
                    newTarget = state.anchored.targetValue,
                )
            }
        }

        // Composition gate: visible while the surface is taller than its docked start (offset below the
        // Docked anchor `range`). Keyed on the measured heights so `range` is fresh; snapshotFlow so
        // reading the frequently-changing offset never recomposes — only the boolean flip does. Stays
        // true through the WHOLE close animation (offset > 0) so content does not vanish mid-shrink.
        LaunchedEffect(state, screenHeightPx, dockStartHeightPx) {
            val range = (screenHeightPx - dockStartHeightPx).coerceAtLeast(0).toFloat()
            snapshotFlow {
                val off = state.anchored.offset
                !off.isNaN() && range > 0.5f && off < range - 0.5f
            }
                .distinctUntilChanged()
                .collect { rendered = it }
        }

        // Invisible full-screen measurer: captures the available height (does NOT gate on `rendered`).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { screenHeightPx = it.height },
        )

        if (rendered) {
            val startPx = dockStartHeightPx
            Surface(
                color = dockColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Height = screenHeight − offset, read in the layout phase (never composition).
                    .layout { measurable, constraints ->
                        val screenH = if (screenHeightPx > 0) screenHeightPx else constraints.maxHeight
                        val range = (screenH - startPx).coerceAtLeast(0).toFloat()
                        val off = state.anchored.offset.let { if (it.isNaN()) range else it }
                        val minH = startPx.coerceIn(0, screenH)
                        val h = (screenH - off).roundToInt().coerceIn(minH, screenH)
                        val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                        layout(placeable.width, h) { placeable.place(0, 0) }
                    }
                    // Corners 28→0 + fast fade, driven off the reveal fraction (offset) in the draw phase.
                    .graphicsLayer {
                        val screenH = if (screenHeightPx > 0) screenHeightPx.toFloat() else size.height
                        val range = (screenH - startPx).coerceAtLeast(0f)
                        val off = state.anchored.offset
                        val f = when {
                            range <= 0f -> 1f
                            off.isNaN() -> 0f
                            else -> ((range - off) / range).coerceIn(0f, 1f)
                        }
                        val corner = 28.dp.toPx() * (1f - f)
                        shape = RoundedCornerShape(topStart = corner, topEnd = corner, bottomEnd = 0f, bottomStart = 0f)
                        clip = true
                        // Fade in over the first ~16% so the content-swap from the dock is not visible.
                        alpha = (f * 6f).coerceIn(0f, 1f)
                    },
            ) {
                Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    // ── Top handle: grabber pill (drag DOWN collapses) + chevron-down button ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .anchoredDraggable(
                                state = state.anchored,
                                orientation = Orientation.Vertical,
                                flingBehavior = fling,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                        )
                        IconButton(
                            onClick = onCollapse,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = AppDimens.SpacingXs)
                                .semantics { contentDescription = collapseLabel },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(AppDimens.IconSizeMd),
                            )
                        }
                    }

                    // ── Full conversation history (fills remaining space, scrolls internally) ──
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        historyContent()
                    }

                    // ── Recording overlay + pinned input (own the ime ∪ navbar inset once) ──
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
                    ) {
                        recordingOverlay?.invoke()
                        inputContent()
                    }
                }
            }
        }
    }
}
