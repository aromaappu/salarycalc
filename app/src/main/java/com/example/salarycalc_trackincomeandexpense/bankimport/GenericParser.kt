package com.example.salarycalc_trackincomeandexpense.bankimport

import java.util.regex.Pattern

/**
 * Generic parser for banks that use the standard 3-column table:
 * Date | Narration | Chq/Ref | Debit | Credit | Balance
 * Works for SBI, HDFC, ICICI, AXIS, FEDERAL, INDIAN BANK,
 * UNION BANK, KOTAK, IDFC, YES BANK.
 */
class GenericParser(override val bank: BankDetector.Bank) : BankParser {

    // dd-MM-yyyy or dd/MM/yyyy or yyyy-MM-dd
    private val DATE_RE = Pattern.compile(
        "(?:^|\\s)(\\d{2}[/\\-]\\d{2}[/\\-]\\d{4}|\\d{4}[/\\-]\\d{2}[/\\-]\\d{2})(?:\\s|$)"
    )
    // Two or three decimal amounts at end of line
    private val AMOUNTS_RE = Pattern.compile(
        "([\\(\\-]?[\\d,]+\\.\\d{2}[\\)]?)\\s+([\\(\\-]?[\\d,]+\\.\\d{2}[\\)]?)(?:\\s+([\\(\\-]?[\\d,]+\\.\\d{2}[\\)]?))?\\s*$"
    )

    override fun parse(text: String): BankParseResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<ParsedTransaction>()
        val pending = mutableListOf<String>()

        for (line in lines) {
            if (isHeaderOrSkip(line)) { pending.clear(); continue }

            val dateMatch = DATE_RE.matcher(line)
            if (dateMatch.find()) {
                val rawDate = dateMatch.group(1)!!
                val date = StatementNormalizer.normalizeDate(rawDate)
                if (date == null) { pending.clear(); continue }

                val amtMatch = AMOUNTS_RE.matcher(line)
                if (amtMatch.find()) {
                    val a1 = StatementNormalizer.normalizeAmount(amtMatch.group(1)!!) ?: 0.0
                    val a2 = StatementNormalizer.normalizeAmount(amtMatch.group(2)!!) ?: 0.0
                    val a3 = amtMatch.group(3)?.let { StatementNormalizer.normalizeAmount(it) }

                    val (amount, balance, type) = when {
                        // 3 amounts: debit, credit, balance — one of first two is 0
                        a3 != null -> {
                            when {
                                a1 == 0.0 && a2 != 0.0 -> Triple(kotlin.math.abs(a2), kotlin.math.abs(a3), "CREDIT")
                                a2 == 0.0 && a1 != 0.0 -> Triple(kotlin.math.abs(a1), kotlin.math.abs(a3), "DEBIT")
                                else -> {
                                    val narr = (pending + listOf(line)).joinToString(" ")
                                    val cr = detectCredit(narr)
                                    Triple(kotlin.math.abs(if (cr) a2 else a1), kotlin.math.abs(a3), if (cr) "CREDIT" else "DEBIT")
                                }
                            }
                        }
                        // 2 amounts: tx + balance
                        else -> {
                            val narr = (pending + listOf(line)).joinToString(" ")
                            val cr = detectCredit(narr)
                            Triple(kotlin.math.abs(a1), kotlin.math.abs(a2), if (cr) "CREDIT" else "DEBIT")
                        }
                    }

                    if (amount > 0) {
                        val fullNarr = (pending + listOf(line)).joinToString(" ")
                        val desc = StatementNormalizer.normalizeDescription(extractDesc(fullNarr))
                        val ref  = extractRef(fullNarr)
                        val hash = HashGenerator.generate(date, amount, desc, ref)
                        result.add(ParsedTransaction(date, desc, amount, type, balance, ref, hash, confidence = 0.88f))
                    }
                }
                pending.clear()
            } else {
                pending.add(line)
                if (pending.size > 5) pending.removeAt(0) // cap lookahead
            }
        }

        val confidence = if (result.isEmpty()) 0.0f else 0.88f
        return BankParseResult(result, confidence)
    }

    private fun detectCredit(text: String): Boolean {
        val u = text.uppercase()
        if (u.contains("/CR/") || u.contains("-CR/") || u.contains("CREDIT") ||
            u.contains("SALARY") || u.contains("DEPOSIT") || u.contains("REFUND") ||
            u.contains("CASHBACK") || u.contains("INTEREST") || u.contains("INWARD") ||
            u.contains("RECEIVED") || u.contains("CASH IN")) return true
        if (u.contains("/DR/") || u.contains("-DR/") || u.contains("DEBIT") ||
            u.contains("WITHDRAWAL") || u.contains("PAYMENT") || u.contains("PURCHASE")) return false
        return false
    }

    private fun extractDesc(text: String): String {
        // Remove dates and amounts, keep meaningful words
        var s = text
        s = s.replace(Regex("\\d{2}[/\\-]\\d{2}[/\\-]\\d{4}"), " ")
        s = s.replace(Regex("\\d{4}[/\\-]\\d{2}[/\\-]\\d{2}"), " ")
        s = s.replace(Regex("[\\d,]+\\.\\d{2}"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        return s.ifEmpty { "Bank Transaction" }
    }

    private fun extractRef(text: String): String {
        val m = Regex("(?:UPI|IMPS|NEFT|RTGS)/(?:DR|CR)/([^/\\s]+)", RegexOption.IGNORE_CASE).find(text)
        if (m != null) return m.groupValues[1].trim()
        val ref = Regex("\\b([A-Z0-9]{10,20})\\b").find(text)
        return ref?.value ?: ""
    }

    private val SKIP_KEYWORDS = listOf(
        "opening balance", "closing balance", "statement", "account no",
        "branch", "ifsc", "customer", "page", "total", "summary",
        "date", "narration", "particulars", "chq", "debit", "credit", "balance"
    )
    private fun isHeaderOrSkip(line: String): Boolean {
        val l = line.lowercase()
        return SKIP_KEYWORDS.count { l.contains(it) } >= 2
    }
}
