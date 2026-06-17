package com.antonchuraev.homesearchchecklist.core.filepicker.api.picker

/**
 * Result of file picking operation.
 */
data class FilePickerResult(
    val filePath: String,
    val fileName: String,
    val mimeType: String?
)

/**
 * Types of files that can be picked.
 */
enum class FilePickerType {
    IMAGE,
    PDF,
    TEXT,
    AUDIO,
}
