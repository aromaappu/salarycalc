package com.example.salarycalc_trackincomeandexpense.ui.util

import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.Transaction
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private val INR_LOCALE = Locale.Builder().setLanguage("en").setRegion("IN").build()

fun formatINR(amount: Double): String =
    NumberFormat.getNumberInstance(INR_LOCALE).format(kotlin.math.abs(amount))

fun formatCurrency(symbol: String, amount: Double): String {
    return if (amount < 0) {
        "$symbol -${formatINR(-amount)}"
    } else {
        "$symbol ${formatINR(amount)}"
    }
}

fun getTodayStr(): String =
    SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(Date())

fun getYesterdayStr(): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_MONTH, -1)
    return SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(cal.time)
}

fun getTodayDateParts(): Triple<Int, Int, Int> {
    val cal = Calendar.getInstance()
    return Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
}

fun isCurrentMonth(dateStr: String): Boolean {
    val parts = dateStr.split("-")
    if (parts.size < 3) return false
    val (year, month, _) = getTodayDateParts()
    return parts[1].toInt() - 1 == month && parts[0].toInt() == year
}

fun isSameYearMonth(dateStr: String, year: Int, month: Int): Boolean {
    val parts = dateStr.split("-")
    if (parts.size < 2) return false
    return parts[0].toInt() == year && parts[1].toInt() - 1 == month
}

fun getMonthKey(dateStr: String): String {
    val parts = dateStr.split("-")
    return if (parts.size >= 2) "${parts[0]}-${parts[1].toInt() - 1}" else ""
}

fun monthNameFor(monthKey: String): String {
    if (monthKey.isBlank()) return "No Data"
    val parts = monthKey.split("-")
    return if (parts.size == 2) "${MONTHS[parts[1].toInt()]} ${parts[0]}" else monthKey
}

// ==================== TOTALS (logic ported from core.js) ====================

data class MonthTotals(val income: Double, val expense: Double)

private data class DateParts(val year: Int, val month: Int, val day: Int)

private fun parseDateParts(dateStr: String): DateParts? {
    val trimmed = dateStr.trim()
    val match = Regex("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})$").matchEntire(trimmed) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    return DateParts(year, month, day)
}

private fun dateMatches(dateStr: String, year: Int, month: Int, day: Int? = null): Boolean {
    val parts = parseDateParts(dateStr) ?: return false
    return parts.year == year && parts.month == month && (day == null || parts.day == day)
}

fun monthTotalsForTransactions(transactions: List<Transaction>, referenceDate: String): MonthTotals {
    val parts = parseDateParts(referenceDate) ?: return MonthTotals(0.0, 0.0)
    var inc = 0.0
    var exp = 0.0
    transactions.forEach { t ->
        if (dateMatches(t.date, parts.year, parts.month)) {
            if (t.type == AppDatabase.TYPE_INCOME) inc += t.amount else exp += t.amount
        }
    }
    return MonthTotals(inc, exp)
}

fun currentMonthTotals(db: AppDatabase): MonthTotals =
    monthTotalsForTransactions(db.getAllTransactions(), getTodayStr())

fun lifetimeBalance(db: AppDatabase): Double {
    var inc = 0.0
    var exp = 0.0
    db.getAllTransactions().forEach { t ->
        if (t.type == AppDatabase.TYPE_INCOME) inc += t.amount else exp += t.amount
    }
    return inc - exp
}

/**
 * Chooses the net balance shown on the dashboard/statement.
 * When the user keeps "use statement closing balance" ticked, the latest
 * imported statement's closing balance wins (falling back to transactions).
 * When unticked, only the transactions added in the app are used.
 */
fun resolveNetBalance(
    statementClosingBalance: Double?,
    transactionBalance: Double,
    useStatementClosingBalance: Boolean
): Double =
    if (useStatementClosingBalance) statementClosingBalance ?: transactionBalance
    else transactionBalance

fun resolveNetBalance(db: AppDatabase, settings: com.example.salarycalc_trackincomeandexpense.data.SettingsStore): Double =
    resolveNetBalance(
        statementClosingBalance = db.getLatestClosingBalance(),
        transactionBalance = lifetimeBalance(db),
        useStatementClosingBalance = settings.isStatementNetBalanceEnabled()
    )

fun todayExpenseForTransactions(transactions: List<Transaction>, referenceDate: String): Double {
    val parts = parseDateParts(referenceDate) ?: return 0.0
    return transactions
        .filter { it.type == AppDatabase.TYPE_EXPENSE && dateMatches(it.date, parts.year, parts.month, parts.day) }
        .sumOf { it.amount }
}

fun getTodayExpense(db: AppDatabase): Double =
    todayExpenseForTransactions(db.getAllTransactions(), getTodayStr())

fun getRecentTransactions(db: AppDatabase, limit: Int = 7): List<Transaction> =
    db.getAllTransactions()
        .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt })
        .take(limit)

fun getTransactionsYear(db: AppDatabase, year: Int): List<Transaction> =
    db.getAllTransactions().filter { it.date.startsWith("$year-") }

fun transactionDisplayName(t: Transaction): String {
    if (t.name.isNotBlank()) return t.name
    return if (t.type == AppDatabase.TYPE_INCOME) "Income" else "Expense"
}
