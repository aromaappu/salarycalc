package com.example.salarycalc_trackincomeandexpense.bankimport

import com.example.salarycalc_trackincomeandexpense.TransactionTypeClassifier
import java.util.Locale
import kotlin.math.abs

/**
 * Layout-independent parser.
 *
 * PDF text extraction does not preserve column spacing, so a statement often
 * comes out like this instead of a single table row:
 *
 *     01-08-2026
 *     UPI PAYMENT
 *     Amazon
 *     350.00
 *     24500.00
 *
 * Instead of depending on fixed spacing, this parser groups consecutive lines
 * into a transaction by pattern recognition:
 *  - a line that starts with a date begins a new transaction
 *  - text lines accumulate into the description (wrapped lines are merged)
 *  - lines carrying decimal amounts accumulate as the transaction amount /
 *    balance / debit / credit columns
 *
 * It never depends on where a value sits on a line.
 */
class UniversalParser(override val bank: BankDetector.Bank = BankDetector.Bank.UNKNOWN) : BankParser {

    // Date-looking substrings that may appear anywhere on a line
    private val DATE_TOKEN = Regex(
        "\\b(\\d{2}[-/. ]\\d{2}[-/. ]\\d{4}|" +
            "\\d{4}[-/. ]\\d{2}[-/. ]\\d{2}|" +
            "\\d{1,2}[-/. ]\\d{1,2}[-/. ]\\d{4}|" +
            "\\d{1,2}[-/. ]\\d{1,2}[-/. ]\\d{2}|" +
            "\\d{1,2}[-/. ](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[-. ]\\d{4}|" +
            "\\d{1,2}\\s(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s\\d{4}|" +
            "(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[-. ]\\d{1,2}[-,]?[-. ]\\d{4})\\b",
        RegexOption.IGNORE_CASE
    )

    // A monetary value with optional currency symbol and optional CR/DR tag.
    // [\d,]+ handles plain numbers (24500.00), western grouping (2,500.00)
    // and Indian grouping (1,73,259.00) — commas are stripped later.
    private val AMOUNT_RE = Regex(
        "(?<![A-Za-z0-9])(?:Rs\\.?|INR|₹)?\\s*\\(?-?[\\d,]+\\.\\d{2}\\)?\\s*(?:Cr|Dr|CR|DR)?(?![A-Za-z0-9])",
        RegexOption.IGNORE_CASE
    )

    private class Accumulator {
        var date: String? = null
        var dateToken: String? = null
        val desc = StringBuilder()
        val amounts = mutableListOf<Double>()
        var explicitType: String? = null
        var hasAmountTags = false

        fun appendDesc(text: String) {
            if (desc.isNotEmpty()) desc.append(' ')
            desc.append(text)
        }
    }

    private class ParsedLine(val date: String?, val dateToken: String?, val amounts: List<Pair<Double, String?>>, val text: String)

    override fun parse(text: String): BankParseResult = parse(text, null)

    /**
     * [openingBalance] (from the statement's "Opening Balance" line) seeds the
     * running balance so the first transaction is classified by balance
     * continuity instead of narration keywords, which can be scrambled across
     * lines by PDF text extraction.
     */
    fun parse(text: String, openingBalance: Double?): BankParseResult {
        val lines = TextCleaner.cleanLines(text)
        val txs = mutableListOf<ParsedTransaction>()
        var cur: Accumulator? = null
        var prevBalance: Double? = openingBalance

        for (line in lines) {
            if (isJunkLine(line)) continue
            val parsed = parseLine(line)
            val date = parsed.date ?: StatementNormalizer.normalizeDate(parsed.dateToken ?: "")

            if (date != null) {
                // A new date always starts a new transaction; finalize the previous one.
                cur?.let { acc ->
                    buildTx(acc, txs, prevBalance)?.let { prevBalance = it.balance }
                }
                cur = Accumulator().apply { this.date = date }
                applyParsed(cur!!, parsed, line)
            } else {
                if (cur == null) {
                    // Text before the first date is statement header — ignore it.
                    continue
                }
                applyParsed(cur!!, parsed, line)
            }
        }
        cur?.let { buildTx(it, txs, prevBalance) }

        val confidence = if (txs.isEmpty()) 0.0f else estimateConfidence(txs)
        return BankParseResult(txs, confidence)
    }

