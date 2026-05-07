package com.antonchuraev.homesearchchecklist.feature.onboarding

import androidx.compose.runtime.Composable

/**
 * Web stub for platform back handler. Browser back navigation is handled by
 * the browser itself. This is a no-op on the web target.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op: browser handles its own back navigation
}
