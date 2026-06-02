package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * Persistent AI command bar shown at the bottom of the home screen.
 *
 * The entire bar (excluding the mic button) is one tap target → [onClick].
 * The mic icon is a separate 40dp touch target → [onMicClick].
 *
 * Visual spec (from gisti-extra.jsx AskGistiBar):
 *  - Height: 56dp, corner radius: 16dp
 *  - Background: `surfaceContainerLowest` (white card)
 *  - Border: 1.5dp `outlineVariant`
 *  - Shadow: light only (2dp), none in dark (follows project's AppCard pattern)
 *  - Left: [SparkleTile] 28dp
 *  - Center: placeholder in `onSurfaceVariant` faint, or value in `onSurface`
 *  - Right: [IconButton] 40dp with Mic icon in `onSurfaceVariant`
 *
 * Token mapping:
 * - Container: `colorScheme.surfaceContainerLowest`
 * - Border: `colorScheme.outlineVariant`
 * - Placeholder text: `colorScheme.onSurfaceVariant`
 * - Value text: `colorScheme.onSurface`
 * - Mic icon: `colorScheme.onSurfaceVariant`
 *
 * @param placeholder Text shown when [value] is null.
 * @param onClick Invoked when the non-mic area is tapped (opens chat).
 * @param onMicClick Invoked when the mic icon is tapped (starts voice input).
 * @param value If non-null, shown in place of the placeholder with full `onSurface` color.
 * @param micContentDescription Accessibility label for the mic [IconButton].
 */
@Composable
fun AskGistiBar(
    placeholder: String,
    onClick: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
    micContentDescription: String = "",
    value: String? = null,
) {
    val shape = RoundedCornerShape(16.dp)

    // Flat input — no shadow (user request 2026-06-02). The 1.5dp outlineVariant
    // border alone separates the bar from the background, keeping the home screen calm.
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            modifier = Modifier
                // The clickable fills the row minus the mic button end zone.
                // We apply clickable on the Row and let the IconButton consume its own events.
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                    role = Role.Button,
                )
                .padding(start = 14.dp, end = 4.dp),
        ) {
            SparkleTile(size = 28.dp)

            Text(
                text = value ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            IconButton(
                onClick = onMicClick,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = micContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
            }
        }
    }
}
