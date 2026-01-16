package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.recorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

@Composable
actual fun rememberAudioRecorderLauncher(
    onResult: (AudioRecordingResult?) -> Unit,
    onError: (String) -> Unit
): AudioRecorderLauncher {
    val context = LocalContext.current
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var outputFile by remember { mutableStateOf<java.io.File?>(null) }
    var startTime by remember { mutableLongStateOf(0L) }
    var pendingStartAfterPermission by remember { mutableStateOf(false) }

    // Use rememberUpdatedState to always have the latest callbacks
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, start recording if we were waiting
            if (pendingStartAfterPermission) {
                pendingStartAfterPermission = false
                try {
                    startRecording(context, mediaRecorder, outputFile) { recorder, file ->
                        mediaRecorder = recorder
                        outputFile = file
                        isRecording = true
                        startTime = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    currentOnError(e.message ?: "Failed to start recording")
                }
            }
        } else {
            pendingStartAfterPermission = false
            // Check if we should show rationale (permission denied but not permanently)
            val activity = context as? android.app.Activity
            val shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: false

            if (shouldShowRationale) {
                // User denied but can still ask again
                currentOnError("PERMISSION_DENIED")
            } else {
                // Permission permanently denied - suggest settings
                currentOnError("PERMISSION_DENIED_PERMANENTLY")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    return remember(context) {
        AudioRecorderLauncher(
            onStart = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    try {
                        startRecording(context, mediaRecorder, outputFile) { recorder, file ->
                            mediaRecorder = recorder
                            outputFile = file
                            isRecording = true
                            startTime = System.currentTimeMillis()
                        }
                    } catch (e: Exception) {
                        currentOnError(e.message ?: "Failed to start recording")
                    }
                } else {
                    pendingStartAfterPermission = true
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onStop = {
                try {
                    mediaRecorder?.stop()
                    mediaRecorder?.release()
                    mediaRecorder = null
                    isRecording = false

                    val file = outputFile
                    if (file != null && file.exists()) {
                        val duration = System.currentTimeMillis() - startTime
                        currentOnResult(
                            AudioRecordingResult(
                                filePath = file.absolutePath,
                                durationMs = duration,
                                mimeType = "audio/m4a"
                            )
                        )
                    } else {
                        currentOnResult(null)
                    }
                } catch (e: Exception) {
                    currentOnError(e.message ?: "Failed to stop recording")
                    mediaRecorder?.release()
                    mediaRecorder = null
                    isRecording = false
                }
            },
            onCancel = {
                try {
                    mediaRecorder?.stop()
                } catch (_: Exception) {
                    // Ignore stop errors on cancel
                }
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
                outputFile?.delete()
                outputFile = null
                currentOnResult(null)
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

private fun startRecording(
    context: android.content.Context,
    existingRecorder: MediaRecorder?,
    existingFile: java.io.File?,
    onStarted: (MediaRecorder, java.io.File) -> Unit
) {
    // Clean up existing recorder
    existingRecorder?.release()
    existingFile?.delete()

    // Create output file
    val outputFile = java.io.File(context.cacheDir, "voice_recording_${System.currentTimeMillis()}.m4a")

    // Create and configure MediaRecorder
    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }

    recorder.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(128000)
        setAudioSamplingRate(44100)
        setOutputFile(outputFile.absolutePath)
        prepare()
        start()
    }

    onStarted(recorder, outputFile)
}
