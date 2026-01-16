package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.recorder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberAudioPlayerLauncher(
    onPlaybackComplete: () -> Unit,
    onError: (String) -> Unit
): AudioPlayerLauncher {
    var audioPlayer by remember { mutableStateOf<AVAudioPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    val currentOnPlaybackComplete by rememberUpdatedState(onPlaybackComplete)
    val currentOnError by rememberUpdatedState(onError)

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer?.stop()
            audioPlayer = null
        }
    }

    return remember {
        AudioPlayerLauncher(
            onPlay = { filePath ->
                try {
                    // Stop existing player
                    audioPlayer?.stop()

                    // Configure audio session for playback
                    val session = AVAudioSession.sharedInstance()
                    session.setCategory(AVAudioSessionCategoryPlayback, null)
                    session.setActive(true, null)

                    // Create URL from file path
                    val url = if (filePath.startsWith("file://")) {
                        NSURL.URLWithString(filePath)
                    } else {
                        NSURL.fileURLWithPath(filePath)
                    } ?: throw Exception("Invalid file path")

                    val player = AVAudioPlayer(url, null)
                        ?: throw Exception("Cannot create audio player")

                    player.prepareToPlay()
                    player.play()

                    audioPlayer = player
                    isPlaying = true

                    // Note: iOS AVAudioPlayer delegate for completion would need
                    // more complex setup with Kotlin/Native. For now, user can
                    // manually stop or it will stop when finished.
                } catch (e: Exception) {
                    isPlaying = false
                    currentOnError(e.message ?: "Failed to play audio")
                }
            },
            onPause = {
                try {
                    audioPlayer?.pause()
                    isPlaying = false
                } catch (e: Exception) {
                    currentOnError(e.message ?: "Failed to pause")
                }
            },
            onStop = {
                try {
                    audioPlayer?.stop()
                    audioPlayer = null
                    isPlaying = false
                } catch (e: Exception) {
                    audioPlayer = null
                    isPlaying = false
                }
            },
            isPlaying = { isPlaying }
        )
    }
}
