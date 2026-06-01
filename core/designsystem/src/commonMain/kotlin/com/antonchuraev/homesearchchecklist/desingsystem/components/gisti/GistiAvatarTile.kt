package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors

/**
 * Rounded square tile that generates a deterministic pastel background + content color
 * from [seed] (typically the checklist's Long id).
 *
 * The domain model has no emoji or custom color field, so this tile derives a stable
 * visual identity from the numeric id using [GistiColors.avatarTint] / [GistiColors.avatarContent].
 *
 * Content priority:
 * 1. If [label] is non-empty → uppercase first character as a monogram (fontWeight 700).
 * 2. If [label] is empty → fallback icon [Icons.Outlined.Checklist] in `avatarContent` tint.
 *
 * Visual spec (from gisti-screens.jsx ListCard emoji tile):
 *  - Default size: 48dp, corner radius: 14dp
 *  - Monogram font size: `size * 0.42` (≈20dp for 48dp tile)
 *
 * Token mapping:
 * - Background: [GistiColors.avatarTint(seed)]
 * - Monogram / icon: [GistiColors.avatarContent(seed)]
 *
 * @param seed Stable numeric key (checklist id). Same seed always produces the same hue.
 * @param label Checklist name used to derive the monogram. Pass empty string for icon fallback.
 * @param size Visual dimensions of the tile (width = height).
 * @param cornerRadius Shape corner radius.
 */
@Composable
fun GistiAvatarTile(
    seed: Long,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    cornerRadius: Dp = 14.dp,
) {
    val tint = GistiColors.avatarTint(seed)
    val content = GistiColors.avatarContent(seed)
    val monogram = remember(label) {
        label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(tint),
    ) {
        if (monogram != null) {
            Text(
                text = monogram,
                color = content,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (size.value * 0.42f).sp,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Checklist,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
