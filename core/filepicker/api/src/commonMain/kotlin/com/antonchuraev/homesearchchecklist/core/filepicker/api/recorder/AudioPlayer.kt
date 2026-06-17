package com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder

import androidx.compose.runtime.Composable

/**
 * Audio player state holder for playback control.
 */
class AudioPlayerLauncher(
    private val onPlay: (filePath: String) -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit,
    val isPlaying: () -> Boolean
) {
    fun play(filePath: String) = onPlay(filePath)
    fun pause() = onPause()
    fun stop() = onStop()
}

/**
 * Creates an audio player launcher for playing recorded audio.
 *
 * @param onPlaybackComplete Callback when playback finishes
 * @param onError Callback when an error occurs
 * @return AudioPlayerLauncher for controlling playback
 */
@Composable
expect fun rememberAudioPlayerLauncher(
    onPlaybackComplete: () -> Unit,
    onError: (String) -> Unit
): AudioPlayerLauncher
