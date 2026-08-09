package com.example.salarycalc_trackincomeandexpense.importer

import com.example.salarycalc_trackincomeandexpense.bankimport.ParsedTransaction
import com.example.salarycalc_trackincomeandexpense.bankimport.TextCleaner
import com.example.salarycalc_trackincomeandexpense.bankimport.UniversalParser

/**
 * Simplest public entry point for parsing raw statement text.
 * Cleans the text, then runs the position-independent universal parser.
 */
object Parser {
    fun parse(raw: String): List<ParsedTransaction> {
        val cleaned = TextCleaner.clean(raw)
        return UniversalParser().parse(cleaned, TextCleaner.openingBalance(raw)).transactions
    }
}
