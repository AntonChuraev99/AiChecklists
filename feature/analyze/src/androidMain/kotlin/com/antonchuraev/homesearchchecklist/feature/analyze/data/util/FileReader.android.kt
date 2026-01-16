package com.antonchuraev.homesearchchecklist.feature.analyze.data.util

import java.io.File

actual object FileReader {
    actual fun readBytes(filePath: String): ByteArray? {
        return try {
            File(filePath).readBytes()
        } catch (e: Exception) {
            null
        }
    }

    actual fun readText(filePath: String): String? {
        return try {
            File(filePath).readText()
        } catch (e: Exception) {
            null
        }
    }
}
