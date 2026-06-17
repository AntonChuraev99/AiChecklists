@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.core.common.api

import kotlin.js.Promise
import kotlinx.coroutines.await

/**
 * wasmJs (web) implementation of [AttachmentStorage], backed by the Origin Private
 * File System (OPFS). Durable across page reloads — the same persistence story as
 * Room's SQLite OPFS driver.
 *
 * Storage layout (pseudo-path, also persisted as [com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment.path]):
 *   opfs://attachments/<fillId>/<itemId>/<attachmentId>.<ext>
 *
 * [sourcePath] semantic (wasmJs):
 *   The file picker (core.filepicker.api) stages the chosen file's bytes in the JS
 *   `globalThis.__filePickerStaging` Map under a key like "wasm-blob://<uuid>". That
 *   key is what flows in as [sourcePath]; __opfsStore copies those staged bytes into
 *   the durable OPFS file, then the transient staging entry is dropped.
 *
 * All bridges delegate to globalThis.__opfs* helpers (defined in init.js.template).
 * Those helpers never reject — they resolve to a sentinel on failure — so each call
 * here is a plain suspend `await` with no extra try/catch needed beyond the @JsFun
 * guards.
 */
actual class AttachmentStorage : AttachmentStoragePort {

    actual override suspend fun storeAttachment(
        sourcePath: String,
        fillId: Long,
        itemId: String,
        attachmentId: String,
        originalFileName: String,
    ): String? {
        val ext = deriveExtension(originalFileName)
        val opfsPath = "opfs://attachments/$fillId/$itemId/$attachmentId.$ext"
        val stored = jsOpfsStore(sourcePath, opfsPath).await<JsAny?>()
        // Drop the transient picker bytes regardless of outcome — they are no longer needed.
        jsDeleteStagedBytes(sourcePath)
        return if (jsBoolValue(stored)) opfsPath else null
    }

    actual override suspend fun deleteAttachment(path: String) {
        jsOpfsDeleteFile(path).await<JsAny?>()
    }

    actual override suspend fun deleteAttachmentsFor(fillId: Long, itemId: String) {
        jsOpfsDeleteDir("opfs://attachments/$fillId/$itemId").await<JsAny?>()
    }

    actual override suspend fun deleteAttachmentsForFill(fillId: Long) {
        jsOpfsDeleteDir("opfs://attachments/$fillId").await<JsAny?>()
    }

    actual override suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?> {
        if (mimeType?.startsWith("image/") != true) return null to null
        val result = jsOpfsProbeImage(path, mimeType).await<JsAny?>() ?: return null to null
        val w = jsProbeWidth(result)
        val h = jsProbeHeight(result)
        return w to h
    }

    actual override suspend fun sizeOf(path: String): Long {
        val size = jsOpfsStat(path).await<JsAny?>()
        return jsDoubleValue(size).toLong()
    }

    private companion object {
        /** Derive a file extension from the original filename; fall back to "bin". */
        fun deriveExtension(fileName: String): String =
            fileName.substringAfterLast('.', "").ifBlank { "bin" }.lowercase()
    }
}

// ---------------------------------------------------------------------------
// JS bridges — delegate to globalThis.__opfs* defined in init.js.template.
// Each @JsFun wraps its body in try/catch so a missing OPFS API (sealed WebView,
// private mode) returns a safe sentinel instead of crashing the wasm runtime.
// ---------------------------------------------------------------------------

@JsFun(
    """(sourceKey, opfsPath) => {
        try { return globalThis.__opfsStore(sourceKey, opfsPath); }
        catch (e) { console.error('[OPFS] store bridge failed:', e); return Promise.resolve(false); }
    }"""
)
private external fun jsOpfsStore(sourceKey: String, opfsPath: String): Promise<JsAny?>

@JsFun(
    """(opfsPath) => {
        try { return globalThis.__opfsDeleteFile(opfsPath); }
        catch (e) { return Promise.resolve(false); }
    }"""
)
private external fun jsOpfsDeleteFile(opfsPath: String): Promise<JsAny?>

@JsFun(
    """(opfsPath) => {
        try { return globalThis.__opfsDeleteDir(opfsPath); }
        catch (e) { return Promise.resolve(false); }
    }"""
)
private external fun jsOpfsDeleteDir(opfsPath: String): Promise<JsAny?>

@JsFun(
    """(opfsPath) => {
        try { return globalThis.__opfsStat(opfsPath); }
        catch (e) { return Promise.resolve(0); }
    }"""
)
private external fun jsOpfsStat(opfsPath: String): Promise<JsAny?>

@JsFun(
    """(opfsPath, mimeType) => {
        try { return globalThis.__opfsProbeImage(opfsPath, mimeType); }
        catch (e) { return Promise.resolve({ w: null, h: null }); }
    }"""
)
private external fun jsOpfsProbeImage(opfsPath: String, mimeType: String?): Promise<JsAny?>

@JsFun("(key) => { try { return globalThis.__deleteStagedBytes(key); } catch (e) { return false; } }")
private external fun jsDeleteStagedBytes(key: String): Boolean

// Result-object field accessors + primitive unwrappers.
@JsFun("(obj) => globalThis.__opfsProbeW(obj)")
private external fun jsProbeWidth(obj: JsAny): Int?

@JsFun("(obj) => globalThis.__opfsProbeH(obj)")
private external fun jsProbeHeight(obj: JsAny): Int?

@JsFun("(v) => (v === true)")
private external fun jsBoolValue(v: JsAny?): Boolean

@JsFun("(v) => (typeof v === 'number' ? v : 0)")
private external fun jsDoubleValue(v: JsAny?): Double
