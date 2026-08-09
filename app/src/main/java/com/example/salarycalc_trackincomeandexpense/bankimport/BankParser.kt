package com.example.salarycalc_trackincomeandexpense.bankimport

/**
 * Every bank-specific regex parser implements this.
 * [parse] returns transactions + a confidence score 0.0–1.0.
 */
interface BankParser {
    val bank: BankDetector.Bank
    fun parse(text: String): BankParseResult
}

data class BankParseResult(
    val transactions: List<ParsedTransaction>,
    val confidence: Float   // 0.0–1.0
)
