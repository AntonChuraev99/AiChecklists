package com.antonchuraev.homesearchchecklist.feature.aichat.impl.data

/**
 * Reads the raw bytes of a recorded audio file by absolute path.
 *
 * Only [androidMain] is wired today — voice recording on iOS / wasmJs is deferred
 * (AudioRecorder stubs throw `UnsupportedOperationException`). Stub actuals return
 * `null` so the transcription path degrades gracefully if it is ever reached on
 * those platforms.
 */
internal expect object AudioFileBytes {
    fun read(path: String): ByteArray?

    /** Deletes the file at [path] (best-effort). Returns true on success. */
    fun delete(path: String): Boolean
}
