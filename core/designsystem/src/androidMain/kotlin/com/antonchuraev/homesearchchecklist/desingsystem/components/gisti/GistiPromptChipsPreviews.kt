package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme

@PreviewLightDark
@Composable
private fun GistiPromptChipsDefaultPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            GistiPromptChips(
                chips = gistiDefaultPromptChips(
                    photoLabel = "Photo → list",
                    addLabel = "Add tasks",
                    remindLabel = "Remind me…",
                ),
                onChipClick = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun GistiPromptChipsCustomPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            GistiPromptChips(
                chips = listOf(
                    GistiPromptChip(emoji = "🛒", label = "Grocery list"),
                    GistiPromptChip(emoji = "✈️", label = "Trip packing"),
                    GistiPromptChip(emoji = "💪", label = "Workout plan"),
                    GistiPromptChip(emoji = "📚", label = "Study notes"),
                ),
                onChipClick = {},
            )
        }
    }
}
