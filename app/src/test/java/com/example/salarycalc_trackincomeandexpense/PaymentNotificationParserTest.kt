package com.example.salarycalc_trackincomeandexpense

import org.junit.Assert.*
import org.junit.Test

class PaymentNotificationParserTest {
    private fun parse(text: String, app: Boolean = false, title: String = "") =
        PaymentNotificationParser.parse(text, app, title)

    @Test fun shortPaymentAppReceipts() {
        for (text in listOf("Received 45rs from Ravi", "Receive Rs.45 from Ravi", "You received ₹45 from Ravi", "You received a payment of ₹45 from Ravi")) {
            val payment = requireNotNull(parse(text, true))
            assertEquals(45.0, payment.amount, 0.001)
            assertEquals("credit", payment.type)
            assertEquals("Ravi", payment.name)
        }
    }

    @Test fun paymentDirectionCanBeInTitle() {
        val payment = requireNotNull(parse("₹45 from Anu", true, "Payment received"))
        assertEquals("credit", payment.type)
        assertEquals("Anu", payment.name)
    }

    @Test fun somebodySentYouMoneyIsIncome() {
        val payment = requireNotNull(parse("Ravi sent you ₹45", true, "Money received"))
        assertEquals("credit", payment.type)
        assertEquals("Ravi", payment.name)
    }

    @Test fun fullAmountsAndIndianGrouping() {
        for ((text, amount) in listOf(
            "A/c XX1234 debited INR 12345.67 to Ravi" to 12345.67,
            "A/c XX1234 credited Rs.1,23,456.78 from Anu" to 123456.78,
            "You received ₹1,000 from Anu" to 1000.0,
            "You received ₹45." to 45.0,
            "You received ₹0.50 from Anu" to 0.5
        )) assertEquals(amount, requireNotNull(parse(text, true)).amount, 0.001)
    }

    @Test fun ignoreBalanceBeforeTransactionAmount() {
        val payment = requireNotNull(parse("Avl bal Rs.9000.00. A/c XX1234 credited Rs.45 from Ravi"))
        assertEquals(45.0, payment.amount, 0.001)
    }

    @Test fun failedPendingRequestsAndOtpAreNotPayments() {
        for (text in listOf(
            "Payment of ₹45 to Ravi failed", "₹45 payment declined", "You sent ₹45. Status pending",
            "Payment of ₹45 is processing", "Ravi requested ₹45", "You received a request for ₹45",
            "OTP 123456 for payment of ₹45", "₹45 will be debited tomorrow",
            "₹45 not received", "Payment of ₹45 cancelled"
        )) assertNull(text, parse(text, true))
    }

    @Test fun untrustedCasualMessagesAreRejected() {
        assertNull(parse("Received 45rs from Ravi"))
        assertNull(parse("Your bank balance is ₹9000"))
        assertNull(parse("Pay ₹45 to win a reward", true))
    }

    @Test fun preserveShopNameAndAvoidBankAccountAsName() {
        assertEquals("Krishna Tea Stall", requireNotNull(parse("You paid ₹45 to Krishna Tea Stall via UPI", true)).name)
        assertEquals("Ravi General Store", requireNotNull(parse("You paid ₹100 to Ravi General Store on 22 Oct", true)).name)
        assertEquals("Payment received", requireNotNull(parse("Rs.45 credited to your account XX1234" )).name)
    }

    @Test fun referenceAndAccountAreExtracted() {
        val payment = requireNotNull(parse("A/c XX1234 credited Rs.45 from RAVI. UPI Ref No: 123456789012"))
        assertEquals("123456789012", payment.reference)
        assertEquals("1234", payment.accountLast4)
        assertEquals("Ravi", payment.name)
    }
}
