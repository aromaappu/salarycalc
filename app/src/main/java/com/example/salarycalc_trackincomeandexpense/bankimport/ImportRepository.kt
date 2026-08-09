package com.example.salarycalc_trackincomeandexpense.bankimport

import android.content.Context
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import java.io.File
import java.io.InputStream
import java.math.BigDecimal

/**
 * Orchestrates the full import pipeline. Everything runs on-device:
 *
 *     1. Store uploaded file
 *     2. PDF → plain text (OCR fallback for scanned PDFs)
 *     3. Clean the text
 *     4. Detect bank format
 *     5. Extract transactions (bank-specific regex + universal parser)
 *     6. Validate data
 *     7. Generate the standard CSV
 *     8. Read the CSV back (CSV → transactions)
 *     9. Remove duplicates against the database
 *    10. Save into the database (done by the caller)
 *
 * The caller (UI screen or background worker) then shows the ImportSummary.
 */
object ImportRepository {

    private const val TAG = "ImportRepository"
    private const val MAX_IMPORT_BYTES = 30L * 1024 * 1024 // 30 MB upload limit
    private const val CONFIDENCE_AUTO_IMPORT = 0.90f

    /** Counts surfaced in the "Import Summary" screen after import. */
    data class ImportSummary(
        val fileName: String = "statement",
        val bank: String = "UNKNOWN",
        val viaOcr: Boolean = false,
        val txtGenerated: Boolean = true,
        val transactionsFound: Int = 0,
        val imported: Int = 0,
        val duplicatesSkipped: Int = 0,
        val errorsFound: Int = 0,
        val closingBalance: Double = 0.0,
        val source: String = ""
    )

    sealed class ImportResult {
        /** Ready to save automatically — confidence is high. */
        data class Ready(
            val transactions: List<ParsedTransaction>,
            val summary: ImportSummary
        ) : ImportResult()

        /** Confidence is low — the user should review the parsed rows first. */
        data class NeedsPreview(
            val transactions: List<ParsedTransaction>,
            val bank: String,
            val confidence: Float,
            val source: String,
            val closingBalance: Double = 0.0
        ) : ImportResult()

        data class Error(val message: String) : ImportResult()
    }

    // ── Public entry points ───────────────────────────────────────────────────

    fun processPdf(
        context: Context,
        stream: InputStream,
        db: AppDatabase,
        apiKey: String,
        fileName: String = "statement"
    ): ImportResult {
        val file = copyToTemp(context, stream)
            ?: return ImportResult.Error("File is too large. Maximum size is 30 MB.")

        val extract = PdfReader.extract(context, file)
        val rawText = when (extract) {
            is PdfReader.Result.Error -> return ImportResult.Error(extract.message)
            is PdfReader.Result.Success -> extract.text
        }
        file.delete()
        return when (extract) {
            is PdfReader.Result.Success -> {
                val closing = TextCleaner.closingBalance(rawText)
                val coordinateParser = CoordinateStatementParser()
                val parsedRows = coordinateParser.parse(extract.elements)
                val coordinateTxs = parsedRows.mapNotNull { row ->
                    if (row.date == null || row.description.isBlank() || (row.debit == null && row.credit == null)) return@mapNotNull null
                    val amount = (row.debit ?: row.credit ?: BigDecimal.ZERO)
                    val type = if (row.debit != null) "DEBIT" else "CREDIT"
                    val hash = HashGenerator.generate(row.date, amount.toDouble(), row.description, "")
                    ParsedTransaction(
                        row.date,
                        row.description,
                        amount.toDouble(),
                        type,
                        row.balance?.toDouble() ?: 0.0,
                        "",
                        hash,
                        confidence = 0.93f
                    )
                }
                if (coordinateTxs.isNotEmpty()) {
                    val validated = TransactionValidator.validate(coordinateTxs)
                    val deduped = DuplicateChecker.dedupe(validated.valid, db)
                    return finalize(deduped.unique, "COORDINATE PDF", db, "PDF", fileName, viaOcr = extract.viaOcr, closingOverride = closing)
                }
                processText(rawText, db, apiKey, "PDF", fileName, viaOcr = extract.viaOcr, closingOverride = closing)
            }
            is PdfReader.Result.Error -> ImportResult.Error(extract.message)
        }
    }

    fun processCsv(
        stream: InputStream,
        db: AppDatabase,
        apiKey: String,
        fileName: String = "statement"
    ): ImportResult {
        val rawText = try {
            stream.bufferedReader(Charsets.UTF_8).readText()
        } catch (e: Exception) {
            return ImportResult.Error("Could not read CSV: ${e.message}")
        }

        // Try the CSV reader first (already in the standard CSV format)
        val csvTxs = try { CsvReader.read(rawText.byteInputStream(Charsets.UTF_8)) } catch (_: Exception) { emptyList() }
        if (csvTxs.isNotEmpty()) {
            val bank = BankDetector.detect(rawText).displayName
            return finalize(csvTxs, bank, db, "CSV", fileName, viaOcr = false, closingOverride = TextCleaner.closingBalance(rawText))
        }
        return processText(rawText, db, apiKey, "CSV", fileName, viaOcr = false, closingOverride = TextCleaner.closingBalance(rawText))
    }

