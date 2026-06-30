package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Collapsed AI-chat dock bar used on both MainScreen and ChecklistDetailScreen bottomBar.
 *
 * This is a **drop-in replacement** for the existing [AskGistiBar] in contexts where the bar
 * should signal it opens a bottom-sheet (rather than navigating full-screen). The visual
 * language is identical to [AskGistiBar]; the only additions are:
 *
 *  1. A **ChevronUp icon** at the trailing-start position (before the mic), rendered in
 *     `primary` color to reinforce "tap to expand" affordance. On MainScreen this replaces
 *     the blank space after the mic; on Detail it reads as "lift the sheet".
 *  2. Optional **`contextLabel`** replaces the placeholder text when the host screen is
 *     context-aware (e.g. "Ask about this list…" on ChecklistDetailScreen). Pass `null`
 *     to show the default placeholder.
 *
 * Visual spec:
 *  - Height: 56dp, corner radius: 16dp (matches AskGistiBar)
 *  - Container: `surfaceContainerLowest`
 *  - Border: 1.5dp `outlineVariant`
 *  - Shadow: 2dp (light only)
 *  - Left:  [SparkleTile] 28dp
 *  - Center: placeholder/context text, `onSurfaceVariant`
 *  - Trailing: ChevronUp 24dp in `primary` + Mic 40dp IconButton in `onSurfaceVariant`
 *
 * @param placeholder     Default placeholder when [contextLabel] is null.
 * @param onClick         Opens the chat sheet (the whole row except the mic button).
 * @param onMicClick      Starts voice input — opens the sheet with the recording overlay.
 * @param contextLabel    When non-null, shown instead of [placeholder] (e.g. "Ask about this list…").
 * @param micContentDescription Accessibility label for the mic [IconButton].
 */
@Composable
fun GistiChatDock(
    placeholder: String,
    onClick: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
    contextLabel: String? = null,
    micContentDescription: String = "Voice input",
) {
    val isDark = LocalIsDarkTheme.current
    val shape = RoundedCornerShape(16.dp)
    val displayText = contextLabel ?: placeholder

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (isDark) 0.dp else 2.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                    role = Role.Button,
                )
                .semantics {
                    contentDescription = "Open Gisti AI chat"
                }
                .padding(start = 14.dp, end = 4.dp),
        ) {
            SparkleTile(size = 28.dp)

            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            // ChevronUp: visual affordance that this bar expands upward into a sheet.
            // Rendered in `primary` so it reads as the "active" cue alongside the neutral text.
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null, // decorative — Row semantics covers the action
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )

            // Mic — independent 48dp touch target (does NOT trigger the sheet)
            IconButton(
                onClick = onMicClick,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = micContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
            }
        }
    }
}

