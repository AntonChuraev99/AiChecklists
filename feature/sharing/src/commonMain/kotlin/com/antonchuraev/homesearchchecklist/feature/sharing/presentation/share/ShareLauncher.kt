package com.antonchuraev.homesearchchecklist.feature.sharing.presentation.share

import androidx.compose.runtime.Composable

/**
 * Platform-specific share launcher.
 * Handles sharing text content or PDF files using native sharing mechanisms.
 */
@Composable
expect fun ShareLauncher(
    textContent: String?,
    pdfFilePath: String?,
    onShareComplete: () -> Unit
)
