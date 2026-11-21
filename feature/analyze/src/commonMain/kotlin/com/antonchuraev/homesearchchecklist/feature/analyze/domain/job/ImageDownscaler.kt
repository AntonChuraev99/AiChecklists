package com.antonchuraev.homesearchchecklist.feature.analyze.domain.job

/**
 * Shrinks a photo before it is base64-encoded and sent: long side to [AnalyzeImageLimits.MAX_LONG_SIDE_PX],
 * EXIF orientation applied (rotated pixels, no orientation tag left), re-encoded as JPEG at
 * [AnalyzeImageLimits.JPEG_QUALITY]. An image already within the limit is still re-encoded only when
 * it is not JPEG or carries a non-normal orientation; otherwise the bytes pass through untouched.
 *
 * Android (`@android-platform-expert`): two-pass `BitmapFactory` with `inSampleSize`, then exact
 * scale + `androidx.exifinterface` rotation. wasmJs (`@wasmjs-expert`): `createImageBitmap` with
 * `imageOrientation: "from-image"` + resize, `OffscreenCanvas`/canvas `toBlob("image/jpeg", q)`.
 *
 * Returns null when the bytes cannot be decoded as an image; the processor then sends the original
 * bytes and logs a warning (a failed downscale must not fail an analysis that would have worked).
 */
interface ImageDownscaler {
    suspend fun downscaleToJpeg(bytes: ByteArray, maxLongSidePx: Int, jpegQuality: Int): ByteArray?
}

object AnalyzeImageLimits {
    /**
     * Gemini 3 bills a fixed token budget per image whatever its size, so a smaller image costs the
     * same and uploads ~10x faster. OCR quality of dense receipts at 2048 px is UNVERIFIED — raise this
     * single constant if small print stops being read.
     */
    const val MAX_LONG_SIDE_PX: Int = 2048
    const val JPEG_QUALITY: Int = 85
}
