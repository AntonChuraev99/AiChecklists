package com.antonchuraev.homesearchchecklist.feature.analyze.data.util

/**
 * Web stub for FileReader. File system access is not available on the web target.
 * Files are handled via the browser file picker (FilePickerResult) instead.
 */
actual object FileReader {
    actual fun readBytes(filePath: String): ByteArray? = null
    actual fun readText(filePath: String): String? = null
}
