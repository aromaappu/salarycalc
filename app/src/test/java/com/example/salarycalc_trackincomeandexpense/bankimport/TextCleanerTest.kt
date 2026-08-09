package com.example.salarycalc_trackincomeandexpense.bankimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCleanerTest {

    @Test
    fun removesPageNumbers() {
        val cleaned = TextCleaner.cleanLines("1\nPage 2 of 5\n3\nfoo\n")
        assertEquals(listOf("foo"), cleaned)
    }

    @Test
    fun removesEmptyLines() {
        val cleaned = TextCleaner.cleanLines("\n\nfoo\n\nbar\n\n")
        assertEquals(listOf("foo", "bar"), cleaned)
    }

    @Test
    fun removesOpeningClosingBalance() {
        val cleaned = TextCleaner.cleanLines(
            "Opening Balance 10000.00\n01-08-2026 tx 350.00\nClosing Balance 24500.00"
        )
        assertEquals(listOf("01-08-2026 tx 350.00"), cleaned)
    }

    @Test
    fun removesFooterAndAdvertisements() {
        val cleaned = TextCleaner.cleanLines(
            listOf(
                "This is a system generated statement",
                "For any complaints, contact us at www.bank.co.in",
                "Thank you for banking with us",
                "01-08-2026 tx 350.00"
            ).joinToString("\n")
        )
        assertEquals(listOf("01-08-2026 tx 350.00"), cleaned)
    }

    @Test
    fun keepsTransactionLines() {
        val cleaned = TextCleaner.cleanLines(
            "01-08-2026 UPI PAYMENT AMAZON 350.00 24500.00"
        )
        assertEquals(1, cleaned.size)
        assertTrue(cleaned[0].contains("350.00"))
    }

    @Test
    fun trimsAndCollapsesWhitespace() {
        val cleaned = TextCleaner.cleanLines("  UPI   PAYMENT   AMAZON  \n")
        assertEquals(listOf("UPI PAYMENT AMAZON"), cleaned)
    }

    @Test
    fun emptyInput() {
        assertTrue(TextCleaner.cleanLines("").isEmpty())
        assertFalse(TextCleaner.cleanLines("   ").isNotEmpty())
    }

    @Test
    fun extractsOpeningBalance() {
        assertEquals(1751.59, TextCleaner.openingBalance("Opening Balance 1,751.59")!!, 0.001)
        assertEquals(10000.0, TextCleaner.openingBalance("Opening Balance : Rs. 10,000.00")!!, 0.001)
        assertEquals(null, TextCleaner.openingBalance("01-08-2026 tx 350.00"))
    }

    @Test
    fun extractsClosingBalance() {
        assertEquals(36571.37, TextCleaner.closingBalance("Closing Balance 36,571.37")!!, 0.001)
        assertEquals(24500.0, TextCleaner.closingBalance("Closing Balance : Rs. 24,500.00")!!, 0.001)
        assertEquals(null, TextCleaner.closingBalance("01-08-2026 tx 350.00"))
    }

    @Test
    fun extractsClosingBalanceWhenValueIsOnNextLine() {
        val raw = "Date Narration Balance\n" +
            "01-08-2026 UPI 350.00 24500.00\n" +
            "Closing Balance\n" +
            "36,571.37\n" +
            "This is a system generated statement"
        assertEquals(36571.37, TextCleaner.closingBalance(raw)!!, 0.001)
    }
}
