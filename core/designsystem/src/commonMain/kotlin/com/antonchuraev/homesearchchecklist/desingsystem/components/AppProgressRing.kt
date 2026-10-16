package com.antonchuraev.homesearchchecklist.desingsystem.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.detail_progress_content_description
import aichecklists.core.designsystem.generated.resources.folder_progress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import org.jetbrains.compose.resources.stringResource

/** Sizing and the label rule for [AppProgressRing]. */
object AppProgressRingDefaults {
    val Size: Dp = 52.dp
    val StrokeWidth: Dp = 5.dp
    val CompleteIconSize: Dp = 22.dp

    /** At or above this font scale the "4/6" label no longer fits the ring; the numbers move out. */
    const val LabelMaxFontScale: Float = 1.3f

    /** Largest total the label is drawn for — "100/100" does not fit 52dp at any scale. */
    const val LabelMaxTotal: Int = 99

    /** Pure label rule, testable without Compose. */
    fun showsLabel(total: Int, fontScale: Float): Boolean =
        total in 1..LabelMaxTotal && fontScale < LabelMaxFontScale
}

/**
 * Determinate progress ring with an optional centred "checked/total" label (redesign B — the detail
 * header card).
 *
 * Wraps M3 [CircularProgressIndicator] (lambda progress, so the fraction is read in draw): 5dp round
 * stroke, track [AppSurface.recessed], progress `primary`, or `tertiary` once complete
 * (`checked == total > 0`, the app's existing completion colour).
 *
 * Label rule ([AppProgressRingDefaults.showsLabel]): drawn only when `total in 1..99` and the font
 * scale is below 1.3 — otherwise the ring carries no label and the caller's subtitle carries the
 * numbers. When complete the label becomes a 22dp check in `tertiary`.
 *
 * Semantics: one node, `detail_progress_content_description` ("Progress: 4 of 6 done"); the visual
 * label is cleared so TalkBack does not read "4/6" as a fraction.
 */
@Composable
fun AppProgressRing(
    checked: Int,
    total: Int,
    modifier: Modifier = Modifier,
    size: Dp = AppProgressRingDefaults.Size,
    strokeWidth: Dp = AppProgressRingDefaults.StrokeWidth,
) {
    val safeTotal = total.coerceAtLeast(0)
    val safeChecked = checked.coerceIn(0, safeTotal)
    val complete = safeTotal > 0 && safeChecked == safeTotal
    val fraction = if (safeTotal == 0) 0f else safeChecked.toFloat() / safeTotal
    val color = if (complete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val description = stringResource(Res.string.detail_progress_content_description, safeChecked, safeTotal)
    val fontScale = LocalDensity.current.fontScale

    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { fraction },
            modifier = Modifier.size(size),
            color = color,
            strokeWidth = strokeWidth,
            trackColor = AppSurface.recessed(),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
        when {
            complete -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(AppProgressRingDefaults.CompleteIconSize),
            )
            AppProgressRingDefaults.showsLabel(safeTotal, fontScale) -> Text(
                text = stringResource(Res.string.folder_progress, safeChecked, safeTotal),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
