package com.antonchuraev.homesearchchecklist.feature.sharing.presentation.pdf

import com.antonchuraev.homesearchchecklist.feature.sharing.domain.formatter.PdfContent

/**
 * Web stub for PDF generator. PDF generation is not supported on the web target.
 * A proper web implementation would use a JS PDF library via JavaScript interop.
 */
actual class PdfGenerator actual constructor() {
    actual suspend fun generatePdf(content: PdfContent, fileName: String): String? = null
}