    /**
     * Saves a list of transactions to the database (creating income sources /
     * expense categories on the fly). Returns the number actually inserted.
     */
    fun save(txs: List<ParsedTransaction>, db: AppDatabase): Int {
        var saved = 0
        val incomeSources    = db.getIncomeSources().toHashSet()
        val expenseCategories = db.getExpenseCategories().toHashSet()

        for (tx in txs) {
            val type = if (tx.type == "CREDIT") AppDatabase.TYPE_INCOME else AppDatabase.TYPE_EXPENSE
            val rawName = tx.description
            // Any parser that kept raw narration (hex / timestamps / refs) gets the
            // same accurate party name used by the universal parser.
            val name = if (NarrationReader.containsRawNarration(rawName)) {
                NarrationReader.cleanNarration(rawName)
            } else {
                rawName.ifEmpty { if (tx.type == "CREDIT") "Credit" else "Expense" }
            }
            if (type == AppDatabase.TYPE_INCOME) {
                if (!incomeSources.contains(name)) { db.addIncomeSource(name); incomeSources.add(name) }
            } else {
                if (!expenseCategories.contains(name)) { db.addExpenseCategory(name); expenseCategories.add(name) }
            }
            val id = db.addTransactionWithHash(type, tx.amount, name, tx.date, tx.hash)
            if (id > 0) saved++
        }
        return saved
    }

    // ── Internal pipeline ─────────────────────────────────────────────────────

    private fun processText(
        rawText: String,
        db: AppDatabase,
        apiKey: String,
        sourceHint: String,
        fileName: String,
        viaOcr: Boolean,
        closingOverride: Double? = null
    ): ImportResult {
        val cleaned = TextCleaner.clean(rawText)
        val bank = BankDetector.detect(cleaned)
        ImportLogger.d("Bank detected: ${bank.displayName}")

        // Step 5: bank-specific regex parser + position-independent universal parser
        val bankResult = ParserRegistry.get(bank).parse(cleaned)
        val universalResult = UniversalParser().parse(cleaned, TextCleaner.openingBalance(rawText))
        val (parsed, source, confidence) = pickBest(bankResult, universalResult)
        ImportLogger.d("Parsed ${parsed.size} txs via $source (confidence=$confidence)")

        if (parsed.isEmpty()) {
            return ImportResult.Error(
                "No transactions found. Try a different statement, or check that the PDF is a statement of account."
            )
        }

        val validated = TransactionValidator.validate(parsed)
        val deduped = DuplicateChecker.dedupe(validated.valid, db)

        if (confidence >= CONFIDENCE_AUTO_IMPORT && !viaOcr) {
            return finalize(deduped.unique, bank.displayName, db, source, fileName, viaOcr, closingOverride)
        }

        // OCR or low confidence transactions must be reviewed before saving
        return ImportResult.NeedsPreview(
            transactions = deduped.unique,
            bank = bank.displayName,
            confidence = confidence,
            source = source,
            closingBalance = closingOverride ?: closingBalance(deduped.unique)
        )
    }

    /**
     * Validation → standard CSV → read CSV back → duplicate removal.
     * The CSV round-trip guarantees the exact bytes that the import step will
     * consume, so the DB always sees one canonical format.
     */
    private fun finalize(
        txs: List<ParsedTransaction>,
        bank: String,
        db: AppDatabase,
        source: String,
        fileName: String,
        viaOcr: Boolean,
        closingOverride: Double? = null
    ): ImportResult {
        val validated = TransactionValidator.validate(txs)
        val csv = CsvGenerator.generate(validated.valid)
        val roundTrip = CsvReader.read(csv.byteInputStream(Charsets.UTF_8))
        val deduped = DuplicateChecker.dedupe(roundTrip, db)

        // Prefer the statement's own "Closing Balance" line when present;
        // fall back to the running balance of the last transaction.
        val closing = closingOverride ?: closingBalance(roundTrip)

        val summary = ImportSummary(
            fileName = fileName,
            bank = bank,
            viaOcr = viaOcr,
            txtGenerated = true,
            transactionsFound = validated.valid.size,
            duplicatesSkipped = deduped.duplicateCount,
            errorsFound = validated.rejected,
            closingBalance = closing,
            source = source
        )
        return ImportResult.Ready(deduped.unique, summary)
    }

    /**
     * The statement's closing balance — the running balance of the last row
     * that carries one. Falls back to 0 when the statement has no balances.
     */
    fun closingBalance(txs: List<ParsedTransaction>): Double =
        txs.lastOrNull { it.balance != 0.0 }?.balance ?: 0.0

    private fun pickBest(
        a: BankParseResult,
        b: BankParseResult
    ): Triple<List<ParsedTransaction>, String, Float> {
        return when {
            a.transactions.isEmpty() && b.transactions.isEmpty() ->
                Triple(emptyList(), "NONE", 0f)
            a.transactions.isEmpty() ->
                Triple(b.transactions, "UNIVERSAL", b.confidence)
            b.transactions.isEmpty() ->
                Triple(a.transactions, "REGEX", a.confidence)
            a.transactions.size > b.transactions.size ->
                Triple(a.transactions, "REGEX", a.confidence)
            b.transactions.size > a.transactions.size ->
                Triple(b.transactions, "UNIVERSAL", b.confidence)
            a.confidence >= b.confidence ->
                Triple(a.transactions, "REGEX", a.confidence)
            else ->
                Triple(b.transactions, "UNIVERSAL", b.confidence)
        }
    }

    /** Copies the uploaded stream to a temp file, enforcing the size limit. */
    private fun copyToTemp(context: Context, stream: InputStream): File? {
        val dir = File(context.cacheDir, "bankimport").apply { mkdirs() }
        val file = File(dir, "statement_${System.currentTimeMillis()}.pdf")
        return try {
            stream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_IMPORT_BYTES) return null
                        output.write(buffer, 0, read)
                    }
                }
            }
            file
        } catch (e: Exception) {
            ImportLogger.e("Failed to copy upload", e)
            file.delete()
            null
        }
    }
}
