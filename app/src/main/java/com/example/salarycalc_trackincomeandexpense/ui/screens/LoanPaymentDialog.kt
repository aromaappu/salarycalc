package com.example.salarycalc_trackincomeandexpense.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.LoanPaymentRecorder
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.components.FormLabel
import com.example.salarycalc_trackincomeandexpense.ui.components.FormTextField
import com.example.salarycalc_trackincomeandexpense.ui.util.formatCurrency
import com.example.salarycalc_trackincomeandexpense.ui.util.getTodayStr
import java.time.LocalDate

@Composable
fun LoanPaymentDialog(
    db: AppDatabase,
    settings: SettingsStore,
    loanId: Long,
    onDismiss: () -> Unit,
    onRecorded: () -> Unit
) {
    val context = LocalContext.current
    val currency = settings.getCurrencySymbol()

    val loan = remember(loanId) { db.getLoan(loanId) }
    if (loan == null) {
        onDismiss()
        return
    }
    val current = remember(loanId) { LoanPaymentRecorder.nextUnpaid(db, loanId) }
    if (current == null) {
        onDismiss()
        return
    }
    val remainingOnInstallment = (current.expectedPayment - current.paidAmount).coerceAtLeast(0.0)

    var amount by remember(loanId) { mutableStateOf(remainingOnInstallment.toString()) }
    var paymentDate by remember(loanId) { mutableStateOf(getTodayStr()) }
    var method by remember(loanId) { mutableStateOf("") }
    var reference by remember(loanId) { mutableStateOf("") }
    var notes by remember(loanId) { mutableStateOf("") }
    var showDatePicker by remember(loanId) { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun record() {
        val amt = amount.toDoubleOrNull()
        if (amt == null || amt <= 0) {
            toast("Enter a valid amount")
            return
        }
        val result = LoanPaymentRecorder.record(
            context = context,
            db = db,
            loanId = loan.id,
            amount = amt,
            paymentDate = paymentDate,
            method = method.trim(),
            reference = reference.trim(),
            notes = notes.trim()
        )
        toast(result.message)
        onRecorded()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "💳 ${loan.name}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            Column {
                Text(
                    "Due ${formatDisplayDate(current.dueDate)} · " +
                        "Remaining ${formatCurrency(currency, remainingOnInstallment)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "EMI ${formatCurrency(currency, current.expectedPayment)} · " +
                        "Interest ${formatCurrency(currency, current.interest)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                FormLabel("Amount ($currency)")
                FormTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    placeholder = "e.g., 23500",
                    keyboardType = KeyboardType.Number
                )

                Spacer(Modifier.height(8.dp))
                FormLabel("Payment date")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📅 $paymentDate", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(Modifier.height(8.dp))
                FormLabel("Method (optional)")
                FormTextField(
                    value = method,
                    onValueChange = { method = it },
                    placeholder = "e.g., UPI / Cash / Bank"
                )

                Spacer(Modifier.height(8.dp))
                FormLabel("Reference (optional)")
                FormTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    placeholder = "e.g., Txn ID"
                )

                Spacer(Modifier.height(8.dp))
                FormLabel("Notes (optional)")
                FormTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = "e.g., Paid from savings"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { record() }) { Text("Record", color = Color(0xFF10B981)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            initialDate = paymentDate,
            onDismiss = { showDatePicker = false },
            onDateSelected = { paymentDate = it; showDatePicker = false }
        )
    }
}

private fun formatDisplayDate(dateStr: String): String {
    return runCatching {
        val d = LocalDate.parse(dateStr)
        "${d.dayOfMonth} ${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${d.year}"
    }.getOrDefault(dateStr)
}
