package com.example.salarycalc_trackincomeandexpense.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object FdMath {

    const val DAYS_PER_YEAR = 365.0

    fun totalDays(tenureYears: Double): Long = (tenureYears * DAYS_PER_YEAR).toLong()

    fun maturityDate(start: LocalDate, tenureYears: Double): LocalDate =
        start.plusDays(totalDays(tenureYears))

    fun daysElapsed(start: LocalDate, asOf: LocalDate): Long =
        ChronoUnit.DAYS.between(start, asOf).coerceAtLeast(0)

    fun daysRemaining(start: LocalDate, asOf: LocalDate, tenureYears: Double): Long =
        (totalDays(tenureYears) - daysElapsed(start, asOf)).coerceAtLeast(0)

    /** Current value today = P * (1 + r/36500)^elapsedDays, capped at maturity. */
    fun currentValue(
        principal: Double,
        ratePercentPerYear: Double,
        start: LocalDate,
        asOf: LocalDate,
        tenureYears: Double
    ): Double {
        val elapsed = daysElapsed(start, asOf).coerceAtMost(totalDays(tenureYears))
        return principal * Math.pow(
            1.0 + ratePercentPerYear / 100.0 / DAYS_PER_YEAR,
            elapsed.toDouble()
        )
    }

    fun maturityValue(principal: Double, ratePercentPerYear: Double, tenureYears: Double): Double {
        val days = totalDays(tenureYears)
        return principal * Math.pow(
            1.0 + ratePercentPerYear / 100.0 / DAYS_PER_YEAR,
            days.toDouble()
        )
    }

    /** Approximate growth of the current value in the next day. */
    fun dailyIncrement(currentValue: Double, ratePercentPerYear: Double): Double =
        currentValue * ratePercentPerYear / 100.0 / DAYS_PER_YEAR

    fun isMatured(start: LocalDate, tenureYears: Double, asOf: LocalDate): Boolean =
        !asOf.isBefore(maturityDate(start, tenureYears))
}
