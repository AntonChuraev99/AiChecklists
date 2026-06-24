package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentCloudStoragePort
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import org.koin.compose.koinInject

/**
 * Lazy materialization state for an [Attachment] (cross-device sync, Phase 4).
 *
 * On the device that originally captured the attachment the bytes already live locally, so
 * materialization resolves to [Ready] instantly. On a second device of the same signed-in user the
 * local file is missing; the bytes are pulled from Firebase Storage into [Attachment.path] before
 * the image loaders (Coil / OpfsImageFetcher) read it.
 */
internal sealed interface AttachmentMaterializeState {
    /** Download (or local-existence probe) in flight. */
    data object Loading : AttachmentMaterializeState

    /** Local file present at [Attachment.path] → safe to render the path as usual. */
    data object Ready : AttachmentMaterializeState

    /** Cloud download failed (or no local copy and no cloud copy to fetch). */
    data object Error : AttachmentMaterializeState
}

/**
 * Ensures the attachment bytes are present locally at [Attachment.path]. If the local file is
 * missing but [Attachment.storagePath] is set, downloads it from Firebase Storage into [path].
 * Returns true if the file is (now) local OR there is nothing to download (no storagePath →
 * caller renders path and Coil shows its own error). Best-effort: never throws.
 */
internal suspend fun ensureAttachmentLocal(
    attachment: Attachment,
    local: AttachmentStoragePort,
    cloud: AttachmentCloudStoragePort,
): Boolean {
    if (local.sizeOf(attachment.path) > 0) return true
    val storagePath = attachment.storagePath ?: return true // no cloud copy; let the loader try path as-is
    return cloud.download(storagePath, attachment.path) is AppResult.Success
}

/**
 * Composable wrapper around [ensureAttachmentLocal]: probes/downloads when the attachment first
 * appears on screen (and again if its [Attachment.path] or [Attachment.storagePath] changes), and
 * exposes the result as [AttachmentMaterializeState] for the UI to switch on.
 *
 * Lazy by construction — it runs only while the composable is in composition (thumbnail visible in
 * the sheet, or the viewer page on screen), so a list of off-screen attachments triggers no
 * downloads.
 */
@Composable
internal fun rememberMaterializedAttachment(attachment: Attachment): AttachmentMaterializeState {
    val local = koinInject<AttachmentStoragePort>()
    val cloud = koinInject<AttachmentCloudStoragePort>()
    var state by remember(attachment.path, attachment.storagePath) {
        mutableStateOf<AttachmentMaterializeState>(AttachmentMaterializeState.Loading)
    }
    LaunchedEffect(attachment.path, attachment.storagePath) {
        state = AttachmentMaterializeState.Loading
        state = if (ensureAttachmentLocal(attachment, local, cloud)) {
            AttachmentMaterializeState.Ready
        } else {
            AttachmentMaterializeState.Error
        }
    }
    return state
}
