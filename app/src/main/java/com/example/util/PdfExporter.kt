package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.Note
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    fun exportNoteToPdf(
        context: Context,
        note: Note,
        onComplete: (File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val pdfDocument = PdfDocument()
            
            // Standard A4 Size: 595 x 842 points
            val pageWidth = 595
            val pageHeight = 842
            val margin = 40f
            val printableWidth = pageWidth - (margin * 2)
            
            // Initialize Paints
            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 12f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }
            
            val h1Paint = Paint().apply {
                color = Color.BLACK
                textSize = 20f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }

            val h2Paint = Paint().apply {
                color = Color.BLACK
                textSize = 15f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }

            val metaPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
                isAntiAlias = true
            }

            // Word wrap content
            val textLines = mutableListOf<WrappedLine>()
            
            // Add Title block
            textLines.add(WrappedLine(note.title, h1Paint, spacingBefore = 10f, spacingAfter = 10f))
            
            val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(note.lastUpdated))
            val metaInfo = "Category: ${note.category}  |  Last Updated: $dateStr"
            textLines.add(WrappedLine(metaInfo, metaPaint, spacingBefore = 2f, spacingAfter = 15f))
            textLines.add(WrappedLine("divider", linePaint, isDivider = true, spacingAfter = 15f))
            
            // Parse and add markdown body (rough conversion for print)
            for (line in note.content.lines()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("# ") -> {
                        val txt = trimmed.removePrefix("# ")
                        val wrapped = wrapText(txt, h1Paint, printableWidth)
                        wrapped.forEach { textLines.add(WrappedLine(it, h1Paint, spacingBefore = 10f, spacingAfter = 5f)) }
                    }
                    trimmed.startsWith("## ") -> {
                        val txt = trimmed.removePrefix("## ")
                        val wrapped = wrapText(txt, h2Paint, printableWidth)
                        wrapped.forEach { textLines.add(WrappedLine(it, h2Paint, spacingBefore = 8f, spacingAfter = 4f)) }
                    }
                    trimmed.startsWith("### ") -> {
                        val txt = trimmed.removePrefix("### ")
                        val wrapped = wrapText(trimmed, h2Paint, printableWidth)
                        wrapped.forEach { textLines.add(WrappedLine(it, h2Paint, spacingBefore = 6f, spacingAfter = 3f)) }
                    }
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                        val txt = "• " + (if (trimmed.startsWith("- ")) trimmed.removePrefix("- ") else trimmed.removePrefix("* "))
                        val wrapped = wrapText(txt, textPaint, printableWidth - 15f)
                        wrapped.forEachIndexed { i, piece ->
                            val bulletText = if (i == 0) piece else "  $piece"
                            textLines.add(WrappedLine(bulletText, textPaint, spacingAfter = 2f))
                        }
                    }
                    trimmed.startsWith("> ") -> {
                        val txt = trimmed.removePrefix("> ")
                        val wrapped = wrapText(txt, metaPaint, printableWidth - 20f)
                        wrapped.forEach { textLines.add(WrappedLine("  $it", metaPaint, spacingAfter = 3f, isQuoteBlock = true)) }
                    }
                    else -> {
                        val wrapped = wrapText(line, textPaint, printableWidth)
                        if (wrapped.isEmpty()) {
                            textLines.add(WrappedLine("", textPaint, spacingAfter = 6f))
                        } else {
                            wrapped.forEach { textLines.add(WrappedLine(it, textPaint, spacingAfter = 2f)) }
                        }
                    }
                }
            }

            // Paging engine
            var currentPageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            
            var yPosition = margin + 20f
            
            // Draw Header
            drawHeader(canvas, pageWidth, margin, "NoteHub | Sync & Study Companion")

            for (wrappedLine in textLines) {
                // Determine item total height
                val lineSpacingNeeded = wrappedLine.spacingBefore + wrappedLine.paint.textSize + wrappedLine.spacingAfter
                
                // If content overflows page, start a new page
                if (yPosition + lineSpacingNeeded > pageHeight - margin - 30f) {
                    drawFooter(canvas, pageWidth, pageHeight, margin, currentPageNumber)
                    pdfDocument.finishPage(page)
                    
                    currentPageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPosition = margin + 20f
                    drawHeader(canvas, pageWidth, margin, "NoteHub | Sync & Study Companion")
                }
                
                yPosition += wrappedLine.spacingBefore
                
                if (wrappedLine.isDivider) {
                    canvas.drawLine(margin, yPosition, pageWidth - margin, yPosition, linePaint)
                } else if (wrappedLine.text.isNotEmpty()) {
                    if (wrappedLine.isQuoteBlock) {
                        // draw quotes background border
                        val accentColorPaint = Paint().apply {
                            color = Color.GRAY
                            strokeWidth = 3f
                        }
                        canvas.drawLine(margin, yPosition - wrappedLine.paint.textSize, margin, yPosition + 4f, accentColorPaint)
                        canvas.drawText(wrappedLine.text, margin + 10f, yPosition, wrappedLine.paint)
                    } else {
                        canvas.drawText(wrappedLine.text, margin, yPosition, wrappedLine.paint)
                    }
                    yPosition += wrappedLine.paint.textSize
                } else {
                    yPosition += 8f // standard empty line gap
                }
                
                yPosition += wrappedLine.spacingAfter
            }
            
            // Draw footer on last page and seal PDF
            drawFooter(canvas, pageWidth, pageHeight, margin, currentPageNumber)
            pdfDocument.finishPage(page)
            
            // Save to Cache Directory
            val cleanTitle = note.title.filter { it.isLetterOrDigit() || it == ' ' }.trim().replace(" ", "_")
            val fileName = "Note_${cleanTitle}_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()
            
            onComplete(file)
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        if (text.isEmpty()) return lines
        
        var start = 0
        while (start < text.length) {
            val count = paint.breakText(text, start, text.length, true, maxWidth, null)
            if (count <= 0) break
            
            var end = start + count
            if (end < text.length && text[end] != ' ' && text[end - 1] != ' ') {
                val lastSpace = text.lastIndexOf(' ', end)
                if (lastSpace > start) {
                    end = lastSpace + 1
                }
            }
            lines.add(text.substring(start, end).trimEnd())
            start = end
        }
        return lines
    }

    private fun drawHeader(canvas: Canvas, pageWidth: Int, margin: Float, text: String) {
        val headerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText(text, margin, margin - 10f, headerPaint)
        
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.5f
        }
        canvas.drawLine(margin, margin - 5f, pageWidth - margin, margin - 5f, linePaint)
    }

    private fun drawFooter(canvas: Canvas, pageWidth: Int, pageHeight: Int, margin: Float, pageNum: Int) {
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.5f
        }
        canvas.drawLine(margin, pageHeight - margin + 5f, pageWidth - margin, pageHeight - margin + 5f, linePaint)
        canvas.drawText("Page $pageNum", pageWidth - margin - 30f, pageHeight - margin + 18f, footerPaint)
        canvas.drawText("Generated by NoteHub App", margin, pageHeight - margin + 18f, footerPaint)
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Note via Share"))
    }

    data class WrappedLine(
        val text: String,
        val paint: Paint,
        val isDivider: Boolean = false,
        val isQuoteBlock: Boolean = false,
        val spacingBefore: Float = 0f,
        val spacingAfter: Float = 0f
    )
}
