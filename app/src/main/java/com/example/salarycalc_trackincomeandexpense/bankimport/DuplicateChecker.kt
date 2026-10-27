package com.example.salarycalc_trackincomeandexpense.bankimport

import com.example.salarycalc_trackincomeandexpense.data.AppDatabase

object DuplicateChecker {

    data class DedupeResult(
        val unique: List<ParsedTransaction>,
        val duplicateCount: Int,
        val possibleDuplicateHashes: Set<String> = emptySet()
    )

    fun dedupe(txs: List<ParsedTransaction>, db: AppDatabase): DedupeResult {
        val storedHashes = db.getAllTxHashes()
        val observed = storedHashes.toMutableSet()
        val unique = mutableListOf<ParsedTransaction>()
        val possible = mutableSetOf<String>()
        var confirmed = 0

        for (tx in txs) {
            if (!observed.add(tx.hash)) {
                confirmed++
                continue
            }
            val type = if (tx.type == "CREDIT") AppDatabase.TYPE_INCOME else AppDatabase.TYPE_EXPENSE
            val current = BankTransactionMatcher.Entry(
                tx.date, type, tx.amount, tx.description, tx.reference, "statement"
            )
            val matches = db.getPotentialTransactionMatches(type, tx.amount, tx.date)
                .map { BankTransactionMatcher.classify(current, it) }
            when {
                BankTransactionMatcher.Match.CONFIRMED in matches -> confirmed++
                else -> {
                    unique.add(tx)
                    if (BankTransactionMatcher.Match.POSSIBLE in matches) possible.add(tx.hash)
                }
            }
        }
        return DedupeResult(unique, confirmed, possible)
    }

    /** Pure form used for regression tests; catches repeated rows in a batch. */
    fun dedupeAgainstHashes(txs: List<ParsedTransaction>, hashes: Set<String>): DedupeResult {
        val seen = hashes.toMutableSet()
        val unique = mutableListOf<ParsedTransaction>()
        var dupes = 0
        for (tx in txs) {
            if (!seen.add(tx.hash)) dupes++ else unique.add(tx)
        }
        return DedupeResult(unique, dupes)
    }
}
