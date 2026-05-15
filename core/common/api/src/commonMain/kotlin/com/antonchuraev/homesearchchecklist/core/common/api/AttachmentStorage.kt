package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Platform-specific storage operations for item attachments.
 *
 * Call sites MUST check [PlatformCapabilities.attachmentsSupported] before using this class.
 * On platforms where it is false the actual implementations are stubs that throw or return null.
 *
 * Lifecycle:
 *   - Instantiated as a Koin singleton scoped to the application (Phase 2 DI wiring).
 *   - All operations are suspend — implementations may do I/O on [kotlinx.coroutines.Dispatchers.IO].
 *
 * Path contract:
 *   Android  — <filesDir>/attachments/<fillId>/<itemId>/<attachmentId>.<ext>
 *   iOS      — <documentDir>/attachments/... (Phase 5)
 *   wasmJs   — unsupported v1; stubs return null / no-op
 *
 * sourcePath semantic (Android):
 *   Accepts either a content:// URI string (from system picker, resolved via ContentResolver)
 *   or an absolute filesystem path (e.g. from cacheDir after FilePicker copy). Both forms
 *   are handled transparently in the Android actual implementation.
 */
expect class AttachmentStorage : AttachmentStoragePort {

    /**
     * Copies a file from the picker-provided [sourcePath] into durable storage scoped to
     * ([fillId], [itemId]). Returns the persistent absolute path to be stored as
     * [com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment.path],
     * or null if the operation failed. Implementations MUST log on failure —
     * no silent null without a log entry.
     */
    override suspend fun storeAttachment(
        sourcePath: String,
        fillId: Long,
        itemId: String,
        attachmentId: String,
        originalFileName: String,
    ): String?

    /**
     * Deletes the single attachment file at [path]. No-op if the file no longer exists.
     */
    override suspend fun deleteAttachment(path: String)

    /**
     * Deletes all attachment files under the ([fillId], [itemId]) scope.
     * Called when an item is removed from a fill.
     */
    override suspend fun deleteAttachmentsFor(fillId: Long, itemId: String)

    /**
     * Deletes all attachment files under [fillId].
     * Called when an entire fill (or its parent checklist) is deleted.
     */
    override suspend fun deleteAttachmentsForFill(fillId: Long)

    /**
     * Reads the pixel dimensions of an already-stored image file without loading the full bitmap.
     * Returns (width, height) for image MIME types, or (null, null) for non-images or on error.
     *
     * Phase 4 ViewModel contract:
     *   1. Call [storeAttachment] → get absolutePath
     *   2. Call [probeImage](absolutePath, mimeType) → get (w, h)
     *   3. Build the [Attachment] domain object with all fields populated
     *
     * iOS / wasmJs actuals return (null, null) unconditionally.
     */
    override suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?>

    /**
     * Returns the byte size of the stored file at [path], or 0 if the file does not exist
     * or the operation fails.
     *
     * Kept in AttachmentStorage (not inlined in ViewModel) so commonMain code never
     * needs to import java.io.File directly.
     *
     * iOS returns 0 unconditionally (attachments unsupported in Phase 5).
     * wasmJs returns 0 unconditionally (OPFS attachments deferred).
     */
    override suspend fun sizeOf(path: String): Long
}
