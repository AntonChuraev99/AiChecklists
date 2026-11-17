package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

/**
 * A flat 2:3 stand-in for the sample note photo (provided through `LocalSampleNoteImage`): desk
 * border, paper sheet, and dark bars inside the photo's text block (x 8–70 %, y 12–62 %), so crops,
 * the dark dim and the overlay band stay visibly placed. Flat colours only — that is what keeps the
 * golden PNG small, which is the whole point of the placeholder.
 */
internal object SampleNotePlaceholder {
    private const val W = 200
    private const val H = 300

    /** A fresh bitmap per call: one test's native bitmap is never shared into the next sandbox. */
    fun create(): ImageBitmap {
        val bitmap = ImageBitmap(W, H)
        val canvas = Canvas(bitmap)
        fun rect(l: Float, t: Float, r: Float, b: Float, color: Color) =
            canvas.drawRect(Rect(l * W, t * H, r * W, b * H), Paint().apply { this.color = color })
        rect(0f, 0f, 1f, 1f, Color(0xFFC8A06A)) // desk
        rect(0.05f, 0.04f, 0.82f, 0.97f, Color(0xFFF6F1E4)) // paper
        // Title + underline, then six lines of "handwriting" of varying length.
        val ink = Color(0xFF2B3A8C)
        rect(0.08f, 0.12f, 0.42f, 0.155f, ink)
        rect(0.08f, 0.165f, 0.44f, 0.172f, ink)
        val lengths = floatArrayOf(0.62f, 0.50f, 0.40f, 0.70f, 0.58f, 0.36f)
        lengths.forEachIndexed { i, right ->
            val top = 0.22f + i * 0.068f
            rect(0.08f, top, right, top + 0.03f, ink)
        }
        return bitmap
    }
}
