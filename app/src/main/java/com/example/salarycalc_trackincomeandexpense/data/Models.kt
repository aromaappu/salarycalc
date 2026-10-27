package com.example.salarycalc_trackincomeandexpense.data

data class Transaction(
    val id: Long,
    val type: String,              // "income" or "expense"
    val amount: Double,
    val name: String,              // income source or expense category
    val date: String,              // "yyyy-MM-dd"
    val receiptName: String?,
    val receiptPath: String?,      // file URI/name stored locally
    val createdAt: Long
)

data class IncomeSource(val name: String)

data class ExpenseCategory(val name: String)

data class AutoIncome(
    val id: Long,
    val source: String,
    val amount: Double,
    val dayOfMonth: Int
)

data class AutoExpense(
    val id: Long,
    val category: String,
    val amount: Double,
    val dayOfMonth: Int
)

data class Reminder(
    val id: Long,
    val name: String,
    val amount: Double,
    val date: String,              // "yyyy-MM-dd"
    val repeatMonthly: Boolean = false
)

data class Note(
    val id: Long,
    val text: String,
    val updatedAt: Long
)

data class BankStatementRecord(
    val id: Long,
    val fileName: String,
    val txCount: Int,
    val closingBalance: Double,
    val importedAt: Long
)
