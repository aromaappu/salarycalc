package com.example.salarycalc_trackincomeandexpense.bankimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the shared, bank-agnostic narration reader against narration formats
 * from multiple banks, so other clients' statements work too — not just the
 * Canara PDF they were developed against.
 */
class NarrationReaderTest {

    @Test
    fun cleanNarration_hdfcUpiDebit() {
        assertEquals("VARUN SHARMA", NarrationReader.cleanNarration(
            "UPI/DR/987654321012/VARUN SHARMA/HDFC/**/U@HDFC/PAID"))
    }

    @Test
    fun cleanNarration_sbiUpiCreditWithSpaceBeforeBank() {
        assertEquals("KAVYA", NarrationReader.cleanNarration(
            "UPI/CR/1234567890/KAVYA/ SBI/**"))
    }

    @Test
    fun cleanNarration_iciciUpiWithReference() {
        assertEquals("ROHAN MEHTA", NarrationReader.cleanNarration(
            "UPI/DR/512345678901/ROHAN MEHTA/ICIC/**ABCDEF@ICICI/PAID"))
    }

    @Test
    fun cleanNarration_impsCredit() {
        assertEquals("PRIYA SINGH", NarrationReader.cleanNarration(
            "IMPS/CR/123456789/PRIYA SINGH/SBI/**"))
    }

    @Test
    fun cleanNarration_neftGenericCredit() {
        assertEquals("RAJESH KUMAR", NarrationReader.cleanNarration(
            "NEFT/CR/XXXXXXXXXX/RAJESH KUMAR/HDFC/**"))
    }

    @Test
    fun cleanNarration_paytmMerchant() {
        assertEquals("AMAZON PAY", NarrationReader.cleanNarration(
            "UPI/DR/234567890123/AMAZON PAY/ICIC/**/PAID"))
    }

    @Test
    fun cleanNarration_mobImpsWithBankToken() {
        assertEquals("ARUN KUMAR", NarrationReader.cleanNarration(
            "MOB-IMPS-CR/ARUN KUMAR/SBI/**/REV"))
    }

    @Test
    fun cleanNarration_rtgsCredit() {
        assertEquals("ORACLE SOLUTIONS", NarrationReader.cleanNarration(
            "RTGS/CR/7788990011/ORACLE SOLUTIONS/HDFC/**"))
    }

    @Test
    fun cleanNarration_hexOnlyFallsBackToGenericLabel() {
        assertEquals("UPI Payment", NarrationReader.cleanNarration("9F3A2B7C1D4E5F6A7B8C/"))
    }

    @Test
    fun cleanNarration_timestampAndChequeStripped() {
        assertEquals("NISHANT GOEL", NarrationReader.cleanNarration(
            "12/08/2025 14:22:09 Chq: 881122334455 UPI/CR/5556667778/NISHANT GOEL/YESB/**"))
    }

    @Test
    fun containsRawNarration_identifiesRawText() {
        assertTrue(NarrationReader.containsRawNarration("UPI/DR/12345/NAME/HDFC/**"))
        assertTrue(NarrationReader.containsRawNarration("8CE405A8DDA5C585D438BD5/ 14:22:09 Chq: 123"))
        assertTrue(NarrationReader.containsRawNarration("VIA//AXB2025080917101217974"))
        assertTrue(NarrationReader.containsRawNarration("MOB-IMPS-CR/RAMEESH KI/THE FEDERA/**"))
    }

    @Test
    fun containsRawNarration_cleanNamesNotTouched() {
        assertFalse(NarrationReader.containsRawNarration("AMAZON PAY"))
        assertFalse(NarrationReader.containsRawNarration("RAMEESH KI"))
        assertFalse(NarrationReader.containsRawNarration("CIEL HR SERVICES LIMITED"))
        assertFalse(NarrationReader.containsRawNarration("UPI Payment"))
        assertFalse(NarrationReader.containsRawNarration("Salary"))
        assertFalse(NarrationReader.containsRawNarration(""))
    }
}
