package com.example.salarycalc_trackincomeandexpense.data

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Pure loan mathematics — no Android dependencies, fully unit-testable.
 *
 * All money is BigDecimal (scale 2, HALF_UP). Dates use java.time (LocalDate),
 * with month-based due-date arithmetic that handles short months, leap years
 * and payment day 31 correctly (never naive "+30 days").
 *
 * Interest models:
 *  - FLAT:     interest is fixed on the ORIGINAL principal, spread monthly.
 *              perMonthInterest = P * (rate/100) / 12  (constant).
 *  - REDUCING: interest is recomputed each month on the outstanding principal.
 *
 * Extra payment rule (per user): extra above the monthly amount reduces
 * PRINCIPAL while keeping the monthly payment unchanged, so the loan
 * completes early.
 *
 * Partial payment rule (per user): interest first, then principal.
 */
object LoanCalculationEngine {

    private val MONEY = MathContext(20, RoundingMode.HALF_UP)
    private val HUNDRED = BigDecimal(100)
    private val TWELVE = BigDecimal(12)

    // Safety cap so a malformed loan cannot loop forever.
    private const val MAX_MONTHS = 600

    data class LoanSpec(
        val principal: BigDecimal,
        val annualRate: BigDecimal,     // percent per annum, e.g. 12
        val durationMonths: Int,
        val monthlyPayment: BigDecimal,
        val interestType: InterestType,
        val firstDueDate: LocalDate,
        val paymentDay: Int
    )

    data class Installment(
        val paymentNumber: Int,
        val dueDate: LocalDate,
        val expectedPayment: BigDecimal,
        val principal: BigDecimal,
        val interest: BigDecimal
    )

    data class Schedule(
        val installments: List<Installment>,
        val totalInterest: BigDecimal
    )

    data class PaymentAllocation(
        val interestPaid: BigDecimal,
        val principalPaid: BigDecimal,
        val extraPayment: BigDecimal,
        val fullPayment: Boolean
    )

    // ============================ VALIDATION ============================

    /** Returns a list of user-facing validation errors (empty when valid). */
    fun validate(spec: LoanSpec): List<String> {
        val errors = mutableListOf<String>()
        if (spec.principal <= BigDecimal.ZERO) errors += "Loan amount must be greater than zero"
        if (spec.annualRate < BigDecimal.ZERO) errors += "Interest rate cannot be negative"
        if (spec.durationMonths < 1) errors += "Loan duration must be at least 1 month"
        if (spec.monthlyPayment <= BigDecimal.ZERO) errors += "Monthly payment must be greater than zero"
        if (spec.paymentDay !in 1..31) errors += "Payment day must be between 1 and 31"
        if (spec.firstDueDate.isBefore(LocalDate.now())) errors += "First due date cannot be in the past"

        if (spec.principal > BigDecimal.ZERO && spec.durationMonths >= 1) {
            val perMonthInterest = monthlyInterest(spec.principal, spec.annualRate, spec.interestType)
            if (spec.monthlyPayment <= perMonthInterest) {
                errors += "Monthly payment must be more than the monthly interest (${money(perMonthInterest)})"
            }
            if (spec.interestType == InterestType.FLAT) {
                val totalInterest = perMonthInterest.multiply(BigDecimal(spec.durationMonths), MONEY)
                val affordable = spec.monthlyPayment.multiply(BigDecimal(spec.durationMonths), MONEY)
                if (affordable < spec.principal.add(totalInterest, MONEY)) {
                    errors += "Monthly payment is too low to cover the loan + interest within the duration"
                }
            }
        }
        return errors
    }

    // ============================ SCHEDULE ============================

