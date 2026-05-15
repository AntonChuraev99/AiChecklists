package com.antonchuraev.homesearchchecklist.core.common.api

import android.graphics.BitmapFactory
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of [AttachmentStorage].
 *
 * Storage layout: <filesDir>/attachments/<fillId>/<itemId>/<attachmentId>.<ext>
 *
 * [sourcePath] accepts two forms:
 *   - content:// URI string — resolved via ContentResolver.openInputStream()
 *   - Absolute filesystem path — opened directly as File (e.g. cacheDir copy from FilePicker)
 *
 * Phase 4 ViewModel usage:
 *   val path = storeAttachment(sourcePath, fillId, itemId, attachmentId, fileName) ?: return
 *   val (w, h) = probeImage(path, mimeType)
 *   val attachment = Attachment(id = attachmentId, path = path, ..., width = w, height = h)
 */
actual class AttachmentStorage : AttachmentStoragePort {

    private val context get() = AppContextHolder.context

    private companion object {
        const val TAG = "AttachmentStorage"
    }

    actual override suspend fun storeAttachment(
        sourcePath: String,
        fillId: Long,
        itemId: String,
        attachmentId: String,
        originalFileName: String,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val sourceUri = sourcePath.toUri()

            // Derive extension from the filename; fall back to MIME lookup; fall back to "bin".
            val ext = originalFileName.substringAfterLast('.', "").ifBlank {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(
                    context.contentResolver.getType(sourceUri).orEmpty()
                ) ?: "bin"
            }

            val targetDir = File(context.filesDir, "attachments/$fillId/$itemId").apply { mkdirs() }
            val targetFile = File(targetDir, "$attachmentId.$ext")

            // Support both content:// URIs (system picker) and plain filesystem paths (cacheDir copy).
            val inputStream = if (sourceUri.scheme == "content") {
                context.contentResolver.openInputStream(sourceUri)
            } else {
                File(sourcePath).takeIf { it.exists() }?.inputStream()
            } ?: error("Cannot open input stream for $sourcePath")

            inputStream.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }

            targetFile.absolutePath
        }.onFailure { e ->
            Log.e(TAG, "storeAttachment failed: $sourcePath -> $fillId/$itemId/$attachmentId", e)
        }.getOrNull()
    }

    actual override suspend fun deleteAttachment(path: String) = withContext(Dispatchers.IO) {
        runCatching {
            File(path).takeIf { it.exists() }?.delete()
        }.onFailure { e ->
            Log.e(TAG, "deleteAttachment failed: $path", e)
        }
        Unit
    }

    actual override suspend fun deleteAttachmentsFor(fillId: Long, itemId: String) = withContext(Dispatchers.IO) {
        runCatching {
            File(context.filesDir, "attachments/$fillId/$itemId").deleteRecursively()
        }.onFailure { e ->
            Log.e(TAG, "deleteAttachmentsFor failed: fillId=$fillId itemId=$itemId", e)
        }
        Unit
    }

    actual override suspend fun deleteAttachmentsForFill(fillId: Long) = withContext(Dispatchers.IO) {
        runCatching {
            File(context.filesDir, "attachments/$fillId").deleteRecursively()
        }.onFailure { e ->
            Log.e(TAG, "deleteAttachmentsForFill failed: fillId=$fillId", e)
        }
        Unit
    }

    /**
     * Reads pixel dimensions from an already-stored file using header-only decode (no full bitmap
     * load into memory). Returns (null, null) for non-image MIME types or on any decode error.
     */
    actual override suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?> =
        withContext(Dispatchers.IO) {
            if (mimeType?.startsWith("image/") != true) return@withContext null to null
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, opts)
                val w = opts.outWidth.takeIf { it > 0 }
                val h = opts.outHeight.takeIf { it > 0 }
                w to h
            }.getOrDefault(null to null)
        }

    actual override suspend fun sizeOf(path: String): Long = withContext(Dispatchers.IO) {
        runCatching { File(path).length() }.getOrDefault(0L)
    }
}
