package com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.PlaylistAddCheck
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.unlock_more_with_premium
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdatePost
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdatePostAction
import org.jetbrains.compose.resources.stringResource

/**
 * A single feature row inside a [ReleaseCard].
 * No outer card — just icon + title + description + optional action buttons.
 *
 * When [lockedActionDeepLinks] contains an action's deepLink, that action
 * renders as "Unlock with Premium" CTA (with a lock icon) instead of its
 * original label. The onClick still fires [onActionClick] — the ViewModel
 * intercepts locked actions and routes to the paywall instead.
 */
@Composable
internal fun FeatureItem(
    post: UpdatePost,
    onActionClick: (UpdatePostAction) -> Unit,
    modifier: Modifier = Modifier,
    lockedActionDeepLinks: Set<String> = emptySet()
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)
    ) {
        // Header row: icon + title
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconForName(post.iconName),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(AppDimens.SpacingSm))
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        // Description
        Text(
            text = post.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Actions
        if (post.actions.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)
            ) {
                val primaryAction = post.actions[0]
                val isPrimaryLocked = primaryAction.deepLink in lockedActionDeepLinks

                // First action — primary button (locked variant shows lock icon + "Unlock with Premium")
                AppButton(
                    text = if (isPrimaryLocked) {
                        stringResource(Res.string.unlock_more_with_premium)
                    } else {
                        primaryAction.label
                    },
                    icon = if (isPrimaryLocked) Icons.Outlined.Lock else null,
                    onClick = { onActionClick(primaryAction) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Remaining actions — text buttons (lock state applies to each individually)
                post.actions.drop(1).forEach { action ->
                    val isLocked = action.deepLink in lockedActionDeepLinks
                    AppButtonText(
                        text = if (isLocked) {
                            stringResource(Res.string.unlock_more_with_premium)
                        } else {
                            action.label
                        },
                        onClick = { onActionClick(action) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

internal fun iconForName(name: String?): ImageVector = when (name) {
    "AutoAwesome" -> Icons.Outlined.AutoAwesome
    "Bolt" -> Icons.Outlined.Bolt
    "Star" -> Icons.Outlined.Star
    "Campaign" -> Icons.Outlined.Campaign
    "Notifications" -> Icons.Outlined.Notifications
    "Widgets" -> Icons.Outlined.Widgets
    "Replay" -> Icons.Outlined.Replay
    "DragIndicator" -> Icons.Filled.DragIndicator
    "PlaylistAddCheck" -> Icons.AutoMirrored.Outlined.PlaylistAddCheck
    "Tune" -> Icons.Outlined.Tune
    "Celebration" -> Icons.Outlined.Celebration
    else -> Icons.AutoMirrored.Outlined.Article
}
