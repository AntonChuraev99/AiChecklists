package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/** From this font scale on, the decorative tile goes so the text keeps the width it needs. */
private const val TILE_HIDE_FONT_SCALE = 1.5f

/**
 * Shared shape of the quiet inline suggestions on checklist detail ([RecurringNudgeCard],
 * [NotificationsOffBanner]): a hairline [AppCard] with a small tinted tile, a title / body column,
 * a 48dp dismiss "x" and a full-width outlined action.
 *
 * Not clickable as a whole — only the action and the "x" are. The tile icon is decorative.
 * Screen-reader order is title → body → action → "x" (the "x" is drawn first in the row, so it is
 * pushed to the end with `traversalIndex`).
 *
 * @param rowAlignment vertical alignment of tile / text / "x". The recurring nudge centres them;
 *   the notifications banner pins them to the top so a wrapped body never drags the "x" down.
 * @param actionIcon optional leading icon inside the action button.
 */
@Composable
internal fun InlineNudgeCard(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String,
    modifier: Modifier = Modifier,
    actionIcon: ImageVector? = null,
    rowAlignment: Alignment.Vertical = Alignment.CenterVertically,
) {
    val showTile = LocalDensity.current.fontScale < TILE_HIDE_FONT_SCALE
    AppCard(
        modifier = modifier,
        contentPadding = PaddingValues(AppDimens.SpacingMd),
    ) {
        Column(
            modifier = Modifier.semantics { isTraversalGroup = true },
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = rowAlignment,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            ) {
                if (showTile) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // IconButton is 48dp (minimum interactive size) — the "x" keeps a full touch target.
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics { traversalIndex = 1f },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = dismissLabel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AppButtonSecondary(
                text = actionLabel,
                onClick = onAction,
                icon = actionIcon,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
