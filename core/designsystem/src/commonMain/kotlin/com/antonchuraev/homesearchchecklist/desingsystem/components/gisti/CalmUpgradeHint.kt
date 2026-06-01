package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Calm, non-intrusive upsell hint row placed below the checklist list on the home screen.
 *
 * Intentionally **not** a gradient banner — this follows the design brief's "quiet upsell"
 * direction (gisti-screens.jsx upgrade-hint comment: "calm upgrade hint"). The CTA uses
 * `primary` text color instead of a filled chip to feel like a suggestion, not an ad.
 *
 * Visual spec (from gisti-screens.jsx RxHome upgrade-hint):
 *  - Row padding: 4dp vertical, 4dp horizontal
 *  - Left: [Icons.Outlined.AutoAwesome] 15dp, tint `onSurfaceVariant` at 75% alpha ("faint")
 *  - Center: [text] (12.5sp / `onSurfaceVariant`, weight 1f)
 *  - Right: [actionLabel] (12.5sp / 700 / `primary`, tap area via semantics)
 *
 * Token mapping:
 * - Icon tint: `colorScheme.onSurfaceVariant` at 0.75f alpha
 * - Text: `colorScheme.onSurfaceVariant`
 * - Action: `colorScheme.primary`, fontWeight 700
 *
 * Accessibility: the action text is a separate clickable element with [Role.Button] semantics.
 * The icon is decorative (contentDescription = null).
 *
 * @param text Supporting text (e.g. "3 of 4 free lists used").
 * @param actionLabel CTA label (e.g. "Go Premium").
 * @param onActionClick Called when the action label is tapped.
 */
@Composable
fun CalmUpgradeHint(
    text: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.size(15.dp),
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onActionClick, role = Role.Button),
        )
    }
}
