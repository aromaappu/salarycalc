package com.example.salarycalc_trackincomeandexpense.bankimport

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Central place to normalize dates and amounts across every parser.
 * Kept as plain JVM code so it can be unit tested without an emulator.
 */
object StatementNormalizer {

    // All supported statement date formats. Order matters — the first match wins.
    private val INPUT_FORMATS = listOf(
        "dd-MM-yyyy", "dd/MM/yyyy", "dd.MM.yyyy",
        "yyyy-MM-dd", "yyyy/MM/dd",
        "dd-MMM-yyyy", "dd/MMM/yyyy",
        "dd MMM yyyy", "dd MMM, yyyy",
        "MMM-dd-yyyy", "MMM dd yyyy", "MMM dd, yyyy"
    )
    private val OUTPUT_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Normalizes any supported date string to yyyy-MM-dd.
     * Returns null when the value is not a recognizable date.
     */
    fun normalizeDate(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        for (fmt in INPUT_FORMATS) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.isLenient = false
                val date = sdf.parse(trimmed) ?: continue
                // Reject obviously wrong years (also guards against lenient month/day rollovers)
                val year = Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
                if (year !in 2000..2099) continue
                return OUTPUT_FORMAT.format(date)
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Normalizes a raw amount token to a Double.
     * Handles: commas, ₹/Rs/INR prefixes, CR/DR tags, parentheses (negative),
     * and trailing minus signs. Returns null when not a number.
     */
    fun normalizeAmount(raw: String): Double? {
        if (raw.isBlank()) return null
        var s = raw.trim()
            .replace("₹", "")
            .replace(Regex("Rs\\.?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("INR", RegexOption.IGNORE_CASE), "")
            .replace("\u00A0", " ")
            .replace("–", "-").replace("—", "-").replace("−", "-")
            .trim()

        // CR / DR tags on either side, e.g. "2500.00 CR", "Dr 2500.00", "CR 2,500.00"
        var negative = false
        val lower = s.lowercase(Locale.US)
        val crSuffix = lower.endsWith("cr")
        val drSuffix = lower.endsWith("dr")
        val crPrefix = lower.startsWith("cr ")
        val drPrefix = lower.startsWith("dr ")
        if (crSuffix || crPrefix) {
            s = s.drop(if (crPrefix) 3 else 0).dropLast(if (crSuffix) 2 else 0).trim()
        } else if (drSuffix || drPrefix) {
            negative = true
            s = s.drop(if (drPrefix) 3 else 0).dropLast(if (drSuffix) 2 else 0).trim()
        }

        // Parenthesized or dash-prefixed/suffixed values are negative
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1).trim()
        }
        if (s.startsWith("-") || s.endsWith("-")) {
            negative = true
            s = s.trim('-').trim()
        }

        s = s.replace(",", "").trim()
        val value = s.toDoubleOrNull() ?: return null
        return if (negative) -value else value
    }

    /** Strips non-alphanumeric symbols and collapses whitespace; used for DB `name` column. */
    fun normalizeDescription(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("[^A-Za-z0-9 /&.\\-@]"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        if (s.length > 60) s = s.take(60).trim()
        return s.ifEmpty { "Bank Transaction" }
    }

    /**
     * Extracts the party name from a raw bank narration, hiding timestamps,
     * cheque numbers, UPI/VIA reference tokens, hex hashes and bank codes.
     * Falls back to a generic label when the narration carries no readable
     * name (only hashes / VIA references).
     *
     * Backward-compatible delegate to the shared, bank-agnostic
     * [NarrationReader] model.
     */
    fun cleanNarration(raw: String): String = NarrationReader.cleanNarration(raw)

    /**
     * Maps a type token to a canonical CREDIT/DEBIT string.
     * Tolerant of word variations across banks: credited/credit(s), deposit(s),
     * debited/debit(s), withdrew/withdraw(s), withdrawal(s), withdrawn, etc.
     */
    fun normalizeType(raw: String): String? {
        val up = raw.trim().uppercase(Locale.US)
        if (up.isEmpty()) return null
        val word = up.split(Regex("\\s+")).first()
        return when {
            word == "CR" || word == "C" ||
                word.startsWith("CREDIT") || word.startsWith("DEPOSIT") -> "CREDIT"
            word == "DR" || word == "D" ||
                word.startsWith("DEBIT") || word.startsWith("WITHDRAW") ||
                word.startsWith("WITHDREW") || word.startsWith("WITHDRAWN") -> "DEBIT"
            else -> null
        }
    }

    /** Converts a normalized yyyy-MM-dd date to the standard CSV date dd-MM-yyyy. */
    fun toCsvDate(date: String): String {
        val parts = date.split("-")
        if (parts.size != 3) return date
        return "${parts[2]}-${parts[1]}-${parts[0]}"
    }
}
