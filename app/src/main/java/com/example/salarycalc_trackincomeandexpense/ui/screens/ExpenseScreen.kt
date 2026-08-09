package com.example.salarycalc_trackincomeandexpense.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.data.Transaction
import com.example.salarycalc_trackincomeandexpense.ui.components.CalculatorDialog
import com.example.salarycalc_trackincomeandexpense.ui.components.EditTransactionDialog
import com.example.salarycalc_trackincomeandexpense.ui.components.FilterDropdown
import com.example.salarycalc_trackincomeandexpense.ui.components.FormLabel
import com.example.salarycalc_trackincomeandexpense.ui.components.FormTextField
import com.example.salarycalc_trackincomeandexpense.ui.components.ItemsManagerDialog
import com.example.salarycalc_trackincomeandexpense.ui.components.PickedReceipt
import com.example.salarycalc_trackincomeandexpense.ui.components.SuggestionField
import com.example.salarycalc_trackincomeandexpense.ui.components.TransactionRow
import com.example.salarycalc_trackincomeandexpense.ui.components.deleteTransactionWithAutoTracking
import com.example.salarycalc_trackincomeandexpense.ui.components.openReceipt
import com.example.salarycalc_trackincomeandexpense.ui.components.rememberReceiptPicker
import com.example.salarycalc_trackincomeandexpense.ui.util.MONTHS
import com.example.salarycalc_trackincomeandexpense.AutoEvents
import com.example.salarycalc_trackincomeandexpense.ui.util.formatCurrency
import com.example.salarycalc_trackincomeandexpense.ui.util.getMonthKey
import com.example.salarycalc_trackincomeandexpense.ui.util.getTodayStr
import com.example.salarycalc_trackincomeandexpense.ui.util.getYesterdayStr
import com.example.salarycalc_trackincomeandexpense.ui.util.monthNameFor
import com.example.salarycalc_trackincomeandexpense.ui.util.transactionDisplayName
import java.util.Calendar

