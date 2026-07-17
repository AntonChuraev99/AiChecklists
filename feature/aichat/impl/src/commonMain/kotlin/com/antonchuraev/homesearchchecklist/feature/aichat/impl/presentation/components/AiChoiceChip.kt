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
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole

/**
 * A single Claude-style choice "pill" chip — a custom Surface (NOT M3 AssistChip/SuggestionChip).
 *
 * Fully rounded ([RoundedCornerShape] 50%). The fill / text / border color is derived from the
 * [ChoiceRole]:
 * - [ChoiceRole.Primary]     primary / onPrimary (the recommended action, max one per block)
 * - [ChoiceRole.Default]     primaryContainer / onPrimaryContainer
 * - [ChoiceRole.Destructive] error / onError + a leading trash icon supplied by the caller
 * - [ChoiceRole.Escape]      transparent + 1dp outlineVariant border + onSurfaceVariant text
 * - [ChoiceRole.Add]         surfaceContainer + dashed-look outline + leading "+" supplied by caller
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
    val container: Color = when (role) {
        ChoiceRole.Primary -> cs.primary
        ChoiceRole.Default -> cs.primaryContainer
        ChoiceRole.Destructive -> cs.error
        ChoiceRole.Escape -> Color.Transparent
        ChoiceRole.Add -> cs.surfaceContainer
    }
    val content: Color = when (role) {
        ChoiceRole.Primary -> cs.onPrimary
        ChoiceRole.Default -> cs.onPrimaryContainer
        ChoiceRole.Destructive -> cs.onError
        ChoiceRole.Escape -> cs.onSurfaceVariant
        ChoiceRole.Add -> cs.onSurface
    }
    val border: BorderStroke? = when (role) {
        ChoiceRole.Escape -> BorderStroke(1.dp, cs.outlineVariant)
        ChoiceRole.Add -> BorderStroke(1.dp, cs.outline)
        else -> null
    }

    // Dim the whole chip (fill, text, icon) uniformly when disabled. We can't disable Surface's
    // onClick AND keep custom colors with one flag, so we drop alpha on the colors and gate onClick.
    val dimAlpha = if (enabled) 1f else 0.38f
    val effectiveContainer = if (container == Color.Transparent) container else container.copy(alpha = dimAlpha)
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
