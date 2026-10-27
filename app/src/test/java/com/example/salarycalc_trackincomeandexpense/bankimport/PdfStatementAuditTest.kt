package com.example.salarycalc_trackincomeandexpense.bankimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfStatementAuditTest {
    private fun tx(date: String, amount: Double, type: String, balance: Double): ParsedTransaction =
        ParsedTransaction(
            date, "Example", amount, type, balance, "",
            HashGenerator.generate(date, amount, "Example", "")
        )

    @Test
    fun reconcilesCompleteMixedDebitCreditStatement() {
        val rows = listOf(
            tx("2026-10-01", 45.0, "DEBIT", 955.0),
            tx("2026-10-02", 100.0, "CREDIT", 1055.0)
        )
        val audit = PdfStatementAudit.check(rows, 1000.0, 1055.0)
        assertTrue(audit.reconciled)
        assertEquals(2, audit.checkedBalances)
        assertEquals(0, audit.inconsistentBalances)
    }

    @Test
    fun identifiesMissingPdfTransactionsByBalanceMismatch() {
        val rows = listOf(
            tx("2026-10-01", 45.0, "DEBIT", 955.0),
            tx("2026-10-03", 50.0, "DEBIT", 1005.0)
        )
        val audit = PdfStatementAudit.check(rows, 1000.0, 1005.0)
        assertFalse(audit.reconciled)
        assertEquals(1, audit.inconsistentBalances)
        assertTrue(audit.totalsMismatch)
    }

    @Test
    fun reversedOrderStatementsAreAuditedChronologically() {
        val rows = listOf(
            tx("2026-10-02", 100.0, "CREDIT", 1055.0),
            tx("2026-10-01", 45.0, "DEBIT", 955.0)
        )
        val audit = PdfStatementAudit.check(rows, 1000.0, 1055.0)
        assertTrue(audit.reconciled)
    }

    @Test
    fun parserDisagreementRequiresReview() {
        val full = listOf(
            tx("2026-10-01", 45.0, "DEBIT", 955.0),
            tx("2026-10-02", 100.0, "CREDIT", 1055.0)
        )
        val partial = listOf(tx("2026-10-01", 45.0, "DEBIT", 955.0))
        val best = PdfCandidateSelector.choose(
            listOf(
                PdfCandidateSelector.Candidate("FULL", full, 0.90f),
                PdfCandidateSelector.Candidate("PARTIAL", partial, 0.99f)
            ),
            1000.0, 1055.0
        )!!
        assertEquals("FULL", best.source)
        assertEquals(2, best.transactions.size)
        assertTrue(best.warnings.any { it.contains("disagree") })
    }

    @Test
    fun differentDebitCreditDirectionIsFlagged() {
        val debit = tx("2026-10-01", 45.0, "DEBIT", 955.0)
        val credit = debit.copy(type = "CREDIT")
        val result = PdfCandidateSelector.choose(
            listOf(
                PdfCandidateSelector.Candidate("REGEX", listOf(debit), 0.90f),
                PdfCandidateSelector.Candidate("LAYOUT", listOf(credit), 0.93f)
            ),
            1000.0, 955.0
        )!!
        assertEquals("REGEX", result.source)
        assertTrue(result.warnings.isNotEmpty())
    }
}
