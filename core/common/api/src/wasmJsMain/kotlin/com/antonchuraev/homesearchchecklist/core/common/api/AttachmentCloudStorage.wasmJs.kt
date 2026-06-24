@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.core.common.api

import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * wasmJs (web) implementation of [AttachmentCloudStorage], backed by the Firebase JS Storage SDK.
 * The actual SDK calls live in init.js (`globalThis.__storage*`); this class is a thin bridge that
 * awaits those Promises and maps their `{ok, error}` JSON envelope to [AppResult].
 *
 * Byte flow on web:
 *  - upload   — reads the durable OPFS bytes at [localPath] (opfs://…) via `__opfsReadBytes`, then
 *               `uploadBytes()` to the cloud object [storagePath].
 *  - download — `getBytes()` the cloud object, then writes the bytes into OPFS at [localPath] via
 *               `__opfsWriteBytes` (so the Coil OPFS Fetcher can render them on this device).
 *  - delete   — `deleteObject()`; a missing object is treated as success (idempotent).
 *
 * Path contract (see [AttachmentCloudStoragePort]):
 *  - [localPath]   — "opfs://attachments/<fillId>/<itemId>/<attachmentId>.<ext>".
 *  - [storagePath] — "users/<uid>/attachments/<fillId>/<itemId>/<attachmentId>.<ext>".
 *
 * The JS bridges NEVER reject — they resolve to a `{ok:false, error}` envelope on failure (and log
 * via console.error) — so each call here is a plain suspend `await` guarded only by the @JsFun
 * try/catch + a Kotlin-side null check on the response.
 *
 * ⚠ CORS: the browser `getBytes()` (download) requires a CORS configuration on the Storage bucket
 * (a manual `gsutil cors set` step). Without it, download fails with a CORS error surfaced here as
 * [AppResult.Error]; upload/delete are unaffected.
 */
actual class AttachmentCloudStorage : AttachmentCloudStoragePort {

    actual override suspend fun upload(localPath: String, storagePath: String): AppResult<Unit> =
        runResult { jsStorageUpload(localPath, storagePath).await<JsAny?>() }

    actual override suspend fun download(storagePath: String, localPath: String): AppResult<Unit> =
        runResult { jsStorageDownload(storagePath, localPath).await<JsAny?>() }

    actual override suspend fun delete(storagePath: String): AppResult<Unit> =
        runResult { jsStorageDelete(storagePath).await<JsAny?>() }

    private companion object {
        private val jsonParser = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class JsResponse(val ok: Boolean = false, val error: String? = null)

        /** Awaits a bridge call, parses its `{ok, error}` JSON envelope into [AppResult]. */
        private suspend inline fun runResult(block: () -> JsAny?): AppResult<Unit> {
            val raw = block() ?: return AppResult.Error(Exception("storage op: null response"))
            val json = jsAnyToString(raw)
            val resp = runCatching { jsonParser.decodeFromString(JsResponse.serializer(), json) }
                .getOrElse { return AppResult.Error(Exception("storage op: bad response: $json")) }
            return if (resp.ok) AppResult.Success(Unit)
            else AppResult.Error(Exception(resp.error ?: "storage op failed"))
        }
    }
}

// ---------------------------------------------------------------------------
// JS bridges — delegate to globalThis.__storage* defined in init.js.template.
// Each @JsFun wraps its body in try/catch so a thrown (rather than resolved-false)
// failure still returns a `{ok:false}` envelope instead of crashing the wasm runtime.
// All three resolve to a JSON string {ok: boolean, error?: string}.
// ---------------------------------------------------------------------------

@JsFun(
    """(opfsPath, storagePath) => {
        try { return globalThis.__storageUpload(opfsPath, storagePath); }
        catch (e) { console.error('[Storage] upload bridge failed:', e); return Promise.resolve(JSON.stringify({ ok: false, error: String(e) })); }
    }"""
)
private external fun jsStorageUpload(opfsPath: String, storagePath: String): Promise<JsAny?>

@JsFun(
    """(storagePath, opfsPath) => {
        try { return globalThis.__storageDownload(storagePath, opfsPath); }
        catch (e) { console.error('[Storage] download bridge failed:', e); return Promise.resolve(JSON.stringify({ ok: false, error: String(e) })); }
    }"""
)
private external fun jsStorageDownload(storagePath: String, opfsPath: String): Promise<JsAny?>

@JsFun(
    """(storagePath) => {
        try { return globalThis.__storageDelete(storagePath); }
        catch (e) { console.error('[Storage] delete bridge failed:', e); return Promise.resolve(JSON.stringify({ ok: false, error: String(e) })); }
    }"""
)
private external fun jsStorageDelete(storagePath: String): Promise<JsAny?>

@JsFun("(v) => String(v)")
private external fun jsAnyToString(v: JsAny): String
