@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import kotlin.js.Promise
import kotlinx.coroutines.await
import okio.Buffer

/**
 * Coil 3 [Fetcher] + [Keyer] for "opfs://..." attachment paths on wasmJs.
 *
 * Coil cannot natively read OPFS pseudo-paths, and the path is persisted to the Room DB
 * (so we can't pre-resolve it to a transient blob: URL). Instead this fetcher reads the
 * OPFS file's bytes via globalThis.__opfsReadBytes (async) into an in-memory okio
 * [Buffer], then hands Coil a [SourceFetchResult] backed by that buffer.
 *
 * Registration: SingletonImageLoader.setSafe { ... .components { add(Factory()); add(OpfsKeyer()) } }
 * in main.kt — see [com.antonchuraev.homesearchchecklist.main].
 *
 * The okio Buffer holds the whole image in memory for the duration of the decode. Attachment
 * uploads are capped (MAX_ATTACHMENT_SIZE_BYTES in the ViewModel), so this is bounded.
 */
class OpfsImageFetcher(
    private val data: String,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = readOpfsBytes(data)
        if (bytes == null) {
            println("[OpfsFetcher] readOpfsBytes returned NULL for $data")
            return null
        }
        // Diagnostic: confirm the bytes Kotlin actually read (size + magic header) — a valid
        // JPEG starts FF D8 FF, PNG 89 50 4E 47. Pinpoints byte corruption vs Coil decode failure.
        val magic = bytes.take(4).joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        println("[OpfsFetcher] read $data size=${bytes.size} magic=$magic")
        val buffer = Buffer().apply { write(bytes) }
        return SourceFetchResult(
            source = ImageSource(source = buffer, fileSystem = options.fileSystem),
            mimeType = null, // let the decoder sniff it from the bytes
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.startsWith(OPFS_SCHEME)) return null
            return OpfsImageFetcher(data, options)
        }
    }
}

/**
 * Cache key for "opfs://" data. The pseudo-path is stable and unique per attachment
 * (contains attachmentId), so it doubles as both memory- and disk-cache key.
 */
class OpfsKeyer : Keyer<String> {
    override fun key(data: String, options: Options): String? =
        if (data.startsWith(OPFS_SCHEME)) data else null
}

private const val OPFS_SCHEME = "opfs://"

// ---------------------------------------------------------------------------
// JS bridge — reads OPFS bytes as a Uint8Array via globalThis.__opfsReadBytes.
// ---------------------------------------------------------------------------

private suspend fun readOpfsBytes(opfsPath: String): ByteArray? {
    val uint8 = runCatching { jsOpfsReadBytes(opfsPath).await<JsAny?>() }.getOrNull() ?: return null
    val length = jsUint8ArrayLength(uint8)
    if (length == 0) return ByteArray(0)
    return ByteArray(length) { i -> jsUint8ArrayGetByte(uint8, i).toByte() }
}

@JsFun(
    """(opfsPath) => {
        try { return globalThis.__opfsReadBytes(opfsPath); }
        catch (e) { console.error('[OPFS] readBytes bridge failed:', e); return Promise.resolve(null); }
    }"""
)
private external fun jsOpfsReadBytes(opfsPath: String): Promise<JsAny?>

@JsFun("(arr) => arr ? arr.length : 0")
private external fun jsUint8ArrayLength(arr: JsAny): Int

@JsFun("(arr, i) => arr[i] & 0xFF")
private external fun jsUint8ArrayGetByte(arr: JsAny, i: Int): Int
