package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTextStyles
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion

/**
 * Call timer pill: «● 1:48 · осталось 3:12».
 *
 * Width ladder, decided in the measure phase: the full text, then only the remaining part when the
 * full one does not fit next to the speed button (59:59 on 320dp×1.5 RU). Font scale capped at 1.3.
 * Semantics are static ([a11y], spoken form) and never live: a ticking text in a live region would
 * be announced every second.
 *
 * @param elapsed `m:ss` elapsed; [remaining] `"осталось 3:12"` or `null` before the session starts.
 * @param live the «on air» dot; hidden while muted / paused / reconnecting / connecting.
 * @param warning ≤ 60 s left: `primaryContainer` fill, the dot becomes an hourglass.
 */
@Composable
fun CallTimerPill(
    elapsed: String,
    remaining: String?,
    live: Boolean,
    warning: Boolean,
    a11y: String,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val spec = if (reduced) tween<Color>(AppMotion.ReducedMotionMillis) else AppMotion.effectsDefaultAs()
    val fill by animateColorAsState(
        if (warning) MaterialTheme.colorScheme.primaryContainer else AppSurface.recessed(),
        spec,
        label = "pillFill",
    )
    val ink by animateColorAsState(
        if (warning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        spec,
        label = "pillInk",
    )
    val dot = AppCallColors.liveDot()
    FontScaleCap {
        Layout(
            modifier = modifier.clearAndSetSemantics { contentDescription = a11y },
            content = {
                PillRow(fill, ink, dot, live, warning, listOfNotNull(elapsed, remaining).joinToString(SEPARATOR))
                PillRow(fill, ink, dot, live, warning, remaining ?: elapsed)
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0)
            val unbounded = Constraints(maxHeight = constraints.maxHeight)
            val full = measurables[0].measure(unbounded)
            val chosen = if (full.width <= constraints.maxWidth) full else measurables[1].measure(loose)
            layout(chosen.width, chosen.height) { chosen.place(0, 0) }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun PillRow(fill: Color, ink: Color, dot: Color, live: Boolean, warning: Boolean, text: String) {
    Row(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .background(fill, AppShapeTokens.Pill)
            .padding(horizontal = AppDimens.SpacingMd, vertical = AppDimens.SpacingXs),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            warning -> Icon(
                Icons.Outlined.HourglassBottom,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(14.dp),
            )
            live -> Canvas(Modifier.size(VoiceCallDefaults.LiveDot)) { drawCircle(dot) }
        }
        Text(
            text = text,
            style = AppTextStyles.monoTime,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val SEPARATOR = " · "
