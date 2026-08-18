package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Which surface the chat is currently being drawn ON.
 *
 * The chat has no single background. The SAME composables render in two places:
 *  - [Page] — the full `ChatScreen`, whose scaffold paints `colorScheme.surface`;
 *  - [BottomChrome] — the chat dock, the full-screen chat overlay and the quick-capture dock, all
 *    of which paint [AppSurface.bottomChrome].
 *
 * Those two are ~11 L\* apart in light and ~4 L\* apart in dark, in OPPOSITE directions, so a chip
 * spelled with one absolute colour role is only ever correct on one of them. This enum is what lets
 * one component be correct on both.
 */
enum class ChatSurfaceTone {
    /** The page: `ChatScreen`, and anything else drawn straight on `colorScheme.surface`. */
    Page,

    /** The app's bottom edge: chat dock, full-screen chat overlay, quick-capture dock. */
    BottomChrome,
}

/**
 * The plane the surrounding chat content sits on. Provided ONCE per host, by the host that paints
 * the surface — never passed down as a parameter.
 *
 * `static`, deliberately: the value changes only when a whole dock is composed or disposed, never
 * per frame, so the read is free and the subtree-wide invalidation on change is irrelevant. A
 * non-static local would add a per-read subscription to every chip in the row for a value that
 * cannot move underneath them.
 *
 * ⚠️ Provide it OUTSIDE the message list, at the host's own content root. Providing it per item
 * would re-run the provider for every row of a `LazyColumn`.
 */
val LocalChatSurfaceTone = staticCompositionLocalOf { ChatSurfaceTone.Page }

/**
 * The chat's colour system, resolved against the plane the chat is currently drawn on
 * ([LocalChatSurfaceTone]).
 *
 * ## The defect this exists to close
 * An unselected reminder chip was `Color.Transparent` + a 1dp `outlineVariant` hairline: with no
 * fill, that single line was the ONLY thing separating the control from its background. On the old
 * white dock the line measured 1.32 : 1 against it and the chip read as a button. On today's
 * `surfaceDim` `#DEDCD6` chrome the same line measures **1.04 : 1** — while the label stays at
 * 6.82 : 1. The result is the exact report: *"the In 1 hour buttons blend into the background"* —
 * legible text with no visible button under it.
 *
 * A transparent control therefore does not get a firmer hairline; it gets a FILL. The hairline is
 * then the second channel rather than the only one, and a chip whose fill already steps off the
 * plane no longer needs the loud line.
 *
 * ## Two outline roles, and the difference is what the element IS
 * | | Role | Light on `surfaceDim` | Dark on `#1A1C20` | Used by |
 * |---|---|---|---|---|
 * | [controlOutline] | `outline` | 3.33 : 1 | 5.47 : 1 | anything TAPPABLE — chips, the input pill, the dock bar |
 * | [contentOutline] | `outlineVariant` | 1.04 : 1 | 1.11 : 1 | the AI bubble — content, already separated by its own fill |
 *
 * The bubble keeps the soft line on purpose: it is not a target, it does not have to announce a hit
 * area, and a firm ring around every answer turns a conversation into a stack of framed cards. Its
 * separation comes from [raised] stepping off the plane; the hairline only closes the shape.
 *
 * ## There is deliberately NO accessor for the plane's own colour
 * The plane is painted by the HOST — `ChatScreen`'s scaffold paints `colorScheme.surface`, the three
 * docks paint `gistiDockColor()` → [AppSurface.bottomChrome] — and the host is also what PROVIDES
 * [LocalChatSurfaceTone]. An accessor reading that local could therefore only be called after the
 * provider, i.e. never at the `color =` argument of the very Surface that paints it: `QuickCaptureDock`
 * provides the local INSIDE its Surface, so a `plane()` there would silently resolve to the `Page`
 * default and paint the dock the page's colour. One existed, was called by nobody for exactly that
 * reason, and was removed — the plane values it returned are still spelled out in the tables below,
 * which is where they are actually needed.
 *
 * ⛔ Not a substitute for [AppSurface.docked] / [AppSurface.dockedSeam], nor for
 * [AppSurface.bottomChromeSeam]. Those are EDGES — the line where a bar meets the content above it —
 * while [controlOutline] is the ring around a thing you press. Three accessors resolve to `outline`
 * in light today and that is a coincidence of the palette, not one decision with three names: the
 * seams answer "where does this surface end", this one answers "what is tappable". Wiring any two of
 * them together means a chip's ring moves when a dock's edge is re-tuned.
 */
object AppChatColors {

    /**
     * Fill of anything lifted off the plane: a chip, the input pill, the dock bar, the AI bubble.
     *
     * Measured step off its own plane — positive in both themes, which is the whole requirement
     * (in dark the ladder only runs up, so "mirror the light value" is not available here):
     *
     * | | Plane | This | ΔL\* |
     * |---|---|---|---|
     * | Light, page | `#FBFAF8` | `#FFFFFF` | +1.7 (plus the hairline; the page is nearly white) |
     * | Light, chrome | `#DEDCD6` | `#FFFFFF` | +12.2 |
     * | Dark, page | `#121317` | `#1A1C20` | +4.3 |
     * | Dark, chrome | `#1A1C20` | `#26282E` | +5.9 |
     */
    @Composable
    @ReadOnlyComposable
    fun raised(): Color = when (LocalChatSurfaceTone.current) {
        ChatSurfaceTone.Page -> AppSurface.card()
        ChatSurfaceTone.BottomChrome -> AppSurface.bottomChromeRaised()
    }

