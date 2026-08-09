package com.example.salarycalc_trackincomeandexpense

import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Manual SMS import used by the "standard" (no-permission) build.
// The user shares or pastes a bank SMS and we parse it into a transaction
// exactly like the auto-tracker does, but without needing READ_SMS.
object SmsImport {

    fun parse(text: String, sender: String? = null): SmsReceiver.TransactionData? {
        if (text.isBlank()) return null
        return SmsReceiver().parseTransactionPublic(text.trim(), sender)
    }

    fun addToDatabase(db: AppDatabase, tx: SmsReceiver.TransactionData): Boolean {
        val amount = tx.amount
        if (amount <= 0) return false

        val type = if (tx.transactionType == "credit") AppDatabase.TYPE_INCOME else AppDatabase.TYPE_EXPENSE
        val name = tx.merchant.ifEmpty { tx.category }
        val date = tx.date.ifBlank {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }

        if (type == AppDatabase.TYPE_INCOME) {
            if (!db.getIncomeSources().contains(name)) db.addIncomeSource(name)
        } else {
            if (!db.getExpenseCategories().contains(name)) db.addExpenseCategory(name)
        }

        db.addTransaction(type, amount, name, date)
        return true
    }
}
