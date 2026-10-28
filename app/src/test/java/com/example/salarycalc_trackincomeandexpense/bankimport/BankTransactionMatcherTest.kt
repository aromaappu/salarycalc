package com.example.salarycalc_trackincomeandexpense.bankimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankTransactionMatcherTest {
    private fun sms(reference: String = "", name: String = "AMAZON", amount: Double = 45.0) =
        BankTransactionMatcher.Entry("2026-10-27", "expense", amount, name, reference, "sms")
    private fun bank(reference: String = "", name: String = "UPI/DR/123456789012/AMAZON", amount: Double = 45.0) =
        BankTransactionMatcher.Entry("2026-10-27", "expense", amount, name, reference, "statement")

    @Test fun sameRefAcrossDifferentDescriptionsIsConfirmed() {
        assertEquals(BankTransactionMatcher.Match.CONFIRMED,
            BankTransactionMatcher.classify(sms("123456789012"), bank("123456789012", "AMAZON PAY")))
    }
    @Test fun differentReferencesNeverSuppressTwoRealPayments() {
        assertEquals(BankTransactionMatcher.Match.NONE,
            BankTransactionMatcher.classify(sms("123456789012"), bank("987654321098", "AMAZON")))
    }
    @Test fun amountOrDirectionMismatchNeverMatches() {
        assertEquals(BankTransactionMatcher.Match.NONE,
            BankTransactionMatcher.classify(sms("123456789012"), bank("123456789012", "AMAZON", 46.0)))
        assertEquals(BankTransactionMatcher.Match.NONE,
            BankTransactionMatcher.classify(sms("123456789012"), bank("123456789012")
                .copy(type = "income")))
    }
    @Test fun noReferenceSimilarMerchantRequiresReview() {
        assertEquals(BankTransactionMatcher.Match.POSSIBLE,
            BankTransactionMatcher.classify(sms(name = "Amazon"), bank(name = "Amazon")))
    }
    @Test fun noReferenceDifferentMerchantIsSafe() {
        assertEquals(BankTransactionMatcher.Match.NONE,
            BankTransactionMatcher.classify(sms(name = "Amazon"), bank(name = "Reliance")))
    }
    @Test fun genericOrMissingMerchantNeverAutoDedupes() {
        assertEquals(BankTransactionMatcher.Match.NONE,
            BankTransactionMatcher.classify(sms(name = "UPI Payment"), bank(name = "UPI Payment")))
    }
    @Test fun referenceParsedFromUpiNarrationIsStable() {
        assertEquals("123456789012", BankTransactionMatcher.reference("",
            "UPI/CR/123456789012/SOME MERCHANT"))
        assertEquals(BankTransactionMatcher.strongKey(sms("123456789012")),
            BankTransactionMatcher.strongKey(bank("123456789012", "AMAZON")))
    }
    @Test fun invalidShortReferenceDoesNotBecomeStrongIdentity() {
        assertNull(BankTransactionMatcher.strongKey(sms("12345")))
    }
}
