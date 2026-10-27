package com.example.salarycalc_trackincomeandexpense.bankimport

import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import java.security.MessageDigest
import java.util.Locale

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

        for (tx in disambiguateRepeatedRows(txs)) {
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

    /**
     * Different genuine payments can share date/amount/merchant when the bank
     * provides no UTR. A distinct running balance proves they are separate.
     * Keep the first legacy hash for upgrade compatibility, and derive stable
     * row hashes for subsequent entries with different ending balances.
     */
    private fun disambiguateRepeatedRows(txs: List<ParsedTransaction>): List<ParsedTransaction> {
        val observedBalances = mutableMapOf<String, MutableSet<Long>>()
        return txs.map { tx ->
            val ref = BankTransactionMatcher.reference(tx.reference, tx.description)
            if (ref.isNotEmpty() || !tx.balance.isFinite() || tx.balance == 0.0) {
                tx
            } else {
                val cents = kotlin.math.round(tx.balance * 100).toLong()
                val balances = observedBalances.getOrPut(tx.hash) { mutableSetOf() }
                if (balances.add(cents) && balances.size > 1) {
                    val raw = "${tx.hash}|balance=$cents"
                    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
                    val hash = digest.joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
                    tx.copy(hash = hash)
                } else tx
            }
        }
    }

    /** Pure form used for regression tests; catches repeated rows in a batch. */
    fun dedupeAgainstHashes(txs: List<ParsedTransaction>, hashes: Set<String>): DedupeResult {
        val seen = hashes.toMutableSet()
        val unique = mutableListOf<ParsedTransaction>()
        var dupes = 0
        for (tx in disambiguateRepeatedRows(txs)) {
            if (!seen.add(tx.hash)) dupes++ else unique.add(tx)
        }
        return DedupeResult(unique, dupes)
    }
}
