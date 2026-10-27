package com.example.salarycalc_trackincomeandexpense.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// JSON backup format matches the original WebView app's salarycast_backup_<date>.json
object BackupUtil {

    fun exportBackupJson(db: AppDatabase, settings: SettingsStore): String {
        val root = JSONObject()

        val txArray = JSONArray()
        db.getAllTransactions(orderAsc = true).forEach { t ->
            val o = JSONObject()
            o.put("id", t.id)
            o.put("type", t.type)
            o.put("amount", t.amount)
            if (t.type == AppDatabase.TYPE_INCOME) {
                o.put("source", t.name)
            } else {
                o.put("category", t.name)
            }
            o.put("date", t.date)
            if (t.receiptName != null) {
                val r = JSONObject()
                r.put("name", t.receiptName)
                val bytes = t.receiptPath?.let { path ->
                    try {
                        File(path).takeIf { it.exists() }?.readBytes()
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bytes != null && bytes.isNotEmpty()) {
                    r.put("data", Base64.encodeToString(bytes, Base64.DEFAULT))
                }
                o.put("receipt", r)
            } else {
                o.put("receipt", JSONObject.NULL)
            }
            txArray.put(o)
        }
        root.put("transactions", txArray)

        val remArray = JSONArray()
        db.getReminders().forEach { r ->
            val o = JSONObject()
            o.put("id", r.id)
            o.put("name", r.name)
            o.put("amount", r.amount)
            o.put("date", r.date)
            o.put("repeatMonthly", r.repeatMonthly)
            remArray.put(o)
        }
        root.put("reminders", remArray)

        root.put("customSources", JSONArray(db.getIncomeSources()))
        root.put("customCategories", JSONArray(db.getExpenseCategories()))
        root.put("savedNotes", db.getNotes())

        val ai = JSONArray()
        db.getAutoIncomes().forEach { a ->
            val o = JSONObject()
            o.put("source", a.source)
            o.put("amount", a.amount)
            o.put("day", a.dayOfMonth)
            ai.put(o)
        }
        root.put("autoIncomes", ai)

        val ae = JSONArray()
        db.getAutoExpenses().forEach { a ->
            val o = JSONObject()
            o.put("category", a.category)
            o.put("amount", a.amount)
            o.put("day", a.dayOfMonth)
            ae.put(o)
        }
        root.put("autoExpenses", ae)

        val loanArray = JSONArray()
        db.getAllLoans().forEach { loan ->
            val o = JSONObject()
            o.put("id", loan.id)
            o.put("name", loan.name)
            o.put("lender", loan.lender)
            o.put("original_amount", loan.originalAmount)
            o.put("interest_rate", loan.interestRate)
            o.put("interest_type", loan.interestType.name)
            o.put("start_date", loan.startDate)
            o.put("first_due_date", loan.firstDueDate)
            o.put("payment_day", loan.paymentDay)
            o.put("monthly_payment", loan.monthlyPayment)
            o.put("duration_months", loan.durationMonths)
            o.put("status", loan.status.name)
            o.put("expected_completion_date", loan.expectedCompletionDate)
            o.put("actual_completion_date", loan.actualCompletionDate)
            o.put("remaining_balance", loan.remainingBalance)
            o.put("total_paid", loan.totalPaid)
            o.put("total_interest_paid", loan.totalInterestPaid)
            o.put("created_at", loan.createdAt)
            o.put("updated_at", loan.updatedAt)

            val schedules = JSONArray()
            db.getLoanSchedules(loan.id).forEach { s ->
                val so = JSONObject()
                so.put("id", s.id)
                so.put("payment_number", s.paymentNumber)
                so.put("due_date", s.dueDate)
                so.put("expected_payment", s.expectedPayment)
                so.put("principal", s.principal)
                so.put("interest", s.interest)
                so.put("paid_amount", s.paidAmount)
                so.put("status", s.status.name)
                schedules.put(so)
            }
            o.put("schedules", schedules)

            val payments = JSONArray()
            db.getLoanPayments(loan.id).forEach { p ->
                val po = JSONObject()
                po.put("schedule_id", p.scheduleId)
                po.put("payment_date", p.paymentDate)
                po.put("expected_amount", p.expectedAmount)
                po.put("paid_amount", p.paidAmount)
                po.put("principal_amount", p.principalAmount)
                po.put("interest_amount", p.interestAmount)
                po.put("extra_payment", p.extraPayment)
                po.put("payment_method", p.paymentMethod)
                po.put("reference", p.reference)
                po.put("notes", p.notes)
                payments.put(po)
            }
            o.put("payments", payments)
            loanArray.put(o)
        }
        root.put("loans", loanArray)

        root.put("activeMonthKey", "")
        root.put("darkMode", settings.isDarkModeEnabled())
        root.put("pinEnabled", settings.isPinEnabled())
        root.put("pinCodeHash", settings.getPinHash())
        root.put("smsTrackingEnabled", settings.isSmsTrackingEnabled())
        root.put("smsConfirmationEnabled", settings.isSmsConfirmationEnabled())
        root.put("pendingSmsTransactions", settings.getPendingSmsTransactions())
        root.put("deletedAutoIncomeMonths", JSONArray(settings.getDeletedAutoIncomeMonths()))
        root.put("deletedAutoExpenseMonths", JSONArray(settings.getDeletedAutoExpenseMonths()))

        val currency = JSONObject()
        currency.put("code", "INR")
        currency.put("symbol", settings.getCurrencySymbol())
        currency.put("name", "Indian Rupee")
        root.put("currency", currency)

        // Compact output (no pretty-printing) keeps the export fast for large histories.
        return root.toString()
    }

    // Replaces all app data with the backup contents (same semantics as restoreData in core.js)
    fun importBackupJson(context: Context, db: AppDatabase, settings: SettingsStore, jsonText: String) {
        val data = JSONObject(jsonText)

        db.resetAll()

        val tx = data.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until tx.length()) {
            val o = tx.optJSONObject(i) ?: continue
            val type = o.optString("type", AppDatabase.TYPE_EXPENSE)
            val amount = o.optDouble("amount", 0.0)
            val name = if (type == AppDatabase.TYPE_INCOME) {
                o.optString("source")
            } else {
                o.optString("category")
            }
            val date = o.optString("date")
            if (name.isBlank() || amount <= 0 || date.isBlank()) continue

            var receiptName: String? = null
            var receiptPath: String? = null
            val receipt = o.opt("receipt")
            if (receipt is JSONObject) {
                val rn = receipt.optString("name")
                if (rn.isNotBlank()) receiptName = rn
                val data64 = receipt.optString("data")
                if (data64.isNotBlank() && rn.isNotBlank()) {
                    try {
                        val bytes = Base64.decode(data64, Base64.DEFAULT)
                        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
                        val f = File(dir, rn)
                        f.writeBytes(bytes)
                        receiptPath = f.absolutePath
                    } catch (e: Exception) {
                        // keep name only
                    }
                }
            } else if (receipt is String && receipt.isNotBlank()) {
                receiptName = receipt
            }

            db.addTransaction(type, amount, name, date, receiptName, receiptPath)
        }

        if (data.has("customSources")) {
            db.clearIncomeSources()
            data.getJSONArray("customSources").forEachString { db.addIncomeSource(it) }
        } else {
            AppDatabase.DEFAULT_SOURCES.forEach { db.addIncomeSource(it) }
        }

        if (data.has("customCategories")) {
            db.clearExpenseCategories()
            data.getJSONArray("customCategories").forEachString { db.addExpenseCategory(it) }
        } else {
            AppDatabase.DEFAULT_CATEGORIES.forEach { db.addExpenseCategory(it) }
        }

        if (data.has("savedNotes")) {
            db.saveNotes(data.optString("savedNotes"))
        }

        data.optJSONArray("autoIncomes")?.forEachJson { o ->
            db.addAutoIncome(o.optString("source"), o.optDouble("amount", 0.0), o.optInt("day", 1))
        }
        data.optJSONArray("autoExpenses")?.forEachJson { o ->
            db.addAutoExpense(o.optString("category"), o.optDouble("amount", 0.0), o.optInt("day", 1))
        }
        data.optJSONArray("reminders")?.forEachJson { o ->
            val name = o.optString("name")
            val amount = o.optDouble("amount", 0.0)
            val date = o.optString("date")
            val monthly = o.optBoolean("repeatMonthly", false)
            val id = db.addReminder(name, amount, date, monthly)
            if (id > 0) {
                com.example.salarycalc_trackincomeandexpense.ReminderScheduler.schedule(
                    context, id.toInt(), "Bill Reminder", "$name due: ₹$amount", date
                )
                if (monthly) com.example.salarycalc_trackincomeandexpense.ReminderScheduler.scheduleNextMonthly(context, id)
            }
        }

        data.optJSONArray("loans")?.forEachJson { o ->
            val loanId = db.addLoan(
                name = o.optString("name"),
                lender = o.optString("lender"),
                originalAmount = o.optDouble("original_amount", 0.0),
                interestRate = o.optDouble("interest_rate", 0.0),
                interestType = runCatching { InterestType.valueOf(o.optString("interest_type")) }
                    .getOrDefault(InterestType.FLAT),
                startDate = o.optString("start_date"),
                firstDueDate = o.optString("first_due_date"),
                paymentDay = o.optInt("payment_day", 1),
                monthlyPayment = o.optDouble("monthly_payment", 0.0),
                durationMonths = o.optInt("duration_months", 0),
                remainingBalance = o.optDouble("remaining_balance", 0.0),
                expectedCompletionDate = o.optString("expected_completion_date").takeIf { it.isNotBlank() }
            )
            if (loanId <= 0) return@forEachJson
            db.updateLoanTotals(
                loanId,
                o.optDouble("total_paid", 0.0),
                o.optDouble("total_interest_paid", 0.0),
                o.optDouble("remaining_balance", 0.0)
            )
            if (o.optString("status") == LoanStatus.COMPLETED.name) {
                db.markLoanCompleted(loanId, o.optString("actual_completion_date"))
            }

            val oldScheduleIds = mutableMapOf<Int, Long>()
            o.optJSONArray("schedules")?.forEachJson { so ->
                val sId = db.addLoanSchedule(
                    loanId,
                    so.optInt("payment_number", 0),
                    so.optString("due_date"),
                    so.optDouble("expected_payment", 0.0),
                    so.optDouble("principal", 0.0),
                    so.optDouble("interest", 0.0),
                    runCatching { ScheduleStatus.valueOf(so.optString("status")) }
                        .getOrDefault(ScheduleStatus.UPCOMING)
                )
                val paid = so.optDouble("paid_amount", 0.0)
                if (paid > 0) {
                    val status = runCatching { ScheduleStatus.valueOf(so.optString("status")) }
                        .getOrDefault(ScheduleStatus.PAID)
                    db.updateLoanSchedule(sId, paid, status)
                }
                oldScheduleIds[so.optInt("id", -1)] = sId
            }

            o.optJSONArray("payments")?.forEachJson { po ->
                val oldScheduleId = po.optLong("schedule_id", -1L)
                db.addLoanPayment(
                    loanId,
                    oldScheduleIds[oldScheduleId.toInt()] ?: -1L,
                    po.optString("payment_date"),
                    po.optDouble("expected_amount", 0.0),
                    po.optDouble("paid_amount", 0.0),
                    po.optDouble("principal_amount", 0.0),
                    po.optDouble("interest_amount", 0.0),
                    po.optDouble("extra_payment", 0.0),
                    po.optString("payment_method"),
                    po.optString("reference"),
                    po.optString("notes")
                )
            }
        }

        if (data.has("darkMode")) settings.setDarkMode(data.optBoolean("darkMode"))
        if (data.has("pinEnabled")) settings.setPinEnabled(data.optBoolean("pinEnabled"))
        if (data.has("pinCodeHash")) settings.setPinHash(data.optString("pinCodeHash"))
        if (data.has("smsTrackingEnabled")) settings.setSmsTrackingEnabled(data.optBoolean("smsTrackingEnabled"))
        if (data.has("smsConfirmationEnabled")) settings.setSmsConfirmationEnabled(data.optBoolean("smsConfirmationEnabled"))
        if (data.has("pendingSmsTransactions")) {
            settings.setPendingSmsTransactions(data.optString("pendingSmsTransactions"))
        }
        data.optJSONObject("currency")?.let {
            settings.setCurrencySymbol(it.optString("symbol", "₹"))
        }
        if (data.has("deletedAutoIncomeMonths")) {
            settings.setDeletedAutoIncomeMonths(
                data.getJSONArray("deletedAutoIncomeMonths").toStringList()
            )
        }
        if (data.has("deletedAutoExpenseMonths")) {
            settings.setDeletedAutoExpenseMonths(
                data.getJSONArray("deletedAutoExpenseMonths").toStringList()
            )
        }
    }

    // Writes the JSON backup to the public Downloads folder (media-safe on all API levels).
    // Returns the display filename on success, or null on failure.
    fun exportToDownloads(context: Context, db: AppDatabase, settings: SettingsStore): String? {
        val json = exportBackupJson(db, settings)
        val filename = "salarycast_backup_${
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }.json"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                filename
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file).use { out: OutputStream ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                filename
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun JSONArray.forEachString(action: (String) -> Unit) {
        for (i in 0 until length()) action(getString(i))
    }

    private fun JSONArray.forEachJson(action: (JSONObject) -> Unit) {
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            action(o)
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }
}