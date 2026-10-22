package com.example.salarycalc_trackincomeandexpense

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log


// Processes notifications only from supported payment apps and the default SMS
// app, after the user accepts the disclosure and enables tracking.
class SmsNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "SmsNotificationListener"
        private const val PREFS_NAME = "SmsTrackingPrefs"
        private const val KEY_SMS_ENABLED = "smsTrackingEnabled"
        const val KEY_DISCLOSURE_ACCEPTED = "notificationDisclosureAcceptedV1"

        // Exact package names only; filter before accessing notification text.
        private val PAYMENT_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp"
        )

        fun isSupportedSource(packageName: String, defaultSmsPackage: String?): Boolean =
            packageName == defaultSmsPackage || packageName in PAYMENT_PACKAGES

        fun isTrackingEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_SMS_ENABLED, false) &&
                prefs.getBoolean(KEY_DISCLOSURE_ACCEPTED, false)
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
        if (sbn.packageName == packageName) return
        if (!isTrackingEnabled(this)) return

        try {
            if (!isSupportedSource(sbn.packageName, Telephony.Sms.getDefaultSmsPackage(this))) return
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
