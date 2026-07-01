package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
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

/** Log tag for the attachment materialize/display path (diagnostic observability). */
private const val TAG = "Attachments"

/**
 * Load-failure stages reported to [AnalyticsEvents.Attachment.LOAD_FAILED]. Kept as shared constants
 * so the thumbnail, the fullscreen viewer and the materialize step all emit the SAME wire value for
 * the `stage` param — a typo here would silently split the funnel in two.
 */
internal const val STAGE_MATERIALIZE = "materialize"
internal const val STAGE_DECODE = "decode"

/** Cap the free-text failure reason so a long Storage/CORS string never bloats an analytics prop. */
private const val MAX_REASON_LEN = 200

/**
 * Ensures the attachment bytes are present locally at [Attachment.path]. If the local file is
 * missing but [Attachment.storagePath] is set, downloads it from Firebase Storage into [path].
 * Returns true if the file is (now) local OR there is nothing to download (no storagePath →
 * caller renders path and Coil shows its own error). Best-effort: never throws.
 *
 * [onFailure] is invoked with the failure reason + the underlying [Throwable] (for Crashlytics)
 * ONLY on the genuine download-failed branch, so the composable can attribute the "broken image".
 */
internal suspend fun ensureAttachmentLocal(
    attachment: Attachment,
    local: AttachmentStoragePort,
    cloud: AttachmentCloudStoragePort,
    logger: AppLogger? = null,
    onFailure: (reason: String, throwable: Throwable?) -> Unit = { _, _ -> },
): Boolean {
    logger?.debug(TAG, "materialize: path=${attachment.path} storagePath=${attachment.storagePath}")
    val localSize = local.sizeOf(attachment.path)
    logger?.debug(TAG, "materialize: localSize=$localSize")
    if (localSize > 0) return true
    val storagePath = attachment.storagePath ?: run {
        logger?.debug(TAG, "materialize: no storagePath — let loader try path as-is")
        return true // no cloud copy; the loader tries path as-is (its onError reports a decode miss)
    }
    logger?.debug(TAG, "materialize: downloading $storagePath")
    return when (val result = cloud.download(storagePath, attachment.path)) {
        is AppResult.Success -> true
        is AppResult.Error -> {
            onFailure(result.exception.message ?: "download failed", result.exception)
            false
        }
        AppResult.Loading -> {
            onFailure("download returned Loading", null)
            false
        }
    }
}

/**
 * Single reporting surface for a failed attachment display: logs via [AppLogger.error] (→ Crashlytics
 * recordException on Android / console.error on web) AND fires [AnalyticsEvents.Attachment.LOAD_FAILED]
 * so both failure stages carry an identical param shape and land in one funnel.
 */
internal fun reportAttachmentLoadFailure(
    analytics: AnalyticsTracker,
    logger: AppLogger,
    attachment: Attachment,
    stage: String,
    reason: String?,
    throwable: Throwable?,
) {
    logger.error(
        TAG,
        "attachment load failed [$stage]: ${attachment.fileName} isImage=${attachment.isImage} " +
            "path=${attachment.path} storagePath=${attachment.storagePath} reason=$reason",
        throwable,
    )
    analytics.event(
        AnalyticsEvents.Attachment.LOAD_FAILED,
        buildMap {
            put(AnalyticsParams.STAGE, stage)
            put(AnalyticsParams.HAS_STORAGE_PATH, (attachment.storagePath != null).toString())
            attachment.mimeType?.let { put(AnalyticsParams.MIME_TYPE, it) }
            reason?.takeIf { it.isNotBlank() }?.let {
                put(AnalyticsParams.ERROR_MESSAGE, it.take(MAX_REASON_LEN))
            }
        },
    )
}

/**
 * A composable-scoped reporter for the `decode` stage: materialization returned
 * [AttachmentMaterializeState.Ready] (local file present) but Coil's `AsyncImage` still failed to
 * load the bytes — the classic partial/corrupt-cache case. Wire it into `AsyncImage(onError = …)`.
 */
@Composable
internal fun rememberAttachmentLoadErrorReporter(): (attachment: Attachment, stage: String, throwable: Throwable?) -> Unit {
    val analytics = koinInject<AnalyticsTracker>()
    val logger = koinInject<AppLogger>()
    return remember(analytics, logger) {
        { attachment, stage, throwable ->
            reportAttachmentLoadFailure(analytics, logger, attachment, stage, throwable?.message, throwable)
        }
    }
}

/**
 * Composable wrapper around [ensureAttachmentLocal]: probes/downloads when the attachment first
 * appears on screen (and again if its [Attachment.path] or [Attachment.storagePath] changes), and
 * exposes the result as [AttachmentMaterializeState] for the UI to switch on. Reports the
 * `materialize` stage of [AnalyticsEvents.Attachment.LOAD_FAILED] on the download-failed branch.
 *
 * Lazy by construction — it runs only while the composable is in composition (thumbnail visible in
 * the sheet, or the viewer page on screen), so a list of off-screen attachments triggers no
 * downloads.
 */
@Composable
internal fun rememberMaterializedAttachment(attachment: Attachment): AttachmentMaterializeState {
    val local = koinInject<AttachmentStoragePort>()
    val cloud = koinInject<AttachmentCloudStoragePort>()
    val logger = koinInject<AppLogger>()
    val analytics = koinInject<AnalyticsTracker>()
    var state by remember(attachment.path, attachment.storagePath) {
        mutableStateOf<AttachmentMaterializeState>(AttachmentMaterializeState.Loading)
    }
    LaunchedEffect(attachment.path, attachment.storagePath) {
        state = AttachmentMaterializeState.Loading
        val ok = ensureAttachmentLocal(attachment, local, cloud, logger) { reason, throwable ->
            reportAttachmentLoadFailure(analytics, logger, attachment, STAGE_MATERIALIZE, reason, throwable)
        }
        state = if (ok) AttachmentMaterializeState.Ready else AttachmentMaterializeState.Error
        logger.debug(TAG, "materialize: -> $state")
    }
    return state
}
