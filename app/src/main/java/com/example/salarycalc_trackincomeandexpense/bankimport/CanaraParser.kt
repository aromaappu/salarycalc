package com.example.salarycalc_trackincomeandexpense.bankimport

import java.util.regex.Pattern

class CanaraParser : BankParser {

    override val bank = BankDetector.Bank.CANARA

    private val DATE_LINE   = Pattern.compile("^(\\d{2})-(\\d{2})-(\\d{4})\\s+(.*)")
    private val AMOUNTS_RE = Pattern.compile(
        "([\\(\\-]?[\\d,]+\\.\\d{2}[\\)]?)\\s+([\\(\\-]?[\\d,]+\\.\\d{2}[\\)]?)(?:\\s+([\\(\\-]?[\\d,]+\\.\\d{2}[\\)]?))?\\s*$"
    )

    override fun parse(text: String): BankParseResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<ParsedTransaction>()
        val pending = mutableListOf<String>()

        for (line in lines) {
            if (shouldSkip(line)) { pending.clear(); continue }

            val dateMatcher = DATE_LINE.matcher(line)
            if (dateMatcher.find()) {
                val d  = dateMatcher.group(1)!!.toInt()
                val mo = dateMatcher.group(2)!!.toInt()
                val y  = dateMatcher.group(3)!!.toInt()
                if (mo !in 1..12 || d !in 1..31 || y !in 2000..2099) { pending.clear(); continue }
                val date = "%04d-%02d-%02d".format(y, mo, d)

                val afterDate = dateMatcher.group(4)!!
                val am = AMOUNTS_RE.matcher(afterDate)
                if (am.find()) {
                    val a1 = StatementNormalizer.normalizeAmount(am.group(1)!!) ?: 0.0
                    val a2 = StatementNormalizer.normalizeAmount(am.group(2)!!) ?: 0.0
                    val a3 = am.group(3)?.let { StatementNormalizer.normalizeAmount(it) }
                    val balance = a3 ?: 0.0
                    val (amount, type) = if (a3 != null) {
                        val withdrawal = a1
                        val deposit = a2
                        when {
                            deposit != 0.0 && withdrawal == 0.0 -> Pair(deposit, "CREDIT")
                            withdrawal != 0.0 && deposit == 0.0 -> Pair(withdrawal, "DEBIT")
                            deposit >= withdrawal -> Pair(deposit, "CREDIT")
                            else -> Pair(withdrawal, "DEBIT")
                        }
                    } else {
                        val fullNarr = (pending + listOf(afterDate.replace(AMOUNTS_RE.toRegex(), "").trim())).joinToString(" ")
                        val isCredit = detectCredit(fullNarr)
                        Pair(a1, if (isCredit) "CREDIT" else "DEBIT")
                    }

                    if (amount > 0.0) {
                        val inlineNarr = AMOUNTS_RE.matcher(afterDate).replaceAll("").trim()
                        val fullNarr = (pending + listOfNotNull(inlineNarr.ifBlank { null })).joinToString(" ")
                        val desc = cleanNarration(fullNarr)
                        val ref  = extractRef(fullNarr)
                        val hash = HashGenerator.generate(date, amount, desc, ref)
                        result.add(ParsedTransaction(date, desc, amount, type, balance, ref, hash, confidence = 0.97f))
                    }
                }
                pending.clear()
            } else {
                if (!line.matches(Regex("\\d{2}:\\d{2}:\\d{2}")) && !line.startsWith("Chq:")) {
                    pending.add(line)
                }
            }
        }

        val confidence = if (result.isEmpty()) 0.0f else 0.97f
        return BankParseResult(result, confidence)
    }

    private fun detectCredit(narr: String): Boolean {
        val u = narr.uppercase()
        if (u.contains("UPI/CR/") || u.contains("IMPS/CR/") || u.contains("NEFT/CR/") ||
            u.contains("MOB-IMPS-CR") || u.contains("-CR/") || u.contains("/CR/")) return true
        if (u.contains("UPI/DR/") || u.contains("IMPS/DR/") || u.contains("NEFT/DR/") ||
            u.contains("MOB-IMPS-DR") || u.contains("-DR/") || u.contains("/DR/")) return false
        val l = narr.lowercase()
        return l.contains("salary") || l.contains("credit") || l.contains("deposit") ||
               l.contains("refund") || l.contains("cashback") || l.contains("interest") ||
               l.contains("inward") || l.contains("received") || l.contains("cash in")
    }

    private fun cleanNarration(raw: String): String {
        var s = raw.trim()
        val upi = Regex("(?:UPI|IMPS|NEFT|RTGS)/(?:DR|CR)/[^/]*/([^/]+)", RegexOption.IGNORE_CASE).find(s)
        if (upi != null) {
            val p = upi.groupValues[1].trim()
            if (p.length >= 2 && !p.all { it.isDigit() }) s = p
        }
        val imps = Regex("MOB-IMPS-(?:CR|DR)/([^/]+)", RegexOption.IGNORE_CASE).find(s)
        if (imps != null) { val p = imps.groupValues[1].trim(); if (p.length >= 2) s = p }
        s = s.replace(Regex("[A-Z0-9]{10,}"), " ")
        s = s.replace(Regex("[^A-Za-z0-9 /&.\\-]"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        if (s.length > 50) s = s.take(50).trim()
        return s.ifEmpty { "Bank Transaction" }
    }

    private fun extractRef(narr: String): String {
        val m = Regex("(?:UPI|IMPS|NEFT|RTGS)/(?:DR|CR)/([^/]+)", RegexOption.IGNORE_CASE).find(narr)
        return m?.groupValues?.get(1)?.trim() ?: ""
    }

    private val SKIP = setOf(
        "opening balance", "closing balance", "date particulars",
        "deposits withdrawals balance", "page ", "statement for",
        "customer id", "branch code", "branch name", "ifsc code"
    )
    private fun shouldSkip(line: String): Boolean {
        val l = line.lowercase()
        return SKIP.any { l.startsWith(it) || l.contains(it) }
    }
}
