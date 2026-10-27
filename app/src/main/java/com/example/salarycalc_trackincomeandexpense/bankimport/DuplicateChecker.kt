package com.example.salarycalc_trackincomeandexpense.bankimport

import com.example.salarycalc_trackincomeandexpense.data.AppDatabase

object DuplicateChecker {

    data class DedupeResult(
        val unique: List<ParsedTransaction>,
        val duplicateCount: Int
    )

    fun dedupe(txs: List<ParsedTransaction>, db: AppDatabase): DedupeResult {
        return dedupeAgainstHashes(txs, db.getAllTxHashes())
    }

    /** Pure form used by unit tests; also catches repeated rows in one upload. */
    fun dedupeAgainstHashes(txs: List<ParsedTransaction>, hashes: Set<String>): DedupeResult {
        val seenHashes = hashes.toMutableSet()
        val unique = mutableListOf<ParsedTransaction>()
        var dupes = 0
        for (tx in txs) {
            if (!seenHashes.add(tx.hash)) dupes++
            else unique.add(tx)
        }
        return DedupeResult(unique, dupes)
    }
}
