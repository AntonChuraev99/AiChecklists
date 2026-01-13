package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.picker

import androidx.compose.runtime.Composable

/**
 * File picker state holder that manages the picker lifecycle.
 */
class FilePickerLauncher(
    private val onLaunch: () -> Unit
) {
    fun launch() {
        onLaunch()
    }
}

/**
 * Creates a file picker launcher that can be used to pick files.
 *
 * @param type The type of file to pick (IMAGE, PDF, TEXT)
 * @param onResult Callback invoked when a file is picked, or null if cancelled
 * @return FilePickerLauncher that can be used to trigger the picker
 */
@Composable
expect fun rememberFilePickerLauncher(
    type: FilePickerType,
    onResult: (FilePickerResult?) -> Unit
): FilePickerLauncher