    private fun applyParsed(acc: Accumulator, p: ParsedLine, raw: String) {
        for ((value, tag) in p.amounts) {
            acc.amounts.add(value)
            val t = StatementNormalizer.normalizeType(tag ?: "")
            if (t != null) acc.explicitType = t
            if (tag != null) acc.hasAmountTags = true
        }
        val text = p.text.trim()
        if (text.isNotEmpty()) acc.appendDesc(text)
        // CR/DR keywords embedded in the narration (e.g. "NEFT/CR/SALARY").
        // Explicit DR markers and strong debit words win over broad credit words
        // ("UPI/DR/.../PAYMENT" must never become a credit just because the payee
        // name contains a word like "SALARY" or "DEPOSIT").
        val upper = raw.uppercase(Locale.US)
        if (upper.contains("/DR/") || upper.contains(" DR") || upper.contains("DEBIT") || upper.contains("WITHDRAWAL")) {
            if (acc.explicitType == null) acc.explicitType = "DEBIT"
        } else if (upper.contains("/CR/") || upper.contains(" CR") || upper.contains("CREDIT") || upper.contains("INWARD") ||
            upper.contains("SALARY") || upper.contains("DEPOSIT") || upper.contains("REFUND") || upper.contains("CASHBACK")
        ) {
            if (acc.explicitType == null) acc.explicitType = "CREDIT"
        } else if (upper.contains("PAYMENT") || upper.contains("PURCHASE") || upper.contains("POS ")) {
            if (acc.explicitType == null) acc.explicitType = "DEBIT"
        }
    }

    /** Splits a line into its date (if any), monetary values, and remaining text. */
    private fun parseLine(line: String): ParsedLine {
        var text = line
        var date: String? = null
        var dateToken: String? = null

        val dm = DATE_TOKEN.find(line)
        if (dm != null) {
            dateToken = dm.value
            val normalized = StatementNormalizer.normalizeDate(dm.value.replace(".", "-"))
            if (normalized != null) {
                date = normalized
                text = text.replaceRange(dm.range.first, dm.range.last + 1, " ")
            }
        }

        val amounts = mutableListOf<Pair<Double, String?>>()
        val am = AMOUNT_RE.find(text)
        if (am != null) {
            // Remove amount tokens from the line; keep everything else as narration.
            val cleaned = AMOUNT_RE.replace(text) { m ->
                val value = StatementNormalizer.normalizeAmount(m.value)
                if (value != null) {
                    val tag = if (m.value.uppercase(Locale.US).contains("CR")) "CR"
                    else if (m.value.uppercase(Locale.US).contains("DR")) "DR"
                    else null
                    amounts.add(value to tag)
                }
                " "
            }
            text = cleaned
        }

        text = text.replace(Regex("\\s+"), " ").trim()
        return ParsedLine(date, dateToken, amounts, text)
    }

