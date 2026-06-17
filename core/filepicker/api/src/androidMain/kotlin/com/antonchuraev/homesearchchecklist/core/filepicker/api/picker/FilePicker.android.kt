package com.antonchuraev.homesearchchecklist.core.filepicker.api.picker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android file picker actual.
 *
 * - [FilePickerType.IMAGE] uses the **Modern Photo Picker** API
 *   ([ActivityResultContracts.PickVisualMedia]) — Android 13+ system-managed picker,
 *   automatic backport on Android 11+ via Play services. No `READ_MEDIA_IMAGES`
 *   permission required; the picker returns a session-scoped content URI.
 * - [FilePickerType.PDF] / [FilePickerType.TEXT] / [FilePickerType.AUDIO] use
 *   [ActivityResultContracts.OpenDocument] (SAF) since PickVisualMedia is
 *   image/video-only.
 */
@Composable
actual fun rememberFilePickerLauncher(
    type: FilePickerType,
    onResult: (FilePickerResult?) -> Unit
): FilePickerLauncher {
    val context = LocalContext.current

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onResult(getFileDetails(context, uri, FilePickerType.IMAGE))
        } else {
            onResult(null)
        }
    }

    val mimeTypes = when (type) {
        FilePickerType.IMAGE -> arrayOf("image/*") // not used by photo picker; kept for OpenDocument fallback
        FilePickerType.PDF -> arrayOf("application/pdf")
        FilePickerType.TEXT -> arrayOf("text/plain", "text/*")
        FilePickerType.AUDIO -> arrayOf("audio/*")
    }

    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onResult(getFileDetails(context, uri, type))
        } else {
            onResult(null)
        }
    }

    return remember(type) {
        FilePickerLauncher {
            when (type) {
                FilePickerType.IMAGE -> photoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
                FilePickerType.PDF,
                FilePickerType.TEXT,
                FilePickerType.AUDIO -> docLauncher.launch(mimeTypes)
            }
        }
    }
}

private fun getFileDetails(context: Context, uri: Uri, type: FilePickerType): FilePickerResult {
    var fileName = "unknown"
    var mimeType: String? = null

    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            fileName = cursor.getString(nameIndex) ?: "unknown"
        }
    }

    mimeType = context.contentResolver.getType(uri)

    // Copy content:// URI to a temp file so callers can read it via File API
    val tempFile = copyUriToTempFile(context, uri, fileName)
    val filePath = tempFile?.absolutePath ?: uri.toString()

    return FilePickerResult(
        filePath = filePath,
        fileName = fileName,
        mimeType = mimeType
    )
}

private fun copyUriToTempFile(context: Context, uri: Uri, fileName: String): java.io.File? {
    return try {
        val extension = fileName.substringAfterLast('.', "tmp")
        val tempFile = java.io.File.createTempFile("analyze_", ".$extension", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    } catch (e: Exception) {
        null
    }
}
