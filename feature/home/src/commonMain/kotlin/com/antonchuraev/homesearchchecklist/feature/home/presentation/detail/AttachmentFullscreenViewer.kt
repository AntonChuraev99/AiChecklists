package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.attachment_fullscreen_close_cd
import aichecklists.core.designsystem.generated.resources.attachment_fullscreen_counter
import aichecklists.core.designsystem.generated.resources.attachment_fullscreen_delete_cd
import aichecklists.core.designsystem.generated.resources.attachment_open_external_button
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen overlay for browsing, zooming, and managing [attachments].
 *
 * Layout:
 * - [Dialog] with `usePlatformDefaultWidth = false` covers the entire screen.
 * - [Surface] with [Modifier.systemBarsPadding] keeps content clear of system bars
 *   (edge-to-edge safe; no statusBarsPadding/navigationBarsPadding double-dipping).
 * - [TopAppBar] with 90% opaque surface background overlaid on the pager.
 *   close (nav icon) | "X of Y" counter (centred title) | delete action (end).
 * - [HorizontalPager] — one page per attachment.
 *   Image pages: Coil AsyncImage + pinch-to-zoom via detectTransformGestures.
 *   Non-image pages: centred file icon, name, MIME, size, "Open" AppButton.
 *
 * Zoom state is hoisted to the pager level (per-page via pageIndex key) so that
 * [HorizontalPager.userScrollEnabled] can be set to false while any page is zoomed.
 * This prevents pager swipe from fighting pan gestures.
 *
 * Only emits callbacks — no state mutation. Phase 4 ViewModel wires [onDelete] /
 * [onOpenExternally] to actual platform actions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun AttachmentFullscreenViewer(
    attachments: List<Attachment>,
    initialAttachmentId: String,
    onClose: () -> Unit,
    onDelete: (attachmentId: String) -> Unit,
    onOpenExternally: (attachmentId: String) -> Unit,
) {
    if (attachments.isEmpty()) return

    val initialPage = attachments
        .indexOfFirst { it.id == initialAttachmentId }
        .coerceAtLeast(0)

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { attachments.size },
    )

    // Per-page zoom scale, keyed by page index. Reset when page changes.
    var currentScale by remember { mutableFloatStateOf(1f) }

    // Keep track of which page is "active" for zoom reset on swipe
    LaunchedEffect(pagerState.currentPage) {
        currentScale = 1f
    }

    val currentAttachment = attachments[pagerState.currentPage]

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ── Pager: swipe disabled while zoomed in ──
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = currentScale <= 1f,
                    modifier = Modifier.fillMaxSize(),
                ) { pageIndex ->
                    val attachment = attachments[pageIndex]
                    val isCurrentPage = pageIndex == pagerState.currentPage

                    if (attachment.isImage) {
                        ZoomableImagePage(
                            attachment = attachment,
                            scale = if (isCurrentPage) currentScale else 1f,
                            onScaleChange = { newScale -> currentScale = newScale },
                            isCurrentPage = isCurrentPage,
                        )
                    } else {
                        FileDetailPage(
                            attachment = attachment,
                            onOpenExternally = { onOpenExternally(attachment.id) },
                        )
                    }
                }

                // ── TopAppBar overlaid at the top ──
                AttachmentViewerTopBar(
                    currentIndex = pagerState.currentPage,
                    total = attachments.size,
                    currentAttachmentId = currentAttachment.id,
                    onClose = onClose,
                    onDelete = onDelete,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Top app bar
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentViewerTopBar(
    currentIndex: Int,
    total: Int,
    currentAttachmentId: String,
    onClose: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val counter = stringResource(
        Res.string.attachment_fullscreen_counter,
        currentIndex + 1,
        total,
    )
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.attachment_fullscreen_close_cd),
                )
            }
        },
        title = {
            Text(
                text = counter,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        actions = {
            IconButton(onClick = { onDelete(currentAttachmentId) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(Res.string.attachment_fullscreen_delete_cd),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Zoomable image page
// ──────────────────────────────────────────────────────────────────────────────

/**
 * A single image page with pinch-to-zoom + pan support.
 *
 * Scale and pan state are owned by the parent composable ([AttachmentFullscreenViewer])
 * so the pager can gate its own swipe gesture. Only the current page has live zoom;
 * non-current pages always receive [scale] = 1f and ignore [onScaleChange].
 *
 * Scale clamped to [1f, 5f]. When scale returns to 1f via over-pinch, offsets reset to zero
 * so the image snaps back to centre (no drifting).
 */
@Composable
private fun ZoomableImagePage(
    attachment: Attachment,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    isCurrentPage: Boolean,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Reset offsets when scale collapses back to 1 (e.g. parent resets on page change)
    LaunchedEffect(scale) {
        if (scale <= 1f) {
            offsetX = 0f
            offsetY = 0f
        }
    }

    val context = LocalPlatformContext.current
    val request = ImageRequest.Builder(context)
        .data(attachment.path)
        .crossfade(true)
        .build()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isCurrentPage) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            onScaleChange(newScale)
                            if (newScale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = attachment.fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
            placeholder = rememberVectorPainter(Icons.AutoMirrored.Filled.InsertDriveFile),
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Non-image (file) page
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun FileDetailPage(
    attachment: Attachment,
    onOpenExternally: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = AppDimens.SpacingXl,
                vertical = AppDimens.SpacingXxxl,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(AppDimens.SpacingLg))
        Text(
            text = attachment.fileName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val mimeType = attachment.mimeType
        if (mimeType != null) {
            Spacer(Modifier.height(AppDimens.SpacingXs))
            Text(
                text = mimeType,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(AppDimens.SpacingXs))
        Text(
            text = formatFileSize(attachment.sizeBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AppDimens.SpacingXl))
        AppButton(
            text = stringResource(Res.string.attachment_open_external_button),
            onClick = onOpenExternally,
            icon = Icons.AutoMirrored.Filled.OpenInNew,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Formats [bytes] as a human-readable size string.
 * Pure Kotlin — no platform APIs required (String.format is Java-only, not available on wasmJs/iOS).
 */
internal fun formatFileSize(bytes: Long): String = when {
    bytes < 1_024L -> "$bytes B"
    bytes < 1_024L * 1_024L -> "${bytes / 1_024L} KB"
    bytes < 1_024L * 1_024L * 1_024L -> "${formatOneDecimal(bytes.toDouble() / (1_024.0 * 1_024.0))} MB"
    else -> "${formatOneDecimal(bytes.toDouble() / (1_024.0 * 1_024.0 * 1_024.0))} GB"
}

private fun formatOneDecimal(value: Double): String {
    val tenths = (value * 10 + 0.5).toLong()
    val whole = tenths / 10
    val fraction = tenths % 10
    return "$whole.$fraction"
}
