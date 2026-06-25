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
 * @param leadingIcon Optional 18dp icon drawn before the label (trash for Destructive, etc.).
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
            Text(
                text = if (isLoading) (loadingLabel ?: label) else label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = maxLines,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
