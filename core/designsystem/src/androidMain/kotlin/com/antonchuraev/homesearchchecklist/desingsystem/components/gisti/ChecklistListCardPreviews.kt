package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
private fun ChecklistListCardVariantsPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // In-progress list
            ChecklistListCard(
                name = "Groceries",
                checkedItems = 2,
                totalItems = 8,
                seed = 0L,
                editedLabel = "edited 2h ago",
            )
            // 100% complete (🎉, success color)
            ChecklistListCard(
                name = "Paris trip",
                checkedItems = 5,
                totalItems = 5,
                seed = 1L,
                editedLabel = "edited yesterday",
            )
            // Empty list (no items yet)
            ChecklistListCard(
                name = "New project",
                checkedItems = 0,
                totalItems = 0,
                seed = 2L,
            )
            // Long name — ellipsis check
            ChecklistListCard(
                name = "Weekly meal prep for the whole family incl. snacks",
                checkedItems = 3,
                totalItems = 12,
                seed = 3L,
                editedLabel = "edited 20m ago",
            )
        }
    }
}
