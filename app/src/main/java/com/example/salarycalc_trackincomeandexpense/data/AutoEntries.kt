package com.example.salarycalc_trackincomeandexpense.data

import java.util.Calendar
import android.content.Context
import com.example.salarycalc_trackincomeandexpense.NotificationHelper

// Port of applyAutoEntries() from core.js — adds due Auto Income/Expense
// transactions once per month when the scheduled day matches today.
object AutoEntries {

    fun applyDue(db: AppDatabase, settings: SettingsStore, context: Context? = null) {
        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH)
        val today = now.get(Calendar.DAY_OF_MONTH)
        val monthKey = "$year-$month"
        val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)

        db.getAutoIncomes().forEach { entry ->
            val source = entry.source.ifBlank { "Income" }
            val amount = entry.amount
            val scheduledDay = entry.dayOfMonth.coerceIn(1, daysInMonth)
            if (source.isBlank() || amount <= 0) return@forEach
            if (today < scheduledDay) return@forEach

            val deleteKey = "${source}_$monthKey"
            if (settings.getDeletedAutoIncomeMonths().contains(deleteKey)) return@forEach
            if (isAddedForMonth(db, AppDatabase.TYPE_INCOME, source, monthKey)) return@forEach

            if (!db.getIncomeSources().contains(source)) db.addIncomeSource(source)
            val inserted = db.addTransaction(
                AppDatabase.TYPE_INCOME, amount, source, makeDateStr(year, month, scheduledDay)
            )
            if (inserted > 0) context?.let {
                NotificationHelper.showNotification(it, (System.nanoTime() and 0x7FFFFFFF).toInt(),
                    "Auto income added", "$source: ₹$amount")
            }
        }

        db.getAutoExpenses().forEach { entry ->
            val category = entry.category.ifBlank { "Expense" }
            val amount = entry.amount
            val scheduledDay = entry.dayOfMonth.coerceIn(1, daysInMonth)
            if (category.isBlank() || amount <= 0) return@forEach
            if (today < scheduledDay) return@forEach

            val deleteKey = "${category}_$monthKey"
            if (settings.getDeletedAutoExpenseMonths().contains(deleteKey)) return@forEach
            if (isAddedForMonth(db, AppDatabase.TYPE_EXPENSE, category, monthKey)) return@forEach

            if (!db.getExpenseCategories().contains(category)) db.addExpenseCategory(category)
            val inserted = db.addTransaction(
                AppDatabase.TYPE_EXPENSE, amount, category, makeDateStr(year, month, scheduledDay)
            )
            if (inserted > 0) context?.let {
                NotificationHelper.showNotification(it, (System.nanoTime() and 0x7FFFFFFF).toInt(),
                    "Auto expense added", "$category: ₹$amount")
            }
        }
    }

    private fun isAddedForMonth(db: AppDatabase, type: String, name: String, monthKey: String): Boolean {
        return db.getTransactions(type = type).any { t ->
            t.name == name && getMonthKey(t.date) == monthKey
        }
    }

    private fun getMonthKey(date: String): String {
        val parts = date.split("-")
        return if (parts.size >= 2) "${parts[0]}-${parts[1].toInt() - 1}" else ""
    }

    private fun makeDateStr(year: Int, monthIndex: Int, day: Int): String =
        "%04d-%02d-%02d".format(year, monthIndex + 1, day)
}
