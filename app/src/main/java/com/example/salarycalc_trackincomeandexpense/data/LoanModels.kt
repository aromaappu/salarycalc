package com.example.salarycalc_trackincomeandexpense.data

import java.math.BigDecimal

enum class InterestType { FLAT, REDUCING }

enum class LoanStatus { ACTIVE, COMPLETED }

enum class ScheduleStatus {
    UPCOMING,
    DUE_TOMORROW,
    DUE_TODAY,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    COMPLETED
}

data class Loan(
    val id: Long,
    val name: String,
    val lender: String,
    val originalAmount: Double,
    val interestRate: Double,          // percent per annum
    val interestType: InterestType,
    val startDate: String,             // "yyyy-MM-dd"
    val firstDueDate: String,          // "yyyy-MM-dd"
    val paymentDay: Int,
    val monthlyPayment: Double,
    val durationMonths: Int,
    val status: LoanStatus,
    val expectedCompletionDate: String?,  // "yyyy-MM-dd"
    val actualCompletionDate: String?,    // "yyyy-MM-dd"
    val totalPaid: Double,
    val totalInterestPaid: Double,
    val remainingBalance: Double,      // principal still owed
    val createdAt: Long,
    val updatedAt: Long
)

data class LoanSchedule(
    val id: Long,
    val loanId: Long,
    val paymentNumber: Int,
    val dueDate: String,               // "yyyy-MM-dd"
    val expectedPayment: Double,
    val principal: Double,
    val interest: Double,
    val paidAmount: Double,
    val status: ScheduleStatus
)

data class LoanPayment(
    val id: Long,
    val loanId: Long,
    val scheduleId: Long,
    val paymentDate: String,           // "yyyy-MM-dd"
    val expectedAmount: Double,
    val paidAmount: Double,
    val principalAmount: Double,
    val interestAmount: Double,
    val extraPayment: Double,
    val paymentMethod: String,
    val reference: String,
    val notes: String
)

/** One scheduled reminder for a loan, so notifications can be tracked and cancelled. */
data class LoanNotification(
    val id: Long,
    val loanId: Long,
    val paymentNumber: Int,
    val dueDate: String,
    val notifType: String,             // "DAY_BEFORE" | "DUE"
    val scheduledAt: Long
)
