package com.antonchuraev.homesearchchecklist.core.common.api

// Stubbed in Phase 1; real implementation deferred until iOS publish phase (Phase 5).
// PlatformCapabilities.attachmentsSupported = false on iOS — call sites must gate on it.
actual class AttachmentStorage : AttachmentStoragePort {

    actual override suspend fun storeAttachment(
        sourcePath: String,
        fillId: Long,
        itemId: String,
        attachmentId: String,
        originalFileName: String,
    ): String? = throw NotImplementedError("AttachmentStorage.storeAttachment — iOS Phase 5")

    actual override suspend fun deleteAttachment(path: String): Unit =
        throw NotImplementedError("AttachmentStorage.deleteAttachment — iOS Phase 5")

    actual override suspend fun deleteAttachmentsFor(fillId: Long, itemId: String): Unit =
        throw NotImplementedError("AttachmentStorage.deleteAttachmentsFor — iOS Phase 5")

    actual override suspend fun deleteAttachmentsForFill(fillId: Long): Unit =
        throw NotImplementedError("AttachmentStorage.deleteAttachmentsForFill — iOS Phase 5")

    /** iOS Phase 5: return (null, null) until native image probing is implemented. */
    actual override suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?> = null to null

    /** iOS Phase 5: attachments unsupported, return 0. */
    actual override suspend fun sizeOf(path: String): Long = 0L
}
