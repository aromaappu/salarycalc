package com.example.salarycalc_trackincomeandexpense.bankimport

import java.util.Locale

/**
 * Compares independent PDF extraction approaches. Never merges competing
 * interpretations: that could duplicate the same bank transaction with a
 * different description or reverse its debit/credit direction.
 */
object PdfCandidateSelector {
    data class Candidate(val source: String, val transactions: List<ParsedTransaction>, val confidence: Float)
    data class Selection(
        val source: String,
        val transactions: List<ParsedTransaction>,
        val confidence: Float,
        val rejected: Int,
        val warnings: List<String>
    )

    fun choose(
        candidates: List<Candidate>,
        openingBalance: Double?,
        closingBalance: Double?
    ): Selection? {
        val evaluated = candidates.mapNotNull { candidate ->
            val validated = TransactionValidator.validate(candidate.transactions)
            if (validated.valid.isEmpty()) return@mapNotNull null
            val distinct = DuplicateChecker.dedupeAgainstHashes(validated.valid, emptySet()).unique
            val audit = PdfStatementAudit.check(distinct, openingBalance, closingBalance)
            // Successful reconciliation matters more than raw row count;
            // a single fabricated bank amount can make the statement unusable.
            val score = distinct.size * 10 -
                audit.inconsistentBalances * 80 -
                (if (audit.totalsMismatch) 100 else 0) -
                validated.rejected * 20 +
                audit.checkedBalances.coerceAtMost(50) * 2
            Triple(candidate, Triple(distinct, validated.rejected, audit), score)
        }
        val chosen = evaluated.maxWithOrNull(compareBy<Triple<Candidate, Triple<List<ParsedTransaction>, Int, PdfStatementAudit.Result>, Int>> { it.third }
            .thenBy { it.first.confidence }) ?: return null
        val (candidate, details, _) = chosen
        val (rows, rejected, audit) = details
        val warnings = audit.warnings.toMutableList()
        val chosenSignature = signature(rows)
        if (evaluated.any { it !== chosen && signature(it.second.first) != chosenSignature }) {
            warnings.add("PDF reading methods disagree about some transactions. Verify every row against the statement.")
        }
        if (rejected > 0) warnings.add("$rejected extracted rows could not be validated.")
        return Selection(candidate.source, rows, candidate.confidence, rejected, warnings.distinct())
    }

    private fun signature(txs: List<ParsedTransaction>): Map<String, Int> =
        txs.groupingBy { tx ->
            val amount = String.format(Locale.US, "%.2f", tx.amount)
            "\${tx.date}|\$amount|\${tx.type}"
        }.eachCount()
}
