package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_panel_collapse
import aichecklists.core.designsystem.generated.resources.chat_panel_help_description
import aichecklists.core.designsystem.generated.resources.chat_panel_open_full
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Inline bottom-anchored overlay chat panel for the Gisti AI assistant.
 *
 * Replaces [GistiChatSheet] (modal bottom sheet) with an **inline overlay** that sits at the
 * bottom of the screen inside the nav content area. Per the redesign decision (2026-06-01):
 *
 *  - **No system scrim, no dim.** The screen behind the panel stays bright and tappable; the
 *    panel does NOT take over the screen. Dismissal is via the banner chevron only
 *    ([onDismiss]) — there is no tap-outside-to-close, because nothing is dimmed.
 *  - **One answer field, not a mini-chat list.** Instead of a scrolling list of every message,
 *    the panel shows a single fixed-height field with the assistant's latest turn
 *    ([lastAnswerContent]). The full conversation lives behind the "open full screen" icon.
 *
 * Visual structure (top → bottom inside the panel Surface):
 * ```
 * ┌──── Banner row (48dp) ─────────────────────────────────────────────┐
 * │  [⌄ collapse]  [?]  |  Sparkle + context label (weight=1)    [↗]  │
 * ├────────────────────────────────────────────────────────────────────┤
 * │  Answer field  (fixed height 96–160dp, vertically scrollable)      │
 * │    ↳ lastAnswerContent  — last assistant bubble / preview / plan   │
 * │   — or emptyStateContent when hasLastAnswer == false —             │
 * ├────────────────────────────────────────────────────────────────────┤
 * │  inputContent slot  (ChatInputRow — owns .imePadding() itself)     │
 * └────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * The panel is shown/hidden via [AnimatedVisibility] (slide-in from bottom). The outer
 * `fillMaxSize` Box is ONLY a layout anchor for `align(BottomCenter)` — it has no background
 * and no click handler, so taps outside the panel Surface pass through to the screen content
 * underneath (no interaction capture).
 *
 * **Decoupling:** `core/designsystem` must NOT import `feature/aichat`. All dynamic content is
 * provided via slots:
 * - [lastAnswerContent] — renders the latest assistant turn. App.kt picks the right surface
 *   (ChatMessageBubble for the last assistant message, ChatPreviewCard for a pending preview,
 *   or AgentPlanCard for a pending agent plan). This closes the prior blocker where confirm
 *   cards never rendered in the dock.
 * - [emptyStateContent] — shown when [hasLastAnswer] is false (greeting + prompt chips).
 * - [inputContent] — the input row slot (App.kt passes ChatInputRow; it owns its own imePadding).
 * - [recordingOverlay] — optional slot rendered directly above the input row while a voice
 *   recording is in progress. App.kt passes `ChatRecordingOverlay` (from feature/aichat) here,
 *   so the same pink "Recording…" surface that the full ChatScreen shows also appears in the
 *   dock. designsystem stays decoupled — it never imports the overlay, only hosts the slot.
 *
 * @param isVisible         Whether the panel is visible. Controls [AnimatedVisibility].
 * @param hasLastAnswer     When true, [lastAnswerContent] is shown; when false,
 *                          [emptyStateContent] is shown. App.kt computes this from chat state
 *                          (true when there is a last assistant message / pending preview /
 *                          pending agent plan / processing indicator to surface).
 * @param onDismiss         Called when the banner chevron-down is tapped (collapse).
 * @param onExpandClick     Called when the "open full screen" icon is tapped.
 * @param onHelpClick       Called when the "?" help icon is tapped.
 * @param lastAnswerContent Slot rendering the latest assistant turn (bubble / preview / plan).
 * @param emptyStateContent Slot shown when [hasLastAnswer] is false.
 * @param inputContent      Slot for the chat input row (must own its own imePadding).
 * @param modifier          Layout modifier for the anchor Box.
 * @param contextLabel      Optional context hint shown in the banner (e.g. "Grocery list").
 * @param recordingOverlay  Optional slot shown above the input row during voice recording.
 *                          The slot itself controls its own visibility (App.kt passes a
 *                          ChatRecordingOverlay that animates in/out via `isRecording`), so
 *                          this can always be supplied; it renders nothing when not recording.
 */
@Composable
fun GistiInlineChatPanel(
    isVisible: Boolean,
    hasLastAnswer: Boolean,
    onDismiss: () -> Unit,
    onExpandClick: () -> Unit,
    onHelpClick: () -> Unit,
    lastAnswerContent: @Composable () -> Unit,
    emptyStateContent: @Composable () -> Unit,
    inputContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contextLabel: String? = null,
    recordingOverlay: (@Composable () -> Unit)? = null,
    // Max height of the answer frame. Defaults to 210dp (~30% taller than the original 160dp,
    // per user request to give the expanded chat sheet more room) so a long assistant answer
    // scrolls inside the frame instead of growing the dock. App.kt raises this further for an
    // interactive choice block (prompt + chips + escape) so its escape/cancel chip is not
    // clipped below the fold — a bounded block, unlike an unbounded text answer.
    answerMaxHeight: Dp = 210.dp,
) {
    val isDark = LocalIsDarkTheme.current
    // Layout-anchor Box only: no background, no clickable → taps outside the panel
    // Surface fall through to the screen content beneath (the redesign drops the scrim).
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight / 3 },
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing,
                ),
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 250),
            ) + fadeOut(animationSpec = tween(durationMillis = 250)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    // Clean white panel: plain `surface` with NO tonal overlay. Depth comes from
                    // the drop shadow alone (light), matching AskGistiBar / AppCard. A tonalElevation
                    // would mix +primary into the surface and make the dock look grey-tinted (the
                    // "страшные цвета" the user reported). In dark the shadow is invisible against a
                    // dark surface, so a hairline top border (outlineVariant) separates the dock from
                    // the content behind it.
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = if (isDark) 0.dp else 8.dp,
                    border = if (isDark) {
                        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    } else {
                        null
                    },
                ) {
                    // Bottom inset (keyboard + system-nav) is owned HERE, applied exactly once via
                    // ime.union(navigationBars): when the keyboard is open the padding equals the IME
                    // height; when closed it equals the nav-bar height. This keeps the input row clear
                    // of the gesture pill at the screen bottom (the small gap the user reported) with
                    // no double-inset while typing. ChatInputRow deliberately drops its own imePadding.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
                    ) {
                        // ── Banner row ──────────────────────────────────────────────
                        GistiInlinePanelBanner(
                            contextLabel = contextLabel,
                            onHelpClick = onHelpClick,
                            onExpandClick = onExpandClick,
                            onCollapseClick = onDismiss,
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )

                        // ── Answer field (single latest turn) ────────────────────────
                        // Fixed-height frame: a short answer keeps the panel compact while a
                        // long answer scrolls inside the frame instead of growing the dock.
                        // App.kt fills lastAnswerContent with the last assistant bubble OR a
                        // pending preview/plan confirm card, so commands can be confirmed
                        // right here in the dock.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 125.dp, max = answerMaxHeight),
                        ) {
                            if (hasLastAnswer) {
                                lastAnswerContent()
                            } else {
                                emptyStateContent()
                            }
                        }

                        // ── Recording overlay slot ─────────────────────────────────
                        // Rendered directly above the input row, mirroring the full
                        // ChatScreen layout. The slot animates its own visibility, so it
                        // takes zero height when not recording.
                        recordingOverlay?.invoke()

                        // ── Input row slot ─────────────────────────────────────────
                        // ChatInputRow owns its own .imePadding() — do NOT wrap here.
                        inputContent()
                    }
                }
            }
        }
    }
}

/**
 * Banner row at the top of [GistiInlineChatPanel].
 *
 * Layout (48dp height, left→right):
 *   [ChevronDown 40dp] [Help 40dp] | [SparkleTile 22dp] [SpacingXs] [context label / "Gisti" weight=1f] | [OpenInFull 40dp]
 *
 * The chevron-down collapses the panel; the OpenInFull icon navigates to the full Chat route.
 * The help icon (HelpOutline) opens the features sheet.
 */
@Composable
private fun GistiInlinePanelBanner(
    contextLabel: String?,
    onHelpClick: () -> Unit,
    onExpandClick: () -> Unit,
    onCollapseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val collapseLabel = stringResource(Res.string.chat_panel_collapse)
    val helpLabel = stringResource(Res.string.chat_panel_help_description)
    val openFullLabel = stringResource(Res.string.chat_panel_open_full)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = AppDimens.SpacingXs),
    ) {
        // Chevron-down: collapses the panel (left anchor)
        IconButton(
            onClick = onCollapseClick,
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = collapseLabel },
        ) {
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )
        }

        // Help icon — opens AiChatFeaturesHelpSheet
        IconButton(
            onClick = onHelpClick,
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = helpLabel },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )
        }

        // Center: Gisti identity + optional context label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = AppDimens.SpacingXs),
        ) {
            SparkleTile(size = 22.dp)
            Spacer(Modifier.size(AppDimens.SpacingXs))
            Column {
                Text(
                    text = "Gisti",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (contextLabel != null) {
                    Text(
                        text = contextLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        // Open full screen icon
        IconButton(
            onClick = onExpandClick,
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = openFullLabel },
        ) {
            Icon(
                imageVector = Icons.Filled.OpenInFull,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )
        }
    }
}

/** The two stable positions of the expandable chat dock. [Peek] is the floor (never hides further). */
enum class DockAnchor { Peek, Expanded }

/**
 * Optional binding that switches the shared Gisti chat dock from its default AI-chat mode into
 * **item-create mode** (the checklist-detail "+" button reuses the dock's bottom input to add an
 * item instead of a separate inline field).
 *
 * When the host passes a non-null override into the `chatDockContent` slot:
 *  - the dock's pinned input binds to [text] / [onTextChange] / [onSend] / [canSend] (the checklist
 *    ViewModel's create path) instead of the ChatViewModel,
 *  - the chat answer/greeting frame is hidden and [chips] (reminder presets + property toggles)
 *    render in the always-visible expanded frame above the input,
 *  - Send creates a checklist item (it never calls the AI chat).
 *
 * When the override is null the dock behaves exactly as the AI-chat dock — every other call-site
 * (MainScreen) passes null and is unaffected.
 *
 * @param text         Current input text (mirrors the checklist VM's pending item input).
 * @param onTextChange Fired on every keystroke (drives the live Smart-Add parser preview).
 * @param onSend       Fired on Send — creates the item, clears the input, keeps item-create mode.
 * @param canSend      True when the input is non-blank (Send disabled otherwise → no blank add).
 * @param chips        The selectable item-create chips (reminder presets + Important/Repeat),
 *                     rendered in the expanded frame above the input.
 */
class ChatDockItemCreateOverride(
    val text: String,
    val onTextChange: (String) -> Unit,
    val onSend: () -> Unit,
    val canSend: Boolean,
    val chips: @Composable () -> Unit,
)

/**
 * Reveal progress of the dock: 0f at [DockAnchor.Peek] (frosted glass, collapsed) → 1f at
 * [DockAnchor.Expanded] (opaque panel). Safe before anchors are measured (offset == NaN → 0f).
 * Read this ONLY inside layout/draw/graphicsLayer lambdas — `offset`/`progress` are
 * `@FrequentlyChangingValue`, so a composition read recomposes every pixel (the old jank class).
 */
fun AnchoredDraggableState<DockAnchor>.dockProgress(): Float =
    if (offset.isNaN()) 0f else progress(DockAnchor.Peek, DockAnchor.Expanded)

/**
 * Continuous, finger-following AI-chat dock content (Approach A v2 — replaces the discrete
 * `AnimatedVisibility(expanded)` morph that felt janky). Built on the [AnchoredDraggableState]
 * primitive (what BottomSheetScaffold is built on) but with OUR layout, so there is no peek-from-top
 * inversion: the input row stays PINNED at the bottom (the host applies its IME inset) and the
 * banner+answer panel ABOVE it is the draggable sheet that grows continuously upward.
 *
 * Gesture surface:
 *  - The slim **grabber** carries `Modifier.anchoredDraggable` → 1:1 finger drag + velocity fling to
 *    the nearest anchor with a bouncy spring snap.
 *  - The reveal panel carries a [NestedScrollConnection] → the expanded answer scrolls; over-drag at
 *    its top collapses the sheet; a drag-up consumes into expansion before the inner list scrolls.
 *  - Peek is the floor (anchors are only Peek & Expanded); a swipe-down from Expanded settles to Peek.
 *
 * The reveal is driven by the offset read INSIDE the panel's `Modifier.layout` (layout-phase, not a
 * composition read) so dragging never recomposes — the jank fix. The blur→opaque crossfade (R2) is
 * driven the same way via [dockProgress] inside the host's draw lambda.
 *
 * @param state            Per-screen [AnchoredDraggableState] (NOT shared across two-pane panes).
 * @param inputContent     The pinned input row; receives `onInputFocusChanged(focused)` — wire it to
 *                         the input's focus so the keyboard-up lock (FIX 2) is scoped to THIS field.
 * @param inputFocusRequester Focused when the dock settles to Expanded via a non-focus path (chip).
 * @param inputBlank       Whether the chat input is blank — on blur (keyboard dismissed) the dock
 *                         collapses to Peek only when blank (a draft holds it open).
 */
@Composable
fun GistiExpandableDockContent(
    state: AnchoredDraggableState<DockAnchor>,
    hasLastAnswer: Boolean,
    onExpandFull: () -> Unit,
    onHelpClick: () -> Unit,
    lastAnswerContent: @Composable () -> Unit,
    emptyStateContent: @Composable () -> Unit,
    inputContent: @Composable (onInputFocusChanged: (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    chipsContent: (@Composable () -> Unit)? = null,
    recordingOverlay: (@Composable () -> Unit)? = null,
    contextLabel: String? = null,
    inputFocusRequester: FocusRequester? = null,
    inputBlank: Boolean = true,
    // True while there is something in the answer frame to show — an in-flight turn (typing
    // indicator), a pending choice block, or a last answer. While true the dock must NOT auto-collapse
    // on blur: sending a message disables+blurs the input (the old auto-collapse trigger), which used
    // to slam the dock shut to Peek mid-turn and HIDE the progress indicator/answer (the reported bug).
    // The grabber drag-down still collapses the dock manually; blur-collapse still works in the empty
    // state. Read as a snapshot (see keepExpandedLatest) so it never re-asserts Expanded after a drag.
    keepExpanded: Boolean = false,
    // Height between the status bar and the keyboard top, measured by the HOST (Dp.Unspecified when
    // the keyboard is down). Used to cap the answer so the dock fits above the keyboard (FIX B).
    dockAvailableDp: Dp = Dp.Unspecified,
    answerMaxHeight: Dp = 210.dp,
    // Minimum height of the answer/empty frame. 125dp gives the chat answer/greeting a comfortable
    // body; item-create mode passes ~0 so the frame WRAPS the short chip row instead of leaving a big
    // empty gap between the chips and the pinned input below.
    answerMinHeight: Dp = 125.dp,
    // PINNED chips: keep [chipsContent] fully visible (full alpha + full height) at ALL dock positions
    // instead of fading it out with the dock-expand progress. Used by item-create, where the dock is
    // Expanded (keyboard up) yet the reminder/property chips must stay on screen for selection — and
    // pinning them makes the create⇄chat peek the SAME height (a chip row is always present), so Back
    // swaps the chip CONTENT in place with no shrink/grow. Default false = the chat peek-chip behaviour
    // (chips fade as the dock expands to reveal the answer).
    chipsPinned: Boolean = false,
) {
    val density = LocalDensity.current
    val snapSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    }
    // @Composable — must be called in composable scope (not inside remember). Drives the drag fling.
    val fling = AnchoredDraggableDefaults.flingBehavior(
        state = state,
        positionalThreshold = { distance -> distance * 0.5f },
        animationSpec = snapSpec,
    )
    val focusManager = LocalFocusManager.current

    // Tracks an ACTIVE grabber drag (DragInteraction between Start and Stop/Cancel). The panel's
    // settled-at-Peek `revealed = 0` shortcut (in the layout below) must NOT apply while the user is
    // dragging the grabber up to open the dock: during the drag targetValue is still Peek and there is
    // no animation running, so the shortcut would pin the panel to 0 and make it "pop" open only past
    // the threshold instead of following the finger from the start (a drag-to-open regression).
    val grabberInteraction = remember { MutableInteractionSource() }
    val dragging by grabberInteraction.collectIsDraggedAsState()

    // ── FIX 2: the CHAT input's focus is the keyboard-up signal (NOT a global WindowInsets.ime — that
    // would let ChecklistDetail's inline add-item keyboard expand/lock the chat dock). While focused
    // the dock is LOCKED Expanded: grabber drag + nested-scroll collapse are disabled and chips are
    // hidden. On blur (BACK or IME-done both blur the field) collapse to Peek only if the input blank.
    var chatFieldFocused by remember { mutableStateOf(false) }
    val focusedLatest by rememberUpdatedState(chatFieldFocused)
    // Snapshot (deliberately NOT a LaunchedEffect key): blocks the blur-collapse branch at the exact
    // moment focus is lost. Keying the effect on it instead would re-assert Expanded every time the
    // turn/answer state changes — fighting a manual grabber drag-down. As a snapshot it only suppresses
    // the auto-collapse while there is content to show; the grabber still collapses the dock manually.
    val keepExpandedLatest by rememberUpdatedState(keepExpanded)
    LaunchedEffect(chatFieldFocused) {
        if (state.offset.isNaN()) return@LaunchedEffect
        if (chatFieldFocused) {
            state.animateTo(DockAnchor.Expanded)
        } else if (inputBlank && !keepExpandedLatest) {
            // Collapse to Peek only when the input is blank AND there is nothing to show. Sending a
            // message disables the field (focus lost) while isProcessing flips true → keepExpanded true
            // here → the dock stays Expanded so the ChatTypingIndicator (and then the answer) is visible.
            state.animateTo(DockAnchor.Peek)
        }
    }

    // Auto-focus the input once the dock settles to Expanded via a NON-focus path (chip / grabber).
    // And — ISSUE B fix — clear focus whenever the dock heads to Peek: with the banner (and its
    // collapse chevron) gone, the grabber is the ONLY collapse affordance, and a swipe-down that
    // dismisses the keyboard leaves Compose focus = true (so chatFieldFocused stayed true → the old
    // grabber was disabled → the dock got STUCK expanded). Clearing focus on the Peek target dismisses
    // the keyboard and lets the blank-blur path settle the collapse. The grabber is now always
    // draggable (see the drag-target overlay below), so dragging it down reliably targets Peek here.
    LaunchedEffect(state.targetValue) {
        when (state.targetValue) {
            DockAnchor.Expanded -> {
                delay(120)
                runCatching { inputFocusRequester?.requestFocus() }
            }
            DockAnchor.Peek -> focusManager.clearFocus()
        }
    }

    // ── FIX B: cap the answer so the WHOLE dock fits above the keyboard — the bottom input then stays
    // visible. The cap uses [dockAvailableDp] (height between the status bar and the keyboard top),
    // computed by the HOST where WindowInsets.ime is reliable. A deep in-morph ime read returns 0 once
    // the host applies imePadding (it consumes the inset) — that 0 made the previous cap = full screen,
    // so the 414dp answer pushed the input under the keyboard on the phone. Now: answer ≤
    // (available − measured input − fixed chrome); it scrolls inside its frame. Unspecified ⇒ keyboard
    // down ⇒ use the design cap [answerMaxHeight] so the dock doesn't fill the whole screen. ──
    var inputContentPx by remember { mutableStateOf(0) }
    // Fixed chrome above the answer that is NOT the input: the grabber zone + the dock's column gaps.
    // The banner row was removed (2026-06-26), so it no longer contributes ~48dp here — that space now
    // goes to the answer. (When the keyboard is up the weight layout is the real guarantee that the
    // input fits; this cap only keeps the answer a comfortable height.)
    val chromeFixed = DockGrabberHeight + AppDimens.SpacingSm + AppDimens.SpacingSm
    val effectiveAnswerMax = if (dockAvailableDp.isSpecified) {
        val inputDp = with(density) { inputContentPx.toDp() }
        minOf(answerMaxHeight, (dockAvailableDp - inputDp - chromeFixed)).coerceAtLeast(72.dp)
    } else {
        answerMaxHeight
    }

    // Nested scroll: drag-up consumes into expansion before the inner answer scrolls; leftover
    // down-drag collapses the sheet; flings settle. ALL disabled while the chat field is focused
    // (dock locked Expanded) so the inner answer scrolls freely without collapsing the dock.
    val nestedScroll = remember(state, snapSpec) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                return if (!focusedLatest && delta < 0f && source == NestedScrollSource.UserInput && !state.offset.isNaN()) {
                    Offset(0f, state.dispatchRawDelta(delta))
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                return if (!focusedLatest && source == NestedScrollSource.UserInput && !state.offset.isNaN()) {
                    Offset(0f, state.dispatchRawDelta(available.y))
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (!focusedLatest && available.y < 0f && state.targetValue != DockAnchor.Expanded && !state.offset.isNaN()) {
                    state.settle(snapSpec)
                    available
                } else {
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!focusedLatest && !state.offset.isNaN()) state.settle(snapSpec)
                return available
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Grabber: the drag gesture lives HERE, on the concrete handle (a real Column child). An
        // earlier attempt put anchoredDraggable on a transparent sibling OVERLAY (to get a big hit zone
        // with a thin visual) — but Compose did not route drags to that transparent overlay in this
        // tree, so the grabber went completely dead. anchoredDraggable on the handle itself is the
        // proven-working configuration. ISSUE B: always enabled (no !chatFieldFocused gate) so the dock
        // can ALWAYS be collapsed — dragging down targets Peek, and the targetValue→Peek effect clears
        // focus to dismiss the keyboard. The handle is a thin full-width bar (easy to hit). ──
        DockGrabberHandle(
            modifier = Modifier.anchoredDraggable(
                state = state,
                orientation = Orientation.Vertical,
                enabled = true,
                flingBehavior = fling,
                interactionSource = grabberInteraction,
            ),
        )

        // ── Reveal panel: banner + answer. Visible height = (full − offset), read INSIDE the layout
        // lambda so dragging relayouts (NOT recomposes). Full height feeds the anchors. ──
        var panelFullPx by remember { mutableStateOf(0) }
        LaunchedEffect(panelFullPx) {
            if (panelFullPx > 0) {
                state.updateAnchors(
                    DraggableAnchors {
                        DockAnchor.Expanded at 0f
                        DockAnchor.Peek at panelFullPx.toFloat()
                    },
                    // Preserve the CURRENT target across an anchor re-measure. Default updateAnchors
                    // re-picks newTarget = closestAnchor(offset); when the panel GROWS mid-collapse (the
                    // chat-answer frame re-appears as item-create exits, or the keyboard toggles), the
                    // small offset becomes closer to Expanded(0) than to the now-distant Peek anchor, so
                    // the default flips the target back to Expanded — the dock springs open as a chat
                    // (the "second Back re-opens the chat" bug). A content-driven re-measure must not
                    // change the user/programmatic intent — only reposition the offset to the new anchors.
                    newTarget = state.targetValue,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // FIX (keyboard-up input clipping): the dock's outer Column receives a BOUNDED max height
                // (the host applies .imePadding() → screen − IME − navbar). With every child at its natural
                // height the grabber + banner + answer consumed that whole budget, so the pinned input —
                // the LAST child — got ~0 remaining height and did not render above the keyboard. While the
                // chat field is focused the dock is LOCKED Expanded and the drag is DISABLED, so the
                // offset-driven reveal is inert and it is SAFE to flex this panel: weight(fill = false)
                // makes the Column measure the NON-weighted input first (natural height, guaranteed), then
                // hand the LEFTOVER bounded height to this answer panel, which scrolls internally. Not
                // focused → no weight → the natural-height offset-driven reveal + anchor measurement below
                // are completely unchanged (so peek/drag still works).
                .then(if (chatFieldFocused) Modifier.weight(1f, fill = false) else Modifier)
                .nestedScroll(nestedScroll)
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val full = placeable.height
                    // When the dock is SETTLED at Peek (not mid-animation), force revealed = 0 — fully
                    // collapsed — regardless of the measured panel height. Otherwise a content swap at Peek
                    // (item-create chips → the taller chat-answer frame as item-create exits) grows `full`
                    // in THIS layout pass while `state.offset` (the Peek anchor) only catches up one frame
                    // later via updateAnchors → for 1–2 frames `full - off > 0` and the panel briefly
                    // reveals ("jumps up" before becoming the plain chat input). During the collapse
                    // animation isAnimationRunning is true → the offset-driven reveal runs (smooth close).
                    val revealed = if (state.targetValue == DockAnchor.Peek && !state.isAnimationRunning && !dragging) {
                        0
                    } else {
                        val off = if (state.offset.isNaN()) full.toFloat() else state.offset
                        (full - off).roundToInt().coerceIn(0, full)
                    }
                    layout(placeable.width, revealed) {
                        placeable.placeRelative(0, 0)
                    }
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // The Peek anchor MUST equal the reveal panel's ACTUAL current height — including the
                    // weight-constrained height while focused. This was previously guarded with
                    // `if (!focusedLatest)`, which FROZE panelFullPx at the stale pre-focus (peek) height.
                    // Because the dock is always focused when Expanded (reaching Expanded auto-focuses the
                    // input) and IME-Back hides the keyboard WITHOUT clearing Compose focus, the Peek
                    // anchor stayed far short of the real panel height — so a DOWN-drag from Expanded could
                    // never reach Peek and the dock would not collapse, while UP-drag from the genuinely
                    // unfocused Peek state worked (the reported asymmetry). Tracking the live height keeps
                    // the grabber drag symmetric: drag DOWN by the panel height fully collapses to Peek.
                    .onSizeChanged { panelFullPx = it.height },
            ) {
                // Banner row (Gisti label + help "?" + open-fullscreen "↗" + collapse "⌄") removed per the
                // 2026-06-26 request to reclaim that vertical space for the chat (a key funnel area). The
                // expanded dock now opens straight into the answer. Collapse is via the grabber (swipe
                // down) or by dismissing the keyboard — the dock auto-collapses to Peek on blur when the
                // input is blank. The full chat + features help remain reachable from the drawer → AI Chat.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minOf(answerMinHeight, effectiveAnswerMax), max = effectiveAnswerMax),
                ) {
                    if (hasLastAnswer) lastAnswerContent() else emptyStateContent()
                }
            }
        }

        // ── Chips (peek only). FIX A: visibility derives PURELY from the dock anchor progress — chips
        // alpha/height = (1 − progress), one source of truth. So EVERY collapse path (chevron, swipe,
        // fling, route-change) restores them: progress → 0 ⇒ chips back. (Keyboard-up still hides them
        // because focus animates the dock to Expanded ⇒ progress 1 ⇒ chips gone — no separate boolean
        // gate, which is what desynced them before.) FIX 3: clipToBounds (needed for the height
        // collapse) would clip the chips' bottom shadow at peek, so the chip row carries a few dp of
        // bottom padding INSIDE the measured content to keep its lower edge within bounds. ──
        if (chipsContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Pinned (item-create) → always full alpha/height; otherwise fade+collapse with the
                    // dock-expand progress (chat peek chips). One source of truth = dockProgress.
                    .graphicsLayer { alpha = if (chipsPinned) 1f else 1f - state.dockProgress() }
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val factor = if (chipsPinned) 1f else 1f - state.dockProgress()
                        val h = (placeable.height * factor).roundToInt().coerceIn(0, placeable.height)
                        layout(placeable.width, h) { placeable.placeRelative(0, 0) }
                    },
            ) {
                Box(modifier = Modifier.padding(bottom = AppDimens.SpacingXs)) {
                    chipsContent()
                }
            }
        }

        // Recording overlay slot (zero height when idle) directly above the pinned input.
        recordingOverlay?.invoke()

        // ── Pinned input. The bottom inset (ime ∪ navbar) is now owned by the HOST (GistiGlassChatDock
        // gets .imePadding().navigationBarsPadding()), which reliably lifts the WHOLE dock above the
        // keyboard — the previous deep windowInsetsPadding(ime) here read ≈0 on the phone. So NO inset
        // here (single owner = host). The Box measures the input content height for the answer cap
        // (FIX B); its focus flips the keyboard-up lock (FIX 2). ──
        Box(modifier = Modifier.onSizeChanged { inputContentPx = it.height }) {
            inputContent { focused -> chatFieldFocused = focused }
        }
    }
}

/**
 * Height of the dock's grab handle — a thin, full-width 24dp bar with a small centered pill. The whole
 * bar is the drag/touch target (the caller puts [Modifier.anchoredDraggable] on it), so it stays a
 * compact visual the user liked while still being an easy full-width grab. A taller transparent overlay
 * was tried to enlarge the hit zone without added height, but Compose did not route drags to it — so the
 * gesture lives on this concrete handle instead. Also feeds [chromeFixed] so the answer cap stays right.
 */
private val DockGrabberHeight = 24.dp

/** The grabber handle. The drag gesture comes from the caller's [Modifier.anchoredDraggable] applied
 *  across this whole full-width [DockGrabberHeight] bar; the visible pill is the small centered bar. */
@Composable
private fun DockGrabberHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DockGrabberHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}
