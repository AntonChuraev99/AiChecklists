package com.antonchuraev.homesearchchecklist.feature.sharing.presentation.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import com.antonchuraev.homesearchchecklist.feature.sharing.domain.formatter.PdfContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream

actual class PdfGenerator : KoinComponent {

    private val context: Context by inject()

    actual suspend fun generatePdf(content: PdfContent, fileName: String): String? {
        val noteLabel = getString(Res.string.pdf_note_label)
        return withContext(Dispatchers.IO) {
            try {
                val document = PdfDocument()

                // Page dimensions (A4-ish proportions, 72 DPI)
                val pageWidth = 595
                val pageHeight = 842

                // Paints
                val titlePaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 24f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                val subtitlePaint = Paint().apply {
                    color = Color.DKGRAY
                    textSize = 16f
                    isAntiAlias = true
                }

                val progressPaint = Paint().apply {
                    color = Color.parseColor("#2196F3")
                    textSize = 14f
                    isAntiAlias = true
                }

                val itemTextPaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 14f
                    isAntiAlias = true
                }

                val itemCheckedPaint = Paint().apply {
                    color = Color.GRAY
                    textSize = 14f
                    isAntiAlias = true
                }

                val notePaint = Paint().apply {
                    color = Color.DKGRAY
                    textSize = 12f
                    isAntiAlias = true
                }

                val checkboxPaint = Paint().apply {
                    color = Color.parseColor("#2196F3")
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    isAntiAlias = true
                }

                val checkboxFilledPaint = Paint().apply {
                    color = Color.parseColor("#2196F3")
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }

                val checkmarkPaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    isAntiAlias = true
                }

                // Calculate items per page
                val margin = 40f
                val headerHeight = 100f
                val itemHeight = 40f
                val itemsPerPage = ((pageHeight - margin * 2 - headerHeight) / itemHeight).toInt()

                // Split items into pages
                val itemChunks = content.items.chunked(itemsPerPage)
                val totalPages = itemChunks.size.coerceAtLeast(1)

                for ((pageIndex, items) in itemChunks.withIndex()) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas

                    var yOffset = margin

                    // Draw header on first page
                    if (pageIndex == 0) {
                        // Title
                        canvas.drawText(content.title, margin, yOffset + 24f, titlePaint)
                        yOffset += 36f

                        // Subtitle (fill name)
                        content.subtitle?.let {
                            canvas.drawText(it, margin, yOffset + 16f, subtitlePaint)
                            yOffset += 24f
                        }

                        // Progress
                        canvas.drawText(content.progress, margin, yOffset + 14f, progressPaint)
                        yOffset += 40f
                    } else {
                        yOffset += 20f
                    }

                    // Draw items
                    for (item in items) {
                        // Draw checkbox
                        val checkboxSize = 14f
                        val checkboxX = margin
                        val checkboxY = yOffset + 2f

                        if (item.checked) {
                            // Filled checkbox
                            canvas.drawRect(
                                checkboxX,
                                checkboxY,
                                checkboxX + checkboxSize,
                                checkboxY + checkboxSize,
                                checkboxFilledPaint
                            )
                            // Checkmark
                            canvas.drawLine(
                                checkboxX + 3f,
                                checkboxY + checkboxSize / 2,
                                checkboxX + checkboxSize / 3,
                                checkboxY + checkboxSize - 3f,
                                checkmarkPaint
                            )
                            canvas.drawLine(
                                checkboxX + checkboxSize / 3,
                                checkboxY + checkboxSize - 3f,
                                checkboxX + checkboxSize - 3f,
                                checkboxY + 3f,
                                checkmarkPaint
                            )
                        } else {
                            // Empty checkbox
                            canvas.drawRect(
                                checkboxX,
                                checkboxY,
                                checkboxX + checkboxSize,
                                checkboxY + checkboxSize,
                                checkboxPaint
                            )
                        }

                        // Draw item text
                        val textX = margin + checkboxSize + 12f
                        val textPaint = if (item.checked) itemCheckedPaint else itemTextPaint
                        canvas.drawText(item.text, textX, yOffset + 14f, textPaint)

                        yOffset += 20f

                        // Draw note if present
                        item.note?.let { note ->
                            canvas.drawText("$noteLabel $note", textX + 8f, yOffset + 12f, notePaint)
                            yOffset += 18f
                        }

                        yOffset += 8f
                    }

                    // Page number
                    if (totalPages > 1) {
                        val pageNumText = getString(Res.string.pdf_page_number, pageIndex + 1, totalPages)
                        val pageNumWidth = notePaint.measureText(pageNumText)
                        canvas.drawText(
                            pageNumText,
                            pageWidth - margin - pageNumWidth,
                            pageHeight - margin / 2,
                            notePaint
                        )
                    }

                    document.finishPage(page)
                }

                // Save to cache directory
                val cacheDir = File(context.cacheDir, "shared_pdfs").apply {
                    if (!exists()) mkdirs()
                }
                val file = File(cacheDir, "$fileName.pdf")

                FileOutputStream(file).use { outputStream ->
                    document.writeTo(outputStream)
                }
                document.close()

                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
