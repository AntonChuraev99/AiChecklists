package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components

import androidx.compose.runtime.withFrameMillis

/**
 * Waits [millis] of FRAME-clock time — the clock the composition's animations run on — instead of
 * `delay`, whose time source on Android is the wall clock (the UI dispatcher has no `Delay` of its
 * own). Keeps a UI hold (ring 1.2 s, spinner threshold, overlay beats) in step with the animations
 * around it, and deterministic under a test clock.
 *
 * Only for short holds: every frame of the wait is requested.
 */
internal suspend fun awaitFrameMillis(millis: Long) {
    val start = withFrameMillis { it }
    while (withFrameMillis { it } - start < millis) Unit
}
