package com.example.salarycalc_trackincomeandexpense

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.util.regex.Pattern

data class BankStatementTx(
    val date: String,
    val amount: Double,
    val isCredit: Boolean,
    val description: String
)

object BankStatementParser {

    // Matches date line: starts with dd-MM-yyyy
    private val DATE_LINE = Pattern.compile(
        "^(\\d{2})-(\\d{2})-(\\d{4})\\s+(.*)"
    )

    // Two amounts at end of date line: transaction amount + balance
    // e.g. "19.00 1,732.59" or "5,000.00 6,732.59"
    private val TWO_AMOUNTS = Pattern.compile(
        "([\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})\\s*$"
    )

    // Single amount at end (some lines only have balance)
    private val ONE_AMOUNT = Pattern.compile(
        "([\\d,]+\\.\\d{2})\\s*$"
    )

    fun parse(context: Context, stream: InputStream): List<BankStatementTx> {
        PDFBoxResourceLoader.init(context.applicationContext)
        val doc = PDDocument.load(stream)
        return try {
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            val text = stripper.getText(doc)
            val result = parseText(text)
            android.util.Log.d("BankParser", "Parsed ${result.size} transactions")
            result.forEach {
                android.util.Log.d("BankParser", "TX: ${it.date} | ${it.amount} | credit=${it.isCredit} | ${it.description}")
            }
            result
        } finally {
            try { doc.close() } catch (_: Exception) {}
        }
    }

    private fun parseAmount(s: String): Double =
        s.replace(",", "").toDoubleOrNull() ?: 0.0

    private fun isDateLine(line: String): Boolean =
        DATE_LINE.matcher(line).find()

    private fun extractDateFromLine(line: String): String? {
        val m = DATE_LINE.matcher(line)
        if (!m.find()) return null
        val d = m.group(1)!!.toInt()
        val mo = m.group(2)!!.toInt()
        val y = m.group(3)!!.toInt()
        if (mo !in 1..12 || d !in 1..31) return null
        return "%04d-%02d-%02d".format(y, mo, d)
    }

    // Extract transaction amount from date line
    // Format: "dd-MM-yyyy <narration> <txAmount> <balance>"
    // OR just: "dd-MM-yyyy <narration> <balance>" (opening/closing)
    private fun extractAmountFromDateLine(line: String): Double {
        val m2 = TWO_AMOUNTS.matcher(line)
        if (m2.find()) {
            // first match = tx amount, second = balance
            return parseAmount(m2.group(1)!!)
        }
        // only one amount = balance line, no tx amount
        return 0.0
    }

    // Detect credit from narration lines
    // UPI/CR/ = credit, UPI/DR/ = debit
    // MOB-IMPS-CR = credit, MOB-IMPS-DR = debit
    // NEFT/CR = credit, salary keywords = credit
    private fun detectCredit(narration: String): Boolean {
        val upper = narration.uppercase()
        // Explicit CR/DR in UPI/IMPS/NEFT prefix — most reliable
        if (upper.contains("UPI/CR/") || upper.contains("IMPS/CR/") ||
            upper.contains("NEFT/CR/") || upper.contains("MOB-IMPS-CR") ||
            upper.contains("-CR/") || upper.contains("/CR/")) return true
        if (upper.contains("UPI/DR/") || upper.contains("IMPS/DR/") ||
            upper.contains("NEFT/DR/") || upper.contains("MOB-IMPS-DR") ||
            upper.contains("-DR/") || upper.contains("/DR/")) return false
        // Keyword fallback
        val lower = narration.lowercase()
        if (lower.contains("salary") || lower.contains("credit") ||
            lower.contains("deposit") || lower.contains("refund") ||
            lower.contains("cashback") || lower.contains("reversal") ||
            lower.contains("interest") || lower.contains("inward")) return true
        return false
    }

    // Clean narration: extract meaningful name from UPI/DR/txnid/NAME/bank/...
    private fun cleanNarration(raw: String): String {
        var s = raw.trim()

        // Extract payee name from UPI format: UPI/DR|CR/txnid/PAYEE/bank/ref
        val upiMatch = Regex(
            "(?:UPI|IMPS|NEFT|RTGS)/(?:DR|CR)/[^/]*/([^/]+)",
            RegexOption.IGNORE_CASE
        ).find(s)
        if (upiMatch != null) {
            val payee = upiMatch.groupValues[1].trim()
            if (payee.length >= 2 && !payee.all { it.isDigit() }) {
                s = payee
            }
        }

        // Extract from MOB-IMPS-CR/NAME/bank/...
        val impsMatch = Regex(
            "MOB-IMPS-(?:CR|DR)/([^/]+)",
            RegexOption.IGNORE_CASE
        ).find(s)
        if (impsMatch != null) {
            val payee = impsMatch.groupValues[1].trim()
            if (payee.length >= 2) s = payee
        }

        // Remove reference numbers, special chars
        s = s.replace(Regex("[A-Z0-9]{10,}"), " ")
        s = s.replace(Regex("[^A-Za-z0-9 /&.\\-]"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        if (s.length > 40) s = s.take(40).trim()
        return s.ifEmpty { "Bank Transaction" }
    }

    private val SKIP_LINES = setOf(
        "opening balance", "closing balance", "date particulars",
        "deposits withdrawals balance", "page ", "statement for",
        "customer id", "branch code", "branch name", "ifsc code",
        "phone ", "address "
    )

    private fun shouldSkip(line: String): Boolean {
        val lower = line.lowercase()
        return SKIP_LINES.any { lower.startsWith(it) || lower.contains(it) }
    }

    private fun parseText(text: String): List<BankStatementTx> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<BankStatementTx>()

        // Collect narration lines that appear BEFORE each date line
        val pendingNarration = mutableListOf<String>()

        for (line in lines) {
            if (shouldSkip(line)) {
                pendingNarration.clear()
                continue
            }

            if (isDateLine(line)) {
                val date = extractDateFromLine(line)
                if (date == null) {
                    pendingNarration.clear()
                    continue
                }
                val amount = extractAmountFromDateLine(line)

                if (amount > 0) {
                    // Build narration from pending lines + anything on date line before amounts
                    val dateMatcher = DATE_LINE.matcher(line)
                    val afterDate = if (dateMatcher.find()) dateMatcher.group(2)!! else ""
                    // Remove amounts from afterDate to get inline narration
                    val inlineNarration = TWO_AMOUNTS.matcher(afterDate).replaceAll("").trim()

                    val fullNarration = (pendingNarration + listOfNotNull(inlineNarration.ifBlank { null }))
                        .joinToString(" ")

                    val isCredit = detectCredit(fullNarration)
                    val description = cleanNarration(fullNarration)

                    result.add(BankStatementTx(date, amount, isCredit, description))
                }
                pendingNarration.clear()
            } else {
                // This is a narration/continuation line — accumulate it
                // Skip pure timestamp lines like "16:03:39"
                if (!line.matches(Regex("\\d{2}:\\d{2}:\\d{2}"))) {
                    // Skip "Chq: XXXXXXX" lines
                    if (!line.startsWith("Chq:")) {
                        pendingNarration.add(line)
                    }
                }
            }
        }

        return result
    }
}
