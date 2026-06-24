package com.antonchuraev.homesearchchecklist.core.common.api

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
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
            runCatching {
                val target = File(localPath)
                target.parentFile?.mkdirs()
                storage.reference.child(storagePath)
                    .getFile(target)
                    .await()
                AppResult.Success(Unit)
            }.getOrElse { e ->
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
