package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole

/**
 * A single Claude-style choice "pill" chip — a custom Surface (NOT M3 AssistChip/SuggestionChip).
 *
 * Fully rounded ([RoundedCornerShape] 50%). The fill / text / border color is derived from the
 * [ChoiceRole]:
 * - [ChoiceRole.Primary]     primary / onPrimary (the recommended action, max one per block)
 * - [ChoiceRole.Default]     primaryContainer / onPrimaryContainer
 * - [ChoiceRole.Destructive] error / onError + a leading trash icon supplied by the caller
 * - [ChoiceRole.Escape]      `AppChatColors.raised()` + 1dp `controlOutline()` + onSurfaceVariant text
 * - [ChoiceRole.Add]         the SAME fill and outline + onSurface text + a leading "+" from the caller
 *
 * The first three are absolute (they carry their own hue); the two NEUTRAL roles resolve against the
 * plane the chat is drawn on, because they are the roles with no hue to fall back on. They share one
 * fill deliberately — see the `container` branch for what does tell them apart, and for why splitting
 * the fills today would be designing for a role no production path emits.
 *
 * Loading: when [isLoading] is true the chip shows a 16dp spinner in place of (or beside) the
 * label and uses [loadingLabel]. When [enabled] is false the chip dims to 38% alpha and ignores
 * taps — the whole choice block goes non-interactive while one chip executes.
 *
 * Width: by default the chip wraps its content. The vertical-layout container passes
 * `Modifier.fillMaxWidth()` so the chip stretches and the label wraps to 2 lines.
 *
 * Meta ([meta], D2): a dimmed disambiguating suffix after a [META_SEPARATOR] ("Shopping • 12").
 * In the content-sized layout it trails the label; in the full-width layout ([fillWidth]) it is
 * pinned to the right edge by giving the LABEL the whole remaining width (`weight(1f)`, fill=true)
 * — the meta is then simply the last, unweighted child, so it lands flush right no matter how
 * short the label is. Do NOT reintroduce a weighted `Spacer` beside a weighted label: two
 * weight-1f siblings split the free space 50/50, which parks the meta at "label + half the chip"
 * and makes its x drift with the label's length (the bug fixed 2026-07-16).
 *
 * The meta is never dropped — the count IS what tells two identically named lists apart — so on a
 * meta-carrying chip the LABEL yields instead: it ellipsises at [maxLines]. That is a deliberate,
 * narrow exception to this component's never-ellipsis rule: a clipped name with a visible count
 * still answers "which one?", a full name without the count does not. Do NOT "fix" this by
 * clipping or dropping the meta.
 *
 * @param leadingIcon Optional 18dp icon drawn before the label (trash for Destructive, etc.).
 * @param meta        Optional dimmed suffix (a bare value — "12", not "12 items"). The full,
 *                    spoken form belongs in the caller's contentDescription, not here.
 * @param fillWidth   True when the parent stretched this chip (Column layout): the meta is then
 *                    right-aligned rather than trailing the label.
 */
