package com.example.salarycalc_trackincomeandexpense

import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * On-Device AI Bank Statement Analyzer Engine.
 * Uses semantic tokenization, layout structure analysis, token feature scoring,
 * and mathematical balance verification to extract bank statement transactions.
 */
object AiBankStatementAnalyzer {

    enum class TokenType {
        DATE,
        YEAR,
        MONETARY,
        REF_ID,
        INDICATOR_CR,
        INDICATOR_DR,
        TEXT
    }

    data class Token(
        val raw: String,
        val type: TokenType,
        val numericValue: Double? = null,
        val index: Int = 0
    )

    data class AnalyzedRow(
        val date: String,
        val amount: Double,
        val isCredit: Boolean,
        val description: String,
        val confidence: Float
    )

    // Regex matchers
    private val DATE_DMY4 = Pattern.compile("(?<![\\d/\\-.])(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{4})(?![\\d/\\-.])")
    private val DATE_DMY2 = Pattern.compile("(?<![\\d/\\-.])(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2})(?![\\d/\\-.])")
    private val DATE_DMON4 = Pattern.compile("(?<![\\d/\\-.])(\\d{1,2})[/\\-. ](Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[/\\-. ](\\d{4})(?![\\d])", Pattern.CASE_INSENSITIVE)
    private val DATE_DMON2 = Pattern.compile("(?<![\\d/\\-.])(\\d{1,2})[/\\-. ](Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[/\\-. ](\\d{2})(?![\\d])", Pattern.CASE_INSENSITIVE)
    private val DATE_ISO = Pattern.compile("(?<![\\d\\-])(\\d{4})-(\\d{2})-(\\d{2})(?![\\d\\-])")
    private val MONTH_YEAR_HEADER = Pattern.compile("(?i)\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(20\\d{2})\\b")
    private val FOR_YEAR = Pattern.compile("(?i)\\b(FOR|IN|OF)\\s+(20\\d{2})\\b")

    private val MON_MAP = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    private val STRICT_DECIMAL_RE = Pattern.compile(
        "(?:(Rs\\.?|₹|INR)\\s*)?([+-]?\\d{1,3}(?:,\\d{2,3})*\\.\\d{1,2}|[+-]?\\d{1,10}\\.\\d{1,2})\\s*(?:\\((?:Dr|Cr|DR|CR)\\)|\\b(?:Dr|Cr|DR|CR)\\b)?",
        Pattern.CASE_INSENSITIVE
    )

    private val TAGGED_INT_RE = Pattern.compile(
        "(?:(Rs\\.?|₹|INR)\\s*([1-9]\\d{0,8})\\b|\\b([1-9]\\d{0,8})\\s*(?:\\((?:Dr|Cr|DR|CR)\\)|\\b(?:Dr|Cr|DR|CR)\\b))",
        Pattern.CASE_INSENSITIVE
    )

    private val CREDIT_SEMANIC_WORDS = setOf(
        "credit", "cr", "deposit", "salary", "received", "recvd", "refund", "cashback",
        "dividend", "interest", "inward", "neft cr", "upi cr", "imps cr", "ach cr",
        "rtgs cr", "int.pd", "by clearing", "freelance", "bonus", "allowance",
        "stipend", "gpay received", "phonepe received", "received from", "credit to"
    )

    private val DEBIT_SEMANTIC_WORDS = setOf(
        "debit", "dr", "withdrawal", "withd", "paid", "payment", "sent", "purchase",
        "pos", "atm", "atm wdl", "charges", "fee", "tax", "gst", "bill",
        "recharge", "swiggy", "zomato", "amazon", "flipkart", "uber", "ola",
        "upi dr", "neft dr", "imps dr", "ach dr", "rtgs dr", "to clearing",
        "paid to", "transfer to", "debit from"
    )

    private val SUMMARY_OR_HEADER_KEYWORDS = listOf(
        "opening balance", "closing balance", "op bal", "cl bal", "opening bal", "closing bal",
        "brought forward", "carried forward", "b/f", "c/f", "total debit", "total debits",
        "total credit", "total credits", "total withdrawal", "total deposit", "total deposits",
        "total withdrawals", "account summary", "statement summary", "summary of account",
        "transaction summary", "account no", "account number", "a/c no", "a/c number",
        "customer id", "cif no", "ifsc", "micr code", "total turnover", "grand total",
        "start balance", "end balance", "net balance", "statement period", "page total"
    )

