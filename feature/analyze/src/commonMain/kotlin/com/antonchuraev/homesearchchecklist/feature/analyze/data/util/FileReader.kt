package com.antonchuraev.homesearchchecklist.feature.analyze.data.util

/**
 * Platform-specific file reading utilities.
 */
expect object FileReader {
    /**
     * Reads file content as ByteArray.
     * @param filePath absolute path to the file
     * @return file content as bytes or null if failed
     */
    fun readBytes(filePath: String): ByteArray?

    /**
     * Reads file content as String.
     * @param filePath absolute path to the file
     * @return file content as string or null if failed
     */
    fun readText(filePath: String): String?
}
