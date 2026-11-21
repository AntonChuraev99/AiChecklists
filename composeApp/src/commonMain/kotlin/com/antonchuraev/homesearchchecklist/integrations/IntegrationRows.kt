package com.antonchuraev.homesearchchecklist.integrations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * Shared rows of the integration info screens (Gisti MCP, Telegram): the icon-tile + label
 * "value prop" row and the small icon + caption note row.
 */

/** Edge of the rounded tile in [ValuePropRow] (and the Telegram account tile). */
internal val IntegrationRowTileSize: Dp = 40.dp

private val RowTileIconSize = 22.dp
private val NoteIconSize = 16.dp

/**
 * Icon-tile + label row. The tile is monochrome (`primaryContainer` by default), the icon decorative.
 *
 * @param textStyle MCP keeps `titleMedium`; Telegram passes `bodyLarge` (spec §8: SemiBold wrapped
 *   to 3–4 heavy lines in RU at fontScale 1.5).
 * @param alignToTileTop `false` (MCP): label centred against the tile, at most two lines.
 *   `true` (Telegram): the row is top-aligned and the label is padded by `SpacingSm`, so the FIRST
 *   line sits on the tile's visual centre and further lines flow below it instead of re-centring
 *   the tile against the whole block. The label wraps freely.
 */
@Composable
internal fun ValuePropRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    alignToTileTop: Boolean = false,
    tileColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    // Top-aligned: centre the FIRST text line on the tile. Whichever is shorter (tile or line box)
    // is pushed down by half the difference, so it holds at every fontScale.
    val (tilePad, textPad) = if (alignToTileTop) {
        firstLineCentring(IntegrationRowTileSize, lineHeightDp(textStyle))
    } else {
        0.dp to 0.dp
    }
    Row(
        modifier = modifier,
        verticalAlignment = if (alignToTileTop) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        Box(Modifier.padding(top = tilePad)) {
            IntegrationRowTile(icon = icon, tileColor = tileColor, iconTint = iconTint)
        }
        Text(
            text = label,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (alignToTileTop) Int.MAX_VALUE else 2,
            modifier = Modifier.padding(top = textPad),
        )
    }
}

/** Line height of [style] in dp, or null when it is unspecified or in `em`. */
@Composable
internal fun lineHeightDp(style: TextStyle): Dp? {
    val lineHeight = style.lineHeight
    if (lineHeight.type != TextUnitType.Sp) return null
    return with(LocalDensity.current) { lineHeight.toDp() }
}

/**
 * Top paddings (leading element, text) that put the centre of the first text line on the centre of
 * a [leading]-tall element. Unknown line height → no padding.
 */
internal fun firstLineCentring(leading: Dp, line: Dp?): Pair<Dp, Dp> {
    if (line == null) return 0.dp to 0.dp
    val diff = (leading - line) / 2
    return if (diff >= 0.dp) 0.dp to diff else (-diff) to 0.dp
}

/** 40dp rounded tile with a 22dp decorative glyph — the leading element of [ValuePropRow]. */
@Composable
internal fun IntegrationRowTile(
    icon: ImageVector,
    tileColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = Modifier
            .size(IntegrationRowTileSize)
            .clip(MaterialTheme.shapes.small)
            .background(tileColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(RowTileIconSize),
        )
    }
}

/**
 * 16dp icon + `bodySmall` caption, both `onSurfaceVariant` (MCP "requires Google" note and sync
 * caption, Telegram connect hint).
 *
 * @param verticalAlignment MCP centres the single-line caption; Telegram passes [Alignment.Top] so a
 *   wrapped hint keeps its icon on the first line.
 */
@Composable
internal fun IntegrationNoteRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
) {
    val style = MaterialTheme.typography.bodySmall
    val (iconPad, textPad) = if (verticalAlignment == Alignment.Top) {
        firstLineCentring(NoteIconSize, lineHeightDp(style))
    } else {
        0.dp to 0.dp
    }
    Row(
        modifier = modifier,
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = iconPad).size(NoteIconSize),
        )
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = textPad),
        )
    }
}
