package com.antonchuraev.homesearchchecklist.feature.aichat.impl.data

internal actual object AudioFileBytes {
    /**
     * iOS voice recording is deferred — AudioRecorder.ios throws UnsupportedOperationException
     * before any path can reach this reader. Stub returns null defensively.
     */
    actual fun read(path: String): ByteArray? = null

    actual fun delete(path: String): Boolean = false
}
