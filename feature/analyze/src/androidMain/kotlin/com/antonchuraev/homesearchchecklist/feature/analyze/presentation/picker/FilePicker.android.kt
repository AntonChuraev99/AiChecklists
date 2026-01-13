package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.picker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFilePickerLauncher(
    type: FilePickerType,
    onResult: (FilePickerResult?) -> Unit
): FilePickerLauncher {
    val context = LocalContext.current

    val mimeTypes = when (type) {
        FilePickerType.IMAGE -> arrayOf("image/*")
        FilePickerType.PDF -> arrayOf("application/pdf")
        FilePickerType.TEXT -> arrayOf("text/plain", "text/*")
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val result = getFileDetails(context, uri, type)
            onResult(result)
        } else {
            onResult(null)
        }
    }

    return remember(type) {
        FilePickerLauncher {
            launcher.launch(mimeTypes)
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

    return FilePickerResult(
        filePath = uri.toString(),
        fileName = fileName,
        mimeType = mimeType
    )
}
