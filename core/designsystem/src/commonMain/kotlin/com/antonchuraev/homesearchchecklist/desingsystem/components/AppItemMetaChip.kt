package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * A compact read-only pill chip used in [ItemMetaRow] to show item metadata:
 * priority, reminder time, or attachment count.
 *
 * Design decisions:
 * - No `onClick` / `clickable` — purely informational (toggling happens inside ItemDetailsSheet).
 * - No ripple by default (Surface without onClick).
 * - Height ~22dp: icon 14dp + vertical padding 4dp top + 4dp bottom.
 * - Shape: `RoundedCornerShape(6.dp)` — more rectangular than a full pill to feel "data tag"
 *   rather than "action chip". Slightly smaller than MD3 shape.small (8dp) — intentionally
 *   compact for the indicator row below checklist item text.
 * - Icon size 14dp, typography labelSmall — fits within ≤24dp target height.
 * - Colors are passed as semantic roles from the caller — no hardcoded Color values here.
 *
 * @param icon           Leading icon (decorative — label conveys meaning to screen readers).
 * @param label          Short text label. Keep to ≤12 chars for layout stability.
 * @param containerColor Tonal background. Use *Container roles from [MaterialTheme.colorScheme].
 * @param contentColor   Icon + text color. Use the paired *onContainer role.
 * @param modifier       Optional external modifier.
 */
@Composable
fun AppItemMetaChip(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppDimens.SpacingSm,
                vertical = AppDimens.SpacingXs,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // decorative — label is the semantic content
                modifier = Modifier.size(14.dp),
                tint = contentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── Previews ───────────────────────────────────────────────────────────────

// Note: @Preview is Android-only API; previews live in the androidMain source set.
// See AppItemMetaChipPreview.kt in feature/home/src/androidMain/ if added later.
