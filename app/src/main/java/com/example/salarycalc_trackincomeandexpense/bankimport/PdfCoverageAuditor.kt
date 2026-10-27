package com.example.salarycalc_trackincomeandexpense.bankimport

/**
 * Detects likely lost pages without pretending that repeated transaction dates
 * mean distinct transfers. Statements with date/value-date columns may repeat
 * each date, so these checks are intentionally conservative.
 */
object PdfCoverageAuditor {
    data class Report(val pageCount: Int, val pagesToReview: List<Int>, val warnings: List<String>)

    private val dateTokens = Regex(
        "\\b(?:\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4})\\b"
    )

    fun check(pages: List<String>, transactions: List<ParsedTransaction>): Report {
        val dates = transactions.map { it.date }.toSet()
        val suspicious = mutableListOf<Int>()
        for ((index, page) in pages.withIndex()) {
            // Ignore repeated headers and statement range dates where possible.
            val pageDates = dateTokens.findAll(page).mapNotNull {
                StatementNormalizer.normalizeDate(it.value)
            }.toList()
            val distinct = pageDates.toSet()
            if (distinct.size >= 2 && distinct.none { it in dates }) {
                suspicious.add(index + 1)
            }
        }
        val warnings = mutableListOf<String>()
        if (suspicious.isNotEmpty()) {
            warnings.add("Check PDF page(s) ${suspicious.joinToString(", ")}: dates appear but no extracted transactions match them.")
        }
        if (pages.size > 1 && transactions.isEmpty()) {
            warnings.add("No transactions were extracted from a multi-page statement.")
        }
        return Report(pages.size, suspicious, warnings)
    }
}
