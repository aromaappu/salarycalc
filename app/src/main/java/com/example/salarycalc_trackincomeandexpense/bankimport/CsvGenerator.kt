package com.example.salarycalc_trackincomeandexpense.bankimport

import java.util.Locale

/**
 * Generates the standard interchange CSV that is always produced — no matter
 * which bank the statement came from.
 *
 *     Date,Description,Debit,Credit,Balance
 *     01-08-2026,Salary,,20000,25000
 *     02-08-2026,Amazon,450,,24550
 *
 * This CSV is the single format consumed by the database import step.
 */
object CsvGenerator {

    const val HEADER = "Date,Description,Debit,Credit,Balance"

    fun generate(txs: List<ParsedTransaction>): String {
        val sb = StringBuilder()
        sb.append(HEADER).append('\n')
        for (tx in txs) {
            val date = StatementNormalizer.toCsvDate(tx.date)
            val desc = escapeCsv(tx.description)
            val debit = if (tx.type == "DEBIT") formatAmount(tx.amount) else ""
            val credit = if (tx.type == "CREDIT") formatAmount(tx.amount) else ""
            val balance = if (tx.balance != 0.0) formatAmount(tx.balance) else ""
            sb.append(date).append(',')
            sb.append(desc).append(',')
            sb.append(debit).append(',')
            sb.append(credit).append(',')
            sb.append(balance).append('\n')
        }
        return sb.toString()
    }

    /** Quotes a field when it contains a comma, quote or newline (RFC 4180). */
    fun escapeCsv(field: String): String =
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    fun formatAmount(value: Double): String = String.format(Locale.US, "%.2f", value)
}