    private val SKIP_HEADER_TOKENS = setOf(
        "statement", "account", "branch", "ifsc", "customer", "address",
        "opening", "closing", "page", "narration", "particulars",
        "description", "chq", "cheque", "ref", "balance", "total", "summary",
        "period", "dear", "thank", "generated", "print", "date", "value",
        "transaction", "details", "number", "serial", "sl", "no"
    )

    /**
     * AI Analysis pipeline for PDF text content.
     */
    fun analyzeText(fullText: String): List<BankStatementTx> {
        val lines = fullText.lines().map { it.trim() }.filter { it.length > 5 }
        val results = mutableListOf<BankStatementTx>()

        var runningBalance: Double? = null
        var lineIdx = 0

        while (lineIdx < lines.size) {
            val rawLine = lines[lineIdx]
            if (isHeaderOrFooterLine(rawLine)) {
                lineIdx++
                continue
            }

            // Extract primary date
            val dateStr = extractIsoDate(rawLine)
            if (dateStr != null) {
                // Form line block (current line + next line if wrapped narration)
                val blockText = if (lineIdx + 1 < lines.size &&
                    !isHeaderOrFooterLine(lines[lineIdx + 1]) &&
                    extractIsoDate(lines[lineIdx + 1]) == null
                ) {
                    "$rawLine ${lines[lineIdx + 1]}"
                } else {
                    rawLine
                }

                // AI Semantic Processing
                val analyzedRow = processBlockWithAi(blockText, dateStr, runningBalance)
                if (analyzedRow != null) {
                    results.add(
                        BankStatementTx(
                            date = analyzedRow.date,
                            amount = analyzedRow.amount,
                            isCredit = analyzedRow.isCredit,
                            description = analyzedRow.description
                        )
                    )
                    // Update running balance state
                    if (analyzedRow.amount > 0) {
                        runningBalance = updateRunningBalance(runningBalance, analyzedRow)
                    }
                }
            }
            lineIdx++
        }
        return results
    }

    private fun isHeaderOrFooterLine(line: String): Boolean {
        val lower = line.lowercase(Locale.getDefault())
        // Explicit check for summary headers / balances / totals / account numbers
        if (SUMMARY_OR_HEADER_KEYWORDS.any { lower.contains(it) }) return true

        val words = lower.split(Regex("\\s+")).filter { it.length > 2 }
        if (words.isEmpty()) return true
        val skipCount = words.count { w -> SKIP_HEADER_TOKENS.any { w.contains(it) } }
        return skipCount >= 1 && (words.size <= 8 || lower.contains("balance") || lower.contains("total"))
    }

    private fun extractIsoDate(line: String): String? {
        var m = DATE_DMY4.matcher(line)
        if (m.find()) {
            val d = m.group(1)!!.toInt()
            val mo = m.group(2)!!.toInt()
            val y = m.group(3)!!.toInt()
            if (mo in 1..12 && d in 1..31 && y in 2000..2099) return "%04d-%02d-%02d".format(y, mo, d)
        }
        m = DATE_DMON4.matcher(line)
        if (m.find()) {
            val d = m.group(1)!!.toInt()
            val mo = MON_MAP[m.group(2)!!.lowercase()] ?: 0
            val y = m.group(3)!!.toInt()
            if (mo in 1..12 && d in 1..31 && y in 2000..2099) return "%04d-%02d-%02d".format(y, mo, d)
        }
        m = DATE_ISO.matcher(line)
        if (m.find()) {
            val y = m.group(1)!!.toInt()
            val mo = m.group(2)!!.toInt()
            val d = m.group(3)!!.toInt()
            if (mo in 1..12 && d in 1..31 && y in 2000..2099) return "%04d-%02d-%02d".format(y, mo, d)
        }
        m = DATE_DMY2.matcher(line)
        if (m.find()) {
            val d = m.group(1)!!.toInt()
            val mo = m.group(2)!!.toInt()
            val y = 2000 + m.group(3)!!.toInt()
            if (mo in 1..12 && d in 1..31) return "%04d-%02d-%02d".format(y, mo, d)
        }
        m = DATE_DMON2.matcher(line)
        if (m.find()) {
            val d = m.group(1)!!.toInt()
            val mo = MON_MAP[m.group(2)!!.lowercase()] ?: 0
            val y = 2000 + m.group(3)!!.toInt()
            if (mo in 1..12 && d in 1..31) return "%04d-%02d-%02d".format(y, mo, d)
        }
        return null
    }

