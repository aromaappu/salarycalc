package com.example.salarycalc_trackincomeandexpense.bankimport

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Confirmed matches need a real bank reference. Name/amount/date similarity
 * is review-only: two valid payments to the same shop can look identical.
 */
object BankTransactionMatcher {
    enum class Match { NONE, POSSIBLE, CONFIRMED }
    data class Entry(
        val date: String, val type: String, val amount: Double,
        val name: String, val reference: String = "", val source: String = ""
    )

    private val embeddedReference = Regex(
        "(?:UPI|IMPS|NEFT|RTGS)[/\\s-]+(?:DR|CR)[/\\s-]+([A-Z0-9]{10,25})",
        RegexOption.IGNORE_CASE
    )
    private val labelledReference = Regex(
        "(?:UTR|UPI\\s*REF|REFERENCE|REF(?:ERENCE)?\\s*(?:NO|NUMBER)?)[\\s:#-]+([A-Z0-9]{10,25})",
        RegexOption.IGNORE_CASE
    )

    fun reference(raw: String, narration: String = ""): String {
        val value = raw.trim().ifEmpty {
            embeddedReference.find(narration)?.groupValues?.get(1)
                ?: labelledReference.find(narration)?.groupValues?.get(1).orEmpty()
        }.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
        return if (value.length in 10..25 && value.count { it.isDigit() } >= 8) value else ""
    }

    fun strongKey(entry: Entry): String? {
        val ref = reference(entry.reference, entry.name)
        if (ref.isEmpty() || !entry.amount.isFinite() || entry.amount <= 0) return null
        return "$ref|${entry.type.lowercase(Locale.ROOT)}|${(entry.amount * 100).roundToLong()}"
    }

    fun classify(incoming: Entry, existing: Entry): Match {
        if (incoming.type.lowercase(Locale.ROOT) != existing.type.lowercase(Locale.ROOT)) return Match.NONE
        if (!incoming.amount.isFinite() || !existing.amount.isFinite() ||
            (incoming.amount * 100).roundToLong() != (existing.amount * 100).roundToLong()
        ) return Match.NONE
        val left = reference(incoming.reference, incoming.name)
        val right = reference(existing.reference, existing.name)
        if (left.isNotEmpty() && right.isNotEmpty())
            return if (left == right) Match.CONFIRMED else Match.NONE
        if (incoming.date != existing.date || !sameMerchant(incoming.name, existing.name)) return Match.NONE
        return Match.POSSIBLE
    }

    private fun sameMerchant(first: String, second: String): Boolean {
        fun merchant(value: String): String {
            val cleaned = if (NarrationReader.containsRawNarration(value)) {
                NarrationReader.cleanNarration(value)
            } else value
            return cleaned.uppercase(Locale.ROOT)
                .replace(Regex("\\b(UPI|IMPS|NEFT|RTGS|PAYMENT|PAID|TRANSFER|TO|FROM|DEBIT|CREDIT)\\b"), " ")
                .replace(Regex("[^A-Z]"), "")
        }
        val a = merchant(first)
        val b = merchant(second)
        val generic = setOf("UPI", "UPIPAYMENT", "BANKTRANSACTION", "OTHER", "CASH", "UNKNOWN", "EXPENSE", "INCOME")
        if (a.length < 5 || b.length < 5 || a in generic || b in generic) return false
        return a == b || (minOf(a.length, b.length) >= 8 && (a.contains(b) || b.contains(a)))
    }
}
