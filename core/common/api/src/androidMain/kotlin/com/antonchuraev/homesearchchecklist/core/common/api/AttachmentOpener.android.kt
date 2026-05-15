package com.antonchuraev.homesearchchecklist.core.common.api

import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class AttachmentOpener {

    private companion object {
        const val TAG = "AttachmentOpener"
    }

    actual suspend fun openExternally(path: String, mimeType: String?): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val context = AppContextHolder.context
                val file = File(path)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val resolvedMime = mimeType
                    ?: context.contentResolver.getType(uri)
                    ?: "application/octet-stream"

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, resolvedMime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }.onFailure { e ->
                Log.e(TAG, "openExternally failed: path=$path mimeType=$mimeType", e)
            }.getOrDefault(false)
        }
}
