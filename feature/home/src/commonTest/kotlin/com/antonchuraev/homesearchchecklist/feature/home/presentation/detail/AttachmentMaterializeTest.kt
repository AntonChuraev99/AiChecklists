package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentCloudStoragePort
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coverage for [ensureAttachmentLocal] (cross-device sync, Phase 4) — the lazy-download helper that
 * makes attachment bytes present locally at [Attachment.path] before the image loaders read them.
 *
 * Decision matrix (local present? × storagePath set? × download result):
 *  - local present                  → true, NO download (bytes already here)
 *  - local missing, no storagePath  → true, NO download (nothing to fetch; loader tries path as-is)
 *  - local missing, storagePath set, download Success → true, download(storagePath, path) called
 *  - local missing, storagePath set, download Error   → false
 */
class AttachmentMaterializeTest {

    private fun attachment(path: String = "/local/photo.jpg", storagePath: String? = null) =
        Attachment(
            id = "att1",
            path = path,
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 1_024L,
            createdAt = 0L,
            storagePath = storagePath,
        )

    @Test
    fun ensureAttachmentLocal_localFileExists_returnsTrue_noDownload() = runTest {
        val local = FakeLocalStorage(sizeByPath = mapOf("/local/photo.jpg" to 1_024L))
        val cloud = FakeCloudStorage()

        val result = ensureAttachmentLocal(
            attachment = attachment(path = "/local/photo.jpg", storagePath = "users/u/att.jpg"),
            local = local,
            cloud = cloud,
        )

        assertTrue(result, "a present local file resolves to Ready without any cloud fetch")
        assertTrue(cloud.downloadCalls.isEmpty(), "must NOT download when the local file already exists")
    }

    @Test
    fun ensureAttachmentLocal_missingLocal_noStoragePath_returnsTrue_noDownload() = runTest {
        val local = FakeLocalStorage(sizeByPath = emptyMap()) // sizeOf == 0
        val cloud = FakeCloudStorage()

        val result = ensureAttachmentLocal(
            attachment = attachment(path = "/local/photo.jpg", storagePath = null),
            local = local,
            cloud = cloud,
        )

        assertTrue(result, "no local copy and no cloud anchor → true (loader falls back to path as-is)")
        assertTrue(cloud.downloadCalls.isEmpty(), "no storagePath means there is nothing to download")
    }

    @Test
    fun ensureAttachmentLocal_missingLocal_withStoragePath_downloadSuccess_returnsTrue() = runTest {
        val local = FakeLocalStorage(sizeByPath = emptyMap()) // sizeOf == 0
        val cloud = FakeCloudStorage(downloadResult = AppResult.Success(Unit))

        val result = ensureAttachmentLocal(
            attachment = attachment(path = "/local/photo.jpg", storagePath = "users/u/att.jpg"),
            local = local,
            cloud = cloud,
        )

        assertTrue(result, "a successful cloud download materializes the bytes locally → true")
        assertEquals(
            listOf("users/u/att.jpg" to "/local/photo.jpg"),
            cloud.downloadCalls,
            "download must be called with (storagePath, local path) in that order",
        )
    }

    @Test
    fun ensureAttachmentLocal_missingLocal_withStoragePath_downloadFails_returnsFalse() = runTest {
        val local = FakeLocalStorage(sizeByPath = emptyMap()) // sizeOf == 0
        val cloud = FakeCloudStorage(downloadResult = AppResult.Error(Exception("not found")))

        val result = ensureAttachmentLocal(
            attachment = attachment(path = "/local/photo.jpg", storagePath = "users/u/att.jpg"),
            local = local,
            cloud = cloud,
        )

        assertFalse(result, "a failed cloud download leaves no local bytes → false")
        assertEquals(1, cloud.downloadCalls.size, "the download was attempted exactly once")
    }

