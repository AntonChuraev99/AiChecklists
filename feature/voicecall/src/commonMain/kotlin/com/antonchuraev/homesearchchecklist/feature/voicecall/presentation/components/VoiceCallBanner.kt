package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.NoticeBannerActionFit
import com.antonchuraev.homesearchchecklist.desingsystem.components.NoticeBannerLayout
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * The single banner under the top row (connection lost / minutes running out).
 *
 * Its semantics are STATIC ([a11yText], a polite live region announced once when it appears); the
 * visible [text] may tick ("…in 14 s") and is hidden from TalkBack so seconds are not read out.
 */
@Composable
fun VoiceCallBanner(
    icon: ImageVector,
    text: String,
    a11yText: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AppCard(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = AppDimens.SpacingMd,
            end = if (actionLabel != null) AppDimens.SpacingXs else AppDimens.SpacingMd,
            top = AppDimens.SpacingSm,
            bottom = AppDimens.SpacingSm,
        ),
    ) {
        NoticeBannerLayout(
            modifier = Modifier.heightIn(min = AppDimens.MinTouchTarget),
            actionFit = NoticeBannerActionFit.LongestWordFits,
            icon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
            },
            text = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = a11yText
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            },
            action = if (actionLabel != null && onAction != null) {
                { AppButtonText(text = actionLabel, onClick = onAction) }
            } else {
                null
            },
        )
    }
}
