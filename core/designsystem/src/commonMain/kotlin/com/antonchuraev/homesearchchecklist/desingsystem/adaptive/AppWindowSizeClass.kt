package com.antonchuraev.homesearchchecklist.desingsystem.adaptive

import androidx.compose.runtime.Composable

/**
 * Material 3 adaptive size buckets:
 * - Compact: <600dp width (phones portrait)
 * - Medium: 600..839dp (tablets portrait, foldables, small laptops)
 * - Expanded: >=840dp (tablets landscape, desktop, large laptops)
 *
 * Used to switch between ModalNavigationDrawer (Compact), NavigationRail (Medium)
 * and PermanentNavigationDrawer (Expanded) — see AdaptiveNavigationShell.
 */
enum class AppWindowSizeClass {
    Compact,
    Medium,
    Expanded,
}

/**
 * Returns current window size class, recomputed on configuration change /
 * browser resize. Stable across recompositions for a single layout pass.
 *
 * Breakpoints per Material 3 guidance:
 * - <600dp width => Compact
 * - 600..839dp width => Medium
 * - >=840dp width => Expanded
 *
 * Android: backed by LocalConfiguration.current.screenWidthDp.
 * wasmJs: backed by globalThis.innerWidth + DisposableEffect on "resize" event.
 * iOS: backed by Compose LocalWindowInfo.containerSize.
 */
@Composable
expect fun rememberAppWindowSizeClass(): AppWindowSizeClass

/**
 * Pure-function classifier reusable from tests + actuals to avoid drift.
 * widthDp = current viewport width in density-independent pixels.
 */
fun classifyWindowWidth(widthDp: Int): AppWindowSizeClass = when {
    widthDp < 600 -> AppWindowSizeClass.Compact
    widthDp < 840 -> AppWindowSizeClass.Medium
    else -> AppWindowSizeClass.Expanded
}
