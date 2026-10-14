@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.AsyncStartRecorderMachine.CancelAction
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.AsyncStartRecorderMachine.SettleAction
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.AsyncStartRecorderMachine.StartOutcome
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.AsyncStartRecorderMachine.StopAction
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// JS bridge — delegates to globalThis bridges defined in init.js.template
// ---------------------------------------------------------------------------

// Returns Promise<{ok, mimeType, error, errorName, permissionBefore, permissionAfter, elapsedMs}>
@JsFun("() => { try { return globalThis.__audioRecordingStart(); } catch (e) { return Promise.resolve({ok: false, mimeType: '', error: String(e), errorName: (e && e.name) ? String(e.name) : '', permissionBefore: 'unknown', permissionAfter: 'unknown', elapsedMs: 0}); } }")
private external fun jsAudioStart(): Promise<JsAny?>

// Returns Promise<{ok, path, mimeType, durationMs, tooShort, error}>
@JsFun("() => { try { return globalThis.__audioRecordingStop(); } catch (e) { return Promise.resolve({ok: false, path: '', mimeType: '', durationMs: 0, tooShort: false, error: String(e)}); } }")
private external fun jsAudioStop(): Promise<JsAny?>

// Synchronous cancel — returns Boolean. Also aborts a start still waiting on getUserMedia.
@JsFun("() => { try { return globalThis.__audioRecordingCancel(); } catch (e) { return false; } }")
private external fun jsAudioCancel(): Boolean

// Result object field accessors
@JsFun("(obj) => (obj != null && obj.ok === true)")
private external fun jsResultOk(obj: JsAny): Boolean

@JsFun("(obj) => (obj && obj.mimeType) ? String(obj.mimeType) : ''")
private external fun jsResultMimeType(obj: JsAny): String

@JsFun("(obj) => (obj && obj.error) ? String(obj.error) : ''")
private external fun jsResultError(obj: JsAny): String

@JsFun("(obj) => (obj && obj.errorName) ? String(obj.errorName) : ''")
private external fun jsResultErrorName(obj: JsAny): String

@JsFun("(obj) => (obj && obj.permissionBefore) ? String(obj.permissionBefore) : 'unknown'")
private external fun jsResultPermissionBefore(obj: JsAny): String

@JsFun("(obj) => (obj && obj.permissionAfter) ? String(obj.permissionAfter) : 'unknown'")
private external fun jsResultPermissionAfter(obj: JsAny): String

@JsFun("(obj) => (obj && obj.elapsedMs) ? (obj.elapsedMs | 0) : 0")
private external fun jsResultElapsedMs(obj: JsAny): Int

@JsFun("(obj) => (obj != null && obj.tooShort === true)")
private external fun jsResultTooShort(obj: JsAny): Boolean

@JsFun("(obj) => (obj && obj.path) ? String(obj.path) : ''")
private external fun jsResultPath(obj: JsAny): String

@JsFun("(obj) => (obj && obj.durationMs) ? (obj.durationMs | 0) : 0")
private external fun jsResultDurationMs(obj: JsAny): Int

@JsFun("(msg) => { console.error('[AudioRecorder] ' + msg); }")
private external fun jsConsoleError(msg: String)

// ---------------------------------------------------------------------------
// actual composable
// ---------------------------------------------------------------------------

/**
 * Browser recorder. `start()` is asynchronous: `getUserMedia` may wait behind the permission
 * prompt, so the press lifecycle runs through [AsyncStartRecorderMachine] — the same contract as
 * the Android recorder (a press released during the prompt records nothing and, once the prompt
 * is answered, reports a code from [AudioRecorderErrorCodes]).
 */
