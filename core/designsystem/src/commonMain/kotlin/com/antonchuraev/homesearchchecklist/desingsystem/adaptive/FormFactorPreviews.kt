package com.antonchuraev.homesearchchecklist.desingsystem.adaptive

import androidx.compose.ui.tooling.preview.Preview

/**
 * Multi-Preview annotation that renders the target Composable across 4 form factors.
 * Use in place of [@Preview] on top-level screen functions so visual regressions
 * caught by the layout engine show up immediately in Android Studio Preview pane.
 *
 * Sizes mirror the Playwright screenshot pipeline in playwright/playwright.config.ts.
 */
@Preview(name = "Phone (360x800)", widthDp = 360, heightDp = 800)
@Preview(name = "Tablet portrait (800x1280)", widthDp = 800, heightDp = 1280)
@Preview(name = "Tablet landscape (1280x800)", widthDp = 1280, heightDp = 800)
@Preview(name = "Desktop (1440x900)", widthDp = 1440, heightDp = 900)
annotation class FormFactorPreviews
