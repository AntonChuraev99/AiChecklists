package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

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
private fun ChecklistDetailChatDockPreview() {
    AppTheme {
        val hazeState = rememberHazeState()
        Box {
            // Stand-in scrolling content so the dock has a backdrop to blur in preview.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .hazeSource(hazeState),
            )
            ChecklistDetailChatDock(
                hazeState = hazeState,
                checklistName = "Grocery list",
                onChatClick = {},
                onMicClick = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
