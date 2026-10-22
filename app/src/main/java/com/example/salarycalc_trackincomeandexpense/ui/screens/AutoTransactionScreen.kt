package com.example.salarycalc_trackincomeandexpense.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.salarycalc_trackincomeandexpense.SmsImport
import com.example.salarycalc_trackincomeandexpense.SmsNotificationListener
import com.example.salarycalc_trackincomeandexpense.SmsReceiver
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.components.OverlayHeader

private const val SMS_PREFS = "SmsTrackingPrefs"
private const val KEY_SMS_ENABLED = "smsTrackingEnabled"
private const val KEY_CONFIRMATION_ENABLED = "smsConfirmationEnabled"

@Composable
fun AutoTransactionScreen(
    db: AppDatabase,
    settings: SettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SMS_PREFS, Context.MODE_PRIVATE) }

    var refreshKey by remember { mutableIntStateOf(0) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showTrackingDisclosure by remember { mutableStateOf(false) }
    var trackingEnabled by remember { mutableStateOf(SmsNotificationListener.isTrackingEnabled(context)) }
    var confirmationEnabled by remember { mutableStateOf(prefs.getBoolean(KEY_CONFIRMATION_ENABLED, false)) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val pending = remember(db, trackingEnabled, confirmationEnabled, refreshKey) {
        if (!trackingEnabled) emptyList() else SmsReceiver().getPendingTransactionsPublic(context)
    }

    fun openNotificationAccess() {
        if (SmsNotificationListener.isAccessGranted(context)) {
            toast("✅ Notification Access already granted")
        } else {
            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
    }

    fun enableTracking() {
        prefs.edit()
            .putBoolean(SmsNotificationListener.KEY_DISCLOSURE_ACCEPTED, true)
            .putBoolean(KEY_SMS_ENABLED, true)
            .apply()
        trackingEnabled = true
        refreshKey++
        toast("Allow Notification Access in Settings to start auto tracking")
        if (!SmsNotificationListener.isAccessGranted(context)) {
            openNotificationAccess()
        }
    }

    fun disableTracking() {
        prefs.edit().putBoolean(KEY_SMS_ENABLED, false).apply()
        trackingEnabled = false
        refreshKey++
    }

    fun toggleTracking() {
        if (trackingEnabled) {
            disableTracking()
        } else {
            showTrackingDisclosure = true
        }
    }

    fun toggleConfirmation() {
        confirmationEnabled = !confirmationEnabled
        prefs.edit().putBoolean(KEY_CONFIRMATION_ENABLED, confirmationEnabled).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        OverlayHeader("📱 Auto Transaction", onBack)
        Column(Modifier.padding(10.dp)) {

            ToggleRow(
                icon = "📱",
                iconBg = Color(0xFFF97316),
                title = "Auto Transactions",
                subtitle = "Automatically detect bank/UPI transactions",
                enabled = trackingEnabled,
                onClick = { toggleTracking() }
            )

            ToggleRow(
                icon = "🔔",
                iconBg = Color(0xFFFACC15),
                title = "Show Confirmation Popup",
                subtitle = "Ask before adding each transaction",
                enabled = confirmationEnabled,
                active = confirmationEnabled,
                dimmed = !trackingEnabled,
                onClick = { toggleConfirmation() }
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF667EEA))
                    .clickable {
                        if (trackingEnabled) openNotificationAccess()
                        else showTrackingDisclosure = true
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text("🔔 Allow Notification Access", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Optional: read payment notifications from your default SMS app, Google Pay, PhonePe, Paytm and BHIM to save transactions locally. Notification Access must be allowed in Settings. Turn off Auto Transactions here to stop processing; revoke Notification Access in Android Settings to remove access.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2563EB))
                    .clickable { showImportDialog = true }
                    .padding(vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text("📥 Import SMS Manually", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Tip: open any bank SMS and tap Share → SalaryCalc, or paste it below.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
            )

            if (pending.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "Pending transactions:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                pending.forEachIndexed { i, tx ->
                    val txType = if (tx.transactionType == "credit") "Income" else "Expense"
                    val txName = tx.merchant.ifEmpty { tx.category }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "$txType: ${tx.amount} - $txName",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${tx.bank.ifEmpty { tx.paymentMethod }} on ${tx.date}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "✅",
                            fontSize = 16.sp,
                            modifier = Modifier.clickable {
                                SmsReceiver().acceptPendingTransaction(context, i)
                                refreshKey++
                                toast("✅ Transaction added")
                            }
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "❌",
                            fontSize = 16.sp,
                            modifier = Modifier.clickable {
                                SmsReceiver().rejectPendingTransaction(context, i)
                                refreshKey++
                                toast("Transaction rejected")
                            }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }

    if (showTrackingDisclosure) {
        AlertDialog(
            onDismissRequest = { showTrackingDisclosure = false },
            title = { Text("Allow payment notification processing?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Android Notification Access can expose notifications from other apps. " +
                            "SalaryCalc filters sources before reading text: only your default SMS app, " +
                            "Google Pay, PhonePe, Paytm and BHIM are supported. Notifications from those " +
                            "sources can include non-payment messages. Their text and sender are processed " +
                            "on your device to detect payments, including while SalaryCalc is closed. " +
                            "Detected amounts, dates and transaction details are stored locally; this " +
                            "feature does not send notification content to a server.\n\n" +
                            "This is optional. You can enter transactions or import an SMS manually instead. " +
                            "Turn off Auto Transactions to stop processing, or revoke Notification Access " +
                            "in Android Settings. Existing saved transactions remain until you delete them."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showTrackingDisclosure = false
                    enableTracking()
                }) { Text("Agree and continue") }
            },
            dismissButton = {
                TextButton(onClick = { showTrackingDisclosure = false }) { Text("Not now") }
            }
        )
    }

    // Non-dismissable confirmation modal: only Accept/Reject closes it. Shown
    // for each pending transaction in order when confirmation is ON. Uses a
    // plain Dialog + Surface so it always renders as a visible light card.
    if (trackingEnabled && confirmationEnabled && pending.isNotEmpty()) {
        val tx = pending.first()
        val txType = if (tx.transactionType == "credit") "Income" else "Expense"
        val txName = tx.merchant.ifEmpty { tx.category }
        Dialog(
            onDismissRequest = { /* modal: only Accept/Reject closes it */ },
            properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Confirm $txType",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${tx.amount} - $txName",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${tx.bank.ifEmpty { tx.paymentMethod }} on ${tx.date}",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Add this transaction?", fontSize = 13.sp, color = Color(0xFF374151))
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                SmsReceiver().rejectPendingTransaction(context, 0)
                                refreshKey++
                                toast("Transaction rejected")
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                        ) { Text("Reject") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                SmsReceiver().acceptPendingTransaction(context, 0)
                                refreshKey++
                                toast("✅ Transaction added")
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF059669))
                        ) { Text("Accept") }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        SmsImportDialog(
            db = db,
            initialText = "",
            onDismiss = { showImportDialog = false },
            onImported = {
                showImportDialog = false
                refreshKey++
            }
        )
    }
}

