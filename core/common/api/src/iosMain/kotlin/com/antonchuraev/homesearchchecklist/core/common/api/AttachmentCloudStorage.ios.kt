package com.antonchuraev.homesearchchecklist.core.common.api

// iOS attachments are not released (PlatformCapabilities.attachmentsSupported = false), so cloud
// transfer is a stub. Ops return AppResult.Error rather than succeeding silently, so any accidental
// call on iOS is visible instead of masquerading as a successful upload/download.
actual class AttachmentCloudStorage : AttachmentCloudStoragePort {

    actual override suspend fun upload(localPath: String, storagePath: String): AppResult<Unit> =
        AppResult.Error(UnsupportedOperationException("AttachmentCloudStorage.upload — iOS not released"))

    actual override suspend fun download(storagePath: String, localPath: String): AppResult<Unit> =
        AppResult.Error(UnsupportedOperationException("AttachmentCloudStorage.download — iOS not released"))

    actual override suspend fun delete(storagePath: String): AppResult<Unit> =
        AppResult.Error(UnsupportedOperationException("AttachmentCloudStorage.delete — iOS not released"))
}
