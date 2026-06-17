@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// JS bridge — delegates to globalThis bridges defined in init.js.template
// ---------------------------------------------------------------------------

// Returns Promise<{ok, mimeType, error}>
@JsFun("() => { try { return globalThis.__audioRecordingStart(); } catch (e) { return Promise.resolve({ok: false, mimeType: '', error: String(e)}); } }")
private external fun jsAudioStart(): Promise<JsAny?>

// Returns Promise<{ok, path, mimeType, durationMs}>
@JsFun("() => { try { return globalThis.__audioRecordingStop(); } catch (e) { return Promise.resolve({ok: false, path: '', mimeType: '', durationMs: 0}); } }")
private external fun jsAudioStop(): Promise<JsAny?>

// Synchronous cancel — returns Boolean
@JsFun("() => { try { return globalThis.__audioRecordingCancel(); } catch (e) { return false; } }")
private external fun jsAudioCancel(): Boolean

// Result object field accessors
@JsFun("(obj) => (obj != null && obj.ok === true)")
private external fun jsResultOk(obj: JsAny): Boolean

@JsFun("(obj) => (obj && obj.mimeType) ? String(obj.mimeType) : ''")
private external fun jsResultMimeType(obj: JsAny): String

@JsFun("(obj) => (obj && obj.error) ? String(obj.error) : ''")
private external fun jsResultError(obj: JsAny): String

@JsFun("(obj) => (obj && obj.path) ? String(obj.path) : ''")
private external fun jsResultPath(obj: JsAny): String

@JsFun("(obj) => (obj && obj.durationMs) ? (obj.durationMs | 0) : 0")
private external fun jsResultDurationMs(obj: JsAny): Int

// ---------------------------------------------------------------------------
// actual composable
// ---------------------------------------------------------------------------

@Composable
actual fun rememberAudioRecorderLauncher(
    onResult: (AudioRecordingResult?) -> Unit,
    onError: (String) -> Unit,
): AudioRecorderLauncher {
    val scope = rememberCoroutineScope()
    val isRecordingState = remember { mutableStateOf(false) }

    return remember {
        AudioRecorderLauncher(
            onStart = {
                scope.launch {
                    val resultObj = runCatching {
                        jsAudioStart().await<JsAny?>()
                    }.getOrNull()

                    if (resultObj == null || !jsResultOk(resultObj)) {
                        val errorMsg = resultObj?.let { jsResultError(it) }.orEmpty()
                        isRecordingState.value = false
                        onError(errorMsg.ifEmpty { "Failed to start audio recording" })
                    } else {
                        isRecordingState.value = true
                    }
                }
            },
            onStop = {
                scope.launch {
                    isRecordingState.value = false
                    val resultObj = runCatching {
                        jsAudioStop().await<JsAny?>()
                    }.getOrNull()

                    if (resultObj != null && jsResultOk(resultObj)) {
                        val path = jsResultPath(resultObj)
                        val durationMs = jsResultDurationMs(resultObj).toLong()
                        val mimeType = jsResultMimeType(resultObj).ifEmpty { "audio/webm" }
                        if (path.isNotEmpty()) {
                            onResult(AudioRecordingResult(path, durationMs, mimeType))
                        } else {
                            onResult(null)
                        }
                    } else {
                        onResult(null)
                    }
                }
            },
            onCancel = {
                isRecordingState.value = false
                runCatching { jsAudioCancel() }
                onResult(null)
            },
            isRecording = { isRecordingState.value },
        )
    }
}
