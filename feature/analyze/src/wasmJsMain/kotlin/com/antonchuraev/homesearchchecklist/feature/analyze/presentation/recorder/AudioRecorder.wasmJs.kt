package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.recorder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Web stub for audio recorder. Voice recording is not supported on this target.
 * Returns a no-op launcher that immediately reports null result.
 */
@Composable
actual fun rememberAudioRecorderLauncher(
    onResult: (AudioRecordingResult?) -> Unit,
    onError: (String) -> Unit
): AudioRecorderLauncher = remember {
    AudioRecorderLauncher(
        onStart = { onError("Audio recording is not supported on web.") },
        onStop = { onResult(null) },
        onCancel = {},
        isRecording = { false }
    )
}