    /** Builds a ParsedTransaction from accumulated lines. Returns null if unusable. */
    private fun buildTx(acc: Accumulator, out: MutableList<ParsedTransaction>, prevBalance: Double?): ParsedTransaction? {
        val date = acc.date ?: return null
        val values = acc.amounts
        if (values.isEmpty()) return null

        // Determine (txAmount, balance, type)
        var txAmount: Double
        var balance = 0.0
        var type: String

        when {
            values.size >= 3 -> {
                // Classic debit/credit/balance triple, or extra amounts from wrapped lines
                val debit = values[values.size - 3]
                val credit = values[values.size - 2]
                val bal = values.last()
                balance = bal
                type = when {
                    abs(debit) < 0.01 && credit > 0.01 -> "CREDIT"
                    abs(credit) < 0.01 && debit > 0.01 -> "DEBIT"
                    acc.explicitType != null -> acc.explicitType!!
                    else -> inferType(acc.desc.toString(), prevBalance, bal)
                }
                txAmount = if (type == "CREDIT") credit else debit
            }
            values.size == 2 -> {
                val a = values[0]
                val b = values[1]
                // Balance-continuity check: if b differs from previous balance by ~a,
                // then (a = tx amount, b = balance).
                val diff = b - (prevBalance ?: 0.0)
                val matches = prevBalance != null && abs(abs(diff) - abs(a)) < 0.01
                if (matches) {
                    txAmount = a
                    balance = b
                    type = if (diff > 0) "CREDIT" else "DEBIT"
                } else if (acc.explicitType != null) {
                    txAmount = a
                    balance = b
                    type = acc.explicitType!!
                } else {
                    // Two-amount rows: either (tx amount, balance) or (debit, credit)
                    when {
                        a == 0.0 && b > 0.0 -> { txAmount = b; balance = 0.0; type = "CREDIT" }
                        a > 0.0 && b == 0.0 -> { txAmount = a; balance = 0.0; type = "DEBIT" }
                        else -> { txAmount = a; balance = b; type = inferType(acc.desc.toString(), prevBalance, b) }
                    }
                }
            }
            else -> {
                txAmount = values[0]
                balance = 0.0
                type = acc.explicitType ?: inferType(acc.desc.toString(), prevBalance, null)
            }
        }

        // Reject single-amount rows that are likely summary/total lines instead of real transactions.
        val rawDesc = acc.desc.toString().trim()
        if (values.size == 1 && (rawDesc.isBlank() || isSummaryDescription(rawDesc))) return null
        if (txAmount.isNaN() || txAmount <= 0.0) return null

        val description = StatementNormalizer.cleanNarration(acc.desc.toString())
        val ref = extractRef(acc.desc.toString())
        val hash = HashGenerator.generate(date, txAmount, description, ref)
        val tx = ParsedTransaction(date, description, txAmount, type, balance, ref, hash, confidence = 0.9f)
        out.add(tx)
        return tx
    }

    private fun inferType(desc: String, prevBalance: Double?, balance: Double?): String {
        val lower = desc.lowercase(Locale.US)
        val inferred = TransactionTypeClassifier.classify(lower)
        if (inferred == "CREDIT") return "CREDIT"
        if (inferred == "DEBIT") return "DEBIT"
        // Balance continuity as a last resort
        if (prevBalance != null && balance != null) {
            return if (balance > prevBalance) "CREDIT" else "DEBIT"
        }
        return "DEBIT"
    }

    private fun extractRef(text: String): String {
        Regex("(?:UPI|IMPS|NEFT|RTGS|MOB-IMPS)/(?:DR|CR)/[^/\\s]+", RegexOption.IGNORE_CASE)
            .find(text)?.let { return it.value.substringAfterLast('/') }
        Regex("\\b[A-Z0-9]{12,20}\\b").find(text)?.let { return it.value }
        return ""
    }

    private fun isSummaryDescription(desc: String): Boolean {
        val lower = desc.lowercase(Locale.US)
        val summaryWords = listOf("net profit", "profit and loss", "profit/loss", "profit", "loss", "net income", "gross profit", "operating profit")
        return summaryWords.any { lower.contains(it) }
    }

    private fun isJunkLine(line: String): Boolean {
        val lower = line.lowercase(Locale.US)
        if (lower.contains("value date") || lower.contains("txn date")) return true
        // Single-column header rows / totals that escaped the cleaner
        val junk = listOf("opening balance", "closing balance", "b/f", "c/f", "total",
            "page", "account no", "statement", "balance as on", "net profit", "profit and loss",
            "profit/loss", "profit", "loss", "net income")
        val hits = junk.count { lower.contains(it) }
        return hits >= 2
    }

    private fun estimateConfidence(txs: List<ParsedTransaction>): Float {
        var full = 0
        for (tx in txs) {
            if (tx.description.isNotEmpty() && tx.description != "Bank Transaction" &&
                (tx.balance != 0.0 || tx.reference.isNotEmpty())
            ) full++
        }
        val ratio = full.toFloat() / txs.size.coerceAtLeast(1)
        return 0.8f + 0.19f * ratio
    }
}
