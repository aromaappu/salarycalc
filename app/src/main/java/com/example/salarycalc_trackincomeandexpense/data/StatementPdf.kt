package com.example.salarycalc_trackincomeandexpense.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates a plain table-style bank statement PDF (Date | Name | Credit | Debit)
 * for transactions in a date range and saves it to the Downloads folder.
 * Uses the same pdfbox-android library already bundled for statement parsing.
 */
object StatementPdf {

    // A4 page geometry
    private val PAGE = PDRectangle.A4
    private const val LEFT = 40f
    private const val RIGHT = 555f
    private const val BOTTOM = 60f
    private const val TOP = 745f
    private const val LINE_H = 15f
    private const val BODY_SIZE = 9f
    private const val HEADER_SIZE = 9f

    // Column x-positions (Credit and Debit are right-aligned to their edge)
    private const val X_DATE = 40f
    private const val X_NAME = 118f
    private const val NAME_WIDTH = 245f
    private const val X_CREDIT = 475f
    private const val X_DEBIT = 555f

    private val AMOUNT_FORMAT = NumberFormat.getNumberInstance(Locale("en", "IN"))

    data class Totals(val credit: Double, val debit: Double)

    /** Total credit (income) and total debit (expense) across a filtered list. */
    fun totals(transactions: List<Transaction>): Totals {
        var credit = 0.0
        var debit = 0.0
        transactions.forEach { t ->
            if (t.type == AppDatabase.TYPE_INCOME) credit += t.amount else debit += t.amount
        }
        return Totals(credit, debit)
    }

    /**
     * Renders a statement PDF for [transactions] between [fromDate] and [toDate]
     * and writes it to the public Downloads folder. Returns the saved filename,
     * or null on failure.
     */
    fun generate(
        context: Context,
        transactions: List<Transaction>,
        fromDate: String,
        toDate: String,
        netBalance: Double = totals(transactions).credit - totals(transactions).debit
    ): String? {
        if (transactions.isEmpty()) return null
        val filename = "SalaryCast_Statement_${fromDate}_${toDate}.pdf"
        return try {
            // Required before any pdfbox call (same as the statement parser).
            PDFBoxResourceLoader.init(context.applicationContext)
            val bytes = renderPdfBytes(transactions, fromDate, toDate, netBalance) ?: return null
            saveToDownloads(context, filename, bytes)
        } catch (e: Throwable) {
            null
        }
    }

