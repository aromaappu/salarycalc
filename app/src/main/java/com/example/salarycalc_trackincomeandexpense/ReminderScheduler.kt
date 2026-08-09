package com.example.salarycalc_trackincomeandexpense

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Schedules and cancels bill-reminder alarms via AlarmManager + NotificationReceiver.
// Mirrors BootReceiver's scheduling so alarms survive reboots (re-scheduled there).
object ReminderScheduler {

    private const val REMINDER_HOUR = 9
    private const val REMINDER_MINUTE = 0

    fun schedule(context: Context, id: Int, title: String, message: String, date: String) {
        val timestamp = reminderTimestamp(date) ?: return
        if (timestamp <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("notif_id", id)
            putExtra("notif_title", title)
            putExtra("notif_message", message)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pendingIntent)

        NotificationStore.add(context, id, title, message, timestamp)
    }

    fun cancel(context: Context, id: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("notif_id", id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        NotificationStore.remove(context, id)
    }

    // Shows an instant notification when a reminder is due today (matches original addReminder())
    fun showInstant(context: Context, id: Int, title: String, message: String) {
        NotificationHelper.showNotification(context, id, title, message)
    }

    private fun reminderTimestamp(date: String): Long? {
        val parts = date.split("-")
        if (parts.size < 3) return null
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(
            parts[0].toIntOrNull() ?: return null,
            (parts[1].toIntOrNull() ?: return null) - 1,
            parts[2].toIntOrNull() ?: return null,
            REMINDER_HOUR,
            REMINDER_MINUTE,
            0
        )
        return cal.timeInMillis
    }
}
