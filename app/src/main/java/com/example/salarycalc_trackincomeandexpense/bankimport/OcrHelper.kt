package com.example.salarycalc_trackincomeandexpense.bankimport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/**
 * OCR fallback for image-only / scanned bank statements.
 *
 * Renders every PDF page to a bitmap with the Android PdfRenderer and runs
 * ML Kit on-device text recognition over each page. The recognised text is
 * joined back into a single string so the rest of the pipeline is unchanged.
 */
object OcrHelper {

    private const val MAX_PAGES = 200
    private const val SCALE = 2 // render at 2x for better OCR accuracy

    /**
     * Returns recognised text, or null when nothing could be read.
     * Blocking — call from a background thread.
     */
    fun recognize(context: Context, file: File): String? {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val sb = StringBuilder()
        try {
            val pageCount = renderer.pageCount.coerceAtMost(MAX_PAGES)
            for (index in 0 until pageCount) {
                renderer.openPage(index).use { page ->
                    val width = (page.width * SCALE).coerceAtLeast(1)
                    val height = (page.height * SCALE).coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val result = com.google.android.gms.tasks.Tasks.await(recognizer.process(image))
                        if (result.text.isNotBlank()) {
                            sb.append(result.text).append("\n")
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } finally {
            try { renderer.close() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
        }
        val text = sb.toString().trim()
        return text.ifBlank { null }
    }
}
