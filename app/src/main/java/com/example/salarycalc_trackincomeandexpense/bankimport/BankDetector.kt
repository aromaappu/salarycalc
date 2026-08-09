package com.example.salarycalc_trackincomeandexpense.bankimport

object BankDetector {

    enum class Bank(val displayName: String) {
        SBI("SBI"),
        HDFC("HDFC"),
        ICICI("ICICI"),
        AXIS("AXIS"),
        CANARA("CANARA"),
        FEDERAL("FEDERAL"),
        INDIAN_BANK("INDIAN BANK"),
        UNION_BANK("UNION BANK"),
        KOTAK("KOTAK"),
        IDFC("IDFC"),
        YES_BANK("YES BANK"),
        UNKNOWN("UNKNOWN")
    }

    private val SIGNATURES: Map<Bank, List<String>> = mapOf(
        Bank.SBI        to listOf("state bank of india", "sbi", "onlinesbi", "yono"),
        Bank.HDFC       to listOf("hdfc bank", "hdfcbank", "hdfc"),
        Bank.ICICI      to listOf("icici bank", "icicibankltd", "icici"),
        Bank.AXIS       to listOf("axis bank", "axisbank"),
        Bank.CANARA     to listOf("canara bank", "canarabank", "canara"),
        Bank.FEDERAL    to listOf("federal bank", "federalbank", "the federal"),
        Bank.INDIAN_BANK to listOf("indian bank", "indianbank"),
        Bank.UNION_BANK to listOf("union bank of india", "unionbank", "union bank"),
        Bank.KOTAK      to listOf("kotak mahindra", "kotak bank", "kotak"),
        Bank.IDFC       to listOf("idfc first bank", "idfcfirstbank", "idfc"),
        Bank.YES_BANK   to listOf("yes bank", "yesbank")
    )

    fun detect(text: String): Bank {
        val lower = text.lowercase()
        var bestBank = Bank.UNKNOWN
        var bestScore = 0
        for ((bank, keywords) in SIGNATURES) {
            val score = keywords.count { lower.contains(it) }
            if (score > bestScore) {
                bestScore = score
                bestBank = bank
            }
        }
        return bestBank
    }
}
