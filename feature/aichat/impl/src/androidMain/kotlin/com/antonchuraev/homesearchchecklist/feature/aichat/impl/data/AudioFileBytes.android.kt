package com.antonchuraev.homesearchchecklist.feature.aichat.impl.data

import java.io.File

internal actual object AudioFileBytes {
    actual fun read(path: String): ByteArray? = runCatching {
        File(path).readBytes()
    }.getOrNull()

    actual fun delete(path: String): Boolean = runCatching {
        File(path).delete()
    }.getOrElse { false }
}
