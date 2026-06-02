package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme

@PreviewLightDark
@Composable
private fun GistiChatDockPreview_MainScreen() {
    AppTheme {
        Column(modifier = Modifier.padding(AppDimens.SpacingLg)) {
            GistiChatDock(
                placeholder = "Ask Gisti to add, remind, or plan…",
                onClick = {},
                onMicClick = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun GistiChatDockPreview_DetailContext() {
    AppTheme {
        Column(modifier = Modifier.padding(AppDimens.SpacingLg)) {
            GistiChatDock(
                placeholder = "Ask Gisti…",
                onClick = {},
                onMicClick = {},
                contextLabel = "Ask about “Grocery list”",
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ChecklistDetailBottomBarPreview() {
    AppTheme {
        ChecklistDetailBottomBar(
            checklistName = "Grocery list",
            onChatClick = {},
            onMicClick = {},
        )
    }
}
