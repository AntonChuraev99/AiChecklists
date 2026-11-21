package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ImageDownscaleMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JpegHeaderTest {

    @Test
    fun orientation_bigEndianExif_isRead() {
        assertEquals(6, JpegHeader.exifOrientation(jpegWithOrientation(6, littleEndian = false)))
    }

    @Test
    fun orientation_littleEndianExif_isRead() {
        assertEquals(8, JpegHeader.exifOrientation(jpegWithOrientation(8, littleEndian = true)))
    }

    @Test
    fun orientation_afterApp0AndFillBytes_isRead() {
        val app0 = bytes(0xFF, 0xE0, 0x00, 0x04, 0x4A, 0x46) // JFIF-like APP0, 2-byte payload
        val exif = jpegWithOrientation(3, littleEndian = false).copyOfRange(2, jpegWithOrientation(3, false).size)
        val file = bytes(0xFF, 0xD8) + app0 + bytes(0xFF) + exif
        assertEquals(3, JpegHeader.exifOrientation(file))
    }

    @Test
    fun orientation_noExif_isNull() {
        val file = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x04, 0x00, 0x00, 0xFF, 0xDA, 0x00, 0x02)
        assertNull(JpegHeader.exifOrientation(file))
    }

    @Test
    fun orientation_outOfRangeValue_isNull() {
        assertNull(JpegHeader.exifOrientation(jpegWithOrientation(9, littleEndian = false)))
    }

    @Test
    fun orientation_truncatedSegment_isNullNotCrash() {
        val full = jpegWithOrientation(6, littleEndian = false)
        for (cut in 3 until full.size) {
            // Every prefix must parse to null or the right value — never throw out of bounds.
            val value = JpegHeader.exifOrientation(full.copyOf(cut))
            assertTrue(value == null || value == 6, "cut=$cut gave $value")
        }
    }

    @Test
    fun orientation_png_isNull() {
        val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertFalse(JpegHeader.isJpeg(png))
        assertNull(JpegHeader.exifOrientation(png))
    }

    @Test
    fun passThrough_onlyForSmallUprightJpeg() {
        val small = assertNotNull(ImageDownscaleMath.plan(1600, 1200, 2048))
        val large = assertNotNull(ImageDownscaleMath.plan(4032, 3024, 2048))
        val upright = jpegWithOrientation(1, littleEndian = false)
        val noExif = bytes(0xFF, 0xD8, 0xFF, 0xDA, 0x00, 0x02)
        val rotated = jpegWithOrientation(6, littleEndian = true)
        val png = bytes(0x89, 0x50, 0x4E, 0x47)

        assertTrue(JpegHeader.canPassThrough(upright, small))
        assertTrue(JpegHeader.canPassThrough(noExif, small))
        assertFalse(JpegHeader.canPassThrough(rotated, small), "rotated JPEG must be re-encoded upright")
        assertFalse(JpegHeader.canPassThrough(png, small), "non-JPEG must be re-encoded as JPEG")
        assertFalse(JpegHeader.canPassThrough(upright, large), "oversized photo must be downscaled")
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    /** SOI + APP1("Exif\0\0" + TIFF header + IFD0 with one Orientation SHORT entry) + SOS. */
    private fun jpegWithOrientation(orientation: Int, littleEndian: Boolean): ByteArray {
        fun u16(v: Int) = if (littleEndian) bytes(v and 0xFF, v shr 8 and 0xFF) else bytes(v shr 8 and 0xFF, v and 0xFF)
        fun u32(v: Int) = if (littleEndian) {
            bytes(v and 0xFF, v shr 8 and 0xFF, v shr 16 and 0xFF, v ushr 24)
        } else {
            bytes(v ushr 24, v shr 16 and 0xFF, v shr 8 and 0xFF, v and 0xFF)
        }
        val tiff = (if (littleEndian) bytes(0x49, 0x49) else bytes(0x4D, 0x4D)) +
            u16(42) + u32(8) +
            u16(1) + // one IFD entry
            u16(0x0112) + u16(3) + u32(1) + u16(orientation) + u16(0) +
            u32(0) // next IFD
        val payload = bytes(0x45, 0x78, 0x69, 0x66, 0x00, 0x00) + tiff
        val length = payload.size + 2
        return bytes(0xFF, 0xD8, 0xFF, 0xE1, length shr 8, length and 0xFF) + payload + bytes(0xFF, 0xDA, 0x00, 0x02)
    }
}
