package com.example.salarycalc_trackincomeandexpense

import com.example.salarycalc_trackincomeandexpense.importer.Parser
import org.junit.Assert.assertEquals
import org.junit.Test

class ParserTest {
    @Test
    fun parsesWrappedDescription() {
        val raw = """
        01-08-2026
        UPI PAYMENT
        AMAZON INDIA
        350.00
        24500.00
        """.trimIndent()
        val parsed = Parser.parse(raw)
        assertEquals(1, parsed.size)
        assertEquals("2026-08-01", parsed[0].date)
        assert(parsed[0].description.contains("AMAZON INDIA"))
    }
}
