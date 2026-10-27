package com.example.salarycalc_trackincomeandexpense.bankimport

import com.example.salarycalc_trackincomeandexpense.data.AppDatabase

object DuplicateChecker {

    data class DedupeResult(
        val unique: List<ParsedTransaction>,
        val duplicateCount: Int
    )

    fun dedupe(txs: List<ParsedTransaction>, db: AppDatabase): DedupeResult {
        val seenHashes = db.getAllTxHashes().toMutableSet()
        val unique = mutableListOf<ParsedTransaction>()
        var dupes = 0
        for (tx in txs) {
            if (!seenHashes.add(tx.hash)) dupes++
            else unique.add(tx)
        }
        return DedupeResult(unique, dupes)
    }
}