    /**
     * Fill of a small element that carries **no outline at all** — the day divider, the inline cost
     * badge. NOT [raised], and the difference is the whole reason this accessor exists.
     *
     * [raised] is a SMALL step (ΔL\* +1.7 on the light page) that works because everything using it
     * also carries a 1dp border: the fill separates, the line closes the shape. A pill with no line
     * has one channel, so it needs a step it can carry alone — and on the near-white light page the
     * only direction with room is DOWN, the opposite of [raised].
     *
     * Measured on the recorded frames, badge fill ↔ the surface it sits on:
     *
     * | | Plane | [raised] would give | this gives |
     * |---|---|---|---|
     * | Light, page | `#FBFAF8` | `#FFFFFF`, ΔL\* **+1.7** (gone) | `surfaceContainer`, ΔL\* −3.9 |
     * | Dark, page | `#121317` | `#1A1C20`, ΔL\* +4.3 | `surfaceContainerHigh`, ΔL\* +10.2 |
     * | Light, chrome | `#DEDCD6` | `#FFFFFF`, ΔL\* +12.2 | same, +12.2 |
     * | Dark, chrome | `#1A1C20` | `#26282E`, ΔL\* +5.9 | same, +5.9 |
     *
     * The light page row is not theoretical: recorded with [raised], the cost badge's pill vanished
     * under the user bubble and left a floating sparkle and a digit.
     */
    @Composable
    @ReadOnlyComposable
    fun quietFill(): Color = when (LocalChatSurfaceTone.current) {
        ChatSurfaceTone.Page -> AppSurface.recessed()
        ChatSurfaceTone.BottomChrome -> AppSurface.bottomChromeRaised()
    }

    /**
     * Fill of a patch drawn ON TOP of [raised] — today: the background of inline `` `code` `` inside a
     * chat bubble.
     *
     * The only accessor here whose backdrop is NOT the plane. Everything else in this object answers
     * "how do I step off the surface the chat is drawn on"; this one answers "how do I step off the
     * BUBBLE", which is one level further up and therefore a different pair of colours.
     *
     * ## Why it is neither [raised] nor [quietFill]
     * [raised] IS the bubble — using it here is ΔL\* 0.0, an invisible patch. [quietFill] is a step
     * off the PLANE, and on the bottom chrome it resolves to the same `bottomChromeRaised()` the
     * bubble already uses, i.e. the same 0.0. That is not hypothetical: inline code was a fixed
     * `surfaceContainerHigh`, which is byte-for-byte `AppSurface.bottomChromeRaised()` in dark, so
     * the moment the dark bubble moved from `#0D0E11` to `#26282E` the code background vanished
     * completely and left a bare monospace run — correct on the page, gone on the chrome.
     *
     * Measured, patch ↔ the bubble it sits in:
     *
     * | | Bubble ([raised]) | This | ΔL\* |
     * |---|---|---|---|
     * | Light, page | `#FFFFFF` | `surfaceContainerHigh` `#ECEAE6` | −7.3 |
     * | Dark, page | `#1A1C20` | `surfaceContainerHigh` `#26282E` | +5.9 |
     * | Light, chrome | `#FFFFFF` | `surfaceContainerHighest` `#E6E4DF` | −9.4 |
     * | Dark, chrome | `#26282E` | `surfaceContainerHighest` `#2D2F35` | +3.3 |
     *
     * The dark-chrome row is the smallest step in this object, and it is the largest one AVAILABLE:
     * `surfaceContainerHighest` is the top of the container ramp, and the bubble is already one rung
     * below it. That is acceptable HERE and would not be for a control — a code patch is a tint
     * behind a monospace run that is legible on its own, so the fill only has to hint at an extent,
     * never to announce a hit area. If a future element on the chrome needs a real boundary above the
     * bubble it needs a border, not this.
     */
    @Composable
    @ReadOnlyComposable
    fun inkFill(): Color = when (LocalChatSurfaceTone.current) {
        ChatSurfaceTone.Page -> MaterialTheme.colorScheme.surfaceContainerHigh
        ChatSurfaceTone.BottomChrome -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    /**
     * Border of an INTERACTIVE element — chips, the input pill, the collapsed dock bar.
     *
     * Plane-independent: `outline` clears 3 : 1 against every surface in this system, so it does not
     * need to branch, and one role for every tappable edge is what makes the row read as one set of
     * controls instead of several.
     */
    @Composable
    @ReadOnlyComposable
    fun controlOutline(): Color = MaterialTheme.colorScheme.outline

    /**
     * Hairline of a NON-interactive element that already carries a tonal step — the AI bubble, the
     * typing indicator. See this object's KDoc for why this stays soft while [controlOutline] is firm.
     */
    @Composable
    @ReadOnlyComposable
    fun contentOutline(): Color = MaterialTheme.colorScheme.outlineVariant
}
