package com.example.salarycalc_trackincomeandexpense

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.salarycalc_trackincomeandexpense.MainActivity
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    const val EXTRA_OPEN_STATEMENT = "salarycalc_open_statement"
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
    fun showNotification(
        context: Context,
        id: Int,
        title: String,
        message: String,
        kind: NotificationPreferences.Kind = classify(title),
        openStatement: Boolean = false
    ) {
        if (!NotificationPreferences.isEnabled(context, kind)) return
        createChannel(context)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        // Create intent to open MainActivity when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_STATEMENT, openStatement)
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

    private fun classify(title: String): NotificationPreferences.Kind {
        val t = title.lowercase(java.util.Locale.ROOT)
        return when {
            t.startsWith("loan ") -> NotificationPreferences.Kind.LOAN
            t.startsWith("bill ") -> NotificationPreferences.Kind.BILL
            t.startsWith("fd ") -> NotificationPreferences.Kind.FD
            t.startsWith("auto income") || t.startsWith("auto expense") -> NotificationPreferences.Kind.AUTO
            else -> NotificationPreferences.Kind.TRANSACTION
        }
    }

    /** Distinguish successful auto-add from a transaction waiting for approval. */
    fun showTransactionNotification(
        context: Context,
        transaction: SmsReceiver.TransactionData,
        needsApproval: Boolean = true
    ) {
        val kind = if (transaction.transactionType == "credit") "Income" else "Expense"
        val title = if (needsApproval) "$kind detected — approve to add"
            else "$kind added successfully"
        val message = "${transaction.merchant.ifEmpty { transaction.category }}: ₹${transaction.amount}"
        val id = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        showNotification(context, id, title, message, NotificationPreferences.Kind.TRANSACTION)
    }
}
