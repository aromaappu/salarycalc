package com.example.salarycalc_trackincomeandexpense.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarycalc_trackincomeandexpense.ReminderScheduler
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.components.FormLabel
import com.example.salarycalc_trackincomeandexpense.ui.components.FormTextField
import com.example.salarycalc_trackincomeandexpense.ui.components.OverlayHeader
import com.example.salarycalc_trackincomeandexpense.ui.util.formatCurrency
import com.example.salarycalc_trackincomeandexpense.ui.util.getTodayStr
import com.example.salarycalc_trackincomeandexpense.ui.util.getYesterdayStr

@Composable
fun RemindersScreen(
    db: AppDatabase,
    settings: SettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currency = settings.getCurrencySymbol()

    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(getTodayStr()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }

    val reminders = remember(db, refreshKey) {
        db.getReminders().sortedBy { it.date }
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun addReminder() {
        val n = name.trim()
        val amt = amount.toDoubleOrNull()
        if (n.isEmpty() || amt == null || amt <= 0) {
            toast("Fill all fields")
            return
        }
        val selectedDate = date
        val id = db.addReminder(n, amt, selectedDate)
        val message = "$n due today: ${formatCurrency(currency, amt)}"
        ReminderScheduler.schedule(
            context,
            id.toInt(),
            "Bill Reminder",
            message,
            selectedDate
        )
        if (selectedDate == getTodayStr()) {
            ReminderScheduler.showInstant(context, id.toInt(), "Bill Reminder", message)
        }
        name = ""
        amount = ""
        date = getTodayStr()
        refreshKey++
        toast("✅ Reminder saved: $n on $selectedDate")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        OverlayHeader("🔔 Bill Reminders", onBack)
        Column(Modifier.padding(10.dp)) {

            FormLabel("Reminder name")
            FormTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "e.g., Rent"
            )

            Spacer(Modifier.height(10.dp))
            FormLabel("Amount ($currency)")
            FormTextField(
                value = amount,
                onValueChange = { amount = it },
                placeholder = "e.g., 15000",
                keyboardType = KeyboardType.Number
            )

            Spacer(Modifier.height(10.dp))
            FormLabel("Date")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📅 $date", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10B981))
                    .clickable { addReminder() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text("✅ Add Reminder", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Saved reminders:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))

            if (reminders.isEmpty()) {
                Text(
                    "No reminders",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            } else {
                reminders.forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🔔 ${r.name} - ${formatCurrency(currency, r.amount)} (${r.date})",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "🗑️",
                            fontSize = 15.sp,
                            modifier = Modifier.clickable { deleteTarget = r.id }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onDateSelected = { date = it; showDatePicker = false }
        )
    }

    deleteTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete reminder?") },
            text = { Text("Delete this reminder?") },
            confirmButton = {
                TextButton(onClick = {
                    db.deleteReminder(id)
                    ReminderScheduler.cancel(context, id.toInt())
                    refreshKey++
                    deleteTarget = null
                    toast("🗑️ Reminder deleted")
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}