@Composable
fun ExpenseScreen(
    db: AppDatabase,
    settings: SettingsStore,
    onExpenseAdded: () -> Unit = {}
) {
    val context = LocalContext.current
    val currency = settings.getCurrencySymbol()
    val scope = rememberCoroutineScope()

    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(getTodayStr()) }
    var receipt by remember { mutableStateOf<PickedReceipt?>(null) }
    var showCalc by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var categoryFieldFocused by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var deleteTarget by remember { mutableStateOf<Transaction?>(null) }

    var filterYear by remember { mutableStateOf("All Years") }
    var filterMonth by remember { mutableStateOf("All Months") }
    var filterCategory by remember { mutableStateOf("All Categories") }
    var refreshKey by remember { mutableIntStateOf(0) }

    var categories by remember { mutableStateOf(listOf<String>()) }
    var filtered by remember { mutableStateOf(listOf<Transaction>()) }
    val pickReceipt = rememberReceiptPicker { picked -> receipt = picked }

    LaunchedEffect(db, filterYear, filterMonth, filterCategory, refreshKey, AutoEvents.refreshTick.value) {
        val result = withContext(Dispatchers.IO) {
            Pair(db.getExpenseCategories(), filterExpenses(db, filterYear, filterMonth, filterCategory))
        }
        categories = result.first
        filtered = result.second
    }

    val now = Calendar.getInstance()
    val currentYear = now.get(Calendar.YEAR)
    val yearOptions = listOf("All Years") +
        (currentYear + 2 downTo currentYear - 5).map { it.toString() }
    val monthOptions = listOf("All Months", "Yesterday", "Today") + MONTHS
    val categoryOptions = listOf("All Categories") + categories

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFFF5576C), Color(0xFFFF6B6B))))
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📉 Expenses", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Track where money goes",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(Color(0x33FFFFFF))
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Add form
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 18.dp)
            ) {
                Text(
                    "📌 Add Expense",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))

                FormLabel("Amount ($currency)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FormTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        placeholder = "e.g., 1250",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
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

                Spacer(Modifier.height(12.dp))
                FormLabel("Category")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SuggestionField(
                        value = category,
                        onValueChange = { category = it },
                        suggestions = if (categoryFieldFocused && category.isNotBlank()) {
                            categories.filter { it.contains(category, ignoreCase = true) }
                        } else emptyList(),
                        onSelect = { c ->
                            category = c
                            categoryFieldFocused = false
                        },
                        placeholder = "Select or type category",
                        modifier = Modifier.weight(1f),
                        onFocusChanged = { categoryFieldFocused = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF667EEA))
                            .clickable { showCategoryManager = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("✏️", fontSize = 18.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))
                FormLabel("Date")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Text(date, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(Modifier.height(12.dp))
                FormLabel("Receipt (Optional)")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5576C))
                        .clickable { pickReceipt() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📎 Attach Receipt", color = Color.White, fontSize = 12.sp)
                }
                if (receipt != null) {
                    Text(
                        receipt!!.name,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFFF5576C), Color(0xFFFF6B6B))))
                        .clickable {
                            val amt = amount.toDoubleOrNull()
                            val cat = category.trim()
                            if (amt == null || amt <= 0) {
                                Toast.makeText(context, "Enter valid amount", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            if (cat.isEmpty()) {
                                Toast.makeText(context, "Select or type a category", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            if (date.isEmpty()) {
                                Toast.makeText(context, "Pick date", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            val capturedAmt = amt
                            val capturedCat = cat
                            val capturedDate = date
                            val capturedReceipt = receipt
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    if (!db.getExpenseCategories().contains(capturedCat)) {
                                        db.addExpenseCategory(capturedCat)
                                    }
                                    db.addTransaction(
                                        type = AppDatabase.TYPE_EXPENSE,
                                        amount = capturedAmt,
                                        name = capturedCat,
                                        date = capturedDate,
                                        receiptName = capturedReceipt?.name,
                                        receiptPath = capturedReceipt?.path
                                    )
                                }
                                amount = ""
                                category = ""
                                date = getTodayStr()
                                receipt = null
                                refreshKey++
                                AutoEvents.refreshTick.intValue++
                                Toast.makeText(context, "✅ Expense added", Toast.LENGTH_SHORT).show()
                                onExpenseAdded()
                            }
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("- Add Expense", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        // History header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "📋 Expense History",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (filtered.size == 1) "1 transaction" else "${filtered.size} transactions",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Filters
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterDropdown("📅 Year", yearOptions, filterYear, { filterYear = it }, Modifier.weight(1f))
                    FilterDropdown("🗓️ Month", monthOptions, filterMonth, { filterMonth = it }, Modifier.weight(1f))
                    FilterDropdown("📌 Category", categoryOptions, filterCategory, { filterCategory = it }, Modifier.weight(1f))
                }
            }
        }

        // Total
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "💸 Filtered Total Expense",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatCurrency(currency, filtered.sumOf { it.amount }),
                    color = Color(0xFFDC2626),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Empty state
        if (filtered.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 35.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📭 No records", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(filtered, key = { it.id }) { tx ->
                Column(Modifier.padding(horizontal = 10.dp)) {
                    TransactionRow(
                        transaction = tx,
                        currencySymbol = currency,
                        showActions = true,
                        onDownloadReceipt = { openReceipt(context, tx) },
                        onEdit = { editingTx = tx },
                        onDelete = { deleteTarget = tx }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

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

    if (showCategoryManager) {
        ItemsManagerDialog(
            title = "📋 Manage Expense Categories",
            items = categories,
            confirmMessage = { c -> "Remove \"$c\" from categories?" },
            onDismiss = { showCategoryManager = false },
            onDelete = { c ->
                db.removeExpenseCategory(c)
                refreshKey++
            }
        )
    }

    editingTx?.let { tx ->
        EditTransactionDialog(
            transaction = tx,
            db = db,
            settings = settings,
            onDismiss = { editingTx = null },
            onSaved = { refreshKey++ }
        )
    }

    deleteTarget?.let { tx ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete?") },
            text = { Text("Delete this transaction?") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTransactionWithAutoTracking(db, settings, tx)
                    deleteTarget = null
                    refreshKey++
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

private fun filterExpenses(
    db: AppDatabase,
    year: String,
    month: String,
    category: String
): List<Transaction> {
    val all = db.getTransactions(type = AppDatabase.TYPE_EXPENSE)
    return all.filter { t ->
        val parts = t.date.split("-")
        val txYear = if (parts.size >= 1) parts[0].toIntOrNull() else null
        val txMonth = if (parts.size >= 2) parts[1].toIntOrNull() else null

        val yearOk = if (year != "All Years") {
            year.toIntOrNull() != null && txYear == year.toIntOrNull()
        } else true

        val monthOk = when {
            month == "All Months" -> true
            month == "Today" -> t.date == getTodayStr()
            month == "Yesterday" -> t.date == getYesterdayStr()
            else -> {
                val idx = MONTHS.indexOf(month)
                idx >= 0 && txMonth != null && txMonth - 1 == idx
            }
        }

        val categoryOk = if (category != "All Categories") {
            t.name.contains(category, ignoreCase = true)
        } else true

        yearOk && monthOk && categoryOk
    }.sortedByDescending { it.date }
}
