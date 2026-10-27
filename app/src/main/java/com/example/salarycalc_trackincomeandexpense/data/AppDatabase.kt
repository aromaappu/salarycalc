package com.example.salarycalc_trackincomeandexpense.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

class AppDatabase(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "salarycalc.db"
        private const val DB_VERSION = 7

        const val TYPE_INCOME = "income"
        const val TYPE_EXPENSE = "expense"

        val DEFAULT_SOURCES = listOf(
            "Salary", "M Incentive", "6M Incentive", "Night Shift", "OT", "Bonus"
        )

        val DEFAULT_CATEGORIES = listOf(
            "Food", "Travel", "Shopping", "Rent", "Bills",
            "Health", "Entertainment", "Education", "Other"
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                name TEXT NOT NULL,
                date TEXT NOT NULL,
                receipt_name TEXT,
                receipt_path TEXT,
                created_at INTEGER NOT NULL,
                tx_hash TEXT,
                statement_id INTEGER
            )"""
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_tx_hash ON transactions(tx_hash) WHERE tx_hash IS NOT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_statement ON transactions(statement_id)")
        db.execSQL(
            """CREATE TABLE income_sources (
                name TEXT PRIMARY KEY
            )"""
        )
        db.execSQL(
            """CREATE TABLE expense_categories (
                name TEXT PRIMARY KEY
            )"""
        )
        db.execSQL(
            """CREATE TABLE auto_income (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                amount REAL NOT NULL,
                day INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE auto_expense (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category TEXT NOT NULL,
                amount REAL NOT NULL,
                day INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                amount REAL NOT NULL,
                date TEXT NOT NULL,
                repeat_monthly INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE bank_statements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_name TEXT NOT NULL,
                tx_count INTEGER NOT NULL,
                closing_balance REAL NOT NULL DEFAULT 0,
                imported_at INTEGER NOT NULL
            )"""
        )
        createLoanTables(db)
        createFdTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS bank_statements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_name TEXT NOT NULL,
                    tx_count INTEGER NOT NULL,
                    closing_balance REAL NOT NULL DEFAULT 0,
                    imported_at INTEGER NOT NULL
                )"""
            )
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN tx_hash TEXT")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_tx_hash ON transactions(tx_hash) WHERE tx_hash IS NOT NULL")
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE bank_statements ADD COLUMN closing_balance REAL NOT NULL DEFAULT 0")
            } catch (e: Exception) {
                // Column may already exist on re-run.
            }
        }
        if (oldVersion < 5) {
            createLoanTables(db)
        }
        if (oldVersion < 6) {
            createFdTables(db)
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN statement_id INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_statement ON transactions(statement_id)")
            db.execSQL("ALTER TABLE reminders ADD COLUMN repeat_monthly INTEGER NOT NULL DEFAULT 0")
        }
    }

    private fun createLoanTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS loans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                lender TEXT NOT NULL,
                original_amount REAL NOT NULL,
                interest_rate REAL NOT NULL,
                interest_type TEXT NOT NULL,
                start_date TEXT NOT NULL,
                first_due_date TEXT NOT NULL,
                payment_day INTEGER NOT NULL,
                monthly_payment REAL NOT NULL,
                duration_months INTEGER NOT NULL,
                status TEXT NOT NULL,
                expected_completion_date TEXT,
                actual_completion_date TEXT,
                total_paid REAL NOT NULL DEFAULT 0,
                total_interest_paid REAL NOT NULL DEFAULT 0,
                remaining_balance REAL NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS loan_schedules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                loan_id INTEGER NOT NULL,
                payment_number INTEGER NOT NULL,
                due_date TEXT NOT NULL,
                expected_payment REAL NOT NULL,
                principal REAL NOT NULL,
                interest REAL NOT NULL,
                paid_amount REAL NOT NULL DEFAULT 0,
                status TEXT NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_loan_schedules_loan ON loan_schedules(loan_id)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS loan_payments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                loan_id INTEGER NOT NULL,
                schedule_id INTEGER NOT NULL,
                payment_date TEXT NOT NULL,
                expected_amount REAL NOT NULL,
                paid_amount REAL NOT NULL,
                principal_amount REAL NOT NULL,
                interest_amount REAL NOT NULL,
                extra_payment REAL NOT NULL DEFAULT 0,
                payment_method TEXT NOT NULL DEFAULT '',
                reference TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_loan_payments_loan ON loan_payments(loan_id)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS loan_notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                loan_id INTEGER NOT NULL,
                payment_number INTEGER NOT NULL,
                due_date TEXT NOT NULL,
                notif_type TEXT NOT NULL,
                scheduled_at INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_loan_notifications_loan ON loan_notifications(loan_id)")
    }

    private fun createFdTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS fds (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                principal REAL NOT NULL,
                interest_rate REAL NOT NULL,
                tenure_years REAL NOT NULL,
                start_date TEXT NOT NULL,
                status TEXT NOT NULL,
                completed_gain REAL NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )"""
        )
    }

    fun seedDefaults() {
        val db = writableDatabase
        DEFAULT_SOURCES.forEach { name ->
            db.insertWithOnConflict(
                "income_sources", null,
                ContentValues().apply { put("name", name) },
                SQLiteDatabase.CONFLICT_IGNORE
            )
        }
        DEFAULT_CATEGORIES.forEach { name ->
            db.insertWithOnConflict(
                "expense_categories", null,
                ContentValues().apply { put("name", name) },
                SQLiteDatabase.CONFLICT_IGNORE
            )
        }
    }

    // ============================ TRANSACTIONS ============================

    fun addTransaction(type: String, amount: Double, name: String, date: String,
                       receiptName: String? = null, receiptPath: String? = null): Long {
        val values = ContentValues().apply {
            put("type", type)
            put("amount", amount)
            put("name", name)
            put("date", date)
            put("receipt_name", receiptName)
            put("receipt_path", receiptPath)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("transactions", null, values)
    }

    /** Insert with SHA-256 hash for bank-import dedup. Returns -1 on hash collision (duplicate). */
    fun addTransactionWithHash(type: String, amount: Double, name: String, date: String, hash: String, statementId: Long? = null): Long {
        val values = ContentValues().apply {
            put("type", type)
            put("amount", amount)
            put("name", name)
            put("date", date)
            put("tx_hash", hash)
            if (statementId != null) put("statement_id", statementId)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict("transactions", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Returns all non-null tx_hash values — used by DuplicateChecker. */
    fun getAllTxHashes(): Set<String> {
        val cursor = readableDatabase.rawQuery(
            "SELECT tx_hash FROM transactions WHERE tx_hash IS NOT NULL", null
        )
        val set = HashSet<String>()
        cursor.use { while (it.moveToNext()) set.add(it.getString(0)) }
        return set
    }

    fun updateTransaction(id: Long, type: String, amount: Double, name: String, date: String,
                          receiptName: String?, receiptPath: String?) {
        val values = ContentValues().apply {
            put("type", type)
            put("amount", amount)
            put("name", name)
            put("date", date)
            put("receipt_name", receiptName)
            put("receipt_path", receiptPath)
        }
        writableDatabase.update("transactions", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteTransaction(id: Long) {
        writableDatabase.delete("transactions", "id = ?", arrayOf(id.toString()))
    }

    fun getTransaction(id: Long): Transaction? {
        val cursor = readableDatabase.query(
            "transactions", null, "id = ?", arrayOf(id.toString()), null, null, null
        )
        return cursor.use { if (it.moveToFirst()) readTransaction(it) else null }
    }

    fun getAllTransactions(orderAsc: Boolean = false): List<Transaction> {
        val order = if (orderAsc) "date ASC, created_at ASC" else "date DESC, created_at DESC"
        return queryTransactions(null, null, order)
    }

    fun getTransactions(type: String, year: String? = null, month: String? = null,
                        name: String? = null): List<Transaction> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        conditions.add("type = ?")
        args.add(type)
        if (year != null) {
            conditions.add("date LIKE ?")
            args.add("$year-%")
        }
        if (month != null && year != null) {
            val mm = month.toInt() + 1
            conditions.add("date LIKE ?")
            args.add("$year-%${"%02d".format(mm)}-%")
        }
        if (name != null) {
            conditions.add("name = ?")
            args.add(name)
        }
        val where = conditions.joinToString(" AND ")
        return queryTransactions(where, args.toTypedArray(), "date DESC, created_at DESC")
    }

    fun getTransactionsBetween(startDate: String, endDate: String): List<Transaction> {
        val where = "date >= ? AND date <= ?"
        return queryTransactions(where, arrayOf(startDate, endDate), "date ASC, created_at ASC")
    }

    fun sumByType(type: String, year: String? = null, month: String? = null): Double {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        conditions.add("type = ?")
        args.add(type)
        if (year != null) {
            conditions.add("date LIKE ?")
            args.add("$year-%")
        }
        if (month != null && year != null) {
            val mm = month.toInt() + 1
            conditions.add("date LIKE ?")
            args.add("$year-%${"%02d".format(mm)}-%")
        }
        val cursor = readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE ${conditions.joinToString(" AND ")}",
            args.toTypedArray()
        )
        return cursor.use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun sumToday(date: String): Double {
        val cursor = readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type = ? AND date = ?",
            arrayOf(TYPE_EXPENSE, date)
        )
        return cursor.use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getRecentTransactions(limit: Int = 10): List<Transaction> {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM transactions ORDER BY date DESC, created_at DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        return cursor.use { parseTransactions(it) }
    }

    fun getLastIncomeDate(): String? {
        val cursor = readableDatabase.rawQuery(
            "SELECT MAX(date) FROM transactions WHERE type = ?",
            arrayOf(TYPE_INCOME)
        )
        return cursor.use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }
    }

    fun getYears(type: String? = null): List<String> {
        val where = if (type != null) "WHERE type = ?" else ""
        val args = if (type != null) arrayOf(type) else null
        val cursor = readableDatabase.rawQuery(
            "SELECT DISTINCT substr(date,1,4) FROM transactions $where ORDER BY substr(date,1,4) DESC",
            args
        )
        val years = mutableListOf<String>()
        cursor.use { while (it.moveToNext()) years.add(it.getString(0)) }
        return years
    }

    private fun queryTransactions(where: String?, args: Array<String>?, order: String): List<Transaction> {
        val cursor = readableDatabase.query(
            "transactions", null, where, args, null, null, order
        )
        return cursor.use { parseTransactions(it) }
    }

    private fun parseTransactions(cursor: android.database.Cursor): List<Transaction> {
        val list = mutableListOf<Transaction>()
        while (cursor.moveToNext()) {
            list.add(readTransaction(cursor))
        }
        return list
    }

    private fun readTransaction(c: android.database.Cursor): Transaction {
        return Transaction(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            type = c.getString(c.getColumnIndexOrThrow("type")),
            amount = c.getDouble(c.getColumnIndexOrThrow("amount")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            date = c.getString(c.getColumnIndexOrThrow("date")),
            receiptName = c.getString(c.getColumnIndexOrThrow("receipt_name")),
            receiptPath = c.getString(c.getColumnIndexOrThrow("receipt_path")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
        )
    }

    // ============================ INCOME SOURCES ============================

    fun getIncomeSources(): List<String> {
        return getNames("income_sources")
    }

    fun addIncomeSource(name: String) {
        insertName("income_sources", name)
    }

    fun removeIncomeSource(name: String) {
        writableDatabase.delete("income_sources", "name = ?", arrayOf(name))
    }

    fun clearIncomeSources() {
        writableDatabase.delete("income_sources", null, null)
    }

    // ============================ EXPENSE CATEGORIES ============================

    fun getExpenseCategories(): List<String> {
        return getNames("expense_categories")
    }

    fun addExpenseCategory(name: String) {
        insertName("expense_categories", name)
    }

    fun removeExpenseCategory(name: String) {
        writableDatabase.delete("expense_categories", "name = ?", arrayOf(name))
    }

    fun clearExpenseCategories() {
        writableDatabase.delete("expense_categories", null, null)
    }

    private fun getNames(table: String): List<String> {
        val cursor = readableDatabase.query(table, arrayOf("name"), null, null, null, null, "name ASC")
        val list = mutableListOf<String>()
        cursor.use { while (it.moveToNext()) list.add(it.getString(0)) }
        return list
    }

    private fun insertName(table: String, name: String) {
        val values = ContentValues().apply { put("name", name) }
        writableDatabase.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    // ============================ AUTO INCOME ============================

    fun getAutoIncomes(): List<AutoIncome> {
        val cursor = readableDatabase.query("auto_income", null, null, null, null, null, "day ASC")
        val list = mutableListOf<AutoIncome>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    AutoIncome(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        source = it.getString(it.getColumnIndexOrThrow("source")),
                        amount = it.getDouble(it.getColumnIndexOrThrow("amount")),
                        dayOfMonth = it.getInt(it.getColumnIndexOrThrow("day"))
                    )
                )
            }
        }
        return list
    }

    fun addAutoIncome(source: String, amount: Double, day: Int) {
        val values = ContentValues().apply {
            put("source", source)
            put("amount", amount)
            put("day", day)
        }
        writableDatabase.insert("auto_income", null, values)
    }

    fun deleteAutoIncome(id: Long) {
        writableDatabase.delete("auto_income", "id = ?", arrayOf(id.toString()))
    }

    // ============================ AUTO EXPENSE ============================

    fun getAutoExpenses(): List<AutoExpense> {
        val cursor = readableDatabase.query("auto_expense", null, null, null, null, null, "day ASC")
        val list = mutableListOf<AutoExpense>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    AutoExpense(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        category = it.getString(it.getColumnIndexOrThrow("category")),
                        amount = it.getDouble(it.getColumnIndexOrThrow("amount")),
                        dayOfMonth = it.getInt(it.getColumnIndexOrThrow("day"))
                    )
                )
            }
        }
        return list
    }

    fun addAutoExpense(category: String, amount: Double, day: Int) {
        val values = ContentValues().apply {
            put("category", category)
            put("amount", amount)
            put("day", day)
        }
        writableDatabase.insert("auto_expense", null, values)
    }

    fun deleteAutoExpense(id: Long) {
        writableDatabase.delete("auto_expense", "id = ?", arrayOf(id.toString()))
    }

    // ============================ REMINDERS ============================

    fun getReminders(): List<Reminder> {
        val cursor = readableDatabase.query("reminders", null, null, null, null, null, "date ASC")
        val list = mutableListOf<Reminder>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Reminder(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        amount = it.getDouble(it.getColumnIndexOrThrow("amount")),
                        date = it.getString(it.getColumnIndexOrThrow("date")),
                        repeatMonthly = it.getInt(it.getColumnIndexOrThrow("repeat_monthly")) != 0
                    )
                )
            }
        }
        return list
    }

    fun addReminder(name: String, amount: Double, date: String, repeatMonthly: Boolean = false): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("amount", amount)
            put("date", date)
            put("repeat_monthly", if (repeatMonthly) 1 else 0)
        }
        return writableDatabase.insert("reminders", null, values)
    }

    fun getReminder(id: Long): Reminder? = getReminders().firstOrNull { it.id == id }

    fun deleteReminder(id: Long) {
        writableDatabase.delete("reminders", "id = ?", arrayOf(id.toString()))
    }

    // ============================ NOTES ============================

    fun getNotes(): String {
        val cursor = readableDatabase.query("notes", arrayOf("text"), null, null, null, null, "updated_at DESC")
        return cursor.use {
            val sb = StringBuilder()
            while (it.moveToNext()) {
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append(it.getString(0))
            }
            sb.toString()
        }
    }

    fun saveNotes(text: String) {
        writableDatabase.delete("notes", null, null)
        val values = ContentValues().apply {
            put("text", text)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insert("notes", null, values)
    }

    // ============================ BANK STATEMENTS ============================

    fun addBankStatement(fileName: String, txCount: Int, closingBalance: Double = 0.0): Long {
        val values = ContentValues().apply {
            put("file_name", fileName)
            put("tx_count", txCount)
            put("closing_balance", closingBalance)
            put("imported_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("bank_statements", null, values)
    }

    fun getBankStatements(): List<BankStatementRecord> {
        val cursor = readableDatabase.query(
            "bank_statements", null, null, null, null, null, "imported_at DESC"
        )
        val list = mutableListOf<BankStatementRecord>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    BankStatementRecord(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        fileName = it.getString(it.getColumnIndexOrThrow("file_name")),
                        txCount = it.getInt(it.getColumnIndexOrThrow("tx_count")),
                        closingBalance = it.getDouble(it.getColumnIndexOrThrow("closing_balance")),
                        importedAt = it.getLong(it.getColumnIndexOrThrow("imported_at"))
                    )
                )
            }
        }
        return list
    }

    /** Closing balance of the most recent imported statement, or null when none. */
    fun getLatestClosingBalance(): Double? {
        val cursor = readableDatabase.rawQuery(
            "SELECT closing_balance FROM bank_statements WHERE closing_balance > 0 ORDER BY imported_at DESC, id DESC LIMIT 1",
            null
        )
        return cursor.use {
            if (it.moveToFirst()) it.getDouble(0) else null
        }
    }

    /** Transaction counts include only rows reliably linked to this statement. */
    fun getLinkedStatementTransactionCount(id: Long): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM transactions WHERE statement_id = ?",
            arrayOf(id.toString())
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    /** Never guess which older, unlinked transactions belonged to this file. */
    fun deleteBankStatementAndLinkedTransactions(id: Long): Int {
        val database = writableDatabase
        database.beginTransaction()
        try {
            val deleted = database.delete("transactions", "statement_id = ?", arrayOf(id.toString()))
            database.delete("bank_statements", "id = ?", arrayOf(id.toString()))
            database.setTransactionSuccessful()
            return deleted
        } finally {
            database.endTransaction()
        }
    }

    fun deleteBankStatement(id: Long) {
        writableDatabase.delete("bank_statements", "id = ?", arrayOf(id.toString()))
    }

    fun updateBankStatementCount(id: Long, count: Int) {
        writableDatabase.update("bank_statements", ContentValues().apply {
            put("tx_count", count)
        }, "id = ?", arrayOf(id.toString()))
    }

    fun getBankStatementByFile(fileName: String): BankStatementRecord? {
        val cursor = readableDatabase.query(
            "bank_statements", null, "file_name = ?", arrayOf(fileName), null, null, null
        )
        return cursor.use { if (it.moveToFirst()) {
            BankStatementRecord(
                id = it.getLong(it.getColumnIndexOrThrow("id")),
                fileName = it.getString(it.getColumnIndexOrThrow("file_name")),
                txCount = it.getInt(it.getColumnIndexOrThrow("tx_count")),
                closingBalance = it.getDouble(it.getColumnIndexOrThrow("closing_balance")),
                importedAt = it.getLong(it.getColumnIndexOrThrow("imported_at"))
            )
        } else null }
    }

    // ============================ LOANS ============================

    fun addLoan(
        name: String,
        lender: String,
        originalAmount: Double,
        interestRate: Double,
        interestType: InterestType,
        startDate: String,
        firstDueDate: String,
        paymentDay: Int,
        monthlyPayment: Double,
        durationMonths: Int,
        remainingBalance: Double,
        expectedCompletionDate: String?
    ): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("name", name)
            put("lender", lender)
            put("original_amount", originalAmount)
            put("interest_rate", interestRate)
            put("interest_type", interestType.name)
            put("start_date", startDate)
            put("first_due_date", firstDueDate)
            put("payment_day", paymentDay)
            put("monthly_payment", monthlyPayment)
            put("duration_months", durationMonths)
            put("status", LoanStatus.ACTIVE.name)
            put("expected_completion_date", expectedCompletionDate)
            put("remaining_balance", remainingBalance)
            put("total_paid", 0.0)
            put("total_interest_paid", 0.0)
            put("created_at", now)
            put("updated_at", now)
        }
        return writableDatabase.insert("loans", null, values)
    }

    /** Full edit for a loan with no payments yet: resets the principal terms. */
    fun updateLoanDetails(
        id: Long,
        name: String,
        lender: String,
        originalAmount: Double,
        interestRate: Double,
        interestType: InterestType,
        startDate: String,
        firstDueDate: String,
        paymentDay: Int,
        monthlyPayment: Double,
        durationMonths: Int
    ) {
        val values = ContentValues().apply {
            put("name", name)
            put("lender", lender)
            put("original_amount", originalAmount)
            put("interest_rate", interestRate)
            put("interest_type", interestType.name)
            put("start_date", startDate)
            put("first_due_date", firstDueDate)
            put("payment_day", paymentDay)
            put("monthly_payment", monthlyPayment)
            put("duration_months", durationMonths)
            put("remaining_balance", originalAmount)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("loans", values, "id = ?", arrayOf(id.toString()))
    }

    /** Restricted edit for a loan that already has payments: name/lender only. */
    fun updateLoanNameLender(id: Long, name: String, lender: String) {
        val values = ContentValues().apply {
            put("name", name)
            put("lender", lender)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("loans", values, "id = ?", arrayOf(id.toString()))
    }

    fun updateLoanTotals(
        id: Long,
        totalPaid: Double,
        totalInterestPaid: Double,
        remainingBalance: Double
    ) {
        val values = ContentValues().apply {
            put("total_paid", totalPaid)
            put("total_interest_paid", totalInterestPaid)
            put("remaining_balance", remainingBalance)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("loans", values, "id = ?", arrayOf(id.toString()))
    }

    fun markLoanCompleted(id: Long, actualCompletionDate: String) {
        val values = ContentValues().apply {
            put("status", LoanStatus.COMPLETED.name)
            put("actual_completion_date", actualCompletionDate)
            put("remaining_balance", 0.0)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("loans", values, "id = ?", arrayOf(id.toString()))
    }

    fun reopenLoan(id: Long) {
        val values = ContentValues().apply {
            put("status", LoanStatus.ACTIVE.name)
            put("actual_completion_date", null as String?)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("loans", values, "id = ?", arrayOf(id.toString()))
    }

    fun setLoanExpectedCompletion(id: Long, date: String) {
        val values = ContentValues().apply {
            put("expected_completion_date", date)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("loans", values, "id = ?", arrayOf(id.toString()))
    }

    fun getLoan(id: Long): Loan? {
        val cursor = readableDatabase.query(
            "loans", null, "id = ?", arrayOf(id.toString()), null, null, null
        )
        return cursor.use { if (it.moveToFirst()) readLoan(it) else null }
    }

    fun getLoans(status: LoanStatus): List<Loan> {
        val cursor = readableDatabase.query(
            "loans", null, "status = ?", arrayOf(status.name), null, null, "created_at DESC"
        )
        return cursor.use { readLoans(it) }
    }

    fun getAllLoans(): List<Loan> {
        val cursor = readableDatabase.query("loans", null, null, null, null, null, "created_at DESC")
        return cursor.use { readLoans(it) }
    }

    fun deleteLoan(id: Long) {
        writableDatabase.delete("loans", "id = ?", arrayOf(id.toString()))
        writableDatabase.delete("loan_schedules", "loan_id = ?", arrayOf(id.toString()))
        writableDatabase.delete("loan_payments", "loan_id = ?", arrayOf(id.toString()))
        writableDatabase.delete("loan_notifications", "loan_id = ?", arrayOf(id.toString()))
    }

    private fun readLoans(cursor: android.database.Cursor): List<Loan> {
        val list = mutableListOf<Loan>()
        while (cursor.moveToNext()) list.add(readLoan(cursor))
        return list
    }

    private fun readLoan(c: android.database.Cursor): Loan {
        return Loan(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            lender = c.getString(c.getColumnIndexOrThrow("lender")),
            originalAmount = c.getDouble(c.getColumnIndexOrThrow("original_amount")),
            interestRate = c.getDouble(c.getColumnIndexOrThrow("interest_rate")),
            interestType = runCatching { InterestType.valueOf(c.getString(c.getColumnIndexOrThrow("interest_type"))) }
                .getOrDefault(InterestType.FLAT),
            startDate = c.getString(c.getColumnIndexOrThrow("start_date")),
            firstDueDate = c.getString(c.getColumnIndexOrThrow("first_due_date")),
            paymentDay = c.getInt(c.getColumnIndexOrThrow("payment_day")),
            monthlyPayment = c.getDouble(c.getColumnIndexOrThrow("monthly_payment")),
            durationMonths = c.getInt(c.getColumnIndexOrThrow("duration_months")),
            status = runCatching { LoanStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status"))) }
                .getOrDefault(LoanStatus.ACTIVE),
            expectedCompletionDate = c.getString(c.getColumnIndexOrThrow("expected_completion_date")),
            actualCompletionDate = c.getString(c.getColumnIndexOrThrow("actual_completion_date")),
            totalPaid = c.getDouble(c.getColumnIndexOrThrow("total_paid")),
            totalInterestPaid = c.getDouble(c.getColumnIndexOrThrow("total_interest_paid")),
            remainingBalance = c.getDouble(c.getColumnIndexOrThrow("remaining_balance")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
        )
    }

    // ============================ LOAN SCHEDULES ============================

    fun clearLoanSchedules(loanId: Long) {
        writableDatabase.delete("loan_schedules", "loan_id = ?", arrayOf(loanId.toString()))
    }

    fun addLoanSchedule(
        loanId: Long,
        paymentNumber: Int,
        dueDate: String,
        expectedPayment: Double,
        principal: Double,
        interest: Double,
        status: ScheduleStatus
    ): Long {
        val values = ContentValues().apply {
            put("loan_id", loanId)
            put("payment_number", paymentNumber)
            put("due_date", dueDate)
            put("expected_payment", expectedPayment)
            put("principal", principal)
            put("interest", interest)
            put("paid_amount", 0.0)
            put("status", status.name)
        }
        return writableDatabase.insert("loan_schedules", null, values)
    }

    fun getLoanSchedules(loanId: Long): List<LoanSchedule> {
        val cursor = readableDatabase.query(
            "loan_schedules", null, "loan_id = ?", arrayOf(loanId.toString()), null, null, "payment_number ASC"
        )
        val list = mutableListOf<LoanSchedule>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    LoanSchedule(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        loanId = it.getLong(it.getColumnIndexOrThrow("loan_id")),
                        paymentNumber = it.getInt(it.getColumnIndexOrThrow("payment_number")),
                        dueDate = it.getString(it.getColumnIndexOrThrow("due_date")),
                        expectedPayment = it.getDouble(it.getColumnIndexOrThrow("expected_payment")),
                        principal = it.getDouble(it.getColumnIndexOrThrow("principal")),
                        interest = it.getDouble(it.getColumnIndexOrThrow("interest")),
                        paidAmount = it.getDouble(it.getColumnIndexOrThrow("paid_amount")),
                        status = runCatching { ScheduleStatus.valueOf(it.getString(it.getColumnIndexOrThrow("status"))) }
                            .getOrDefault(ScheduleStatus.UPCOMING)
                    )
                )
            }
        }
        return list
    }

    fun updateLoanSchedule(scheduleId: Long, paidAmount: Double, status: ScheduleStatus) {
        val values = ContentValues().apply {
            put("paid_amount", paidAmount)
            put("status", status.name)
        }
        writableDatabase.update("loan_schedules", values, "id = ?", arrayOf(scheduleId.toString()))
    }

    fun deleteLoanSchedule(id: Long) {
        writableDatabase.delete("loan_schedules", "id = ?", arrayOf(id.toString()))
    }

    fun deleteLoanSchedulesFrom(loanId: Long, paymentNumber: Int) {
        writableDatabase.delete(
            "loan_schedules",
            "loan_id = ? AND payment_number >= ?",
            arrayOf(loanId.toString(), paymentNumber.toString())
        )
    }

    // ============================ LOAN PAYMENTS ============================

    fun addLoanPayment(
        loanId: Long,
        scheduleId: Long,
        paymentDate: String,
        expectedAmount: Double,
        paidAmount: Double,
        principalAmount: Double,
        interestAmount: Double,
        extraPayment: Double,
        paymentMethod: String,
        reference: String,
        notes: String
    ): Long {
        val values = ContentValues().apply {
            put("loan_id", loanId)
            put("schedule_id", scheduleId)
            put("payment_date", paymentDate)
            put("expected_amount", expectedAmount)
            put("paid_amount", paidAmount)
            put("principal_amount", principalAmount)
            put("interest_amount", interestAmount)
            put("extra_payment", extraPayment)
            put("payment_method", paymentMethod)
            put("reference", reference)
            put("notes", notes)
        }
        return writableDatabase.insert("loan_payments", null, values)
    }

    fun getLoanPayments(loanId: Long): List<LoanPayment> {
        val cursor = readableDatabase.query(
            "loan_payments", null, "loan_id = ?", arrayOf(loanId.toString()), null, null, "payment_date ASC, id ASC"
        )
        val list = mutableListOf<LoanPayment>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    LoanPayment(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        loanId = it.getLong(it.getColumnIndexOrThrow("loan_id")),
                        scheduleId = it.getLong(it.getColumnIndexOrThrow("schedule_id")),
                        paymentDate = it.getString(it.getColumnIndexOrThrow("payment_date")),
                        expectedAmount = it.getDouble(it.getColumnIndexOrThrow("expected_amount")),
                        paidAmount = it.getDouble(it.getColumnIndexOrThrow("paid_amount")),
                        principalAmount = it.getDouble(it.getColumnIndexOrThrow("principal_amount")),
                        interestAmount = it.getDouble(it.getColumnIndexOrThrow("interest_amount")),
                        extraPayment = it.getDouble(it.getColumnIndexOrThrow("extra_payment")),
                        paymentMethod = it.getString(it.getColumnIndexOrThrow("payment_method")),
                        reference = it.getString(it.getColumnIndexOrThrow("reference")),
                        notes = it.getString(it.getColumnIndexOrThrow("notes"))
                    )
                )
            }
        }
        return list
    }

    // ============================ LOAN NOTIFICATIONS ============================

    fun addLoanNotification(loanId: Long, paymentNumber: Int, dueDate: String, notifType: String) {
        val values = ContentValues().apply {
            put("loan_id", loanId)
            put("payment_number", paymentNumber)
            put("due_date", dueDate)
            put("notif_type", notifType)
            put("scheduled_at", System.currentTimeMillis())
        }
        writableDatabase.insert("loan_notifications", null, values)
    }

    fun clearLoanNotifications(loanId: Long) {
        writableDatabase.delete("loan_notifications", "loan_id = ?", arrayOf(loanId.toString()))
    }

    fun getLoanNotificationCount(loanId: Long): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM loan_notifications WHERE loan_id = ?", arrayOf(loanId.toString())
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    // ============================ FIXED DEPOSITS ============================

    fun addFd(
        name: String,
        principal: Double,
        interestRate: Double,
        tenureYears: Double,
        startDate: String
    ): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("name", name)
            put("principal", principal)
            put("interest_rate", interestRate)
            put("tenure_years", tenureYears)
            put("start_date", startDate)
            put("status", FdStatus.ACTIVE.name)
            put("completed_gain", 0.0)
            put("created_at", now)
            put("updated_at", now)
        }
        return writableDatabase.insert("fds", null, values)
    }

    fun updateFdDetails(
        id: Long,
        name: String,
        principal: Double,
        interestRate: Double,
        tenureYears: Double,
        startDate: String
    ) {
        val values = ContentValues().apply {
            put("name", name)
            put("principal", principal)
            put("interest_rate", interestRate)
            put("tenure_years", tenureYears)
            put("start_date", startDate)
            put("status", FdStatus.ACTIVE.name)
            put("completed_gain", 0.0)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("fds", values, "id = ?", arrayOf(id.toString()))
    }

    fun markFdCompleted(id: Long, completedGain: Double) {
        val values = ContentValues().apply {
            put("status", FdStatus.COMPLETED.name)
            put("completed_gain", completedGain)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("fds", values, "id = ?", arrayOf(id.toString()))
    }

    fun getFd(id: Long): Fd? {
        val cursor = readableDatabase.query(
            "fds", null, "id = ?", arrayOf(id.toString()), null, null, null
        )
        return cursor.use { if (it.moveToFirst()) readFd(it) else null }
    }

    fun getFds(status: FdStatus): List<Fd> {
        val cursor = readableDatabase.query(
            "fds", null, "status = ?", arrayOf(status.name), null, null, "created_at DESC"
        )
        return cursor.use { readFds(it) }
    }

    fun getAllFds(): List<Fd> {
        val cursor = readableDatabase.query("fds", null, null, null, null, null, "created_at DESC")
        return cursor.use { readFds(it) }
    }

    fun deleteFd(id: Long) {
        writableDatabase.delete("fds", "id = ?", arrayOf(id.toString()))
    }

    private fun readFds(cursor: android.database.Cursor): List<Fd> {
        val list = mutableListOf<Fd>()
        while (cursor.moveToNext()) list.add(readFd(cursor))
        return list
    }

    private fun readFd(c: android.database.Cursor): Fd {
        return Fd(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            principal = c.getDouble(c.getColumnIndexOrThrow("principal")),
            interestRate = c.getDouble(c.getColumnIndexOrThrow("interest_rate")),
            tenureYears = c.getDouble(c.getColumnIndexOrThrow("tenure_years")),
            startDate = c.getString(c.getColumnIndexOrThrow("start_date")),
            status = runCatching { FdStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status"))) }
                .getOrDefault(FdStatus.ACTIVE),
            completedGain = c.getDouble(c.getColumnIndexOrThrow("completed_gain")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
        )
    }

    // ============================ RESET ============================

    fun resetAll() {
        writableDatabase.delete("transactions", null, null)
        writableDatabase.delete("income_sources", null, null)
        writableDatabase.delete("expense_categories", null, null)
        writableDatabase.delete("auto_income", null, null)
        writableDatabase.delete("auto_expense", null, null)
        writableDatabase.delete("reminders", null, null)
        writableDatabase.delete("notes", null, null)
        writableDatabase.delete("bank_statements", null, null)
        writableDatabase.delete("loans", null, null)
        writableDatabase.delete("loan_schedules", null, null)
        writableDatabase.delete("loan_payments", null, null)
        writableDatabase.delete("loan_notifications", null, null)
        writableDatabase.delete("fds", null, null)
    }

    /**
     * Fully deletes all app data so it cannot be recovered from the device.
     * Removes the database file itself (plus WAL/SHM/journal), all receipt
     * files, and the app's own JSON backups in Downloads. The schema is
     * recreated empty and defaults reseeded on next use.
     */
    fun purgeAll(context: Context) {
        close()
        val dbFile = context.getDatabasePath(DB_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            File(dbFile.absolutePath + suffix).delete()
        }
        File(context.filesDir, "receipts").listFiles()?.forEach { it.delete() }
        deleteOwnBackupFiles(context)
        seedDefaults()
    }

    private fun deleteOwnBackupFiles(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                    arrayOf("salarycast_backup_%")
                )
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (dir.exists()) {
                    dir.listFiles()?.forEach { f ->
                        if (f.name.startsWith("salarycast_backup_")) f.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // Best-effort cleanup; the database and receipts are always removed.
        }
    }
}
