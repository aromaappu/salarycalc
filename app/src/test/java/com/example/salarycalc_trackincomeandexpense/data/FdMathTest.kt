package com.example.salarycalc_trackincomeandexpense.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FdMathTest {

    private val start = LocalDate.of(2026, 1, 1)

    @Test
    fun currentValueEqualsPrincipalAtDayZero() {
        assertEquals(100000.0, FdMath.currentValue(100000.0, 7.5, start, start, 3.0), 0.001)
    }

    @Test
    fun valueGrowsWithElapsedDays() {
        val day0 = FdMath.currentValue(100000.0, 7.5, start, start, 3.0)
        val day30 = FdMath.currentValue(100000.0, 7.5, start, start.plusDays(30), 3.0)
        assertTrue(day30 > day0)
    }

    @Test
    fun valueGrowsBeforeStartDateIsClampedToPrincipal() {
        val before = FdMath.currentValue(100000.0, 7.5, start, start.minusDays(10), 3.0)
        assertEquals(100000.0, before, 0.001)
    }

    @Test
    fun maturityValueCompoundsDaily() {
        // 1-year FD at 7.3% p.a. -> P * (1 + 0.073/365)^365
        val mv = FdMath.maturityValue(100000.0, 7.3, 1.0)
        val expected = 100000.0 * Math.pow(1 + 0.073 / 365.0, 365.0)
        assertEquals(expected, mv, 0.01)
    }

    @Test
    fun maturityValueMatchesCurrentValueAtMaturityDate() {
        val mv = FdMath.maturityValue(50000.0, 6.5, 2.0)
        val cv = FdMath.currentValue(50000.0, 6.5, start, start.plusDays(730), 2.0)
        assertEquals(mv, cv, 0.001)
    }

    @Test
    fun currentValueCapsAtMaturityValueAfterTenure() {
        val mv = FdMath.maturityValue(50000.0, 6.5, 2.0)
        val later = FdMath.currentValue(50000.0, 6.5, start, start.plusDays(1000), 2.0)
        assertEquals(mv, later, 0.001)
    }

    @Test
    fun dailyIncrementScalesWithValue() {
        val d1 = FdMath.dailyIncrement(100000.0, 7.3)
        val d2 = FdMath.dailyIncrement(200000.0, 7.3)
        assertTrue(d1 > 0)
        assertEquals(d1 * 2, d2, 0.001)
    }

    @Test
    fun isMaturedExactlyAtMaturityDate() {
        val maturity = start.plusDays(FdMath.totalDays(2.0))
        assertFalse(FdMath.isMatured(start, 2.0, maturity.minusDays(1)))
        assertTrue(FdMath.isMatured(start, 2.0, maturity))
    }

    @Test
    fun daysRemainingHitsZeroAtMaturity() {
        assertEquals(365, FdMath.daysRemaining(start, start, 1.0))
        assertEquals(0, FdMath.daysRemaining(start, start.plusDays(365), 1.0))
        assertEquals(0, FdMath.daysRemaining(start, start.plusDays(500), 1.0))
    }

    @Test
    fun zeroRateKeepsValueEqualToPrincipal() {
        assertEquals(100000.0, FdMath.maturityValue(100000.0, 0.0, 5.0), 0.001)
        assertEquals(100000.0, FdMath.currentValue(100000.0, 0.0, start, start.plusDays(500), 5.0), 0.001)
    }

    @Test
    fun totalDaysRoundsTenureInYears() {
        assertEquals(365, FdMath.totalDays(1.0))
        assertEquals(547, FdMath.totalDays(1.5))
    }

    @Test
    fun maturityDateAddsTenureDays() {
        assertEquals(LocalDate.of(2028, 1, 1), FdMath.maturityDate(start, 2.0))
    }
}
