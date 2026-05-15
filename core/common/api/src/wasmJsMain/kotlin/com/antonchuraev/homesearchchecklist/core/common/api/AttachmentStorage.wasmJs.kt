package com.antonchuraev.homesearchchecklist.core.common.api

// Stubbed in Phase 1; web attachments are unsupported in v1 (OPFS file API deferred).
// PlatformCapabilities.attachmentsSupported = false on wasmJs — call sites must gate on it.
// Pending: docs/todos/ (wasmJs attachments v2)
actual class AttachmentStorage : AttachmentStoragePort {

    actual override suspend fun storeAttachment(
        sourcePath: String,
        fillId: Long,
        itemId: String,
        attachmentId: String,
        originalFileName: String,
    ): String? = null // unsupported; gate on PlatformCapabilities.attachmentsSupported

    actual override suspend fun deleteAttachment(path: String) {
        // no-op on wasmJs
    }

    actual override suspend fun deleteAttachmentsFor(fillId: Long, itemId: String) {
        // no-op on wasmJs
    }

    actual override suspend fun deleteAttachmentsForFill(fillId: Long) {
        // no-op on wasmJs
    }

    /** wasmJs: OPFS-backed image probing deferred to v2. */
    actual override suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?> = null to null

    /** wasmJs: OPFS attachments deferred to v2, return 0. */
    actual override suspend fun sizeOf(path: String): Long = 0L
}
