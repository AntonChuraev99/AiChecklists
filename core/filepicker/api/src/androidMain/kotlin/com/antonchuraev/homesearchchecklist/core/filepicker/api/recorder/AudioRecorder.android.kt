package com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

private const val TAG = "AudioRecorder"

/** Lifecycle of the RECORD_AUDIO request raised by a `start()` call. */
private enum class PermissionRequest {
    /** No request is in flight. */
    NONE,

    /** The dialog is up; recording starts as soon as the permission is granted. */
    AWAITING_START,

    /** The dialog is up, but the press that raised it is over; a grant must not start recording. */
    ABANDONED,
}

@Composable
actual fun rememberAudioRecorderLauncher(
    onResult: (AudioRecordingResult?) -> Unit,
    onError: (String) -> Unit
): AudioRecorderLauncher {
    val context = LocalContext.current
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var startTime by remember { mutableLongStateOf(0L) }
    var permissionRequest by remember { mutableStateOf(PermissionRequest.NONE) }

    // Use rememberUpdatedState to always have the latest callbacks
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)

    // Only called when nothing is recording, so whatever is still referenced here is a leftover
    // of a finished session: a recorder that was never released, the previous output file.
    val beginRecording = remember(context) {
        {
            mediaRecorder?.release()
            mediaRecorder = null
            outputFile?.delete()
            outputFile = null

            runCatching { startRecording(context) }
                .onSuccess { (recorder, file) ->
                    mediaRecorder = recorder
                    outputFile = file
                    isRecording = true
                    startTime = System.currentTimeMillis()
                }
                .onFailure { e ->
                    Log.e(TAG, "start failed: ${e.message}", e)
                    // The host logs this string as the failure cause, so it carries the
                    // exception type: a bare message is often null and merges every failure
                    currentOnError("${e::class.simpleName}: ${e.message ?: "no message"}")
                }
            Unit
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val request = permissionRequest
        permissionRequest = PermissionRequest.NONE

        if (isGranted) {
            if (request == PermissionRequest.AWAITING_START) {
                // Tap-to-record caller still waiting behind the dialog — start as promised
                beginRecording()
            } else {
                // The press ended while the dialog was up (or its state was lost with the
                // composition): recording now would run with no finger down and nobody to stop it
                currentOnError(AudioRecorderErrorCodes.PERMISSION_GRANTED_PRESS_AGAIN)
            }
        } else {
            // Check if we should show rationale (permission denied but not permanently)
            val activity = context as? android.app.Activity
            val shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: false

            if (shouldShowRationale) {
                // User denied but can still ask again
                currentOnError(AudioRecorderErrorCodes.PERMISSION_DENIED)
            } else {
                // Permission permanently denied - suggest settings
                currentOnError(AudioRecorderErrorCodes.PERMISSION_DENIED_PERMANENTLY)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaRecorder?.let { recorder ->
                // Leaving mid-recording: nobody is left to receive the file. A file that was
                // already delivered through onResult belongs to the caller and is not touched.
                recorder.release()
                outputFile?.delete()
                outputFile = null
            }
            mediaRecorder = null
        }
    }

    return remember(context) {
        AudioRecorderLauncher(
            onStart = {
                when {
                    // Deliberate no-op, no callback: a second start() must not tear down the
                    // session that is already recording or already waiting for the permission.
                    isRecording || permissionRequest != PermissionRequest.NONE -> Unit

                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED -> beginRecording()

                    else -> {
                        permissionRequest = PermissionRequest.AWAITING_START
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            onStop = {
                val recorder = mediaRecorder
                when {
                    // Released before the dialog was answered: same as cancel in this state
                    permissionRequest != PermissionRequest.NONE ->
                        permissionRequest = PermissionRequest.ABANDONED

                    // Nothing is recording
                    recorder == null -> currentOnResult(null)

                    else -> {
                        val file = outputFile
                        val stopped = runCatching { recorder.stop() }
                        recorder.release()
                        mediaRecorder = null
                        isRecording = false

                        stopped
                            .onSuccess {
                                if (file != null && file.exists()) {
                                    currentOnResult(
                                        AudioRecordingResult(
                                            filePath = file.absolutePath,
                                            durationMs = System.currentTimeMillis() - startTime,
                                            mimeType = "audio/m4a"
                                        )
                                    )
                                } else {
                                    currentOnResult(null)
                                }
                            }
                            .onFailure { e ->
                                // stop() throws when no valid audio was captured (stopped right
                                // after start); the file it leaves behind is unusable
                                Log.w(TAG, "stop failed, recording discarded: ${e.message}", e)
                                file?.delete()
                                outputFile = null
                                currentOnError(AudioRecorderErrorCodes.RECORDING_TOO_SHORT)
                            }
                    }
                }
            },
            onCancel = {
                if (permissionRequest != PermissionRequest.NONE) {
                    // Nothing was recorded, so there is no result to report yet: the terminal
                    // callback for this press comes from the permission result
                    permissionRequest = PermissionRequest.ABANDONED
                } else {
                    // Stop errors are irrelevant on cancel — the recording is discarded anyway
                    runCatching { mediaRecorder?.stop() }
                    mediaRecorder?.release()
                    mediaRecorder = null
                    isRecording = false
                    outputFile?.delete()
                    outputFile = null
                    currentOnResult(null)
                }
            },
            isRecording = { isRecording },
            openSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        )
    }
}

/**
 * Creates, configures and starts a recorder writing to a new cache file.
 * On failure nothing is leaked: the recorder is released and the file deleted before rethrowing.
 */
private fun startRecording(context: Context): Pair<MediaRecorder, File> {
    // Create output file
    val outputFile = File(context.cacheDir, "voice_recording_${System.currentTimeMillis()}.m4a")

    // Create and configure MediaRecorder
    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }

    return runCatching {
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setOutputFile(outputFile)
            } else {
                // setOutputFile(File) is API 26+, minSdk is 24
                setOutputFile(outputFile.absolutePath)
            }
            prepare()
            start()
        }
        recorder to outputFile
    }.onFailure {
        recorder.release()
        outputFile.delete()
    }.getOrThrow()
}