@Composable
private fun ToggleRow(
    icon: String,
    iconBg: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    active: Boolean = enabled,
    dimmed: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = !dimmed, onClick = onClick)
            .alpha(if (dimmed) 0.4f else 1f)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 17.sp)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(26.dp)
                .background(if (active) Color(0xFF10B981) else Color(0xFFD1D5DB)),
            contentAlignment = if (active) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(20.dp)
                    .background(Color.White)
            )
        }
    }
}

// Dialog used both by the Manual Import button and when an SMS is shared into
// the app (ACTION_SEND). Works in every build — no SMS permission needed.
@Composable
fun SmsImportDialog(
    db: AppDatabase,
    initialText: String,
    onDismiss: () -> Unit,
    onImported: () -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialText) }
    val parsed = remember(text) { SmsImport.parse(text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📥 Import SMS") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text("Paste the bank/UPI SMS here...") }
                )
                if (text.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    val tx = parsed
                    if (tx != null) {
                        val txType = if (tx.transactionType == "credit") "Income" else "Expense"
                        val txName = tx.merchant.ifEmpty { tx.category }
                        Text(
                            "Detected: $txType · ${tx.amount} · $txName",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            "⚠️ Could not detect a transaction in this message.",
                            fontSize = 12.sp,
                            color = Color(0xFFB45309)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = {
                    val tx = parsed
                    if (tx != null && SmsImport.addToDatabase(db, tx)) {
                        Toast.makeText(context, "✅ Transaction added", Toast.LENGTH_SHORT).show()
                        onImported()
                    } else {
                        Toast.makeText(context, "Could not add transaction", Toast.LENGTH_SHORT).show()
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
