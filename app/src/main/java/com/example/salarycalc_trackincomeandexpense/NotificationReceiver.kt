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

        // Ensure channel exists (Android 8+)
        NotificationHelper.createChannel(appContext)

        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext, id, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, builder.build())

        NotificationStore.remove(appContext, id)
        NotificationStore.removeExpired(appContext)
    }
}
