package com.antonchuraev.homesearchchecklist.feature.sharing.presentation.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Web stub for share launcher. On the web target, text content could be copied
 * to clipboard via the Web Share API or clipboard API.
 * This implementation triggers completion immediately (no-op for now).
 * A proper implementation would use JavaScript interop via wasmjs-expert.
 */
@Composable
actual fun ShareLauncher(
    textContent: String?,
    pdfFilePath: String?,
    onShareComplete: () -> Unit
) {
    LaunchedEffect(textContent, pdfFilePath) {
        if (textContent != null || pdfFilePath != null) {
            // TODO: Implement web sharing via Web Share API (window.navigator.share)
            //       or clipboard copy — delegate to wasmjs-expert for JS interop.
            onShareComplete()
        }
    }
}