/**
 * Generic floating glassmorphism chat-dock shell shared by **MainScreen** and
 * **ChecklistDetailScreen**.
 *
 * Place this as a **sibling overlay** (e.g. `Box` with `Modifier.align(BottomCenter)`) ON TOP of the
 * scrolling content. The host MUST mark that scrolling content with `Modifier.hazeSource(hazeState)`
 * as a SIBLING of this dock (never a parent — that would cause self-sampling). The strip's backdrop
 * is a live blur of whatever scrolls behind it, sampled from [hazeState] via [Modifier.hazeEffect].
 *
 * Layout (inside the clipped, blurred strip):
 *
 * ```
 * ┌─────────────────────────────────────────────────────┐  ← full-width strip, top corners 28dp,
 * │  ▁▁▁▁▁▁ frosted glass strip (blurred backdrop) ▁▁▁▁  │     full-width strip, top corners 28dp
 * │  [optional chipsContent — edge-to-edge]             │
 * │  ┌─[pillContent]───────────────────────────────┐    │
 * │  │ ✨  Ask Gisti…            ↑          🎤       │    │
 * │  └──────────────────────────────────────────────┘   │
 * └─────────────────────────────────────────────────────┘
 *   padding: top=24dp (frost strip), bottom=16dp + navigationBarsPadding; pill inset by the caller
 * ```
 *
 * Why a single shared shell: the blur wiring (clip → hazeEffect(style) → padded Column with
 * navigationBarsPadding) must stay identical on both screens. Duplicating it would let
 * the two docks drift (one renders the blur, the other a flat tint — exactly the bug this dock
 * recovered from when the project sat on the 2.0-alpha `haze-blur` `blurEffect{}` API whose backdrop
 * never rendered). Both screens supply their own [pillContent] (command bar) and optional
 * [chipsContent]; only the glass strip is shared.
 *
 * Visible-blur tuning (the app background is near-white #FBFAF8, so a naive glass is invisible):
 *  - `blurRadius = 16.dp` — a moderate blur; content under the strip stays recognisable (silhouettes
 *    and colours read through) rather than smeared into an opaque wash.
 *  - `tint` is **translucent** (alpha 0.4): a high-alpha tint masks the blur and reads as a flat
 *    solid plate; keeping it < 0.5 lets the blurred content show through as frosted glass.
 *  - The Column adds top padding so a strip of pure frosted glass sits ABOVE the (opaque) chips/pill
 *    where the blur is directly perceivable.
 *
 * Layout note (edge-to-edge chips): [chipsContent] is rendered OUTSIDE any horizontal padding so the
 * chip row keeps its own `contentPadding` edge-bleed (the last chip scrolls out from under the screen
 * edge as a scroll affordance). The caller insets [pillContent] horizontally instead (see
 * `.claude/rules/ui-card-patterns.md`).
 *
 * @param hazeState     Shared [HazeState] whose source is the scrolling content behind this dock.
 * @param bottomPadding Gap below the pill, ABOVE the navigation-bar inset (default [AppDimens.SpacingLg]).
 *                      MainScreen passes a smaller value to sit the bar closer to the screen bottom.
 * @param chipsContent  Optional slot for a prompt-chip row, rendered above the pill with NO outer
 *                      horizontal padding (the chips inset themselves). Pass `null` for pill only.
 * @param pillContent   The command bar (e.g. [AskGistiBar] / [GistiChatDock]); the caller insets it
 *                      horizontally so the pill stays padded while the chips above stay edge-to-edge.
 */
@Composable
fun GistiGlassChatDock(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = AppDimens.SpacingLg,
    // Optional slot rendered as the TOPMOST element of the dock (above the chips) — the drag grabber.
    grabberContent: (@Composable () -> Unit)? = null,
    chipsContent: (@Composable () -> Unit)? = null,
    pillContent: @Composable () -> Unit,
) {
    val dockShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    // Variant B "Crisp hairline": the dock reads as a distinct foreground panel via a 1dp hairline
    // (outlineVariant) tracing its top edge + rounded corners — NOT via a tinted surface. The earlier
    // "FIX D" used a flat grey (surfaceContainerLow) on the near-white page, but the ~2% lightness gap
    // (dock #F6F5F2 vs page #FBFAF8) plus no border made the dock blend into the background. Now the
    // light surface is pure white (surfaceContainerLowest) + hairline; dark keeps surfaceContainerLow
    // (a touch lighter than the page) + hairline. Separation comes from the crisp line, so it never
    // goes muddy-grey and renders identically on Android and Web/Skiko. ([hazeState] is retained for
    // call-site/source compatibility but is no longer sampled — the dock no longer blurs behind it.)
    val dockColor = gistiDockColor()
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant
    val legacyDock = DockDesignDebug.useLegacyDock

    Surface(
        // Hairline traces the TOP edge + the two top corners ONLY — never the bottom. The dock sits
        // navbar-padded directly above the (same-colour) system-nav strip, so a full border would
        // draw a stray 1dp divider BETWEEN the dock and that strip. Stroking only the top keeps the
        // page↔dock separation while the dock flows seamlessly into the nav strip below it.
        // Suppressed in the legacy debug variant (the old dock had no border at all).
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                if (!legacyDock) {
                    val stroke = 1.dp.toPx()
                    val r = 28.dp.toPx()
                    val inset = stroke / 2f
                    val hairline = Path().apply {
                        moveTo(inset, size.height)
                        lineTo(inset, r)
                        arcTo(Rect(inset, inset, inset + 2 * r, inset + 2 * r), 180f, 90f, false)
                        lineTo(size.width - r, inset)
                        arcTo(Rect(size.width - inset - 2 * r, inset, size.width - inset, inset + 2 * r), 270f, 90f, false)
                        lineTo(size.width - inset, size.height)
                    }
                    drawPath(hairline, color = hairlineColor, style = Stroke(width = stroke))
                }
            },
        shape = dockShape,
        color = dockColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.SpacingSm, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            // Grabber TOPMOST (above the chips), then chips, then the pill/morph content.
            grabberContent?.invoke()
            // Contextual prompt chips — edge-to-edge (own contentPadding, NO outer horizontal
            // padding here so the last chip bleeds past the screen edge as a scroll affordance).
            chipsContent?.invoke()
            pillContent()
        }
    }
}

