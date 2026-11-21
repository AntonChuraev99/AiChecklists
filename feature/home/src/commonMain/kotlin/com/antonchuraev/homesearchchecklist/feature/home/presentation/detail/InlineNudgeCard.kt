package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/** From this font scale on, the decorative tile goes so the text keeps the width it needs. */
internal const val TILE_HIDE_FONT_SCALE = 1.5f

/** How [InlineNudgeCard] renders its action. */
internal enum class NudgeActionStyle {
    /** A full-width outlined button under the row — for a banner that reports something broken. */
    Button,

    /**
     * The label as primary-coloured text under the body, inside the text column. Not clickable on
     * its own: pair it with `onClick` so the whole card is the target — for a suggestion.
     */
    Link,
}

/**
 * Shared shape of the quiet inline suggestions ([RecurringNudgeCard], [NotificationsOffBanner], the
 * Inbox Telegram promo): a hairline [AppCard] with a small tinted tile, a title / body column,
 * a 48dp dismiss "x" and an action.
 *
 * By default not clickable as a whole — only the action and the "x" are. The tile icon is decorative.
 * Screen-reader order is title → body → action → "x" (the "x" is drawn first in the row, so it is
 * pushed to the end with `traversalIndex`).
 *
 * @param rowAlignment vertical alignment of tile / text / "x". The recurring nudge centres them;
 *   the notifications banner pins them to the top so a wrapped body never drags the "x" down.
 * @param actionIcon optional leading icon inside the action button ([NudgeActionStyle.Button] only).
 * @param onClick non-null → the whole card is one button (title, body and link merged); the "x"
 *   keeps its own tap. Default null keeps the existing banners unchanged.
 * @param actionStyle [NudgeActionStyle.Button] (default) or [NudgeActionStyle.Link].
 * @param hideBodyAtLargeFont at fontScale ≥ [TILE_HIDE_FONT_SCALE] the body is not composed (title
 *   and action stay), so a suggestion never fills the first screen at large font.
 * @param dismissTestTag optional `testTag` of the "x".
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
    onClick: (() -> Unit)? = null,
    actionStyle: NudgeActionStyle = NudgeActionStyle.Button,
    hideBodyAtLargeFont: Boolean = false,
    dismissTestTag: String? = null,
) {
    val largeFont = LocalDensity.current.fontScale >= TILE_HIDE_FONT_SCALE
    val showTile = !largeFont
    val showBody = !(hideBodyAtLargeFont && largeFont)
    AppCard(
        // AppCard(onClick) carries no role of its own — TalkBack must hear "button" (see AppCard).
        modifier = if (onClick != null) modifier.semantics { role = Role.Button } else modifier,
        onClick = onClick,
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
                    if (showBody) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (actionStyle == NudgeActionStyle.Link) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = AppDimens.SpacingXs),
                        )
                    }
                }
                // IconButton is 48dp (minimum interactive size) — the "x" keeps a full touch target.
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .semantics { traversalIndex = 1f }
                        .then(if (dismissTestTag != null) Modifier.testTag(dismissTestTag) else Modifier),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = dismissLabel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (actionStyle == NudgeActionStyle.Button) {
                AppButtonSecondary(
                    text = actionLabel,
                    onClick = onAction,
                    icon = actionIcon,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
