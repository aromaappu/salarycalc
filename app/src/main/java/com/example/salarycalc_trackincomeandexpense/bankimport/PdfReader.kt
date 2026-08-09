package com.example.salarycalc_trackincomeandexpense.bankimport

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/**
 * PDF → plain text conversion.
 *
 * Handles the error cases the import pipeline must not crash on:
 *  - encrypted / password-protected PDFs
 *  - corrupted / non-PDF files
 *  - image-only / scanned PDFs (falls back to on-device OCR)
 *
 * Works on a local file so the same bytes can be handed to the OCR renderer.
 */
object PdfReader {

    data class PdfTextElement(
        val text: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val pageNumber: Int
    )

    sealed class Result {
        data class Success(val text: String, val viaOcr: Boolean, val elements: List<PdfTextElement> = emptyList()) : Result()
        data class Error(val message: String) : Result()
    }

    // Below this much text a PDF is almost certainly image-only, not a text statement
    private const val MIN_TEXT_LENGTH = 40

    fun extract(context: Context, file: File): Result {
        PDFBoxResourceLoader.init(context.applicationContext)
        return try {
            val doc = PDDocument.load(file)
            try {
                if (doc.isEncrypted) {
                    return Result.Error("This PDF is password-protected. Please unlock it and try again.")
                }
                if (doc.numberOfPages == 0) {
                    return Result.Error("This PDF contains no pages.")
                }
                val stripper = object : PDFTextStripper() {
                    override fun writeString(text: String, textPositions: List<TextPosition>) {
                        if (textPositions.isEmpty()) return
                        val first = textPositions.first()
                        val last = textPositions.last()
                        val x = first.xDirAdj
                        val y = first.yDirAdj
                        val width = (last.xDirAdj + last.widthOfSpace) - x
                        val height = (textPositions.maxOfOrNull { it.heightDir } ?: 0f)
                        val page = currentPageNo
                        elements.add(PdfTextElement(text.trim(), x, y, width, height, page))
                        super.writeString(text, textPositions)
                    }
                }.apply { sortByPosition = true }
                val text = stripper.getText(doc)
                val stripped = text.replace(Regex("\\s+"), " ").trim()

                if (stripped.length < MIN_TEXT_LENGTH) {
                    // No selectable text → scanned/image-only → OCR fallback
                    ImportLogger.d("PDF has no selectable text (${stripped.length} chars) — running OCR")
                    val ocrText = OcrHelper.recognize(context, file)
                    if (ocrText.isNullOrBlank()) {
                        Result.Error("No text could be read from this PDF. It may be a low-quality scanned document.")
                    } else {
                        Result.Success(ocrText, viaOcr = true)
                    }
                } else {
                    Result.Success(text, viaOcr = false)
                }
            } finally {
                try { doc.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            ImportLogger.e("PDF load failed", e)
            Result.Error("Could not read this PDF. It may be corrupted or not a valid PDF file.")
        }
    }

    private val elements = mutableListOf<PdfTextElement>()

    fun resetElements() {
        elements.clear()
    }
}
