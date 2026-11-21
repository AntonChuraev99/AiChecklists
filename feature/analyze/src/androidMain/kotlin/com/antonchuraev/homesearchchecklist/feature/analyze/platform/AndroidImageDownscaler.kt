package com.antonchuraev.homesearchchecklist.feature.analyze.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ImageDownscaleMath
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ImageDownscaler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Android [ImageDownscaler]: header-only decode → power-of-two `inSampleSize` decode → one exact
 * scale + EXIF rotation in a single `Matrix` → JPEG. Runs on [Dispatchers.Default]; both bitmaps are
 * recycled. Any failure, `OutOfMemoryError` included, returns null (= send the original bytes) and is
 * logged — a failed downscale must never fail an analysis that would have worked.
 */
class AndroidImageDownscaler(private val logger: AppLogger) : ImageDownscaler {

    override suspend fun downscaleToJpeg(bytes: ByteArray, maxLongSidePx: Int, jpegQuality: Int): ByteArray? =
        withContext(Dispatchers.Default) {
            runCatching { downscale(bytes, maxLongSidePx, jpegQuality) }.getOrElse { e ->
                if (e is CancellationException) throw e
                logger.warning(TAG, "downscale failed (${e::class.simpleName}: ${e.message}) — sending the original")
                null
            }
        }

    private fun downscale(bytes: ByteArray, maxLongSidePx: Int, jpegQuality: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val plan = ImageDownscaleMath.plan(bounds.outWidth, bounds.outHeight, maxLongSidePx) ?: return null
        val orientation = readOrientation(bytes)
        val isJpeg = bounds.outMimeType.equals(JPEG_MIME, ignoreCase = true)
        // Already small, already JPEG, already upright: the original bytes are the answer.
        if (!plan.needsResize && isJpeg && orientation.isUpright()) return bytes

        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = plan.inSampleSize },
        ) ?: return null

        var output: Bitmap? = null
        try {
            val targetLongSide = max(plan.targetWidth, plan.targetHeight)
            val decodedLongSide = max(decoded.width, decoded.height)
            val scale = (targetLongSide.toFloat() / decodedLongSide).coerceAtMost(1f)
            val matrix = Matrix().apply {
                setScale(scale, scale)
                applyOrientation(orientation)
            }
            output = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val stream = ByteArrayOutputStream()
            if (!output.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)) return null
            return stream.toByteArray()
        } finally {
            if (output != null && output !== decoded) output.recycle()
            decoded.recycle()
        }
    }

    private fun readOrientation(bytes: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrElse { e ->
        // Silent fallback is a project-rule violation: an unreadable EXIF block means a rotated photo
        // may reach the AI sideways, and without this line "AI didn't read my photo" has no trace.
        logger.warning(TAG, "EXIF orientation unreadable (${e::class.simpleName}: ${e.message}) — assuming upright")
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun Int.isUpright(): Boolean =
        this == ExifInterface.ORIENTATION_NORMAL || this == ExifInterface.ORIENTATION_UNDEFINED

    /** Standard EXIF orientation → transform, applied after the scale. */
    private fun Matrix.applyOrientation(orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(-90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
            else -> Unit
        }
    }

    private companion object {
        const val TAG = "AnalyzeDownscale"
        const val JPEG_MIME = "image/jpeg"
    }
}
