package com.example.salarycalc_trackincomeandexpense

import android.content.Context

/**
 * Notification preference switches. Defaults to enabled to preserve behavior
 * for users who upgrade from an earlier version.
 * Disabling an alert never disables recording transactions or scheduling bills.
 */
object NotificationPreferences {
    enum class Kind(val label: String, val description: String) {
        AUTO("Auto income & expense", "Successful scheduled income and expense entries"),
        TRANSACTION("UPI / bank transactions", "Detected, approved and automatically saved transactions"),
        BILL("Bill reminders", "One-time and repeating due-date notifications"),
        LOAN("Loan payments", "Upcoming, due and overdue installments"),
        FD("FD interest", "Monthly estimated fixed-deposit growth"),
        STATEMENT("Monthly statement", "Month-end reminder that your statement is ready to download")
    }

    private const val PREFS = "salarycalc_notification_preferences"

    fun isEnabled(context: Context, kind: Kind): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("notify_${kind.name.lowercase(java.util.Locale.ROOT)}", true)

    fun setEnabled(context: Context, kind: Kind, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("notify_${kind.name.lowercase(java.util.Locale.ROOT)}", enabled).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
