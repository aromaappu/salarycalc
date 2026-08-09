package com.example.salarycalc_trackincomeandexpense.bankimport

object TransactionValidator {

    data class ValidationResult(
        val valid: List<ParsedTransaction>,
        val rejected: Int
    )

    fun validate(txs: List<ParsedTransaction>): ValidationResult {
        val valid = mutableListOf<ParsedTransaction>()
        var rejected = 0
        for (tx in txs) {
            if (isValid(tx)) valid.add(tx) else rejected++
        }
        return ValidationResult(valid, rejected)
    }

    private fun isValid(tx: ParsedTransaction): Boolean {
        if (tx.date.isBlank() || !tx.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return false
        val parts = tx.date.split("-")
        val y = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        val d = parts[2].toIntOrNull() ?: return false
        if (y !in 2000..2099 || m !in 1..12 || d !in 1..31) return false
        if (tx.amount <= 0.0 || tx.amount > 500_000_000.0) return false
        if (tx.type != "CREDIT" && tx.type != "DEBIT") return false
        if (tx.description.isBlank()) return false
        if (tx.hash.length != 64) return false
        return true
    }
}
