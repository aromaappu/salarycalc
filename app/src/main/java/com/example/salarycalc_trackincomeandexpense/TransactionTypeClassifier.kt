package com.example.salarycalc_trackincomeandexpense

object TransactionTypeClassifier {
    private val debitHints = listOf(
        "debit", "dr", "withdraw", "withdrawal", "payment", "purchase", "pos",
        "atm", "charge", "fee", "tax", "gst", "bill", "recharge", "paid", "sent",
        "spent", "deducted", "cash out", "transfer to"
    )

    private val creditHints = listOf(
        "credit", "cr", "deposit", "salary", "received", "refund", "cashback",
        "interest", "inward", "reversal", "dividend", "credited", "incoming"
    )

    fun classify(text: String): String {
        val lower = text.lowercase()
        val hasDebit = debitHints.any { lower.contains(it) }
        val hasCredit = creditHints.any { lower.contains(it) }

        return when {
            hasDebit && !hasCredit -> "DEBIT"
            hasCredit && !hasDebit -> "CREDIT"
            hasDebit && hasCredit -> {
                val debitPriority = listOf(
                    "withdraw", "withdrawal", "debit", "payment", "purchase", "pos",
                    "atm", "charge", "fee", "tax", "bill", "paid", "sent", "spent",
                    "deducted"
                )
                if (debitPriority.any { lower.contains(it) }) "DEBIT" else "CREDIT"
            }
            else -> "DEBIT"
        }
    }
}
