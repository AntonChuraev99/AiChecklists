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
                    remindLabel = "Remind me…",
                    linkLabel = "Link → list",
                    planDayLabel = "Plan day",
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
                    GistiPromptChip(emoji = "🛒", label = "Grocery list", action = GistiQuickAction.PHOTO),
                    GistiPromptChip(emoji = "✈️", label = "Trip packing", action = GistiQuickAction.LINK),
                    GistiPromptChip(emoji = "💪", label = "Workout plan", action = GistiQuickAction.PLAN_DAY),
                    GistiPromptChip(emoji = "📚", label = "Study notes", action = GistiQuickAction.PDF),
                ),
                onChipClick = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun GistiPromptChipsChecklistPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            GistiPromptChips(
                chips = gistiChecklistPromptChips(
                    whatsMissingLabel = "What's missing?",
                    addItemsLabel = "Add items",
                    summaryLabel = "Summary",
                    remindLabel = "Remind me",
                ),
                onChipClick = {},
            )
        }
    }
}
