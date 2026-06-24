package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Cloud (Firebase Storage) transfer for attachment bytes — the cross-device half of attachment
 * sync. The LOCAL bytes live behind [AttachmentStoragePort]; this port moves those bytes to/from a
 * shared cloud bucket so another device of the same signed-in user can materialize them.
 *
 * Why a separate port from [AttachmentStoragePort]: local persistence and cloud transfer are
 * different concerns with different failure modes, and keeping them apart lets commonTest fake
 * cloud transfer without touching local storage.
 *
 * Path contract:
 *  - [localPath]   — platform-local handle, SAME format as
 *                    [com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment.path]
 *                    (Android: absolute filesDir path; wasmJs: "opfs://…").
 *  - [storagePath] — bucket-relative object key, SAME value as
 *                    [com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment.storagePath]
 *                    (e.g. users/<uid>/attachments/<fillId>/<itemId>/<attachmentId>.<ext>),
 *                    byte-identical across platforms. Build it via [AttachmentCloudPaths.forAttachment].
 *
 * All ops are suspend and return [AppResult] — they MUST NOT throw and MUST NOT silently swallow
 * errors (log on every failure path). App Check is enforced on the bucket at the Firebase backend
 * (NOT via Security Rules), so an unauthorized call surfaces here as [AppResult.Error].
 */
interface AttachmentCloudStoragePort {

    /** Upload the local bytes at [localPath] to cloud object [storagePath]. Overwrites if present. */
    suspend fun upload(localPath: String, storagePath: String): AppResult<Unit>

    /**
     * Download cloud object [storagePath] and write its bytes to [localPath] (the local cache the
     * image loaders read). Succeeds with [AppResult.Success] once the local file is written;
     * [AppResult.Error] if the object is missing or the transfer fails.
     */
    suspend fun download(storagePath: String, localPath: String): AppResult<Unit>

    /** Delete cloud object [storagePath]. A missing object is NOT an error (idempotent delete). */
    suspend fun delete(storagePath: String): AppResult<Unit>
}

/**
 * Builds bucket-relative Storage object keys so Android and web produce byte-identical paths for
 * the same attachment — a mismatch would strand the bytes on the uploading platform.
 *
 * Layout: `users/<uid>/attachments/<fillId>/<itemId>/<attachmentId>.<ext>` — the `users/<uid>`
 * prefix is what the Security Rules pin read/write to the owner.
 */
object AttachmentCloudPaths {
    fun forAttachment(
        uid: String,
        fillId: Long,
        itemId: String,
        attachmentId: String,
        fileName: String,
    ): String {
        val ext = fileName.substringAfterLast('.', "").ifBlank { "bin" }.lowercase()
        return "users/$uid/attachments/$fillId/$itemId/$attachmentId.$ext"
    }
}
