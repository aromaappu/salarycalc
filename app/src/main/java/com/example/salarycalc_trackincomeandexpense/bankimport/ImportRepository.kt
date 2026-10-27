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
            val closingBalance: Double = 0.0,
            val skippedRows: Int = 0,
            val warnings: List<String> = emptyList(),
            val possibleDuplicateHashes: Set<String> = emptySet()
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

        // Always clean up temporary statement data, including unreadable PDFs.
        val extract = try {
            PdfReader.extract(context, file)
        } finally {
            file.delete()
        }
        val rawText = when (extract) {
            is PdfReader.Result.Error -> return ImportResult.Error(extract.message)
            is PdfReader.Result.Success -> extract.text
        }
        val bank = BankDetector.detect(rawText)
        val cleaned = TextCleaner.clean(rawText)
        val opening = TextCleaner.openingBalance(rawText)
        val closing = TextCleaner.closingBalance(rawText)
        val bankParsed = ParserRegistry.get(bank).parse(cleaned)
        val universalParsed = UniversalParser().parse(cleaned, opening)

        val coordinateRows = CoordinateStatementParser().parse(
            (extract as PdfReader.Result.Success).elements
        )
        val coordinateTxs = coordinateRows.mapNotNull { row ->
            if (row.status != "VALID" || row.date == null ||
                row.description.isBlank() || (row.debit == null && row.credit == null)
            ) return@mapNotNull null

            val amount = (row.debit ?: row.credit ?: BigDecimal.ZERO).toDouble()
            val type = if (row.debit != null) "DEBIT" else "CREDIT"
            val description = StatementNormalizer.cleanNarration(row.description)
            val reference = Regex("(?:UPI|IMPS|NEFT|RTGS)/(?:DR|CR)/([^/\\s]+)", RegexOption.IGNORE_CASE)
                .find(row.description)?.groupValues?.getOrNull(1).orEmpty()
            ParsedTransaction(
                date = row.date,
                description = description,
                amount = amount,
                type = type,
                balance = row.balance?.toDouble() ?: 0.0,
                reference = reference,
                hash = HashGenerator.generate(row.date, amount, description, reference),
                confidence = 0.85f
            )
        }

        val selection = PdfCandidateSelector.choose(
            listOf(
                PdfCandidateSelector.Candidate("BANK REGEX", bankParsed.transactions, bankParsed.confidence),
                PdfCandidateSelector.Candidate("UNIVERSAL", universalParsed.transactions, universalParsed.confidence),
                PdfCandidateSelector.Candidate("PDF LAYOUT", coordinateTxs, 0.85f)
            ),
            opening,
            closing
        ) ?: return ImportResult.Error(
            "Could not read transactions reliably from this PDF. Try a clearer statement or a CSV export."
        )

        val deduped = DuplicateChecker.dedupe(selection.transactions, db)
        val incompleteRows = coordinateRows.count { it.status != "VALID" }
        val warnings = selection.warnings.toMutableList()
        if (deduped.duplicateCount > 0) {
            warnings.add("${deduped.duplicateCount} already-imported transaction(s) skipped.")
        }
        if (deduped.possibleDuplicateHashes.isNotEmpty()) {
            warnings.add("${deduped.possibleDuplicateHashes.size} transactions resemble existing entries and are unchecked by default. Select only if they are separate payments.")
        }
        if (incompleteRows > 0 && selection.source == "PDF LAYOUT") {
            warnings.add("$incompleteRows PDF table rows could not be interpreted.")
        }
        if ((extract as PdfReader.Result.Success).viaOcr) {
            warnings.add("Scanned PDF: OCR may misread names, amounts and dates.")
        }
        // Bank PDFs never auto-save: missing rows and bank-specific layouts
        // cannot be proven correct by parser confidence alone.
        return ImportResult.NeedsPreview(
            transactions = deduped.unique,
            bank = bank.displayName,
            confidence = selection.confidence,
            source = selection.source,
            closingBalance = closing ?: closingBalance(selection.transactions),
            skippedRows = selection.rejected + if (selection.source == "PDF LAYOUT") incompleteRows else 0,
            warnings = warnings.distinct(),
            possibleDuplicateHashes = deduped.possibleDuplicateHashes
        )
    }

    fun processCsv(
        stream: InputStream,
        db: AppDatabase,
        apiKey: String,
        fileName: String = "statement"
    ): ImportResult {
        val bytes = try {
            stream.use { it.readBytes() }
        } catch (e: Exception) {
            return ImportResult.Error("Could not read CSV: ${e.message}")
        }
        if (bytes.size > MAX_IMPORT_BYTES) {
            return ImportResult.Error("File is too large. Maximum size is 30 MB.")
        }
        val rawText = CsvReader.decode(bytes)

        val read = try {
            CsvReader.readDetailed(rawText.byteInputStream(Charsets.UTF_8))
        } catch (e: Exception) {
            return ImportResult.Error("Could not parse CSV: ${e.message}")
        }
        if (read.transactions.isEmpty()) {
            return ImportResult.Error(
                if (read.dataRows > 0) "CSV rows found, but none could be safely imported. Check the date and amount columns."
                else "CSV column headings not recognized. Expected Date and Debit/Credit or Amount columns."
            )
        }
        val bank = BankDetector.detect(rawText).displayName
        val validated = TransactionValidator.validate(read.transactions)
        val skipped = read.skippedRows + validated.rejected
        val dedupedCsv = DuplicateChecker.dedupe(validated.valid, db)
        if (read.needsReview || skipped > 0 || dedupedCsv.possibleDuplicateHashes.isNotEmpty()) {
            val deduped = dedupedCsv
            return ImportResult.NeedsPreview(
                transactions = deduped.unique,
                bank = bank,
                confidence = if (read.needsReview) 0.75f else 0.97f,
                source = "CSV",
                closingBalance = TextCleaner.closingBalance(rawText)
                    ?: closingBalance(read.transactions),
                skippedRows = skipped,
                warnings = if (deduped.possibleDuplicateHashes.isNotEmpty())
                    listOf("${deduped.possibleDuplicateHashes.size} possible duplicates are unchecked. Review before adding.")
                else emptyList(),
                possibleDuplicateHashes = deduped.possibleDuplicateHashes
            )
        }
        return finalize(read.transactions, bank, db, "CSV", fileName,
            viaOcr = false, closingOverride = TextCleaner.closingBalance(rawText))
    }

    /**
     * Saves a list of transactions to the database (creating income sources /
     * expense categories on the fly). Returns the number actually inserted.
     */
    fun save(txs: List<ParsedTransaction>, db: AppDatabase, statementId: Long? = null): Int {
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
            val reference = BankTransactionMatcher.reference(tx.reference, rawName)
            val entry = BankTransactionMatcher.Entry(tx.date, type, tx.amount, rawName, reference, "statement")
            // Recheck after preview: new SMS alerts may have arrived meanwhile.
            val alreadySaved = db.getPotentialTransactionMatches(type, tx.amount, tx.date).any {
                BankTransactionMatcher.classify(entry, it) == BankTransactionMatcher.Match.CONFIRMED
            }
            if (alreadySaved) continue
            val id = db.addTransactionWithHash(type, tx.amount, name, tx.date, tx.hash, statementId, reference)
            if (id > 0) {
                saved++
                if (type == AppDatabase.TYPE_INCOME) {
                    if (incomeSources.add(name)) db.addIncomeSource(name)
                } else {
                    if (expenseCategories.add(name)) db.addExpenseCategory(name)
                }
            }
        }
        return saved
    }

    /**
     * Link every new imported row to its statement in the same DB transaction,
     * so deleting that statement can remove only its own imported rows.
     */
    fun saveStatement(txs: List<ParsedTransaction>, db: AppDatabase, fileName: String, closingBalance: Double): Int {
        if (txs.isEmpty()) return 0
        val sql = db.writableDatabase
        sql.beginTransaction()
        try {
            val statementId = db.addBankStatement(fileName, 0, closingBalance)
            if (statementId <= 0) return 0
            val count = save(txs, db, statementId)
            if (count == 0) db.deleteBankStatement(statementId)
            else db.updateBankStatementCount(statementId, count)
            sql.setTransactionSuccessful()
            return count
        } finally {
            sql.endTransaction()
        }
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

        if (confidence >= CONFIDENCE_AUTO_IMPORT && !viaOcr && sourceHint != "PDF" &&
            validated.rejected == 0) {
            // Finalize the original rows so duplicate/error counts stay accurate.
            return finalize(parsed, bank.displayName, db, source, fileName, viaOcr, closingOverride)
        }

        // PDFs, OCR, uncertain classifications and rejected rows require review.
        return ImportResult.NeedsPreview(
            transactions = deduped.unique,
            bank = bank.displayName,
            confidence = confidence,
            source = source,
            closingBalance = closingOverride ?: closingBalance(deduped.unique),
            skippedRows = validated.rejected
        )
    }

    /**
     * Keep the validated original transactions: exporting and re-reading CSV
     * discards bank reference IDs and changes transaction hashes, allowing
     * the same transaction to be imported twice through different paths.
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
        val deduped = DuplicateChecker.dedupe(validated.valid, db)

        // Prefer the statement's own "Closing Balance" line when present;
        // fall back to the running balance of the last transaction.
        val closing = closingOverride ?: closingBalance(validated.valid)

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
        return if (deduped.possibleDuplicateHashes.isNotEmpty()) {
            ImportResult.NeedsPreview(
                transactions = deduped.unique,
                bank = bank,
                confidence = 0.80f,
                source = source,
                closingBalance = closing,
                warnings = listOf("Possible duplicates require your approval before importing."),
                possibleDuplicateHashes = deduped.possibleDuplicateHashes
            )
        } else ImportResult.Ready(deduped.unique, summary)
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
                        if (total > MAX_IMPORT_BYTES) throw IllegalArgumentException("File too large")
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
