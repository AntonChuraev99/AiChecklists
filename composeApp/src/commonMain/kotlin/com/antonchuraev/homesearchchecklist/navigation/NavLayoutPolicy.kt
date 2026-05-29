package com.antonchuraev.homesearchchecklist.navigation

/**
 * Platform identifier returned by `getPlatformName()` for the web (wasmJs) target.
 */
private const val PLATFORM_WEB = "web"

/**
 * Decides whether the navigation layout should be **single-pane** (every
 * destination is full-screen; opening a checklist replaces the list in place)
 * instead of the **list-detail two-pane** layout used on wide windows.
 *
 * - **Web (wasmJs)** → single-pane. On a wide browser window the checklist list
 *   should fill the whole content area, and tapping a checklist should replace
 *   it in place — not sit beside it in a second detail pane.
 * - **Android / iOS** → list-detail two-pane is kept (returns `false`), so the
 *   Medium/Expanded experience on those platforms is unchanged.
 *
 * Kept as a pure function (taking the platform name rather than calling
 * `getPlatformName()` itself) so the decision is unit-testable on the JVM host
 * without a Compose runtime — mirrors how `classifyWindowWidth` is split out of
 * the `@Composable rememberAppWindowSizeClass`.
 *
 * @param platformName value from `getPlatformName()` — `"web"`, `"android"`, or `"ios"`.
 */
internal fun shouldUseSinglePaneLayout(platformName: String): Boolean =
    platformName == PLATFORM_WEB
