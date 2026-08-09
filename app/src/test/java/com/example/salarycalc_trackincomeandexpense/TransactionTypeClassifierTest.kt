package com.example.salarycalc_trackincomeandexpense

import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionTypeClassifierTest {
    @Test
    fun withdrawMessagesStayDebit() {
        assertEquals("DEBIT", TransactionTypeClassifier.classify("Cash withdrawal from ATM"))
    }

    @Test
    fun depositMessagesStayCredit() {
        assertEquals("CREDIT", TransactionTypeClassifier.classify("Salary credited to account"))
    }

    @Test
    fun mixedMessagesPreferWithdrawal() {
        assertEquals("DEBIT", TransactionTypeClassifier.classify("Deposit reversal for withdrawal"))
    }
}
