@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.feature.aichat.impl.data

// ---------------------------------------------------------------------------
// JS bridge — reads/deletes from globalThis.__filePickerStaging (init.js.template)
// Staging keys:
//   "wasm-audio://<uuid>" — produced by __audioRecordingStop
//   "wasm-blob://<uuid>"  — produced by __pickFile (file picker, unused here but accepted)
// ---------------------------------------------------------------------------

@JsFun("""(path) => {
    try {
        return globalThis.__readStagedBytes(path);
    } catch (e) {
        console.error('[AudioFileBytes] __readStagedBytes failed:', e);
        return null;
    }
}""")
private external fun jsReadStagedBytes(path: String): JsAny? // Uint8Array | null

@JsFun("(arr) => arr ? arr.length : 0")
private external fun jsUint8ArrayLength(arr: JsAny): Int

@JsFun("(arr, i) => arr[i] & 0xFF")
private external fun jsUint8ArrayGetByte(arr: JsAny, i: Int): Int

@JsFun("""(path) => {
    try {
        return globalThis.__deleteStagedBytes(path);
    } catch (e) {
        console.error('[AudioFileBytes] __deleteStagedBytes failed:', e);
        return false;
    }
}""")
private external fun jsDeleteStagedBytes(path: String): Boolean

// ---------------------------------------------------------------------------
// actual object
// ---------------------------------------------------------------------------

internal actual object AudioFileBytes {

    /**
     * Reads staged audio bytes from the in-memory staging map populated by
     * globalThis.__audioRecordingStop (mic recording) or globalThis.__pickFile (file picker).
     *
     * Accepts "wasm-audio://<uuid>" keys (mic) and "wasm-blob://<uuid>" keys (file picker).
     */
    actual fun read(path: String): ByteArray? {
        if (!path.startsWith("wasm-audio://") && !path.startsWith("wasm-blob://")) return null
        val uint8 = runCatching { jsReadStagedBytes(path) }.getOrNull() ?: return null
        val length = jsUint8ArrayLength(uint8)
        if (length == 0) return ByteArray(0)
        return ByteArray(length) { i -> jsUint8ArrayGetByte(uint8, i).toByte() }
    }

    /**
     * Removes the staged bytes from the in-memory map to free memory.
     * Should be called after the bytes have been read and sent to the server.
     */
    actual fun delete(path: String): Boolean = runCatching {
        jsDeleteStagedBytes(path)
    }.getOrElse { false }
}
