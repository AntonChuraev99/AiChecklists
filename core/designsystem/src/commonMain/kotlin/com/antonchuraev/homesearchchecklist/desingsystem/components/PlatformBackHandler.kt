package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.runtime.Composable

/**
 * Intercepts the platform back gesture/button while [enabled] is true.
 *
 * - **Android:** maps to `androidx.activity.compose.BackHandler` (system back button/gesture).
 * - **iOS / wasmJs:** no-op — iOS has no hardware back button and the browser owns its own
 *   back navigation.
 *
 * Lives in the design system so any feature (paywall, onboarding, …) can lock navigation
 * during a critical operation without depending on another feature module.
 *
 * @param enabled while true, [onBack] is invoked instead of the default back behaviour;
 *                pass `false` to let back propagate normally (no interception).
 * @param onBack  invoked on back while [enabled]. Pass an empty lambda to fully swallow back.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
