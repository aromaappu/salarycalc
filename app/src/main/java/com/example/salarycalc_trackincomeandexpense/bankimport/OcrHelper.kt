package com.example.salarycalc_trackincomeandexpense.bankimport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/**
 * On-device OCR. Can recognize only image-only pages of a mixed PDF rather than
 * discarding text from other pages or processing the first page alone.
 */
object OcrHelper {
    private const val MAX_PAGES = 200
    private const val SCALE = 2

    fun recognize(context: Context, file: File): String? =
        recognizePages(context, file, null).values.joinToString("\n").trim().ifBlank { null }

    /** Zero-based page indexes; null recognizes every page. */
    fun recognizePages(context: Context, file: File, pageIndexes: Set<Int>?): Map<Int, String> {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(pfd)
            try {
                val totalPages = renderer.pageCount
                if (totalPages > MAX_PAGES && pageIndexes == null) {
                    throw IllegalArgumentException("Too many scanned pages. Maximum is $MAX_PAGES.")
                }
                val selected = (pageIndexes ?: (0 until totalPages).toSet()).sorted()
                require(selected.all { it >= 0 && it < totalPages }) { "Invalid PDF page selection" }
                require(selected.size <= MAX_PAGES) { "Too many scanned pages. Maximum is $MAX_PAGES." }

                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    val result = linkedMapOf<Int, String>()
                    for (index in selected) {
                        renderer.openPage(index).use { page ->
                            val width = (page.width * SCALE).coerceAtLeast(1)
                            val height = (page.height * SCALE).coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val image = InputImage.fromBitmap(bitmap, 0)
                                val recognized = Tasks.await(recognizer.process(image)).text.trim()
                                result[index] = recognized
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                    return result
                } finally {
                    recognizer.close()
                }
            } finally {
                renderer.close()
            }
        } finally {
            pfd.close()
        }
    }
}
