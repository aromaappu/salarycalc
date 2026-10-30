package com.example.salarycalc_trackincomeandexpense

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.AutoEntries
import com.example.salarycalc_trackincomeandexpense.data.FdMath
import com.example.salarycalc_trackincomeandexpense.data.FdStatus
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Works while the app is closed. WorkManager is battery-aware: reminders are
 * approximate and can be delayed by Android's power restrictions.
 */
class DailyFinanceWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val db = AppDatabase(applicationContext)
        try {
            AutoEntries.applyDue(db, SettingsStore(applicationContext), applicationContext)
            notifyEstimatedFdGrowth(db)
            notifyMonthlyStatement()
        } finally {
            db.close()
        }
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }

    private fun notifyEstimatedFdGrowth(db: AppDatabase) {
        val prefs = applicationContext.getSharedPreferences("salarycalc_fd_notices", Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val previousMonth = YearMonth.from(today).minusMonths(1)
        val periodKey = previousMonth.toString()
        val fromMonth = previousMonth.atDay(1)
        val until = previousMonth.plusMonths(1).atDay(1)
        db.getFds(FdStatus.ACTIVE).forEach { fd ->
            val started = runCatching { LocalDate.parse(fd.startDate) }.getOrNull() ?: return@forEach
            val from = if (started.isAfter(fromMonth)) started else fromMonth
            if (!from.isBefore(until)) return@forEach
            val key = "${fd.id}_${periodKey}"
            if (prefs.getBoolean(key, false)) return@forEach
            val increment = FdMath.currentValue(
                fd.principal, fd.interestRate, started, until, fd.tenureYears
            ) - FdMath.currentValue(
                fd.principal, fd.interestRate, started, from, fd.tenureYears
            )
            if (increment <= 0.004) return@forEach
            NotificationHelper.showNotification(
                applicationContext, (fd.id % 100000).toInt() + 800000,
                "FD interest estimate — ${fd.name}",
                "Estimated growth last month: ₹${String.format(java.util.Locale.US, "%.2f", increment)}"
            )
            prefs.edit().putBoolean(key, true).apply()
        }
    }

    /**
     * On the 1st of each month, remind the user that the previous month's
     * statement is ready. Fires once per month even if the worker runs again.
     */
    private fun notifyMonthlyStatement() {
        val today = LocalDate.now()
        if (today.dayOfMonth != 1) return
        val previousMonth = YearMonth.from(today).minusMonths(1)
        val prefs = applicationContext.getSharedPreferences(
            "salarycalc_statement_notices", Context.MODE_PRIVATE
        )
        val key = previousMonth.toString()
        if (prefs.getBoolean(key, false)) return
        val monthName = previousMonth.month.getDisplayName(
            java.time.format.TextStyle.FULL, java.util.Locale.getDefault()
        )
        val id = 900000 + (previousMonth.year % 100) * 12 + previousMonth.monthValue
        NotificationHelper.showNotification(
            applicationContext,
            id,
            "Download $monthName statement",
            "Your $monthName ${previousMonth.year} statement is ready. Tap to open and download it.",
            NotificationPreferences.Kind.STATEMENT,
            openStatement = true
        )
        prefs.edit().putBoolean(key, true).apply()
    }

    companion object {
        fun ensureScheduled(context: Context) {
            val now = java.time.ZonedDateTime.now()
            val next = now.toLocalDate().atTime(9, 0).atZone(ZoneId.systemDefault())
                .let { if (it.isAfter(now)) it else it.plusDays(1) }
            val initialMinutes = java.time.Duration.between(now, next).toMinutes().coerceAtLeast(1)
            val work = PeriodicWorkRequestBuilder<DailyFinanceWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialMinutes, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "salarycalc_daily_finance", ExistingPeriodicWorkPolicy.KEEP, work
            )
        }
    }
}
