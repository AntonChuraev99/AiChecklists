package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface

/** Sizing for [AppSoftIconButton]. */
object AppSoftIconButtonDefaults {
    /** Touch target. */
    val TargetSize: Dp = 48.dp

    /** The visible "soft" disc. */
    val DiscSize: Dp = 40.dp

    val IconSize: Dp = 22.dp
}

/**
 * A top-bar icon button in the redesign-B "soft & tactile" look: a 40dp cream disc
 * ([AppSurface.card] + 1dp `outlineVariant`) inside a 48dp target, 22dp icon.
 *
 * Wraps M3 [IconButton], so the role, the 40dp state-layer ripple and keyboard focus are M3's. The
 * disc is drawn inside the button, under the ripple.
 *
 * A "scheduled reminder" state is expressed by the caller passing the FILLED bell vector and
 * `tint = colorScheme.primary` — the vector carries filled-vs-outlined, so there is no separate flag.
 *
 * @param contentDescription Required — an icon-only control has no other accessible name.
 */
@Composable
fun AppSoftIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(AppSoftIconButtonDefaults.TargetSize),
    ) {
        Box(
            modifier = Modifier
                .size(AppSoftIconButtonDefaults.DiscSize)
                .background(AppSurface.card(), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(AppSoftIconButtonDefaults.IconSize),
            )
        }
    }
}
