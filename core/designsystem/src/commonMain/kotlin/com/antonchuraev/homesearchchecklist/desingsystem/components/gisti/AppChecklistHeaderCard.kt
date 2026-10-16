package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCardDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppProgressRing
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface

/** Sizing and the narrow rule for [AppChecklistHeaderCard]. */
object AppChecklistHeaderCardDefaults {
    val TileSize: Dp = 48.dp
    val TileCorner: Dp = 16.dp
    val Padding: Dp = 16.dp
    val Gap: Dp = 14.dp

    /**
     * Below this CARD width × fontScale the avatar tile is dropped (it is decorative) and the title
     * takes its 62dp. The spec states the rule as a 340dp WINDOW; the card sits inside 16dp page
     * gutters, so the same breakpoint measured on the card is 340 − 2 × 16 = 308dp. Measured on the
     * mock: at 320dp a RU title + ring squeezes to 3 lines and wraps the subtitle.
     */
    val NarrowCardWidth: Dp = 308.dp

    /** Pure narrow rule, testable without Compose. */
    fun showsTile(cardWidth: Dp, fontScale: Float): Boolean = cardWidth >= NarrowCardWidth * fontScale
}

/**
 * The checklist detail header (redesign B): avatar tile · title + progress subtitle · progress ring.
 * Primitives only — no domain types.
 *
 * Anatomy: [Surface] in [AppShapeTokens.HeaderCard] (28dp), [AppSurface.card] fill, 1dp
 * `outlineVariant` hairline ([AppCardDefaults.border]), no elevation; padding 16, row gap 14, top
 * aligned:
 *  1. [GistiAvatarTile] 48dp / 16dp corners — monogram of [title], or a folder icon when [isFolder];
 *     hidden below [AppChecklistHeaderCardDefaults.NarrowCardWidth] × fontScale — and in that
 *     narrow mode the ring moves under the title, beside the subtitle, so the title gets the full
 *     inner width (a long single RU word otherwise breaks mid-word at 320dp × 1.5);
 *  2. title `headlineSmall` (22/28 Bold), max 3 lines + ellipsis, `heading()`; [subtitle]
 *     `bodyMedium` Medium `onSurfaceVariant`, max 2 lines;
 *  3. [AppProgressRing] 52dp (its own label rule and a11y description).
 *
 * Empty level (`total == 0`): no ring and no subtitle — the title stays.
 * The card is not clickable.
 */
@Composable
fun AppChecklistHeaderCard(
    seed: Long,
    title: String,
    subtitle: String?,
    checked: Int,
    total: Int,
    isFolder: Boolean,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapeTokens.HeaderCard,
        color = AppSurface.card(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = AppCardDefaults.border(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        BoxWithConstraints {
            val showTile = AppChecklistHeaderCardDefaults.showsTile(maxWidth, fontScale)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppChecklistHeaderCardDefaults.Padding),
                horizontalArrangement = Arrangement.spacedBy(AppChecklistHeaderCardDefaults.Gap),
                verticalAlignment = Alignment.Top,
            ) {
                if (showTile) {
                    GistiAvatarTile(
                        seed = seed,
                        label = title,
                        size = AppChecklistHeaderCardDefaults.TileSize,
                        cornerRadius = AppChecklistHeaderCardDefaults.TileCorner,
                        icon = if (isFolder) Icons.Outlined.Folder else null,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    val showSubtitle = total > 0 && !subtitle.isNullOrEmpty()
                    if (!showTile && total > 0) {
                        // Narrow: the ring drops UNDER the title, beside the subtitle. Measured on
                        // the 320dp × 1.5 RU frame: beside the title it left ~190dp, and the single
                        // word "Еженедельные" (≈228dp at 33sp Bold) was broken mid-word. Under the
                        // title the heading gets the card's full inner width.
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AppProgressRing(checked = checked, total = total)
                            if (showSubtitle) HeaderSubtitle(subtitle.orEmpty(), Modifier.weight(1f))
                        }
                    } else if (showSubtitle) {
                        HeaderSubtitle(subtitle.orEmpty(), Modifier.padding(top = 4.dp))
                    }
                }
                if (showTile && total > 0) {
                    AppProgressRing(checked = checked, total = total)
                }
            }
        }
    }
}

@Composable
private fun HeaderSubtitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
