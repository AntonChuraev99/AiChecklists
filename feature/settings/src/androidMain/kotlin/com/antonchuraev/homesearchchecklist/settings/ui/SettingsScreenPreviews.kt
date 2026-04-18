package com.antonchuraev.homesearchchecklist.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.settings.domain.AppThemeMode

// ---------------------------------------------------------------------------
// Android-only Compose Previews for SettingsScreenContent.
// Placed in androidMain because @Preview is not available in commonMain KMP.
// ---------------------------------------------------------------------------

@Preview(name = "Settings — Light Theme", showBackground = true, showSystemUi = true)
@Composable
private fun SettingsLightPreview() {
    AppTheme(darkTheme = false) {
        SettingsScreenContent(
            selectedTheme = AppThemeMode.System,
            onThemeChange = {},
            onBackClick = {},
        )
    }
}

@Preview(
    name = "Settings — Dark Theme",
    showBackground = true,
    showSystemUi = true,
    backgroundColor = 0xFF141218,
)
@Composable
private fun SettingsDarkPreview() {
    AppTheme(darkTheme = true) {
        SettingsScreenContent(
            selectedTheme = AppThemeMode.Dark,
            onThemeChange = {},
            onBackClick = {},
        )
    }
}

@Preview(name = "Settings — Light / Light selected", showBackground = true)
@Composable
private fun SettingsLightSelectedPreview() {
    AppTheme(darkTheme = false) {
        SettingsScreenContent(
            selectedTheme = AppThemeMode.Light,
            onThemeChange = {},
            onBackClick = {},
        )
    }
}
