package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Interface contract for attachment storage operations.
 *
 * Decouples the ViewModel from the platform-specific [AttachmentStorage] expect class,
 * enabling in-memory fakes in commonTest without expect/actual gymnastics.
 *
 * Production wiring: Koin binds [AttachmentStorage] (which implements this interface) to
 * [AttachmentStoragePort] on every platform. Test wiring: [FakeAttachmentStorage] implements
 * the interface directly in commonTest.
 */
interface AttachmentStoragePort {
    suspend fun storeAttachment(
        sourcePath: String,
        fillId: Long,
        itemId: String,
        attachmentId: String,
        originalFileName: String,
    ): String?

    suspend fun deleteAttachment(path: String)
    suspend fun deleteAttachmentsFor(fillId: Long, itemId: String)
    suspend fun deleteAttachmentsForFill(fillId: Long)
    suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?>
    suspend fun sizeOf(path: String): Long
}
