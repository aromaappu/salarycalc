package com.example.salarycalc_trackincomeandexpense.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class LoanCalculationEngineTest {

    private fun spec(
        principal: String,
        rate: String,
        months: Int,
        monthly: String,
        type: InterestType = InterestType.FLAT,
        firstDue: String = "2026-01-10",
        day: Int = 10
    ) = LoanCalculationEngine.LoanSpec(
        principal = BigDecimal(principal),
        annualRate = BigDecimal(rate),
        durationMonths = months,
        monthlyPayment = BigDecimal(monthly),
        interestType = type,
        firstDueDate = LocalDate.parse(firstDue),
        paymentDay = day
    )

    @Test
    fun flatSchedulePaysOffExactPrincipal() {
        // 1,00,000 at 12% p.a. for 24 months flat -> 24,000 interest, ~5,166.67/month
        val s = spec("100000", "12", 24, "5166.67")
        val schedule = LoanCalculationEngine.generateSchedule(s)
        assertEquals(24, schedule.installments.size)
        val principalSum = schedule.installments.sumOf { it.principal }
        assertEquals(BigDecimal("100000.00"), LoanCalculationEngine.money(principalSum))
        assertEquals(BigDecimal("24000.00"), LoanCalculationEngine.money(schedule.totalInterest))
        assertEquals(1, schedule.installments.first().paymentNumber)
        assertEquals(24, schedule.installments.last().paymentNumber)
    }

    @Test
    fun reducingBalanceClearsPrincipal() {
        val s = spec("10000", "12", 12, "900", InterestType.REDUCING)
        val schedule = LoanCalculationEngine.generateSchedule(s)
        val principalSum = schedule.installments.sumOf { it.principal }
        assertEquals(BigDecimal("10000.00"), LoanCalculationEngine.money(principalSum))
        // First month interest = 10000 * 0.12 / 12 = 100
        assertEquals(BigDecimal("100.00"), schedule.installments.first().interest)
        assertTrue(schedule.installments.last().dueDate >= LocalDate.parse("2026-12-10"))
    }

    @Test
    fun monthlyPaymentMustCoverInterest() {
        val s = spec("10000", "36", 12, "50", InterestType.FLAT)
        assertTrue(LoanCalculationEngine.validate(s).isNotEmpty())
    }

    @Test
    fun validationRejectsNegativeAmount() {
        val s = spec("-5000", "12", 12, "500")
        assertTrue(LoanCalculationEngine.validate(s).any { it.contains("Loan amount") })
    }

    @Test
    fun validationRejectsPastFirstDue() {
        val s = LoanCalculationEngine.LoanSpec(
            BigDecimal("5000"), BigDecimal("12"), 12, BigDecimal("500"),
            InterestType.FLAT, LocalDate.parse("2020-01-10"), 10
        )
        assertTrue(LoanCalculationEngine.validate(s).any { it.contains("past") })
    }

    @Test
    fun nextDueDateClampsToFebruary() {
        assertEquals(
            LocalDate.parse("2026-02-28"),
            LoanCalculationEngine.nextDueDate(LocalDate.parse("2026-01-31"), 31)
        )
    }

    @Test
    fun nextDueDateHandlesLeapYear() {
        assertEquals(
            LocalDate.parse("2024-02-29"),
            LoanCalculationEngine.nextDueDate(LocalDate.parse("2024-01-31"), 31)
        )
    }

    @Test
    fun nextDueDateClampsTo30DayMonth() {
        assertEquals(
            LocalDate.parse("2025-09-30"),
            LoanCalculationEngine.nextDueDate(LocalDate.parse("2025-08-31"), 31)
        )
    }

    @Test
    fun nextDueDateRollsOverYear() {
        assertEquals(
            LocalDate.parse("2026-01-15"),
            LoanCalculationEngine.nextDueDate(LocalDate.parse("2025-12-15"), 15)
        )
    }

    @Test
    fun fullPaymentAllocation() {
        val current = LoanCalculationEngine.Installment(
            1, LocalDate.parse("2026-08-10"),
            BigDecimal("5000"), BigDecimal("4200"), BigDecimal("800")
        )
        val alloc = LoanCalculationEngine.allocatePayment(current, BigDecimal("5000"))
        assertEquals(BigDecimal("800"), alloc.interestPaid)
        assertEquals(BigDecimal("4200"), alloc.principalPaid)
        assertEquals(BigDecimal.ZERO, alloc.extraPayment)
        assertTrue(alloc.fullPayment)
    }

    @Test
    fun partialPaymentInterestFirst() {
        val current = LoanCalculationEngine.Installment(
            1, LocalDate.parse("2026-08-10"),
            BigDecimal("5000"), BigDecimal("4200"), BigDecimal("800")
        )
        val alloc = LoanCalculationEngine.allocatePayment(current, BigDecimal("3000"))
        assertEquals(BigDecimal("800"), alloc.interestPaid)
        assertEquals(BigDecimal("2200"), alloc.principalPaid)
        assertEquals(BigDecimal.ZERO, alloc.extraPayment)
        assertTrue(!alloc.fullPayment)
    }

    @Test
    fun extraPaymentGoesToPrincipal() {
        val current = LoanCalculationEngine.Installment(
            1, LocalDate.parse("2026-08-10"),
            BigDecimal("5000"), BigDecimal("4200"), BigDecimal("800")
        )
        val alloc = LoanCalculationEngine.allocatePayment(current, BigDecimal("7000"))
        assertEquals(BigDecimal("800"), alloc.interestPaid)
        assertEquals(BigDecimal("4200"), alloc.principalPaid)
        assertEquals(BigDecimal("2000"), alloc.extraPayment)
        assertTrue(alloc.fullPayment)
    }

    @Test
    fun statusesComputeFromCurrentDate() {
        val today = LocalDate.parse("2026-08-10")
        assertEquals(
            ScheduleStatus.PAID,
            LoanCalculationEngine.scheduleStatus(today, BigDecimal("5000"), BigDecimal("5000"), today)
        )
        assertEquals(
            ScheduleStatus.DUE_TODAY,
            LoanCalculationEngine.scheduleStatus(today, BigDecimal("5000"), BigDecimal.ZERO, today)
        )
        assertEquals(
            ScheduleStatus.DUE_TOMORROW,
            LoanCalculationEngine.scheduleStatus(
                LocalDate.parse("2026-08-11"), BigDecimal("5000"), BigDecimal.ZERO, today
            )
        )
        assertEquals(
            ScheduleStatus.OVERDUE,
            LoanCalculationEngine.scheduleStatus(
                LocalDate.parse("2026-08-09"), BigDecimal("5000"), BigDecimal.ZERO, today
            )
        )
        assertEquals(
            ScheduleStatus.UPCOMING,
            LoanCalculationEngine.scheduleStatus(
                LocalDate.parse("2026-09-10"), BigDecimal("5000"), BigDecimal.ZERO, today
            )
        )
        assertEquals(
            ScheduleStatus.PARTIALLY_PAID,
            LoanCalculationEngine.scheduleStatus(today, BigDecimal("5000"), BigDecimal("3000"), today)
        )
        assertEquals(
            ScheduleStatus.OVERDUE,
            LoanCalculationEngine.scheduleStatus(
                LocalDate.parse("2026-08-09"), BigDecimal("5000"), BigDecimal("3000"), today
            )
        )
    }

    @Test
    fun extraPaymentShortensSchedule() {
        val s = spec("100000", "12", 24, "5166.67")
        val original = LoanCalculationEngine.generateSchedule(s)
        val originalMonthlyInterest = original.installments.first().interest

        // After 1 full payment of 5,166.67, outstanding ~95,833.33
        val outstanding = BigDecimal("95833.33")
        val regen = LoanCalculationEngine.regenerateSchedule(
            remainingPrincipal = outstanding,
            spec = s,
            originalMonthlyInterest = originalMonthlyInterest,
            startNumber = 2,
            firstRemainingDue = LoanCalculationEngine.nextDueDate(s.firstDueDate, s.paymentDay)
        )
        val paidOffRows = regen.installments.sumOf { it.principal }
        assertEquals(LoanCalculationEngine.money(BigDecimal("95833.33")), LoanCalculationEngine.money(paidOffRows))
        assertEquals(2, regen.installments.first().paymentNumber)
    }

    @Test
    fun daysRemaining() {
        assertEquals(
            3L,
            LoanCalculationEngine.daysRemaining(
                LocalDate.parse("2026-08-13"), LocalDate.parse("2026-08-10")
            )
        )
        assertEquals(
            -1L,
            LoanCalculationEngine.daysRemaining(
                LocalDate.parse("2026-08-09"), LocalDate.parse("2026-08-10")
            )
        )
    }
}
