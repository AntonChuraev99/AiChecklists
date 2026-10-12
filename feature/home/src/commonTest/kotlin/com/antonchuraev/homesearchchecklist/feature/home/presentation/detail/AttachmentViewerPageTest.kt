package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Coverage for [attachmentForPage] — which attachment the fullscreen viewer shows for a pager page.
 *
 * The pager's `currentPage` is corrected by a measure pass, while the list it indexes is read live,
 * so for at least one composition after an attachment is deleted (or a sync shrinks the list) the
 * page can point past the end. Production crash: `IndexOutOfBoundsException` in
 * `AttachmentFullscreenViewer` (1.21.1).
 */
class AttachmentViewerPageTest {

    private fun attachment(id: String) = Attachment(
        id = id,
        path = "/local/$id.pdf",
        fileName = "$id.pdf",
        mimeType = "application/pdf",
        sizeBytes = 1_024L,
        createdAt = 0L,
    )

    private val a = attachment("a")
    private val b = attachment("b")
    private val c = attachment("c")

    @Test
    fun pageInBounds_returnsThatAttachment() {
        assertEquals(a, attachmentForPage(listOf(a, b, c), 0))
        assertEquals(b, attachmentForPage(listOf(a, b, c), 1))
        assertEquals(c, attachmentForPage(listOf(a, b, c), 2))
    }

    @Test
    fun pageEqualsSize_afterLastWasDeleted_returnsNewLast() {
        // Viewer sat on page 2 of [a, b, c]; "c" was deleted, the pager still reports page 2.
        assertEquals(b, attachmentForPage(listOf(a, b), 2))
    }

    @Test
    fun pageFarPastEnd_afterSyncShrankList_returnsNewLast() {
        assertEquals(a, attachmentForPage(listOf(a), 7))
    }

    @Test
    fun negativePage_returnsFirst() {
        assertEquals(a, attachmentForPage(listOf(a, b), -1))
    }

    @Test
    fun emptyList_returnsNull() {
        assertNull(attachmentForPage(emptyList(), 0))
        assertNull(attachmentForPage(emptyList(), 3))
    }
}
