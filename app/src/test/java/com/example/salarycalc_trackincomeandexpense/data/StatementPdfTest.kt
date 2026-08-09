package com.example.salarycalc_trackincomeandexpense.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StatementPdfTest {

    private fun tx(id: Long, type: String, amount: Double, name: String, date: String) =
        Transaction(id, type, amount, name, date, null, null, 0L)

    @Test
    fun totalsSeparateCreditAndDebit() {
        val txs = listOf(
            tx(1, AppDatabase.TYPE_INCOME, 1000.0, "Salary", "2026-08-01"),
            tx(2, AppDatabase.TYPE_EXPENSE, 250.0, "Food", "2026-08-02"),
            tx(3, AppDatabase.TYPE_INCOME, 500.5, "Bonus", "2026-08-03"),
            tx(4, AppDatabase.TYPE_EXPENSE, 75.25, "Travel", "2026-08-04")
        )
        val totals = StatementPdf.totals(txs)
        assertEquals(1500.5, totals.credit, 0.001)
        assertEquals(325.25, totals.debit, 0.001)
    }

    @Test
    fun totalsEmptyList() {
        val totals = StatementPdf.totals(emptyList())
        assertEquals(0.0, totals.credit, 0.001)
        assertEquals(0.0, totals.debit, 0.001)
    }
}
