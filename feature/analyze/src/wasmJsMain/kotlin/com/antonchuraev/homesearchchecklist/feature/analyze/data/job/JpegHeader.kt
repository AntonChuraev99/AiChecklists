package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.DownscalePlan

/**
 * Header-only facts about a picked photo that decide whether [WasmImageDownscaler] may hand the
 * original bytes back untouched. Pure (no browser API), so it is unit-tested in wasmJsTest.
 *
 * The browser decodes with `imageOrientation: "from-image"`, so the pixels it hands back are already
 * rotated — but the ORIGINAL bytes are only safe to pass through when they need no rotation at all:
 * a server that ignores the EXIF tag would otherwise read a sideways receipt.
 */
internal object JpegHeader {

    /** EXIF `Orientation` value meaning "stored upright" (also what a missing tag means). */
    const val ORIENTATION_NORMAL: Int = 1

    private const val MARKER_PREFIX = 0xFF
    private const val SOI = 0xD8
    private const val EOI = 0xD9
    private const val SOS = 0xDA
    private const val APP1 = 0xE1
    private const val TEM = 0x01
    private const val RST0 = 0xD0
    private const val RST7 = 0xD7
    private const val TAG_ORIENTATION = 0x0112
    private const val TYPE_SHORT = 3
    private const val TIFF_MAGIC = 42
    private const val IFD_ENTRY_SIZE = 12

    fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes.u8(0) == MARKER_PREFIX && bytes.u8(1) == SOI && bytes.u8(2) == MARKER_PREFIX

    /**
     * EXIF orientation (1..8) of a JPEG, or null when the file is not a JPEG, carries no EXIF block,
     * no orientation tag, or a malformed header. Never throws: every read is bounds-checked.
     */
    fun exifOrientation(bytes: ByteArray): Int? {
        if (!isJpeg(bytes)) return null
        var offset = 2
        while (offset + 4 <= bytes.size) {
            if (bytes.u8(offset) != MARKER_PREFIX) return null
            val marker = bytes.u8(offset + 1)
            when {
                // Fill byte before a marker.
                marker == MARKER_PREFIX -> { offset += 1; continue }
                // Stand-alone markers carry no length.
                marker == SOI || marker == TEM || marker in RST0..RST7 -> { offset += 2; continue }
                // Image data starts / file ends: EXIF (APP1) always precedes these.
                marker == SOS || marker == EOI -> return null
            }
            val segmentLength = bytes.u16be(offset + 2)
            if (segmentLength < 2) return null
            val segmentStart = offset + 4
            val segmentEnd = offset + 2 + segmentLength
            if (segmentEnd > bytes.size) return null
            if (marker == APP1 && isExifSegment(bytes, segmentStart, segmentEnd)) {
                return readTiffOrientation(bytes, tiffStart = segmentStart + 6, end = segmentEnd)
            }
            offset = segmentEnd
        }
        return null
    }

    /**
     * True when [WasmImageDownscaler] may return the input as-is: no resize needed, already JPEG and
     * already upright. Anything else (a PNG/WebP/HEIC, an oversized photo, a rotated JPEG) is re-encoded.
     */
    fun canPassThrough(bytes: ByteArray, plan: DownscalePlan): Boolean =
        !plan.needsResize &&
            isJpeg(bytes) &&
            (exifOrientation(bytes) ?: ORIENTATION_NORMAL) == ORIENTATION_NORMAL

    private fun isExifSegment(bytes: ByteArray, start: Int, end: Int): Boolean =
        end - start >= 6 &&
            bytes.u8(start) == 'E'.code && bytes.u8(start + 1) == 'x'.code &&
            bytes.u8(start + 2) == 'i'.code && bytes.u8(start + 3) == 'f'.code &&
            bytes.u8(start + 4) == 0 && bytes.u8(start + 5) == 0

    private fun readTiffOrientation(bytes: ByteArray, tiffStart: Int, end: Int): Int? {
        if (tiffStart + 8 > end) return null
        val littleEndian = when {
            bytes.u8(tiffStart) == 'I'.code && bytes.u8(tiffStart + 1) == 'I'.code -> true
            bytes.u8(tiffStart) == 'M'.code && bytes.u8(tiffStart + 1) == 'M'.code -> false
            else -> return null
        }
        if (bytes.u16(tiffStart + 2, littleEndian) != TIFF_MAGIC) return null
        val ifdOffset = bytes.u32(tiffStart + 4, littleEndian)
        if (ifdOffset < 8 || ifdOffset > end - tiffStart - 2) return null
        val ifdStart = tiffStart + ifdOffset.toInt()
        val entryCount = bytes.u16(ifdStart, littleEndian)
        for (i in 0 until entryCount) {
            val entry = ifdStart + 2 + i * IFD_ENTRY_SIZE
            if (entry + IFD_ENTRY_SIZE > end) return null
            if (bytes.u16(entry, littleEndian) != TAG_ORIENTATION) continue
            if (bytes.u16(entry + 2, littleEndian) != TYPE_SHORT) return null
            val value = bytes.u16(entry + 8, littleEndian)
            return value.takeIf { it in 1..8 }
        }
        return null
    }

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

    private fun ByteArray.u16be(index: Int): Int = (u8(index) shl 8) or u8(index + 1)

    private fun ByteArray.u16(index: Int, littleEndian: Boolean): Int =
        if (littleEndian) u8(index) or (u8(index + 1) shl 8) else u16be(index)

    private fun ByteArray.u32(index: Int, littleEndian: Boolean): Long {
        val b0 = u8(index).toLong()
        val b1 = u8(index + 1).toLong()
        val b2 = u8(index + 2).toLong()
        val b3 = u8(index + 3).toLong()
        return if (littleEndian) {
            b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        } else {
            (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }
    }
}
