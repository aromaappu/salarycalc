package com.example.salarycalc_trackincomeandexpense.bankimport

object ParserRegistry {

    private val parsers: Map<BankDetector.Bank, BankParser> = buildMap {
        put(BankDetector.Bank.CANARA,      CanaraParser())
        // All other supported banks use the generic 3-column parser
        listOf(
            BankDetector.Bank.SBI,
            BankDetector.Bank.HDFC,
            BankDetector.Bank.ICICI,
            BankDetector.Bank.AXIS,
            BankDetector.Bank.FEDERAL,
            BankDetector.Bank.INDIAN_BANK,
            BankDetector.Bank.UNION_BANK,
            BankDetector.Bank.KOTAK,
            BankDetector.Bank.IDFC,
            BankDetector.Bank.YES_BANK
        ).forEach { put(it, GenericParser(it)) }
        // Unknown layouts rely on the universal, position-independent parser
        put(BankDetector.Bank.UNKNOWN, UniversalParser())
    }

    fun get(bank: BankDetector.Bank): BankParser =
        parsers[bank] ?: UniversalParser(bank)
}
