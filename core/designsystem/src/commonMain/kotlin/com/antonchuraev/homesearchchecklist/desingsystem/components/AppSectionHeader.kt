package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDensity
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTextStyles

/** Sizing for [AppSectionHeader]. */
object AppSectionHeaderDefaults {
    /** Circular icon tile. */
    val TileSize: Dp = 28.dp

    /** Icon inside the tile. */
    val IconSize: Dp = 18.dp

    /** Space below the header, above the group it heads. */
    val BottomPadding: Dp = 8.dp
}

/**
 * Section header with a tinted icon tile (redesign B): `[◉] Today 3`.
 *
 * Anatomy: 28dp circular tile ([iconContainer]) holding an 18dp icon ([iconContent]) · 10dp · label
 * in [AppTextStyles.sectionHeader] at `onSurface` · 6dp · optional [count] in `bodyMedium`
 * `onSurfaceVariant` · optional [trailing] slot pinned to the end (e.g. the Completed expand toggle).
 * Top padding [AppDensity.SectionHeaderTop], bottom [AppSectionHeaderDefaults.BottomPadding].
 *
 * The label is NEVER upper-cased — Devanagari has no case and an `uppercase()` mangles nothing but
 * reads as a different style next to EN/RU. It wraps (no `maxLines`) so a long RU / HI label at
 * fontScale 1.5 grows the header instead of clipping it.
 *
 * The icon is decorative (`contentDescription = null`); the row is a `heading()` so a screen reader
 * can jump section to section. [count] is read as part of the heading ("Today 3").
 *
 * Colours come from the caller as roles / palette tokens (e.g. `GistiColors.paletteTint(2)` /
 * `paletteContent(2)`), never as hex — keep icon-on-tile ≥ 3:1.
 */
@Composable
fun AppSectionHeader(
    label: String,
    icon: ImageVector,
    iconContainer: Color,
    iconContent: Color,
    modifier: Modifier = Modifier,
    count: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppSectionHeaderDefaults.TileSize)
            .padding(
                top = AppDensity.SectionHeaderTop,
                bottom = AppSectionHeaderDefaults.BottomPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AppSectionHeaderDefaults.TileSize)
                .background(iconContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconContent,
                modifier = Modifier.size(AppSectionHeaderDefaults.IconSize),
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = AppTextStyles.sectionHeader,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) trailing()
    }
}
