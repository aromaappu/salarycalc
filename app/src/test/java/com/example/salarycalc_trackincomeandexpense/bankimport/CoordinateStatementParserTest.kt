package com.example.salarycalc_trackincomeandexpense.bankimport

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateStatementParserTest {
    @Test
    fun parsesRowsFromCoordinates() {
        val elements = listOf(
            PdfReader.PdfTextElement("01/08/2026", 52f, 421f, 60f, 10f, 1),
            PdfReader.PdfTextElement("UPI AMAZON", 120f, 421f, 100f, 10f, 1),
            PdfReader.PdfTextElement("450.00", 430f, 421f, 40f, 10f, 1),
            PdfReader.PdfTextElement("24550.00", 520f, 421f, 60f, 10f, 1),
            PdfReader.PdfTextElement("02/08/2026", 52f, 410f, 60f, 10f, 1),
            PdfReader.PdfTextElement("SALARY", 120f, 410f, 80f, 10f, 1),
            PdfReader.PdfTextElement("20000.00", 430f, 410f, 40f, 10f, 1),
            PdfReader.PdfTextElement("25000.00", 520f, 410f, 60f, 10f, 1)
        )

        val parsed = CoordinateStatementParser().parse(elements)
        println(parsed)
        assertEquals(2, parsed.size)
        assertEquals("VALID", parsed[0].status)
        assertEquals("2026-08-01", parsed[0].date)
        assertEquals("UPI AMAZON", parsed[0].description)
        assertEquals("450.00".toBigDecimal(), parsed[0].debit)
        assertEquals("2026-08-02", parsed[1].date)
        assertEquals("20000.00".toBigDecimal(), parsed[1].credit)
    }

    @Test
    fun parsesDepositAndWithdrawalRowsWithBalance() {
        val elements = listOf(
            PdfReader.PdfTextElement("03/08/2026", 52f, 421f, 60f, 10f, 1),
            PdfReader.PdfTextElement("CASH DEPOSIT", 120f, 421f, 100f, 10f, 1),
            PdfReader.PdfTextElement("5000.00", 430f, 421f, 40f, 10f, 1),
            PdfReader.PdfTextElement("15000.00", 520f, 421f, 60f, 10f, 1),
            PdfReader.PdfTextElement("04/08/2026", 52f, 410f, 60f, 10f, 1),
            PdfReader.PdfTextElement("ATM WITHDRAWAL", 120f, 410f, 100f, 10f, 1),
            PdfReader.PdfTextElement("1200.00", 430f, 410f, 40f, 10f, 1),
            PdfReader.PdfTextElement("13800.00", 520f, 410f, 60f, 10f, 1)
        )

        val parsed = CoordinateStatementParser().parse(elements)
        assertEquals(2, parsed.size)
        assertEquals("2026-08-03", parsed[0].date)
        assertEquals("CASH DEPOSIT", parsed[0].description)
        assertEquals("5000.00".toBigDecimal(), parsed[0].credit)
        assertEquals("15000.00".toBigDecimal(), parsed[0].balance)
        assertEquals("2026-08-04", parsed[1].date)
        assertEquals("ATM WITHDRAWAL", parsed[1].description)
        assertEquals("1200.00".toBigDecimal(), parsed[1].debit)
        assertEquals("13800.00".toBigDecimal(), parsed[1].balance)
    }

    @Test
    fun creditMarkerMarksRowAsCredit() {
        val elements = listOf(
            PdfReader.PdfTextElement("05/08/2026", 52f, 421f, 60f, 10f, 1),
            PdfReader.PdfTextElement("UPI/CR/123456789012/GOOGLE PAY", 120f, 421f, 100f, 10f, 1),
            PdfReader.PdfTextElement("2500.00", 430f, 421f, 40f, 10f, 1),
            PdfReader.PdfTextElement("27000.00", 520f, 421f, 60f, 10f, 1)
        )

        val parsed = CoordinateStatementParser().parse(elements)
        assertEquals(1, parsed.size)
        assertEquals("VALID", parsed[0].status)
        assertEquals("2500.00".toBigDecimal(), parsed[0].credit)
        assertEquals(null, parsed[0].debit)
    }

    @Test
    fun zeroColumnMarksCreditRowEvenWithoutKeywords() {
        val elements = listOf(
            PdfReader.PdfTextElement("06/08/2026", 52f, 421f, 60f, 10f, 1),
            PdfReader.PdfTextElement("BY TRANSFER", 120f, 421f, 100f, 10f, 1),
            PdfReader.PdfTextElement("0.00", 430f, 421f, 40f, 10f, 1),
            PdfReader.PdfTextElement("5000.00", 480f, 421f, 40f, 10f, 1),
            PdfReader.PdfTextElement("32000.00", 520f, 421f, 60f, 10f, 1)
        )

        val parsed = CoordinateStatementParser().parse(elements)
        assertEquals(1, parsed.size)
        assertEquals("5000.00".toBigDecimal(), parsed[0].credit)
        assertEquals(null, parsed[0].debit)
    }

    @Test
    fun chequeNumberIsNotParsedAsAmount() {
        val elements = listOf(
            PdfReader.PdfTextElement("07/08/2026", 52f, 421f, 60f, 10f, 1),
            PdfReader.PdfTextElement("CHQ PAYMENT", 120f, 421f, 100f, 10f, 1),
            PdfReader.PdfTextElement("452012345", 300f, 421f, 80f, 10f, 1),
            PdfReader.PdfTextElement("3500.00", 430f, 421f, 40f, 10f, 1),
            PdfReader.PdfTextElement("28500.00", 520f, 421f, 60f, 10f, 1)
        )

        val parsed = CoordinateStatementParser().parse(elements)
        assertEquals(1, parsed.size)
        assertEquals("3500.00".toBigDecimal(), parsed[0].debit)
        assertEquals("28500.00".toBigDecimal(), parsed[0].balance)
    }
}
