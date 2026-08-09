package com.example.salarycalc_trackincomeandexpense

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

// Re-schedules all saved notifications after the phone reboots.
// AlarmManager wipes every pending alarm on restart, so this reads
// the list we saved in SharedPreferences (NotificationStore) and
// re-creates each alarm exactly as it was.
class BootReceiver : BroadcastReceiver() {

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        id: Int,
        title: String,
        message: String,
        timestampMillis: Long,
        type: String = "",
        amount: Double = 0.0,
        category: String = ""
    ) {
        val alarmIntent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("notif_id", id)
            putExtra("notif_title", title)
            putExtra("notif_message", message)
            putExtra("trans_type", type)
            putExtra("trans_amount", amount)
            putExtra("trans_category", category)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            timestampMillis,
            pendingIntent
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val savedArray = NotificationStore.getAll(context)

        try {
            for (i in 0 until savedArray.length()) {
                val obj: JSONObject = savedArray.getJSONObject(i)
                val id = obj.getInt("id")
                val title = obj.getString("title")
                val message = obj.getString("message")
                val timestampMillis = obj.getLong("timestamp")
                val type = obj.optString("type", "")
                val amount = obj.optDouble("amount", 0.0)
                val category = obj.optString("category", "")

                // Skip anything whose time has already passed
                if (timestampMillis <= System.currentTimeMillis()) continue

                scheduleAlarm(context, alarmManager, id, title, message, timestampMillis, type, amount, category)
            }
            NotificationStore.removeExpired(context)
        } catch (e: Exception) {
            // If saved data is empty/corrupt, just skip silently
        }
    }
}
