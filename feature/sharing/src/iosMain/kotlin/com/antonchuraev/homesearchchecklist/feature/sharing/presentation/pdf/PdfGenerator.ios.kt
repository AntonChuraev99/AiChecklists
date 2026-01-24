package com.antonchuraev.homesearchchecklist.feature.sharing.presentation.pdf

import com.antonchuraev.homesearchchecklist.feature.sharing.domain.formatter.PdfContent
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGContextSetStrokeColorWithColor
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextStrokeRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSAttributedString
import platform.Foundation.create
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginPDFContextToFile
import platform.UIKit.UIGraphicsBeginPDFPageWithInfo
import platform.UIKit.UIGraphicsEndPDFContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.drawInRect

@OptIn(ExperimentalForeignApi::class)
actual class PdfGenerator {

    actual suspend fun generatePdf(content: PdfContent, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Get cache directory
                val paths = NSSearchPathForDirectoriesInDomains(
                    NSCachesDirectory,
                    NSUserDomainMask,
                    true
                )
                val cacheDir = (paths.firstOrNull() as? String) ?: return@withContext null

                // Create subdirectory for shared PDFs
                val pdfDir = "$cacheDir/shared_pdfs"
                NSFileManager.defaultManager.createDirectoryAtPath(
                    pdfDir,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null
                )

                val filePath = "$pdfDir/$fileName.pdf"

                // Page dimensions (A4)
                val pageWidth = 595.0
                val pageHeight = 842.0
                val pageRect = CGRectMake(0.0, 0.0, pageWidth, pageHeight)

                // Start PDF context
                UIGraphicsBeginPDFContextToFile(filePath, pageRect, null)

                // Begin first page
                UIGraphicsBeginPDFPageWithInfo(pageRect, null)

                val context = UIGraphicsGetCurrentContext() ?: run {
                    UIGraphicsEndPDFContext()
                    return@withContext null
                }

                val margin = 40.0
                var yOffset = margin

                // Fonts
                val titleFont = UIFont.boldSystemFontOfSize(24.0)
                val subtitleFont = UIFont.systemFontOfSize(16.0)
                val progressFont = UIFont.systemFontOfSize(14.0)
                val itemFont = UIFont.systemFontOfSize(14.0)
                val noteFont = UIFont.systemFontOfSize(12.0)

                // Colors
                val blackColor = UIColor.blackColor
                val grayColor = UIColor.grayColor
                val blueColor = UIColor.colorWithRed(0.129, 0.588, 0.953, 1.0) // #2196F3

                // Draw title
                drawAttributedText(content.title, margin, yOffset, titleFont, blackColor, pageWidth - margin * 2)
                yOffset += 36.0

                // Draw subtitle
                content.subtitle?.let {
                    drawAttributedText(it, margin, yOffset, subtitleFont, grayColor, pageWidth - margin * 2)
                    yOffset += 24.0
                }

                // Draw progress
                drawAttributedText(content.progress, margin, yOffset, progressFont, blueColor, pageWidth - margin * 2)
                yOffset += 40.0

                // Draw items
                val checkboxSize = 14.0
                for (item in content.items) {
                    // Check if we need a new page
                    if (yOffset > pageHeight - margin - 60) {
                        UIGraphicsBeginPDFPageWithInfo(pageRect, null)
                        yOffset = margin
                    }

                    // Draw checkbox
                    val checkboxRect = CGRectMake(margin, yOffset, checkboxSize, checkboxSize)

                    if (item.checked) {
                        // Filled checkbox
                        CGContextSetFillColorWithColor(context, blueColor.CGColor)
                        CGContextFillRect(context, checkboxRect)
                    } else {
                        // Empty checkbox
                        CGContextSetStrokeColorWithColor(context, blueColor.CGColor)
                        CGContextStrokeRect(context, checkboxRect)
                    }

                    // Draw item text
                    val textX = margin + checkboxSize + 12.0
                    val textColor = if (item.checked) grayColor else blackColor
                    drawAttributedText(item.text, textX, yOffset, itemFont, textColor, pageWidth - textX - margin)
                    yOffset += 22.0

                    // Draw note if present
                    item.note?.let { note ->
                        drawAttributedText("Note: $note", textX + 8.0, yOffset, noteFont, grayColor, pageWidth - textX - margin - 8.0)
                        yOffset += 18.0
                    }

                    yOffset += 8.0
                }

                UIGraphicsEndPDFContext()

                filePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun drawAttributedText(
        text: String,
        x: Double,
        y: Double,
        font: UIFont,
        color: UIColor,
        maxWidth: Double
    ) {
        val attributes = mapOf<Any?, Any?>(
            NSFontAttributeName to font,
            NSForegroundColorAttributeName to color
        )
        val attrString = NSAttributedString.create(string = text, attributes = attributes)
        val rect = CGRectMake(x, y, maxWidth, 100.0)
        attrString.drawInRect(rect)
    }
}