@Composable
internal fun AiChoiceChip(
    label: String,
    role: ChoiceRole,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingLabel: String? = null,
    leadingIcon: ImageVector? = null,
    meta: String? = null,
    fillWidth: Boolean = false,
    maxLines: Int = 2,
) {
    val cs = MaterialTheme.colorScheme
    // Primary / Default / Destructive are ACCENTS: they carry their own hue and are legible on
    // either plane, so they stay absolute. Escape and Add are the two neutral roles, and they are
    // exactly the class of control the reminder chips belong to — Escape was `Color.Transparent`
    // (the reported "buttons blend into the background" defect, one hairline and nothing else) and
    // Add's `surfaceContainer` is only ΔL* ~2 off the dark chrome. Both go plane-relative.
    //
    // ONE arm for the two of them, not two arms that happen to agree. They were split, and the split
    // read as an intention to tint them apart that was never carried out. Audited before merging:
    //  - what actually separates them is the LABEL (`onSurfaceVariant` vs `onSurface` — ΔL* 18.8 in
    //    light, 10.4 in dark), the leading "+" the caller passes for Add and never for Escape, and
    //    the layout (AiChoiceResponse puts the escape in its own row BELOW the options);
    //  - a tonal split of the fills would be designing for a role nothing renders: no production code
    //    path emits `ChoiceRole.Add` today — the only non-test reference to it is the icon mapping in
    //    AiChoiceResponse. Give it a fill of its own when something starts producing it, with a spec.
    val container: Color = when (role) {
        ChoiceRole.Primary -> cs.primary
        ChoiceRole.Default -> cs.primaryContainer
        ChoiceRole.Destructive -> cs.error
        ChoiceRole.Escape, ChoiceRole.Add -> AppChatColors.raised()
    }
    val content: Color = when (role) {
        ChoiceRole.Primary -> cs.onPrimary
        ChoiceRole.Default -> cs.onPrimaryContainer
        ChoiceRole.Destructive -> cs.onError
        ChoiceRole.Escape -> cs.onSurfaceVariant
        ChoiceRole.Add -> cs.onSurface
    }
    // Both neutral roles are tap targets, so both take the firm control outline. Escape used to take
    // the soft `outlineVariant` — the weaker of the two channels on the chip that had no other.
    val border: BorderStroke? = when (role) {
        ChoiceRole.Escape, ChoiceRole.Add -> BorderStroke(1.dp, AppChatColors.controlOutline())
        else -> null
    }

    // Dim the whole chip (fill, text, icon) uniformly when disabled. We can't disable Surface's
    // onClick AND keep custom colors with one flag, so we drop alpha on the colors and gate onClick.
    // The old `if (container == Color.Transparent)` guard is gone with the transparent role it
    // guarded: every role now has a fill, so every role dims the same way.
    val dimAlpha = if (enabled) 1f else 0.38f
    val effectiveContainer = container.copy(alpha = dimAlpha)
    val effectiveContent = content.copy(alpha = dimAlpha)

    Surface(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(percent = 50),
        color = effectiveContainer,
        contentColor = effectiveContent,
        border = border,
        modifier = modifier.minimumInteractiveComponentSize(),
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 40.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                    modifier = Modifier.size(16.dp),
                )
            } else if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            // While loading the meta is suppressed: the chip is mid-execution, there is nothing
            // left to disambiguate and "Adding… • 12" reads like noise.
            val showMeta = meta != null && !isLoading
            Text(
                text = if (isLoading) (loadingLabel ?: label) else label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = maxLines,
                // See the KDoc: the label — not the meta — is what gives way when a chip carries
                // both and the name is long.
                overflow = if (showMeta) TextOverflow.Ellipsis else TextOverflow.Clip,
                // fill = true (the default): the label claims ALL the space left over after the
                // meta, so the meta is pushed flush against the right padding and every chip in
                // the block lines its count up on the same x. See the KDoc — a weighted Spacer
                // here would halve that space instead and let the meta float.
                modifier = if (showMeta && fillWidth) Modifier.weight(1f) else Modifier,
            )
            if (showMeta) {
                Text(
                    text = "$META_SEPARATOR $meta",
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalContentColor.current.copy(alpha = META_ALPHA),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Separator between a chip label and its meta.
 *
 * U+2022, NOT "·" (U+00B7) and NOT "→": Skiko on wasmJs has no CSS-style font fallback, and D1
 * shipped an arrow that rendered as tofu on the web canvas. The bullet is proven on that same
 * canvas (the object rows draw it today) — do not swap it for an unverified glyph.
 */
private const val META_SEPARATOR = "•"

/**
 * Meta dimming. 0.6f on onPrimaryContainer over primaryContainer measures ≈ 7:1 — comfortably
 * past WCAG AA for this text size, so the meta reads as secondary without becoming decoration.
 */
private const val META_ALPHA = 0.6f
