package com.example.salarycalc_trackincomeandexpense.data

import android.content.Context
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.min

/**
 * Shared payment recording used by the LoanPaymentDialog AND the app-open
 * due popup, so both produce identical results.
 *
 * Rules (per user):
 *  - Interest is settled first, then principal.
 *  - Any amount above the installment is an extra payment -> reduces principal,
 *    keeping the monthly payment unchanged (loan completes early).
 *  - Partial payments leave the installment PARTIALLY_PAID.
 */
object LoanPaymentRecorder {

    data class Result(
        val ok: Boolean,
        val message: String,
        val fullyPaid: Boolean,
        val extraApplied: Boolean
    )

    fun record(
        context: Context,
        db: AppDatabase,
        loanId: Long,
        amount: Double,
        paymentDate: String,
        method: String,
        reference: String,
        notes: String
    ): Result {
        val loan = db.getLoan(loanId)
            ?: return Result(false, "Loan not found", false, false)
        if (loan.status != LoanStatus.ACTIVE) {
            return Result(false, "Loan is not active", false, false)
        }
        val schedules = db.getLoanSchedules(loanId)
        val current = schedules.firstOrNull {
            it.status != ScheduleStatus.PAID && it.paidAmount < it.expectedPayment
        } ?: return Result(false, "No pending installment", false, false)
        if (amount <= 0) return Result(false, "Enter a valid amount", false, false)

        val payments = db.getLoanPayments(loanId)
        val alreadyInterest = payments.filter { it.scheduleId == current.id }.sumOf { it.interestAmount }
        val alreadyPrincipal = payments.filter { it.scheduleId == current.id }.sumOf { it.principalAmount }
        val remainingInterest = (current.interest - alreadyInterest).coerceAtLeast(0.0)
        val remainingPrincipal = (current.principal - alreadyPrincipal).coerceAtLeast(0.0)
        val remainingOnInstallment = (current.expectedPayment - current.paidAmount).coerceAtLeast(0.0)

        if (amount > remainingOnInstallment + loan.remainingBalance) {
            return Result(false, "Amount exceeds the loan balance", false, false)
        }
        val today = LocalDate.now()

        val interestPaid = min(amount, remainingInterest)
        val principalPaid = min(amount - interestPaid, remainingPrincipal)
        val extra = amount - interestPaid - principalPaid

        db.addLoanPayment(
            loanId = loan.id,
            scheduleId = current.id,
            paymentDate = paymentDate,
            expectedAmount = current.expectedPayment,
            paidAmount = amount,
            principalAmount = principalPaid,
            interestAmount = interestPaid,
            extraPayment = extra,
            paymentMethod = method,
            reference = reference,
            notes = notes
        )

        val newPaidAmount = min(current.paidAmount + amount, current.expectedPayment)
        val newStatus = if (newPaidAmount >= current.expectedPayment) {
            ScheduleStatus.PAID
        } else if (today.isAfter(runCatching { LocalDate.parse(current.dueDate) }.getOrDefault(today))) {
            ScheduleStatus.OVERDUE
        } else ScheduleStatus.PARTIALLY_PAID
        db.updateLoanSchedule(current.id, newPaidAmount, newStatus)

        val newTotalPaid = loan.totalPaid + amount
        val newTotalInterest = loan.totalInterestPaid + interestPaid
        var newRemaining = loan.remainingBalance - principalPaid - extra

        if (extra > 0 && newStatus == ScheduleStatus.PAID) {
            if (newRemaining <= 0.0) {
                db.deleteLoanSchedulesFrom(loan.id, current.paymentNumber + 1)
                newRemaining = 0.0
            } else {
                val future = schedules.filter {
                    it.paymentNumber > current.paymentNumber &&
                        it.status != ScheduleStatus.PAID && it.paidAmount < it.expectedPayment
                }
                val startNumber = future.firstOrNull()?.paymentNumber ?: (current.paymentNumber + 1)
                val firstRemainingDue = runCatching {
                    LocalDate.parse(future.firstOrNull()?.dueDate)
                }.getOrElse {
                    LoanCalculationEngine.nextDueDate(
                        runCatching { LocalDate.parse(current.dueDate) }.getOrDefault(today),
                        loan.paymentDay
                    )
                }
                val spec = LoanCalculationEngine.LoanSpec(
                    principal = BigDecimal(newRemaining),
                    annualRate = BigDecimal(loan.interestRate),
                    durationMonths = loan.durationMonths,
                    monthlyPayment = BigDecimal(loan.monthlyPayment),
                    interestType = loan.interestType,
                    firstDueDate = firstRemainingDue,
                    paymentDay = loan.paymentDay
                )
                val originalMonthlyInterest = if (loan.interestType == InterestType.FLAT) {
                    BigDecimal(loan.originalAmount).multiply(BigDecimal(loan.interestRate))
                        .divide(BigDecimal(100), MathContext(20, RoundingMode.HALF_UP))
                        .divide(BigDecimal(12), MathContext(20, RoundingMode.HALF_UP))
                        .setScale(2, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                val regen = LoanCalculationEngine.regenerateSchedule(
                    remainingPrincipal = BigDecimal(newRemaining),
                    spec = spec,
                    originalMonthlyInterest = originalMonthlyInterest,
                    startNumber = startNumber,
                    firstRemainingDue = firstRemainingDue
                )
                db.deleteLoanSchedulesFrom(loan.id, startNumber)
                regen.installments.forEach { inst ->
                    db.addLoanSchedule(
                        loan.id,
                        inst.paymentNumber,
                        inst.dueDate.toString(),
                        inst.expectedPayment.toDouble(),
                        inst.principal.toDouble(),
                        inst.interest.toDouble(),
                        ScheduleStatus.UPCOMING
                    )
                }
                regen.installments.lastOrNull()?.let {
                    db.setLoanExpectedCompletion(loan.id, it.dueDate.toString())
                }
            }
        }

        db.updateLoanTotals(loan.id, newTotalPaid, newTotalInterest, newRemaining.coerceAtLeast(0.0))
        recomputeStatuses(db, loan.id, today)

        if (newRemaining <= 0.0) {
            db.markLoanCompleted(loan.id, today.toString())
            LoanScheduler.cancelAll(context, db, loan.id)
            return Result(true, "🎉 Loan fully paid!", true, false)
        }
        LoanScheduler.scheduleNext(context, db, loan.id)
        if (extra > 0) return Result(true, "✅ Payment recorded · extra went to principal", false, true)
        return Result(true, "✅ Payment recorded", false, false)
    }

    /** Next unpaid installment of a loan, if any. */
    fun nextUnpaid(db: AppDatabase, loanId: Long): LoanSchedule? =
        db.getLoanSchedules(loanId).firstOrNull {
            it.status != ScheduleStatus.PAID && it.paidAmount < it.expectedPayment
        }

    private fun recomputeStatuses(db: AppDatabase, loanId: Long, today: LocalDate) {
        db.getLoanSchedules(loanId).forEach { s ->
            val due = runCatching { LocalDate.parse(s.dueDate) }.getOrElse { today }
            val status = LoanCalculationEngine.scheduleStatus(
                dueDate = due,
                expectedPayment = BigDecimal(s.expectedPayment),
                paidAmount = BigDecimal(s.paidAmount),
                today = today
            )
            if (status != s.status) db.updateLoanSchedule(s.id, s.paidAmount, status)
        }
    }
}