    /**
     * Builds the repayment schedule. The final installment is adjusted so the
     * principal is exactly cleared (no forced-zero rounding).
     */
    fun generateSchedule(spec: LoanSpec): Schedule {
        val perMonthInterest = monthlyInterest(spec.principal, spec.annualRate, spec.interestType)
        val rows = mutableListOf<Installment>()
        var outstanding = spec.principal
        var number = 1
        var due = spec.firstDueDate

        while (outstanding > BigDecimal.ZERO && number <= MAX_MONTHS) {
            val interest = perMonthInterestFor(spec, outstanding, perMonthInterest)
            var principalPortion = spec.monthlyPayment.subtract(interest, MONEY)
            if (principalPortion <= BigDecimal.ZERO) {
                throw IllegalArgumentException("Monthly payment does not cover interest")
            }
            if (principalPortion >= outstanding) {
                principalPortion = outstanding
                rows.add(
                    Installment(
                        paymentNumber = number,
                        dueDate = due,
                        expectedPayment = principalPortion.add(interest, MONEY),
                        principal = principalPortion,
                        interest = interest
                    )
                )
                break
            }
            rows.add(
                Installment(
                    paymentNumber = number,
                    dueDate = due,
                    expectedPayment = spec.monthlyPayment,
                    principal = principalPortion,
                    interest = interest
                )
            )
            outstanding = outstanding.subtract(principalPortion, MONEY)
            number++
            due = nextDueDate(due, spec.paymentDay)
        }

        val totalInterest = rows.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.interest, MONEY) }
        return Schedule(rows, totalInterest)
    }

    /**
     * Regenerates the FUTURE schedule after an extra payment reduced the
     * outstanding principal. Keeps the monthly payment unchanged, so the loan
     * finishes early. For FLAT loans the monthly interest stays fixed on the
     * original principal; for REDUCING it recomputes on the outstanding.
     */
    fun regenerateSchedule(
        remainingPrincipal: BigDecimal,
        spec: LoanSpec,
        originalMonthlyInterest: BigDecimal,
        startNumber: Int,
        firstRemainingDue: LocalDate
    ): Schedule {
        if (remainingPrincipal <= BigDecimal.ZERO) {
            return Schedule(emptyList(), BigDecimal.ZERO)
        }
        val rows = mutableListOf<Installment>()
        var outstanding = remainingPrincipal
        var number = startNumber
        var due = firstRemainingDue

        while (outstanding > BigDecimal.ZERO && number <= MAX_MONTHS) {
            val interest = when (spec.interestType) {
                InterestType.FLAT -> originalMonthlyInterest
                InterestType.REDUCING -> reducingInterest(outstanding, spec.annualRate)
            }
            var principalPortion = spec.monthlyPayment.subtract(interest, MONEY)
            if (principalPortion <= BigDecimal.ZERO) {
                throw IllegalArgumentException("Monthly payment does not cover interest")
            }
            if (principalPortion >= outstanding) {
                principalPortion = outstanding
                rows.add(
                    Installment(
                        paymentNumber = number,
                        dueDate = due,
                        expectedPayment = principalPortion.add(interest, MONEY),
                        principal = principalPortion,
                        interest = interest
                    )
                )
                break
            }
            rows.add(
                Installment(
                    paymentNumber = number,
                    dueDate = due,
                    expectedPayment = spec.monthlyPayment,
                    principal = principalPortion,
                    interest = interest
                )
            )
            outstanding = outstanding.subtract(principalPortion, MONEY)
            number++
            due = nextDueDate(due, spec.paymentDay)
        }

        val totalInterest = rows.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.interest, MONEY) }
        return Schedule(rows, totalInterest)
    }

    // ============================ PAYMENT ALLOCATION ============================

    /**
     * Splits a payment against the current installment. Interest is settled
     * first; anything beyond the installment's expected amount is an extra
     * payment that goes to principal.
     */
    fun allocatePayment(current: Installment, amount: BigDecimal): PaymentAllocation {
        if (amount >= current.expectedPayment) {
            return PaymentAllocation(
                interestPaid = current.interest,
                principalPaid = current.principal,
                extraPayment = amount.subtract(current.expectedPayment, MONEY),
                fullPayment = true
            )
        }
        val interestPaid = amount.min(current.interest)
        val principalPaid = amount.subtract(interestPaid, MONEY).min(current.principal)
        return PaymentAllocation(
            interestPaid = interestPaid,
            principalPaid = principalPaid,
            extraPayment = BigDecimal.ZERO,
            fullPayment = false
        )
    }

    // ============================ DATES ============================

    /** Adds one month to [due] and clamps to [paymentDay] (Feb, 30/31 fallback). */
    fun nextDueDate(due: LocalDate, paymentDay: Int): LocalDate {
        val next = YearMonth.from(due).plusMonths(1)
        return next.atDay(paymentDay.coerceAtMost(next.lengthOfMonth()))
    }

    fun daysRemaining(due: LocalDate, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, due)

    // ============================ STATUS ============================

    fun scheduleStatus(
        dueDate: LocalDate,
        expectedPayment: BigDecimal,
        paidAmount: BigDecimal,
        today: LocalDate
    ): ScheduleStatus {
        if (paidAmount >= expectedPayment) return ScheduleStatus.PAID
        if (paidAmount > BigDecimal.ZERO) {
            return if (today.isAfter(dueDate)) ScheduleStatus.OVERDUE else ScheduleStatus.PARTIALLY_PAID
        }
        return when {
            today.isEqual(dueDate) -> ScheduleStatus.DUE_TODAY
            today.isEqual(dueDate.minusDays(1)) -> ScheduleStatus.DUE_TOMORROW
            today.isAfter(dueDate) -> ScheduleStatus.OVERDUE
            else -> ScheduleStatus.UPCOMING
        }
    }

    // ============================ HELPERS ============================

    /** Fixed monthly interest: FLAT uses the original principal, REDUCING uses outstanding. */
    private fun monthlyInterest(principal: BigDecimal, annualRate: BigDecimal, type: InterestType): BigDecimal =
        when (type) {
            InterestType.FLAT -> reducingInterest(principal, annualRate)
            InterestType.REDUCING -> reducingInterest(principal, annualRate)
        }

    private fun perMonthInterestFor(
        spec: LoanSpec,
        outstanding: BigDecimal,
        flatMonthlyInterest: BigDecimal
    ): BigDecimal = when (spec.interestType) {
        InterestType.FLAT -> flatMonthlyInterest
        InterestType.REDUCING -> reducingInterest(outstanding, spec.annualRate)
    }

    private fun reducingInterest(outstanding: BigDecimal, annualRate: BigDecimal): BigDecimal =
        outstanding.multiply(annualRate, MONEY).divide(HUNDRED, MONEY).divide(TWELVE, MONEY)
            .setScale(2, RoundingMode.HALF_UP)

    fun money(v: BigDecimal): BigDecimal = v.setScale(2, RoundingMode.HALF_UP)
}
