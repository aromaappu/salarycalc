package com.example.salarycalc_trackincomeandexpense

import android.app.Notification
import android.content.ComponentName
import android.content.Context
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
            val notification = sbn.notification
            // Group summaries merge multiple transactions and must not become a payment.
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val trustedPaymentApp = sbn.packageName in PAYMENT_PACKAGES
            val receiver = SmsReceiver()
            val messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                extras.getParcelableArray("android.messages")
            )
            if (messages.isNotEmpty()) {
                messages.forEach { message ->
                    val body = message.text?.toString() ?: return@forEach
                    receiver.processNotificationTransaction(
                        this, body, title, message.timestamp.takeIf { it > 0 } ?: sbn.postTime,
                        sbn.packageName, trustedPaymentApp, if (trustedPaymentApp) title else ""
                    )
                }
            } else {
                // Prefer expanded text; do not concatenate an InboxStyle history into one payment.
                val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: return
                receiver.processNotificationTransaction(
                    this, body, title, sbn.postTime, sbn.packageName,
                    trustedPaymentApp, if (trustedPaymentApp) title else ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }
}
