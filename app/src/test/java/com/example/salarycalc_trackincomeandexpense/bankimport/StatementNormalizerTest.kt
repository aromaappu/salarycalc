package com.example.salarycalc_trackincomeandexpense.bankimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatementNormalizerTest {

    // ── Dates ──────────────────────────────────────────────────────────────────

    @Test
    fun normalizeDate_ddmmyyyyWithSlashes() {
        assertEquals("2026-08-01", StatementNormalizer.normalizeDate("01/08/2026"))
    }

    @Test
    fun normalizeDate_ddmmyyyyWithDashes() {
        assertEquals("2026-08-01", StatementNormalizer.normalizeDate("01-08-2026"))
    }

    @Test
    fun normalizeDate_iso() {
        assertEquals("2026-08-01", StatementNormalizer.normalizeDate("2026-08-01"))
    }

    @Test
    fun normalizeDate_dayMonthNameYear() {
        assertEquals("2026-08-01", StatementNormalizer.normalizeDate("01 Aug 2026"))
    }

    @Test
    fun normalizeDate_monthNameDayYear() {
        assertEquals("2026-08-01", StatementNormalizer.normalizeDate("Aug 01 2026"))
    }

    @Test
    fun normalizeDate_monthNameDayYearWithComma() {
        assertEquals("2026-08-01", StatementNormalizer.normalizeDate("Aug 01, 2026"))
    }

    @Test
    fun normalizeDate_ddMonYyyy() {
        assertEquals("2026-08-01", StatementNormalizer.normalizeDate("01-Aug-2026"))
    }

    @Test
    fun normalizeDate_rejectsInvalidDate() {
        assertNull(StatementNormalizer.normalizeDate("31/02/2026"))
        assertNull(StatementNormalizer.normalizeDate("not a date"))
        assertNull(StatementNormalizer.normalizeDate(""))
    }

    @Test
    fun normalizeDate_rejectsOutOfRangeYear() {
        assertNull(StatementNormalizer.normalizeDate("01-08-1999"))
    }

    // ── Amounts ─────────────────────────────────────────────────────────────────

    @Test
    fun normalizeAmount_withComma() {
        assertEquals(2500.0, StatementNormalizer.normalizeAmount("2,500.00")!!, 0.001)
    }

    @Test
    fun normalizeAmount_plainDecimal() {
        assertEquals(2500.0, StatementNormalizer.normalizeAmount("2500.00")!!, 0.001)
    }

    @Test
    fun normalizeAmount_rupeePrefix() {
        assertEquals(2500.0, StatementNormalizer.normalizeAmount("₹2,500.00")!!, 0.001)
    }

    @Test
    fun normalizeAmount_creditTag() {
        assertEquals(2500.0, StatementNormalizer.normalizeAmount("2,500.00 CR")!!, 0.001)
        assertEquals(2500.0, StatementNormalizer.normalizeAmount("CR 2,500.00")!!, 0.001)
    }

    @Test
    fun normalizeAmount_debitTagIsNegative() {
        assertEquals(-250.0, StatementNormalizer.normalizeAmount("DR 250.00")!!, 0.001)
        assertEquals(-250.0, StatementNormalizer.normalizeAmount("250.00 DR")!!, 0.001)
    }

    @Test
    fun normalizeAmount_parenthesizedNegative() {
        assertEquals(-250.0, StatementNormalizer.normalizeAmount("(250.00)")!!, 0.001)
    }

    @Test
    fun normalizeAmount_trailingMinus() {
        assertEquals(-250.0, StatementNormalizer.normalizeAmount("250.00-")!!, 0.001)
    }

    @Test
    fun normalizeAmount_rejectsNonNumber() {
        assertNull(StatementNormalizer.normalizeAmount("abc"))
        assertNull(StatementNormalizer.normalizeAmount(""))
    }

    // ── CSV date conversion ─────────────────────────────────────────────────────

    @Test
    fun toCsvDate_isoToDdMmYyyy() {
        assertEquals("01-08-2026", StatementNormalizer.toCsvDate("2026-08-01"))
    }

    // ── Type variations across banks ────────────────────────────────────────────

    @Test
    fun normalizeType_creditVariants() {
        assertEquals("CREDIT", StatementNormalizer.normalizeType("CREDIT"))
        assertEquals("CREDIT", StatementNormalizer.normalizeType("Credit"))
        assertEquals("CREDIT", StatementNormalizer.normalizeType("CREDITED"))
        assertEquals("CREDIT", StatementNormalizer.normalizeType("CREDITS"))
        assertEquals("CREDIT", StatementNormalizer.normalizeType("DEPOSIT"))
        assertEquals("CREDIT", StatementNormalizer.normalizeType("DEPOSITED"))
        assertEquals("CREDIT", StatementNormalizer.normalizeType("DEPOSITS"))
        assertEquals("CREDIT", StatementNormalizer.normalizeType("DEPOSITING"))
        assertEquals("CREDIT", StatementNormalizer.normalizeType("CR"))
        assertEquals("CREDIT", StatementNormalizer.normalizeType("C"))
    }

    @Test
    fun normalizeType_debitVariants() {
        assertEquals("DEBIT", StatementNormalizer.normalizeType("DEBIT"))
        assertEquals("DEBIT", StatementNormalizer.normalizeType("Debit"))
        assertEquals("DEBIT", StatementNormalizer.normalizeType("DEBITED"))
        assertEquals("DEBIT", StatementNormalizer.normalizeType("DEBITS"))
        assertEquals("DEBIT", StatementNormalizer.normalizeType("WITHDRAW"))
        assertEquals("DEBIT", StatementNormalizer.normalizeType("WITHDREW"))
        assertEquals("DEBIT", StatementNormalizer.normalizeType("WITHDRAWAL"))
        assertEquals("DEBIT", StatementNormalizer.normalizeType("WITHDRAWALS"))
        assertEquals("DEBIT", StatementNormalizer.normalizeType("WITHDRAWN"))
        assertEquals("DEBIT", StatementNormalizer.normalizeType("DR"))
        assertEquals("DEBIT", StatementNormalizer.normalizeType("D"))
    }

    @Test
    fun normalizeType_unknownTokenRejected() {
        assertNull(StatementNormalizer.normalizeType("TRANSFER"))
        assertNull(StatementNormalizer.normalizeType(""))
        assertNull(StatementNormalizer.normalizeType("   "))
    }

    // ── Narration names ─────────────────────────────────────────────────────────

    @Test
    fun cleanNarration_upiPayeeName() {
        assertEquals("REMYA MOL", StatementNormalizer.cleanNarration(
            "6FF30BAB6E4106/08/08/2025 12:47:48 Chq: 101162953886 UPI/CR/558608122092/REMYA MOL/YESB/**"))
    }

    @Test
    fun cleanNarration_mobImpsMultiWordName() {
        assertEquals("RAMEESH KI", StatementNormalizer.cleanNarration(
            "3333336626934/05/08/2025 16:03:39 Chq: 521752048493 MOB-IMPS-CR/RAMEESH KI/THE FEDERA/14612100129101/ARO"))
    }

    @Test
    fun cleanNarration_slashInsideName() {
        assertEquals("M/S.MAL AB", StatementNormalizer.cleanNarration(
            "F684AD1B06510D/18/09/2025 12:31:28 Chq: 101487854078 UPI/DR/562704050153/M/S.MAL AB/ICIC/**E2329@ICICI/PAID"))
    }

    @Test
    fun cleanNarration_neftSalaryName() {
        assertEquals("CIEL HR SERVICES LIMITED", StatementNormalizer.cleanNarration(
            "17E535/ 22:06:15 Chq: 103316854014 NEFT CR-HDFCH01005997433- HDFC0000240-CIEL HR SERVICES LIMITED--0001-"))
    }

    @Test
    fun cleanNarration_atmWithdrawal() {
        assertEquals("MALABARCANCERCENTRETHI", StatementNormalizer.cleanNarration(
            "684B5CE7/ 13:48:09 Chq: 101317724297 ATM CASH-SCCPV370- MALABARCANCERCENTRETHI"))
    }

    @Test
    fun cleanNarration_hexOrViaNoiseBecomesGenericLabel() {
        assertEquals("UPI Payment", StatementNormalizer.cleanNarration("8CE405A8DDA5C585D438BD5/"))
        assertEquals("UPI Payment", StatementNormalizer.cleanNarration("VIA//AXB2025080917101217974"))
        assertEquals("UPI Payment", StatementNormalizer.cleanNarration("C74BEDAE7D9D7EE4CA001F/0"))
    }

    @Test
    fun cleanNarration_naviBillKeptWhole() {
        assertEquals("NAVI BILL", StatementNormalizer.cleanNarration(
            "P/9048025607/ 16:39:36/521716800204 Chq: 521716800204 UPI/DR/521756025964/NAVI BILL/ICIC/**HARGE@ICICI/PAID VIA//PMS2508"))
    }
}
