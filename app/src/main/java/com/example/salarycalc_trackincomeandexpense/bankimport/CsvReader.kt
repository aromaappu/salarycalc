package com.example.salarycalc_trackincomeandexpense.bankimport

import java.io.InputStream
import java.util.Locale
import kotlin.math.abs

object CsvReader {

    data class ReadResult(
        val transactions: List<ParsedTransaction>,
        val dataRows: Int,
        val skippedRows: Int,
        val needsReview: Boolean
    )

    fun read(stream: InputStream): List<ParsedTransaction> = readDetailed(stream).transactions

    fun decode(bytes: ByteArray): String = when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        else -> String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
    }

    /**
     * Bank CSV files are not all in the app's export format. Read the header
     * by column meaning, handle quoted commas/newlines, and report rejected
     * rows rather than silently claiming they were imported.
     */
    fun readDetailed(stream: InputStream): ReadResult {
        val bytes = stream.use { it.readBytes() }
        val content = decode(bytes)
        val records = parseRecords(content, detectDelimiter(content))
        if (records.isEmpty()) return ReadResult(emptyList(), 0, 0, false)

        val headerIndex = records.indexOfFirst { row ->
            val columns = detectColumns(row)
            columns.containsKey("date") &&
                (columns.containsKey("debit") || columns.containsKey("credit") || columns.containsKey("amount"))
        }
        if (headerIndex < 0) return ReadResult(emptyList(), 0, 0, false)
        val cols = detectColumns(records[headerIndex])
        var skipped = 0
        var dataRows = 0
        var needsReview = !cols.containsKey("desc")
        val result = mutableListOf<ParsedTransaction>()

        for (row in records.drop(headerIndex + 1)) {
            if (row.all { it.isBlank() }) continue
            if (row.any { it.contains("closing balance", true) || it.contains("opening balance", true) } &&
                row.none { StatementNormalizer.normalizeDate(it.trim()) != null }) continue

            val rawDate = row.getOrNull(cols.getValue("date"))?.trim().orEmpty()
            // Ignore repeated table headers and statement footers.
            if (rawDate.equals("date", true) || rawDate.contains("transaction date", true)) continue
            val date = StatementNormalizer.normalizeDate(rawDate)
                ?: StatementNormalizer.normalizeDate(rawDate.substringBefore(' '))
            if (date == null) {
                // The first column can also contain statement footer text.
                if (rawDate.isNotBlank() && rawDate.any { it.isDigit() }) {
                    dataRows++
                    skipped++
                }
                continue
            }
            dataRows++

            val rawDesc = cols["desc"]?.let { row.getOrNull(it) }.orEmpty().orEmpty()
            val description = StatementNormalizer.normalizeDescription(rawDesc)
            val debitRaw = cols["debit"]?.let { row.getOrNull(it) }.orEmpty().orEmpty()
            val creditRaw = cols["credit"]?.let { row.getOrNull(it) }.orEmpty().orEmpty()
            val debit = abs(StatementNormalizer.normalizeAmount(debitRaw) ?: 0.0)
            val credit = abs(StatementNormalizer.normalizeAmount(creditRaw) ?: 0.0)
            val amountRaw = cols["amount"]?.let { row.getOrNull(it) }.orEmpty().orEmpty()
            val amountValue = StatementNormalizer.normalizeAmount(amountRaw)
            val typeValue = cols["type"]?.let { row.getOrNull(it) }.orEmpty().orEmpty()
            val explicitType = StatementNormalizer.normalizeType(typeValue)
                ?: when {
                    Regex("(?i)\\bDR\\b").containsMatchIn(amountRaw) -> "DEBIT"
                    Regex("(?i)\\bCR\\b").containsMatchIn(amountRaw) -> "CREDIT"
                    else -> null
                }

            val resolved = when {
                debit > 0.0 && credit > 0.0 -> null // contradictory columns
                debit > 0.0 -> debit to "DEBIT"
                credit > 0.0 -> credit to "CREDIT"
                amountValue != null && amountValue != 0.0 -> {
                    val inferred = explicitType ?: if (amountValue < 0) "DEBIT" else "CREDIT"
                    if (explicitType == null && amountValue > 0) needsReview = true
                    abs(amountValue) to inferred
                }
                else -> null
            }
            if (resolved == null || !resolved.first.isFinite() || resolved.first <= 0) {
                skipped++
                continue
            }
            val balance = cols["balance"]?.let { row.getOrNull(it) }
                ?.let { StatementNormalizer.normalizeAmount(it) } ?: 0.0
            val reference = cols["ref"]?.let { row.getOrNull(it) }?.trim().orEmpty()
            val hash = HashGenerator.generate(date, resolved.first, description, reference)
            result.add(ParsedTransaction(
                date, description, resolved.first, resolved.second,
                balance, reference, hash,
                confidence = if (needsReview) 0.75f else 0.97f
            ))
        }
        return ReadResult(result, dataRows, skipped, needsReview)
    }

    private fun detectColumns(header: List<String>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        var fallbackDate: Int? = null
        header.forEachIndexed { index, heading ->
            val h = heading.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
            if (h.contains("date") && fallbackDate == null) fallbackDate = index
            when {
                h.contains("date") && !h.contains("value date") ->
                    result.putIfAbsent("date", index)
                h.contains("narration") || h.contains("description") ||
                    h.contains("particular") || h.contains("remark") ||
                    h.contains("details") || h.contains("payee") ->
                    result.putIfAbsent("desc", index)
                h.contains("dr/cr") || h.contains("cr/dr") || h.contains("dr cr") ||
                    h.contains("cr dr") || h == "type" || h.contains("transaction type") ->
                    result.putIfAbsent("type", index)
                h.contains("balance") || h == "bal" || h.contains("running bal") ->
                    result.putIfAbsent("balance", index)
                h.contains("debit") || h.contains("withdrawal") ||
                    h == "dr" || h.contains("paid out") ->
                    result.putIfAbsent("debit", index)
                h.contains("credit") || h.contains("deposit") ||
                    h == "cr" || h.contains("paid in") ->
                    result.putIfAbsent("credit", index)
                h.contains("amount") || h == "amt" ->
                    result.putIfAbsent("amount", index)
                h.contains("reference") || h.contains("ref no") || h == "ref" ||
                    h.contains("cheque") || h.contains("chq") || h.contains("utr") ->
                    result.putIfAbsent("ref", index)
            }
        }
        if (!result.containsKey("date") && fallbackDate != null) result["date"] = fallbackDate!!
        return result
    }

    private fun detectDelimiter(csv: String): Char {
        val header = csv.lineSequence().take(25).firstOrNull {
            it.contains("date", true) && (it.contains("amount", true) ||
                it.contains("debit", true) || it.contains("withdrawal", true) ||
                it.contains("credit", true) || it.contains("deposit", true))
        } ?: csv.lineSequence().firstOrNull().orEmpty()
        return listOf(',', ';', '\t').maxByOrNull { separator ->
            header.count { it == separator }
        } ?: ','
    }

    /** RFC 4180 fields, including doubled quote escapes and multiline descriptions. */
    private fun parseRecords(csv: String, delimiter: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < csv.length) {
            when (val ch = csv[i]) {
                '"' -> {
                    if (quoted && i + 1 < csv.length && csv[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else quoted = !quoted
                }
                delimiter -> if (quoted) cell.append(ch) else {
                    cells.add(cell.toString().trim())
                    cell.setLength(0)
                }
                '\n', '\r' -> if (quoted) {
                    cell.append('\n')
                    if (ch == '\r' && i + 1 < csv.length && csv[i + 1] == '\n') i++
                } else {
                    cells.add(cell.toString().trim())
                    cell.setLength(0)
                    if (cells.any { it.isNotBlank() }) records.add(cells)
                    cells = mutableListOf()
                    if (ch == '\r' && i + 1 < csv.length && csv[i + 1] == '\n') i++
                }
                else -> cell.append(ch)
            }
            i++
        }
        cells.add(cell.toString().trim())
        if (cells.any { it.isNotBlank() }) records.add(cells)
        return records
    }
}
