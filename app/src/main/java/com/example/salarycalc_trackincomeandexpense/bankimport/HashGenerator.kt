package com.example.salarycalc_trackincomeandexpense.bankimport

import java.security.MessageDigest

object HashGenerator {
    fun generate(date: String, amount: Double, description: String, reference: String): String {
        val raw = "$date|${String.format(java.util.Locale.US, "%.2f", amount)}|${description.trim().lowercase(java.util.Locale.ROOT)}|${reference.trim().lowercase(java.util.Locale.ROOT)}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
