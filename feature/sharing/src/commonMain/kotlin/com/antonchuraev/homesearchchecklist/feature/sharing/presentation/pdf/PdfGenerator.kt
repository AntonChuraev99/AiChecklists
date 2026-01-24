package com.antonchuraev.homesearchchecklist.feature.sharing.presentation.pdf

import com.antonchuraev.homesearchchecklist.feature.sharing.domain.formatter.PdfContent

/**
 * Platform-specific PDF generator interface.
 * Creates PDF documents from checklist data.
 */
expect class PdfGenerator() {
    /**
     * Generates a PDF file from the given content.
     * @param content The structured content to render in the PDF
     * @param fileName The name of the PDF file (without extension)
     * @return The absolute path to the generated PDF file, or null if generation failed
     */
    suspend fun generatePdf(content: PdfContent, fileName: String): String?
}
