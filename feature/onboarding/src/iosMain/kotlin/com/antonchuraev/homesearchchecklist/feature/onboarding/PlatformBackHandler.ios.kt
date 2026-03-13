package com.antonchuraev.homesearchchecklist.feature.onboarding

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No system back button on iOS
}
