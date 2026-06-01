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
private fun AskGistiBarPlaceholderPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            AskGistiBar(
                placeholder = "Ask Gisti to add, remind, or plan…",
                onClick = {},
                onMicClick = {},
                micContentDescription = "Voice input",
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun AskGistiBarWithValuePreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            AskGistiBar(
                placeholder = "Ask Gisti to add, remind, or plan…",
                onClick = {},
                onMicClick = {},
                micContentDescription = "Voice input",
                value = "Remind me to buy milk tomorrow",
            )
        }
    }
}
