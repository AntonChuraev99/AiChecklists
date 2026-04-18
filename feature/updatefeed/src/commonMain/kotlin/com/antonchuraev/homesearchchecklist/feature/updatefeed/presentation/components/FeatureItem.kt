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
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdatePost
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdatePostAction

/**
 * A single feature row inside a [ReleaseCard].
 * No outer card — just icon + title + description + optional action buttons.
 */
@Composable
internal fun FeatureItem(
    post: UpdatePost,
    onActionClick: (UpdatePostAction) -> Unit,
    modifier: Modifier = Modifier
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
                // First action — primary button
                AppButton(
                    text = post.actions[0].label,
                    onClick = { onActionClick(post.actions[0]) },
                    modifier = Modifier.fillMaxWidth()
                )
                // Remaining actions — text buttons
                post.actions.drop(1).forEach { action ->
                    AppButtonText(
                        text = action.label,
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
