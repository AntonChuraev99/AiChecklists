@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ImageDownscaleMath
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ImageDownscaler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * Browser [ImageDownscaler]: decode with `createImageBitmap(blob, { imageOrientation: "from-image" })`
 * (EXIF rotation applied by the decoder, so the returned pixels are upright), size from the shared
 * [ImageDownscaleMath.plan], then draw onto a white `OffscreenCanvas` (or a detached `<canvas>`) and
 * encode `image/jpeg`.
 *
 * - Already within the limit, already JPEG, already upright → the input is returned untouched.
 * - Any browser failure (undecodable format such as HEIC in Chrome, missing API in an old WebView,
 *   canvas/encoder refusal) → null, which the contract defines as "send the original".
 * - The JS side never rejects; every bridge resolves to null on failure and logs to the console.
 * - Every `ImageBitmap` is closed and the canvas is zero-sized after use: decoded photos are tens of
 *   MB of GPU/CPU memory and the GC does not reclaim them promptly.
 */
class WasmImageDownscaler(
    private val logger: AppLogger,
) : ImageDownscaler {

    override suspend fun downscaleToJpeg(bytes: ByteArray, maxLongSidePx: Int, jpegQuality: Int): ByteArray? {
        if (bytes.isEmpty()) return null
        var bitmap: JsAny? = null
        try {
            bitmap = jsDecodeOriented(bytes.toUint8Array()).await<JsAny?>()
            if (bitmap == null) {
                logger.warning(TAG, "downscale: browser could not decode the image (${bytes.size} B) — sending the original")
                return null
            }
            val width = jsBitmapWidth(bitmap)
            val height = jsBitmapHeight(bitmap)
            val plan = ImageDownscaleMath.plan(width, height, maxLongSidePx)
            if (plan == null) {
                logger.warning(TAG, "downscale: unusable decoded size ${width}x$height — sending the original")
                return null
            }
            if (JpegHeader.canPassThrough(bytes, plan)) return bytes

            val source = bitmap
            bitmap = null // ownership moves to the encoder, which closes it in every branch
            val encoded = jsEncodeJpeg(
                source,
                plan.targetWidth,
                plan.targetHeight,
                jpegQuality.coerceIn(1, 100) / 100.0,
            ).await<JsAny?>()
            if (encoded == null) {
                logger.warning(TAG, "downscale: JPEG encode failed for ${plan.targetWidth}x${plan.targetHeight} — sending the original")
                return null
            }
            val result = uint8ArrayToByteArray(encoded)
            if (result.isEmpty()) {
                logger.warning(TAG, "downscale: encoder returned 0 bytes — sending the original")
                return null
            }
            logger.info(
                TAG,
                "downscale: ${width}x$height ${bytes.size} B -> ${plan.targetWidth}x${plan.targetHeight} ${result.size} B",
            )
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(TAG, "downscale failed: ${e.message}", e)
            return null
        } finally {
            bitmap?.let { jsCloseBitmap(it) }
        }
    }

    private companion object {
        const val TAG = "AnalyzeDownscale"
    }
}

// ---------------------------------------------------------------------------
// JS bridges. Each body is wrapped in try/catch and every promise resolves (never rejects): a missing
// createImageBitmap / OffscreenCanvas (old Safari, sealed in-app WebView) must degrade to "send the
// original", not to an exception crossing into wasm.
// ---------------------------------------------------------------------------

/** Uint8Array -> Promise<ImageBitmap | null>, EXIF orientation applied by the decoder. */
@JsFun(
    """(bytes) => {
        try {
            if (typeof createImageBitmap !== 'function') return Promise.resolve(null);
            const blob = new Blob([bytes]);
            return createImageBitmap(blob, { imageOrientation: 'from-image' })
                .then((bmp) => bmp, (e) => { console.warn('[AnalyzeDownscale] decode failed:', e); return null; });
        } catch (e) {
            console.warn('[AnalyzeDownscale] decode threw:', e);
            return Promise.resolve(null);
        }
    }"""
)
private external fun jsDecodeOriented(bytes: JsAny): Promise<JsAny?>

/**
 * (ImageBitmap, w, h, quality 0..1) -> Promise<Uint8Array | null>. Always closes the source bitmap.
 * Resizes with `createImageBitmap(..., { resizeQuality: 'high' })` where supported (better than a
 * bilinear `drawImage` on a large ratio); the `drawImage` to an exact w x h canvas is the fallback
 * and also the guarantee when a browser ignores the resize options. White fill first: a transparent
 * PNG would otherwise turn black in JPEG.
 */
@JsFun(
    """(source, w, h, quality) => {
        const closeQuietly = (b) => { try { if (b && b.close) b.close(); } catch (e) {} };
        try {
            const resizePromise = (source.width === w && source.height === h)
                ? Promise.resolve(source)
                : createImageBitmap(source, { resizeWidth: w, resizeHeight: h, resizeQuality: 'high' })
                    .then((r) => r, () => source);
            return resizePromise.then((img) => {
                let canvas = null;
                try {
                    canvas = (typeof OffscreenCanvas === 'function')
                        ? new OffscreenCanvas(w, h)
                        : Object.assign(document.createElement('canvas'), { width: w, height: h });
                    const ctx = canvas.getContext('2d');
                    if (!ctx) return null;
                    ctx.imageSmoothingEnabled = true;
                    ctx.imageSmoothingQuality = 'high';
                    ctx.fillStyle = '#ffffff';
                    ctx.fillRect(0, 0, w, h);
                    ctx.drawImage(img, 0, 0, w, h);
                } catch (e) {
                    console.warn('[AnalyzeDownscale] draw failed:', e);
                    canvas = null;
                } finally {
                    if (img !== source) closeQuietly(img);
                    closeQuietly(source);
                }
                if (!canvas) return null;
                const blobPromise = (typeof canvas.convertToBlob === 'function')
                    ? canvas.convertToBlob({ type: 'image/jpeg', quality: quality })
                    : new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality));
                return blobPromise
                    .then((blob) => {
                        // An encoder without JPEG support silently falls back to PNG: treat that as failure.
                        if (!blob || blob.type !== 'image/jpeg') return null;
                        return blob.arrayBuffer().then((buf) => new Uint8Array(buf));
                    })
                    .then((r) => r, (e) => { console.warn('[AnalyzeDownscale] encode failed:', e); return null; })
                    .finally(() => { try { canvas.width = 0; canvas.height = 0; } catch (e) {} });
            }).then((r) => r, (e) => { console.warn('[AnalyzeDownscale] resize failed:', e); closeQuietly(source); return null; });
        } catch (e) {
            console.warn('[AnalyzeDownscale] encode threw:', e);
            closeQuietly(source);
            return Promise.resolve(null);
        }
    }"""
)
private external fun jsEncodeJpeg(source: JsAny, width: Int, height: Int, quality: Double): Promise<JsAny?>

@JsFun("(bmp) => { try { return bmp.width | 0; } catch (e) { return 0; } }")
private external fun jsBitmapWidth(bitmap: JsAny): Int

@JsFun("(bmp) => { try { return bmp.height | 0; } catch (e) { return 0; } }")
private external fun jsBitmapHeight(bitmap: JsAny): Int

@JsFun("(bmp) => { try { if (bmp && bmp.close) bmp.close(); } catch (e) {} }")
private external fun jsCloseBitmap(bitmap: JsAny)
