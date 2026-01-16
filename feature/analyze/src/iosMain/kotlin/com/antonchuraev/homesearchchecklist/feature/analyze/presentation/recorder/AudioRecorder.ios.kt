package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.recorder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.NSDate
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberAudioRecorderLauncher(
    onResult: (AudioRecordingResult?) -> Unit,
    onError: (String) -> Unit
): AudioRecorderLauncher {
    var audioRecorder by remember { mutableStateOf<AVAudioRecorder?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var outputUrl by remember { mutableStateOf<NSURL?>(null) }
    var startTime by remember { mutableLongStateOf(0L) }

    // Use rememberUpdatedState to always have the latest callbacks
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)

    DisposableEffect(Unit) {
        onDispose {
            audioRecorder?.stop()
            audioRecorder = null
        }
    }

    return remember {
        AudioRecorderLauncher(
            onStart = {
                val session = AVAudioSession.sharedInstance()

                when (session.recordPermission) {
                    AVAudioSessionRecordPermissionGranted -> {
                        try {
                            startRecording(session, audioRecorder, outputUrl) { recorder, url ->
                                audioRecorder = recorder
                                outputUrl = url
                                isRecording = true
                                startTime = (NSDate().timeIntervalSince1970 * 1000).toLong()
                            }
                        } catch (e: Exception) {
                            currentOnError(e.message ?: "Failed to start recording")
                        }
                    }
                    AVAudioSessionRecordPermissionDenied -> {
                        // Permission was previously denied - suggest settings
                        currentOnError("PERMISSION_DENIED_PERMANENTLY")
                    }
                    else -> {
                        // Undetermined - request permission
                        session.requestRecordPermission { granted ->
                            if (granted) {
                                try {
                                    startRecording(session, audioRecorder, outputUrl) { recorder, url ->
                                        audioRecorder = recorder
                                        outputUrl = url
                                        isRecording = true
                                        startTime = (NSDate().timeIntervalSince1970 * 1000).toLong()
                                    }
                                } catch (e: Exception) {
                                    currentOnError(e.message ?: "Failed to start recording")
                                }
                            } else {
                                // User just denied - they can still change in settings
                                currentOnError("PERMISSION_DENIED_PERMANENTLY")
                            }
                        }
                    }
                }
            },
            onStop = {
                try {
                    audioRecorder?.stop()
                    audioRecorder = null
                    isRecording = false

                    val url = outputUrl
                    if (url != null) {
                        val duration = (NSDate().timeIntervalSince1970 * 1000).toLong() - startTime
                        currentOnResult(
                            AudioRecordingResult(
                                filePath = url.absoluteString ?: "",
                                durationMs = duration,
                                mimeType = "audio/m4a"
                            )
                        )
                    } else {
                        currentOnResult(null)
                    }
                } catch (e: Exception) {
                    currentOnError(e.message ?: "Failed to stop recording")
                    audioRecorder = null
                    isRecording = false
                }
            },
            onCancel = {
                audioRecorder?.stop()
                audioRecorder = null
                isRecording = false

                outputUrl?.path?.let { path ->
                    NSFileManager.defaultManager.removeItemAtPath(path, null)
                }
                outputUrl = null
                currentOnResult(null)
            },
            isRecording = { isRecording },
            openSettings = {
                val settingsUrl = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
                if (settingsUrl != null) {
                    UIApplication.sharedApplication.openURL(settingsUrl)
                }
            }
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun startRecording(
    session: AVAudioSession,
    existingRecorder: AVAudioRecorder?,
    existingUrl: NSURL?,
    onStarted: (AVAudioRecorder, NSURL) -> Unit
) {
    // Clean up existing recorder
    existingRecorder?.stop()
    existingUrl?.path?.let { path ->
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    // Configure audio session
    session.setCategory(AVAudioSessionCategoryPlayAndRecord, null)
    session.setActive(true, null)

    // Create output file URL
    val documentsUrl = NSFileManager.defaultManager.URLsForDirectory(
        NSDocumentDirectory,
        NSUserDomainMask
    ).firstOrNull() as? NSURL ?: throw Exception("Cannot access documents directory")

    val fileName = "voice_recording_${NSDate().timeIntervalSince1970.toLong()}.m4a"
    val outputUrl = documentsUrl.URLByAppendingPathComponent(fileName)
        ?: throw Exception("Cannot create output URL")

    // Configure recorder settings
    val settings = mapOf<Any?, Any?>(
        AVFormatIDKey to kAudioFormatMPEG4AAC,
        AVSampleRateKey to 44100.0,
        AVNumberOfChannelsKey to 1,
        AVEncoderBitRateKey to 128000,
        AVEncoderAudioQualityKey to 100 // AVAudioQualityMax
    )

    val recorder = AVAudioRecorder(outputUrl, settings, null)
        ?: throw Exception("Cannot create audio recorder")

    recorder.prepareToRecord()
    recorder.record()

    onStarted(recorder, outputUrl)
}
