package com.example.salarycalc_trackincomeandexpense.bankimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvGeneratorTest {

    private fun tx(
        date: String, desc: String, amount: Double, type: String,
        balance: Double = 0.0, ref: String = ""
    ) = ParsedTransaction(
        date = date, description = desc, amount = amount, type = type,
        balance = balance, reference = ref, hash = HashGenerator.generate(date, amount, desc, ref)
    )

    @Test
    fun headerIsAlwaysStandard() {
        val csv = CsvGenerator.generate(listOf(tx("2026-08-01", "Salary", 20000.0, "CREDIT", 25000.0)))
        assertEquals("Date,Description,Debit,Credit,Balance", csv.lines().first())
    }

    @Test
    fun creditGoesInCreditColumn() {
        val csv = CsvGenerator.generate(listOf(tx("2026-08-01", "Salary", 20000.0, "CREDIT", 25000.0)))
        assertEquals("01-08-2026,Salary,,20000.00,25000.00", csv.lines()[1])
    }

    @Test
    fun debitGoesInDebitColumn() {
        val csv = CsvGenerator.generate(listOf(tx("2026-08-02", "Amazon", 450.0, "DEBIT", 24550.0)))
        assertEquals("02-08-2026,Amazon,450.00,,24550.00", csv.lines()[1])
    }

    @Test
    fun emptyBalanceIsEmpty() {
        val csv = CsvGenerator.generate(listOf(tx("2026-08-02", "Amazon", 450.0, "DEBIT")))
        assertEquals("02-08-2026,Amazon,450.00,,", csv.lines()[1])
    }

    @Test
    fun escapesCommaInDescription() {
        val csv = CsvGenerator.generate(listOf(tx("2026-08-02", "Amazon, Pay", 450.0, "DEBIT")))
        assertTrue(csv.lines()[1].startsWith("02-08-2026,\"Amazon, Pay\","))
    }

    @Test
    fun roundTripThroughCsvReader() {
        val txs = listOf(
            tx("2026-08-01", "Salary", 20000.0, "CREDIT", 25000.0),
            tx("2026-08-02", "Amazon", 450.0, "DEBIT", 24550.0),
            tx("2026-08-03", "Interest", 25.0, "CREDIT", 24575.0)
        )
        val csv = CsvGenerator.generate(txs)
        val read = CsvReader.read(csv.byteInputStream(Charsets.UTF_8))

        assertEquals(txs.size, read.size)
        assertEquals("2026-08-01", read[0].date)
        assertEquals("Salary", read[0].description)
        assertEquals(20000.0, read[0].amount, 0.001)
        assertEquals("CREDIT", read[0].type)
        assertEquals(25000.0, read[0].balance, 0.001)

        assertEquals("2026-08-02", read[1].date)
        assertEquals("Amazon", read[1].description)
        assertEquals(450.0, read[1].amount, 0.001)
        assertEquals("DEBIT", read[1].type)
        assertEquals(24550.0, read[1].balance, 0.001)
    }
}
