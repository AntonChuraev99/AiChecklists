package com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Web stub for audio player. Audio playback is not supported on this target.
 * Returns a no-op launcher.
 */
@Composable
actual fun rememberAudioPlayerLauncher(
    onPlaybackComplete: () -> Unit,
    onError: (String) -> Unit
): AudioPlayerLauncher = remember {
    AudioPlayerLauncher(
        onPlay = { onError("Audio playback is not supported on web.") },
        onPause = {},
        onStop = {},
        isPlaying = { false }
    )
}
