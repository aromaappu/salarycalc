package com.example.salarycalc_trackincomeandexpense.bankimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserRegistryAndMalformedTest {

    @Test
    fun registryReturnsGenericParserForKnownBanks() {
        assertTrue(ParserRegistry.get(BankDetector.Bank.SBI) is GenericParser)
        assertTrue(ParserRegistry.get(BankDetector.Bank.HDFC) is GenericParser)
    }

    @Test
    fun registryReturnsCanaraParserForCanara() {
        assertTrue(ParserRegistry.get(BankDetector.Bank.CANARA) is CanaraParser)
    }

    @Test
    fun registryReturnsUniversalParserForUnknown() {
        assertTrue(ParserRegistry.get(BankDetector.Bank.UNKNOWN) is UniversalParser)
    }

    @Test
    fun malformedRandomTextDoesNotCrash() {
        val result = UniversalParser().parse(
            "qwerty asdfgh zxcvbn 12345 !!! ### --- www.example.com random junk text"
        )
        // Must not throw; may find nothing
        assertTrue(result.transactions.isEmpty())
    }

    @Test
    fun gibberishWithDatesButNoAmountsDoesNotCrash() {
        val text = """
            hello 01-08-2026 world
            this is not a statement
            more random 2026-08-02 stuff
        """.trimIndent()
        val result = UniversalParser().parse(text)
        // Dates exist but no amounts → no valid transactions
        assertTrue(result.transactions.isEmpty())
    }

    @Test
    fun bankDetectionFindsSbi() {
        val text = """
            State Bank of India
            YONO
            Savings Account Statement
        """.trimIndent()
        assertEquals(BankDetector.Bank.SBI, BankDetector.detect(text))
    }

    @Test
    fun bankDetectionReturnsUnknownForNoSignature() {
        assertEquals(BankDetector.Bank.UNKNOWN, BankDetector.detect("some random text without a bank"))
    }
}
