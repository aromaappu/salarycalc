package com.example.salarycalc_trackincomeandexpense

import org.junit.Assert.*
import org.junit.Test

class PaymentDuplicateMatcherTest {
    private fun entry(source: String, time: Long = 100_000, ref: String = "", name: String = "Ravi", amount: Double = 45.0, type: String = "credit", text: String = "received") =
        PaymentDuplicateMatcher.entry(PaymentNotificationParser.Payment(amount, type, name, ref, ""), time, source, text)

    @Test fun bankAndUpiWithSameReferenceAreOnePayment() {
        assertTrue(PaymentDuplicateMatcher.matches(entry("sms", ref = "123456789012"), entry("upi", time = 170_000, ref = "123456789012")))
    }

    @Test fun differentReferencesAreSeparateEvenForSameAmountAndName() {
        assertFalse(PaymentDuplicateMatcher.matches(entry("sms", ref = "123456789012"), entry("upi", ref = "987654321098")))
    }

    @Test fun missingReferenceUsesShortCrossSourceWindow() {
        assertTrue(PaymentDuplicateMatcher.matches(entry("sms", name = "Payment received"), entry("upi", time = 140_000)))
        assertFalse(PaymentDuplicateMatcher.matches(entry("sms"), entry("upi", time = 195_000)))
        assertFalse(PaymentDuplicateMatcher.matches(entry("sms", name = "Anu"), entry("upi")))
    }

    @Test fun repeatedGenuineSameDayPaymentsAreNotDiscarded() {
        assertFalse(PaymentDuplicateMatcher.matches(entry("sms"), entry("sms", time = 101_000)))
        assertFalse(PaymentDuplicateMatcher.matches(entry("sms"), entry("upi", time = 3_700_000)))
    }

    @Test fun exactRepostIsIgnoredAndDirectionAmountMatter() {
        assertTrue(PaymentDuplicateMatcher.matches(entry("sms"), entry("sms")))
        assertFalse(PaymentDuplicateMatcher.matches(entry("sms"), entry("upi", type = "debit")))
        assertFalse(PaymentDuplicateMatcher.matches(entry("sms"), entry("upi", amount = 46.0)))
    }

    @Test fun matchedSourcesDoNotSwallowTheNextUpiPayment() {
        val matched = entry("sms").copy(sources = setOf("sms", "upi"))
        assertFalse(PaymentDuplicateMatcher.matches(matched, entry("upi", time = 110_000)))
    }
}
