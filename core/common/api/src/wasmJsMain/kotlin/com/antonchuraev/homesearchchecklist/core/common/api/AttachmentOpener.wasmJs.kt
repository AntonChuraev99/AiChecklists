@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.core.common.api

import kotlin.js.Promise
import kotlinx.coroutines.await

/**
 * wasmJs (web) implementation of [AttachmentOpener].
 *
 * Reads the OPFS-backed file into a fresh object URL, then triggers a synthetic
 * anchor (`<a>`) click:
 *   - images and application/pdf -> `target="_blank"` (browser opens its native viewer)
 *   - everything else            -> `download="<fileName>"` (saves with the original name)
 *
 * Popup-safety: this runs inside a coroutine that `await`s the async OPFS read, so we
 * are no longer in the original synchronous user-gesture frame. `window.open()` would
 * be popup-blocked there. Anchor-driven open/download, by contrast, is permitted by
 * browsers even outside a fresh user-activation frame (it navigates/downloads via an
 * `<a>` rather than spawning a popup window), so it survives the await. The object URL
 * is revoked after a 60s delay so the new tab / download has time to consume it.
 *
 * [path] is the "opfs://attachments/..." pseudo-path stored in the attachment record.
 */
actual class AttachmentOpener {

    actual suspend fun openExternally(path: String, mimeType: String?): Boolean {
        val result = jsOpfsOpenExternally(path, fileNameFromPath(path), mimeType).await<JsAny?>()
        return jsBoolValue(result)
    }

    private companion object {
        /** Last path segment ("att_123.png") used as the download filename fallback. */
        fun fileNameFromPath(path: String): String =
            path.substringAfterLast('/', "").ifBlank { "attachment" }
    }
}

@JsFun(
    """(opfsPath, fileName, mimeType) => {
        try { return globalThis.__opfsOpenExternally(opfsPath, fileName, mimeType); }
        catch (e) { console.error('[OPFS] open bridge failed:', e); return Promise.resolve(false); }
    }"""
)
private external fun jsOpfsOpenExternally(
    opfsPath: String,
    fileName: String,
    mimeType: String?,
): Promise<JsAny?>

@JsFun("(v) => (v === true)")
private external fun jsBoolValue(v: JsAny?): Boolean