    private fun saveToDownloads(context: Context, filename: String, bytes: ByteArray): String? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                filename
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file).use { it.write(bytes) }
                filename
            }
        } catch (e: Exception) {
            null
        }

    private fun renderPdfBytes(
        transactions: List<Transaction>,
        fromDate: String,
        toDate: String,
        netBalance: Double
    ): ByteArray? {
        return try {
            PDDocument().use { doc ->
                val writer = PdfWriter(doc)
                writer.onNewPage = { drawTableHeader(writer) }
                writer.title("BANK STATEMENT")
                writer.line(
                    "Period: ${displayDate(fromDate)}  to  ${displayDate(toDate)}",
                    PDType1Font.HELVETICA,
                    9f
                )
                writer.line(
                    "Generated on ${displayDate(Date())}  ·  ${transactions.size} transactions",
                    PDType1Font.HELVETICA,
                    9f
                )
                writer.line("", PDType1Font.HELVETICA, 8f)

                drawTableHeader(writer)

                transactions.forEach { t ->
                    drawRow(writer, t)
                }

                val totals = totals(transactions)
                // Totals block: page breaks here keep the plain totals layout
                // (no table header) so it reads like a statement footer.
                writer.onNewPage = {}
                writer.ensureSpace(LINE_H * 3f)
                writer.rule()
                writer.line("", PDType1Font.HELVETICA, 4f)
                writer.rightText(
                    "TOTAL CREDIT  ${amount(totals.credit)}",
                    PDType1Font.HELVETICA_BOLD,
                    HEADER_SIZE,
                    X_CREDIT
                )
                writer.rightText(
                    "TOTAL DEBIT  ${amount(totals.debit)}",
                    PDType1Font.HELVETICA_BOLD,
                    HEADER_SIZE,
                    X_DEBIT
                )
                writer.line("", PDType1Font.HELVETICA, 4f)
                writer.rule()
                writer.line("", PDType1Font.HELVETICA, 4f)
                writer.rightText(
                    "NET BALANCE  ${amount(netBalance)}",
                    PDType1Font.HELVETICA_BOLD,
                    HEADER_SIZE,
                    X_DEBIT
                )

                writer.close()
                val out = java.io.ByteArrayOutputStream()
                doc.save(out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun drawTableHeader(writer: PdfWriter) {
        writer.ensureSpace(LINE_H * 2f)
        writer.text("DATE", PDType1Font.HELVETICA_BOLD, HEADER_SIZE, X_DATE)
        writer.text("NAME", PDType1Font.HELVETICA_BOLD, HEADER_SIZE, X_NAME)
        writer.rightText("CREDIT", PDType1Font.HELVETICA_BOLD, HEADER_SIZE, X_CREDIT)
        writer.rightText("DEBIT", PDType1Font.HELVETICA_BOLD, HEADER_SIZE, X_DEBIT)
        writer.line("", PDType1Font.HELVETICA, 2f)
        writer.rule()
    }

    private fun drawRow(writer: PdfWriter, t: Transaction) {
        val lines = wrapText(
            sanitize(t.name),
            PDType1Font.HELVETICA,
            BODY_SIZE,
            NAME_WIDTH
        )
        writer.ensureSpace(LINE_H * lines.size + 2f)
        writer.text(displayDate(t.date), PDType1Font.HELVETICA, BODY_SIZE, X_DATE)
        if (t.type == AppDatabase.TYPE_INCOME) {
            writer.rightText(amount(t.amount), PDType1Font.HELVETICA, BODY_SIZE, X_CREDIT)
        } else {
            writer.rightText(amount(t.amount), PDType1Font.HELVETICA, BODY_SIZE, X_DEBIT)
        }
        lines.forEachIndexed { i, line ->
            writer.lineAt(i * LINE_H, X_NAME, PDType1Font.HELVETICA, BODY_SIZE, line)
        }
        repeat(lines.size) { writer.advance() }
    }

    private fun amount(value: Double): String {
        val formatted = AMOUNT_FORMAT.format(Math.abs(value))
        return if (value < 0) "-$formatted" else formatted
    }

    private fun displayDate(dateStr: String): String = try {
        SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(dateStr) ?: Date()
        )
    } catch (e: Exception) {
        dateStr
    }

    private fun displayDate(date: Date): String =
        SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(date)

    /** pdfbox Type1 fonts only encode ASCII — drop anything unprintable. */
    private fun sanitize(text: String): String {
        val sb = StringBuilder(text.length)
        text.forEach { c -> sb.append(if (c.code in 32..126) c else '?') }
        return sb.toString()
    }

    private fun wrapText(text: String, font: PDFont, size: Float, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var line = ""
        for (w in words) {
            val test = if (line.isEmpty()) w else "$line $w"
            if (font.getStringWidth(test) / 1000f * size <= maxWidth) {
                line = test
            } else {
                if (line.isNotEmpty()) lines.add(line)
                line = w
            }
        }
        if (line.isNotEmpty()) lines.add(line)
        return lines.ifEmpty { listOf("") }
    }

    /** Keeps a mutable cursor across pages; starts a fresh page when out of room. */
    private class PdfWriter(private val doc: PDDocument) {
        private var page: PDPage
        private var content: PDPageContentStream
        private var y = TOP

        /** Invoked right after a fresh page is started — used to repeat the table header. */
        var onNewPage: () -> Unit = {}

        init {
            page = PDPage(PAGE)
            doc.addPage(page)
            content = PDPageContentStream(doc, page)
        }

        fun ensureSpace(needed: Float) {
            if (y - needed < BOTTOM) {
                content.close()
                page = PDPage(PAGE)
                doc.addPage(page)
                content = PDPageContentStream(doc, page)
                y = TOP
                onNewPage()
            }
        }

        fun title(str: String) {
            ensureSpace(LINE_H)
            beginText(X_DATE, y, PDType1Font.HELVETICA_BOLD, 14f)
            content.showText(str)
            endText()
            y -= 18f
        }

        fun line(str: String, font: PDFont, size: Float) {
            ensureSpace(size + 6f)
            beginText(X_DATE, y, font, size)
            content.showText(str)
            endText()
            y -= size + 6f
        }

        fun lineAt(offset: Float, x: Float, font: PDFont, size: Float, str: String) {
            beginText(x, y - offset, font, size)
            content.showText(str)
            endText()
        }

        fun text(str: String, font: PDFont, size: Float, x: Float) {
            beginText(x, y, font, size)
            content.showText(str)
            endText()
        }

        fun rightText(str: String, font: PDFont, size: Float, rightEdge: Float) {
            val width = font.getStringWidth(str) / 1000f * size
            beginText(rightEdge - width, y, font, size)
            content.showText(str)
            endText()
        }

        fun advance() {
            y -= LINE_H
        }

        fun rule() {
            content.setStrokingColor(0.6f, 0.6f, 0.6f)
            content.moveTo(X_DATE, y - 2f)
            content.lineTo(RIGHT, y - 2f)
            content.stroke()
            y -= 6f
        }

        fun close() {
            content.close()
        }

        private fun beginText(x: Float, atY: Float, font: PDFont, size: Float) {
            content.beginText()
            content.setFont(font, size)
            content.newLineAtOffset(x, atY)
        }

        private fun endText() {
            content.endText()
        }
    }
}