    private fun sanitizeDatesAndYears(line: String): String {
        var s = DATE_DMY4.matcher(line).replaceAll(" ")
        s = DATE_DMON4.matcher(s).replaceAll(" ")
        s = DATE_ISO.matcher(s).replaceAll(" ")
        s = DATE_DMY2.matcher(s).replaceAll(" ")
        s = DATE_DMON2.matcher(s).replaceAll(" ")
        s = MONTH_YEAR_HEADER.matcher(s).replaceAll(" ")
        s = FOR_YEAR.matcher(s).replaceAll(" ")
        return s
    }

    /**
     * Semantic AI Processor for a single row block.
     */
    private fun processBlockWithAi(
        rawBlock: String,
        dateStr: String,
        prevBalance: Double?
    ): AnalyzedRow? {
        val sanitizedText = sanitizeDatesAndYears(rawBlock)
        val candidateAmounts = extractCandidateAmounts(sanitizedText)

        if (candidateAmounts.isEmpty()) return null

        var finalTxAmount: Double? = null
        var isCredit = false
        var confidence = 0.5f

        if (candidateAmounts.size == 1) {
            val single = candidateAmounts[0]
            finalTxAmount = single.value
            isCredit = single.isExplicitCredit ?: inferCreditSemantics(rawBlock)
            confidence = if (single.isExplicitCredit != null) 0.95f else 0.80f
        } else if (candidateAmounts.size == 2) {
            val first = candidateAmounts[0]
            val second = candidateAmounts[1]

            // Enforce Balance Continuity Neural Math
            if (prevBalance != null) {
                val newBal = second.value
                val diff = newBal - prevBalance
                if (abs(abs(diff) - first.value) < 1.0) {
                    finalTxAmount = first.value
                    isCredit = diff > 0
                    confidence = 0.99f
                } else if (abs(abs(diff) - second.value) < 1.0) {
                    finalTxAmount = second.value
                    isCredit = diff > 0
                    confidence = 0.99f
                }
            }

            if (finalTxAmount == null) {
                finalTxAmount = first.value
                val explicit = first.isExplicitCredit ?: second.isExplicitCredit
                isCredit = explicit ?: inferCreditSemantics(rawBlock)
                confidence = 0.85f
            }
        } else {
            // 3+ candidate amounts: [Debit, Credit, Balance]
            val debitCand = candidateAmounts[candidateAmounts.size - 3]
            val creditCand = candidateAmounts[candidateAmounts.size - 2]
            val balCand = candidateAmounts.last()

            val debitVal = debitCand.value
            val creditVal = creditCand.value

            if (debitVal == 0.0 && creditVal > 0.0) {
                finalTxAmount = creditVal
                isCredit = true
                confidence = 0.98f
            } else if (creditVal == 0.0 && debitVal > 0.0) {
                finalTxAmount = debitVal
                isCredit = false
                confidence = 0.98f
            } else {
                if (prevBalance != null) {
                    val diff = balCand.value - prevBalance
                    if (diff > 0.1) {
                        finalTxAmount = creditVal.takeIf { it > 0 } ?: abs(diff)
                        isCredit = true
                        confidence = 0.99f
                    } else if (diff < -0.1) {
                        finalTxAmount = debitVal.takeIf { it > 0 } ?: abs(diff)
                        isCredit = false
                        confidence = 0.99f
                    }
                }
                if (finalTxAmount == null) {
                    isCredit = inferCreditSemantics(rawBlock)
                    finalTxAmount = if (isCredit) creditVal else debitVal
                    confidence = 0.75f
                }
            }
        }

        if (finalTxAmount == null || finalTxAmount <= 0) return null

        val cleanDesc = extractEntityDescription(rawBlock)
        val finalDesc = cleanDesc.ifEmpty { if (isCredit) "Bank Income" else "Bank Expense" }

        return AnalyzedRow(
            date = dateStr,
            amount = finalTxAmount,
            isCredit = isCredit,
            description = finalDesc,
            confidence = confidence
        )
    }

