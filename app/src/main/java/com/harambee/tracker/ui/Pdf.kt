package com.harambee.tracker.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File

/** Writes plain text (a list or report) to an A4 PDF that can be shared or printed. */
object ReportPdf {
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 48f
    private const val LINE = 16f

    fun create(context: Context, fileName: String, title: String, text: String): File {
        val doc = PdfDocument()
        val body = Paint().apply { textSize = 11f; isAntiAlias = true }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val heading = Paint(bold).apply { textSize = 16f }
        val maxWidth = WIDTH - 2 * MARGIN

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageNumber).create())
            y = MARGIN
            if (pageNumber == 1) {
                page!!.canvas.drawText(title, MARGIN, y + 6, heading)
                y += LINE * 2
            }
            page!!.canvas.drawText("Page $pageNumber", WIDTH - MARGIN - 40, HEIGHT - MARGIN / 2, body)
        }
        newPage()

        for (raw in text.lines()) {
            // WhatsApp *bold* and _italic_ markers become bold text.
            val isBold = raw.trim().startsWith("*") && raw.trim().endsWith("*") && raw.trim().length > 1
            val line = raw.replace("*", "").replace(Regex("(^|\\s)_|_($|\\s)"), "$1$2")
            val paint = if (isBold) bold else body
            for (wrapped in wrap(line, paint, maxWidth)) {
                if (y > HEIGHT - MARGIN - LINE) newPage()
                page!!.canvas.drawText(wrapped, MARGIN, y, paint)
                y += LINE
            }
        }
        page?.let { doc.finishPage(it) }

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun wrap(line: String, paint: Paint, maxWidth: Float): List<String> {
        if (line.isEmpty() || paint.measureText(line) <= maxWidth) return listOf(line)
        val out = mutableListOf<String>()
        var current = ""
        for (word in line.split(" ")) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                out += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) out += current
        return out
    }
}
