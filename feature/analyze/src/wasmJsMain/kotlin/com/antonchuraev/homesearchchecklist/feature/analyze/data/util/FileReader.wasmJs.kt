@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.feature.analyze.data.util

// ---------------------------------------------------------------------------
// JS bridge — reads from globalThis.__filePickerStaging (set by __pickFile)
// ---------------------------------------------------------------------------

@JsFun("""(path) => {
    try {
        return globalThis.__readStagedBytes(path);
    } catch (e) {
        console.error('[FileReader] __readStagedBytes failed:', e);
        return null;
    }
}""")
private external fun jsReadStagedBytes(path: String): JsAny? // Uint8Array | null

@JsFun("(arr) => arr ? arr.length : 0")
private external fun jsUint8ArrayLength(arr: JsAny): Int

@JsFun("(arr, i) => arr[i] & 0xFF")
private external fun jsUint8ArrayGetByte(arr: JsAny, i: Int): Int

// ---------------------------------------------------------------------------
// actual object
// ---------------------------------------------------------------------------

actual object FileReader {

    /**
     * Reads staged file bytes from the in-memory staging map populated by __pickFile.
     * filePath must be a "wasm-blob://<uuid>" key previously returned by the file picker.
     */
    actual fun readBytes(filePath: String): ByteArray? {
        if (!filePath.startsWith("wasm-blob://")) return null
        val uint8Array = runCatching { jsReadStagedBytes(filePath) }.getOrNull() ?: return null
        val length = jsUint8ArrayLength(uint8Array)
        if (length == 0) return ByteArray(0)
        return ByteArray(length) { i -> jsUint8ArrayGetByte(uint8Array, i).toByte() }
    }

    /**
     * Reads staged file content as UTF-8 string.
     * Suitable for text files picked with FilePickerType.TEXT.
     */
    actual fun readText(filePath: String): String? {
        return readBytes(filePath)?.decodeToString()
    }
}
