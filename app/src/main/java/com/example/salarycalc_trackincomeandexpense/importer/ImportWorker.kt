package com.example.salarycalc_trackincomeandexpense.importer

import android.content.Context
import android.net.Uri
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.salarycalc_trackincomeandexpense.NotificationHelper
import com.example.salarycalc_trackincomeandexpense.bankimport.ImportRepository
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase

/**
 * Background importer used by [com.example.salarycalc_trackincomeandexpense.ImportActivity].
 *
 * Receives the picked file's URI via WorkManager input data, runs the full
 * import pipeline off the main thread, then reports the ImportSummary through
 * a system notification.
 */
class ImportWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val uriStr = inputData.getString("fileUri") ?: return Result.failure()
        val fileName = inputData.getString("sourceFilename") ?: "statement"
        val appContext = applicationContext
        val db = AppDatabase(appContext)

        return try {
            val uri = Uri.parse(uriStr)
            val mime = appContext.contentResolver.getType(uri)
            val isCsv = mime?.contains("csv") == true ||
                (mime == "text/plain" && uriStr.lowercase().contains(".csv"))

            val result = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                if (isCsv) ImportRepository.processCsv(stream, db, "", fileName)
                else ImportRepository.processPdf(appContext, stream, db, "", fileName)
            }

            when (result) {
                is ImportRepository.ImportResult.Ready -> {
                    val saved = ImportRepository.save(result.transactions, db)
                    db.addBankStatement(fileName, saved)
                    val summary = result.summary.copy(imported = saved, fileName = fileName)
                    val msg = "Bank: ${summary.bank} • Imported: ${summary.imported} • " +
                        "Duplicates: ${summary.duplicatesSkipped} • Errors: ${summary.errorsFound}"
                    NotificationHelper.showNotification(
                        appContext, 10001, "✅ Bank statement imported", msg
                    )
                    Result.success()
                }

                is ImportRepository.ImportResult.NeedsPreview -> {
                    NotificationHelper.showNotification(
                        appContext, 10002, "⚠️ Statement needs review",
                        "${result.transactions.size} transactions found but confidence is low. Open the app to review."
                    )
                    Result.success()
                }

                is ImportRepository.ImportResult.Error -> {
                    NotificationHelper.showNotification(
                        appContext, 10003, "❌ Import failed", result.message
                    )
                    Result.failure()
                }

                null -> Result.failure()
            }
        } catch (e: Exception) {
            NotificationHelper.showNotification(
                appContext, 10003, "❌ Import failed", "Something went wrong: ${e.message}"
            )
            Result.failure()
        }
    }
}
