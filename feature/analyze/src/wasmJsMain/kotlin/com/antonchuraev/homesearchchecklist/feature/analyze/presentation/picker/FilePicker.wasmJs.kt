package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Web stub for file picker. Returns a no-op launcher.
 * A proper web implementation would use the HTML <input type="file"> element
 * via JavaScript interop (handled by wasmjs-expert if needed).
 */
@Composable
actual fun rememberFilePickerLauncher(
    type: FilePickerType,
    onResult: (FilePickerResult?) -> Unit
): FilePickerLauncher = remember { FilePickerLauncher(onLaunch = { onResult(null) }) }
