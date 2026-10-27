package com.example.salarycalc_trackincomeandexpense.bankimport

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class CoordinateStatementParser {

    data class ColumnRange(val startX: Float, val endX: Float)

    data class BankStatementLayout(
        val dateColumn: ColumnRange,
        val descriptionColumn: ColumnRange,
        val debitColumn: ColumnRange,
        val creditColumn: ColumnRange,
        val balanceColumn: ColumnRange
    )

    data class ParsedRow(
        val date: String?,
        val description: String,
        val debit: BigDecimal?,
        val credit: BigDecimal?,
        val balance: BigDecimal?,
        val status: String,
        val errorReason: String? = null,
        val sourcePage: Int
    )

    companion object {
        const val ROW_Y_TOLERANCE = 6f
    }

    fun parse(elements: List<PdfReader.PdfTextElement>): List<ParsedRow> {
        if (elements.isEmpty()) return emptyList()
        val layout = inferLayout(elements)
        val rows = groupByRow(elements, layout)
        // PDFBox already supplies elements in reading order. Sorting by Y
        // reverses some banks' statements and merges same-Y rows across pages.
        return rows.mapNotNull { row -> buildRow(row, layout) }
    }

    private fun inferLayout(elements: List<PdfReader.PdfTextElement>): BankStatementLayout {
        val xs = elements.map { it.x }.sorted()
        val minX = xs.firstOrNull() ?: 0f
        val maxX = xs.lastOrNull() ?: 0f
        val span = if (maxX > minX) maxX - minX else 200f
        val quarter = span / 4f
        return BankStatementLayout(
            dateColumn = ColumnRange(minX, minX + quarter),
            descriptionColumn = ColumnRange(minX + quarter, minX + quarter * 2f),
            debitColumn = ColumnRange(minX + quarter * 2f, minX + quarter * 3f),
            creditColumn = ColumnRange(minX + quarter * 3f, maxX),
            balanceColumn = ColumnRange(minX + quarter * 3f, maxX)
        )
    }

    private fun groupByRow(elements: List<PdfReader.PdfTextElement>, layout: BankStatementLayout): List<List<PdfReader.PdfTextElement>> {
        val rows = mutableListOf<MutableList<PdfReader.PdfTextElement>>()
        elements.forEach { element ->
            val last = rows.lastOrNull()
            val candidate = last?.firstOrNull()
            if (last != null && candidate != null &&
                element.pageNumber == candidate.pageNumber &&
                kotlin.math.abs(element.y - candidate.y) <= ROW_Y_TOLERANCE
            ) {
                last.add(element)
            } else {
                rows.add(mutableListOf(element))
            }
        }
        return rows
    }

    private fun buildRow(rowElements: List<PdfReader.PdfTextElement>, layout: BankStatementLayout): ParsedRow? {
        val texts = rowElements.map { it.text.trim() }.filter { it.isNotBlank() }
        if (texts.isEmpty()) return null
        val date = texts.firstOrNull { matchesDate(it) }?.let { normalizeDate(it) }
        val indicator = rowElements.firstOrNull { isIndicator(it.text) }?.text?.uppercase(Locale.US)
        val description = rowElements.filterNot { matchesDate(it.text) || isAmount(it.text) || isIndicator(it.text) }
            .map { it.text.trim() }.joinToString(" ")

        // Bank amounts always carry two decimals; integer tokens (cheque/reference
        // numbers, page numbers) are not transaction amounts.
        val amountElements = rowElements.filter { isAmount(it.text) }.sortedBy { it.x }
        val decimalElements = amountElements.filter { it.text.matches(Regex(".*\\.\\d{2}")) }
        val amountValues = (decimalElements.ifEmpty { amountElements }).map { parseAmount(it.text)!! }

        var txAmount = amountValues.firstOrNull()
        val balanceAmount = if (amountValues.size >= 2) amountValues.lastOrNull() else null

        // Classic [debit, credit, balance] rows print 0.00 on the inactive side,
        // so a leading 0.00 means the real amount is the next value.
        if (amountValues.size >= 3 && amountValues[0].signum() == 0 && amountValues[1].signum() > 0) {
            txAmount = amountValues[1]
        }

        val type = inferRowType(description, indicator, amountValues)
        val debit = if (type == "DEBIT") txAmount else null
        val credit = if (type == "CREDIT") txAmount else null
        val status = when {
            date == null -> "NEEDS_REVIEW"
            description.isBlank() -> "NEEDS_REVIEW"
            txAmount == null || txAmount.signum() <= 0 -> "NEEDS_REVIEW"
            else -> "VALID"
        }
        return ParsedRow(
            date = date,
            description = description,
            debit = debit,
            credit = credit,
            balance = balanceAmount,
            status = status,
            errorReason = if (status == "VALID") null else "Incomplete coordinate parse",
            sourcePage = rowElements.firstOrNull()?.pageNumber ?: 1
        )
    }

    /**
     * Credit/debit classification without trusting column order (PDF text
     * extraction does not preserve it). Explicit CR/DR markers are authoritative,
     * then zero-padded columns, then narration keywords.
     */
    private fun inferRowType(description: String, indicator: String?, amounts: List<BigDecimal>): String {
        when (indicator) {
            "CR" -> return "CREDIT"
            "DR" -> return "DEBIT"
        }
        val up = description.uppercase(Locale.US)
        if (up.contains("/CR/") || up.contains("-CR/") || up.contains("UPI/CR") || up.contains("NEFT/CR") ||
            up.contains("IMPS/CR") || up.contains("RTGS/CR") || up.contains("MOB-IMPS-CR")) return "CREDIT"
        if (up.contains("/DR/") || up.contains("-DR/") || up.contains("UPI/DR") || up.contains("NEFT/DR") ||
            up.contains("IMPS/DR") || up.contains("RTGS/DR") || up.contains("MOB-IMPS-DR")) return "DEBIT"
        if (amounts.size >= 3 && amounts[0].signum() == 0 && amounts[1].signum() > 0) return "CREDIT"
        if (amounts.size >= 3 && amounts[1].signum() == 0 && amounts[0].signum() > 0) return "DEBIT"
        val lower = description.lowercase()
        if (lower.contains("deposit") || lower.contains("salary") || lower.contains("credit") ||
            lower.contains("refund") || lower.contains("cashback") || lower.contains("received") ||
            lower.contains("inward") || lower.contains("cash in")) return "CREDIT"
        if (lower.contains("withdraw") || lower.contains("payment") || lower.contains("atm") ||
            lower.contains("debit") || lower.contains("purchase") || lower.contains("pos") ||
            lower.contains("sent") || lower.contains("paid")) return "DEBIT"
        return "DEBIT"
    }

    private fun parseAmount(text: String): BigDecimal? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.contains("/") || trimmed.contains("-")) return null
        if (trimmed.any { it.isLetter() }) return null
        val cleaned = trimmed.replace(Regex("[^0-9,.-]"), "")
        if (cleaned.isBlank()) return null
        if (!Regex("^(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)$").matches(cleaned)) {
            return null
        }
        return try {
            BigDecimal(cleaned.replace(",", ""))
        } catch (_: Exception) {
            null
        }
    }

    private fun matchesDate(text: String): Boolean {
        return try {
            LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy")) != null
        } catch (_: Exception) {
            try {
                LocalDate.parse(text, DateTimeFormatter.ofPattern("dd-MM-yyyy")) != null
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun normalizeDate(text: String): String? {
        return try {
            LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy")).toString()
        } catch (_: Exception) {
            try {
                LocalDate.parse(text, DateTimeFormatter.ofPattern("dd-MM-yyyy")).toString()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun isAmount(text: String): Boolean {
        return parseAmount(text) != null
    }

    private fun isIndicator(text: String): Boolean {
        return text.equals("CR", ignoreCase = true) || text.equals("DR", ignoreCase = true)
    }
}
