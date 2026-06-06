package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No hardware back button on iOS — navigation is driven by the OS swipe/back model.
}
