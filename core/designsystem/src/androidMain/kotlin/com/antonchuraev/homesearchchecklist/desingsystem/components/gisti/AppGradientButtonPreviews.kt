package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme

@PreviewLightDark
@Composable
private fun AppGradientButtonEnabledPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppGradientButton(
                text = "Fill via AI",
                onClick = {},
                icon = Icons.Filled.AutoAwesome,
            )
            AppGradientButton(
                text = "Create with AI",
                onClick = {},
                icon = null,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun AppGradientButtonDisabledPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            AppGradientButton(
                text = "Fill via AI",
                onClick = {},
                icon = Icons.Filled.AutoAwesome,
                enabled = false,
            )
        }
    }
}
