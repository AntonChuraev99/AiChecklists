package com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder

import androidx.compose.runtime.Composable

/**
 * Audio recorder state holder that manages the recording lifecycle.
 */
class AudioRecorderLauncher(
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
    private val onCancel: () -> Unit,
    val isRecording: () -> Boolean,
    private val openSettings: () -> Unit = {}
) {
    fun start() = onStart()
    fun stop() = onStop()
    fun cancel() = onCancel()
    fun openAppSettings() = openSettings()
}

/**
 * Result of audio recording.
 */
data class AudioRecordingResult(
    val filePath: String,
    val durationMs: Long,
    val mimeType: String = "audio/m4a"
)

/**
 * Creates an audio recorder launcher that can be used to record voice.
 *
 * @param onResult Callback invoked when recording is stopped, with the file path
 * @param onError Callback invoked when an error occurs
 * @return AudioRecorderLauncher that can be used to control recording
 */
@Composable
expect fun rememberAudioRecorderLauncher(
    onResult: (AudioRecordingResult?) -> Unit,
    onError: (String) -> Unit
): AudioRecorderLauncher
