package com.example.salarycalc_trackincomeandexpense

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("notif_id", 0)
        val title = intent.getStringExtra("notif_title") ?: "SalaryCalc"
        val message = intent.getStringExtra("notif_message") ?: ""

        val type = intent.getStringExtra("trans_type") ?: ""
        val amount = intent.getDoubleExtra("trans_amount", 0.0)
        val category = intent.getStringExtra("trans_category") ?: ""

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (type.isNotEmpty() && amount > 0) {
            val autoAddIntent = Intent("com.example.salarycalc_trackincomeandexpense.AUTO_TRANSACTION").apply {
                putExtra("type", type)
                putExtra("amount", amount)
                putExtra("name", category)
                putExtra("date", todayStr)
            }
            context.sendBroadcast(autoAddIntent)
            PendingAutoTransactionStore.add(context, type, amount, category, todayStr)
        }

        val appContext = context.applicationContext
        NotificationHelper.showNotification(appContext, id, title, message)
        NotificationStore.remove(appContext, id)
        // Only database-backed bill reminders recur; loan alarms remain one-off.
        if (title == "Bill Reminder") {
            ReminderScheduler.scheduleNextMonthly(appContext, id.toLong())
        }
        NotificationStore.removeExpired(appContext)
    }
}
