package com.antonchuraev.homesearchchecklist.feature.analyze.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals

/** `input_type` on the wire must match the Cloud Function's accepted values exactly. */
class AiInputTypeWireTest {

    @Test
    fun pdfBase64_isPdfBase64OnTheWire() {
        assertEquals("pdf_base64", AiInputType.PDF_BASE64.wire)
    }

    @Test
    fun existingWireValues_areUnchanged() {
        assertEquals(
            mapOf(
                AiInputType.TEXT to "text",
                AiInputType.URL to "url",
                AiInputType.IMAGE_BASE64 to "image_base64",
                AiInputType.AUDIO_BASE64 to "audio_base64",
                AiInputType.PDF_BASE64 to "pdf_base64",
                AiInputType.NONE to "none",
            ),
            AiInputType.entries.associateWith { it.wire },
        )
    }
}
