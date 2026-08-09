package com.example.salarycalc_trackincomeandexpense

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.salarycalc_trackincomeandexpense.MainActivity
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    const val CHANNEL_ID = "salarycalc_reminders"
    private const val CHANNEL_NAME = "SalaryCalc Reminders"
    private const val CHANNEL_DESC = "Auto Income, Auto Expense, and Bill Reminder notifications"

    // Call this once when the app starts (Android 8+ requires a channel before notifying)
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // Builds and shows the actual notification in the system tray
    fun showNotification(context: Context, id: Int, title: String, message: String) {
        createChannel(context)

        // Create intent to open MainActivity when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, builder.build())
    }

    // Show notification for auto-detected transaction
    fun showTransactionNotification(context: Context, transaction: SmsReceiver.TransactionData) {
        val title = if (transaction.transactionType == "credit") "💰 Income Detected" else "📉 Expense Detected"
        val message = "${transaction.merchant.ifEmpty { transaction.category }}: ₹${transaction.amount}"
        showNotification(context, 9999, title, message)
    }
}