@Composable
actual fun rememberAudioRecorderLauncher(
    onResult: (AudioRecordingResult?) -> Unit,
    onError: (String) -> Unit,
): AudioRecorderLauncher {
    val scope = rememberCoroutineScope()
    val isRecordingState = remember { mutableStateOf(false) }
    val machine = remember { AsyncStartRecorderMachine() }

    // The launcher below is remembered once; callbacks must not be frozen at their first value
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)

    DisposableEffect(Unit) {
        onDispose {
            if (machine.phase != AsyncStartRecorderMachine.Phase.IDLE) {
                // Leaving mid-press: nobody is left to receive anything. A pending start is
                // aborted on the JS side (its stream is released when it arrives).
                runCatching { jsAudioCancel() }
            }
            machine.onDispose()
            isRecordingState.value = false
        }
    }

    return remember {
        AudioRecorderLauncher(
            onStart = {
                if (machine.onStart()) {
                    scope.launch {
                        val outcome = try {
                            readStartOutcome(jsAudioStart().await<JsAny?>())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            StartOutcome.Failed("${e::class.simpleName}: ${e.message ?: "no message"}")
                        }
                        when (val action = machine.onStartSettled(outcome)) {
                            SettleAction.Ignore -> Unit
                            SettleAction.KeepRecording -> isRecordingState.value = true
                            is SettleAction.Release -> {
                                // The press is over: discard what the late start just began
                                runCatching { jsAudioCancel() }
                                isRecordingState.value = false
                                val code = action.errorCode
                                if (code != null) currentOnError(code) else currentOnResult(null)
                            }
                            is SettleAction.Fail -> {
                                isRecordingState.value = false
                                if (outcome is StartOutcome.Failed) jsConsoleError("start failed: ${outcome.message}")
                                currentOnError(action.errorCode)
                            }
                        }
                    }
                }
            },
            onStop = {
                when (machine.onStop()) {
                    // Released before the start settled (permission prompt or a very short press):
                    // the single callback for this press comes when the start settles
                    StopAction.DeferUntilStartSettles -> Unit
                    StopAction.ReportNothingRecorded -> currentOnResult(null)
                    StopAction.StopRecording -> {
                        isRecordingState.value = false
                        scope.launch { deliverStopResult(currentOnResult, currentOnError) }
                    }
                }
            },
            onCancel = {
                when (machine.onCancel()) {
                    CancelAction.DeferUntilStartSettles -> Unit
                    CancelAction.ReportNothingRecorded -> currentOnResult(null)
                    CancelAction.DiscardRecording -> {
                        isRecordingState.value = false
                        runCatching { jsAudioCancel() }
                        currentOnResult(null)
                    }
                }
            },
            isRecording = { isRecordingState.value },
        )
    }
}

private fun readStartOutcome(resultObj: JsAny?): StartOutcome {
    if (resultObj == null) return StartOutcome.Failed("start returned no result")
    return if (jsResultOk(resultObj)) {
        StartOutcome.Started(
            promptShown = AsyncStartRecorderMachine.promptWasShown(
                permissionBefore = jsResultPermissionBefore(resultObj),
                elapsedMs = jsResultElapsedMs(resultObj),
            )
        )
    } else {
        AsyncStartRecorderMachine.failureOutcome(
            errorName = jsResultErrorName(resultObj),
            message = jsResultError(resultObj),
            permissionAfter = jsResultPermissionAfter(resultObj),
        )
    }
}

private suspend fun deliverStopResult(
    onResult: (AudioRecordingResult?) -> Unit,
    onError: (String) -> Unit,
) {
    val resultObj = try {
        jsAudioStop().await<JsAny?>()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        onError("stop failed: ${e::class.simpleName}: ${e.message ?: "no message"}")
        return
    }
    when {
        resultObj == null -> onError("stop returned no result")
        jsResultOk(resultObj) -> {
            val path = jsResultPath(resultObj)
            if (path.isEmpty()) {
                onError("stop returned an empty path")
            } else {
                onResult(
                    AudioRecordingResult(
                        filePath = path,
                        durationMs = jsResultDurationMs(resultObj).toLong(),
                        mimeType = jsResultMimeType(resultObj).ifEmpty { "audio/webm" },
                    )
                )
            }
        }
        // Stopped before the recorder produced any data — same as Android's throwing stop()
        jsResultTooShort(resultObj) -> onError(AudioRecorderErrorCodes.RECORDING_TOO_SHORT)
        else -> onError("stop failed: ${jsResultError(resultObj).ifEmpty { "no message" }}")
    }
}
