package com.example.salarycalc_trackincomeandexpense

import androidx.compose.runtime.mutableIntStateOf

// Single source of truth for the auto-transaction broadcast actions and a
// process-wide refresh tick. SmsReceiver sends the broadcast after adding a
// transaction; MainActivity registers a receiver that bumps this tick so the
// Compose screens (Income/Expense/Charts/Dashboard) recompute and show the
// newly added transaction immediately.
object AutoEvents {
    const val ACTION_NEW_SMS_TRANSACTION = "com.example.salarycalc_trackincomeandexpense.NEW_SMS_TRANSACTION"
    const val ACTION_AUTO_TRANSACTION = "com.example.salarycalc_trackincomeandexpense.AUTO_TRANSACTION"

    val refreshTick = mutableIntStateOf(0)
}