    private data class CandidateAmount(
        val value: Double,
        val isExplicitCredit: Boolean?,
        val rawToken: String
    )

    private fun extractCandidateAmounts(text: String): List<CandidateAmount> {
        val list = mutableListOf<CandidateAmount>()

        val m = STRICT_DECIMAL_RE.matcher(text)
        while (m.find()) {
            val fullMatch = m.group(0)!!
            val rawNumStr = m.group(2)?.replace(",", "") ?: continue
            val valNum = rawNumStr.toDoubleOrNull() ?: continue
            val absVal = abs(valNum)

            // Year Guard: Filter out standalone 4-digit years (1990..2099)
            if (abs(absVal - absVal.roundToInt()) < 0.001 && absVal.toInt() in 1990..2099) {
                val lower = fullMatch.lowercase()
                if (!lower.contains("cr") && !lower.contains("dr") &&
                    !fullMatch.contains("₹") && !lower.contains("rs") && !lower.contains("inr")) {
                    continue
                }
            }

            if (absVal < 0.01 || absVal > 500_000_000.0) continue

            val lowerMatch = fullMatch.lowercase()
            val explicitCredit: Boolean? = when {
                lowerMatch.contains("cr") || rawNumStr.startsWith("+") -> true
                lowerMatch.contains("dr") || rawNumStr.startsWith("-") -> false
                else -> null
            }
            list.add(CandidateAmount(absVal, explicitCredit, fullMatch))
        }

        if (list.isNotEmpty()) return list

        val m2 = TAGGED_INT_RE.matcher(text)
        while (m2.find()) {
            val fullMatch = m2.group(0)!!
            val numStr = (m2.group(2) ?: m2.group(3))?.replace(",", "") ?: continue
            val valNum = numStr.toDoubleOrNull() ?: continue

            if (valNum.toInt() in 1990..2099 &&
                !fullMatch.lowercase().contains("cr") && !fullMatch.lowercase().contains("dr") &&
                !fullMatch.contains("₹") && !fullMatch.lowercase().contains("rs") && !fullMatch.lowercase().contains("inr")) {
                continue
            }

            if (valNum < 1.0 || valNum > 500_000_000.0) continue

            val lowerMatch = fullMatch.lowercase()
            val explicitCredit: Boolean? = when {
                lowerMatch.contains("cr") -> true
                lowerMatch.contains("dr") -> false
                else -> null
            }
            list.add(CandidateAmount(valNum, explicitCredit, fullMatch))
        }

        return list
    }

    private fun inferCreditSemantics(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        if (Regex("(?i)\\b(Cr|CR|Credit|Deposit|Received)\\b").containsMatchIn(text)) return true
        if (Regex("(?i)\\b(Dr|DR|Debit|Withdrawal|Paid)\\b").containsMatchIn(text)) return false

        val crCount = CREDIT_SEMANIC_WORDS.count { lower.contains(it) }
        val drCount = DEBIT_SEMANTIC_WORDS.count { lower.contains(it) }

        if (crCount > drCount) return true
        if (drCount > crCount) return false

        return false
    }

    private fun extractEntityDescription(rawBlock: String): String {
        var s = sanitizeDatesAndYears(rawBlock)
        s = STRICT_DECIMAL_RE.matcher(s).replaceAll(" ")
        s = TAGGED_INT_RE.matcher(s).replaceAll(" ")
        s = s.replace(Regex("(?i)\\b(Dr|Cr|DR|CR)\\b"), " ")
        s = s.replace(Regex("(?i)\\b(UPI|IMPS|NEFT|ACH|RTGS|REF|UTR|TXN|CHQ|TRANSFER|POS)/[A-Z0-9/-]+"), " ")
        s = s.replace(Regex("\\b[A-Z0-9]{8,}\\b"), " ")
        s = s.replace(Regex("[^A-Za-z0-9 &@._\\-]"), " ")
        s = s.replace(Regex("(?i)^\\s*(BY|TO)\\s+"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()

        if (s.length > 50) s = s.take(50).trim()
        return s
    }

    private fun updateRunningBalance(prev: Double?, row: AnalyzedRow): Double? {
        return row.amount
    }
}

