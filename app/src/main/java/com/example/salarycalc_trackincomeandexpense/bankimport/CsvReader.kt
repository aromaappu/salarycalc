package com.example.salarycalc_trackincomeandexpense.bankimport

import java.io.InputStream

object CsvReader {

    fun read(stream: InputStream): List<ParsedTransaction> {
        val lines = stream.bufferedReader(Charsets.UTF_8).readLines()
            .map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()

        val header = splitCsv(lines[0]).map { it.lowercase().trim() }
        val cols = detectColumns(header)

        val result = mutableListOf<ParsedTransaction>()
        for (i in 1 until lines.size) {
            val cells = splitCsv(lines[i])
            if (cells.size <= (cols.values.maxOrNull() ?: 0)) continue

            val rawDate = cols["date"]?.let { cells.getOrNull(it) } ?: continue
            val date = StatementNormalizer.normalizeDate(rawDate) ?: continue

            val rawDesc = cols["desc"]?.let { cells.getOrNull(it) }?.trim() ?: ""
            val description = StatementNormalizer.normalizeDescription(rawDesc)

            val debitVal  = cols["debit"]?.let  { StatementNormalizer.normalizeAmount(cells.getOrNull(it) ?: "") } ?: 0.0
            val creditVal = cols["credit"]?.let { StatementNormalizer.normalizeAmount(cells.getOrNull(it) ?: "") } ?: 0.0
            val amountCol = cols["amount"]?.let { StatementNormalizer.normalizeAmount(cells.getOrNull(it) ?: "") }

            val (amount, type) = when {
                amountCol != null && amountCol != 0.0 -> {
                    val rawType = cols["type"]?.let { cells.getOrNull(it) } ?: ""
                    val normalizedType = StatementNormalizer.normalizeType(rawType)
                    val value = kotlin.math.abs(amountCol)
                    when {
                        normalizedType != null -> value to normalizedType
                        amountCol < 0 -> value to "DEBIT"
                        else -> value to "CREDIT"
                    }
                }
                creditVal > 0 && debitVal == 0.0 -> creditVal to "CREDIT"
                debitVal  > 0 && creditVal == 0.0 -> debitVal  to "DEBIT"
                creditVal > 0 -> creditVal to "CREDIT"
                debitVal  > 0 -> debitVal  to "DEBIT"
                else -> continue
            }

            val balance = cols["balance"]?.let { StatementNormalizer.normalizeAmount(cells.getOrNull(it) ?: "") } ?: 0.0
            val reference = cols["ref"]?.let { cells.getOrNull(it)?.trim() } ?: ""

            val hash = HashGenerator.generate(date, amount, description, reference)
            result.add(ParsedTransaction(date, description, amount, type, balance, reference, hash, confidence = 0.97f))
        }
        return result
    }

    // Maps logical column names to their index in the header row
    private fun detectColumns(header: List<String>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        header.forEachIndexed { i, h ->
            when {
                h.contains("date") || h.contains("txn date") || h.contains("value date") ->
                    if (!map.containsKey("date")) map["date"] = i
                h.contains("narration") || h.contains("description") || h.contains("particulars") || h.contains("remarks") ->
                    if (!map.containsKey("desc")) map["desc"] = i
                (h.contains("debit") || h.contains("withdrawal") || h.contains("dr")) && !h.contains("credit") ->
                    if (!map.containsKey("debit")) map["debit"] = i
                (h.contains("credit") || h.contains("deposit") || h.contains("cr")) && !h.contains("debit") ->
                    if (!map.containsKey("credit")) map["credit"] = i
                h.contains("amount") && !h.contains("debit") && !h.contains("credit") ->
                    if (!map.containsKey("amount")) map["amount"] = i
                h.contains("balance") || h.contains("bal") ->
                    if (!map.containsKey("balance")) map["balance"] = i
                h.contains("ref") || h.contains("chq") || h.contains("cheque") || h.contains("utr") ->
                    if (!map.containsKey("ref")) map["ref"] = i
                h.contains("type") || h.contains("cr/dr") || h.contains("dr/cr") ->
                    if (!map.containsKey("type")) map["type"] = i
            }
        }
        return map
    }

    private fun splitCsv(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { result.add(sb.toString()); sb.clear() }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString())
        return result
    }
}
