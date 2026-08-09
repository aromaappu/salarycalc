package com.example.salarycalc_trackincomeandexpense.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarycalc_trackincomeandexpense.AutoEvents
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.util.formatCurrency
import java.util.Calendar

@Composable
fun AutoEntryDialog(
    isIncome: Boolean,
    db: AppDatabase,
    settings: SettingsStore,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currency = settings.getCurrencySymbol()

    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var nameFieldFocused by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val names = remember(db, refreshKey) {
        if (isIncome) db.getIncomeSources() else db.getExpenseCategories()
    }
    val autoIncomes = remember(db, refreshKey) { db.getAutoIncomes() }
    val autoExpenses = remember(db, refreshKey) { db.getAutoExpenses() }
    val entries: List<Any> = if (isIncome) autoIncomes else autoExpenses

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun clearForm() {
        name = ""
        amount = ""
        day = ""
        editingId = null
    }

    fun save() {
        val n = name.trim()
        val amt = amount.toDoubleOrNull()
        val parsedDay = day.toIntOrNull() ?: 1
        val clampedDay = parsedDay.coerceIn(1, 28)
        if (n.isEmpty() || amt == null || amt <= 0) {
            toast(if (isIncome) "Enter valid source and amount" else "Enter valid category and amount")
            return
        }
        if (isIncome) {
            if (!db.getIncomeSources().contains(n)) db.addIncomeSource(n)
            if (editingId != null) db.deleteAutoIncome(editingId!!)
            db.addAutoIncome(n, amt, clampedDay)
        } else {
            if (!db.getExpenseCategories().contains(n)) db.addExpenseCategory(n)
            if (editingId != null) db.deleteAutoExpense(editingId!!)
            db.addAutoExpense(n, amt, clampedDay)
        }
        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_MONTH)
        if (clampedDay == currentDay) {
            db.addTransaction(
                type = if (isIncome) AppDatabase.TYPE_INCOME else AppDatabase.TYPE_EXPENSE,
                amount = amt,
                name = n,
                date = "%04d-%02d-%02d".format(
                    now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, clampedDay
                )
            )
        }
        clearForm()
        refreshKey++
        AutoEvents.refreshTick.intValue++
        toast(if (isIncome) "✅ Auto income saved" else "✅ Auto expense saved")
    }

    fun onDeletePressed() {
        if (editingId == null) {
            confirmClear = true
        } else {
            confirmDelete = editingId
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isIncome) "💰 Auto Income" else "📉 Auto Expense") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // ===== FORM =====
                Text(
                    if (isIncome) "Type" else "Category",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth()) {
                    FormTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Start typing or select",
                        modifier = Modifier.onFocusChanged { nameFieldFocused = it.isFocused }
                    )
                    if (nameFieldFocused && name.isNotBlank()) {
                        val matches = names.filter { it.contains(name, ignoreCase = true) }
                        if (matches.isNotEmpty()) {
                            DropdownMenu(
                                expanded = true,
                                onDismissRequest = { nameFieldFocused = false }
                            ) {
                                matches.forEach { n ->
                                    DropdownMenuItem(
                                        text = { Text(n) },
                                        onClick = { name = n; nameFieldFocused = false }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "Amount ($currency)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                FormTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    placeholder = "e.g., ${if (isIncome) "50000" else "15000"}",
                    keyboardType = KeyboardType.Number
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    "Day of month",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                FormTextField(
                    value = day,
                    onValueChange = { day = it },
                    placeholder = "1",
                    keyboardType = KeyboardType.Number
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFEF4444))
                            .clickable { onDeletePressed() }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🗑️ Delete", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF10B981))
                            .clickable { save() }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💾 Save", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    if (isIncome) "Existing auto incomes:" else "Existing auto expenses:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                if (entries.isEmpty()) {
                    Text(
                        "No auto incomes set",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    entries.forEach { e ->
                        val label = if (isIncome) {
                            val inc = e as com.example.salarycalc_trackincomeandexpense.data.AutoIncome
                            "${inc.source} - ${formatCurrency(currency, inc.amount)} (Day ${inc.dayOfMonth})"
                        } else {
                            val exp = e as com.example.salarycalc_trackincomeandexpense.data.AutoExpense
                            "${exp.category} - ${formatCurrency(currency, exp.amount)} (Day ${exp.dayOfMonth})"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    if (isIncome) {
                                        val inc = e as com.example.salarycalc_trackincomeandexpense.data.AutoIncome
                                        name = inc.source
                                        amount = inc.amount.toString()
                                        day = inc.dayOfMonth.toString()
                                        editingId = inc.id
                                    } else {
                                        val exp = e as com.example.salarycalc_trackincomeandexpense.data.AutoExpense
                                        name = exp.category
                                        amount = exp.amount.toString()
                                        day = exp.dayOfMonth.toString()
                                        editingId = exp.id
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                label,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(5.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete?") },
            text = {
                Text(
                    if (isIncome) "Delete this auto income entry?" else "Delete this auto expense entry?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isIncome) db.deleteAutoIncome(id) else db.deleteAutoExpense(id)
                    name = ""
                    amount = ""
                    day = ""
                    editingId = null
                    refreshKey++
                    confirmDelete = null
                    toast(if (isIncome) "🗑️ Auto income deleted" else "🗑️ Auto expense deleted")
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear this form?") },
            text = { Text("Clear this form?") },
            confirmButton = {
                TextButton(onClick = {
                    clearForm()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}
