package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.SparkleTile
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDensity
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface

/** How an [AppAiInviteRow] sits on its surroundings. */
enum class AiInviteStyle {
    /** Recessed fill, 16dp corners — the Inbox plan nudge on the page. */
    Recessed,

    /** Transparent pill with a dashed `outline` border — "Ask Gisti about this list" on the detail. */
    Outlined,

    /** No container of its own — it lives inside a `Modifier.groupSegment`. */
    GroupRow,
}

/** Sizing for [AppAiInviteRow]. */
object AppAiInviteRowDefaults {
    /** Minimum, never fixed — a RU title wraps to 2 lines at 320dp. */
    val MinHeight: Dp = 48.dp
    val TileSize: Dp = 32.dp
    val ChevronSize: Dp = 20.dp
    val DashOn: Dp = 6.dp
    val DashOff: Dp = 4.dp
}

/**
 * An invitation to ask Gisti something about what is on screen (redesign B): sparkle tile · title
 * (+ optional subtitle) · chevron. ONE anatomy for every AI invite row — [AppPlanNudge] delegates
 * here rather than drawing a twin.
 *
 * Anatomy: [Surface] with `onClick` (one button node; the chevron and tile are decorative), min height
 * 48, padding start 8 / end 16 / vertical 6, gap 10. [SparkleTile] 32dp circle · title `titleSmall`
 * `onSurface` max 2 lines · subtitle `bodySmall` `onSurfaceVariant` · 20dp chevron.
 *
 * [AiInviteStyle.Outlined] draws its dashed 1dp `outline` border by hand (`PathEffect.dashPathEffect`
 * 6/4dp, 4.15:1 on the page) — M3 `BorderStroke` has no dash.
 *
 * No elevation in any style.
 */
@Composable
fun AppAiInviteRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    style: AiInviteStyle = AiInviteStyle.Recessed,
) {
    val shape = when (style) {
        AiInviteStyle.Recessed -> MaterialTheme.shapes.medium
        AiInviteStyle.Outlined -> AppShapeTokens.Pill
        AiInviteStyle.GroupRow -> RectangleShape
    }
    val container = when (style) {
        AiInviteStyle.Recessed -> AppSurface.recessed()
        AiInviteStyle.Outlined, AiInviteStyle.GroupRow -> Color.Transparent
    }
    val dashColor = MaterialTheme.colorScheme.outline
    val dashed = if (style == AiInviteStyle.Outlined) {
        Modifier.drawBehind {
            val stroke = 1.dp.toPx()
            val half = stroke / 2f
            val outline = shape.createOutline(
                size = size.copy(width = size.width - stroke, height = size.height - stroke),
                layoutDirection = layoutDirection,
                density = this,
            )
            translate(left = half, top = half) {
                drawOutline(
                    outline = outline,
                    color = dashColor,
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(AppAiInviteRowDefaults.DashOn.toPx(), AppAiInviteRowDefaults.DashOff.toPx()),
                        ),
                    ),
                )
            }
        }
    } else {
        Modifier
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .heightIn(min = AppAiInviteRowDefaults.MinHeight)
            .then(dashed),
    ) {
        // Inside a group the sparkle tile centres on the column the round checkboxes of the rows
        // above it sit on (16 + 24/2 = 28dp), so the segment reads as one more row of the group.
        val start = if (style == AiInviteStyle.GroupRow) {
            AppDensity.GroupRowPaddingHorizontal -
                (AppAiInviteRowDefaults.TileSize - AppDensity.RoundCheckboxBox) / 2
        } else {
            8.dp
        }
        Row(
            modifier = Modifier.padding(start = start, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SparkleTile(
                size = AppAiInviteRowDefaults.TileSize,
                cornerRadius = AppAiInviteRowDefaults.TileSize / 2,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null, // decorative — the row itself is the button
                modifier = Modifier.size(AppAiInviteRowDefaults.ChevronSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
