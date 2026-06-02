package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
                                .heightIn(min = 96.dp, max = 160.dp),
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
