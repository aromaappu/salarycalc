package com.example.salarycalc_trackincomeandexpense.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.core.content.FileProvider
import com.example.salarycalc_trackincomeandexpense.AutoEvents
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.data.Transaction
import com.example.salarycalc_trackincomeandexpense.ui.screens.DatePickerDialog
import java.io.File

// ==== Receipt open (equivalent of downloadReceipt in core.js) ====
fun openReceipt(context: Context, transaction: Transaction) {
    val path = transaction.receiptPath
    if (path.isNullOrBlank()) {
        Toast.makeText(context, "❌ No receipt attached", Toast.LENGTH_SHORT).show()
        return
    }
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "❌ No receipt attached", Toast.LENGTH_SHORT).show()
        return
    }
    val mime = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        else -> "image/*"
    }
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open receipt"))
    } catch (e: Exception) {
        Toast.makeText(context, "❌ No app to open this receipt", Toast.LENGTH_SHORT).show()
    }
}

// ==== Delete with auto-income/expense month tracking (delTx in core.js) ====
fun deleteTransactionWithAutoTracking(
    db: AppDatabase,
    settings: SettingsStore,
    transaction: Transaction
) {
    val monthKey = transaction.date.split("-").let { parts ->
        if (parts.size >= 2) "${parts[0]}-${parts[1].toInt() - 1}" else ""
    }
    if (transaction.type == AppDatabase.TYPE_INCOME) {
        val isAuto = db.getAutoIncomes().any { it.source == transaction.name }
        if (isAuto && monthKey.isNotBlank()) {
            val deleteKey = "${transaction.name}_$monthKey"
            val list = settings.getDeletedAutoIncomeMonths()
            if (!list.contains(deleteKey)) {
                settings.setDeletedAutoIncomeMonths(list + deleteKey)
            }
        }
    } else {
        val isAuto = db.getAutoExpenses().any { it.category == transaction.name }
        if (isAuto && monthKey.isNotBlank()) {
            val deleteKey = "${transaction.name}_$monthKey"
            val list = settings.getDeletedAutoExpenseMonths()
            if (!list.contains(deleteKey)) {
                settings.setDeletedAutoExpenseMonths(list + deleteKey)
            }
        }
    }
    db.deleteTransaction(transaction.id)
    AutoEvents.refreshTick.intValue++
}

// ==== Edit transaction modal (editTx + saveEditTx in core.js) ====
@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    db: AppDatabase,
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val currency = settings.getCurrencySymbol()
    val isIncome = transaction.type == AppDatabase.TYPE_INCOME

    var amount by remember { mutableStateOf(transaction.amount.toString()) }
    var name by remember { mutableStateOf(transaction.name) }
    var date by remember { mutableStateOf(transaction.date) }
    var receiptName by remember { mutableStateOf(transaction.receiptName) }
    var receiptPath by remember { mutableStateOf(transaction.receiptPath) }
    var showCalc by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val options = remember(db, transaction.id) {
        val list = if (isIncome) db.getIncomeSources() else db.getExpenseCategories()
        if (list.contains(transaction.name)) list else list + transaction.name
    }
    val pickReceipt = rememberReceiptPicker { picked ->
        receiptName = picked.name
        receiptPath = picked.path
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✏️ Edit Transaction") },
        text = {
            Column {
                Text(
                    if (isIncome) "Income" else "Expense",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                Text(
                    "Amount ($currency)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = amount,
                        onValueChange = { amount = it },
                        placeholder = { Text("0", fontSize = 14.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF667EEA))
                            .clickable { showCalc = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("🧮", fontSize = 18.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))
                FilterDropdown(
                    if (isIncome) "Source" else "Category",
                    options,
                    name,
                    { name = it }
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    "Date",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Text(date, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "Receipt (Optional)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF667EEA))
                        .clickable { pickReceipt() }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📎 Attach Receipt", color = Color.White, fontSize = 12.sp)
                }
                if (receiptName != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            receiptName!!,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEF4444))
                                .clickable {
                                    receiptName = null
                                    receiptPath = null
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("🗑️ Remove", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amount.toDoubleOrNull()
                if (amt == null || amt <= 0) {
                    Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                db.updateTransaction(
                    id = transaction.id,
                    type = transaction.type,
                    amount = amt,
                    name = name,
                    date = date,
                    receiptName = receiptName,
                    receiptPath = receiptPath
                )
                AutoEvents.refreshTick.intValue++
                onSaved()
                onDismiss()
            }) { Text("💾 Save Changes") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showCalc) {
        CalculatorDialog(
            onDismiss = { showCalc = false },
            onUseAmount = { result -> amount = result }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onDateSelected = { date = it; showDatePicker = false }
        )
    }
}
