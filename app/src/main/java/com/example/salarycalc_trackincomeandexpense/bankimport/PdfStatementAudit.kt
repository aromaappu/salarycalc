package com.example.salarycalc_trackincomeandexpense.bankimport

import java.math.BigDecimal
import kotlin.math.abs

/**
 * Independent checks for PDF parsing. A transaction list may look plausible
 * while silently dropping an entire page or reversing debit and credit.
 * Never treat these checks as proof that every statement row was found.
 */
object PdfStatementAudit {
    data class Result(
        val reconciled: Boolean,
        val checkedBalances: Int,
        val inconsistentBalances: Int,
        val totalsMismatch: Boolean,
        val warnings: List<String>
    )

    fun check(
        transactions: List<ParsedTransaction>,
        openingBalance: Double?,
        closingBalance: Double?
    ): Result {
        if (transactions.isEmpty()) {
            return Result(false, 0, 0, false, listOf("No transactions were extracted."))
        }
        val chronological = if (transactions.size > 1 &&
            transactions.first().date > transactions.last().date
        ) transactions.asReversed() else transactions

        var previous: BigDecimal? = openingBalance?.let { money(it) }
        var comparisons = 0
        var mismatches = 0
        for (tx in chronological) {
            if (tx.balance == 0.0) {
                // Zero also represents a missing balance in the older parser.
                previous = null
                continue
            }
            val current = money(tx.balance)
            if (previous != null) {
                val expected = previous.add(
                    if (tx.type == "CREDIT") money(tx.amount) else money(-tx.amount)
                )
                comparisons++
                if (expected.subtract(current).abs() > BigDecimal("0.01")) mismatches++
            }
            previous = current
        }
        val totalsMismatch = if (openingBalance != null && closingBalance != null) {
            val net = transactions.fold(money(openingBalance)) { value, tx ->
                value.add(money(if (tx.type == "CREDIT") tx.amount else -tx.amount))
            }
            net.subtract(money(closingBalance)).abs() > BigDecimal("0.01")
        } else false
        val warnings = mutableListOf<String>()
        if (mismatches > 0) warnings.add("$mismatches running balances do not reconcile.")
        if (totalsMismatch) warnings.add("The transaction totals do not match opening and closing balances.")
        if (comparisons == 0 && openingBalance == null && closingBalance == null) {
            warnings.add("This PDF has no balance evidence to verify missing transactions.")
        }
        return Result(
            reconciled = (comparisons > 0 || (openingBalance != null && closingBalance != null)) &&
                mismatches == 0 && !totalsMismatch,
            checkedBalances = comparisons,
            inconsistentBalances = mismatches,
            totalsMismatch = totalsMismatch,
            warnings = warnings
        )
    }

    private fun money(number: Double): BigDecimal = BigDecimal.valueOf(number).setScale(2, java.math.RoundingMode.HALF_UP)
}
