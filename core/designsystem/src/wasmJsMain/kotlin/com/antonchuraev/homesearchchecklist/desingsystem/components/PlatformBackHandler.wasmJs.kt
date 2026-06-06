package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op: the browser owns its own back navigation.
}
