package com.antonchuraev.homesearchchecklist.core.common.api

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of [AttachmentCloudStorage] — Firebase Storage transfer for attachment
 * bytes (the cross-device half of attachment sync).
 *
 * Bucket is pinned EXPLICITLY to the new `*.firebasestorage.app` URL via
 * [FirebaseStorage.getInstance] so it cannot diverge from a possible legacy `*.appspot.com`
 * default left in google-services.json. App Check is enforced on the bucket at the Firebase
 * backend, so an unauthorized call surfaces here as [AppResult.Error].
 *
 * Every op runs on [Dispatchers.IO], never throws, and logs on every failure path (CLAUDE.md:
 * no silent catch).
 */
actual class AttachmentCloudStorage : AttachmentCloudStoragePort {

    private val storage get() = FirebaseStorage.getInstance(BUCKET_URL)

    actual override suspend fun upload(localPath: String, storagePath: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                storage.reference.child(storagePath)
                    .putFile(Uri.fromFile(File(localPath)))
                    .await()
                AppResult.Success(Unit)
            }.getOrElse { e ->
                Log.e(TAG, "upload failed: $localPath -> $storagePath: ${e.message}", e)
                AppResult.Error(Exception(e.message, e))
            }
        }

    actual override suspend fun download(storagePath: String, localPath: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            val target = File(localPath)
            target.parentFile?.mkdirs()
            var tmp: File? = null
            runCatching {
                // Download to a UNIQUE temp sibling, then atomically promote it to [localPath].
                // getFile() streams bytes straight to disk, and this call lives in a Compose
                // LaunchedEffect that is cancelled the moment the thumbnail leaves composition
                // (scroll / swipe / sheet close). A cancelled or failed download must NOT leave a
                // partial file at [localPath]: the materialize probe treats any sizeOf>0 as "Ready",
                // so a truncated file renders as a permanent (and intermittent-looking) broken image.
                // A unique temp name also stops concurrent downloads of the same image (thumbnail +
                // fullscreen) from interleaving bytes into one shared .part.
                val t = File.createTempFile("dl_", ".part", target.parentFile).also { tmp = it }
                storage.reference.child(storagePath)
                    .getFile(t)
                    .await()
                // renameTo is atomic within the same directory. If it loses the promote race (a
                // concurrent download already produced target) or the FS refuses, an existing
                // complete target is still success; otherwise it is a genuine failure.
                if (!t.renameTo(target) && !target.exists()) {
                    error("rename failed: no target at ${target.path}")
                }
                AppResult.Success(Unit)
            }.getOrElse { e ->
                tmp?.delete() // never leave a partial temp behind
                // Cancellation = the thumbnail/viewer left composition, NOT a load failure. Rethrow
                // so structured concurrency unwinds cleanly and we do NOT fire a false
                // attachment_load_failed event + Crashlytics recordException on every scroll-away.
                if (e is CancellationException) throw e
                Log.e(TAG, "download failed: $storagePath -> $localPath: ${e.message}", e)
                AppResult.Error(Exception(e.message, e))
            }
        }

    actual override suspend fun delete(storagePath: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                storage.reference.child(storagePath).delete().await()
                AppResult.Success(Unit)
            }.getOrElse { e ->
                // Idempotent delete: a missing object means it is already gone — not an error.
                if (e is StorageException && e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                    AppResult.Success(Unit)
                } else {
                    Log.e(TAG, "delete failed: $storagePath: ${e.message}", e)
                    AppResult.Error(Exception(e.message, e))
                }
            }
        }

    private companion object {
        const val TAG = "AttachmentCloudStorage"

        // Pin the new *.firebasestorage.app bucket explicitly (do NOT rely on the
        // google-services.json default, which may still carry a legacy *.appspot.com value).
        const val BUCKET_URL = "gs://aichecklists-40230.firebasestorage.app"
    }
}
