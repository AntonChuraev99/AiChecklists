package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme

@PreviewLightDark
@Composable
private fun GistiAvatarTileVariantsPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // All 6 palette hues via different seeds
            GistiAvatarTile(seed = 0L, label = "Groceries")
            GistiAvatarTile(seed = 1L, label = "Paris trip")
            GistiAvatarTile(seed = 2L, label = "Work tasks")
            GistiAvatarTile(seed = 3L, label = "Fitness")
            GistiAvatarTile(seed = 4L, label = "Reading")
            GistiAvatarTile(seed = 5L, label = "Budget")
        }
    }
}

@PreviewLightDark
@Composable
private fun GistiAvatarTileEmptyLabelPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Fallback icon when label is empty
            GistiAvatarTile(seed = 0L, label = "")
            // Size variant (64dp)
            GistiAvatarTile(seed = 1L, label = "Weekend", size = 64.dp, cornerRadius = 18.dp)
        }
    }
}
