package com.antonchuraev.homesearchchecklist.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.detail_pane_placeholder
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * Placeholder shown in the detail pane of a list-detail layout when no item
 * is selected yet (Medium/Expanded window size class, initial state).
 *
 * On Compact screens this is never shown — the detail pane isn't visible until
 * the user taps a list item, which performs a full-screen push.
 */
@Composable
fun EmptyDetailPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppDimens.SpacingXxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingLg),
        ) {
            Icon(
                imageVector = Icons.Outlined.Checklist,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = stringResource(Res.string.detail_pane_placeholder),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
