package com.example.salarycalc_trackincomeandexpense.ui.util

import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun monthTotalsAndTodayExpenseUseTransactionDates() {
        val transactions = listOf(
            Transaction(1, AppDatabase.TYPE_INCOME, 100.0, "Salary", "2026-08-01", null, null, 0),
            Transaction(2, AppDatabase.TYPE_EXPENSE, 20.0, "Food", "2026-08-02", null, null, 0),
            Transaction(3, AppDatabase.TYPE_EXPENSE, 50.0, "Travel", "2026-07-31", null, null, 0),
            Transaction(4, AppDatabase.TYPE_INCOME, 300.0, "Bonus", "2026-08-03", null, null, 0)
        )

        val totals = monthTotalsForTransactions(transactions, "2026-08-15")
        assertEquals(400.0, totals.income, 0.0)
        assertEquals(20.0, totals.expense, 0.0)
        assertEquals(20.0, todayExpenseForTransactions(transactions, "2026-08-02"), 0.0)
    }

    @Test
    fun netBalanceUsesStatementClosingWhenSelected() {
        assertEquals(
            1250.50,
            resolveNetBalance(
                statementClosingBalance = 1250.50,
                transactionBalance = 800.0,
                useStatementClosingBalance = true
            ),
            0.0
        )
    }

    @Test
    fun netBalanceUsesAddedTransactionsWhenStatementClosingNotSelected() {
        assertEquals(
            800.0,
            resolveNetBalance(
                statementClosingBalance = 1250.50,
                transactionBalance = 800.0,
                useStatementClosingBalance = false
            ),
            0.0
        )
    }

    @Test
    fun netBalanceFallsBackToTransactionsWhenStatementHasNoClosingBalance() {
        assertEquals(
            800.0,
            resolveNetBalance(
                statementClosingBalance = null,
                transactionBalance = 800.0,
                useStatementClosingBalance = true
            ),
            0.0
        )
    }
}
