package com.example.salarycalc_trackincomeandexpense.data

import android.content.Context
import com.example.salarycalc_trackincomeandexpense.NotificationHelper
import com.example.salarycalc_trackincomeandexpense.ReminderScheduler
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Loan due-date notifications, built on the app's existing
 * ReminderScheduler (AlarmManager) + NotificationStore rails.
 *
 * Every alarm id is derived deterministically from loanId + paymentNumber,
 * so schedules/cancels always hit the same PendingIntent (duplicate-safe)
 * and one loan's alarms never collide with another's.
 */
object LoanScheduler {

    private val AMOUNT = NumberFormat.getNumberInstance(Locale("en", "IN"))

    /** Stable alarm id for a loan installment reminder (day-before = 0, due = 1). */
    fun alarmId(loanId: Long, paymentNumber: Int, dayBefore: Boolean): Int {
        val offset = if (dayBefore) 0 else 1
        return 300000 + (loanId % 50000L).toInt() * 10 + paymentNumber * 2 + offset
    }

    /**
     * Cancels every pending alarm for the loan, then schedules the next
     * unpaid installment's day-before + due-day reminders.
     */
    fun scheduleNext(context: Context, db: AppDatabase, loanId: Long) {
        val loan = db.getLoan(loanId) ?: return
        if (loan.status != LoanStatus.ACTIVE) {
            cancelAll(context, db, loanId)
            return
        }
        cancelAll(context, db, loanId)

        val today = LocalDate.now()
        val schedules = db.getLoanSchedules(loanId)
        val next = schedules.firstOrNull { isUnpaid(it) } ?: return

        val due = parseDate(next.dueDate) ?: return
        val expected = next.expectedPayment

        // One day before
        val dayBefore = due.minusDays(1)
        val beforeId = alarmId(loanId, next.paymentNumber, dayBefore = true)
        if (!dayBefore.isBefore(today)) {
            ReminderScheduler.schedule(
                context, beforeId, "Loan Payment Reminder",
                "${loan.name} payment of ${rupee(expected)} is due tomorrow.\nDue date: ${fmt(due)}",
                dayBefore.toString()
            )
            db.addLoanNotification(loanId, next.paymentNumber, next.dueDate, "DAY_BEFORE")
        }

        // On the due date
        val dueId = alarmId(loanId, next.paymentNumber, dayBefore = false)
        if (!due.isBefore(today)) {
            ReminderScheduler.schedule(
                context, dueId, "Loan Payment Due",
                "${loan.name}\n${rupee(expected)} is due today.\nTap to record payment.",
                due.toString()
            )
            db.addLoanNotification(loanId, next.paymentNumber, next.dueDate, "DUE")
        }
    }

    /** Shows an overdue alert right away for the next unpaid overdue installment. */
    fun notifyOverdue(context: Context, db: AppDatabase, loanId: Long) {
        val loan = db.getLoan(loanId) ?: return
        if (loan.status != LoanStatus.ACTIVE) return
        val today = LocalDate.now()
        val next = db.getLoanSchedules(loanId).firstOrNull {
            isUnpaid(it) && (parseDate(it.dueDate)?.isBefore(today) ?: false)
        } ?: return
        val remaining = next.expectedPayment - next.paidAmount
        NotificationHelper.showNotification(
            context,
            alarmId(loanId, next.paymentNumber, dayBefore = false),
            "Loan Payment Overdue",
            "${loan.name}\nDue: ${rupee(next.expectedPayment)} · Paid: ${rupee(next.paidAmount)} · Remaining: ${rupee(remaining)}"
        )
    }

    /** Cancels every alarm for the loan (payment/completion/deletion). */
    fun cancelAll(context: Context, db: AppDatabase, loanId: Long) {
        db.getLoanSchedules(loanId).forEach { s ->
            ReminderScheduler.cancel(context, alarmId(loanId, s.paymentNumber, dayBefore = true))
            ReminderScheduler.cancel(context, alarmId(loanId, s.paymentNumber, dayBefore = false))
        }
        db.clearLoanNotifications(loanId)
    }

    /** App-start reconciliation: reschedule next reminders + overdue alerts. */
    fun reconcileAll(context: Context, db: AppDatabase) {
        val today = LocalDate.now()
        val prefs = context.getSharedPreferences("salarycalc_loan_due_notices", Context.MODE_PRIVATE)
        db.getLoans(LoanStatus.ACTIVE).forEach { loan ->
            scheduleNext(context, db, loan.id)
            val next = db.getLoanSchedules(loan.id).firstOrNull { isUnpaid(it) } ?: return@forEach
            val due = parseDate(next.dueDate) ?: return@forEach
            val key = "loan_${loan.id}_${next.paymentNumber}_${today}"
            if (prefs.getBoolean(key, false)) return@forEach
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (due.isBefore(today)) {
                notifyOverdue(context, db, loan.id)
            } else if (due.isEqual(today) && hour >= 9) {
                NotificationHelper.showNotification(
                    context, alarmId(loan.id, next.paymentNumber, dayBefore = false),
                    "Loan payment due today",
                    "${loan.name}: ${rupee(next.expectedPayment - next.paidAmount)} remaining."
                )
            } else return@forEach
            prefs.edit().putBoolean(key, true).apply()
        }
    }

    private fun isUnpaid(s: LoanSchedule) =
        s.status != ScheduleStatus.PAID && s.paidAmount < s.expectedPayment

    private fun parseDate(s: String): LocalDate? = try {
        LocalDate.parse(s)
    } catch (e: Exception) {
        null
    }

    private fun rupee(v: Double): String = "₹" + AMOUNT.format(v)

    private fun fmt(d: LocalDate): String =
        SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date.from(d.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()))
}