/**
 * Background colour of [GistiGlassChatDock] ("Crisp hairline" variant). Exposed so any system
 * navigation-bar strip painted behind the dock (e.g. MainScreen / ChecklistDetailScreen) stays in
 * sync with the dock surface — the strip and the dock MUST read as one continuous surface, so the
 * colour lives in exactly ONE place. Light = pure white (surfaceContainerLowest); dark keeps
 * surfaceContainerLow (a touch lighter than the page). Separation from the page comes from the
 * dock's 1dp [outlineVariant][androidx.compose.material3.ColorScheme.outlineVariant] hairline.
 */
@Composable
@ReadOnlyComposable
fun gistiDockColor(): Color =
    if (DockDesignDebug.useLegacyDock) {
        // Legacy (pre-"Crisp hairline") flat-grey dock — same surfaceContainerLow in light & dark.
        MaterialTheme.colorScheme.surfaceContainerLow
    } else if (LocalIsDarkTheme.current) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

/**
 * Floating glassmorphism chat dock for **ChecklistDetailScreen** — a thin wrapper over
 * [GistiGlassChatDock] that supplies the context-aware [GistiChatDock] pill (placeholder replaced by
 * the quoted [checklistName]). All blur/strip mechanics live in [GistiGlassChatDock]; see its KDoc
 * for the hazeSource sibling contract.
 *
 * @param hazeState           Shared [HazeState] whose source is the scrolling content behind this dock.
 * @param checklistName       Current checklist name — shown (quoted) as the context label.
 * @param onChatClick         Opens the chat sheet anchored to this checklist.
 * @param onMicClick          Opens the sheet in voice-input mode.
 * @param chatPlaceholder     Localised placeholder for the dock bar.
 * @param micContentDescription Accessibility label for mic.
 * @param chipsContent        Optional contextual prompt-chip row rendered above the dock pill.
 */
@Composable
fun ChecklistDetailChatDock(
    hazeState: HazeState,
    checklistName: String,
    onChatClick: () -> Unit,
    onMicClick: () -> Unit,
    chatPlaceholder: String = "Ask Gisti…",
    micContentDescription: String = "Voice input",
    modifier: Modifier = Modifier,
    bottomPadding: Dp = AppDimens.SpacingLg,
    chipsContent: (@Composable () -> Unit)? = null,
) {
    GistiGlassChatDock(
        hazeState = hazeState,
        modifier = modifier,
        bottomPadding = bottomPadding,
        chipsContent = chipsContent,
        pillContent = {
            // Context-aware dock: "“<name>”". Horizontal screen padding lives HERE (on the pill only)
            // so it is inset normally while the chips above stay edge-to-edge.
            GistiChatDock(
                placeholder = chatPlaceholder,
                onClick = onChatClick,
                onMicClick = onMicClick,
                contextLabel = "“$checklistName”",
                micContentDescription = micContentDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
            )
        },
    )
}
