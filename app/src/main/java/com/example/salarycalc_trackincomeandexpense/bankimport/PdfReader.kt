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
                // Keep text and positions separate by page. A statement may
                // mix selectable-text pages and image-only/scanned pages.
                val pageElements = mutableListOf<PdfTextElement>()
                val pageTexts = linkedMapOf<Int, String>()
                for (pageNumber in 1..doc.numberOfPages) {
                    val stripper = object : PDFTextStripper() {
                        override fun writeString(text: String, textPositions: List<TextPosition>) {
                            if (textPositions.isNotEmpty()) {
                                val first = textPositions.first()
                                val last = textPositions.last()
                                pageElements.add(
                                    PdfTextElement(
                                        text.trim(),
                                        first.xDirAdj,
                                        first.yDirAdj,
                                        (last.xDirAdj + last.widthOfSpace) - first.xDirAdj,
                                        textPositions.maxOfOrNull { it.heightDir } ?: 0f,
                                        pageNumber
                                    )
                                )
                            }
                            super.writeString(text, textPositions)
                        }
                    }.apply {
                        sortByPosition = true
                        startPage = pageNumber
                        endPage = pageNumber
                    }
                    pageTexts[pageNumber - 1] = stripper.getText(doc)
                }

                val scanPages = pageTexts.filterValues {
                    it.replace(Regex("\\s+"), " ").trim().length < MIN_TEXT_LENGTH
                }.keys.toSet()
                val ocrPages = if (scanPages.isNotEmpty()) {
                    OcrHelper.recognizePages(context, file, scanPages)
                } else emptyMap()

                // Never silently omit an unreadable page from a statement.
                val unreadable = scanPages.filter { index ->
                    ocrPages[index].isNullOrBlank() && pageTexts[index].isNullOrBlank()
                }
                if (unreadable.isNotEmpty()) {
                    return Result.Error(
                        "Could not read PDF page(s) ${unreadable.map { it + 1 }.joinToString(", ")}. " +
                            "Please use a clearer bank statement."
                    )
                }
                val text = pageTexts.keys.joinToString("\n") { page ->
                    ocrPages[page]?.takeIf { it.isNotBlank() } ?: pageTexts[page].orEmpty()
                }
                if (text.isBlank()) {
                    Result.Error("No statement text found in the PDF.")
                } else {
                    Result.Success(text, viaOcr = scanPages.isNotEmpty(), elements = pageElements.toList())
                }
            } finally {
                try { doc.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            ImportLogger.e("PDF load failed", e)
            Result.Error("Could not read this PDF. It may be corrupted or not a valid PDF file.")
        }
    }

}