    @Test
    fun ensureAttachmentLocal_downloadFails_invokesOnFailure_withReasonAndThrowable() = runTest {
        val local = FakeLocalStorage(sizeByPath = emptyMap()) // sizeOf == 0
        val cloud = FakeCloudStorage(downloadResult = AppResult.Error(Exception("CORS blocked")))
        var reportedReason: String? = null
        var reportedThrowable: Throwable? = null

        val result = ensureAttachmentLocal(
            attachment = attachment(path = "/local/photo.jpg", storagePath = "users/u/att.jpg"),
            local = local,
            cloud = cloud,
            onFailure = { reason, throwable ->
                reportedReason = reason
                reportedThrowable = throwable
            },
        )

        assertFalse(result, "a failed cloud download leaves no local bytes → false")
        assertEquals("CORS blocked", reportedReason, "onFailure carries the download reason for analytics")
        assertEquals(
            "CORS blocked",
            reportedThrowable?.message,
            "onFailure carries the underlying throwable for Crashlytics recordException",
        )
    }

    @Test
    fun ensureAttachmentLocal_success_doesNotInvokeOnFailure() = runTest {
        val local = FakeLocalStorage(sizeByPath = emptyMap()) // sizeOf == 0
        val cloud = FakeCloudStorage(downloadResult = AppResult.Success(Unit))
        var failed = false

        ensureAttachmentLocal(
            attachment = attachment(path = "/local/photo.jpg", storagePath = "users/u/att.jpg"),
            local = local,
            cloud = cloud,
            onFailure = { _, _ -> failed = true },
        )

        assertFalse(failed, "a successful materialize must not report a load failure")
    }

    @Test
    fun ensureAttachmentLocal_downloadCancelled_propagates_andDoesNotReportFailure() = runTest {
        // A cancelled download = the thumbnail/viewer left composition (scroll / swipe / close), NOT
        // a load failure. It must propagate as cancellation and must NOT fire onFailure — otherwise
        // every scroll-away pollutes the attachment_load_failed funnel + Crashlytics.
        val local = FakeLocalStorage(sizeByPath = emptyMap()) // sizeOf == 0
        val cloud = object : AttachmentCloudStoragePort {
            override suspend fun download(storagePath: String, localPath: String): AppResult<Unit> =
                throw CancellationException("scrolled away mid-download")
            override suspend fun upload(localPath: String, storagePath: String) = AppResult.Success(Unit)
            override suspend fun delete(storagePath: String) = AppResult.Success(Unit)
        }
        var reported = false

        assertFailsWith<CancellationException> {
            ensureAttachmentLocal(
                attachment = attachment(path = "/local/photo.jpg", storagePath = "users/u/att.jpg"),
                local = local,
                cloud = cloud,
                onFailure = { _, _ -> reported = true },
            )
        }

        assertFalse(reported, "a cancelled download must not be reported as a load failure")
    }

    // ── Fakes ─────────────────────────────────────────────────────────────────

    /** In-memory local storage; [sizeOf] returns the configured size (default 0 = missing). */
    private class FakeLocalStorage(
        private val sizeByPath: Map<String, Long>,
    ) : AttachmentStoragePort {
        override suspend fun sizeOf(path: String): Long = sizeByPath[path] ?: 0L

        // ── Unused stubs ──
        override suspend fun storeAttachment(
            sourcePath: String,
            fillId: Long,
            itemId: String,
            attachmentId: String,
            originalFileName: String,
        ): String? = null
        override suspend fun deleteAttachment(path: String) {}
        override suspend fun deleteAttachmentsFor(fillId: Long, itemId: String) {}
        override suspend fun deleteAttachmentsForFill(fillId: Long) {}
        override suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?> = null to null
    }

    /** In-memory cloud transfer; records [download] calls as (storagePath, localPath) pairs. */
    private class FakeCloudStorage(
        private val downloadResult: AppResult<Unit> = AppResult.Success(Unit),
    ) : AttachmentCloudStoragePort {
        val downloadCalls = mutableListOf<Pair<String, String>>()

        override suspend fun download(storagePath: String, localPath: String): AppResult<Unit> {
            downloadCalls.add(storagePath to localPath)
            return downloadResult
        }

        override suspend fun upload(localPath: String, storagePath: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun delete(storagePath: String): AppResult<Unit> = AppResult.Success(Unit)
    }
}
