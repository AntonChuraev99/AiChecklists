package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentCloudStoragePort
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * The fullscreen attachment viewer must survive its list shrinking while it is open.
 *
 * Production crash (1.21.1): `IndexOutOfBoundsException` in `AttachmentFullscreenViewer`. The pager's
 * `currentPage` is corrected by a measure pass, but the composition indexed the live list with it —
 * so deleting an attachment (or a sync dropping one) left one composition pointing past the end.
 *
 * Driven through the real composable because the defect is a composition-vs-measure ordering one:
 * the pure selector is covered by `AttachmentViewerPageTest`, this pins that nothing else in the
 * viewer indexes by page. Non-image attachments are used on purpose — the file page needs no Coil.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*AttachmentFullscreenViewerShrinkTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttachmentFullscreenViewerShrinkTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val a = attachment("a")
    private val b = attachment("b")
    private val c = attachment("c")

    private var attachments by mutableStateOf(listOf(a, b, c))
    private var closeCalls = 0

    @Before
    fun startKoinWithStoragePorts() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single<AttachmentStoragePort> { NoopLocalStorage }
                    single<AttachmentCloudStoragePort> { NoopCloudStorage }
                }
            )
        }
    }

    @After
    fun stopKoinAfterTest() {
        stopKoin()
    }

    private fun showViewerOn(initialAttachmentId: String) {
        composeTestRule.setContent {
            AppTheme {
                AttachmentFullscreenViewer(
                    attachments = attachments,
                    initialAttachmentId = initialAttachmentId,
                    onClose = { closeCalls++ },
                    onDelete = {},
                    onOpenExternally = {},
                )
            }
        }
        composeTestRule.onNodeWithText("$initialAttachmentId.pdf").assertIsDisplayed()
    }

    @Test
    fun currentAndLastAttachmentRemoved_showsNewLast_withoutCrash() {
        showViewerOn("c")

        attachments = listOf(a, b)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("b.pdf").assertIsDisplayed()
        assertEquals(0, closeCalls, "the viewer stays open while attachments remain")
    }

    @Test
    fun listShrinksFarBelowCurrentPage_showsRemainingAttachment_withoutCrash() {
        showViewerOn("c")

        attachments = listOf(a)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("a.pdf").assertIsDisplayed()
        assertEquals(0, closeCalls, "the viewer stays open while attachments remain")
    }

    @Test
    fun attachmentBeforeCurrentRemoved_keepsShowingTheSameAttachment() {
        showViewerOn("c")

        attachments = listOf(b, c)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("c.pdf").assertIsDisplayed()
        assertEquals(0, closeCalls, "the viewer stays open while attachments remain")
    }

    @Test
    fun listEmptiedWhileOpen_closesTheViewer() {
        attachments = listOf(a)
        showViewerOn("a")

        attachments = emptyList()
        composeTestRule.waitForIdle()

        assertEquals(1, closeCalls, "an emptied viewer must ask its host to close, exactly once")
    }

    private fun attachment(id: String) = Attachment(
        id = id,
        path = "/local/$id.pdf",
        fileName = "$id.pdf",
        mimeType = "application/pdf",
        sizeBytes = 1_024L,
        createdAt = 0L,
    )

    private object NoopLocalStorage : AttachmentStoragePort {
        override suspend fun sizeOf(path: String): Long = 0L
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

    private object NoopCloudStorage : AttachmentCloudStoragePort {
        override suspend fun download(storagePath: String, localPath: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun upload(localPath: String, storagePath: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun delete(storagePath: String): AppResult<Unit> = AppResult.Success(Unit)
    }
}
