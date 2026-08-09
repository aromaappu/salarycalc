package com.example.salarycalc_trackincomeandexpense.bankimport

/**
 * Unified transaction model produced by every parser (regex, AI, CSV).
 * [hash] is SHA-256 of date+amount+description+reference — used for dedup.
 * [confidence] 0.0–1.0: if < 0.90 the preview screen is shown before import.
 */
data class ParsedTransaction(
    val date: String,           // yyyy-MM-dd
    val description: String,
    val amount: Double,
    val type: String,           // "CREDIT" or "DEBIT"
    val balance: Double,
    val reference: String,
    val hash: String,
    val confidence: Float = 1.0f
)
