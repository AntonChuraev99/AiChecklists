package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDensity
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors

/**
 * Round, VISUAL-ONLY checkbox for task rows inside a grouped section (redesign B).
 *
 * It has no click handler and no semantics of its own: the row's 30% hit zone toggles the task and
 * carries `Role.Checkbox` / `toggleableState` (rule `ui-card-patterns`, the 30/70 split). Giving this
 * glyph a click would add a second, smaller target inside the zone and a duplicate a11y node.
 *
 * It replaces the M3 `Checkbox` ONLY inside the grouped task rows; every other checkbox in the app
 * stays M3.
 *
 * | State | Ring | Fill | Glyph |
 * |---|---|---|---|
 * | unchecked | 2dp `outline` (4.15:1 page / 4.67:1 card) | none | — |
 * | unchecked + [important] | 2dp [GistiColors.priorityIndicator] | same @ 12% | — |
 * | checked | none | `primary` | 16dp check, `onPrimary` |
 *
 * Laid out in a 24dp box ([AppDensity.RoundCheckboxBox]); the ring is 22dp
 * ([AppDensity.RoundCheckboxSize]). Colour changes cross-fade on the effects spec.
 */
@Composable
fun AppRoundCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
    important: Boolean = false,
) {
    val ringTarget = when {
        checked -> MaterialTheme.colorScheme.primary
        important -> GistiColors.priorityIndicator
        else -> MaterialTheme.colorScheme.outline
    }
    val fillTarget = when {
        checked -> MaterialTheme.colorScheme.primary
        important -> GistiColors.priorityIndicator.copy(alpha = IMPORTANT_FILL_ALPHA)
        else -> Color.Transparent
    }
    val ring by animateColorAsState(ringTarget, AppMotion.effectsDefaultAs())
    val fill by animateColorAsState(fillTarget, AppMotion.effectsDefaultAs())

    Box(
        modifier = modifier.size(AppDensity.RoundCheckboxBox),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(AppDensity.RoundCheckboxSize)
                .background(fill, CircleShape)
                .border(width = 2.dp, color = ring, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null, // visual only — the row's zone carries the state
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private const val IMPORTANT_FILL_ALPHA = 0.12f
