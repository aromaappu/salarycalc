package com.example.salarycalc_trackincomeandexpense.bankimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ValidatorAndHashTest {

    private fun tx(
        date: String = "2026-08-01", desc: String = "Amazon", amount: Double = 450.0,
        type: String = "DEBIT", balance: Double = 24550.0, ref: String = "REF1"
    ) = ParsedTransaction(
        date = date, description = desc, amount = amount, type = type,
        balance = balance, reference = ref,
        hash = HashGenerator.generate(date, amount, desc, ref)
    )

    // ── Validation ─────────────────────────────────────────────────────────────

    @Test
    fun validTransactionPasses() {
        val result = TransactionValidator.validate(listOf(tx()))
        assertEquals(1, result.valid.size)
        assertEquals(0, result.rejected)
    }

    @Test
    fun rejectsMissingDate() {
        val result = TransactionValidator.validate(listOf(tx(date = "")))
        assertEquals(0, result.valid.size)
        assertEquals(1, result.rejected)
    }

    @Test
    fun rejectsInvalidDate() {
        val result = TransactionValidator.validate(listOf(tx(date = "2026-13-45")))
        assertEquals(0, result.valid.size)
    }

    @Test
    fun rejectsMissingAmount() {
        val result = TransactionValidator.validate(listOf(tx(amount = 0.0)))
        assertEquals(0, result.valid.size)
        assertEquals(1, result.rejected)
    }

    @Test
    fun rejectsBlankDescription() {
        val result = TransactionValidator.validate(listOf(tx(desc = " ")))
        assertEquals(0, result.valid.size)
    }

    @Test
    fun rejectsInvalidType() {
        val result = TransactionValidator.validate(listOf(tx(type = "WITHDRAW")))
        assertEquals(0, result.valid.size)
    }

    @Test
    fun rejectsBadHash() {
        val bad = tx().copy(hash = "too-short")
        val result = TransactionValidator.validate(listOf(bad))
        assertEquals(0, result.valid.size)
    }

    @Test
    fun skipsInvalidRowsAndKeepsValidOnes() {
        val result = TransactionValidator.validate(listOf(tx(), tx(amount = 0.0), tx()))
        assertEquals(2, result.valid.size)
        assertEquals(1, result.rejected)
    }

    // ── Hash / duplicate detection ─────────────────────────────────────────────

    @Test
    fun sameTransactionProducesSameHash() {
        assertEquals(
            HashGenerator.generate("2026-08-01", 450.0, "Amazon", "REF1"),
            HashGenerator.generate("2026-08-01", 450.0, "Amazon", "REF1")
        )
    }

    @Test
    fun differentAmountProducesDifferentHash() {
        assertNotEquals(
            HashGenerator.generate("2026-08-01", 450.0, "Amazon", "REF1"),
            HashGenerator.generate("2026-08-01", 451.0, "Amazon", "REF1")
        )
    }

    @Test
    fun differentDateProducesDifferentHash() {
        assertNotEquals(
            HashGenerator.generate("2026-08-01", 450.0, "Amazon", "REF1"),
            HashGenerator.generate("2026-08-02", 450.0, "Amazon", "REF1")
        )
    }

    @Test
    fun hashIsStableAcrossDescriptionCase() {
        // Description casing is normalized before hashing → same hash (dedup-safe)
        assertEquals(
            HashGenerator.generate("2026-08-01", 450.0, "Amazon", "ref1"),
            HashGenerator.generate("2026-08-01", 450.0, "AMAZON", "REF1")
        )
    }
}
