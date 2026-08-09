package com.example.salarycalc_trackincomeandexpense.bankimport

import java.util.Locale

/**
 * Cleans raw PDF/OCR text before parsing.
 *
 * Responsibilities:
 *  - drop blank lines and page numbers
 *  - drop repeated page headers (bank name / "statement of account")
 *  - drop footers (generated on / "thank you" / contact ads / toll-free numbers)
 *  - drop opening/closing balance and totals summary lines
 *
 * It deliberately keeps the line structure — the parser still needs to know
 * where each line starts so it can merge wrapped descriptions.
 */
object TextCleaner {

    private val PAGE_NUMBER = Regex(
        "^\\s*(?:page)?\\s*\\d+(?:\\s*of\\s*\\d+)?\\s*(?:page)?\\s*$",
        RegexOption.IGNORE_CASE
    )

    // Summary / balance lines that must never become transactions.
    private val SUMMARY_KEYWORDS = listOf(
        "opening balance", "closing balance", "opening bal", "closing bal",
        "brought forward", "carried forward", "b/f", "c/f",
        "total debit", "total debits", "total credit", "total credits",
        "total withdrawal", "total deposit", "total withdrawals",
        "account summary", "statement summary", "summary of account",
        "transaction summary", "grand total", "net balance", "page total",
        "beginning balance", "ending balance",
        "net profit", "profit and loss", "profit/loss", "profit", "loss", "net income"
    )

    // Footer / advertisement markers that commonly appear on statements.
    private val FOOTER_KEYWORDS = listOf(
        "generated", "www.", ".com", ".co.in", ".in ",
        "toll free", "customer care", "contact us", "call us",
        "calls to and from", "charges for service", "money market",
        "your statement", "if you have any queries", "printed on",
        "this is a system generated", "do not reply", "courtesy",
        "net banking", "yono", "sbi card", "terms and conditions",
        "sign up for", "download our", "app on", "availability",
        "is only available", "for any complaints", "national toll free",
        "thank you for banking", "banking with us", "thank you for",
        "yours faithfully", "we are happy to assist", "visit our website"
    )

    private val HEADER_KEYWORDS = listOf(
        "statement of account", "account statement", "e-statement", "mini statement",
        "account no", "account number", "customer id", "ifsc", "micr",
        "branch code", "branch name", "address", "period", "statement period",
        "date particulars", "narration", "withdrawal", "deposits", "balance", "chq"
    )

    /** Returns cleaned, non-blank lines in order. */
    fun cleanLines(rawText: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in rawText.lines()) {
            val line = raw.trim().replace(Regex("\\s+"), " ")
            if (line.isEmpty()) continue
            if (shouldDrop(line)) continue
            out.add(line)
        }
        return out
    }

    /** Returns the cleaned text joined with newlines (used by bank detection). */
    fun clean(rawText: String): String = cleanLines(rawText).joinToString("\n")

    /** Extracts the statement's opening balance from a line like "Opening Balance 1,751.59". */
    fun openingBalance(rawText: String): Double? {
        for (raw in rawText.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (!Regex("opening\\s*bal", RegexOption.IGNORE_CASE).containsMatchIn(line)) continue
            val m = Regex("(?:Rs\\.?|INR|₹)?\\s*([\\d,]+\\.\\d{2})").find(line)
            val value = m?.groupValues?.get(1)?.let { StatementNormalizer.normalizeAmount(it) }
            if (value != null) return value
        }
        return null
    }

    /**
     * Extracts the statement's closing balance. The amount may sit on the same
     * line as "Closing Balance" or, with PDF text extraction, on the line
     * right after it — both are handled.
     */
    fun closingBalance(rawText: String): Double? {
        val lines = rawText.lines()
        for (i in lines.indices) {
            val label = lines[i].trim()
            if (label.isEmpty() || !Regex("closing\\s*bal", RegexOption.IGNORE_CASE).containsMatchIn(label)) continue
            val candidates = buildList {
                add(label)
                if (i + 1 < lines.size && lines[i + 1].isNotBlank()) add(lines[i + 1].trim())
            }
            for (candidate in candidates) {
                val m = Regex("(?:Rs\\.?|INR|₹)?\\s*([\\d,]+\\.\\d{2})").find(candidate)
                val value = m?.groupValues?.get(1)?.let { StatementNormalizer.normalizeAmount(it) }
                if (value != null) return value
            }
        }
        return null
    }

    private fun shouldDrop(line: String): Boolean {
        if (PAGE_NUMBER.matches(line)) return true

        val lower = line.lowercase(Locale.US)

        // Explicit balance/totals summary lines
        if (SUMMARY_KEYWORDS.any { lower.contains(it) }) return true

        // Header row that describes columns — contains several header words at once
        val headerHits = HEADER_KEYWORDS.count { lower.contains(it) }
        if (headerHits >= 3) return true

        // Footer / advertisement lines
        val footerHits = FOOTER_KEYWORDS.count { lower.contains(it) }
        if (footerHits >= 1 && line.length <= 140) {
            // A single footer keyword is not enough to drop real narration,
            // but if the line also has no digits it is almost certainly boilerplate.
            if (!line.any { it.isDigit() }) return true
            if (footerHits >= 2) return true
        }

        // A lone long digit-run line (ref numbers) outside a transaction is noise
        return false
    }
}
