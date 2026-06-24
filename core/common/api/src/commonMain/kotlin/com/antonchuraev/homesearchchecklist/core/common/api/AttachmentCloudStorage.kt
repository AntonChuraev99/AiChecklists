package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Platform Firebase Storage client for attachment bytes. Contract: [AttachmentCloudStoragePort].
 *
 * Bound as a Koin singleton (one per platform) to [AttachmentCloudStoragePort] so commonMain
 * callers stay platform-agnostic.
 *
 * Actuals:
 *  - Android — Firebase Storage Android SDK (`putFile`/`getFile`, awaited via
 *    kotlinx-coroutines-play-services). Pins the new `*.firebasestorage.app` bucket explicitly so
 *    it cannot diverge from a legacy `*.appspot.com` default in google-services.json.
 *  - wasmJs  — Firebase JS Storage via init.js `globalThis.__storage*` bridges, reading/writing
 *    bytes through the existing OPFS helpers.
 *  - iOS     — stub (attachments are not released on iOS); every op returns [AppResult.Error].
 */
expect class AttachmentCloudStorage() : AttachmentCloudStoragePort {

    override suspend fun upload(localPath: String, storagePath: String): AppResult<Unit>

    override suspend fun download(storagePath: String, localPath: String): AppResult<Unit>

    override suspend fun delete(storagePath: String): AppResult<Unit>
}
