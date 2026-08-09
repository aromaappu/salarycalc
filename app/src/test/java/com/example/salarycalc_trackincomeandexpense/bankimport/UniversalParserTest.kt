package com.example.salarycalc_trackincomeandexpense.bankimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the "broken TXT" layout handling — the case where PDF text extraction
 * splits a single transaction across multiple lines:
 *
 *     01-08-2026
 *     UPI PAYMENT
 *     Amazon
 *     350.00
 *     24500.00
 */
class UniversalParserTest {

    private val parser = UniversalParser()

    @Test
    fun parsesBrokenLayoutAndExtractsMerchantName() {
        val text = """
            Statement of Account
            Page 1 of 1

            01-08-2026
            UPI PAYMENT
            Amazon
            350.00
            24500.00
        """.trimIndent()

        val result = parser.parse(text)
        assertEquals(1, result.transactions.size)
        val tx = result.transactions[0]
        assertEquals("2026-08-01", tx.date)
        assertEquals(350.0, tx.amount, 0.001)
        assertEquals("DEBIT", tx.type)
        assertEquals("Amazon", tx.description)
        assertEquals(24500.0, tx.balance, 0.001)
    }

    @Test
    fun parsesSameLineLayout() {
        val text = """
            01-08-2026 UPI PAYMENT AMAZON 350.00 24500.00
            02-08-2026 Salary 20000.00 44500.00
        """.trimIndent()

        val result = parser.parse(text)
        assertEquals(2, result.transactions.size)

        val debit = result.transactions[0]
        assertEquals("2026-08-01", debit.date)
        assertEquals(350.0, debit.amount, 0.001)
        assertEquals("DEBIT", debit.type)
        assertEquals(24500.0, debit.balance, 0.001)

        // Second row: balance continuity (44500 - 24500 = 20000) proves it is a credit
        val credit = result.transactions[1]
        assertEquals("2026-08-02", credit.date)
        assertEquals(20000.0, credit.amount, 0.001)
        assertEquals("CREDIT", credit.type)
        assertEquals(44500.0, credit.balance, 0.001)
    }

    @Test
    fun openingBalanceSeedsFirstTransactionTypeByContinuity() {
        // Narration for the first row is scrambled across lines and contains a
        // misleading "MOB-IMPS-CR" token. With the opening balance seeded, the
        // balance continuity (1751.59 - 19.00 = 1732.59) proves it is a debit.
        val text = """
            05-08-2025
            3333336626934/05/08/2025
            16:03:39
            Chq: 521752048493
            MOB-IMPS-CR/RAMEESH
            19.00 1,732.59
        """.trimIndent()

        val result = parser.parse(text, 1751.59)
        assertEquals(1, result.transactions.size)
        val tx = result.transactions[0]
        assertEquals("2025-08-05", tx.date)
        assertEquals(19.0, tx.amount, 0.001)
        assertEquals("DEBIT", tx.type)
        assertEquals(1732.59, tx.balance, 0.001)
    }

    @Test
    fun parsesDebitCreditBalanceTriple() {
        val text = "03-08-2026 NEFT/CR/SALARY 0.00 20000.00 44500.00"
        val result = parser.parse(text)
        assertEquals(1, result.transactions.size)
        val tx = result.transactions[0]
        assertEquals("CREDIT", tx.type)
        assertEquals(20000.0, tx.amount, 0.001)
        assertEquals(44500.0, tx.balance, 0.001)
    }

    @Test
    fun parsesBrokenTripleLayout() {
        val text = """
            03-08-2026
            NEFT/CR/SALARY
            0.00
            20000.00
            44500.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals(1, result.transactions.size)
        assertEquals("CREDIT", result.transactions[0].type)
        assertEquals(20000.0, result.transactions[0].amount, 0.001)
        assertEquals(44500.0, result.transactions[0].balance, 0.001)
    }

    @Test
    fun ignoresOpeningAndClosingBalanceLines() {
        val text = """
            Statement of Account
            Account No: 123456789
            Opening Balance 10000.00

            01-08-2026
            UPI PAYMENT
            Amazon
            350.00
            24500.00

            Closing Balance 24500.00
            Total Debits 350.00
            Total Credits 0.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals(1, result.transactions.size)
    }

    @Test
    fun ignoresPageNumbersAndHeaders() {
        val text = """
            State Bank of India
            Savings Account
            Page 1 of 2

            01-08-2026
            UPI PAYMENT
            Amazon
            350.00
            24500.00

            2

            02-08-2026
            Salary
            20000.00
            44500.00
            Page 2 of 2
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals(2, result.transactions.size)
    }

    @Test
    fun emptyStatementReturnsEmpty() {
        val result = parser.parse("")
        assertTrue(result.transactions.isEmpty())
    }

    @Test
    fun headerOnlyTextReturnsEmpty() {
        val text = """
            State Bank of India
            Statement of Account
            Page 1 of 1
            Opening Balance 100.00
            Closing Balance 100.00
        """.trimIndent()
        val result = parser.parse(text)
        assertTrue(result.transactions.isEmpty())
    }

    @Test
    fun transactionWithoutDescriptionStillParsed() {
        val text = """
            05-08-2026
            150.00
            24650.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals(1, result.transactions.size)
        assertEquals(150.0, result.transactions[0].amount, 0.001)
    }

    @Test
    fun dateOnlyWithSingleAmountParsed() {
        val text = """
            05-08-2026
            150.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals(1, result.transactions.size)
        assertEquals("2026-08-05", result.transactions[0].date)
        assertEquals(150.0, result.transactions[0].amount, 0.001)
    }

    @Test
    fun creditKeywordsDetectCreditWithoutBalanceContinuity() {
        val text = """
            06-08-2026
            Salary Credit
            50000.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals(1, result.transactions.size)
        assertEquals("CREDIT", result.transactions[0].type)
    }

    @Test
    fun supportsMonthNameDates() {
        val text = """
            Aug 01 2026
            UPI PAYMENT
            Amazon
            350.00
            24500.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals(1, result.transactions.size)
        assertEquals("2026-08-01", result.transactions[0].date)
    }

    @Test
    fun largeStatementParsesAllRows() {
        val sb = StringBuilder()
        val txCount = 1500
        var balance = 10000.0
        for (i in 0 until txCount) {
            val amount = 10.0 + i
            balance += amount
            sb.append("01-08-2026\n")
            sb.append("UPI PAYMENT\n")
            sb.append("Merchant $i\n")
            sb.append("%.2f\n".format(amount))
            sb.append("%.2f\n".format(balance))
        }
        val result = parser.parse(sb.toString())
        assertEquals(txCount, result.transactions.size)
    }
}
