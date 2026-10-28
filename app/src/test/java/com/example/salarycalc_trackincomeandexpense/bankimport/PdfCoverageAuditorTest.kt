package com.example.salarycalc_trackincomeandexpense.bankimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfCoverageAuditorTest {
    private fun transaction(date: String): ParsedTransaction =
        ParsedTransaction(date, "AMAZON", 45.0, "DEBIT", 100.0, "",
            HashGenerator.generate(date, 45.0, "AMAZON", ""))

    @Test fun warnsWhenEntirePageHasUnrepresentedDates() {
        val pages = listOf(
            "01-10-2026 AMAZON 45.00 955.00",
            "03-10-2026 SHOP 20.00 935.00\n04-10-2026 SHOP 10.00 925.00"
        )
        val report = PdfCoverageAuditor.check(pages, listOf(transaction("2026-10-01")))
        assertEquals(listOf(2), report.pagesToReview)
        assertTrue(report.warnings.any { it.contains("2") })
    }

    @Test fun repeatDatesOnDifferentPagesAreNotClaimedMissing() {
        val pages = listOf(
            "01-10-2026 AMAZON 45.00",
            "01-10-2026 SHOP 20.00\n02-10-2026 SHOP 10.00"
        )
        val report = PdfCoverageAuditor.check(pages,
            listOf(transaction("2026-10-01"), transaction("2026-10-02")))
        assertTrue(report.pagesToReview.isEmpty())
    }
}
