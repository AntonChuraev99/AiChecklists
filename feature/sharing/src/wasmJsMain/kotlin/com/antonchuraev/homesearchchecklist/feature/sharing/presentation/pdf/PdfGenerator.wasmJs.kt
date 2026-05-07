@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.feature.sharing.presentation.pdf

import com.antonchuraev.homesearchchecklist.feature.sharing.domain.formatter.PdfContent
import com.antonchuraev.homesearchchecklist.feature.sharing.domain.formatter.PdfItem

// ---------------------------------------------------------------------------
// JS bridge — delegates to globalThis.__printHtml in init.js.template
// ---------------------------------------------------------------------------

@JsFun("""(html) => {
    try {
        globalThis.__printHtml(html);
    } catch (e) {
        console.error('[PdfGenerator] __printHtml call failed:', e);
    }
}""")
private external fun jsPrintHtml(html: String)

// ---------------------------------------------------------------------------
// HTML builder helpers
// ---------------------------------------------------------------------------

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

private fun buildPdfHtml(content: PdfContent, fileName: String): String = buildString {
    appendLine("<!DOCTYPE html>")
    appendLine("<html lang=\"en\">")
    appendLine("<head>")
    appendLine("<meta charset=\"UTF-8\">")
    appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
    appendLine("<title>${escapeHtml(fileName)}</title>")
    appendLine("<style>")
    appendLine("""
        * { box-sizing: border-box; margin: 0; padding: 0; }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
            font-size: 12pt;
            color: #212121;
            background: #ffffff;
            padding: 0;
        }

        .page {
            width: 210mm;
            min-height: 297mm;
            padding: 20mm 20mm 20mm 20mm;
            margin: 0 auto;
        }

        .title {
            font-size: 20pt;
            font-weight: 700;
            color: #212121;
            margin-bottom: 4pt;
            word-break: break-word;
        }

        .subtitle {
            font-size: 13pt;
            font-weight: 400;
            color: #757575;
            margin-bottom: 6pt;
            word-break: break-word;
        }

        .progress {
            font-size: 11pt;
            color: #2196F3;
            font-weight: 600;
            margin-bottom: 16pt;
        }

        .divider {
            border: none;
            border-top: 1px solid #E0E0E0;
            margin: 12pt 0;
        }

        .item {
            display: flex;
            align-items: flex-start;
            padding: 5pt 0;
            break-inside: avoid;
            page-break-inside: avoid;
        }

        .item-checkbox {
            font-family: monospace;
            font-size: 13pt;
            color: #212121;
            margin-right: 8pt;
            flex-shrink: 0;
            line-height: 1.4;
        }

        .item-checkbox.checked {
            color: #2196F3;
        }

        .item-content {
            flex: 1;
        }

        .item-text {
            font-size: 12pt;
            color: #212121;
            line-height: 1.4;
            word-break: break-word;
        }

        .item-text.checked {
            color: #757575;
            text-decoration: line-through;
        }

        .item-note {
            font-size: 10pt;
            color: #9E9E9E;
            font-style: italic;
            margin-top: 2pt;
            word-break: break-word;
        }

        @media print {
            @page {
                size: A4;
                margin: 20mm;
            }
            body {
                -webkit-print-color-adjust: exact;
                print-color-adjust: exact;
            }
            .page {
                padding: 0;
                width: 100%;
            }
            .item {
                break-inside: avoid;
                page-break-inside: avoid;
            }
        }
    """.trimIndent())
    appendLine("</style>")
    appendLine("</head>")
    appendLine("<body>")
    appendLine("<div class=\"page\">")

    // Title
    appendLine("<div class=\"title\">${escapeHtml(content.title)}</div>")

    // Subtitle (optional fill name)
    if (content.subtitle != null) {
        appendLine("<div class=\"subtitle\">${escapeHtml(content.subtitle)}</div>")
    }

    // Progress
    appendLine("<div class=\"progress\">${escapeHtml(content.progress)}</div>")

    appendLine("<hr class=\"divider\">")

    // Items
    content.items.forEach { item ->
        val checkedClass = if (item.checked) " checked" else ""
        val checkboxSymbol = if (item.checked) "☑" else "☐"
        appendLine("<div class=\"item\">")
        appendLine("<span class=\"item-checkbox$checkedClass\">$checkboxSymbol</span>")
        appendLine("<div class=\"item-content\">")
        appendLine("<div class=\"item-text$checkedClass\">${escapeHtml(item.text)}</div>")
        if (item.note != null) {
            appendLine("<div class=\"item-note\">${escapeHtml(item.note)}</div>")
        }
        appendLine("</div>")
        appendLine("</div>")
    }

    appendLine("</div>") // .page
    appendLine("</body>")
    appendLine("</html>")
}

// ---------------------------------------------------------------------------
// actual class
// ---------------------------------------------------------------------------

actual class PdfGenerator actual constructor() {

    /**
     * Renders [content] as an A4-styled HTML page and opens the browser print dialog.
     *
     * Returns "web-print://done" as a marker so ShareLauncher and ShareViewModel know
     * the print flow was initiated. A null return would signal failure.
     *
     * The actual PDF file is created by the user choosing "Save as PDF" in the browser
     * print dialog — we don't generate a binary PDF on the web target.
     */
    actual suspend fun generatePdf(content: PdfContent, fileName: String): String? {
        return runCatching {
            val html = buildPdfHtml(content, fileName)
            jsPrintHtml(html)
            "web-print://done"
        }.getOrNull()
    }
}
