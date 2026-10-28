package com.example.salarycalc_trackincomeandexpense

import java.util.Locale

/** Pure parsing rules shared by SMS and payment-app notifications. No Android access. */
object PaymentNotificationParser {
    data class Payment(
        val amount: Double,
        val type: String,
        val name: String,
        val reference: String,
        val accountLast4: String
    )

    private const val NUMBER = "[0-9]+(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?"
    private const val CURRENCY = "(?:₹|rs\\.?|inr|rupees?)"
    private val credit = Regex("\\b(?:credited|received|receive|recvd|deposited|refund(?:ed)?|cashback\\s+credited)\\b", RegexOption.IGNORE_CASE)
    private val debit = Regex("\\b(?:debited|sent|paid|spent|withdrawn|deducted|transferred|payment\\s+successful)\\b", RegexOption.IGNORE_CASE)
    private val failure = Regex("\\b(?:failed|failure|unsuccessful|declined|pending|processing|initiated|cancelled|canceled|scheduled|requested|request|collect\\s+request|mandate|pay\\s+now|will\\s+be\\s+debited|to\\s+be\\s+debited|not\\s+(?:received|paid|debited|credited|successful)|could\\s+not|unable\\s+to)\\b", RegexOption.IGNORE_CASE)
    private val otp = Regex("\\b(?:otp|one[ -]time\\s+password|verification\\s+code)\\b", RegexOption.IGNORE_CASE)

    fun parse(message: String, trustedPaymentApp: Boolean = false, title: String = ""): Payment? {
        val text = message.replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return null
        val isRefund = Regex("\\b(?:refund(?:ed)?|reversal|reversed)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        // A refund can legitimately mention the failed payment it reverses.
        if (otp.containsMatchIn(text) || (!isRefund && failure.containsMatchIn(text))) return null
        // A payment-app title may carry the direction while its body carries the name/amount.
        val combined = if (trustedPaymentApp && title.isNotBlank()) "$title. $text" else text
        if (otp.containsMatchIn(combined) || (!isRefund && failure.containsMatchIn(combined))) return null
        val sentToYou = Regex("\\b(?:sent|paid|transferred)\\s+(?:you|to\\s+you)\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined)
        val isCredit = credit.containsMatchIn(combined) || sentToYou
        val debitText = combined.replace(Regex("\\b(?:sent|paid|transferred)\\s+(?:you|to\\s+you)\\b", RegexOption.IGNORE_CASE), " ")
        val isDebit = debit.containsMatchIn(debitText)
        if (!isCredit && !isDebit) return null
        if (!trustedPaymentApp && !Regex("\\b(?:bank|account|upi|neft|imps|rtgs|balance|txn|utr|credited|debited|card|atm)\\b|a/c", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        // Mixed outgoing/incoming notifications are ambiguous; do not guess their direction.
        if (isCredit && isDebit) return null
        val type = if (isCredit) "credit" else "debit"
        val withoutBalance = combined.replace(
            Regex("(?:available\\s+balance|avl\\.?\\s*bal\\.?|balance|bal\\.)\\s*(?:is|:)?\\s*$CURRENCY?\\s*$NUMBER", RegexOption.IGNORE_CASE),
            " "
        )
        val amountPatterns = listOf(
            Regex("(?:credited|debited|received|receive|recvd|sent|paid|spent|withdrawn|refunded|payment(?:\\s+of)?)\\s*(?:(?:with|by|for|of|:|successfully)\\s*)?$CURRENCY\\s*($NUMBER)(?![0-9,]|\\.[0-9])", RegexOption.IGNORE_CASE),
            Regex("$CURRENCY\\s*($NUMBER)(?![0-9,]|\\.[0-9])", RegexOption.IGNORE_CASE),
            Regex("(?<![0-9,.])($NUMBER)\\s*$CURRENCY\\b", RegexOption.IGNORE_CASE)
        )
        val amount = amountPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(withoutBalance)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        }?.takeIf { it.isFinite() && it > 0.0 && it <= 1_000_000_000.0 } ?: return null
        val reference = listOf(
            Regex("\\b(?:upi\\s*(?:ref(?:erence)?|rrn)|utr|rrn)\\s*(?:no\\.?|number|id)?\\s*[:#.-]?\\s*([a-z0-9]{8,35})\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(?:ref(?:erence)?|txn|transaction)\\s*(?:no\\.?|number|id)?\\s*[:#.-]?\\s*([a-z0-9]{8,35})\\b", RegexOption.IGNORE_CASE)
        ).firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }?.uppercase(Locale.ROOT).orEmpty()
        val account = Regex("(?:a/c|account|acct)\\s*(?:no\\.?|ending|ending\\s+in)?\\s*[:.-]?\\s*[xX*]*([0-9]{4})(?![0-9])", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1).orEmpty()
        return Payment(amount, type, counterparty(combined, type, if (trustedPaymentApp) title else ""), reference, account)
    }

    private fun counterparty(text: String, type: String, title: String): String {
        val direction = if (type == "credit") "from" else "(?:to|at)"
        val stop = "(?=\\s+(?:on|via|using|with|ref|reference|upi|utr|rrn|txn|transaction|a/c|account|avl|balance|has|is|was|for)\\b|[.;!\\n]|$)"
        val patterns = listOf(
            Regex("(?:^|[.!]\\s+)([^.!]+?)\\s+(?:sent|paid|transferred)\\s+(?:you|to\\s+you)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b$direction\\s+(.+?)$stop", RegexOption.IGNORE_CASE),
            // Bank narration: UPI/CR/reference/NAME/bank, or UPI/DR/reference/NAME/bank.
            Regex("\\bUPI/(?:CR|DR)/[a-z0-9]+/([^/]+)", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val name = pattern.find(text)?.groupValues?.get(1)?.let(::cleanName).orEmpty()
            if (name.isNotBlank()) return name
        }
        val titleName = cleanName(title)
        if (titleName.isNotBlank() && !Regex("google pay|gpay|phonepe|paytm|bhim|payment|money", RegexOption.IGNORE_CASE).containsMatchIn(titleName)) return titleName
        return if (type == "credit") "Payment received" else "Payment sent"
    }

    private fun cleanName(raw: String): String {
        val name = raw.replace(Regex("\\s+"), " ").trim(' ', ':', '-', ',', '"', '\'')
        if (name.length !in 2..80 || !name.any { it.isLetter() }) return ""
        if (Regex("^[xX*]+[0-9]+$").matches(name)) return ""
        if (Regex("^(?:your|my|bank|a/c|account|acct|upi|rs\\.?|inr|₹|[xX*]+[0-9])\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)) return ""
        if (Regex("\\b(?:credited|debited|received|paid|sent|balance|successfully)\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)) return ""
        // Preserve an actual person's/shop's name rather than replacing it with a category.
        return if (name == name.uppercase(Locale.ROOT) || name == name.lowercase(Locale.ROOT)) {
            name.split(' ').joinToString(" ") { word ->
                if ('@' in word) word else word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
        } else name
    }
}
