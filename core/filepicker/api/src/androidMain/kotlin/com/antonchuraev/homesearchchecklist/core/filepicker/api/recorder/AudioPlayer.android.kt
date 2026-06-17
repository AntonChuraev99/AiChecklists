package com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

@Composable
actual fun rememberAudioPlayerLauncher(
    onPlaybackComplete: () -> Unit,
    onError: (String) -> Unit
): AudioPlayerLauncher {
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    val currentOnPlaybackComplete by rememberUpdatedState(onPlaybackComplete)
    val currentOnError by rememberUpdatedState(onError)

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    return remember {
        AudioPlayerLauncher(
            onPlay = { filePath ->
                try {
                    // Stop and release existing player
                    mediaPlayer?.release()

                    val player = MediaPlayer().apply {
                        setDataSource(filePath)
                        setOnCompletionListener {
                            isPlaying = false
                            currentOnPlaybackComplete()
                        }
                        setOnErrorListener { _, _, _ ->
                            isPlaying = false
                            currentOnError("Playback error")
                            true
                        }
                        prepare()
                        start()
                    }
                    mediaPlayer = player
                    isPlaying = true
                } catch (e: Exception) {
                    isPlaying = false
                    currentOnError(e.message ?: "Failed to play audio")
                }
            },
            onPause = {
                try {
                    mediaPlayer?.pause()
                    isPlaying = false
                } catch (e: Exception) {
                    currentOnError(e.message ?: "Failed to pause")
                }
            },
            onStop = {
                try {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                    mediaPlayer = null
                    isPlaying = false
                } catch (e: Exception) {
                    mediaPlayer = null
                    isPlaying = false
                }
            },
            isPlaying = { isPlaying }
        )
    }
}
