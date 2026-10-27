package com.example.salarycalc_trackincomeandexpense.bankimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvReaderImportRegressionTest {

    private fun read(csv: String) = CsvReader.readDetailed(csv.byteInputStream(Charsets.UTF_8))

    @Test
    fun importsEveryRowOfStandardBankCsv() {
        val csv = """
            Transaction Date,Narration,Withdrawal,Deposit,Running Balance,Reference No
            01/10/2026,UPI PAYMENT,45.00,,955.00,UPI123
            02/10/2026,SALARY,,20000.00,20955.00,NEFT123
            03/10/2026,CASH WITHDRAWAL,500.00,,20455.00,ATM123
        """.trimIndent()
        val parsed = read(csv)
        assertEquals(3, parsed.dataRows)
        assertEquals(3, parsed.transactions.size)
        assertEquals(0, parsed.skippedRows)
        assertEquals(listOf("DEBIT", "CREDIT", "DEBIT"), parsed.transactions.map { it.type })
        assertEquals("UPI123", parsed.transactions[0].reference)
    }

    @Test
    fun quotedCommasEscapedQuotesAndMultilineDescriptionsAreNotLost() {
        val csv = "Date,Description,Debit,Credit,Balance\n" +
            "01-10-2026,\"Store, \"\"Main\"\"\nChennai\",45.00,,955.00\n" +
            "02-10-2026,Salary,,1000.00,1955.00\n"
        val parsed = read(csv)
        assertEquals(2, parsed.transactions.size)
        assertEquals(0, parsed.skippedRows)
        assertTrue(parsed.transactions.first().description.contains("Main"))
    }

    @Test
    fun drCrTypeColumnIsNotMisreadAsCreditAmount() {
        val csv = "Date,Description,Amount,DR/CR\n" +
            "01-10-2026,ATM,500.00,DR\n" +
            "02-10-2026,Refund,25.00,CR\n"
        val parsed = read(csv)
        assertEquals(2, parsed.transactions.size)
        assertEquals(listOf("DEBIT", "CREDIT"), parsed.transactions.map { it.type })
        assertFalse(parsed.needsReview)
    }

    @Test
    fun singlePositiveAmountWithoutDirectionRequiresReview() {
        val parsed = read("Date,Description,Amount\n01-10-2026,Payment,45.00\n")
        assertEquals(1, parsed.transactions.size)
        assertTrue(parsed.needsReview)
    }

    @Test
    fun malformedDateOrConflictingAmountsAreCountedNotSilentlyDropped() {
        val csv = "Date,Description,Debit,Credit\n" +
            "01-10-2026,Shop,100.00,\n" +
            "03-10-2026,Bad,50.00,40.00\n" +
            "31-02-2026,Invalid,50.00,\n"
        val parsed = read(csv)
        assertEquals(1, parsed.transactions.size)
        assertEquals(2, parsed.skippedRows)
    }

    @Test
    fun sameCsvRepeatedHasSameIdsAndSkipsAllAgainstExistingSet() {
        val csv = "Date,Description,Debit,Credit,Balance,Reference\n" +
            "01-10-2026,Shop,45.00,,955.00,REF1\n" +
            "02-10-2026,Salary,,1000.00,1955.00,REF2\n"
        val original = read(csv).transactions
        val again = read(csv).transactions
        assertEquals(original.map { it.hash }, again.map { it.hash })
        val deduped = DuplicateChecker.dedupeAgainstHashes(again, original.map { it.hash }.toSet())
        assertTrue(deduped.unique.isEmpty())
        assertEquals(2, deduped.duplicateCount)
    }

    @Test
    fun supportsSemicolonSeparatedStatementAndTwoDigitYears() {
        val csv = "Date;Narration;Withdrawal;Deposit;Balance\n" +
            "01/10/26;Shop;45.00;;955.00\n" +
            "02/10/26;Refund;;20.00;975.00\n"
        val parsed = read(csv)
        assertEquals(2, parsed.transactions.size)
        assertEquals("2026-10-01", parsed.transactions[0].date)
        assertEquals("CREDIT", parsed.transactions[1].type)
    }

    @Test
    fun readsUtf16BankExport() {
        val csv = "Date,Description,Debit,Credit\n01-10-2026,Shop,45.00,\n"
        val withBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            csv.toByteArray(Charsets.UTF_16LE)
        val parsed = CsvReader.readDetailed(withBom.inputStream())
        assertEquals(1, parsed.transactions.size)
    }

    @Test
    fun repeatedRowsInSingleBatchAreDetected() {
        val tx = read("Date,Description,Debit,Credit\n01-10-2026,Shop,45.00,\n")
            .transactions.first()
        val deduped = DuplicateChecker.dedupeAgainstHashes(listOf(tx, tx), emptySet())
        assertEquals(1, deduped.unique.size)
        assertEquals(1, deduped.duplicateCount)
    }
}
