package com.example.salarycalc_trackincomeandexpense

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log


// Reads bank/UPI payment messages from the phone's notifications and adds them
// to the app automatically. Uses Notification Access instead of the SMS
// permission, so the APK installs without Google Play Protect warnings while
// still auto-tracking payment SMS exactly like the SMS-permission version.
class SmsNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "SmsNotificationListener"
        private const val PREFS_NAME = "SmsTrackingPrefs"
        private const val KEY_SMS_ENABLED = "smsTrackingEnabled"

        fun isTrackingEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_SMS_ENABLED, false)
        }

        fun isAccessGranted(context: Context): Boolean {
            val component = ComponentName(context, SmsNotificationListener::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return enabled.split(":").any { it.trim() == component.flattenToString() }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(TAG, "onNotificationPosted pkg=${sbn.packageName} tracking=${isTrackingEnabled(this)}")
        // Never process our own notifications
        if (sbn.packageName == packageName) return
        if (!isTrackingEnabled(this)) return

        try {
            val extras = sbn.notification.extras
            val body = extractText(extras) ?: return
            // For SMS notifications the title is usually the sender (e.g. AD-PAY)
            val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""

            val receiver = SmsReceiver()
            val handled = receiver.processNotificationTransaction(this, body, sender, sbn.postTime)
            if (handled) {
                Log.d(TAG, "Transaction detected and processed from notification")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }

    private fun extractText(extras: Bundle): String? {
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!textLines.isNullOrEmpty()) {
            return textLines.joinToString(" ") { it?.toString() ?: "" }
        }
        return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    }
}
